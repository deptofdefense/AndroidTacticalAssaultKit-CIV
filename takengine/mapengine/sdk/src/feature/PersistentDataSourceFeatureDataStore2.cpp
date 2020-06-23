#include "feature/PersistentDataSourceFeatureDataStore2.h"

#include <cassert>
#include <sstream>
#include <string>
#include <tuple>

#include "currency/Currency2.h"
#include "currency/CurrencyRegistry2.h"
#include "db/BindArgument.h"
#include "db/Query.h"
#include "db/Statement2.h"
#include "db/WhereClauseBuilder2.h"
#include "feature/BruteForceLimitOffsetFeatureCursor.h"
#include "feature/FeatureCursor2.h"
#include "feature/FeatureDataSource2.h"
#include "feature/FeatureSet2.h"
#include "feature/FeatureSetCursor2.h"
#include "feature/FeatureSetDatabase.h"
#include "feature/FeatureSpatialDatabase.h"
#include "feature/MultiplexingFeatureCursor.h"
#include "feature/ParseGeometry.h"
#include "feature/Style.h"
#include "port/Collections.h"
#include "port/STLSetAdapter.h"
#include "port/STLVectorAdapter.h"
#include "raster/osm/OSMUtils.h"
#include "thread/Lock.h"
#include "util/DataInput2.h"
#include "util/DataOutput2.h"
#include "util/IO.h"
#include "util/IO2.h"
#include "util/Logging.h"
#include "util/Logging2.h"
#include "util/Memory.h"

using namespace TAK::Engine::Feature;

using namespace TAK::Engine::Currency;
using namespace TAK::Engine::DB;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::raster::osm;
using namespace atakmap::util;

#define CURRENCY_NAME "PersistentDataSourceFeatureDataStore.Currency"
#define CURRENCY_VERSION 2

#define TAG "PersistenDataSourceFeatureDataStore2"

#define INDEX_SCHEMA_VERSION 8

#define TEMPLATE_FDB_FILENAME ".template.fdb"

#define FDB_FEATURESET_LIMIT 250

namespace
{
    class ValidateCurrency : public CatalogCurrency2
    {
    public:
        ValidateCurrency() NOTHROWS;
    public:
        virtual const char *getName() const NOTHROWS;
        virtual int getAppVersion() const NOTHROWS;

        virtual TAK::Engine::Util::TAKErr getAppData(AppDataPtr &data, const char *file) const NOTHROWS;
        virtual TAK::Engine::Util::TAKErr isValidApp(bool *value, const char *f, const int appVersion, const AppData &data) const NOTHROWS;
    };

    class GenerateCurrency : public CatalogCurrency2
    {
    public :
        GenerateCurrency() NOTHROWS;
    public:
        TAKErr init(const FeatureDataSource2::Content &content) NOTHROWS;
    public:
        virtual const char *getName() const NOTHROWS;
        virtual int getAppVersion() const NOTHROWS;

        virtual TAKErr getAppData(AppDataPtr &data, const char *file) const NOTHROWS;
        virtual TAKErr isValidApp(bool *value, const char *f, const int appVersion, const AppData &data) const NOTHROWS;
    private :
        std::set<std::shared_ptr<FeatureDataSource2>> contentSpis;
    };

    class FileCursorImpl : public DataSourceFeatureDataStore2::FileCursor
    {
    public:
        FileCursorImpl(CatalogDatabase2::CatalogCursorPtr &&filter) NOTHROWS;
    public: // FileCursor
        virtual TAKErr getFile(TAK::Engine::Port::String &path) NOTHROWS;
    public : // RowIterator
        virtual TAKErr moveToNext() NOTHROWS;
    private :
        CatalogDatabase2::CatalogCursorPtr filter;
    };

    class ModifiedFileCursorImpl : public DataSourceFeatureDataStore2::FileCursor
    {
    public:
        ModifiedFileCursorImpl(QueryPtr &&filter) NOTHROWS;
    public: // FileCursor
        virtual TAKErr getFile(TAK::Engine::Port::String &path) NOTHROWS;
    public: // RowIterator
        virtual TAKErr moveToNext() NOTHROWS;
    private:
        QueryPtr filter;
    };

    int getCodedStringLength(const char *s) NOTHROWS;
    TAKErr putString(DataOutput2 &buffer, const char *s) NOTHROWS;
    TAKErr getString(TAK::Engine::Port::String &value, DataInput2 &buffer) NOTHROWS;

    void appDataDeleter(const CatalogCurrency2::AppData *appData);
}

PersistentDataSourceFeatureDataStore2::PersistentDataSourceFeatureDataStore2() NOTHROWS :
    AbstractFeatureDataStore2(0xFFFFFFFF,
                              VISIBILITY_SETTINGS_FEATURESET | VISIBILITY_SETTINGS_FEATURE),
    index(NULL),
    indexDb(NULL, NULL),
    mutex(TEMT_Recursive)
{
    static ValidateCurrency validate;
    currency.registerCurrency(&validate);
}

PersistentDataSourceFeatureDataStore2::~PersistentDataSourceFeatureDataStore2() NOTHROWS
{
    closeImpl();
}

TAKErr PersistentDataSourceFeatureDataStore2::open(const char *database) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    if (pathExists(database) && !isDirectory(database) && getFileSize(database) > 0) {
        Logger::log(Logger::Error, TAG ": database is not a directory or an empty file");
        return TE_InvalidArg;
    }

    if (pathExists(database) && !isDirectory(database) && getFileSize(database) == 0) {
        if (!deletePath(database)) {
            Logger::log(Logger::Error, TAG ": Failed to delete database file");
            return TE_IO;
        }
    }

    if (!pathExists(database)) {
        if (!createDir(database)) {
            Logger::log(Logger::Error, TAG ": Failed to create database directory");
            return TE_IO;
        }
    }

    this->databaseDir = database;

    {
        std::ostringstream strm;
        strm << this->databaseDir;
        strm << "/";
        strm << "fdbs";
        this->fdbsDir = strm.str().c_str();
    }

    {
        std::ostringstream strm;
        strm << this->databaseDir;
        strm << "/";
        strm << TEMPLATE_FDB_FILENAME;
        this->fdbTemplate = strm.str().c_str();
    }

    {
        std::ostringstream strm;
        strm << this->databaseDir;
        strm << "/";
        strm << "index.sqlite";
        code = Databases_openDatabase(this->indexDb, strm.str().c_str());
        TE_CHECKRETURN_CODE(code);
    }

    this->index = new Index(*this, currency);
    code = this->index->open(this->indexDb.get());
    TE_CHECKRETURN_CODE(code);

    // check and generate the FDBs dir *after* creating the index as we will
    // dump the FDBs if the schema is updated
    if (!pathExists(this->fdbsDir)) {
        if (!createDir(this->fdbsDir)) {
            Logger::log(Logger::Error, TAG ": Failed to create FDBs directory");
            return TE_IO;
        }
    }

    // load existing entries
    QueryPtr result(NULL, NULL);
    code = this->indexDb->query(result, "SELECT id, name, fdb_path, provider, type, file_id FROM featuresets");
    TE_CHECKRETURN_CODE(code);

    const char *dbFile;
    std::map<Port::String, std::shared_ptr<DatabaseRef>, Port::StringLess> refs;
    const char *cstr;
    do  {
        code = result->moveToNext();
        TE_CHECKBREAK_CODE(code);

        std::shared_ptr<FeatureDb> db(new FeatureDb());

        code = result->getLong(&db->fsid, 0);
        TE_CHECKBREAK_CODE(code);
        code = result->getString(&cstr, 1);
        TE_CHECKBREAK_CODE(code);
        db->name = cstr;
        code = result->getString(&cstr, 3);
        TE_CHECKBREAK_CODE(code);
        db->provider = cstr;
        code = result->getString(&cstr, 4);
        TE_CHECKBREAK_CODE(code);
        db->type = cstr;
        result->getLong(&db->catalogId, 5);
        TE_CHECKBREAK_CODE(code);

        code = result->getString(&dbFile, 2);
        TE_CHECKBREAK_CODE(code);
#if TE_SHOULD_ADAPT_STORAGE_PATHS
        Port::String dbFileRuntime = dbFile;
        code = File_getRuntimePath(dbFileRuntime);
        TE_CHECKBREAK_CODE(code);
        dbFile = dbFileRuntime.get();
#endif

        if (!pathExists(dbFile)) {
            // FDB was deleted, invalidate the entry
            this->index->invalidateCatalogEntry(db->catalogId);
            continue;
        }
        db->fdbFile = dbFile;

        std::map<Port::String, std::shared_ptr<DatabaseRef>, Port::StringLess>::iterator entry;
        entry = refs.find(dbFile);
        if (entry == refs.end()) {
            int visibilityFlags;
            code = getVisibilitySettingsFlags(&visibilityFlags);

            int modificationFlags;
            code = this->getModificationFlags(&modificationFlags);

            std::unique_ptr<FeatureSetDatabase, void(*)(const FeatureDataStore2 *)> fdb(new FeatureSetDatabase(modificationFlags, visibilityFlags), Memory_deleter_const<FeatureDataStore2, FeatureSetDatabase>);
            if (fdb->open(dbFile) != TE_Ok) {
                // something is wrong, invalidate the entry
                this->index->invalidateCatalogEntry(db->catalogId);
                continue;
            }

            std::shared_ptr<DatabaseRef> ref(
            new DatabaseRef(
                std::move(fdb),
                dbFile));
            db->database = ref;
            refs[dbFile] = db->database;
        } else {
            db->database = entry->second;
            this->sharedDbs.insert(db->database);
        }

        FeatureSetPtr_const fs(NULL, NULL);
        code = db->database->value->getFeatureSet(fs, db->fsid);
        if (code == TE_InvalidArg || !fs.get()) {
            // something is wrong (ingest likely did not complete), invalidate the entry
            int64_t id;
            if (result->getLong(&id, 5) == TE_Ok)
                this->index->invalidateCatalogEntry(id);
            continue;
        }
        TE_CHECKBREAK_CODE(code);

        db->minResolution = fs->getMinResolution();
        db->maxResolution = fs->getMaxResolution();
        db->version = fs->getVersion();

        int64_t fsid = db->fsid;
        this->fsidToFeatureDb[fsid] = db;
    } while(true);

    result.reset();
    return (code == TE_Done) ? TE_Ok : code;
}


TAKErr PersistentDataSourceFeatureDataStore2::getFeature(FeaturePtr_const &feature, const int64_t fid) NOTHROWS
{
    TAKErr code;
    FeatureQueryParameters params;
    params.featureIds->add(fid);

    FeatureCursorPtr result(NULL, NULL);
    code = this->queryFeatures(result, params);
    TE_CHECKRETURN_CODE(code);

    code = result->moveToNext();
    if (code == TE_Done)
        return TE_InvalidArg; // feature does not exist
    TE_CHECKRETURN_CODE(code);

    const Feature2 *f;
    code = result->get(&f);
    TE_CHECKRETURN_CODE(code);

    feature = FeaturePtr(new Feature2(*f), Memory_deleter_const<Feature2>);
    return code;
}

TAKErr PersistentDataSourceFeatureDataStore2::matches(bool *matched, const FeatureDb &db, const FeatureQueryParameters *params) NOTHROWS
{
    if (!params) {
        *matched = true;
        return TE_Ok;
    }

    TAKErr code;

    code = TE_Ok;

    if (!params->providers->empty()) {
        code = AbstractFeatureDataStore2::matches(matched, *params->providers, db.provider, '%');
        TE_CHECKRETURN_CODE(code);
        if (!(*matched))
            return code;
    }
    if (!params->types->empty()) {
        code = AbstractFeatureDataStore2::matches(matched, *params->types, db.type, '%');
        TE_CHECKRETURN_CODE(code);
        if (!(*matched))
            return code;
    }
    if (!params->featureSetIds->empty()) {
        int64_t fsid = db.fsid;
        code = params->featureSetIds->contains(matched, fsid);
        TE_CHECKRETURN_CODE(code);
        if (!(*matched))
            return code;
    }
    if (!params->featureSets->empty()) {
        code = AbstractFeatureDataStore2::matches(matched, *params->featureSets, db.name, '%');
        TE_CHECKRETURN_CODE(code);
        if (!(*matched))
            return code;
    }
    if (!params->featureIds->empty()) {
        bool matches = false;
        Collection<int64_t>::IteratorPtr fids(NULL, NULL);
        code = params->featureIds->iterator(fids);
        TE_CHECKRETURN_CODE(code);

        int64_t fid;
        do {
            code = fids->get(fid);
            TE_CHECKBREAK_CODE(code);
            const int64_t fsid = ((fid >> 32LL) & 0xFFFFFFFFLL);
            if (fsid == db.fsid) {
                matches = true;
                break;
            }
            code = fids->next();
            TE_CHECKBREAK_CODE(code);
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);
        *matched = matches;
        if (!(*matched))
            return code;
    }
    if (!isnan(params->minResolution)) {
        // XXX -
    }
    if (!isnan(params->maxResolution)) {
        // XXX -
    }

    *matched = true;
    return code;
}

//private static boolean matches(FeatureDb db, FeatureSetQueryParameters params) {
TAKErr PersistentDataSourceFeatureDataStore2::matches(bool *matched, const FeatureDb &db, const FeatureSetQueryParameters *params) NOTHROWS
{
    if (!params) {
        *matched = true;
        return TE_Ok;
    }

    TAKErr code;

    code = TE_Ok;

    if (!params->providers->empty()) {
        code = AbstractFeatureDataStore2::matches(matched, *params->providers, db.provider, '%');
        TE_CHECKRETURN_CODE(code);
        if (!(*matched))
            return code;
    }
    if (!params->types->empty()) {
        code = AbstractFeatureDataStore2::matches(matched, *params->types, db.type, '%');
        TE_CHECKRETURN_CODE(code);
        if (!(*matched))
            return code;
    }
    if (!params->ids->empty()) {
        int64_t fsid = db.fsid;
        code = params->ids->contains(matched, fsid);
        TE_CHECKRETURN_CODE(code);
        if (!(*matched))
            return code;
    }
    if (!params->names->empty()) {
        code = AbstractFeatureDataStore2::matches(matched, *params->names, db.name, '%');
        TE_CHECKRETURN_CODE(code);
        if (!(*matched))
            return code;
    }

    *matched = true;
    return code;
}


