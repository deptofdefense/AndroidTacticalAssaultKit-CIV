#include "feature/FDB.h"

#include <cstdint>
#include <list>

#include "db/Statement2.h"
#include "feature/BruteForceLimitOffsetFeatureCursor.h"
#include "feature/FeatureDatabase.h"
#include "feature/GeometryFactory.h"
#include "feature/LegacyAdapters.h"
#include "feature/MultiplexingFeatureCursor.h"
#include "feature/Style.h"
#include "raster/osm/OSMUtils.h"
#include "port/Collections.h"
#include "port/STLListAdapter.h"
#include "port/STLSetAdapter.h"
#include "port/STLVectorAdapter.h"
#include "thread/Lock.h"
#include "util/IO.h"
#include "util/Logging.h"
#include "util/MathUtils.h"
#include "util/Memory.h"
#include "util/NonHeapAllocatable.h"
#include "db/DatabaseFactory.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Port;

#define ABS_TAG "FDB"

#define DEFAULT_VISIBILITY_FLAGS \
    (VISIBILITY_SETTINGS_FEATURESET | \
     VISIBILITY_SETTINGS_FEATURE)

#define FEATURE_DATABASE_VERSION 5  // Added support for read_only property

namespace
{
    template<class Iface, class Impl = Iface>
    void deleter(Iface *obj)
    {
        Impl *impl = static_cast<Impl *>(obj);
        delete impl;
    }

    template<class Iface, class Impl = Iface>
    void deleter_const(const Iface *obj)
    {
        const Impl *impl = static_cast<const Impl *>(obj);
        delete impl;
    }

    template<class Iface>
    void leaker_const(const Iface *obj)
    {}


    std::ostringstream &insert(std::ostringstream &strm, const char *s) NOTHROWS;

    TAKErr appendOrder(bool &first, std::ostringstream &sql, Collection<BindArgument> &args, const FeatureDataStore2::FeatureQueryParameters::Order order) NOTHROWS;
    TAKErr appendSpatialFilter(const atakmap::feature::Geometry &filter, WhereClauseBuilder2 &whereClause, const bool spatialFilterEnabled) NOTHROWS;

    // attribute coders
#define DECL_CODER(type) \
    TAKErr encode##type(DataOutput2 &dos, const atakmap::util::AttributeSet &attr, const char *key) NOTHROWS; \
    TAKErr decode##type(atakmap::util::AttributeSet &attr, DataInput2 &dos, const char *key) NOTHROWS;

    DECL_CODER(Int);
    DECL_CODER(Long);
    DECL_CODER(Double);
    DECL_CODER(String);
    DECL_CODER(Binary);
    DECL_CODER(IntArray);
    DECL_CODER(LongArray);
    DECL_CODER(DoubleArray);
    DECL_CODER(StringArray);
    DECL_CODER(BinaryArray);
#undef DECL_CODER

    std::map<atakmap::util::AttributeSet::Type, int> ATTRIB_TYPES;

    template<class T>
    class PointerContainerMgr : NonCopyable,
                                NonHeapAllocatable
    {
    public :
        PointerContainerMgr(T &c) NOTHROWS;
        ~PointerContainerMgr() NOTHROWS;
    public :
        void release() NOTHROWS;
    private :
        T &container;
    };

    class DefaultFeatureDefinition : public FeatureDefinition2,
                                     NonHeapAllocatable
    {
    public :
        DefaultFeatureDefinition(const Feature2 &impl) NOTHROWS;
    public:
        TAKErr getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS override;
        FeatureDefinition2::GeometryEncoding getGeomCoding() NOTHROWS override;
        AltitudeMode getAltitudeMode() NOTHROWS override;
        double getExtrude() NOTHROWS override;
        TAKErr getName(const char **value) NOTHROWS override;
        FeatureDefinition2::StyleEncoding getStyleCoding() NOTHROWS override;
        TAKErr getRawStyle(FeatureDefinition2::RawData *value) NOTHROWS override;
        TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS override;
        TAKErr get(const Feature2 **feature) NOTHROWS override;
    private :
        const Feature2 &impl;
    }; // FeatureDefinition


}

FDB::FDB(int modificationFlags, int visibilityFlags) NOTHROWS :
    AbstractFeatureDataStore2(modificationFlags, visibilityFlags),
    database_file_(nullptr),
    database_(nullptr, nullptr),
    spatial_index_enabled_(false),
    info_dirty_(true),
    visible_(true),
    visible_check_(true),
    min_lod_(0),
    max_lod_(0x7FFFFFFF),
    lod_check_(true),
    read_only_(false),
    attr_schema_dirty_(true)
{
    static TAKErr attrSpecCodersInitialized = AttributeSpec::initCoders();
}

FDB::~FDB() NOTHROWS
{}

TAKErr FDB::open(const char *path) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    if (this->database_.get())
        return TE_IllegalState;
    int dbVersionIgnored;
    return this->open(path, &dbVersionIgnored, true);
}

