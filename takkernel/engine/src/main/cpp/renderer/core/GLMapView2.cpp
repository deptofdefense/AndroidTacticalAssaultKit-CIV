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

    TAKErr intersectWithTerrainTiles(GeoPoint2 *value, const MapSceneModel2 &map_scene, std::shared_ptr<const TerrainTile> &focusTile, const std::shared_ptr<const TerrainTile> *tiles, const std::size_t numTiles, const float x, const float y) NOTHROWS;
    TAKErr intersectWithTerrainTileImpl(GeoPoint2 *value, const TerrainTile &tile, const MapSceneModel2 &scene, const float x, const float y) NOTHROWS;
    
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
        ecef.pick.hi.base.handle = 0;
        ecef.depth.hi.base.handle = 0;
        ecef.color.md.base.handle = 0;
        ecef.pick.md.base.handle = 0;
        ecef.depth.md.base.handle = 0;
        ecef.color.lo.base.handle = 0;
        ecef.pick.lo.base.handle = 0;
        ecef.depth.lo.base.handle = 0;
        planar.color.hi.base.handle = 0;
        planar.pick.hi.base.handle = 0;
        planar.depth.hi.base.handle = 0;
        planar.color.md.base.handle = 0;
        planar.pick.md.base.handle = 0;
        planar.depth.md.base.handle = 0;
        planar.color.lo.base.handle = 0;
        planar.pick.lo.base.handle = 0;
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
        TerrainTileShaders pick;
        TerrainTileShaders depth;
    } ecef;
    struct {
        TerrainTileShaders color;
        TerrainTileShaders pick;
        TerrainTileShaders depth;
    } planar;

    Statistics elevationStats;
    int64_t lastElevationQuery;
    std::vector<std::shared_ptr<const TerrainTile>> terrainTiles;
    std::vector<GLTerrainTile> visibleTiles;
    std::unordered_map<const TerrainTile *, GLTerrainTile> gltiles;
    GLOffscreenFramebufferPtr depthSamplerFbo;
};


