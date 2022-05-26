//
// Created by GeoDev on 1/14/2021.
//

#include "renderer/core/GLGlobeBase.h"

#include "core/LegacyAdapters.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLWorkers.h"
#include "renderer/core/GLLabelManager.h"
#include "renderer/core/GLLayer2.h"
#include "renderer/core/GLMapRenderable2.h"
#include "thread/Lock.h"
#include "util/Memory.h"

using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer::Core::Controls;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::renderer;

#define _EPSILON 0.0001
#define _EPSILON_F 0.01
#define IS_TINY(v) \
    (std::abs(v) <= _EPSILON)
#define IS_TINYF(v) \
    (std::abs(v) <= _EPSILON_F)

namespace {
    void asyncSetBaseMap(void *opaque) NOTHROWS;
#ifndef __ANDROID__
    void asyncSetLabelManager(void* opaque) NOTHROWS;
#endif
    bool hasSettled(double dlat, double dlng, double dscale, double drot, double dtilt, double dfocusX, double dfocusY) NOTHROWS;
    void State_save(GLGlobeBase::State *value, const GLGlobeBase& view) NOTHROWS;
}

struct GLGlobeBase::AsyncRunnable
{
    enum EventType {
        MapMoved,
        ProjectionChanged,
        ElevationExaggerationFactorChanged,
        FocusChanged,
        LayersChanged,
    };

    GLGlobeBase &owner;
    std::shared_ptr<Mutex> mutex;
    std::shared_ptr<bool> canceled;
    bool enqueued;

    struct {
        float x {0};
        float y {0};
    } focus;
    struct {
        double lat {0};
        double lon {0};
        double alt {0};
        double scale {0};
        double rot {0};
        double tilt {0};
        float factor {0};
    } target;
    struct {
        std::size_t width {0};
        std::size_t height {0};
    } resize;
    int srid {-1};
    struct {
        std::list<std::shared_ptr<GLLayer2>> renderables;
        std::list<std::shared_ptr<GLLayer2>> releasables;
    } layers;
    AsyncRunnable(GLGlobeBase &owner_, const std::shared_ptr<Mutex> &mutex_) NOTHROWS :
        owner(owner_),
        mutex(mutex_),
        canceled(owner_.disposed),
        enqueued(false)
    {}
};

GLGlobeBase::GLGlobeBase(RenderContext &ctx, const double dpi, const MapCamera2::Mode mode) NOTHROWS :
    context(ctx),
    displayDpi(dpi),
    animationFactor(1.0),
    targeting(false),
    isScreenshot(false),
    settled(true),
    animationLastTick(0LL),
    animationDelta(0LL),
    basemap(nullptr, nullptr),
    labelManager(new GLLabelManager(), Memory_deleter_const<GLLabelManager>),
    asyncRunnablesMutex(new Mutex(TEMT_Recursive)),
    disposed(new bool(false)),
    layerRenderersMutex(TEMT_Recursive),
    cammode(mode)
{
    state.focus.geo.latitude = renderPasses[0].drawLat;
    state.focus.geo.longitude = renderPasses[0].drawLng;
    state.focus.geo.altitude = renderPasses[0].drawAlt;
    state.focus.x = renderPasses[0].focusx;
    state.focus.y = renderPasses[0].focusy;
    state.srid = renderPasses[0].drawSrid;
    state.resolution = renderPasses[0].drawMapResolution;
    state.rotation = renderPasses[0].drawRotation;
    state.tilt = renderPasses[0].drawTilt;
    state.width = (renderPasses[0].right-renderPasses[0].left);
    state.height = (renderPasses[0].top-renderPasses[0].bottom);

    this->renderPass = this->renderPasses;
}
GLGlobeBase::~GLGlobeBase() NOTHROWS
{
    GLGlobeBase::stop();

    if (this->basemap.get()) {
        this->basemap.reset();
    }

    Lock lock(*asyncRunnablesMutex);
    *disposed = true;
    if(mapMovedEvent.get() && mapMovedEvent->enqueued)
        mapMovedEvent.release();
    if(projectionChangedEvent.get() && projectionChangedEvent->enqueued)
        projectionChangedEvent.release();
    if(layersChangedEvent.get() && layersChangedEvent->enqueued)
        layersChangedEvent.release();
    if(focusChangedEvent.get() && focusChangedEvent->enqueued)
        focusChangedEvent.release();
    if(mapResizedEvent.get() && mapResizedEvent->enqueued)
        mapResizedEvent.release();
}
TAKErr GLGlobeBase::start() NOTHROWS
{
    return TE_Ok;
}
TAKErr GLGlobeBase::stop() NOTHROWS
{
    // clear all the renderables
    std::list<atakmap::core::Layer *> empty;
    refreshLayers(empty);

    if (this->labelManager)
        this->labelManager->stop();

    return TE_Ok;
}
void GLGlobeBase::render() NOTHROWS
{
    const int64_t tick = Platform_systime_millis();
    if (this->animationLastTick)
        this->animationDelta = tick - this->animationLastTick;
    else
        glGetError();
    this->animationLastTick = tick;

    this->prepareScene();
    this->renderPass = &this->renderPasses[0];
    this->drawRenderables();
    this->renderPass = &this->renderPasses[0];
}
void GLGlobeBase::setBaseMap(GLMapRenderable2Ptr &&map) NOTHROWS
{
    if (!context.isRenderThread()) {
        std::unique_ptr<std::pair<GLGlobeBase&, GLMapRenderable2Ptr>> p(new std::pair<GLGlobeBase&, GLMapRenderable2Ptr>(*this, std::move(map)));
        context.queueEvent(asyncSetBaseMap, std::unique_ptr<void, void(*)(const void *)>(p.release(), Memory_leaker_const<void>));
    } else {
        basemap = std::move(map);
    }
}
#ifndef __ANDROID__
void GLGlobeBase::setLabelManager(GLLabelManager* labelMan) NOTHROWS
{
    if (!context.isRenderThread()) {
        std::unique_ptr<std::pair<GLGlobeBase&, GLLabelManager*>> p(new std::pair<GLGlobeBase&, GLLabelManager*>(*this, std::move(labelMan)));
        context.queueEvent(asyncSetLabelManager, std::unique_ptr<void, void(*)(const void *)>(p.release(), Memory_leaker_const<void>));
    } else {
        labelManager = std::unique_ptr<GLLabelManager, void(*)(const GLLabelManager *)>(labelMan, Memory_leaker_const<GLLabelManager>);
    }
}
#endif
void GLGlobeBase::release() NOTHROWS
{
    for(auto it = renderables.begin(); it != renderables.end(); it++)
        (*it)->release();
    if(basemap)
        basemap->release();
}
void GLGlobeBase::prepareScene() NOTHROWS
{
    // animation tick
    const bool animated = animate();
    if (animated) {
        drawVersion++;
    }

    // validate scene model
    validateSceneModel(
            this,
            (renderPasses[0].right-renderPasses[0].left),
            (renderPasses[0].top-renderPasses[0].bottom),
            cammode,
            animation.last.clip.near,
            animation.last.clip.far);
    renderPasses[0].near = (float)renderPasses[0].scene.camera.near;
    renderPasses[0].far = (float)renderPasses[0].scene.camera.far;

    // compute bounds
    computeBounds();

    // render pass 0 is fully formed
    renderPasses[0].drawVersion = drawVersion;

    // dispatch camera changed
    if(animated) {
        Lock cclock(cameraChangedMutex);
        for(auto it : cameraListeners)
            it->onCameraChanged(*this);
    }

    glDepthRangef(0.0f, 1.0f);
    glClearDepthf(1.0f);

    glDepthFunc(GL_LEQUAL);
    glDepthMask(GL_TRUE);
    glEnable(GL_DEPTH_TEST);

    GLWorkers_doResourceLoadingWork(30);
    GLWorkers_doGLThreadWork();
}
void GLGlobeBase::drawRenderables(const GLGlobeBase::State &renderState) NOTHROWS
{
    this->renderPass = &renderState;
    this->multiPartPass = false;

    glViewport(static_cast<GLint>(renderState.viewport.x), static_cast<GLint>(renderState.viewport.y), static_cast<GLsizei>(renderState.viewport.width), static_cast<GLsizei>(renderState.viewport.height));

    GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION);
    GLES20FixedPipeline::getInstance()->glPushMatrix();
    GLES20FixedPipeline::getInstance()->glOrthof(static_cast<float>(renderState.left), static_cast<float>(renderState.right), static_cast<float>(renderState.bottom), static_cast<float>(renderState.top), renderState.near, renderState.far);

    GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);
    GLES20FixedPipeline::getInstance()->glPushMatrix();

    if (renderState.basemap && basemap)
        basemap->draw(*this, renderState.renderPass);

    std::list<std::shared_ptr<GLLayer2>>::iterator it;
    for (it = this->renderables.begin(); it != this->renderables.end(); it++) {
#ifdef MSVC
        GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);