TAKErr FDB::open(const char *path, int* dbVersion, bool buildIndices) NOTHROWS
{
    TAKErr code;

    this->database_file_ = path;
    const bool creating = (!atakmap::util::pathExists(this->database_file_) || atakmap::util::getFileSize(this->database_file_) == 0LL);

    DatabaseInformation info(this->database_file_);
    code = DatabaseFactory_create(this->database_, info);
    TE_CHECKRETURN_CODE(code);

#ifdef MSVC
    // XXX - windows seems to be EXTREMELY slow with database IO in
    //       synchronous mode.  We're going to turn off for windows builds;
    //       this yields approximately a 20x speedup for visibility
    //       toggling.
    code = this->database_->execute("pragma synchronous = OFF", nullptr, 0);
    TE_CHECKRETURN_CODE(code);
#endif

    if (creating) {
        code = this->buildTables(buildIndices);
        TE_CHECKRETURN_CODE(code);

        this->spatial_index_enabled_ = buildIndices;
    } else {
        // test for spatial index
        std::vector<Port::String> tableNames;
        Port::STLVectorAdapter<Port::String> tableNamesV(tableNames);
        code = Databases_getTableNames(tableNamesV, *this->database_);
        TE_CHECKRETURN_CODE(code);

        Port::String idxFeaturesGeomStr("idx_features_geometry");
        code = tableNamesV.contains(&this->spatial_index_enabled_, idxFeaturesGeomStr);
        TE_CHECKRETURN_CODE(code);
    }

    this->attr_schema_dirty_ = true;

#ifdef __APPLE__
    //XXX- should only be used for internal dbs
    QueryPtr journalCursor(nullptr, nullptr);
    code = this->database->query(journalCursor, "pragma journal_mode=wal");
    TE_CHECKRETURN_CODE(code);

    if (journalCursor->moveToNext() == TE_Ok) {
        const char *value = "";
        journalCursor->getString(&value, 0);
        atakmap::util::Logger::log(atakmap::util::Logger::Debug, "FDB journal_mode=%s", value);
    }
#endif

    this->database_->getVersion(dbVersion);

    if (*dbVersion < FEATURE_DATABASE_VERSION) {
        this->upgradeTables(dbVersion);
    }

    code = this->refresh();
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr FDB::buildTables(const bool indices) NOTHROWS
{
    TAKErr code;
    QueryPtr result(nullptr, nullptr);

    std::pair<int, int> version;
    try {
        version = atakmap::feature::getSpatialiteVersion(*this->database_);
    } catch(...) {
        return TE_Err;
    }
    const int major = version.first;
    const int minor = version.second;

    const char *initSpatialMetadataSql;
    if (major > 4 || (major == 4 && minor >= 1))
        initSpatialMetadataSql = "SELECT InitSpatialMetadata(1)";
    else
        initSpatialMetadataSql = "SELECT InitSpatialMetadata()";

    result.reset();

    code = this->database_->query(result, initSpatialMetadataSql);
    TE_CHECKRETURN_CODE(code);

    code = result->moveToNext();
    TE_CHECKRETURN_CODE(code);
    result.reset();

    code = this->database_->execute(
        "CREATE TABLE featuresets"
        "    (id INTEGER PRIMARY KEY AUTOINCREMENT,"
        "     name TEXT,"
        "     name_version INTEGER,"
        "     visible INTEGER,"
        "     visible_version INTEGER,"
        "     visible_check INTEGER,"
        "     min_lod INTEGER,"
        "     max_lod INTEGER,"
        "     lod_version INTEGER,"
        "     lod_check INTEGER,"
        "     type TEXT,"
        "     provider TEXT,"
        "     read_only INTEGER)",
        nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    code = this->database_->execute(
        "CREATE TABLE features"
        "    (fid INTEGER PRIMARY KEY AUTOINCREMENT,"
        "     fsid INTEGER,"
        "     name TEXT COLLATE NOCASE,"
        "     style_id INTEGER,"
        "     attribs_id INTEGER,"
        "     version INTEGER,"
        "     visible INTEGER,"
        "     visible_version INTEGER,"
        "     min_lod INTEGER,"
        "     max_lod INTEGER,"
        "     lod_version INTEGER,"
        "     altitude_mode INTEGER,"
        "     extrude REAL)",
        nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    result.reset();

    code = this->database_->query(
        result,
        "SELECT AddGeometryColumn(\'features\', \'geometry\', 4326, \'GEOMETRY\', \'XY\')");
    TE_CHECKRETURN_CODE(code);
    code = result->moveToNext();
    TE_CHECKRETURN_CODE(code);
    result.reset();

    code = this->database_->execute(
        "CREATE TABLE styles"
        "    (id INTEGER PRIMARY KEY AUTOINCREMENT,"
        "     coding TEXT,"
        "     value BLOB)",
        nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    code = this->database_->execute(
        "CREATE TABLE attributes"
        "    (id INTEGER PRIMARY KEY AUTOINCREMENT,"
        "     value BLOB)",
        nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    code = this->database_->execute("CREATE TABLE attribs_schema"
        "    (id INTEGER PRIMARY KEY AUTOINCREMENT,"
        "     name TEXT,"
        "     coding INTEGER)",
        nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    code = this->createTriggersNoSync();
    TE_CHECKRETURN_CODE(code);
    if (indices) {
        code = this->createIndicesNoSync();
        TE_CHECKRETURN_CODE(code);
    }

    code = this->database_->setVersion(FEATURE_DATABASE_VERSION);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr FDB::upgradeTables(int* dbVersion) NOTHROWS 
{
    TAKErr code;
    code = this->database_->execute("ALTER TABLE featuresets ADD COLUMN read_only INTEGER", nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    code = this->database_->execute("UPDATE featuresets SET read_only = 0", nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    if (*dbVersion <= 3) {
        code = this->database_->execute("ALTER TABLE features ADD COLUMN altitude_mode INTEGER", nullptr, 0);
        TE_CHECKRETURN_CODE(code);

        code = this->database_->execute("ALTER TABLE features ADD COLUMN extrude REAL", nullptr, 0);
        TE_CHECKRETURN_CODE(code);

        code = this->database_->execute("UPDATE features SET altitude_mode = 0, extrude = 0", nullptr, 0);
        TE_CHECKRETURN_CODE(code);
    }

    code = this->database_->setVersion(FEATURE_DATABASE_VERSION);
    TE_CHECKRETURN_CODE(code);
    *dbVersion = FEATURE_DATABASE_VERSION;

    return code;
}

TAKErr FDB::createIndicesNoSync() NOTHROWS
{
    TAKErr code;

    code = this->database_
        ->execute(
        "CREATE INDEX IF NOT EXISTS IdxFeaturesLevelOfDetail ON features(min_lod, max_lod)",
        nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    code = this->database_->execute(
        "CREATE INDEX IF NOT EXISTS IdxFeaturesName ON features(name)",
        nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    QueryPtr result(nullptr, nullptr);
    code = this->database_->query(result, "SELECT CreateSpatialIndex(\'features\', \'geometry\')");
    TE_CHECKRETURN_CODE(code);
    code = result->moveToNext();
    TE_CHECKRETURN_CODE(code);
    result.reset();

    return code;
}

TAKErr FDB::dropIndicesNoSync() NOTHROWS
{
    TAKErr code;
    code = this->database_->execute("DROP INDEX IF EXISTS IdxFeaturesLevelOfDetail", nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database_->execute("DROP INDEX IF EXISTS IdxFeaturesGroupIdName", nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    std::vector<Port::String> tableNames;
    Port::STLVectorAdapter<Port::String> tableNamesV(tableNames);
    code = Databases_getTableNames(tableNamesV, *this->database_);
    TE_CHECKRETURN_CODE(code);

    bool hasSpatialIndex;
    Port::String idxFeaturesGeomStr("idx_features_geometry");
    code = tableNamesV.contains(&hasSpatialIndex, idxFeaturesGeomStr);
    TE_CHECKRETURN_CODE(code);

    if (hasSpatialIndex) {
        QueryPtr result(nullptr, nullptr);
        code = this->database_->query(result, "SELECT DisableSpatialIndex(\'features\', \'geometry\')");
        TE_CHECKRETURN_CODE(code);
        code = result->moveToNext();
        TE_CHECKRETURN_CODE(code);
        result.reset();
        code = this->database_->execute("DROP TABLE idx_features_geometry", nullptr, 0);
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr FDB::createTriggersNoSync() NOTHROWS
{
    TAKErr code;
    code = this->database_->execute(
        "CREATE TRIGGER IF NOT EXISTS features_visible_update AFTER UPDATE OF visible ON features "
        "BEGIN "
        "UPDATE featuresets SET visible_check = 1 WHERE id = OLD.fsid; "
        "UPDATE features SET visible_version = (SELECT visible_version FROM featuresets WHERE id = OLD.fsid LIMIT 1) WHERE fid = OLD.fid; "
        "END;", nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database_->execute(
        "CREATE TRIGGER IF NOT EXISTS featuresets_visible_update AFTER UPDATE OF visible ON featuresets "
        "BEGIN "
        "UPDATE featuresets SET visible_version = (OLD.visible_version+1), visible_check = 0 WHERE id = OLD.id; "
        "END;", nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database_->execute(
        "CREATE TRIGGER IF NOT EXISTS features_min_lod_update AFTER UPDATE OF min_lod ON features "
        "BEGIN "
        "UPDATE featuresets SET lod_check = 1 WHERE id = OLD.fsid; "
        "UPDATE features SET lod_version = (SELECT lod_version FROM featuresets WHERE id = OLD.fsid LIMIT 1) WHERE fid = OLD.fid; "
        "END;", nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database_->execute(
        "CREATE TRIGGER IF NOT EXISTS features_max_lod_update AFTER UPDATE OF max_lod ON features "
        "BEGIN "
        "UPDATE featuresets SET lod_check = 1 WHERE id = OLD.fsid; "
        "UPDATE features SET lod_version = (SELECT lod_version FROM featuresets WHERE id = OLD.fsid LIMIT 1) WHERE fid = OLD.fid; "
        "END;", nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database_->execute(
        "CREATE TRIGGER IF NOT EXISTS featuresets_min_lod_update AFTER UPDATE OF min_lod ON featuresets "
        "BEGIN "
        "UPDATE featuresets SET lod_version = (OLD.lod_version+1), lod_check = 0 WHERE id = OLD.id; "
        "END;", nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database_->execute(
        "CREATE TRIGGER IF NOT EXISTS featuresets_max_lod_update AFTER UPDATE OF max_lod ON featuresets "
        "BEGIN "
        "UPDATE featuresets SET lod_version = (OLD.lod_version+1), lod_check = 0 WHERE id = OLD.id; "
        "END;", nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database_->execute(
        "CREATE TRIGGER IF NOT EXISTS featuresets_name_update AFTER UPDATE OF name ON featuresets "
        "BEGIN "
        "UPDATE featuresets SET name_version = (OLD.name_version+1) WHERE id = OLD.id; "
        "END;", nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database_->execute(
        "CREATE TRIGGER IF NOT EXISTS featuresets_delete AFTER DELETE ON featuresets "
        "FOR EACH ROW "
        "BEGIN "
        "DELETE FROM features WHERE fsid = OLD.id; "
        "END;", nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr FDB::validateInfo() NOTHROWS
{
    if (!this->info_dirty_)
        return TE_Ok;

    TAKErr code;

    int dbVersion;
    this->database_->getVersion(&dbVersion);

    this->visible_ = false;
    this->visible_check_ = false;
    this->min_lod_ = 0x7FFFFFFF;
    this->max_lod_ = 0;
    this->lod_check_ = false;
    this->read_only_ = false;

    const char *sql = "SELECT"
                      "    id,"
                      "    name, name_version,"
                      "    visible, visible_version, visible_check,"
                      "    min_lod, max_lod, lod_version, lod_check,"
                      "    read_only"
                      " FROM featuresets";

    QueryPtr result(nullptr, nullptr);
    code = this->database_->query(result, sql);
    TE_CHECKRETURN_CODE(code);
    do {
        code = result->moveToNext();
        TE_CHECKBREAK_CODE(code);

        int64_t fsid;
        code = result->getLong(&fsid, 0);
        TE_CHECKBREAK_CODE(code);

        std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator entry;
        entry = this->feature_sets_.find(fsid);
        if (entry == this->feature_sets_.end())
            continue;

        std::shared_ptr<FeatureSetDefn> fs = entry->second;

        union {
            const char *s;
            int i;
        } scratch;

        code = result->getLong(&fs->fsid, 0);
        TE_CHECKBREAK_CODE(code);
        code = result->getString(&scratch.s, 1);
        TE_CHECKBREAK_CODE(code);
        fs->name = scratch.s;
        code = result->getInt(&fs->nameVersion, 2);
        TE_CHECKBREAK_CODE(code);
        code = result->getInt(&scratch.i, 3);
        TE_CHECKBREAK_CODE(code);
        fs->visible = !!scratch.i;
        code = result->getInt(&fs->visibleVersion, 4);
        TE_CHECKBREAK_CODE(code);
        code = result->getInt(&scratch.i, 5);
        TE_CHECKBREAK_CODE(code);
        fs->visibleCheck = !!scratch.i;
        code = result->getInt(&fs->minLod, 6);
        TE_CHECKBREAK_CODE(code);
        code = result->getInt(&fs->maxLod, 7);
        TE_CHECKBREAK_CODE(code);
        code = result->getInt(&fs->lodVersion, 8);
        TE_CHECKBREAK_CODE(code);
        code = result->getInt(&scratch.i, 9);
        TE_CHECKBREAK_CODE(code);
        fs->lodCheck = !!scratch.i;
        code = result->getInt(&scratch.i, 10);
        TE_CHECKBREAK_CODE(code);
        fs->readOnly = !!scratch.i;

        this->visible_ |= fs->visible;
        this->visible_check_ |= fs->visibleCheck;

        if (fs->minLod < this->min_lod_)
            this->min_lod_ = fs->minLod;
        if (fs->maxLod > this->max_lod_)
            this->max_lod_ = fs->maxLod;
        this->lod_check_ |= fs->lodCheck;
        this->read_only_ |= fs->readOnly;
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);

    this->info_dirty_ = false;
    result.reset();

    return code;
}

TAKErr FDB::validateAttributeSchema() NOTHROWS
{
    TAKErr code;

    code = TE_Ok;
    if (this->attr_schema_dirty_) {
        QueryPtr result(nullptr, nullptr);

        code = this->database_->query(result, "SELECT id, name, coding FROM attribs_schema");
        TE_CHECKRETURN_CODE(code);

        int64_t id;
        const char *name;
        int coding;
        do {
            code = result->moveToNext();
            TE_CHECKBREAK_CODE(code);

            code = result->getLong(&id, 0);
            TE_CHECKBREAK_CODE(code);
            code = result->getString(&name, 1);
            TE_CHECKBREAK_CODE(code);
            code = result->getInt(&coding, 2);
            TE_CHECKBREAK_CODE(code);

            std::shared_ptr<AttributeSpec> schemaSpec(new AttributeSpec(name, id, coding));
            this->id_to_attr_schema_[id] = schemaSpec;

            KeyAttrSchemaMap::iterator parentSpec;
            parentSpec = this->key_to_attr_schema_.find(name);
            if (parentSpec != this->key_to_attr_schema_.end()) {
                parentSpec->second->secondaryDefs[coding] = schemaSpec;
            } else {
                this->key_to_attr_schema_[name] = schemaSpec;
            }
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);

        result.reset();

        this->attr_schema_dirty_ = false;
    }

    return code;
}

TAKErr FDB::isCompatible(bool *matched, const FeatureSetDefn &defn, const FeatureSetQueryParameters *params) NOTHROWS
{
    if (!params) {
        *matched = true;
        return TE_Ok;
    }

    TAKErr code;

    code = TE_Ok;
    *matched = true;

    if (!params->ids->empty()) {
        int64_t fsid = defn.fsid;
        code = params->ids->contains(matched, fsid);
        TE_CHECKRETURN_CODE(code);
        if (!(*matched))
            return code;
    }
    if (!params->names->empty()) {
        code = AbstractFeatureDataStore2::matches(matched, *params->names, defn.name, '%');
        TE_CHECKRETURN_CODE(code);
        if (!(*matched))
            return code;
    }
    if (!params->types->empty()) {
        code = AbstractFeatureDataStore2::matches(matched, *params->types, defn.type, '%');
        TE_CHECKRETURN_CODE(code);
        if (!(*matched))
            return code;
    }
    if (!params->providers->empty()) {
        code = AbstractFeatureDataStore2::matches(matched, *params->providers, defn.provider, '%');
        TE_CHECKRETURN_CODE(code);
        if (!(*matched))
            return code;
    }

    return code;
}


TAKErr FDB::isCompatible(bool *matched, const FeatureSetDefn &defn, const FeatureQueryParameters *params) NOTHROWS
{
    if (!params) {
        *matched = true;
        return TE_Ok;
    }

    TAKErr code;

    *matched = true;
    code = TE_Ok;

    if (!params->featureSetIds->empty()) {
        int64_t fsid = defn.fsid;
        code = params->featureSetIds->contains(matched, fsid);
        TE_CHECKRETURN_CODE(code);
        if (!(*matched))
            return code;
    }
    if (!params->featureSets->empty()) {
        code = AbstractFeatureDataStore2::matches(matched, *params->featureSets, defn.name, '%');
        TE_CHECKRETURN_CODE(code);
        if (!(*matched))
            return code;
    }
    if (!params->types->empty()) {
        code = AbstractFeatureDataStore2::matches(matched, *params->types, defn.type, '%');
        TE_CHECKRETURN_CODE(code);
        if (!(*matched))
            return code;
    }
    if (!params->providers->empty()) {
        code = AbstractFeatureDataStore2::matches(matched, *params->providers, defn.provider, '%');
        TE_CHECKRETURN_CODE(code);
        if (!(*matched))
            return code;
    }

    return code;
}


TAKErr FDB::isCompatible(bool *matched, const FeatureSetQueryParameters *params) NOTHROWS
{
    *matched = false;
    if (this->feature_sets_.empty())
        return TE_Ok;

    TAKErr code;

    code = TE_Ok;

    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator fs;
    for (fs = this->feature_sets_.begin(); fs != this->feature_sets_.end(); fs++) {
        code = isCompatible(matched, *fs->second, params);
        TE_CHECKBREAK_CODE(code);
        if (*matched)
            break;
    }
    return code;
}

TAKErr FDB::isCompatible(bool *matched, const FeatureQueryParameters *params) NOTHROWS
{
    *matched = false;
    if (this->feature_sets_.empty())
        return TE_Ok;

    TAKErr code;

    code = TE_Ok;

    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator fs;
    for (fs = this->feature_sets_.begin(); fs != this->feature_sets_.end(); fs++) {
        code = isCompatible(matched, *fs->second, params);
        TE_CHECKBREAK_CODE(code);
        if (*matched)
            break;
    }
    return code;
}

TAKErr FDB::getFeature(FeaturePtr_const &feature, const int64_t fid) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!this->database_.get()) {
        return TE_IllegalState;
    }

    FeatureQueryParameters params;
    code = params.featureIds->add(fid);
    TE_CHECKRETURN_CODE(code);
    params.limit = 1;


    FeatureCursorPtr result(nullptr, nullptr);
    code = this->queryFeatures(result, params);
    TE_CHECKRETURN_CODE(code);
    code = result->moveToNext();
    if (code == TE_Done)
        return TE_InvalidArg;
    else if (code != TE_Ok)
        return code;

    const Feature2 *retval;
    code = result->get(&retval);
    TE_CHECKRETURN_CODE(code);

    feature = FeaturePtr_const(new Feature2(*retval), deleter_const<Feature2>);
    result.reset();

    return code;
}

//public FeatureCursor queryFeatures(FeatureQueryParameters params) {
TAKErr FDB::queryFeatures(FeatureCursorPtr &result) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!this->database_.get()) {
        return TE_IllegalState;
    }

    int dbVersion;
    this->database_->getVersion(&dbVersion);

    const int idCol = 0;
    const int fsidCol = 1;
    const int versionCol = 2;
    int extrasCol = versionCol + 1;

    int nameCol = -1;
    int geomCol = -1;
    int styleCol = -1;
    int attribsCol = -1;
    int altitudeModeCol = -1;
    int extrudeCol = -1;

    std::ostringstream sql;
    sql << "SELECT features.fid, features.fsid, features.version";
    sql << ", features.name";
    nameCol = extrasCol++;
    sql << ", features.geometry";
    geomCol = extrasCol++;
    sql << ", features.altitude_mode";
    altitudeModeCol = extrasCol++;
    sql << ", features.extrude";
    extrudeCol = extrasCol++;
    sql << ", styles.value";
    styleCol = extrasCol++;
    sql << ", attributes.value";
    attribsCol = extrasCol++;
    sql << " FROM features";
    sql << " LEFT JOIN styles ON features.style_id = styles.id";
    sql << " LEFT JOIN attributes ON features.attribs_id = attributes.id";

    QueryPtr cursor(nullptr, nullptr);

    code = this->database_->query(cursor, sql.str().c_str());
    TE_CHECKRETURN_CODE(code);

    result = FeatureCursorPtr(new FeatureCursorImpl(*this, std::move(cursor), idCol, fsidCol, versionCol, nameCol, geomCol, styleCol,
                                                    attribsCol, altitudeModeCol, extrudeCol),
                              deleter_const<FeatureCursor2, FeatureCursorImpl>);
    cursor.release();
    cursor.reset();

    return code;
}

TAKErr FDB::queryFeatures(FeatureCursorPtr &result, const FeatureQueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!this->database_.get()) {
        return TE_IllegalState;
    }

    int dbVersion;
    this->database_->getVersion(&dbVersion);

    const int ignoredFields = params.ignoredFields;

    const int idCol = 0;
    const int fsidCol = 1;
    const int versionCol = 2;
    int extrasCol = versionCol + 1;

    int nameCol = -1;
    int geomCol = -1;
    int styleCol = -1;
    int attribsCol = -1;
    int altitudeModeCol = -1;
    int extrudeCol = -1;

    std::list<BindArgument> args;

    std::ostringstream sql;
    sql << "SELECT features.fid, features.fsid, features.version";

    if (!MathUtils_hasBits(ignoredFields, FeatureQueryParameters::NameField)) {
        sql << ", features.name";
        nameCol = extrasCol++;
    }
    if (!MathUtils_hasBits(ignoredFields, FeatureQueryParameters::GeometryField)) {
        if (!params.ops->empty()) {
            std::ostringstream geomStr;
            geomStr << "features.geometry";

            Collection<FeatureQueryParameters::SpatialOp>::IteratorPtr opsIter(nullptr, nullptr);
            code = params.ops->iterator(opsIter);
            TE_CHECKRETURN_CODE(code);
            do {
                FeatureQueryParameters::SpatialOp op;
                code = opsIter->get(op);
                TE_CHECKBREAK_CODE(code);

                if (op.type == FeatureQueryParameters::SpatialOp::Simplify) {
                    insert(geomStr, "SimplifyPreserveTopology(");
                    geomStr << ", ?)";

                    args.push_back(BindArgument(op.args.simplify.distance));
                } else if (op.type == FeatureQueryParameters::SpatialOp::Buffer) {
                    insert(geomStr, "Buffer(");
                    geomStr << ", ?)";

                    args.push_back(BindArgument(op.args.buffer.distance));
                }
                code = opsIter->next();
                TE_CHECKBREAK_CODE(code);
            } while (true);
            if (code == TE_Done)
                code = TE_Ok;
            sql << ", ";
            sql << geomStr.str();
        } else {
            sql << ", features.geometry";
        }
        geomCol = extrasCol++;
        sql << ", features.altitude_mode";
        altitudeModeCol = extrasCol++;
        sql << ", features.extrude";
        extrudeCol = extrasCol++;
    }
    if (!MathUtils_hasBits(ignoredFields, FeatureQueryParameters::StyleField)) {
        sql << ", styles.value";
        styleCol = extrasCol++;
    }
    if (!MathUtils_hasBits(ignoredFields, FeatureQueryParameters::AttributesField)) {
        sql << ", attributes.value";
        attribsCol = extrasCol++;
    }

    sql << " FROM features";
    if (!MathUtils_hasBits(ignoredFields, FeatureQueryParameters::StyleField))
        sql << " LEFT JOIN styles ON features.style_id = styles.id";
    if (!MathUtils_hasBits(ignoredFields, FeatureQueryParameters::AttributesField))
        sql << " LEFT JOIN attributes ON features.attribs_id = attributes.id";

    std::set<std::shared_ptr<const FeatureSetDefn>> fsNoCheck;
    code = this->filterNoSync(fsNoCheck, &params, true);
    if (fsNoCheck.empty()) {
        result = FeatureCursorPtr(new MultiplexingFeatureCursor(), deleter_const<FeatureCursor2, MultiplexingFeatureCursor>);
        return code;
    }

    std::list<std::shared_ptr<const FeatureSetDefn>> fsCheck;
    if (params.visibleOnly) {
        this->validateInfo();

        auto iter = fsNoCheck.begin();
        std::shared_ptr<const FeatureSetDefn> defn;
        while (iter != fsNoCheck.end()) {
            defn = *iter;
            if (defn->visibleCheck) {
                fsCheck.push_back(defn);
                iter = fsNoCheck.erase(iter);
            } else {
                iter++;
            }
        }
    }
    if (!isnan(params.minResolution) || !isnan(params.maxResolution)) {
        this->validateInfo();

        auto iter = fsNoCheck.begin();
        std::shared_ptr<const FeatureSetDefn> defn;
        while (iter != fsNoCheck.end()) {
            defn = *iter;
            if (defn->lodCheck) {
                fsCheck.push_back(defn);
                iter = fsNoCheck.erase(iter);
            } else {
                iter++;
            }
        }
    }

    std::list<FeatureCursorImpl *> retval;
    PointerContainerMgr<std::list<FeatureCursorImpl *>> retvalMgr(retval);

    if (!fsCheck.empty()) {
        std::list<std::shared_ptr<const FeatureSetDefn>>::iterator fsCheckIter;
        //for (FeatureSetDefn fs : fsCheck) {
        for (fsCheckIter = fsCheck.begin(); fsCheckIter != fsCheck.end(); fsCheckIter++) {
            std::shared_ptr<const FeatureSetDefn> fs = *fsCheckIter;
            std::ostringstream subsql;
            std::list<BindArgument> subargs;
            WhereClauseBuilder2 where;

            bool emptyResults;
            code = this->buildParamsWhereClauseCheck(&emptyResults, params, *fs, where);
            TE_CHECKBREAK_CODE(code);
            if (emptyResults)
                continue;

            subsql << sql.str();
            {
            STLListAdapter<BindArgument> subargsAdapter(subargs);
            STLListAdapter<BindArgument> argsAdapter(args);
            Collections_addAll(subargsAdapter, argsAdapter);
            }
            const char *select;
            where.getSelection(&select);

            if (select) {
                subsql << " WHERE ";
                subsql << select;
                STLListAdapter<BindArgument> subargsAdapter(subargs);
                where.getBindArgs(subargsAdapter);
            }

            if (!params.order->empty()) {
                bool first = true;
                std::ostringstream orderSql;
                Collection<FeatureQueryParameters::Order>::IteratorPtr orderIter(nullptr, nullptr);
                code = params.order->iterator(orderIter);
                TE_CHECKBREAK_CODE(code);
                do {
                    FeatureQueryParameters::Order order;
                    code = orderIter->get(order);
                    TE_CHECKBREAK_CODE(code);

                    STLListAdapter<BindArgument> subargsAdapter(subargs);
                    code = appendOrder(first, orderSql, subargsAdapter, order);
                    TE_CHECKBREAK_CODE(code);

                    code = orderIter->next();
                    TE_CHECKBREAK_CODE(code);
                } while (true);
                if (code == TE_Done)
                    code = TE_Ok;
                TE_CHECKBREAK_CODE(code);

                if (!first) {
                    subsql << " ORDER BY ";
                    subsql << orderSql.str();
                }
            }

            if (params.limit > 0) {
                subsql << " LIMIT ?";
                subargs.push_back(BindArgument(params.limit));

                if (fsCheck.size() == 1 && fsNoCheck.empty() && params.offset > 0) {
                    subsql << " OFFSET ?";
                    subargs.push_back(BindArgument(params.offset));
                }
            }

            QueryPtr queryResult(nullptr, nullptr);
            STLListAdapter<BindArgument> subargsAdapter(subargs);
            code = BindArgument::query(queryResult, *this->database_, subsql.str().c_str(), subargsAdapter);
            TE_CHECKBREAK_CODE(code);

            retval.push_back(new FeatureCursorImpl(*this, std::move(queryResult), idCol, fsidCol, versionCol, nameCol, geomCol, styleCol,
                                                   attribsCol, altitudeModeCol, extrudeCol));
            queryResult.reset();
        }
    }

    if (!fsNoCheck.empty()) {
        do {
            std::ostringstream subsql;
            std::list<BindArgument> subargs;
            WhereClauseBuilder2 where;

            bool emptyResults;
            STLSetAdapter<std::shared_ptr<const FeatureSetDefn>> fsNoCheckAdapter(fsNoCheck);
            code = this->buildParamsWhereClauseNoCheck(&emptyResults, params, fsNoCheckAdapter, where);
            TE_CHECKBREAK_CODE(code);
            if (emptyResults)
                continue;

            subsql << sql.str();
            {
            STLListAdapter<BindArgument> subargsAdapter(subargs), argsAdapter(args);
            Collections_addAll(subargsAdapter, argsAdapter);
            }

            const char *select;
            where.getSelection(&select);
            if (select) {
                subsql << " WHERE ";
                subsql << select;
                STLListAdapter<BindArgument> subargsAdapter(subargs);
                where.getBindArgs(subargsAdapter);
            }

            if (!params.order->empty()) {
                bool first = true;
                std::ostringstream orderSql;
                Collection<FeatureQueryParameters::Order>::IteratorPtr orderIter(nullptr, nullptr);
                code = params.order->iterator(orderIter);
                TE_CHECKBREAK_CODE(code);
                do {
                    FeatureQueryParameters::Order order;
                    code = orderIter->get(order);
                    TE_CHECKBREAK_CODE(code);

                    STLListAdapter<BindArgument> subargsAdapter(subargs);
                    code = appendOrder(first, orderSql, subargsAdapter, order);
                    TE_CHECKBREAK_CODE(code);

                    code = orderIter->next();
                    TE_CHECKBREAK_CODE(code);
                } while (true);
                if (code == TE_Done)
                    code = TE_Ok;
                TE_CHECKBREAK_CODE(code);

                if (!first) {
                    subsql << " ORDER BY ";
                    subsql << orderSql.str();
                }
            }

            if (params.limit > 0) {
                subsql << " LIMIT ?";
                subargs.push_back(BindArgument(params.limit));

                if (fsCheck.empty() && params.offset > 0) {
                    subsql << " OFFSET ?";
                    subargs.push_back(BindArgument(params.offset));
                }
            }

            QueryPtr queryResult(nullptr, nullptr);
            STLListAdapter<BindArgument> subargsAdapter(subargs);
            code = BindArgument::query(queryResult, *this->database_, subsql.str().c_str(), subargsAdapter);
            TE_CHECKBREAK_CODE(code);

            retval.push_back(new FeatureCursorImpl(*this, std::move(queryResult), idCol, fsidCol, versionCol, nameCol, geomCol, styleCol,
                                                   attribsCol, altitudeModeCol, extrudeCol));
            queryResult.reset();
        } while (false);
    }

    if (retval.size() == 1) {
        auto cursor = retval.begin();
        result = FeatureCursorPtr(*cursor, deleter_const<FeatureCursor2, FeatureCursorImpl>);
        retvalMgr.release();

        return code;
    } else {
        std::unique_ptr<MultiplexingFeatureCursor> cursor(new MultiplexingFeatureCursor(*params.order));
        std::list<FeatureCursorImpl *>::iterator it;
        // XXX - if loop does not complete, may result in double-delete on entries added
        for (it = retval.begin(); it != retval.end(); it++) {
            cursor->add(std::move(FeatureCursorPtr(*it, deleter_const<FeatureCursor2, FeatureCursorImpl>)));
        }
        retvalMgr.release();

        result = FeatureCursorPtr(cursor.release(), deleter_const<FeatureCursor2, MultiplexingFeatureCursor>);
        if (params.limit > 0) {
            result = FeatureCursorPtr(new BruteForceLimitOffsetFeatureCursor(std::move(result), params.limit, params.offset), deleter_const<FeatureCursor2, BruteForceLimitOffsetFeatureCursor>);
        }

        return code;
    }
}

//public int queryFeaturesCount(FeatureQueryParameters params) {
TAKErr FDB::queryFeaturesCount(int *value) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!this->database_.get()) {
        return TE_IllegalState;
    }

    QueryPtr result(nullptr, nullptr);

    code = this->database_->query(result, "SELECT Count(1) FROM features");
    code = result->moveToNext();
    if (code == TE_Done) {
        *value = 0;
        return TE_Ok;
    } else if (code == TE_Ok) {
        code = result->getInt(value, 0);
        TE_CHECKRETURN_CODE(code);
    }
    result.reset();

    return code;
}

TAKErr FDB::queryFeaturesCount(int *value, const FeatureQueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!this->database_.get()) {
        return TE_IllegalState;
    }

    std::set<std::shared_ptr<const FeatureSetDefn>> fsNoCheck;
    code = this->filterNoSync(fsNoCheck, &params, true);
    TE_CHECKRETURN_CODE(code);
    if (fsNoCheck.empty()) {
        *value = 0;
        return TE_Ok;
    }

    std::list<BindArgument> args;

    std::list<std::shared_ptr<const FeatureSetDefn>> fsCheck;
    if (params.visibleOnly) {
        code = this->validateInfo();
        TE_CHECKRETURN_CODE(code);

        auto iter = fsNoCheck.begin();
        std::shared_ptr<const FeatureSetDefn> defn;
        while (iter != fsNoCheck.end()) {
            defn = *iter;
            if (defn->visibleCheck) {
                fsCheck.push_back(defn);
                iter = fsNoCheck.erase(iter);
            } else {
                iter++;
            }
        }
    }
    if (!isnan(params.minResolution) || !isnan(params.maxResolution)) {
        code = this->validateInfo();
        TE_CHECKRETURN_CODE(code);

        auto iter = fsNoCheck.begin();
        std::shared_ptr<const FeatureSetDefn>defn;
        while (iter != fsNoCheck.end()) {
            defn = *iter;
            if (defn->lodCheck) {
                fsCheck.push_back(defn);
                iter = fsNoCheck.erase(iter);
            } else {
                iter++;
            }
        }
    }

    if ((params.limit > 0 && params.offset > 0) &&
        (fsCheck.size() > 1 ||
        (!fsCheck.empty() && !fsNoCheck.empty()))) {

        return AbstractFeatureDataStore2::queryFeaturesCount(value, *this, params);
    }

    std::ostringstream sql;
    sql << "SELECT ";
    if (params.limit > 0)
        sql << "1";
    else
        sql << "Count(1)";
    sql << " FROM features";

    int retval = 0;
    if (!fsCheck.empty()) {
        std::list<std::shared_ptr<const FeatureSetDefn>>::iterator fs;
        for (fs = fsCheck.begin(); fs != fsCheck.end(); fs++) {
            std::ostringstream subsql;
            std::list<BindArgument> subargs;
            WhereClauseBuilder2 where;

            bool emptyResults;
            code = this->buildParamsWhereClauseCheck(&emptyResults, params, **fs, where);
            TE_CHECKBREAK_CODE(code);
            if (emptyResults)
                continue;

            subsql << sql.str();
            {
            STLListAdapter<BindArgument> subargsAdapter(subargs), argsAdapter(args);
            Collections_addAll(subargsAdapter, argsAdapter);
            }

            const char *select;
            where.getSelection(&select);
            if (select) {
                subsql << " WHERE ";
                subsql << select;
                STLListAdapter<BindArgument> subargsAdapter(subargs);
                where.getBindArgs(subargsAdapter);
            }

            if (!params.order->empty()) {
                bool first = true;
                std::ostringstream orderSql;
                Collection<FeatureQueryParameters::Order>::IteratorPtr orderIter(nullptr, nullptr);
                code = params.order->iterator(orderIter);
                TE_CHECKBREAK_CODE(code);
                do {
                    FeatureQueryParameters::Order order;
                    code = orderIter->get(order);
                    TE_CHECKBREAK_CODE(code);

                    STLListAdapter<BindArgument> subargsAdapter(subargs);
                    code = appendOrder(first, orderSql, subargsAdapter, order);
                    TE_CHECKBREAK_CODE(code);

                    code = orderIter->next();
                    TE_CHECKBREAK_CODE(code);
                } while (true);
                if (code == TE_Done)
                    code = TE_Ok;
                TE_CHECKBREAK_CODE(code);

                if (!first) {
                    subsql << " ORDER BY ";
                    subsql << orderSql.str();
                }
            }

            if (params.limit > 0) {
                subsql << " LIMIT ?";
                subargs.push_back(BindArgument(params.limit));

                if (fsCheck.size() == 1 && fsNoCheck.empty() && params.offset > 0) {
                    subsql << " OFFSET ?";
                    subargs.push_back(BindArgument(params.offset));
                }

                insert(subsql, "SELECT Count(1) FROM (");
                subsql << ")";
            }

            QueryPtr result(nullptr, nullptr);

            STLListAdapter<BindArgument> subargsAdapter(subargs);
            code = BindArgument::query(result, *this->database_, subsql.str().c_str(), subargsAdapter);
            TE_CHECKBREAK_CODE(code);
            code = result->moveToNext();
            if (code == TE_Ok) {
                int cnt;
                code = result->getInt(&cnt, 0);
                TE_CHECKBREAK_CODE(code);

                retval += cnt;
            } else if (code != TE_Done) {
                code = TE_Ok;
            } else {
                break;
            }

            result.reset();
        }

        TE_CHECKRETURN_CODE(code);
    }

    if (!fsNoCheck.empty()) {
        if (fsNoCheck.size() == this->feature_sets_.size())
            fsNoCheck.clear();

        do {
            std::ostringstream subsql;
            std::list<BindArgument> subargs;
            WhereClauseBuilder2 where;

            bool emptyResults;
            STLSetAdapter<std::shared_ptr<const FeatureSetDefn>> fsNoCheckAdapter(fsNoCheck);
            code = this->buildParamsWhereClauseNoCheck(&emptyResults, params, fsNoCheckAdapter, where);
            TE_CHECKBREAK_CODE(code);
            if (emptyResults)
                continue;

            subsql << sql.str();
            STLListAdapter<BindArgument> subargsAdapter(subargs), argsAdapter(args);
            code = Collections_addAll(subargsAdapter, argsAdapter);
            TE_CHECKBREAK_CODE(code);

            const char *select;
            where.getSelection(&select);
            if (select) {
                subsql << " WHERE ";
                subsql << select;
                STLListAdapter<BindArgument> whereargs_adapter(subargs);
                where.getBindArgs(whereargs_adapter);
            }

            if (!params.order->empty()) {
                bool first = true;
                std::ostringstream orderSql;
                Collection<FeatureQueryParameters::Order>::IteratorPtr orderIter(nullptr, nullptr);
                code = params.order->iterator(orderIter);
                TE_CHECKBREAK_CODE(code);
                do {
                    FeatureQueryParameters::Order order;
                    code = orderIter->get(order);
                    TE_CHECKBREAK_CODE(code);

                    STLListAdapter<BindArgument> orderargs_adapter(subargs);
                    code = appendOrder(first, orderSql, orderargs_adapter, order);
                    TE_CHECKBREAK_CODE(code);

                    code = orderIter->next();
                    TE_CHECKBREAK_CODE(code);
                } while (true);
                if (code == TE_Done)
                    code = TE_Ok;
                TE_CHECKBREAK_CODE(code);

                if (!first) {
                    subsql << " ORDER BY ";
                    subsql << orderSql.str();
                }
            }

            if (params.limit > 0) {
                subsql << " LIMIT ?";
                subargs.push_back(BindArgument(params.limit));

                if (fsCheck.empty() && params.offset > 0) {
                    subsql << " OFFSET ?";
                    subargs.push_back(BindArgument(params.offset));
                }

                insert(subsql, "SELECT Count(1) FROM (");
                subsql << ")";
            }

            QueryPtr result(nullptr, nullptr);

            {
            STLListAdapter<BindArgument> queryargs_adapter(subargs);
            code = BindArgument::query(result, *this->database_, subsql.str().c_str(), queryargs_adapter);
            TE_CHECKBREAK_CODE(code);
            }

            code = result->moveToNext();
            if (code == TE_Ok) {
                int cnt;
                code = result->getInt(&cnt, 0);
                TE_CHECKBREAK_CODE(code);
                retval += cnt;
            } else if (code == TE_Done) {
                code = TE_Ok;
            } else {
                break;
            }

            result.reset();
        } while (false);

        TE_CHECKRETURN_CODE(code);
    }

    *value = retval;
    return code;
}

//public synchronized FeatureSet getFeatureSet(int64_t fsid) {
TAKErr FDB::getFeatureSet(FeatureSetPtr_const &featureSet, const int64_t fsid) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!this->database_.get()) {
        return TE_IllegalState;
    }

    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator entry;
    entry = this->feature_sets_.find(fsid);
    if (entry == this->feature_sets_.end()) {
        return TE_InvalidArg;
    }

    code = this->validateInfo();
    TE_CHECKRETURN_CODE(code);

    return this->getFeatureSetImpl(featureSet, *entry->second);
}

//private FeatureSet getFeatureSetImpl(FeatureSetDefn defn) {
TAKErr FDB::getFeatureSetImpl(FeatureSetPtr_const &featureSet, const FeatureSetDefn &defn) NOTHROWS
{
    using namespace atakmap::raster::osm;

    featureSet = FeatureSetPtr_const(
        new FeatureSet2(defn.fsid,
                        defn.provider,
                        defn.type,
                        defn.name,
                        OSMUtils::mapnikTileResolution(defn.minLod),
                        OSMUtils::mapnikTileResolution(defn.maxLod),
                        defn.visibleVersion + defn.lodVersion + defn.nameVersion),
        deleter_const<FeatureSet2>);
    return TE_Ok;
}

//protected Collection<FeatureSetDefn> filterNoSync(FeatureSetQueryParameters params, boolean softVisibilityCheck) {
TAKErr FDB::filterNoSync(std::set<std::shared_ptr<const FeatureSetDefn>> &retval, const FeatureSetQueryParameters *params, const bool softVisibilityCheck) NOTHROWS
{
    TAKErr code;
    size_t offsetCountdown = 0;
    size_t limit = SIZE_MAX;
    if (params && params->offset) {
        offsetCountdown = params->offset;
    }

    code = TE_Ok;
    if (params && params->visibleOnly) {
        code = this->validateInfo();
        TE_CHECKRETURN_CODE(code);
    }

    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator entry;
    for (entry = this->feature_sets_.begin(); entry != this->feature_sets_.end(); entry++) {
        std::shared_ptr<FeatureSetDefn> fs = entry->second;

        bool compatible;
        code = isCompatible(&compatible, *fs, params);
        TE_CHECKBREAK_CODE(code);
        if (!compatible)
            continue;
        if (params && params->visibleOnly) {
            if (!fs->visibleCheck && !fs->visible)
                continue;
            if (!softVisibilityCheck && fs->visibleCheck) {
                bool vis;
                code = this->isFeatureSetVisible(&vis, fs->fsid);
                TE_CHECKBREAK_CODE(code);
                if (!vis)
                    continue;
            }
        }
        if (offsetCountdown == 0) {
            retval.insert(fs);
        } else {
            --offsetCountdown;
        }

        if (retval.size() >= limit)
            break;
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}

//protected Collection<FeatureSetDefn> filterNoSync(FeatureQueryParameters params, boolean softChecks) {
TAKErr FDB::filterNoSync(std::set<std::shared_ptr<const FeatureSetDefn>> &retval, const FeatureQueryParameters *params, const bool softChecks) NOTHROWS
{
    using namespace atakmap::raster::osm;

    TAKErr code;

    code = TE_Ok;

    if (params && params->visibleOnly) {
        code = this->validateInfo();
        TE_CHECKRETURN_CODE(code);
    }

    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator fs;
    for (fs = this->feature_sets_.begin(); fs != this->feature_sets_.end(); fs++) {
        bool compatible;
        code = isCompatible(&compatible, *fs->second, params);
        TE_CHECKBREAK_CODE(code);
        if (!compatible)
            continue;

        if (params) {
            if (params->visibleOnly) {
                if (!fs->second->visibleCheck && !fs->second->visible)
                    continue;
                if (!softChecks && fs->second->visibleCheck) {
                    bool vis;
                    code = this->isFeatureSetVisible(&vis, fs->second->fsid);
                    TE_CHECKBREAK_CODE(code);
                    if (!vis)
                        continue;
                }
            }
            if (!isnan(params->minResolution)) {
                const int queryMinLod = OSMUtils::mapnikTileLevel(params->minResolution);
                if (!fs->second->lodCheck && fs->second->maxLod < queryMinLod) {
                    continue;
                }
                else if (fs->second->lodCheck && !softChecks) {
                    std::ostringstream sql;
                    std::list<BindArgument> args;

                    sql << "SELECT 1 FROM features WHERE fsid = ? AND ";
                    args.push_back(BindArgument(fs->second->fsid));

                    if (fs->second->maxLod >= queryMinLod) {
                        sql << "(lod_version != ? OR (lod_version = ? AND max_lod >= ?))";
                        args.push_back(BindArgument(fs->second->lodVersion));
                        args.push_back(BindArgument(fs->second->lodVersion));
                        args.push_back(BindArgument(queryMinLod));
                    }
                    else {
                        sql << "(lod_version = ? AND max_lod >= ?)";
                        args.push_back(BindArgument(fs->second->lodVersion));
                        args.push_back(BindArgument(queryMinLod));
                    }

                    sql << " LIMIT 1";

                    QueryPtr result(nullptr, nullptr);

                    STLListAdapter<BindArgument> argsAdapter(args);
                    code = BindArgument::query(result, *this->database_, sql.str().c_str(), argsAdapter);
                    TE_CHECKBREAK_CODE(code);
                    code = result->moveToNext();
                    if (code == TE_Done) {
                        code = TE_Ok;
                        continue;
                    } else if (code == TE_Ok) {
                        int one;
                        code = result->getInt(&one, 0);
                        TE_CHECKBREAK_CODE(code);
                        if (one < 1)
                            continue;
                    } else {
                        break;
                    }
                }
            }
            if (!isnan(params->maxResolution)) {
                const int queryMaxLod = OSMUtils::mapnikTileLevel(params->maxResolution);
                if (!fs->second->lodCheck && fs->second->minLod > queryMaxLod) {
                    continue;
                }
                else if (fs->second->lodCheck && !softChecks) {
                    std::ostringstream sql;
                    std::list<BindArgument> args;

                    sql << "SELECT 1 FROM features WHERE fsid = ? AND ";
                    args.push_back(BindArgument(fs->second->fsid));

                    if (fs->second->minLod <= queryMaxLod) {
                        sql << "(lod_version != ? OR (lod_version = ? AND min_lod <= ?))";
                        args.push_back(BindArgument(fs->second->lodVersion));
                        args.push_back(BindArgument(fs->second->lodVersion));
                        args.push_back(BindArgument(queryMaxLod));
                    } else {
                        sql << "(lod_version = ? AND min_lod <= ?)";
                        args.push_back(BindArgument(fs->second->lodVersion));
                        args.push_back(BindArgument(queryMaxLod));
                    }

                    sql << " LIMIT 1";

                    QueryPtr result(nullptr, nullptr);

                    STLListAdapter<BindArgument> argsAdapter(args);
                    code = BindArgument::query(result, *this->database_, sql.str().c_str(), argsAdapter);
                    TE_CHECKBREAK_CODE(code);
                    code = result->moveToNext();
                    if (code == TE_Done) {
                        code = TE_Ok;
                        continue;
                    } else if (code == TE_Ok) {
                        int one;
                        code = result->getInt(&one, 0);
                        TE_CHECKBREAK_CODE(code);
                        if (one < 1)
                            continue;
                    } else {
                        break;
                    }

                    result.reset();
                }
            }
        }
        retval.insert(std::const_pointer_cast<const FeatureSetDefn>(fs->second));
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}

//public synchronized FeatureSetCursor queryFeatureSets(FeatureSetQueryParameters params) {
TAKErr FDB::queryFeatureSets(FeatureSetCursorPtr &result) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!this->database_.get()) {
        return TE_IllegalState;
    }

    code = this->validateInfo();
    TE_CHECKRETURN_CODE(code);

    std::vector<std::shared_ptr<const FeatureSetDefn>> retval;

    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator entry;
    for (entry = this->feature_sets_.begin(); entry != this->feature_sets_.end(); entry++)
        retval.push_back(entry->second);

    STLVectorAdapter<std::shared_ptr<const FeatureSetDefn>> retvalAdapter(retval);
    result = FeatureSetCursorPtr(new FeatureSetCursorImpl(*this, retvalAdapter), deleter_const<FeatureSetCursor2, FeatureSetCursorImpl>);

    return code;
}

TAKErr FDB::queryFeatureSets(FeatureSetCursorPtr &result, const FeatureSetQueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!this->database_.get()) {
        return TE_IllegalState;
    }

    std::set<std::shared_ptr<const FeatureSetDefn>> retval;
    code = this->filterNoSync(retval, &params, false);
    STLSetAdapter<std::shared_ptr<const FeatureSetDefn>> retvalAdapter(retval);
    result = FeatureSetCursorPtr(new FeatureSetCursorImpl(*this, retvalAdapter), deleter_const<FeatureSetCursor2, FeatureSetCursorImpl>);

    return code;
}

//public synchronized int queryFeatureSetsCount(FeatureSetQueryParameters params) {
TAKErr FDB::queryFeatureSetsCount(int *value) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    if (!this->database_.get()) {
        return TE_IllegalState;
    }

    *value = static_cast<int>(this->feature_sets_.size());
    return TE_Ok;
}

TAKErr FDB::queryFeatureSetsCount(int *value, const FeatureSetQueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!this->database_.get()) {
        return TE_IllegalState;
    }

    std::set<std::shared_ptr<const FeatureSetDefn>> fs;
    code = this->filterNoSync(fs, &params, false);
    TE_CHECKRETURN_CODE(code);
    *value = static_cast<int>(fs.size());
    return code;
}

//public synchronized boolean isFeatureVisible(int64_t fid) {
TAKErr FDB::isFeatureVisible(bool *value, const int64_t fid) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!this->database_.get()) {
        return TE_IllegalState;
    }

    FeatureQueryParameters params;
    params.featureIds->add(fid);
    params.visibleOnly = true;
    params.limit = 1;

    int cnt;
    code = this->queryFeaturesCount(&cnt, params);
    TE_CHECKRETURN_CODE(code);

    *value = (cnt > 0);
    return code;
}

//public synchronized boolean isFeatureSetVisible(int64_t fsid) {
TAKErr FDB::isFeatureSetVisible(bool *value, const int64_t fsid) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!this->database_.get()) {
        return TE_IllegalState;
    }

    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator featureSet;
    featureSet = this->feature_sets_.find(fsid);
    if (featureSet == this->feature_sets_.end())
        return TE_InvalidArg;

    code = this->validateInfo();
    TE_CHECKRETURN_CODE(code);
    if (!featureSet->second->visibleCheck) {
        *value = featureSet->second->visible;
        return code;
    }

    FeatureQueryParameters params;
    params.featureSetIds->add(fsid);
    params.visibleOnly = true;
    params.limit = 1;

    int cnt;
    code = this->queryFeaturesCount(&cnt, params);
    TE_CHECKRETURN_CODE(code);

    *value = (cnt > 0);
    return code;
}

TAKErr FDB::setFeatureSetReadOnlyImpl(const int64_t fsid, const bool readOnly) NOTHROWS 
{
    int dbVersion;
    database_->getVersion(&dbVersion);
    if (dbVersion != FEATURE_DATABASE_VERSION)
        return TAKErr::TE_Unsupported;

    TAKErr code;
    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator entry;

    entry = this->feature_sets_.find(fsid);
    if (entry == this->feature_sets_.end()) {
        return TE_InvalidArg;
    }

    StatementPtr stmt(nullptr, nullptr);

    code = this->database_->compileStatement(stmt, "UPDATE featuresets SET read_only = ? WHERE id = ?");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(1, readOnly ? 1 : 0);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(2, fsid);
    TE_CHECKRETURN_CODE(code);

    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    entry->second->readOnly = readOnly;

    return code;
}

TAKErr FDB::setFeatureSetsReadOnlyImpl(const FeatureSetQueryParameters &paramsRef, const bool readOnly) NOTHROWS
{
    TAKErr code;

    const FeatureSetQueryParameters *params = &paramsRef;

    std::set<std::shared_ptr<const FeatureSetDefn>> fs;
    code = this->filterNoSync(fs, params, false);
    TE_CHECKRETURN_CODE(code);

    if (fs.empty()) {
        // params not compatible; no-op
        return TE_Ok;
    }

    std::ostringstream sql;
    std::list<BindArgument> args;

    sql << "UPDATE featuresets SET read_only = ?";
    args.push_back(BindArgument(visible_ ? 1 : 0));

    if (params) {
        sql << " WHERE id IN (";

        auto iter = fs.begin();
#if 0
        if (!iter.hasNext())
            throw new IllegalStateException();
#endif
        sql << "?";
        args.push_back(BindArgument((*iter)->fsid));
        iter++;

        while (iter != fs.end()) {
            sql << ", ?";
            args.push_back(BindArgument((*iter)->fsid));
            iter++;
        }
        sql << ")";
    }

    StatementPtr stmt(nullptr, nullptr);

    code = this->database_->compileStatement(stmt, sql.str().c_str());
    TE_CHECKRETURN_CODE(code);

    int idx = 1;

    std::list<BindArgument>::iterator arg;
    for (arg = args.begin(); arg != args.end(); arg++) {
        code = (*arg).bind(*stmt, idx++);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    std::set<std::shared_ptr<const FeatureSetDefn>>::iterator defn_const;
    for (defn_const = fs.begin(); defn_const != fs.end(); defn_const++) {
        // XXX - consider non-const filter variant
        std::shared_ptr<FeatureSetDefn> defn = this->feature_sets_[(*defn_const)->fsid];

        defn->readOnly = readOnly;
    }

    return code;
}

TAKErr FDB::isFeatureSetReadOnly(bool *value, const int64_t fsid) NOTHROWS 
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!this->database_.get()) {
        return TE_IllegalState;
    }

    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator featureSet;
    featureSet = this->feature_sets_.find(fsid);
    if (featureSet == this->feature_sets_.end())
        return TE_InvalidArg;

    code = this->validateInfo();
    TE_CHECKRETURN_CODE(code);

    *value = featureSet->second->readOnly;

    return code;
}

TAKErr FDB::isFeatureReadOnly(bool *value, const int64_t fid) NOTHROWS 
{
    int dbVersion;
    database_->getVersion(&dbVersion);
    if (dbVersion != FEATURE_DATABASE_VERSION)
        return TAKErr::TE_Unsupported;

    TAKErr code;
    QueryPtr query(nullptr, nullptr);

    code = this->database_->compileQuery(query,
                                        "SELECT COUNT(1) FROM features LEFT JOIN featuresets ON features.fsid = featuresets.id WHERE "
                                        "featuresets.read_only = 1 AND features.fid = ?");
    TE_CHECKRETURN_CODE(code);
    code = query->bindLong(1, fid);
    TE_CHECKRETURN_CODE(code);

    code = query->moveToNext();
    if (code == TE_Done)
        return TE_InvalidArg;
    TE_CHECKRETURN_CODE(code);

    if (value) {
        int32_t readOnly;
        code = query->getInt(&readOnly, 0);
        *value = readOnly != 0;
    }

    query.reset();

    return code;
}

//public synchronized boolean isAvailable() {
TAKErr FDB::isAvailable(bool *available) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    *available = !!this->database_.get();
    return TE_Ok;
}

//public synchronized void refresh() {
TAKErr FDB::refresh() NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!this->database_.get()) {
        return TE_IllegalState;
    }

    this->feature_sets_.clear();

    int dbVersion;
    this->database_->getVersion(&dbVersion);

    this->visible_ = false;
    this->visible_check_ = false;
    this->min_lod_ = 0x7FFFFFFF;
    this->max_lod_ = 0;
    this->lod_check_ = false;
    this->read_only_ = false;

    const char *sql = "SELECT"
                      "    id,"
                      "    name, name_version,"
                      "    visible, visible_version, visible_check,"
                      "    min_lod, max_lod, lod_version, lod_check,"
                      "    type, provider, read_only"
                      " FROM featuresets";

    QueryPtr result(nullptr, nullptr);
    code = this->database_->query(result, sql);
    TE_CHECKRETURN_CODE(code);

    do {
        code = result->moveToNext();
        TE_CHECKBREAK_CODE(code);

        std::unique_ptr<FeatureSetDefn> fs(new FeatureSetDefn());

        union {
            const char *s;
            int i;
        } scratch;

        code = result->getLong(&fs->fsid, 0);
        TE_CHECKBREAK_CODE(code);
        code = result->getString(&scratch.s, 1);
        TE_CHECKBREAK_CODE(code);
        fs->name = scratch.s;
        code = result->getInt(&fs->nameVersion, 2);
        TE_CHECKBREAK_CODE(code);
        code = result->getInt(&scratch.i, 3);
        TE_CHECKBREAK_CODE(code);
        fs->visible = !!scratch.i;
        code = result->getInt(&fs->visibleVersion, 4);
        TE_CHECKBREAK_CODE(code);
        code = result->getInt(&scratch.i, 5);
        TE_CHECKBREAK_CODE(code);
        fs->visibleCheck = !!scratch.i;
        code = result->getInt(&fs->minLod, 6);
        TE_CHECKBREAK_CODE(code);
        code = result->getInt(&fs->maxLod, 7);
        TE_CHECKBREAK_CODE(code);
        code = result->getInt(&fs->lodVersion, 8);
        TE_CHECKBREAK_CODE(code);
        code = result->getInt(&scratch.i, 9);
        TE_CHECKBREAK_CODE(code);
        fs->lodCheck = !!scratch.i;
        code = result->getString(&scratch.s, 10);
        TE_CHECKBREAK_CODE(code);
        fs->type = scratch.s;
        code = result->getString(&scratch.s, 11);
        TE_CHECKBREAK_CODE(code);
        fs->provider = scratch.s;
        code = result->getInt(&scratch.i, 12);
        TE_CHECKBREAK_CODE(code);
        fs->readOnly = !!scratch.i;

        this->visible_ |= fs->visible;
        this->visible_check_ |= fs->visibleCheck;

        if (fs->minLod < this->min_lod_)
            this->min_lod_ = fs->minLod;
        if (fs->maxLod > this->max_lod_)
            this->max_lod_ = fs->maxLod;
        this->lod_check_ |= fs->lodCheck;
        this->read_only_ |= fs->readOnly;

        int64_t fsid = fs->fsid;
        this->feature_sets_[fsid] = std::shared_ptr<FeatureSetDefn>(fs.release());
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);

    this->info_dirty_ = false;
    result.reset();

    this->dispatchDataStoreContentChangedNoSync(true);

    return code;
}

//public String getUri() {
TAKErr FDB::getUri(TAK::Engine::Port::String &value) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    if (!this->database_.get())
        return TE_IllegalState;

    value = this->database_file_;
    return TE_Ok;
}

TAKErr FDB::getMaxFeatureVersion(const int64_t fsid, int64_t* version) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!this->database_.get()) {
        return TE_IllegalState;
    }

    QueryPtr result(nullptr, nullptr);

    code = this->database_->query(result, "SELECT max(version) FROM features WHERE fsid = ?");
    TE_CHECKRETURN_CODE(code);
    code = result->bindLong(1, fsid);
    TE_CHECKRETURN_CODE(code);
    code = result->moveToNext();
    if (code == TE_Ok) {
        code = result->getLong(version, 0);
        TE_CHECKRETURN_CODE(code);
    }
    result.reset();

    return code;
}

