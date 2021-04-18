#include "renderer/GL.h"

#include "renderer/model/GLScene.h"

#include "core/Projection2.h"
#include "core/ProjectionFactory3.h"
#include "elevation/ElevationManager.h"
#include "feature/LineString2.h"
#include "model/MeshTransformer.h"
#include "renderer/model/HitTestControl.h"
#include "renderer/model/SceneObjectControl.h"
#include "thread/Lock.h"
#include "util/ConfigOptions.h"
#include "util/Memory.h"
#include "util/Tasking.h"
#include "renderer/GLWorkers.h"
#include "renderer/GLDepthSampler.h"
#include "renderer/model/GLMesh.h"
#include "renderer/GLES20FixedPipeline.h"

#define PROFILE_HIT_TESTS 0

#if PROFILE_HIT_TESTS
#include <chrono>
#endif


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
using namespace atakmap::renderer;

namespace
{
    TAKErr buildNodeList(std::list<std::shared_ptr<SceneNode>> &value, SceneNode &node) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (node.hasChildren()) {
            Collection<std::shared_ptr<SceneNode>>::IteratorPtr iter(nullptr, nullptr);
            code = node.getChildren(iter);
            TE_CHECKRETURN_CODE(code);

            do {
                std::shared_ptr<SceneNode> child;
                code = iter->get(child);
                TE_CHECKBREAK_CODE(code);

                if (child->hasMesh())
                    value.push_back(child);
                if (child->hasSubscene())
                    value.push_back(child);

                if (child->hasChildren())
                    buildNodeList(value, *child);

                code = iter->next();
                TE_CHECKBREAK_CODE(code);
            } while (true);
            if(code == TE_Done)
                code = TE_Ok;
            TE_CHECKRETURN_CODE(code);
        }
        return code;
    }
}

class GLScene::SceneControlImpl : public SceneObjectControl
{
public :
    SceneControlImpl(GLScene &owner) NOTHROWS;
public :
    TAKErr setLocation(const GeoPoint2 &location, const Matrix2 *localFrame, const int srid, const AltitudeMode altitudeMode) NOTHROWS override;
    TAKErr getInfo(SceneInfo *value) NOTHROWS override;
    TAKErr addUpdateListener(UpdateListener *l) NOTHROWS override;
    TAKErr removeUpdateListener(const UpdateListener &l) NOTHROWS override;
    TAKErr clampToGround() NOTHROWS override;
public :
    void dispatchBoundsChanged(const Envelope2 &aabb, const double minGsd, const double maxGsd) NOTHROWS;
    void dispatchClampToGroundOffsetComputed(const double offset) NOTHROWS;
private :
    GLScene &owner_;
    std::set<UpdateListener *> listeners_;
    Mutex mutex_;
};


GLScene::GLScene(RenderContext& ctx, const SceneInfo& info, const GLSceneSpi::Options& opts) NOTHROWS :
    GLScene(ctx, info, ScenePtr(nullptr, nullptr), opts) {}

GLScene::GLScene(TAK::Engine::Core::RenderContext& ctx, const TAK::Engine::Model::SceneInfo& info, TAK::Engine::Model::ScenePtr&& scene, const GLSceneSpi::Options& opts) NOTHROWS :
    ctx_(ctx),
    info_(info),
    opts_(opts),
    scene_(std::move(scene)),
    initializer(nullptr, nullptr),
    indicator(ctx),
    cancelInitialize(true),
    mutex_(TEMT_Recursive),
    clampRequested(info.altitudeMode == TEAM_ClampToGround),
    location_dirty_(false),
    xray_color_(0x3F07FFFFu) 
{
    sceneCtrl.reset(new SceneControlImpl(*this));

    controls_["TAK.Engine.Renderer.Model.SceneObjectControl"] = (void*)static_cast<SceneObjectControl*>(sceneCtrl.get());

    TAK::Engine::Port::String modelIcon;
    if (ConfigOptions_getOption(modelIcon, "TAK.Engine.Model.default-icon") != TE_Ok) modelIcon = "null";
    indicator.setIcon(*info_.location, modelIcon);
}