GLMapView2::GLMapView2(RenderContext &ctx, AtakMapView &aview,
                       int left, int bottom,
                       int right, int top) NOTHROWS :
    GLGlobeBase(ctx, ctx.getRenderSurface()->getDpi(), MapCamera2::Scale),
    left(left), bottom(bottom),
    right(right), top(top), drawLat(0),
    drawLng(0), drawRotation(0), drawMapScale(0),
    drawMapResolution(0),
    westBound(-180),
    southBound(-90), northBound(90),
    eastBound(180), drawSrid(-1),
    focusx(0), focusy(0), upperLeft(),
    upperRight(), lowerRight(), lowerLeft(),
    renderPump(0),
    scene(ctx.getRenderSurface()->getDpi(),
        ctx.getRenderSurface()->getWidth(),
        ctx.getRenderSurface()->getHeight(),
        aview.getProjection(),
        getPoint(aview),
        (float)ctx.getRenderSurface()->getWidth()/2.f,
        (float)ctx.getRenderSurface()->getHeight()/2.f,
        aview.getMapRotation(),
        aview.getMapTilt(),
        aview.getMapResolution()),
    offscreen(nullptr, nullptr),
    terrain(new ElMgrTerrainRenderService(ctx), Memory_deleter_const<TerrainRenderService, ElMgrTerrainRenderService>),
    view(aview),
    drawHorizon(false),
    pixelDensity(1.0),
    drawTilt(0.0),
    enableMultiPassRendering(true),
    debugDrawBounds(false),
    near(1),
    far(-1),
    numRenderPasses(0u),
    terrainBlendFactor(1.0f),
    debugDrawOffscreen(false),
    dbgdrawflags(4),
    poleInView(false),
    debugDrawMesh(false),
    debugDrawDepth(false),
    suspendMeshFetch(false),
    tiltSkewOffset(DEFAULT_TILT_SKEW_OFFSET),
    tiltSkewMult(DEFAULT_TILT_SKEW_MULT),
    continuousScrollEnabled(aview.isContinuousScrollEnabled()),
    diagnosticMessagesEnabled(false),
    inRenderPump(false)
{
    elevationScaleFactor = aview.getElevationExaggerationFactor();
    sceneModelVersion = drawVersion - 1;

    drawSrid = scene.projection->getSpatialReferenceID();
    GeoPoint2 center;
    scene.projection->inverse(&center, scene.camera.target);
    drawLat = center.latitude;
    drawLng = center.longitude;
    drawAlt = center.altitude;
    drawRotation = scene.camera.azimuth;
    drawTilt = 90.0 + scene.camera.elevation;
    drawMapScale = atakmap::core::AtakMapView_getMapScale(scene.displayDpi, scene.gsd);
    drawMapResolution = scene.gsd;
    focusx = scene.focusX;
    focusy = scene.focusY;
    this->left = 0;
    this->right = (int)scene.width;
    this->bottom = 0;
    this->top = (int)scene.height;

    animation.target.point.latitude = drawLat;
    animation.target.point.longitude = drawLng;
    animation.target.point.altitude = drawAlt;
    animation.target.mapScale = drawMapScale;
    animation.target.rotation = drawRotation;
    animation.target.tilt = drawTilt;
    animation.target.focusOffset.x = focusx-(float)this->right/2.f;
    animation.target.focusOffset.y = (float)this->top/2.f-focusy;
    animation.settled = false;
    animationFactor = 1.f;
    settled = false;
    animation.last = animation.target;
    animation.current = animation.target;

    State_save(&this->renderPasses[0u], *this);
    this->renderPasses[0u].renderPass = GLMapView2::Sprites | GLMapView2::Surface | GLMapView2::Scenes | GLMapView2::XRay;
    this->renderPasses[0u].texture = 0;
    this->renderPasses[0u].basemap = true;
    this->renderPasses[0u].debugDrawBounds = this->debugDrawBounds;
    this->renderPasses[0u].viewport.x = static_cast<float>(left);
    this->renderPasses[0u].viewport.y = static_cast<float>(bottom);
    this->renderPasses[0u].viewport.width = static_cast<float>(right-left);
    this->renderPasses[0u].viewport.height = static_cast<float>(top-bottom);

    state.focus.geo.latitude = renderPasses[0].drawLat;
    state.focus.geo.longitude = renderPasses[0].drawLng;
    state.focus.geo.altitude = renderPasses[0].drawAlt;
    state.focus.offsetX = renderPasses[0].focusx-renderPasses[0].viewport.width/2.f;
    state.focus.offsetY = renderPasses[0].focusy-renderPasses[0].viewport.height/2.f;
    state.srid = renderPasses[0].drawSrid;
    state.resolution = renderPasses[0].drawMapResolution;
    state.rotation = renderPasses[0].drawRotation;
    state.tilt = renderPasses[0].drawTilt;
    state.width = (renderPasses[0].right-renderPasses[0].left);
    state.height = (renderPasses[0].top-renderPasses[0].bottom);

    this->diagnosticMessagesEnabled = !!ConfigOptions_getIntOptionOrDefault("glmapview.render-diagnostics", 0);
#ifdef __ANDROID__
    this->gpuTerrainIntersect = !!ConfigOptions_getIntOptionOrDefault("glmapview.surface-rendering-v2", 0);
#else
    this->gpuTerrainIntersect = !!ConfigOptions_getIntOptionOrDefault("glmapview.surface-rendering-v2", 1);
#endif
}

GLMapView2::~GLMapView2() NOTHROWS
{
    GLMapView2::stop();

    if(this->offscreen && this->offscreen->depthSamplerFbo) {
        if (!context.isAttached()) {
            Logger_log(TELL_Warning, "GLMapView2::release() not called prior to destruct; destruct off of render thread leaking GL resources");
            this->offscreen->depthSamplerFbo.release();
        }
    }
}

TAKErr GLMapView2::start() NOTHROWS
{
    GLGlobeBase::start();

    this->terrain->start();

    this->view.addLayersChangedListener(this);
    std::list<Layer *> layers;
    view.getLayers(layers);
    refreshLayers(layers);

    this->tiltSkewOffset = ConfigOptions_getDoubleOptionOrDefault("glmapview.tilt-skew-offset", DEFAULT_TILT_SKEW_OFFSET);
    this->tiltSkewMult = ConfigOptions_getDoubleOptionOrDefault("glmapview.tilt-skew-mult", DEFAULT_TILT_SKEW_MULT);

    view.attachRenderer(*this, false);

    return TE_Ok;
}