TAKErr FDB::close() NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    if (!this->database_.get())
        return TE_IllegalState;
    return closeImpl();
}

TAKErr FDB::closeImpl() NOTHROWS
{
    if (this->database_.get()) {
        this->feature_sets_.clear();
        this->attr_schema_dirty_ = true;
        this->id_to_attr_schema_.clear();
        this->info_dirty_ = true;
        this->key_to_attr_schema_.clear();
        this->database_.reset();
    }

    return TE_Ok;
}

//protected boolean setFeatureVisibleImpl(int64_t fid, boolean visible) {
TAKErr FDB::setFeatureVisibleImpl(const int64_t fid, const bool visible) NOTHROWS
{
    TAKErr code;
    StatementPtr stmt(nullptr, nullptr);

    code = this->database_->compileStatement(stmt, "UPDATE features SET visible = ? WHERE fid = ?");
	TE_CHECKRETURN_CODE(code);
	code = stmt->bindInt(1, visible ? 1 : 0);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(2, fid);
    TE_CHECKRETURN_CODE(code);

    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    this->info_dirty_ = true;

    return code;
}

//protected boolean setFeaturesVisibleImpl(FeatureQueryParameters params, boolean visible) {
TAKErr FDB::setFeaturesVisibleImpl(const FeatureQueryParameters &paramsRef, const bool visible) NOTHROWS
{
    TAKErr code;

    const FeatureQueryParameters *params = &paramsRef;

    bool compatible;
    code = this->isCompatible(&compatible, params);
    TE_CHECKRETURN_CODE(code);

    if (params && !compatible) {
        // params weren't compatible, no-op
        return TE_Ok;
    }

    if (!params ||
        (!params->visibleOnly &&
        params->featureIds->empty() &&
        params->featureNames->empty() &&
        !params->spatialFilter.get() &&
        isnan(params->minResolution) &&
        isnan(params->maxResolution))) {

        std::ostringstream sql;
        std::list<BindArgument> args;
        WhereClauseBuilder2 where;

        sql << "UPDATE featuresets SET visible = ?";
        args.push_back(BindArgument(visible ? 1 : 0));

        if (params != nullptr && params->providers->empty() && params->types->empty()) {
            if (!params->featureSetIds->empty()) {
                where.beginCondition();

                std::list<BindArgument> fsids;

                struct longToBindArg
                {
                    BindArgument operator()(const int64_t v) NOTHROWS
                    {
                        return BindArgument(v);
                    }
                };
                {
                STLListAdapter<BindArgument> fsidsAdapter(fsids);
                code = Collections_transmute<int64_t, BindArgument, longToBindArg>(fsidsAdapter, *params->featureSetIds);
                TE_CHECKRETURN_CODE(code);
                }

                {
                STLListAdapter<BindArgument> fsidsAdapter(fsids);
                where.appendIn("id", fsidsAdapter);
                }
            }
            if (!params->featureSets->empty()) {
                where.beginCondition();
                where.appendIn("name", *params->featureSets);
            }
        } else {
            std::set<std::shared_ptr<const FeatureSetDefn>> fs;
            code = this->filterNoSync(fs, params, true);
            TE_CHECKRETURN_CODE(code);

            where.beginCondition();
            std::list<BindArgument> fsids;

            struct fsdefToBindArg
            {
                BindArgument operator()(const std::shared_ptr<const FeatureSetDefn> v) NOTHROWS
                {
                    return BindArgument(v->fsid);
                }
            };
            {
            STLListAdapter<BindArgument> fsidsAdapter(fsids);
            STLSetAdapter<std::shared_ptr<const FeatureSetDefn>> fsAdapter(fs);
            code = Collections_transmute<std::shared_ptr<const FeatureSetDefn>, BindArgument, fsdefToBindArg>(fsidsAdapter, fsAdapter);
            }
            {
            STLListAdapter<BindArgument> fsidsAdapter(fsids);
            where.appendIn("id", fsidsAdapter);
            }
        }

        const char *selection;
         where.getSelection(&selection);
        if (selection) {
            sql << " WHERE ";
            sql << selection;
            STLListAdapter<BindArgument> argsAdapter(args);
            where.getBindArgs(argsAdapter);
        }

        // visibility for all features is being toggled
        StatementPtr stmt(nullptr, nullptr);

        code = this->database_->compileStatement(stmt, sql.str().c_str());
        int idx = 1;
        std::list<BindArgument>::iterator arg;
        for (arg = args.begin(); arg != args.end(); arg++) {
            code = (*arg).bind(*stmt, idx++);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);

        stmt.reset();

        return code;
    } else {
        std::set<std::shared_ptr<const FeatureSetDefn>> fsNoCheck;
        code = this->filterNoSync(fsNoCheck, params, true);
        TE_CHECKRETURN_CODE(code);
        if (fsNoCheck.empty()) {
            // none visible; no-op
            return TE_Ok;
        }

        std::list<std::shared_ptr<const FeatureSetDefn>> fsCheck;
        if (params->visibleOnly) {
            code = this->validateInfo();
            TE_CHECKRETURN_CODE(code);

            auto iter = fsNoCheck.begin();
            std::shared_ptr<const FeatureSetDefn>defn;
            while (iter != fsNoCheck.end()) {
                defn = *iter;
                if (defn->visibleCheck) {
                    fsCheck.push_back(defn);
                    iter = fsNoCheck.erase(iter);
                } else {
                    iter++;
                }
            }
        }
        if (!isnan(params->minResolution) || !isnan(params->maxResolution)) {
            this->validateInfo();

            auto iter = fsNoCheck.begin();
            std::shared_ptr<const FeatureSetDefn>defn;
            while (iter != fsNoCheck.end()) {
                defn = *iter;
                if (defn->lodCheck) {
                    fsCheck.push_back(defn);
                    iter = fsNoCheck.erase(iter);
                } else {
                    iter++;
                }
            }
        }

        if (!fsCheck.empty()) {
            std::list<std::shared_ptr<const FeatureSetDefn>>::iterator fs;
            for (fs = fsCheck.begin(); fs != fsCheck.end(); fs++) {
                std::ostringstream sql;
                std::list<BindArgument> args;
                WhereClauseBuilder2 where;

                sql << "UPDATE features SET visible = ?";
                args.push_back(BindArgument(visible ? 1 : 0));
                bool emptyResults;
                code = this->buildParamsWhereClauseCheck(&emptyResults, *params, **fs, where);
                if (emptyResults)
                    continue;

                sql << " WHERE ";
                const char *selection;
                where.getSelection(&selection);
                sql << selection;

                STLListAdapter<BindArgument> argsAdapter(args);
                where.getBindArgs(argsAdapter);

                StatementPtr stmt(nullptr, nullptr);

                code = this->database_->compileStatement(stmt, sql.str().c_str());
                TE_CHECKRETURN_CODE(code);
                int idx = 1;
                std::list<BindArgument>::iterator arg;
                for (arg = args.begin(); arg != args.end(); arg++) {
                    code = (*arg).bind(*stmt, idx++);
                    TE_CHECKBREAK_CODE(code);
                }
                TE_CHECKRETURN_CODE(code);
                code = stmt->execute();
                TE_CHECKRETURN_CODE(code);

                stmt.reset();
            }
        }

        if (!fsNoCheck.empty()) {
            // if all FS are no-check, clear the set for where building
            if (fsNoCheck.size() == this->feature_sets_.size())
                fsNoCheck.clear();

            do {
                std::ostringstream sql;
                std::list<BindArgument> args;
                WhereClauseBuilder2 where;

                sql << "UPDATE features SET visible = ?";
                args.push_back(BindArgument(visible ? 1 : 0));
                bool emptyResults;
                STLSetAdapter<std::shared_ptr<const FeatureSetDefn>> fsNoCheckAdapter(fsNoCheck);
                code = this->buildParamsWhereClauseNoCheck(&emptyResults, *params, fsNoCheckAdapter, where);
                TE_CHECKBREAK_CODE(code);
                if (emptyResults)
                    continue;

                const char *selection;
                where.getSelection(&selection);
                if (selection) {
                    sql << " WHERE ";
                    sql << selection;
                    STLListAdapter<BindArgument> argsAdapter(args);
                    where.getBindArgs(argsAdapter);
                }

                StatementPtr stmt(nullptr, nullptr);

                code = this->database_->compileStatement(stmt, sql.str().c_str());
                TE_CHECKBREAK_CODE(code);
                int idx = 1;

                std::list<BindArgument>::iterator arg;
                for (arg = args.begin(); arg != args.end(); arg++) {
                    code = (*arg).bind(*stmt, idx++);
                    TE_CHECKBREAK_CODE(code);
                }
                TE_CHECKBREAK_CODE(code);
                code = stmt->execute();
                stmt.reset();
            } while (false);
            TE_CHECKRETURN_CODE(code);
        }

        // some unknown features had visibility toggled; mark info as dirty
        this->info_dirty_ = true;

        return code;
    }
}

