package com.atakmap.map.layer.raster.tilematrix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import android.graphics.Point;
import android.util.SparseBooleanArray;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.map.contentservices.CacheRequest;
import com.atakmap.map.contentservices.CacheRequestListener;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.spatial.GeometryTransformer;

public final class TileScraper {

    private final static String TAG = "TileScraper";
    private static final int MAX_TILES = 300000;
    private static final int MAX_RETRIES = 5;

    private final static ThreadFactory DOWNLOAD_SERVICE_THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger cnt = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName("DownloadAndCache-Thread-" + cnt.getAndIncrement());
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    };

    private final static int DOWNLOAD_ATTEMPTS = 2;

    private final TileMatrix client;
    private final TileContainer sink;
    private final CacheRequest request;
    private final CacheRequestListener callback;
    
    public TileScraper(TileMatrix client, TileContainer sink, CacheRequest request, CacheRequestListener callback) {
        this.client = client;
        this.sink = sink;
        this.request = request;
        this.callback = callback;
        
        if(this.sink.isReadOnly())
            throw new IllegalArgumentException();
        
        // XXX - ensure compatible tile matrices
    }
    
    public void run() {
        Downloader downloader;
        if(request.maxThreads > 1)
            downloader = new MultiThreadDownloader(request.maxThreads);
        else
            downloader = new LegacyDownloader();
        
        
        downloader.download(new ScrapeContext(client, sink, request));
    }

    public static int estimateTileCount(TileClient client, CacheRequest request) {
        ScrapeContext ctx = new ScrapeContext(client, null, request);
        return ctx.totalTiles;
    }
    
    /**************************************************************************/

    private static class TilePoint {
        int r, c;

        TilePoint(int row, int column) {
            this.r = row;
            this.c = column;
        }
    }

    private static class DownloadTask implements Runnable {
        private final ScrapeContext context;
        private final int tileX;
        private final int tileY;
        private final int tileZ;

        public DownloadTask(ScrapeContext context, int tileZ, int tileX,
                int tileY) {
            this.context = context;
            this.tileX = tileX;
            this.tileY = tileY;
            this.tileZ = tileZ;
        }

        @Override
        public void run() {
            boolean success = false;
            try {
                Throwable[] err = new Throwable[1];

                // attempt to download the tile
                int attempts = 0;
                while (attempts < DOWNLOAD_ATTEMPTS) {
                    // clear the error for the attempt
                    err[0] = null;
                    // load the tile
                    byte[] d = this.context.client.getTileData(this.tileZ,
                                                               this.tileX,
                                                               this.tileY,
                                                               err);
                    if(d != null) {
                        // valid entry in cache
                        this.context.sink.setTile(this.tileZ, this.tileX, this.tileY, d, System.currentTimeMillis()+context.request.expirationOffset);
                        success = true;
                        break;
                    } else if(err[0] == null) {
                        // there was no exception raised during which means that
                        // the client is unable to download
                        break;
                    } else {
                        attempts++;
                    }
                }
                
                // set the error if necessary
                this.context.downloadError |= (err[0] != null); 
            } finally {
                this.context.downloadComplete(success);
            }
        }
    }

    private static class ScrapeContext {
        public final TileMatrix client;
        public final TileContainer sink;
        public final CacheRequest request;
        public final String uri;
        public final int[] levels;
        public int currentLevelIdx;
        public int totalTilesCurrentLevel;
        private boolean downloadError;
        private int tilesDownloaded;
        private int totalTiles;
        private Map<Integer, List<TilePoint>> tiles = new HashMap<>();
        private int minLevel = 0, maxLevel = 0;

        // Temp vars across methods
        private final TileMatrix.ZoomLevel[] zooms;
        private Vector2D[] points = new Vector2D[0];
        private Vector2D[] tp = new Vector2D[] {
                new Vector2D(0, 0), new Vector2D(0, 0),
                new Vector2D(0, 0), new Vector2D(0, 0)
        };
        private Vector2D[] tmpSeg = new Vector2D[] {
                new Vector2D(0, 0), new Vector2D(0, 0), new Vector2D(0, 0)
        };
        private boolean closed;

        public ScrapeContext(TileMatrix client, TileContainer container, CacheRequest request) {
            this.client = client;
            this.sink = container;
            this.request = request;
            this.uri = client.getName();

            this.zooms = client.getZoomLevel();
            SparseBooleanArray lvlArray = new SparseBooleanArray();
            for (TileMatrix.ZoomLevel zoom : this.zooms) {
                if (zoom.resolution <= request.minResolution
                        && zoom.resolution >= request.maxResolution)
                    lvlArray.append(zoom.level, true);
            }
            if (lvlArray.size() == 0)
                lvlArray.append(this.zooms[this.zooms.length - 1].level, true);

            // NOTE: order of 'keyAt' is guaranteed to be ascending
            this.levels = new int[lvlArray.size()];
            for (int i = 0; i < this.levels.length; i++)
                this.levels[i] = lvlArray.keyAt(i);

            this.currentLevelIdx = 0;

            this.downloadError = false;
            this.tilesDownloaded = 0;
            this.totalTiles = 0;

            Geometry geo = GeometryTransformer.transform(request.region, 4326,
                    client.getSRID());
            Envelope env = geo.getEnvelope();
            if (levels.length > 0) { 
                this.minLevel = levels[0];
                this.maxLevel = levels[levels.length - 1];
            }

            // Convert geometry to Vector2D for use with intersection math
            LineString ls = null;
            if (geo instanceof LineString)
                ls = (LineString) geo;
            else if (geo instanceof Polygon)
                ls = ((Polygon) geo).getExteriorRing();
            if (ls != null) {
                this.points = new Vector2D[ls.getNumPoints()];
                for (int i = 0; i < this.points.length; i++)
                    this.points[i] = new Vector2D(ls.getX(i), ls.getY(i));
            }
            this.closed = this.points[0].x == this.points[this.points.length - 1].x
                    && this.points[0].y == this.points[this.points.length - 1].y;

            // Scan for intersecting tiles
            Point minTile = TileMatrix.Util.getTileIndex(client,
                    0, env.minX, env.maxY);
            Point maxTile = TileMatrix.Util.getTileIndex(client,
                    0, env.maxX, env.minY);

            for (int r = minTile.y; r <= maxTile.y; r++)
                for (int c = minTile.x; c <= maxTile.x; c++)
                    getTiles(c, r, 0, this.maxLevel);
        }

        /**
         * Get all tiles in a quad tree
         * @param col Root tile column
         * @param row Root tile row
         * @param level Root tile level
         * @param max Maximum level
         */
        private void getTiles(int col, int row, int level, int max) {

            if (level > max || this.totalTiles >= MAX_TILES)
                return;

            TileMatrix.ZoomLevel zoom = this.zooms[level];

            // Get tile points for checking intersection
            getSourcePoint(client, zoom, col, row, this.tp[0]);
            getSourcePoint(client, zoom, col + 1, row, this.tp[1]);
            getSourcePoint(client, zoom, col + 1, row + 1, this.tp[2]);
            getSourcePoint(client, zoom, col, row + 1, this.tp[3]);

            // Intersects or contains
            boolean add = Vector2D.polygonContainsPoint(this.points[0], this.tp);
            if (!add) {
                outer:for (int i = 0; i < 4; i++) {
                    Vector2D s = this.tp[i];
                    Vector2D e = this.tp[i == 3 ? 0 : i + 1];
                    for (int j = 0; j < this.points.length - 1; j++) {
                        if (segmentIntersects(s, e, this.points[j],
                                this.points[j + 1])) {
                            add = true;
                            break outer;
                        }
                    }
                }
                if (!add && closed)
                    add = Vector2D.polygonContainsPoint(this.tp[0], this.points);
            }

            if (add) {
                // Add this tile
                if (level >= this.minLevel && level <= this.maxLevel) {
                    if (!this.request.countOnly) {
                        List<TilePoint> lt = this.tiles.get(level);
                        if (lt == null)
                            this.tiles.put(level, lt = new ArrayList<>());
                        lt.add(new TilePoint(row, col));
                    }
                    this.totalTiles++;
                }


                // Check sub-tiles
                row *= 2;
                col *= 2;
                for (int r = row; r <= row + 1; r++)
                    for (int c = col; c <= col + 1; c++)
                        getTiles(c, r, level + 1, max);
            }
        }

        private boolean segmentIntersects(Vector2D seg10,
                Vector2D seg11, Vector2D seg01, Vector2D seg00) {
            tmpSeg[0].set(seg01.x - seg00.x, seg01.y - seg00.y);
            tmpSeg[1].set(seg11.x - seg10.x, seg11.y - seg10.y);
            double c1 = tmpSeg[1].cross(tmpSeg[0]);
            if (c1 != 0d) {
                tmpSeg[2].set(seg00.x - seg10.x, seg00.y - seg10.y);
                double t = tmpSeg[2].cross(tmpSeg[0]) / c1;
                double u = tmpSeg[2].cross(tmpSeg[1]) / c1;
                return t >= 0 && t <= 1 && u >= 0 && u <= 1;
            }
            return false;
        }

        private void getSourcePoint(TileMatrix client, TileMatrix.ZoomLevel z,
                int c, int r, Vector2D v) {
            v.x = client.getOriginX() + (c * z.pixelSizeX * z.tileWidth);
            v.y = client.getOriginY() - (r * z.pixelSizeY * z.tileHeight);
        }

        public synchronized void downloadComplete(boolean success) {
            if (success) {
                this.tilesDownloaded++;
            } else {
                this.downloadError = true;
            }
        }

        public synchronized boolean downloadError() {
            return this.downloadError;
        }

        public synchronized int tilesDownloaded() {
            return this.tilesDownloaded;
        }
    }

    private abstract class Downloader {

        private int levelStartTiles;

        protected void reportStatus(ScrapeContext downloadContext) {
            final int numDownload = downloadContext.tilesDownloaded();
            
            if(TileScraper.this.callback != null) {
                callback.onRequestProgress(downloadContext.currentLevelIdx,
                                           downloadContext.levels.length,
                                           numDownload-this.levelStartTiles,
                                           downloadContext.totalTilesCurrentLevel,
                                           numDownload,
                                           downloadContext.totalTiles);
            }            
        }

        protected void onDownloadEnter(final ScrapeContext context) {
        }

        protected void onDownloadExit(final ScrapeContext context, final int jobStatus) {
        }

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
        protected boolean checkReadyForDownload(ScrapeContext context) {
            return true;
        }

        protected void onLevelDownloadComplete(ScrapeContext context) {
        }

        protected void onLevelDownloadStart(ScrapeContext context) {
        }

        protected abstract void downloadTileImpl(ScrapeContext context,
                int tileLevel, int tileX, int tileY);



        /**
         * kicks off a download of the selected layers at the selected levels in the selected rectangle
         */
        public boolean download(ScrapeContext downloadContext) {
            Log.d(TAG, "Starting download of " + client.getName() + " cache...");
            int retries = 0;

            if(callback != null)
                callback.onRequestStarted();

            try {
                reportStatus(downloadContext);
                
                this.onDownloadEnter(downloadContext);

                for (int l = 0; l < downloadContext.levels.length; l++) {
                    downloadContext.currentLevelIdx = l;
                    int currentLevel = downloadContext.levels[l];

                    TileMatrix.ZoomLevel zoom = TileMatrix.Util
                            .findZoomLevel(client, currentLevel);

                    int tile180X = zoom != null ? (int) ((client.getOriginX() * -2)
                            / (zoom.pixelSizeX * zoom.tileWidth)) : -1;

                    List<TilePoint> tiles = downloadContext.tiles.get(downloadContext.levels[l]);
                    downloadContext.totalTilesCurrentLevel = tiles.size();
                    this.levelStartTiles = downloadContext.tilesDownloaded();

                    this.onLevelDownloadStart(downloadContext);

                    for (TilePoint tile : tiles) {
                        while (true) {

                            // check for cancel
                            if (checkRequestCancelled())
                                return false;

                            // check for error
                            if (downloadContext.downloadError()) {
                                retries++;
                                if (retries > MAX_RETRIES) {
                                    if (callback != null)
                                        callback.onRequestError(null, null, true);

                                    Log.d(TAG,
                                            "Lost network connection during map download.");

                                    return false;
                                } else {
                                    Log.d(TAG, "attempting retry[" + retries + "], failed download for " + tile.c + ", " + tile.r +  " backing off for " + (1000 * retries) + "ms");
                                    // don't consider it an error just yet.
                                    downloadContext.downloadError = false;
                                    try {
                                        Thread.sleep(1000 * retries);
                                    } catch (InterruptedException ignored) {
                                    }
                                }
                            } else {
                                retries = 0;
                            }

                            // Check again for cancle before potentially
                            // sleeping another 50 ms
                            if (checkRequestCancelled())
                                return false;

                            // report status
                            this.reportStatus(downloadContext);

                            // check if we should sleep for a little bit
                            // before proceeding to initiate download of
                            // the next tile
                            if (!this
                                    .checkReadyForDownload(downloadContext)) {
                                try {
                                    Thread.sleep(50);
                                } catch (InterruptedException ignored) {
                                }
                                continue;
                            }

                            // proceed to download
                            break;
                        }

                        // One last cancel check before starting download
                        if (checkRequestCancelled())
                            return false;

                        // download
                        if (tile180X > -1 && tile.c >= tile180X)
                            downloadTileImpl(downloadContext, currentLevel, tile.c - tile180X, tile.r);
                        else
                            downloadTileImpl(downloadContext, currentLevel, tile.c, tile.r);

                        this.onLevelDownloadComplete(downloadContext);
                    }
                }
                
                if(callback != null)
                    callback.onRequestComplete();

                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error while trying to download from "
                        + downloadContext.uri, e);
                return false;
            } finally {
                this.onDownloadExit(downloadContext, 0);
            }
        }

        private boolean checkRequestCancelled() {
            if (request.canceled) {
                if (callback != null)
                    callback.onRequestCanceled();
                return true;
            }
            return false;
        }
    }

    private class MultiThreadDownloader extends Downloader {
        private ThreadPoolExecutor downloadService;
        private LinkedBlockingQueue<Runnable> queue;

        public MultiThreadDownloader(int numDownloadThreads) {
            if (numDownloadThreads <= 1)
                throw new IllegalArgumentException();
            this.queue = new LinkedBlockingQueue<>();

            this.downloadService = new ThreadPoolExecutor(
                    request.maxThreads,
                    request.maxThreads,
                    500,
                    TimeUnit.MILLISECONDS,
                    this.queue,
                    DOWNLOAD_SERVICE_THREAD_FACTORY);
        }

        private void flush(ScrapeContext downloadContext, boolean reportStatus) {

            // wait for queue to empty 
            while (this.queue.size() > 0) {
                // check for cancel
                if (request.canceled)
                    break;

                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                }

                // report status
                if (reportStatus)
                    this.reportStatus(downloadContext);
            }
        }

        @Override
        protected void onDownloadExit(ScrapeContext context, int jobStatus) {
            this.flush(context, false);
            if (!this.downloadService.isShutdown())
                this.downloadService.shutdown();
        }

        @Override
        protected boolean checkReadyForDownload(ScrapeContext context) {
            return (this.queue.size() < (3 * this.downloadService
                    .getMaximumPoolSize()));
        }

        @Override
        protected void onLevelDownloadStart(ScrapeContext downloadContext) {
        }

        @Override
        protected void onLevelDownloadComplete(ScrapeContext downloadContext) {
            this.flush(downloadContext, true);
        }

        @Override
        protected void downloadTileImpl(ScrapeContext context, int tileLevel,
                int tileX, int tileY) {
            // enqueue
            downloadService.execute(new DownloadTask(context, tileLevel, tileX,
                    tileY));
        }
    }

    private class LegacyDownloader extends Downloader {

        @Override
        protected void downloadTileImpl(ScrapeContext context, int tileLevel,
                int tileX, int tileY) {
            
            DownloadTask t = new DownloadTask(context, tileLevel, tileX, tileY);
            t.run();
        }
    }

   
}