TAKErr PersistentDataSourceFeatureDataStore2::filter(std::shared_ptr<FeatureQueryParameters> &retval, const FeatureDb &db, const FeatureQueryParameters *params, const bool shared) NOTHROWS {
    if (!params)
        return TE_Ok;

    TAKErr code;

    code = TE_Ok;

    const bool creatingParams = !retval.get();

    if (!params->featureNames->empty()) {
        if (!retval.get())
            retval.reset(new FeatureQueryParameters());

        // check and mask off FSID
        if (creatingParams) {
            code = Collections_addAll(*retval->featureNames, *params->featureNames);
            TE_CHECKRETURN_CODE(code);
        }
    }
    if (!params->featureIds->empty()) {
        if (!retval.get())
            retval.reset(new FeatureQueryParameters());

        // check and mask off FSID
        TAK::Engine::Port::Collection<int64_t>::IteratorPtr fids(NULL, NULL);
        code = params->featureIds->iterator(fids);
        do {
            int64_t fid;
            code = fids->get(fid);
            TE_CHECKBREAK_CODE(code);
            const int64_t fsid = ((fid >> 32LL) & 0xFFFFFFFFLL);
            if (fsid == db.fsid)
                retval->featureIds->add((fid & 0xFFFFFFFFLL));
            code = fids->next();
            TE_CHECKBREAK_CODE(code);
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);
    }
    if (!params->featureSetIds->empty()) {
        if (!retval.get())
            retval.reset(new FeatureQueryParameters());
        retval->featureSetIds->add(db.fsid);
    }
    if (!params->featureSets->empty()) {
        if (!retval.get())
            retval.reset(new FeatureQueryParameters());
        retval->featureSets->add(db.name);
    }
    if (!params->providers->empty()) {
        if (!retval.get())
            retval.reset(new FeatureQueryParameters());
        retval->providers->add(db.provider);
    }
    if (!params->types->empty()) {
        if (!retval.get())
            retval.reset(new FeatureQueryParameters());
        retval->types->add(db.type);
    }
    if (params->visibleOnly ||
        !params->geometryTypes->empty() ||
        params->ignoredFields != 0 ||
        !isnan(params->maxResolution) ||
        !isnan(params->minResolution) ||
        params->limit != 0 ||
        !params->ops->empty() ||
        !params->order->empty() ||
        params->spatialFilter.get()) {

        if (!retval.get())
            retval.reset(new FeatureQueryParameters());

        if (creatingParams) {
            retval->visibleOnly = params->visibleOnly;
            code = Collections_addAll(*retval->geometryTypes, *params->geometryTypes);
            TE_CHECKRETURN_CODE(code);
            retval->ignoredFields = params->ignoredFields;
            retval->maxResolution = params->maxResolution;
            retval->minResolution = params->minResolution;
            retval->limit = params->limit;
            retval->offset = params->offset;
            code = Collections_addAll(*retval->ops, *params->ops);
            TE_CHECKRETURN_CODE(code);
            code = Collections_addAll(*retval->order, *params->order);
            TE_CHECKRETURN_CODE(code);
            if (params->spatialFilter.get())
                retval->spatialFilter = atakmap::feature::UniqueGeometryPtr(atakmap::feature::Geometry::clone(params->spatialFilter.get()), atakmap::feature::destructGeometry);
        }
    }

    return code;
}


//private static void filter(FeatureDb db, FeatureSetQueryParameters params, boolean shared, FeatureSetQueryParameters[] retval) {
TAKErr PersistentDataSourceFeatureDataStore2::filter(std::shared_ptr<FeatureSetQueryParameters> &retval, const FeatureDb &db, const FeatureSetQueryParameters *params, const bool shared) NOTHROWS
{
    if (!params)
        return TE_Ok;

    TAKErr code;

    code = TE_Ok;

    if (!params->ids->empty()) {
        if (!retval.get())
            retval.reset(new FeatureSetQueryParameters());
        retval->ids->add(db.fsid);
    }
    if (params->names->empty()) {
        if (!retval.get())
            retval.reset(new FeatureSetQueryParameters());
        retval->names->add(db.name);
    }
    if (params->providers->empty()) {
        if (!retval.get())
            retval.reset(new FeatureSetQueryParameters());
        retval->providers->add(db.provider);
    }
    if (params->types->empty()) {
        if (!retval.get())
            retval.reset(new FeatureSetQueryParameters());
        retval->types->add(db.type);
    }
    if (params->visibleOnly ||
        params->limit != 0) {

        if (!retval.get())
            retval.reset(new FeatureSetQueryParameters());

        retval->visibleOnly = params->visibleOnly;
        retval->limit = params->limit;
        retval->offset = params->offset;
    }

    return code;
}

TAKErr PersistentDataSourceFeatureDataStore2::prepareQuery(std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureQueryParameters>> &value, const FeatureQueryParameters *params) NOTHROWS
{
    TAKErr code;

    code = TE_Ok;
    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator iter;
    for (iter = this->fsidToFeatureDb.begin(); iter != this->fsidToFeatureDb.end(); iter++) {
        std::shared_ptr<FeatureDb> db = iter->second;
        bool matched;
        code = matches(&matched, *db, params);
        if (code != TE_Ok)
            break;
        if (matched) {
            std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureQueryParameters>>::iterator entry;
            entry = value.find(db->database);
            std::shared_ptr<FeatureQueryParameters> paramsPtr(NULL);
            if(entry != value.end())
                paramsPtr = entry->second;
            code = filter(paramsPtr, *db, params, (this->sharedDbs.find(db->database) != this->sharedDbs.end()));

            if (code != TE_Ok)
                break;
            if (entry == value.end())
                value[db->database] = paramsPtr;
        }
    }
    return code;
}

//private Map<DatabaseRef, FeatureSetQueryParameters> prepareQuery(FeatureSetQueryParameters params) {
TAKErr PersistentDataSourceFeatureDataStore2::prepareQuery(std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureSetQueryParameters>> &value, const FeatureSetQueryParameters *params) NOTHROWS
{
    TAKErr code(TE_Ok);
    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator iter;
    for (iter = this->fsidToFeatureDb.begin(); iter != this->fsidToFeatureDb.end(); iter++) {
        std::shared_ptr<FeatureDb> db = iter->second;
        bool matched;
        code = matches(&matched, *db, params);
        if (code != TE_Ok)
            break;
        if (matched) {
            std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureSetQueryParameters>>::iterator entry;
            entry = value.find(db->database);
            std::shared_ptr<FeatureSetQueryParameters> paramsPtr(NULL);
            if (entry != value.end())
                paramsPtr = entry->second;
            code = filter(paramsPtr, *db, params, (this->sharedDbs.find(db->database) != this->sharedDbs.end()));
            if (code != TE_Ok)
                break;
            if (entry == value.end())
                value[db->database] = paramsPtr;
        }
    }
    return code;
}

TAKErr PersistentDataSourceFeatureDataStore2::queryFeatures(FeatureCursorPtr &result) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureQueryParameters>> dbs;
    code = this->prepareQuery(dbs, NULL);
    TE_CHECKRETURN_CODE(code);

    std::vector<FeatureCursorPtr> cursors;
    std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureQueryParameters>>::iterator db;
    for (db = dbs.begin(); db != dbs.end(); db++) {
        FeatureCursorPtr cursor(NULL, NULL);
        if (db->second.get())
            code = db->first->value->queryFeatures(cursor, *db->second);
        else
            code = db->first->value->queryFeatures(cursor);
        if (code != TE_Ok)
            break;
        cursors.push_back(std::move(FeatureCursorPtr(new DistributedFeatureCursorImpl(std::move(cursor), db->first), Memory_deleter_const<FeatureCursor2, DistributedFeatureCursorImpl>)));
    }
    TE_CHECKRETURN_CODE(code);

    std::unique_ptr<MultiplexingFeatureCursor> retval(new MultiplexingFeatureCursor());
    for (std::size_t i = 0; i < cursors.size(); i++)
        retval->add(std::move(cursors[i]));
    cursors.clear();

    result = FeatureCursorPtr(retval.release(), Memory_deleter_const<FeatureCursor2, MultiplexingFeatureCursor>);

    return code;
}


TAKErr PersistentDataSourceFeatureDataStore2::queryFeatures(FeatureCursorPtr &result, const FeatureQueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureQueryParameters>> dbs;
    code = this->prepareQuery(dbs, &params);
    TE_CHECKRETURN_CODE(code);

    // if there is a limit/offset specified and we are going to be
    // querying more than one database we will need to brute force;
    // strip the offset/limit off of any DB instance queries
    if (dbs.size() > 1 && params.limit != 0) {
        std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureQueryParameters>>::iterator iter;
        //for (FeatureQueryParameters dbParams : dbs.values()) {
        for (iter = dbs.begin(); iter != dbs.end(); iter++) {
            if (iter->second.get()) {
                iter->second.get()->offset = 0;
                iter->second.get()->limit = 0;
            }
        }
    }

    std::vector<FeatureCursorPtr> cursors;
    std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureQueryParameters>>::iterator db;
    for (db = dbs.begin(); db != dbs.end(); db++) {
        FeatureCursorPtr cursor(NULL, NULL);
        if (db->second.get())
            code = db->first->value->queryFeatures(cursor, *db->second);
        else
            code = db->first->value->queryFeatures(cursor);
        if (code != TE_Ok)
            break;
        cursors.push_back(std::move(FeatureCursorPtr(new DistributedFeatureCursorImpl(std::move(cursor), db->first), Memory_deleter_const<FeatureCursor2, DistributedFeatureCursorImpl>)));
    }
    TE_CHECKRETURN_CODE(code);

    std::unique_ptr<MultiplexingFeatureCursor> retval(new MultiplexingFeatureCursor());
    for (std::size_t i = 0; i < cursors.size(); i++)
        retval->add(std::move(cursors[i]));
    cursors.clear();

    result = FeatureCursorPtr(retval.release(), Memory_deleter_const<FeatureCursor2, MultiplexingFeatureCursor>);
    if (dbs.size() > 1 && params.limit != 0)
        result = FeatureCursorPtr(new BruteForceLimitOffsetFeatureCursor(std::move(result), params.limit, params.offset), Memory_deleter_const<FeatureCursor2, BruteForceLimitOffsetFeatureCursor>);

    return code;
}

    //public int queryFeaturesCount(FeatureQueryParameters params) {
TAKErr PersistentDataSourceFeatureDataStore2::queryFeaturesCount(int *value) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureQueryParameters>> dbs;
    code = this->prepareQuery(dbs, NULL);
    TE_CHECKRETURN_CODE(code);

    std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureQueryParameters>>::iterator db;
    int retval = 0;
    for (db = dbs.begin(); db != dbs.end(); db++) {
        int cnt;
        if (db->second)
            code = db->first->value->queryFeaturesCount(&cnt, *db->second);
        else
            code = db->first->value->queryFeaturesCount(&cnt);
        if (code != TE_Ok)
            break;
        retval += cnt;
    }
    *value = retval;
    return code;
}

TAKErr PersistentDataSourceFeatureDataStore2::queryFeaturesCount(int *value, const FeatureQueryParameters &params) NOTHROWS
{
    if (params.limit > 0 && params.offset > 0) {
        return AbstractFeatureDataStore2::queryFeaturesCount(value, *this, params);
    } else {
        TAKErr code(TE_Ok);
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, mutex);
        TE_CHECKRETURN_CODE(code);
        std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureQueryParameters>> dbs;
        code = this->prepareQuery(dbs, &params);
        TE_CHECKRETURN_CODE(code);

        std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureQueryParameters>>::iterator db;
        int retval = 0;
        for (db = dbs.begin(); db != dbs.end(); db++) {
            int cnt;
            if (db->second)
                code = db->first->value->queryFeaturesCount(&cnt, *db->second);
            else
                code = db->first->value->queryFeaturesCount(&cnt);
            if (code != TE_Ok)
                break;
            retval += cnt;
        }
        *value = retval;
        return code;
    }
}


//public synchronized FeatureSet getFeatureSet(int64_t fsid) {
TAKErr PersistentDataSourceFeatureDataStore2::getFeatureSet(FeatureSetPtr_const &value, const int64_t fsid) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry = this->fsidToFeatureDb.find(fsid);
    if (entry == this->fsidToFeatureDb.end())
        return TE_InvalidArg;

    return entry->second->database->value->getFeatureSet(value, fsid);
}

//public synchronized FeatureSetCursor queryFeatureSets(FeatureSetQueryParameters params) {
TAKErr PersistentDataSourceFeatureDataStore2::queryFeatureSets(FeatureSetCursorPtr &result) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    std::set<std::shared_ptr<const FeatureDb>, FeatureSetCursorImpl::LT_featureSetName> retval;
    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry;
    for (entry = this->fsidToFeatureDb.begin(); entry != this->fsidToFeatureDb.end(); entry++)
        retval.insert(entry->second);

    result = FeatureSetCursorPtr(new FeatureSetCursorImpl(retval), Memory_deleter_const<FeatureSetCursor2, FeatureSetCursorImpl>);
    return TE_Ok;
}