#endif
        if ((*it)->getRenderPass()&renderState.renderPass)
            (*it)->draw(*this, renderState.renderPass);
    }

    // restore the transforms
    GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION);
    GLES20FixedPipeline::getInstance()->glPopMatrix();

    GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);
    GLES20FixedPipeline::getInstance()->glPopMatrix();

    // restore the viewport
    glViewport(renderPasses[0].left, renderPasses[0].bottom, renderPasses[0].right-renderPasses[0].left, renderPasses[0].top-renderPasses[0].bottom);
}
void GLGlobeBase::drawRenderable(GLMapRenderable2& renderable, const int pass) NOTHROWS
{
    renderable.draw(*this, pass);
}
GLLabelManager* GLGlobeBase::getLabelManager() const NOTHROWS
{
    return labelManager.get();
}
TAKErr GLGlobeBase::registerControl(const Layer2 &layer, const char *type, void *ctrl) NOTHROWS
{
    if (!type)
        return TE_InvalidArg;
    if (!ctrl)
        return TE_InvalidArg;

    TAKErr code(TE_Ok);
    Lock lock(controlsMutex);
    TE_CHECKRETURN_CODE(lock.status);

    std::map<const Layer2 *, std::map<std::string, std::set<void *>>>::iterator entry;
    entry = controls.find(&layer);
    if (entry == controls.end()) {
        controls[&layer][type].insert(ctrl);
    } else {
        entry->second[type].insert(ctrl);
    }

    Control c;
    c.type = type;
    c.value = ctrl;

    std::set<MapRenderer::OnControlsChangedListener *>::iterator it;
    it = controlsListeners.begin();
    while(it != controlsListeners.end()) {
        if ((*it)->onControlRegistered(layer, c) == TE_Done)
            it = controlsListeners.erase(it);
        else
            it++;
    }

    return code;
}
TAKErr GLGlobeBase::unregisterControl(const TAK::Engine::Core::Layer2 &layer, const char *type, void *ctrl) NOTHROWS
{
    if (!type)
        return TE_InvalidArg;
    if (!ctrl)
        return TE_InvalidArg;

    TAKErr code(TE_Ok);
    Lock lock(controlsMutex);
    TE_CHECKRETURN_CODE(lock.status);

    std::map<const Layer2 *, std::map<std::string, std::set<void *>>>::iterator entry;
    entry = controls.find(&layer);
    if (entry == controls.end()) {
        Logger_log(TELL_Warning, "No controls are registered on Layer [%s]", layer.getName());
        return TE_Ok;
    }

    auto ctrlEntry = entry->second.find(type);
    if (ctrlEntry == entry->second.end()) {
        Logger_log(TELL_Warning, "No control of type [%s] are registered on Layer [%s]", type, layer.getName());
        return TE_Ok;
    }

    ctrlEntry->second.erase(ctrl);
    if (ctrlEntry->second.empty()) {
        entry->second.erase(ctrlEntry);
        if (entry->second.empty())
            controls.erase(entry);
    }

    Control c;
    c.type = type;
    c.value = ctrl;

    std::set<MapRenderer::OnControlsChangedListener *>::iterator it;
    it = controlsListeners.begin();
    while(it != controlsListeners.end()) {
        if ((*it)->onControlUnregistered(layer, c) == TE_Done)
            it = controlsListeners.erase(it);
        else
            it++;
    }

    return code;
}
TAKErr GLGlobeBase::visitControls(bool *visited, void *opaque, TAKErr(*visitor)(void *opaque, const Layer2 &layer, const Control &ctrl), const Layer2 &layer, const char *type) NOTHROWS
{
    if (visited)
        *visited = false;

    if (!type)
        return TE_InvalidArg;
    if (!visitor)
        return TE_InvalidArg;

    TAKErr code(TE_Ok);
    Lock lock(controlsMutex);
    TE_CHECKRETURN_CODE(lock.status);

    // obtain controls on layer
    std::map<const Layer2 *, std::map<std::string, std::set<void *>>>::iterator entry;
    entry = controls.find(&layer);
    if (entry == controls.end())
        return TE_Ok;

    // obtain controls of type
    auto ctrlEntry = entry->second.find(type);
    if (ctrlEntry == entry->second.end())
        return TE_Ok;

    if (visited)
        *visited = !entry->second.empty();

    // visit
    for (auto it = ctrlEntry->second.begin(); it != ctrlEntry->second.end(); it++) {
        Control c;
        c.type = type;
        c.value = *it;

        code = visitor(opaque, layer, c);
        TE_CHECKBREAK_CODE(code);
    }

    return code;
}
TAKErr GLGlobeBase::visitControls(bool *visited, void *opaque, TAKErr(*visitor)(void *opaque, const Layer2 &layer, const Control &ctrl), const Layer2 &layer) NOTHROWS
{
    if (visited)
        *visited = false;

    if (!visitor)
        return TE_InvalidArg;

    TAKErr code(TE_Ok);
    Lock lock(controlsMutex);
    TE_CHECKRETURN_CODE(lock.status);

    // obtain controls on layer
    std::map<const Layer2 *, std::map<std::string, std::set<void *>>>::iterator entry;
    entry = controls.find(&layer);
    if (entry == controls.end())
        return TE_Ok;

    // obtain controls of type
    std::map<std::string, std::set<void *>>::iterator ctrlEntry;
    for (ctrlEntry = entry->second.begin(); ctrlEntry != entry->second.end(); ctrlEntry++) {
        if (visited)
            *visited |= !entry->second.empty();

        // visit
        for (auto it = ctrlEntry->second.begin(); it != ctrlEntry->second.end(); it++) {
            Control c;
            c.type = ctrlEntry->first.c_str();
            c.value = *it;

            code = visitor(opaque, layer, c);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKBREAK_CODE(code);
    }

    return code;
}
TAKErr GLGlobeBase::visitControls(void *opaque, TAKErr(*visitor)(void *opaque, const Layer2 &layer, const Control &ctrl)) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(controlsMutex);
    TE_CHECKRETURN_CODE(lock.status);

    // obtain controls on layer
    std::map<const Layer2 *, std::map<std::string, std::set<void *>>>::iterator entry;
    for (entry = controls.begin(); entry != controls.end(); entry++) {
        // obtain controls of type
        std::map<std::string, std::set<void *>>::iterator ctrlEntry;
        for (ctrlEntry = entry->second.begin(); ctrlEntry != entry->second.end(); ctrlEntry++) {
            // visit
            for (auto it = ctrlEntry->second.begin(); it != ctrlEntry->second.end(); it++) {
                Control c;
                c.type = ctrlEntry->first.c_str();
                c.value = *it;

                code = visitor(opaque, *entry->first, c);
                TE_CHECKBREAK_CODE(code);
            }
            TE_CHECKBREAK_CODE(code);
        }
    }

    return code;
}
TAKErr GLGlobeBase::addOnControlsChangedListener(TAK::Engine::Core::MapRenderer::OnControlsChangedListener *l) NOTHROWS
{
    if (!l)
        return TE_InvalidArg;

    TAKErr code(TE_Ok);
    Lock lock(controlsMutex);
    TE_CHECKRETURN_CODE(lock.status);

    controlsListeners.insert(l);
    return code;
}
TAKErr GLGlobeBase::removeOnControlsChangedListener(TAK::Engine::Core::MapRenderer::OnControlsChangedListener *l) NOTHROWS
{
    if (!l)
        return TE_InvalidArg;

    TAKErr code(TE_Ok);
    Lock lock(controlsMutex);
    TE_CHECKRETURN_CODE(lock.status);

    controlsListeners.erase(l);
    return code;
}
RenderContext &GLGlobeBase::getRenderContext() const NOTHROWS
{
    return context;
}
TAKErr GLGlobeBase::lookAt(const TAK::Engine::Core::GeoPoint2 &from, const TAK::Engine::Core::GeoPoint2 &at, const CameraCollision collision, const bool animate) NOTHROWS
{
    return TE_Unsupported;
}
TAKErr GLGlobeBase::lookAt(const TAK::Engine::Core::GeoPoint2 &at, const double resolution, const double azimuth, const double tilt, const CameraCollision collision, const bool animate) NOTHROWS
{
    // validate inputs
    if(TE_ISNAN(at.latitude) || TE_ISNAN(at.longitude))
        return TE_InvalidArg;
    if(TE_ISNAN(resolution))
        return TE_InvalidArg;
    if(TE_ISNAN(azimuth))
        return TE_InvalidArg;
    if(TE_ISNAN(tilt))
        return TE_InvalidArg;

    Lock lock(*asyncRunnablesMutex);
    state.focus.geo = at;
    state.resolution = resolution;
    state.rotation = azimuth;
    state.tilt = tilt;

    if(!mapMovedEvent.get())
        mapMovedEvent.reset(new AsyncRunnable(*this, asyncRunnablesMutex));
    mapMovedEvent->target.lat = at.latitude;
    mapMovedEvent->target.lon = at.longitude;
    mapMovedEvent->target.alt = at.altitude;
    mapMovedEvent->target.rot = azimuth;
    mapMovedEvent->target.tilt = tilt;
    mapMovedEvent->target.scale = atakmap::core::AtakMapView_getMapScale(displayDpi, resolution);
    mapMovedEvent->target.factor = animate ? 0.3f : 1.0f;

    if(!mapMovedEvent->enqueued) {
        context.queueEvent(asyncAnimate, std::unique_ptr<void, void(*)(const void *)>(mapMovedEvent.get(), Memory_leaker_const<void>));
        mapMovedEvent->enqueued = true;
    }

    return TE_Ok;
}
TAKErr GLGlobeBase::lookFrom(const GeoPoint2 &from_, const double azimuth, const double elevation, const CameraCollision collision, const bool animate) NOTHROWS
{
    GeoPoint2 from(from_);
    if(TE_ISNAN(from.altitude))
        from.altitude = 0.0;

    std::size_t height = state.height;
    std::size_t width = state.width;

    double el;
    if(getTerrainMeshElevation(&el, from.latitude, from.longitude) != TE_Ok || TE_ISNAN(el))
        el = 0.0;

    double gsd = MapSceneModel2_gsd(el + from.altitude, 45, height);
    double scale = atakmap::core::AtakMapView_getMapScale(displayDpi, gsd);

    const double resolution = atakmap::core::AtakMapView_getMapResolution(displayDpi, scale);
    double tilt = 90 + elevation;

    //    sm = new MapSceneModel(getSurface().getDpi(), sm.width, sm.height, sm.mapProjection, new GeoPoint(at.getLatitude(),
    //    at.getLongitude(), alt), sm.focusx, sm.focusy, sm.camera.azimuth, sm.camera.elevation+90d, sm.gsd, true); final double range0 =
    //    (sm.gsd/(sm.height/2d)) / Math.tan(Math.toRadians(sm.camera.fov/2d)); final double CT0 = range0 -
    //    sm.mapProjection.inverse(sm.camera.target, null).getAltitude();
    MapSceneModel2 sm(
        displayDpi,
        width,
        height,
        state.srid,
        from,
        state.focus.x, state.focus.y,
        azimuth,
        tilt,
        gsd,
        NAN,
        NAN,
        MapCamera2::Perspective,
        true);    // obtain local mesh elevation at camera location


    GeoPoint2 cam;
    sm.projection->inverse(&cam, sm.camera.location);
    
    GeoPoint2 origFocus;
    sm.projection->inverse(&origFocus, sm.camera.target);
    double range = cam.altitude;
    origFocus.altitude = cam.altitude;

    Lock lock(*asyncRunnablesMutex);
    state.focus.geo = origFocus;
    state.resolution = resolution;
    state.rotation = azimuth;
    state.tilt = tilt;

    if(!mapMovedEvent.get())
        mapMovedEvent.reset(new AsyncRunnable(*this, asyncRunnablesMutex));
    mapMovedEvent->target.lat = origFocus.latitude;
    mapMovedEvent->target.lon = origFocus.longitude;
    mapMovedEvent->target.alt = origFocus.altitude;
    mapMovedEvent->target.rot = azimuth;
    mapMovedEvent->target.tilt = tilt;
    mapMovedEvent->target.scale = scale;
    mapMovedEvent->target.factor = animate ? 0.3f : 1.0f;

    if(!mapMovedEvent->enqueued) {
        context.queueEvent(asyncAnimate, std::unique_ptr<void, void(*)(const void *)>(mapMovedEvent.get(), Memory_leaker_const<void>));
        mapMovedEvent->enqueued = true;
    }
    return TE_Ok;
}
bool GLGlobeBase::isAnimating() const NOTHROWS
{
    return !animation.settled;
}
TAKErr GLGlobeBase::addOnCameraChangedListener(TAK::Engine::Core::MapRenderer2::OnCameraChangedListener *l) NOTHROWS
{
    if(!l)
        return TE_InvalidArg;
    Lock lock(cameraChangedMutex);
    TE_CHECKRETURN_CODE(lock.status);
    cameraListeners.insert(l);
    return TE_Ok;
}
TAKErr GLGlobeBase::removeOnCameraChangedListener(TAK::Engine::Core::MapRenderer2::OnCameraChangedListener *l) NOTHROWS
{
    if(!l)
        return TE_InvalidArg;
    Lock lock(cameraChangedMutex);
    TE_CHECKRETURN_CODE(lock.status);
    cameraListeners.erase(l);
    return TE_Ok;
}
bool GLGlobeBase::animate() NOTHROWS
{
    // previous `current` animation state becomes new `last` animation state
    animation.last = animation.current;

    // various state parameters always reflect the last (becomes `current` one
    // pump after `settled`)
    {
        WriteLock wlock(renderPasses0Mutex);

        renderPasses[0].drawMapScale = animation.last.mapScale;
        renderPasses[0].drawLat = animation.last.point.latitude;
        renderPasses[0].drawLng = animation.last.point.longitude;
        renderPasses[0].drawAlt = animation.last.point.altitude;
        renderPasses[0].drawRotation = animation.last.rotation;
        renderPasses[0].drawTilt = animation.last.tilt;
        renderPasses[0].focusx = animation.last.focusx;
        renderPasses[0].focusy = animation.last.focusy;
        renderPasses[0].drawMapResolution = atakmap::core::AtakMapView_getMapResolution(displayDpi, renderPasses[0].drawMapScale);
    }

    if(animation.settled && !settled) {
        // the animation was settled, but map state is one frame behind
        settled = true;
        return true;
    } else if (settled) {
        return false;
    }

    this->animationLastUpdate = this->animationLastTick;

    // capture the difference between the target and current animation states
    // to determine if the animation is "settled"
    const double scaleDelta = (animation.target.mapScale - animation.current.mapScale);
    const double pointDelta = TAK::Engine::Core::GeoPoint2_distance(animation.target.point,
                                                                    animation.current.point,
                                                                    true);
    const double latDelta = (animation.target.point.latitude -
                             animation.current.point.latitude);
    const double lngDelta = (animation.target.point.longitude -
                             animation.current.point.longitude);
    const double focusxDelta = (animation.target.focusx - animation.current.focusx);
    const double focusyDelta = (animation.target.focusy - animation.current.focusy);

    // update the `current` animation state using the delta values and the
    // animation factor
    GeoPoint2 updatePoint = TAK::Engine::Core::GeoPoint2_pointAtDistance(animation.current.point,
                                                                         TAK::Engine::Core::GeoPoint2_bearing(
                                                                                 animation.current.point,
                                                                                 animation.target.point,
                                                                                 false),
                                                                         pointDelta *
                                                                         animationFactor, false);

    animation.current.mapScale += scaleDelta * animationFactor;
    animation.current.point.latitude = updatePoint.latitude;
    animation.current.point.longitude = updatePoint.longitude;
    if (animation.current.point.longitude < -180.0)
        animation.current.point.longitude += 360.0;
    else if (animation.current.point.longitude > 180.0)
        animation.current.point.longitude -= 360.0;
    animation.current.point.altitude = animation.target.point.altitude;
    animation.current.focusx += (float) (focusxDelta * animationFactor);
    animation.current.focusy += (float) (focusyDelta * animationFactor);

    double rotDelta = (animation.target.rotation - animation.current.rotation);

    // Go the other way
    if (fabs(rotDelta) > 180) {
        if (rotDelta < 0) {
            animation.current.rotation -= 360;
        } else {
            animation.current.rotation += 360;
        }
        rotDelta = (animation.target.rotation - animation.current.rotation);
    }

    double tiltDelta = (animation.target.tilt - animation.current.tilt);
    animation.current.tilt += tiltDelta * animationFactor;

    //drawRotation += rotDelta * 0.1;
    animation.current.rotation += rotDelta * animationFactor;

    // determine if the map is settled. A small epsilon is employed as
    // floating-point arithmetic may not yield exact convergence resulting in
    // an infinite animation jitter
    animation.settled = hasSettled(latDelta, lngDelta, scaleDelta, rotDelta, 0.0,
                         focusxDelta, focusyDelta);

    // if settled, set to the exact target values.
    if (animation.settled)
        animation.current = animation.target;

    // camera is updated, dispatch
    return true;
}
TAKErr GLGlobeBase::setDisplayMode(const MapRenderer::DisplayMode mode) NOTHROWS
{
    int srid;
    switch(mode) {
        case MapRenderer::Globe :
            srid = 4978;
            break;
        case MapRenderer::Flat :
            srid = 4326;
            break;
        default :
            return TE_InvalidArg;
    }

    Lock lock(*asyncRunnablesMutex);
    state.srid = srid;

    if(!projectionChangedEvent.get())
        projectionChangedEvent.reset(new AsyncRunnable(*this, asyncRunnablesMutex));
    projectionChangedEvent->srid = srid;

    if(!projectionChangedEvent->enqueued) {
        context.queueEvent(asyncProjUpdate, std::unique_ptr<void, void(*)(const void *)>(projectionChangedEvent.get(), Memory_leaker_const<void>));
        projectionChangedEvent->enqueued = true;
    }
    return TE_Ok;
}
MapRenderer::DisplayMode GLGlobeBase::getDisplayMode() const NOTHROWS
{
    Lock lock(*asyncRunnablesMutex);
    return (state.srid == 4978) ? MapRenderer::Globe : MapRenderer::Flat;
}
MapRenderer::DisplayOrigin GLGlobeBase::getDisplayOrigin() const NOTHROWS
{
    return MapRenderer::LowerLeft;
}
TAKErr GLGlobeBase::setFocusPoint(const float focusx, const float focusy) NOTHROWS
{
    Lock lock(*asyncRunnablesMutex);
    state.focus.x = focusx;
    state.focus.y = focusy;
    if(!focusChangedEvent.get())
        focusChangedEvent.reset(new AsyncRunnable(*this, asyncRunnablesMutex));
    focusChangedEvent->focus.x = focusx;
    focusChangedEvent->focus.y = focusy;
    if(!focusChangedEvent->enqueued) {
        context.queueEvent(asyncAnimateFocus, std::unique_ptr<void, void(*)(const void *)>(focusChangedEvent.get(), Memory_leaker_const<void>));
        focusChangedEvent->enqueued = true;
    }
    return TE_Ok;
}
TAKErr GLGlobeBase::getFocusPoint(float *focusx, float *focusy) const NOTHROWS
{
    if(!focusx || !focusy)
        return TE_InvalidArg;
    Lock lock(*asyncRunnablesMutex);
    *focusx = state.focus.x;
    // correct for LL origin
    *focusy = (float)state.height-state.focus.y;
    return TE_Ok;
}
TAKErr GLGlobeBase::setSurfaceSize(const std::size_t width, const std::size_t height) NOTHROWS
{
    if(!width || !height)
        return TE_InvalidArg;
    Lock lock(*asyncRunnablesMutex);
    state.width = width;
    state.height = height;

    if(!mapResizedEvent.get())
        mapResizedEvent.reset(new AsyncRunnable(*this, this->asyncRunnablesMutex));
    mapResizedEvent->resize.width = width;
    mapResizedEvent->resize.height = height;
    if(!mapResizedEvent->enqueued) {
        context.queueEvent(glMapResized, std::unique_ptr<void, void(*)(const void *)>(mapResizedEvent.get(), Memory_leaker_const<void>));
        mapResizedEvent->enqueued = true;
    }
    return TE_Ok;
}
TAKErr GLGlobeBase::getMapSceneModel(MapSceneModel2 *value, const bool instant, const DisplayOrigin origin) NOTHROWS
{
    return TE_Unsupported;
}

