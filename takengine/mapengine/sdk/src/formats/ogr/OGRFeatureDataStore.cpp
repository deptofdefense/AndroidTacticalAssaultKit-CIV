#include "formats/ogr/OGRFeatureDataStore.h"

#include <sstream>

#include <gdal_priv.h>
#include <ogrsf_frmts.h>
#include <ogr_srs_api.h>

#include "feature/BruteForceLimitOffsetFeatureCursor.h"
#include "feature/DefaultDriverDefinition2.h"
#include "feature/Geometry.h"
#include "feature/GeometryFactory.h"
#include "feature/FeatureCursor2.h"
#include "feature/FeatureSetCursor2.h"
#include "feature/LegacyAdapters.h"
#include "feature/MultiplexingFeatureCursor.h"
#include "feature/OGRDriverDefinition2.h"
#include "feature/ParseGeometry.h"
#include "feature/Style.h"
#include "port/Platform.h"
#include "port/StringBuilder.h"
#include "thread/Lock.h"
#include "util/IO2.h"
#include "util/Memory.h"

using namespace TAK::Engine::Formats::OGR;

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

#define CONNECTION_POOL_LIMIT 3

namespace
{
    TAKErr Platform_atoi(int *value, const char *s) NOTHROWS
    {
        if (!value)
            return TE_InvalidArg;
        if (!s)
            return TE_InvalidArg;
        try {
            *value = std::stoi(s);
            return TE_Ok;
        } catch (...) {
            return TE_Err;
        }
    }
    TAKErr Platform_atoll(int64_t *value, const char *s) NOTHROWS
    {
        if (!value)
            return TE_InvalidArg;
        if (!s)
            return TE_InvalidArg;
        try {
            *value = std::stoll(s);
            return TE_Ok;
        } catch (...) {
            return TE_Err;
        }
    }
    TAKErr Platform_atof(float *value, const char *s) NOTHROWS
    {
        if (!value)
            return TE_InvalidArg;
        if (!s)
            return TE_InvalidArg;
        try {
            *value = std::stof(s);
            return TE_Ok;
        } catch (...) {
            return TE_Err;
        }
    }

    double min(double a, double b, double c, double d) NOTHROWS;
    double max(double a, double b, double c, double d) NOTHROWS;

    TAKErr GetStyleGeomType(atakmap::feature::Geometry::Type *value, OGRGeometryH feature) NOTHROWS;
    TAK::Engine::Port::String GetDescription(OGRLayerH layer, OGRFeatureH feature) NOTHROWS;
    int GetSpatialReferenceID(OGRSpatialReferenceH srs) NOTHROWS;
    OGRSpatialReferenceH GetSR4326();
    TAKErr ogr2geom(GeometryPtr_const &value, OGRFeatureH feature) NOTHROWS;
    TAKErr ogr2attr(atakmap::util::AttributeSet *value, OGRFeatureH feature) NOTHROWS;

    void GDALDataset_delete(GDALDatasetH value)
    {
        GDALClose(value);
    }

    void OGRCoordinateTransformation_delete(OGRCoordinateTransformationH value)
    {
        OCTDestroyCoordinateTransformation(value);
    }

    struct OgrFeatureCursorRowData
    {
    public :
        OgrFeatureCursorRowData() NOTHROWS;
    public :
        OGRFeatureH ogrFeature;
        int64_t version;
        std::unique_ptr<TAK::Engine::Port::String> name;
        array_ptr<uint8_t> wkb;
        int wkbSize;
        TAK::Engine::Feature::StylePtr_const style;
        std::unique_ptr<atakmap::util::AttributeSet> attributes;
        FeaturePtr_const feature;
    };

    typedef std::unique_ptr<void, void(*)(GDALDatasetH)> GDALDataset_unique_ptr;
    typedef std::shared_ptr<void> GDALDataset_shared_ptr;

    typedef std::unique_ptr<void, void(*)(OGRCoordinateTransformationH)> OGRCoordinateTransformation_unique_ptr;
    typedef std::shared_ptr<void> OGRCoordinateTransformation_shared_ptr;

    typedef std::unique_ptr<void, void(*)(OGRSpatialReferenceH)> OGRSpatialReference_unique_ptr;
    typedef std::shared_ptr<void> OGRSpatialReference_shared_ptr;

    typedef std::unique_ptr<void, void(*)(OGRGeometryH)> OGRGeometry_unique_ptr;
}

class OGRFeatureDataStore::OgrLayerFeatureCursor : public FeatureCursor2
{
public:
    OgrLayerFeatureCursor(OGRFeatureDataStore &owner, const FeatureSetDefn &defn, OGRLayerH layer, const GDALDataset_shared_ptr &conn) NOTHROWS;
public: // FeatureCursor2
    Util::TAKErr getId(int64_t *value) NOTHROWS override;
    Util::TAKErr getFeatureSetId(int64_t *value) NOTHROWS override;
    Util::TAKErr getVersion(int64_t *value) NOTHROWS override;
public: // FeatureDefinition2
    Util::TAKErr getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS override;
    FeatureDefinition2::GeometryEncoding getGeomCoding() NOTHROWS override;
    AltitudeMode getAltitudeMode() NOTHROWS override;
    double getExtrude() NOTHROWS override;
    Util::TAKErr getName(const char **value) NOTHROWS override;
    FeatureDefinition2::StyleEncoding getStyleCoding() NOTHROWS override;
    Util::TAKErr getRawStyle(FeatureDefinition2::RawData *value) NOTHROWS override;
    Util::TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS override;
    Util::TAKErr get(const Feature2 **feature) NOTHROWS override;
public: // RowIterator
    Util::TAKErr moveToNext() NOTHROWS override;
private:
    OGRFeatureDataStore &owner;
    const OGRFeatureDataStore::FeatureSetDefn defn;
    GDALDataset_shared_ptr conn;
    OGRLayerH layer;
    std::unique_ptr<OgrFeatureCursorRowData> rowData;
    OGRSpatialReference_unique_ptr layer2lla;
};

class OGRFeatureDataStore::FeatureSetCursorImpl : public FeatureSetCursor2
{
public:
    FeatureSetCursorImpl(std::set<FeatureSetDefn, FeatureSetDefn_LT> defns) NOTHROWS;
public:
    TAKErr get(const FeatureSet2 **featureSet) NOTHROWS override;
    TAKErr moveToNext() NOTHROWS override;
private :
    std::set<FeatureSetDefn, FeatureSetDefn_LT> defns;
    std::set<FeatureSetDefn, FeatureSetDefn_LT>::iterator impl;
    bool first;
    FeatureSetPtr_const row;
}; // FeatureSetCursor