//protected boolean setFeatureSetVisibleImpl(int64_t fsid, boolean visible) {
TAKErr FDB::setFeatureSetVisibleImpl(const int64_t fsid, const bool visible) NOTHROWS
{
    TAKErr code;
    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator entry;

    entry = this->feature_sets_.find(fsid);
    if (entry == this->feature_sets_.end()) {
        return TE_InvalidArg;
    }

    StatementPtr stmt(nullptr, nullptr);

    code = this->database_->compileStatement(stmt, "UPDATE featuresets SET visible = ? WHERE id = ?");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(1, visible ? 1 : 0);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(2, fsid);
    TE_CHECKRETURN_CODE(code);

    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    // update the visibility info -- we will leave current clean/dirty state
    // as version may not yet be validated.
    entry->second->visible = visible;
    entry->second->visibleCheck = false;
    entry->second->visibleVersion++;

    return code;
}

//protected boolean setFeatureSetsVisibleImpl(FeatureSetQueryParameters params, boolean visible) {
TAKErr FDB::setFeatureSetsVisibleImpl(const FeatureSetQueryParameters &paramsRef, const bool visible) NOTHROWS
{
    TAKErr code;

    const FeatureSetQueryParameters *params = &paramsRef;

    std::set<std::shared_ptr<const FeatureSetDefn>> fs;
    code = this->filterNoSync(fs, params, false);
    TE_CHECKRETURN_CODE(code);

    if (fs.empty()) {
        // params not compatible; no-op
        return TE_Ok;
    }

    std::ostringstream sql;
    std::list<BindArgument> args;

    sql << "UPDATE featuresets SET visible = ?";
    args.push_back(BindArgument(visible ? 1 : 0));


    if (params) {
        sql << " WHERE id IN (";

        auto iter = fs.begin();
#if 0
        if (!iter.hasNext())
            throw new IllegalStateException();
#endif
        sql << "?";
        args.push_back(BindArgument((*iter)->fsid));
        iter++;

        while (iter != fs.end()) {
            sql << ", ?";
            args.push_back(BindArgument((*iter)->fsid));
            iter++;
        }
        sql << ")";
    }

    StatementPtr stmt(nullptr, nullptr);

    code = this->database_->compileStatement(stmt, sql.str().c_str());
    TE_CHECKRETURN_CODE(code);

    int idx = 1;

    std::list<BindArgument>::iterator arg;
    for (arg = args.begin(); arg != args.end(); arg++) {
        code = (*arg).bind(*stmt, idx++);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    std::set<std::shared_ptr<const FeatureSetDefn>>::iterator defn_const;
    for (defn_const = fs.begin(); defn_const != fs.end(); defn_const++) {
        // XXX - consider non-const filter variant
        std::shared_ptr<FeatureSetDefn> defn = this->feature_sets_[(*defn_const)->fsid];

        defn->visible = visible;
        defn->visibleVersion++;
        defn->visibleCheck = false;
    }

    return code;
}

TAKErr FDB::setFeatureVersionImpl(const int64_t featureID, const int64_t version) NOTHROWS
{
    TAKErr code;
    StatementPtr stmt(nullptr, nullptr);

    code = this->database_->compileStatement(stmt, "UPDATE features SET version = ? WHERE fid = ?");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(1, version);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(2, featureID);
    TE_CHECKRETURN_CODE(code);

    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    this->info_dirty_ = true;

    return code;
}

//protected void beginBulkModificationImpl() {
TAKErr FDB::beginBulkModificationImpl() NOTHROWS
{
    // no-op
    return TE_Ok;
}

//protected boolean endBulkModificationImpl(boolean successful) {
TAKErr FDB::endBulkModificationImpl(const bool successful) NOTHROWS
{
    // no-op
    return TE_Ok;
}


//protected final boolean insertFeatureSetImpl(String provider, String type, String name, double minResolution, double maxResolution, FeatureSet[] returnRef) {
TAKErr FDB::insertFeatureSetImpl(FeatureSetPtr_const *returnRef, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    using namespace atakmap::raster::osm;

    TAKErr code;

    StatementPtr stmt(nullptr, nullptr);
    {
        code = this->database_->compileStatement(stmt,
            "INSERT INTO featuresets"
            "    (name,"
            "     name_version,"
            "     visible,"
            "     visible_version,"
            "     visible_check,"
            "     min_lod,"
            "     max_lod,"
            "     lod_version,"
            "     lod_check,"
            "     type,"
            "     provider,"
            "     read_only)"
            " VALUES (?, 1, 1, 1, 0, ?, ?, 1, 0, ?, ?, 1)");
        TE_CHECKRETURN_CODE(code);

        code = stmt->bindString(1, name);
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindInt(2, OSMUtils::mapnikTileLevel(minResolution));
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindInt(3, OSMUtils::mapnikTileLevel(maxResolution));
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindString(4, type);
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindString(5, provider);
        TE_CHECKRETURN_CODE(code);

        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);
        stmt.reset();
    }

    int64_t fsid;
    code = Databases_lastInsertRowID(&fsid, *this->database_);
    TE_CHECKRETURN_CODE(code);

    std::shared_ptr<FeatureSetDefn> defn(new FeatureSetDefn());
    defn->fsid = fsid;
    defn->name = name;
    defn->nameVersion = 1;
    defn->minLod = OSMUtils::mapnikTileLevel(minResolution);
    defn->maxLod = OSMUtils::mapnikTileLevel(maxResolution);
    defn->lodCheck = false;
    defn->lodVersion = 1;
    defn->type = type;
    defn->provider = provider;
    defn->visible = true;
    defn->visibleCheck = false;
    defn->visibleVersion = 1;

    this->feature_sets_[fsid] = defn;
    if (returnRef) {
        code = this->getFeatureSetImpl(*returnRef, *defn);
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}


//private void insertFeatureSetImpl(int64_t fsid, String provider, String type, String name, double minResolution, double maxResolution) {
TAKErr FDB::insertFeatureSetImpl(const int64_t fsid, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    using namespace atakmap::raster::osm;

    TAKErr code;

    StatementPtr stmt(nullptr, nullptr);

    code = this->database_->compileStatement(stmt,
        "INSERT INTO featuresets"
        "    (id,"
        "     name,"
        "     name_version,"
        "     visible,"
        "     visible_version,"
        "     visible_check,"
        "     min_lod,"
        "     max_lod,"
        "     lod_version,"
        "     lod_check,"
        "     type,"
        "     provider,"
        "     read_only)"
        " VALUES (?, ?, 1, 1, 1, 0, ?, ?, 1, 0, ?, ?, 1)");
    TE_CHECKRETURN_CODE(code);

    code = stmt->bindLong(1, fsid);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(2, name);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(3, OSMUtils::mapnikTileLevel(minResolution));
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(4, OSMUtils::mapnikTileLevel(maxResolution));
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(5, type);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(6, provider);
    TE_CHECKRETURN_CODE(code);

    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    std::shared_ptr<FeatureSetDefn> defn(new FeatureSetDefn());
    defn->fsid = fsid;
    defn->name = name;
    defn->nameVersion = 1;
    defn->minLod = OSMUtils::mapnikTileLevel(minResolution);
    defn->maxLod = OSMUtils::mapnikTileLevel(maxResolution);
    defn->lodCheck = false;
    defn->lodVersion = 1;
    defn->type = type;
    defn->provider = provider;
    defn->visible = true;
    defn->visibleCheck = false;
    defn->visibleVersion = 1;

    this->feature_sets_[fsid] = defn;

    return code;
}

//protected boolean updateFeatureSetImpl(int64_t fsid, String name) {
TAKErr FDB::updateFeatureSetImpl(const int64_t fsid, const char *name) NOTHROWS
{
    TAKErr code;

    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator defn;
    defn = this->feature_sets_.find(fsid);
    if (defn == this->feature_sets_.end()) {
        return TE_InvalidArg;
    }

    StatementPtr stmt(nullptr, nullptr);

    code = this->database_->compileStatement(stmt, "UPDATE featuresets SET name = ? WHERE id = ?");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(1, name);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(2, fsid);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    defn->second->name = name;
    defn->second->nameVersion++;

    return code;
}

//protected boolean updateFeatureSetImpl(int64_t fsid, double minResolution, double maxResolution) {
TAKErr FDB::updateFeatureSetImpl(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS
{
    using namespace atakmap::raster::osm;

    TAKErr code;

    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator defn;
    defn = this->feature_sets_.find(fsid);
    if (defn == this->feature_sets_.end()) {
        return TE_InvalidArg;
    }

    StatementPtr stmt(nullptr, nullptr);

    code = this->database_->compileStatement(stmt, "UPDATE featuresets SET min_lod = ?, max_lod = ? WHERE id = ?");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(1, OSMUtils::mapnikTileLevel(minResolution));
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(2, OSMUtils::mapnikTileLevel(maxResolution));
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(3, fsid);
    TE_CHECKRETURN_CODE(code);

    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    defn->second->minLod = OSMUtils::mapnikTileLevel(minResolution);
    defn->second->maxLod = OSMUtils::mapnikTileLevel(maxResolution);
    defn->second->lodCheck = false;
    defn->second->lodVersion++;

    return code;
}

//protected boolean updateFeatureSetImpl(int64_t fsid, String name, double minResolution, double maxResolution) {
TAKErr FDB::updateFeatureSetImpl(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    using namespace atakmap::raster::osm;

    TAKErr code;

    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator defn;
    defn = this->feature_sets_.find(fsid);
    if (defn == this->feature_sets_.end()) {
        return TE_InvalidArg;
    }

    StatementPtr stmt(nullptr, nullptr);

    code = this->database_->compileStatement(stmt, "UPDATE featuresets SET name = ?, min_lod = ?, max_lod = ? WHERE id = ?");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(1, name);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(2, OSMUtils::mapnikTileLevel(minResolution));
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(3, OSMUtils::mapnikTileLevel(maxResolution));
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(4, fsid);
    TE_CHECKRETURN_CODE(code);

    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    defn->second->name = name;
    defn->second->nameVersion++;
    defn->second->minLod = OSMUtils::mapnikTileLevel(minResolution);
    defn->second->maxLod = OSMUtils::mapnikTileLevel(maxResolution);
    defn->second->lodCheck = false;
    defn->second->lodVersion++;

    return code;
}

TAK::Engine::Util::TAKErr FDB::updateFeatureSetImpl(const int64_t fsid, const char *name, const char *type, const double minResolution, const double maxResolution) NOTHROWS
{
    using namespace atakmap::raster::osm;

    TAK::Engine::Util::TAKErr code;

    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator defn;
    defn = this->feature_sets_.find(fsid);
    if (defn == this->feature_sets_.end()) {
        return Util::TE_InvalidArg;
    }

    DB::StatementPtr stmt(nullptr, nullptr);

    code = this->database_->compileStatement(stmt, "UPDATE featuresets SET name = ?, type = ?, min_lod = ?, max_lod = ? WHERE id = ?");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(1, name);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(2, type);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(3, OSMUtils::mapnikTileLevel(minResolution));
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(4, OSMUtils::mapnikTileLevel(maxResolution));
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(5, fsid);
    TE_CHECKRETURN_CODE(code);

    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    defn->second->name = name;
    defn->second->nameVersion++;
    defn->second->minLod = OSMUtils::mapnikTileLevel(minResolution);
    defn->second->maxLod = OSMUtils::mapnikTileLevel(maxResolution);
    defn->second->lodCheck = false;
    defn->second->lodVersion++;

    return code;
}

//protected boolean deleteFeatureSetImpl(int64_t fsid) {
TAKErr FDB::deleteFeatureSetImpl(const int64_t fsid) NOTHROWS
{
    TAKErr code;
    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator featureSet;

    featureSet = this->feature_sets_.find(fsid);
    if (featureSet == this->feature_sets_.end())
        return TE_InvalidArg;

    StatementPtr stmt(nullptr, nullptr);

    code = this->database_->compileStatement(stmt, "DELETE FROM featuresets WHERE id = ?");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(1, fsid);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    // remove from featureSets
    this->feature_sets_.erase(featureSet);

    return code;
}

//protected boolean deleteAllFeatureSetsImpl() {
TAKErr FDB::deleteAllFeatureSetsImpl() NOTHROWS
{
    TAKErr code;
    this->feature_sets_.clear();

    // delete features first in single statement to avoid the trigger
    code = this->database_->execute("DELETE FROM features", nullptr, 0);
    TE_CHECKRETURN_CODE(code);
    code = this->database_->execute("DELETE FROM featuresets", nullptr, 0);
    TE_CHECKRETURN_CODE(code);

    return code;
}

//protected boolean insertFeatureImpl(int64_t fsid, String name, Geometry geom, Style style, AttributeSet attributes, Feature[] returnRef) {
TAKErr FDB::insertFeatureImpl(FeaturePtr_const *returnRef, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    Feature2 f(
        FeatureDataStore2::FEATURE_ID_NONE, fsid, name, std::move(GeometryPtr_const(&geom, leaker_const<atakmap::feature::Geometry>)),
        altitudeMode, extrude, std::move(StylePtr_const(style, leaker_const<atakmap::feature::Style>)),
        std::move(AttributeSetPtr_const(&attributes, leaker_const<atakmap::util::AttributeSet>)), FeatureDataStore2::FEATURE_VERSION_NONE);
    DefaultFeatureDefinition fDef(f);
	int64_t fid;
    return this->insertFeatureImpl(returnRef, &fid, fsid, fDef);
}

//protected boolean insertFeatureImpl(int64_t fsid, FeatureDefinition def, Feature[] returnRef) {
TAKErr FDB::insertFeatureImpl(FeaturePtr_const *returnRef, int64_t* fid, const int64_t fsid, FeatureDefinition2 &def) NOTHROWS
{
    if (this->feature_sets_.find(fsid) == this->feature_sets_.end())
        return TE_InvalidArg;

    TAKErr code;
    InsertContext ctx;

    code = this->insertFeatureImpl(fid, ctx, fsid, def);
    TE_CHECKRETURN_CODE(code);
    if (returnRef) {
        code = this->getFeature(*returnRef, *fid);
        TE_CHECKRETURN_CODE(code);
    }
    return code;
}

TAKErr FDB::insertFeatureImpl(int64_t *fid, InsertContext &ctx, const int64_t fsid, FeatureDefinition2 &def) NOTHROWS
{
    TAKErr code;

    FeatureDefinition2::RawData rawStyle;
    code = def.getRawStyle(&rawStyle);

    Port::String ogrStyle;
    switch (def.getStyleCoding()) {
    case FeatureDefinition2::StyleOgr:
    {
        ogrStyle = rawStyle.text;
        break;
    }
    case FeatureDefinition2::StyleStyle:
    {
        const auto *style = (const atakmap::feature::Style *)rawStyle.object;
        if (style) {
            try {
                if (style->toOGR(ogrStyle) != TE_Ok)
                    return TE_Err;
            } catch (...) {
                return TE_Err;
            }
        }
        break;
    }
    default:
    {
        const Feature2 *feature;
        code = def.get(&feature);
        TE_CHECKBREAK_CODE(code);

        const atakmap::feature::Style *style = feature->getStyle();
        if (style) {
            try {
                if (style->toOGR(ogrStyle) != TE_Ok)
                    return TE_Err;
            } catch (...) {
                return TE_Err;
            }
        }
        break;
    }
    }
    TE_CHECKRETURN_CODE(code);

    std::map<Port::String, int64_t, Port::StringLess>::iterator entry;

    entry = ctx.styleIds.find(ogrStyle);
    int64_t styleId;
    if (entry == ctx.styleIds.end()) {
        if (!ctx.insertStyleStatement.get()) {
            code = this->database_->compileStatement(ctx.insertStyleStatement, "INSERT INTO styles (coding, value) VALUES ('ogr', ?)");
            TE_CHECKRETURN_CODE(code);
        }

        code = ctx.insertStyleStatement->clearBindings();
        TE_CHECKRETURN_CODE(code);
        code = ctx.insertStyleStatement->bindString(1, ogrStyle);
        TE_CHECKRETURN_CODE(code);

        code = ctx.insertStyleStatement->execute();
        TE_CHECKRETURN_CODE(code);

        // XXX - this was originally contained in a 'finally'
        code = ctx.insertStyleStatement->clearBindings();
        TE_CHECKRETURN_CODE(code);

        code = Databases_lastInsertRowID(&styleId, *this->database_);
        TE_CHECKRETURN_CODE(code);
        ctx.styleIds[ogrStyle] = styleId;
    } else {
        styleId = entry->second;
    }

    AltitudeMode altitudeMode = def.getAltitudeMode();
    double extrude = def.getExtrude();

    int64_t attributesId = 0LL;
    const atakmap::util::AttributeSet *attribs;
    code = def.getAttributes(&attribs);
    TE_CHECKRETURN_CODE(code);
    if (attribs) {
        ctx.codedAttribs.reset();
        code = FDB::encodeAttributes(*this, ctx, *attribs);
        TE_CHECKRETURN_CODE(code);

        const uint8_t *codedAttribs;
        std::size_t codedAttribsLen;
        code = ctx.codedAttribs.get(&codedAttribs, &codedAttribsLen);
        TE_CHECKRETURN_CODE(code);

        if (codedAttribsLen) {
            if (!ctx.insertAttributesStatement.get()) {
                code = this->database_->compileStatement(ctx.insertAttributesStatement, "INSERT INTO attributes (value) VALUES (?)");
                TE_CHECKRETURN_CODE(code);
            }
            code = ctx.insertAttributesStatement->clearBindings();
            TE_CHECKRETURN_CODE(code);
            code = ctx.insertAttributesStatement->bindBlob(1, codedAttribs, codedAttribsLen);
            TE_CHECKRETURN_CODE(code);
            code = ctx.insertAttributesStatement->execute();
            TE_CHECKRETURN_CODE(code);

            // XXX - was previously contained in 'finally'
            code = ctx.insertAttributesStatement->clearBindings();
            TE_CHECKRETURN_CODE(code);

            code = Databases_lastInsertRowID(&attributesId, *this->database_);
            TE_CHECKRETURN_CODE(code);
        }
    }

    Statement2 *stmt(nullptr);

    FeatureDefinition2::RawData rawGeom;
    code = def.getRawGeometry(&rawGeom);
    TE_CHECKRETURN_CODE(code);
    std::string blobString;
    do {
        switch (def.getGeomCoding()) {
        case FeatureDefinition2::GeomGeometry:
        {
            if (!ctx.insertFeatureBlobStatement.get()) {
                code = this->database_->compileStatement(ctx.insertFeatureBlobStatement,
                                                        "INSERT INTO features "
                                                        "(name, "            // 1
                                                        " geometry, "        // 2
                                                        " style_id,"         // 3
                                                        " attribs_id,"       // 4
                                                        " visible,"          // 5
                                                        " visible_version,"  // 6
                                                        " min_lod,"          // 7
                                                        " max_lod,"          // 8
                                                        " lod_version, "     // 9
                                                        " version, "         // 10
                                                        " fsid, "            // 11
                                                        " altitude_mode, "   // 12
                                                        " extrude)"          // 13
                                                        " VALUES (?, ?, ?, ?, 1, 0, 0, ?, 0, 1, ?, ?, ?)");
                
                TE_CHECKBREAK_CODE(code);
                continue;
            }
            if (code == TE_Ok) {
                stmt = ctx.insertFeatureBlobStatement.get();
                const auto *geom = static_cast<const atakmap::feature::Geometry *>(rawGeom.object);
                std::ostringstream strm;
                geom->toBlob(strm);
                blobString = strm.str();
                ctx.insertGeomArg.set(reinterpret_cast<const uint8_t *>(blobString.c_str()), blobString.size());
            }
            break;
        }
        case FeatureDefinition2::GeomBlob:
        {
            if (!ctx.insertFeatureBlobStatement.get()) {
                code = this->database_->compileStatement(ctx.insertFeatureBlobStatement,
                                                        "INSERT INTO features "
                                                        "(name, "            // 1
                                                        " geometry, "        // 2
                                                        " style_id,"         // 3
                                                        " attribs_id,"       // 4
                                                        " visible,"          // 5
                                                        " visible_version,"  // 6
                                                        " min_lod,"          // 7
                                                        " max_lod,"          // 8
                                                        " lod_version, "     // 9
                                                        " version, "         // 10
                                                        " fsid, "            // 11
                                                        " altitude_mode, "   // 12
                                                        " extrude)"          // 13
                                                        " VALUES (?, ?, ?, ?, 1, 0, 0, ?, 0, 1, ?, ?, ?)");
                
                TE_CHECKBREAK_CODE(code);
                continue;
            }
            if (code == TE_Ok) {
                stmt = ctx.insertFeatureBlobStatement.get();
                ctx.insertGeomArg.set(rawGeom.binary.value, rawGeom.binary.len);
            }
            break;
        }
        case FeatureDefinition2::GeomWkb:
        {
            if (!ctx.insertFeatureWkbStatement.get()) {
                code = this->database_->compileStatement(ctx.insertFeatureWkbStatement,
                                                        "INSERT INTO features "
                                                        "(name, "            // 1
                                                        " geometry, "        // 2
                                                        " style_id,"         // 3
                                                        " attribs_id,"       // 4
                                                        " visible,"          // 5
                                                        " visible_version,"  // 6
                                                        " min_lod,"          // 7
                                                        " max_lod,"          // 8
                                                        " lod_version, "     // 9
                                                        " version, "         // 10
                                                        " fsid, "            // 11
                                                        " altitude_mode, "   // 12
                                                        " extrude)"          // 13
                                                        " VALUES (?, GeomFromWKB(?, 4326), ?, ?, 1, 0, 0, ?, 0, 1, ?, ?, ?)");
                
                TE_CHECKBREAK_CODE(code);
                continue;
            }
            if (code == TE_Ok) {
                stmt = ctx.insertFeatureWkbStatement.get();
                ctx.insertGeomArg.set(rawGeom.binary.value, rawGeom.binary.len);
            }
            break;
        }
        case FeatureDefinition2::GeomWkt:
        {
            if (!ctx.insertFeatureWktStatement.get()) {
                code = this->database_->compileStatement(ctx.insertFeatureWktStatement,
                                                        "INSERT INTO features "
                                                        "(name, "            // 1
                                                        " geometry, "        // 2
                                                        " style_id,"         // 3
                                                        " attribs_id,"       // 4
                                                        " visible,"          // 5
                                                        " visible_version,"  // 6
                                                        " min_lod,"          // 7
                                                        " max_lod,"          // 8
                                                        " lod_version, "     // 9
                                                        " version, "         // 10
                                                        " fsid, "            // 11
                                                        " altitude_mode, "   // 12
                                                        " extrude)"          // 13
                                                        " VALUES (?, GeomFromText(?, 4326), ?, ?, 1, 0, 0, ?, 0, 1, ?, ?, ?)");
                TE_CHECKBREAK_CODE(code);
                continue;
            }
            if (code == TE_Ok) {
                stmt = ctx.insertFeatureWktStatement.get();
                ctx.insertGeomArg.set(rawGeom.text);
            }
            break;
        }
        default:
        {
            code = TE_IllegalState;
            break;
        }
        }
        TE_CHECKBREAK_CODE(code);
    } while (!stmt);
    TE_CHECKRETURN_CODE(code);

    code = stmt->clearBindings();
    TE_CHECKRETURN_CODE(code);

    int idx = 1;
    // name
    const char *fname;
    code = def.getName(&fname);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(idx++, fname);
    TE_CHECKRETURN_CODE(code);
    // geometry
    code = ctx.insertGeomArg.bind(*stmt, idx++);
    TE_CHECKRETURN_CODE(code);
    // style ID
    if (styleId > 0LL)
        code = stmt->bindLong(idx++, styleId);
    else
        code = stmt->bindNull(idx++);
    TE_CHECKRETURN_CODE(code);
    // attributes ID
    if (attributesId > 0LL)
        code = stmt->bindLong(idx++, attributesId);
    else
        code = stmt->bindNull(idx++);
    TE_CHECKRETURN_CODE(code);
    // maximum LOD
    code = stmt->bindInt(idx++, 0x7FFFFFFF);
    TE_CHECKRETURN_CODE(code);
    // FSID
    code = stmt->bindLong(idx++, fsid);
    TE_CHECKRETURN_CODE(code);
    // altitude mode
    code = stmt->bindInt(idx++, altitudeMode);
    TE_CHECKRETURN_CODE(code);
    // extrude
    code = stmt->bindDouble(idx++, extrude);
    TE_CHECKRETURN_CODE(code);

    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    // XXX - previously contained in 'finally'
    code = stmt->clearBindings();
    TE_CHECKRETURN_CODE(code);

    code = Databases_lastInsertRowID(fid, *this->database_);
    TE_CHECKRETURN_CODE(code);

    return code;
}

//protected boolean updateFeatureImpl(int64_t fid, String name) {
TAKErr FDB::updateFeatureImpl(const int64_t fid, const char *name) NOTHROWS
{
    TAKErr code;
    StatementPtr stmt(nullptr, nullptr);

    code = this->database_->compileStatement(stmt, "UPDATE features SET name = ?, version = (version+1) WHERE fid = ?");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(1, name);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(2, fid);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    return code;
}

//protected boolean updateFeatureImpl(int64_t fid, Geometry geom) {
TAKErr FDB::updateFeatureImpl(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS
{
    TAKErr code;
    BlobPtr wkb(nullptr, nullptr);

    code = LegacyAdapters_toWkb(wkb, geom);
    TE_CHECKRETURN_CODE(code);

    StatementPtr stmt(nullptr, nullptr);

    code = this->database_->compileStatement(stmt, "UPDATE features SET geometry = GeomFromWkb(?, 4326), version = (version + 1) WHERE fid = ?");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindBlob(1, wkb->first, (wkb->second - wkb->first));
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(2, fid);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECK_CODE_LOG_DB_ERRMSG(code, this->database_);
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    return code;
}

TAKErr FDB::updateFeatureImpl(const int64_t fid, const TAK::Engine::Feature::AltitudeMode altitudeMode, double extrude) NOTHROWS 
{
    TAKErr code;

    StatementPtr stmt(nullptr, nullptr);

    code = this->database_->compileStatement(stmt, "UPDATE features SET altitude_mode = ?, extrude = ?, version = (version+1) WHERE fid = ?");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(1, altitudeMode);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindDouble(2, extrude);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(3, fid);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    return code;
}

//protected boolean updateFeatureImpl(int64_t fid, Style style) {
TAKErr FDB::updateFeatureImpl(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS
{
    TAKErr code;

    int64_t styleId;
    StatementPtr stmt(nullptr, nullptr);
    if (style) {
        Port::String ogrStyle;
        try {
            if (style->toOGR(ogrStyle) != TE_Ok)
                return TE_Err;
        } catch (...) {
            return TE_Err;
        }

        stmt.reset();

        code = this->database_->compileStatement(stmt, "INSERT INTO styles (coding, value) VALUES('ogr', ?)");
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindString(1, ogrStyle);
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);

        stmt.reset();

        code = Databases_lastInsertRowID(&styleId, *this->database_);
        TE_CHECKRETURN_CODE(code);
    } else {
        styleId = 0LL;
    }

    stmt.reset();

    code = this->database_->compileStatement(stmt, "UPDATE features SET style_id = ?, version = (version + 1) WHERE fid = ?");
    TE_CHECKRETURN_CODE(code);
    if (styleId > 0LL)
        code = stmt->bindLong(1, styleId);
    else
        code = stmt->bindNull(1);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(2, fid);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    return code;
}

//protected boolean updateFeatureImpl(int64_t fid, AttributeSet attributes) {
TAKErr FDB::updateFeatureImpl(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    TAKErr code;

    InsertContext ctx;

    code = encodeAttributes(*this, ctx, attributes);
    TE_CHECKRETURN_CODE(code);

    const uint8_t *blob;
    std::size_t blobLen;
    code = ctx.codedAttribs.get(&blob, &blobLen);
    TE_CHECKRETURN_CODE(code);

    StatementPtr stmt(nullptr, nullptr);

    stmt.reset();

    QueryPtr result(nullptr, nullptr);
    code = this->database_->compileQuery(result, "SELECT attribs_id FROM features WHERE fid = ?");
    TE_CHECKRETURN_CODE(code);
    code = result->bindLong(1u, fid);
    TE_CHECKRETURN_CODE(code);
    code = result->moveToNext();
    if (code == TE_Done)
        return TE_InvalidArg;
    TE_CHECKRETURN_CODE(code);

    int64_t currentAttribsId;
    code = result->getLong(&currentAttribsId, 0u);
    TE_CHECKRETURN_CODE(code);

    int64_t attribsId;
    if (currentAttribsId == 0LL) {
        stmt.reset();
        code = this->database_->compileStatement(stmt, "INSERT INTO attributes (value) VALUES(?)");
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindBlob(1, blob, blobLen);
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);

        stmt.reset();
        
        code = Databases_lastInsertRowID(&attribsId, *this->database_);
        TE_CHECKRETURN_CODE(code);
    } else {
        stmt.reset();
        code = this->database_->compileStatement(stmt, "UPDATE attributes SET value = ? WHERE id = ?");
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindBlob(1, blob, blobLen);
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindLong(2, currentAttribsId);
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);

        stmt.reset();
        
        attribsId = currentAttribsId;
    }

    code = this->database_->compileStatement(stmt, "UPDATE features SET attribs_id = ?, version = (version + 1) WHERE fid = ?");
    TE_CHECKRETURN_CODE(code);

    code = stmt->bindLong(1, attribsId);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(2, fid);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    return code;
}

//protected boolean updateFeatureImpl(int64_t fid, String name, Geometry geom, Style style, AttributeSet attributes) {
TAKErr FDB::updateFeatureImpl(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    TAKErr code;

    BlobPtr wkb(nullptr, nullptr);
    code = LegacyAdapters_toWkb(wkb, geom);
    TE_CHECKRETURN_CODE(code);

    InsertContext ctx;

    code = encodeAttributes(*this, ctx, attributes);
    TE_CHECKRETURN_CODE(code);

    const uint8_t *attribsBlob;
    std::size_t attribsBlobLen;
    code = ctx.codedAttribs.get(&attribsBlob, &attribsBlobLen);
    TE_CHECKRETURN_CODE(code);

    StatementPtr stmt(nullptr, nullptr);

    int64_t styleId;
    if (style) {
        TAK::Engine::Port::String ogrStyle;
        try {
            if (style->toOGR(ogrStyle) != TE_Ok)
                return TE_Err;
        } catch (...) {
            return TE_Err;
        }

        stmt.reset();

        code = this->database_->compileStatement(stmt, "INSERT INTO styles(coding,value) VALUES('ogr', ?)");
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindString(1, ogrStyle);
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);

        stmt.reset();

        code = Databases_lastInsertRowID(&styleId, *this->database_);
        TE_CHECKRETURN_CODE(code);
    } else {
        styleId = 0LL;
    }
    
    QueryPtr result(nullptr, nullptr);
    code = this->database_->compileQuery(result, "SELECT attribs_id FROM features WHERE fid = ?");
    TE_CHECKRETURN_CODE(code);
    code = result->bindLong(1u, fid);
    TE_CHECKRETURN_CODE(code);
    code = result->moveToNext();
    if (code == TE_Done)
        return TE_InvalidArg;
    TE_CHECKRETURN_CODE(code);

    int64_t currentAttribsId;
    code = result->getLong(&currentAttribsId, 0u);
    TE_CHECKRETURN_CODE(code);

    int64_t attribsId;
    if (currentAttribsId == 0LL) {
        stmt.reset();
        code = this->database_->compileStatement(stmt, "INSERT INTO attributes (value) VALUES(?)");
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindBlob(1, attribsBlob, attribsBlobLen);
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);

        stmt.reset();
        
        code = Databases_lastInsertRowID(&attribsId, *this->database_);
        TE_CHECKRETURN_CODE(code);
    } else {
        stmt.reset();
        code = this->database_->compileStatement(stmt, "UPDATE attributes SET value = ? WHERE id = ?");
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindBlob(1, attribsBlob, attribsBlobLen);
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindLong(2, currentAttribsId);
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);

        stmt.reset();
        
        attribsId = currentAttribsId;
    }

    stmt.reset();

    code = this->database_->compileStatement(stmt, "UPDATE features SET name = ?, geometry = GeomFromWkb(?, 4326), style_id = ?, attribs_id = ?, version = version + 1 WHERE fid = ?");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(1, name);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindBlob(2, wkb->first, (wkb->second-wkb->first));
    TE_CHECKRETURN_CODE(code);
    if (styleId > 0LL)
        code = stmt->bindLong(3, styleId);
    else
        code = stmt->bindNull(3);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(4, attribsId);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(5, fid);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    return code;
}

