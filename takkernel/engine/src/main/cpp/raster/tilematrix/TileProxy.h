#ifndef TAK_ENGINE_RASTER_TILEMATRIX_TILEPROXY_H_INCLUDED
#define TAK_ENGINE_RASTER_TILEMATRIX_TILEPROXY_H_INCLUDED

#include <vector>

#include "core/Projection2.h"
#include "core/Control.h"
#include "raster/tilematrix/TileClient.h"
#include "raster/tilematrix/TileContainer.h"
#include "renderer/raster/TileCacheControl.h"
#include "thread/Monitor.h"
#include "thread/ThreadPool.h"

namespace TAK {
    namespace Engine {
        namespace Raster {
            namespace TileMatrix {
                class ENGINE_API TileProxy : public TileClient
                {
                private :
                    struct TileFetchTask
                    {
                        int priority{ -1 };
                        std::weak_ptr<TileClient> source;
                        std::weak_ptr<TileContainer> sink;
                        Math::Point2<std::size_t> tileIndex;
                        int64_t expiry{ -1LL };
                        //final TileCacheControl.OnTileUpdateListener[] callback;
                        Feature::Envelope2 bounds;
                        Math::Point2<double> centroid;
                        double radius{ 0.0 };
                    };
                    struct TileUpdateListener
                    {
                        Renderer::Raster::TileCacheControl::OnTileUpdateListener* value{ nullptr };
                        Thread::Mutex mutex;
                    };
                    struct TileFetchWorker
                    {
                        std::shared_ptr<std::vector<TileFetchTask>> downloadQueue;
                        std::shared_ptr<Thread::Monitor> monitor;
                        std::shared_ptr<bool> detached;
                        std::shared_ptr<TileUpdateListener> callback;
                    };
                    class PrioritizerImpl;
                    class ClientControlImpl;
                public :
                    TileProxy(TileClientPtr &&client, TileContainerPtr &&cache) NOTHROWS;
                    TileProxy(const std::shared_ptr<TileClient> &client, const std::shared_ptr<TileContainer> &cache) NOTHROWS;
                private :
                    TileProxy(const std::shared_ptr<TileClient> &client, const std::shared_ptr<TileContainer> &cache, const int64_t expiry) NOTHROWS;
                public :
                    ~TileProxy() NOTHROWS;
                public :
                    Util::TAKErr abortTile(const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS;
                public: // TileClient
                    Util::TAKErr clearAuthFailed() NOTHROWS override;
                    Util::TAKErr checkConnectivity() NOTHROWS override;
                    Util::TAKErr cache(const CacheRequest &request, std::shared_ptr<CacheRequestListener> &listener) NOTHROWS override;
                    Util::TAKErr estimateTilecount(int *count, const CacheRequest &request) NOTHROWS override;
                public : // TileMatrix
                    const char* getName() const NOTHROWS override;
                    int getSRID() const NOTHROWS override;
                    Util::TAKErr getZoomLevel(Port::Collection<ZoomLevel>& value) const NOTHROWS override;
                    double getOriginX() const NOTHROWS override;
                    double getOriginY() const NOTHROWS override;
                    Util::TAKErr getTile(Renderer::BitmapPtr& result, const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS override;
                    Util::TAKErr getTileData(std::unique_ptr<const uint8_t, void (*)(const uint8_t*)>& value, std::size_t* len,
                                                     const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS override;
                    Util::TAKErr getBounds(Feature::Envelope2 *value) const NOTHROWS override;
                public : // controls
                    Util::TAKErr getControl(Core::Control* control, const char* controlName) NOTHROWS;
                    Util::TAKErr getControls(Port::Collection<Core::Control> &controls) NOTHROWS;
                private :
                    Util::TAKErr findQueuedRequestIndexNoSync(std::size_t* value, const std::size_t zoom, const std::size_t x, const std::size_t y) const NOTHROWS;
                    Util::TAKErr downloadTile(const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS;
                private :
                    static void* downloadThread(void *opaque) NOTHROWS;
                private :
                    struct {
                        std::shared_ptr<TileClient> client;
                        std::shared_ptr<TileContainer> cache;
                    } impl;
                    Core::Projection2Ptr proj;
                    int64_t expiry;
                    int priority{ 0 };
                    std::shared_ptr<std::vector<TileFetchTask>> downloadQueue;
                    std::shared_ptr<Thread::Monitor> monitor;
                    Thread::ThreadPoolPtr threadPool;
                    std::shared_ptr<bool> detached;
                    std::shared_ptr<PrioritizerImpl> prioritizer;
                    std::unique_ptr<ClientControlImpl> clientControl;
                    std::shared_ptr<TileUpdateListener> callback;
                };
            }
        }
    }
}

#endif