class OGRFeatureDataStore::DriverDefSchemaHandler : public OGRFeatureDataStore::SchemaHandler
{
public :
    DriverDefSchemaHandler(OGRDriverDefinition2Ptr &&impl_) NOTHROWS :
        impl(std::move(impl_))
    {}
public :
    TAKErr ignoreLayer(bool *value, OGRLayerH layer) const NOTHROWS override
    {
        if (!layer)
            return TE_InvalidArg;
        return impl->skipLayer(value, *static_cast<OGRLayer *>(layer));
    }
    bool styleRequiresAttributes() const NOTHROWS override
    {
        return false;
    }
    TAKErr getFeatureStyle(Feature::StylePtr_const &value, OGRLayerH layer, OGRFeatureH cfeature, const atakmap::util::AttributeSet &attribs) NOTHROWS override
    {
        if (!cfeature)
            return TE_InvalidArg;

        TAKErr code;
        TAK::Engine::Port::String ogrStyle;
        auto *feature = static_cast<OGRFeature *>(cfeature);
        code = impl->getStyle(ogrStyle, *feature, *feature->GetGeometryRef());
        TE_CHECKRETURN_CODE(code);
        if (ogrStyle) {
            try {
                value = StylePtr_const(atakmap::feature::Style::parseStyle(ogrStyle), atakmap::feature::Style::destructStyle);
            } catch (...) {
                return TE_Err;
            }
        } else {
            value.reset();
        }
        return code;
    }
    TAKErr getFeatureName(TAK::Engine::Port::String &value, OGRLayerH layer, OGRFeatureH feature, const atakmap::util::AttributeSet &attribs) NOTHROWS override
    {
        TAKErr code(TE_Ok);
        Lock lock(mutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);

        TAK::Engine::Port::String nameCol;
        auto entry = nameColumn.find(layer);
        if (entry == nameColumn.end()) {
            OGRFeatureDefnH def = OGR_L_GetLayerDefn(layer);
            if (def) {
                int fieldCount = OGR_FD_GetFieldCount(def);
                std::list<std::string> candidates;
                for (int i = 0; i < fieldCount; i++) {
                    OGRFieldDefnH fieldDef = OGR_FD_GetFieldDefn(def, i);
                    if (!fieldDef)
                        continue;
                    const char *cfieldName = OGR_Fld_GetNameRef(fieldDef);
                    if (!cfieldName)
                        continue;

#ifdef MSVC
                    if(_stricmp("name", cfieldName) == 0) {
#else
                    if(strcasecmp("name", cfieldName) == 0) {
#endif
                        nameCol = cfieldName;
                        break;
                    } else {
                        std::string fieldName(cfieldName);
                        std::string fieldNameLower;
                        fieldNameLower.reserve(fieldName.length());

                        std::transform(fieldName.begin(), fieldName.end(), fieldNameLower.begin(), ::tolower);

                        if (strstr(fieldNameLower.c_str(), "name"))
                            candidates.push_back(fieldName);
                    }
                }

                if (!nameCol && !candidates.empty())
                    nameCol = (*candidates.begin()).c_str();
            }
            this->nameColumn[layer] = nameCol;
        } else {
            nameCol = entry->second;
        }

        value = nullptr;

        try {
            if (nameCol &&
                attribs.containsAttribute(nameCol) &&
                (attribs.getAttributeType(nameCol) == atakmap::util::AttributeSet::STRING)) {

                value = attribs.getString(nameCol);
            }
            return code;
        } catch (...) {
            return TE_Err;
        }
    }
    TAKErr getFeatureSetName(Port::String &value, OGRLayerH layer) NOTHROWS override
    {
        if (!layer)
            return TE_InvalidArg;
        value = OGR_L_GetName(layer);
        return TE_Ok;
    }
private :
    OGRDriverDefinition2Ptr impl;
    std::map<OGRLayerH, TAK::Engine::Port::String> nameColumn;
    Mutex mutex;
};

OGRFeatureDataStore::FeatureSetDefn::FeatureSetDefn() :
    fsid(FeatureDataStore2::FEATURESET_ID_NONE),
    layerName(nullptr),
    displayName(nullptr),
    visible(true),
    lla2layer(nullptr),
    minResolution(NAN),
    maxResolution(NAN)
{}

OGRFeatureDataStore::OGRFeatureDataStore(const char *uri_, const char *workingDir_, const bool asyncRefresh_) NOTHROWS :
    AbstractFeatureDataStore2(0, VISIBILITY_SETTINGS_FEATURESET),
    uri(uri_),
    workingDir(workingDir_),
    refreshThread(nullptr, nullptr),
    refreshRequest(0),
    disposed(false),
    disposing(false),
    backgroundRefresh(asyncRefresh_),
    provider("ogr"),
    type("ogr)")
{
    bool exists;
    IO_exists(&exists, workingDir);
    bool isDir;
    IO_isDirectory(&isDir, workingDir);

    if (exists && !isDir) {
        IO_delete(workingDir);
        IO_mkdirs(workingDir);
    } else if (!exists) {
        IO_mkdirs(workingDir);
    }

    // refresh to update immediately from the cache (if available) and fetch
    // the feature data from remote
    this->refresh();
}

OGRFeatureDataStore::OGRFeatureDataStore(const char *uri_, const char *workingDir_, const bool asyncRefresh_, SchemaHandlerPtr &&schema_) NOTHROWS :
    AbstractFeatureDataStore2(0, VISIBILITY_SETTINGS_FEATURESET),
    uri(uri_),
    workingDir(workingDir_),
    refreshThread(nullptr, nullptr),
    refreshRequest(0),
    disposed(false),
    disposing(false),
    backgroundRefresh(asyncRefresh_),
    userSchema(std::move(schema_)),
    provider("ogr"),
    type("ogr")
{
    bool exists;
    IO_exists(&exists, workingDir);
    bool isDir;
    IO_isDirectory(&isDir, workingDir);

    if (exists && !isDir) {
        IO_delete(workingDir);
        IO_mkdirs(workingDir);
    } else if (!exists) {
        IO_mkdirs(workingDir);
    }

    // refresh to update immediately from the cache (if available) and fetch
    // the feature data from remote
    this->refresh();
}

OGRFeatureDataStore::~OGRFeatureDataStore() NOTHROWS
{
    this->close();
}

TAKErr OGRFeatureDataStore::close() NOTHROWS
{
    TAKErr code(TE_Ok);
    LockPtr lock(nullptr, nullptr);
    code = Lock_create(lock, mutex_);
    TE_CHECKRETURN_CODE(code);

    this->disposing = true;
    if (this->refreshThread.get()) {
        lock.reset();
        this->refreshThread->join();
        this->refreshThread.reset();
        code = Lock_create(lock, mutex_);
        TE_CHECKRETURN_CODE(code);
    }

    std::list<GDALDatasetH>::iterator conn;
    for (conn = _connectionPool.begin(); conn != _connectionPool.end(); conn++)
        GDALClose(*conn);
    _connectionPool.clear();
    return code;
}

TAKErr OGRFeatureDataStore::ConfigureForQuery(OGRLayerH layer, const FeatureSetDefn &defn, const FeatureDataStore2::FeatureQueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);
    OGR_L_ResetReading(layer);
    if (!params.featureIds->empty())
    {
        // XXX - 
    }
    if (!params.featureNames->empty())
    {
        // XXX - 
    }
    if (!params.featureSetIds->empty())
    {
        bool contains;
        int64_t fsid = defn.fsid;
        code = params.featureSetIds->contains(&contains, fsid);
        TE_CHECKRETURN_CODE(code);
        if (!contains)
            return TE_Done;
    }
    if (!params.featureSets->empty())
    {
        // XXX - check name
    }
    if (params.spatialFilter.get())
    {
        atakmap::feature::Envelope spatialFilter = params.spatialFilter->getEnvelope();

        // need to transform to source CS
        if (defn.lla2layer.get())
        {
            double minxminy[] = { spatialFilter.minX, spatialFilter.minY, 0 };
            OCTTransform(defn.lla2layer.get(), 1, minxminy + 0, minxminy + 1, minxminy + 2);
            double minxmaxy[] = { spatialFilter.minX, spatialFilter.maxY, 0 };
            OCTTransform(defn.lla2layer.get(), 1, minxmaxy + 0, minxmaxy + 1, minxmaxy + 2);
            double maxxmaxy[] = { spatialFilter.maxX, spatialFilter.maxY, 0 };
            OCTTransform(defn.lla2layer.get(), 1, maxxmaxy + 0, maxxmaxy + 1, maxxmaxy + 2);
            double maxxminy[] = { spatialFilter.maxX, spatialFilter.minY, 0 };
            OCTTransform(defn.lla2layer.get(), 1, maxxminy + 0, maxxminy + 1, maxxminy + 2);

            spatialFilter.minX = min(minxminy[0], minxmaxy[0], maxxmaxy[0], maxxminy[0]);
            spatialFilter.minY = min(minxminy[1], minxmaxy[1], maxxmaxy[1], maxxminy[1]);
            spatialFilter.maxX = max(minxminy[0], minxmaxy[0], maxxmaxy[0], maxxminy[0]);
            spatialFilter.maxY = max(minxminy[1], minxmaxy[1], maxxmaxy[1], maxxminy[1]);
        }

        OGR_L_SetSpatialFilterRect(layer, spatialFilter.minX, spatialFilter.minY, spatialFilter.maxX, spatialFilter.maxY);
    }
    else
    {
        OGR_L_SetSpatialFilter(layer, nullptr);
    }

    return TE_Ok;
}