//protected boolean deleteFeatureImpl(int64_t fid) {
TAKErr FDB::deleteFeatureImpl(const int64_t fid) NOTHROWS
{
    TAKErr code;
    StatementPtr stmt(nullptr, nullptr);

    code = this->database_->compileStatement(stmt, "DELETE FROM features WHERE fid = ?");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(1, fid);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    // XXX -
    //return (Databases.lastChangeCount(this->database)>0);
    return code;
}

//protected boolean deleteAllFeaturesImpl(int64_t fsid) {
TAKErr FDB::deleteAllFeaturesImpl(const int64_t fsid) NOTHROWS
{
    TAKErr code;

    if (this->feature_sets_.find(fsid) == this->feature_sets_.end())
        return TE_InvalidArg;

    StatementPtr stmt(nullptr, nullptr);

    code = this->database_->compileStatement(stmt, "DELETE FROM features WHERE fsid = ?");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(1, fsid);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    // XXX -
    //return (Databases.lastChangeCount(this->database)>0);
    return code;
}

/**************************************************************************/

//private boolean buildParamsWhereClauseNoCheck(FeatureQueryParameters params, Collection<FeatureSetDefn> fs, WhereClauseBuilder whereClause) {
TAKErr FDB::buildParamsWhereClauseNoCheck(bool *emptyResults, const FeatureQueryParameters &params, Collection<std::shared_ptr<const FeatureSetDefn>> &fs, WhereClauseBuilder2 &whereClause) NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    bool indexedSpatialFilter = !!params.spatialFilter.get();

    if (!fs.empty()) {
        whereClause.beginCondition();
        struct FsidTransmute
        {
            BindArgument operator()(const std::shared_ptr<const FeatureSetDefn> &arg)
            {
                return BindArgument(arg->fsid);
            }
        };

        std::list<BindArgument> args;
        {
        STLListAdapter<BindArgument> argsAdapter(args);
        code = Collections_transmute<std::shared_ptr<const FeatureSetDefn>, BindArgument, FsidTransmute>(argsAdapter, fs);
        TE_CHECKRETURN_CODE(code);
        }

        {
        STLListAdapter<BindArgument> argsAdapter(args);
        whereClause.appendIn("features.fsid", argsAdapter);
        }
    }


    if (!isnan(params.maxResolution) || !isnan(params.minResolution)) {
        // handled upstream
    }
    if (params.visibleOnly) {
        // handled upstream
    }
    if (!params.providers->empty()) {
        // handled upstream
    }
    if (!params.types->empty()) {
        // handled upstream
    }
    if (!params.featureSets->empty()) {
        // handled upstream
    }
    if (!params.featureSetIds->empty()) {
        // handled upstream
    }
    if (!params.featureNames->empty()) {
        whereClause.beginCondition();
        struct NameTransmute
        {
            BindArgument operator()(const TAK::Engine::Port::String &arg)
            {
                BindArgument retval(arg);
                retval.own();
                return retval;
            }
        };

        std::list<BindArgument> args;
        {
        STLListAdapter<BindArgument> argsAdapter(args);
        code = Collections_transmute<TAK::Engine::Port::String, BindArgument, NameTransmute>(argsAdapter, *params.featureNames);
        TE_CHECKRETURN_CODE(code);
        }

        {
        STLListAdapter<BindArgument> argsAdapter(args);
        whereClause.appendIn("features.name", argsAdapter);
        }
    }
    if (!params.featureIds->empty()) {
        whereClause.beginCondition();
        struct IdTransmute
        {
            BindArgument operator()(const int64_t &arg)
            {
                return BindArgument(arg);
            }
        };
        std::list<BindArgument> args;
        {
        STLListAdapter<BindArgument> argsAdapter(args);
        code = Collections_transmute<int64_t, BindArgument, IdTransmute>(argsAdapter, *params.featureIds);
        TE_CHECKRETURN_CODE(code);
        }

        {
        STLListAdapter<BindArgument> argsAdapter(args);
        whereClause.appendIn("features.fid", argsAdapter);
        }
    }
    if (params.spatialFilter.get()) {
        code = appendSpatialFilter(*params.spatialFilter,
            whereClause,
            indexedSpatialFilter &&
            this->spatial_index_enabled_);
        TE_CHECKRETURN_CODE(code);
    }

    *emptyResults = false;
    return code;
}

