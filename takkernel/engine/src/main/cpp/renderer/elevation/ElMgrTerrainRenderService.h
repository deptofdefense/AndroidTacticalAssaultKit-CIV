#ifndef TAK_ENGINE_RENDERER_ELEVATION_ELMGRTERRAINRENDERSERVICE_H_INCLUDED
#define TAK_ENGINE_RENDERER_ELEVATION_ELMGRTERRAINRENDERSERVICE_H_INCLUDED

#include "core/MapSceneModel2.h"
#include "core/RenderContext.h"
#include "feature/Envelope2.h"
#include "renderer/elevation/TerrainRenderService.h"
#include "thread/Monitor.h"
#include "thread/Thread.h"
#include "thread/ThreadPool.h"
#include "util/BlockPoolAllocator.h"
#include "util/Error.h"
#include "util/MemBuffer2.h"
#include "util/PoolAllocator.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Elevation {
                class ENGINE_API ElMgrTerrainRenderService : public TerrainRenderService
                {
                private :
                    class QuadNode;
                    class SourceRefresh;
                    struct WorldTerrain
                    {
                        int srid {-1};
                        int sourceVersion {-1};
                        int sceneVersion {-1};
                        int terrainVersion {-1};
                        std::vector<std::shared_ptr<const TerrainTile>> tiles;

                        friend class ElMgrTerrainRenderService::QuadNode;
                    };
                    struct Request
                    {
                        TAK::Engine::Core::MapSceneModel2 scene;
                        int srid {-1};
                        int sceneVersion {-1};
                        bool derive {false};
                    };
                public :
                    ElMgrTerrainRenderService(TAK::Engine::Core::RenderContext &ctx) NOTHROWS;
                    ElMgrTerrainRenderService() NOTHROWS;
                    ~ElMgrTerrainRenderService() NOTHROWS;
                public : // TerrainRenderService
                    int getTerrainVersion() const NOTHROWS;
                    /**
                     * Returns the mesh that was returned from the most recent
                     * call to
                     * `lock(Collection<shared_ptr<TerrainTile>>, MapSceneModel2, ...)`
                     *
                     * @param value
                     * @return
                     */
                    Util::TAKErr lock(Port::Collection<std::shared_ptr<const TerrainTile>> &value) NOTHROWS;
                    /**
                     *
                     * @param value
                     * @param view
                     * @param srid
                     * @param sceneVersion
                     * @param allowDerive   If `true` the service may derive
                     *                      high resolution tiles from lower
                     *                      resolution. This may result in a
                     *                      higher resolution mesh being
                     *                      returned if terrain data is not
                     *                      yet loaded for the entire extent of
                     *                      the scene.
                     * @return
                     */
                    Util::TAKErr lock(Port::Collection<std::shared_ptr<const TerrainTile>> &value, const TAK::Engine::Core::MapSceneModel2 &view, const int srid, const int sceneVersion, const bool allowDerive = false) NOTHROWS;
                    Util::TAKErr unlock(Port::Collection<std::shared_ptr<const TerrainTile>> &tiles) NOTHROWS;
                    Util::TAKErr getElevation(double *value, const double latitude, const double longitude) const NOTHROWS;
                    Util::TAKErr start() NOTHROWS;
                    Util::TAKErr stop() NOTHROWS;
                private :
                    Util::TAKErr enqueue(const std::shared_ptr<QuadNode> &node) NOTHROWS;
                private :
                    static void *fetchWorkerThread(void *);
                    static void *requestWorkerThread(void *);
                private :
                    struct {
                        Thread::ThreadPoolPtr worker {Thread::ThreadPoolPtr(nullptr, nullptr)};
                        std::vector<std::shared_ptr<QuadNode>> entries;
                        bool sorted {false};
                        mutable Thread::Monitor monitor {Thread::TEMT_Recursive};
                    } queue;
                    std::unique_ptr<WorldTerrain> worldTerrain;

                    Request request;

                    std::unique_ptr<SourceRefresh> sourceRefresh;

                    std::shared_ptr<QuadNode> east;
                    std::shared_ptr<QuadNode> west;

                    std::shared_ptr<QuadNode> roots[8u];

                    Thread::ThreadPtr requestWorker;

                    std::size_t nodeCount;

                    struct {
                        /** version of quadtree, bumped when a tile is fetched */
                        int quadtree{ 1 };
                        /** version of source data, bumped on source added/removed/changed */
                        int source{ 0 };
                        /** version of the world front buffer, bumped on flip */
                        int world{ 0 };
                    } version;

                    bool sticky;

                    bool reset = false;
                    std::size_t numPosts;
                    /**
                     * scalar adjustment applied to nominal node resolution for
                     * GSD constraints passed to ElevationManager during node
                     * mesh population
                     */
                    double fetchResolutionAdjustment;
                    /**
                     * Scalar adjustment applied to node level selection based
                     * on current camera.
                     */
                    double nodeSelectResolutionAdjustment;

                    Util::BlockPoolAllocator meshAllocator;
                    Util::PoolAllocator<TerrainTile> tileAllocator;

                    bool terminate;

                    struct {
                        bool legacyElevationApi;
                        bool constrainQueryRes;
                        bool fillWithHiRes;
                    } fetchOptions;

                    Util::MemBuffer2 edgeIndices;

                    mutable Thread::Monitor monitor;

                    TAK::Engine::Core::RenderContext &renderer;
                };
            }
        }
    }
}

#endif