TAKErr OGRFeatureDataStore::refreshImpl() NOTHROWS
{
    TAKErr code(TE_Ok);
    

    // connect to WFS
    GDALDataset_unique_ptr wfs(nullptr, nullptr);

    wfs = GDALDataset_unique_ptr(
        GDALOpenEx(this->uri,
            GDAL_OF_READONLY | GDAL_OF_VERBOSE_ERROR | GDAL_OF_INTERNAL,
            nullptr, nullptr, nullptr),
        GDALDataset_delete);
    if (!wfs.get())
    {
        Logger_log(TELL_Warning, "Failed to connect to WFS %s, server may be unavailable.", this->uri.get());
        return TE_Err;
    }

    std::shared_ptr<SchemaHandler> datasetSchema = userSchema;
    if (!datasetSchema.get()) {
        SchemaHandlerPtr schemaPtr(nullptr, nullptr);
        code = this->createSchemaHandler(schemaPtr, wfs.get());
        TE_CHECKRETURN_CODE(code);

        datasetSchema = std::move(schemaPtr);
    }

    std::list<FeatureSetDefn> featureSets;
    int layerCount = GDALDatasetGetLayerCount(wfs.get());
    for (int i = 0; i < layerCount; i++) {
        if (this->disposing)
            break;
        OGRLayerH layer = GDALDatasetGetLayer(wfs.get(), i);
        if (!layer)
            continue;
        bool ignore;
        code = datasetSchema->ignoreLayer(&ignore, layer);
        TE_CHECKBREAK_CODE(code);
        if (ignore)
            continue;

        FeatureSetDefn defn;
        defn.fsid = featureSets.size() + 1;
        code = datasetSchema->getFeatureSetName(defn.displayName, layer);
        defn.layerName = OGR_L_GetName(layer);
        defn.minResolution = std::numeric_limits<double>::max();
        defn.maxResolution = 0.0;
        defn.visible = true;
        defn.lla2layer = nullptr;
        int layerSrid = GetSpatialReferenceID(OGR_L_GetSpatialRef(layer));
        switch (layerSrid)
        {
        case -1:
        case 4326:
            break;
        default:
            OGRSpatialReferenceH layerSpatialRef = OGR_L_GetSpatialRef(layer);
            if (layerSpatialRef)
            {
                OGRSpatialReferenceH spatialRef4326 = GetSR4326();
                defn.lla2layer = OGRCoordinateTransformation_unique_ptr(OCTNewCoordinateTransformation(spatialRef4326, layerSpatialRef), OGRCoordinateTransformation_delete);
            }
            break;
        }

        // XXX - calculate info -- count, coverage, display thesholds ???

        featureSets.push_back(defn);
    }
    TE_CHECKRETURN_CODE(code);

    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    {
        type = "ogr";
        GDALDriverH driver = GDALGetDatasetDriver(wfs.get());
        if (driver)
            type = GDALGetDriverShortName(driver);

        this->schema = std::move(datasetSchema);

        // swap refreshed feature sets with old list
        this->fsidToFeatureDb.clear();
        this->fsNameToFeatureDb.clear();

        std::list<FeatureSetDefn>::iterator defn;
        for(defn = featureSets.begin(); defn != featureSets.end(); defn++) {
            std::shared_ptr<FeatureSetDefn> defnPtr(new FeatureSetDefn(*defn));
            defnPtr->provider = provider;
            defnPtr->type = type;
            defnPtr->schema = this->schema;

            this->fsidToFeatureDb[defnPtr->fsid] = defnPtr;
            this->fsNameToFeatureDb[defnPtr->displayName] = defnPtr;
        }

        // XXX - if defs changed, clear connection pool
#if 0
        if (defs changed)
#endif
        {
            std::list<GDALDatasetH>::iterator conn;
            for (conn = _connectionPool.begin(); conn != _connectionPool.end(); conn++)
                GDALClose(*conn);
            _connectionPool.clear();
        }
        // put connection in pool, and null
        _connectionPool.push_back(wfs.release());

        // notify datastore changed
        if(!this->disposing)
            this->dispatchDataStoreContentChangedNoSync(true);
    }

    wfs.reset();

    return code;
}

/**************************************************************************/

TAKErr OGRFeatureDataStore::getFeature(FeaturePtr_const &value, const int64_t fid) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    int64_t fsid = ((fid >> 32) & 0xFFFFFFFFLL);

    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator db;
    db = this->fsidToFeatureDb.find(fsid);
    if (db == this->fsidToFeatureDb.end())
        return TE_InvalidArg;

    GDALDataset_unique_ptr conn(nullptr, nullptr);
    code = OpenConnection(conn);
    TE_CHECKRETURN_CODE(code);

    // find associated layer
    OGRLayerH layer = GDALDatasetGetLayerByName(conn.get(), db->second->layerName);
    if (!layer)
        return TE_InvalidArg;

    if (!this->schema.get())
        return TE_IllegalState;

    // look up feature
    int layerFid = (int)(fid & 0xFFFFFFFFLL);
    OGRFeatureH feature = OGR_L_GetFeature(layer, layerFid);
    if (!feature)
        return TE_InvalidArg;

    atakmap::util::AttributeSet attribs;
    code = ogr2attr(&attribs, feature);
    TE_CHECKRETURN_CODE(code);

    TAK::Engine::Port::String name;
    code = this->schema->getFeatureName(name, layer, feature, attribs);
    TE_CHECKRETURN_CODE(code);

    GeometryPtr_const geom(nullptr, nullptr);
    code = ogr2geom(geom, feature);
    TE_CHECKRETURN_CODE(code);
                        
    StylePtr_const style(nullptr, nullptr);
    code = this->schema->getFeatureStyle(style, layer, feature, attribs);
    TE_CHECKRETURN_CODE(code);

    value = FeaturePtr_const(
        new Feature2(fid, fsid, name, *geom, TAK::Engine::Feature::AltitudeMode::TEAM_ClampToGround, 0.0, *style, attribs, 1LL),
        Memory_deleter_const<Feature2>);

    CloseConnection(std::move(conn));

    return code;
}

TAKErr OGRFeatureDataStore::OpenConnection(GDALDataset_unique_ptr &value) NOTHROWS
{
    TAKErr code(TE_Ok);
    {
        Lock lock(mutex_);
        code = lock.status;
        {
            if (!_connectionPool.empty())
            {
                auto first = _connectionPool.begin();
                value = GDALDataset_unique_ptr(*first, GDALDataset_delete);
                _connectionPool.erase(first);
                return TE_Ok;
            }
        }
    }

    GDALDataset_unique_ptr wfs = GDALDataset_unique_ptr(
        GDALOpenEx(this->uri,
            GDAL_OF_READONLY | GDAL_OF_VERBOSE_ERROR | GDAL_OF_INTERNAL,
            nullptr, nullptr, nullptr),
        GDALDataset_delete);
    if (!wfs.get()) {
        Logger_log(TELL_Warning, "Failed to connect to WFS %s, server may be unavailable.", this->uri.get());
        return TE_Err;
    }

    value = std::move(wfs);
    return code;
}

TAKErr OGRFeatureDataStore::CloseConnection(GDALDataset_unique_ptr &&conn) NOTHROWS
{
    if (!conn.get())
        return TE_Ok;
    TAKErr code(TE_Ok);

    bool pooled = false;
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (_connectionPool.size() < CONNECTION_POOL_LIMIT) {
        _connectionPool.push_back(conn.release());
        pooled = true;
    }

    return code;
}

