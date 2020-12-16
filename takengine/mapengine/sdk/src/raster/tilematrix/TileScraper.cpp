#include "raster/tilematrix/TileScraper.h"
#include "util/Memory.h"
#include "port/STLVectorAdapter.h"
#include "feature/GeometryTransformer.h"
#include "feature/Polygon2.h"
#include "math/Vector4.h"
#include "thread/Lock.h"
#include "thread/Thread.h"

using namespace TAK::Engine;
using namespace TAK::Engine::Raster::TileMatrix;

namespace {
    const char *TAG = "TileScraper";
    const int MAX_TILES = 300000;
    const int DOWNLOAD_ATTEMPTS = 2;
}

Util::TAKErr TAK::Engine::Raster::TileMatrix::TileScraper_create(TileScraperPtr &value, std::shared_ptr<TileMatrix> client, 
    std::shared_ptr<TileContainer> sink, const CacheRequest &request, std::shared_ptr<CacheRequestListener> callback)
{
    bool ro;
    Util::TAKErr code = sink->isReadOnly(&ro);
    if (code != Util::TE_Ok || ro)
        return Util::TE_InvalidArg;
    
    std::unique_ptr<CacheRequest> req(new CacheRequest(request));
    std::unique_ptr<TileScraper::ScrapeContext> ctx;
    code = TileScraper::ScrapeContext::create(ctx, client.get(), sink.get(), req.get());
    TE_CHECKRETURN_CODE(code);
    TileScraperPtr ret(new TileScraper(std::move(ctx), client, sink, std::move(req), callback), Util::Memory_deleter_const<TileScraper>);
    value = std::move(ret);
    return Util::TE_Ok;
}

TileScraper::TileScraper(std::unique_ptr<ScrapeContext> ctx, std::shared_ptr<TileMatrix> client,
    std::shared_ptr<TileContainer> sink, std::unique_ptr<CacheRequest> request, std::shared_ptr<CacheRequestListener> callback) :
    client(client),
    sink(sink),
    request(std::move(request)),
    callback(callback),
    scrapeContext(std::move(ctx)),
    downloader()
{

}

TileScraper::~TileScraper()
{
    downloader.reset();
}

void TileScraper::run() {
    if(request->maxThreads > 1)
        downloader = std::unique_ptr<Downloader>(new TileScraper::MultiThreadDownloader(callback, request->maxThreads));
    else
        downloader = std::unique_ptr<Downloader>(new TileScraper::LegacyDownloader(callback));

    downloader->download(scrapeContext);
}

Util::TAKErr TAK::Engine::Raster::TileMatrix::TileScraper_estimateTileCount(int &value, TileClient *client, CacheRequest *request)
{
    std::unique_ptr<TileScraper::ScrapeContext> v;
    TileScraper::ScrapeContext::create(v, client, nullptr, request);
    value = v->totalTiles;
    return Util::TE_Ok;
}

/**************************************************************************/

TileScraper::TilePoint::TilePoint(int row, int column) : r(row), c(column)
{
}


/**************************************************************************/

TileScraper::DownloadTask::DownloadTask(std::shared_ptr<ScrapeContext> context, size_t tileZ, size_t tileX, size_t tileY) : context(context), tileX(tileX), tileY(tileY), tileZ(tileZ)
{
}


Util::TAKErr TileScraper::DownloadTask::run()
{
    bool success = false;
    Util::TAKErr err = Util::TE_Ok;

    // attempt to download the tile
    int attempts = 0;
    while (attempts < DOWNLOAD_ATTEMPTS) {
        // clear the error for the attempt
        err = Util::TE_Ok;
        // load the tile
        std::unique_ptr<const uint8_t, void(*)(const uint8_t *)> d(NULL, NULL);
        size_t len;
        err = this->context->client->getTileData(d, &len, this->tileZ, this->tileX, this->tileY);

        if (err == Util::TE_Ok && d.get() != nullptr) {
            // valid entry in cache
            this->context->sink->setTile(this->tileZ, this->tileX, this->tileY, d.get(), len, TAK::Engine::Port::Platform_systime_millis() + context->request->expirationOffset);
            success = true;
            break;
        } else if (err == Util::TE_Ok) {
            // there was no exception raised during which means that
            // the client is unable to download
            break;
        } else {
            attempts++;
        }
    }

    // set the error if necessary
    this->context->downloadError |= (err != Util::TE_Ok);
    this->context->downloadComplete(success);
    return err;
}

