//
// Created by GeoDev on 1/14/2021.
//

#ifndef TAK_ENGINE_RENDERER_CORE_GLGLOBE_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLGLOBE_H_INCLUDED

#include <list>
#include <map>
#include <unordered_map>
#include <vector>

#include "GLAtmosphere.h"

#include "core/AtakMapView.h"
#include "core/MapRenderer.h"
#include "core/MapSceneModel2.h"
#include "model/VertexDataLayout.h"
#include "port/Platform.h"
#include "renderer/GLOffscreenFramebuffer.h"
#include "renderer/core/ColorControl.h"
#include "renderer/core/GLAntiMeridianHelper.h"
#include "renderer/core/GLGlobeBase.h"
#include "renderer/elevation/GLTerrainTile_decls.h"
#include "renderer/elevation/TerrainRenderService.h"
#include "renderer/core/controls/IlluminationControlImpl.h"
#include "thread/RWMutex.h"


namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                class GLGlobeSurfaceRenderer;
                class GLLabelManager;
                class GLLayer2;
                class GLMapRenderable2;

                class ENGINE_API GLGlobe :
                    public GLGlobeBase,
                    public atakmap::core::AtakMapView::MapMovedListener,
                    public atakmap::core::AtakMapView::MapProjectionChangedListener,
                    public atakmap::core::AtakMapView::MapElevationExaggerationFactorListener,
                    public atakmap::core::AtakMapView::MapResizedListener,
                    public atakmap::core::MapControllerFocusPointChangedListener
                {
                private:
                    struct MeshColor
                    {
                        TAK::Engine::Model::DrawMode mode {TAK::Engine::Model::TEDM_Triangles};
                        struct {
                            TAK::Engine::Renderer::Core::ColorControl::Mode mode {TAK::Engine::Renderer::Core::ColorControl::Modulate};
                            unsigned int argb {0xFFFFFFFFu};
                            float r{1.f};
                            float g{1.f};
                            float b{1.f};
                            float a{1.f};
                        } color;
                        bool enabled {false};
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
                public:
                    GLGlobe(TAK::Engine::Core::RenderContext &ctx, atakmap::core::AtakMapView &aview, int left, int bottom, int right, int top) NOTHROWS;
                public:
                    ~GLGlobe() NOTHROWS;
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
                    virtual Util::TAKErr inverse(TAK::Engine::Core::MapRenderer::InverseResult *result, TAK::Engine::Core::GeoPoint2 *value, const TAK::Engine::Core::MapRenderer::InverseMode mode, const unsigned int hints, const Math::Point2<double> &screen, const TAK::Engine::Core::MapRenderer::DisplayOrigin origin) NOTHROWS override;
                public:
                    static double getRecommendedGridSampleDistance() NOTHROWS;
                private :
                    void cullTerrainTiles_cpu() NOTHROWS;
                    void cullTerrainTiles_pbo() NOTHROWS;
                    void setVisibleTerrainTiles(const std::size_t* indices, const std::size_t count, const bool confirmed) NOTHROWS;
                    TAK::Engine::Util::TAKErr intersectWithTerrainImpl(TAK::Engine::Core::GeoPoint2 *retGP, std::shared_ptr<const Elevation::TerrainTile> &focusTile, const TAK::Engine::Core::MapSceneModel2 &map_scene, const float x, const float y) const NOTHROWS;
                    static TAK::Engine::Util::TAKErr glPickTerrainTile(std::shared_ptr<const Elevation::TerrainTile> *value, GLGlobe* view, const TAK::Engine::Core::MapSceneModel2 &map_scene, const float x, const float y) NOTHROWS;
                    static void glPickTerrainTile2(void *opaque) NOTHROWS;
                public :
                    void enableDrawMode(const TAK::Engine::Model::DrawMode mode) NOTHROWS;
                    void disableDrawMode(const TAK::Engine::Model::DrawMode mode) NOTHROWS;
                    bool isDrawModeEnabled(const TAK::Engine::Model::DrawMode mode) const NOTHROWS;
                    void setColor(const TAK::Engine::Model::DrawMode mode, const unsigned int color, const ColorControl::Mode colorMode) NOTHROWS;
                    unsigned int getColor(const TAK::Engine::Model::DrawMode mode) const NOTHROWS;
                    ColorControl::Mode getColorMode(const TAK::Engine::Model::DrawMode mode);
                protected : // protected interface
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

                    void drawTerrainTiles(const TAK::Engine::Renderer::GLTexture2 &tex, const std::size_t drawSurfaceWidth, const std::size_t drawSurfaceHeight) NOTHROWS;
                    void drawTerrainMeshes() NOTHROWS;

                    Util::TAKErr constructOffscreenRenderPass(const bool preserveBounds, const double resolution, const double tilt, const bool base_map, const std::size_t drawSurfaceWidth, const std::size_t drawSurfaceHeight, const float x, const float y, const float width, const float height) NOTHROWS;
                    void refreshSceneMatrices() NOTHROWS;

                public:
                    virtual int getTerrainVersion() const NOTHROWS override;
                    void updateTerrainMesh(Math::Statistics *meshStats) NOTHROWS;
                    Util::TAKErr visitTerrainTiles(Util::TAKErr(*visitor)(void *opaque, const std::shared_ptr<const Elevation::TerrainTile> &tile) NOTHROWS, void *opaque) NOTHROWS;
                    GLGlobeSurfaceRenderer& getSurfaceRenderer() const NOTHROWS;
                    /**
                     * Retrieves the visible surface.
                     *
                     * <P>May only be invoked on the render thread.
                     *
                     * @param value
                     * @return
                     */
                    Util::TAKErr getSurfaceBounds(Port::Collection<Feature::Envelope2> &value) const NOTHROWS;
                public :
                    void setAtmosphereEnabled(const bool enabled) NOTHROWS;
                    bool isAtmosphereEnabled() const NOTHROWS;
                protected :
                    virtual Util::TAKErr createLayerRenderer(std::unique_ptr<GLLayer2, void(*)(const GLLayer2 *)> &value, TAK::Engine::Core::Layer2 &subject) NOTHROWS override;
                private:
                    static void glElevationExaggerationFactorChanged(void *opaque) NOTHROWS;
                public:
                    GLAntiMeridianHelper idlHelper;// = new GLAntiMeridianHelper();

                    std::size_t numRenderPasses;
                    float terrainBlendFactor;

                private:
                    struct
                    {
                        std::unique_ptr<GLTexture2> whitePixel;

                        struct {
                            int terrain{ -1 };
                            int scene{ -1 };
                        } lastVersion;

                        double hfactor{ NAN };
                        int terrainEnabled;

                        struct {
                            TAK::Engine::Renderer::Elevation::TerrainTileShaders color;
                            TAK::Engine::Renderer::Elevation::TerrainTileShaders pick;
                            TAK::Engine::Renderer::Elevation::TerrainTileShaders depth;
                        } ecef;
                        struct {
                            TAK::Engine::Renderer::Elevation::TerrainTileShaders color;
                            TAK::Engine::Renderer::Elevation::TerrainTileShaders pick;
                            TAK::Engine::Renderer::Elevation::TerrainTileShaders depth;
                        } planar;

                        TAK::Engine::Math::Statistics elevationStats;
                        int64_t lastElevationQuery{ 0LL };

                        struct {
                            TAK::Engine::Renderer::GLOffscreenFramebuffer tileCullFbo;
                            GLuint tileCullPbo { GL_NONE };
                            std::vector<std::shared_ptr<const TAK::Engine::Renderer::Elevation::TerrainTile>> terrainTiles;
                            std::vector<std::size_t> visIndices;
                            bool processed {false};
                        } computeContext[2u];

                        std::vector<std::shared_ptr<const TAK::Engine::Renderer::Elevation::TerrainTile>> *terrainTiles2{nullptr};

                        struct {
                            bool confirmed{ false };
                            std::vector<TAK::Engine::Renderer::Elevation::GLTerrainTile> value;
                            std::vector<intptr_t> lastConfirmed;
                        } visibleTiles;
                        std::unordered_map<const TAK::Engine::Renderer::Elevation::TerrainTile *, TAK::Engine::Renderer::Elevation::GLTerrainTile> gltiles;
                        TAK::Engine::Renderer::GLOffscreenFramebuffer depthSamplerFbo;
                        std::size_t tileCullFboReadIdx{ 0u };
                        Util::array_ptr<uint32_t> rgba;
                        std::size_t terrainFetch{ 0u };

                        TAK::Engine::Core::MapSceneModel2 computeScene;

                        struct
                        {
                            bool enabled{ false };
                            bool capture{ false };
                            TAK::Engine::Math::Point2<double> frustum[8u];
                        } debugFrustum;
                    } offscreen;

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
                    struct{
                        GLAtmosphere renderer;
                        bool enabled{true};
                    } atmosphere;

                    bool gpuTerrainIntersect;
                    bool diagnosticMessagesEnabled;
                    std::vector<std::string> diagnosticMessages;

                    std::vector<MeshColor> meshDrawModes;
                    std::unique_ptr<GLGlobeSurfaceRenderer> surfaceRenderer;
                public :
                    atakmap::core::AtakMapView &view; //COVERED
                private:
                    struct
                    {
                        int terrainVersion {-1};
                        TAK::Engine::Core::GeoPoint2 point;
                        int sceneModelVersion {-1};
                        std::shared_ptr<const Elevation::TerrainTile> tile;
                    } focusEstimation;
                    Controls::IlluminationControlImpl illuminationControl;
                public:
                    Controls::IlluminationControlImpl* getIlluminationControl() const NOTHROWS override;
                private :
                    friend class atakmap::renderer::map::GLMapView;
                    friend class GLGlobeSurfaceRenderer;
                    friend struct State;
                    friend void GLMapViewDebug_drawVisibleSurface(GLGlobe &) NOTHROWS;
                    void GLGlobe_lookAt(GLGlobe &, const TAK::Engine::Core::GeoPoint2 &, const double, const double, const double, const double, const TAK::Engine::Core::MapRenderer::CameraCollision, const bool) NOTHROWS;
                };

                /**
                 * Updates camera state, potentially applying terrain mesh
                 * collision adjustments.
                 * @param collideRadius If greater than zero, defines the
                 *                      minimum radius that must be maintained
                 *                      between the camera and the terrain mesh
                 *
                 * @return  `TE_Done` if camera collision occurs and
                 *          `collision` is `CameraCollision::Abort`; `TE_Ok` on
                 *          success; various code on failure.
                 *
                 * @deprecated interim utility API
                 */
                ENGINE_API Util::TAKErr GLGlobe_lookAt(GLGlobe& value, const TAK::Engine::Core::GeoPoint2 &focus, const double resolution, const double rotation, const double tilt, const double collideRadius, const TAK::Engine::Core::MapRenderer::CameraCollision collision, const bool animate) NOTHROWS;
                ENGINE_API Util::TAKErr GLGlobe_lookFrom(GLGlobe& value, const TAK::Engine::Core::GeoPoint2 &from, const double rotation, const double elevation, const double collideRadius, const TAK::Engine::Core::MapRenderer::CameraCollision collision, const bool animate) NOTHROWS;
            }
        }
    }
}

#endif //TAK_ENGINE_RENDERER_CORE_GLGLOBE_H_INCLUDED