GLScene::~GLScene() NOTHROWS
{}

void GLScene::start() NOTHROWS
{}
void GLScene::stop() NOTHROWS
{}
int GLScene::getRenderPass() NOTHROWS
{
    return GLMapView2::Sprites | GLMapView2::XRay | indicator.getRenderPass();
}
void GLScene::draw(const GLMapView2 &view, const int renderPass) NOTHROWS
{
    // init tile grid, if necessary
    SceneInfo locationUpdate;
    bool updateLocation = false;
    {
        Lock lock(mutex_);
        if(!this->scene_.get()) {
            // kick off initialization if zoomed in sufficiently
            if (!this->initializer.get() &&
                (info_.minDisplayResolution && view.drawMapResolution < info_.minDisplayResolution)) {

                this->cancelInitialize = false;
                
                ThreadCreateParams params;
                params.name = "GLScene-initializer";
                params.priority = TETP_Normal;

                Thread_start(initializer, initializeThread, this, params);
            }
            indicator.draw(view, renderPass);
            return;
        }

        if (location_dirty_) {
            updateLocation = true;
            locationUpdate = this->info_;
            location_dirty_ = false;
        }
    }

    if(!this->loader_.get())
        this->loader_.reset(new GLSceneNodeLoader(3u));

    // XXX - can tile AOI be quickly computed???

    // XXX - should be graph traversal...

    bool drawIndicator = true;

    // compute and draw tiles in view
    if (updateLocation) {
        TAK::Engine::Port::String modelIcon;
        if (ConfigOptions_getOption(modelIcon, "TAK.Engine.Model.default-icon") != TE_Ok) modelIcon = "null";
        indicator.setIcon(*locationUpdate.location, modelIcon);
    }

    std::map<SceneNode *, std::shared_ptr<GLSceneNode>>::iterator it;
    std::list<GLSceneNode *> drawable;
    for (it = node_renderers_.begin(); it != node_renderers_.end(); it++) {
        GLSceneNode &tile = *it->second;
        if (updateLocation) {
            tile.setLocation(*locationUpdate.location, locationUpdate.localFrame.get(), locationUpdate.srid, locationUpdate.altitudeMode);
        }

        // XXX - more efficient testing

        // test in view
        if (renderPass&(GLMapView2::Sprites|GLMapView2::XRay)) {
#if 1
            GLSceneNode::RenderVisibility renderable = tile.isRenderable(view);
#else
            GLSceneNode::RenderVisibility renderable = GLSceneNode::Draw;
#endif
            if (renderable == GLSceneNode::RenderVisibility::None) {
                this->loader_->cancel(tile);
                if (tile.hasLODs())
                    tile.unloadLODs();
                else
                    tile.release();
            } else {
                const bool prefetch = (renderable == GLSceneNode::RenderVisibility::Prefetch);

                if (!tile.isLoaded(view)) {
                    bool queued;
                    if ((loader_->isQueued(&queued, tile, prefetch) == TE_Ok) && !queued) {
                        GLSceneNode::LoadContext loadContext;
                        if (tile.prepareLoadContext(&loadContext, view) == TE_Ok)
                            loader_->enqueue(it->second, std::move(loadContext), prefetch);
                    }
                } else {
                    // if any tiles are drawing, don't draw indicator
                    drawIndicator = false;
                }

                // draw
                if (!prefetch)
                    drawable.push_back(&tile);
            }
        } else {
            // if doing other-than-sprites pass, indicator is rendered if no tiles are loaded
            drawIndicator &= !tile.isLoaded(view);
        }
    }

    // RAII construct to restore render state
    class RenderStateRestore
    {
    public :
        RenderStateRestore() NOTHROWS : _state(RenderState_getCurrent())
        {}
        ~RenderStateRestore() NOTHROWS { reset(); }
    public :
        /** resets to initial state */
        void reset() NOTHROWS { RenderState_makeCurrent(_state); }
    private :
        RenderState _state;
    };
    RenderStateRestore restore;

    RenderState state = RenderState_getCurrent();

    // xray draw
    if(xray_color_ && (renderPass&GLMapView2::XRay)) {
        // only draw samples below surface
        if (!state.depth.enabled) {
            state.depth.enabled = true;
            glEnable(GL_DEPTH_TEST);
        }
        if (state.depth.mask) {
            state.depth.mask = GL_FALSE;
            glDepthMask(state.depth.mask);
        }
        if (state.depth.func != GL_GREATER) {
            state.depth.func = GL_GREATER;
            glDepthFunc(state.depth.func);
        }

        for (auto it_drawables = drawable.begin(); it_drawables != drawable.end(); it_drawables++) {
            (*it_drawables)->setColor(ColorControl::Replace, xray_color_);
            (*it_drawables)->draw(view, state, ((renderPass|GLMapView2::Sprites)&~GLMapView2::XRay));
        }
    }
    if (renderPass&(GLMapView2::Surface | GLMapView2::Sprites)) {
        //  regular draw
        if (!state.depth.enabled) {
            state.depth.enabled = true;
            glEnable(GL_DEPTH_TEST);
        }
        if (!state.depth.mask) {
            state.depth.mask = GL_TRUE;
            glDepthMask(state.depth.mask);
        }
        if (state.depth.func != GL_LEQUAL) {
            state.depth.func = GL_LEQUAL;
            glDepthFunc(state.depth.func);
        }
        for (auto it_drawables = drawable.begin(); it_drawables != drawable.end(); it_drawables++) {
            (*it_drawables)->setColor(ColorControl::Modulate, 0xFFFFFFFFu);
            (*it_drawables)->draw(view, state, renderPass);
        }
        if (state.shader.get()) {
            for (std::size_t i = state.shader->numAttribs; i > 0u; i--)
                glDisableVertexAttribArray(static_cast<GLuint>(i - 1u));
        }

        restore.reset();
        if(drawIndicator)
            indicator.draw(view, renderPass);
    }
}
void GLScene::release() NOTHROWS
{
    // stop initializer thread if running
    do {
        {
            Lock lock(mutex_);
            if (!initializer.get())
                break;

            // signal to thread to complete
            this->cancelInitialize = true;
            // unlock
        }

        // wait for join
        initializer.reset();
    } while (false);

    // stop all loading
    if (loader_.get()) {
        loader_->cancelAll();
        loader_.reset();
    }

    // clear renderers
    if(!node_renderers_.empty()) {
        std::map<SceneNode *, std::shared_ptr<GLSceneNode>>::iterator it;
        for (it = node_renderers_.begin(); it != node_renderers_.end(); it++)
            it->second->release();
        node_renderers_.clear();
    }

    // clear nodes
    display_nodes_.clear();

    // clear scene
    scene_.reset();

    indicator.release();
}

