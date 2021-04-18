#include "model/SceneLayer.h"

#include "core/Projection2.h"
#include "core/ProjectionFactory3.h"
#include "feature/BruteForceLimitOffsetFeatureCursor.h"
#include "feature/BruteForceLimitOffsetFeatureSetCursor.h"
#include "feature/LineString.h"
#include "feature/MultiplexingFeatureCursor.h"
#include "feature/MultiplexingFeatureSetCursor.h"
#include "feature/Polygon.h"
#include "port/STLSetAdapter.h"
#include "port/STLVectorAdapter.h"
#include "port/StringBuilder.h"
#include "thread/Lock.h"

using namespace TAK::Engine::Model;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

#define ATTR_KEY "TAK.Engine.Model.SceneInfo"

#define SID_MASK ~0x8000000000000000LL
#define FID(s) ((s>>1)&SID_MASK)
#define TRANSIENT_BIT 1u
#define PERSISTENT_BIT 0u
#define FID2SID(f, b) ((f<<1)|b)
#define TRANSIENT_SID(f) FID2SID(f, TRANSIENT_BIT)
#define PERSISTENT_SID(f) FID2SID(f, PERSISTENT_BIT)
#define IS_TRANSIENT_SID(s) (s&0x1)

#define __HACK_POI_PADDING 10.0

#ifdef _MSC_VER
#define TE_FILESEP '\\'
#else
#define TE_FILESEP '/'
#endif

namespace
{
    TAKErr encodeInfo(atakmap::util::AttributeSet &value, const SceneInfo &info) NOTHROWS;
    TAKErr decodeInfo(SceneInfo *value, const atakmap::util::AttributeSet &attrs) NOTHROWS;
    TAKErr lcs2wgs84(TAK::Engine::Math::Point2<double> *value, const SceneInfo &info, const Projection2 &proj, const TAK::Engine::Math::Point2<double> &lcs) NOTHROWS
    {
        TAKErr code(TE_Ok);
        TAK::Engine::Math::Point2<double> p(lcs);
        if (info.localFrame.get()) {
            code = info.localFrame->transform(&p, p);
            TE_CHECKRETURN_CODE(code);
        }
        GeoPoint2 g;
        code = proj.inverse(&g, p);
        TE_CHECKRETURN_CODE(code);
        value->x = g.longitude;
        value->y = g.latitude;
        value->z = g.altitude;
        return code;
    }
    TAKErr lcs2wgs84(atakmap::feature::GeometryPtr &value, const SceneInfo &info) NOTHROWS
    {
        TAKErr code(TE_Ok);
        Projection2Ptr proj(nullptr, nullptr);
        code = ProjectionFactory3_create(proj, info.srid);
        TE_CHECKRETURN_CODE(code);

        atakmap::feature::LineString ls;

        TAK::Engine::Math::Point2<double> lla;

        code = lcs2wgs84(&lla, info, *proj, TAK::Engine::Math::Point2<double>(info.aabb->minX, info.aabb->maxY, info.aabb->minZ));
        TE_CHECKRETURN_CODE(code);
        ls.addPoint(lla.x, lla.y);
        code = lcs2wgs84(&lla, info, *proj, TAK::Engine::Math::Point2<double>(info.aabb->maxX, info.aabb->maxY, info.aabb->minZ));
        TE_CHECKRETURN_CODE(code);
        ls.addPoint(lla.x, lla.y);
        code = lcs2wgs84(&lla, info, *proj, TAK::Engine::Math::Point2<double>(info.aabb->maxX, info.aabb->minY, info.aabb->minZ));
        TE_CHECKRETURN_CODE(code);
        ls.addPoint(lla.x, lla.y);
        code = lcs2wgs84(&lla, info, *proj, TAK::Engine::Math::Point2<double>(info.aabb->minX, info.aabb->minY, info.aabb->minZ));
        TE_CHECKRETURN_CODE(code);
        ls.addPoint(lla.x, lla.y);
        code = lcs2wgs84(&lla, info, *proj, TAK::Engine::Math::Point2<double>(info.aabb->minX, info.aabb->maxY, info.aabb->minZ));
        TE_CHECKRETURN_CODE(code);
        ls.addPoint(lla.x, lla.y);

        value = atakmap::feature::GeometryPtr(new(std::nothrow) atakmap::feature::Polygon(ls), Memory_deleter_const<atakmap::feature::Geometry, atakmap::feature::Polygon>);
        if (!value.get())
            return TE_OutOfMemory;

        return code;
    }

    class SceneFeatureCursor : public FeatureCursor2
    {
    public :
        SceneFeatureCursor(FeatureCursorPtr &&impl, const unsigned bit) NOTHROWS;
    public: // FeatureCursor2
        TAKErr getId(int64_t *value) NOTHROWS override;
        TAKErr getFeatureSetId(int64_t *value) NOTHROWS override;
        TAKErr getVersion(int64_t *value) NOTHROWS override;
    public: // FeatureDefinition2
        TAKErr getRawGeometry(RawData *value) NOTHROWS override;
        GeometryEncoding getGeomCoding() NOTHROWS override;
        AltitudeMode getAltitudeMode() NOTHROWS override;
        double getExtrude() NOTHROWS override;
        TAKErr getName(const char **value) NOTHROWS override;
        StyleEncoding getStyleCoding() NOTHROWS override;
        TAKErr getRawStyle(RawData *value) NOTHROWS override;
        TAKErr getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS override;
        TAKErr get(const Feature2 **feature) NOTHROWS override;
    public : // RowIterator
        TAKErr moveToNext() NOTHROWS override;
    private :
        FeatureCursorPtr impl;
        unsigned bit;
        FeaturePtr_const row;
    };

    class SceneFeatureSetCursor : public FeatureSetCursor2
    {
    public :
        SceneFeatureSetCursor(FeatureSetCursor2Ptr &&impl, const unsigned bit) NOTHROWS;
    public :
        TAKErr get(const FeatureSet2 **value) NOTHROWS override;
    public :
        TAKErr moveToNext() NOTHROWS override;
    private :
        FeatureSetCursor2Ptr impl;
        unsigned bit;
        FeatureSetPtr_const row;
    };

    class SceneWrapper : public Scene
    {
    public:
        SceneWrapper(const std::shared_ptr<Scene> &impl) NOTHROWS;
    public :
        SceneNode &getRootNode() const NOTHROWS override;
        const Envelope2 &getAABB() const NOTHROWS override;
        unsigned int getProperties() const NOTHROWS override;
    private :
        std::shared_ptr<Scene> impl;
    };

