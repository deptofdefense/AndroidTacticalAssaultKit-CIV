#ifndef TAK_ENGINE_RENDERER_CORE_GLMAPVIEW2_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLMAPVIEW2_H_INCLUDED

#include <memory>
#include <vector>
#include <string>

#include "core/MapSceneModel2.h"
#include "core/AtakMapView.h"
#include "core/AtakMapController.h"
#include "core/GeoPoint2.h"
#include "core/MapRenderer.h"
#include "core/RenderContext.h"
#include "core/RenderSurface.h"
#include "feature/Envelope2.h"
#include "elevation/ElevationChunk.h"
#include "renderer/core/GLLayer2.h"
#include "core/Layer2.h"
#include "math/Statistics.h"
#include "port/Collection.h"
#include "util/MemBuffer2.h"
#include "util/Memory.h"
#include "util/Error.h"
#include "renderer/GLTexture2.h"
#include "renderer/core/GLAntiMeridianHelper.h"
#include "renderer/core/GLGlobeBase.h"
#include "renderer/elevation/TerrainTile.h"
#include "thread/RWMutex.h"

#include <map>

namespace atakmap {
    namespace renderer {
        namespace map {
            class GLMapView;
        }
    }
}

#ifdef _MSC_VER
#ifdef near
#undef near
#endif
#ifdef far
#undef far
#endif
#endif

namespace TAK
{
    namespace Engine
    {
        namespace Renderer
        {
            namespace Elevation {
                class TerrainRenderService;
            }

            namespace Core
            {
                class GLLabelManager;

