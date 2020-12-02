#ifndef TAK_ENGINE_RENDERER_CORE_GLMAPVIEW2_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLMAPVIEW2_H_INCLUDED

#include <memory>

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
                    public TAK::Engine::Core::MapRenderer,
                    public atakmap::core::AtakMapView::MapMovedListener,
                    public atakmap::core::AtakMapView::MapProjectionChangedListener,
                    public atakmap::core::AtakMapView::MapLayersChangedListener,
                    public atakmap::core::AtakMapView::MapElevationExaggerationFactorListener,
                    public atakmap::core::AtakMapView::MapResizedListener,
                    public atakmap::core::MapControllerFocusPointChangedListener
                {
                private :
                    class TerrainTileInternal;
                public :
                    struct ENGINE_API State
                    {
                        State() NOTHROWS;

                        double drawMapScale;
                        double drawMapResolution;
                        double drawLat;
                        double drawLng;
                        double drawRotation;
                        double drawTilt;
                        double animationFactor;
                        int drawVersion;
                        bool targeting;
                        double westBound;
                        double southBound;
                        double northBound;
                        double eastBound;
                        int left;
                        int right;
                        int top;
                        int bottom;
                        float near;
                        float far;
                        int drawSrid;
                        float focusx, focusy;
                        TAK::Engine::Core::GeoPoint2 upperLeft;
                        TAK::Engine::Core::GeoPoint2 upperRight;
                        TAK::Engine::Core::GeoPoint2 lowerRight;
                        TAK::Engine::Core::GeoPoint2 lowerLeft;
                        bool settled;
                        int renderPump;
                        TAK::Engine::Math::Matrix2 verticalFlipTranslate;
                        int verticalFlipTranslateHeight;
                        int64_t animationLastTick;
                        int64_t animationDelta;
                        int sceneModelVersion;
                        TAK::Engine::Core::MapSceneModel2 scene;
                        float sceneModelForwardMatrix[16];
                        bool drawHorizon;
                        bool crossesIDL;
                        bool poleInView;
                        double displayDpi;

                        /** if non-zero, the ID of the texture that will be the target for the state's render pass */
                        int texture;
                        /** bitwise-OR of render pass flags that this state includes */
                        int renderPass;

                        struct
                        {
                            float x;
                            float y;
                            float width;
                            float height;
                        } viewport;

                        bool basemap;
                        bool debugDrawBounds;
                    };
                private:
                    class Offscreen;

                    struct TargetView
                    {
                        double targetMapScale;
                        double targetLat;
                        double targetLng;
                        double targetRotation;
                        double targetTilt;

                        float targetFocusx;
                        float targetFocusy;
                    };

                    struct AsyncRunnable;
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
                    };
                public:
                    GLMapView2(TAK::Engine::Core::RenderContext &ctx, atakmap::core::AtakMapView &aview, int left, int bottom, int right, int top) NOTHROWS;
                public:
                    ~GLMapView2() NOTHROWS;
                public: // public interface
                    Util::TAKErr start() NOTHROWS;
                    Util::TAKErr stop() NOTHROWS;
                    void setBaseMap(GLMapRenderable2Ptr &&map) NOTHROWS;
                    void setLabelManager(GLLabelManager* labelManager) NOTHROWS;
                    GLLabelManager* getLabelManager() const NOTHROWS;
                    void render() NOTHROWS;
                    Util::TAKErr getTerrainMeshElevation(double *value, const double latitude, const double longitude) const NOTHROWS;
                    Elevation::TerrainRenderService &getTerrainRenderService() NOTHROWS;
                public: // coordinate transformation functions
                    Util::TAKErr forward(Math::Point2<float> *value, const TAK::Engine::Core::GeoPoint2 &geo) const NOTHROWS;
                    Util::TAKErr forward(float *value, const size_t dstSize, const float *src, const size_t srcSize, const size_t count) const NOTHROWS;
                    Util::TAKErr forward(float *value, const size_t dstSize, const double *src, const size_t srcSize, const size_t count) const NOTHROWS;
                    Util::TAKErr inverse(TAK::Engine::Core::GeoPoint2 *value, const Math::Point2<float> &point) const NOTHROWS;
                    Util::TAKErr inverse(float *value, const size_t dstSize, const float *src, const size_t srcSize, const size_t count) const NOTHROWS;
                    Util::TAKErr inverse(double *value, const size_t dstSize, const float *src, const size_t srcSize, const size_t count) const NOTHROWS;
                public : // MapRenderer
                    Util::TAKErr registerControl(const TAK::Engine::Core::Layer2 &layer, const char *type, void *ctrl) NOTHROWS override;
                    Util::TAKErr unregisterControl(const TAK::Engine::Core::Layer2 &layer, const char *type, void *ctrl) NOTHROWS override;
                    Util::TAKErr visitControls(bool *visited, void *opaque, Util::TAKErr(*visitor)(void *opaque, const TAK::Engine::Core::Layer2 &layer, const TAK::Engine::Core::Control &ctrl), const TAK::Engine::Core::Layer2 &layer, const char *type) NOTHROWS override;
                    Util::TAKErr visitControls(bool *visited, void *opaque, Util::TAKErr(*visitor)(void *opaque, const TAK::Engine::Core::Layer2 &layer, const TAK::Engine::Core::Control &ctrl), const TAK::Engine::Core::Layer2 &layer) NOTHROWS override;
                    Util::TAKErr visitControls(void *opaque, Util::TAKErr(*visitor)(void *opaque, const TAK::Engine::Core::Layer2 &layer, const TAK::Engine::Core::Control &ctrl)) NOTHROWS override;
                    bool isContinuousScrollEnabled() const NOTHROWS;
                    Util::TAKErr addOnControlsChangedListener(TAK::Engine::Core::MapRenderer::OnControlsChangedListener *l) NOTHROWS override;
                    Util::TAKErr removeOnControlsChangedListener(TAK::Engine::Core::MapRenderer::OnControlsChangedListener *l) NOTHROWS override;
                    TAK::Engine::Core::RenderContext &getRenderContext() const NOTHROWS;
                public: // MapMovedListener
                    void mapMoved(atakmap::core::AtakMapView *map_, const bool animate) override;
                public: // MapProjectionChangedListener
                    void mapProjectionChanged(atakmap::core::AtakMapView* map_view) override;
                public: // MapLayersChangedListener
                    void mapLayerAdded(atakmap::core::AtakMapView* map_view, atakmap::core::Layer *layer) override;
                    void mapLayerRemoved(atakmap::core::AtakMapView *map_view, atakmap::core::Layer *layer) override;
                    void mapLayerPositionChanged(atakmap::core::AtakMapView* mapView, atakmap::core::Layer *layer, const int oldPosition, const int newPosition) override;
                public : // MapResizedListener
                    void mapResized(atakmap::core::AtakMapView *mapView);
                public:
                    void mapElevationExaggerationFactorChanged(atakmap::core::AtakMapView *map_view, const double factor) override;
                public: // MapControllerFocusPointChangedListener
                    void mapControllerFocusPointChanged(atakmap::core::AtakMapController *controller, const atakmap::math::Point<float> * const focus) override;
                public:
                    TAK::Engine::Util::TAKErr intersectWithTerrain2(TAK::Engine::Core::GeoPoint2 *retGP, const TAK::Engine::Core::MapSceneModel2 &map_scene, const float x, const float y) const NOTHROWS;
                public:
                    static double getRecommendedGridSampleDistance() NOTHROWS;
                private :
                    TAK::Engine::Util::TAKErr intersectWithTerrainImpl(TAK::Engine::Core::GeoPoint2 *retGP, std::shared_ptr<const Elevation::TerrainTile> &focusTile, const TAK::Engine::Core::MapSceneModel2 &map_scene, const float x, const float y) const NOTHROWS;
                private: // protected interface
                    /** prepares the scene for rendering */
                    void prepareScene() NOTHROWS;
                    /** renders the current scene */
                    void drawRenderables() NOTHROWS;
                    void drawRenderables(const State &state) NOTHROWS;

                    void startAnimating(double lat, double lng, double scale, double rotation, double tilt, double animateFactor) NOTHROWS;
                    void startAnimatingFocus(float x, float y, double animateFactor) NOTHROWS;
                    bool animate() NOTHROWS;
                    Util::TAKErr validateSceneModel(const std::size_t width, const std::size_t height) NOTHROWS;
                    void refreshLayersImpl(const std::list<std::shared_ptr<GLLayer2>> &toRender, const std::list<std::shared_ptr<GLLayer2>> &toRelease) NOTHROWS;
                    void refreshLayers(const std::list<atakmap::core::Layer *> &layers) NOTHROWS;
                    void initOffscreenShaders() NOTHROWS;
                    bool initOffscreenRendering() NOTHROWS;

                    void drawTerrainTiles(const TAK::Engine::Renderer::GLTexture2 &tex, const std::size_t drawSurfaceWidth, const std::size_t drawSurfaceHeight) NOTHROWS;
                    void drawTerrainMeshes() NOTHROWS;

                    void drawTerrainMesh(const TAK::Engine::Elevation::ElevationChunk::Data &tile) NOTHROWS;

                    Util::TAKErr constructOffscreenRenderPass(const bool preserveBounds, const double resolution, const double tilt, const bool base_map, const std::size_t drawSurfaceWidth, const std::size_t drawSurfaceHeight, const float x, const float y, const float width, const float height) NOTHROWS;
                    void refreshSceneMatrices() NOTHROWS;

                public:
                    int getTerrainVersion() const NOTHROWS;
                    void updateTerrainMesh(Math::Statistics *meshStats) NOTHROWS;
                    Util::TAKErr visitTerrainTiles(Util::TAKErr(*visitor)(void *opaque, const std::shared_ptr<const Elevation::TerrainTile> &tile) NOTHROWS, void *opaque) NOTHROWS;
                private:
                    static void asyncRefreshLayers(void *opaque) NOTHROWS;
                    static void asyncAnimate(void *opaque) NOTHROWS;
                    static void asyncAnimateFocus(void *opaque) NOTHROWS;
                    static void glElevationExaggerationFactorChanged(void *opaque) NOTHROWS;
                    static void glMapResized(void *opaque) NOTHROWS;
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
                    double drawMapScale;// = 2.5352504279048383E-9; //COVERED
                    /** The resolution in meters-per-pixel that the map is being drawn at. */
                    double drawMapResolution;// = 0.0; //COVERED
                    /** The latitude of the center point of the rendering */
                    double drawLat;// = 0; //COVERED
                    /** The longitude of the center point of the rendering */
                    double drawLng;// = 0; //COVERED
                    /** The rotation, in radians, of the map about the center point */
                    double drawRotation;// = 0; //COVERED
                    /** The tilt, in radians, of the map about the center point */
                    double drawTilt;// = 0;
                    /** The current animation factor for transitions */
                    double animationFactor;// = 0.3; //COVERED
                    /**
                    * The current version of the draw parameters. Must be incremented each time the parameters
                    * change.
                    */
                    int drawVersion;// = 0; //COVERED

                    /** Flag indicating whether or not this view is used for targeting */
                    bool targeting;// = false; //COVERED

                    int drawSrid;// = -1; //COVERED

                    float focusx, focusy; //COVERED

                    double displayDpi;

                    TAK::Engine::Core::GeoPoint2 upperLeft; //COVERED
                    TAK::Engine::Core::GeoPoint2 upperRight; //COVERED
                    TAK::Engine::Core::GeoPoint2 lowerRight; //COVERED
                    TAK::Engine::Core::GeoPoint2 lowerLeft; //COVERED

                    bool settled; //COVERED

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

                    int64_t animationLastTick;// = -1; //COVERED
                    int64_t animationDelta;// = -1; //COVERED
                    int64_t animationLastUpdate;

                    double hardwareTransformResolutionThreshold;
                    double elevationScaleFactor;

                    TAK::Engine::Core::MapSceneModel2 scene; //COVERED
                    TAK::Engine::Core::MapSceneModel2 oscene;
                    float sceneModelForwardMatrix[16];

                    const State *renderPass;
                    State renderPasses[32];
                    std::size_t numRenderPasses;
                    float terrainBlendFactor;

                    mutable Thread::RWMutex renderPassMutex;
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
                    bool suspendMeshFetch;
                    double tiltSkewOffset;
                    double tiltSkewMult;

                public :
                    bool continuousScrollEnabled;
                    TAK::Engine::Core::RenderContext &context; //COVERED
                    atakmap::core::AtakMapView &view; //COVERED
                private:
                    Thread::Mutex layerRenderersMutex;
                    std::shared_ptr<Thread::Mutex> asyncRunnablesMutex;
                    std::shared_ptr<bool> disposed;
                    std::unique_ptr<AsyncRunnable> mapMovedEvent;
                    std::unique_ptr<AsyncRunnable> projectionChangedEvent;
                    std::unique_ptr<AsyncRunnable> focusChangedEvent;
                    std::unique_ptr<AsyncRunnable> layersChangedEvent;
                    std::unique_ptr<AsyncRunnable> mapResizedEvent;

                    /** may only be accessed while holding 'layerRenderersMutex' */
                    std::map<const atakmap::core::Layer*, std::shared_ptr<TAK::Engine::Core::Layer2>> adaptedLayers;
                    std::map<const TAK::Engine::Core::Layer2 *, std::shared_ptr<GLLayer2>> layerRenderers;
                    /** access is only thread-safe on the GL thread */
                    std::list<std::shared_ptr<GLLayer2>>  renderables; //COVERED

                    struct
                    {
                        int terrainVersion;
                        TAK::Engine::Core::GeoPoint2 point;
                        int sceneModelVersion;
                        std::shared_ptr<const Elevation::TerrainTile> tile;
                    } focusEstimation;

                    GLMapRenderable2Ptr basemap; //COVERED
                    GLLabelManager* labelManager;

                public :
                    Math::Matrix2 verticalFlipTranslate; //COVERED
                    std::size_t verticalFlipTranslateHeight;  // COVERED
#ifdef __ANDROID__
                public :
#endif
                public :
                    int sceneModelVersion; //COVERED
#ifdef __ANDROID__
                private :
#endif
                    TargetView animationState;

                    Thread::Mutex controlsMutex;
                    std::map<const TAK::Engine::Core::Layer2 *, std::map<std::string, std::set<void *>>> controls;
                    std::set<TAK::Engine::Core::MapRenderer::OnControlsChangedListener *> controlsListeners;

                    friend class atakmap::renderer::map::GLMapView;
                    friend struct State;
                };

                typedef std::unique_ptr<GLMapView2, void(*)(const GLMapView2 *)> GLMapView2Ptr;

                ENGINE_API Util::TAKErr GLMapView2_estimateResolution(double *res, TAK::Engine::Core::GeoPoint2 *closest, const GLMapView2 &model, 
                                                           double ullat, double ullng, double lrlat, double lrlng) NOTHROWS;
                ENGINE_API Util::TAKErr GLMapView2_estimateResolution(double *res, TAK::Engine::Core::GeoPoint2 *closest,
                                                           const TAK::Engine::Core::MapSceneModel2 &model,
                                                           double ullat, double ullng, double lrlat, double lrlng) NOTHROWS;
            }
        }
    }
}

#endif
