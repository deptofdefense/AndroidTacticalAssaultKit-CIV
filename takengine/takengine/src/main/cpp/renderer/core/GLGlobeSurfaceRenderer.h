#ifndef TAK_ENGINE_RENDERER_CORE_GLGLOBESURFACERENDERER_H_INCLUDED
#define TAK_ENGINE_RENDERER_CORE_GLGLOBESURFACERENDERER_H_INCLUDED

#include "feature/Envelope2.h"
#include "port/Platform.h"
#include "renderer/GLMegaTexture.h"
#include "renderer/core/GLDiagnostics.h"
#include "renderer/core/GLDirtyRegion.h"
#include "renderer/elevation/GLTerrainTile.h"
#include "thread/Mutex.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Core {
                // forward declaration
                class GLGlobe;

                class ENGINE_API GLGlobeSurfaceRenderer
                {
                private :
                    struct ProgramCounter
                    {
                        std::size_t pc{0u};
                        bool interrupted{false};
                    };
                private :
                    GLGlobeSurfaceRenderer(GLGlobe &owner) NOTHROWS;
                public :
                    ~GLGlobeSurfaceRenderer() NOTHROWS;
                public :
                    /** Marks all visible tiles as dirty. */
                    void markDirty() NOTHROWS;
                    void markDirty(const TAK::Engine::Feature::Envelope2 &region, const bool streaming) NOTHROWS;
                    /**
                     * Sets the minimum refresh interval. The visible tiles of
                     * the surface will be marked as dirty once this amount of
                     * time elapses since the last texture update.
                     * @param millis    The minimum refresh interval, in
                     *                  milliseconds. If `0`, the refresh
                     *                  interval is disabled.
                     */
                    void setMinimumRefreshInterval(const std::size_t millis) NOTHROWS;
                    std::size_t getMinimumRefreshInterval() const NOTHROWS;
                private :
                    /**
                     * Updates the surface texture.
                     * @param limitMillis   A millisecond limit for the update.
                     *                      If the update is not completed
                     *                      within the specified interval, the
                     *                      operation will be paused and will
                     *                      resume on the next call to
                     *                      `update`. The limit is disabled if
                     *                      `0` is specified.
                     */
                    void update(const std::size_t limitMillis) NOTHROWS;
                    bool updateTiles(ProgramCounter &pc, const std::vector<std::size_t> &updateIndices, const bool allowInterrupt, const int64_t limit) NOTHROWS;
                    void syncTexture(const bool evictStale) NOTHROWS;
                    void draw() NOTHROWS;
                    void release() NOTHROWS;
                    void validateFrontIndices() NOTHROWS;
                    bool isRenderPumpComplete() const NOTHROWS;
                private :
                    GLGlobe &owner;
                    TAK::Engine::Renderer::GLMegaTexture front;
                    TAK::Engine::Renderer::GLMegaTexture back;
                    std::vector<TAK::Engine::Renderer::Elevation::GLTerrainTile> drawTiles;
                    std::vector<TAK::Engine::Renderer::GLMegaTexture::TileIndex> frontIndices;
                    bool paused;
                    /** list of dirty regions queued by clients */
                    GLDirtyRegion dirtyRegions;
                    /** indicates all on screen is dirty */
                    bool dirty;
                    bool streamDirty;

                    std::size_t refreshInterval;
                    int64_t lastRefresh;

                    /**
                     * Guards following resources:
                     * <UL>
                     *  <LI>dirty regions
                     * </UL>
                     */
                    Thread::Mutex mutex;

                    struct
                    {
                        struct {
                            ProgramCounter visible;
                            ProgramCounter offscreen;
                        } pc;
                        std::size_t limit{0u};
                        std::size_t pump{ 0u };
                        int version{ 0u };
                        /** full list of tiles to be present in the back buffer following resolution */
                        std::vector<std::shared_ptr<const TAK::Engine::Renderer::Elevation::TerrainTile>> resolveTiles;
                        /** indices of the tiles in `resolveTiles` that are dirty */
                        struct {
                            std::vector<std::size_t> visible;
                            std::vector<std::size_t> offscreen;
                        } dirtyTiles;

                        double resadj{1.0};
                        std::size_t level0{0u};

                        GLDirtyRegion dirtyRegions;
                        bool stream{false};

                        int64_t updateStart{0LL};
                        std::size_t frames{0u};
                    } updateContext;

                    GLDiagnostics profile;

                    friend class GLGlobe;
                    friend void GLMapViewDebug_drawVisibleSurface(GLGlobe &) NOTHROWS;
                };
            }
        }
    }
}
#endif //TAK_ENGINE_RENDERER_CORE_GLGLOBESURFACERENDERER_H_INCLUDED
