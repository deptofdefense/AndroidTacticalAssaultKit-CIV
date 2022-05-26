//
// Created by GeoDev on 1/14/2021.
//

#ifndef TAK_ENGINE_RENDERER_CORE_GLGLOBEBASE_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLGLOBEBASE_H_INCLUDED

#ifdef near
#undef near
#endif
#ifdef far
#undef far
#endif

#include <map>
#include <set>

#include "core/AtakMapView.h"
#include "core/MapRenderer2.h"
#include "core/MapSceneModel2.h"
#include "port/Platform.h"
#include "renderer/core/controls/SurfaceRendererControl.h"
#include "renderer/core/controls/IlluminationControlImpl.h"
#include "renderer/elevation/GLTerrainTile_decls.h"
#include "thread/Mutex.h"
#include "thread/RWMutex.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Elevation {
                class TerrainRenderService;
            }

            namespace Core {
                class GLLabelManager;
                class GLLayer2;
                class GLMapRenderable2;

                class ENGINE_API GLGlobeBase :
                        public TAK::Engine::Core::MapRenderer2,
                        public atakmap::core::AtakMapView::MapLayersChangedListener
                {
                public :
                    struct ENGINE_API State
                    {
                        State() NOTHROWS;
                        State(const GLGlobeBase& view) NOTHROWS;

                        double drawMapScale {2.5352504279048383E-9};
                        double drawMapResolution {0.0};
                        double drawLat {0.0};
                        double drawLng {0.0};
                        double drawAlt {0.0};
                        double drawRotation {0.0};
                        double drawTilt {0.0};
                        double animationFactor {0.3};
                        int drawVersion {0};
                        bool targeting {false};
                        bool isScreenshot {false};
                        double westBound {-180.0};
                        double southBound {-90.0};
                        double northBound {90.0};
                        double eastBound {180.0};
                        int left {0};
                        int right {0};
                        int top {0};
                        int bottom {0};
                        float near {1.f};
                        float far {-1.f};
                        int drawSrid {-1};
                        float focusx {0};
                        float focusy {0};
                        TAK::Engine::Core::GeoPoint2 upperLeft;
                        TAK::Engine::Core::GeoPoint2 upperRight;
                        TAK::Engine::Core::GeoPoint2 lowerRight;
                        TAK::Engine::Core::GeoPoint2 lowerLeft;
                        bool settled {false};
                        int renderPump {-1};
                        TAK::Engine::Math::Matrix2 verticalFlipTranslate;
                        int verticalFlipTranslateHeight {0};
                        int64_t animationLastTick {-1};
                        int64_t animationDelta {-1};
                        int sceneModelVersion {-1};
                        TAK::Engine::Core::MapSceneModel2 scene;
                        float sceneModelForwardMatrix[16] {};
                        bool drawHorizon {false};
                        bool crossesIDL {false};
                        bool poleInView {false};
                        double displayDpi {0};

                        /** if non-zero, the ID of the texture that will be the target for the state's render pass */
                        int texture {0};
                        /** bitwise-OR of render pass flags that this state includes */
                        int renderPass {0};

                        struct
                        {
                            float x {0};
                            float y {0};
                            float width {0};
                            float height {0};
                        } viewport;

                        bool basemap {false};
                        bool debugDrawBounds {false};

                        struct {
                            Elevation::GLTerrainTile *value {nullptr};
                            std::size_t count {0u};
                        } renderTiles;
                    };
                protected :
                    struct TargetView
                    {
                        double mapScale;
                        TAK::Engine::Core::GeoPoint2 point;
                        double rotation;
                        double tilt;
                        struct {
                            double near{NAN};
                            double far{NAN};
                            bool override{false};
                        } clip;

                        float focusx;
                        float focusy;
                    };
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
                private :
                    struct AsyncRunnable;
                protected :
                    GLGlobeBase(TAK::Engine::Core::RenderContext &ctx, const double dpi, const TAK::Engine::Core::MapCamera2::Mode mode) NOTHROWS;
                    ~GLGlobeBase() NOTHROWS;
                public :
                    virtual Util::TAKErr start() NOTHROWS;
                    virtual Util::TAKErr stop() NOTHROWS;
                    virtual void render() NOTHROWS;
                    void setBaseMap(std::unique_ptr<GLMapRenderable2, void(*)(const GLMapRenderable2 *)> &&map) NOTHROWS;

#ifndef __ANDROID__
                    void setLabelManager(GLLabelManager* labelManager) NOTHROWS;
#endif
                    GLLabelManager* getLabelManager() const NOTHROWS;
                    /**
                     * Invokes `release()` on all renderables; subsequent call
                     * to `render()` will force per-renderable
                     * reinitialization per the contract of `GLMapRenderable`.
                     */
                    virtual void release() NOTHROWS;
                protected :
                    /** prepares the scene for rendering */
                    virtual void prepareScene() NOTHROWS;
                    /**
                     * Computes the bounds for the `renderPasses[0]` based on
                     * the validated scene. The fields on `renderPasses[0]`
                     * should be properly set on return of this function
                     * <UL>
                     *   <LI>`upperLeft`</LI>
                     *   <LI>`upperRight`</LI>
                     *   <LI>`lowerRight`</LI>
                     *   <LI>`lowerLeft`</LI>
                     *   <LI>`northBound`</LI>
                     *   <LI>`westBound`</LI>
                     *   <LI>`southBound`</LI>
                     *   <LI>`eastBound`</LI>
                     *   <LI>`crossesIDL`</LI>
                     * </UL>
                     */
                    virtual void computeBounds() NOTHROWS = 0;
                    /** renders the current scene */
                    virtual void drawRenderables() NOTHROWS = 0;
                    virtual void drawRenderables(const State &state) NOTHROWS;
                    virtual void drawRenderable(GLMapRenderable2 &renderable, const int renderPass) NOTHROWS;
                public : // MapRenderer
                    virtual Util::TAKErr registerControl(const TAK::Engine::Core::Layer2 &layer, const char *type, void *ctrl) NOTHROWS override;
                    virtual Util::TAKErr unregisterControl(const TAK::Engine::Core::Layer2 &layer, const char *type, void *ctrl) NOTHROWS override;
                    virtual Util::TAKErr visitControls(bool *visited, void *opaque, Util::TAKErr(*visitor)(void *opaque, const TAK::Engine::Core::Layer2 &layer, const TAK::Engine::Core::Control &ctrl), const TAK::Engine::Core::Layer2 &layer, const char *type) NOTHROWS override;
                    virtual Util::TAKErr visitControls(bool *visited, void *opaque, Util::TAKErr(*visitor)(void *opaque, const TAK::Engine::Core::Layer2 &layer, const TAK::Engine::Core::Control &ctrl), const TAK::Engine::Core::Layer2 &layer) NOTHROWS override;
                    virtual Util::TAKErr visitControls(void *opaque, Util::TAKErr(*visitor)(void *opaque, const TAK::Engine::Core::Layer2 &layer, const TAK::Engine::Core::Control &ctrl)) NOTHROWS override;
                    virtual Util::TAKErr addOnControlsChangedListener(TAK::Engine::Core::MapRenderer::OnControlsChangedListener *l) NOTHROWS override;
                    virtual Util::TAKErr removeOnControlsChangedListener(TAK::Engine::Core::MapRenderer::OnControlsChangedListener *l) NOTHROWS override;
                    virtual TAK::Engine::Core::RenderContext &getRenderContext() const NOTHROWS override;
                public: // camera control
                    virtual Util::TAKErr lookAt(const TAK::Engine::Core::GeoPoint2 &from, const TAK::Engine::Core::GeoPoint2 &at, const TAK::Engine::Core::MapRenderer::CameraCollision collision, const bool animate) NOTHROWS;
                    virtual Util::TAKErr lookAt(const TAK::Engine::Core::GeoPoint2 &at, const double resolution, const double azimuth, const double tilt, const TAK::Engine::Core::MapRenderer::CameraCollision collision, const bool animate) NOTHROWS;
                    virtual Util::TAKErr lookFrom(const TAK::Engine::Core::GeoPoint2 &from, const double azimuth, const double elevation, const TAK::Engine::Core::MapRenderer::CameraCollision collision, const bool animate) NOTHROWS;
                    virtual bool isAnimating() const NOTHROWS;
                    virtual Util::TAKErr addOnCameraChangedListener(TAK::Engine::Core::MapRenderer2::OnCameraChangedListener *l) NOTHROWS override;
                    virtual Util::TAKErr removeOnCameraChangedListener(TAK::Engine::Core::MapRenderer2::OnCameraChangedListener *l) NOTHROWS override;
                protected :
                    virtual bool animate() NOTHROWS;
                public :
                    virtual Util::TAKErr setDisplayMode(const TAK::Engine::Core::MapRenderer::DisplayMode mode) NOTHROWS;
                    virtual TAK::Engine::Core::MapRenderer::DisplayMode getDisplayMode() const NOTHROWS;
                    virtual TAK::Engine::Core::MapRenderer::DisplayOrigin getDisplayOrigin() const NOTHROWS;
                    virtual Util::TAKErr setFocusPoint(const float focusx, const float focusy) NOTHROWS;
                    virtual Util::TAKErr getFocusPoint(float *focusx, float *focusy) const NOTHROWS;
                    virtual Util::TAKErr setSurfaceSize(const std::size_t width, const std::size_t height) NOTHROWS;
                    virtual Util::TAKErr inverse(TAK::Engine::Core::MapRenderer::InverseResult *result, TAK::Engine::Core::GeoPoint2 *value, const TAK::Engine::Core::MapRenderer::InverseMode mode, const unsigned int hints, const Math::Point2<double> &screen, const TAK::Engine::Core::MapRenderer::DisplayOrigin) NOTHROWS = 0;
                    virtual Util::TAKErr getMapSceneModel(TAK::Engine::Core::MapSceneModel2 *value, const bool instant, const DisplayOrigin origin) NOTHROWS;
                public: // MapLayersChangedListener
                    void mapLayerAdded(atakmap::core::AtakMapView* map_view, atakmap::core::Layer *layer) override;
                    void mapLayerRemoved(atakmap::core::AtakMapView *map_view, atakmap::core::Layer *layer) override;
                    void mapLayerPositionChanged(atakmap::core::AtakMapView* mapView, atakmap::core::Layer *layer, const int oldPosition, const int newPosition) override;
                protected :
                    virtual Util::TAKErr createLayerRenderer(std::unique_ptr<GLLayer2, void(*)(const GLLayer2 *)> &value, TAK::Engine::Core::Layer2 &subject) NOTHROWS = 0;
                private :
                    void refreshLayersImpl(const std::list<std::shared_ptr<GLLayer2>> &toRender, const std::list<std::shared_ptr<GLLayer2>> &toRelease) NOTHROWS;
                protected :
                    void refreshLayers(const std::list<atakmap::core::Layer *> &layers) NOTHROWS;
                public : // compatibility API
                    virtual int getTerrainVersion() const NOTHROWS;
                    virtual Util::TAKErr getTerrainMeshElevation(double *value, const double latitude, const double longitude) const NOTHROWS;
                    virtual Elevation::TerrainRenderService &getTerrainRenderService() const NOTHROWS = 0;
                    virtual Controls::SurfaceRendererControl* getSurfaceRendererControl() const NOTHROWS;
                protected :
                    static Util::TAKErr validateSceneModel(GLGlobeBase *view, const std::size_t width, const std::size_t height, const TAK::Engine::Core::MapCamera2::Mode mode, const double nearMeters = NAN, const double farMeters = NAN) NOTHROWS;
                private :
                    static void asyncAnimate(void *opaque) NOTHROWS;
                    static void asyncRefreshLayers(void *opaque) NOTHROWS;
                    static void asyncProjUpdate(void *opaque) NOTHROWS;
                    static void asyncAnimateFocus(void *opaque) NOTHROWS;
                    static void glMapResized(void *opaque) NOTHROWS;
                public:
                    /** The current animation factor for transitions */
                    double animationFactor;// override.3; //COVERED
                    /** Flag indicating whether or not this view is used for targeting */
                    bool targeting {false};
                    bool isScreenshot {false};
                    double displayDpi {96.0};
                    int64_t animationLastTick {-1LL};
                    int64_t animationDelta {-1LL};
                    int64_t animationLastUpdate {-1LL};
                    bool settled {false};
                    int sceneModelVersion {0};
                    double elevationScaleFactor {1.0};
                    const State *renderPass{nullptr};
                    State renderPasses[32];
                    bool multiPartPass{false};
                    bool continuousScrollEnabled{true};
                    /** guards `renderPasses[0]` */
                    mutable Thread::RWMutex renderPasses0Mutex;
                    TAK::Engine::Core::RenderContext &context; //COVERED
                    /**
                    * The current version of the draw parameters. Must be incremented each time the parameters
                    * change.
                    */
                    int drawVersion {0};
                protected :
                    struct {
                        TargetView current;
                        TargetView last;
                        TargetView target;
                        bool settled;
                    } animation;
                    /** Thread-safe representation of current state */
                    struct
                    {
                        struct
                        {
                            TAK::Engine::Core::GeoPoint2 geo;
                            float x;
                            float y;
                        } focus;
                        int srid;
                        double resolution;
                        double tilt;
                        double rotation;
                        std::size_t width;
                        std::size_t height;
                    } state;
                    TAK::Engine::Core::MapCamera2::Mode cammode;
                private : // layers management
                    std::shared_ptr<Thread::Mutex> asyncRunnablesMutex;
                    std::shared_ptr<bool> disposed;
                    std::unique_ptr<AsyncRunnable> layersChangedEvent;
                    std::unique_ptr<AsyncRunnable> mapMovedEvent;
                    std::unique_ptr<AsyncRunnable> projectionChangedEvent;
                    std::unique_ptr<AsyncRunnable> focusChangedEvent;
                    std::unique_ptr<AsyncRunnable> mapResizedEvent;

                    /** may only be accessed while holding 'layerRenderersMutex' */
                    std::map<const atakmap::core::Layer*, std::shared_ptr<TAK::Engine::Core::Layer2>> adaptedLayers;
                    std::map<const TAK::Engine::Core::Layer2 *, std::shared_ptr<GLLayer2>> layerRenderers;
                protected :
                    Thread::Mutex layerRenderersMutex;
                    /** access is only thread-safe on the GL thread */
                    std::list<std::shared_ptr<GLLayer2>>  renderables; //COVERED
                private :
                    std::unique_ptr<GLMapRenderable2, void(*)(const GLMapRenderable2 *)> basemap; //COVERED
                    std::unique_ptr<GLLabelManager, void(*)(const GLLabelManager *)> labelManager;
                private : // controls
                    Thread::Mutex controlsMutex;
                    std::map<const TAK::Engine::Core::Layer2 *, std::map<std::string, std::set<void *>>> controls;
                    std::set<TAK::Engine::Core::MapRenderer::OnControlsChangedListener *> controlsListeners;
                    Thread::Mutex cameraChangedMutex;
                    std::set<TAK::Engine::Core::MapRenderer2::OnCameraChangedListener *> cameraListeners;
                public:
                    virtual Controls::IlluminationControlImpl* getIlluminationControl() const NOTHROWS;
                private :
                    friend struct State;
                    friend class GLGlobeSurfaceRenderer;
                };

                /**
                 * Converts the default `MapSceneModel2` origin (upper-left) of the
                 * `forwardTransform` and `inverseTransform` to the GL display origin
                 * (lower-left)
                 * @param scene
                 */
                ENGINE_API void GLGlobeBase_glScene(TAK::Engine::Core::MapSceneModel2 &scene) NOTHROWS;

                typedef std::unique_ptr<GLGlobeBase, void(*)(const GLGlobeBase*)> GLGlobeBasePtr;
            }
        }
    }
}

#endif //TAK_ENGINE_RENDERER_CORE_GLGLOBEBASE_H_INCLUDED
