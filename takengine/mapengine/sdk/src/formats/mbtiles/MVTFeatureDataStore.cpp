#include "formats/mbtiles/MVTFeatureDataStore.h"

#ifdef __ANDROID__
namespace std {
    template<class T>
    inline std::string to_string(const T &v)
    {
        std::ostringstream strm;
        strm << v;
        return strm.str();
    }
    inline float strtof(const char *str, char **endptr)
    {
        return ::strtof(str, endptr);
    }
    inline double strtold(const char *str, char **endptr)
    {
        return ::strtold(str, endptr);
    }
    inline long long int strtoll(const char *str, char **endptr, int base = 10)
    {
        return ::strtoll(str, endptr, base);
    }
    inline unsigned long long int strtoull(const char *str, char **endptr, int base = 10)
    {
        return ::strtoull(str, endptr, base);
    }
    inline int stoi(const std::string &str, std::size_t *numRead, int base = 10)
    {
        const char *cstr = str.c_str();
        char *endptr;
        const int result = strtol(cstr, &endptr, base);
        if(numRead)
            *numRead = (endptr-cstr);
        return result;
    }
    inline int snprintf(char *s, size_t m, const char *fmt, ...)
    {
        va_list args;
        va_start(args, fmt);
        int result = ::vsnprintf(s, m , fmt, args);
        va_end(args);
        return result;
    }
}
#endif

#include <cpl_vsi.h>
#include <ogrsf_frmts.h>
#include <tinygltf/json.hpp>

#include "db/Database2.h"
#include "db/Query.h"
#include "feature/FeatureCursor2.h"
#include "feature/FeatureSetCursor2.h"
#include "feature/MultiplexingFeatureCursor.h"
#include "formats/mbtiles/MBTilesInfo.h"
#include "formats/ogr/AutoStyleSchemaHandler.h"
#include "formats/ogr/OGRFeatureDataStore.h"
#include "raster/osm/OSMUtils.h"
#include "thread/RWMutex.h"
#include "util/DataInput2.h"
#include "util/DataOutput2.h"
#include "util/Memory.h"

using namespace TAK::Engine::Formats::MBTiles;

using namespace TAK::Engine::DB;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Formats::OGR;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
    class VSIMEMFILE
    {
    public :
        VSIMEMFILE(const uint8_t* data, const std::size_t dataLen, const char *vsipath) NOTHROWS;
        ~VSIMEMFILE() NOTHROWS;
    public :
        operator const char* () NOTHROWS;
        VSIMEMFILE &operator=(VSIMEMFILE &&other) NOTHROWS;
    private :
        TAK::Engine::Port::String linkedPath;
    };

    struct FeatureId
    {
        struct
        {
            int x;
            int y;
            int z;
        } tile;
        int64_t mvt_id;

        bool operator<(const FeatureId& other) const
        {
            if (mvt_id < other.mvt_id)
                return true;
            else if (mvt_id > other.mvt_id)
                return false;
            else if (tile.z < other.tile.z)
                return true;
            else if (tile.z > other.tile.z)
                return false;
            else if (tile.y < other.tile.y)
                return true;
            else if (tile.y > other.tile.y)
                return false;
            else if (tile.x < other.tile.x)
                return true;
            else if (tile.x > other.tile.x)
                return false;
            else
                return false;
        }
    };

    class QueryContextImpl
    {
    public :
        QueryContextImpl(const char* path, const std::shared_ptr<OGRFeatureDataStore::SchemaHandler> &schema) NOTHROWS;
    public :
        RWMutex mutex;
        DatabasePtr database;
        std::size_t minZoom;
        std::size_t maxZoom;
        struct {
            int minX {0};
            int minY {0};
            int maxX {0};
            int maxY {0};
        } bounds;
        VSIMEMFILE metadata;
        TAK::Engine::Port::String json;
        std::shared_ptr<OGRFeatureDataStore::SchemaHandler> schema;
        struct {
            std::map<FeatureId, int64_t> forward;
            std::map<int64_t, FeatureId> inverse;
        } fids;
        std::map<std::string, FeatureSet2> fsids;
        std::map<int64_t, bool> fsvisible;
        int64_t nextFid{ 1LL };
    };

    class SpatialFilterFeatureCursorImpl : public FeatureCursor2
    {
    public :
        SpatialFilterFeatureCursorImpl(const std::shared_ptr<QueryContextImpl>& ctx, const int level, const int stx, const int sty, const int ftx, const int fty, const bool visonly) NOTHROWS;
    public: // FeatureCursor2
        TAKErr getId(int64_t *value) NOTHROWS override;
        TAKErr getFeatureSetId(int64_t *value) NOTHROWS override;
        TAKErr getVersion(int64_t *value) NOTHROWS override;
    public: // FeatureDefinition2
        TAKErr getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS override;
        FeatureDefinition2::GeometryEncoding getGeomCoding() NOTHROWS override;
        AltitudeMode getAltitudeMode() NOTHROWS override;
        double getExtrude() NOTHROWS override;
        TAKErr getName(const char **value) NOTHROWS override;
        FeatureDefinition2::StyleEncoding getStyleCoding() NOTHROWS override;
        TAKErr getRawStyle(FeatureDefinition2::RawData *value) NOTHROWS override;
        TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS override;
        TAKErr get(const Feature2 **feature) NOTHROWS override;
    public: // RowIterator
        TAKErr moveToNext() NOTHROWS override;
    private :
        std::shared_ptr<QueryContextImpl> ctx;
        std::unique_ptr<OGRFeatureDataStore> ds;
        FeatureCursorPtr impl;
        VSIMEMFILE pbf;
        QueryPtr tile;
        int64_t fid;
        int64_t fsid;
        std::map<int64_t, int64_t> fsidTranslate;
        bool visibleOnly;
        int level;
        int tx;
        int ty;
    };

    class FeatureIdCursor : public FeatureCursor2
    {
    public :
        FeatureIdCursor(FeatureDataStore2 &datastore, TAK::Engine::Port::Collection<int64_t> &fids) NOTHROWS;
    public: // FeatureCursor2
        TAKErr getId(int64_t *value) NOTHROWS override;
        TAKErr getFeatureSetId(int64_t *value) NOTHROWS override;
        TAKErr getVersion(int64_t *value) NOTHROWS override;
    public: // FeatureDefinition2
        TAKErr getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS override;
        FeatureDefinition2::GeometryEncoding getGeomCoding() NOTHROWS override;
        AltitudeMode getAltitudeMode() NOTHROWS override;
        double getExtrude() NOTHROWS override;
        TAKErr getName(const char **value) NOTHROWS override;
        FeatureDefinition2::StyleEncoding getStyleCoding() NOTHROWS override;
        TAKErr getRawStyle(FeatureDefinition2::RawData *value) NOTHROWS override;
        TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS override;
        TAKErr get(const Feature2 **feature) NOTHROWS override;
    public: // RowIterator
        TAKErr moveToNext() NOTHROWS override;
    private :
        FeatureDataStore2 &ds;
        std::vector<int64_t> fids;
        int it;
        FeaturePtr_const row;
    };

    class FeatureSetCursorImpl : public FeatureSetCursor2
    {
    public :
        FeatureSetCursorImpl(const std::vector<FeatureSet2> &values) NOTHROWS;
    public :
        TAKErr get(const FeatureSet2 **value) NOTHROWS override;
    public :
        TAKErr moveToNext() NOTHROWS override;
    private :
        std::vector<FeatureSet2> values;
        int idx;
        bool done;
    };

    TAKErr createFeatureSetIDTranslationTable(std::map<int64_t, int64_t> *value, const QueryContextImpl &ctx, FeatureDataStore2 &ds) NOTHROWS;
}