void GLGlobeBase::mapLayerAdded(atakmap::core::AtakMapView* map_view, atakmap::core::Layer *layer)
{
    std::list<atakmap::core::Layer *> layers;
    map_view->getLayers(layers);
    refreshLayers(layers);
}
void GLGlobeBase::mapLayerRemoved(atakmap::core::AtakMapView* map_view, atakmap::core::Layer *layer)
{
    std::list<atakmap::core::Layer *> layers;
    map_view->getLayers(layers);
    refreshLayers(layers);
}
void GLGlobeBase::mapLayerPositionChanged(atakmap::core::AtakMapView* mapView, atakmap::core::Layer *layer, const int oldPosition, const int newPosition)
{
    std::list<atakmap::core::Layer *> layers;
    mapView->getLayers(layers);
    refreshLayers(layers);
}
// XXX - next 2 -- need to be using start/stop to protect subject access
void GLGlobeBase::refreshLayersImpl(const std::list<std::shared_ptr<GLLayer2>> &toRender, const std::list<std::shared_ptr<GLLayer2>> &toRelease) NOTHROWS
{
    renderables.clear();

    std::list<std::shared_ptr<GLLayer2>>::const_iterator it;

    for (it = toRender.begin(); it != toRender.end(); it++)
        renderables.push_back(std::shared_ptr<GLLayer2>(*it));

    for (it = toRelease.begin(); it != toRelease.end(); it++)
        (*it)->release();
}
void GLGlobeBase::refreshLayers(const std::list<atakmap::core::Layer *> &layers) NOTHROWS
{
    std::list<atakmap::core::Layer *>::const_iterator layersIter;

    Lock lock(layerRenderersMutex);

    // validate layer adapters
    std::map<const atakmap::core::Layer *, std::shared_ptr<Layer2>> invalidLayerAdapters;
    std::map<const atakmap::core::Layer *, std::shared_ptr<Layer2>>::iterator adapterIter;
    for (adapterIter = adaptedLayers.begin(); adapterIter != adaptedLayers.end(); adapterIter++) {
        invalidLayerAdapters.insert(std::make_pair(adapterIter->first, std::move(adapterIter->second)));
    }
    adaptedLayers.clear();

    for (layersIter = layers.begin(); layersIter != layers.end(); layersIter++) {
        adapterIter = invalidLayerAdapters.find(*layersIter);
        if (adapterIter != invalidLayerAdapters.end()) {
            adaptedLayers.insert(std::make_pair(adapterIter->first, std::move(adapterIter->second)));
            invalidLayerAdapters.erase(adapterIter);
        } else {
            std::shared_ptr<atakmap::core::Layer> layer = atakmap::core::LayerPtr(*layersIter, Memory_leaker_const<atakmap::core::Layer>);
            std::shared_ptr<Layer2> layer2;
            if (LegacyAdapters_adapt(layer2, layer) == TE_Ok)
                adaptedLayers.insert(std::make_pair(*layersIter, std::move(layer2)));
        }
    }

    std::map<const Layer2 *, std::shared_ptr<GLLayer2>> invalidLayerRenderables;
    for (auto renderIter = layerRenderers.begin(); renderIter != layerRenderers.end(); ++renderIter) {
        invalidLayerRenderables[renderIter->first] = std::shared_ptr<GLLayer2>(renderIter->second);
    }
    layerRenderers.clear();

    // compile the render and release list
    if(!layersChangedEvent.get())
        layersChangedEvent.reset(new AsyncRunnable(*this, asyncRunnablesMutex));

    // always clear the renderables, this list is rebuilt every time; items in releasables still need to be released
    layersChangedEvent->layers.renderables.clear();

    for (layersIter = layers.begin(); layersIter != layers.end(); ++layersIter) {
        adapterIter = adaptedLayers.find(*layersIter);
        if (adapterIter == adaptedLayers.end())
            continue;
        Layer2 *layer = adapterIter->second.get();

        std::shared_ptr<GLLayer2> glLayer;
        auto entry = invalidLayerRenderables.find(layer);
        if (entry != invalidLayerRenderables.end()) {
            glLayer = entry->second;
            invalidLayerRenderables.erase(entry);
            layerRenderers[layer] = std::shared_ptr<GLLayer2>(glLayer);
        }
        if (!glLayer.get()) {
            GLLayer2Ptr gllayerPtr(nullptr, nullptr);
            if (createLayerRenderer(gllayerPtr, *layer) == TE_Ok) {
                glLayer = std::move(gllayerPtr);
                if (glLayer.get()) {
                    layerRenderers[layer] = std::shared_ptr<GLLayer2>(glLayer);
                    glLayer->start();
                }
            }
        }
        if(glLayer)
            layersChangedEvent->layers.renderables.push_back(glLayer);
    }

    // stop all renderables that will be released
    for (auto renderIter = invalidLayerRenderables.begin(); renderIter != invalidLayerRenderables.end(); ++renderIter)
    {
        renderIter->second->stop();
        layersChangedEvent->layers.releasables.push_back(std::shared_ptr<GLLayer2>(renderIter->second));
    }


    // update the render list
    if (context.isRenderThread()) {
        refreshLayersImpl(layersChangedEvent->layers.renderables, layersChangedEvent->layers.releasables);
        layersChangedEvent->layers.renderables.clear();
        layersChangedEvent->layers.releasables.clear();
    } else {
        if(!layersChangedEvent->enqueued){
            context.queueEvent(asyncRefreshLayers, std::unique_ptr<void, void(*)(const void *)>(layersChangedEvent.get(), Memory_leaker_const<void>));
            layersChangedEvent->enqueued = true;
        }
    }
}
int GLGlobeBase::getTerrainVersion() const NOTHROWS
{
    return 0;
}
TAKErr GLGlobeBase::getTerrainMeshElevation(double* value, const double latitude, const double longitude) const NOTHROWS
{
    *value = 0;
    return TE_Ok;
}
SurfaceRendererControl* GLGlobeBase::getSurfaceRendererControl() const NOTHROWS
{
    return nullptr;
}
IlluminationControlImpl* GLGlobeBase::getIlluminationControl() const NOTHROWS
{
    return nullptr;
}
void GLGlobeBase::asyncRefreshLayers(void *opaque) NOTHROWS
{
    std::unique_ptr<AsyncRunnable> runnable(static_cast<AsyncRunnable *>(opaque));
    Lock lock(*runnable->mutex);
    if (*runnable->canceled)
        return;

    runnable->owner.refreshLayersImpl(runnable->layers.renderables, runnable->layers.releasables);
    runnable->layers.renderables.clear();
    runnable->layers.releasables.clear();

    runnable->enqueued = false;

    // return to pool
    runnable.release();
}
void GLGlobeBase::asyncAnimate(void *opaque) NOTHROWS
{
    std::unique_ptr<AsyncRunnable> runnable(static_cast<AsyncRunnable *>(opaque));
    Lock lock(*runnable->mutex);
    // view was disposed, return and cleanup
    if (*runnable->canceled)
        return;

    runnable->owner.animation.target.point.latitude = runnable->target.lat;
    runnable->owner.animation.target.point.longitude = runnable->target.lon;
    runnable->owner.animation.target.point.altitude = runnable->target.alt;
    runnable->owner.animation.target.mapScale = runnable->target.scale;
    runnable->owner.animation.target.rotation = runnable->target.rot;
    runnable->owner.animation.target.tilt = runnable->target.tilt;
    runnable->owner.animation.settled = false;
    runnable->owner.animationFactor = runnable->target.factor;
    runnable->owner.settled = false;
    runnable->owner.drawVersion++;

    runnable->enqueued = false;

    // not disposed, return to pool
    runnable.release();
}
void GLGlobeBase::asyncProjUpdate(void *opaque) NOTHROWS
{
    std::unique_ptr<AsyncRunnable> runnable(static_cast<AsyncRunnable *>(opaque));
    Lock lock(*runnable->mutex);
    // view was disposed, return and cleanup
    if (*runnable->canceled)
        return;

    {
        WriteLock wlock(runnable->owner.renderPasses0Mutex);
        runnable->owner.renderPasses[0].drawSrid = runnable->srid;
    }
    // trigger an animation tick
    runnable->owner.animation.settled = false;
    runnable->owner.settled = false;

    runnable->enqueued = false;

    // not disposed, return to pool
    runnable.release();
}
void GLGlobeBase::asyncAnimateFocus(void *opaque) NOTHROWS
{
    std::unique_ptr<AsyncRunnable> runnable(static_cast<AsyncRunnable *>(opaque));
    Lock lock(*runnable->mutex);
    // view was disposed, return and cleanup
    if (*runnable->canceled)
        return;

    runnable->owner.animation.target.focusx = runnable->focus.x;
    runnable->owner.animation.target.focusy = runnable->focus.y;
    runnable->owner.animation.settled = false;
    runnable->owner.settled = false;
    runnable->owner.drawVersion++;

    runnable->enqueued = false;

    // not disposed, return to pool
    runnable.release();
}
void GLGlobeBase::glMapResized(void *opaque) NOTHROWS
{
    std::unique_ptr<AsyncRunnable> runnable(static_cast<AsyncRunnable *>(opaque));
    Lock lock(*runnable->mutex);
    if (*runnable->canceled)
        return;

    {
        WriteLock wlock(runnable->owner.renderPasses0Mutex);
        runnable->owner.renderPasses[0].top = static_cast<int>(runnable->resize.height);
        runnable->owner.renderPasses[0].bottom = 0;
        runnable->owner.renderPasses[0].left = 0;
        runnable->owner.renderPasses[0].right = static_cast<int>(runnable->resize.width);
    }
    // force refresh
    runnable->owner.settled = false;
    runnable->owner.animation.settled = false;
    runnable->owner.drawVersion++;
    runnable->enqueued = false;
    runnable.release();
}
TAKErr GLGlobeBase::validateSceneModel(GLGlobeBase *view, const std::size_t width, const std::size_t height, const MapCamera2::Mode mode, const double nearMeters, const double farMeters) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (view->sceneModelVersion != view->drawVersion) {
        WriteLock wlock(view->renderPasses0Mutex);

        view->renderPasses[0].scene.set(
                view->displayDpi,
                width,
                height,
                view->renderPasses[0].drawSrid,
                GeoPoint2(view->renderPasses[0].drawLat, view->renderPasses[0].drawLng, view->renderPasses[0].drawAlt, TAK::Engine::Core::AltitudeReference::HAE),
                view->renderPasses[0].focusx,
                view->renderPasses[0].focusy,
                view->renderPasses[0].drawRotation,
                view->renderPasses[0].drawTilt,
                view->renderPasses[0].drawMapResolution,
                nearMeters,
                farMeters,
                mode);

        GLGlobeBase_glScene(view->renderPasses[0].scene);

        {
            // fill the forward matrix for the Model-View
            double matrixD[16];
            view->renderPasses[0].scene.forwardTransform.get(matrixD, Matrix2::COLUMN_MAJOR);
            for (int i = 0; i < 16; i++)
                view->renderPasses[0].sceneModelForwardMatrix[i] = (float)matrixD[i];
        }

        // mark as valid
        view->sceneModelVersion = view->drawVersion;
    }

    return code;
}
// nested classes
GLGlobeBase::State::State() NOTHROWS :
    drawMapScale(2.5352504279048383E-9),
    drawMapResolution(0.0),
    drawLat(0.0),
    drawLng(0.0),
    drawAlt(0.0),
    drawRotation(0.0),
    drawTilt(0.0),
    animationFactor(0.3),
    drawVersion(0),
    targeting(false),
    westBound(-180.0),
    southBound(-90.0),
    northBound(90.0),
    eastBound(180.0),
    left(0),
    right(0),
    top(0),
    bottom(0),
    near(1.f),
    far(-1.f),
    drawSrid(-1),
    animationLastTick(-1),
    animationDelta(-1)
{}
GLGlobeBase::State::State(const GLGlobeBase &view) NOTHROWS
{
    State_save(this, view);
}
// Globals
void TAK::Engine::Renderer::Core::GLGlobeBase_glScene(MapSceneModel2 &scene) NOTHROWS
{
    Matrix2 verticalFlipTranslate;
    verticalFlipTranslate.setToTranslate(0.0, (double)scene.height, 0.0);
    Matrix2 verticalFlipScale(1, 0, 0, 0,
                              0, -1, 0, 0,
                              0, 0, 1, 0,
                              0, 0, 0, 1);

    // account for flipping of y-axis for OpenGL coordinate space
    scene.inverseTransform.concatenate(verticalFlipTranslate);
    scene.inverseTransform.concatenate(verticalFlipScale);

    scene.forwardTransform.preConcatenate(verticalFlipScale);
    scene.forwardTransform.preConcatenate(verticalFlipTranslate);
}