TAKErr PersistentDataSourceFeatureDataStore2::queryFeatureSets(FeatureSetCursorPtr &result, const FeatureSetQueryParameters &params) NOTHROWS
{
    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry;
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    code = TE_Ok;

    std::set<int64_t> fsids;
    for (entry = this->fsidToFeatureDb.begin(); entry != this->fsidToFeatureDb.end(); entry++)
        fsids.insert(entry->first);

    if (!params.ids->empty()) {
        std::set<int64_t>::iterator iter = fsids.begin();
        while (iter != fsids.end()) {
            bool contained;
            int64_t fsid = *iter;
            code = params.ids->contains(&contained, fsid);
            if (code != TE_Ok)
                break;
            if (contained)
                iter++;
            else
                iter = fsids.erase(iter);
        }
        TE_CHECKRETURN_CODE(code);
    }

    if (!params.types->empty()) {
        std::set<int64_t>::iterator iter = fsids.begin();
        while (iter != fsids.end()) {
            entry = this->fsidToFeatureDb.find(*iter);
            if (entry == this->fsidToFeatureDb.end()) {
                iter = fsids.erase(iter);
            } else {
                bool matched = false;
                code = AbstractFeatureDataStore2::matches(&matched, *params.types, entry->second->type, '%');
                if (code != TE_Ok)
                    break;
                if (!matched)
                    iter = fsids.erase(iter);
                else
                    iter++;
            }
        }
        TE_CHECKRETURN_CODE(code);
    }
    if (!params.providers->empty()) {
        std::set<int64_t>::iterator iter = fsids.begin();
        while (iter != fsids.end()) {
            entry = this->fsidToFeatureDb.find(*iter);
            if (entry == this->fsidToFeatureDb.end()) {
                iter = fsids.erase(iter);
            } else {
                bool matched = false;
                code = AbstractFeatureDataStore2::matches(&matched, *params.providers, entry->second->provider, '%');
                if (code != TE_Ok)
                    break;
                if (!matched)
                    iter = fsids.erase(iter);
                else
                    iter++;
            }
        }
        TE_CHECKRETURN_CODE(code);
    }
    if (params.visibleOnly) {
        std::set<int64_t>::iterator iter = fsids.begin();
        while (iter != fsids.end()) {
            entry = this->fsidToFeatureDb.find(*iter);
            if (entry == this->fsidToFeatureDb.end()) {
                iter = fsids.erase(iter);
            } else {
                bool visible = false;
                code = this->isFeatureSetVisible(&visible, *iter);
                if (code != TE_Ok)
                    break;
                if (!visible)
                    iter = fsids.erase(iter);
                else
                    iter++;
            }
        }
        TE_CHECKRETURN_CODE(code);
    }
    if (!params.names->empty()) {
        std::set<int64_t>::iterator iter = fsids.begin();
        while (iter != fsids.end()) {
            entry = this->fsidToFeatureDb.find(*iter);
            if (entry == this->fsidToFeatureDb.end()) {
                iter = fsids.erase(iter);
            }
            else {
                bool matched = false;
                code = AbstractFeatureDataStore2::matches(&matched, *params.names, entry->second->name, '%');
                if (code != TE_Ok)
                    break;
                if (!matched)
                    iter = fsids.erase(iter);
                else
                    iter++;
            }
        }
        TE_CHECKRETURN_CODE(code);
    }

    std::set<std::shared_ptr<const FeatureDb>, FeatureSetCursorImpl::LT_featureSetName> dbs;
    for (std::set<int64_t>::iterator fsid = fsids.begin(); fsid != fsids.end(); fsid++) {
        entry = this->fsidToFeatureDb.find(*fsid);
        if (entry != this->fsidToFeatureDb.end())
            dbs.insert(entry->second);
    }

    if (params.offset != 0) {
        for (int i = 0; i < params.offset; i++) {
            if (dbs.empty())
                break;
            dbs.erase(dbs.begin());
        }
    }
    if (params.limit != 0) {
        while (dbs.size() > params.limit)
            dbs.erase(--dbs.end());
    }

    result = FeatureSetCursorPtr(new FeatureSetCursorImpl(dbs), Memory_deleter_const<FeatureSetCursor2, FeatureSetCursorImpl>);

    return code;
}

//public synchronized int queryFeatureSetsCount(FeatureSetQueryParameters params) {
TAKErr PersistentDataSourceFeatureDataStore2::queryFeatureSetsCount(int *value) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    *value = this->fsidToFeatureDb.size();
    return TE_Ok;
}
TAKErr PersistentDataSourceFeatureDataStore2::queryFeatureSetsCount(int *value, const FeatureSetQueryParameters &params) NOTHROWS
{
    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry;
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    code = TE_Ok;

    std::set<int64_t> fsids;
    for (entry = this->fsidToFeatureDb.begin(); entry != this->fsidToFeatureDb.end(); entry++)
        fsids.insert(entry->first);

    if (!params.ids->empty()) {
        std::set<int64_t>::iterator iter = fsids.begin();
        while (iter != fsids.end()) {
            bool contained;
            int64_t fsid = *iter;
            code = params.ids->contains(&contained, fsid);
            if (code != TE_Ok)
                break;
            if (contained)
                iter++;
            else
                iter = fsids.erase(iter);
        }
        TE_CHECKRETURN_CODE(code);
    }
    if (!params.types->empty()) {
        std::set<int64_t>::iterator iter = fsids.begin();
        while (iter != fsids.end()) {
            entry = this->fsidToFeatureDb.find(*iter);
            if (entry == this->fsidToFeatureDb.end()) {
                iter = fsids.erase(iter);
            } else {
                bool matched = false;
                code = AbstractFeatureDataStore2::matches(&matched, *params.types, entry->second->type, '%');
                if (code != TE_Ok)
                    break;
                if (!matched)
                    iter = fsids.erase(iter);
                else
                    iter++;
            }
        }
        TE_CHECKRETURN_CODE(code);
    }
    if (!params.providers->empty()) {
        std::set<int64_t>::iterator iter = fsids.begin();
        while (iter != fsids.end()) {
            entry = this->fsidToFeatureDb.find(*iter);
            if (entry == this->fsidToFeatureDb.end()) {
                iter = fsids.erase(iter);
            }
            else {
                bool matched = false;
                code = AbstractFeatureDataStore2::matches(&matched, *params.providers, entry->second->provider, '%');
                if (code != TE_Ok)
                    break;
                if (!matched)
                    iter = fsids.erase(iter);
                else
                    iter++;
            }
        }
        TE_CHECKRETURN_CODE(code);
    }
    if (params.visibleOnly) {
        std::set<int64_t>::iterator iter = fsids.begin();
        while (iter != fsids.end()) {
            entry = this->fsidToFeatureDb.find(*iter);
            if (entry == this->fsidToFeatureDb.end()) {
                iter = fsids.erase(iter);
            }
            else {
                bool visible = false;
                code = this->isFeatureSetVisible(&visible, *iter);
                if (code != TE_Ok)
                    break;
                if (!visible)
                    iter = fsids.erase(iter);
                else
                    iter++;
            }
        }
        TE_CHECKRETURN_CODE(code);
    }
    if (!params.names->empty()) {
        std::set<int64_t>::iterator iter = fsids.begin();
        while (iter != fsids.end()) {
            entry = this->fsidToFeatureDb.find(*iter);
            if (entry == this->fsidToFeatureDb.end()) {
                iter = fsids.erase(iter);
            }
            else {
                bool matched = false;
                code = AbstractFeatureDataStore2::matches(&matched, *params.names, entry->second->name, '%');
                if (code != TE_Ok)
                    break;
                if (!matched)
                    iter = fsids.erase(iter);
                else
                    iter++;
            }
        }
        TE_CHECKRETURN_CODE(code);
    }

    int retval = fsids.size();
    if (params.offset != 0)
        retval = (params.offset<retval) ? (retval-params.offset) : 0;
    if (params.limit && retval > params.limit)
        retval = params.limit;

    *value = retval;

    return code;
}

//public synchronized boolean isFeatureSetVisible(int64_t fsid) {
TAKErr PersistentDataSourceFeatureDataStore2::isFeatureSetVisible(bool *value, const int64_t fsid) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry = this->fsidToFeatureDb.find(fsid);
    if (entry == this->fsidToFeatureDb.end())
        return TE_InvalidArg;

    return entry->second->database->value->isFeatureSetVisible(value, fsid);
}

//public synchronized boolean isAvailable() {
TAKErr PersistentDataSourceFeatureDataStore2::isAvailable(bool *value) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    *value = !!this->index;
    return TE_Ok;
}

//public synchronized void refresh() {
TAKErr PersistentDataSourceFeatureDataStore2::refresh() NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    if (!this->index)
        return TE_IllegalState;
    code = this->index->validateCatalog();
    TE_CHECKRETURN_CODE(code);
    this->setContentChanged();
    return TE_Ok;
}

//public String getUri() {
TAKErr PersistentDataSourceFeatureDataStore2::getUri(TAK::Engine::Port::String &value) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    value = this->databaseDir;
    return TE_Ok;
}

TAKErr PersistentDataSourceFeatureDataStore2::close() NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    if (!this->index) {
        Logger::log(Logger::Error, "PersistentDataSourceFeatureDataStore2::close() already closed.");
        return TE_IllegalState;
    }
    return closeImpl();
}

TAKErr PersistentDataSourceFeatureDataStore2::closeImpl() NOTHROWS
{
    if (this->index) {
        delete this->index;
        this->index = NULL;
    }
    this->indexDb.reset();

    this->fsidToFeatureDb.clear();

    return TE_Ok;
}


//protected boolean setFeatureVisibleImpl(int64_t fid, boolean visible) {
TAKErr PersistentDataSourceFeatureDataStore2::setFeatureVisibleImpl(const int64_t fid, const bool visible) NOTHROWS
{
    TAKErr code;
    FeatureQueryParameters params;
    params.featureIds->add(fid);
    code = this->setFeaturesVisibleImpl(params, visible);
    TE_CHECKRETURN_CODE(code);
    this->setContentChanged();
    return code;
}

//protected boolean setFeaturesVisibleImpl(FeatureQueryParameters params, boolean visible) {
TAKErr PersistentDataSourceFeatureDataStore2::setFeaturesVisibleImpl(const FeatureQueryParameters &params, const bool visible) NOTHROWS
{
    TAKErr code;
    std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureQueryParameters>> dbs;
    code = this->prepareQuery(dbs, &params);

    // if there is a limit/offset specified and we are going to be
    // querying more than one database we will need to brute force;
    // strip the offset/limit off of any DB instance queries
    if (dbs.size() > 1 && params.limit != 0) {
        std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureQueryParameters>>::iterator dbParams;
        for (dbParams = dbs.begin(); dbParams != dbs.end(); dbParams++) {
            if (dbParams->second) {
                dbParams->second->offset = 0;
                dbParams->second->limit = 0;

                // XXX - limit/offset broken here!!!
            }
        }
    }

    std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureQueryParameters>>::iterator db;
    for (db = dbs.begin(); db != dbs.end(); db++) {
        if (db->second)
            code = db->first->value->setFeaturesVisible(*db->second, visible);
        else
            code = db->first->value->setFeaturesVisible(FeatureQueryParameters(), visible);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    this->setContentChanged();
    return code;
}

//protected synchronized boolean setFeatureSetVisibleImpl(int64_t setId, boolean visible)
TAKErr PersistentDataSourceFeatureDataStore2::setFeatureSetVisibleImpl(const int64_t fsid, const bool visible) NOTHROWS
{
    TAKErr code(TE_Ok);
    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator db = this->fsidToFeatureDb.find(fsid);
    if (db == this->fsidToFeatureDb.end())
        return TE_InvalidArg;
    code = db->second->database->value->setFeatureSetVisible(db->second->fsid, visible);
    TE_CHECKRETURN_CODE(code);
    this->setContentChanged();
    return code;
}


//protected synchronized boolean setFeatureSetsVisibleImpl(FeatureSetQueryParameters params, boolean visible) {
TAKErr PersistentDataSourceFeatureDataStore2::setFeatureSetsVisibleImpl(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS
{
    TAKErr code;
    std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureSetQueryParameters>> dbs;
    code = this->prepareQuery(dbs, &params);

    std::map<std::shared_ptr<DatabaseRef>, std::shared_ptr<FeatureSetQueryParameters>>::iterator entry;
    for (entry = dbs.begin(); entry != dbs.end(); entry++) {
        if (entry->second)
            code = entry->first->value->setFeatureSetsVisible(*entry->second, visible);
        else
            code = entry->first->value->setFeatureSetsVisible(FeatureSetQueryParameters(), visible);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);
    this->setContentChanged();
    return code;
}


//protected void beginBulkModificationImpl() {}
TAKErr PersistentDataSourceFeatureDataStore2::beginBulkModificationImpl() NOTHROWS
{
    return TE_Ok;
}

//protected boolean endBulkModificationImpl(boolean successful)
TAKErr PersistentDataSourceFeatureDataStore2::endBulkModificationImpl(const bool successful) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    this->setContentChanged();

    // no-op
    return TE_Ok;
}

//protected boolean deleteAllFeatureSetsImpl() {
TAKErr PersistentDataSourceFeatureDataStore2::deleteAllFeatureSetsImpl() NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    code = this->index->deleteAll();
    TE_CHECKRETURN_CODE(code);
    this->setContentChanged();
    return code;
}

//protected boolean insertFeatureSetImpl(String provider, String type, String name, double minResolution, double maxResolution, FeatureSet[] returnRef) {
TAKErr PersistentDataSourceFeatureDataStore2::insertFeatureSetImpl(FeatureSetPtr_const *value, const char *provider, const char *type, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    return TE_Unsupported;
}

//protected boolean updateFeatureSetImpl(int64_t fsid, String name) {
TAKErr PersistentDataSourceFeatureDataStore2::updateFeatureSetImpl(const int64_t fsid, const char *name) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry;
    entry = this->fsidToFeatureDb.find(fsid);
    if (entry == this->fsidToFeatureDb.end())
        return TE_InvalidArg;

    StatementPtr stmt(NULL, NULL);
    code = this->indexDb->compileStatement(stmt,
        "UPDATE featuresets SET name = ? WHERE id = ?");

    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(1, name);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(2, fsid);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    code = entry->second->database->value->updateFeatureSet(fsid, name);
    if (code == TE_Ok) {
        this->markFileDirty(fsid);
        this->setContentChanged();
    }

    entry->second->name = name;
    entry->second->version++;

    return code;
}

//protected boolean updateFeatureSetImpl(int64_t fsid, double minResolution, double maxResolution) {
TAKErr PersistentDataSourceFeatureDataStore2::updateFeatureSetImpl(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry;
    entry = this->fsidToFeatureDb.find(fsid);
    if (entry == this->fsidToFeatureDb.end())
        return TE_InvalidArg;
    code = entry->second->database->value->updateFeatureSet(fsid, minResolution, maxResolution);
    if (code == TE_Ok) {
        this->markFileDirty(fsid);
        this->setContentChanged();
    }

    entry->second->minResolution = minResolution;
    entry->second->maxResolution = maxResolution;
    entry->second->version++;

    return code;
}

