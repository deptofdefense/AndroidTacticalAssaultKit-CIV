#include "renderer/GL.h"

#include "renderer/model/GLProgressiveScene.h"

#include "renderer/model/GLMesh.h"
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
#include "renderer/GLWorkers.h"
#include "renderer/model/GLSceneFactory.h"
#include "math/Point2.h"
#include "math/Rectangle.h"
#include "util/DataInput2.h"
#include "renderer/BitmapFactory2.h"

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
using namespace TAK::Engine::Math;


namespace
{
    SharedWorkerPtr createSceneLoadWorker() {
        SharedWorkerPtr worker;
        Worker_createThreadPool(worker, 0, 4, 60 * 1000);
        return worker;
    }

    SharedWorkerPtr& sceneLoadWorker() NOTHROWS {
#if 0
        return GeneralWorkers_flex();
#else
        static SharedWorkerPtr inst(createSceneLoadWorker());
        return inst;
#endif
    }

    SharedWorkerPtr& sceneTextureLoadWorker() NOTHROWS {
#if 0
        return GeneralWorkers_flex();
#else
        static SharedWorkerPtr inst(createSceneLoadWorker());
        return inst;
#endif
    }

    class MeshBufferTextureLoader : public MaterialManager::TextureLoader {
    public:
        MeshBufferTextureLoader(const std::shared_ptr<const Mesh> &mesh) NOTHROWS;
        ~MeshBufferTextureLoader() NOTHROWS override;
        TAKErr load(TAK::Engine::Util::FutureTask<std::shared_ptr<TAK::Engine::Renderer::Bitmap2>>& value, const char* uri) NOTHROWS override;
    private:
        std::shared_ptr<const Mesh> mesh;
    };
}

GLProgressiveScene::Spi::~Spi() NOTHROWS {

}

TAKErr GLProgressiveScene::Spi::create(GLMapRenderable2Ptr& value, RenderContext& ctx, const SceneInfo& info, const Options& opts) NOTHROWS {
    TAKErr code = TE_Unsupported;
    if (info.type != nullptr && strcmp(info.type, "Cesium3DTiles") == 0) {
        value = GLMapRenderable2Ptr(new GLProgressiveScene(ctx, info, opts), Memory_deleter_const<GLMapRenderable2, GLProgressiveScene>);
        code = TE_Ok;
    }
    return code;
}

class GLProgressiveScene::SceneControlImpl : public SceneObjectControl
{
public :
    SceneControlImpl(GLProgressiveScene &owner) NOTHROWS;
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
    GLProgressiveScene &owner;
    std::set<UpdateListener *> listeners;
    Mutex mutex;
};

GLProgressiveScene::GLProgressiveScene(RenderContext &ctx_, const SceneInfo &info_, const GLSceneSpi::Options &opts_) NOTHROWS :
    sceneState(std::make_shared<SceneState>(info_, ctx_, opts_)),
    locationDirty(false),
    xray_color(0x3F07FFFFu)
{
    sceneCtrl.reset(new SceneControlImpl(*this));

    controls["TAK.Engine.Renderer.Model.SceneObjectControl"] = (void *)static_cast<SceneObjectControl *>(sceneCtrl.get());

    if (sceneState->indicator) {
        TAK::Engine::Port::String modelIcon;
        if (ConfigOptions_getOption(modelIcon, "TAK.Engine.Model.default-icon") != TE_Ok)
            modelIcon = "null";
        sceneState->indicator->setIcon(*sceneState->info.location, modelIcon);
    }

    if (sceneState->opts.materialManager == nullptr)
        sceneState->opts.materialManager = std::make_shared<MaterialManager>(ctx_);
}

//
//
//

GLProgressiveScene::SceneState::~SceneState() NOTHROWS {
}

//
// GLProgressiveScene
//

GLProgressiveScene::~GLProgressiveScene() NOTHROWS {
    if (this->sceneState) {
        this->sceneState->pendingDrawState.cancel();
        this->sceneState->pendingScene.cancel();
    }
}