    class FeatureSetInsertGuard
    {
    public :
        FeatureSetInsertGuard(const int64_t fsid, FeatureDataStore2 &store) NOTHROWS;
        ~FeatureSetInsertGuard() NOTHROWS;
    public :
        void setValid() NOTHROWS;
    private :
        int64_t fsid;
        FeatureDataStore2 &store;
        bool valid;
    };
}

SceneLayer::SceneLayer(const char *name, const char *cpath) NOTHROWS :
    AbstractLayer2(name),
    persistent(-1, -1),
    transient(-1, -1)
{
    TAK::Engine::Port::String indexFile;
    if (cpath) {
        // create the directory if it does not exist
        IO_mkdirs(cpath);

        // derive the index file
        StringBuilder indexSB;
        indexSB << cpath << TE_FILESEP << "index.sqlite";
        indexFile = indexSB.c_str();

        // derive the cache dir
        StringBuilder cacheSB;
        cacheSB << cpath << TE_FILESEP << "cache";
        cacheDir = cacheSB.c_str();
    }

    if (persistent.open(indexFile) != TE_Ok && indexFile) {
        // failed to open the physical file. nuke the index and the cache and
        // open an in-memory DB this time around
        IO_delete(indexFile);
        indexFile = nullptr;
        IO_delete(cacheDir);
        cacheDir = nullptr;
        persistent.open(nullptr);

        // XXX - log the error
    }

    transient.open(nullptr);

    // validate contents on start
    do {
        FeatureSetCursor2Ptr result(nullptr, nullptr);
        if (persistent.queryFeatureSets(result) != TE_Ok)
            break;
        while (result->moveToNext() == TE_Ok) {
            const FeatureSet2 *fs;
            if (result->get(&fs) != TE_Ok)
                continue;
            // evict entries do not exist
            bool exists;
            if (IO_exists(&exists, fs->getName()) != TE_Ok)
                continue;
            if(!exists)
                removeSceneSet(FID2SID(fs->getId(), PERSISTENT_BIT));
        }
    } while (false);
}
SceneLayer::~SceneLayer() NOTHROWS
{}