//protected boolean updateFeatureSetImpl(int64_t fsid, String name, double minResolution, double maxResolution) {
TAKErr PersistentDataSourceFeatureDataStore2::updateFeatureSetImpl(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry;
    entry = this->fsidToFeatureDb.find(fsid);
    if (entry == this->fsidToFeatureDb.end())
        return TE_InvalidArg;

    StatementPtr stmt(NULL, NULL);
    code = this->indexDb->compileStatement(stmt,
        "UPDATE featuresets SET name = ? WHERE id = ?");

    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(1, name);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(2, fsid);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    code = entry->second->database->value->updateFeatureSet(fsid, name, minResolution, maxResolution);
    if (code == TE_Ok) {
        this->markFileDirty(fsid);
        this->setContentChanged();
    }

    entry->second->name = name;
    entry->second->minResolution = minResolution;
    entry->second->maxResolution = maxResolution;
    entry->second->version++;

    return code;
}

//protected boolean deleteFeatureSetImpl(int64_t fsid) {
TAKErr PersistentDataSourceFeatureDataStore2::deleteFeatureSetImpl(const int64_t fsid) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry;
    entry = this->fsidToFeatureDb.find(fsid);
    if (entry == this->fsidToFeatureDb.end())
        return TE_InvalidArg;

    code = entry->second->database->value->deleteFeatureSet(fsid);
    TE_CHECKRETURN_CODE(code);

    // erase the FDB entry
    this->fsidToFeatureDb.erase(entry);

    // mark the file dirty
    this->markFileDirty(fsid);

    // delete the index record
    StatementPtr stmt(NULL, NULL);
    code = this->indexDb->compileStatement(stmt, "DELETE FROM featuresets WHERE id = ?");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(1, fsid);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    this->setContentChanged();

    return code;
}

//protected boolean insertFeatureImpl(int64_t fsid, String name, Geometry geom, Style style, AttributeSet attributes, Feature[] returnRef) {
TAKErr PersistentDataSourceFeatureDataStore2::insertFeatureImpl(FeaturePtr_const *value, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry;
    entry = this->fsidToFeatureDb.find(fsid);
    if (entry == this->fsidToFeatureDb.end())
        return TE_InvalidArg;
    code = entry->second->database->value->insertFeature(value, fsid, name, geom, style, attributes);
    if (code == TE_Ok) {
        markFileDirty(fsid);
        this->setContentChanged();

        // need to adjust FID for FSID masking
        if (value && value->get()) {
            const Feature2 *inserted = value->get();
            GeometryPtr_const insertedGeom = GeometryPtr_const(inserted->getGeometry() ? inserted->getGeometry()->clone() : NULL, atakmap::feature::destructGeometry);
            StylePtr_const insertedStyle = StylePtr_const(inserted->getStyle() ? inserted->getStyle()->clone() : NULL, atakmap::feature::Style::destructStyle);
            AttributeSetPtr_const insertedAttributes = AttributeSetPtr_const(inserted->getAttributes() ? new AttributeSet(*inserted->getAttributes()) : new AttributeSet(), Memory_deleter_const<AttributeSet>);

            *value = FeaturePtr_const(new Feature2((fsid << 32) | inserted->getId(),
                                                   fsid,
                                                   inserted->getName(),
                                                   std::move(insertedGeom),
                                                   std::move(insertedStyle),
                                                   std::move(insertedAttributes),
                                                   inserted->getVersion()),
                                      Memory_deleter_const<Feature2>);
        }
    }
    return code;
}

//protected boolean updateFeatureImpl(int64_t fid, String name) {
TAKErr PersistentDataSourceFeatureDataStore2::updateFeatureImpl(const int64_t fid, const char *name) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    const int64_t fsid = (fid >> 32) & 0xFFFFFFFFLL;
    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry;
    entry = this->fsidToFeatureDb.find(fsid);
    if (entry == this->fsidToFeatureDb.end())
        return TE_InvalidArg;
    code =  entry->second->database->value->updateFeature(fid & 0xFFFFFFFFLL, name);
    if (code == TE_Ok) {
        this->markFileDirty(fsid);
        this->setContentChanged();
    }
    return code;
}

//protected boolean updateFeatureImpl(int64_t fid, Geometry geom) {
TAKErr PersistentDataSourceFeatureDataStore2::updateFeatureImpl(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    const int64_t fsid = (fid >> 32) & 0xFFFFFFFFLL;
    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry;
    entry = this->fsidToFeatureDb.find(fsid);
    if (entry == this->fsidToFeatureDb.end())
        return TE_InvalidArg;
    code = entry->second->database->value->updateFeature(fid & 0xFFFFFFFFLL, geom);
    if (code == TE_Ok) {
        this->markFileDirty(fsid);
        this->setContentChanged();
    }
    return code;
}


//protected boolean updateFeatureImpl(int64_t fid, Style style) {
TAKErr PersistentDataSourceFeatureDataStore2::updateFeatureImpl(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    const int64_t fsid = (fid >> 32) & 0xFFFFFFFFLL;
    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry;
    entry = this->fsidToFeatureDb.find(fsid);
    if (entry == this->fsidToFeatureDb.end())
        return TE_InvalidArg;
    code = entry->second->database->value->updateFeature(fid & 0xFFFFFFFFLL, style);
    if (code == TE_Ok) {
        this->markFileDirty(fsid);
        this->setContentChanged();
    }
    return code;
}

//protected boolean updateFeatureImpl(int64_t fid, AttributeSet attributes) {
TAKErr PersistentDataSourceFeatureDataStore2::updateFeatureImpl(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    const int64_t fsid = (fid >> 32) & 0xFFFFFFFFLL;
    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry;
    entry = this->fsidToFeatureDb.find(fsid);
    if (entry == this->fsidToFeatureDb.end())
        return TE_InvalidArg;
    code = entry->second->database->value->updateFeature(fid & 0xFFFFFFFFLL, attributes);
    if (code == TE_Ok) {
        this->markFileDirty(fsid);
        this->setContentChanged();
    }
    return code;
}

//protected boolean updateFeatureImpl(int64_t fid, String name, Geometry geom, Style style, AttributeSet attributes) {
TAKErr PersistentDataSourceFeatureDataStore2::updateFeatureImpl(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    const int64_t fsid = (fid >> 32) & 0xFFFFFFFFLL;
    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry;
    entry = this->fsidToFeatureDb.find(fsid);
    if (entry == this->fsidToFeatureDb.end())
        return TE_InvalidArg;
    code = entry->second->database->value->updateFeature(fid & 0xFFFFFFFFLL, name, geom, style, attributes);
    if (code == TE_Ok) {
        this->markFileDirty(fsid);
        this->setContentChanged();
    }
    return code;
}

//protected boolean deleteFeatureImpl(int64_t fsid) {
TAKErr PersistentDataSourceFeatureDataStore2::deleteFeatureImpl(const int64_t fid) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    const int64_t fsid = (fid >> 32) & 0xFFFFFFFFLL;
    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry;
    entry = this->fsidToFeatureDb.find(fsid);
    if (entry == this->fsidToFeatureDb.end())
        return TE_InvalidArg;
    code = entry->second->database->value->deleteFeature(fid & 0xFFFFFFFFLL);
    if (code == TE_Ok) {
        this->markFileDirty(fsid);
        this->setContentChanged();
    }
    return code;
}

//protected boolean deleteAllFeaturesImpl(int64_t fsid) {
TAKErr PersistentDataSourceFeatureDataStore2::deleteAllFeaturesImpl(int64_t fsid) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry;
    entry = this->fsidToFeatureDb.find(fsid);
    if (entry == this->fsidToFeatureDb.end())
        return TE_InvalidArg;
    code = entry->second->database->value->deleteAllFeatures(fsid);
    if (code == TE_Ok) {
        this->markFileDirty(fsid);
        this->setContentChanged();
    }
    return code;
}

//synchronized boolean contains(File file) {
TAKErr PersistentDataSourceFeatureDataStore2::contains(bool *value, const char *file) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    CatalogDatabase2::CatalogCursorPtr result(NULL, NULL);
    code = this->index->queryCatalogPath(result, file);
    TE_CHECKRETURN_CODE(code);

    code = result->moveToNext();
    *value = (code == TE_Ok);

    return (code == TE_Done) ? TE_Ok : code;
}


//public synchronized File getFile(FeatureSet info)
TAKErr PersistentDataSourceFeatureDataStore2::getFile(TAK::Engine::Port::String &value, const int64_t fsid) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    return this->getFileImpl(value, fsid);
}


//    private File getFileImpl(int64_t fsid) {
TAKErr PersistentDataSourceFeatureDataStore2::getFileImpl(TAK::Engine::Port::String &value, const int64_t fsid) NOTHROWS
{
    TAKErr code(TE_Ok);

    CatalogDatabase2::CatalogCursorPtr result(NULL, NULL);
    code = this->index->query(result, fsid);
    TE_CHECKRETURN_CODE(code);

    code = result->moveToNext();
    if (code == TE_Done)
        return TE_InvalidArg;
    TE_CHECKRETURN_CODE(code);

    const char *path(NULL);
    code = result->getPath(&path);
    TE_CHECKRETURN_CODE(code);

    value = path;
    return code;
}

//public boolean add(File file) throws IOException{
TAKErr PersistentDataSourceFeatureDataStore2::add(const char *file) NOTHROWS
{
    return this->add(file, nullptr);
}

//public boolean add(File file, String hint) throws IOException{
TAKErr PersistentDataSourceFeatureDataStore2::add(const char *cfile, const char *hint) NOTHROWS
{
    return addImpl(cfile, hint);
}

TAKErr PersistentDataSourceFeatureDataStore2::addImpl(const char* cfile, const char* hint) NOTHROWS {
    TAKErr code(TE_Ok);
    AddMgr pendingMgr(*this);

    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    if (this->pending.find(cfile) != this->pending.end()) {
        while (this->pending.find(cfile) != this->pending.end()) this->cond.wait(*lock);
        bool contained;
        code = this->contains(&contained, cfile);
        TE_CHECKRETURN_CODE(code);

        return contained ? TE_Ok : TE_Err;
    }
    pendingMgr.markPending(cfile);

    // create data source
    FeatureDataSource2::ContentPtr content(NULL, NULL);
    code = FeatureDataSourceFactory_parse(content, cfile, hint);

    TAK::Engine::Port::String file(cfile);
#ifdef MSVC
    // XXX - need to define portable case-insensitive 'strstr' function
    const char *extStart = strstr(cfile, ".zip");
#else
    const char *extStart = strcasestr(cfile, ".zip");
#endif
    if(extStart) {
        file[(int)(extStart-cfile+4u)] = '\0';
    }

    Logger::log(Logger::Debug, TAG ": Adding %s (%s) -> %p", atakmap::util::getFileName(file).c_str(), hint, content.get());
    TE_CHECKRETURN_CODE(code);

    int64_t catalogId;
    {
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, mutex);
        TE_CHECKRETURN_CODE(code);
        GenerateCurrency currency;
        code = currency.init(*content);
        TE_CHECKRETURN_CODE(code);
        code = this->index->addCatalogEntry(&catalogId, file, currency);
        TE_CHECKRETURN_CODE(code);

        pendingMgr.setCatalogId(catalogId);

        StatementPtr stmt(NULL, NULL);

        code = this->indexDb->compileStatement(
            stmt, "INSERT INTO catalog_ex (file_id, provider, type, fdb_type, modified) VALUES (?, ?, ?, NULL, 0)");
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindLong(1, catalogId);
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindString(2, content->getProvider());
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindString(3, content->getType());
        TE_CHECKRETURN_CODE(code);

        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);

		// rebuild the template if it does not exist before we proceed to generating the FDBs
		if (!pathExists(this->fdbTemplate)) {
			FeatureSetDatabase::Builder t;
			code = t.create(this->fdbTemplate);
			TE_CHECKRETURN_CODE(code);
		}
    }

    std::map<std::string, std::set<std::shared_ptr<FeatureDb>>> dbs;

    bool atLimit(false);
    do {
        bool available;
        code = this->isAvailable(&available);
        TE_CHECKBREAK_CODE(code);
        code = generateFDB(dbs, nullptr, &atLimit, file, catalogId, *content, pendingMgr, FDB_FEATURESET_LIMIT);
        TE_CHECKBREAK_CODE(code);
    } while (atLimit);
    TE_CHECKRETURN_CODE(code);

    {
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, mutex);
        TE_CHECKRETURN_CODE(code);

        // bulk insert of featuresets as atomic operation

        // check if the datastore has been closed
        bool available;
        code = this->isAvailable(&available);
        TE_CHECKRETURN_CODE(code);

        if (!available) return TE_IllegalState;

        int visibilityFlags;
        code = this->getVisibilitySettingsFlags(&visibilityFlags);
        TE_CHECKRETURN_CODE(code);

        int modificationFlags;
        code = this->getModificationFlags(&modificationFlags);
        TE_CHECKRETURN_CODE(code);

        std::map<std::string, std::set<std::shared_ptr<FeatureDb>>>::iterator dbIter;
        for (dbIter = dbs.begin(); dbIter != dbs.end(); dbIter++) {
            std::unique_ptr<FeatureSetDatabase, void (*)(const FeatureDataStore2 *)> fdbImpl(
                new FeatureSetDatabase(modificationFlags, visibilityFlags), Memory_deleter_const<FeatureDataStore2, FeatureSetDatabase>);
            code = fdbImpl->open(dbIter->first.c_str());
            TE_CHECKRETURN_CODE(code);

            std::shared_ptr<DatabaseRef> fdb(new DatabaseRef(std::move(fdbImpl), dbIter->first.c_str()));

            std::set<std::shared_ptr<FeatureDb>>::iterator db;
            for (db = dbIter->second.begin(); db != dbIter->second.end(); db++) {
                (*db)->database = fdb;

                // add FDB
                this->fsidToFeatureDb[(*db)->fsid] = *db;
                                
                code = this->index->setFeatureSetCatalogId((*db)->fsid, catalogId);
                TE_CHECKBREAK_CODE(code);
            }

            if (dbs.size() > 1) this->sharedDbs.insert(fdb);
        }
        this->setContentChanged();
        this->dispatchDataStoreContentChangedNoSync(false);
    }

    pendingMgr.setSuccessful();

    return code;
}