bool OGRFeatureDataStore::matches(const FeatureSetDefn &defn, const FeatureDataStore2::FeatureQueryParameters &params) NOTHROWS
{
    if (!params.providers->empty()) {
        bool matched;
        if(AbstractFeatureDataStore2::matches(&matched, *params.providers, defn.provider, '%') != TE_Ok || !matched)
            return false;
    }
    if (!params.types->empty()) {
        bool matched;
        if(AbstractFeatureDataStore2::matches(&matched, *params.types, defn.type, '%') != TE_Ok || !matched)
            return false;
    }
    if (!params.featureSetIds->empty()) {
        bool contained;
        int64_t fsid = defn.fsid;
        if(params.featureSetIds->contains(&contained, fsid) != TE_Ok || !contained)
            return false;
    }
    if (!params.featureIds->empty())
    {
        bool matches = false;
        TAK::Engine::Port::Collection<int64_t>::IteratorPtr iter(nullptr, nullptr);
        if (params.featureIds->iterator(iter) != TE_Ok)
            return false;
        do {
            int64_t fid;
            TAKErr code = iter->get(fid);
            if (code != TE_Ok)
                return false;
            int64_t fsid = ((fid >> 32) & 0xFFFFFFFFL);
            if (fsid == defn.fsid) {
                matches = true;
                break;
            }
            code = iter->next();
            if (code == TE_Done)
                break;
            else if (code != TE_Ok)
                return false;
        } while (true);
        if (!matches)
            return false;
    }
    if (!isnan(params.minResolution))
    {
        // XXX - 
    }
    if (!isnan(params.maxResolution))
    {
        // XXX - 
    }
    if (params.visibleOnly && !defn.visible)
    {
        return false;
    }

    return true;
}

TAKErr OGRFeatureDataStore::Filter(FeatureQueryParameters *value, const FeatureSetDefn &db, const FeatureQueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);
    FeatureQueryParameters retval(params);

    if (!params.featureIds->empty())
    {
        // check and mask off FSID
        code = retval.featureIds->clear();
        TE_CHECKRETURN_CODE(code);

        TAK::Engine::Port::Collection<int64_t>::IteratorPtr iter(nullptr, nullptr);
        code = params.featureIds->iterator(iter);
        TE_CHECKRETURN_CODE(code);
        do {
            int64_t fid;
            code = iter->get(fid);
            TE_CHECKBREAK_CODE(code);

            int64_t fsid = ((fid >> 32) & 0xFFFFFFFFLL);
            if (fsid == db.fsid)
                retval.featureIds->add(fid & 0xFFFFFFFFLL);

            code = iter->next();
            TE_CHECKBREAK_CODE(code);
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);
    }
    if (!params.featureSetIds->empty())
    {
        code = retval.featureSetIds->clear();
        TE_CHECKRETURN_CODE(code);
    }
    if (!params.featureSets->empty())
    {
        code = retval.featureSets->clear();
        TE_CHECKRETURN_CODE(code);
    }
    if (!params.providers->empty())
    {
        code = retval.providers->clear();
        TE_CHECKRETURN_CODE(code);
    }
    if (!params.types->empty())
    {
        code = retval.types->clear();
        TE_CHECKRETURN_CODE(code);
    }

    *value = retval;
    return code;
}

TAKErr OGRFeatureDataStore::PrepareQuery(std::list<std::pair<FeatureSetDefn, FeatureQueryParameters>> &value, const FeatureQueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);
    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator it;
    for (it = this->fsidToFeatureDb.begin(); it != this->fsidToFeatureDb.end(); it++) {
        if (matches(*it->second, params)) {
            FeatureQueryParameters innerParams;
            code = Filter(&innerParams, *it->second, params);
            TE_CHECKBREAK_CODE(code);
            value.push_back(std::make_pair(*it->second, innerParams));
        }
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr OGRFeatureDataStore::queryFeatures(FeatureCursorPtr &cursor) NOTHROWS
{
    // NOTE: we could clightly optimize by simply calling 'resetReading' on all of the layers, however, 
    return queryFeatures(cursor, FeatureQueryParameters());
}

TAKErr OGRFeatureDataStore::queryFeatures(FeatureCursorPtr &value, const FeatureQueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);

    std::list<std::pair<FeatureSetDefn, FeatureDataStore2::FeatureQueryParameters>> dbs;

    {
        Lock lock(mutex_);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);
        code = this->PrepareQuery(dbs, params);
        TE_CHECKRETURN_CODE(code);
    }

    if (dbs.empty()) {
        value = FeatureCursorPtr(new MultiplexingFeatureCursor(), Memory_deleter_const<FeatureCursor2, MultiplexingFeatureCursor>);
        return TE_Ok;
    }

    // if there is a limit/offset specified and we are going to be
    // querying more than one database we will need to brute force;
    // strip the offset/limit off of any DB instance queries 
    if (dbs.size() > 1 && params.limit)
    {
        std::list<std::pair<FeatureSetDefn, FeatureQueryParameters>>::iterator dbParams;
        for (dbParams = dbs.begin(); dbParams != dbs.end(); dbParams++) {
            dbParams->second.offset = 0;
            dbParams->second.limit = 0;
        }
    }

    GDALDataset_unique_ptr wfs(nullptr, nullptr);
    {
        code = OpenConnection(wfs);
        TE_CHECKRETURN_CODE(code);

        GDALDatasetH dataset = wfs.get();

        std::list<FeatureCursorPtr> cursors;

        std::list<std::pair<FeatureSetDefn, FeatureDataStore2::FeatureQueryParameters>>::iterator db;
        std::shared_ptr<void> shared_wfs;
        for(db = dbs.begin(); db != dbs.end(); db++) {
            OGRLayerH layer = GDALDatasetGetLayerByName(dataset, (*db).first.layerName);
            if (!layer)
            {
                Logger_log(TELL_Warning, "WFSClient: Failed to find layer %s", (*db).first.layerName.get());
                continue;
            }

            if (!shared_wfs.get())
                shared_wfs = std::move(wfs);

            if (ConfigureForQuery(layer, (*db).first, params) != TE_Ok) {
                Logger_log(TELL_Warning, "WFSClient: Failed to configure layer %s for query", (*db).first.layerName.get());
                continue;
            }

            cursors.push_back(std::move(FeatureCursorPtr(
                new OgrLayerFeatureCursor(*this, (*db).first, layer, shared_wfs),
                Memory_deleter_const<FeatureCursor2, OgrLayerFeatureCursor>)));
        }

        FeatureCursorPtr retval(nullptr, nullptr);
        if (cursors.size() == 1u) {
            retval = std::move(*cursors.begin());
        } else {
            std::unique_ptr<MultiplexingFeatureCursor> muxCursor(new MultiplexingFeatureCursor(*params.order));
            std::list<FeatureCursorPtr>::iterator it;
            for (it = cursors.begin(); it != cursors.end(); it++) {
                code = muxCursor->add(std::move(*it));
                TE_CHECKBREAK_CODE(code);
            }
            TE_CHECKRETURN_CODE(code);

            retval = FeatureCursorPtr(muxCursor.release(), Memory_deleter_const<FeatureCursor2, MultiplexingFeatureCursor>);
        }
        // cursors have been handed off
        cursors.clear();
        if (dbs.size() > 1 && params.limit)
            retval = FeatureCursorPtr(new BruteForceLimitOffsetFeatureCursor(std::move(retval), params.limit, params.offset), Memory_deleter_const<FeatureCursor2, BruteForceLimitOffsetFeatureCursor>);

        value = std::move(retval);
    }
    code = CloseConnection(std::move(wfs));
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr OGRFeatureDataStore::queryFeaturesCount(int *value) NOTHROWS
{
    // XXX - 
    return this->queryFeaturesCount(value, FeatureQueryParameters());
}

