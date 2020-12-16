#include <chrono>

#include "renderer/model/GLSceneLayer.h"

#include "core/ProjectionFactory3.h"
#include "elevation/ElevationManager.h"
#include "math/Utils.h"
#include "math/Rectangle.h"
#include "feature/Envelope2.h"
#include "feature/Feature2.h"
#include "feature/GeometryCollection2.h"
#include "feature/GeometryTransformer.h"
#include "feature/Point2.h"
#include "feature/Polygon.h"
#include "port/STLListAdapter.h"
#include "renderer/core/GLLayerFactory2.h"
#include "renderer/model/GLSceneFactory.h"
#include "renderer/model/GLScene.h"
#include "renderer/model/SceneObjectControl.h"
#include "thread/Lock.h"

using namespace TAK::Engine::Renderer::Model;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

#define TAG "GLModelLayer";


namespace
{
    class QueryContextImpl : public GLAsynchronousMapRenderable3::QueryContext
    {
    public :
        std::set<GLMapRenderable2 *> result;
    };

    class Spi : public GLLayerSpi2
    {
    public :
        TAKErr create(GLLayer2Ptr &value, GLMapView2 &ctx, Layer2 &subject) NOTHROWS override;
    };

    void glReleaseRenderableSet(void *opaque) NOTHROWS
    {
        auto *arg = static_cast<std::set<GLMapRenderable2 *> *>(opaque);
        for (auto it = arg->begin(); it != arg->end(); it++)
            (*it)->release();
        arg->clear();
    }
}

class GLSceneLayer::SceneRenderer  : public SceneObjectControl::UpdateListener
{
public :
    SceneRenderer(GLSceneLayer &owner, RenderContext &ctx, const Feature2 &feature, const char *resourcesDir) NOTHROWS;
    ~SceneRenderer() NOTHROWS override;
public :
    TAKErr onBoundsChanged(const Envelope2 &aabb, const double minGsd, const double maxGsd) NOTHROWS override;
    TAKErr onClampToGroundOffsetComputed(const double offset) NOTHROWS override;
public :
    GLSceneLayer &owner;
    GLMapRenderable2Ptr value;
    SceneInfo info;
    //ColorControl color;
    HitTestControl *hittest;
    SceneObjectControl *ctrl;
    TAK::Engine::Feature::Envelope2 featureBounds;
    int64_t fid;
    int64_t version;
    bool valid;
};

GLSceneLayer::GLSceneLayer(TAK::Engine::Core::MapRenderer &ctx, TAK::Engine::Model::SceneLayer &subject_) NOTHROWS :
    GLAsynchronousMapRenderable3(),
    subject(subject_),
    renderer(ctx)
{}
GLSceneLayer::~GLSceneLayer() NOTHROWS
{}

TAKErr GLSceneLayer::hitTest(TAK::Engine::Core::GeoPoint2 *value, const TAK::Engine::Core::MapSceneModel2 &sceneModel, const float x, const float y) NOTHROWS
{
    TAKErr code(TE_Done);
    {
        Thread::ReadLock readLock(this->sceneMutex);
        TE_CHECKRETURN_CODE(readLock.status);

        auto end = this->active.end();
        for(auto it = this->active.begin(); it != end; it++) {
            if (!it->second->hittest)
                continue;
            code = it->second->hittest->hitTest(value, sceneModel, x, y);
            if (code != TE_Done)
                break;
        }    
    }

    return code;
}