TAKErr PersistentDataSourceFeatureDataStore2::updateImpl(const char *cfile, const char *hint) NOTHROWS {
    TAKErr code(TE_Ok);
    AddMgr pendingMgr(*this);

    // create data source
    FeatureDataSource2::ContentPtr content(NULL, NULL);
    code = FeatureDataSourceFactory_parse(content, cfile, hint);

    TAK::Engine::Port::String file(cfile);
#ifdef MSVC
    // XXX - need to define portable case-insensitive 'strstr' function
    const char *extStart = strstr(cfile, ".zip");
#else
    const char *extStart = strcasestr(cfile, ".zip");
#endif
    if (extStart) {
        file[(int)(extStart - cfile + 4u)] = '\0';
    }

    Logger::log(Logger::Debug, TAG ": Updating %s (%s) -> %p", atakmap::util::getFileName(file).c_str(), hint, content.get());
    TE_CHECKRETURN_CODE(code);

    int64_t catalogId;
    {
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, mutex);
        TE_CHECKRETURN_CODE(code);

        QueryPtr query(nullptr, nullptr);
        code = this->indexDb->compileQuery(query, "SELECT id FROM catalog WHERE path = ?");
        TE_CHECKRETURN_CODE(code);

        code = query->bindString(1, cfile);
        TE_CHECKRETURN_CODE(code);

        code = query->moveToNext();
        TE_CHECKRETURN_CODE(code);

        code = query->getLong(&catalogId, 0);
        TE_CHECKRETURN_CODE(code);

        GenerateCurrency currency;
        code = currency.init(*content);
        TE_CHECKRETURN_CODE(code);
        code = this->index->updateCatalogEntry(catalogId, file, currency);
        TE_CHECKRETURN_CODE(code);
    }

    std::list<int64_t> staleFsids;
    std::map<std::string, std::tuple<int64_t,int64_t>> replacementFsidMap;
    {
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, mutex);
        TE_CHECKRETURN_CODE(code);

        QueryPtr query(nullptr, nullptr);
        code = this->indexDb->compileQuery(query, "SELECT id, name FROM featuresets WHERE file_id = ?");
        TE_CHECKRETURN_CODE(code);

        code = query->bindLong(1, catalogId);
        TE_CHECKRETURN_CODE(code);

        int64_t staleFsid = 0;
        const char *staleFsName;
        while (query->moveToNext() == TE_Ok) {
            code = query->getLong(&staleFsid, 0);
            TE_CHECKBREAK_CODE(code);
            code = query->getString(&staleFsName, 1);
            TE_CHECKBREAK_CODE(code);

            staleFsids.push_back(staleFsid);

            int64_t updateVersion(0);
            std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator staleDbIter;
            staleDbIter = this->fsidToFeatureDb.find(staleFsid);
            if (staleDbIter != this->fsidToFeatureDb.end()) {
                FDB *fdb = dynamic_cast<FDB *>(staleDbIter->second->database->value);
                if (fdb != nullptr) {
                    fdb->getMaxFeatureVersion(staleFsid, &updateVersion);
                }

                StatementPtr stmt(NULL, NULL);
                code = this->indexDb->compileStatement(stmt, "DELETE FROM featuresets WHERE id = ?");
                TE_CHECKBREAK_CODE(code);

                code = stmt->bindLong(1, staleFsid);
                TE_CHECKBREAK_CODE(code);
                code = stmt->execute();
                TE_CHECKBREAK_CODE(code);
            }

            replacementFsidMap.insert(std::make_pair(std::string(staleFsName), std::make_tuple(staleFsid, updateVersion)));
        }
    }

    std::map<std::string, std::set<std::shared_ptr<FeatureDb>>> dbs;

    bool atLimit(false);
    do {
        bool available;
        code = this->isAvailable(&available);
        TE_CHECKBREAK_CODE(code);
        code = generateFDB(dbs, &replacementFsidMap, &atLimit, file, catalogId, *content, pendingMgr, FDB_FEATURESET_LIMIT);
        TE_CHECKBREAK_CODE(code);
    } while (atLimit);
    TE_CHECKRETURN_CODE(code);

    {
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, mutex);
        TE_CHECKRETURN_CODE(code);

        // bulk insert of featuresets as atomic operation

        // check if the datastore has been closed
        bool available;
        code = this->isAvailable(&available);
        TE_CHECKRETURN_CODE(code);

        if (!available) return TE_IllegalState;

        int visibilityFlags;
        code = this->getVisibilitySettingsFlags(&visibilityFlags);
        TE_CHECKRETURN_CODE(code);

        int modificationFlags;
        code = this->getModificationFlags(&modificationFlags);
        TE_CHECKRETURN_CODE(code);

        std::map<std::string, std::set<std::shared_ptr<FeatureDb>>>::iterator dbIter;
        for (dbIter = dbs.begin(); dbIter != dbs.end(); dbIter++) {
            std::unique_ptr<FeatureSetDatabase, void (*)(const FeatureDataStore2 *)> fdbImpl(
                new FeatureSetDatabase(modificationFlags, visibilityFlags), Memory_deleter_const<FeatureDataStore2, FeatureSetDatabase>);
            code = fdbImpl->open(dbIter->first.c_str());
            TE_CHECKRETURN_CODE(code);

            std::shared_ptr<DatabaseRef> fdb(new DatabaseRef(std::move(fdbImpl), dbIter->first.c_str()));

            std::set<std::shared_ptr<FeatureDb>>::iterator db;
            for (db = dbIter->second.begin(); db != dbIter->second.end(); db++) {
                (*db)->database = fdb;

                std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator staleDbIter = this->fsidToFeatureDb.find((*db)->fsid);
                if (staleDbIter != this->fsidToFeatureDb.end()) {
                    staleDbIter->second->database->markForDelete();
                    this->sharedDbs.erase(staleDbIter->second->database);
                    this->fsidToFeatureDb.erase((*db)->fsid);
                    staleFsids.remove((*db)->fsid);
                }

                // add FDB
                this->fsidToFeatureDb[(*db)->fsid] = *db;

                code = this->index->setFeatureSetCatalogId((*db)->fsid, catalogId);
                TE_CHECKBREAK_CODE(code);
            }

            if (dbs.size() > 1) this->sharedDbs.insert(fdb);
        }

        // Cleanup any other stale featuresets that weren't replaced
        for (int64_t staleFsid : staleFsids) {
            std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator staleDbIter = this->fsidToFeatureDb.find(staleFsid);
            if (staleDbIter != this->fsidToFeatureDb.end()) {
                staleDbIter->second->database->markForDelete();
                this->sharedDbs.erase(staleDbIter->second->database);
                this->fsidToFeatureDb.erase(staleFsid);
                staleFsids.remove(staleFsid);
            }
        }
        this->setContentChanged();
        this->dispatchDataStoreContentChangedNoSync(false);
    }

    pendingMgr.setSuccessful();

    return code;
}

TAKErr PersistentDataSourceFeatureDataStore2::generateFDB(std::map<std::string, std::set<std::shared_ptr<FeatureDb>>> &dbsMap,
                                                          std::map<std::string, std::tuple<int64_t,int64_t>>* replacementFsids, bool *atLimit, const char *file,
                                                          const int64_t catalogId, FeatureDataSource2::Content &content, AddMgr &pendingMgr,
                                                          const std::size_t featureSetLimit) NOTHROWS {
    TAKErr code(TE_Ok);

    std::set<std::shared_ptr<FeatureDb>> dbs;
    TAK::Engine::Port::String fdbFile;

    // iterate data source into FDB builders
    std::unique_ptr<FeatureSetDatabase::Builder> builder;

    *atLimit = false;

    // create the FDB file
    code = IO_createTempFile(
        fdbFile,
        getFileName(file).c_str(),
        ".sqlite",
        this->fdbsDir);
    TE_CHECKRETURN_CODE(code);

    pendingMgr.addFDB(fdbFile);

    // try to copy the template rather than building the spatial
    // metadata every time

    if (pathExists(fdbFile))
        deletePath(fdbFile);

    code = IO_copy(fdbFile, this->fdbTemplate);
    if (code != TE_IO) {
        TE_CHECKRETURN_CODE(code);
    }

    builder.reset(new FeatureSetDatabase::Builder());
    code = builder->create(fdbFile);
    TE_CHECKRETURN_CODE(code);

    std::size_t featureSetCountThisFdb = 0;
    {
        FeatureSetDatabase::Builder::BulkInsertion bulkInsert(*builder);
        code = bulkInsert.checkInit();
        TE_CHECKRETURN_CODE(code);

        int featureCount;
        int64_t fsid;
        int64_t version;
        do {
            code = content.moveToNextFeatureSet();
            TE_CHECKBREAK_CODE(code);

            featureCount = 0;

            TAK::Engine::Port::String fsName;

            bool replace(false);
            {
                LockPtr lock(NULL, NULL);
                code = Lock_create(lock, mutex);
                TE_CHECKBREAK_CODE(code);
                if (!this->index)
                    break;
                code = content.getFeatureSetName(fsName);
                TE_CHECKBREAK_CODE(code);

                if (replacementFsids != nullptr) {
                    std::string nameKey(fsName);
                    auto replacementIter = replacementFsids->find(nameKey);
                    if (replacementIter != replacementFsids->end()) {
                        fsid = std::get<0>(replacementIter->second);
                        version = std::get<1>(replacementIter->second) + 1;
                        replace = true;
                        replacementFsids->erase(nameKey);
                    }
                }

                if (replace) {
                    code = this->index->reserveFeatureSet(fsid, fsName, fdbFile, content.getProvider(), content.getType());
                } else {
                    code = this->index->reserveFeatureSet(&fsid, fsName, fdbFile, content.getProvider(), content.getType());
                }
                TE_CHECKBREAK_CODE(code);
            }

            code = builder->insertFeatureSet(
                fsid,
                content.getProvider(),
                content.getType(),
                fsName);
            TE_CHECKBREAK_CODE(code);

            bool featureSetVisible(true);
            code = content.getFeatureSetVisible(&featureSetVisible);
            TE_CHECKBREAK_CODE(code);
            if (!featureSetVisible) {
                builder->setFeatureSetVisible(fsid, featureSetVisible);
            }

			int64_t fid;
            do {
                code = content.moveToNextFeature();
                TE_CHECKBREAK_CODE(code);
                FeatureDefinition2 *defn;
                code = content.get(&defn);
                TE_CHECKBREAK_CODE(code);
                code = builder->insertFeature(&fid, fsid, *defn);
                TE_CHECKBREAK_CODE(code);

				bool visible(true);
				code = content.getVisible(&visible);
				TE_CHECKBREAK_CODE(code);
				if (!visible) {
					builder->setFeatureVisible(fid, visible);
				}
                if (replace) {
                    builder->setFeatureVersion(fid, version);
                }

                featureCount++;

                // every 1K features, do a quick check to make sure the data
                // store has not been closed
                if ((featureCount % 250) == 0) {
                    // this isn't thread-safe, however, index is only likely to
                    // go in one direction
                    if (!this->index)
                        break;
                }
            } while (true);
            if (code != TE_Done && code != TE_Ok)
                break;

            double minRes;
            code = content.getMinResolution(&minRes);
            TE_CHECKBREAK_CODE(code);
            double maxRes;
            code = content.getMaxResolution(&maxRes);
            TE_CHECKBREAK_CODE(code);

            code = builder->updateFeatureSet(fsid,
                minRes,
                maxRes);
            TE_CHECKBREAK_CODE(code);

            bool available;
            code = this->isAvailable(&available);
            TE_CHECKBREAK_CODE(code);

            if (!available)
                return TE_IllegalState;

            if (featureCount > 0) {
                // create the FDB
                std::unique_ptr<FeatureDb> db(new FeatureDb());
                db->fsid = fsid;
                code = content.getFeatureSetName(db->name);
                TE_CHECKBREAK_CODE(code);
                code = content.getMinResolution(&db->minResolution);
                TE_CHECKBREAK_CODE(code);
                code = content.getMaxResolution(&db->maxResolution);
                TE_CHECKBREAK_CODE(code);
                db->provider = content.getProvider();
                db->type = content.getType();
                db->version = 1;
                db->database.reset();
                db->fdbFile = fdbFile;
                db->catalogId = catalogId;

                dbs.insert(std::move(db));
                featureSetCountThisFdb++;
            } else {
                code = builder->deleteFeatureSet(fsid);
                TE_CHECKBREAK_CODE(code);

                {
                    LockPtr lock(NULL, NULL);
                    code = Lock_create(lock, mutex);
                    TE_CHECKBREAK_CODE(code);
                    StatementPtr stmt(NULL, NULL);

                    code = this->indexDb->compileStatement(stmt, "DELETE FROM featuresets WHERE id = ?");
                    TE_CHECKBREAK_CODE(code);

                    code = stmt->bindLong(1, fsid);
                    TE_CHECKBREAK_CODE(code);
                    code = stmt->execute();
                    TE_CHECKBREAK_CODE(code);
                }
            }

            // restrict the number of featuresets per FDB to the specified limit
            if (featureSetLimit && featureSetCountThisFdb >= featureSetLimit) {
                *atLimit = true;
                break;
            }
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;

        do {
            TE_CHECKBREAK_CODE(code);

            // create the indices
            code = builder->createIndices();
            TE_CHECKBREAK_CODE(code);

            // if the datastore is still available mark the insert  successful
            bool available;
            code = this->isAvailable(&available);
            TE_CHECKBREAK_CODE(code);

            if (!dbs.empty() && available) {
                code = bulkInsert.setSuccessful();
                TE_CHECKBREAK_CODE(code);
            }
        } while (false);
    }
    builder.reset();

    if (code == TE_Ok && !dbs.empty()) {
        // update the mapping
        dbsMap[fdbFile.get()] = dbs;
    } else {
        // if no feature sets were generated, delete the FDB file
        bool wasDeleted = deletePath(fdbFile);
        dbs.clear();
    }

    return code;
}

TAKErr PersistentDataSourceFeatureDataStore2::markFileDirty(const int64_t fsid) NOTHROWS
{
    TAKErr code(TE_Ok);

    StatementPtr stmt(NULL, NULL);
    code = indexDb->compileStatement(stmt, "UPDATE catalog_ex SET modified = 1 WHERE file_id IN (SELECT file_id FROM featuresets WHERE id = ? LIMIT 1)");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(1, fsid);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);
    return code;
}