TAKErr OGRFeatureDataStore::queryFeaturesCount(int *value, const FeatureQueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);

    std::list<std::pair<FeatureSetDefn, FeatureDataStore2::FeatureQueryParameters>> dbs;
    {
        Lock lock(mutex_);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);
        code = this->PrepareQuery(dbs, params);
        TE_CHECKRETURN_CODE(code);
    }

    if (dbs.empty()) {
        *value = 0;
        return TE_Ok;
    }

    // if there is a limit/offset specified and we are going to be
    // querying more than one database we will need to brute force;
    // strip the offset/limit off of any DB instance queries 
    if (dbs.size() > 1 && params.limit)
    {
        std::list<std::pair<FeatureSetDefn, FeatureQueryParameters>>::iterator dbParams;
        for (dbParams = dbs.begin(); dbParams != dbs.end(); dbParams++) {
            dbParams->second.offset = 0;
            dbParams->second.limit = 0;
        }
    }

    GDALDataset_unique_ptr wfs(nullptr, nullptr);

    code = OpenConnection(wfs);
    TE_CHECKRETURN_CODE(code);

    GDALDatasetH dataset = wfs.get();

    *value = 0;

    std::list<std::pair<FeatureSetDefn, FeatureDataStore2::FeatureQueryParameters>>::iterator db;
    std::shared_ptr<void> shared_wfs;
    for (db = dbs.begin(); db != dbs.end(); db++) {
        OGRLayerH layer = GDALDatasetGetLayerByName(dataset, (*db).first.layerName);
        if (!layer)
        {
            Logger_log(TELL_Warning, "WFSClient: Failed to find layer %s", (*db).first.layerName.get());
            continue;
        }

        if (!shared_wfs.get())
            shared_wfs = std::move(wfs);

        if (ConfigureForQuery(layer, (*db).first, params) != TE_Ok) {
            Logger_log(TELL_Warning, "WFSClient: Failed to configure layer %s for query", (*db).first.layerName.get());
            continue;
        }

        int layerFeatureCount;
        int force = 0;
        do {
            layerFeatureCount = static_cast<int>(OGR_L_GetFeatureCount(layer, force));
            if (!force)
                force = 1;
            else
                break;
        } while (true);

        if (layerFeatureCount < 0)
            return TE_IllegalState;

        *value += layerFeatureCount;
    }

    code = CloseConnection(std::move(wfs));
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr OGRFeatureDataStore::getFeatureSet(FeatureSetPtr_const &value, const int64_t fsid) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator db;
    db = this->fsidToFeatureDb.find(fsid);
    if (db == this->fsidToFeatureDb.end())
        return TE_InvalidArg;

    value = FeatureSetPtr_const(
        new FeatureSet2(db->second->fsid,
                        db->second->provider,
                        db->second->type,
                        db->second->displayName,
                        db->second->minResolution,
                        db->second->maxResolution,
                        1LL),
        Memory_deleter_const<FeatureSet2>
    );

    return code;
}

TAKErr OGRFeatureDataStore::queryFeatureSets(FeatureSetCursorPtr &cursor) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    TE_CHECKRETURN_CODE(code);

    std::set<FeatureSetDefn, FeatureSetDefn_LT> retval;
    {
        std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator it;
        for (it = this->fsidToFeatureDb.begin(); it != this->fsidToFeatureDb.end(); it++)
            retval.insert(*it->second);
    }

    cursor = FeatureSetCursorPtr(
        new FeatureSetCursorImpl(retval),
        Memory_deleter_const<FeatureSetCursor2, FeatureSetCursorImpl>);

    return code;
}
TAKErr OGRFeatureDataStore::queryFeatureSets(FeatureSetCursorPtr &cursor, const FeatureSetQueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::set<FeatureSetDefn, FeatureSetDefn_LT> retval;

    if (!params.providers->empty()) {
        bool matched;
        if (AbstractFeatureDataStore2::matches(&matched, *params.providers, provider, '%') != TE_Ok || !matched) {
            cursor = FeatureSetCursorPtr(
                new FeatureSetCursorImpl(retval),
                Memory_deleter_const<FeatureSetCursor2, FeatureSetCursorImpl>);
            return TE_Ok;
        }
    }
    if (!params.types->empty()) {
        bool matched;
        if (AbstractFeatureDataStore2::matches(&matched, *params.types, type, '%') != TE_Ok || !matched) {
            cursor = FeatureSetCursorPtr(
                new FeatureSetCursorImpl(retval),
                Memory_deleter_const<FeatureSetCursor2, FeatureSetCursorImpl>);
            return TE_Ok;
        }
    }

    {
        std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator it;
        for (it = this->fsidToFeatureDb.begin(); it != this->fsidToFeatureDb.end(); it++)
            retval.insert(*it->second);
    }

    if (!params.ids->empty()) {
        auto it = retval.begin();
        while (it != retval.end()) {
            bool contained;
            int64_t fsid = (*it).fsid;
            code = params.ids->contains(&contained, fsid);
            TE_CHECKBREAK_CODE(code);

            if (!contained)
                it = retval.erase(it);
            else
                it++;
        }
        TE_CHECKRETURN_CODE(code);
    }

    if (params.visibleOnly)
    {
        auto it = retval.begin();
        while (it != retval.end()) {
            if ((*it).visible)
                it = retval.erase(it);
            else
                it++;
        }
    }
    if (!params.names->empty())
    {
        auto it = retval.begin();
        while (it != retval.end()) {
            bool matched;
            code = AbstractDataSourceFeatureDataStore2::matches(&matched, *params.names, (*it).displayName, '%');
            TE_CHECKBREAK_CODE(code);
            if (!matched) {
                it = retval.erase(it);
            } else {
                it++;
            }
        }
        TE_CHECKRETURN_CODE(code);
    }

    if (params.limit > 0) {
        for (std::size_t i = 0u; i < static_cast<std::size_t>(params.offset); i++) {
            if (retval.empty())
                break;
            retval.erase(retval.begin());
        }
        for (std::size_t i = 0u; i < static_cast<std::size_t>(params.limit); i++) {
            if (retval.empty())
                break;
            retval.erase(std::prev(retval.end()));
        }
    }

    cursor = FeatureSetCursorPtr(
        new FeatureSetCursorImpl(retval),
        Memory_deleter_const<FeatureSetCursor2, FeatureSetCursorImpl>);

    return code;
}

TAKErr OGRFeatureDataStore::queryFeatureSetsCount(int *value) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    *value = static_cast<int>(this->fsidToFeatureDb.size());
    return code;
}

TAKErr OGRFeatureDataStore::queryFeatureSetsCount(int *value, const FeatureSetQueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!params.providers->empty()) {
        bool matched;
        if (AbstractFeatureDataStore2::matches(&matched, *params.providers, provider, '%') != TE_Ok || !matched) {
            *value = 0;
            return TE_Ok;
        }
    }
    if (!params.types->empty()) {
        bool matched;
        if (AbstractFeatureDataStore2::matches(&matched, *params.types, type, '%') != TE_Ok || !matched) {
            *value = 0;
            return TE_Ok;
        }
    }

    std::set<int64_t> fsids;
    {
        std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator it;
        for (it = this->fsidToFeatureDb.begin(); it != this->fsidToFeatureDb.end(); it++)
            fsids.insert(it->first);
    }

    if (!params.ids->empty()) {
        auto it = fsids.begin();
        while (it != fsids.end()) {
            bool contained;
            int64_t fsid = *it;
            code = params.ids->contains(&contained, fsid);
            TE_CHECKBREAK_CODE(code);

            if (!contained)
                it = fsids.erase(it);
            else
                it++;
        }
        TE_CHECKRETURN_CODE(code);
    }

    if (params.visibleOnly)
    {
        auto it = fsids.begin();
        while (it != fsids.end()) {
            std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator db;
            db = this->fsidToFeatureDb.find(*it);
            if ((db == this->fsidToFeatureDb.end()) || !db->second->visible)
                it = fsids.erase(it);
            else
                it++;
        }
    }
    if (!params.names->empty())
    {
        auto it = fsids.begin();
        while (it != fsids.end()) {
            std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator db;
            db = this->fsidToFeatureDb.find(*it);
            if (db == this->fsidToFeatureDb.end()) {
                it = fsids.erase(it);
            } else {
                bool matched;
                code = AbstractDataSourceFeatureDataStore2::matches(&matched, *params.names, db->second->displayName, '%');
                TE_CHECKBREAK_CODE(code);
                if (!matched) {
                    it = fsids.erase(it);
                } else {
                    it++;
                }
            }
        }
        TE_CHECKRETURN_CODE(code);
    }

    *value = static_cast<int>(fsids.size());
    if (params.limit) {
        *value -= params.offset;
        *value = std::max(params.limit, *value);
        if (*value < 0)
            *value = 0;
    }
    return code;
}

TAKErr OGRFeatureDataStore::isFeatureVisible(bool *value, const int64_t fid) NOTHROWS
{
    const int64_t fsid = ((fid >> 32) & 0xFFFFFFFFLL);
    return this->isFeatureSetVisible(value, fsid);
}

TAKErr OGRFeatureDataStore::isFeatureSetVisible(bool *value, const int64_t fsid) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator db;
    db = this->fsidToFeatureDb.find(fsid);
    if (db == this->fsidToFeatureDb.end())
        return TE_InvalidArg;
    *value = db->second->visible;

    return code;
}