TAKErr GLMapView2::stop() NOTHROWS
{
    view.detachRenderer(*this);

    this->view.removeLayersChangedListener(this);
    this->view.removeMapElevationExaggerationFactorListener(this);
    this->view.removeMapProjectionChangedListener(this);
    this->view.removeMapMovedListener(this);
    this->view.removeMapResizedListener(this);
    this->view.getController()->removeFocusPointChangedListener(this);

    GLGlobeBase::stop();

    this->terrain->stop();

    return TE_Ok;
}
bool GLMapView2::isRenderDiagnosticsEnabled() const NOTHROWS
{
    return diagnosticMessagesEnabled;
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
bool GLMapView2::isContinuousScrollEnabled() const NOTHROWS
{
    return this->continuousScrollEnabled;
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
    offscreen->ecef.color = GLTerrainTile_getColorShader(this->context, 4978, TECSO_Lighting);
    offscreen->ecef.pick = GLTerrainTile_getColorShader(this->context, 4978);
    offscreen->ecef.depth = GLTerrainTile_getDepthShader(this->context, 4978);
    offscreen->planar.color = GLTerrainTile_getColorShader(this->context, 4326, TECSO_Lighting);
    offscreen->planar.pick = GLTerrainTile_getColorShader(this->context, 4326);
    offscreen->planar.depth = GLTerrainTile_getDepthShader(this->context, 4326);
}
bool GLMapView2::initOffscreenRendering() NOTHROWS
{
#define DBG_GL_ERR() \
        { GLenum errv; while((errv=glGetError()) != GL_NO_ERROR) Logger_log(TELL_Info, "GLMapView2::initOffscreenRendering() GL Error %s at %d: %d", __FILE__, __LINE__, (int)errv); }

    DBG_GL_ERR();

    if (TE_ISNAN(offscreen->hfactor))
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

    const int offscreenTextureWidth = std::min((int)((float)state.width*wfactor), ivalue);
    const int offscreenTextureHeight = std::min((int)((float)state.height*hfactor), ivalue);

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

    GeoPoint2 p2;
    GLGlobeBase::lookAt(GeoPoint2(p.latitude, p.longitude, p.altitude, TAK::Engine::Core::AltitudeReference::HAE),
            map_view->getMapResolution(),
            map_view->getMapRotation(),
            map_view->getMapTilt(),
            CameraCollision::Ignore,
            animate);
}

TAKErr GLMapView2::createLayerRenderer(GLLayer2Ptr &value, Layer2 &subject) NOTHROWS
{
    return GLLayerFactory2_create(value, *this, subject);
}

void GLMapView2::mapProjectionChanged(atakmap::core::AtakMapView* map_view)
{
    int srid = map_view->getProjection();
    switch(srid) {
        case 4978 :
            setDisplayMode(MapRenderer::Globe);
            break;
        case 4326 :
            setDisplayMode(MapRenderer::Flat);
            break;
        default :
            break;
    }
}

void GLMapView2::mapResized(atakmap::core::AtakMapView *mapView)
{
    const int w = (int)mapView->getWidth();
    const int h = (int)mapView->getHeight();
    if(w <= 0 || h <= 0)
        return;
    GLGlobeBase::setSurfaceSize((std::size_t)w, (std::size_t)h);
}

void GLMapView2::mapControllerFocusPointChanged(atakmap::core::AtakMapController *controller, const atakmap::math::Point<float> * const focus)
{
    // We call `GLGlobeBase::setFocusPointOffset` here as `GLGlobeBase::setFocusPoint` calls virtual
    // `setFocusPointOffset`, resulting in an infinite loop

    // XXX - focus from controller is UL origin, should need to flip y
    GLGlobeBase::setFocusPointOffset(focus->x-(float)state.width/2.f, focus->y-(float)state.height/2.f);
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

void GLMapView2::render() NOTHROWS
{
    const int64_t tick = Platform_systime_millis();
    this->renderPump++;
    GLGlobeBase::render();
    const int64_t renderPumpElapsed = Platform_systime_millis()-tick;

    if (diagnosticMessagesEnabled) {
        glDepthFunc(GL_ALWAYS);
        glDepthMask(GL_FALSE);
        glDisable(GL_DEPTH_TEST);

        atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_PROJECTION);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glOrthof((float)left, (float)right, (float)bottom, (float)top, 1.f, -1.f);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);
        atakmap::renderer::GLES20FixedPipeline::getInstance()->glLoadIdentity();
        // report the terrain tile shader used
        {
            std::ostringstream dbg;
            const char *shader = "???";
            if(offscreen) {
                auto shaders = GLTerrainTile_getColorShader(context, renderPasses[0].drawSrid, TECSO_Lighting);
                auto ctx = GLTerrainTile_begin(renderPasses[0].scene, offscreen->ecef.color);
                if(ctx.shader.base.handle == shaders.hi.base.handle)
                    shader = "HI";
                else if(ctx.shader.base.handle == shaders.md.base.handle)
                    shader = "MD";
                else if(ctx.shader.base.handle == shaders.lo.base.handle)
                    shader = "LO";
                GLTerrainTile_end(ctx);
            }
            dbg << "Shader " << shader << " [" << ((int)(renderPasses[0].drawMapResolution*100.0)/100.0) << "m]";
            diagnosticMessages.push_back(dbg.str());
        }
        // report render pump duration
        {
            std::ostringstream dbg;
            dbg << "Render pump " << renderPumpElapsed << "ms";
            diagnosticMessages.push_back(dbg.str());
        }
        GLText2 *text = GLText2_intern(TextFormatParams(14));
        if (text) {
            TextFormat2 &fmt = text->getTextFormat();
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glPushMatrix();
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glTranslatef(16, top - 64 - fmt.getCharHeight(), 0);

            text->draw("Renderer Diagnostics", 1, 0, 1, 1);
            

            for (auto it = diagnosticMessages.begin(); it != diagnosticMessages.end(); it++) {
                atakmap::renderer::GLES20FixedPipeline::getInstance()->glTranslatef(0, -fmt.getCharHeight() + 4, 0);
                text->draw((*it).c_str(), 1, 0, 1, 1);
            }
            atakmap::renderer::GLES20FixedPipeline::getInstance()->glPopMatrix();
        }
        diagnosticMessages.clear();
    }
}
TAKErr GLMapView2::lookAt(const GeoPoint2 &at, const double resolution, const double azimuth, const double tilt, const MapRenderer::CameraCollision collision, const bool animate) NOTHROWS
{

    MapSceneModel2 lookAtScene;
    {
        ReadLock lock(renderPasses0Mutex);
        lookAtScene = renderPasses[0].scene;
    }

    lookAtScene = MapSceneModel2(displayDpi,
                        lookAtScene.width,
                        lookAtScene.height,
                        state.srid,
                        at,
                        lookAtScene.focusX, lookAtScene.focusY,
                        azimuth,
                        tilt,
                        resolution);
    GeoPoint2 atSurface;
    lookAtScene.inverse(&atSurface, Point2<float>(lookAtScene.focusX, lookAtScene.focusY, 0.f));
    return GLGlobeBase::lookAt(at, resolution, azimuth, tilt, collision, animate);
}
void GLMapView2::release() NOTHROWS
{
    GLGlobeBase::release();

    if(this->offscreen) {
        this->offscreen.reset();
    }
}