//public synchronized void remove(File file) {
TAKErr PersistentDataSourceFeatureDataStore2::remove(const char *file) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    code = this->index->deleteCatalogPath(file);
    TE_CHECKRETURN_CODE(code);
    this->setContentChanged();
    this->dispatchDataStoreContentChangedNoSync(false);
    return code;
}

//public boolean update(File file) throws IOException{
TAKErr PersistentDataSourceFeatureDataStore2::update(const char *file) NOTHROWS
{
    TAKErr code;

    bool b;
    code = this->contains(&b, file);
    TE_CHECKRETURN_CODE(code);
    if (!b)
        return TE_InvalidArg;

    return this->updateImpl(file, nullptr);
}


//public boolean update(FeatureSet featureSet) throws IOException{
TAKErr PersistentDataSourceFeatureDataStore2::update(const int64_t fsid) NOTHROWS
{
    TAKErr code;
    TAK::Engine::Port::String file;
    code = this->getFile(file, fsid);
    TE_CHECKRETURN_CODE(code);

    return this->update(file);
}


//public FileCursor queryFiles() {
TAKErr PersistentDataSourceFeatureDataStore2::queryFiles(FileCursorPtr &result) NOTHROWS
{
    TAKErr code;
    CatalogDatabase2::CatalogCursorPtr impl(NULL, NULL);
    code = this->index->queryCatalog(impl);
    TE_CHECKRETURN_CODE(code);

    result = FileCursorPtr(new FileCursorImpl(std::move(impl)), Memory_deleter_const<FileCursor, FileCursorImpl>);
    return code;
}

TAKErr PersistentDataSourceFeatureDataStore2::isModified(bool *value, const char *file) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    QueryPtr query(NULL, NULL);
    code = this->indexDb->compileQuery(query, "SELECT 1 FROM catalog_ex LEFT JOIN catalog ON catalog.id = catalog_ex.file_id WHERE catalog.path = ? AND modified != 0 LIMIT 1");
    TE_CHECKRETURN_CODE(code);

    code = query->bindString(1, file);
    TE_CHECKRETURN_CODE(code);

    code = query->moveToNext();
    *value = (code == TE_Ok) ? true : false;
    if(code == TE_Done)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);

    return code;
}
TAKErr PersistentDataSourceFeatureDataStore2::queryFiles(FileCursorPtr &cursor, bool modifiedOnly) NOTHROWS
{
    if (!modifiedOnly) {
        return queryFiles(cursor);
    } else {
        TAKErr code(TE_Ok);
        QueryPtr query(NULL, NULL);
        code = indexDb->compileQuery(query, "SELECT catalog.path FROM catalog LEFT JOIN catalog_ex ON catalog_ex.file_id = catalog.id WHERE catalog_ex.modified != 0");
        TE_CHECKRETURN_CODE(code);
        cursor = FileCursorPtr(new ModifiedFileCursorImpl(std::move(query)), Memory_deleter_const<FileCursor, ModifiedFileCursorImpl>);
        return code;
    }
}
TAKErr PersistentDataSourceFeatureDataStore2::queryFeatures(FeatureCursorPtr &result, const char *file) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    QueryPtr query(NULL, NULL);
    code = this->indexDb->compileQuery(query, "SELECT featuresets.id FROM featuresets LEFT JOIN catalog ON catalog.id = featuresets.file_id WHERE catalog.path = ?");
    TE_CHECKRETURN_CODE(code);

    code = query->bindString(1, file);
    TE_CHECKRETURN_CODE(code);

    FeatureQueryParameters params;

    do {
        code = query->moveToNext();
        TE_CHECKBREAK_CODE(code);

        int64_t fsid;
        code = query->getLong(&fsid, 0);
        TE_CHECKBREAK_CODE(code);

        code = params.featureSetIds->add(fsid);
        TE_CHECKBREAK_CODE(code);
    } while (true);
    if(code == TE_Done)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);

    return queryFeatures(result, params);
}

TAKErr PersistentDataSourceFeatureDataStore2::queryFeatureSets(FeatureSetCursorPtr &result, const char *file) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    QueryPtr query(NULL, NULL);
    code = this->indexDb->compileQuery(query, "SELECT featuresets.id FROM featuresets LEFT JOIN catalog ON catalog.id = featuresets.file_id WHERE catalog.path = ?");
    TE_CHECKRETURN_CODE(code);

    code = query->bindString(1, file);
    TE_CHECKRETURN_CODE(code);

    FeatureSetQueryParameters params;

    do {
        code = query->moveToNext();
        TE_CHECKBREAK_CODE(code);

        int64_t fsid;
        code = query->getLong(&fsid, 0);
        TE_CHECKBREAK_CODE(code);

        code = params.ids->add(fsid);
        TE_CHECKBREAK_CODE(code);
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);

    return queryFeatureSets(result, params);
}

TAKErr PersistentDataSourceFeatureDataStore2::insertFeatureSet(FeatureSetPtr_const *featureSet, const char *file, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);

    // get catalogid
    QueryPtr query(NULL, NULL);
    code = this->indexDb->compileQuery(query, "SELECT catalog_ex.file_id, catalog_ex.provider, catalog_ex.type FROM catalog LEFT JOIN catalog_ex ON catalog.id = catalog_ex.file_id WHERE path = ? LIMIT 1");
    TE_CHECKRETURN_CODE(code);
    code = query->bindString(1, file);
    TE_CHECKRETURN_CODE(code);

    code = query->moveToNext();
    if (code == TE_Done)
        return TE_InvalidArg;
    else if (code != TE_Ok)
        return code;

    const char *cstr;

    int64_t catalogid;
    code = query->getLong(&catalogid, 0);
    TE_CHECKRETURN_CODE(code);
    TAK::Engine::Port::String provider;
    code = query->getString(&cstr, 1);
    TE_CHECKRETURN_CODE(code);
    provider = cstr;
    TAK::Engine::Port::String type;
    code = query->getString(&cstr, 2);
    TE_CHECKRETURN_CODE(code);
    type = cstr;

    // check can add featureset to FDB
    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry;
    for (entry = this->fsidToFeatureDb.begin(); entry != this->fsidToFeatureDb.end(); entry++) {
        if (entry->second->catalogId != catalogid)
            continue;
        int fsCount;
        if (entry->second->database->value->queryFeatureSetsCount(&fsCount) != TE_Ok || fsCount >= FDB_FEATURESET_LIMIT)
            continue;
        break;
    }

    std::shared_ptr<FeatureDb> fdb(new FeatureDb());

    // create new FDB if necessary
    if (entry == this->fsidToFeatureDb.end()) {
        TAK::Engine::Port::String fdbFile;

        code = IO_createTempFile(
            fdbFile,
            getFileName(file).c_str(),
            ".sqlite",
            this->fdbsDir);
        TE_CHECKRETURN_CODE(code);

        int visibilityFlags;
        code = this->getVisibilitySettingsFlags(&visibilityFlags);
        TE_CHECKRETURN_CODE(code);

        int modificationFlags;
        code = this->getModificationFlags(&modificationFlags);
        TE_CHECKRETURN_CODE(code);

        FeatureDataStore2Ptr fdbImpl(
                new FeatureSetDatabase(modificationFlags, visibilityFlags),
                Memory_deleter_const<FeatureDataStore2, FeatureSetDatabase>);
        code = dynamic_cast<FeatureSetDatabase &>(*fdbImpl).open(fdbFile);
        TE_CHECKRETURN_CODE(code);

        fdb->database = std::shared_ptr<DatabaseRef>(new DatabaseRef(std::move(fdbImpl), fdbFile));
        fdb->fdbFile = fdbFile;
    } else {
        fdb->database = entry->second->database;
        fdb->fdbFile = entry->second->fdbFile;
    }

    class InsertGuard
    {
    public :
        InsertGuard(int64_t fsid_, Database2 &db_) :
            fsid(fsid_),
            revert(true),
            db(db_)
        {}
        ~InsertGuard()
        {
            if (revert) {
                StatementPtr s(NULL, NULL);
                if (db.compileStatement(s, "DELETE FROM featuresets WHERE id = ?") != TE_Ok)
                    return;
                if (s->bindLong(1, fsid) != TE_Ok)
                    return;
                s->execute();
            }
        }
    public :
        void successful()
        {
            revert = false;
        }
    private :
        int64_t fsid;
        bool revert;
        Database2 &db;
    };

    // update the index
    StatementPtr stmt(NULL, NULL);
    code = this->indexDb->compileStatement(stmt, "INSERT INTO featuresets (name, file_id, fdb_path, provider, type) VALUES (?, ?, ?, ?, ?)");
    TE_CHECKRETURN_CODE(code);

    fdb->catalogId = catalogid;
    fdb->minResolution = minResolution;
    fdb->maxResolution = maxResolution;
    fdb->name = name;
    fdb->provider = provider;
    fdb->type = type;
    fdb->version = 0LL;

    code = stmt->bindString(1, fdb->name);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(2, fdb->catalogId);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(3, fdb->fdbFile);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(4, fdb->provider);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(5, fdb->type);
    TE_CHECKRETURN_CODE(code);

    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    code = Databases_lastInsertRowID(&fdb->fsid, *indexDb);
    TE_CHECKRETURN_CODE(code);

    InsertGuard guard(fdb->fsid, *indexDb);

    code = dynamic_cast<FeatureSetDatabase *>(fdb->database->value)->insertFeatureSet(fdb->fsid, provider, type, name, minResolution, maxResolution);
    TE_CHECKRETURN_CODE(code);

    guard.successful();

    this->fsidToFeatureDb[fdb->fsid] = fdb;



    this->markFileDirty(fdb->fsid);

    if (featureSet)
        *featureSet = FeatureSetPtr_const(new FeatureSet2(fdb->fsid, fdb->provider, fdb->type, fdb->name, fdb->minResolution, fdb->maxResolution, 0LL), Memory_deleter_const<FeatureSet2>);

    return code;
}

//public boolean isFeatureVisible(int64_t fid) {
TAKErr PersistentDataSourceFeatureDataStore2::isFeatureVisible(bool *value, const int64_t fid) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    const int64_t fsid = ((fid >> 32LL) & 0xFFFFFFFFLL);

    std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator entry;
    entry = this->fsidToFeatureDb.find(fsid);
    if (entry == this->fsidToFeatureDb.end()) {
        return TE_InvalidArg;
    }

    return entry->second->database->value->isFeatureVisible(value, fid & 0xFFFFFFFFLL);
}


//protected boolean insertFeatureImpl(int64_t fsid, FeatureDefinition def, Feature[] returnRef) {
TAKErr PersistentDataSourceFeatureDataStore2::insertFeatureImpl(FeaturePtr_const *value, const int64_t fsid, const FeatureDefinition2 &def) NOTHROWS
{
    return TE_Unsupported;
}


/**************************************************************************/

/**
*
* <P>Thread Safety: All operations are externally synchronized on owning
* PersistentDataSourceFeatureDataStore.
*
* @author Developer
*/

PersistentDataSourceFeatureDataStore2::Index::Index(PersistentDataSourceFeatureDataStore2 &owner_, CatalogCurrencyRegistry2 &currencyRegistry_) :
    CatalogDatabase2(currencyRegistry_),
    owner(owner_)
{}

TAKErr PersistentDataSourceFeatureDataStore2::Index::open(Database2 *database) NOTHROWS
{
    TAKErr code(TE_Ok);
#ifdef MSVC
    // XXX - dataset ingestion is at least an order of magnitude slower due to
    //       the 'insertFeatureSet' call. Synchronous mode on Windows seems to
    //       perform very, very poorly.
    code = database->execute("PRAGMA synchronous = OFF", NULL, 0);
    TE_CHECKRETURN_CODE(code);
#endif
    DatabasePtr db(database, Memory_leaker_const<Database2>);
    code = this->openImpl(std::move(db));
    TE_CHECKRETURN_CODE(code);

#if INDEX_SCHEMA_VERSION == 8
    // for index schema 8, add a modified column for the featuresets if necessary
    QueryPtr query(NULL, NULL);
    code = this->database->compileQuery(query, "SELECT value FROM catalog_metadata WHERE key = ? LIMIT 1");
    TE_CHECKRETURN_CODE(code);
    code = query->bindString(1, "subschema_version");
    TE_CHECKRETURN_CODE(code);
    code = query->moveToNext();
    if (code == TE_Done) {
        code = TE_Ok;

        StatementPtr stmt(NULL, NULL);

        // create the extended catalog table
        stmt.reset();
        code = this->database->compileStatement(stmt, "CREATE TABLE catalog_ex (file_id INTEGER PRIMARY KEY AUTOINCREMENT, provider TEXT, type TEXT, fdb_type TEXT, modified INTEGER)");
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);

        // fill the table with derived values from the current catalog
        stmt.reset();
        code = this->database->compileStatement(stmt, "INSERT INTO catalog_ex SELECT DISTINCT file_id, provider, type, fdb_type, 0 FROM featuresets");
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);

        stmt.reset();
        code = this->database->compileStatement(stmt, "INSERT INTO catalog_metadata (key, value) VALUES(?, ?)");
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindString(1, "subschema_version");
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindInt(2, 1);
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);

    } else if (code == TE_Ok) {
        // XXX check subschema version
    }
    TE_CHECKRETURN_CODE(code);
#endif
    return code;
}