TAKErr GLScene::hitTest(TAK::Engine::Core::GeoPoint2 *value, const TAK::Engine::Core::MapSceneModel2 &scene, const float x, const float y) NOTHROWS
{
#if PROFILE_HIT_TESTS
    auto profile_start = std::chrono::high_resolution_clock::now();
#endif

    TAKErr code(TE_Done);

    if (!value)
        return TE_InvalidArg;

#if 1 // Flip this to compare CPU hit tests
    GeoPoint2 hitGeo;
    TAKErr awaitCode = Task_begin(GLWorkers_glThread(), depthTestTask, this, scene, x, y)
        .await(hitGeo, code);
    
    if (awaitCode != TE_Ok)
        return awaitCode;

    if (code == TE_Ok)
        *value = hitGeo;
#else
    code = TE_Unsupported;
#endif

    // if depth method unsupported, fall back on pure CPU method (perhaps some really janky GPU)
    if (code == TE_Unsupported) {
        std::vector<std::shared_ptr<GLSceneNode>> hitTestNodes;
        {
            Lock lock(mutex_);
            TAKErr lockCode = lock.status;
            TE_CHECKRETURN_CODE(lockCode);

            //XXX-- compatibleSrid check

            auto it = this->node_renderers_.begin();
            auto end = this->node_renderers_.end();

            hitTestNodes.reserve(this->node_renderers_.size());
            while (it != end) {
                hitTestNodes.push_back(it->second);
                ++it;
            }
        }

        auto it = hitTestNodes.begin();
        auto end = hitTestNodes.end();

        bool candidate = false;
        double candDist2 = NAN;
        while (it != end) {
            // XXX - AABB pre-screen

            // test intersect on the mesh
            GeoPoint2 isect;
            code = (*it)->hitTest(&isect, scene, x, y);
            ++it;
            if (code == TE_Done)
                continue;

            // we had an intersect, compare it with the best candidate
            TAK::Engine::Math::Point2<double> hit;
            if (TE_Ok == scene.forward(&hit, isect)) {
                // XXX - we should check to see if 'z' is outside of near/far clip planes

                if (!candidate || hit.z < candDist2) {
                    candDist2 = hit.z;

                    // candidate identified
                    candidate = true;

                    // record the intersection
                    *value = isect;
                }
            }
        }

        // set code for return
        code = candidate ? TE_Ok : TE_Done;
    }

#if PROFILE_HIT_TESTS
    auto profile_end = std::chrono::high_resolution_clock::now();
    auto profile_span = profile_end - profile_start;
    auto millis = std::chrono::duration_cast<std::chrono::milliseconds>(profile_span).count();
    TAK::Engine::Util::Logger_log(TAK::Engine::Util::LogLevel::TELL_Debug, "GLScene: hitTest: millis=%lld", millis);
#endif

    return code;
}