/**************************************************************************/

Util::TAKErr TileScraper::ScrapeContext::create(std::unique_ptr<TileScraper::ScrapeContext> &v, TileMatrix *client, TileContainer *container, CacheRequest *request)
{
    std::unique_ptr<TileScraper::ScrapeContext> ret(new ScrapeContext(client, container, request));
    Util::TAKErr code = Util::TE_Ok;

    Port::STLVectorAdapter<TileMatrix::ZoomLevel> pcZoom(ret->zooms);
    code = client->getZoomLevel(pcZoom);
    TE_CHECKRETURN_CODE(code);

    std::map<int, bool> lvlArray;
    for (std::size_t i = 0u; i < ret->zooms.size(); i++) {
        if (ret->zooms[i].resolution <= request->minResolution
                    && ret->zooms[i].resolution >= request->maxResolution)
            lvlArray[ret->zooms[i].level] = true;
    }
    if (lvlArray.size() == 0)
        lvlArray[ret->zooms[ret->zooms.size() - 1].level] = true;

    // NOTE: order of 'keyAt' is guaranteed to be ascending
    for (auto iter = lvlArray.begin(); iter != lvlArray.end(); ++iter)
        ret->levels.push_back(iter->first);

    Feature::Geometry2Ptr_const geo(NULL, NULL);
    code = Feature::GeometryTransformer_transform(geo, *(request->region), 4326, client->getSRID());
    TE_CHECKRETURN_CODE(code);
    Feature::Envelope2 env;
    code = geo->getEnvelope(&env);
    TE_CHECKRETURN_CODE(code);

    if (ret->levels.size() > 0) { 
        ret->minLevel = ret->levels[0];
        ret->maxLevel = ret->levels[ret->levels.size() - 1];
    }

    // Convert geometry to Vector2D for use with intersection math
    std::shared_ptr<Feature::LineString2> ls_shared(nullptr);
    Feature::LineString2 *ls = nullptr;
    if (geo->getClass() == Feature::GeometryClass::TEGC_LineString)
        ls = (Feature::LineString2 *) geo.get();
    else if (geo->getClass() == Feature::GeometryClass::TEGC_Polygon) {
        code = ((Feature::Polygon2 *) geo.get())->getExteriorRing(ls_shared);
        TE_CHECKRETURN_CODE(code);
        ls = ls_shared.get();
    }

    if (ls != nullptr) {
        for (size_t i = 0; i < ls->getNumPoints(); ++i) {
            double x, y;
            code = ls->getX(&x, i);
            TE_CHECKRETURN_CODE(code);
            code = ls->getY(&y, i);
            TE_CHECKRETURN_CODE(code);
            ret->points.push_back(Math::Point2<double>(x, y));
        }
    }
    ret->closed = ret->points[0].x == ret->points[ret->points.size() - 1].x
        && ret->points[0].y == ret->points[ret->points.size() - 1].y;

    // Scan for intersecting tiles
    Math::Point2<double> minTile;
    Math::Point2<double> maxTile;
    code = TileMatrix_getTileIndex(&minTile, *client, 0, env.minX, env.maxY);
    TE_CHECKRETURN_CODE(code);
    code = TileMatrix_getTileIndex(&maxTile, *client, 0, env.maxX, env.minY);
    TE_CHECKRETURN_CODE(code);

    for (int r = (int)minTile.y; r <= (int)maxTile.y; r++)
        for (int c = (int)minTile.x; c <= (int)maxTile.x; c++)
            ret->getTiles(c, r, 0, ret->maxLevel);

    v = std::move(ret);
    return Util::TE_Ok;
}