//private boolean buildParamsWhereClauseCheck(FeatureQueryParameters params, FeatureSetDefn defn, WhereClauseBuilder whereClause) {
TAKErr FDB::buildParamsWhereClauseCheck(bool *emptyResults, const FeatureQueryParameters &params, const FeatureSetDefn &defn, WhereClauseBuilder2 &whereClause) NOTHROWS
{
    using namespace atakmap::raster::osm;

    TAKErr code;

    code = TE_Ok;

    bool indexedSpatialFilter = !!params.spatialFilter.get();

    if (!isnan(params.maxResolution) || !isnan(params.minResolution)) {
        code = this->validateInfo();
        TE_CHECKRETURN_CODE(code);

        if (defn.lodCheck) {
            // restrict to groups that may be visible -- this will be groups
            // that are explicitly marked as visible or have the visible_check
            // flag toggled

            if (!isnan(params.minResolution)) {
                const int queryMinLod = OSMUtils::mapnikTileLevel(params.minResolution);
                whereClause.beginCondition();
                if (defn.maxLod >= queryMinLod) {
                    whereClause.append("(lod_version != ? OR (lod_version = ? AND max_lod >= ?))");
                    whereClause.addArg(defn.lodVersion);
                    whereClause.addArg(defn.lodVersion);
                    whereClause.addArg(queryMinLod);
                }
                else {
                    whereClause.append("(lod_version = ? AND max_lod >= ?)");
                    whereClause.addArg(defn.lodVersion);
                    whereClause.addArg(queryMinLod);
                }
            }
            if (!isnan(params.maxResolution)) {
                const int queryMaxLod = OSMUtils::mapnikTileLevel(params.maxResolution);
                whereClause.beginCondition();
                if (defn.minLod <= queryMaxLod) {
                    whereClause.append("(lod_version != ? OR (lod_version = ? AND min_lod <= ?))");
                    whereClause.addArg(defn.lodVersion);
                    whereClause.addArg(defn.lodVersion);
                    whereClause.addArg(queryMaxLod);
                }
                else {
                    whereClause.append("(lod_version = ? AND min_lod <= ?)");
                    whereClause.addArg(defn.lodVersion);
                    whereClause.addArg(queryMaxLod);
                }

                // XXX - make this more dynamic
                indexedSpatialFilter &= (queryMaxLod>2);
            }
        }
        else {
            if (!isnan(params.minResolution)) {
                const int queryMinLod = OSMUtils::mapnikTileLevel(params.minResolution);
                if (defn.maxLod < queryMinLod) {
                    *emptyResults = true;
                    return code;
                }
            }
            if (!isnan(params.maxResolution)) {
                const int queryMaxLod = OSMUtils::mapnikTileLevel(params.maxResolution);
                if (defn.minLod > queryMaxLod) {
                    *emptyResults = true;
                    return code;
                }

                // XXX - make this more dynamic
                indexedSpatialFilter &= (queryMaxLod>2);
            }
        }
    }

    if (params.visibleOnly) {
        code = this->validateInfo();
        TE_CHECKRETURN_CODE(code);
        if (defn.visibleCheck) {
            whereClause.beginCondition();
            if (defn.visible) {
                whereClause.append("(features.visible_version != ? OR (features.visible_version = ? AND features.visible = 1))");
                whereClause.addArg(defn.visibleVersion);
                whereClause.addArg(defn.visibleVersion);
            }
            else {
                whereClause.append("(features.visible_version = ? AND features.visible = 1)");
                whereClause.addArg(defn.visibleVersion);
            }
        }
        else if (!defn.visible) {
            *emptyResults = true;
            return code;
        } // else - no check required and no features deviate from global
    }

    if (!params.providers->empty()) {
        // handled upstream
    }
    if (!params.types->empty()) {
        // handled upstream
    }
    if (!params.featureSets->empty()) {
        // handled upstream
    }
    if (!params.featureSetIds->empty()) {
        // handled upstream
    }
    if (!params.featureNames->empty()) {
        whereClause.beginCondition();
        struct NameTransmute
        {
            BindArgument operator()(const TAK::Engine::Port::String &arg)
            {
                BindArgument retval(arg);
                retval.own();
                return retval;
            }
        };
        std::list<BindArgument> args;
        {
        STLListAdapter<BindArgument> argsAdapter(args);
        code = Collections_transmute<TAK::Engine::Port::String, BindArgument, NameTransmute>(argsAdapter, *params.featureNames);
        TE_CHECKRETURN_CODE(code);
        }

        {
        STLListAdapter<BindArgument> argsAdapter(args);
        whereClause.appendIn("features.name", argsAdapter);
        }
    }
    if (!params.featureIds->empty()) {
        whereClause.beginCondition();
        struct IdTransmute
        {
            BindArgument operator()(const int64_t &arg)
            {
                return BindArgument(arg);
            }
        };
        std::list<BindArgument> args;
        {
        STLListAdapter<BindArgument> argsAdapter(args);
        code = Collections_transmute<int64_t, BindArgument, IdTransmute>(argsAdapter, *params.featureIds);
        TE_CHECKRETURN_CODE(code);
        }

        {
        STLListAdapter<BindArgument> argsAdapter(args);
        whereClause.appendIn("features.fid", argsAdapter);
        }
    }
    if (params.spatialFilter.get()) {
        code = appendSpatialFilter(*params.spatialFilter,
            whereClause,
            indexedSpatialFilter &&
            this->spatial_index_enabled_);
        TE_CHECKRETURN_CODE(code);
    }

    whereClause.beginCondition();
    whereClause.append("fsid = ?");
    whereClause.addArg(defn.fsid);

    *emptyResults = false;
    return code;
}

