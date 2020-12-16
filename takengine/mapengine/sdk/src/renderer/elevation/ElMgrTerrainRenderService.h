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
                    };
                public :
                    ElMgrTerrainRenderService(TAK::Engine::Core::RenderContext &ctx) NOTHROWS;
                    ElMgrTerrainRenderService() NOTHROWS;
                    ~ElMgrTerrainRenderService() NOTHROWS;
                public : // TerrainRenderService
                    int getTerrainVersion() const NOTHROWS;
                    Util::TAKErr lock(Port::Collection<std::shared_ptr<const TerrainTile>> &value) NOTHROWS;
                    Util::TAKErr lock(Port::Collection<std::shared_ptr<const TerrainTile>> &value, const TAK::Engine::Core::MapSceneModel2 &view, const int srid, const int sceneVersion) NOTHROWS;
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
                    std::list<std::shared_ptr<QuadNode>> queue;
                    std::unique_ptr<WorldTerrain> worldTerrain;

                    Request request;

                    std::unique_ptr<SourceRefresh> sourceRefresh;

                    std::unique_ptr<QuadNode> east;
                    std::unique_ptr<QuadNode> west;

                    QuadNode *roots[8u];

                    Thread::ThreadPoolPtr fetchWorker;
                    Thread::ThreadPtr requestWorker;

                    std::size_t nodeCount;

                    int terrainVersion;
                    int sourceVersion;

                    bool sticky;

                    bool reset = false;
                    std::size_t numPosts;
                    double resadj;

                    Util::BlockPoolAllocator meshAllocator;
                    Util::PoolAllocator<TerrainTile> tileAllocator;

                    bool terminate;

                    struct {
                        bool legacyElevationApi;
                        bool constrainQueryRes;
                        bool fillWithHiRes;
                    } fetchOptions;

                    mutable Thread::Monitor monitor;

                    TAK::Engine::Core::RenderContext &renderer;
                };
            }
        }
    }
}

#endif

