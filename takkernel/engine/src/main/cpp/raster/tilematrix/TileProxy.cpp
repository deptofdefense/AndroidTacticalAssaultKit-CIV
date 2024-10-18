#ifdef MSVC
#include "raster/tilematrix/TileProxy.h"

#include <algorithm>

#include "core/ProjectionFactory3.h"
#include "math/Vector4.h"
#include "renderer/raster/TileClientControl.h"
#include "thread/Lock.h"

using namespace TAK::Engine::Raster::TileMatrix;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer::Raster;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
    int64_t defaultExpiry() NOTHROWS
    {
        return Platform_systime_millis() - (24LL * 60LL * 60LL * 1000LL);
    }
}

class TileProxy::PrioritizerImpl : public TileCacheControl
{
public :
    PrioritizerImpl(TileProxy &owner) NOTHROWS;
    ~PrioritizerImpl() NOTHROWS;
public :
    void prioritize(const TAK::Engine::Core::GeoPoint2 &p) NOTHROWS override;
    void abort(const std::size_t level, const std::size_t x, const std::size_t y) NOTHROWS override;
    bool isQueued(const std::size_t level, const std::size_t x, const std::size_t y) NOTHROWS override;
    void setOnTileUpdateListener(OnTileUpdateListener *l) NOTHROWS override;
    void expireTiles(const int64_t expiry) NOTHROWS override;
private :
    TileProxy& owner;
};
class TileProxy::ClientControlImpl : public TileClientControl
{
public :
    ClientControlImpl(TileProxy &owner) NOTHROWS;
public :
    void setOfflineOnlyMode(bool offlineOnly) override;
    bool isOfflineOnlyMode() override;
    void refreshCache() override;
    void setCacheAutoRefreshInterval(int64_t milliseconds) override;
    int64_t getCacheAutoRefreshInterval() override;
private :
    TileProxy& owner;
    bool offlineOnly;
    int64_t refreshInterval;
};

TileProxy::TileProxy(TileClientPtr &&client, TileContainerPtr &&cache) NOTHROWS :
    TileProxy(std::shared_ptr<TileClient>(std::move(client)), std::shared_ptr<TileContainer>(std::move(cache)), defaultExpiry())
{}
TileProxy::TileProxy(const std::shared_ptr<TileClient>& client, const std::shared_ptr<TileContainer>& cache) NOTHROWS :
    TileProxy(client, cache, defaultExpiry())
{}
TileProxy::TileProxy(const std::shared_ptr<TileClient>& client_, const std::shared_ptr<TileContainer>& cache_, const int64_t expiry_) NOTHROWS :
    proj(nullptr, nullptr),
    expiry(expiry_),
    downloadQueue(std::make_shared<std::vector<TileFetchTask>>()),
    monitor(std::make_shared<Monitor>()),
    threadPool(nullptr, nullptr),
    detached(std::make_shared<bool>(false)),
    callback(std::make_shared<TileUpdateListener>())
{
    impl.client = client_;
    impl.cache = cache_;
    ProjectionFactory3_create(proj, impl.client->getSRID());

    prioritizer = std::make_shared<PrioritizerImpl>(*this);
    clientControl = std::make_unique<ClientControlImpl>(*this);
}
TileProxy::~TileProxy() NOTHROWS
{
    Monitor::Lock lock(*monitor);
    // Detach all threads in the pool. This allows for immediate destruct,
    // client and cache will only be kept open if 1) referenced externally or
    // 2) ongoing active download/cache operation
    if (threadPool) {
        threadPool->detachAll();
        threadPool.reset();
    }

    // mark as detached
    *detached = true;

    // clear the queue
    downloadQueue->clear();
    prioritizer.reset();

    // notify
    lock.broadcast();
}