TileScraper::ScrapeContext::ScrapeContext(TileMatrix *client, TileContainer *container, CacheRequest *request) : 
    client(client), sink(container), request(request), uri(client->getName()),
    levels(), currentLevelIdx(0), totalTilesCurrentLevel(0), totalTiles(0),
    downloadError(false), tilesDownloaded(0),
    tiles(), minLevel(0), maxLevel(0), zooms(),
    points(), tp {
        Math::Point2<double>(0, 0),
        Math::Point2<double>(0, 0),
        Math::Point2<double>(0, 0),
        Math::Point2<double>(0, 0),
    },
    tmpSeg {
        Math::Point2<double>(0, 0),
        Math::Point2<double>(0, 0),
        Math::Point2<double>(0, 0),
    },
    closed(false)
{
}

void TileScraper::ScrapeContext::getTiles(int col, int row, int level, int max)
{
    if (level > max || this->totalTiles >= MAX_TILES)
        return;

    TileMatrix::ZoomLevel &zoom = this->zooms[level];

    // Get tile points for checking intersection
    getSourcePoint(this->tp + 0, zoom, col, row);
    getSourcePoint(this->tp + 1, zoom, col + 1, row);
    getSourcePoint(this->tp + 2, zoom, col + 1, row + 1);
    getSourcePoint(this->tp + 3, zoom, col, row + 1);

    // Intersects or contains
    bool add = Math::Vector2_polygonContainsPoint(this->points[0], this->tp, 4);
    if (!add) {
        bool breakOuter = false;
        for (int i = 0; i < 4 && !breakOuter; i++) {
            const Math::Point2<double> &s = this->tp[i];
            const Math::Point2<double> &e = this->tp[i == 3 ? 0 : i + 1];
            for (std::size_t j = 0u; j < this->points.size() - 1; j++) {
                if (segmentIntersects(s, e, this->points[j], this->points[j + 1])) {
                    add = true;

                    breakOuter = true;
                    break;
                }
            }
        }
        if (!add && closed)
            add = Math::Vector2_polygonContainsPoint(this->tp[0], &this->points[0], this->points.size());
    }

    if (add) {
        // Add this tile
        if (level >= this->minLevel && level <= this->maxLevel) {
            if (!this->request->countOnly) {
                auto iter = this->tiles.find(level);
                if (iter == this->tiles.end()) {
                    this->tiles[level] = std::list<TileScraper::TilePoint>();
                    iter = this->tiles.insert(std::pair<int, std::list<TileScraper::TilePoint>>(level, std::list<TileScraper::TilePoint>())).first;
                }
                iter->second.push_back(TilePoint(row, col));
            }
            this->totalTiles++;
        }


        // Check sub-tiles
        row *= 2;
        col *= 2;
        for (int r = row; r <= row + 1; r++)
            for (int c = col; c <= col + 1; c++)
                getTiles(c, r, level + 1, max);
    }
}

bool TileScraper::ScrapeContext::segmentIntersects(Math::Point2<double> seg10,
    Math::Point2<double> seg11,
    Math::Point2<double> seg01,
    Math::Point2<double> seg00)
{
    tmpSeg[0].x = seg01.x - seg00.x;
    tmpSeg[0].y = seg01.y - seg00.y;
    tmpSeg[1].x = seg11.x - seg10.x;
    tmpSeg[1].y = seg11.y - seg10.y;
    Math::Point2<double> c;
    Math::Vector2_cross(&c, tmpSeg[1], tmpSeg[0]);
    double c1 = c.z;
    if (c1 != 0.0) {
        tmpSeg[2].x = seg00.x - seg10.x;
        tmpSeg[2].y = seg00.y - seg10.y;
        Math::Vector2_cross(&c, tmpSeg[2], tmpSeg[0]);
        double t = c.z / c1;
        Math::Vector2_cross(&c, tmpSeg[2], tmpSeg[1]);
        double u = c.z / c1;
        return t >= 0 && t <= 1 && u >= 0 && u <= 1;
    }
    return false;
}

void TileScraper::ScrapeContext::getSourcePoint(Math::Point2<double> *v, const TileMatrix::ZoomLevel &z,
    int c, int r)
{
    v->x = client->getOriginX() + (c * z.pixelSizeX * z.tileWidth);
    v->y = client->getOriginY() - (r * z.pixelSizeY * z.tileHeight);
}