                class ENGINE_API GLMapView2 :
                    public GLGlobeBase,
                    public atakmap::core::AtakMapView::MapMovedListener,
                    public atakmap::core::AtakMapView::MapProjectionChangedListener,
                    public atakmap::core::AtakMapView::MapElevationExaggerationFactorListener,
                    public atakmap::core::AtakMapView::MapResizedListener,
                    public atakmap::core::MapControllerFocusPointChangedListener
                {
                private:
                    class Offscreen;
                public:
                    /**
                     * Enumeration of possible render passes. May be bitwise OR'd
                     */
                    enum RenderPass
                    {
                        Surface = 0x0001,
                        Sprites = 0x0002,
                        UserInterface = 0x0004,
                        Scenes = 0x0008,
                        XRay = 0x0010,
                        Surface2 = 0x0020,
                    };
                public:
                    GLMapView2(TAK::Engine::Core::RenderContext &ctx, atakmap::core::AtakMapView &aview, int left, int bottom, int right, int top) NOTHROWS;
                public:
                    ~GLMapView2() NOTHROWS;
                public: // public interface
                    virtual Util::TAKErr start() NOTHROWS override;
                    virtual Util::TAKErr stop() NOTHROWS override;
                    virtual void render() NOTHROWS override;
                    /**
                     * Invokes `relesae()` on all renderables; subsequent call
                     * to `render()` will force per-renderable
                     * reinitialization per the contract of `GLMapRenderable`.
                     */
                    virtual void release() NOTHROWS override;
                    virtual Util::TAKErr getTerrainMeshElevation(double *value, const double latitude, const double longitude) const NOTHROWS override;
                    virtual Elevation::TerrainRenderService &getTerrainRenderService() const NOTHROWS override;
                public : // render debugging
                    bool isRenderDiagnosticsEnabled() const NOTHROWS;
                    void setRenderDiagnosticsEnabled(const bool enabled) NOTHROWS;
                    void addRenderDiagnosticMessage(const char *msg) NOTHROWS;
                    bool isContinuousScrollEnabled() const NOTHROWS;
                public: // MapMovedListener
                    void mapMoved(atakmap::core::AtakMapView *map_, const bool animate) override;
                public: // MapProjectionChangedListener
                    void mapProjectionChanged(atakmap::core::AtakMapView* map_view) override;
                public : // MapResizedListener
                    void mapResized(atakmap::core::AtakMapView *mapView);
                public:
                    void mapElevationExaggerationFactorChanged(atakmap::core::AtakMapView *map_view, const double factor) override;
                public: // MapControllerFocusPointChangedListener
                    void mapControllerFocusPointChanged(atakmap::core::AtakMapController *controller, const atakmap::math::Point<float> * const focus) override;
                public:
                    TAK::Engine::Util::TAKErr intersectWithTerrain2(TAK::Engine::Core::GeoPoint2 *retGP, const TAK::Engine::Core::MapSceneModel2 &map_scene, const float x, const float y) const NOTHROWS;
                    virtual Util::TAKErr inverse(TAK::Engine::Core::MapRenderer::InverseResult *result, TAK::Engine::Core::GeoPoint2 *value, const TAK::Engine::Core::MapRenderer::InverseMode mode, const unsigned int hints, const Math::Point2<double> &screen, const TAK::Engine::Core::MapRenderer::DisplayOrigin) NOTHROWS override;
                public:
                    static double getRecommendedGridSampleDistance() NOTHROWS;
                private :
                    TAK::Engine::Util::TAKErr intersectWithTerrainImpl(TAK::Engine::Core::GeoPoint2 *retGP, std::shared_ptr<const Elevation::TerrainTile> &focusTile, const TAK::Engine::Core::MapSceneModel2 &map_scene, const float x, const float y) const NOTHROWS;
                    static TAK::Engine::Util::TAKErr glPickTerrainTile(std::shared_ptr<const Elevation::TerrainTile> *value, GLMapView2 *view, const TAK::Engine::Core::MapSceneModel2 &map_scene, const float x, const float y) NOTHROWS;
                    static void glPickTerrainTile2(void *opaque) NOTHROWS;
                protected: // protected interface
                    /** prepares the scene for rendering */
                    virtual void prepareScene() NOTHROWS override;
                    virtual void computeBounds() NOTHROWS override;
                    /** renders the current scene */
                    virtual void drawRenderables() NOTHROWS override;
                    virtual void drawRenderables(const State &state) NOTHROWS override;
                protected :
                    virtual bool animate() NOTHROWS override;
                private :
                    void initOffscreenShaders() NOTHROWS;
                    bool initOffscreenRendering() NOTHROWS;

                    void drawTerrainTiles(const TAK::Engine::Renderer::GLTexture2 &tex, const std::size_t drawSurfaceWidth, const std::size_t drawSurfaceHeight) NOTHROWS;
                    void drawTerrainMeshes() NOTHROWS;

                    Util::TAKErr constructOffscreenRenderPass(const bool preserveBounds, const double resolution, const double tilt, const bool base_map, const std::size_t drawSurfaceWidth, const std::size_t drawSurfaceHeight, const float x, const float y, const float width, const float height) NOTHROWS;
                    void refreshSceneMatrices() NOTHROWS;

                public:
                    virtual int getTerrainVersion() const NOTHROWS override;
                    void updateTerrainMesh(Math::Statistics *meshStats) NOTHROWS;
                    Util::TAKErr visitTerrainTiles(Util::TAKErr(*visitor)(void *opaque, const std::shared_ptr<const Elevation::TerrainTile> &tile) NOTHROWS, void *opaque) NOTHROWS;
                protected :
                    virtual Util::TAKErr createLayerRenderer(std::unique_ptr<GLLayer2, void(*)(const GLLayer2 *)> &value, TAK::Engine::Core::Layer2 &subject) NOTHROWS override;
                private:
                    static void glElevationExaggerationFactorChanged(void *opaque) NOTHROWS;
                public:
                    /** the grid offset (latitude) of the current scene (upper-left) */
                    double drawGridOffsetLat;
                    /** the grid offset (longitude) of the current scene (upper-left) */
                    double drawGridOffsetLng;
                    /** the grid cell width (longitudinal) in degrees */
                    double drawGridCellWidth;
                    /** the grid cell height (longitudinal) in degrees */
                    double drawGridCellHeight;
                    /** the number of grid cells along the horizontal (longitudinal) axis */
                    int drawGridNumCellsX;
                    /** the number of grid cells along the vertical (latitudinal) axis */
                    int drawGridNumCellsY;

                    bool drawHorizon;    //COVERED
                    double pixelDensity; //Unaccounted for

                    /** The scale that the map is being drawn at. */
                    double drawMapScale {2.5352504279048383E-9}; //COVERED
                    /** The resolution in meters-per-pixel that the map is being drawn at. */
                    double drawMapResolution {0.0};
                    /** The latitude of the center point of the rendering */
                    double drawLat {0.0};
                    /** The longitude of the center point of the rendering */
                    double drawLng {0.0};
                    double drawAlt {0.0};
                    /** The rotation, in radians, of the map about the center point */
                    double drawRotation {0.0};
                    /** The tilt, in radians, of the map about the center point */
                    double drawTilt {0.0};
                    float focusx, focusy;

                    int drawSrid;// = -1; //COVERED

                    TAK::Engine::Core::GeoPoint2 upperLeft; //COVERED
                    TAK::Engine::Core::GeoPoint2 upperRight; //COVERED
                    TAK::Engine::Core::GeoPoint2 lowerRight; //COVERED
                    TAK::Engine::Core::GeoPoint2 lowerLeft; //COVERED

                    int renderPump; //COVERED
                    double westBound;// = -180; //COVERED
                    double southBound;// = -90; //COVERED
                    double northBound;// = 90; //COVERED
                    double eastBound;// = 180; //COVERED
                    bool crossesIDL;// = false;
                    bool poleInView;
                    GLAntiMeridianHelper idlHelper;// = new GLAntiMeridianHelper();

                    int left; //COVERED
                    int right; //COVERED
                    int top; //COVERED
                    int bottom; //COVERED
                    float near;
                    float far;

                    TAK::Engine::Core::MapSceneModel2 scene;
                    float sceneModelForwardMatrix[16];

                    std::size_t numRenderPasses;
                    float terrainBlendFactor;

                private:
                    //Util::TAKErr forwardImpl(TAK::Engine::Math::Point2<float>* retval, const TAK::Engine::Core::GeoPoint2& p) const;
                    //Util::TAKErr inverseImpl(TAK::Engine::Core::GeoPoint2* p, const TAK::Engine::Math::Point2<float>& retval) const;
                    //static void wrapCorner(TAK::Engine::Core::GeoPoint2& g);

                    std::unique_ptr<Offscreen, void(*)(const Offscreen *)> offscreen;
                    mutable Thread::RWMutex offscreenMutex;
                    const bool enableMultiPassRendering;
                    const bool debugDrawBounds;
                    std::unique_ptr<Elevation::TerrainRenderService, void(*)(const Elevation::TerrainRenderService *)> terrain;
                    bool debugDrawOffscreen;
                    int dbgdrawflags;
                    bool debugDrawMesh;
                    bool debugDrawDepth;
                    bool suspendMeshFetch;
                    double tiltSkewOffset;
                    double tiltSkewMult;
                    bool inRenderPump;

                    bool gpuTerrainIntersect;
                    bool diagnosticMessagesEnabled;
                    std::vector<std::string> diagnosticMessages;
                public :
                    bool continuousScrollEnabled;
                    atakmap::core::AtakMapView &view; //COVERED
                private:
                    struct
                    {
                        int terrainVersion {-1};
                        TAK::Engine::Core::GeoPoint2 point;
                        int sceneModelVersion {-1};
                        std::shared_ptr<const Elevation::TerrainTile> tile;
                    } focusEstimation;
                public :

#ifdef __ANDROID__
                public :
#endif
                public :
#ifdef __ANDROID__
                private :
#endif

                    friend class atakmap::renderer::map::GLMapView;
                    friend struct State;
                };

