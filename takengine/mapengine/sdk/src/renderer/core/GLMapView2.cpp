#include "renderer/core/GLMapView2.h"

#include <algorithm>
#include <cmath>
#include <list>
#include <sstream>
#include <unordered_map>

#include <GLES2/gl2.h>

#include "core/GeoPoint.h"
#include "core/LegacyAdapters.h"
#include "core/ProjectionFactory.h"
#include "core/ProjectionFactory3.h"
#include "feature/GeometryTransformer.h"
#include "feature/LineString2.h"
#include "feature/Envelope2.h"
#include "math/AABB.h"
#include "math/Statistics.h"
#include "math/Rectangle.h"
#include "math/Ellipsoid2.h"
#include "math/Frustum2.h"
#include "math/Frustum2.h"
#include "math/Sphere2.h"
#include "math/Mesh.h"
#include "model/MeshTransformer.h"
#include "port/Platform.h"
#include "port/STLVectorAdapter.h"
#include "port/STLListAdapter.h"
#include "raster/osm/OSMUtils.h"
#include "renderer/GLDepthSampler.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLOffscreenFramebuffer.h"
#include "renderer/GLTexture2.h"
#include "renderer/GLSLUtil.h"
#include "renderer/GLWireframe.h"
#include "renderer/GLWorkers.h"
#include "renderer/Shader.h"
#include "renderer/core/GLOffscreenVertex.h"
#include "renderer/core/GLLayerFactory2.h"
#include "renderer/core/GLLabelManager.h"
#include "renderer/elevation/GLTerrainTile.h"
#include "renderer/elevation/ElMgrTerrainRenderService.h"
#include "thread/Monitor.h"
#include "util/ConfigOptions.h"
#include "util/Memory.h"
#include "util/MathUtils.h"
#include "util/Distance.h"

using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Elevation;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::core;
using namespace atakmap::raster::osm;
using namespace atakmap::renderer;

#define GLMAPVIEW2_DEPTH_ENABLED 1
#define _EPSILON 0.0001
#define _EPSILON_F 0.01
#define TILT_WIDTH_ADJUST_FACTOR 1.05
#define DEBUG_DRAW_MESH_SKIRT 0

#define DEFAULT_TILT_SKEW_OFFSET 1.2
#define DEFAULT_TILT_SKEW_MULT 4.0

#define IS_TINY(v) \
    (std::abs(v) <= _EPSILON)
#define IS_TINYF(v) \
    (std::abs(v) <= _EPSILON_F)

#define SAMPLER_SIZE 8u

namespace {

    const double recommendedGridSampleDistance = 0.125;
    double maxeldiff = 1000.0;
    double maxSkewAdj = 1.25;

    struct AsyncAnimateBundle
    {
        double lat;
        double lon;
        double scale;
        double rot;
        double tilt;
        double factor;
    };

    struct AsyncRenderableRefreshBundle
    {
        std::list<std::shared_ptr<GLLayer2>> renderables;
        std::list<std::shared_ptr<GLLayer2>> releaseables;
    };

    struct AsyncResizeBundle
    {
        std::size_t width;
        std::size_t height;
    };

    struct AsyncSurfaceIntersectBundle
    {
        float x;
        float y;
        GLMapView2 *view;
        GeoPoint2 result;
        TAKErr code;
        bool done;
        Monitor *monitor;
    };

    struct AsyncSurfacePickBundle
    {
        float x;
        float y;
        GLMapView2 *view;
        std::shared_ptr<const TerrainTile> result[9u];
        TAKErr code;
        bool done;
        Monitor *monitor;
    };

    class DebugTimer
    {
    public :
        DebugTimer(const char *text, GLMapView2 &view_, const bool enabled_) NOTHROWS:
            start(Platform_systime_millis()),
            view(view_),
            enabled(enabled_)
        {
            if(enabled)
                msg << text;
        }
    public :
        void stop() NOTHROWS
        {
            if(enabled) {
                msg << " " << (Platform_systime_millis() - start) << "ms";
                view.addRenderDiagnosticMessage(msg.str().c_str());
            }
        }
    private :
        std::ostringstream msg;
        GLMapView2 &view;
        int64_t start;
        bool enabled;
    };

    bool hasSettled(double dlat, double dlng, double dscale, double drot, double dtilt, double dfocusX, double dfocusY) NOTHROWS;

    double estimateDistanceToHorizon(const GeoPoint2 &cam, const GeoPoint2 &tgt) NOTHROWS;

    void wrapCorner(GeoPoint2 &value) NOTHROWS;

    template<class T>
    TAKErr forwardImpl(float *value, const size_t dstSize, const T *src, const size_t srcSize, const size_t count, const MapSceneModel2 &sm) NOTHROWS;

    template<class T>
    TAKErr inverseImpl(T *value, const size_t dstSize, const float *src, const size_t srcSize, const size_t count, const MapSceneModel2 &sm) NOTHROWS;

    Matrix2 &xformVerticalFlipScale() NOTHROWS;
    MapSceneModel2 createOffscreenSceneModel(GLMapView2::State *value, const GLMapView2 &view, const double offscreenResolution, const double tilt, const std::size_t drawSurfaceWidth, const std::size_t drawSurfaceHeight, const float x, const float y, const float width, const float height) NOTHROWS;

    Point2<double> adjustCamLocation(const MapSceneModel2 &model) NOTHROWS;

    template<class T>
    void debugBounds(const T &view) NOTHROWS;
    template<class T>
    void updateBoundsImpl(T *value, const bool continuousScrollEnabled) NOTHROWS;
    template<class T>
    TAKErr updateLatLonAABBoxEllipsoidImpl(T *value) NOTHROWS;

    GeoPoint2 getPoint(const AtakMapView &view) NOTHROWS
    {
        GeoPoint legacy;
        view.getPoint(&legacy, false);
        GeoPoint2 retval;
        GeoPoint_adapt(&retval, legacy);
        return retval;
    }

    float getFocusX(const AtakMapView &view) NOTHROWS
    {
        atakmap::math::Point<float> focus;
        view.getController()->getFocusPoint(&focus);
        return focus.x;
    }

    float getFocusY(const AtakMapView &view) NOTHROWS
    {
        atakmap::math::Point<float> focus;
        view.getController()->getFocusPoint(&focus);
        return focus.y;
    }

    template<class T>
    void Void_deleter_const(const void *opaque)
    {
        const T *impl = static_cast<const T *>(opaque);
        delete impl;
    }

    void asyncProjUpdate(void *opaque) NOTHROWS;
    void asyncSetBaseMap(void *opaque) NOTHROWS;