TAKErr GLScene::depthTestTask(TAK::Engine::Core::GeoPoint2& value, GLScene* scene, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS {

    TAKErr code = TE_Done;

    // Used only by the glThread and therefore statically cached for reuse, but as depth sampling grows in scope
    // in the renderer, should be moved to a shared place.
    static GLDepthSamplerPtr depth_sampler_(nullptr, nullptr);

    if (!depth_sampler_) {
        // For now, use (ENCODED_DEPTH_ENCODED_ID) method, since seems to get a 16-bit only depth buffer
        // the other way
        code = GLDepthSampler_create(depth_sampler_, scene->ctx_, GLDepthSampler::ENCODED_DEPTH_ENCODED_ID);
        if (code != TE_Ok)
            return code;
    }

    float screenY = y;
    double pointZ = 0.0;
    Matrix2 projection;

    code = depth_sampler_->performDepthSample(&pointZ, &projection, nullptr, *scene, sceneModel, x, screenY);
    if (code != TE_Ok)
        return code;

    TAK::Engine::Math::Point2<double> point(x, screenY, 0);
    projection.transform(&point, point);
    point.z = pointZ;

    Matrix2 mat;
    if (projection.createInverse(&mat) != TE_Ok)
        return TE_Done;

    mat.transform(&point, point);

    // ortho -> projection
    sceneModel.inverseTransform.transform(&point, point);
    // projection -> LLA
    code = sceneModel.projection->inverse(&value, point);

    return code;
}

TAKErr GLScene::gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS {

    TAKErr code(TE_Ok);

    auto it = node_renderers_.begin();
    auto end = node_renderers_.end();

    if (levelDepth == 0) {
        while (it != end) {
            // XXX - AABB pre-screen

            result.push_back(it->second.get());
            ++it;
        }
    } else {
        while (it != end) {
            // XXX - AABB pre-screen

            code = it->second->gatherDepthSamplerDrawables(result, levelDepth - 1, sceneModel, x, y);
            if (code != TE_Ok)
                break;
            ++it;
        }
    }

    return code;
}

void GLScene::depthSamplerDraw(TAK::Engine::Renderer::GLDepthSampler& sampler, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS {
    auto it = node_renderers_.begin();
    auto end = node_renderers_.end();

    while (it != end) {
        // XXX - AABB pre-screen

        it->second->depthSamplerDraw(sampler, sceneModel);
        ++it;
    }
}

TAKErr GLScene::getControl(void **ctrl, const char *type) NOTHROWS
{
    if (!type)
        return TE_InvalidArg;
    auto entry = controls_.find(type);
    if (entry == controls_.end())
        return TE_InvalidArg;
    *ctrl = entry->second;
    return TE_Ok;
}

TAKErr processCB(void *opaque, int current, int max) NOTHROWS
{
    static_cast<GLContentIndicator *>(opaque)->showProgress((int)((double)current / (double)max*100.0));
    return TE_Ok;
}

void *GLScene::initializeThread(void *opaque)
{
    auto *glscene = static_cast<GLScene *>(opaque);

    do {
        TAKErr code(TE_Ok);
        ScenePtr scene(nullptr, nullptr);

        // XXX - install progress callback and send updates to indicator
        ProcessingCallback cb;
        cb.progress = processCB;
        cb.cancelToken = &glscene->cancelInitialize;
        cb.opaque = &glscene->indicator;

        // load scene
        do {
            // look for optimized
            TAK::Engine::Port::String optimizedPath;
            if (glscene->opts_.cacheDir) {
                std::ostringstream strm;
                strm << glscene->opts_.cacheDir << TAK::Engine::Port::Platform_pathSep() << "optimized.tbsg";
                optimizedPath = strm.str().c_str();
            }
            bool optimizedExists;
            if (IO_exists(&optimizedExists, optimizedPath) == TE_Ok && optimizedExists) {
                code = SceneFactory_decode(scene, optimizedPath, true);
                if (code == TE_Ok)
                    break;
            }

            // no optimized, create
            code = SceneFactory_create(scene, glscene->info_.uri, nullptr, &cb, glscene->info_.resourceAliases.get());
            TE_CHECKBREAK_CODE(code);

            // if not direct, store optimized version
            if (!(scene->getProperties()&Scene::DirectSceneGraph) && optimizedPath) {
                TAK::Engine::Port::String resourceDir;
                if (IO_getParentFile(resourceDir, optimizedPath) != TE_Ok)
                    break;
                IO_mkdirs(resourceDir);
                SceneFactory_encode(optimizedPath, *scene); 
            }
        } while (false);
        glscene->indicator.clearProgress();
        if (code != TE_Ok) {
            TAK::Engine::Port::String modelIcon;
            if (!glscene->cancelInitialize && ConfigOptions_getOption(modelIcon, "TAK.Engine.Model.default-error-icon") == TE_Ok)
                glscene->indicator.setIcon(*glscene->info_.location, modelIcon);
        }
        TE_CHECKBREAK_CODE(code);

        // build out nodes
        std::list<std::shared_ptr<SceneNode>> meshNodes;
        SceneNodePtr root(&scene->getRootNode(), Memory_leaker_const<SceneNode>);
        buildNodeList(meshNodes, scene->getRootNode());
        if(root->hasMesh())
            meshNodes.push_back(std::shared_ptr<SceneNode>(std::move(root)));

        std::shared_ptr<MaterialManager> matmgr(new MaterialManager(glscene->ctx_));

        // create renderers
        std::map<SceneNode *, std::shared_ptr<GLSceneNode>> renderers;
        std::list<std::shared_ptr<SceneNode>>::iterator it;
        for (it = meshNodes.begin(); it != meshNodes.end(); it++)
            renderers[(*it).get()] = std::shared_ptr<GLSceneNode>(new GLSceneNode(glscene->ctx_, *it, glscene->info_, matmgr));

        struct IndicatorUpdate
        {
            GLScene *glscene {nullptr};
            Envelope2 aabb_lcs;
            Envelope2 aabb_wgs84;

            static void run(void *opaque) NOTHROWS
            {
                auto *arg = static_cast<IndicatorUpdate *>(opaque);
                double pts[10] = 
                {
                    arg->aabb_wgs84.minX, arg->aabb_wgs84.minY,
                    arg->aabb_wgs84.minX, arg->aabb_wgs84.maxY,
                    arg->aabb_wgs84.maxX, arg->aabb_wgs84.maxY,
                    arg->aabb_wgs84.maxX, arg->aabb_wgs84.minY,
                    arg->aabb_wgs84.minX, arg->aabb_wgs84.minY,
                };
                LineString2 ls;
                ls.addPoints(pts, 5u, 2u);

                if (arg->glscene->info_.location.get()) {
                    TAK::Engine::Port::String modelIcon;
                    if (ConfigOptions_getOption(modelIcon, "TAK.Engine.Model.default-icon") != TE_Ok)
                        modelIcon = "null";
                    arg->glscene->indicator.setIcon(*arg->glscene->info_.location, modelIcon);
                } else {
                    arg->glscene->indicator.clearIcon();
                }
                arg->glscene->indicator.setBounds(ls, atakmap::feature::BasicStrokeStyle(0xFF00FF00, 1.0), true);
            }
        };

        std::unique_ptr<IndicatorUpdate> update(new IndicatorUpdate());
        update->glscene = glscene;

        MeshTransformOptions aabb_src;
        aabb_src.srid = glscene->info_.srid;
        aabb_src.localFrame = Matrix2Ptr(new Matrix2(*glscene->info_.localFrame), Memory_deleter_const<Matrix2>);
        
        update->aabb_lcs = scene->getAABB();

        MeshTransformOptions aabb_dst;
        aabb_dst.srid = 4326;

        Mesh_transform(&update->aabb_wgs84, scene->getAABB(), aabb_src, aabb_dst);

        // set 'glscene' members to initialize outputs
        bool needSourceBounds;
        {
            Lock lock(glscene->mutex_);
            code = lock.status;

            glscene->scene_ = std::move(scene);
            glscene->display_nodes_ = meshNodes;
            glscene->node_renderers_ = renderers;

            needSourceBounds = !glscene->info_.aabb.get();
            if (needSourceBounds)
                glscene->info_.aabb = Envelope2Ptr(new Envelope2(update->aabb_wgs84), Memory_deleter_const<Envelope2>);

            // XXX - 
            // initializer thread is done, detach and reset the pointer
            //glscene->initializer->detach();
            //glscene->initializer.reset();

            if (glscene->clampRequested)
                glscene->sceneCtrl->clampToGround();

        }

        // update the bounds on the source, if not previously populated
        if(needSourceBounds)
            glscene->sceneCtrl->dispatchBoundsChanged(update->aabb_lcs, glscene->info_.minDisplayResolution, glscene->info_.maxDisplayResolution);

        // push to GL thread
        std::unique_ptr<void, void(*)(const void *)> glarg(update.release(), Memory_void_deleter_const<IndicatorUpdate>);
        glscene->ctx_.queueEvent(IndicatorUpdate::run, std::move(glarg));

        return nullptr;
    } while (false);

    // XXX - set indicator error
    // leave initializer thread intact to prevent reinitialization

    return nullptr;
}

GLScene::SceneControlImpl::SceneControlImpl(GLScene &owner) NOTHROWS :
    owner_(owner)
{}
TAKErr GLScene::SceneControlImpl::setLocation(const GeoPoint2 &location, const Matrix2 *localFrame, const int srid, const AltitudeMode altitudeMode) NOTHROWS
{
    TAKErr code(TE_Ok);
    struct IndicatorUpdate
    {
        GLScene *glscene {nullptr};
        Envelope2 aabb_lcs;
        Envelope2 aabb_wgs84;

        static void run(void *opaque) NOTHROWS
        {
            auto *arg = static_cast<IndicatorUpdate *>(opaque);
            double pts[10] =
            {
                arg->aabb_wgs84.minX, arg->aabb_wgs84.minY,
                arg->aabb_wgs84.minX, arg->aabb_wgs84.maxY,
                arg->aabb_wgs84.maxX, arg->aabb_wgs84.maxY,
                arg->aabb_wgs84.maxX, arg->aabb_wgs84.minY,
                arg->aabb_wgs84.minX, arg->aabb_wgs84.minY,
            };
            LineString2 ls;
            ls.addPoints(pts, 5u, 2u);

            if (arg->glscene->info_.location.get()) {
                TAK::Engine::Port::String modelIcon;
                if (ConfigOptions_getOption(modelIcon, "TAK.Engine.Model.default-icon") != TE_Ok)
                    modelIcon = "null";
                arg->glscene->indicator.setIcon(*arg->glscene->info_.location, modelIcon);
            }
            else {
                arg->glscene->indicator.clearIcon();
            }
            arg->glscene->indicator.setBounds(ls, atakmap::feature::BasicStrokeStyle(0xFF00FF00, 1.0), true);
        }
    };
    std::unique_ptr<IndicatorUpdate> update;
    {
        Lock lock(owner_.mutex_);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);

        if (srid == owner_.info_.srid &&
            owner_.info_.location->latitude == location.latitude &&
            owner_.info_.location->longitude == location.longitude &&
            owner_.info_.location->altitude == location.altitude &&
            owner_.info_.location->altitudeRef == location.altitudeRef &&
            owner_.info_.altitudeMode == altitudeMode &&
            ((localFrame && owner_.info_.localFrame.get() &&
                *localFrame == *owner_.info_.localFrame) ||
                (!localFrame && !owner_.info_.localFrame.get()))) {

            return TE_Ok;
        }
        owner_.info_.srid = srid;
        owner_.info_.location = GeoPoint2Ptr(new GeoPoint2(location), Memory_deleter_const<GeoPoint2>);
        owner_.info_.altitudeMode = altitudeMode;

        if (owner_.info_.altitudeMode == TEAM_ClampToGround)
            return clampToGround();

        if (localFrame)
            owner_.info_.localFrame = Matrix2Ptr(new Matrix2(*localFrame), Memory_deleter_const<Matrix2>);
        else
            owner_.info_.localFrame.reset();

        // if the renderers are already initalized, need to update them
        owner_.location_dirty_ = true;
        
        // recompute the indicator bounds
        if (owner_.scene_.get()) {
            update.reset(new IndicatorUpdate());
            MeshTransformOptions aabb_src;
            aabb_src.srid = owner_.info_.srid;
            aabb_src.localFrame = Matrix2Ptr(new Matrix2(*owner_.info_.localFrame), Memory_deleter_const<Matrix2>);
            update->aabb_lcs = owner_.scene_->getAABB();

            MeshTransformOptions aabb_dst;
            aabb_dst.srid = 4326;

            if (Mesh_transform(&update->aabb_wgs84, update->aabb_lcs, aabb_src, aabb_dst) == TE_Ok)
                update->glscene = &owner_;
            else
                update.reset();


        }
    }

    if (update.get()) {
        // push to GL thread
        owner_.ctx_.queueEvent(IndicatorUpdate::run, std::move(std::unique_ptr<void, void(*)(const void *)>(update.release(), Memory_void_deleter_const<IndicatorUpdate>)));
    }

    return code;
}
TAKErr GLScene::SceneControlImpl::getInfo(SceneInfo *info) NOTHROWS
{
    if(!info)
        return TE_InvalidArg;
    info = &owner_.info_;
    return TE_Ok;
}
TAKErr GLScene::SceneControlImpl::addUpdateListener(UpdateListener *l) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    listeners_.insert(l);
    return code;
}
TAKErr GLScene::SceneControlImpl::removeUpdateListener(const UpdateListener &l) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    listeners_.erase(&const_cast<UpdateListener &>(l));
    return code;
}
TAKErr GLScene::SceneControlImpl::clampToGround() NOTHROWS
{
    TAKErr code(TE_Ok);
    // acquire lock
    Lock lock(owner_.mutex_);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    
    // if initialization is not completed,  flag clamp request
    if (!owner_.scene_.get()) {
        owner_.clampRequested = true;
        return code;
    }

    // iterate nodes and find anchor point on lowest LOD meshes
    TAK::Engine::Math::Point2<double> sceneMinLCS(NAN, NAN, NAN);
    for (auto entry = owner_.node_renderers_.begin(); entry != owner_.node_renderers_.end(); entry++) {
        SceneNode &node = *entry->second->subject;
        const std::size_t numLods = node.getNumLODs();
        for (std::size_t i = 0; i < numLods; i++) {
            std::shared_ptr<const Mesh> mesh;
            if (node.loadMesh(mesh, i) != TE_Ok)
                continue;

            const std::size_t numVerts = mesh->getNumVertices();
            if (!numVerts)
                continue;

            const Envelope2 &aabb = mesh->getAABB();
            TAK::Engine::Math::Point2<double> meshMin((aabb.minX+aabb.maxX)/2.0, (aabb.minY+aabb.maxY)/2.0, aabb.minZ);
            
            // apply mesh transform to mesh min to convert to scene LCS
            if (node.getLocalFrame() && node.getLocalFrame()->transform(&meshMin, meshMin) != TE_Ok)
                continue;

            // compare with current LCS reference
            if (isnan(sceneMinLCS.z) || meshMin.z < sceneMinLCS.z)
                sceneMinLCS = meshMin;
        }
    }

    if (isnan(sceneMinLCS.z))
        return TE_Ok;

    TAK::Engine::Math::Point2<double> sceneOriginWCS(0.0, 0.0, 0.0);
    if (owner_.info_.localFrame.get()) {
        code = owner_.info_.localFrame->transform(&sceneOriginWCS, sceneOriginWCS);
        TE_CHECKRETURN_CODE(code);
    }

    // XXX - this is a little crude, but we are assuming that the floor of the
    //       AABB is on the surface

    // first, subtract off origin WCS to reset WCS origin to 0AGL, then subtract off the scene LCS AABB floor to reset WCS floor to 0AGL
    owner_.sceneCtrl->dispatchClampToGroundOffsetComputed(-sceneOriginWCS.z-sceneMinLCS.z);
    return code;
}
void GLScene::SceneControlImpl::dispatchBoundsChanged(const Envelope2 &aabb, const double minGsd, const double maxGsd) NOTHROWS
{
    Lock lock(mutex_);
    if (lock.status != TE_Ok)
        return;

    auto it = listeners_.begin();
    while (it != listeners_.end()) {
        if ((*it)->onBoundsChanged(aabb, minGsd, maxGsd) == TE_Done)
            it = listeners_.erase(it);
        else
            it++;
    }
}
void GLScene::SceneControlImpl::dispatchClampToGroundOffsetComputed(const double offset) NOTHROWS
{
    Lock lock(mutex_);
    if (lock.status != TE_Ok)
        return;

    auto it = listeners_.begin();
    while (it != listeners_.end()) {
        if ((*it)->onClampToGroundOffsetComputed(offset) == TE_Done)
            it = listeners_.erase(it);
        else
            it++;
    }
}