                typedef std::unique_ptr<GLMapView2, void(*)(const GLMapView2 *)> GLMapView2Ptr;

                ENGINE_API Util::TAKErr GLMapView2_estimateResolution(double *res, TAK::Engine::Core::GeoPoint2 *closest, const GLMapView2 &model,
                                                           double ullat, double ullng, double lrlat, double lrlng) NOTHROWS;
                ENGINE_API Util::TAKErr GLMapView2_estimateResolution(double *res, TAK::Engine::Core::GeoPoint2 *closest,
                                                           const TAK::Engine::Core::MapSceneModel2 &model,
                                                           double ullat, double ullng, double lrlat, double lrlng) NOTHROWS;

                ENGINE_API Util::TAKErr GLMapView2_forward(Math::Point2<float> *value, const GLGlobeBase &view, const TAK::Engine::Core::GeoPoint2 &geo) NOTHROWS;
                ENGINE_API Util::TAKErr GLMapView2_forward(float *value, const GLGlobeBase &view, const size_t dstSize, const float *src, const size_t srcSize, const size_t count) NOTHROWS;
                ENGINE_API Util::TAKErr GLMapView2_forward(float *value, const GLGlobeBase &view, const size_t dstSize, const double *src, const size_t srcSize, const size_t count) NOTHROWS;
                ENGINE_API Util::TAKErr GLMapView2_inverse(TAK::Engine::Core::GeoPoint2 *value, const GLGlobeBase &view, const Math::Point2<float> &point) NOTHROWS;
                ENGINE_API Util::TAKErr GLMapView2_inverse(float *value, const GLGlobeBase &view, const size_t dstSize, const float *src, const size_t srcSize, const size_t count) NOTHROWS;
                ENGINE_API Util::TAKErr GLMapView2_inverse(double *value, const GLGlobeBase &view, const size_t dstSize, const float *src, const size_t srcSize, const size_t count) NOTHROWS;

            }
        }
    }
}

#endif