TAKErr GLSceneLayer::hitTest(TAK::Engine::Port::Collection<int64_t> &fids, float screenX, float screenY, const TAK::Engine::Core::GeoPoint2 &point, double resolution, float radius, int limit) NOTHROWS
{
    TAKErr code(TE_Ok);

    TAK::Engine::Math::Point2<double> loc(point.longitude, point.latitude);
    double rlat = atakmap::math::toRadians(loc.y);
    double metersDegLat = 111132.92 - 559.82 * cos(2 * rlat)
        + 1.175 * cos(4 * rlat);
    double metersDegLng = 111412.84 * cos(rlat)
        - 93.5 * cos(3 * rlat);

    double thresholdMeters = resolution * radius;
    double ra = thresholdMeters / metersDegLat;
    double ro = thresholdMeters / metersDegLng;

    TAK::Engine::Feature::Envelope2 hitBox(loc.x - ro, loc.y - ra, loc.x + ro, loc.y + ra);

    {
        Thread::ReadLock readLock(this->sceneMutex);
        code = readLock.status;
        TE_CHECKRETURN_CODE(code);

        auto end = this->active.end();
        for(auto it = this->active.begin(); it != end; it++) {
            TAK::Engine::Feature::Envelope2 mbb = it->second->featureBounds;
            if (atakmap::math::Rectangle<double>::intersects(mbb.minX, mbb.minY, mbb.maxX, mbb.maxY,
                hitBox.minX, hitBox.minY, hitBox.maxX, hitBox.maxY)) {
                
                fids.add(it->second->fid);
                if (limit>0u && fids.size() > std::size_t(limit))
                    break;
            }
            ++it;
        }
    }

    return code;
}