/**************************************************************************/

//private static AttributeSpec insertAttrSchema(InsertContext ctx, DatabaseIface database, String key, AttributeSet metadata) {
TAKErr FDB::insertAttrSchema(std::shared_ptr<AttributeSpec> &retval, InsertContext &ctx, Database2 &database, const char *key, const atakmap::util::AttributeSet &metadata) NOTHROWS
{
    using namespace atakmap::util;

    TAKErr code;

    if (!metadata.containsAttribute(key))
        return TE_InvalidArg;

    AttributeSet::Type type = metadata.getAttributeType(key);
    std::map<AttributeSet::Type, int>::iterator typeCode;
    typeCode = ATTRIB_TYPES.find(type);
    if (typeCode == ATTRIB_TYPES.end()) {
        Logger::log(Logger::Warning, ABS_TAG ": Skipping attribute %s with unsupported type %d", key, type);
        // XXX - java warned and returned null
        return TE_InvalidArg;
    }

    if (!ctx.insertAttributeSchemaStatement.get()) {
        code = database.compileStatement(ctx.insertAttributeSchemaStatement, "INSERT INTO attribs_schema (name, coding) VALUES (?, ?)");
        TE_CHECKRETURN_CODE(code);
    }
    code = ctx.insertAttributeSchemaStatement->bindString(1, key);
    TE_CHECKRETURN_CODE(code);
    code = ctx.insertAttributeSchemaStatement->bindInt(2, typeCode->second);
    TE_CHECKRETURN_CODE(code);
    code = ctx.insertAttributeSchemaStatement->execute();
    TE_CHECKRETURN_CODE(code);
    code = ctx.insertAttributeSchemaStatement->clearBindings();
    TE_CHECKRETURN_CODE(code);

    // XXX - executed in finally
#if 0
    if (ctx.insertAttributeSchemaStatement != null)
            ctx.insertAttributeSchemaStatement.clearBindings();
#endif

    int64_t attribSchemaId;
    code = Databases_lastInsertRowID(&attribSchemaId, database);
    TE_CHECKRETURN_CODE(code);
    retval = std::shared_ptr<AttributeSpec>(new AttributeSpec(key, attribSchemaId, typeCode->second));

    return code;
}