    bool intersectsAABB(GeoPoint2 *value, const MapSceneModel2 &scene, const TAK::Engine::Feature::Envelope2 &aabb_wgs84, float x, float y) NOTHROWS
    {
        Point2<double> org(x, y, -1.0);
        Point2<double> tgt(x, y, 1.0);

        if (scene.inverseTransform.transform(&org, org) != TE_Ok)
            return false;
        if (scene.inverseTransform.transform(&tgt, tgt) != TE_Ok)
            return false;

        Point2<double> points[8];
        if (scene.projection->forward(&points[0], GeoPoint2(aabb_wgs84.minY, aabb_wgs84.minX, aabb_wgs84.minZ, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
            return false;
        if(scene.projection->forward(&points[1], GeoPoint2(aabb_wgs84.minY, aabb_wgs84.maxX, aabb_wgs84.minZ, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
        return false;
        if (scene.projection->forward(&points[2], GeoPoint2(aabb_wgs84.maxY, aabb_wgs84.maxX, aabb_wgs84.minZ, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
        return false;
        if (scene.projection->forward(&points[3], GeoPoint2(aabb_wgs84.maxY, aabb_wgs84.minX, aabb_wgs84.minZ, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
        return false;
        if (scene.projection->forward(&points[4], GeoPoint2(aabb_wgs84.minY, aabb_wgs84.minX, aabb_wgs84.maxZ, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
        return false;
        if (scene.projection->forward(&points[5], GeoPoint2(aabb_wgs84.minY, aabb_wgs84.maxX, aabb_wgs84.maxZ, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
        return false;
        if (scene.projection->forward(&points[6], GeoPoint2(aabb_wgs84.maxY, aabb_wgs84.maxX, aabb_wgs84.maxZ, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
        return false;
        if (scene.projection->forward(&points[7], GeoPoint2(aabb_wgs84.maxY, aabb_wgs84.minX, aabb_wgs84.maxZ, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
            return false;

        AABB aabb(points, 8u);
#if false
        if (aabb.contains(org) && aabb.contains(tgt)) {
            return true;
        }
#endif

        return scene.inverse(value, Point2<float>(x, y), aabb) == TE_Ok;
    }

    void asyncProjUpdate(void* opaque) NOTHROWS;
    void asyncSetBaseMap(void* opaque) NOTHROWS;
    void asyncSetLabelManager(void* opaque) NOTHROWS;

    struct MapRendererRunnable
    {
        MapRendererRunnable(std::unique_ptr<void, void(*)(void *)> &&opaque_, void(*run_)(void *)) :
            opaque(std::move(opaque_)),
            run(run_)
        {}

        std::unique_ptr<void, void(*)(void *)> opaque;
        void(*run)(void *);
    };
    void glMapRendererRunnable(void *opaque)
    {
        std::unique_ptr<MapRendererRunnable> arg(static_cast<MapRendererRunnable *>(opaque));
        arg->run(arg->opaque.get());
    }

    void State_save(GLMapView2::State *value, const GLMapView2 &view) NOTHROWS;
    void State_restore(GLMapView2 *view, const GLMapView2::State &state) NOTHROWS;

    //https://stackoverflow.com/questions/1903954/is-there-a-standard-sign-function-signum-sgn-in-c-c
    template <typename T>
    int sgn(T val)
    {
        return (T(0) < val) - (val < T(0));
    }

    TAKErr validateSceneModel(GLMapView2 *view, const std::size_t width, const std::size_t height) NOTHROWS;


    TAKErr intersectWithTerrainTiles(GeoPoint2 *value, const MapSceneModel2 &map_scene, std::shared_ptr<const TerrainTile> &focusTile, const std::shared_ptr<const TerrainTile> *tiles, const std::size_t numTiles, const float x, const float y) NOTHROWS;

    void drawTerrainMeshes(GLMapView2 &view, const std::vector<GLTerrainTile> &terrainTile, const TerrainTileShaders &ecef, const TerrainTileShaders &planar, const GLTexture2 &whitePixel) NOTHROWS;
    void drawTerrainMeshesImpl(const GLMapView2::State &renderPass, const TerrainTileShader &shader, const Matrix2 &mvp, const Matrix2 *local, const std::size_t numLocal, const std::vector<GLTerrainTile> &terrainTiles, const float r, const float g, const float b, const float a) NOTHROWS;
    void drawTerrainMeshImpl(const GLMapView2::State &state, const TerrainTileShader &shader, const Matrix2 &mvp, const Matrix2 *local, const std::size_t numLocal, const GLTerrainTile &tile, const float r, const float g, const float b, const float a) NOTHROWS;
    TAKErr lla2ecef_transform(Matrix2 *value, const Projection2 &ecef, const Matrix2 *localFrame) NOTHROWS;

    float meshColors[13][4] =
    {
        {1.f, 1.f, 1.f, 1.f},
        {1.f, 0.f, 0.f, 1.f},
        {0.f, 1.f, 0.f, 1.f},
        {0.f, 0.f, 1.f, 1.f},
        {1.f, 1.f, 0.f, 1.f},
        {1.f, 0.f, 1.f, 1.f},
        {0.f, 1.f, 1.f, 1.f},
        {1.f, 0.5f, 0.5f, 1.f},
        {0.5f, 1.0f, 0.5f, 1.f},
        {0.5f, 0.5f, 1.0f, 1.f},
        {0.5f, 1.0f, 1.0f, 1.f},
        {1.0f, 1.0f, 0.5f, 1.f},
        {1.0f, 0.5f, 1.0f, 1.f},
    };
}

class GLMapView2::Offscreen
{
public:
    Offscreen() NOTHROWS :
        lastTerrainVersion(-1),
        hfactor(NAN),
        terrainEnabled(1),
        lastElevationQuery(0LL),
        depthSamplerFbo(nullptr, nullptr)
    {
        ecef.color.hi.base.handle = 0;
        ecef.depth.hi.base.handle = 0;
        ecef.color.md.base.handle = 0;
        ecef.depth.md.base.handle = 0;
        ecef.color.lo.base.handle = 0;
        ecef.depth.lo.base.handle = 0;
        planar.color.hi.base.handle = 0;
        planar.depth.hi.base.handle = 0;
        planar.color.md.base.handle = 0;
        planar.depth.md.base.handle = 0;
        planar.color.lo.base.handle = 0;
        planar.depth.lo.base.handle = 0;

        fbo[0] = 0u;
        fbo[1] = 0u;
    }

    /** offscreen texture */
public:
    std::unique_ptr<GLTexture2> texture;
    std::unique_ptr<GLTexture2> whitePixel;

    /** offscreen FBO handles, index 0 is FBO, index 1 is depth buffer */
    GLuint fbo[2];

    int lastTerrainVersion;

    double hfactor = NAN;
    int terrainEnabled;

    struct {
        TerrainTileShaders color;
        TerrainTileShaders depth;
    } ecef;
    struct {
        TerrainTileShaders color;
        TerrainTileShaders depth;
    } planar;

    Statistics elevationStats;
    int64_t lastElevationQuery;
    std::vector<std::shared_ptr<const TerrainTile>> terrainTiles;
    std::vector<GLTerrainTile> visibleTiles;
    std::unordered_map<const TerrainTile *, GLTerrainTile> gltiles;
    GLOffscreenFramebufferPtr depthSamplerFbo;
};

struct GLMapView2::AsyncRunnable
{
    enum EventType {
        MapMoved,
        ProjectionChanged,
        ElevationExaggerationFactorChanged,
        FocusChanged,
        LayersChanged,
    };

    GLMapView2 &owner;
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
    AsyncRunnable(GLMapView2 &owner, const std::shared_ptr<Mutex> &mutex) NOTHROWS;
};

GLMapView2::GLMapView2(RenderContext &ctx, AtakMapView &aview,
    int left, int bottom,
    int right, int top) NOTHROWS : left(left), bottom(bottom),
    right(right), top(top), drawLat(0),
    drawLng(0), drawRotation(0), drawMapScale(0),
    drawMapResolution(0),
    animationFactor(1.0), drawVersion(0),
    targeting(false), westBound(-180),
    southBound(-90), northBound(90),
    eastBound(180), drawSrid(-1),
    focusx(0), focusy(0), upperLeft(),
    upperRight(), lowerRight(), lowerLeft(),
    settled(true), renderPump(0),
    scene(aview.getDisplayDpi(),
        static_cast<int>(aview.getWidth()),
        static_cast<int>(aview.getHeight()),
        aview.getProjection(),
        getPoint(aview),
        getFocusX(aview),
        getFocusY(aview),
        aview.getMapRotation(),
        aview.getMapTilt(),
        aview.getMapResolution()),
    offscreen(nullptr, nullptr),
    terrain(new ElMgrTerrainRenderService(ctx), Memory_deleter_const<TerrainRenderService, ElMgrTerrainRenderService>),
    context(ctx), view(aview),
    renderables(), basemap(nullptr, nullptr),
    labelManager(nullptr),
    asyncRunnablesMutex(new Mutex(TEMT_Recursive)),
    disposed(new bool(false)),
    verticalFlipTranslate(),
    verticalFlipTranslateHeight(-1),
    sceneModelVersion(drawVersion - 1),
    animationLastTick(0LL),
    animationDelta(0LL),
    drawHorizon(false),
    pixelDensity(1.0),
    drawTilt(0.0),
    enableMultiPassRendering(true),
    debugDrawBounds(false),
    elevationScaleFactor(aview.getElevationExaggerationFactor()),
    near(1),
    far(-1),
    numRenderPasses(0u),
    terrainBlendFactor(1.0f),
    renderPass(nullptr),
    debugDrawOffscreen(false),
    dbgdrawflags(4),
    poleInView(false),
    debugDrawMesh(false),
    debugDrawDepth(false),
    suspendMeshFetch(false),
    tiltSkewOffset(DEFAULT_TILT_SKEW_OFFSET),
    tiltSkewMult(DEFAULT_TILT_SKEW_MULT),
    displayDpi(aview.getDisplayDpi()),
    continuousScrollEnabled(aview.isContinuousScrollEnabled()),
    hardwareTransformResolutionThreshold(1.0),
    layerRenderersMutex(TEMT_Recursive),
    diagnosticMessagesEnabled(false),
    inRenderPump(false)
{
    verticalFlipTranslateHeight = top - bottom + 1;
    verticalFlipTranslate.translate(0, static_cast<double>(verticalFlipTranslateHeight));

    sceneModelVersion = drawVersion - 1;

    drawSrid = scene.projection->getSpatialReferenceID();
    atakmap::core::GeoPoint oldCenter;
    GeoPoint2 center;
    view.getPoint(&oldCenter);
    atakmap::core::GeoPoint_adapt(&center, oldCenter);
    drawLat = center.latitude;
    drawLng = center.longitude;
    drawRotation = view.getMapRotation();
    drawTilt = view.getMapTilt();
    drawMapScale = view.getMapScale();
    drawMapResolution = view.getMapResolution(drawMapScale);
    atakmap::math::Point<float> p;
    view.getController()->getFocusPoint(&p);
    focusx = p.x;
    focusy = p.y;

    startAnimating(drawLat, drawLng, drawMapScale, drawRotation, drawTilt, 1.0);
    startAnimatingFocus(focusx, focusy, 1.0);

    State_save(&this->renderPasses[0u], *this);
    this->renderPasses[0u].renderPass = GLMapView2::Sprites | GLMapView2::Surface | GLMapView2::Scenes | GLMapView2::XRay;
    this->renderPasses[0u].texture = 0;
    this->renderPasses[0u].basemap = true;
    this->renderPasses[0u].debugDrawBounds = this->debugDrawBounds;
    this->renderPasses[0u].viewport.x = static_cast<float>(left);
    this->renderPasses[0u].viewport.y = static_cast<float>(bottom);
    this->renderPasses[0u].viewport.width = static_cast<float>(right-left);
    this->renderPasses[0u].viewport.height = static_cast<float>(top-bottom);

    this->renderPass = this->renderPasses;

    this->diagnosticMessagesEnabled = !!ConfigOptions_getIntOptionOrDefault("glmapview.render-diagnostics", 0);
#ifdef __ANDROID__
    this->gpuTerrainIntersect = !!ConfigOptions_getIntOptionOrDefault("glmapview.surface-rendering-v2", 0);
#else
    this->gpuTerrainIntersect = !!ConfigOptions_getIntOptionOrDefault("glmapview.surface-rendering-v2", 1);
#endif
}



GLMapView2::~GLMapView2() NOTHROWS
{
    this->stop();

    if (this->basemap.get()) {
        this->basemap.reset();
    }

    Lock lock(*asyncRunnablesMutex);
    *disposed = true;

    if(mapMovedEvent.get() && mapMovedEvent->enqueued)
        mapMovedEvent.release();
    if(projectionChangedEvent.get() && projectionChangedEvent->enqueued)
        projectionChangedEvent.release();
    if(focusChangedEvent.get() && focusChangedEvent->enqueued)
        focusChangedEvent.release();
    if(layersChangedEvent.get() && layersChangedEvent->enqueued)
        layersChangedEvent.release();
    if(mapResizedEvent.get() && mapResizedEvent->enqueued)
        mapResizedEvent.release();
}

TAKErr GLMapView2::start() NOTHROWS
{
    this->terrain->start();

    this->view.addLayersChangedListener(this);
    std::list<Layer *> layers;
    view.getLayers(layers);
    refreshLayers(layers);

    this->view.addMapElevationExaggerationFactorListener(this);
    mapElevationExaggerationFactorChanged(&this->view, this->view.getElevationExaggerationFactor());

    this->view.addMapProjectionChangedListener(this);
    mapProjectionChanged(&this->view);

    this->view.addMapMovedListener(this);
    mapMoved(&this->view, false);

    this->view.getController()->addFocusPointChangedListener(this);
    atakmap::math::Point<float> focus;
    this->view.getController()->getFocusPoint(&focus);
    mapControllerFocusPointChanged(this->view.getController(), &focus);

    this->view.addMapResizedListener(this);
    left = 0;
    right = static_cast<int>(view.getWidth());
    top = static_cast<int>(view.getHeight());
    bottom = 0;

    this->tiltSkewOffset = ConfigOptions_getDoubleOptionOrDefault("glmapview.tilt-skew-offset", DEFAULT_TILT_SKEW_OFFSET);
    this->tiltSkewMult = ConfigOptions_getDoubleOptionOrDefault("glmapview.tilt-skew-mult", DEFAULT_TILT_SKEW_MULT);

    this->displayDpi = view.getDisplayDpi();

    return TE_Ok;
}

TAKErr GLMapView2::stop() NOTHROWS
{
    this->view.removeLayersChangedListener(this);
    this->view.removeMapElevationExaggerationFactorListener(this);
    this->view.removeMapProjectionChangedListener(this);
    this->view.removeMapMovedListener(this);
    this->view.removeMapResizedListener(this);
    this->view.getController()->removeFocusPointChangedListener(this);

    // clear all the renderables
    std::list<Layer *> empty;
    refreshLayers(empty);

    if (this->labelManager)
        this->labelManager->stop();

    this->terrain->stop();

    return TE_Ok;
}

void GLMapView2::setRenderDiagnosticsEnabled(const bool enabled) NOTHROWS
{
    diagnosticMessagesEnabled = enabled;
}
void GLMapView2::addRenderDiagnosticMessage(const char *msg) NOTHROWS
{
    if (!msg)
        return;
    if(diagnosticMessagesEnabled)
        diagnosticMessages.push_back(msg);
}

TAKErr GLMapView2::registerControl(const Layer2 &layer, const char *type, void *ctrl) NOTHROWS
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
TAKErr GLMapView2::unregisterControl(const TAK::Engine::Core::Layer2 &layer, const char *type, void *ctrl) NOTHROWS
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
TAKErr GLMapView2::visitControls(bool *visited, void *opaque, TAKErr(*visitor)(void *opaque, const Layer2 &layer, const Control &ctrl), const Layer2 &layer, const char *type) NOTHROWS
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
TAKErr GLMapView2::visitControls(bool *visited, void *opaque, TAKErr(*visitor)(void *opaque, const Layer2 &layer, const Control &ctrl), const Layer2 &layer) NOTHROWS
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
TAKErr GLMapView2::visitControls(void *opaque, TAKErr(*visitor)(void *opaque, const Layer2 &layer, const Control &ctrl)) NOTHROWS
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
bool GLMapView2::isContinuousScrollEnabled() const NOTHROWS
{
    return this->continuousScrollEnabled;
}
TAKErr GLMapView2::addOnControlsChangedListener(TAK::Engine::Core::MapRenderer::OnControlsChangedListener *l) NOTHROWS
{
    if (!l)
        return TE_InvalidArg;

    TAKErr code(TE_Ok);
    Lock lock(controlsMutex);
    TE_CHECKRETURN_CODE(lock.status);

    controlsListeners.insert(l);
    return code;
}
TAKErr GLMapView2::removeOnControlsChangedListener(TAK::Engine::Core::MapRenderer::OnControlsChangedListener *l) NOTHROWS
{
    if (!l)
        return TE_InvalidArg;

    TAKErr code(TE_Ok);
    Lock lock(controlsMutex);
    TE_CHECKRETURN_CODE(lock.status);

    controlsListeners.erase(l);
    return code;
}
RenderContext &GLMapView2::getRenderContext() const NOTHROWS
{
    return context;
}
void GLMapView2::initOffscreenShaders() NOTHROWS
{
    GLOffscreenFramebuffer::Options opts;
    opts.colorFormat = GL_RGBA;
    opts.colorType = GL_UNSIGNED_BYTE;
    GLOffscreenFramebuffer_create(offscreen->depthSamplerFbo, SAMPLER_SIZE, SAMPLER_SIZE, opts);

    this->offscreen->whitePixel.reset(new GLTexture2(1u, 1u, Bitmap2::RGB565));
    this->offscreen->whitePixel->setMinFilter(GL_NEAREST);
    this->offscreen->whitePixel->setMagFilter(GL_NEAREST);
    {
        const uint16_t px = 0xFFFFu;
        this->offscreen->whitePixel->load(&px, 0, 0, 1, 1);
    }

    // set up the offscreen shaders
    offscreen->ecef.color = GLTerrainTile_getColorShader(this->context, 4978);
    offscreen->ecef.depth = GLTerrainTile_getDepthShader(this->context, 4978);
    offscreen->planar.color = GLTerrainTile_getColorShader(this->context, 4326);
    offscreen->planar.depth = GLTerrainTile_getDepthShader(this->context, 4326);
}
bool GLMapView2::initOffscreenRendering() NOTHROWS
{
#define DBG_GL_ERR() \
        { GLenum errv; while((errv=glGetError()) != GL_NO_ERROR) Logger_log(TELL_Info, "GLMapView2::initOffscreenRendering() GL Error %s at %d: %d", __FILE__, __LINE__, (int)errv); }

    DBG_GL_ERR();

    if (isnan(offscreen->hfactor))
#if 0
        offscreen->hfactor = ConfigOptions.getOption("glmapview.offscreen->hfactor", 3.5d);
#else
        offscreen->hfactor = 3.5;
#endif

    float wfactor = 1.25;
    auto hfactor = (float)offscreen->hfactor;

    int ivalue;
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &ivalue);
    DBG_GL_ERR();

    // 4096x4096 is the absoluate maximum texture we will try to allocate
    ivalue = std::min(ivalue, 4096);

    const int offscreenTextureWidth = std::min((int)((float)view.getWidth()*wfactor), ivalue);
    const int offscreenTextureHeight = std::min((int)((float)view.getHeight()*hfactor), ivalue);

    const int textureSize = std::max(offscreenTextureWidth, offscreenTextureHeight);

    // Using 565 without any alpha to avoid alpha drawing overlays from becoming darker
    // when the map is tilted. Alternatively glBlendFuncSeparate could be used for all
    // glBlendFunc calls where srcAlpha is set to 0 and dstAlpha is 1
    this->offscreen->texture.reset(new GLTexture2(textureSize, textureSize, Bitmap2::RGB565));
    DBG_GL_ERR();
    this->offscreen->texture->setMinFilter(GL_LINEAR);
    DBG_GL_ERR();
    this->offscreen->texture->setMagFilter(GL_LINEAR);
    DBG_GL_ERR();
    this->offscreen->texture->init();

    if (!this->offscreen->texture->getTexId()) {
        Logger_log(TELL_Info, "GLMapView2::initOffscreenRendering() failed to generate texture");
        return false;
    }

    bool fboCreated = false;
    do {
        if (this->offscreen->fbo[0] == 0)
            glGenFramebuffers(1, this->offscreen->fbo);
        DBG_GL_ERR();
        if (this->offscreen->fbo[1] == 0)
            glGenRenderbuffers(1, this->offscreen->fbo + 1);
        DBG_GL_ERR();
        glBindRenderbuffer(GL_RENDERBUFFER, this->offscreen->fbo[1]);
        DBG_GL_ERR();
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, static_cast<GLsizei>(this->offscreen->texture->getTexWidth()),
            static_cast<GLsizei>(this->offscreen->texture->getTexHeight()));
        DBG_GL_ERR();
        glBindRenderbuffer(GL_RENDERBUFFER, 0);
        DBG_GL_ERR();

        glBindFramebuffer(GL_FRAMEBUFFER, this->offscreen->fbo[0]);
        DBG_GL_ERR();

        // clear any pending errors
        while (glGetError() != GL_NO_ERROR)
            ;
        glFramebufferTexture2D(GL_FRAMEBUFFER,
            GL_COLOR_ATTACHMENT0,
            GL_TEXTURE_2D, this->offscreen->texture->getTexId(), 0);
        DBG_GL_ERR();
        // XXX - observing hard crash following bind of "complete"
        //       FBO on SM-T230NU. reported error is 1280 (invalid
        //       enum) on glFramebufferTexture2D. I have tried using
        //       the color-renderable formats required by GLES 2.0
        //       (RGBA4, RGB5_A1, RGB565) but all seem to produce
        //       the same outcome.
        if (glGetError() != GL_NO_ERROR)
            break;

        glFramebufferRenderbuffer(GL_FRAMEBUFFER,
            GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER,
            this->offscreen->fbo[1]);
        DBG_GL_ERR();
        const int fboStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        DBG_GL_ERR();
        fboCreated = (fboStatus == GL_FRAMEBUFFER_COMPLETE);
    } while (false);

    return fboCreated;
}
void GLMapView2::mapMoved(atakmap::core::AtakMapView* map_view, bool animate)
{
    atakmap::core::GeoPoint p;
    map_view->getPoint(&p);

    Lock lock(*asyncRunnablesMutex);
    if(!mapMovedEvent.get())
        mapMovedEvent.reset(new AsyncRunnable(*this, asyncRunnablesMutex));
    mapMovedEvent->target.lat = p.latitude;
    mapMovedEvent->target.lon = p.longitude;
    mapMovedEvent->target.rot = map_view->getMapRotation();
    mapMovedEvent->target.tilt = map_view->getMapTilt();
    mapMovedEvent->target.scale = map_view->getMapScale();
    mapMovedEvent->target.factor = animate ? 0.3f : 1.0f;

    if(!mapMovedEvent->enqueued) {
        context.queueEvent(asyncAnimate, std::unique_ptr<void, void(*)(const void *)>(mapMovedEvent.get(), Memory_leaker_const<void>));
        mapMovedEvent->enqueued = false;
    }
}

void GLMapView2::setBaseMap(GLMapRenderable2Ptr &&map) NOTHROWS
{
    if (!context.isRenderThread()) {
        std::unique_ptr<std::pair<GLMapView2&, GLMapRenderable2Ptr>> p(new std::pair<GLMapView2&, GLMapRenderable2Ptr>(*this, std::move(map)));
        context.queueEvent(asyncSetBaseMap, std::unique_ptr<void, void(*)(const void *)>(p.release(), Memory_leaker_const<void>));
    }
    else {
        basemap = std::move(map);
    }
}

void GLMapView2::setLabelManager(GLLabelManager* labelMan) NOTHROWS
{
    if (!context.isRenderThread()) {
        std::unique_ptr<std::pair<GLMapView2&, GLLabelManager*>> p(new std::pair<GLMapView2&, GLLabelManager*>(*this, std::move(labelMan)));
        context.queueEvent(asyncSetLabelManager, std::unique_ptr<void, void(*)(const void *)>(p.release(), Memory_leaker_const<void>));
    }
    else {
        labelManager = std::move(labelMan);
    }
}

GLLabelManager* GLMapView2::getLabelManager() const NOTHROWS
{
    return labelManager;
}

/**
* Transforms the given LLA into a coordinate in the GL coordinate space.
* @param value	returns the corresponding GL coordinate
* @param geo	the latitude/longitude/altitude
* @return TE_Ok on success, various codes on failure
*/
TAKErr GLMapView2::forward(Point2<float> *value, const GeoPoint2 &geo) const NOTHROWS
{
    return scene.forward(value, geo);
}

void GLMapView2::startAnimating(double lat, double lng, double scale, double rotation,
double tilt, double animateFactor) NOTHROWS
{
    animationState.targetLat = lat;
    animationState.targetLng = lng;
    animationState.targetMapScale = scale;
    animationState.targetRotation = rotation;
    animationState.targetTilt = tilt;
    animationFactor = animateFactor;
    settled = false;
}

void GLMapView2::startAnimatingFocus(float x, float y, double animateFactor) NOTHROWS
{
    animationState.targetFocusx = x;
    animationState.targetFocusy = y;
    animationFactor = animateFactor;
    settled = false;
}

void GLMapView2::mapProjectionChanged(atakmap::core::AtakMapView* map_view)
{
    int srid = map_view->getProjection();
    std::unique_ptr<std::pair<GLMapView2 *, int>> p(new std::pair<GLMapView2 *, int>(this, srid));
    context.queueEvent(asyncProjUpdate, std::unique_ptr<void, void(*)(const void *)>(p.release(), Memory_leaker_const<void>));
}

void GLMapView2::mapLayerAdded(atakmap::core::AtakMapView* map_view, atakmap::core::Layer *layer)
{
    std::list<Layer *> layers;
    map_view->getLayers(layers);
    refreshLayers(layers);
}
void GLMapView2::mapLayerRemoved(atakmap::core::AtakMapView* map_view, atakmap::core::Layer *layer)
{
    std::list<Layer *> layers;
    map_view->getLayers(layers);
    refreshLayers(layers);
}
void GLMapView2::mapLayerPositionChanged(atakmap::core::AtakMapView* mapView, atakmap::core::Layer *layer, const int oldPosition, const int newPosition)
{
    std::list<Layer *> layers;
    mapView->getLayers(layers);
    refreshLayers(layers);
}

// XXX - next 2 -- need to be using start/stop to protect subject access

void GLMapView2::refreshLayersImpl(const std::list<std::shared_ptr<GLLayer2>> &toRender, const std::list<std::shared_ptr<GLLayer2>> &toRelease) NOTHROWS
{
    renderables.clear();

    std::list<std::shared_ptr<GLLayer2>>::const_iterator it;

    for (it = toRender.begin(); it != toRender.end(); it++)
        renderables.push_back(std::shared_ptr<GLLayer2>(*it));

    for (it = toRelease.begin(); it != toRelease.end(); it++)
        (*it)->release();
}

void GLMapView2::refreshLayers(const std::list<atakmap::core::Layer *> &layers) NOTHROWS
{
    std::list<Layer *>::const_iterator layersIter;

    Lock lock(layerRenderersMutex);

    // validate layer adapters
    std::map<const Layer *, std::shared_ptr<Layer2>> invalidLayerAdapters;
    std::map<const Layer *, std::shared_ptr<Layer2>>::iterator adapterIter;
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
            std::shared_ptr<Layer> layer = LayerPtr(*layersIter, Memory_leaker_const<Layer>);
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
            if (GLLayerFactory2_create(gllayerPtr, *this, *layer) == TE_Ok) {
                glLayer = std::move(gllayerPtr);
                if (glLayer.get()) {
                    layerRenderers[layer] = std::shared_ptr<GLLayer2>(glLayer);
                    glLayer->start();
                }
            }
        }
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


void GLMapView2::asyncRefreshLayers(void *opaque) NOTHROWS
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

void GLMapView2::mapResized(atakmap::core::AtakMapView *mapView)
{
    Lock lock(*asyncRunnablesMutex);
    if(!mapResizedEvent.get())
        mapResizedEvent.reset(new AsyncRunnable(*this, this->asyncRunnablesMutex));
    mapResizedEvent->resize.width = static_cast<std::size_t>(mapView->getWidth());
    mapResizedEvent->resize.height = static_cast<std::size_t>(mapView->getHeight());
    if(!mapResizedEvent->enqueued) {
        context.queueEvent(glMapResized, std::unique_ptr<void, void(*)(const void *)>(mapResizedEvent.get(), Memory_leaker_const<void>));
        mapResizedEvent->enqueued = true;
    }
}

void GLMapView2::mapControllerFocusPointChanged(atakmap::core::AtakMapController *controller, const atakmap::math::Point<float> * const focus)
{
    Lock lock(*asyncRunnablesMutex);
    if(!focusChangedEvent.get())
        focusChangedEvent.reset(new AsyncRunnable(*this, asyncRunnablesMutex));
    focusChangedEvent->focus.x = focus->x;
    focusChangedEvent->focus.y = focus->y;
    if(!focusChangedEvent->enqueued) {
        context.queueEvent(asyncAnimateFocus, std::unique_ptr<void, void(*)(const void *)>(focusChangedEvent.get(), Memory_leaker_const<void>));
        focusChangedEvent->enqueued = true;
    }
}

void GLMapView2::mapElevationExaggerationFactorChanged(atakmap::core::AtakMapView *map_view, const double factor)
{
    std::unique_ptr<std::pair<GLMapView2 &, double>> opaque(new std::pair<GLMapView2 &, double>(*this, factor));
    if (context.isRenderThread())
        glElevationExaggerationFactorChanged(opaque.release());
    else
        context.queueEvent(glElevationExaggerationFactorChanged, std::unique_ptr<void, void(*)(const void *)>(opaque.release(), Memory_leaker_const<void>));
}

void GLMapView2::glElevationExaggerationFactorChanged(void *opaque) NOTHROWS
{
    std::unique_ptr<std::pair<GLMapView2 &, double>> arg(static_cast<std::pair<GLMapView2 &, double> *>(opaque));
    arg->first.elevationScaleFactor = arg->second;
}


/**
* Bulk transforms a number of points from LLA to GL coordinate space.
* @param value     Returns the transformed points
* @param dstSize   Specifies the number of components for the destination
*                  buffer. A value of 2 for x,y pairs, a value of 3 for
*                  x,y,z triplets. Other values will result in
*                  TE_InvalidArg being returned.
* @param src       The source coordinate buffer
* @param srcSize   Specifies the components in the source buffer. A value
*                  of 2 indicates longitude,latitude pairs (in that
*                  order); a value of 3 indicates
*                  longitude,latitude,altitude (m HAE) triplets (in that
*                  order). Other values will result in TE_InvalidArg being
*                  returned.
* @param count		The number of points in the source buffer.
* @return	TE_Ok on success, various codes on failure.
*/

TAKErr GLMapView2::forward(float *value, const size_t dstSize, const double *src, const size_t srcSize, const size_t count) const NOTHROWS
{
    return forwardImpl<double>(value, dstSize, src, srcSize, count, this->scene);
}

TAKErr GLMapView2::forward(float *value, const size_t dstSize, const float *src, const size_t srcSize, const size_t count) const NOTHROWS
{
    return forwardImpl<float>(value, dstSize, src, srcSize, count, this->scene);
}


/**
* Transforms the given GL coordinate space point into LLA.
* @param value	returns the corresponding latitude/longitude/altitude
* @param point	A point in GL coordinate space
* @return TE_Ok on success, various codes on failure
*/
TAKErr GLMapView2::inverse(TAK::Engine::Core::GeoPoint2 *value, const Point2<float> &point) const NOTHROWS
{
    return scene.inverse(value, point);
}

/**
* Bulk transforms a number of points from GL coordinate space to LLA.
* @param value     Returns the transformed points
* @param dstSize   Specifies the components in the output buffer. A value
*                  of 2 indicates longitude,latitude pairs (in that
*                  order); a value of 3 indicates
*                  longitude,latitude,altitude (m HAE) triplets (in that
*                  order). Other values will result in TE_InvalidArg being
*                  returned.
* @param src       The source coordinate buffer
* @param srcSize   Specifies the number of components for the source
*                  buffer. A value of 2 for x,y pairs, a value of 3 for
*                  x,y,z triplets. Other values will result in
*                  TE_InvalidArg being returned.
* @param count		The number of points in the source buffer.
* @return	TE_Ok on success, various codes on failure.
*/
TAKErr GLMapView2::inverse(double *value, const size_t dstSize, const float *src, const size_t srcSize, const size_t count) const NOTHROWS
{
    return inverseImpl<double>(value, dstSize, src, srcSize, count, this->scene);
}

TAKErr GLMapView2::inverse(float *value, const size_t dstSize, const float *src, const size_t srcSize, const size_t count) const NOTHROWS
{
    return inverseImpl<float>(value, dstSize, src, srcSize, count, this->scene);
}

void GLMapView2::render() NOTHROWS
{
    const int64_t tick = Platform_systime_millis();
    if (this->animationLastTick)
        this->animationDelta = tick - this->animationLastTick;
    else
        glGetError();
    this->animationLastTick = tick;
    this->renderPump++;

    this->prepareScene();
    this->drawRenderables();

    const int64_t renderPumpElapsed = Platform_systime_millis()-tick;

    if (diagnosticMessagesEnabled) {
        std::ostringstream dbg;
        dbg << "render pump " << renderPumpElapsed << "ms";
        diagnosticMessages.push_back(dbg.str());
        GLText2 *text = GLText2_intern(TextFormatParams(24));
        if (text) {
            TextFormat2 &fmt = text->getTextFormat();
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glPushMatrix();
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glTranslatef(16, top - 32 - fmt.getCharHeight(), 0);

            text->draw("Renderer Diagnostics", 1, 0, 0, 1);
            

            for (auto it = diagnosticMessages.begin(); it != diagnosticMessages.end(); it++) {
                atakmap::renderer::GLES20FixedPipeline::getInstance()->glTranslatef(0, -fmt.getCharHeight() + 4, 0);
                text->draw((*it).c_str(), 1, 0, 0, 1);
            }
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glPopMatrix();
        }
        diagnosticMessages.clear();
    }
}
void GLMapView2::release() NOTHROWS
{
    for(auto it = renderables.begin(); it != renderables.end(); it++)
        (*it)->release();
    if(basemap)
        basemap->release();
}

void GLMapView2::prepareScene() NOTHROWS
{
    if (animate()) {
        drawVersion++;
        if (offscreen.get())
            offscreen->lastTerrainVersion = ~offscreen->lastTerrainVersion;
    }

    // validate scene model
    validateSceneModel(this, static_cast<size_t>(view.getWidth()), static_cast<size_t>(view.getHeight()));
    this->oscene = this->scene;

    this->near = static_cast<float>(scene.camera.near);
    this->far = static_cast<float>(scene.camera.far);

    updateBoundsImpl<GLMapView2>(this, this->continuousScrollEnabled);

    GLES20FixedPipeline *fixedPipe = GLES20FixedPipeline::getInstance();
    fixedPipe->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);
    fixedPipe->glLoadIdentity();

    if (GLMAPVIEW2_DEPTH_ENABLED) {
        ::glDepthMask(true);
        ::glEnable(GL_DEPTH_TEST);

        ::glDepthRangef(0.0f, 1.0f);
        ::glClearDepthf(1.0f);
        ::glDepthFunc(GL_LEQUAL);
    }

	GLWorkers_doResourceLoadingWork(30);
    GLWorkers_doGLThreadWork();
}

int GLMapView2::getTerrainVersion() const NOTHROWS
{
    if (this->offscreen) {
        do {
            ReadLock lock(this->offscreenMutex);
            TE_CHECKBREAK_CODE(lock.status);
            return this->offscreen->lastTerrainVersion;
        } while(false);
    }

    return 0;
}

TAKErr GLMapView2::visitTerrainTiles(TAKErr(*visitor)(void *opaque, const std::shared_ptr<const TerrainTile> &tile) NOTHROWS, void *opaque) NOTHROWS
{
    TAKErr code(TE_Ok);
    ReadLock lock(this->offscreenMutex);
    TE_CHECKRETURN_CODE(lock.status);

    if(this->offscreen) {
        for (auto tile = offscreen->terrainTiles.cbegin(); tile != offscreen->terrainTiles.cend(); tile++) {
            code = visitor(opaque, *tile);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);
    }
    return code;
}

void GLMapView2::drawRenderables() NOTHROWS
{
    this->numRenderPasses = 0u;

    GLint currentFbo;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &currentFbo);

    GLES20FixedPipeline *fixedPipe = GLES20FixedPipeline::getInstance();
    fixedPipe->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_TEXTURE);
    fixedPipe->glLoadIdentity();

    fixedPipe->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION);
    fixedPipe->glOrthof(static_cast<float>(left), static_cast<float>(right), static_cast<float>(bottom), static_cast<float>(top), near, far);

    fixedPipe->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);
    fixedPipe->glLoadIdentity();

    // XXX - if tilt, set FBO to texture
    bool offscreenSurfaceRendering = false;

    // last render pass is always remainder
    {
        WriteLock wlock(renderPassMutex);

        State_save(&this->renderPasses[this->numRenderPasses], *this);
        this->renderPasses[this->numRenderPasses].renderPass =
                GLMapView2::Sprites | GLMapView2::Surface | GLMapView2::Scenes | GLMapView2::XRay;
        this->renderPasses[this->numRenderPasses].texture = 0;
        this->renderPasses[this->numRenderPasses].basemap = true;
        this->renderPasses[this->numRenderPasses].debugDrawBounds = this->debugDrawBounds;
        this->renderPasses[this->numRenderPasses].viewport.x = static_cast<float>(left);
        this->renderPasses[this->numRenderPasses].viewport.y = static_cast<float>(bottom);
        this->renderPasses[this->numRenderPasses].viewport.width = static_cast<float>(right - left);
        this->renderPasses[this->numRenderPasses].viewport.height = static_cast<float>(top - bottom);
        this->numRenderPasses++;
    }
    this->renderPass = this->renderPasses;

#define __EXP_XRAY_MODE 1

    if (this->enableMultiPassRendering
#if !__EXP_XRAY_MODE
        && this->drawTilt > 0.0
#endif
        ) {

        if (!this->offscreen.get()) {
            this->offscreen = std::unique_ptr<Offscreen, void(*)(const Offscreen *)>(new Offscreen(), Memory_deleter_const<Offscreen>);
            this->initOffscreenShaders();
        }

        // doing offscreen rendering if tilt or perspective camera
        offscreenSurfaceRendering = (this->drawTilt > 0.0 || this->scene.camera.mode == MapCamera2::Perspective);

        // if doing offscreen rendering, ensure that the texture/FBO is created
        if(offscreenSurfaceRendering && !this->offscreen->texture.get())
            offscreenSurfaceRendering = this->initOffscreenRendering();
        // bind target FBO as appropriate
        if(offscreenSurfaceRendering)
            glBindFramebuffer(GL_FRAMEBUFFER, this->offscreen->fbo[0]);
        else
            glBindFramebuffer(GL_FRAMEBUFFER, currentFbo);

    }

    bool terrainUpdate = !offscreen.get() || (offscreen->lastTerrainVersion != terrain->getTerrainVersion());
    if(offscreen.get() && !offscreen->terrainTiles.empty())
        terrainUpdate |= (offscreen->terrainTiles[0]->data.srid != drawSrid);
    if(offscreen.get() && !offscreen->terrainTiles.empty())
        terrainUpdate &= !suspendMeshFetch;

    if (terrainUpdate) {
        DebugTimer te_timer("Terrain Update", *this, diagnosticMessagesEnabled);

        std::list<std::shared_ptr<const TerrainTile>> terrainTiles;
        int terrainTilesVersion = -1;

        STLListAdapter<std::shared_ptr<const TerrainTile>> tta(terrainTiles);
        terrainTilesVersion = terrain->getTerrainVersion();
        terrain->lock(tta, this->renderPasses[0u].scene, 4326, this->renderPasses[0u].drawVersion);
        if (!this->offscreen.get()) {
            terrain->unlock(tta);
            terrainTiles.clear();
        }

        if (this->focusEstimation.tile.get()) {
            bool focusTileValid = false;
            for (auto it = terrainTiles.begin(); it != terrainTiles.end(); it++) {
                if ((*it).get() == this->focusEstimation.tile.get()) {
                    focusTileValid = true;
                    break;
                }
            }
            if (!focusTileValid)
                this->focusEstimation.tile.reset();
        }

        std::unordered_map<const TerrainTile *, GLTerrainTile> staleTiles(offscreen->gltiles);
        {
            WriteLock lock(this->offscreenMutex);

            if (!offscreen->terrainTiles.empty()) {
                STLVectorAdapter<std::shared_ptr<const TerrainTile>> toUnlock(offscreen->terrainTiles);
                this->terrain->unlock(toUnlock);
            }

            this->offscreen->terrainTiles.clear();
            for (auto tile = terrainTiles.cbegin(); tile != terrainTiles.cend(); tile++) {
                this->offscreen->terrainTiles.push_back(*tile);
                staleTiles.erase((*tile).get());
            }
            this->offscreen->lastTerrainVersion = terrainTilesVersion;
        }

        std::vector<GLuint> ids;
        ids.reserve(staleTiles.size()*2u);

        for(auto it = staleTiles.begin(); it != staleTiles.end(); it++) {
            offscreen->gltiles.erase(it->first);
            if(it->second.vbo)
                ids.push_back(it->second.vbo);
            if(it->second.ibo)
                ids.push_back(it->second.ibo);
        }

        if(ids.size())
            glDeleteBuffers((GLsizei)ids.size(), &ids.at(0));

        te_timer.stop();
    }

    if(diagnosticMessagesEnabled) {
        std::ostringstream strm;
        strm << "Terrain tiles " << offscreen->terrainTiles.size()
             << " (instances " << TerrainTile::getLiveInstances() << "/" << TerrainTile::getTotalInstances()
             << " allocs " << TerrainTile::getHeapAllocations() << ")";
        addRenderDiagnosticMessage(strm.str().c_str());
    }
    // frustum cull the visible tiles
    if (this->offscreen.get()) {
        DebugTimer vistiles_timer("Visible Terrain Tile Culling", *this, diagnosticMessagesEnabled);
        this->offscreen->visibleTiles.clear();
        this->offscreen->visibleTiles.reserve(this->offscreen->terrainTiles.size());

        const bool handleIdlCrossing = this->scene.displayModel->earth->getGeomClass() == GeometryModel2::PLANE && this->crossesIDL;
        Matrix2 m(scene.camera.projection);
        m.concatenate(scene.camera.modelView);
        Frustum2 frustum(m);
        for (std::size_t i = 0u; i < this->offscreen->terrainTiles.size(); i++) {
            // compute AABB in WCS and check for intersection with the frustum
            TAK::Engine::Feature::Envelope2 aabbWCS(this->offscreen->terrainTiles[i]->aabb_wgs84);
            TAK::Engine::Feature::GeometryTransformer_transform(&aabbWCS, aabbWCS, 4326, drawSrid);
            if (frustum.intersects(AABB(Point2<double>(aabbWCS.minX, aabbWCS.minY, aabbWCS.minZ), Point2<double>(aabbWCS.maxX, aabbWCS.maxY, aabbWCS.maxZ))) ||
                (handleIdlCrossing && drawLng*((aabbWCS.minX+aabbWCS.maxX)/2.0) < 0 &&
                    frustum.intersects(
                        AABB(Point2<double>(aabbWCS.minX-(360.0*sgn((aabbWCS.minX+aabbWCS.maxX)/2.0)), aabbWCS.minY, aabbWCS.minZ),
                             Point2<double>(aabbWCS.maxX-(360.0*sgn((aabbWCS.minX+aabbWCS.maxX)/2.0)), aabbWCS.maxY, aabbWCS.maxZ))))) {

                auto entry = offscreen->gltiles.find(offscreen->terrainTiles[i].get());
                if(entry == offscreen->gltiles.end()) {
                    GLTerrainTile gltile;
                    gltile.tile = offscreen->terrainTiles[i];

                    // XXX - VBOs seem to be slowing down WinTAK during map
                    //       movements. I believe this may be due to ANGLE/D3D
                    //       management of buffer objects. Using allocation
                    //       pool may mitigate. 
#ifdef __ANDROID__
                    GLuint bufs[2u];
                    glGenBuffers(2u, bufs);

                    const TAK::Engine::Model::Mesh &mesh =  *gltile.tile->data.value;

                    // VBO
                    do {
                        const void* buf = nullptr;
                        const VertexDataLayout vertexDataLayout = mesh.getVertexDataLayout();

                        glBindBuffer(GL_ARRAY_BUFFER, bufs[0u]);
                        if(mesh.getVertices(&buf, TEVA_Position) != TE_Ok)
                            break;
                        std::size_t size = mesh.getNumVertices() * vertexDataLayout.position.stride;

                        if (vertexDataLayout.interleaved)
                            VertexDataLayout_requiredInterleavedDataSize(&size, vertexDataLayout, mesh.getNumVertices());
                        glBufferData(GL_ARRAY_BUFFER, size, buf, GL_STATIC_DRAW);
                        glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);

                        gltile.vbo = bufs[0u];
                        bufs[0u] = GL_NONE;
                    } while(false);

                    // IBO
                    if(mesh.isIndexed()) {
                        do {
                            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, bufs[1u]);
                            DataType indexType;
                            if(mesh.getIndexType(&indexType) != TE_Ok)
                                break;
                            std::size_t size = mesh.getNumIndices() * DataType_size(indexType);
                            glBufferData(GL_ELEMENT_ARRAY_BUFFER, size, static_cast<const uint8_t *>(mesh.getIndices()) + mesh.getIndexOffset(), GL_STATIC_DRAW);
                            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, GL_NONE);

                            gltile.ibo = bufs[1u];
                            bufs[1u] = GL_NONE;
                        } while(false);
                    }
                    // used have been zero'd; delete any unused
                    glDeleteBuffers(2u, bufs);
#endif
                    this->offscreen->visibleTiles.push_back(gltile);
                    this->offscreen->gltiles[this->offscreen->terrainTiles[i].get()] = gltile;
                } else {
                    this->offscreen->visibleTiles.push_back(entry->second);
                }
            }
        }
        vistiles_timer.stop();
    }

    if (offscreenSurfaceRendering) {
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);


        // XXX - this bit of code will adjust the scene (center, bounds and
        //       offscreen texture size) based on an estimation of the scene
        //       center given the local elevation statistics. the best method
        //       for bounds discovery is likely a frustum intersect against
        //       the display geometry surface, using the minimum and maximum
        //       elevation values from the collected nodes.

        // adjust the scene as necessary to account for shifting during tilt
        // due to local elevation
        {
            // obtain focus point at altitude
            State stack;
            State_save(&stack, *this);

            DebugTimer focusest_timer("Focus Estimation", *this, diagnosticMessagesEnabled);
            if (this->focusEstimation.sceneModelVersion != this->sceneModelVersion || this->focusEstimation.terrainVersion != this->offscreen->lastTerrainVersion) {
                if (intersectWithTerrainImpl(&focusEstimation.point, this->focusEstimation.tile, this->scene, focusx, top-focusy) != TE_Ok) {
                    GeoPoint altAdjustedCenter;
                    view.getPoint(&altAdjustedCenter, true);
                    GeoPoint_adapt(&focusEstimation.point, altAdjustedCenter);
                }

                this->focusEstimation.sceneModelVersion = this->sceneModelVersion;
                this->focusEstimation.terrainVersion = this->offscreen->lastTerrainVersion;
            }
            focusest_timer.stop();

            // adjust the center
            drawLat = focusEstimation.point.latitude;
            drawLng = focusEstimation.point.longitude;

            sceneModelVersion = ~sceneModelVersion;
            validateSceneModel(this, scene.width, scene.height);
            updateBoundsImpl(this, this->continuousScrollEnabled);

            // reconstruct the bounds for the base renderpass
            this->renderPasses[0u].drawLat = this->drawLat;
            this->renderPasses[0u].drawLng = this->drawLng;
            this->renderPasses[0u].northBound = this->northBound;
            this->renderPasses[0u].westBound = this->westBound;
            this->renderPasses[0u].southBound = this->southBound;
            this->renderPasses[0u].eastBound = this->eastBound;
            this->renderPasses[0u].upperLeft = this->upperLeft;
            this->renderPasses[0u].upperRight = this->upperRight;
            this->renderPasses[0u].lowerRight = this->lowerRight;
            this->renderPasses[0u].lowerLeft = this->lowerLeft;

            // construct multiple offscreen passes to capture surface texture at multiple resolutions
#if 0
            constructOffscreenRenderPass(this->drawMapResolution*1.0, true, view.getWidth(), view.getHeight(), 0, 0, offscreen->texture->getTexWidth() / 2.0, offscreen->texture->getTexHeight());
            constructOffscreenRenderPass(this->drawMapResolution*4.0, true, view.getWidth(), view.getHeight(), offscreen->texture->getTexWidth() / 2.0, 0, offscreen->texture->getTexWidth() / 4.0, offscreen->texture->getTexHeight());
            constructOffscreenRenderPass(this->drawMapResolution*32.0, true, view.getWidth(), view.getHeight(), offscreen->texture->getTexWidth() / 4.0 * 3.0, 0, offscreen->texture->getTexWidth() / 4.0, offscreen->texture->getTexHeight());
#else
            // XXX - legacy behavior

            {
                const double tiltSkew = sin(this->drawTilt*M_PI/180.0);

                // compute an adjustment to be applied to the scale based on the
                // current tilt
                double scaleAdj;
                if(scene.camera.mode == MapCamera2::Perspective)
                    scaleAdj = 1.0 + (tiltSkew*1.1);
                else 
                    scaleAdj = this->tiltSkewOffset + (tiltSkew*this->tiltSkewMult);

                // we're going to stretch the capture texture vertically to try
                // to closely match the AOI with the perspective skew. If we
                // did not adjust the texture dimensions, the AOI defined by
                // simply zooming out would request way more data than we are
                // actually interested in
#if 1
                const int targetOffscreenTextureHeight = (int)std::ceil(scaleAdj*view.getHeight());
                const int targetOffscreenTextureWidth = (int)view.getWidth();

                std::size_t offscreenTexWidth = std::min(targetOffscreenTextureWidth, static_cast<int>(offscreen->texture->getTexWidth()));
                std::size_t offscreenTexHeight = std::min(targetOffscreenTextureHeight, static_cast<int>(offscreen->texture->getTexHeight()));

                // capture actual scale adjustment
                scaleAdj *= std::max((double)targetOffscreenTextureHeight / (double)offscreenTexHeight, (double)targetOffscreenTextureWidth / (double)offscreenTexWidth);

                offscreenTexWidth = offscreen->texture->getTexWidth();
                offscreenTexHeight = offscreen->texture->getTexHeight();

#else
                const int targetOffscreenTextureWidth = (int)ceil((double)view.getWidth() / (scaleAdj*0.75));
                const int targetOffscreenTextureHeight = (int)std::min(ceil((double)view.getHeight() * scaleAdj), (double)offscreen->texture->getTexHeight());

                std::size_t offscreenTexWidth = (std::size_t)targetOffscreenTextureWidth;
                std::size_t offscreenTexHeight = (std::size_t)targetOffscreenTextureHeight;
#endif

                // if the pole is in view, make sure we capture full 180deg longitude
                if (drawSrid != 4326) {
                    const double fullEquitorialExtentPixels = atakmap::core::AtakMapView_getFullEquitorialExtentPixels(displayDpi);
                    const double mapScale180 = offscreenTexWidth / fullEquitorialExtentPixels;

                    if (poleInView) {
                        const double estimatedScale = AtakMapView_getMapScale(displayDpi, drawMapResolution*scaleAdj);
                        if (mapScale180 < estimatedScale) {
                            scaleAdj = AtakMapView_getMapResolution(displayDpi, mapScale180) / drawMapResolution;
                        }
                    } else {

                        scaleAdj *= std::max((5.0*(1.0-tiltSkew))*(1.0 - cos(drawLat*M_PI / 180.0)), 1.0);
                    }
                }
                constructOffscreenRenderPass(!poleInView, drawMapResolution * scaleAdj, 0.0, true, static_cast<std::size_t>(view.getWidth()), static_cast<std::size_t>(view.getHeight()), 0, 0, static_cast<float>(offscreenTexWidth), static_cast<float>(offscreenTexHeight));
                renderPasses[this->numRenderPasses-1u].drawMapResolution = drawMapResolution;
                renderPasses[this->numRenderPasses-1u].drawMapScale = drawMapScale;
            }
#endif
            State_restore(this, stack);
        }

        if (GLMAPVIEW2_DEPTH_ENABLED) {
            glDepthMask(GL_FALSE);
            glDisable(GL_DEPTH_TEST);
        }
    }
#if __EXP_XRAY_MODE
    else {
        renderPasses[this->numRenderPasses] = renderPasses[this->numRenderPasses-1u];
        renderPasses[this->numRenderPasses].texture = 1;
        renderPasses[this->numRenderPasses].renderPass = RenderPass::Surface;
        this->numRenderPasses++;
    }
#endif

    if (GLMAPVIEW2_DEPTH_ENABLED && !offscreenSurfaceRendering)
        glDepthFunc(GL_ALWAYS);

    // clean up initial render pass
    for (std::size_t i = 1u; i < this->numRenderPasses; i++) {
        renderPasses[0u].renderPass &= ~renderPasses[i].renderPass;
        renderPasses[0u].basemap &= !renderPasses[i].basemap;
        renderPasses[0u].debugDrawBounds &= !renderPasses[i].debugDrawBounds;
    }

    inRenderPump = true;

#if 1
    DebugTimer offscreenPass_timer("Offscreen Render Passes", *this, diagnosticMessagesEnabled);
    // render all offscreen passes
    for (std::size_t i = this->numRenderPasses; i > 0u; i--) {
        if (!renderPasses[i - 1u].texture)
            continue;
        this->drawRenderables(this->renderPasses[i - 1u]);
    }
    offscreenPass_timer.stop();
#endif
    // if tilt, reset the FBO to the display and render the captured scene
#if !__EXP_XRAY_MODE
    if (offscreenSurfaceRendering) {
#else
    GLint depthFunc;
    glGetIntegerv(GL_DEPTH_FUNC, &depthFunc);
    GLboolean depthMask;
    glGetBooleanv(GL_DEPTH_WRITEMASK, &depthMask);
    GLboolean depthEnabled = glIsEnabled(GL_DEPTH_TEST);
    {
#endif

        if (GLMAPVIEW2_DEPTH_ENABLED) {
            glDepthMask(GL_TRUE);
            glEnable(GL_DEPTH_TEST);
            glDepthFunc(GL_LEQUAL);
        }
        
        glViewport(static_cast<GLint>(renderPasses[0u].viewport.x), static_cast<GLint>(renderPasses[0u].viewport.y), static_cast<GLsizei>(renderPasses[0u].viewport.width), static_cast<GLsizei>(renderPasses[0u].viewport.height));

        GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION);
        GLES20FixedPipeline::getInstance()->glOrthof(static_cast<float>(renderPasses[0u].left), static_cast<float>(renderPasses[0u].right), static_cast<float>(renderPasses[0u].bottom), static_cast<float>(renderPasses[0u].top), renderPasses[0u].near, renderPasses[0u].far);

        GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);

        // reset FBO to display
        if (offscreenSurfaceRendering) {
            glBindFramebuffer(GL_FRAMEBUFFER, currentFbo);
            glClearColor(0, 0, 0, 1);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        } else {
            glClear(GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        }


        if (debugDrawOffscreen) {
            float otc[8];
            otc[0] = 0;
            otc[1] = 0;
            otc[2] = 0;
            otc[3] = 1;
            otc[4] = 1;
            otc[5] = 0;
            otc[6] = 1;
            otc[7] = 1;
            float ovc[8];
            ovc[0] = static_cast<float>(left);
            ovc[1] = static_cast<float>(bottom);
            ovc[2] = static_cast<float>(left);
            ovc[3] = static_cast<float>(top);
            ovc[4] = static_cast<float>(right);
            ovc[5] = static_cast<float>(bottom);
            ovc[6] = static_cast<float>(right);
            ovc[7] = static_cast<float>(top);
            GLTexture2_draw(offscreen->texture->getTexId(), GL_TRIANGLE_STRIP, 4u, GL_FLOAT, otc, GL_FLOAT, ovc);
        } else {
            //if (GLMAPVIEW2_DEPTH_ENABLED) {
            //    glDepthFunc(GL_LEQUAL);
            //}
            DebugTimer terrainDepth_timer("Render Terrain", *this, diagnosticMessagesEnabled);
            // if not doing offscreen rendering, we want to write to the terrain to the depth buffer, but not update the color buffer
            if(!offscreenSurfaceRendering)
                glColorMask(GL_FALSE, GL_FALSE, GL_FALSE, GL_FALSE);

            if(!debugDrawMesh) {
                const GLTexture2 *tex = offscreenSurfaceRendering ? offscreen->texture.get() : offscreen->whitePixel.get();
                drawTerrainTiles(*tex,
                                 static_cast<std::size_t>(view.getWidth()),
                                 static_cast<std::size_t>(view.getHeight()));
            }if(debugDrawMesh)
                drawTerrainMeshes();

            // re-enable color mask
            if(!offscreenSurfaceRendering)
                glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
            terrainDepth_timer.stop();
        }
    }

#if __EXP_XRAY_MODE
    if (!offscreenSurfaceRendering) {
        // restore depth state
        glDepthFunc(depthFunc);
        glDepthMask(depthMask);
        if (depthEnabled)
            glEnable(GL_DEPTH_TEST);
        else
            glDisable(GL_DEPTH_TEST);
    }
#endif
#if 1
    DebugTimer onscreenPasses_timer("On Screen Render Passes", *this, diagnosticMessagesEnabled);
    // execute all on screen passes
    for (std::size_t i = this->numRenderPasses; i > 0u; i--) {
        if (renderPasses[i - 1u].texture)
            continue;
        this->drawRenderables(this->renderPasses[i - 1u]);
    }
    onscreenPasses_timer.stop();

    DebugTimer labels_timer("Labels", *this, diagnosticMessagesEnabled);
    if (labelManager)
        labelManager->draw(*this, RenderPass::Sprites);
    labels_timer.stop();

    DebugTimer uipass_timer("UI Pass", *this, diagnosticMessagesEnabled);
    // execute UI pass
    {
        // clear the depth buffer
        glClear(GL_DEPTH_BUFFER_BIT);

        State uipass(this->renderPasses[0u]);
        uipass.basemap = false;
        uipass.texture = GL_NONE;
        uipass.renderPass = RenderPass::UserInterface;
        this->drawRenderables(uipass);
    }
    uipass_timer.stop();
#endif
    inRenderPump = false;
}

void GLMapView2::drawRenderables(const GLMapView2::State &renderState) NOTHROWS
{
    // save the current view state
    State viewState;
    State_save(&viewState, *this);

    // load the render state
    State_restore(this, renderState);
    this->idlHelper.update(*this);
    this->renderPass = &renderState;

    glViewport(static_cast<GLint>(renderState.viewport.x), static_cast<GLint>(renderState.viewport.y), static_cast<GLsizei>(renderState.viewport.width), static_cast<GLsizei>(renderState.viewport.height));

    GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION);
    GLES20FixedPipeline::getInstance()->glPushMatrix();
    GLES20FixedPipeline::getInstance()->glOrthof(static_cast<float>(renderState.left), static_cast<float>(renderState.right), static_cast<float>(renderState.bottom), static_cast<float>(renderState.top), renderState.near, renderState.far);

    GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);
    GLES20FixedPipeline::getInstance()->glPushMatrix();

    // render the basemap and layers. this will always include the surface
    // pass and may also include the sprites pass
    if (renderState.basemap && this->basemap.get())
        this->basemap->draw(*this, renderState.renderPass);

    std::list<std::shared_ptr<GLLayer2>>::iterator it;
    for (it = this->renderables.begin(); it != this->renderables.end(); it++) {
#ifdef MSVC
        GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);
#endif
        if ((*it)->getRenderPass()&renderState.renderPass)
            (*it)->draw(*this, renderState.renderPass);
    }

    // debug draw bounds if requested
    if (renderState.debugDrawBounds)
        debugBounds(renderState);

    // restore the view state
    State_restore(this, viewState);
    this->idlHelper.update(*this);

    // restore the transforms
    GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION);
    GLES20FixedPipeline::getInstance()->glPopMatrix();

    GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);
    GLES20FixedPipeline::getInstance()->glPopMatrix();

    // restore the viewport
    glViewport(viewState.left, viewState.bottom, viewState.right-viewState.left, viewState.top-viewState.bottom);
}

void GLMapView2::drawTerrainTiles(const GLTexture2 &tex, const std::size_t drawSurfaceWidth, const std::size_t drawSurfaceHeight) NOTHROWS
{
    TerrainTileShaders *shaders = 
        debugDrawDepth ?
            ((this->renderPasses[0].drawSrid == 4978) ? &offscreen->ecef.depth : &offscreen->planar.depth) :
            ((this->renderPasses[0].drawSrid == 4978) ? &offscreen->ecef.color : &offscreen->planar.color);

    if (debugDrawDepth) {
        GLTerrainTile_drawTerrainTiles(this->renderPasses, this->numRenderPasses, &offscreen->visibleTiles.at(0), offscreen->visibleTiles.size(), *shaders, tex, (float)elevationScaleFactor, 1.f, 1.f, 1.f, 1.f);
    } else {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GLTerrainTile_drawTerrainTiles(this->renderPasses, this->numRenderPasses, &offscreen->visibleTiles.at(0), offscreen->visibleTiles.size(), *shaders, tex, (float)elevationScaleFactor, 1.f, 1.f, 1.f, (float)terrainBlendFactor);
        glDisable(GL_BLEND);
    }
}
void GLMapView2::drawTerrainMeshes() NOTHROWS
{
    ::drawTerrainMeshes(*this, offscreen->visibleTiles, offscreen->ecef.color, offscreen->planar.color, *offscreen->whitePixel);
}
TAKErr GLMapView2::constructOffscreenRenderPass(const bool preserveBounds, const double resolution, const double tilt, const bool base_map, const std::size_t drawSurfaceWidth, const std::size_t drawSurfaceHeight, const float x, const float y, const float width, const float height) NOTHROWS
{
    State bnds;
    bnds.upperLeft = this->upperLeft;
    bnds.upperRight = this->upperRight;
    bnds.lowerRight = this->lowerRight;
    bnds.lowerLeft = this->lowerLeft;
    bnds.northBound = this->northBound;
    bnds.southBound = this->southBound;
    bnds.westBound = this->westBound;
    bnds.eastBound = this->eastBound;
    bnds.crossesIDL = this->crossesIDL;
    bnds.drawHorizon = this->drawHorizon;

    State &pass = this->renderPasses[this->numRenderPasses];
    createOffscreenSceneModel(&pass, *this, resolution, tilt, drawSurfaceWidth, drawSurfaceHeight, x, y, width, height);

    GeoPoint2 focus;
    pass.scene.inverse(&focus, Point2<float>(pass.scene.focusX, pass.scene.height-pass.scene.focusY));
    pass.drawLat = focus.latitude;
    pass.drawLng = focus.longitude;

    // use tilt bounds
    if (preserveBounds) {
        pass.upperLeft = bnds.upperLeft;
        pass.upperRight = bnds.upperRight;
        pass.lowerRight = bnds.lowerRight;
        pass.lowerLeft = bnds.lowerLeft;
        pass.northBound = bnds.northBound;
        pass.southBound = bnds.southBound;
        pass.westBound = bnds.westBound;
        pass.eastBound = bnds.eastBound;
        pass.crossesIDL = bnds.crossesIDL;
        pass.drawHorizon = bnds.drawHorizon;
    } else {
        // recompute bounds
        updateBoundsImpl(&this->renderPasses[this->numRenderPasses], this->continuousScrollEnabled);
    }

    this->renderPasses[this->numRenderPasses].renderPass = GLMapView2::Surface;
    this->renderPasses[this->numRenderPasses].texture = this->offscreen->texture->getTexId();
    this->renderPasses[this->numRenderPasses].basemap = base_map;
    this->renderPasses[this->numRenderPasses].debugDrawBounds = this->debugDrawBounds;
    this->numRenderPasses++;

    return TE_Ok;
}

bool GLMapView2::animate() NOTHROWS
{
    if (settled)
    return false;

    this->animationLastUpdate = this->animationLastTick;

    double scaleDelta = (animationState.targetMapScale - drawMapScale);
    double latDelta = (animationState.targetLat - drawLat);
    double lngDelta = (animationState.targetLng - drawLng);
    double focusxDelta = (animationState.targetFocusx - focusx);
    double focusyDelta = (animationState.targetFocusy - focusy);

    drawMapScale += scaleDelta * animationFactor;
    drawLat += latDelta * animationFactor;
    drawLng += lngDelta * animationFactor;
    focusx += (float)(focusxDelta * animationFactor);
    focusy += (float)(focusyDelta * animationFactor);

    double rotDelta = (animationState.targetRotation - drawRotation);

    // Go the other way
    if (fabs(rotDelta) > 180) {
        if (rotDelta < 0) {
            drawRotation -= 360;
        }
        else {
            drawRotation += 360;
        }
        rotDelta = (animationState.targetRotation - drawRotation);
    }

    double tiltDelta = (animationState.targetTilt - drawTilt);
    drawTilt += tiltDelta * animationFactor;

    //drawRotation += rotDelta * 0.1;
    drawRotation += rotDelta * animationFactor;

    settled = hasSettled(latDelta, lngDelta, scaleDelta, rotDelta, 0.0,
        focusxDelta, focusyDelta);

    if (settled) {
        drawMapScale = animationState.targetMapScale;
        drawLat = animationState.targetLat;
        drawLng = animationState.targetLng;
        drawRotation = animationState.targetRotation;
        drawTilt = animationState.targetTilt;
        focusx = animationState.targetFocusx;
        focusy = animationState.targetFocusy;
    }

    drawMapResolution = view.getMapResolution(drawMapScale);

    return true;
}

TAKErr GLMapView2::getTerrainMeshElevation(double *value, const double latitude, const double longitude_) const NOTHROWS
{
    TAKErr code(TE_InvalidArg);

    *value = NAN;

    // wrap the longitude if necessary
    double longitude = longitude_;
    if(longitude > 180.0)
        longitude -= 360.0;
    else if(longitude < -180.0)
        longitude += 360.0;

    double elevation = NAN;
    {
        ReadLock lock(offscreenMutex);
        TE_CHECKRETURN_CODE(lock.status);

        if (this->offscreen.get()) {
            const double altAboveSurface = 30000.0;
            for (auto tile = this->offscreen->terrainTiles.cbegin(); tile != this->offscreen->terrainTiles.cend(); tile++) {
                // AABB/bounds check
                const TAK::Engine::Feature::Envelope2 aabb_wgs84 = (*tile)->aabb_wgs84;
                if (!atakmap::math::Rectangle<double>::contains(aabb_wgs84.minX,
                                                                aabb_wgs84.minY,
                                                                aabb_wgs84.maxX,
                                                                aabb_wgs84.maxY,
                                                                longitude, latitude)) {
                    continue;
                }

                // the tile has no elevation data values...
                if (!(*tile)->hasData) {
                    // if on a border, continue as other border tile may have data, else break
                    if(aabb_wgs84.minX < longitude && aabb_wgs84.maxX > longitude &&
                       aabb_wgs84.minY < latitude && aabb_wgs84.maxY > latitude) {

                        elevation = 0.0;
                        code = TE_Ok;
                        break;
                    } else {
                        continue;
                    }
                }
                if(!(*tile)->heightmap) {
                    // if there's no heightmap, shoot a nadir ray into the
                    // terrain tile mesh and obtain the height at the
                    // intersection
                    Projection2Ptr proj(nullptr, nullptr);
                    if (ProjectionFactory3_create(proj, (*tile)->data.srid) != TE_Ok)
                        continue;

                    Matrix2 invLocalFrame;
                    (*tile)->data.localFrame.createInverse(&invLocalFrame);

                    // obtain the ellipsoid surface point
                    Point2<double> surface;
                    if (proj->forward(&surface, GeoPoint2(latitude, longitude)) != TE_Ok)
                        continue;
                    invLocalFrame.transform(&surface, surface);

                    // obtain the point at altitude
                    Point2<double> above;
                    if (proj->forward(&above, GeoPoint2(latitude, longitude, 30000.0, TAK::Engine::Core::AltitudeReference::HAE)) != TE_Ok)
                        continue;
                    invLocalFrame.transform(&above, above);

                    // construct the geometry model and compute the intersection
                    TAK::Engine::Math::Mesh model((*tile)->data.value, nullptr);

                    Point2<double> isect;
                    if (!model.intersect(&isect, Ray2<double>(above, Vector4<double>(surface.x - above.x, surface.y - above.y, surface.z - above.z))))
                        continue;

                    (*tile)->data.localFrame.transform(&isect, isect);
                    GeoPoint2 geoIsect;
                    if (proj->inverse(&geoIsect, isect) != TE_Ok)
                        continue;

                    elevation = geoIsect.altitude;
                    code = TE_Ok;
                } else {
                    // do a heightmap lookup
                    const double postSpaceX = (aabb_wgs84.maxX-aabb_wgs84.minX) / ((*tile)->posts_x-1u);
                    const double postSpaceY = (aabb_wgs84.maxY-aabb_wgs84.minY) / ((*tile)->posts_y-1u);

                    const double postX = (longitude-aabb_wgs84.minX)/postSpaceX;
                    const double postY = (*tile)->invert_y_axis ?
                        (latitude-aabb_wgs84.minY)/postSpaceY :
                        (aabb_wgs84.maxY-latitude)/postSpaceY ;

                    const auto postL = static_cast<std::size_t>(MathUtils_clamp((int)postX, 0, (int)((*tile)->posts_x-1u)));
                    const auto postR = static_cast<std::size_t>(MathUtils_clamp((int)ceil(postX), 0, (int)((*tile)->posts_x-1u)));
                    const auto postT = static_cast<std::size_t>(MathUtils_clamp((int)postY, 0, (int)((*tile)->posts_y-1u)));
                    const auto postB = static_cast<std::size_t>(MathUtils_clamp((int)ceil(postY), 0, (int)((*tile)->posts_y-1u)));

                    TAK::Engine::Math::Point2<double> p;

                    // obtain the four surrounding posts to interpolate from
                    (*tile)->data.value->getPosition(&p, (postT*(*tile)->posts_x)+postL);
                    const double ul = p.z;
                    (*tile)->data.value->getPosition(&p, (postT*(*tile)->posts_x)+postR);
                    const double ur = p.z;
                    (*tile)->data.value->getPosition(&p, (postB*(*tile)->posts_x)+postR);
                    const double lr = p.z;
                    (*tile)->data.value->getPosition(&p, (postB*(*tile)->posts_x)+postL);
                    const double ll = p.z;

                    // interpolate the height
                    p.z = MathUtils_interpolate(ul, ur, lr, ll,
                            MathUtils_clamp(postX-(double)postL, 0.0, 1.0),
                            MathUtils_clamp(postY-(double)postT, 0.0, 1.0));
                    // transform the height back to HAE
                    (*tile)->data.localFrame.transform(&p, p);
                    elevation = p.z;
                    code = TE_Ok;
                }

                break;
            }
        }
    }

    // if lookup failed and lookup off mesh is allowed, query the terrain
    // service
    if (isnan(elevation)) {
        if(this->terrain->getElevation(&elevation, latitude, longitude) != TE_Ok)
            elevation = 0.0;
        code = TE_Ok;
    }

    *value = elevation;

    return code;
}
TerrainRenderService &GLMapView2::getTerrainRenderService() const NOTHROWS
{
    return *this->terrain;
}
void GLMapView2::asyncAnimate(void *opaque) NOTHROWS
{
    std::unique_ptr<AsyncRunnable> runnable(static_cast<AsyncRunnable *>(opaque));
    Lock lock(*runnable->mutex);
    // view was disposed, return and cleanup
    if (*runnable->canceled)
        return;

    runnable->owner.animationState.targetLat = runnable->target.lat;
    runnable->owner.animationState.targetLng = runnable->target.lon;
    runnable->owner.animationState.targetMapScale = runnable->target.scale;
    runnable->owner.animationState.targetRotation = runnable->target.rot;
    runnable->owner.animationState.targetTilt = runnable->target.tilt;
    runnable->owner.animationFactor = runnable->target.factor;
    runnable->owner.settled = false;
    runnable->owner.drawVersion++;

    runnable->enqueued = false;

    // not disposed, return to pool
    runnable.release();
}
void GLMapView2::asyncAnimateFocus(void *opaque) NOTHROWS
{
    std::unique_ptr<AsyncRunnable> runnable(static_cast<AsyncRunnable *>(opaque));
    Lock lock(*runnable->mutex);
    // view was disposed, return and cleanup
    if (*runnable->canceled)
        return;

    runnable->owner.animationState.targetFocusx = runnable->focus.x;
    runnable->owner.animationState.targetFocusy = runnable->focus.y;
    runnable->owner.settled = false;
    runnable->owner.drawVersion++;

    runnable->enqueued = false;

    // not disposed, return to pool
    runnable.release();
}
void GLMapView2::glMapResized(void *opaque) NOTHROWS
{
    std::unique_ptr<AsyncRunnable> runnable(static_cast<AsyncRunnable *>(opaque));
    Lock lock(*runnable->mutex);
    if (*runnable->canceled)
        return;

    runnable->owner.top = static_cast<int>(runnable->resize.height);
    runnable->owner.bottom = 0;
    runnable->owner.left = 0;
    runnable->owner.right = static_cast<int>(runnable->resize.width);
    runnable->owner.drawVersion++;
    runnable->enqueued = false;
    runnable.release();
}

TAKErr GLMapView2::intersectWithTerrain2(GeoPoint2 *value, const MapSceneModel2 &map_scene, const float x, const float y) const NOTHROWS
{
#if 0
    GLMapView2 *t = const_cast<GLMapView2 *>(this);
    t->debugDrawOffscreen = !t->debugDrawOffscreen;
#endif
    std::shared_ptr<const TerrainTile> ignored;
    return intersectWithTerrainImpl(value, ignored, map_scene, x, y);
}

TAKErr intersectWithTerrainTileImpl(GeoPoint2 *value, const TerrainTile &tile, const MapSceneModel2 &scene, const float x, const float y) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!tile.hasData)
        return TE_Done;

    const int sceneSrid = scene.projection->getSpatialReferenceID();
    ElevationChunk::Data node(tile.data);
    if (node.srid != sceneSrid) {
        if (tile.data_proj.value && tile.data_proj.srid == sceneSrid) {
            node = tile.data_proj;
        } else {
            ElevationChunk::Data data_proj;

            MeshPtr transformed(nullptr, nullptr);
            VertexDataLayout srcLayout(node.value->getVertexDataLayout());
            MeshTransformOptions transformedOpts;
            MeshTransformOptions srcOpts;
            srcOpts.layout = VertexDataLayoutPtr(&srcLayout, Memory_leaker_const<VertexDataLayout>);
            srcOpts.srid = node.srid;
            srcOpts.localFrame = Matrix2Ptr(&node.localFrame, Memory_leaker_const<Matrix2>);
            MeshTransformOptions dstOpts;
            dstOpts.srid = sceneSrid;
            code = Mesh_transform(transformed, &transformedOpts, *node.value, srcOpts, dstOpts, nullptr);
            TE_CHECKRETURN_CODE(code);

            data_proj.srid = transformedOpts.srid;
            if(transformedOpts.localFrame.get())
                data_proj.localFrame = *transformedOpts.localFrame;
            data_proj.value = std::move(transformed);
            node = data_proj;

            // XXX - 
            const_cast<TerrainTile &>(tile).data_proj = data_proj;
        }
    }

    TAK::Engine::Math::Mesh mesh(node.value, &node.localFrame);
    return scene.inverse(value, Point2<float>(x, y), mesh);
}

TAKErr GLMapView2::intersectWithTerrainImpl(GeoPoint2 *value, std::shared_ptr<const TerrainTile> &focusTile, const MapSceneModel2 &map_scene, const float x, const float y) const NOTHROWS
{
    if(gpuTerrainIntersect) {
        AsyncSurfacePickBundle pick;
        pick.done = false;
        pick.x = x;
        pick.y = y;
        pick.code = TE_Done;
        pick.view = const_cast<GLMapView2*>(this);

        pick.code = scene.inverse(value, Point2<float>(x, y));
        if (context.isRenderThread()) {
            const bool current = context.isAttached();
            if (!current)
                context.attach();
            pick.code = glPickTerrainTile(pick.result, const_cast<GLMapView2 *>(this), map_scene, x, y);
            if (!current)
                context.detach();
        } else {
            Monitor monitor;
            pick.monitor = &monitor;

            context.queueEvent(GLMapView2::glPickTerrainTile2, std::unique_ptr<void, void(*)(const void *)>(&pick, Memory_leaker_const<void>));

            Monitor::Lock lock(monitor);
            if (!pick.done)
                lock.wait();
        }

        // missed all tiles
        if(pick.code == TE_Done)
            return pick.code;

        // we hit something
        if(pick.code == TE_Ok) {
            // check intersect with first pick tile as that one should have contained x,y
            if (pick.code == TE_Ok && intersectWithTerrainTileImpl(value, *pick.result[0], map_scene, x, y) == TE_Ok) {
                focusTile = pick.result[0];
                return TE_Ok;
            }

            // check neighbors
            std::size_t numNeighbors = 8u;
            while (numNeighbors > 0u && !pick.result[numNeighbors])
                numNeighbors--;

            if(numNeighbors && intersectWithTerrainTiles(value, map_scene, focusTile, pick.result+1u, numNeighbors, x, y) == TE_Ok)
                return TE_Ok;
        }

        // failed to find hit on computed tiles, fall through to legacy
    }

    TAKErr code(TE_Ok);
    ReadLock lock(this->offscreenMutex);
    TE_CHECKRETURN_CODE(lock.status);

    if (!this->offscreen.get() || this->offscreen->terrainTiles.empty()) {
        code = map_scene.inverse(value, Point2<float>(x, y));
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    focusTile = this->focusEstimation.tile;
    return intersectWithTerrainTiles(value, map_scene, focusTile, &this->offscreen->terrainTiles.at(0), this->offscreen->terrainTiles.size(), x, y);
}
void GLMapView2::glPickTerrainTile2(void* opaque) NOTHROWS
{
    auto arg = static_cast<AsyncSurfacePickBundle*>(opaque);
    arg->code = GLMapView2::glPickTerrainTile(arg->result, arg->view, arg->view->scene, arg->x, arg->y);

    Monitor::Lock lock(*arg->monitor);
    arg->done = true;
    lock.signal();
}
TAKErr GLMapView2::glPickTerrainTile(std::shared_ptr<const Elevation::TerrainTile> *value, GLMapView2* pview, const TAK::Engine::Core::MapSceneModel2& map_scene, const float x, const float y) NOTHROWS
{
    GLMapView2& view = *pview;
    glViewport(static_cast<GLint>(view.renderPasses[0u].viewport.x), static_cast<GLint>(view.renderPasses[0u].viewport.y), static_cast<GLsizei>(view.renderPasses[0u].viewport.width), static_cast<GLsizei>(view.renderPasses[0u].viewport.height));

    GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION);
    GLES20FixedPipeline::getInstance()->glOrthof(static_cast<float>(view.renderPasses[0u].left), static_cast<float>(view.renderPasses[0u].right), static_cast<float>(view.renderPasses[0u].bottom), static_cast<float>(view.renderPasses[0u].top), view.renderPasses[0u].near, view.renderPasses[0u].far);

    GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);

    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LEQUAL);
    glDepthMask(GL_TRUE);

    GLint viewport[4];
    GLint boundFbo;
    glGetIntegerv(GL_VIEWPORT, viewport);
    glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, (GLint *)&boundFbo);
    GLboolean blend_enabled = glIsEnabled(GL_BLEND);

    // clear to zero
    glClearColor(0.f, 0.f, 0.f, 0.f);

    GLOffscreenFramebuffer* fbo = view.offscreen->depthSamplerFbo.get();
    fbo->bind();

    const unsigned subsamplex = 8;
    const unsigned subsampley = 8;
    glViewport(-(int)(x/subsamplex) + fbo->width/2 - 1, -(int)(y/subsampley) + fbo->height/2 - 1, viewport[2]/subsamplex, viewport[3]/subsampley);

    glEnable(GL_SCISSOR_TEST);
    glScissor(0, 0, fbo->width, fbo->height);

    glDisable(GL_BLEND);

#if 0
                    float r, g, b, a;
                    if (color.id) {
                        r = (((idx+1u) >> 24) & 0xFF) / 255.f;
                        g = (((idx+1u) >> 16) & 0xFF) / 255.f;
                        b = (((idx+1u) >> 8) & 0xFF) / 255.f;
                        a = ((idx+1u) & 0xFF) / 255.f;
                    } else {
                        r = color.r;
                        g = color.g;
                        b = color.b;
                        a = color.a;
                    }
#endif
    // XXX - 
    const std::size_t nrp = view.numRenderPasses;
    State rp0(view.renderPasses[0u]);
    rp0.texture = view.offscreen->whitePixel->getTexId();
    auto ctx = GLTerrainTile_begin(rp0,
                                   (view.drawSrid == 4978) ? view.offscreen->ecef.color : view.offscreen->planar.color);
    GLTerrainTile_bindTexture(ctx, view.offscreen->whitePixel->getTexId(), 1u, 1u);
    for(std::size_t i = 0u; i < view.offscreen->visibleTiles.size(); i++) {
        // encode ID as RGBA
        const float r = (((i+1u) >> 24) & 0xFF) / 255.f;
        const float g = (((i+1u) >> 16) & 0xFF) / 255.f;
        const float b = (((i+1u) >> 8) & 0xFF) / 255.f;
        const float a = ((i+1u) & 0xFF) / 255.f;
        // draw the tile
        GLTerrainTile_drawTerrainTiles(ctx, &rp0, 1u, &view.offscreen->visibleTiles[i], 1u, r, g, b, a);
    }
    GLTerrainTile_end(ctx);

    // read the pixels
    uint32_t rgba[(SAMPLER_SIZE-1u)*(SAMPLER_SIZE-1u)];
    memset(rgba, 0, sizeof(uint32_t)*(SAMPLER_SIZE-1u)*(SAMPLER_SIZE-1u));
    glReadPixels(0, 0, fbo->width-1u, fbo->height-1u, GL_RGBA, GL_UNSIGNED_BYTE, rgba);

    // swap the center pixel with the first. The first return result is assumed
    // to be the tile contianing the specified x,y; remainder are neighbors
    {
        const uint32_t centerPixel = rgba[((SAMPLER_SIZE-1u)*(SAMPLER_SIZE-1u)) / 2u];
        const uint32_t firstPixel = rgba[0];
        rgba[0] = centerPixel;
        rgba[((SAMPLER_SIZE-1u)*(SAMPLER_SIZE-1u)) / 2u] = firstPixel;
    }
    
    // build list of unique IDs
    std::size_t unique = 0u;
    uint32_t pixel[(SAMPLER_SIZE-1u)*(SAMPLER_SIZE-1u)];
    {
        // seek out the first value ID
        std::size_t i;
        for(i = 0u; i < (fbo->width-1u)*(fbo->height-1u); i++) {
            if(rgba[i]) {
                pixel[unique++] = rgba[0];
                break;
            }
        }
        // populate remaining unique IDs
        for( ; i < (fbo->width-1u)*(fbo->height-1u); i++) {
            bool u = true;
            for(std::size_t j = 0u; j < unique; j++) {
                u &= (pixel[j] != rgba[i]);
                if(!u)
                    break;
            }
            if(u)
               pixel[unique++] = rgba[i];
        }
    }

    // need to flip pixel if GPU endianness != CPU endianness
    for(std::size_t i = 0u; i < unique; i++) {
        const uint8_t b0 = ((pixel[i] >> 24) & 0xFF);
        const uint8_t b1 = ((pixel[i] >> 16) & 0xFF);
        const uint8_t b2 = ((pixel[i] >> 8) & 0xFF);
        const uint8_t b3 = (pixel[i] & 0xFF);

        pixel[i] = (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    // restore GL state
    glDisable(GL_SCISSOR_TEST);
    glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

    if (blend_enabled)
        glEnable(GL_BLEND);

    glBindFramebuffer(GL_FRAMEBUFFER, boundFbo);
    if(!view.inRenderPump)
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    else
        Logger_log(TELL_Warning, "GLMapView2::intersectWithTerrain invoked on GL thread during render pump");

    // if the click was a miss, return none
    if (!unique)
        return TE_Done;

    // emit the candidate tiles
    std::size_t out = 0u;
    for(std::size_t i = 0u; i < unique; i++) {
        if(pixel[i] && pixel[i] <= view.offscreen->visibleTiles.size())
        if(out < 9u)
            value[out++] = view.offscreen->visibleTiles[pixel[i]-1u].tile;
    }

    return out ? TE_Ok : TE_Done;
}

double GLMapView2::getRecommendedGridSampleDistance() NOTHROWS
{
    return recommendedGridSampleDistance;
}

GLMapView2::AsyncRunnable::AsyncRunnable(GLMapView2 &owner_, const std::shared_ptr<Mutex> &mutex_) NOTHROWS :
    owner(owner_),
    mutex(owner_.asyncRunnablesMutex),
    canceled(owner_.disposed),
    enqueued(false)
{}

GLMapView2::State::State() NOTHROWS :
    drawMapScale(2.5352504279048383E-9),
    drawMapResolution(0.0),
    drawLat(0.0),
    drawLng(0.0),
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

TAKErr TAK::Engine::Renderer::Core::GLMapView2_estimateResolution(double *res, GeoPoint2 *closest, const GLMapView2 &model,
                                                                  double ullat, double ullng, double lrlat, double lrlng) NOTHROWS
{
    return GLMapView2_estimateResolution(res, closest, model.oscene, ullat, ullng, lrlat, lrlng);
}

TAKErr TAK::Engine::Renderer::Core::GLMapView2_estimateResolution(double *res, GeoPoint2 *closest, const MapSceneModel2 &model,
                                                                  double ullat, double ullng, double lrlat, double lrlng) NOTHROWS
{
    TAKErr code(TE_Ok);

    if (model.camera.elevation > -90.0) {
        // get eye pos as LLA
        TAK::Engine::Math::Point2<double> eyeProj;
        if (model.camera.mode == MapCamera2::Scale) {
            eyeProj = adjustCamLocation(model);
        } else {
            eyeProj = model.camera.location;
        }
        GeoPoint2 eye;
        code = model.projection->inverse(&eye, eyeProj);
        if (eye.longitude < -180.0)
            eye.longitude += 360.0;
        else if (eye.longitude > 180.0)
            eye.longitude -= 360.0;

        // XXX - find closest LLA on tile
        const double closestLat = std::min(std::max(eye.latitude, lrlat), ullat);
        const double eyelng = eye.longitude;
        double lrlng_dist = fabs(lrlng - eyelng);
        if (lrlng_dist > 180.0)
            lrlng_dist = 360.0 - lrlng_dist;
        double ullng_dist = fabs(ullng - eyelng);
        if (ullng_dist > 180.0)
            ullng_dist = 360.0 - ullng_dist;
        double closestLng;
        if (eyelng >= ullng && eyelng <= lrlng) {
            closestLng = eyelng;
        } else if (eyelng > lrlng || (lrlng_dist < ullng_dist)) {
            closestLng = lrlng;
        } else if (eyelng < ullng || (ullng_dist < lrlng_dist)) {
            closestLng = ullng;
        } else {
            return TE_IllegalState;
        }

        if (closestLng < -180.0)
            closestLng += 360.0;
        else if (closestLng > 180.0)
            closestLng -= 360.0;
        GeoPoint2 closestPt(closestLat, closestLng, 0.0, TAK::Engine::Core::AltitudeReference::HAE);

        if (closest)
            *closest = closestPt;

        const bool isSame = (eye.latitude == closestLat && eye.longitude == closestLng);
        if (isSame) {
            *res = model.gsd;
            return TE_Ok;
        }

        const double closestslant = GeoPoint2_slantDistance(eye, closestPt);

        const double camlocx = model.camera.location.x * model.displayModel->projectionXToNominalMeters;
        const double camlocy = model.camera.location.y * model.displayModel->projectionYToNominalMeters;
        const double camlocz = model.camera.location.z * model.displayModel->projectionZToNominalMeters;
        const double camtgtx = model.camera.target.x * model.displayModel->projectionXToNominalMeters;
        const double camtgty = model.camera.target.y * model.displayModel->projectionYToNominalMeters;
        const double camtgtz = model.camera.target.z * model.displayModel->projectionZToNominalMeters;

        double camtgtslant;
        TAK::Engine::Math::Vector2_length(&camtgtslant,
                                          TAK::Engine::Math::Point2<double>(camlocx - camtgtx, camlocy - camtgty, camlocz - camtgtz));

        *res = (closestslant / camtgtslant) * model.gsd;
    } else {
        *res = model.gsd;
    }
    return TE_Ok;
}

namespace
{
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

    void asyncSetBaseMap(void *opaque) NOTHROWS
    {
        std::unique_ptr<std::pair<GLMapView2&, GLMapRenderable2Ptr>> p(static_cast<std::pair<GLMapView2 &, GLMapRenderable2Ptr> *>(opaque));
        p->first.setBaseMap(std::move(p->second));
    }

    void asyncSetLabelManager(void* opaque) NOTHROWS
    {
        std::unique_ptr<std::pair<GLMapView2&, GLLabelManager*>> p(static_cast<std::pair<GLMapView2&, GLLabelManager*>*>(opaque));
        p->first.setLabelManager(std::move(p->second));
    }

    void asyncProjUpdate(void *opaque) NOTHROWS
    {
        std::unique_ptr<std::pair<GLMapView2 *, int>> p(static_cast<std::pair<GLMapView2 *, int> *>(opaque));
        GLMapView2 *mv = p->first;
        int srid = p->second;
        mv->drawSrid = srid;
        mv->drawVersion++;
    }

    double estimateDistanceToHorizon(const GeoPoint2 &cam, const GeoPoint2 &tgt) NOTHROWS
    {
        //Get the distance to the horizon using camera's height (wikipedia)
        double distanceToHorizon = 3570 * std::sqrt(cam.altitude);
        double R = 6378137;
        double s = R * atan2(distanceToHorizon, R) * 1.5;

        const float fov = 45;

        double surfaceDistCamToTgt = atakmap::util::distance::calculateRange(atakmap::core::GeoPoint(cam.latitude, cam.longitude), atakmap::core::GeoPoint(tgt.latitude, tgt.longitude));

        return s - surfaceDistCamToTgt;
    }

    void wrapCorner(GeoPoint2& g) NOTHROWS
    {
        if (g.longitude > 180.0)
            g.longitude = g.longitude - 360.0;
        else if (g.longitude < -180.0)
            g.longitude = g.longitude + 360.0;
    }

    template<class T>
    TAKErr forwardImpl(float *value, const size_t dstSize, const T *src, const size_t srcSize, const size_t count, const MapSceneModel2 &sm) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (srcSize == 2 && dstSize == 2) {
            double lat;
            double lon;
            for (size_t i = 0; i < count; i++) {
                lon = src[0];
                lat = src[1];
                Point2<double> pointD;
                code = sm.forward(&pointD, GeoPoint2(lat, lon));
                TE_CHECKBREAK_CODE(code);
                value[0] = (float)pointD.x;
                value[1] = (float)pointD.y;
                src += 2;
                value += 2;
            }
            TE_CHECKRETURN_CODE(code);
        } else if (srcSize == 2 && dstSize == 3) {
            double lat;
            double lon;
            for (std::size_t i = 0; i < count; i++) {
                lon = src[0];
                lat = src[1];
                Point2<double> pointD;
                code = sm.forward(&pointD, GeoPoint2(lat, lon));
                TE_CHECKBREAK_CODE(code);
                value[0] = (float)pointD.x;
                value[1] = (float)pointD.y;
                value[2] = (float)pointD.z;
                src += 2;
                value += 3;
            }
            TE_CHECKRETURN_CODE(code);
        } else if (srcSize == 3 && dstSize == 2) {
            double lat;
            double lon;
            double alt;
            for (std::size_t i = 0; i < count; i++) {
                lon = src[0];
                lat = src[1];
                alt = src[2];
                Point2<double> pointD;
                code = sm.forward(&pointD, GeoPoint2(lat, lon, alt, TAK::Engine::Core::AltitudeReference::HAE));
                TE_CHECKBREAK_CODE(code);
                value[0] = (float)pointD.x;
                value[1] = (float)pointD.y;
                src += 3;
                value += 2;
            }
            TE_CHECKRETURN_CODE(code);
        } else if (srcSize == 3 && dstSize == 3) {
            double lat;
            double lon;
            double alt;
            for (std::size_t i = 0; i < count; i++) {
                lon = src[0];
                lat = src[1];
                alt = src[2];
                Point2<double> pointD;
                code = sm.forward(&pointD, GeoPoint2(lat, lon, alt, TAK::Engine::Core::AltitudeReference::HAE));
                TE_CHECKBREAK_CODE(code);
                value[0] = (float)pointD.x;
                value[1] = (float)pointD.y;
                value[2] = (float)pointD.z;
                src += 3;
                value += 3;
            }
            TE_CHECKRETURN_CODE(code);
        } else {
            return TE_InvalidArg;
        }
        return code;
    }

    template<class T>
    TAKErr inverseImpl(T *value, const size_t dstSize, const float *src, const size_t srcSize, const size_t count, const MapSceneModel2 &sm) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (srcSize == 2 && dstSize == 2) {
            float x;
            float y;
            for (size_t i = 0; i < count; i++) {
                x = src[0];
                y = src[1];
                GeoPoint2 geo;
                code = sm.inverse(&geo, Point2<float>(x, y));
                TE_CHECKBREAK_CODE(code);
                value[0] = (T)geo.latitude;
                value[1] = (T)geo.longitude;
                src += 2;
                value += 2;
            }
            TE_CHECKRETURN_CODE(code);
        } else if (srcSize == 2 && dstSize == 3) {
            float x;
            float y;
            for (std::size_t i = 0; i < count; i++) {
                x = src[0];
                y = src[1];
                GeoPoint2 geo;
                code = sm.inverse(&geo, Point2<float>(x, y));
                TE_CHECKBREAK_CODE(code);
                value[0] = (T)geo.latitude;
                value[1] = (T)geo.longitude;
                value[2] = (T)geo.altitude;
                src += 2;
                value += 3;
            }
            TE_CHECKRETURN_CODE(code);
        } else if (srcSize == 3 && dstSize == 2) {
            float x;
            float y;
            float z;
            for (std::size_t i = 0; i < count; i++) {
                x = src[0];
                y = src[1];
                z = src[2];
                GeoPoint2 geo;
                code = sm.inverse(&geo, Point2<float>(x, y, z));
                TE_CHECKBREAK_CODE(code);
                value[0] = (T)geo.latitude;
                value[1] = (T)geo.longitude;
                src += 3;
                value += 2;
            }
            TE_CHECKRETURN_CODE(code);
        } else if (srcSize == 3 && dstSize == 3) {
            float x;
            float y;
            float z;
            for (std::size_t i = 0; i < count; i++) {
                x = src[0];
                y = src[1];
                z = src[2];
                GeoPoint2 geo;
                code = sm.inverse(&geo, Point2<float>(x, y, z));
                TE_CHECKBREAK_CODE(code);
                value[0] = (T)geo.latitude;
                value[1] = (T)geo.longitude;
                value[2] = (T)geo.altitude;
                src += 3;
                value += 3;
            }
            TE_CHECKRETURN_CODE(code);
        } else {
            return TE_InvalidArg;
        }
        return code;
    }

    Matrix2 &xformVerticalFlipScale() NOTHROWS
    {
        static Matrix2 mx(1, 0, 0, 0,
        0, -1, 0, 0,
        0, 0, 1, 0,
        0, 0, 0, 1);
        return mx;
    }

    Point2<double> adjustCamLocation(const MapSceneModel2 &model) NOTHROWS
    {
        const double camLocAdj = 2.5;

        const double camlocx = model.camera.location.x * model.displayModel->projectionXToNominalMeters;
        const double camlocy = model.camera.location.y * model.displayModel->projectionYToNominalMeters;
        const double camlocz = model.camera.location.z * model.displayModel->projectionZToNominalMeters;
        const double camtgtx = model.camera.target.x * model.displayModel->projectionXToNominalMeters;
        const double camtgty = model.camera.target.y * model.displayModel->projectionYToNominalMeters;
        const double camtgtz = model.camera.target.z * model.displayModel->projectionZToNominalMeters;

        double len;
        TAK::Engine::Math::Vector2_length(&len, TAK::Engine::Math::Point2<double>(camlocx - camtgtx, camlocy - camtgty, camlocz - camtgtz));

        const double dirx = (camlocx - camtgtx) / len;
        const double diry = (camlocy - camtgty) / len;
        const double dirz = (camlocz - camtgtz) / len;
        return TAK::Engine::Math::Point2<double>((camtgtx + (dirx * len * camLocAdj)) / model.displayModel->projectionXToNominalMeters,
                                                 (camtgty + (diry * len * camLocAdj)) / model.displayModel->projectionYToNominalMeters,
                                                 (camtgtz + (dirz * len * camLocAdj)) / model.displayModel->projectionZToNominalMeters);
    }


    template<class T>
    void debugBounds(const T &view) NOTHROWS
    {
        const double ullat = view.upperLeft.latitude;
        const double ullng = view.upperLeft.longitude;
        const double urlat = view.upperRight.latitude;
        const double urlng = view.upperRight.longitude;
        const double lrlat = view.lowerRight.latitude;
        const double lrlng = view.lowerRight.longitude;
        const double lllat = view.lowerLeft.latitude;
        const double lllng = view.lowerLeft.longitude;

        // draw bounding box on offscreen
        float bb[14];

        bb[0] = (float)ullng;
        bb[1] = (float)ullat;
        bb[2] = (float)urlng;
        bb[3] = (float)urlat;
        bb[4] = (float)lrlng;
        bb[5] = (float)lrlat;
        bb[6] = (float)lllng;
        bb[7] = (float)lllat;
        bb[8] = (float)ullng;
        bb[9] = (float)ullat;
        bb[10] = (float)view.drawLng;
        bb[11] = (float)view.drawLat;
        bb[12] = (float)urlng;
        bb[13] = (float)urlat;

        forwardImpl(bb, 2u, bb, 2u, 7u, view.scene);

        glDisable(GL_DEPTH_TEST);
        glDepthFunc(GL_ALWAYS);

        GLES20FixedPipeline::getInstance()->glColor4f(1, 0, 0, 1);
        glLineWidth(8);
        GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
        GLES20FixedPipeline::getInstance()->glVertexPointer(2, GL_FLOAT, 0, bb);
        GLES20FixedPipeline::getInstance()->glDrawArrays(GL_LINE_STRIP, 0, 7);
        GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

        Point2<float> scratchF;

        // offscreen computed center
        view.scene.forward(&scratchF, GeoPoint2(view.drawLat, view.drawLng));

        bb[0] = scratchF.x - 10;
        bb[1] = scratchF.y;
        bb[2] = scratchF.x;
        bb[3] = scratchF.y + 10;
        bb[4] = scratchF.x + 10;
        bb[5] = scratchF.y;
        bb[6] = scratchF.x;
        bb[7] = scratchF.y - 10;
        bb[8] = scratchF.x - 10;
        bb[9] = scratchF.y;

        GLES20FixedPipeline::getInstance()->glColor4f(0, 0, 1, 1);
        glLineWidth(8);
        GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
        GLES20FixedPipeline::getInstance()->glVertexPointer(2, GL_FLOAT, 0, bb);
        GLES20FixedPipeline::getInstance()->glDrawArrays(GL_LINE_STRIP, 0, 5);
        GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);

        // model surface center
        Point2<double> scratchD;
        view.scene.forwardTransform.transform(&scratchD, view.scene.camera.target);

        scratchF.x = static_cast<float>(scratchD.x);
        scratchF.y = static_cast<float>(scratchD.y);

        bb[0] = scratchF.x - 10;
        bb[1] = scratchF.y;
        bb[2] = scratchF.x;
        bb[3] = scratchF.y + 10;
        bb[4] = scratchF.x + 10;
        bb[5] = scratchF.y;
        bb[6] = scratchF.x;
        bb[7] = scratchF.y - 10;
        bb[8] = scratchF.x - 10;
        bb[9] = scratchF.y;

        GLES20FixedPipeline::getInstance()->glColor4f(0, 1, 0, 1);
        glLineWidth(8);
        GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
        GLES20FixedPipeline::getInstance()->glVertexPointer(2, GL_FLOAT, 0, bb);
        GLES20FixedPipeline::getInstance()->glDrawArrays(GL_LINE_STRIP, 0, 5);
        GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    }

    void State_save(GLMapView2::State *value, const GLMapView2& view) NOTHROWS
    {
        value->drawMapScale = view.drawMapScale;
        value->drawMapResolution = view.drawMapResolution;
        value->drawLat = view.drawLat;
        value->drawLng = view.drawLng;
        value->drawRotation = view.drawRotation;
        value->drawTilt = view.drawTilt;
        value->animationFactor = view.animationFactor;
        value->drawVersion = view.drawVersion;
        value->targeting = view.targeting;
        value->westBound = view.westBound;
        value->southBound = view.southBound;
        value->northBound = view.northBound;
        value->eastBound = view.eastBound;
        value->left = view.left;
        value->right = view.right;
        value->top = view.top;
        value->bottom = view.bottom;
        value->near = view.near;
        value->far = view.far;
        value->drawSrid = view.drawSrid;
        value->focusx = view.focusx;
        value->focusy = view.focusy;
        value->upperLeft = view.upperLeft;
        value->upperRight = view.upperRight;
        value->lowerRight = view.lowerRight;
        value->lowerLeft = view.lowerLeft;
        value->settled = view.settled;
        value->renderPump = view.renderPump;
        value->verticalFlipTranslate.set(view.verticalFlipTranslate);
        value->verticalFlipTranslateHeight = static_cast<int>(view.verticalFlipTranslateHeight);
        value->animationLastTick = view.animationLastTick;
        value->animationDelta = view.animationDelta;
        value->sceneModelVersion = view.sceneModelVersion;
        value->scene = view.scene;
        memcpy(value->sceneModelForwardMatrix, view.sceneModelForwardMatrix, sizeof(float) * 16);
        value->drawHorizon = view.drawHorizon;
        value->crossesIDL = view.crossesIDL;
        value->displayDpi = view.displayDpi;
    }

    void State_restore(GLMapView2* value, const GLMapView2::State &view) NOTHROWS
    {
        value->drawMapScale = view.drawMapScale;
        value->drawMapResolution = view.drawMapResolution;
        value->drawLat = view.drawLat;
        value->drawLng = view.drawLng;
        value->drawRotation = view.drawRotation;
        value->drawTilt = view.drawTilt;
        value->animationFactor = view.animationFactor;
        value->drawVersion = view.drawVersion;
        value->targeting = view.targeting;
        value->westBound = view.westBound;
        value->southBound = view.southBound;
        value->northBound = view.northBound;
        value->eastBound = view.eastBound;
        value->left = view.left;
        value->right = view.right;
        value->top = view.top;
        value->bottom = view.bottom;
        value->near = view.near;
        value->far = view.far;
        value->drawSrid = view.drawSrid;
        value->focusx = view.focusx;
        value->focusy = view.focusy;
        value->upperLeft = view.upperLeft;
        value->upperRight = view.upperRight;
        value->lowerRight = view.lowerRight;
        value->lowerLeft = view.lowerLeft;
        value->settled = view.settled;
        value->renderPump = view.renderPump;
        value->verticalFlipTranslate.set(view.verticalFlipTranslate);
        value->verticalFlipTranslateHeight = view.verticalFlipTranslateHeight;
        value->animationLastTick = view.animationLastTick;
        value->animationDelta = view.animationDelta;
        value->sceneModelVersion = view.sceneModelVersion;
        value->scene = view.scene;
        memcpy(value->sceneModelForwardMatrix, view.sceneModelForwardMatrix, sizeof(float) * 16);
        value->drawHorizon = view.drawHorizon;
        value->crossesIDL = view.crossesIDL;
        value->displayDpi = view.displayDpi;
    }

    MapSceneModel2 createOffscreenSceneModel(GLMapView2::State *value, const GLMapView2 &view, const double offscreenResolution, const double tilt, const std::size_t drawSurfaceWidth, const std::size_t drawSurfaceHeight, const float x, const float y, const float width, const float height) NOTHROWS
    {
        // update focus
        const float focusx = ((float)view.focusx / (float)drawSurfaceWidth) * width;
        const float focusy = ((float)view.focusy / (float)drawSurfaceHeight) * height;

        // generate the scene model based on current parameters
        const float vflipHeight = height;

        MapSceneModel2 retval(view.view.getDisplayDpi(),
            static_cast<std::size_t>(width), static_cast<std::size_t>(height),
            4326,
            GeoPoint2(view.drawLat, view.poleInView ? 0.0 : view.drawLng),
            focusx, focusy,
            view.poleInView ? 0.0 : view.drawRotation,
            tilt,
            offscreenResolution,
            tilt == 0.0 ? MapCamera2::Scale : MapCamera2::Perspective);

        // account for flipping of y-axis for OpenGL coordinate space
        retval.inverseTransform.translate(0.0, vflipHeight, 0.0);
        retval.inverseTransform.concatenate(xformVerticalFlipScale());

        retval.forwardTransform.preConcatenate(xformVerticalFlipScale());
        Matrix2 tx;
        tx.setToTranslate(0.0, vflipHeight, 0.0);
        retval.forwardTransform.preConcatenate(tx);

        // copy everything from view, then update affected fields
        State_save(value, view);
        value->verticalFlipTranslateHeight = static_cast<int>(vflipHeight);
        value->verticalFlipTranslate.setToTranslate(0, vflipHeight, 0);

        value->scene = retval;
        double mx[16];
        value->scene.forwardTransform.get(mx, Matrix2::COLUMN_MAJOR);
        for (std::size_t i = 0u; i < 16u; i++)
            value->sceneModelForwardMatrix[i] = (float)mx[i];

        value->drawSrid = value->scene.projection->getSpatialReferenceID();
        value->drawTilt = value->scene.camera.elevation + 90.0;
        value->drawRotation = value->scene.camera.azimuth;
        value->drawMapResolution = value->scene.gsd;
        value->drawMapScale = view.view.mapResolutionAsMapScale(value->drawMapResolution);
        value->focusx = value->scene.focusX;
        value->focusy = value->scene.focusY;

        // update _left, _right, _top, _bottom
        value->left = 0;
        value->right = static_cast<int>(width);
        value->bottom = 0;
        value->top = static_cast<int>(height);
        value->near = static_cast<float>(value->scene.camera.near);
        value->far = static_cast<float>(value->scene.camera.far);

        // update viewport
        value->viewport.x = x;
        value->viewport.y = y;
        value->viewport.width = width;
        value->viewport.height = height;

        return retval;
    }

    template<class T>
    void updateBoundsImpl(T *value, const bool continuousScrollEnabled) NOTHROWS
    {
        // corners
        Point2<float> pointF;

        pointF.x = static_cast<float>(value->left);
        pointF.y = static_cast<float>(value->top);

        value->scene.inverse(&value->upperLeft, pointF);

        pointF.x = static_cast<float>(value->right);
        pointF.y = static_cast<float>(value->top);
        value->scene.inverse(&value->upperRight, pointF);

        pointF.x = static_cast<float>(value->right);
        pointF.y = static_cast<float>(value->bottom);
        value->scene.inverse(&value->lowerRight, pointF);

        pointF.x = static_cast<float>(value->left);
        pointF.y = static_cast<float>(value->bottom);
        value->scene.inverse(&value->lowerLeft, pointF);

        value->northBound = std::max(std::max(value->upperLeft.latitude, value->upperRight.latitude), std::max(value->lowerRight.latitude, value->lowerLeft.latitude));
        value->southBound = std::min(std::min(value->upperLeft.latitude, value->upperRight.latitude), std::min(value->lowerRight.latitude, value->lowerLeft.latitude));
        value->eastBound = std::max(std::max(value->upperLeft.longitude, value->upperRight.longitude), std::max(value->lowerRight.longitude, value->lowerLeft.longitude));
        value->westBound = std::min(std::min(value->upperLeft.longitude, value->upperRight.longitude), std::min(value->lowerRight.longitude, value->lowerLeft.longitude));
        value->crossesIDL = (value->eastBound > 180.0 || value->westBound < -180.0) && continuousScrollEnabled;
        value->drawHorizon = false;

        if (value->scene.projection->is3D()) {
            updateLatLonAABBoxEllipsoidImpl(value);
        } else if (value->crossesIDL) {
            wrapCorner(value->upperLeft);
            wrapCorner(value->upperRight);
            wrapCorner(value->lowerRight);
            wrapCorner(value->lowerLeft);

            if (value->westBound < -180.0)
                value->westBound += 360.0;
            if (value->eastBound > 180.0)
                value->eastBound -= 360.0;
        }
    }

    double distanceSquared(const Point2<double> &a, const Point2<double> &b) NOTHROWS
    {
        const double dx = (a.x - b.x);
        const double dy = (a.y - b.y);
        const double dz = (a.z - b.z);
        return (dx*dx) + (dy*dy) + (dz*dz);
    }
    double wrapLongitude(double v) NOTHROWS
    {
        if (v < -180.0)
            return v + 360.0;
        else if (v > 180.0)
            return v - 180.0;
        else
            return v;
    }

    template<class T>
    TAKErr updateLatLonAABBoxEllipsoidImpl(T *value) NOTHROWS
    {
        TAKErr code(TE_Ok);
        auto *ellipsoid = static_cast<Ellipsoid2 *>(value->scene.earth.get());

        int w = (value->right - value->left), hw = w >> 1, h = (value->top - value->bottom), hh = h >> 1;
        Point2<double> north_pole(0, 0, ellipsoid->radiusY);
        Point2<double> south_pole(0, 0, -ellipsoid->radiusY);
        double north = -90, south = 90, east = -180, west = 180;

        struct ViewportPoint
        {
            GeoPoint2 lla;
            bool valid {false};
            Point2<float> xy;
        };

        ViewportPoint points[8];
        size_t idx = 0u;

        //GetEllipsoidPosition(0,0,true),
        points[idx].xy.x = static_cast<float>(value->left);
        points[idx].xy.y = static_cast<float>(value->bottom + h);
        code = value->scene.inverse(&points[idx].lla, points[idx].xy, false);
        points[idx++].valid = (code == TE_Ok);

        //GetEllipsoidPosition((float)hw,0,true),
        points[idx].xy.x = static_cast<float>(value->left + hw);
        points[idx].xy.y = static_cast<float>(value->bottom + h);
        code = value->scene.inverse(&points[idx].lla, points[idx].xy, false);
        points[idx++].valid = (code == TE_Ok);


        //GetEllipsoidPosition( (float)w,0,true),
        points[idx].xy.x = static_cast<float>(value->left + w);
        points[idx].xy.y = static_cast<float>(value->bottom + h);
        code = value->scene.inverse(&points[idx].lla, points[idx].xy, false);
        points[idx++].valid = (code == TE_Ok);

        //GetEllipsoidPosition(0,(float)hh,true),
        points[idx].xy.x = static_cast<float>(value->left + w);
        points[idx].xy.y = static_cast<float>(value->bottom + hh);
        code = value->scene.inverse(&points[idx].lla, points[idx].xy, false);
        points[idx++].valid = (code == TE_Ok);

        //GetEllipsoidPosition((float)w,(float)hh,true),
        points[idx].xy.x = static_cast<float>(value->left + w);
        points[idx].xy.y = static_cast<float>(value->bottom);
        code = value->scene.inverse(&points[idx].lla, points[idx].xy, false);
        points[idx++].valid = (code == TE_Ok);

        //GetEllipsoidPosition( 0,(float)h,true),
        points[idx].xy.x = static_cast<float>(value->left + hw);
        points[idx].xy.y = static_cast<float>(value->bottom);
        code = value->scene.inverse(&points[idx].lla, points[idx].xy, false);
        points[idx++].valid = (code == TE_Ok);

        //GetEllipsoidPosition((float)hw,(float)h,true),
        points[idx].xy.x = static_cast<float>(value->left);
        points[idx].xy.y = static_cast<float>(value->bottom);
        code = value->scene.inverse(&points[idx].lla, points[idx].xy, false);
        points[idx++].valid = (code == TE_Ok);

        //GetEllipsoidPosition( (float)w,(float)h,true)
        points[idx].xy.x = static_cast<float>(value->left);
        points[idx].xy.y = static_cast<float>(value->bottom + hh);
        code = value->scene.inverse(&points[idx].lla, points[idx].xy, false);
        points[idx++].valid = (code == TE_Ok);

        std::size_t numValid = 0u;
        for (std::size_t i = 0u; i < 8u; i++)
            if (points[i].valid)
                numValid++;

        bool horizonInView = (numValid < 8u);
        value->poleInView = (numValid == 0u && value->drawLat != 0.0);
        if(numValid > 0u) {
            double furthestDsq;

            // seed `north`, `south` and `furthestDsq` from first valid point
            std::size_t i = 0;
            for (; i < 8u; i++) {
                if (!points[i].valid)
                    continue;
                north = points[i].lla.latitude;
                south = points[i].lla.latitude;
                Point2<double> proj;
                value->scene.projection->forward(&proj, points[i].lla);
                furthestDsq = distanceSquared(value->scene.camera.location, proj);
                break;
            }

            // search remaining valid points for furthest distance from camera
            for( ; i < 8u; i++) {
                if (!points[i].valid)
                    continue;
                const double lat = points[i].lla.latitude;
                if(lat > north)
                    north = lat;
                else if(lat < south)
                    south = lat;

                Point2<double> proj;
                value->scene.projection->forward(&proj, points[i].lla);
                double dsq = distanceSquared(value->scene.camera.location, proj);
                if(dsq > furthestDsq)
                    furthestDsq = dsq;
            }

            Point2<double> projNorthPole;
            value->scene.projection->forward(&projNorthPole, GeoPoint2(90.0, 0.0));
            Point2<double> projSouthPole;
            value->scene.projection->forward(&projSouthPole, GeoPoint2(-90.0, 0.0));

            value->poleInView = (value->drawLat < south || value->drawLat > north) ||
                    distanceSquared(value->scene.camera.location, projNorthPole) < furthestDsq ||
                    distanceSquared(value->scene.camera.location, projSouthPole) < furthestDsq;
        }
        // if only two intersection points are detected, the full globe is in
        // view
        if(numValid == 2u && !value->poleInView) {
            north = 90.0;
            south = -90.0;
        }

        horizonInView |= value->poleInView;

        if(value->poleInView) {
            // complete globe is in view, wrap 180 degrees
            if(numValid == 0u) {
                north = std::min(value->drawLat+90.0, 90.0);
                south = std::max(value->drawLat-90.0, -90.0);
            }

            if (value->drawLat > 0.0) {
                north = 90.0;
                if(numValid < 8u && value->drawTilt == 0.0)
                    south = value->drawLat-90.0;
            } else if (value->drawLat < 0.0) {
                south = -90.0;
                if(numValid < 8u && value->drawTilt == 0.0)
                    north = value->drawLat+90.0;
            } else {
                // XXX -
            }

            // if pole is in view, we need to wrap 360 degrees
            east = wrapLongitude(value->drawLng + 180.0);
            west = wrapLongitude(value->drawLng - 180.0);
            value->crossesIDL = value->drawLng != 0.0;

            value->upperLeft.latitude = north;
            value->upperLeft.longitude = wrapLongitude(west);
            value->upperRight.latitude = north;
            value->upperRight.longitude = wrapLongitude(east);
            value->lowerRight.latitude = south;
            value->lowerRight.longitude = wrapLongitude(east);
            value->lowerLeft.latitude = south;
            value->lowerLeft.longitude = wrapLongitude(west);
#if 0
        } else if (horizonInView) {
            east = wrapLongitude(value->drawLng + 90.0);
            west = wrapLongitude(value->drawLng - 90.0);
            value->crossesIDL = fabs(value->drawLng) > 90.0;

            value->upperLeft.latitude = north;
            value->upperLeft.longitude = wrapLongitude(west);
            value->upperRight.latitude = north;
            value->upperRight.longitude = wrapLongitude(east);
            value->lowerRight.latitude = south;
            value->lowerRight.longitude = wrapLongitude(east);
            value->lowerLeft.latitude = south;
            value->lowerLeft.longitude = wrapLongitude(west);
        } else {
#else
        } else {
            if (horizonInView) {
                GeoPoint2 camLLA;
                //Convert the camera's location from x,y,z to GeoPoint LatLonAlt
                value->scene.projection->inverse(&camLLA, value->scene.camera.location);

                const double estimatedDistToHorizon = estimateDistanceToHorizon(camLLA, GeoPoint2(value->drawLat, value->drawLng));
                double tgtDistToHorizon = 0;
                for (size_t i = 0u; (i < 8u); i++) {
                    double dist = atakmap::util::distance::calculateRange(atakmap::core::GeoPoint(value->drawLat, value->drawLng), atakmap::core::GeoPoint(points[i].lla.latitude, points[i].lla.longitude));
                    if (dist > tgtDistToHorizon)
                        tgtDistToHorizon = dist;
                }

                // the distance to the horizon is the maximum of the distance
                // between the target and any on screen points and the computed
                // horizon, clamped with the semi-minor half circumerfence
                tgtDistToHorizon = std::max(tgtDistToHorizon, estimatedDistToHorizon);
                tgtDistToHorizon = std::min(tgtDistToHorizon, 6356752.3142*M_PI);

                // iterate the points -- if any were missing, the horizon is in view.
                // compute the distance to the horizon and fill in the point
                for (size_t i = 0u; (i < 8u); i++) {
                    if (!points[i].valid) {
                        const double fov = value->scene.camera.fov;

                        double offAz = 0;
                        switch (i) {
                        case 0:
                            offAz = -(fov / 2.0);
                            break;
                        case 1:
                            offAz = 0;
                            break;
                        case 2:
                            offAz = (fov / 2.0);
                            break;
                        case 3:
                            offAz = 90;
                            break;
                        case 4:
                            offAz = 180 - (fov / 2.0);
                            break;
                        case 5:
                            offAz = 180;
                            break;
                        case 6:
                            offAz = 180 + (fov / 2.0);
                            break;
                        case 7:
                            offAz = 270;
                            break;

                        }

                        camLLA.altitude = 0;

                        points[i].lla = GeoPoint2_pointAtDistance(camLLA, value->drawRotation + offAz, tgtDistToHorizon, false);
                        points[i].valid = true;
                    }

                    wrapCorner(points[i].lla);
                }
                value->upperLeft = points[0].lla;
                value->upperLeft.altitude = NAN;
                value->upperRight = points[2].lla;
                value->upperRight.altitude = NAN;
                value->lowerRight = points[4].lla;
                value->lowerRight.altitude = NAN;
                value->lowerLeft = points[6].lla;
                value->lowerLeft.altitude = NAN;
            }
#endif

            TAK::Engine::Feature::LineString2 ring;
            for (std::size_t i = 0u; i <= 8; i++) {
                if (!points[i%8u].valid)
                    continue;
                ring.addPoint(points[i%8u].lla.longitude, points[i%8u].lla.latitude);
            }

            // derived from http://stackoverflow.com/questions/1165647/how-to-determine-if-a-list-of-polygon-points-are-in-clockwise-order
            // compute winding order to determine IDL crossing
            double sum = 0.0;
            const std::size_t count = ring.getNumPoints();
            if (count) {
                for (std::size_t i = 0u; i < (count - 1u); i++) {
                    TAK::Engine::Feature::Point2 p0(0.0, 0.0);
                    ring.get(&p0, i);
                    TAK::Engine::Feature::Point2 p1(0.0, 0.0);
                    ring.get(&p1, i + 1u);
                    double dx = p1.x - p0.x;
                    double dy = p1.y + p0.y;
                    sum += dx * dy;
                }
            }
            TAK::Engine::Feature::Envelope2 mbb;
            ring.getEnvelope(&mbb);
            north = mbb.maxY;
            south = mbb.minY;

            if (sum >= 0) {
                value->crossesIDL = false;
            } else {
                // winding order indicates crossing
                value->crossesIDL = true;
            }

            if(!value->crossesIDL) {
                west = mbb.minX;
                east = mbb.maxX;
            } else {
                // crossing IDL
                west = 180.0;
                east = -180.0;
                double lng;
                for(std::size_t i = 0u; i < 8u; i++) {
                    if (!points[i].valid)
                        continue;
                    lng = points[i].lla.longitude;
                    if(lng > 0 && lng < west)
                        west = lng;
                    else if(lng < 0 && lng > east)
                        east = lng;
                }
            }
        }

        value->northBound = north;
        value->westBound = west;
        value->southBound = south;
        value->eastBound = east;
        return TE_Ok;
    }
    TAKErr validateSceneModel(GLMapView2 *view, const std::size_t width, const std::size_t height) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (view->sceneModelVersion != view->drawVersion) {
            const std::size_t vflipHeight = height;
            if (vflipHeight != view->verticalFlipTranslateHeight) {
                view->verticalFlipTranslate.setToTranslate(0, static_cast<double>(vflipHeight));
                view->verticalFlipTranslateHeight = vflipHeight;
            }

            view->scene.set(view->view.getDisplayDpi(),
                            width,
                            height,
                            view->drawSrid,
                            GeoPoint2(view->drawLat, view->drawLng),
                            view->focusx,
                            view->focusy,
                            view->drawRotation,
                            view->drawTilt,
                            view->drawMapResolution);

            // account for flipping of y-axis for OpenGL coordinate space
            view->scene.inverseTransform.concatenate(view->verticalFlipTranslate);
            view->scene.inverseTransform.concatenate(xformVerticalFlipScale());

            view->scene.forwardTransform.preConcatenate(xformVerticalFlipScale());
            view->scene.forwardTransform.preConcatenate(view->verticalFlipTranslate);

            {
                // fill the forward matrix for the Model-View
                double matrixD[16];
                view->scene.forwardTransform.get(matrixD, Matrix2::COLUMN_MAJOR);
                for (int i = 0; i < 16; i++)
                    view->sceneModelForwardMatrix[i] = (float)matrixD[i];
            }

            // mark as valid
            view->sceneModelVersion = view->drawVersion;
        }

        return code;
    }

    void glUniformMatrix4(GLint location, const Matrix2 &matrix) NOTHROWS
    {
        double matrixD[16];
        float matrixF[16];
        matrix.get(matrixD, Matrix2::COLUMN_MAJOR);
        for (std::size_t i = 0u; i < 16u; i++)
            matrixF[i] = (float)matrixD[i];
        glUniformMatrix4fv(location, 1, false, matrixF);
    }

    void glUniformMatrix4v(GLint location, const Matrix2 *matrix, const std::size_t count) NOTHROWS
    {
#define MAX_UNIFORM_MATRICES 16u
        double matrixD[MAX_UNIFORM_MATRICES*16];
        float matrixF[MAX_UNIFORM_MATRICES*16];

        const std::size_t limit = std::min(count, (std::size_t)MAX_UNIFORM_MATRICES);
        if(limit < count)
            Logger_log(TELL_Warning, "Max uniform matrices exceeded, %u", (unsigned)count);

        for (std::size_t i = 0u; i < limit; i++)
            matrix[i].get(matrixD+(i*16u), Matrix2::COLUMN_MAJOR);
        for (std::size_t i = 0u; i < (limit*16u); i++)
            matrixF[i] = (float)matrixD[i];

        glUniformMatrix4fv(location, static_cast<GLsizei>(limit), false, matrixF);
    }

    TAKErr intersectWithTerrainTiles(GeoPoint2 *value, const MapSceneModel2 &map_scene, std::shared_ptr<const TerrainTile> &focusTile, const std::shared_ptr<const TerrainTile> *tiles, const std::size_t numTiles, const float x, const float y) NOTHROWS
    {
        TAKErr code(TE_Ok);

        const int sceneSrid = map_scene.projection->getSpatialReferenceID();

        Point2<double> camdir;
        Vector2_subtract<double>(&camdir, map_scene.camera.location, map_scene.camera.target);
        // scale by nominal display model meters
        camdir.x *= map_scene.displayModel->projectionXToNominalMeters;
        camdir.y *= map_scene.displayModel->projectionYToNominalMeters;
        camdir.z *= map_scene.displayModel->projectionZToNominalMeters;

        double mag;
        Vector2_length(&mag, camdir);
        mag = std::max(mag, 2000.0);

        // scale the direction vector
        Vector2_multiply(&camdir, camdir, mag * 2.0);

        Point2<double> loc(map_scene.camera.target);
        // scale by nominal display model meters
        loc.x *= map_scene.displayModel->projectionXToNominalMeters;
        loc.y *= map_scene.displayModel->projectionYToNominalMeters;
        loc.z *= map_scene.displayModel->projectionZToNominalMeters;

        // add the scaled camera direction
        Vector2_add(&loc, loc, camdir);

        GeoPoint2 candidate;
        double candidateDistSq = NAN;

        // check the previous tile containing the focus point first to obtain an initial candidate
        if (focusTile.get() && intersectsAABB(&candidate, map_scene, focusTile->aabb_wgs84, x, y)) {
            const ElevationChunk::Data& node = focusTile->data;

            TAK::Engine::Math::Mesh mesh(node.value, &node.localFrame);
            if (intersectWithTerrainTileImpl(&candidate, *focusTile, map_scene, x, y) == TE_Ok) {
                Point2<double> proj;
                map_scene.projection->forward(&proj, candidate);
                // convert hit to nominal display model meters
                proj.x *= map_scene.displayModel->projectionXToNominalMeters;
                proj.y *= map_scene.displayModel->projectionYToNominalMeters;
                proj.z *= map_scene.displayModel->projectionZToNominalMeters;

                const double dx = proj.x - loc.x;
                const double dy = proj.y - loc.y;
                const double dz = proj.z - loc.z;
                candidateDistSq = ((dx * dx) + (dy * dy) + (dz * dz));

                *value = candidate;
            }
        }

        // compare all other tiles with the candidate derived from focus or earth surface
        for (std::size_t i = 0; i < numTiles; i++) {
            const TerrainTile& tile = *tiles[i];
            // skip checking focus twice
            if (focusTile.get() && focusTile.get() == &tile)
                continue;

            // if the tile doesn't have data, skip -- we've already computed surface intersection above
            if (!tile.hasData)
                continue;

            // check isect on AABB
            if (!intersectsAABB(&candidate, map_scene, tile.aabb_wgs84, x, y)) {
                // no AABB isect, continue
                continue;
            } else if (!isnan(candidateDistSq)) {
                // if we have a candidate and the AABB intersection is further
                // than the candidate distance, any content intersect is going to
                // be further
                Point2<double> proj;
                map_scene.projection->forward(&proj, candidate);
                // convert hit to nominal display model meters
                proj.x *= map_scene.displayModel->projectionXToNominalMeters;
                proj.y *= map_scene.displayModel->projectionYToNominalMeters;
                proj.z *= map_scene.displayModel->projectionZToNominalMeters;

                const double dx = proj.x - loc.x;
                const double dy = proj.y - loc.y;
                const double dz = proj.z - loc.z;
                const double distSq = ((dx * dx) + (dy * dy) + (dz * dz));

                if (distSq > candidateDistSq)
                    continue;
            }

            // do the raycast into the mesh
            code = intersectWithTerrainTileImpl(&candidate, tile, map_scene, x, y);
            if (code != TE_Ok)
                continue;

            Point2<double> proj;
            map_scene.projection->forward(&proj, candidate);
            // convert hit to nominal display model meters
            proj.x *= map_scene.displayModel->projectionXToNominalMeters;
            proj.y *= map_scene.displayModel->projectionYToNominalMeters;
            proj.z *= map_scene.displayModel->projectionZToNominalMeters;

            const double dx = proj.x - loc.x;
            const double dy = proj.y - loc.y;
            const double dz = proj.z - loc.z;
            const double distSq = ((dx * dx) + (dy * dy) + (dz * dz));
            if (isnan(candidateDistSq) || distSq < candidateDistSq) {
                *value = candidate;
                candidateDistSq = distSq;
                focusTile = tiles[i];
            }
        }

        if (isnan(candidateDistSq)) {
            map_scene.inverse(value, Point2<float>(x, y));
            // no focus found
            focusTile.reset();
        }

        return isnan(candidateDistSq) ? TE_Err : TE_Ok;
    }

    void drawTerrainMeshes(GLMapView2 &view, const std::vector<GLTerrainTile> &terrainTiles, const TerrainTileShaders &ecef, const TerrainTileShaders &planar, const GLTexture2 &whitePixel) NOTHROWS
    {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // select shader
        const TerrainTileShaders *shaders;
        Matrix2 localFrame[TE_GLTERRAINTILE_MAX_LOCAL_TRANSFORMS];
        std::size_t numLocalFrames = 0u;
        if(view.scene.displayModel->earth->getGeomClass() == TAK::Engine::Math::GeometryModel2::ELLIPSOID) {
            shaders = &ecef;
            if(view.drawMapResolution <= shaders->hi_threshold) {
                Matrix2 tx;
                tx.setToTranslate(view.drawLng, view.drawLat, 0.0);
                lla2ecef_transform(&localFrame[0], *view.scene.projection, &tx);
                localFrame[0].translate(-view.drawLng, -view.drawLat, 0.0);
                numLocalFrames++;
            } else if (view.drawMapResolution <= shaders->md_threshold) {
                const auto &ellipsoid = static_cast<const Ellipsoid2 &>(*view.scene.displayModel->earth);

                const double a = ellipsoid.radiusX;
                const double b = ellipsoid.radiusZ;

                const double cosLat0d = cos(view.drawLat*M_PI/180.0);
                const double cosLng0d = cos(view.drawLng*M_PI/180.0);
                const double sinLat0d = sin(view.drawLat*M_PI/180.0);
                const double sinLng0d = sin(view.drawLng*M_PI/180.0);

                const double a2_b2 = (a*a)/(b*b);
                const double b2_a2 = (b*b)/(a*a);
                const double cden = sqrt((cosLat0d*cosLat0d) + (b2_a2 * (sinLat0d*sinLat0d)));
                const double lden = sqrt((a2_b2 * (cosLat0d*cosLat0d)) + (sinLat0d*sinLat0d));

                // scale by ellipsoid radii
                localFrame[2].setToScale(a/cden, a/cden, b/lden);
                // calculate coefficients for lat/lon => ECEF conversion, using small angle approximation
                localFrame[2].concatenate(Matrix2(
                        -cosLat0d*sinLng0d, -cosLng0d*sinLat0d, sinLat0d*sinLng0d, cosLat0d*cosLng0d,
                        cosLat0d*cosLng0d, -sinLat0d*sinLng0d, -sinLat0d*cosLng0d, cosLat0d*sinLng0d,
                        0, cosLat0d, 0, sinLat0d,
                        0, 0, 0, 1
                ));
                // convert degrees to radians
                localFrame[2].scale(M_PI/180.0, M_PI/180.0, M_PI/180.0*M_PI/180.0);
                numLocalFrames++;

                // degrees are relative to focus
                localFrame[1].setToTranslate(-view.drawLng, -view.drawLat, 0);
                numLocalFrames++;

                // degrees are relative to focus
                localFrame[0].setToIdentity();
                numLocalFrames++;
            }
        } else {
            shaders = &planar;
        }

        const TerrainTileShader &shader = (view.drawMapResolution <= shaders->hi_threshold) ?
                                        shaders->hi :
                                        (view.drawMapResolution <= shaders->md_threshold) ?
                                        shaders->md : shaders->lo;

        glUseProgram(shader.base.handle);
        int activeTexture[1];
        glGetIntegerv(GL_ACTIVE_TEXTURE, activeTexture);
        glBindTexture(GL_TEXTURE_2D, whitePixel.getTexId());

        glUniform1i(shader.base.uTexture, activeTexture[0] - GL_TEXTURE0);
        glUniform4f(shader.base.uColor, 1.0, 1.0, 1.0, 1.0);
        glUniform1f(shader.uTexWidth, static_cast<float>(whitePixel.getTexWidth()));
        glUniform1f(shader.uTexHeight, static_cast<float>(whitePixel.getTexHeight()));

        // XXX - terrain enabled
        glUniform1f(shader.uElevationScale, static_cast<float>(view.elevationScaleFactor));

        // first pass
        {
            // construct the MVP matrix
            Matrix2 mvp;
            // projectino
            float matrixF[16u];
            atakmap::renderer::GLES20FixedPipeline::getInstance()->readMatrix(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION, matrixF);
            for(std::size_t i = 0u; i < 16u; i++)
                mvp.set(i%4, i/4, matrixF[i]);
            // model-view
            mvp.concatenate(view.scene.forwardTransform);

            drawTerrainMeshesImpl(view.renderPasses[0], shader, mvp, localFrame, numLocalFrames, terrainTiles, 0, 1, 0, 1);
        }

        if(view.crossesIDL &&
            ((view.scene.displayModel->earth->getGeomClass() == GeometryModel2::PLANE) ||
             (&shader != &shaders->lo))) {

            GLMapView2::State stack;
            State_save(&stack, view);

            if(view.scene.displayModel->earth->getGeomClass() == GeometryModel2::PLANE) {
            // reconstruct the scene model in the secondary hemisphere
            if (view.idlHelper.getPrimaryHemisphere() == GLAntiMeridianHelper::West)
                view.drawLng += 360.0;
            else if (view.idlHelper.getPrimaryHemisphere() == GLAntiMeridianHelper::East)
                view.drawLng -= 360.0;
            else {
                    Logger_log(TELL_Error, "GLMapView::drawTerrainTiles : invalid primary hemisphere %d", (int)view.idlHelper.getPrimaryHemisphere());
                return;
            }

            view.sceneModelVersion = ~view.sceneModelVersion;
            validateSceneModel(&view, view.scene.width, view.scene.height);
            } else if(&shader == &shaders->hi) {
                // reconstruct the scene model in the secondary hemisphere
                if (view.idlHelper.getPrimaryHemisphere() == GLAntiMeridianHelper::West)
                    localFrame[0].translate(-360.0, 0.0, 0.0);
                else if (view.idlHelper.getPrimaryHemisphere() == GLAntiMeridianHelper::East)
                    localFrame[0].translate(360.0, 0.0, 0.0);
                else {
                    Logger_log(TELL_Error, "GLMapView::drawTerrainTiles : invalid primary hemisphere %d", (int)view.idlHelper.getPrimaryHemisphere());
                    return;
                }
            } else if(&shader == &shaders->md) {
                // reconstruct the scene model in the secondary hemisphere
                if (view.idlHelper.getPrimaryHemisphere() == GLAntiMeridianHelper::West)
                    localFrame[1].translate(-360.0, 0.0, 0.0);
                else if (view.idlHelper.getPrimaryHemisphere() == GLAntiMeridianHelper::East)
                    localFrame[1].translate(360.0, 0.0, 0.0);
                else {
                    Logger_log(TELL_Error, "GLMapView::drawTerrainTiles : invalid primary hemisphere %d", (int)view.idlHelper.getPrimaryHemisphere());
                    return;
                }
            }

            // construct the MVP matrix
            Matrix2 mvp;
            // projectino
            float matrixF[16u];
            atakmap::renderer::GLES20FixedPipeline::getInstance()->readMatrix(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION, matrixF);
            for(std::size_t i = 0u; i < 16u; i++)
                mvp.set(i%4, i/4, matrixF[i]);
            // model-view
            mvp.concatenate(view.scene.forwardTransform);

            drawTerrainMeshesImpl(view.renderPasses[0u], shader, mvp, localFrame, numLocalFrames, terrainTiles, 0, 1, 0, 1);

            State_restore(&view, stack);
        }

        glUseProgram(0);
    }
    void drawTerrainMeshesImpl(const GLMapView2::State &renderPass, const TerrainTileShader &shader, const Matrix2 &mvp, const Matrix2 *local, const std::size_t numLocal, const std::vector<GLTerrainTile> &terrainTiles, const float r, const float g, const float b, const float a) NOTHROWS
    {
        glUniform4f(shader.base.uColor, r, g, b, a);

        glEnableVertexAttribArray(shader.base.aVertexCoords);

        // draw terrain tiles
        std::size_t idx = 0u;
        for (auto tile = terrainTiles.begin(); tile != terrainTiles.end(); tile++) {
            float *color = meshColors[idx%13u];
            drawTerrainMeshImpl(renderPass, shader, mvp, local, numLocal, *tile, color[0], color[1], color[2], color[3]);
            idx++;
        }

        glDisableVertexAttribArray(shader.base.aVertexCoords);
    }

    void drawTerrainMeshImpl(const GLMapView2::State &state, const TerrainTileShader &shader, const Matrix2 &mvp, const Matrix2 *local, const std::size_t numLocal, const GLTerrainTile &gltile, const float r, const float g, const float b, const float a) NOTHROWS
    {
        if(!gltile.tile)
            return;

        TAKErr code(TE_Ok);
        const TerrainTile &tile = *gltile.tile;

        int drawMode;
        switch (tile.data.value->getDrawMode()) {
            case TEDM_Triangles:
                drawMode = GL_TRIANGLES;
                break;
            case TEDM_TriangleStrip:
                drawMode = GL_TRIANGLE_STRIP;
                break;
            default:
                Logger_log(TELL_Warning, "GLMapView2: Undefined terrain model draw mode");
                return;
        }

        // set the local frame
        Matrix2 matrix;

        if (atakmap::math::Rectangle<double>::contains(tile.aabb_wgs84.minX, tile.aabb_wgs84.minY, tile.aabb_wgs84.maxX, tile.aabb_wgs84.maxY, state.drawLat, state.drawLng)) {
            matrix.setToIdentity();
        }
        matrix.set(mvp);
        if(shader.uLocalTransform < 0) {
            for(std::size_t i = numLocal; i >= 1; i--)
                matrix.concatenate(local[i-1u]);
            matrix.concatenate(tile.data.localFrame);
        } else {
            Matrix2 mx[TE_GLTERRAINTILE_MAX_LOCAL_TRANSFORMS];
            for(std::size_t i = numLocal; i >= 1; i--)
                mx[i-1u].set(local[i-1u]);
            mx[0].concatenate(tile.data.localFrame);
            glUniformMatrix4v(shader.uLocalTransform, mx, numLocal ? numLocal : 1u);
        }

        glUniformMatrix4(shader.base.uMVP, matrix);

        // set the local frame for the offscreen texture
        matrix.set(state.scene.forwardTransform);
        if(shader.uLocalTransform < 0) {
            // offscreen is in LLA, so we only need to convert the tile vertices from the LCS to WCS
            matrix.concatenate(tile.data.localFrame);
        }

        glUniformMatrix4(shader.uModelViewOffscreen, matrix);

        glUniform4f(shader.base.uColor, r, g, b, a);

#if 0
        if (depthEnabled) {
#else
        if(true) {
#endif
            glDepthFunc(GL_LEQUAL);
        }

        // render offscreen texture
        const VertexDataLayout &layout = tile.data.value->getVertexDataLayout();

        if(gltile.vbo) {
            // VBO
            glBindBuffer(GL_ARRAY_BUFFER, gltile.vbo);
            glVertexAttribPointer(shader.base.aVertexCoords, 3u, GL_FLOAT, false, static_cast<GLsizei>(layout.position.stride), (void *)layout.position.offset);
            glBindBuffer(GL_ARRAY_BUFFER, GL_NONE);
        } else {
            const void *vertexCoords;
            code = tile.data.value->getVertices(&vertexCoords, TEVA_Position);
            if (code != TE_Ok) {
                Logger_log(TELL_Error, "GLMapView2::drawTerrainTile : failed to obtain vertex coords, code=%d", code);
                return;
            }

            glVertexAttribPointer(shader.base.aVertexCoords, 3u, GL_FLOAT, false, static_cast<GLsizei>(layout.position.stride), static_cast<const uint8_t *>(vertexCoords) + layout.position.offset);
        }

        std::size_t numIndicesWireframe = 0u;
        array_ptr<uint8_t> wireframeIndices;
        int glIndexType = GL_NONE;

        if (tile.data.value->isIndexed()) {
            const GLuint numMeshIndices = DEBUG_DRAW_MESH_SKIRT ? static_cast<GLuint>(tile.data.value->getNumIndices()) : static_cast<GLuint>(tile.skirtIndexOffset);
            GLWireframe_getNumWireframeElements(&numIndicesWireframe, drawMode, numMeshIndices);

            const void *srcIndices = static_cast<const uint8_t *>(tile.data.value->getIndices()) + tile.data.value->getIndexOffset();

            DataType indexType;
            tile.data.value->getIndexType(&indexType);
            switch(indexType) {
                case TEDT_UInt8 :
                    glIndexType = GL_UNSIGNED_BYTE;
                    // XXX - no support for uint8_t indices
                    wireframeIndices.reset(new uint8_t[numIndicesWireframe * sizeof(uint16_t)]);
                    GLWireframe_deriveIndices(reinterpret_cast<uint16_t *>(wireframeIndices.get()), drawMode, numMeshIndices);
                    glIndexType = GL_UNSIGNED_SHORT;
                    break;
                case TEDT_UInt16 :
                    glIndexType = GL_UNSIGNED_SHORT;
                    wireframeIndices.reset(new uint8_t[numIndicesWireframe * sizeof(uint16_t)]);
                    GLWireframe_deriveIndices(reinterpret_cast<uint16_t *>(wireframeIndices.get()), &numIndicesWireframe, reinterpret_cast<const uint16_t *>(srcIndices), drawMode, numMeshIndices);
                    break;
                case TEDT_UInt32 :
                    glIndexType = GL_UNSIGNED_INT;
                    wireframeIndices.reset(new uint8_t[numIndicesWireframe * sizeof(uint32_t)]);
                    GLWireframe_deriveIndices(reinterpret_cast<uint32_t *>(wireframeIndices.get()), &numIndicesWireframe, reinterpret_cast<const uint32_t *>(srcIndices), drawMode, numMeshIndices);
                    break;
                default :
                    Logger_log(TELL_Error, "GLMapView2::drawTerrainTile : index type not supported by GL %d", indexType);
                    return;
            }
        } else {
            GLWireframe_getNumWireframeElements(&numIndicesWireframe, drawMode, static_cast<GLuint>(tile.data.value->getNumVertices()));

            DataType indexType;
            tile.data.value->getIndexType(&indexType);
            if (tile.data.value->getNumVertices() < 0xFFFFu) {
                glIndexType = GL_UNSIGNED_SHORT;
                wireframeIndices.reset(new uint8_t[numIndicesWireframe * sizeof(uint16_t)]);
                GLWireframe_deriveIndices(reinterpret_cast<uint16_t *>(wireframeIndices.get()), drawMode, static_cast<GLuint>(tile.data.value->getNumVertices()));
            } else {
                glIndexType = GL_UNSIGNED_INT;
                wireframeIndices.reset(new uint8_t[numIndicesWireframe * sizeof(uint32_t)]);
                GLWireframe_deriveIndices(reinterpret_cast<uint32_t *>(wireframeIndices.get()), drawMode, static_cast<GLuint>(tile.data.value->getNumVertices()));
            }
        }

        glDrawElements(GL_LINES, static_cast<GLsizei>(numIndicesWireframe), glIndexType, wireframeIndices.get());
    }

    TAKErr lla2ecef_transform(Matrix2 *value, const Projection2 &ecef, const Matrix2 *localFrame) NOTHROWS
    {
        TAKErr code(TE_Ok);

        Matrix2 mx;

        Point2<double> pointD(0.0, 0.0, 0.0);
        GeoPoint2 geo;

        // if draw projection is ECEF and source comes in as LLA, we can
        // transform from LLA to ECEF by creating a local ENU CS and
        // chaining the following conversions (all via matrix)
        // 1. LCS -> LLA
        // 2. LLA -> ENU
        // 3. ENU -> ECEF
        // 4. ECEF -> NDC (via MapSceneModel 'forward' matrix)

        // obtain origin as LLA
        pointD.x = 0;
        pointD.y = 0;
        pointD.z = 0;
        if(localFrame)
            localFrame->transform(&pointD, pointD);
        // transform origin to ECEF
        geo.latitude = pointD.y;
        geo.longitude = pointD.x;
        geo.altitude = pointD.z;
        geo.altitudeRef = TAK::Engine::Core::AltitudeReference::HAE;

        code = ecef.forward(&pointD, geo);
        TE_CHECKRETURN_CODE(code);

        // construct ENU -> ECEF
#define __RADIANS(x) ((x)*M_PI/180.0)
        const double phi = __RADIANS(geo.latitude);
        const double lambda = __RADIANS(geo.longitude);

        mx.translate(pointD.x, pointD.y, pointD.z);

        Matrix2 enu2ecef(
                -sin(lambda), -sin(phi)*cos(lambda), cos(phi)*cos(lambda), 0.0,
                cos(lambda), -sin(phi)*sin(lambda), cos(phi)*sin(lambda), 0.0,
                0, cos(phi), sin(phi), 0.0,
                0.0, 0.0, 0.0, 1.0
        );

        mx.concatenate(enu2ecef);

        // construct LLA -> ENU
        const double metersPerDegLat = GeoPoint2_approximateMetersPerDegreeLatitude(geo.latitude);
        const double metersPerDegLng = GeoPoint2_approximateMetersPerDegreeLongitude(geo.latitude);

        mx.scale(metersPerDegLng, metersPerDegLat, 1.0);

        value->set(mx);

        return code;
    }
}