TAKErr SceneLayer::add(int64_t *sid, const std::shared_ptr<Scene> &scene, const SceneInfo &info) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!scene.get())
        return TE_InvalidArg;
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    

    FeatureSetPtr_const fs(nullptr, nullptr);
    FeatureDataStore2 &base = transient;
    code = base.insertFeatureSet(&fs, "SceneLayer", info.type, info.uri, info.minDisplayResolution, info.maxDisplayResolution);
    TE_CHECKRETURN_CODE(code);

    FeatureSetInsertGuard fsguard(fs->getId(), base);

    // encode the info
    atakmap::util::AttributeSet attrs;
    code = encodeInfo(attrs, info);
    TE_CHECKRETURN_CODE(code);

    // insert the feature into the transient store
    FeaturePtr_const inserted(nullptr, nullptr);

    // XXX - want to be able to specify NULL location
    GeometryPtr location(new atakmap::feature::Point(0.0, 0.0), Memory_deleter_const<atakmap::feature::Geometry>);
    if (info.location.get()) {
        // XXX - pad out for now until we've got bounds refresh hooked up
        atakmap::feature::LineString ls;
        const double metersDegLat = GeoPoint2_approximateMetersPerDegreeLatitude(info.location->latitude);
        const double metersDegLng = GeoPoint2_approximateMetersPerDegreeLongitude(info.location->longitude);
        const double padX = __HACK_POI_PADDING / metersDegLng;
        const double padY = __HACK_POI_PADDING / metersDegLat;
        ls.addPoint(info.location->longitude - padX, info.location->latitude + padY);
        ls.addPoint(info.location->longitude + padX, info.location->latitude + padY);
        ls.addPoint(info.location->longitude + padX, info.location->latitude - padY);
        ls.addPoint(info.location->longitude - padX, info.location->latitude - padY);
        ls.addPoint(info.location->longitude - padX, info.location->latitude + padY);
        
        location = GeometryPtr(new atakmap::feature::Polygon(ls), Memory_deleter_const<atakmap::feature::Geometry>);
    } else {
        return TE_InvalidArg;
    }
    code = transient.insertFeature(&inserted, fs->getId(), info.name, *location, info.altitudeMode, 0.0, nullptr, attrs);
    TE_CHECKRETURN_CODE(code);

    scenes[TRANSIENT_SID(inserted->getId())] = scene;

    if (sid)
        *sid = TRANSIENT_SID(inserted->getId());

    fsguard.setValid();

    // notify content changed
    dispatchContentChangedNoSync();

    return code;
}
TAKErr SceneLayer::add(int64_t *sid, ScenePtr &&scene, const SceneInfo &info) NOTHROWS
{
    std::shared_ptr<Scene> sscene(std::move(scene));
    return add(sid, sscene, info);
}
TAKErr SceneLayer::add(int64_t *ssid, const char *uri, const char *hint) NOTHROWS
{
    TAKErr code(TE_Ok);

    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    // check if file already added
    FeatureDataStore2::FeatureSetQueryParameters params;
    code = params.names->add(uri);
    TE_CHECKRETURN_CODE(code);
    params.limit = 1;

    int count;
    code = persistent.queryFeatureSetsCount(&count, params);
    TE_CHECKRETURN_CODE(code);
    if (count)
        return TE_InvalidArg;

    // parse scene(s)
    struct SceneInfoComp
    {
        bool operator()(const SceneInfoPtr &a, const SceneInfoPtr &b) const
        {
            auto aptr = (intptr_t)(void *)a.get();
            auto bptr = (intptr_t)(void *)b.get();
            return aptr < bptr;
        }
    };
    std::set<SceneInfoPtr, SceneInfoComp> infos;
    TAK::Engine::Port::STLSetAdapter<SceneInfoPtr, SceneInfoComp> result(infos);

    code = SceneInfoFactory_create(result, uri, hint);
    TE_CHECKRETURN_CODE(code);

    if (infos.empty())
        return TE_Ok;

    // collect type and display thresholds from results
    auto it = infos.begin();
    const char *type = (*it)->type;
    double minRes = (*it)->minDisplayResolution;
    double maxRes = (*it)->maxDisplayResolution;
    it++;

    for ( ; it != infos.end(); it++) {
        if (::isnan(minRes) || (*it)->minDisplayResolution > minRes)
            minRes = (*it)->minDisplayResolution;
        if (::isnan(maxRes) || (*it)->maxDisplayResolution < maxRes)
            maxRes = (*it)->maxDisplayResolution;
    }

    // insert featureset
    FeatureSetPtr_const fs(nullptr, nullptr);
    FeatureDataStore2 &base = persistent;
    code = base.insertFeatureSet(&fs, "SceneLayer", type, uri, minRes, maxRes);
    TE_CHECKRETURN_CODE(code);

    FeatureSetInsertGuard fsguard(fs->getId(), base);

    if (ssid)
        *ssid = fs->getId();

    // add scene infos to featureset
    for (it = infos.begin(); it != infos.end(); it++) {
        int64_t fid;
        code = addNoSync(&fid, persistent, fs->getId(), *(*it));
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    fsguard.setValid();

    dispatchContentChangedNoSync();

    return code;
}
TAKErr SceneLayer::add(int64_t *sid, const SceneInfo &info) NOTHROWS
{
    TAKErr code(TE_Ok);

    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!info.uri)
        return TE_InvalidArg;

    FeatureDataStore2::FeatureSetQueryParameters params;
    code = params.names->add(info.uri);
    TE_CHECKRETURN_CODE(code);
    params.limit = 1;

    int count;
    code = persistent.queryFeatureSetsCount(&count, params);
    TE_CHECKRETURN_CODE(code);
    if (count)
        return TE_InvalidArg;

    FeatureSetPtr_const fs(nullptr, nullptr);
    FeatureDataStore2 &base = persistent;
    code = base.insertFeatureSet(&fs, "SceneLayer", info.type, info.uri, info.minDisplayResolution, info.maxDisplayResolution);
    TE_CHECKRETURN_CODE(code);

    FeatureSetInsertGuard fsguard(fs->getId(), base);

    int64_t fid;
    code = addNoSync(&fid, persistent, fs->getId(), info);
    TE_CHECKRETURN_CODE(code);

    if (sid)
        *sid = PERSISTENT_SID(fid);

    fsguard.setValid();

    // notify content changed
    dispatchContentChangedNoSync();

    return code;
}
TAKErr SceneLayer::remove(const Scene &scene) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    for (auto it = scenes.begin(); it != scenes.end(); it++) {
        if ((it->second.get() == &scene)) {
            // clean up
            code = removeScene(it->first);
            break;
        }
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}
TAKErr SceneLayer::remove(const char *uri) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    // check if file already added
    FeatureDataStore2::FeatureSetQueryParameters params;
    code = params.names->add(uri);
    TE_CHECKRETURN_CODE(code);
    params.limit = 1;

    FeatureSetCursorPtr result(nullptr, nullptr);
    code = persistent.queryFeatureSets(result, params);
    TE_CHECKRETURN_CODE(code);

    code = result->moveToNext();
    if (code == TE_Done)
        return TE_InvalidArg;
    TE_CHECKRETURN_CODE(code);

    const FeatureSet2 *fs;
    code = result->get(&fs);
    TE_CHECKRETURN_CODE(code);

    cleanCache(FID2SID(fs->getId(), PERSISTENT_BIT));

    code =  persistent.deleteFeatureSet(fs->getId());
    TE_CHECKRETURN_CODE(code);

    // notify content changed
    dispatchContentChangedNoSync();
    return code;
}
TAKErr SceneLayer::remove(const SceneInfo &info) NOTHROWS
{
    return remove(info.uri);
}
TAKErr SceneLayer::removeScene(const int64_t sid) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    auto entry = scenes.find(sid);
    const bool isTransient = (entry != scenes.end());
    FeatureDataStore2 &store = isTransient ? transient : persistent;
    
    FeaturePtr_const f(nullptr, nullptr);
    code = store.getFeature(f, FID(sid));
    TE_CHECKRETURN_CODE(code);

    cleanCache(FID2SID(f->getFeatureSetId(), (isTransient ? TRANSIENT_BIT : PERSISTENT_BIT)));

    // delete the parent FS
    auto fsid = f->getFeatureSetId();
    code = store.setFeatureSetReadOnly(fsid, false);
    TE_CHECKRETURN_CODE(code);
    code = store.deleteFeatureSet(fsid);
    TE_CHECKRETURN_CODE(code);

    // clear the entry
    if (isTransient)
        scenes.erase(entry);

    // notify content changed
    dispatchContentChangedNoSync();

    return code;
}
TAKErr SceneLayer::removeSceneSet(const int64_t ssid) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    FeatureDataStore2 &store = IS_TRANSIENT_SID(ssid) ? transient : persistent;
    
    cleanCache(ssid);

    // delete the parent FS
    code = store.setFeatureSetReadOnly(FID(ssid), false);
    TE_CHECKRETURN_CODE(code);
    code = store.deleteFeatureSet(FID(ssid));
    TE_CHECKRETURN_CODE(code);

    // XXX - 
    // clear the entry
//    if (IS_TRANSIENT_SID(ssid))
//        scenes.erase(entry);

    // notify content changed
    dispatchContentChangedNoSync();

    return code;
}
TAKErr SceneLayer::contains(bool *value, const char *uri) NOTHROWS
{
    TAKErr code(TE_Ok);

    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (!uri)
        return TE_InvalidArg;

    FeatureDataStore2::FeatureSetQueryParameters params;
    code = params.names->add(uri);
    TE_CHECKRETURN_CODE(code);
    params.limit = 1;

    int count;
    code = persistent.queryFeatureSetsCount(&count, params);
    TE_CHECKRETURN_CODE(code);

    *value = !!count;
    return code;
}
TAKErr SceneLayer::refresh(const char *uri) NOTHROWS
{
    TAKErr code(TE_Ok);

    // XXX - better implementation
    code = remove(uri);
    TE_CHECKRETURN_CODE(code);

    code = add(nullptr, uri, nullptr);
    TE_CHECKRETURN_CODE(code);

    return code;
}
TAKErr SceneLayer::shutdown() NOTHROWS
{
    TAKErr code(TE_Ok);

    code = persistent.close();
    TE_CHECKRETURN_CODE(code);

    code = transient.close();
    TE_CHECKRETURN_CODE(code);

    return code;
}
TAKErr SceneLayer::update(const int64_t sid, const SceneInfo &info) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    // encode the info
    atakmap::util::AttributeSet attrs;
    code = encodeInfo(attrs, info);
    TE_CHECKRETURN_CODE(code);

    FeatureDataStore2 &store = IS_TRANSIENT_SID(sid) ? transient : persistent;

    GeometryPtr geom(nullptr, nullptr);