class MVTFeatureDataStore::QueryContext
{
    public :
        QueryContext(const char* path, const std::shared_ptr<OGRFeatureDataStore::SchemaHandler> &schema) NOTHROWS :
            impl(new QueryContextImpl(path, schema))
        {}
    public :
        std::shared_ptr<QueryContextImpl> impl;
};

MVTFeatureDataStore::MVTFeatureDataStore(const char* uri_, const char* workingDir_) NOTHROWS :
    AbstractFeatureDataStore2(0, VISIBILITY_SETTINGS_FEATURESET|VISIBILITY_SETTINGS_FEATURE),
    uri(uri_),
    workingDir(workingDir_),
    schema(std::move(OGRFeatureDataStore::SchemaHandlerPtr(new AutoStyleSchemaHandler(), Memory_deleter_const<OGRFeatureDataStore::SchemaHandler, AutoStyleSchemaHandler>))),
    ctx(new QueryContext(uri_, schema))
{
    if (workingDir) {
        FileInput2 visrecord;
        std::ostringstream strm;
        strm << workingDir << "/fsvis";
        if (visrecord.open(strm.str().c_str()) == TE_Ok) {
            for (std::size_t i = 0u; i < (visrecord.length() / (sizeof(int64_t) + sizeof(int))); i++) {
                int64_t fsid;
                int vis;
                visrecord.readLong(&fsid);
                visrecord.readInt(&vis);
                ctx->impl->fsvisible[fsid] = !!vis;
            }
        }
    }
}
MVTFeatureDataStore::~MVTFeatureDataStore() NOTHROWS
{}

