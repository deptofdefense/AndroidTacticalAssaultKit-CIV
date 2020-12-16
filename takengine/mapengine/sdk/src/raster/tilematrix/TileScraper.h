#ifndef TAK_ENGINE_RASTER_TILESCRAPER_H_INCLUDED
#define TAK_ENGINE_RASTER_TILESCRAPER_H_INCLUDED

#include "raster/tilematrix/TileMatrix.h"
#include "raster/tilematrix/TileContainer.h"
#include "raster/tilematrix/TileClient.h"
#include "port/String.h"
#include "port/Platform.h"
#include "feature/Geometry2.h"
#include "thread/Mutex.h"
#include "thread/Monitor.h"
#include "thread/ThreadPool.h"
#include <vector>
#include <string>
#include <map>
#include <list>

namespace TAK {
    namespace Engine {
        namespace Raster {
            namespace TileMatrix {

                class ENGINE_API TileScraper {

                private:
                    class Downloader;
                    class DownloadTask;
                    class ScrapeContext;

                    std::shared_ptr<TileMatrix> client;
                    std::shared_ptr<TileContainer> sink;
                    std::unique_ptr<CacheRequest> request;
                    std::shared_ptr<CacheRequestListener> callback;

                    std::shared_ptr<ScrapeContext> scrapeContext;
                    std::unique_ptr<Downloader> downloader;


                    TileScraper(std::unique_ptr<ScrapeContext> ctx, std::shared_ptr<TileMatrix> client,
                        std::shared_ptr<TileContainer> sink, std::unique_ptr<CacheRequest> request, std::shared_ptr<CacheRequestListener> callback);
                
                    friend ENGINE_API Util::TAKErr TileScraper_create(std::unique_ptr<TileScraper, void(*)(const TileScraper *)> &value,
                        std::shared_ptr<TileMatrix> client, std::shared_ptr<TileContainer> sink, const CacheRequest &request, std::shared_ptr<CacheRequestListener> callback);
                    friend ENGINE_API Util::TAKErr TileScraper_estimateTileCount(int &value, TileClient *client, CacheRequest *request);

                public:
                    ~TileScraper();

                    void run();

                    /**************************************************************************/

                private:
                    struct TilePoint {
                        int r, c;

                        TilePoint(int row, int column);
                    };

                    class ScrapeContext {
                    public:
                        TileMatrix * const client;
                        TileContainer * const sink;
                        const CacheRequest *request;
                        std::string uri;
                        std::vector<int> levels;
                        int currentLevelIdx;
                        int totalTilesCurrentLevel;
                        int totalTiles;
                    private:
                        bool downloadError;
                        int tilesDownloaded;
                        std::map<int, std::list<TilePoint>> tiles;
                        int minLevel;
                        int maxLevel;

                        // Temp vars across methods
                        std::vector<TileMatrix::ZoomLevel> zooms;
                        std::vector<Math::Point2<double>> points;
                        Math::Point2<double> tp[4];
                        Math::Point2<double> tmpSeg[3];
                        bool closed;
                        Thread::Mutex mutex;
                        ScrapeContext(TileMatrix *client, TileContainer *container, CacheRequest *request);

                    public:
                        static Util::TAKErr create(std::unique_ptr<ScrapeContext> &v, TileMatrix *client, TileContainer *container, CacheRequest *request);

                        void downloadComplete(bool success);
                        bool hadDownloadError();
                        int getNumTilesDownloaded();

                    private:
                        /**
                        * Get all tiles in a quad tree
                        * @param col Root tile column
                        * @param row Root tile row
                        * @param level Root tile level
                        * @param max Maximum level
                        */
                        void getTiles(int col, int row, int level, int max);

                        bool segmentIntersects(Math::Point2<double> seg10,
                            Math::Point2<double> seg11,
                            Math::Point2<double> seg01,
                            Math::Point2<double> seg00);

                        void getSourcePoint(Math::Point2<double> *v, const TileMatrix::ZoomLevel &z,
                            int c, int r);

                        friend ENGINE_API Util::TAKErr TileScraper_estimateTileCount(int &value, TileClient *client, CacheRequest *request);
                        friend class Downloader;
                        friend class DownloadTask;
                    };