#if 1
    if (info.aabb.get()) {
        // XXX - non planar projections
        // aabb is LCS, need to transform to WGS84
        code = lcs2wgs84(geom, info);
        TE_CHECKRETURN_CODE(code);
    } else
#endif
        if (info.location.get()) {
        // XXX - pad out for now until we've got bounds refresh hooked up
        atakmap::feature::LineString ls;
        const double metersDegLat = GeoPoint2_approximateMetersPerDegreeLatitude(info.location->latitude);
        const double metersDegLng = GeoPoint2_approximateMetersPerDegreeLongitude(info.location->longitude);
        const double padX = __HACK_POI_PADDING / metersDegLng;
        const double padY = __HACK_POI_PADDING / metersDegLat;
        ls.addPoint(info.location->longitude - padX, info.location->latitude + padY);
        ls.addPoint(info.location->longitude + padX, info.location->latitude + padY);
        ls.addPoint(info.location->longitude + padX, info.location->latitude - padY);
        ls.addPoint(info.location->longitude - padX, info.location->latitude - padY);
        ls.addPoint(info.location->longitude - padX, info.location->latitude + padY);
        
        geom = GeometryPtr(new atakmap::feature::Polygon(ls), Memory_deleter_const<atakmap::feature::Geometry>);
    } else {
        return TE_InvalidArg;
    }

    code = store.updateFeature(FID(sid), info.name, *geom, nullptr, attrs);
    TE_CHECKRETURN_CODE(code);

    dispatchContentChangedNoSync();

    return code;
}
TAKErr SceneLayer::setVisible(const int64_t sid, const bool visible) NOTHROWS
{
    TAKErr code(TE_Ok);
    FeatureDataStore2 &store = IS_TRANSIENT_SID(sid) ? transient : persistent;
    code = store.setFeatureVisible(FID(sid), visible);
    TE_CHECKRETURN_CODE(code);

    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    this->dispatchContentChangedNoSync();
    return code;
}
bool SceneLayer::isVisible(const int64_t sid) NOTHROWS
{
    bool retval;
    FeatureDataStore2 &store = IS_TRANSIENT_SID(sid) ? transient : persistent;
    TAKErr code = store.isFeatureVisible(&retval, FID(sid));
    if (code == TE_Ok)
        return retval;
    else if (code == TE_InvalidArg)
        return false;
    else
        return false;
}
TAKErr SceneLayer::query(FeatureCursorPtr &result) NOTHROWS
{
    TAKErr code(TE_Ok);

    FeatureCursorPtr transientResult(nullptr, nullptr);
    code = transient.queryFeatures(transientResult);
    TE_CHECKRETURN_CODE(code);

    FeatureCursorPtr persistentResult(nullptr, nullptr);
    code = persistent.queryFeatures(persistentResult);
    TE_CHECKRETURN_CODE(code);

    std::unique_ptr<MultiplexingFeatureCursor> retval(new MultiplexingFeatureCursor());
    code = retval->add(FeatureCursorPtr(new SceneFeatureCursor(std::move(transientResult), TRANSIENT_BIT), Memory_deleter_const<FeatureCursor2, SceneFeatureCursor>));
    TE_CHECKRETURN_CODE(code);

    code = retval->add(FeatureCursorPtr(new SceneFeatureCursor(std::move(persistentResult), PERSISTENT_BIT), Memory_deleter_const<FeatureCursor2, SceneFeatureCursor>));
    TE_CHECKRETURN_CODE(code);

    result = FeatureCursorPtr(retval.release(), Memory_deleter_const<FeatureCursor2, MultiplexingFeatureCursor>);

    return code;
}
TAKErr SceneLayer::query(FeatureCursorPtr &result, const FeatureDataStore2::FeatureQueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);

    std::list<std::unique_ptr<FeatureDataStore2::FeatureQueryParameters>> paramsCleaner;
    const FeatureDataStore2::FeatureQueryParameters *persistentParams = nullptr;
    const FeatureDataStore2::FeatureQueryParameters *transientParams = nullptr;
    if (!params.featureIds->empty()) {
        TAK::Engine::Port::Collection<int64_t>::IteratorPtr iter(nullptr, nullptr);
        code = params.featureIds->iterator(iter);
        TE_CHECKRETURN_CODE(code);

        do {
            int64_t sid;
            code = iter->get(sid);
            TE_CHECKBREAK_CODE(code);

            // get the correct params instance
            const FeatureDataStore2::FeatureQueryParameters *paramsImpl = IS_TRANSIENT_SID(sid) ? transientParams : persistentParams;
            // allocate if necessary
            if (!paramsImpl) {
                std::unique_ptr<FeatureDataStore2::FeatureQueryParameters> pp(new FeatureDataStore2::FeatureQueryParameters(params));
                paramsImpl = pp.get();
                paramsCleaner.push_back(std::move(pp));
                if (IS_TRANSIENT_SID(sid))
                    transientParams = paramsImpl;
                else
                    persistentParams = paramsImpl;
            }

            // derive the FID from the SID and add to the params
            code = paramsImpl->featureIds->add(FID(sid));
            TE_CHECKBREAK_CODE(code);

            code = iter->next();
            TE_CHECKBREAK_CODE(code);
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);

        // if we're only querying against one store, utilize original limit/offset
        if (paramsCleaner.size() == 1u) {
            paramsCleaner.front()->limit = params.limit;
            paramsCleaner.front()->offset = params.offset;
        }
    }

    if (!params.featureSetIds->empty()) {
        TAK::Engine::Port::Collection<int64_t>::IteratorPtr iter(nullptr, nullptr);
        code = params.featureSetIds->iterator(iter);
        TE_CHECKRETURN_CODE(code);

        do {
            int64_t sid;
            code = iter->get(sid);
            TE_CHECKBREAK_CODE(code);

            // get the correct params instance
            const FeatureDataStore2::FeatureQueryParameters *paramsImpl = IS_TRANSIENT_SID(sid) ? transientParams : persistentParams;
            // allocate if necessary
            if (!paramsImpl) {
                std::unique_ptr<FeatureDataStore2::FeatureQueryParameters> pp(new FeatureDataStore2::FeatureQueryParameters(params));
                paramsImpl = pp.get();
                paramsCleaner.push_back(std::move(pp));
                if (IS_TRANSIENT_SID(sid))
                    transientParams = paramsImpl;
                else
                    persistentParams = paramsImpl;
            }

            // derive the FSID from the SID and add to the params
            code = paramsImpl->featureSetIds->add(FID(sid));
            TE_CHECKBREAK_CODE(code);

            code = iter->next();
            TE_CHECKBREAK_CODE(code);
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);

        // if we're only querying against one store, utilize original limit/offset
        if (paramsCleaner.size() == 1u) {
            paramsCleaner.front()->limit = params.limit;
            paramsCleaner.front()->offset = params.offset;
        }
    }

    // if both param sets are NULL, there's no ID filtering occurring, use
    // source params
    if (persistentParams == nullptr && transientParams == nullptr) {
        persistentParams = &params;
        transientParams = &params;
    }

    FeatureCursorPtr transientResult(nullptr, nullptr);
    FeatureCursorPtr persistentResult(nullptr, nullptr);

    if (transientParams) {
        code = transient.queryFeatures(transientResult, *transientParams);
        TE_CHECKRETURN_CODE(code);
        transientResult = FeatureCursorPtr(new SceneFeatureCursor(std::move(transientResult), TRANSIENT_BIT), Memory_deleter_const<FeatureCursor2, SceneFeatureCursor>);
        if (!persistentParams) {
            result = std::move(transientResult);
            return code;
        }
    }
    if (persistentParams) {
        code = persistent.queryFeatures(persistentResult, *persistentParams);
        TE_CHECKRETURN_CODE(code);
        persistentResult = FeatureCursorPtr(new SceneFeatureCursor(std::move(persistentResult), PERSISTENT_BIT), Memory_deleter_const<FeatureCursor2, SceneFeatureCursor>);
        if (!transientParams) {
            result = std::move(persistentResult);
            return code;
        }
    }

    std::unique_ptr<MultiplexingFeatureCursor> retval(new MultiplexingFeatureCursor());
    if (persistentResult) {
        code = retval->add(std::move(persistentResult));
        TE_CHECKRETURN_CODE(code);
    }
    if (transientResult) {
        code = retval->add(std::move(transientResult));
        TE_CHECKRETURN_CODE(code);
    }

    result = FeatureCursorPtr(retval.release(), Memory_deleter_const<FeatureCursor2, MultiplexingFeatureCursor>);

    // if both stores are being queried and there is a limit, put a brute force wrapper
    if (paramsCleaner.size() == 2u && params.limit)
        result = FeatureCursorPtr(new BruteForceLimitOffsetFeatureCursor(std::move(result), params.limit, params.offset), Memory_deleter_const<FeatureCursor2, BruteForceLimitOffsetFeatureCursor>);

    return code;
}
TAKErr SceneLayer::query(FeatureSetCursorPtr &result) NOTHROWS
{
    TAKErr code(TE_Ok);

    FeatureSetCursorPtr transientResult(nullptr, nullptr);
    code = transient.queryFeatureSets(transientResult);
    TE_CHECKRETURN_CODE(code);

    FeatureSetCursorPtr persistentResult(nullptr, nullptr);
    code = persistent.queryFeatureSets(persistentResult);
    TE_CHECKRETURN_CODE(code);

    std::unique_ptr<MultiplexingFeatureSetCursor> retval(new MultiplexingFeatureSetCursor());
    code = retval->add(FeatureSetCursor2Ptr(new SceneFeatureSetCursor(std::move(transientResult), TRANSIENT_BIT), Memory_deleter_const<FeatureSetCursor2, SceneFeatureSetCursor>));
    TE_CHECKRETURN_CODE(code);

    code = retval->add(FeatureSetCursor2Ptr(new SceneFeatureSetCursor(std::move(persistentResult), PERSISTENT_BIT), Memory_deleter_const<FeatureSetCursor2, SceneFeatureSetCursor>));
    TE_CHECKRETURN_CODE(code);

    result = FeatureSetCursor2Ptr(retval.release(), Memory_deleter_const<FeatureSetCursor2, MultiplexingFeatureSetCursor>);

    return code;
}
TAKErr SceneLayer::query(FeatureSetCursor2Ptr &result, const FeatureDataStore2::FeatureSetQueryParameters &params) NOTHROWS
{
    TAKErr code(TE_Ok);

    std::list<std::unique_ptr<FeatureDataStore2::FeatureSetQueryParameters>> paramsCleaner;
    const FeatureDataStore2::FeatureSetQueryParameters *persistentParams = &params;
    const FeatureDataStore2::FeatureSetQueryParameters *transientParams = &params;
    if (!params.ids->empty()) {
        persistentParams = nullptr;
        transientParams = nullptr;

        TAK::Engine::Port::Collection<int64_t>::IteratorPtr iter(nullptr, nullptr);
        code = params.ids->iterator(iter);
        TE_CHECKRETURN_CODE(code);

        do {
            code = iter->next();
            TE_CHECKBREAK_CODE(code);

            int64_t sid;
            code = iter->get(sid);
            TE_CHECKBREAK_CODE(code);

            // get the correct params instance
            const FeatureDataStore2::FeatureSetQueryParameters *paramsImpl = IS_TRANSIENT_SID(sid) ? transientParams : persistentParams;
            // allocate if necessary
            if (!paramsImpl) {
                std::unique_ptr<FeatureDataStore2::FeatureSetQueryParameters> pp(new FeatureDataStore2::FeatureSetQueryParameters(params));
                paramsImpl = pp.get();
                paramsCleaner.push_back(std::move(pp));
                if (IS_TRANSIENT_SID(sid))
                    transientParams = paramsImpl;
                else
                    persistentParams = paramsImpl;
            }

            // derive the FSID from the SID and add to the params
            code = paramsImpl->ids->add(FID(sid));
            TE_CHECKBREAK_CODE(code);
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);

        // if we're only querying against one store, utilize original limit/offset
        if (paramsCleaner.size() == 1u) {
            paramsCleaner.front()->limit = params.limit;
            paramsCleaner.front()->offset = params.offset;
        }
    }

    FeatureSetCursorPtr transientResult(nullptr, nullptr);
    FeatureSetCursorPtr persistentResult(nullptr, nullptr);

    if (transientParams) {
        code = transient.queryFeatureSets(transientResult, *transientParams);
        TE_CHECKRETURN_CODE(code);
        transientResult = FeatureSetCursor2Ptr(new SceneFeatureSetCursor(std::move(transientResult), TRANSIENT_BIT), Memory_deleter_const<FeatureSetCursor2, SceneFeatureSetCursor>);
        if (!persistentParams) {
            result = std::move(transientResult);
            return code;
        }
    }
    if (persistentParams) {
        code = persistent.queryFeatureSets(persistentResult, *persistentParams);
        TE_CHECKRETURN_CODE(code);
        persistentResult = FeatureSetCursor2Ptr(new SceneFeatureSetCursor(std::move(persistentResult), PERSISTENT_BIT), Memory_deleter_const<FeatureSetCursor2, SceneFeatureSetCursor>);
        if (!transientParams) {
            result = std::move(persistentResult);
            return code;
        }
    }

    std::unique_ptr<MultiplexingFeatureSetCursor> retval(new MultiplexingFeatureSetCursor());
    if (persistentResult) {
        code = retval->add(std::move(persistentResult));
        TE_CHECKRETURN_CODE(code);
    }
    if (transientResult) {
        code = retval->add(std::move(transientResult));
        TE_CHECKRETURN_CODE(code);
    }

    result = FeatureSetCursor2Ptr(retval.release(), Memory_deleter_const<FeatureSetCursor2, MultiplexingFeatureSetCursor>);

    // if both stores are being queried and there is a limit, put a brute force wrapper
    if (paramsCleaner.size() == 2u && params.limit)
        result = FeatureSetCursor2Ptr(new BruteForceLimitOffsetFeatureSetCursor(std::move(result), params.limit, params.offset), Memory_deleter_const<FeatureSetCursor2, BruteForceLimitOffsetFeatureSetCursor>);

    return code;
}
TAKErr SceneLayer::addContentChangedListener(ContentChangedListener *listener) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    listeners.insert(listener);
    return code;
}
TAKErr SceneLayer::removeContentChangedListener(ContentChangedListener *listener) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    listeners.erase(listener);
    return code;
}
TAKErr SceneLayer::loadScene(ScenePtr &value, const int64_t sid) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    if (IS_TRANSIENT_SID(sid)) {
        std::map<int64_t, std::shared_ptr<Scene>>::iterator entry;
        entry = scenes.find(sid);
        if (entry == scenes.end())
            return TE_InvalidArg;

        value = ScenePtr(new SceneWrapper(entry->second), Memory_deleter_const<Scene, SceneWrapper>);
        return TE_Ok;
    } else {
        FeaturePtr_const f(nullptr, nullptr);
        code = persistent.getFeature(f, FID(sid));
        TE_CHECKRETURN_CODE(code);

        const atakmap::util::AttributeSet *attrs = f->getAttributes();
        if (!attrs)
            return TE_InvalidArg;

        SceneInfo info;
        code = decodeInfo(&info, *attrs);
        TE_CHECKRETURN_CODE(code);

        code = SceneFactory_create(value, info.uri, nullptr, nullptr, info.resourceAliases.get());
        TE_CHECKRETURN_CODE(code);

        return code;
    }
}
TAKErr SceneLayer::loadScene(std::shared_ptr<Scene> &value, const int64_t sid) NOTHROWS
{
    TAKErr code(TE_Ok);
    ScenePtr scene(nullptr, nullptr);
    code = loadScene(scene, sid);
    TE_CHECKRETURN_CODE(code);

    value = std::move(scene);
    return code;
}
TAKErr SceneLayer::addNoSync(int64_t *fid, FeatureDataStore2 &store, const int64_t fsid, const SceneInfo &info) NOTHROWS
{
    TAKErr code(TE_Ok);

    // encode the info
    atakmap::util::AttributeSet attrs;
    code = encodeInfo(attrs, info);
    TE_CHECKRETURN_CODE(code);

    // insert the feature into the transient store
    FeaturePtr_const inserted(nullptr, nullptr);
    GeometryPtr location(new atakmap::feature::Point(0.0, 0.0), Memory_deleter_const<atakmap::feature::Geometry>);
    if (info.location.get()) {
        // XXX - pad out for now until we've got bounds refresh hooked up
        atakmap::feature::LineString ls;
        const double metersDegLat = GeoPoint2_approximateMetersPerDegreeLatitude(info.location->latitude);
        const double metersDegLng = GeoPoint2_approximateMetersPerDegreeLongitude(info.location->longitude);
        const double padX = __HACK_POI_PADDING / metersDegLng;
        const double padY = __HACK_POI_PADDING / metersDegLat;
        ls.addPoint(info.location->longitude - padX, info.location->latitude + padY);
        ls.addPoint(info.location->longitude + padX, info.location->latitude + padY);
        ls.addPoint(info.location->longitude + padX, info.location->latitude - padY);
        ls.addPoint(info.location->longitude - padX, info.location->latitude - padY);
        ls.addPoint(info.location->longitude - padX, info.location->latitude + padY);
        
        location = GeometryPtr(new atakmap::feature::Polygon(ls), Memory_deleter_const<atakmap::feature::Geometry>);
    } else {
        return TE_InvalidArg;
    }
    code = store.insertFeature(&inserted, fsid, info.name, *location, info.altitudeMode, 0.0, nullptr, attrs);
    TE_CHECKRETURN_CODE(code);

    if (fid)
        *fid = inserted->getId();

    return code;
}
void SceneLayer::dispatchContentChangedNoSync() NOTHROWS
{
    auto it = listeners.begin();
    while (it != listeners.end()) {
        TAKErr code = (*it)->contentChanged(*this);
        if (code == TE_Done) {
            it = listeners.erase(it);
        } else {
            it++;
        }
    }
}
TAKErr SceneLayer::getSceneCacheDir(TAK::Engine::Port::String &value, const int64_t sid) const NOTHROWS
{
    if (IS_TRANSIENT_SID(sid) || !cacheDir) {
        value = nullptr;
        return TE_Ok;
    }

    StringBuilder strm;
    strm << cacheDir << TE_FILESEP << "scenes" << TE_FILESEP << sid;
    value = strm.c_str();
    return TE_Ok;
}
TAKErr SceneLayer::getSceneSetCacheDir(TAK::Engine::Port::String &value, const int64_t sid) const NOTHROWS
{
    if (IS_TRANSIENT_SID(sid) || !cacheDir) {
        value = nullptr;
        return TE_Ok;
    }

    StringBuilder strm;
    strm << cacheDir << TE_FILESEP << "scenesets" << TE_FILESEP << sid;
    value = strm.c_str();
    return TE_Ok;
}
void SceneLayer::cleanCache(const int64_t ssid) NOTHROWS
{
    TAK::Engine::Port::String cache;
    if (getSceneSetCacheDir(cache, ssid) == TE_Ok) {
        if(cache)
            IO_delete(cache);

        // delete dirs for all features
        do {
            FeatureDataStore2::FeatureQueryParameters fparams;
            if (fparams.featureSetIds->add(ssid) != TE_Ok)
                break;
            FeatureCursorPtr fresult(nullptr, nullptr);
            if (this->query(fresult, fparams) != TE_Ok)
                break;
            while (fresult->moveToNext() == TE_Ok) {
                int64_t sid;
                if (fresult->getId(&sid) != TE_Ok)
                    continue;
                if (getSceneCacheDir(cache, sid) != TE_Ok)
                    continue;
                if (cache)
                    IO_delete(cache);
            }
        } while (false);
    }
}