void GLProgressiveScene::start() NOTHROWS
{}
void GLProgressiveScene::stop() NOTHROWS
{}
int GLProgressiveScene::getRenderPass() NOTHROWS
{
    return GLMapView2::Sprites | /*GLMapView2::XRay | */
        (sceneState->indicator ? sceneState->indicator->getRenderPass() : 0);
}

struct DrawableVisitorArgs {
    GLProgressiveScene* scene;
    std::list<GLSceneNode*> &drawable;
    bool &drawIndicator;
};

bool needsDrawStateUpdate(const GLMapView2& view, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS {
    return sceneModel.camera.location != view.scene.camera.location ||
        sceneModel.camera.near != view.scene.camera.near ||
        sceneModel.camera.far != view.scene.camera.far;
}
bool wildyOffDrawState(const GLMapView2& view, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS {
    //TODO--
    return false;
}

TAKErr GLProgressiveScene::loadScene(std::shared_ptr<Scene> &result, const TAK::Engine::Model::SceneInfo& info) NOTHROWS {
    ScenePtr scene(nullptr, nullptr);
    TAK::Engine::Util::Logger_log(TAK::Engine::Util::LogLevel::TELL_Debug, "loading subscene: %s", info.uri.get());
    TAKErr code = SceneFactory_create(scene, info.uri, info.type, nullptr, nullptr);
    if (code == TE_Ok)
        result = std::shared_ptr<Scene>(scene.release(), scene.get_deleter());
    return code;
}
TAKErr GLProgressiveScene::setScene(const std::shared_ptr<Scene> &scene, const std::shared_ptr<SceneState>& sceneState) NOTHROWS {
    sceneState->scene = scene;
    sceneState->pendingScene.detach();
    
    return TE_Ok;
}

TAKErr calculateSceneBoundsWGS84(Envelope2& bounds, const std::shared_ptr<TAK::Engine::Model::Scene>& scene, const SceneInfo& info) NOTHROWS {
    MeshTransformOptions aabb_src;
    aabb_src.srid = info.srid;
    //aabb_src.localFrame = Matrix2Ptr(new Matrix2(*info.localFrame), Memory_deleter_const<Matrix2>);

    MeshTransformOptions aabb_dst;
    aabb_dst.srid = 4326;

    return Mesh_transform(&bounds, info.aabb ? *info.aabb : scene->getAABB(), aabb_src, aabb_dst);
}
TAKErr GLProgressiveScene::updateIndicatorBounds(const Envelope2& bounds, const std::shared_ptr<SceneState>& sceneState) NOTHROWS {
    double pts[10] =
    {
        bounds.minX, bounds.minY,
        bounds.minX, bounds.maxY,
        bounds.maxX, bounds.maxY,
        bounds.maxX, bounds.minY,
        bounds.minX, bounds.minY,
    };
    LineString2 ls;
    ls.addPoints(pts, 5u, 2u);
    if (sceneState->indicator)
        sceneState->indicator->setBounds(ls, atakmap::feature::BasicStrokeStyle(0xFF00FF00, 1.0), true);
    return TE_Ok;
}

TAKErr GLProgressiveScene::buildDrawState(std::shared_ptr<DrawState2>& result,
    const std::shared_ptr<SceneState>& sceneState,
    const ViewState& viewState,
    TAK::Engine::Model::SceneNode* subject) NOTHROWS {

    TAKErr code(TE_Ok);
    result = std::make_shared<DrawState2>(sceneState->drawState->pump + 1);

    Matrix2 transform;
    if (sceneState->info.localFrame)
        transform = *sceneState->info.localFrame;
    if (subject->getLocalFrame())
        transform.concatenate(*subject->getLocalFrame());

    if (subject->hasChildren()) {
        Collection<std::shared_ptr<SceneNode>>::IteratorPtr iter(nullptr, nullptr);
        code = subject->getChildren(iter);
        TE_CHECKRETURN_CODE(code);

        do {
            std::shared_ptr<SceneNode> child;
            code = iter->get(child);
            TE_CHECKBREAK_CODE(code);

            buildDrawStateChild(result, sceneState, viewState, child, &transform);

            code = iter->next();
            TE_CHECKBREAK_CODE(code);
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr MapSceneModel2_intersectsECEF(bool* value, const MapSceneModel2& scene, 
    const double mbbMinX_, const double mbbMinY_, const double mbbMinZ_, 
    const double mbbMaxX_, const double mbbMaxY_, const double mbbMaxZ_) NOTHROWS
{
    Matrix2 xform(scene.forwardTransform);

    Envelope2 mbb(mbbMinX_, mbbMinY_, mbbMinZ_, mbbMaxX_, mbbMaxY_, mbbMaxZ_);

    double minX;
    double minY;
    double minZ;
    double maxX;
    double maxY;
    double maxZ;

    // transform the MBB to the native projection
    TAK::Engine::Math::Point2<double> points[8];
    points[0] = TAK::Engine::Math::Point2<double>(mbb.minX, mbb.minY, mbb.minZ);
    points[1] = TAK::Engine::Math::Point2<double>(mbb.minX, mbb.maxY, mbb.minZ);
    points[2] = TAK::Engine::Math::Point2<double>(mbb.maxX, mbb.maxY, mbb.minZ);
    points[3] = TAK::Engine::Math::Point2<double>(mbb.maxX, mbb.minY, mbb.minZ);
    points[4] = TAK::Engine::Math::Point2<double>(mbb.minX, mbb.minY, mbb.maxZ);
    points[5] = TAK::Engine::Math::Point2<double>(mbb.minX, mbb.maxY, mbb.maxZ);
    points[6] = TAK::Engine::Math::Point2<double>(mbb.maxX, mbb.maxY, mbb.maxZ);
    points[7] = TAK::Engine::Math::Point2<double>(mbb.maxX, mbb.minY, mbb.maxZ);

    std::size_t idx = 0u;
    for (; idx < 8u; idx++) {
        TAK::Engine::Math::Point2<double> scratch;
        if (xform.transform(&scratch, points[idx]) != TE_Ok)
            continue;
        minX = scratch.x;
        minY = scratch.y;
        minZ = scratch.z;
        maxX = scratch.x;
        maxY = scratch.y;
        maxZ = scratch.z;
        break;
    }
    if (idx == 8u) {
        // XXX - think this is actually error condition
        *value = false;
        return TE_Ok;
    }
    for (; idx < 8u; idx++) {
        TAK::Engine::Math::Point2<double> scratch;
        if (xform.transform(&scratch, points[idx]) != TE_Ok)
            continue;
        if (scratch.x < minX)        minX = scratch.x;
        else if (scratch.x > maxX)   maxX = scratch.x;
        if (scratch.y < minY)        minY = scratch.y;
        else if (scratch.y > maxY)   maxY = scratch.y;
        if (scratch.z < minZ)        minZ = scratch.z;
        else if (scratch.z > maxZ)   maxZ = scratch.z;
    }

#if 1
    // XXX - observing intersect failure for equirectangular with perspective camera on Y-axis
    if (scene.projection->getSpatialReferenceID() == 4326 && scene.camera.mode == MapCamera2::Perspective)
        *value = atakmap::math::Rectangle<double>::intersects(0.0, 
            0.0, 
            static_cast<double>(scene.width), 
            static_cast<double>(scene.height), 
            minX, 
            0.0, 
            maxX, 
            static_cast<double>(scene.height));
    else
#endif
        * value = atakmap::math::Rectangle<double>::intersects(
            0, 
            0, 
            static_cast<double>(scene.width),
            static_cast<double>(scene.height),
            minX, 
            minY, 
            maxX, 
            maxY);

    return TE_Ok;
}

TAKErr GLProgressiveScene::buildDrawStateChild(std::shared_ptr<DrawState2>& result,
    const std::shared_ptr<SceneState>& sceneState,
    const ViewState& viewState,
    const std::shared_ptr<TAK::Engine::Model::SceneNode>& subject,
    const Matrix2 *transform) NOTHROWS {

    TAKErr code(TE_Ok);

    bool intersects;
    Envelope2 mbb = subject->getAABB();

    if (sceneState->info.srid == 4978) {
        if (transform) {
            TAK::Engine::Math::Point2<double> min(mbb.minX, mbb.minY, mbb.minZ);
            TAK::Engine::Math::Point2<double> max(mbb.maxX, mbb.maxY, mbb.maxZ);
            transform->transform(&min, min);
            transform->transform(&max, max);
            mbb.minX = min.x; mbb.minY = min.y; mbb.minZ = min.z;
            mbb.maxX = max.x; mbb.maxY = max.y; mbb.maxZ = max.z;
        }

        if (MapSceneModel2_intersectsECEF(&intersects, viewState.sceneModel, mbb.minX, mbb.minY, mbb.minZ, mbb.maxX, mbb.maxY, mbb.maxZ) != TE_Ok || !intersects)
            return TE_Done;
    } else if (MapSceneModel2_intersects(&intersects, viewState.sceneModel, mbb.minX, mbb.minY, mbb.minZ, mbb.maxX, mbb.maxY, mbb.maxZ) != TE_Ok || !intersects) {
        return TE_Done;
    }

    if ((subject->hasMesh() || subject->hasSubscene())) {
        auto it = sceneState->drawState->nodeMap.find(subject.get());
        if (it != sceneState->drawState->nodeMap.end()) {
            result->nodeMap.insert(std::make_pair(subject.get(), std::make_pair(result->pump, it->second.second)));
            result->drawList.push_back(it->second.second);
        } else if (subject->hasMesh()) {

            Matrix2 meshTransform = *transform;
            if (subject->getLocalFrame())
                meshTransform.concatenate(*subject->getLocalFrame());

            std::shared_ptr<const Mesh> meshPtr;
            subject->loadMesh(meshPtr);
            Math::Point2<double> anchor;

            MaterialManager::TextureLoaderPtr meshTextureLoader(new MeshBufferTextureLoader(meshPtr), Memory_deleter_const<MaterialManager::TextureLoader, MeshBufferTextureLoader>);
            std::shared_ptr<MaterialManager> meshMatManager(std::make_shared<MaterialManager>(sceneState->ctx, std::move(meshTextureLoader), *sceneState->opts.materialManager));
            std::shared_ptr<GLMesh> item(new GLMesh(sceneState->ctx, &meshTransform, TEAM_ClampToGround, meshPtr, anchor, meshMatManager));
            item->srid_ = sceneState->info.srid;
            
            result->nodeMap.insert(std::make_pair(subject.get(), std::make_pair(result->pump, item)));
            result->drawList.push_back(item);
        } else {
            const SceneInfo* info = nullptr;
            code = subject->getSubsceneInfo(&info);
            TE_CHECKRETURN_CODE(code);
            GLMapRenderable2Ptr glScenePtr(nullptr, nullptr);
            GLSceneSpi::Options opts = sceneState->opts;
 //           opts.showIndicator = false;
            opts.showIndicator = true;
            code = GLSceneFactory_create(glScenePtr, sceneState->ctx, *info, opts);
            TE_CHECKRETURN_CODE(code);
            std::shared_ptr<GLMapRenderable2> item(glScenePtr.release(), glScenePtr.get_deleter());
            result->nodeMap.insert(std::make_pair(subject.get(), std::make_pair(result->pump, item)));
            result->drawList.push_back(item);
        }
    }

    if (subject->hasChildren()) {
        Collection<std::shared_ptr<SceneNode>>::IteratorPtr iter(nullptr, nullptr);
        code = subject->getChildren(iter);
        TE_CHECKRETURN_CODE(code);

        do {
            std::shared_ptr<SceneNode> child;
            code = iter->get(child);
            TE_CHECKBREAK_CODE(code);

            Matrix2 childTransform;
            if (transform) {
                childTransform = *transform;
            }
            if (subject->getLocalFrame())
                childTransform.concatenate(*subject->getLocalFrame());

            buildDrawStateChild(result, sceneState, viewState, child, &childTransform);

            code = iter->next();
            TE_CHECKBREAK_CODE(code);
        } while (true);
        if (code == TE_Done)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}

TAKErr GLProgressiveScene::setDrawState(const std::shared_ptr<DrawState2>& drawState, const std::shared_ptr<SceneState>& sceneState) NOTHROWS {
    sceneState->drawState = drawState;
    if (!drawState)
        sceneState->drawState = std::make_shared<DrawState2>(0);
    sceneState->resolved = true;
    sceneState->pendingDrawState.detach();
    return TE_Ok;
}

TAKErr GLProgressiveScene::setSceneLoadError(std::shared_ptr<Scene>& scene, TAKErr err, const std::shared_ptr<SceneState>& sceneState) NOTHROWS {
    sceneState->resolved = true;
    TAK::Engine::Port::String modelIcon;
    if (sceneState->indicator && ConfigOptions_getOption(modelIcon, "TAK.Engine.Model.default-error-icon") == TE_Ok)
        sceneState->indicator->setIcon(*sceneState->info.location, modelIcon);
    return err;
}

void GLProgressiveScene::draw(const GLMapView2 &view, const int renderPass) NOTHROWS
{
    if (!sceneState->scene) {
        if (!sceneState->pendingScene) {
            sceneState->pendingScene = Task_begin(sceneLoadWorker(), loadScene, sceneState->info);
            sceneState->pendingScene
                .thenOn(GeneralWorkers_cpu(), calculateSceneBoundsWGS84, sceneState->info)
                .thenOn(GLWorkers_glThread(), updateIndicatorBounds, this->sceneState);
            sceneState->pendingScene
                .trapOn(GLWorkers_glThread(), setSceneLoadError, this->sceneState)
                .thenOn(GLWorkers_glThread(), setScene, this->sceneState);
        }
    } else if (sceneState->scene && (renderPass & GLMapView2::Sprites) != 0) {
        if (sceneState->pendingDrawState && wildyOffDrawState(view, this->drawViewState.sceneModel)) {
            sceneState->pendingDrawState.cancel();
            sceneState->pendingDrawState.detach();
        }

        if (!sceneState->pendingDrawState && needsDrawStateUpdate(view, this->drawViewState.sceneModel)) {
            drawViewState.sceneModel = view.scene;
            drawViewState.displayResolution = view.drawMapResolution;
            sceneState->pendingDrawState = Task_begin(GeneralWorkers_cpu(), buildDrawState, this->sceneState, this->drawViewState, &sceneState->scene->getRootNode());
            sceneState->pendingDrawState.thenOn(GLWorkers_glThread(), setDrawState, this->sceneState);
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

    std::shared_ptr<DrawState2> drawState = sceneState->drawState;
    if (!drawState)
        return;

    if (renderPass & (GLMapView2::Surface | GLMapView2::Sprites)) {
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
        for (auto it = drawState->drawList.begin(), end = drawState->drawList.end(); it != end; it++) {
            (*it)->draw(view, renderPass);
        }
        if (state.shader.get()) {
            for (size_t i = state.shader->numAttribs; i > 0u; i--)
                glDisableVertexAttribArray(static_cast<GLuint>(i - 1u));
        }

        restore.reset();
        if(sceneState->indicator && !sceneState->resolved)
            sceneState->indicator->draw(view, renderPass);
    }
}
void GLProgressiveScene::release() NOTHROWS
{
    // clear scene
    sceneState->scene.reset();
    sceneState->drawState = std::make_shared<DrawState2>(0);
    if (sceneState->indicator)
        sceneState->indicator->release();
    sceneState->resolved = false;
}
TAKErr GLProgressiveScene::hitTest(TAK::Engine::Core::GeoPoint2 *value, const TAK::Engine::Core::MapSceneModel2 &scene, const float x, const float y) NOTHROWS
{
    TAKErr code(TE_Done);
    //TODO-- depth buffer based GL_SELECT hit testing
    return code;
}
TAKErr GLProgressiveScene::getControl(void **ctrl, const char *type) NOTHROWS
{
    if (!type)
        return TE_InvalidArg;
    auto entry = controls.find(type);
    if (entry == controls.end())
        return TE_InvalidArg;
    *ctrl = entry->second;
    return TE_Ok;
}

namespace {
    TAKErr processCB(void* opaque, int current, int max) NOTHROWS
    {
        static_cast<GLContentIndicator*>(opaque)->showProgress((int)((double)current / (double)max * 100.0));
        return TE_Ok;
    }
}

GLProgressiveScene::SceneControlImpl::SceneControlImpl(GLProgressiveScene &owner_) NOTHROWS :
    owner(owner_)
{}
TAKErr GLProgressiveScene::SceneControlImpl::setLocation(const GeoPoint2 &location, const Matrix2 *localFrame, const int srid, const AltitudeMode altitudeMode) NOTHROWS
{
    TAKErr code(TE_Ok);
#if 0
    struct IndicatorUpdate
    {
        GLProgressiveScene *GLProgressiveScene;
        Envelope2 aabb_lcs;
        Envelope2 aabb_wgs84;

        static void run(void *opaque)
        {
            IndicatorUpdate *arg = static_cast<IndicatorUpdate *>(opaque);
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

            if (arg->GLProgressiveScene->sceneState->info.location.get()) {
                TAK::Engine::Port::String modelIcon;
                if (ConfigOptions_getOption(modelIcon, "TAK.Engine.Model.default-icon") != TE_Ok)
                    modelIcon = "null";
                arg->GLProgressiveScene->indicator->setIcon(*arg->GLProgressiveScene->sceneState->info.location, modelIcon);
            }
            else {
                arg->GLProgressiveScene->indicator->clearIcon();
            }
            arg->GLProgressiveScene->indicator->setBounds(ls, atakmap::feature::BasicStrokeStyle(0xFF00FF00, 1.0), true);
        }
    };
    std::unique_ptr<IndicatorUpdate> update;
    {
        Lock lock(owner.mutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);

        if (srid == owner.sceneState->info.srid &&
            owner.sceneState->info.location->latitude == location.latitude &&
            owner.sceneState->info.location->longitude == location.longitude &&
            owner.sceneState->info.location->altitude == location.altitude &&
            owner.sceneState->info.location->altitudeRef == location.altitudeRef &&
            owner.sceneState->info.altitudeMode == altitudeMode &&
            ((localFrame && owner.sceneState->info.localFrame.get() &&
                *localFrame == *owner.sceneState->info.localFrame) ||
                (!localFrame && !owner.sceneState->info.localFrame.get()))) {

            return TE_Ok;
        }
        owner.sceneState->info.srid = srid;
        owner.sceneState->info.location = GeoPoint2Ptr(new GeoPoint2(location), Memory_deleter_const<GeoPoint2>);
        owner.sceneState->info.altitudeMode = altitudeMode;

        if (owner.sceneState->info.altitudeMode == TEAM_ClampToGround)
            return clampToGround();

        if (localFrame)
            owner.sceneState->info.localFrame = Matrix2Ptr(new Matrix2(*localFrame), Memory_deleter_const<Matrix2>);
        else
            owner.sceneState->info.localFrame.reset();

        // if the renderers are already initalized, need to update them
        owner.locationDirty = true;

        // recompute the indicator bounds
        if (owner.sceneState->scene.get()) {
            update.reset(new IndicatorUpdate());
            MeshTransformOptions aabb_src;
            aabb_src.srid = owner.sceneState->info.srid;
            aabb_src.localFrame = Matrix2Ptr(new Matrix2(*owner.sceneState->info.localFrame), Memory_deleter_const<Matrix2>);
            update->aabb_lcs = owner.sceneState->scene->getAABB();

            MeshTransformOptions aabb_dst;
            aabb_dst.srid = 4326;

            if (Mesh_transform(&update->aabb_wgs84, update->aabb_lcs, aabb_src, aabb_dst) == TE_Ok)
                update->GLProgressiveScene = &owner;
            else
                update.reset();


        }
    }

    if (update.get()) {
        // push to GL thread
        owner.sceneState->ctx.queueEvent(std::move(std::unique_ptr<void, void(*)(void *)>(update.release(), Memory_void_deleter<IndicatorUpdate>)), IndicatorUpdate::run);
    }
#endif
    return code;
}
TAKErr GLProgressiveScene::SceneControlImpl::getInfo(SceneInfo *info) NOTHROWS
{
    if(!info)
        return TE_InvalidArg;
    info = &owner.sceneState->info;
    return TE_Ok;
}
TAKErr GLProgressiveScene::SceneControlImpl::addUpdateListener(UpdateListener *l) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    listeners.insert(l);
    return code;
}
TAKErr GLProgressiveScene::SceneControlImpl::removeUpdateListener(const UpdateListener &l) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    listeners.erase(&const_cast<UpdateListener &>(l));
    return code;
}
TAKErr GLProgressiveScene::SceneControlImpl::clampToGround() NOTHROWS
{
    TAKErr code(TE_Ok);
#if 0
    // acquire lock
    Lock lock(owner.mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    
    // if initialization is not completed,  flag clamp request
    if (!owner.sceneState->scene.get()) {
        owner.clampRequested = true;
        return code;
    }

    // iterate nodes and find anchor point on lowest LOD meshes
    TAK::Engine::Math::Point2<double> sceneMinLCS(NAN, NAN, NAN);
    for (auto entry = owner.sceneState->drawState->nodeMap.begin(); entry != owner.sceneState->drawState->nodeMap.end(); entry++) {
        SceneNode &node = *entry->first;
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
    if (owner.sceneState->info.localFrame.get()) {
        code = owner.sceneState->info.localFrame->transform(&sceneOriginWCS, sceneOriginWCS);
        TE_CHECKRETURN_CODE(code);
    }

    // XXX - this is a little crude, but we are assuming that the floor of the
    //       AABB is on the surface

    // first, subtract off origin WCS to reset WCS origin to 0AGL, then subtract off the scene LCS AABB floor to reset WCS floor to 0AGL
    owner.sceneCtrl->dispatchClampToGroundOffsetComputed(-sceneOriginWCS.z-sceneMinLCS.z);
#endif
    return code;
}
void GLProgressiveScene::SceneControlImpl::dispatchBoundsChanged(const Envelope2 &aabb, const double minGsd, const double maxGsd) NOTHROWS
{
    Lock lock(mutex);
    if (lock.status != TE_Ok)
        return;

    auto it = listeners.begin();
    while (it != listeners.end()) {
        if ((*it)->onBoundsChanged(aabb, minGsd, maxGsd) == TE_Done)
            it = listeners.erase(it);
        else
            it++;
    }
}
void GLProgressiveScene::SceneControlImpl::dispatchClampToGroundOffsetComputed(const double offset) NOTHROWS
{
    Lock lock(mutex);
    if (lock.status != TE_Ok)
        return;

    auto it = listeners.begin();
    while (it != listeners.end()) {
        if ((*it)->onClampToGroundOffsetComputed(offset) == TE_Done)
            it = listeners.erase(it);
        else
            it++;
    }
}

namespace {
    MeshBufferTextureLoader::MeshBufferTextureLoader(const std::shared_ptr<const Mesh>& mesh) NOTHROWS
        : mesh(mesh)
    { }

    MeshBufferTextureLoader::~MeshBufferTextureLoader() NOTHROWS
    { }

    TAKErr loadMeshBufferBitmap(std::shared_ptr<TAK::Engine::Renderer::Bitmap2> &result, const std::shared_ptr<const Mesh>& mesh, size_t bufferIndex) NOTHROWS {

        const MemBuffer2 *buffer = nullptr;
        TAKErr code = mesh->getBuffer(&buffer, bufferIndex);
        if (code != TE_Ok)
            return code;
        if (!buffer)
            return TE_IllegalState;

        TAK::Engine::Util::MemoryInput2 input;
        input.open(buffer->get(), buffer->size());
        
        TAK::Engine::Renderer::BitmapPtr bmp(nullptr,  nullptr);
        code = TAK::Engine::Renderer::BitmapFactory2_decode(bmp, input, nullptr);
        if (code != TE_Ok)
            return code;

        result = std::shared_ptr<TAK::Engine::Renderer::Bitmap2>(bmp.release(), bmp.get_deleter());
        return TE_Ok;
    }

    TAKErr MeshBufferTextureLoader::load(TAK::Engine::Util::FutureTask<std::shared_ptr<TAK::Engine::Renderer::Bitmap2>>& value, const char* uri) NOTHROWS {
        
        TAKErr code = TE_Unsupported;
        size_t bufferIndex = 0;

        if (Material_getBufferIndexTextureURI(&bufferIndex, uri) == TE_Ok) {
            value = Task_begin(sceneTextureLoadWorker(), loadMeshBufferBitmap, this->mesh, bufferIndex);
            code = TE_Ok;
        }

        return code;
    }
}