void TileScraper::ScrapeContext::downloadComplete(bool success)
{
    Thread::Lock lock(this->mutex);
    if (success) {
        this->tilesDownloaded++;
    } else {
        this->downloadError = true;
    }
}

bool TileScraper::ScrapeContext::hadDownloadError() {
    Thread::Lock lock(this->mutex);
    return this->downloadError;
}

int TileScraper::ScrapeContext::getNumTilesDownloaded() {
    Thread::Lock lock(this->mutex);
    return this->tilesDownloaded;
}


/**************************************************************************/

TileScraper::Downloader::Downloader(std::shared_ptr<CacheRequestListener> callback) : callback(callback), levelStartTiles(0)
{
}

TileScraper::Downloader::~Downloader()
{
}

void TileScraper::Downloader::reportStatus(std::shared_ptr<ScrapeContext> downloadContext)
{
    const int numDownload = downloadContext->getNumTilesDownloaded();

    if (callback.get() != nullptr) {
        callback->onRequestProgress(downloadContext->currentLevelIdx,
            (int)downloadContext->levels.size(),
            numDownload - this->levelStartTiles,
            downloadContext->totalTilesCurrentLevel,
            numDownload,
            downloadContext->totalTiles);
    }            
}

void TileScraper::Downloader::onDownloadEnter(std::shared_ptr<ScrapeContext> context)
{
}

void TileScraper::Downloader::onDownloadExit(std::shared_ptr<ScrapeContext> context, int jobStatus)
{
}

bool TileScraper::Downloader::checkReadyForDownload(std::shared_ptr<ScrapeContext> context)
{
    return true;
}

void TileScraper::Downloader::onLevelDownloadComplete(std::shared_ptr<ScrapeContext> context)
{
}

void TileScraper::Downloader::onLevelDownloadStart(std::shared_ptr<ScrapeContext> context)
{
}

bool TileScraper::Downloader::download(std::shared_ptr<ScrapeContext> downloadContext)
{
    Util::Logger_log(Util::LogLevel::TELL_Debug, "%s Starting download of %s cache...", TAG, downloadContext->client->getName());

    if (callback.get() != nullptr)
        callback->onRequestStarted();

    reportStatus(downloadContext);

    this->onDownloadEnter(downloadContext);

    Util::TAKErr code = Util::TE_Ok;
    for (std::size_t l = 0u; code == Util::TE_Ok && l < downloadContext->levels.size(); l++) {
        downloadContext->currentLevelIdx = static_cast<int>(l);
        int currentLevel = downloadContext->levels[l];

        TileMatrix::ZoomLevel zoom;
        Util::TAKErr zoomCode = TileMatrix_findZoomLevel(&zoom, *(downloadContext->client), currentLevel);
        int tile180X = (zoomCode == Util::TE_Ok) ? (int) ((downloadContext->client->getOriginX() * -2)
            / (zoom.pixelSizeX * zoom.tileWidth)) : -1;

        auto tilesListIter = downloadContext->tiles.find(downloadContext->levels[l]);
        auto tiles = tilesListIter->second;
        downloadContext->totalTilesCurrentLevel = (int)tiles.size();
        this->levelStartTiles = downloadContext->getNumTilesDownloaded();

        this->onLevelDownloadStart(downloadContext);

        for (auto tilesIter = tiles.begin(); tilesIter != tiles.end(); ++tilesIter) {
            while (true) {
                // check for error
                if (downloadContext->hadDownloadError()) {
                    if (callback.get() != nullptr)
                        callback->onRequestError(nullptr, true);

                    Util::Logger_log(Util::LogLevel::TELL_Debug, "%s Lost network connection during map download.", TAG);

                    return false;
                } else
                    // check for cancel
                    if (downloadContext->request->canceled) {
                    if (callback.get() != nullptr)
                            callback->onRequestCanceled();
                        return false;
                    }

                // report status
                this->reportStatus(downloadContext);

                // check if we should sleep for a little bit
                // before proceeding to initiate download of
                // the next tile
                if (!this->checkReadyForDownload(downloadContext)) {
                    Thread::Thread_sleep(50);
                    continue;
                }

                // proceed to download
                break;
            }

            // download
            if (tile180X > -1 && tilesIter->c >= tile180X)
                code = downloadTileImpl(downloadContext, currentLevel, tilesIter->c - tile180X, tilesIter->r);
            else
                code = downloadTileImpl(downloadContext, currentLevel, tilesIter->c, tilesIter->r);

            this->onLevelDownloadComplete(downloadContext);
        }
    }

    bool retval;
    if (code == Util::TE_Ok) {
        if (callback.get() != nullptr)
            callback->onRequestComplete();
        retval = true;
    } else {
        Util::Logger_log(Util::LogLevel::TELL_Error, "%s Error while trying to download from %s", TAG, downloadContext->uri);
        retval = false;
    }
    this->onDownloadExit(downloadContext, 0);
    return retval;
}