SceneLayer::ContentChangedListener::~ContentChangedListener() NOTHROWS
{}

TAKErr TAK::Engine::Model::SceneLayer_getInfo(SceneInfo *value, const atakmap::util::AttributeSet &attrs) NOTHROWS
{
    TAKErr code(TE_Ok);
    code = decodeInfo(value, attrs);
    TE_CHECKRETURN_CODE(code);

    return code;
}

namespace
{
    TAKErr encodeInfo(atakmap::util::AttributeSet &value, const SceneInfo &info) NOTHROWS
    {
        try {
            atakmap::util::AttributeSet nested;

            if (info.name)
                nested.setString("name", info.name);
            if (info.uri)
                nested.setString("uri", info.uri);
            if (info.type)
                nested.setString("type", info.type);

            if (info.location.get()) {
                atakmap::util::AttributeSet location;
                location.setDouble("latitude", info.location->latitude);
                location.setDouble("longitude", info.location->longitude);
                location.setDouble("altitude", info.location->altitude);
                nested.setAttributeSet("location", location);
            }
            nested.setInt("srid", info.srid);
            nested.setInt("altitudeMode", info.altitudeMode);

            if (info.localFrame.get()) {
                double localFrame[16];
                info.localFrame->get(localFrame);
                const double *pLocalFrame = localFrame;
                nested.setDoubleArray("localFrame", atakmap::util::AttributeSet::DoubleArray(pLocalFrame, pLocalFrame + 16u));
            }
            if (info.aabb.get()) {
                atakmap::util::AttributeSet aabb;
                aabb.setDouble("minX", info.aabb->minX);
                aabb.setDouble("minY", info.aabb->minY);
                aabb.setDouble("minZ", info.aabb->minZ);
                aabb.setDouble("maxX", info.aabb->maxX);
                aabb.setDouble("maxY", info.aabb->maxY);
                aabb.setDouble("maxZ", info.aabb->maxZ);
                nested.setAttributeSet("aabb", aabb);
            }
            nested.setDouble("minDisplayResolution", info.minDisplayResolution);
            nested.setDouble("maxDisplayResolution", info.maxDisplayResolution);
            nested.setDouble("resolution", info.resolution);

            if (info.resourceAliases.get()) {
                // XXX - const cast....
                auto &resourceAliases = const_cast<TAK::Engine::Port::Collection<ResourceAlias> &>(*info.resourceAliases);
                if (!resourceAliases.empty()) {
                    atakmap::util::AttributeSet aliases;

                    TAK::Engine::Port::Collection<ResourceAlias>::IteratorPtr iter(nullptr, nullptr);
                    resourceAliases.iterator(iter);
                    do {
                        ResourceAlias alias;
                        if (iter->get(alias) == TE_Ok) {
                            aliases.setString(alias.getResourceRef(), alias.getTargetPath());
                        }
                        if (iter->next() != TE_Ok)
                            break;
                    } while (true);

                    nested.setAttributeSet("resourceAliases", aliases);
                }
            }

            value.setAttributeSet(ATTR_KEY, nested);
            return TE_Ok;
        } catch (...) {
            return TE_Err;
        }
    }