//protected static void encodeAttributes(FDB impl, InsertContext ctx, AttributeSet metadata) {
TAKErr FDB::encodeAttributes(FDB &impl, InsertContext &ctx, const atakmap::util::AttributeSet &metadata) NOTHROWS
{
    using namespace atakmap::util;

    TAKErr code;

    code = ctx.codedAttribs.reset();
    TE_CHECKRETURN_CODE(code);

    DynamicOutput &dos = ctx.codedAttribs;

    std::vector<const char *> keys = metadata.getAttributeNames();

    code = dos.writeInt(1); // version
    TE_CHECKRETURN_CODE(code);
    code = dos.writeInt(static_cast<int32_t>(keys.size())); // number of entries
    TE_CHECKRETURN_CODE(code);

    std::vector<const char *>::iterator key;
    for (key = keys.begin(); key != keys.end(); key++) {
        AttributeSpec *schemaSpec;
        KeyAttrSchemaMap::iterator schemaSpecEntry;
        schemaSpecEntry = impl.key_to_attr_schema_.find(*key);
        if (schemaSpecEntry == impl.key_to_attr_schema_.end()) {
            std::shared_ptr<AttributeSpec> spec;
            code = insertAttrSchema(spec, ctx, *impl.database_, *key, metadata);
            TE_CHECKBREAK_CODE(code);

            impl.key_to_attr_schema_[*key] = spec;
            impl.id_to_attr_schema_[spec->id] = spec;

            schemaSpec = spec.get();
        } else {
            AttributeSet::Type type = metadata.getAttributeType(*key);
            auto typeCode = ATTRIB_TYPES.find(type);
            if (typeCode == ATTRIB_TYPES.end()) {
                Logger::log(Logger::Warning, ABS_TAG  ": Skipping attribute %s with unsupported type %d", key, type);
                continue;
            }

            schemaSpec = schemaSpecEntry->second.get();

            // add a secondary type for the key as a new schema row
            if (schemaSpec->type != typeCode->second) {
                std::map<int, std::shared_ptr<AttributeSpec>>::iterator secondarySchema;
                secondarySchema = schemaSpec->secondaryDefs.find(typeCode->second);
                if (secondarySchema == schemaSpec->secondaryDefs.end()) {
                    std::shared_ptr<AttributeSpec> secondarySpec;
                    code = insertAttrSchema(secondarySpec, ctx, *impl.database_, *key, metadata);
                    TE_CHECKBREAK_CODE(code);

                    schemaSpec->secondaryDefs[typeCode->second]  = secondarySpec;
                    impl.id_to_attr_schema_[secondarySpec->id] = secondarySpec;

                    schemaSpec = secondarySpec.get();
                }
            }
        }

        code = dos.writeInt((int)schemaSpec->id);
        TE_CHECKBREAK_CODE(code);
        if (schemaSpec->type != 5) {
            code = schemaSpec->coder.encode(dos, metadata, *key);
            TE_CHECKBREAK_CODE(code);
        } else {
            // recurse
            InsertContext ctx2;
            code = encodeAttributes(impl, ctx2, metadata.getAttributeSet(*key));
            TE_CHECKBREAK_CODE(code);
            const uint8_t *recurse;
            std::size_t recurseLen;
            code = ctx2.codedAttribs.get(&recurse, &recurseLen);
            TE_CHECKBREAK_CODE(code);
            code = dos.write(recurse, recurseLen);
            TE_CHECKBREAK_CODE(code);
        }
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}

//private static AttributeSet decodeAttributes(byte[] attribsBlob, Map<Long, AttributeSpec> schema) {
TAKErr FDB::decodeAttributes(AttributeSetPtr_const &result, const uint8_t *blob, const std::size_t blobLen, FDB::IdAttrSchemaMap &schema) NOTHROWS
{
    TAKErr code;

    if (!blob) {
        result = AttributeSetPtr_const(new atakmap::util::AttributeSet(), deleter_const<atakmap::util::AttributeSet>);
        return TE_Ok;
    }

    std::unique_ptr<MemoryInput2> dis(new MemoryInput2());
    code = dis->open(blob, blobLen);
    TE_CHECKRETURN_CODE(code);

    code = decodeAttributesImpl(result, *dis, schema);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr FDB::decodeAttributesImpl(AttributeSetPtr_const &result , DataInput2 &dis, FDB::IdAttrSchemaMap &schema) NOTHROWS
{
    using namespace atakmap::util;

    TAKErr code;

    int version;
    code = dis.readInt(&version);
    TE_CHECKRETURN_CODE(code);

    if (version != 1) {
        Logger::log(Logger::Error, ABS_TAG ": Bad AttributeSet coding version: %d", version);
        return TE_InvalidArg;
    }

    int numKeys;
    code = dis.readInt(&numKeys); // number of entries
    TE_CHECKRETURN_CODE(code);

    std::unique_ptr<AttributeSet> retval(new AttributeSet());


    for (int i = 0; i < numKeys; i++) {
        int schemaSpecId;
        code = dis.readInt(&schemaSpecId);
        TE_CHECKBREAK_CODE(code);

        IdAttrSchemaMap::iterator schemaSpec;
        schemaSpec = schema.find(schemaSpecId);
        if (schemaSpec == schema.end()) {
            Logger::log(Logger::Error, ABS_TAG ": Unable to located AttributeSpec schema ID %d", schemaSpecId);
            return TE_InvalidArg;
        }

        if (schemaSpec->second->type != 5) {
            code = schemaSpec->second->coder.decode(*retval, dis, schemaSpec->second->key);
            TE_CHECKBREAK_CODE(code);
        } else {
            AttributeSetPtr_const nested(nullptr, nullptr);
            code = decodeAttributesImpl(nested, dis, schema);
            TE_CHECKBREAK_CODE(code);
            retval->setAttributeSet(schemaSpec->second->key, *nested);
            nested.reset();
        }
    }
    TE_CHECKRETURN_CODE(code);

    result = AttributeSetPtr_const(retval.release(), Memory_deleter_const<atakmap::util::AttributeSet>);

    return code;
}

/*****************************************************************/
// AttributeSpec

std::map<atakmap::util::AttributeSet::Type, FDB::AttributeCoder> FDB::AttributeSpec::CLAZZ_TO_CODER;
std::map<int, FDB::AttributeCoder> FDB::AttributeSpec::TYPECODE_TO_CODER;

FDB::AttributeSpec::AttributeSpec(const char *key_, const int64_t id_, const int type_) :
    key(key_),
    id(id_),
    type(type_),
    coder(TYPECODE_TO_CODER[type])
{}

FDB::AttributeSpec::~AttributeSpec()
{}

TAKErr FDB::AttributeSpec::initCoders() NOTHROWS
{
    using namespace atakmap::util;

    ATTRIB_TYPES[AttributeSet::INT] = 0;
    ATTRIB_TYPES[AttributeSet::LONG] = 1;
    ATTRIB_TYPES[AttributeSet::DOUBLE] = 2;
    ATTRIB_TYPES[AttributeSet::STRING] = 3;
    ATTRIB_TYPES[AttributeSet::BLOB] = 4;
    ATTRIB_TYPES[AttributeSet::ATTRIBUTE_SET] = 5;
    ATTRIB_TYPES[AttributeSet::INT_ARRAY] = 6;
    ATTRIB_TYPES[AttributeSet::LONG_ARRAY] = 7;
    ATTRIB_TYPES[AttributeSet::DOUBLE_ARRAY] = 8;
    ATTRIB_TYPES[AttributeSet::STRING_ARRAY] = 9;
    ATTRIB_TYPES[AttributeSet::BLOB_ARRAY] = 10;

    CLAZZ_TO_CODER[AttributeSet::INT] = AttributeCoder{ encodeInt, decodeInt };
    CLAZZ_TO_CODER[AttributeSet::LONG] = AttributeCoder{ encodeLong, decodeLong };
    CLAZZ_TO_CODER[AttributeSet::DOUBLE] = AttributeCoder{ encodeDouble, decodeDouble };
    CLAZZ_TO_CODER[AttributeSet::STRING] = AttributeCoder{ encodeString, decodeString };
    CLAZZ_TO_CODER[AttributeSet::BLOB] = AttributeCoder{ encodeBinary, decodeBinary };
    CLAZZ_TO_CODER[AttributeSet::ATTRIBUTE_SET] = AttributeCoder{ nullptr, nullptr };
    CLAZZ_TO_CODER[AttributeSet::INT_ARRAY] = AttributeCoder{ encodeIntArray, decodeIntArray };
    CLAZZ_TO_CODER[AttributeSet::LONG_ARRAY] = AttributeCoder{ encodeLongArray, decodeLongArray };
    CLAZZ_TO_CODER[AttributeSet::DOUBLE_ARRAY] = AttributeCoder{ encodeDoubleArray, decodeDoubleArray };
    CLAZZ_TO_CODER[AttributeSet::STRING_ARRAY] = AttributeCoder{ encodeStringArray, decodeStringArray };
    CLAZZ_TO_CODER[AttributeSet::LONG_ARRAY] = AttributeCoder{ encodeBinaryArray, decodeBinaryArray };

    std::map<AttributeSet::Type, AttributeCoder>::iterator entry;
    for (entry = CLAZZ_TO_CODER.begin(); entry != CLAZZ_TO_CODER.end(); entry++)
        TYPECODE_TO_CODER[ATTRIB_TYPES[entry->first]] = entry->second;

    return TE_Ok;
}

/**************************************************************************/
// FeatureCursorImpl

FDB::FeatureCursorImpl::FeatureCursorImpl(FDB &owner_, QueryPtr &&filter_, const int idCol_, const int fsidCol_, const int versionCol_,
                                          const int nameCol_, const int geomCol_, const int styleCol_, const int attribsCol_,
                                          const int altitudeModeCol_, const int extrudeCol_) NOTHROWS : CursorWrapper2(std::move(filter_)),
                                                                                                        owner(owner_),
                                                                                                        idCol(idCol_),
                                                                                                        fsidCol(fsidCol_),
                                                                                                        nameCol(nameCol_),
                                                                                                        geomCol(geomCol_),
                                                                                                        styleCol(styleCol_),
                                                                                                        attribsCol(attribsCol_),
                                                                                                        versionCol(versionCol_),
                                                                                                        altitudeModeCol(altitudeModeCol_),
                                                                                                        extrudeCol(extrudeCol_),
                                                                                                        rowFeature(nullptr, nullptr),
                                                                                                        rowAttribs(nullptr, nullptr) {}

TAKErr FDB::FeatureCursorImpl::getId(int64_t *value) NOTHROWS
{
    return this->filter->getLong(value, this->idCol);
}

TAKErr FDB::FeatureCursorImpl::getVersion(int64_t *value) NOTHROWS
{
    return this->filter->getLong(value, this->versionCol);
}

TAKErr FDB::FeatureCursorImpl::getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS
{
    if (this->geomCol < 0) {
        value->binary.value = nullptr;
        value->binary.len = 0;
        return TE_Ok;
    }
    return this->filter->getBlob(&value->binary.value, &value->binary.len, this->geomCol);

}

FeatureDefinition2::GeometryEncoding FDB::FeatureCursorImpl::getGeomCoding() NOTHROWS
{
    return FeatureDefinition2::GeomBlob;
}

AltitudeMode FDB::FeatureCursorImpl::getAltitudeMode() NOTHROWS 
{
    if (this->altitudeModeCol == -1) {
        return AltitudeMode::TEAM_ClampToGround;
    }
    int value;
    this->filter->getInt(&value, this->altitudeModeCol);
    return (AltitudeMode)value;
}

double FDB::FeatureCursorImpl::getExtrude() NOTHROWS 
{
    if (this->extrudeCol == -1) {
        return 0.0;
    }
    double value;
    this->filter->getDouble(&value, this->extrudeCol);
    return value;
}

TAKErr FDB::FeatureCursorImpl::getName(const char **value) NOTHROWS
{
    if (this->nameCol < 0) {
        *value = nullptr;
        return TE_Ok;
    }
    return this->filter->getString(value, this->nameCol);
}

FeatureDefinition2::StyleEncoding FDB::FeatureCursorImpl::getStyleCoding() NOTHROWS
{
    return FeatureDefinition2::StyleOgr;
}

TAKErr FDB::FeatureCursorImpl::getRawStyle(RawData *value) NOTHROWS
{
    if (this->styleCol < 0) {
        value->binary.value = nullptr;
        value->binary.len = 0;
        return TE_Ok;
    }

    return this->filter->getString(&value->text, this->styleCol);
}

TAKErr FDB::FeatureCursorImpl::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS
{
    TAKErr code;

    if (this->attribsCol < 0) {
        *value = nullptr;
        return TE_Ok;
    }

    if (this->rowAttribs.get()) {
        *value = this->rowAttribs.get();
        return TE_Ok;
    }

    const uint8_t *attribsBlob;
    std::size_t attribsBlobLen;
    code = this->filter->getBlob(&attribsBlob, &attribsBlobLen, this->attribsCol);
    TE_CHECKRETURN_CODE(code);

    // XXX -
    Lock lock(owner.mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    code = owner.validateAttributeSchema();
    TE_CHECKRETURN_CODE(code);
    code = decodeAttributes(this->rowAttribs, attribsBlob, attribsBlobLen, owner.id_to_attr_schema_);
    TE_CHECKRETURN_CODE(code);

    *value = this->rowAttribs.get();

    return code;
}

TAKErr FDB::FeatureCursorImpl::get(const Feature2 **feature) NOTHROWS
{
    TAKErr code;
    if (!this->rowFeature.get()) {
        int64_t fid;
        code = this->getId(&fid);
        TE_CHECKRETURN_CODE(code);
        int64_t fsid;
        code = this->getFeatureSetId(&fsid);
        TE_CHECKRETURN_CODE(code);
        int64_t version;
        code = this->getVersion(&version);
        TE_CHECKRETURN_CODE(code);

        code = Feature_create(rowFeature, fid, fsid, *this, version);
        TE_CHECKRETURN_CODE(code);
    }
    *feature = this->rowFeature.get();
    code = TE_Ok;

    return code;
}

TAKErr FDB::FeatureCursorImpl::getFeatureSetId(int64_t *value) NOTHROWS
{
    return this->filter->getLong(value, this->fsidCol);
}

TAKErr FDB::FeatureCursorImpl::moveToNext() NOTHROWS
{
    rowFeature.reset();
    rowAttribs.reset();
    return this->filter->moveToNext();
}

/**************************************************************************/
// FeatureSetCursorImpl

FDB::FeatureSetCursorImpl::FeatureSetCursorImpl(FDB &owner_, TAK::Engine::Port::Collection<std::shared_ptr<const FeatureSetDefn>> &rows_) NOTHROWS :
    owner(owner_),
    rowData(nullptr, nullptr),
    pos(-1)
{
    // XXX - violates coding standard
    STLVectorAdapter<std::shared_ptr<const FeatureSetDefn>> rowsAdapter(rows);
    Collections_addAll(rowsAdapter, rows_);

    LT_featureSetName srt;
    std::sort(rows.begin(), rows.end(), srt);
}

TAKErr FDB::FeatureSetCursorImpl::get(const FeatureSet2 **featureSet) NOTHROWS
{
    TAKErr code;

    if (pos < 0 || static_cast<std::size_t>(pos) >= rows.size())
        return TE_IllegalState;

    code = TE_Ok;
    if (!rowData.get()) {
        code = owner.getFeatureSetImpl(rowData, *rows[pos]);
        TE_CHECKRETURN_CODE(code);
    }
    *featureSet = rowData.get();
    return code;
}

TAKErr FDB::FeatureSetCursorImpl::moveToNext() NOTHROWS
{
    if ((pos+1) == rows.size())
        return TE_Done;
    pos++;
    rowData.reset();
    return TE_Ok;
}

FDB::InsertContext::InsertContext() NOTHROWS :
    insertFeatureBlobStatement(nullptr, nullptr),
    insertFeatureWktStatement(nullptr, nullptr),
    insertFeatureWkbStatement(nullptr, nullptr),
    insertStyleStatement(nullptr, nullptr),
    insertAttributesStatement(nullptr, nullptr),
    insertAttributeSchemaStatement(nullptr, nullptr),
    insertGeomArg(nullptr, 0u)
{
    codedAttribs.open(512);
}

FDB::InsertContext::~InsertContext() NOTHROWS
{}


FDB::Builder::Builder(FDB &db_) NOTHROWS :
    db(db_)
{}

FDB::Builder::~Builder() NOTHROWS
{}

TAKErr FDB::Builder::createIndices() NOTHROWS
{
    return db.createIndicesNoSync();
}

TAKErr FDB::Builder::beginBulkInsertion() NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(db.mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    if (!db.database_)
        return TE_IllegalState;
    return db.database_->beginTransaction();
}

TAKErr FDB::Builder::endBulkInsertion(const bool commit) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(db.mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    if (!db.database_)
        return TE_IllegalState;

    if (commit) {
        code = db.database_->setTransactionSuccessful();
        TE_CHECKRETURN_CODE(code);
    }
    code = db.database_->endTransaction();
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr FDB::Builder::insertFeatureSet(int64_t *fsid, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    TAKErr code;
    FeatureSetPtr_const inserted(nullptr, nullptr);

    code = db.insertFeatureSetImpl(&inserted, provider, type, name, minResolution, maxResolution);
    TE_CHECKRETURN_CODE(code);

    *fsid = inserted->getId();
    return code;
}

TAKErr FDB::Builder::insertFeatureSet(const int64_t fsid, const char *provider, const char *type, const char *name) NOTHROWS
{
    return db.insertFeatureSetImpl(fsid, provider, type, name, std::numeric_limits<double>::max(), 0.0);
}

TAKErr FDB::Builder::insertFeature(int64_t* fid, const int64_t fsid, FeatureDefinition2 &def) NOTHROWS
{
    return db.insertFeatureImpl(nullptr, fid, fsid, def);
}

TAKErr FDB::Builder::insertFeature(const int64_t fsid, const char *name, const atakmap::feature::Geometry &geometry, const AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attribs) NOTHROWS
{
    return db.insertFeatureImpl(nullptr, fsid, name, geometry, altitudeMode, extrude, style, attribs);
}

TAKErr FDB::Builder::setFeatureSetVisible(const int64_t fsid, const bool& visible) NOTHROWS
{
	return db.setFeatureSetVisibleImpl(fsid, visible);
}

TAKErr FDB::Builder::setFeatureVisible(const int64_t fid, const bool& visible) NOTHROWS
{
	return db.setFeatureVisibleImpl(fid, visible);
}

TAKErr FDB::Builder::setFeatureVersion(const int64_t fid, const int64_t version) NOTHROWS
{
	return db.setFeatureVersionImpl(fid, version);
}

TAKErr FDB::Builder::updateFeatureSet(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS
{
    TAKErr code;
    code = db.updateFeatureSetImpl(fsid, minResolution, maxResolution);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr FDB::Builder::deleteFeatureSet(const int64_t fsid) NOTHROWS
{
    return db.deleteFeatureSetImpl(fsid);
}

namespace
{

std::ostringstream &insert(std::ostringstream &strm, const char *s) NOTHROWS
{
    const std::string suffix = strm.str();
    strm.seekp(0);
    strm << s;
    strm << suffix;
    return strm;
}

TAKErr appendOrder(bool &first, std::ostringstream &sql, Collection<BindArgument> &args, const FeatureDataStore2::FeatureQueryParameters::Order order) NOTHROWS
{
    if (order.type == FeatureDataStore2::FeatureQueryParameters::Order::FeatureId) {
        if (!first)
            sql << ",";
        sql << " features.fid ASC";
        first = false;
        return TE_Ok;
    } else if (order.type == FeatureDataStore2::FeatureQueryParameters::Order::FeatureName) {
        if (!first)
            sql << ",";
        sql << " features.name ASC";
        first = false;
        return TE_Ok;
    } else if (order.type == FeatureDataStore2::FeatureQueryParameters::Order::FeatureSet) {
        // order by FS is handled upstream
        return TE_Ok;
    } else if (order.type == FeatureDataStore2::FeatureQueryParameters::Order::Distance) {
        if (!first)
            sql << ",";
        sql << " Distance(features.geometry, MakePoint(?, ?, 4326), 1) ASC";
        args.add(BindArgument(order.args.distance.y));
        args.add(BindArgument(order.args.distance.x));
        first = false;
        return TE_Ok;
    } else if (order.type == FeatureDataStore2::FeatureQueryParameters::Order::Resolution) {
        if (!first)
            sql << ",";
        sql << " features.max_lod DESC";
        first = false;
        return TE_Ok;
    } else {
        // XXX -
        return TE_InvalidArg;
    }
}

TAKErr appendSpatialFilter(const atakmap::feature::Geometry &filter, WhereClauseBuilder2 &whereClause, const bool spatialFilterEnabled) NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    whereClause.beginCondition();
    if (spatialFilterEnabled) {
        BlobPtr filterBlob(nullptr, nullptr);
        code = LegacyAdapters_toBlob(filterBlob, filter);
        TE_CHECKRETURN_CODE(code);

        whereClause.append("features.ROWID IN (SELECT ROWID FROM SpatialIndex WHERE f_table_name = \'features\' AND search_frame = ?)");

        BindArgument arg(filterBlob->first, (filterBlob->second - filterBlob->first));
        arg.own();
        whereClause.addArg(arg);
    } else {
        atakmap::feature::Envelope mbb = filter.getEnvelope();
        whereClause.append("Intersects(BuildMbr(?, ?, ?, ?, 4326), features.geometry) = 1");
        whereClause.addArg(mbb.minX);
        whereClause.addArg(mbb.minY);
        whereClause.addArg(mbb.maxX);
        whereClause.addArg(mbb.maxY);
    }

    return code;
}

#define DEFN_PRIMITIVE_CODER(name, type) \
    TAKErr encode##name(DataOutput2 &dos, const atakmap::util::AttributeSet &attr, const char *key) NOTHROWS \
    { \
        TAKErr code; \
        if(!attr.containsAttribute(key)) return TE_InvalidArg; \
        type value = attr.get##name(key); \
        code = dos.write##name(value); \
        TE_CHECKRETURN_CODE(code); \
        return code; \
    } \
    TAKErr decode##name(atakmap::util::AttributeSet &attr, DataInput2 &dos, const char *key) NOTHROWS \
    { \
        TAKErr code; \
        type value; \
        code = dos.read##name(&value); \
        TE_CHECKRETURN_CODE(code); \
        try { \
            attr.set##name(key, value); \
        } catch(...) { \
            return TE_Err; \
        } \
        return code; \
    }

DEFN_PRIMITIVE_CODER(Int, int);
DEFN_PRIMITIVE_CODER(Long, int64_t);
DEFN_PRIMITIVE_CODER(Double, double);

#undef DEFN_PRIMITIVE_CODER

TAKErr encodeString(DataOutput2 &dos, const atakmap::util::AttributeSet &attr, const char *key) NOTHROWS
{
    TAKErr code;
    if(!attr.containsAttribute(key)) return TE_InvalidArg;
    const char *value = attr.getString(key);
    const std::size_t valLen = strlen(value);
    if (valLen > 0) {
        code = dos.writeInt(static_cast<int32_t>(strlen(value)));
        TE_CHECKRETURN_CODE(code);
        code = dos.writeString(value);
        TE_CHECKRETURN_CODE(code);
    } else {
        code = dos.writeInt(-1);
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}
TAKErr decodeString(atakmap::util::AttributeSet &attr, DataInput2 &dos, const char *key) NOTHROWS
{
    TAKErr code;
    int len;
    std::size_t numRead;
    array_ptr<char> value;
    code = dos.readInt(&len);
    if (len >= 0) {
        TE_CHECKRETURN_CODE(code);
        value.reset(new char[len + 1]);
        memset(value.get(), 0x00, len + 1);
        code = dos.readString(value.get(), &numRead, len);
        TE_CHECKRETURN_CODE(code);
        if (numRead < static_cast<std::size_t>(len))
            return TE_IO;
        try {
            attr.setString(key, value.get());
        } catch (...) {
            return TE_Err;
        }
    } else {
        try {
            attr.setString(key, nullptr);
        } catch (...) {
            return TE_Err;
        }
    }
    return code;
}

TAKErr encodeBinary(DataOutput2 &dos, const atakmap::util::AttributeSet &attr, const char *key) NOTHROWS
{
    TAKErr code;
    if (!attr.containsAttribute(key)) return TE_InvalidArg;
    atakmap::util::AttributeSet::Blob value = attr.getBlob(key);
    if (value.first) {
        code = dos.writeInt(static_cast<int32_t>(value.second - value.first));
        TE_CHECKRETURN_CODE(code);
        code = dos.write(value.first, (value.second - value.first));
        TE_CHECKRETURN_CODE(code);
    } else {
        code = dos.writeInt(-1);
        TE_CHECKRETURN_CODE(code);
    }
    return code;
}
TAKErr decodeBinary(atakmap::util::AttributeSet &attr, DataInput2 &dos, const char *key) NOTHROWS
{
    TAKErr code;
    int len;
    std::size_t numRead;
    array_ptr<uint8_t> value;
    code = dos.readInt(&len);
    if (len >= 0) {
        TE_CHECKRETURN_CODE(code);
        value.reset(new uint8_t[len]);
        code = dos.read(value.get(), &numRead, len);
        TE_CHECKRETURN_CODE(code);
        if (numRead < static_cast<std::size_t>(len))
            return TE_IO;
        try {
            attr.setBlob(key, atakmap::util::AttributeSet::Blob(value.get(), value.get()+len));
        } catch (...) {
            return TE_Err;
        }
    } else {
        try {
            attr.setBlob(key, atakmap::util::AttributeSet::Blob(NULL, NULL));
        } catch (...) {
            return TE_Err;
        }
    }
    return code;
}

#define DEFN_PRIMITIVE_ARRAY_CODER(name, type) \
    TAKErr encode##name##Array(DataOutput2 &dos, const atakmap::util::AttributeSet &attr, const char *key) NOTHROWS \
    { \
        TAKErr code; \
        if(!attr.containsAttribute(key)) return TE_InvalidArg; \
        atakmap::util::AttributeSet::name##Array value = attr.get##name##Array(key); \
        if(!value.first) { \
            code = dos.writeInt(-1); \
            TE_CHECKRETURN_CODE(code); \
        } else { \
            std::size_t len = (value.second-value.first); \
            code = dos.writeInt(static_cast<int32_t>(len)); \
            TE_CHECKRETURN_CODE(code); \
            for(std::size_t i = 0; i < len; i++) { \
                code = dos.write##name(value.first[i]); \
                TE_CHECKBREAK_CODE(code); \
            } \
            TE_CHECKRETURN_CODE(code); \
        } \
        return code; \
    } \
    TAKErr decode##name##Array(atakmap::util::AttributeSet &attr, DataInput2 &dos, const char *key) NOTHROWS \
    { \
        TAKErr code; \
        int len; \
        code = dos.readInt(&len); \
        TE_CHECKRETURN_CODE(code); \
        if(len > 0) { \
            array_ptr<type> value(new type[len]); \
            for(std::size_t i = 0; i < static_cast<std::size_t>(len); i++) { \
                code = dos.read##name(value.get()+i); \
                TE_CHECKBREAK_CODE(code); \
            } \
            TE_CHECKRETURN_CODE(code); \
            try { \
                attr.set##name##Array(key, atakmap::util::AttributeSet::name##Array(value.get(), value.get()+len)); \
            } catch(...) { \
                return TE_Err; \
            } \
        } else { \
            try { \
                attr.set##name##Array(key, atakmap::util::AttributeSet::name##Array(NULL, NULL)); \
            } catch(...) { \
                return TE_Err; \
            } \
        } \
        return code; \
    }

DEFN_PRIMITIVE_ARRAY_CODER(Int, int);
DEFN_PRIMITIVE_ARRAY_CODER(Long, int64_t);
DEFN_PRIMITIVE_ARRAY_CODER(Double, double);

#undef DEFN_PRIMITIVE_ARRAY_CODER


TAKErr encodeStringArray(DataOutput2 &dos, const atakmap::util::AttributeSet &attr, const char *key) NOTHROWS
{
    TAKErr code;
    if (!attr.containsAttribute(key)) return TE_InvalidArg;
    atakmap::util::AttributeSet::StringArray arr = attr.getStringArray(key);
    auto length = static_cast<int32_t>((arr.first) ? (arr.second-arr.first) : -1);
    code = dos.writeInt(length);
    TE_CHECKRETURN_CODE(code);
    for (int i = 0; i < length; i++) {
        const char *value = arr.first[i];
        const std::size_t valLen = strlen(value);
        if (valLen > 0) {
            code = dos.writeInt(static_cast<int32_t>(valLen));
            TE_CHECKBREAK_CODE(code);
            code = dos.writeString(value);
            TE_CHECKBREAK_CODE(code);
        }
        else {
            code = dos.writeInt(-1);
            TE_CHECKBREAK_CODE(code);
        }
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}
TAKErr decodeStringArray(atakmap::util::AttributeSet &attr, DataInput2 &dos, const char *key) NOTHROWS
{
    TAKErr code;
    int arrLen;
    code = dos.readInt(&arrLen);
    TE_CHECKRETURN_CODE(code);
    if (arrLen > 0) {
        array_ptr<array_ptr<char>> arr(new array_ptr<char>[arrLen]);
        array_ptr<const char *> sarr(new const char *[arrLen]);
        for (int i = 0; i < arrLen; i++) {
            int len;
            code = dos.readInt(&len);
            TE_CHECKBREAK_CODE(code);
            if (!len)
                continue;
            arr.get()[i].reset(new char[len + 1]);
            std::size_t nread;
            code = dos.readString(arr.get()[i].get(), &nread, len);
            TE_CHECKBREAK_CODE(code);
            sarr.get()[i] = arr.get()[i].get();
        }
        TE_CHECKRETURN_CODE(code);

        try {
            attr.setStringArray(key, atakmap::util::AttributeSet::StringArray(sarr.get(), sarr.get() + arrLen));
        } catch (std::invalid_argument &) {
            code = TE_Err;
        }
    } else {
        try {
            attr.setStringArray(key, atakmap::util::AttributeSet::StringArray(NULL, NULL));
        } catch (std::invalid_argument &) {
            code = TE_Err;
        }
    }

    return code;
}

TAKErr encodeBinaryArray(DataOutput2 &dos, const atakmap::util::AttributeSet &attr, const char *key) NOTHROWS
{
    TAKErr code;
    if (!attr.containsAttribute(key)) return TE_InvalidArg;
    atakmap::util::AttributeSet::BlobArray arr = attr.getBlobArray(key);
    auto length = static_cast<int32_t>((arr.first) ? (arr.second - arr.first) : -1);
    code = dos.writeInt(length);
    TE_CHECKRETURN_CODE(code);
    for (int i = 0; i < length; i++) {
        atakmap::util::AttributeSet::Blob value = arr.first[i];
        const std::size_t valLen = (value.second-value.first);
        if (valLen > 0) {
            code = dos.writeInt(static_cast<int32_t>(valLen));
            TE_CHECKBREAK_CODE(code);
            code = dos.write(value.first, valLen);
            TE_CHECKBREAK_CODE(code);
        } else {
            code = dos.writeInt(-1);
            TE_CHECKBREAK_CODE(code);
        }
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}
TAKErr decodeBinaryArray(atakmap::util::AttributeSet &attr, DataInput2 &dos, const char *key) NOTHROWS
{
    TAKErr code;
    int arrLen;
    code = dos.readInt(&arrLen);
    TE_CHECKRETURN_CODE(code);
    if (arrLen > 0) {
        array_ptr<array_ptr<uint8_t>> arr(new array_ptr<uint8_t>[arrLen]);
        array_ptr<atakmap::util::AttributeSet::Blob> barr(new atakmap::util::AttributeSet::Blob[arrLen]);
        for (int i = 0; i < arrLen; i++) {
            int len;
            code = dos.readInt(&len);
            TE_CHECKBREAK_CODE(code);
            if (!len)
                continue;
            arr.get()[i].reset(new uint8_t[len + 1]);
            std::size_t nread;
            code = dos.read(arr.get()[i].get(), &nread, len);
            TE_CHECKBREAK_CODE(code);
            barr.get()[i] = std::pair<const uint8_t *, const uint8_t *>(arr.get()[i].get(), arr.get()[i].get() + nread);
    }
        TE_CHECKRETURN_CODE(code);

        attr.setBlobArray(key, atakmap::util::AttributeSet::BlobArray(barr.get(), barr.get() + arrLen));
    }
    else {
        attr.setBlobArray(key, atakmap::util::AttributeSet::BlobArray(NULL, NULL));
    }

    return code;
}


template<class T>
PointerContainerMgr<T>::PointerContainerMgr(T &c) NOTHROWS :
    container(c)
{}

template<class T>
PointerContainerMgr<T>::~PointerContainerMgr() NOTHROWS
{
    typename T::iterator iter;
    for (iter = container.begin(); iter != container.end(); iter++) {
        delete *iter;
    }
}

template<class T>
void PointerContainerMgr<T>::release() NOTHROWS
{
    container.clear();
}

DefaultFeatureDefinition::DefaultFeatureDefinition(const Feature2 &impl_) NOTHROWS :
    impl(impl_)
{}

TAKErr DefaultFeatureDefinition::getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS
{
    value->object = impl.getGeometry();
    return TE_Ok;
}

FeatureDefinition2::GeometryEncoding DefaultFeatureDefinition::getGeomCoding() NOTHROWS
{
    return FeatureDefinition2::GeomGeometry;
}

AltitudeMode DefaultFeatureDefinition::getAltitudeMode() NOTHROWS 
{
    return impl.getAltitudeMode();
}

double DefaultFeatureDefinition::getExtrude() NOTHROWS 
{
    return impl.getExtrude();
}

TAKErr DefaultFeatureDefinition::getName(const char **value) NOTHROWS
{
    *value = impl.getName();
    return TE_Ok;
}

FeatureDefinition2::StyleEncoding DefaultFeatureDefinition::getStyleCoding() NOTHROWS
{
    return FeatureDefinition2::StyleStyle;
}

TAKErr DefaultFeatureDefinition::getRawStyle(FeatureDefinition2::RawData *value) NOTHROWS
{
    value->object = impl.getStyle();
    return TE_Ok;
}

TAKErr DefaultFeatureDefinition::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS
{
    *value = impl.getAttributes();
    return TE_Ok;
}

TAKErr DefaultFeatureDefinition::get(const Feature2 **feature) NOTHROWS
{
    *feature = &impl;
    return TE_Ok;
}

}