TileScraper::MultiThreadDownloader::MultiThreadDownloader(std::shared_ptr<CacheRequestListener> callback, int numDownloadThreads) : 
    Downloader(callback), queue(), shutdown(false), terminate(false), queueMonitor(), poolSize(numDownloadThreads), pool(NULL, NULL)
{
    Thread::ThreadPool_create(pool, poolSize, TileScraper::MultiThreadDownloader::threadEntry, this);
}

TileScraper::MultiThreadDownloader::~MultiThreadDownloader()
{
    {
        Thread::Monitor::Lock mLock(queueMonitor);
        terminate = true;
        mLock.broadcast();
    }
    pool->joinAll();
}

void TileScraper::MultiThreadDownloader::flush(std::shared_ptr<ScrapeContext> context, bool reportStatus)
{
    // wait for queue to empty 
    while (this->queue.size() > 0) {
        // check for cancel
        if (context->request->canceled)
            break;

        Thread::Thread_sleep(50);

        // report status
        if (reportStatus)
            this->reportStatus(context);
    }
}

void TileScraper::MultiThreadDownloader::onDownloadExit(std::shared_ptr<ScrapeContext> context, int jobStatus)
{
    this->flush(context, false);
    {
        Thread::Monitor::Lock mLock(queueMonitor);
        shutdown = true;
        mLock.broadcast();
    }
}

bool TileScraper::MultiThreadDownloader::checkReadyForDownload(std::shared_ptr<ScrapeContext> context)
{
    Thread::Monitor::Lock mLock(queueMonitor);
    return (this->queue.size() < (3u * static_cast<std::size_t>(poolSize)));
}

void TileScraper::MultiThreadDownloader::onLevelDownloadComplete(std::shared_ptr<ScrapeContext> context) {
    this->flush(context, true);
}

Util::TAKErr TileScraper::MultiThreadDownloader::downloadTileImpl(std::shared_ptr<ScrapeContext> context, int tileLevel,
                int tileX, int tileY)
{
    Thread::Monitor::Lock mLock(queueMonitor);
    queue.push_back(std::unique_ptr<DownloadTask>(new DownloadTask(context, tileLevel, tileX, tileY)));
    mLock.broadcast();
    return Util::TE_Ok;
}

void *TileScraper::MultiThreadDownloader::threadEntry(void *selfPtr)
{
    TileScraper::MultiThreadDownloader *self = (TileScraper::MultiThreadDownloader *)selfPtr;
    self->threadRun();
    return nullptr;
}

void TileScraper::MultiThreadDownloader::threadRun()
{
    while (true) {
        std::unique_ptr<DownloadTask> task;
        {
            Thread::Monitor::Lock mLock(queueMonitor);
            if (terminate)
                break;
            if (queue.empty()) {
                if (shutdown)
                    break;
                mLock.wait();
                continue;
            }
            task = std::move(queue.front());
            queue.pop_front();
        }
        task->run();
        task.reset();
    }
}



TileScraper::LegacyDownloader::LegacyDownloader(std::shared_ptr<CacheRequestListener> callback) : Downloader(callback)
{
}

TileScraper::LegacyDownloader::~LegacyDownloader()
{
}

Util::TAKErr TileScraper::LegacyDownloader::downloadTileImpl(std::shared_ptr<ScrapeContext> context, int tileLevel,
                int tileX, int tileY)
{
    DownloadTask t(context, tileLevel, tileX, tileY);
    return t.run();
}