TAKErr MVTFeatureDataStore::getFeature(FeaturePtr_const &feature, const int64_t fid) NOTHROWS
{
    TAKErr code(TE_Ok);
    ReadLock rlock(ctx->impl->mutex);
    auto entry = ctx->impl->fids.inverse.find(fid);
    if (entry == ctx->impl->fids.inverse.end())
        return TE_InvalidArg;
    
    QueryPtr tile(nullptr, nullptr);
    code = ctx->impl->database->compileQuery(tile, "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ? LIMIT 1");
    TE_CHECKRETURN_CODE(code);

    TE_CHECKRETURN_CODE(code);
    code = tile->bindInt(1, entry->second.tile.z);
    TE_CHECKRETURN_CODE(code);
    code = tile->bindInt(2, entry->second.tile.x);
    TE_CHECKRETURN_CODE(code);
    code = tile->bindInt(3, ((1 << entry->second.tile.z) - entry->second.tile.y - 1));
    TE_CHECKRETURN_CODE(code);

    code = tile->moveToNext();
    TE_CHECKRETURN_CODE(code);

    const uint8_t *blob;
    std::size_t blobLen;
    code = tile->getBlob(&blob, &blobLen, 0u);
    TE_CHECKRETURN_CODE(code);
    if (!blob || !blobLen)
        return TE_Err;

    VSIMEMFILE pbf(nullptr, 0u, nullptr);
    {
        std::ostringstream strm;
        strm << (uintptr_t)ctx->impl->database.get() << "/" << entry->second.tile.z << "/" << entry->second.tile.y << "/" << entry->second.tile.x;
        pbf = VSIMEMFILE(blob, blobLen, strm.str().c_str());
    }

    // open PBF blob with OGR feature data source
    char **opts = nullptr;
    {
        std::ostringstream strm;
        strm << entry->second.tile.x;
        opts = CSLSetNameValue(opts, "X", strm.str().c_str());
    }
    {
        std::ostringstream strm;
        //strm << ((1<<level)-idx.y-1);
        strm << entry->second.tile.y;
        opts = CSLSetNameValue(opts, "Y", strm.str().c_str());
    }
    {
        std::ostringstream strm;
        strm << entry->second.tile.z;
        opts = CSLSetNameValue(opts, "Z", strm.str().c_str());
    }
    if (ctx->impl->metadata) {
        opts = CSLSetNameValue(opts, "METADATA_FILE", ctx->impl->metadata);
    }

    OGRFeatureDataStore ds(pbf, "MVT", (const char **)opts, ctx->impl->schema);
    CSLDestroy(opts);

    code = ds.getFeature(feature, entry->second.mvt_id);
    TE_CHECKRETURN_CODE(code);

    std::map<int64_t, int64_t> fsidTranslate;
    code = createFeatureSetIDTranslationTable(&fsidTranslate, *ctx->impl, ds);
    TE_CHECKRETURN_CODE(code);

    const auto fsid = fsidTranslate.find(feature->getFeatureSetId());
    if (fsid == fsidTranslate.end())
        return TE_IllegalState;

    // translate the FID
    feature = FeaturePtr_const(
        new Feature2(fid,
                     fsid->second,
                     feature->getName(),
                     *feature->getGeometry(),
                     feature->getAltitudeMode(),
                     feature->getExtrude(),
                     *feature->getStyle(),
                     *feature->getAttributes(),
                     feature->getVersion()),
        Memory_deleter_const<Feature2>);
    return TE_Ok;
}
TAKErr MVTFeatureDataStore::queryFeatures(FeatureCursorPtr &cursor) NOTHROWS
{
    FeatureQueryParameters params;
    return queryFeatures(cursor, params);
}
TAKErr MVTFeatureDataStore::queryFeatures(FeatureCursorPtr &cursor, const FeatureQueryParameters &params) NOTHROWS
{
    std::unique_ptr<MultiplexingFeatureCursor> result(new MultiplexingFeatureCursor());
    if (params.spatialFilter && !isnan(params.maxResolution)) {
        do {
            int level;
            if (!isnan(params.maxResolution)) {
                level = atakmap::raster::osm::OSMUtils::mapnikTileLevel(params.maxResolution);
            } else {
                level = (int)ctx->impl->maxZoom;
            }
            if (level < (int)ctx->impl->minZoom)
                break;

            atakmap::feature::Envelope mbb = params.spatialFilter->getEnvelope();

            // determine tiles/zoom levels to be queried
            level = (level > (int)ctx->impl->maxZoom) ? (int)ctx->impl->maxZoom : level;
            const int stx = std::max(atakmap::raster::osm::OSMUtils::mapnikTileX(level, mbb.minX), ctx->impl->bounds.minX>>(ctx->impl->maxZoom-level));
            const int ftx = std::min(atakmap::raster::osm::OSMUtils::mapnikTileX(level, mbb.maxX), ctx->impl->bounds.maxX>>(ctx->impl->maxZoom-level));
            const int sty = std::max(atakmap::raster::osm::OSMUtils::mapnikTileY(level, mbb.maxY), ctx->impl->bounds.minY>>(ctx->impl->maxZoom-level));
            const int fty = std::min(atakmap::raster::osm::OSMUtils::mapnikTileY(level, mbb.minY), ctx->impl->bounds.maxY>>(ctx->impl->maxZoom-level));
            if(ftx >= stx && fty >= sty) {
                result->add(FeatureCursorPtr(new SpatialFilterFeatureCursorImpl(ctx->impl, level, stx, sty, ftx, fty, params.visibleOnly), Memory_deleter_const<FeatureCursor2, SpatialFilterFeatureCursorImpl>));
            }
        } while (false);
    } else if (params.featureIds) {
        result->add(FeatureCursorPtr(new FeatureIdCursor(*this, *params.featureIds), Memory_deleter_const<FeatureCursor2, FeatureIdCursor>));
    }
    cursor = FeatureCursorPtr(result.release(), Memory_deleter_const<FeatureCursor2, MultiplexingFeatureCursor>);
    return TE_Ok;
}
TAKErr MVTFeatureDataStore::queryFeaturesCount(int *value) NOTHROWS
{
    FeatureQueryParameters params;
    return queryFeaturesCount(value, params);
}
TAKErr MVTFeatureDataStore::queryFeaturesCount(int *value, const FeatureQueryParameters &params) NOTHROWS
{
    *value = 0;
    TAKErr code(TE_Ok);
    if (params.visibleOnly) {
        if (!params.featureSetIds->empty()) {
            TAK::Engine::Port::Collection<int64_t>::IteratorPtr it(nullptr, nullptr);
            code = params.featureSetIds->iterator(it);
            TE_CHECKRETURN_CODE(code);
            do {
                int64_t fsid;
                code = it->get(fsid);
                TE_CHECKBREAK_CODE(code);

                bool vis;
                if (isFeatureSetVisible(&vis, fsid) == TE_Ok && vis) {
                    (*value)++;
                    break;
                }

                code = it->next();
            } while (code == TE_Ok);
            if (code == TE_Done)
                code = TE_Ok;
            TE_CHECKRETURN_CODE(code);
        }
    } else {
        *value = 1;
    }
    return TE_Ok;
}
TAKErr MVTFeatureDataStore::getFeatureSet(FeatureSetPtr_const &value, const int64_t fsid) NOTHROWS
{
    for (auto it = ctx->impl->fsids.begin(); it != ctx->impl->fsids.end(); it++) {
        if (it->second.getId() != fsid)
            continue;
        value = FeatureSetPtr_const(new(std::nothrow) FeatureSet2(it->second), Memory_deleter_const<FeatureSet2>);
        if (!value)
            return TE_OutOfMemory;
        return TE_Ok;
    }
    return TE_InvalidArg;
}
TAKErr MVTFeatureDataStore::queryFeatureSets(FeatureSetCursorPtr &cursor) NOTHROWS
{
    return queryFeatureSets(cursor, FeatureSetQueryParameters());
}
TAKErr MVTFeatureDataStore::queryFeatureSets(FeatureSetCursorPtr &cursor, const FeatureSetQueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);

    std::vector<FeatureSet2> result;
    result.reserve(ctx->impl->fsids.size());
    for (const auto& fs : ctx->impl->fsids)
        result.push_back(fs.second);

    if (!params.ids->empty()) {
        auto it = result.begin();
        while(it != result.end()) {
            int64_t fsid = (*it).getId();
            bool c;
            code = params.ids->contains(&c, fsid);
            TE_CHECKBREAK_CODE(code);
            if (!c)
                it = result.erase(it);
            else
                it++;
        }
        TE_CHECKRETURN_CODE(code);
    }
    if (!params.names->empty()) {
        auto it = result.begin();
        while(it != result.end()) {
            TAK::Engine::Port::String fsname = (*it).getName();
            bool c;
            code = params.names->contains(&c, fsname);
            TE_CHECKBREAK_CODE(code);
            if (!c)
                it = result.erase(it);
            else
                it++;
        }
        TE_CHECKRETURN_CODE(code);
    }
    cursor = FeatureSetCursor2Ptr(new FeatureSetCursorImpl(result), Memory_deleter_const<FeatureSetCursor2, FeatureSetCursorImpl>);
    return TE_Ok;
}
TAKErr MVTFeatureDataStore::queryFeatureSetsCount(int *value) NOTHROWS
{
    FeatureSetQueryParameters params;
    return queryFeatureSetsCount(value, params);
}
TAKErr MVTFeatureDataStore::queryFeatureSetsCount(int *value, const FeatureSetQueryParameters &params) NOTHROWS
{
    // XXX - filtering
    if (params.visibleOnly) {
        *value = 0u;
        for (auto it : ctx->impl->fsids) {
            auto vis = ctx->impl->fsvisible.find(it.second.getId());
            if (vis == ctx->impl->fsvisible.end() || vis->second)
                (*value)++;
        }
    } else {
        *value = (int)ctx->impl->fsids.size();
    }
    return TE_Ok;
}
TAKErr MVTFeatureDataStore::isFeatureVisible(bool *value, const int64_t fid) NOTHROWS
{
    *value = true;
    return TE_Ok;
}
TAKErr MVTFeatureDataStore::isFeatureSetVisible(bool *value, const int64_t fsid) NOTHROWS
{
    *value = false;
    for (auto it : ctx->impl->fsvisible) {
        if (it.first == fsid) {
            *value = it.second;
            return TE_Ok;
        }
    }
    for (auto it : ctx->impl->fsids) {
        if (it.second.getId() == fsid) {
            // featureset is managed by this datastore, visiblility defaults to
            // `true` if no record is available
            *value = true;
            return TE_Ok;
        }
    }
    return TE_Ok;
}
TAKErr MVTFeatureDataStore::isFeatureSetReadOnly(bool *value, const int64_t fsid) NOTHROWS
{
    *value = true;
    return TE_Ok;
}
TAKErr MVTFeatureDataStore::isFeatureReadOnly(bool *value, const int64_t fid) NOTHROWS
{
    *value = true;
    return TE_Ok;
}
TAKErr MVTFeatureDataStore::isAvailable(bool *value) NOTHROWS
{
    *value = true;
    return TE_Ok;
}
TAKErr MVTFeatureDataStore::refresh() NOTHROWS
{
    return TE_Ok;
}
TAKErr MVTFeatureDataStore::getUri(TAK::Engine::Port::String &value) NOTHROWS
{
    value = uri;
    return TE_Ok;
}
TAKErr MVTFeatureDataStore::close() NOTHROWS
{
    return TE_Ok;
}
TAKErr MVTFeatureDataStore::beginBulkModificationImpl() NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::endBulkModificationImpl(const bool successful) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::insertFeatureSetImpl(FeatureSetPtr_const *featureSet, const char *provider_val, const char *type_val, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::updateFeatureSetImpl(const int64_t fsid, const char *name) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::updateFeatureSetImpl(const int64_t fsid, const double minResolution, const double maxResolution) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::updateFeatureSetImpl(const int64_t fsid, const char *name, const double minResolution, const double maxResolution) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::deleteFeatureSetImpl(const int64_t fsid) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::deleteAllFeatureSetsImpl() NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::insertFeatureImpl(FeaturePtr_const *feature, const int64_t fsid, const char *name, const atakmap::feature::Geometry &geom, const AltitudeMode altitudeMode, const double extrude, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::updateFeatureImpl(const int64_t fid, const char *name) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::updateFeatureImpl(const int64_t fid, const atakmap::feature::Geometry &geom) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::updateFeatureImpl(const int64_t fid, const AltitudeMode altitudeMode, const double extrude) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::updateFeatureImpl(const int64_t fid, const atakmap::feature::Style *style) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::updateFeatureImpl(const int64_t fid, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::updateFeatureImpl(const int64_t fid, const char *name, const atakmap::feature::Geometry &geom, const atakmap::feature::Style *style, const atakmap::util::AttributeSet &attributes) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::deleteFeatureImpl(const int64_t fid) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::deleteAllFeaturesImpl(const int64_t fsid) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::setFeatureVisibleImpl(const int64_t fid, const bool visible) NOTHROWS
{
    return TE_InvalidArg;
}
TAKErr MVTFeatureDataStore::setFeaturesVisibleImpl(const FeatureQueryParameters &params, const bool visible) NOTHROWS
{
    TAKErr code(TE_Ok);
    FeatureCursorPtr result(nullptr, nullptr);
    code = queryFeatures(result, params);
    TE_CHECKRETURN_CODE(code);
    do {
        code = result->moveToNext();
        if (code != TE_Ok)
            break;
        int64_t fid;
        code = result->getId(&fid);
        TE_CHECKBREAK_CODE(code);
        code = setFeatureVisible(fid, visible);
        TE_CHECKBREAK_CODE(code);
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);

    return code;
}
TAKErr MVTFeatureDataStore::setFeatureSetVisibleImpl(const int64_t fsid, const bool visible) NOTHROWS
{
    for (auto it : ctx->impl->fsids) {
        if (it.second.getId() == fsid) {
            ctx->impl->fsvisible[fsid] = visible;

            FileOutput2 visrecord;
            std::ostringstream strm;
            strm << workingDir << "/fsvis";
            if (visrecord.open(strm.str().c_str()) == TE_Ok) {
                for (auto vit : ctx->impl->fsvisible) {
                    visrecord.writeLong(vit.first);
                    visrecord.writeInt(vit.second ? 1 : 0);
                }
            }
            setContentChanged();
            return TE_Ok;
        }
    }
    return TE_InvalidArg;
}
TAKErr MVTFeatureDataStore::setFeatureSetsVisibleImpl(const FeatureSetQueryParameters &params, const bool visible) NOTHROWS
{
    TAKErr code(TE_Ok);
    FeatureSetCursorPtr result(nullptr, nullptr);
    code = queryFeatureSets(result, params);
    TE_CHECKRETURN_CODE(code);
    do {
        code = result->moveToNext();
        if (code != TE_Ok)
            break;
        const FeatureSet2 *fs;
        code = result->get(&fs);
        TE_CHECKBREAK_CODE(code);
        code = setFeatureSetVisible(fs->getId(), visible);
        TE_CHECKBREAK_CODE(code);
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);

    return code;
}
TAKErr MVTFeatureDataStore::setFeatureSetReadOnlyImpl(const int64_t fsid, const bool readOnly) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr MVTFeatureDataStore::setFeatureSetsReadOnlyImpl(const FeatureSetQueryParameters &params, const bool readOnly) NOTHROWS
{
    TAKErr code(TE_Ok);
    FeatureSetCursorPtr result(nullptr, nullptr);
    code = queryFeatureSets(result, params);
    TE_CHECKRETURN_CODE(code);
    do {
        if (code != TE_Ok)
            break;
        const FeatureSet2 *fs;
        code = result->get(&fs);
        TE_CHECKBREAK_CODE(code);
        code = setFeatureSetReadOnly(fs->getId(), readOnly);
        TE_CHECKBREAK_CODE(code);
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;
    TE_CHECKRETURN_CODE(code);

    return code;
}

namespace {
    QueryContextImpl::QueryContextImpl(const char* path, const std::shared_ptr<OGRFeatureDataStore::SchemaHandler> &schema_) NOTHROWS :
        database(nullptr, nullptr),
        schema(schema_),
        metadata(nullptr, 0u, nullptr),
        minZoom(32u),
        maxZoom(32u)
    {
        Databases_openDatabase(database, path, true);

        if (database) {
            QueryPtr result(nullptr, nullptr);

            database->query(result, "SELECT min(zoom_level) FROM tiles");
            if (result->moveToNext() == TE_Ok) {
                int v;
                result->getInt(&v, 0);
                minZoom = (std::size_t)v;
            }
            database->query(result, "SELECT max(zoom_level) FROM tiles");
            if (result->moveToNext() == TE_Ok) {
                int v;
                result->getInt(&v, 0);
                maxZoom = (std::size_t)v;
            }

            bounds.maxX = (1<<maxZoom);
            bounds.maxY = (1<<maxZoom);
            database->query(result, "SELECT min(tile_column) FROM tiles");
            if (result->moveToNext() == TE_Ok)
                result->getInt(&bounds.minX, 0);
            database->query(result, "SELECT max(tile_column) FROM tiles");
            if (result->moveToNext() == TE_Ok)
                result->getInt(&bounds.maxX, 0);
            database->query(result, "SELECT min(tile_row) FROM tiles");
            if (result->moveToNext() == TE_Ok) {
                int v;
                result->getInt(&v, 0);
                bounds.maxY = ((1u<<maxZoom)-v-1);
            }
            database->query(result, "SELECT max(tile_row) FROM tiles");
            if (result->moveToNext() == TE_Ok) {
                int v;
                result->getInt(&v, 0);
                bounds.minY = ((1u<<maxZoom)-v-1);
            }

            database->query(result, "SELECT value FROM metadata WHERE name = \'json\' LIMIT 1");
            if (result->moveToNext() == TE_Ok) {
                const char *v = nullptr;
                if (result->getString(&v, 0) == TE_Ok && v) {
                    json = v;
                    std::ostringstream strm;
                    strm << (uintptr_t)database.get() << "/json";
                    metadata = std::move(VSIMEMFILE(reinterpret_cast<const uint8_t *>(json.get()), strlen(json), strm.str().c_str()));
                }

                MBTilesInfo info;
                MBTilesInfo_get(&info, *database);

                // build out featuresets
                try {
                    TAK::Engine::Port::String filename;
                    IO_getName(filename, path);

                    nlohmann::json m = nlohmann::json::parse(json.get());
                    if (!m.empty()) {
                        const auto vector_layers = m.find("vector_layers");
                        if (vector_layers != m.end() && (*vector_layers).is_array()) {
                            for (auto it = (*vector_layers).begin(); it != (*vector_layers).end(); it++) {
                                const auto id = (*it).find("id");
                                if (id == (*it).end() || !(*id).is_string())
                                    continue;

                                std::ostringstream fsname;
#if 0
                                if (filename)
                                    fsname << filename << "/";
#endif
                                fsname << (*id).get<std::string>();
                                fsids.insert(std::pair<std::string, FeatureSet2>((*id).get<std::string>(), FeatureSet2((int64_t)fsids.size() + 1LL, "MVT", "MVT", fsname.str().c_str(), atakmap::raster::osm::OSMUtils::mapnikTileResolution((int)std::max(1u, (unsigned)info.minLevel)-1), 0.0, 1LL)));
                            }
                        }
                    }
                } catch (...) {}
            }
        }
    }

    VSIMEMFILE::VSIMEMFILE(const uint8_t* data, const std::size_t dataLen, const char *vsipath) NOTHROWS
    {
        if (data && dataLen) {
            std::ostringstream os;
            os << "/vsimem/";
            os << vsipath;

            const std::string gdalMemoryFile(os.str().c_str());
            VSILFILE* fpMem = VSIFileFromMemBuffer(gdalMemoryFile.c_str(), (GByte*)data, (vsi_l_offset)dataLen, FALSE);
            if (fpMem) {
                linkedPath = gdalMemoryFile.c_str();
                VSIFCloseL(fpMem);
            }
        }
    }
    VSIMEMFILE::~VSIMEMFILE() NOTHROWS
    {
        if(linkedPath)
            VSIUnlink(linkedPath);
    }
    VSIMEMFILE::operator const char* () NOTHROWS
    {
        return linkedPath;
    }
    VSIMEMFILE &VSIMEMFILE::operator=(VSIMEMFILE&& other) NOTHROWS
    {
        if (linkedPath) {
            VSIUnlink(linkedPath);
            linkedPath = nullptr;
        }
        linkedPath = other.linkedPath;
        other.linkedPath = nullptr;
        return *this;
    }

    SpatialFilterFeatureCursorImpl::SpatialFilterFeatureCursorImpl(const std::shared_ptr<QueryContextImpl>& ctx_, const int level_, const int stx_, const int sty_, const int ftx_, const int fty_, const bool visonly_) NOTHROWS :
        ctx(ctx_),
        impl(nullptr, nullptr),
        tile(nullptr, nullptr),
        pbf(nullptr, 0u, nullptr),
        visibleOnly(visonly_),
        level(level_)
    {
        TAKErr code(TE_Ok);
        do {
            code = ctx->database->compileQuery(tile, "SELECT tile_data, tile_column, tile_row FROM tiles WHERE zoom_level = ? AND tile_column >= ? AND tile_column <= ?  AND tile_row >= ? AND tile_row <= ?");
            TE_CHECKBREAK_CODE(code);
            code = tile->bindInt(1, level_);
            TE_CHECKBREAK_CODE(code);
            code = tile->bindInt(2, stx_);
            TE_CHECKBREAK_CODE(code);
            code = tile->bindInt(3, ftx_);
            TE_CHECKBREAK_CODE(code);
            code = tile->bindInt(4, (1<<level)-fty_-1);
            TE_CHECKBREAK_CODE(code);
            code = tile->bindInt(5, (1<<level)-sty_-1);
            TE_CHECKBREAK_CODE(code);
        } while(false);
        if(code != TE_Ok)
            tile.reset();
    }
    TAKErr SpatialFilterFeatureCursorImpl::getId(int64_t* value) NOTHROWS
    {
        if (!impl)
            return TE_IllegalState;
        *value = fid;
        return TE_Ok;
    }
    TAKErr SpatialFilterFeatureCursorImpl::getFeatureSetId(int64_t* value) NOTHROWS
    {
        if (!impl)
            return TE_IllegalState;
        *value = fsid;
        return TE_Ok;
    }
    TAKErr SpatialFilterFeatureCursorImpl::getVersion(int64_t* value) NOTHROWS
    {
        *value = 0LL;
        return TE_Ok;
    }
    TAKErr SpatialFilterFeatureCursorImpl::getRawGeometry(FeatureDefinition2::RawData* value) NOTHROWS
    {
        if (!impl)
            return TE_IllegalState;
        return impl->getRawGeometry(value);
    }
    FeatureDefinition2::GeometryEncoding SpatialFilterFeatureCursorImpl::getGeomCoding() NOTHROWS
    {
        if (!impl)
            return FeatureDefinition2::GeomGeometry;
        return impl->getGeomCoding();
    }
    AltitudeMode SpatialFilterFeatureCursorImpl::getAltitudeMode() NOTHROWS
    {
        if (!impl)
            return TEAM_Absolute;
        return impl->getAltitudeMode();
    }
    double SpatialFilterFeatureCursorImpl::getExtrude() NOTHROWS
    {
        if (!impl)
            return 0.0;
        return impl->getExtrude();
    }
    TAKErr SpatialFilterFeatureCursorImpl::getName(const char** value) NOTHROWS
    {
        if (!impl)
            return TE_IllegalState;
        return impl->getName(value);
    }
    FeatureDefinition2::StyleEncoding SpatialFilterFeatureCursorImpl::getStyleCoding() NOTHROWS
    {
        if (!impl)
            return FeatureDefinition2::StyleStyle;
        return impl->getStyleCoding();
    }
    TAKErr SpatialFilterFeatureCursorImpl::getRawStyle(FeatureDefinition2::RawData* value) NOTHROWS
    {
        if (!impl)
            return TE_IllegalState;
        return impl->getRawStyle(value);
    }
    TAKErr SpatialFilterFeatureCursorImpl::getAttributes(const atakmap::util::AttributeSet** value) NOTHROWS
    {
        if (!impl)
            return TE_IllegalState;
        return impl->getAttributes(value);
    }
    TAKErr SpatialFilterFeatureCursorImpl::get(const Feature2** feature) NOTHROWS
    {
        if (!impl)
            return TE_IllegalState;
        return impl->get(feature);
    }
    TAKErr SpatialFilterFeatureCursorImpl::moveToNext() NOTHROWS
    {
        TAKErr code(TE_Ok);
        if(!tile)
            return TE_Done;
        do {
            if (impl) {
                code = impl->moveToNext();
                if (code == TE_Ok) {
                    FeatureId id;
                    impl->getId(&id.mvt_id);
                    id.tile.x = tx;
                    id.tile.y = ty;
                    id.tile.z = level;
                    {
                        WriteLock wlock(ctx->mutex);
                        auto entry = ctx->fids.forward.find(id);
                        if (entry == ctx->fids.forward.end()) {
                            fid = ctx->nextFid++;
                            ctx->fids.forward[id] = fid;
                            ctx->fids.inverse[fid] = id;
                        }
                        else {
                            fid = entry->second;
                        }
                    }

                    impl->getFeatureSetId(&fsid);
                    const auto translated = fsidTranslate.find(fsid);
                    if (translated == fsidTranslate.end())
                        continue;
                    fsid = translated->second;
                    if (visibleOnly) {
                        const auto vis = ctx->fsvisible.find(fsid);
                        if (vis != ctx->fsvisible.end() && !vis->second)
                            continue;
                    }
                    return code;
                }
            }
            code = tile->moveToNext();
            if(code == TE_Done)
                return code;
            else if(code != TE_Ok)
                continue;

            tile->getInt(&tx, 1);
            tile->getInt(&ty, 2);

            ty = (1<<level)-ty-1;

            const uint8_t *blob;
            std::size_t blobLen;
            if (tile->getBlob(&blob, &blobLen, 0u) != TE_Ok)
                continue;
            if (!blob || !blobLen)
                continue;

            {
                std::ostringstream strm;
                strm << (uintptr_t) ctx->database.get() << "/" << level << "/" << ty << "/" << tx;
                pbf = std::move(VSIMEMFILE(blob, blobLen, strm.str().c_str()));
            }

            // open PBF blob with OGR feature data source
            char **opts = nullptr;
            {
                std::ostringstream strm;
                strm << tx;
                opts = CSLSetNameValue(opts, "X", strm.str().c_str());
            }
            {
                std::ostringstream strm;
                //strm << ((1<<level)-idx.y-1);
                strm << ty;
                opts = CSLSetNameValue(opts, "Y", strm.str().c_str());
            }
            {
                std::ostringstream strm;
                strm << level;
                opts = CSLSetNameValue(opts, "Z", strm.str().c_str());
            }
            if (ctx->metadata) {
                opts = CSLSetNameValue(opts, "METADATA_FILE", ctx->metadata);
            }

            this->ds.reset(new OGRFeatureDataStore(pbf, "MVT", (const char **)opts, ctx->schema));
            CSLDestroy(opts);

            // build out FSID translation table
            {
                fsidTranslate.clear();
                if (createFeatureSetIDTranslationTable(&fsidTranslate, *ctx, *ds) != TE_Ok)
                    continue;
            }
            if (ds->queryFeatures(impl) != TE_Ok)
                continue;
        } while (true);

        return code;
    }

    FeatureIdCursor::FeatureIdCursor(FeatureDataStore2 &datastore, TAK::Engine::Port::Collection<int64_t> &fids_) NOTHROWS :
        ds(datastore),
        it(0),
        row(nullptr, nullptr)
    {
        TAK::Engine::Port::Collection<int64_t>::IteratorPtr iter(nullptr, nullptr);
        if (fids_.iterator(iter) != TE_Ok)
            return;
        fids.reserve(fids_.size());
        do {
            int64_t fid;
            if (iter->get(fid) != TE_Ok)
                break;
            fids.push_back(fid);
            if (iter->next() != TE_Ok)
                break;
        } while (true);
    }

    TAKErr FeatureIdCursor::getId(int64_t *value) NOTHROWS
    {
        if (!row.get())
            return TE_IllegalState;
        *value = row->getId();
        return TE_Ok;
    }
    TAKErr FeatureIdCursor::getFeatureSetId(int64_t *value) NOTHROWS
    {
        if (!row.get())
            return TE_IllegalState;
        *value = row->getFeatureSetId();
        return TE_Ok;
    }
    TAKErr FeatureIdCursor::getVersion(int64_t *value) NOTHROWS
    {
        if (!row.get())
            return TE_IllegalState;
        *value = row->getVersion();
        return TE_Ok;
    }
    TAKErr FeatureIdCursor::getRawGeometry(FeatureDefinition2::RawData *value) NOTHROWS
    {
        if (!row.get())
            return TE_IllegalState;
        value->object = row->getGeometry();
        return TE_Ok;
    }
    FeatureDefinition2::GeometryEncoding FeatureIdCursor::getGeomCoding() NOTHROWS
    {
        return FeatureDefinition2::GeomGeometry;
    }
    AltitudeMode FeatureIdCursor::getAltitudeMode() NOTHROWS
    {
        if (!row.get())
            return TEAM_Absolute;
        return row->getAltitudeMode();
    }
    double FeatureIdCursor::getExtrude() NOTHROWS
    {
        if (!row.get())
            return 0.0;
        return row->getExtrude();
    }
    TAKErr FeatureIdCursor::getName(const char **value) NOTHROWS
    {
        if (!row.get())
            return TE_IllegalState;
        *value = row->getName();
        return TE_Ok;
    }
    FeatureDefinition2::StyleEncoding FeatureIdCursor::getStyleCoding() NOTHROWS
    {
        return FeatureDefinition2::StyleStyle;
    }
    TAKErr FeatureIdCursor::getRawStyle(FeatureDefinition2::RawData *value) NOTHROWS
    {
        if (!row.get())
            return TE_IllegalState;
        value->object = row->getStyle();
        return TE_Ok;
    }
    TAKErr FeatureIdCursor::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS
    {
        if (!row.get())
            return TE_IllegalState;
        *value = row->getAttributes();
        return TE_Ok;
    }
    TAKErr FeatureIdCursor::get(const Feature2** feature) NOTHROWS
    {
        *feature = row.get();
        return TE_Ok;
    }
    TAKErr FeatureIdCursor::moveToNext() NOTHROWS
    {
        do {
            if (it == fids.size())
                return TE_Done;
            it++;
            if (ds.getFeature(row, fids[it - 1u]) != TE_Ok)
                continue;
            else
                break;
        } while (true);
        return TE_Ok;
    }

    FeatureSetCursorImpl::FeatureSetCursorImpl(const std::vector<FeatureSet2> &values_) NOTHROWS :
        values(values_),
        idx(-1),
        done(false)
    {}
    TAKErr FeatureSetCursorImpl::get(const FeatureSet2** value) NOTHROWS
    {
        if (idx < 0 || idx >= values.size())
            return TE_IllegalState;
        *value = &values.at(idx);
        return TE_Ok;
    }
    TAKErr FeatureSetCursorImpl::moveToNext() NOTHROWS
    {
        if (idx == values.size())
            return TE_Done;
        idx++;
        return (idx < values.size()) ? TE_Ok : TE_Done;
    }

    TAKErr createFeatureSetIDTranslationTable(std::map<int64_t, int64_t> *value, const QueryContextImpl &ctx, FeatureDataStore2 &ds) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (!value)
            return TE_InvalidArg;
        FeatureSetCursor2Ptr fsresult(nullptr, nullptr);
        code = ds.queryFeatureSets(fsresult);
        TE_CHECKRETURN_CODE(code);
        do {
            if (fsresult->moveToNext() != TE_Ok)
                break;
            const FeatureSet2 *fs;
            fsresult->get(&fs);
            std::string fsname;
            if (fs->getName())
                fsname = fs->getName();
            const auto& ctxFsid = ctx.fsids.find(fsname);
            if (ctxFsid == ctx.fsids.end())
                return TE_IllegalState;
            (*value)[fs->getId()] = ctxFsid->second.getId();
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;
        return code;
    }
}