void GLMapView2::prepareScene() NOTHROWS
{
    GLGlobeBase::prepareScene();

    GLES20FixedPipeline *fixedPipe = GLES20FixedPipeline::getInstance();
    fixedPipe->glMatrixMode(GLES20FixedPipeline::MatrixMode::MM_GL_MODELVIEW);
    fixedPipe->glLoadIdentity();
}
void GLMapView2::computeBounds() NOTHROWS
{
    this->scene = this->renderPasses[0].scene;
    memcpy(this->sceneModelForwardMatrix, this->renderPasses[0].sceneModelForwardMatrix, 16u*sizeof(float));

    this->near = static_cast<float>(scene.camera.near);
    this->far = static_cast<float>(scene.camera.far);

    updateBoundsImpl<GLMapView2>(this, this->continuousScrollEnabled);
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

    GLint currentFbo = GL_NONE;
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
        WriteLock wlock(renderPasses0Mutex);

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

        renderPasses[0].renderTiles.value = !offscreen->visibleTiles.empty() ? &offscreen->visibleTiles.at(0) : nullptr;
        renderPasses[0].renderTiles.count = offscreen->visibleTiles.size();
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

            struct {
                double lat;
                double lng;
                double alt;
                MapSceneModel2 scene;
            } rp0;

            rp0.lat = this->renderPasses[0].drawLat;
            rp0.lng = this->renderPasses[0].drawLng;
            rp0.alt = this->renderPasses[0].drawAlt;
            rp0.scene = this->renderPasses[0].scene;

            // adjust to surface center
            drawLat = focusEstimation.point.latitude;
            drawLng = focusEstimation.point.longitude;
            drawAlt = 0;

            // reset lat/lng for scene model revalidate
            this->renderPasses[0u].drawLat = this->drawLat;
            this->renderPasses[0u].drawLng = this->drawLng;
            this->renderPasses[0u].drawAlt = this->drawAlt;

            sceneModelVersion = ~sceneModelVersion;
            validateSceneModel(this, scene.width, scene.height, cammode);
            updateBoundsImpl(&this->renderPasses[0u], this->continuousScrollEnabled);

            // reconstruct the bounds for the base renderpass
            this->northBound = this->renderPasses[0u].northBound;
            this->westBound = this->renderPasses[0u].westBound;
            this->southBound = this->renderPasses[0u].southBound;
            this->eastBound = this->renderPasses[0u].eastBound;
            this->upperLeft = this->renderPasses[0u].upperLeft;
            this->upperRight = this->renderPasses[0u].upperRight;
            this->lowerRight = this->renderPasses[0u].lowerRight;
            this->lowerLeft = this->renderPasses[0u].lowerLeft;

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
                const int targetOffscreenTextureHeight = (int)std::ceil(scaleAdj*renderPasses[0].scene.height);
                const int targetOffscreenTextureWidth = (int)renderPasses[0].scene.width;

                std::size_t offscreenTexWidth = std::min(targetOffscreenTextureWidth, static_cast<int>(offscreen->texture->getTexWidth()));
                std::size_t offscreenTexHeight = std::min(targetOffscreenTextureHeight, static_cast<int>(offscreen->texture->getTexHeight()));

                // capture actual scale adjustment
                scaleAdj *= std::max((double)targetOffscreenTextureHeight / (double)offscreenTexHeight, (double)targetOffscreenTextureWidth / (double)offscreenTexWidth);

                offscreenTexWidth = offscreen->texture->getTexWidth();
                offscreenTexHeight = offscreen->texture->getTexHeight();

#else
                const int targetOffscreenTextureWidth = (int)ceil((double)renderPasses[0].scene.width / (scaleAdj*0.75));
                const int targetOffscreenTextureHeight = (int)std::min(ceil((double)renderPasses[0].scene.height * scaleAdj), (double)offscreen->texture->getTexHeight());

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
                State_restore(this, this->renderPasses[0u]);
                constructOffscreenRenderPass(!poleInView, drawMapResolution * scaleAdj, 0.0, true, renderPasses[0].scene.width, renderPasses[0].scene.height, 0, 0, static_cast<float>(offscreenTexWidth), static_cast<float>(offscreenTexHeight));
                renderPasses[this->numRenderPasses-1u].drawMapResolution = drawMapResolution;
                renderPasses[this->numRenderPasses-1u].drawMapScale = drawMapScale;
                renderPasses[this->numRenderPasses-1u].debugDrawBounds = debugDrawBounds;
                renderPasses[this->numRenderPasses - 1u].renderTiles = renderPasses[0u].renderTiles;

                this->renderPasses[0].drawLat = rp0.lat;
                this->renderPasses[0].drawLng = rp0.lng;
                this->renderPasses[0].drawAlt = rp0.alt;
                this->renderPasses[0].scene = rp0.scene;
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
                                 renderPasses[0].scene.width,
                                 renderPasses[0].scene.height);
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
    if (getLabelManager())
        getLabelManager()->draw(*this, RenderPass::Sprites);
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
    this->renderPass = this->renderPasses;
}
void GLMapView2::drawRenderables(const GLMapView2::State &renderState) NOTHROWS
{
    // save the current view state
    State viewState;
    State_save(&viewState, *this);

    // load the render state
    State_restore(this, renderState);
    this->idlHelper.update(*this);

    GLGlobeBase::drawRenderables(renderState);

    // debug draw bounds if requested
    if (renderState.debugDrawBounds)
        debugBounds(renderState);

    // restore the view state
    State_restore(this, viewState);
    this->idlHelper.update(*this);
}