TAKErr PersistentDataSourceFeatureDataStore2::Index::invalidateCatalogEntry(const int64_t catalogId) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    StatementPtr stmt(NULL, NULL);

    std::ostringstream sql;
    sql << "UPDATE " << TABLE_CATALOG << " SET " <<
        COLUMN_CATALOG_SYNC << " = 0 WHERE " <<
        COLUMN_CATALOG_ID << " = ?";
    code = this->database->compileStatement(stmt,
            sql.str().c_str());
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(1, catalogId);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);
    stmt.reset();
    return code;
}

TAKErr PersistentDataSourceFeatureDataStore2::Index::finalizeCatalogEntry(const int64_t catalogId) NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    TE_CHECKRETURN_CODE(code);
    StatementPtr stmt(NULL, NULL);

    std::ostringstream sql;
    sql << "UPDATE " << TABLE_CATALOG << " SET " <<
        COLUMN_CATALOG_SYNC << " = 1 WHERE " <<
        COLUMN_CATALOG_ID << " = ?";
    code = this->database->compileStatement(stmt,
            sql.str().c_str());
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(1, catalogId);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);
    stmt.reset();
    return code;
}

TAKErr PersistentDataSourceFeatureDataStore2::Index::query(CatalogDatabase2::CatalogCursorPtr &retval, const int64_t fsid) NOTHROWS
{
    TAKErr code;

    QueryPtr result(NULL, NULL);

    std::ostringstream sql;
    sql << "SELECT * FROM "
        << TABLE_CATALOG
        << " WHERE "
        << COLUMN_CATALOG_ID
        << " IN (SELECT file_id FROM featuresets WHERE id = ? LIMIT 1) LIMIT 1";
    code = this->database->compileQuery(result, sql.str().c_str());
    TE_CHECKRETURN_CODE(code);
    code = result->bindLong(1, fsid);
    TE_CHECKRETURN_CODE(code);

    retval = CatalogCursorPtr(new CatalogDatabase2::CatalogCursor(std::move(result)), Memory_deleter_const<CatalogDatabase2::CatalogCursor>);

    return code;
}

TAKErr PersistentDataSourceFeatureDataStore2::Index::insertFeatureSet(int64_t *fsid, const int64_t catalogId, const char *name, const char *fdb, const char *provider, const char *type) NOTHROWS
{
    TAKErr code;
    StatementPtr stmt(NULL, NULL);

    code = this->database->compileStatement(stmt,
        "INSERT INTO featuresets (file_id, name, fdb_path, provider, type) VALUES (?, ?, ?, ?, ?)");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(1, catalogId);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(2, name);
    TE_CHECKRETURN_CODE(code);
    TE_GET_STORAGE_PATH_CODE(fdb, fdbStorage, code);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(3, fdbStorage);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(4, provider);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(5, type);
    TE_CHECKRETURN_CODE(code);

    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    return Databases_lastInsertRowID(fsid, *this->database);
}

TAKErr PersistentDataSourceFeatureDataStore2::Index::reserveFeatureSet(int64_t *fsid, const char *name, const char *fdb, const char *provider, const char *type) NOTHROWS
{
    TAKErr code;
    StatementPtr stmt(NULL, NULL);

    code = this->database->compileStatement(stmt,
        "INSERT INTO featuresets (name, fdb_path, provider, type) VALUES (?, ?, ?, ?)");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(1, name);
    TE_CHECKRETURN_CODE(code);
    TE_GET_STORAGE_PATH_CODE(fdb, fdbStorage, code);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(2, fdbStorage);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(3, provider);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(4, type);
    TE_CHECKRETURN_CODE(code);

    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    return Databases_lastInsertRowID(fsid, *this->database);
}

TAKErr PersistentDataSourceFeatureDataStore2::Index::reserveFeatureSet(const int64_t fsid, const char *name, const char *fdb,
                                                                       const char *provider, const char *type) NOTHROWS {
    TAKErr code;
    StatementPtr stmt(NULL, NULL);

    code = this->database->compileStatement(stmt, "INSERT INTO featuresets (id, name, fdb_path, provider, type) VALUES (?, ?, ?, ?, ?)");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(1, fsid);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(2, name);
    TE_CHECKRETURN_CODE(code);
    TE_GET_STORAGE_PATH_CODE(fdb, fdbStorage, code);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(3, fdbStorage);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(4, provider);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(5, type);
    TE_CHECKRETURN_CODE(code);

    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    return code;
}

TAKErr PersistentDataSourceFeatureDataStore2::Index::setFeatureSetCatalogId(const int64_t fsid, const int64_t catalogId) NOTHROWS
{
    TAKErr code;
    StatementPtr stmt(NULL, NULL);
    code = this->database->compileStatement(stmt, "UPDATE featuresets SET file_id = ? WHERE id = ?");

    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(1, catalogId);
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindLong(2, fsid);
    TE_CHECKRETURN_CODE(code);

    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    stmt.reset();

    return code;
}

/**************************************************************************/
// CatalogDatabase

TAKErr PersistentDataSourceFeatureDataStore2::Index::validateCatalogNoSync(CatalogCursor &result) NOTHROWS
{
    TAKErr code(TE_Ok);

    // validate catalog table
    code = CatalogDatabase2::validateCatalogNoSync(result);
    TE_CHECKRETURN_CODE(code);

    // check for any content in the featuresets table that may be present but
    // somehow orphaned
    code = this->onCatalogEntryRemoved(-1, false);
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr PersistentDataSourceFeatureDataStore2::Index::validateCatalogRowNoSync(bool *value, CatalogCursor &result) NOTHROWS
{
    TAKErr code;
    int syncVersion;
    code = result.getSyncVersion(&syncVersion);
    TE_CHECKRETURN_CODE(code);
    if (syncVersion == 0) {
        *value = false;
        return TE_Ok;
    }
    return CatalogDatabase2::validateCatalogRowNoSync(value, result);
}


TAKErr PersistentDataSourceFeatureDataStore2::Index::checkDatabaseVersion(bool *value) NOTHROWS
{
    TAKErr code;
    int dbVersion;
    code = this->database->getVersion(&dbVersion);
    TE_CHECKRETURN_CODE(code);
    *value = (dbVersion == databaseVersion());
    return code;
}


TAKErr PersistentDataSourceFeatureDataStore2::Index::setDatabaseVersion() NOTHROWS
{
    return this->database->setVersion(databaseVersion());
}


TAKErr PersistentDataSourceFeatureDataStore2::Index::dropTables() NOTHROWS
{
    TAKErr code;
    code = this->database->execute("DROP TABLE IF EXISTS featuresets", NULL, 0);
    TE_CHECKRETURN_CODE(code);
    if (pathExists(owner.fdbsDir))
        deletePath(owner.fdbsDir);
    code = this->database->execute("DROP TABLE IF EXISTS catalog_ex", NULL, 0);
    TE_CHECKRETURN_CODE(code);
    return CatalogDatabase2::dropTables();
}


TAKErr PersistentDataSourceFeatureDataStore2::Index::buildTables() NOTHROWS
{
    TAKErr code;

    code = CatalogDatabase2::buildTables();
    TE_CHECKRETURN_CODE(code);

    code = this->database->execute(
        "CREATE TABLE featuresets ("
            "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            "name TEXT COLLATE NOCASE, "
            "file_id INTEGER, "
            "fdb_path TEXT, "
            "provider TEXT, "
            "type TEXT, "
            "fdb_type TEXT)", NULL, 0);
    TE_CHECKRETURN_CODE(code);

    // create the extended catalog table
    code = this->database->execute(
        "CREATE TABLE catalog_ex ("
            "file_id INTEGER PRIMARY KEY AUTOINCREMENT, "
            "provider TEXT, "
            "type TEXT, "
            "fdb_type TEXT, "
            "modified INTEGER)", NULL, 0);
    TE_CHECKRETURN_CODE(code);

    StatementPtr stmt(NULL, NULL);
    code = this->database->compileStatement(stmt, "INSERT INTO catalog_metadata (key, value) VALUES(?, ?)");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindString(1, "subschema_version");
    TE_CHECKRETURN_CODE(code);
    code = stmt->bindInt(2, 1);
    TE_CHECKRETURN_CODE(code);
    code = stmt->execute();
    TE_CHECKRETURN_CODE(code);

    // create the template file to save some CPU on spatial metadata and
    // table construction
    const char *templateFile = owner.fdbTemplate;

    // the template file does not contain any external data so there is
    // no need to use FileSystems.deleteFile.
    if (pathExists(templateFile))
        deletePath(templateFile);

    if (!pathExists(templateFile)) {
        FeatureSetDatabase::Builder t;
        code = t.create(templateFile);
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}


TAKErr PersistentDataSourceFeatureDataStore2::Index::onCatalogEntryRemoved(const int64_t catalogId, const bool automated) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (catalogId > 0LL) {
        QueryPtr query(NULL, NULL);

        code = this->database->compileQuery(query, "SELECT id, fdb_path FROM featuresets WHERE file_id = ?");
        TE_CHECKRETURN_CODE(code);
        code = query->bindLong(1, catalogId);
        TE_CHECKRETURN_CODE(code);

        std::map<int64_t, std::shared_ptr<FeatureDb>>::iterator db;
        do {
            code = query->moveToNext();
            TE_CHECKBREAK_CODE(code);

            // remove from containers
            int64_t fsid;
            code = query->getLong(&fsid, 0);
            TE_CHECKBREAK_CODE(code);

            db = owner.fsidToFeatureDb.find(fsid);
            if (db == owner.fsidToFeatureDb.end()) {
                const char *fdbPath;
                code = query->getString(&fdbPath, 1);
                if (code == TE_Ok) {
                    TE_GET_RUNTIME_PATH_CODE(fdbPath, fdbPathRuntime, code);
                    TE_CHECKBREAK_CODE(code);
                    deletePath(fdbPath);
                } else {
                    // XXX - not really ok, but we will leak the file on disk
                    code = TE_Ok;
                }
                continue;
            }

            // mark database for delete on dereference
            db->second->database->markForDelete();

            // clear shared
            owner.sharedDbs.erase(db->second->database);
            owner.fsidToFeatureDb.erase(fsid);
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);
        query.reset();

        StatementPtr stmt(NULL, NULL);

        stmt.reset();
        code = this->database->compileStatement(stmt, "DELETE FROM featuresets WHERE file_id = ?");
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindLong(1, catalogId);
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);

        stmt.reset();
        code = this->database->compileStatement(stmt, "DELETE FROM catalog_ex WHERE file_id = ?");
        TE_CHECKRETURN_CODE(code);
        code = stmt->bindLong(1, catalogId);
        TE_CHECKRETURN_CODE(code);
        code = stmt->execute();
        TE_CHECKRETURN_CODE(code);

        return code;
    } else if (catalogId != 0LL) {
        std::set<int64_t> links;

        QueryPtr result(NULL, NULL);

        std::ostringstream sql;
        sql << "SELECT file_id FROM featuresets WHERE file_id IN (SELECT file_id FROM featuresets LEFT JOIN "
            << TABLE_CATALOG
            << " on featuresets.file_id = "
            << TABLE_CATALOG
            << "."
            << COLUMN_CATALOG_ID
            << " WHERE "
            << TABLE_CATALOG
            << "."
            << COLUMN_CATALOG_ID << " IS NULL)";

        code = this->database->query(result, sql.str().c_str());
        TE_CHECKRETURN_CODE(code);
        do {
            code = result->moveToNext();
            if (code != TE_Ok)
                break;
            int64_t link;
            code = result->getLong(&link, 0);
            if (code != TE_Ok)
                break;
            links.insert(link);
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);
        result.reset();

        std::set<int64_t>::iterator link;
        for (link = links.begin(); link != links.end(); link++) {
            code = this->onCatalogEntryRemoved(*link, automated);
            if (code != TE_Ok)
                break;
        }
        TE_CHECKRETURN_CODE(code);

        return code;
    } else {
        return TE_IllegalState;
    }
}

int PersistentDataSourceFeatureDataStore2::Index::databaseVersion() NOTHROWS
{
    return (catalogSchemaVersion() | (INDEX_SCHEMA_VERSION << 16));
}

/**************************************************************************/

PersistentDataSourceFeatureDataStore2::DistributedFeatureCursorImpl::DistributedFeatureCursorImpl(FeatureCursorPtr &&cursor_, std::shared_ptr<DatabaseRef> db_) NOTHROWS :
    filter(std::move(cursor_)),
    db(db_)
{}

TAKErr PersistentDataSourceFeatureDataStore2::DistributedFeatureCursorImpl::moveToNext() NOTHROWS
{
    this->rowData.reset();
    return this->filter->moveToNext();
}

TAKErr PersistentDataSourceFeatureDataStore2::DistributedFeatureCursorImpl::getId(int64_t *value) NOTHROWS
{
    TAKErr code;
    int64_t fsid;
    int64_t fid;

    code = this->filter->getFeatureSetId(&fsid);
    TE_CHECKRETURN_CODE(code);
    code = this->filter->getId(&fid);
    TE_CHECKRETURN_CODE(code);

    *value = (fsid << 32LL) | fid;
    return code;
}

TAKErr PersistentDataSourceFeatureDataStore2::DistributedFeatureCursorImpl::getFeatureSetId(int64_t *value) NOTHROWS
{
    return this->filter->getFeatureSetId(value);
}

TAKErr PersistentDataSourceFeatureDataStore2::DistributedFeatureCursorImpl::getVersion(int64_t *value) NOTHROWS
{
    return this->filter->getVersion(value);
}

TAKErr PersistentDataSourceFeatureDataStore2::DistributedFeatureCursorImpl::getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS
{
    return this->filter->getRawGeometry(value);
}

FeatureDefinition2::GeometryEncoding PersistentDataSourceFeatureDataStore2::DistributedFeatureCursorImpl::getGeomCoding() NOTHROWS
{
    return this->filter->getGeomCoding();
}

TAKErr PersistentDataSourceFeatureDataStore2::DistributedFeatureCursorImpl::getName(const char **value) NOTHROWS
{
    return this->filter->getName(value);
}