                    class DownloadTask {
                    private:
                        std::shared_ptr<ScrapeContext> context;
                        const size_t tileX;
                        const size_t tileY;
                        const size_t tileZ;

                    public:
                        DownloadTask(std::shared_ptr<ScrapeContext> context, size_t tileZ, size_t tileX,
                            size_t tileY);

                        Util::TAKErr run();
                    };



                    class Downloader {

                    private:
                        std::shared_ptr<CacheRequestListener> callback;
                        int levelStartTiles;

                    protected:
                        Downloader(std::shared_ptr<CacheRequestListener> callback);
                        void reportStatus(std::shared_ptr<ScrapeContext> context);

                        void onDownloadEnter(std::shared_ptr<ScrapeContext> context);
                        virtual void onDownloadExit(std::shared_ptr<ScrapeContext> context, int jobStatus);

                        /**
                        * Return <code>true</code> if ready to download the next tile,
                        * <code>false</code> if the downloader should wait and check again
                        * later.
                        *
                        * @param context   The current download context
                        *
                        * @return  <code>true</code> if ready to download the next tile
                        *          immediately, <code>false</code> if the download should be
                        *          delayed.
                        */
                        virtual bool checkReadyForDownload(std::shared_ptr<ScrapeContext> context);

                        virtual void onLevelDownloadComplete(std::shared_ptr<ScrapeContext> context);
                        virtual void onLevelDownloadStart(std::shared_ptr<ScrapeContext> context);
                        virtual Util::TAKErr downloadTileImpl(std::shared_ptr<ScrapeContext> context,
                            int tileLevel, int tileX, int tileY) = 0;

                        /**
                        * kicks off a download of the selected layers at the selected levels in the selected rectangle
                        * TODO: More refined scraping for routes - currently just uses the bounds
                        */
                    public:
                        bool download(std::shared_ptr<ScrapeContext> downloadContext);
                        virtual ~Downloader();
                    };

                    class MultiThreadDownloader : public Downloader {
                    private:
                        std::list<std::unique_ptr<DownloadTask>> queue;
                        // if true, threads should cease when queue is empty
                        bool shutdown;
                        // if true, threads should cease as soon as possible
                        bool terminate;
                        Thread::Monitor queueMonitor;
                        int poolSize;
                        Thread::ThreadPoolPtr pool;

                    public:
                        MultiThreadDownloader(std::shared_ptr<CacheRequestListener> callback, int numDownloadThreads);
                        virtual ~MultiThreadDownloader();
                        
                        void flush(std::shared_ptr<ScrapeContext> context, bool reportStatus);
                    protected:
                        virtual void onDownloadExit(std::shared_ptr<ScrapeContext> context, int jobStatus);

                        virtual bool checkReadyForDownload(std::shared_ptr<ScrapeContext> context);
                        virtual void onLevelDownloadComplete(std::shared_ptr<ScrapeContext> context);
                        virtual Util::TAKErr downloadTileImpl(std::shared_ptr<ScrapeContext> context, int tileLevel,
                            int tileX, int tileY);
                    private:
                        static void *threadEntry(void *selfPtr);
                        void threadRun();
                    };

                    class LegacyDownloader : public Downloader {
                    public:
                        LegacyDownloader(std::shared_ptr<CacheRequestListener> callback);
                        virtual ~LegacyDownloader();
                    protected:
                        virtual Util::TAKErr downloadTileImpl(std::shared_ptr<ScrapeContext> context, int tileLevel,
                            int tileX, int tileY);
                    };


                };

                typedef std::unique_ptr<TileScraper, void(*)(const TileScraper *)> TileScraperPtr;
                /*
                 * Create a TileScraper to transfer tiles from client to the specified sink according to the given request.
                 * callback is notified of progress. 
                 */
                ENGINE_API Util::TAKErr TileScraper_create(TileScraperPtr &value,
                    std::shared_ptr<TileMatrix> client, std::shared_ptr<TileContainer> sink, const CacheRequest &request, std::shared_ptr<CacheRequestListener> callback);
                ENGINE_API Util::TAKErr TileScraper_estimateTileCount(int &value, TileClient *client, CacheRequest *request);



            }
        }
    }
}

#endif