void GLMapView2::drawTerrainTiles(const GLTexture2 &tex, const std::size_t drawSurfaceWidth, const std::size_t drawSurfaceHeight) NOTHROWS
{
    if (offscreen->visibleTiles.empty()) return;

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
    ::drawTerrainMeshes(*this, offscreen->visibleTiles, offscreen->ecef.pick, offscreen->planar.pick, *offscreen->whitePixel);
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

bool GLMapView2::animate() NOTHROWS {
    const bool retval = GLGlobeBase::animate();
    drawSrid = renderPasses[0].drawSrid;
    drawMapScale = renderPasses[0].drawMapScale;
    drawLat = renderPasses[0].drawLat;
    drawLng = renderPasses[0].drawLng;
    drawAlt = renderPasses[0].drawAlt;
    drawRotation = renderPasses[0].drawRotation;
    drawTilt = renderPasses[0].drawTilt;
    focusx = renderPasses[0].focusx;
    focusy = renderPasses[0].focusy;
    drawMapResolution = renderPasses[0].drawMapResolution;
    left = renderPasses[0].left;
    right = renderPasses[0].right;
    top = renderPasses[0].top;
    bottom = renderPasses[0].bottom;

    if (retval) {
        drawVersion++;
        if (offscreen.get())
            offscreen->lastTerrainVersion = ~offscreen->lastTerrainVersion;
    }

    return retval;
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
    if (TE_ISNAN(elevation)) {
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

TAKErr GLMapView2::intersectWithTerrain2(GeoPoint2 *value, const MapSceneModel2 &map_scene, const float x, const float y) const NOTHROWS
{
#if 0
    GLMapView2 *t = const_cast<GLMapView2 *>(this);
    t->debugDrawOffscreen = !t->debugDrawOffscreen;
#endif
    std::shared_ptr<const TerrainTile> ignored;
    return intersectWithTerrainImpl(value, ignored, map_scene, x, y);
}
TAKErr GLMapView2::inverse(MapRenderer::InverseResult *result, GeoPoint2 *value, const MapRenderer::InverseMode mode, const unsigned int hints, const Point2<double> &screen, const MapRenderer::DisplayOrigin origin) NOTHROWS
{
    if(!result)
        return TE_InvalidArg;
    if(!value)
        return TE_InvalidArg;
    Point2<double> xyz(screen);
    MapSceneModel2 sm;
    {
        ReadLock rlock(renderPasses0Mutex);
        sm = renderPasses[0].scene;
    }
    if(origin == MapRenderer::UpperLeft)
        xyz.y = (sm.height - xyz.y);
    switch(mode) {
        case InverseMode::Transform :
        {
            Point2<double> proj;
            sm.inverseTransform.transform(&proj, xyz);
            *result = (sm.projection->inverse(value, proj) == TE_Ok) ? MapRenderer::Transformed : MapRenderer::None;
            return TE_Ok;
        }
        case InverseMode::RayCast :
        {
            if (!(hints & MapRenderer::IgnoreTerrainMesh) &&
                intersectWithTerrain2(value, sm, (float) xyz.x, (float) xyz.y) == TE_Ok) {
                *result = MapRenderer::TerrainMesh;
                return TE_Ok;
            }
            if (sm.inverse(value, Point2<float>((float) xyz.x, (float) xyz.y, (float) xyz.z)) ==
                TE_Ok) {
                *result = MapRenderer::GeometryModel;
                return TE_Ok;
            }
            *result = MapRenderer::None;
            return TE_Ok;
        }
        default :
            return TE_InvalidArg;
    }
}
TAKErr GLMapView2::intersectWithTerrainImpl(GeoPoint2 *value, std::shared_ptr<const TerrainTile> &focusTile, const MapSceneModel2 &map_scene, const float x, const float y) const NOTHROWS
{
    // short-circuit for nadir view if no altitude
    if(map_scene.camera.elevation == -90.0) {
        if(map_scene.inverse(value, Point2<float>(x, y)) != TE_Ok)
            return TE_Err;
        if(getTerrainMeshElevation(&value->altitude, value->latitude, value->longitude) != TE_Ok)
            value->altitude = NAN;
        value->altitudeRef = TAK::Engine::Core::AltitudeReference::HAE;
        if(TE_ISNAN(value->altitude) || !value->altitude)
            return TE_Ok;
    }

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
    auto ctx = GLTerrainTile_begin(rp0.scene,
                                   (view.drawSrid == 4978) ? view.offscreen->ecef.pick : view.offscreen->planar.pick);
    Matrix2 lla2tex(0.0, 0.0, 0.0, 0.5,
                    0.0, 0.0, 0.0, 0.5,
                    0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 1.0);
    GLTerrainTile_bindTexture(ctx, view.offscreen->whitePixel->getTexId(), 1u, 1u);
    for(std::size_t i = 0u; i < view.offscreen->visibleTiles.size(); i++) {
        // encode ID as RGBA
        const float r = (((i+1u) >> 24) & 0xFF) / 255.f;
        const float g = (((i+1u) >> 16) & 0xFF) / 255.f;
        const float b = (((i+1u) >> 8) & 0xFF) / 255.f;
        const float a = ((i+1u) & 0xFF) / 255.f;
        // draw the tile
        GLTerrainTile_drawTerrainTiles(ctx, lla2tex, &view.offscreen->visibleTiles[i], 1u, r, g, b, a);
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

TAKErr TAK::Engine::Renderer::Core::GLMapView2_estimateResolution(double *res, GeoPoint2 *closest, const GLMapView2 &model,
                                                                  double ullat, double ullng, double lrlat, double lrlng) NOTHROWS
{
    return GLMapView2_estimateResolution(res, closest, model.renderPasses[0u].scene, ullat, ullng, lrlat, lrlng);
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

TAKErr TAK::Engine::Renderer::Core::GLMapView2_forward(Point2<float> *value, const GLGlobeBase &view, const TAK::Engine::Core::GeoPoint2 &geo) NOTHROWS
{
    return view.renderPass->scene.forward(value, geo);
}

TAKErr TAK::Engine::Renderer::Core::GLMapView2_forward(float *value, const GLGlobeBase &view, const size_t dstSize, const float *src, const size_t srcSize, const size_t count) NOTHROWS
{
    return forwardImpl<float>(value, dstSize, src, srcSize, count, view.renderPass->scene);
}
TAKErr TAK::Engine::Renderer::Core::GLMapView2_forward(float *value, const GLGlobeBase &view, const size_t dstSize, const double *src, const size_t srcSize, const size_t count) NOTHROWS
{
    return forwardImpl<double>(value, dstSize, src, srcSize, count, view.renderPass->scene);
}
TAKErr TAK::Engine::Renderer::Core::GLMapView2_inverse(GeoPoint2 *value, const GLGlobeBase &view, const Point2<float> &point) NOTHROWS
{
    return view.renderPass->scene.inverse(value, point);
}
TAKErr TAK::Engine::Renderer::Core::GLMapView2_inverse(float *value, const GLGlobeBase &view, const size_t dstSize, const float *src, const size_t srcSize, const size_t count) NOTHROWS
{
    return inverseImpl<float>(value, dstSize, src, srcSize, count, view.renderPass->scene);
}
TAKErr TAK::Engine::Renderer::Core::GLMapView2_inverse(double *value, const GLGlobeBase &view, const size_t dstSize, const float *src, const size_t srcSize, const size_t count) NOTHROWS
{
    return inverseImpl<double>(value, dstSize, src, srcSize, count, view.renderPass->scene);
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
        value->drawAlt = view.drawAlt;
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
        value->drawAlt = view.drawAlt;
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
            MapCamera2::Scale);
        GLGlobeBase_glScene(retval);

        // copy everything from view, then update affected fields
        State_save(value, view);

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
            } else if (!TE_ISNAN(candidateDistSq)) {
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
            if (TE_ISNAN(candidateDistSq) || distSq < candidateDistSq) {
                *value = candidate;
                candidateDistSq = distSq;
                focusTile = tiles[i];
            }
        }

        if (TE_ISNAN(candidateDistSq)) {
            map_scene.inverse(value, Point2<float>(x, y));
            // no focus found
            focusTile.reset();
        }

        return TE_ISNAN(candidateDistSq) ? TE_Err : TE_Ok;
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

                // XXX - duplicated code from now inaccessible `validateSceneModel`
                view.scene.set(
                    view.scene.displayDpi,
                    view.scene.width,
                    view.scene.height,
                    view.drawSrid,
                    GeoPoint2(view.drawLat, view.drawLng, view.drawAlt, TAK::Engine::Core::AltitudeReference::HAE),
                    view.scene.focusX,
                    view.scene.focusY,
                    view.scene.camera.azimuth,
                    90.0+view.scene.camera.elevation,
                    view.scene.gsd,
                    view.scene.camera.mode);
                GLGlobeBase_glScene(view.scene);
                {
                   double mx[16];
                    view.scene.forwardTransform.get(mx, Matrix2::COLUMN_MAJOR);
                    for(std::size_t i = 0u; i < 16u; i++)
                        view.sceneModelForwardMatrix[i] = (float)mx[i];
                }
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
        glEnableVertexAttribArray(shader.aEcefVertCoords);

        // draw terrain tiles
        std::size_t idx = 0u;
        for (auto tile = terrainTiles.begin(); tile != terrainTiles.end(); tile++) {
            float *color = meshColors[idx%13u];
            drawTerrainMeshImpl(renderPass, shader, mvp, local, numLocal, *tile, color[0], color[1], color[2], color[3]);
            idx++;
        }

        glDisableVertexAttribArray(shader.base.aVertexCoords);
        glDisableVertexAttribArray(shader.aEcefVertCoords);
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
        for(std::size_t i = numLocal; i >= 1; i--)
            matrix.concatenate(local[i-1u]);
        if(tile.data_proj.srid == state.drawSrid)
            matrix.concatenate(tile.data_proj.localFrame);
        else
            matrix.concatenate(tile.data.localFrame);
        glUniformMatrix4(shader.base.uMVP, matrix);

        // set the local frame for the offscreen texture
        Matrix2 lla2tex(0.0, 0.0, 0.0, 0.5,
                        0.0, 0.0, 0.0, 0.5,
                        0.0, 0.0, 0.0, 0.0,
                        0.0, 0.0, 0.0, 1.0);

        glUniformMatrix4(shader.uModelViewOffscreen, lla2tex);

        glUniform4f(shader.base.uColor, r, g, b, a);

#if 0
        if (depthEnabled) {
#else
        if(true) {
#endif
            glDepthFunc(GL_LEQUAL);
        }

        // render offscreen texture
        const VertexDataLayout& layout = tile.data.value->getVertexDataLayout();

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

            glVertexAttribPointer(shader.base.aVertexCoords, 3u, GL_FLOAT, false, static_cast<GLsizei>(layout.position.stride), static_cast<const uint8_t*>(vertexCoords) + layout.position.offset);
            VertexArray ecefAttr;
            if (VertexDataLayout_getVertexArray(&ecefAttr, layout, tile.ecefAttr) == TE_Ok) {
                glVertexAttribPointer(shader.aEcefVertCoords, 3u, GL_FLOAT, false, static_cast<GLsizei>(ecefAttr.stride), (static_cast<const uint8_t*>(vertexCoords) + ecefAttr.offset));
            }
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