namespace {
    void asyncSetBaseMap(void *opaque) NOTHROWS
    {
        std::unique_ptr<std::pair<GLGlobeBase&, GLMapRenderable2Ptr>> p(static_cast<std::pair<GLGlobeBase &, GLMapRenderable2Ptr> *>(opaque));
        p->first.setBaseMap(std::move(p->second));
    }

#ifndef __ANDROID__
    void asyncSetLabelManager(void* opaque) NOTHROWS
    {
        std::unique_ptr<std::pair<GLGlobeBase&, GLLabelManager*>> p(static_cast<std::pair<GLGlobeBase&, GLLabelManager*>*>(opaque));
        p->first.setLabelManager(std::move(p->second));
    }
#endif
    bool hasSettled(double dlat, double dlng, double dscale, double drot, double dtilt, double dfocusX, double dfocusY) NOTHROWS
    {
        return IS_TINY(dlat) &&
               IS_TINY(dlng) &&
               IS_TINY(dscale) &&
               IS_TINY(drot) &&
               IS_TINY(dtilt) &&
               IS_TINYF(dfocusX) &&
               IS_TINYF(dfocusY);
    }

    void State_save(GLGlobeBase::State *value, const GLGlobeBase& view) NOTHROWS
    {
        value->drawMapScale = view.renderPasses[0].drawMapScale;
        value->drawMapResolution = view.renderPasses[0].drawMapResolution;
        value->drawLat = view.renderPasses[0].drawLat;
        value->drawLng = view.renderPasses[0].drawLng;
        value->drawAlt = view.renderPasses[0].drawAlt;
        value->drawRotation = view.renderPasses[0].drawRotation;
        value->drawTilt = view.renderPasses[0].drawTilt;
        value->animationFactor = view.animationFactor;
        value->drawVersion = view.renderPasses[0].drawVersion;
        value->targeting = view.targeting;
        value->isScreenshot = view.isScreenshot;
        value->westBound = view.renderPasses[0].westBound;
        value->southBound = view.renderPasses[0].southBound;
        value->northBound = view.renderPasses[0].northBound;
        value->eastBound = view.renderPasses[0].eastBound;
        value->left = view.renderPasses[0].left;
        value->right = view.renderPasses[0].right;
        value->top = view.renderPasses[0].top;
        value->bottom = view.renderPasses[0].bottom;
        value->near = view.renderPasses[0].near;
        value->far = view.renderPasses[0].far;
        value->drawSrid = view.renderPasses[0].drawSrid;
        value->focusx = view.renderPasses[0].focusx;
        value->focusy = view.renderPasses[0].focusy;
        value->upperLeft = view.renderPasses[0].upperLeft;
        value->upperRight = view.renderPasses[0].upperRight;
        value->lowerRight = view.renderPasses[0].lowerRight;
        value->lowerLeft = view.renderPasses[0].lowerLeft;
        value->settled = view.settled;
        value->renderPump = view.renderPasses[0].renderPump;
        value->animationLastTick = view.animationLastTick;
        value->animationDelta = view.animationDelta;
        value->sceneModelVersion = view.sceneModelVersion;
        value->scene = view.renderPasses[0].scene;
        memcpy(value->sceneModelForwardMatrix, view.renderPasses[0].sceneModelForwardMatrix, sizeof(float) * 16);
        value->drawHorizon = view.renderPasses[0].drawHorizon;
        value->crossesIDL = view.renderPasses[0].crossesIDL;
        value->displayDpi = view.displayDpi;
    }
}