    TAKErr decodeInfo(SceneInfo *value, const atakmap::util::AttributeSet &attrs) NOTHROWS
    {
        try {
            if (!attrs.containsAttribute(ATTR_KEY))
                return TE_InvalidArg;
            if (attrs.getAttributeType(ATTR_KEY) != atakmap::util::AttributeSet::ATTRIBUTE_SET)
                return TE_InvalidArg;

            atakmap::util::AttributeSet nested = attrs.getAttributeSet(ATTR_KEY);
            if (nested.containsAttribute("name"))
                value->name = nested.getString("name");
            if (nested.containsAttribute("uri"))
                value->uri = nested.getString("uri");
            if (nested.containsAttribute("type"))
                value->type = nested.getString("type");

            if (nested.containsAttribute("location")) {
                atakmap::util::AttributeSet location = nested.getAttributeSet("location");
                value->location = GeoPoint2Ptr(
                    new GeoPoint2(location.getDouble("latitude"),
                                  location.getDouble("longitude"),
                                  location.getDouble("altitude"),   
                                  AltitudeReference::HAE), Memory_deleter_const<GeoPoint2>);
            }

            if (nested.containsAttribute("srid"))
                value->srid = nested.getInt("srid");
            else
                value->srid = -1;

            if (!nested.containsAttribute("altitudeMode"))
                return TE_InvalidArg;
            value->altitudeMode = (AltitudeMode)nested.getInt("altitudeMode");
            if (value->location.get()) {
                switch (value->altitudeMode) {
                case TEAM_Absolute :
                    value->location->altitudeRef = AltitudeReference::HAE;
                    break;
                case TEAM_Relative :
                case TEAM_ClampToGround :
                    value->location->altitudeRef = AltitudeReference::AGL;
                    break;
                }
            }

            if (nested.containsAttribute("localFrame")) {
                const double *mx = nested.getDoubleArray("localFrame").first;
                std::unique_ptr<TAK::Engine::Math::Matrix2> localFrame(new TAK::Engine::Math::Matrix2(mx[0], mx[1], mx[2], mx[3], mx[4], mx[5], mx[6], mx[7], mx[8], mx[9], mx[10], mx[11], mx[12], mx[13], mx[14], mx[15]));
                value->localFrame = TAK::Engine::Math::Matrix2Ptr_const(localFrame.release(), Memory_deleter_const<TAK::Engine::Math::Matrix2>);
            }

            if (nested.containsAttribute("aabb")) {
                atakmap::util::AttributeSet aabb = nested.getAttributeSet("aabb");
                value->aabb = Envelope2Ptr(
                    new Envelope2(aabb.getDouble("minX"),
                                  aabb.getDouble("minY"),
                                  aabb.getDouble("minZ"),
                                  aabb.getDouble("maxX"),
                                  aabb.getDouble("maxY"),
                                  aabb.getDouble("maxZ")),
                    Memory_deleter_const<Envelope2>);
            }
            value->minDisplayResolution = nested.getDouble("minDisplayResolution");
            value->maxDisplayResolution = nested.getDouble("maxDisplayResolution");
            if(nested.containsAttribute("resolution"))
                value->resolution = nested.getDouble("resolution");


            if (nested.containsAttribute("resourceAliases")) {
                std::unique_ptr<TAK::Engine::Port::STLVectorAdapter<ResourceAlias>> resourceAliases(new TAK::Engine::Port::STLVectorAdapter<ResourceAlias>());

                atakmap::util::AttributeSet aliases = nested.getAttributeSet("resourceAliases");
                std::vector<const char *> resourceRefs = aliases.getAttributeNames();
                for (std::size_t i = 0u; i < resourceRefs.size(); i++) {
                    const char *targetPath = aliases.getString(resourceRefs[i]);
                    resourceAliases->add(ResourceAlias(resourceRefs[i], targetPath));
                }

                value->resourceAliases = ResourceAliasCollectionPtr(resourceAliases.release(), Memory_deleter_const<TAK::Engine::Port::Collection<ResourceAlias>, TAK::Engine::Port::STLVectorAdapter<ResourceAlias>>);
            }

            return TE_Ok;
        } catch (...) {
            return TE_Err;
        }
    }