TAKErr OGRFeatureDataStore::setFeatureSetReadOnlyImpl(const int64_t fsid, const bool readOnly) NOTHROWS 
{
    return Util::TE_Ok; 
}

TAKErr OGRFeatureDataStore::setFeatureSetsReadOnlyImpl(const FeatureSetQueryParameters& params, const bool visible) NOTHROWS
{
    return Util::TE_Ok; 
}

TAKErr OGRFeatureDataStore::isFeatureSetReadOnly(bool *value, const int64_t fsid) NOTHROWS 
{
    if (value) {
        *value = false;
    }
    return Util::TE_Ok;
}

TAKErr OGRFeatureDataStore::isFeatureReadOnly(bool *value, const int64_t fid) NOTHROWS 
{
    if (value) {
        *value = false;
    }
    return Util::TE_Ok;
}

TAKErr OGRFeatureDataStore::isAvailable(bool *value) NOTHROWS
{
    *value = !this->disposing && !this->disposed;
    return TE_Ok;
}

TAKErr OGRFeatureDataStore::refresh() NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (this->disposing || this->disposed)
        return TE_IllegalState;

    if (this->backgroundRefresh) {
        if (this->refreshThread.get())
            return TE_Ok;

        this->refreshRequest++;
        ThreadCreateParams params;
        params.priority = TETP_Normal;
        code = Thread_start(this->refreshThread, asyncRefresh, this, params);
        TE_CHECKRETURN_CODE(code);
    } else {
        code = this->refreshImpl();
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

void *OGRFeatureDataStore::asyncRefresh(void *opaque)
{
    auto *instance = static_cast<OGRFeatureDataStore *>(opaque);
    while (true) {
        int serviceRequest;
        {
            Lock lock(instance->mutex_);
            if (lock.status != TE_Ok)
                break;
            if (instance->disposing)
                break;
            serviceRequest = instance->refreshRequest;
        }

        instance->refreshImpl();
        {
            Lock lock(instance->mutex_);
            if (lock.status != TE_Ok)
                break;

            // if the refresh serviced the current request, we're done
            if (instance->refreshRequest == serviceRequest)
                break;
        }
    }
    instance->refreshThread->detach();
    if (!instance->disposing)
        instance->refreshThread.reset();
    return nullptr;
}

TAKErr OGRFeatureDataStore::getUri(TAK::Engine::Port::String &value) NOTHROWS
{
    value = this->workingDir;
    return TE_Ok;
}

TAKErr OGRFeatureDataStore::setFeatureVisibleImpl(const int64_t fid, const bool visible) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::setFeaturesVisibleImpl(const FeatureQueryParameters &params, const bool visible) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::setFeatureSetVisibleImpl(const int64_t fsid, const bool visible) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator db;
    db = this->fsidToFeatureDb.find(fsid);
    if (db == this->fsidToFeatureDb.end())
        return TE_InvalidArg;
    db->second->visible = visible;
    this->dispatchDataStoreContentChangedNoSync(true);
    return code;
}