TAKErr GLSceneLayer::getSceneObjectControl(SceneObjectControl **ctrl, const int64_t sid) NOTHROWS
{
    TAKErr code(TE_Ok);
    ReadLock lock(renderables_mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    auto entry = cache.find(sid);
    if (entry == cache.end())
        return TE_InvalidArg;

    *ctrl = entry->second->ctrl;
    return ctrl ? TE_Ok : TE_InvalidArg;
}

Layer2 &GLSceneLayer::getSubject() NOTHROWS
{
    return subject;
}
void GLSceneLayer::start() NOTHROWS
{
    this->subject.addContentChangedListener(this);
    this->renderer.registerControl(this->subject, FeatureHitTestControl_getType(), static_cast<FeatureHitTestControl *>(this));
    this->renderer.registerControl(this->subject, HitTestControl_getType(), static_cast<HitTestControl *>(this));
    this->renderer.registerControl(this->subject, SceneLayerControl_getType(), static_cast<SceneLayerControl *>(this));
}
void GLSceneLayer::stop() NOTHROWS
{
    this->renderer.unregisterControl(this->subject, FeatureHitTestControl_getType(), static_cast<FeatureHitTestControl *>(this));
    this->renderer.unregisterControl(this->subject, HitTestControl_getType(), static_cast<HitTestControl *>(this));
    this->renderer.unregisterControl(this->subject, SceneLayerControl_getType(), static_cast<SceneLayerControl *>(this));
    this->subject.removeContentChangedListener(this);
}


//protected Collection<? extends GLMapRenderable2> getRenderList()
TAKErr GLSceneLayer::getRenderables(Collection<GLMapRenderable2 *>::IteratorPtr &iter) NOTHROWS
{
    STLListAdapter<GLMapRenderable2 *> adrawList(this->drawList);
    return adrawList.iterator(iter);
}

    
//protected void resetPendingData(Collection<GLMapRenderable2> pendingData)
TAKErr GLSceneLayer::resetQueryContext(GLAsynchronousMapRenderable3::QueryContext &opaque) NOTHROWS
{
    auto &ctx = static_cast<QueryContextImpl &>(opaque);
    ctx.result.clear();
    return TE_Ok;
}

//protected Collection<GLMapRenderable2> createPendingData()
TAKErr GLSceneLayer::createQueryContext(GLAsynchronousMapRenderable3::QueryContextPtr &value) NOTHROWS
{
    value = GLAsynchronousMapRenderable3::QueryContextPtr(new QueryContextImpl(), Memory_deleter_const<GLAsynchronousMapRenderable3::QueryContext, QueryContextImpl>);
    return TE_Ok;
}

    
//protected boolean updateRenderList(ViewState state, Collection<GLMapRenderable2> pendingData)
TAKErr GLSceneLayer::updateRenderableLists(GLAsynchronousMapRenderable3::QueryContext &opaque) NOTHROWS
{
    auto &ctx = static_cast<QueryContextImpl &>(opaque);
    std::set<GLMapRenderable2 *> toRelease(this->drawList.begin(), this->drawList.end());
    this->drawList.clear();
    for (auto it = ctx.result.begin(); it != ctx.result.end(); it++) {
        toRelease.erase(*it);
        this->drawList.push_back(*it);
    }

    if (!toRelease.empty()) {
        std::unique_ptr<void, void(*)(const void *)> glarg(new std::set<GLMapRenderable2 *>(toRelease), Memory_void_deleter_const<std::set<GLMapRenderable2 *>>);
        this->renderer.getRenderContext().queueEvent(glReleaseRenderableSet, std::move(glarg));
    }
    return TE_Ok;
}

    
//protected void query(ViewState state, Collection<GLMapRenderable2> result)
TAKErr GLSceneLayer::query(GLAsynchronousMapRenderable3::QueryContext &result, const GLAsynchronousMapRenderable3::ViewState &state)  NOTHROWS
{
    TAKErr code(TE_Ok);
    
    std::set<std::shared_ptr<SceneRenderer>> renderers;
    if (state.crossesIDL) {
        ViewStatePtr scratch(nullptr, nullptr);
        code = this->newViewStateInstance(scratch);
        TE_CHECKRETURN_CODE(code);

        scratch->copy(state);

        // west of IDL
        scratch->eastBound = 180.0;
        code = queryImpl(renderers, *scratch);
        TE_CHECKRETURN_CODE(code);

        // reset
        scratch->copy(state);

        // east of IDL
        scratch->westBound = -180.0;
        code =queryImpl(renderers, *scratch);
        TE_CHECKRETURN_CODE(code);

    } else {
        code = queryImpl(renderers, state);
        TE_CHECKRETURN_CODE(code);
    }

    // block query when hit-testing. 
    // XXX-- Temporary until general access to MapRenderable controls is available
    {
        auto &ctx = static_cast<QueryContextImpl &>(result);

        Thread::WriteLock writeLock(this->sceneMutex);
        code = writeLock.status;
        TE_CHECKRETURN_CODE(code);

        this->active.clear();
        for (auto it = renderers.begin(); it != renderers.end(); it++) {
            active[(*it)->fid] = *it;
            ctx.result.insert((*it)->value.get());
        }
    }

    return code;
}

//private void queryImpl(ViewState state, Collection<GLMapRenderable2> retval)
TAKErr GLSceneLayer::queryImpl(std::set<std::shared_ptr<SceneRenderer>> &ctx, const GLAsynchronousMapRenderable3::ViewState &state)  NOTHROWS
{
    TAKErr code(TE_Ok);

    // XXX - query and create models
    FeatureCursorPtr result(nullptr, nullptr);
    FeatureDataStore2::FeatureQueryParameters params;

    atakmap::feature::LineString spatialFilter;
    spatialFilter.addPoint(state.westBound, state.northBound);
    spatialFilter.addPoint(state.eastBound, state.northBound);
    spatialFilter.addPoint(state.eastBound, state.southBound);
    spatialFilter.addPoint(state.westBound, state.southBound);
    spatialFilter.addPoint(state.westBound, state.northBound);

    params.spatialFilter = GeometryPtr(new atakmap::feature::Polygon(spatialFilter), Memory_deleter_const<atakmap::feature::Geometry, atakmap::feature::Polygon>);
    //params.maxResolution = state.drawMapResolution;
    params.visibleOnly = true;

    //if (this->checkQueryThreadAbort())
    //    return;

    //long s = System.currentTimeMillis();
    //result = this->dataStore.queryFeatures(params);
    code = this->subject.query(result, params);
    TE_CHECKRETURN_CODE(code);
    do {
        code = result->moveToNext();
        TE_CHECKBREAK_CODE(code);

        //if (this->checkQueryThreadAbort())
        //    break;
        int64_t fid;
        code = result->getId(&fid);
        TE_CHECKBREAK_CODE(code);

        //SceneRenderer entry = cache.get(result.getId());
        auto entry = cache.find(fid);
        if (entry != cache.end()) {
            int64_t version;
            code = result->getVersion(&version);
            TE_CHECKBREAK_CODE(code);

            if (entry->second->version != version) {
                if (entry->second->ctrl) {
                    do {
                        const atakmap::util::AttributeSet *attrs;
                        code = result->getAttributes(&attrs);
                        if (!attrs)
                            break;
                        SceneInfo info;
                        code = SceneLayer_getInfo(&info, *attrs);
                        TE_CHECKBREAK_CODE(code);

                        entry->second->info = info;
                        code = entry->second->ctrl->setLocation(*info.location, info.localFrame.get(), info.srid, info.altitudeMode);
                        TE_CHECKBREAK_CODE(code);
                    } while (false);
                }
                entry->second->version = version;
            }
        } else {
            const Feature2 *f;
            code = result->get(&f);
            TE_CHECKBREAK_CODE(code);

            TAK::Engine::Port::String resourceDirectory;
            if (subject.getSceneCacheDir(resourceDirectory, f->getId()) != TE_Ok)
                resourceDirectory = nullptr;

            cache[fid] = std::unique_ptr<SceneRenderer>(new SceneRenderer(*this, renderer.getRenderContext(), *f, resourceDirectory));
            entry = cache.find(fid);
        }

        // XXX - if we fail to construct the scene, this will always be NULL.
        //       do we want to provide mechanism for re-check in the event of
        //       asynchronous registration???
        if (entry->second->value.get()) {
            ctx.insert(entry->second);
        }
    } while (true);
    if (code == TE_Done)
        code = TE_Ok;

    return code;
}

    
int GLSceneLayer::getRenderPass() NOTHROWS
{
    // we are capturing surface render bounds with surface pass
    return GLMapView2::Sprites|GLMapView2::Surface|GLMapView2::XRay;
}

    
//public void onDataStoreContentChanged(FeatureDataStore2 dataStore)
TAKErr GLSceneLayer::contentChanged(const SceneLayer &layer) NOTHROWS
{
    invalidateNoSync();
    return TE_Ok;
}

#if 0
    
public void onFeatureInserted(FeatureDataStore2 dataStore, long fid,
                                FeatureDefinition2 def, long version) {
    invalidate();
}

    
public void onFeatureUpdated(FeatureDataStore2 dataStore, long fid,
                                int modificationMask, String name, Geometry geom, Style style,
                                AttributeSet attribs, int attribsUpdateType) {
    invalidate();
}

    
public void onFeatureDeleted(FeatureDataStore2 dataStore, long fid) {
    invalidate();
}

    
public void onFeatureVisibilityChanged(FeatureDataStore2 dataStore,
                                        long fid, boolean visible) {
    invalidate();
}
#endif

#if 0    
public synchronized void hitTest(Collection<Long> fids, float screenX,
                                    float screenY, GeoPoint point, double resolution, float radius,
                                    int limit) {
    final PointD loc = new PointD(point.getLongitude(),
            point.getLatitude());
    final double rlat = Math.toRadians(loc.y);
    final double metersDegLat = 111132.92 - 559.82 * Math.cos(2 * rlat)
            + 1.175 * Math.cos(4 * rlat);
    final double metersDegLng = 111412.84 * Math.cos(rlat)
            - 93.5 * Math.cos(3 * rlat);

    final double thresholdMeters = resolution * radius;
    final double ra = thresholdMeters / metersDegLat;
    final double ro = thresholdMeters / metersDegLng;

    final Envelope hitBox = new Envelope(loc.x - ro, loc.y - ra, Double.NaN,
            loc.x + ro, loc.y + ra, Double.NaN);

    for (GLMapRenderable2 r : this->drawList) {
        if (!(r instanceof GLModelRenderer2))
            continue;
        GLModelRenderer2 mr = (GLModelRenderer2) r;
        final Envelope mbb = mr.featureBounds;
        if (mbb == null)
            continue;

        if (!Rectangle.intersects(mbb.minX, mbb.minY, mbb.maxX, mbb.maxY,
                hitBox.minX, hitBox.minY, hitBox.maxX, hitBox.maxY))
            continue;

        fids.add(mr.feature.getId());
    }
}
#endif

GLSceneLayer::SceneRenderer::SceneRenderer(GLSceneLayer &owner_, RenderContext &ctx_, const Feature2 &feature_, const char *resourcesDir_) NOTHROWS :
    owner(owner_),
    version(0),
    valid(false),
    value(nullptr, nullptr),
    hittest(nullptr),
    ctrl(nullptr)
{
    TAKErr code(TE_Ok);

    do {
        this->fid = feature_.getId();
        const int64_t fsid = feature_.getFeatureSetId();

        const atakmap::util::AttributeSet *attrs = feature_.getAttributes();
        if (attrs) {
            code = SceneLayer_getInfo(&info, *attrs);
            TE_CHECKBREAK_CODE(code);
        }

        GLSceneSpi::Options opts;
        opts.cacheDir = resourcesDir_;

        // XXX - shared material manager

        code = GLSceneFactory_create(value, ctx_, info, opts);
        TE_CHECKBREAK_CODE(code);

        auto *hit_test_control = dynamic_cast<HitTestControl *>(value.get());
        if (hit_test_control) {
            this->hittest = hit_test_control;
        }

        auto *scene = dynamic_cast<GLScene *>(value.get());
        if (scene) {
            void *octrl;
            if(scene->getControl(&octrl, "TAK.Engine.Renderer.Model.SceneObjectControl") == TE_Ok)
                ctrl = static_cast<SceneObjectControl *>(octrl);
        }

        if (ctrl)
            ctrl->addUpdateListener(this);
        
        const atakmap::feature::Geometry *geom = feature_.getGeometry();
        if (geom) {
            const atakmap::feature::Envelope bounds = geom->getEnvelope();
            this->featureBounds.maxX = bounds.maxX;
            this->featureBounds.maxY = bounds.maxY;
            this->featureBounds.maxZ = bounds.maxZ;
            this->featureBounds.minX = bounds.minX;
            this->featureBounds.minY = bounds.minY;
            this->featureBounds.minZ = bounds.minZ;
        }
#if 0
        if (value instanceof Controls) {
            color = ((Controls)value).getControl(ColorControl.class);
            hittest = ((Controls)value).getControl(ModelHitTestControl.class);
            ctrl = ((Controls)value).getControl(SceneObjectControl.class);
        } else if (value instanceof GLModelRenderer2) {
            hittest = (GLModelRenderer2)value;
        }
#endif
        version = feature_.getVersion();

        //    if(ctrl != null)
        //        ctrl.addOnSceneBoundsChangedListener(this);
    } while (false);
}
GLSceneLayer::SceneRenderer::~SceneRenderer() NOTHROWS
{
    if (ctrl)
        ctrl->removeUpdateListener(*this);
}

TAKErr GLSceneLayer::SceneRenderer::onBoundsChanged(const Envelope2 &aabb, const double minGsd, const double maxGsd) NOTHROWS
{
    TAKErr code(TE_Ok);

#if 0
#define __doubleeq(a, b) \
    ((isnan(a) && isnan(b)) || (a==b))

    if (__doubleeq(minGsd, info.minDisplayResolution) &&
        __doubleeq(maxGsd, info.maxDisplayResolution) &&
        info.aabb.get() &&
        __doubleeq(info.aabb->minX, aabb.minX) &&
        __doubleeq(info.aabb->minY, aabb.minY) &&
        __doubleeq(info.aabb->minZ, aabb.minZ) &&
        __doubleeq(info.aabb->maxX, aabb.maxX) &&
        __doubleeq(info.aabb->maxY, aabb.maxY) &&
        __doubleeq(info.aabb->maxZ, aabb.maxZ)) {

        return TE_Ok;
    }
#undef __doubleeq
#endif

    info.aabb = Envelope2Ptr(new Envelope2(aabb), Memory_deleter_const<Envelope2>);
    info.minDisplayResolution = minGsd;
    info.maxDisplayResolution = maxGsd;

    code = owner.subject.update(fid, info);
    TE_CHECKRETURN_CODE(code);

    FeatureCursorPtr result(nullptr, nullptr);
    FeatureDataStore2::FeatureQueryParameters params;
    code = params.featureIds->add(fid);
    TE_CHECKRETURN_CODE(code);
    params.ignoredFields = FeatureDataStore2::FeatureQueryParameters::AttributesField | FeatureDataStore2::FeatureQueryParameters::StyleField;
    params.limit = 1;
    code = owner.subject.query(result, params);
    TE_CHECKRETURN_CODE(code);

    // capture the updated version -- this prevents an unneccessary update loop
    if (result->moveToNext() == TE_Ok) {
        const Feature2 *f;
        if (result->get(&f) == TE_Ok) {
            this->version = f->getVersion();

            // update the feature bounds
            const atakmap::feature::Geometry *g = f->getGeometry();
            if (g) {
                try {
                    atakmap::feature::Envelope e = g->getEnvelope();
                    this->featureBounds.minX = e.minX;
                    this->featureBounds.minY = e.minY;
                    this->featureBounds.minZ = e.minZ;
                    this->featureBounds.maxX = e.maxX;
                    this->featureBounds.maxY = e.maxY;
                    this->featureBounds.maxZ = e.maxZ;
                } catch (...) {}
            }
        }
    } else {
        return TE_IllegalState;
    }

    return code;
}
TAKErr GLSceneLayer::SceneRenderer::onClampToGroundOffsetComputed(const double offset) NOTHROWS
{
    TAKErr code(TE_Ok);

    SceneInfo update(this->info);
    // set the altitude mode to relative
    update.altitudeMode = TEAM_Relative;


    // update the local frame
    Matrix2Ptr localFrame(new Matrix2(), Memory_deleter_const<Matrix2>);
    if (update.localFrame.get())
        localFrame->concatenate(*update.localFrame);
    localFrame->translate(0.0, 0.0, offset);
    update.localFrame = Matrix2Ptr_const(localFrame.release(), localFrame.get_deleter());

    if (update.location.get()) {
        GeoPoint2 llaOrigin;
        TAK::Engine::Math::Point2<double> sceneOrigin(0.0, 0.0, 0.0);

        code = update.localFrame->transform(&sceneOrigin, sceneOrigin);
        TE_CHECKRETURN_CODE(code);

        Projection2Ptr proj(nullptr, nullptr);
        code = ProjectionFactory3_create(proj, update.srid); 
        TE_CHECKRETURN_CODE(code);

        code = proj->inverse(&llaOrigin, sceneOrigin);
        TE_CHECKRETURN_CODE(code);

        GeoPoint2 updateloc(*update.location);
        updateloc.altitude = llaOrigin.altitude;
        updateloc.altitudeRef = AltitudeReference::AGL;
        update.location = GeoPoint2Ptr(new GeoPoint2(updateloc), Memory_deleter_const<GeoPoint2>);
    }

    // XXX - update AABB

    this->info = update;

    code = owner.subject.update(fid, update);
    TE_CHECKRETURN_CODE(code);

    return code;
}

GLLayerSpi2 &TAK::Engine::Renderer::Model::GLSceneLayer_spi() NOTHROWS
{
    static Spi s;
    return s;
}

namespace
{

    TAKErr Spi::create(GLLayer2Ptr &value, GLMapView2 &ctx, Layer2 &subject) NOTHROWS
    {
        auto *impl = dynamic_cast<SceneLayer *>(&subject);
        if (!impl)
            return TE_InvalidArg;
        return GLLayerFactory2_create(value, subject, std::move(GLMapRenderable2Ptr(new GLSceneLayer(ctx, *impl), Memory_deleter_const<GLMapRenderable2, GLSceneLayer>)));
    }
}