TAKErr TileProxy::abortTile(const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS
{
    {
        Monitor::Lock lock(*monitor);
        TE_CHECKRETURN_CODE(lock.status);

        // XXX - we could also just mark the tile as aborted, however, that
        //       would potentially lead to a situation where we're taking
        //       up a lot of slots in the circular buffer with tiles that
        //       will aren't downloadable
        std::size_t idx;
        if (findQueuedRequestIndexNoSync(&idx, zoom, x, y) == TE_Ok)
            downloadQueue->erase(downloadQueue->begin() + idx);
    }
    return TE_Ok;
}
TAKErr TileProxy::clearAuthFailed() NOTHROWS
{
    return impl.client->clearAuthFailed();
}
TAKErr TileProxy::checkConnectivity() NOTHROWS
{
    return impl.client->checkConnectivity();
}
TAKErr TileProxy::cache(const CacheRequest &request, std::shared_ptr<CacheRequestListener> &listener) NOTHROWS
{
    return impl.client->cache(request, listener);
}
TAKErr TileProxy::estimateTilecount(int *count, const CacheRequest &request) NOTHROWS
{
    return impl.client->estimateTilecount(count, request);
}
const char* TileProxy::getName() const NOTHROWS
{
    return impl.client->getName();
}
int TileProxy::getSRID() const NOTHROWS
{
    return impl.client->getSRID();
}
TAKErr TileProxy::getZoomLevel(Port::Collection<ZoomLevel>& value) const NOTHROWS
{
    return impl.client->getZoomLevel(value);
}
double TileProxy::getOriginX() const NOTHROWS
{
    return impl.client->getOriginX();
}
double TileProxy::getOriginY() const NOTHROWS
{
    return impl.client->getOriginY();
}
TAKErr TileProxy::getTile(Renderer::BitmapPtr& result, const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS
{
    const TAKErr code = impl.cache->getTile(result, zoom, x, y);
    downloadTile(zoom, x, y);
    return code;
}
TAKErr TileProxy::getTileData(std::unique_ptr<const uint8_t, void (*)(const uint8_t*)>& value, std::size_t* len,
        const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS
{
    const TAKErr code = impl.cache->getTileData(value, len, zoom, x, y);
    downloadTile(zoom, x, y);
    return code;
}
TAKErr TileProxy::getBounds(Feature::Envelope2* value) const NOTHROWS
{
    return impl.client->getBounds(value);
}
TAKErr TileProxy::getControl(Control* control, const char* controlName) NOTHROWS
{
    if (!control)
        return TE_InvalidArg;

    if(String_strcmp(TileCacheControl_getType(), controlName) == 0) {
        control->value = static_cast<TileCacheControl*>(prioritizer.get());
        control->type = TileCacheControl_getType();
        return TE_Ok;
    } else if(String_strcmp(TileClientControl_getType(), controlName) == 0) {
        control->value = static_cast<TileClientControl*>(clientControl.get());
        control->type = TileClientControl_getType();
        return TE_Ok;
    }
    return TE_Done;
}
TAKErr TileProxy::getControls(Collection<Control>& controls) NOTHROWS
{
    TAKErr code(TE_Ok);
    code = controls.add(Control{TileCacheControl_getType(), static_cast<TileCacheControl*>(prioritizer.get())});
    code = controls.add(Control{TileClientControl_getType(), static_cast<TileClientControl*>(clientControl.get())});
    TE_CHECKRETURN_CODE(code);

    return code;
}
TAKErr TileProxy::findQueuedRequestIndexNoSync(std::size_t* value, const std::size_t zoom, const std::size_t x, const std::size_t y) const NOTHROWS
{
    if (!downloadQueue->empty()) {
        for (std::size_t i = downloadQueue->size(); i > 0; i--) {
            const std::size_t idx = i - 1u;
            const auto &request = (*downloadQueue)[idx];
            if (request.tileIndex.x == x && request.tileIndex.y == y && request.tileIndex.z == zoom) {
                *value = idx;
                return TE_Ok;
            }
        }
    }
    return TE_Done;
}
TAKErr TileProxy::downloadTile(const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS
{
    // nothing to do if offline mode
    if (clientControl->isOfflineOnlyMode())
        return TE_Done;

    {
        Monitor::Lock lock(*monitor);
        TE_CHECKRETURN_CODE(lock.status);
        std::size_t idx;
        if(findQueuedRequestIndexNoSync(&idx, zoom, x, y) == TE_Done) {
            if(!threadPool) {
                const std::size_t numThreads = 3u;
                std::vector<void *> workerData;
                workerData.reserve(numThreads);
                for(std::size_t i = 0u; i < numThreads; i++) {
                    std::unique_ptr<TileFetchWorker> worker(new TileFetchWorker());
                    worker->downloadQueue = downloadQueue;
                    worker->monitor = monitor;
                    worker->detached = detached;
                    worker->callback = callback;
                    workerData.push_back(worker.release());
                }
                if (ThreadPool_create(threadPool, numThreads, downloadThread, &workerData.at(0)) != TE_Ok)
                    return TE_Err;
            }
            TileFetchTask task;
            task.priority = ++priority;
            task.source = impl.client;
            task.sink = impl.cache;
            task.tileIndex = TAK::Engine::Math::Point2<std::size_t>(x, y, zoom);
            task.expiry = expiry;
            TileMatrix_getTileBounds(&task.bounds, *impl.client, (int)zoom, (int)x, (int)y);
            task.centroid.x = (task.bounds.minX+task.bounds.maxX)/2.0;
            task.centroid.y = (task.bounds.minY+task.bounds.maxY)/2.0;
            task.centroid.z = 0.0;
            task.radius = Vector2_length(Point2<double>(task.centroid.x-task.bounds.maxX, task.centroid.y-task.bounds.maxY, 0.0));

            downloadQueue->push_back(task);
            lock.signal();
        }
    }
    return TE_Ok;
}
void *TileProxy::downloadThread(void *opaque) NOTHROWS
{
    std::unique_ptr<TileFetchWorker> worker(static_cast<TileFetchWorker*>(opaque));
    while(true) {
        TileFetchTask task;
        {
            Monitor::Lock lock(*worker->monitor);
            if (*worker->detached)
                break;
            if(worker->downloadQueue->empty()) {
                lock.wait();
                continue;
            }

            task = worker->downloadQueue->back();
            worker->downloadQueue->pop_back();
        }

        // execute task
        int64_t expiration(-1LL);
        {
            const std::shared_ptr<TileContainer> cache = task.sink.lock();
            expiration = cache->getTileExpiration(task.tileIndex.z, task.tileIndex.x, task.tileIndex.y);
        }

        if (expiration >= task.expiry)
            continue;

        // the tile is considered expired, download
        const std::shared_ptr<TileClient> client = task.source.lock();
        if (!client)
            continue;

        std::unique_ptr<const uint8_t, void(*)(const uint8_t*)> data(nullptr, nullptr);
        std::size_t len;
        if (client->getTileData(data, &len, task.tileIndex.z, task.tileIndex.x, task.tileIndex.y) != TE_Ok)
            continue;

        {
            const std::shared_ptr<TileContainer> cache = task.sink.lock();
            if (!cache || cache->setTile(task.tileIndex.z, task.tileIndex.x, task.tileIndex.y, data.get(), len, Platform_systime_millis()) != TE_Ok)
                continue;
            // signal update
            {
                Lock lock(worker->callback->mutex);
                if (worker->callback->value)
                    worker->callback->value->onTileUpdated(task.tileIndex.z, task.tileIndex.x, task.tileIndex.y);
            }
        }
    }

    return nullptr;
}

TileProxy::PrioritizerImpl::PrioritizerImpl(TileProxy &owner_) NOTHROWS :
    owner(owner_)
{
}
TileProxy::PrioritizerImpl::~PrioritizerImpl() NOTHROWS
{
    Lock lock(owner.callback->mutex);
    owner.callback->value = nullptr;
}

void TileProxy::PrioritizerImpl::prioritize(const TAK::Engine::Core::GeoPoint2 &lla) NOTHROWS
{
    Point2<double> xyz;
    if (owner.proj->forward(&xyz, lla) != TE_Ok)
        return;

    Monitor::Lock lock(*owner.monitor);
    std::sort(owner.downloadQueue->begin(), owner.downloadQueue->end(), [xyz](const TileFetchTask& a, const TileFetchTask& b)
    {
        do {
            // XXX - use distance-squared for efficiency
            const double da = Vector2_length(Vector2_subtract(a.centroid, xyz));
            const double db = Vector2_length(Vector2_subtract(b.centroid, xyz));
            if (da <= a.radius && db <= b.radius)
                break; // priority focus is contained within both bounding circles, default priority
            else if (da <= a.radius)
                return false; // contained in A bounding circle
            else if (db <= b.radius)
                return true; // contained in B bounding circle
            // both radii are greater
            else if ((da - a.radius) < (db - b.radius))
                return false; // closer to A
            else if ((da - a.radius) > (db - b.radius))
                return true; // closer to B
        } while (false);

        // default prioritization, based first on lo->hi res, then insert order
        if (a.tileIndex.z < b.tileIndex.z)
            return false;
        else if (a.tileIndex.z > b.tileIndex.z)
            return true;
        else
            return (a.priority < b.priority);
    });
}
void TileProxy::PrioritizerImpl::abort(const std::size_t level, const std::size_t x, const std::size_t y) NOTHROWS
{
    owner.abortTile(level, x, y);
}
bool TileProxy::PrioritizerImpl::isQueued(const std::size_t level, const std::size_t x, const std::size_t y) NOTHROWS
{
    Monitor::Lock lock(*owner.monitor);
    std::size_t idx;
    return owner.findQueuedRequestIndexNoSync(&idx, level, x, y) != TE_Done;
}
void TileProxy::PrioritizerImpl::setOnTileUpdateListener(OnTileUpdateListener *l) NOTHROWS
{
    Lock lock(owner.callback->mutex);
    owner.callback->value = l;
}
void TileProxy::PrioritizerImpl::expireTiles(const int64_t expiry) NOTHROWS
{
    owner.expiry = expiry;
}

TileProxy::ClientControlImpl::ClientControlImpl(TileProxy &owner_) NOTHROWS :
    owner(owner_),
    offlineOnly(false),
    refreshInterval(0LL)
{}
void TileProxy::ClientControlImpl::setOfflineOnlyMode(bool value)
{
    offlineOnly = value;
}
bool TileProxy::ClientControlImpl::isOfflineOnlyMode()
{
    return offlineOnly;
}
void TileProxy::ClientControlImpl::refreshCache()
{
    owner.prioritizer->expireTiles(Platform_systime_millis()-1LL);
}
void TileProxy::ClientControlImpl::setCacheAutoRefreshInterval(int64_t milliseconds)
{
    refreshInterval = milliseconds;
}
int64_t TileProxy::ClientControlImpl::getCacheAutoRefreshInterval()
{
    return refreshInterval;
}
#endif