TAKErr OGRFeatureDataStore::setFeatureSetsVisibleImpl(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS
{
    TAKErr code(TE_Ok);
    
    Lock lock(mutex_);
    TE_CHECKRETURN_CODE(code);

    if (!params.providers->empty())
    {
        bool contains;
        code = params.providers->contains(&contains, provider);
        TE_CHECKRETURN_CODE(code);
        if (!contains)
            return TE_InvalidArg;
    }
    if (!params.types->empty())
    {
        bool contains;
        code = params.types->contains(&contains, type);
        TE_CHECKRETURN_CODE(code);
        if (!contains)
            return TE_InvalidArg;
    }

    std::set<int64_t> fsids;
    {
        std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator it;
        for (it = this->fsidToFeatureDb.begin(); it != this->fsidToFeatureDb.end(); it++)
            fsids.insert(it->first);
    }

    if (!params.ids->empty())
    {
        auto it = fsids.begin();
        while (it != fsids.end()) {
            bool contains;
            int64_t fsid = *it;
            code = params.ids->contains(&contains, fsid);
            TE_CHECKBREAK_CODE(code);
            if (!contains) {
                it = fsids.erase(it);
            } else {
                it++;
            }
        }
        TE_CHECKRETURN_CODE(code);
    }

    if (params.visibleOnly)
    {
        auto it = fsids.begin();
        while (it != fsids.end()) {
            std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator db;
            db = this->fsidToFeatureDb.find(*it);
            if ((db == this->fsidToFeatureDb.end()) || !db->second->visible) {
                it = fsids.erase(it);
            } else {
                it++;
            }
        }
    }
    if (!params.names->empty())
    {
        auto it = fsids.begin();
        while (it != fsids.end()) {
            std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator db;
            db = this->fsidToFeatureDb.find(*it);
            if (db == this->fsidToFeatureDb.end()) {
                it = fsids.erase(it);
            } else {
                bool matched;
                code = AbstractDataSourceFeatureDataStore2::matches(&matched, *params.names, db->second->displayName, '%');
                TE_CHECKBREAK_CODE(code);
                if (!matched) {
                    it = fsids.erase(it);
                } else {
                    it++;
                }
            }
        }
        TE_CHECKRETURN_CODE(code);
    }

    std::set<int64_t>::iterator it;
    for(it = fsids.begin(); it != fsids.end(); it++) {
        std::map<int64_t, std::shared_ptr<FeatureSetDefn>>::iterator fdb;
        fdb = this->fsidToFeatureDb.find(*it);
        if (fdb != this->fsidToFeatureDb.end())
            fdb->second->visible = visible;
    }

    this->dispatchDataStoreContentChangedNoSync(true);

    return code;
}

TAKErr OGRFeatureDataStore::beginBulkModificationImpl() NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::endBulkModificationImpl(const bool successful) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::deleteAllFeatureSetsImpl() NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::insertFeatureSetImpl(FeatureSetPtr_const *featureSet, const char *provider_val, const char *type_val, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::updateFeatureSetImpl(const int64_t fsid, const char *name) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::updateFeatureSetImpl(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::updateFeatureSetImpl(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::deleteFeatureSetImpl(const int64_t fsid) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::insertFeatureImpl(FeaturePtr_const *feature, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::updateFeatureImpl(const int64_t fid, const char *name) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::updateFeatureImpl(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::updateFeatureImpl(const int64_t fid, const TAK::Engine::Feature::AltitudeMode altitudeMode, const double extrude) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::updateFeatureImpl(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::updateFeatureImpl(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::updateFeatureImpl(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::deleteFeatureImpl(const int64_t fid) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::deleteAllFeaturesImpl(const int64_t fsid) NOTHROWS
{
    return TE_Unsupported;
}

TAKErr OGRFeatureDataStore::createSchemaHandler(SchemaHandlerPtr &value, GDALDatasetH dataset) const NOTHROWS
{
    TAKErr code(TE_Ok);

    OGRDriverDefinition2Ptr driverDef(nullptr, nullptr);
    const char *driverName = "ogr";
    if (dataset) {
        GDALDriverH driver = GDALGetDatasetDriver(dataset);
        driverName = GDALGetDriverShortName(driver);
        code = OGRDriverDefinition2_create(driverDef, uri, driverName);
    }
    if (!dataset || code != TE_Ok) {
        driverDef = OGRDriverDefinition2Ptr(new DefaultDriverDefinition2(driverName, driverName, 1), Memory_deleter_const<OGRDriverDefinition2, DefaultDriverDefinition2>);
        code = TE_Ok;
    }

    value = SchemaHandlerPtr(new DriverDefSchemaHandler(std::move(driverDef)), Memory_deleter_const<SchemaHandler, DriverDefSchemaHandler>);
    return code;
}

/**************************************************************************/

OGRFeatureDataStore::SchemaHandler::~SchemaHandler() NOTHROWS
{}

OGRFeatureDataStore::FeatureSetCursorImpl::FeatureSetCursorImpl(std::set<FeatureSetDefn, FeatureSetDefn_LT> defns_) NOTHROWS :
    defns(defns_),
    impl(defns.begin()),
    first(true),
    row(nullptr, nullptr)
{}

TAKErr OGRFeatureDataStore::FeatureSetCursorImpl::get(const FeatureSet2 **value) NOTHROWS
{
    if (this->impl == this->defns.end())
        return TE_IllegalState;

    if (!this->row.get()) {
        this->row = FeatureSetPtr_const(
            new FeatureSet2((*this->impl).fsid,
                (*this->impl).provider,
                (*this->impl).type,
                (*this->impl).displayName,
                (*this->impl).minResolution,
                (*this->impl).maxResolution,
                1LL),
            Memory_deleter_const<FeatureSet2>);
    }

    *value = this->row.get();
    return TE_Ok;
}

TAKErr OGRFeatureDataStore::FeatureSetCursorImpl::moveToNext() NOTHROWS
{
    if (impl == defns.end())
        return TE_Done;
    if (!first)
        impl++;
    first = false;
    return (impl != defns.end()) ? TE_Ok : TE_Done;
}

/**************************************************************************/

OGRFeatureDataStore::OgrLayerFeatureCursor::OgrLayerFeatureCursor(OGRFeatureDataStore &owner_, const FeatureSetDefn &defn_, OGRLayerH layer_, const GDALDataset_shared_ptr &conn_) NOTHROWS :
    owner(owner_),
    defn(defn_),
    layer(layer_),
    conn(conn_),
    layer2lla(nullptr, nullptr)
{
    OGRSpatialReferenceH layerSpatialRef = OGR_L_GetSpatialRef(this->layer);
    if (layerSpatialRef && GetSpatialReferenceID(layerSpatialRef) != 4326)
    {
        OGRSpatialReferenceH spatialRef4326 = GetSR4326();
        layer2lla = OGRCoordinateTransformation_unique_ptr(OCTNewCoordinateTransformation(layerSpatialRef, spatialRef4326), OGRCoordinateTransformation_delete);
    }
}

TAKErr OGRFeatureDataStore::OgrLayerFeatureCursor::get(const Feature2 **feature) NOTHROWS
{
    TAKErr code(TE_Ok);

    if (!this->rowData.get())
        return TE_IllegalState;

    if (!this->rowData->feature.get()) {
        int64_t fsid;
        code = this->getFeatureSetId(&fsid);
        TE_CHECKRETURN_CODE(code);

        int64_t fid;
        code = this->getId(&fid);
        TE_CHECKRETURN_CODE(code);

        const char *name;
        code = this->getName(&name);
        TE_CHECKRETURN_CODE(code);

        GeometryPtr_const geom(nullptr, nullptr);
        code = ogr2geom(geom, this->rowData->ogrFeature);
        TE_CHECKRETURN_CODE(code);

        AltitudeMode altitudeMode = this->getAltitudeMode();
        double extrude = this->getExtrude();

        RawData rawData;
        code = this->getRawStyle(&rawData);
        TE_CHECKRETURN_CODE(code);

        const atakmap::util::AttributeSet *attribs;
        code = this->getAttributes(&attribs);
        TE_CHECKRETURN_CODE(code);

        int64_t version;
        code = this->getVersion(&version);
        TE_CHECKRETURN_CODE(code);

        this->rowData->feature = FeaturePtr_const(
            new Feature2(
                fid, fsid, name, std::move(geom), altitudeMode, extrude,
                std::move(StylePtr_const(this->rowData->style.get(), Memory_leaker_const<atakmap::feature::Style>)),
                std::move(AttributeSetPtr_const(this->rowData->attributes.get(), Memory_leaker_const<atakmap::util::AttributeSet>)),
                version),
            Memory_deleter_const<Feature2>);
    }

    *feature = this->rowData->feature.get();
    return code;
}

TAKErr OGRFeatureDataStore::OgrLayerFeatureCursor::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!this->rowData.get())
        return TE_IllegalState;
    if (!this->rowData->attributes.get()) {
        this->rowData->attributes.reset(new atakmap::util::AttributeSet());
        code = ogr2attr(this->rowData->attributes.get(), this->rowData->ogrFeature);
        TE_CHECKRETURN_CODE(code);
    }
    *value = this->rowData->attributes.get();
    return code;
}

TAKErr OGRFeatureDataStore::OgrLayerFeatureCursor::getFeatureSetId(int64_t *value) NOTHROWS
{
    *value = this->defn.fsid;
    return TE_Ok;
}

FeatureDefinition2::GeometryEncoding OGRFeatureDataStore::OgrLayerFeatureCursor::getGeomCoding() NOTHROWS
{
    return FeatureDefinition2::GeomWkb;
}

TAKErr OGRFeatureDataStore::OgrLayerFeatureCursor::getId(int64_t *value) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!this->rowData.get())
        return TE_IllegalState;
    *value = ((int64_t)this->defn.fsid << 32) | (int64_t)OGR_F_GetFID(this->rowData->ogrFeature);
    return code;
}

TAKErr OGRFeatureDataStore::OgrLayerFeatureCursor::getName(const char **value) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!this->rowData.get())
        return TE_IllegalState;
    if (!this->rowData->name.get()) {
        const atakmap::util::AttributeSet *attribs(nullptr);
        code = this->getAttributes(&attribs);
        TE_CHECKRETURN_CODE(code);

        this->rowData->name.reset(new TAK::Engine::Port::String());
        code = this->defn.schema->getFeatureName(*this->rowData->name, this->layer, this->rowData->ogrFeature, *attribs);
        TE_CHECKRETURN_CODE(code);
    }
    *value = this->rowData->name->get();
    return code;
}

TAKErr OGRFeatureDataStore::OgrLayerFeatureCursor::getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS
{
    if (!value)
        return TE_InvalidArg;

    if (!this->rowData.get())
        return TE_IllegalState;

    if(!this->rowData->wkb.get()) {
        OGRGeometry_unique_ptr geometry(OGR_F_GetGeometryRef(this->rowData->ogrFeature), Memory_leaker<void>);
        if (!geometry.get())
            return TE_Err;

        if (this->layer2lla.get()) {
            geometry = OGRGeometry_unique_ptr(OGR_G_Clone(geometry.get()), OGR_G_DestroyGeometry);
            if (!geometry.get())
                return TE_IllegalState;
            if (OGR_G_Transform(geometry.get(), this->layer2lla.get()) != CE_None)
                return TE_Err;
        }

        this->rowData->wkbSize = OGR_G_WkbSize(geometry.get());
        if (this->rowData->wkbSize < 0)
            return TE_Err;
        
        this->rowData->wkb.reset(new uint8_t[this->rowData->wkbSize]);
        if(OGR_G_ExportToWkb(geometry.get(),
                             (TE_PlatformEndian == TE_LittleEndian) ? wkbNDR : wkbXDR,
                             this->rowData->wkb.get()) != CE_None) {

            return TE_Err;
        }
    }

    value->binary.len = this->rowData->wkbSize;
    value->binary.value = this->rowData->wkb.get();

    return TE_Ok;
}

AltitudeMode OGRFeatureDataStore::OgrLayerFeatureCursor::getAltitudeMode() NOTHROWS
{
    return AltitudeMode::TEAM_ClampToGround;
}

double OGRFeatureDataStore::OgrLayerFeatureCursor::getExtrude() NOTHROWS
{
    return 0.0;
}

TAKErr OGRFeatureDataStore::OgrLayerFeatureCursor::getRawStyle(FeatureDefinition2::RawData *value) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!this->rowData.get())
        return TE_IllegalState;
    if(!this->rowData->style.get()) {
        const atakmap::util::AttributeSet *attribs(nullptr);
        if (this->defn.schema->styleRequiresAttributes()) {
            code = this->getAttributes(&attribs);
            TE_CHECKRETURN_CODE(code);
        }
        code = this->defn.schema->getFeatureStyle(this->rowData->style, this->layer, this->rowData->ogrFeature, *attribs);
        TE_CHECKRETURN_CODE(code);
    }
    value->object = this->rowData->style.get();
    return code;
}

FeatureDefinition2::StyleEncoding OGRFeatureDataStore::OgrLayerFeatureCursor::getStyleCoding() NOTHROWS
{
    // XXX - should probably be returning OGR style??? not sure if we want to round-trip for WFS though...
    return FeatureDefinition2::StyleStyle;
}

TAKErr OGRFeatureDataStore::OgrLayerFeatureCursor::getVersion(int64_t *value) NOTHROWS
{
    // XXX - query time
    *value = 1LL;
    return TE_Ok;
}

TAKErr OGRFeatureDataStore::OgrLayerFeatureCursor::moveToNext() NOTHROWS
{
#if 1
    this->rowData.reset();
    OGRFeatureH ogrFeature = OGR_L_GetNextFeature(this->layer);
    if (!ogrFeature)
        return TE_Done;
    this->rowData.reset(new OgrFeatureCursorRowData());
    this->rowData->ogrFeature = ogrFeature;
    return TE_Ok;
#else
    return TE_Done;
#endif
}


namespace
{
    OgrFeatureCursorRowData::OgrFeatureCursorRowData() NOTHROWS :
        ogrFeature(nullptr),
        version(FeatureDataStore2::FEATURE_VERSION_NONE),
        wkb(nullptr),
        wkbSize(-1),
        style(nullptr, nullptr),
        feature(nullptr, nullptr)
    {}

    double min(double a, double b, double c, double d) NOTHROWS
    {
        return std::min(std::min(a, b), std::min(c, d));
    }
    double max(double a, double b, double c, double d) NOTHROWS
    {
        return std::max(std::max(a, b), std::max(c, d));
    }

    TAKErr GetStyleGeomType(atakmap::feature::Geometry::Type *value, OGRGeometryH feature) NOTHROWS
    {
        if (!value)
            return TE_InvalidArg;
        if (!feature)
            return TE_InvalidArg;

        TAKErr code(TE_Ok);
        OGRwkbGeometryType ogrGeomType = OGR_G_GetGeometryType(feature);
        switch (ogrGeomType)
        {
        case wkbMultiPoint:
        case wkbMultiPoint25D:
        case wkbPoint:
        case wkbPoint25D:
            *value = atakmap::feature::Geometry::POINT;
            break;
        case wkbCircularString:
        case wkbCircularStringZ:
        case wkbCompoundCurve:
        case wkbCompoundCurveZ:
        case wkbLinearRing:
        case wkbLineString:
        case wkbLineString25D:
        case wkbMultiCurve:
        case wkbMultiCurveZ:
        case wkbMultiLineString:
        case wkbMultiLineString25D:
            *value = atakmap::feature::Geometry::LINESTRING;
            break;
        case wkbCurvePolygon:
        case wkbCurvePolygonZ:
        case wkbMultiPolygon:
        case wkbMultiPolygon25D:
        case wkbMultiSurface:
        case wkbMultiSurfaceZ:
        case wkbPolygon:
        case wkbPolygon25D:
            *value = atakmap::feature::Geometry::POLYGON;
            break;
        case wkbGeometryCollection:
        case wkbGeometryCollection25D:
        {
            std::set<atakmap::feature::Geometry::Type> types;
            int childCount = OGR_G_GetGeometryCount(feature);
            for (int i = 0; i < childCount; i++)
            {
                atakmap::feature::Geometry::Type childType;
                OGRGeometryH child = OGR_G_GetGeometryRef(feature, i);
                if (!child || GetStyleGeomType(&childType, child) != TE_Ok)
                    continue;

                types.insert(childType);
            }
            if (types.size() == 1u)
                *value = *types.begin();
            else
                //throw new ArgumentException("Unknow geometry type encountered");
                code = TE_InvalidArg;
            break;
        }
        default:
            //throw new ArgumentException("Unknow geometry type encountered");
            code = TE_InvalidArg;
            break;
        }

        return code;
    }

    TAK::Engine::Port::String GetDescription(OGRLayerH layer, OGRFeatureH feature) NOTHROWS
    {
        StringBuilder retval;

        int fieldCount = OGR_F_GetFieldCount(feature);
        for (int i = 0; i < fieldCount; i++)
        {
            OGRFieldDefnH def = OGR_F_GetFieldDefnRef(feature, i);
            if (!def)
                continue;

            retval << OGR_FD_GetName(def);
            retval << ": ";
            OGRFieldType type = OGR_Fld_GetType(def);
            if (type == OFTInteger)
            {
                retval << OGR_F_GetFieldAsInteger(feature, i);
            }
            else if (type == OFTInteger64)
            {
                retval << OGR_F_GetFieldAsInteger64(feature, i);
            }
            else if (type == OFTReal)
            {
                retval << OGR_F_GetFieldAsDouble(feature, i);
            }
            else if (type == OFTString)
            {
                retval << OGR_F_GetFieldAsString(feature, i);
            }
            retval << "\n";
        }

        return retval.c_str();
    }

    int GetSpatialReferenceID(OGRSpatialReferenceH srs) NOTHROWS
    {
        if (!srs)
            return -1;

        const char *value = OSRGetAttrValue(srs, "AUTHORITY", 0);
        if (!value || strcmp(value, "EPSG") != 0)
            return -1;

        value = OSRGetAttrValue(srs, "AUTHORITY", 1);
        if (!value)
            return -1;

        int srid;
        if (Platform_atoi(&srid, value) != TE_Ok)
            return -1;
        return srid;
    }
    OGRSpatialReferenceH GetSR4326()
    {
        static OGRSpatialReferenceH sr4326 = nullptr;
        if (!sr4326)
        {
            OGRSpatialReferenceH sr = OSRNewSpatialReference(nullptr);
            if (OSRImportFromEPSG(sr, 4326) == CE_None)
                sr4326 = sr;
        }
        return sr4326;
    }
    TAKErr ogr2geom(GeometryPtr_const &value, OGRFeatureH feature) NOTHROWS
    {
        TAKErr code(TE_Ok);
        OGRGeometryH geom = OGR_F_GetGeometryRef(feature);
        if (!geom)
            return TE_InvalidArg;

        OGRGeometry_unique_ptr geomPtr(OGR_G_Clone(geom), OGR_G_DestroyGeometry);
        if (OGR_G_TransformTo(geomPtr.get(), GetSR4326()) != CE_None)
            return TE_Err;

        int wkbSize = OGR_G_WkbSize(geomPtr.get());
        if (wkbSize <= 0)
            return TE_InvalidArg;

        array_ptr<unsigned char> wkb(new unsigned char[wkbSize]);
        OGR_G_ExportToWkb(geomPtr.get(), (TE_PlatformEndian == TE_LittleEndian) ? wkbNDR : wkbXDR, wkb.get());

#if 0
        Geometry2Ptr geom2(NULL, NULL);
        code = GeometryFactory_fromWkb(geom2, wkb.get(), wkbSize);
        TE_CHECKRETURN_CODE(code);

        code = LegacyAdapters_adapt(value, *geom2);
        TE_CHECKRETURN_CODE(code);
#else
        atakmap::feature::ByteBuffer wkb_bb(wkb.get(), wkb.get() + wkbSize);
        try {
            value = GeometryPtr_const(atakmap::feature::parseWKB(wkb_bb), atakmap::feature::destructGeometry);
            if (!value.get())
                return TE_Err;
        } catch (...) {
            return TE_Err;
        }
#endif

        return code;
    }
    TAKErr ogr2attr(atakmap::util::AttributeSet *value, OGRFeatureH feature) NOTHROWS
    {
        int fieldCount = OGR_F_GetFieldCount(feature);
        for (int i = 0; i < fieldCount; i++)
        {
            OGRFieldDefnH def = OGR_F_GetFieldDefnRef(feature, i);
            if (!def)
                continue;
            OGRFieldType type = OGR_Fld_GetType(def);
            if (type == OFTInteger) {
                value->setInt(OGR_Fld_GetNameRef(def), OGR_F_GetFieldAsInteger(feature, i));
            } else if (type == OFTInteger64) {
                value->setLong(OGR_Fld_GetNameRef(def), OGR_F_GetFieldAsInteger64(feature, i));
            } else if (type == OFTReal) {
                value->setDouble(OGR_Fld_GetNameRef(def), OGR_F_GetFieldAsDouble(feature, i));
            } else if (type == OFTString) {
                value->setString(OGR_Fld_GetNameRef(def), OGR_F_GetFieldAsString(feature, i));
            }
        }
        return TE_Ok;
    }
}