    SceneFeatureCursor::SceneFeatureCursor(FeatureCursorPtr &&impl_, const unsigned bit_) NOTHROWS :
        impl(std::move(impl_)),
        bit(bit_),
        row(nullptr, nullptr)
    {}
    TAKErr SceneFeatureCursor::getId(int64_t *value) NOTHROWS
    {
        if (!value)
            return TE_InvalidArg;
        TAKErr code(TE_Ok);
        int64_t fid;
        code = impl->getId(&fid);
        TE_CHECKRETURN_CODE(code);

        *value = FID2SID(fid, bit);
        return code;
    }
    TAKErr SceneFeatureCursor::getFeatureSetId(int64_t *value) NOTHROWS
    {
        if (!value)
            return TE_InvalidArg;
        TAKErr code(TE_Ok);
        int64_t fsid;
        code = impl->getFeatureSetId(&fsid);
        TE_CHECKRETURN_CODE(code);

        *value = FID2SID(fsid, bit);
        return code;
    }
    TAKErr SceneFeatureCursor::getVersion(int64_t *value) NOTHROWS
    {
        return impl->getVersion(value);
    }
    TAKErr SceneFeatureCursor::getRawGeometry(RawData *value) NOTHROWS
    {
        return impl->getRawGeometry(value);
    }
    FeatureDefinition2::GeometryEncoding SceneFeatureCursor::getGeomCoding() NOTHROWS
    {
        return impl->getGeomCoding();
    }
    AltitudeMode SceneFeatureCursor::getAltitudeMode() NOTHROWS 
    {
        return impl->getAltitudeMode();
    }
    double SceneFeatureCursor::getExtrude() NOTHROWS 
    {
        return impl->getExtrude();
    }
    TAKErr SceneFeatureCursor::getName(const char **value) NOTHROWS
    {
        return impl->getName(value);
    }
    FeatureDefinition2::StyleEncoding SceneFeatureCursor::getStyleCoding() NOTHROWS
    {
        return impl->getStyleCoding();
    }
    TAKErr SceneFeatureCursor::getRawStyle(RawData *value) NOTHROWS
    {
        return impl->getRawStyle(value);
    }
    TAKErr SceneFeatureCursor::getAttributes(const atakmap::util::AttributeSet **value) NOTHROWS
    {
        return impl->getAttributes(value);
    }
    TAKErr SceneFeatureCursor::get(const Feature2 **value) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (!row.get()) {
            int64_t fid;
            int64_t fsid;
            int64_t version;

            code = getId(&fid);
            TE_CHECKRETURN_CODE(code);

            code = getFeatureSetId(&fsid);
            TE_CHECKRETURN_CODE(code);

            code = getVersion(&version);
            TE_CHECKRETURN_CODE(code);

            code = Feature_create(row, fid, fsid, *this, version);
        }