FeatureDefinition2::StyleEncoding PersistentDataSourceFeatureDataStore2::DistributedFeatureCursorImpl::getStyleCoding() NOTHROWS
{
    return this->filter->getStyleCoding();
}

TAKErr PersistentDataSourceFeatureDataStore2::DistributedFeatureCursorImpl::getRawStyle(FeatureDefinition2::RawData *value) NOTHROWS
{
    return this->filter->getRawStyle(value);
}

TAKErr PersistentDataSourceFeatureDataStore2::DistributedFeatureCursorImpl::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS
{
    return this->filter->getAttributes(value);
}

TAKErr PersistentDataSourceFeatureDataStore2::DistributedFeatureCursorImpl::get(const Feature2 **feature) NOTHROWS
{
    if (rowData.get()) {
        *feature = rowData.get();
        return TE_Ok;
    } else {
        const Feature2 *f(NULL);
        TAKErr code = this->filter->get(&f);
        TE_CHECKRETURN_CODE(code);
        rowData.reset(new Feature2((f->getFeatureSetId() << 32LL) | f->getId(),
                                   f->getFeatureSetId(),
                                   f->getName(),
                                   GeometryPtr_const(f->getGeometry(), Memory_leaker_const<atakmap::feature::Geometry>),
                                   StylePtr_const(f->getStyle(), Memory_leaker_const<atakmap::feature::Style>),
                                   AttributeSetPtr_const(f->getAttributes(), Memory_leaker_const<atakmap::util::AttributeSet>),
                                   f->getVersion()));
        *feature = rowData.get();
        return code;
    }
}

/**************************************************************************/

PersistentDataSourceFeatureDataStore2::FeatureDb::FeatureDb() :
    database(NULL),
    fsid(FeatureDataStore2::FEATURESET_ID_NONE),
    name(NULL),
    provider(NULL),
    type(NULL),
    minResolution(NAN),
    maxResolution(NAN),
    version(FeatureDataStore2::FEATURESET_VERSION_NONE)
{}

PersistentDataSourceFeatureDataStore2::AddMgr::AddMgr(PersistentDataSourceFeatureDataStore2 &owner_) NOTHROWS :
    owner(owner_),
    marked(false),
    catalogId(0LL),
    successful(false)
{}

PersistentDataSourceFeatureDataStore2::AddMgr::~AddMgr() NOTHROWS
{
    LockPtr lock(NULL, NULL);
    Lock_create(lock, owner.mutex);
    if (!path.empty()) {
        if (!successful) {
            if (owner.index)
                owner.index->deleteCatalogPath(path.c_str());

            // delete any FDBs that may have been created
            std::list<std::string>::iterator it;
            for (it = fdbFiles.begin(); it != fdbFiles.end(); it++) {
                deletePath((*it).c_str());
            }
        } else if(owner.index) {
            owner.index->finalizeCatalogEntry(catalogId);
        }

        owner.pending.erase(path);
        owner.cond.broadcast(*lock);
    }
}

void PersistentDataSourceFeatureDataStore2::AddMgr::markPending(const char *f_) NOTHROWS
{
    path = f_;
}

void PersistentDataSourceFeatureDataStore2::AddMgr::setCatalogId(const int64_t catalogId_) NOTHROWS
{
    catalogId = catalogId_;
}

void PersistentDataSourceFeatureDataStore2::AddMgr::addFDB(const char *fdbFile_) NOTHROWS
{
    fdbFiles.push_back(fdbFile_);
}

void PersistentDataSourceFeatureDataStore2::AddMgr::setSuccessful() NOTHROWS
{
    successful = true;
}

PersistentDataSourceFeatureDataStore2::FeatureSetCursorImpl::FeatureSetCursorImpl(std::set<std::shared_ptr<const FeatureDb>, LT_featureSetName> featureSets_) NOTHROWS :
    featureSets(featureSets_),
    iter(featureSets.begin())
{}

PersistentDataSourceFeatureDataStore2::FeatureSetCursorImpl::~FeatureSetCursorImpl() NOTHROWS
{}

TAKErr PersistentDataSourceFeatureDataStore2::FeatureSetCursorImpl::get(const FeatureSet2 **featureSet) NOTHROWS
{
    if (!rowData.get())
        return TE_IllegalState;
    *featureSet = rowData.get();
    return TE_Ok;
}

TAKErr PersistentDataSourceFeatureDataStore2::FeatureSetCursorImpl::moveToNext() NOTHROWS
{
    TAKErr code = (iter == featureSets.end()) ? TE_Done : TE_Ok;
    if (iter != featureSets.end()) {
        std::shared_ptr<const FeatureDb> db = *iter;
        rowData.reset(new FeatureSet2(
            db->fsid,
            db->provider,
            db->type,
            db->name,
            db->minResolution,
            db->maxResolution,
            db->version));
        iter++;
    } else {
        rowData.reset();
    }
    return code;
}

/***************************************************************************/
// DatabaseRef

PersistentDataSourceFeatureDataStore2::DatabaseRef::DatabaseRef(FeatureDataStorePtr &&value_, const char *dbFile_) NOTHROWS :
    dbFile(dbFile_),
    deleteDbFile(false),
    valuePtr(std::move(value_)),
    value(valuePtr.get())
{}

PersistentDataSourceFeatureDataStore2::DatabaseRef::~DatabaseRef() NOTHROWS
{
    valuePtr.reset();
    if (deleteDbFile) {
        deletePath(dbFile);
    }
}

void PersistentDataSourceFeatureDataStore2::DatabaseRef::markForDelete() NOTHROWS
{
    deleteDbFile = true;
}

namespace
{

/**************************************************************************/
// Currency

ValidateCurrency::ValidateCurrency() NOTHROWS
{}


const char *ValidateCurrency::getName() const NOTHROWS
{
    return CURRENCY_NAME;
}


int ValidateCurrency::getAppVersion() const NOTHROWS
{
    return CURRENCY_VERSION;
}


TAKErr ValidateCurrency::getAppData(CatalogCurrency2::AppDataPtr &appData, const char *file) const NOTHROWS
{
    return TE_Unsupported;
}


TAKErr ValidateCurrency::isValidApp(bool *value, const char *cf, const int appVersion, const CatalogCurrency2::AppData &appData) const NOTHROWS
{
    TAKErr code(TE_Ok);

    if (appVersion != this->getAppVersion()) {
        *value = false;
        return TE_Ok;
    }

    MemoryInput2 parse;
    code = parse.open(appData.value, appData.length);
    TE_CHECKRETURN_CODE(code);
    parse.setSourceEndian(atakmap::util::LITTLE_ENDIAN);

    int numSpis;
    code = parse.readInt(&numSpis);
    TE_CHECKRETURN_CODE(code);

    int parseVersion;
    std::shared_ptr<FeatureDataSource2> spi;
    for (int i = 0; i < numSpis; i++) {
        {
            short s;
            code = parse.readShort(&s);
            if (code != TE_Ok)
                break;
            parseVersion = s & 0xFFFF;
        }
        {
            TAK::Engine::Port::String s;
            code = getString(s, parse);
            if (code != TE_Ok)
                break;

            code = FeatureDataSourceFactory_getProvider(spi, s);
            if (code == TE_InvalidArg)
            {
                // XXX - not sure on absolute best behavior here, but we're
                //       going to do a soft accept if the parser is not yet
                //       registered for WTK-1.6
                *value = true;
                code = TE_Ok;
                break;
            }
            if (code != TE_Ok)
                break;
        }
        if (!spi.get() || spi->parseVersion() != parseVersion) {
            *value = false;
            return code;
        }
    }
    TE_CHECKRETURN_CODE(code);

    TAK::Engine::Port::String f(cf);

#ifdef MSVC
    // XXX - need to define portable case-insensitive 'strstr' function
    const char *extStart = strstr(cf, ".zip");
#else
    const char *extStart = strcasestr(cf, ".zip");
#endif
    if(extStart)
        f[(int)(extStart-cf+4u)] = '\0';

    uint8_t b;
    code = parse.readByte(&b);
    TE_CHECKRETURN_CODE(code);

    const bool isDir = ((b & 0x01) == 0x01);
    if (isDirectory(f) != isDir) {
        *value = false;
        return code;
    }
    int64_t length;
    code = parse.readLong(&length);
    TE_CHECKRETURN_CODE(code);
    if (length != getFileSize(f)) {
        *value = false;
        return code;
    }
    int64_t lastModified;
    code = parse.readLong(&lastModified);
    if (lastModified != getLastModified(f)) {
        *value = false;
        return code;
    }

    *value = true;
    return code;
}

GenerateCurrency::GenerateCurrency() NOTHROWS
{}

TAKErr GenerateCurrency::init(const FeatureDataSource2::Content &content) NOTHROWS
{
    TAKErr code;
    std::shared_ptr<FeatureDataSource2> spi;
    code = FeatureDataSourceFactory_getProvider(spi, content.getProvider());
    TE_CHECKRETURN_CODE(code);
    if (!spi.get())
        return TE_Unsupported;
    this->contentSpis.insert(spi);

    return code;
}

const char *GenerateCurrency::getName() const NOTHROWS
{
    return CURRENCY_NAME;
}

int GenerateCurrency::getAppVersion() const NOTHROWS
{
    return CURRENCY_VERSION;
}

TAKErr GenerateCurrency::getAppData(CatalogCurrency2::AppDataPtr &appData, const char *cfile) const NOTHROWS
{
    TAKErr code(TE_Ok);
    std::set<std::shared_ptr<FeatureDataSource2>>::iterator spi;

    TAK::Engine::Port::String file(cfile);
#ifdef MSVC
    // XXX - need to define portable case-insensitive 'strstr' function
    const char *extStart = strstr(cfile, ".zip");
#else
    const char *extStart = strcasestr(cfile, ".zip");
#endif
    if(extStart)
        file[(int)(extStart-cfile+4u)] = '\0';

    int len = 4 + (2 * this->contentSpis.size()) + 1 + 16;

    for (spi = this->contentSpis.begin(); spi != this->contentSpis.end(); spi++)
        len += getCodedStringLength((*spi)->getName());

    array_ptr<uint8_t> blob(new uint8_t[len]);

    MemoryOutput2 retval;
    retval.open(blob.get(), len);
    retval.setSourceEndian(atakmap::util::LITTLE_ENDIAN);

    code = retval.writeInt(this->contentSpis.size());
    TE_CHECKRETURN_CODE(code);

    for (spi = this->contentSpis.begin(); spi != this->contentSpis.end(); spi++) {
        code = retval.writeShort((short)(*spi)->parseVersion());
        if (code != TE_Ok)
            break;
        code = putString(retval, (*spi)->getName());
        if (code != TE_Ok)
            break;
    }
    TE_CHECKRETURN_CODE(code);
    code = retval.writeByte(isDirectory(file) ? 0x01u : 0x00u);
    TE_CHECKRETURN_CODE(code);
    code = retval.writeLong(getFileSize(file));
    TE_CHECKRETURN_CODE(code);
    code = retval.writeLong(getLastModified(file));
    TE_CHECKRETURN_CODE(code);

    std::size_t remaining;
    code = retval.remaining(&remaining);
    TE_CHECKRETURN_CODE(code);
    if (remaining > 0) {
        Logger::log(Logger::Error, "PersistentDataSourceFeatureDataStore2::GenerateCurrency: remaining=%d", remaining);
        return TE_IllegalState;
    }

    std::unique_ptr<CatalogCurrency2::AppData> ptrData(new CatalogCurrency2::AppData {blob.release(), static_cast<size_t>(len)});

    appData = CatalogCurrency2::AppDataPtr(ptrData.release(), appDataDeleter);
    return code;
}


TAKErr GenerateCurrency::isValidApp(bool *value, const char *f, int appVersion, const CatalogCurrency2::AppData &data) const NOTHROWS
{
    return TE_Unsupported;
}

FileCursorImpl::FileCursorImpl(CatalogDatabase2::CatalogCursorPtr &&filter_) NOTHROWS :
    filter(std::move(filter_))
{}

TAKErr FileCursorImpl::getFile(TAK::Engine::Port::String &file) NOTHROWS
{
    TAKErr code;
    const char *path;
    code = this->filter->getPath(&path);
    TE_CHECKRETURN_CODE(code);
    file = path;
    return code;
}

TAKErr FileCursorImpl::moveToNext() NOTHROWS
{
    return this->filter->moveToNext();
}

ModifiedFileCursorImpl::ModifiedFileCursorImpl(QueryPtr &&filter_) NOTHROWS :
    filter(std::move(filter_))
{}

TAKErr ModifiedFileCursorImpl::getFile(TAK::Engine::Port::String &value) NOTHROWS
{
    TAKErr code(TE_Ok);
    const char *cpath;
    code = filter->getString(&cpath, 0);
    TE_CHECKRETURN_CODE(code);
    value = cpath;
    return code;
}
TAKErr ModifiedFileCursorImpl::moveToNext() NOTHROWS
{
    return filter->moveToNext();
}

int getCodedStringLength(const char *s) NOTHROWS
{
    return 4 + strlen(s);
}

TAKErr putString(DataOutput2 &buffer, const char *s) NOTHROWS
{
    TAKErr code;

    const std::size_t len = strlen(s);
    code = buffer.writeInt(len);
    TE_CHECKRETURN_CODE(code);
    for (int i = 0; i < len; i++) {
        code = buffer.writeByte(s[i]);
        if (code != TE_Ok)
            break;
    }
    return code;
}

TAKErr getString(TAK::Engine::Port::String &value, DataInput2 &buffer) NOTHROWS
{
    TAKErr code;
    int len;
    code = buffer.readInt(&len);
    TE_CHECKRETURN_CODE(code);

    array_ptr<char> cstr(new char[len + 1]);
    for (int i = 0; i < len; i++) {
        uint8_t c;
        code = buffer.readByte(&c);
        if (code != TE_Ok)
            break;
        cstr.get()[i] = (char)c;
    }
    TE_CHECKRETURN_CODE(code);

    cstr.get()[len] = '\0';

    value = cstr.get();
    return code;
}

void appDataDeleter(const CatalogCurrency2::AppData *appData)
{
    delete [] appData->value;
    delete appData;
}

}