        *value = row.get();
        return code;
    }
    TAKErr SceneFeatureCursor::moveToNext() NOTHROWS
    {
        row.reset();

        return impl->moveToNext();
    }

    SceneFeatureSetCursor::SceneFeatureSetCursor(FeatureSetCursor2Ptr &&impl_, const unsigned bit_) NOTHROWS :
        impl(std::move(impl_)),
        bit(bit_),
        row(nullptr, nullptr)
    {}
    TAKErr SceneFeatureSetCursor::get(const FeatureSet2 **value) NOTHROWS
    {
        if (!row.get())
            return TE_IllegalState;
        *value = row.get();
        return TE_Ok;
    }
    TAKErr SceneFeatureSetCursor::moveToNext() NOTHROWS
    {
        TAKErr code(TE_Ok);
        row.reset();
        code = impl->moveToNext();
        if (code == TE_Done)
            return code;
        TE_CHECKRETURN_CODE(code);

        const FeatureSet2 *value;
        code = impl->get(&value);
        TE_CHECKRETURN_CODE(code);
        row = FeatureSetPtr_const(
            new FeatureSet2(FID2SID(value->getId(), bit),
                            value->getProvider(),
                            value->getType(),
                            value->getName(),
                            value->getMinResolution(),
                            value->getMaxResolution(),
                            value->getVersion()),
            Memory_deleter_const<FeatureSet2>);
        return code;
    }

    SceneWrapper::SceneWrapper(const std::shared_ptr<Scene> &impl_) NOTHROWS :
        impl(impl_)
    {}
    SceneNode &SceneWrapper::getRootNode() const NOTHROWS
    {
        return impl->getRootNode();
    }
    const Envelope2 &SceneWrapper::getAABB() const NOTHROWS
    {
        return impl->getAABB();
    }
    unsigned int SceneWrapper::getProperties() const NOTHROWS
    {
        return impl->getProperties();
    }

    FeatureSetInsertGuard::FeatureSetInsertGuard(const int64_t fsid_, FeatureDataStore2 &store_) NOTHROWS :
        fsid(fsid_),
        store(store_),
        valid(false)
    {}
    FeatureSetInsertGuard::~FeatureSetInsertGuard() NOTHROWS
    {
        if (!valid)
            store.deleteFeatureSet(fsid);
    }
    void FeatureSetInsertGuard::setValid() NOTHROWS
    {
        valid = true;
    }

}
