package com.atakmap.map.layer.raster.mobac;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.raster.controls.TileClientControl;
import com.atakmap.map.layer.raster.tilepyramid.AbstractTilePyramidTileReader;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderSpi;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory.Options;
import com.atakmap.util.ReferenceCount;

public class MobacTileReader extends AbstractTilePyramidTileReader {

    public final static TileReaderSpi SPI = new TileReaderSpi() {
        @Override
        public String getName() {
            return "mobac";
        }

        @Override
        public TileReader create(String uri, Options options) {
            File file = new File(uri);
            if(!IOProviderFactory.exists(file))
                return null;
            
            try {
                MobacMapSource source = MobacMapSourceFactory.create(file);
                if(source == null)
                    return null;
                
                String cacheUri = null;
                AsynchronousIO io = TileReader.getMasterIOThread();
                if(options != null) {
                    cacheUri = options.cacheUri;
                    if(options.asyncIO != null)
                        io = options.asyncIO;
                }
    
                return new MobacTileReader(uri, cacheUri, io, source);
            } catch(Exception e) {
                return null;
            }
        }
        
        @Override
        public boolean isSupported(String uri) {
            File file = new File(uri);
            if(!IOProviderFactory.exists(file))
                return false;

            try {
                final MobacMapSource source = MobacMapSourceFactory.create(file);
                return (source != null);
            } catch(IOException e) {
                return false;
            }
        }
    };

    private final int maxZoom;
    private MobacMapSource source;
    private MobacTileClientRef client;
    private final ConnectivityChecker connectivityChecker;
    private final AtomicBoolean doConnectivityCheck = new AtomicBoolean(true);
    private final TileServerControlImpl tilesControl;
    private TileDownloader backgroundDownloader;
    
    private long version;

    public MobacTileReader(String uri, String cacheUri, AsynchronousIO asyncIO, MobacMapSource mapSource) throws IOException {
        super(uri,
              null,
              asyncIO,
              mapSource.getMaxZoom()-mapSource.getMinZoom()+1,
              (long)(mapSource.getTileSize()*getGridWidth(mapSource))<<(long)mapSource.getMaxZoom(),
              (long)(mapSource.getTileSize()*getGridHeight(mapSource))<<(long)mapSource.getMaxZoom(),
              mapSource.getTileSize(),
              mapSource.getTileSize());

        this.source = mapSource;
        this.client = new MobacTileClientRef(new MobacTileClient(this.source, cacheUri));
        this.maxZoom = mapSource.getMaxZoom();
        this.connectivityChecker = new ConnectivityChecker();
        this.tilesControl = new TileServerControlImpl();

        this.registerControl(this.tilesControl);
        
        this.version = 0L;
        this.backgroundDownloader = new TileDownloader(this.client);
        Thread t = new Thread(this.backgroundDownloader, TAG + "-Init");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }
    
    /**************************************************************************/
    // Tile Reader
    
    @Override
    public void disposeImpl() {
        super.disposeImpl();

        if(this.backgroundDownloader != null) {
            this.backgroundDownloader.dispose();
            this.backgroundDownloader = null;
        }
        
        this.client.dereference();
        this.client = null;
        this.readLock.notify();
    }

    @Override
    protected void cancel() {
        final TileDownloader downloader = this.backgroundDownloader;
        if(downloader != null)
            downloader.cancel();
    }
    
    @Override
    public long getTileVersion(int level, long tileColumn, long tileRow) {
        // XXX - goes here or in start ??? here is likely more apt as we want
        //       the up to date version value
        
        // check to see if we need to refresh on every render pump 
        this.tilesControl.checkRefresh();
        
        return this.version;
    }

    @Override
    protected Bitmap getTileImpl(int level, long tileColumn, long tileRow, ReadResult[] code) {
        return getTileImpl(level, tileColumn, tileRow, null, code);
    }

    @Override
    protected Bitmap getTileImpl(int level, long tileColumn, long tileRow, BitmapFactory.Options opts, ReadResult[] code) {
        if (level < 0)
            throw new IllegalArgumentException();


        // set offline mode as appropriate
        this.client.value.setOfflineMode(this.tilesControl.isOfflineOnlyMode());

        final Bitmap retval = this.backgroundDownloader.load(this.maxZoom-level,
                                                             tileColumn,
                                                             tileRow,
                                                             opts);

        code[0] = (retval!=null) ? ReadResult.SUCCESS : ReadResult.ERROR;
        return retval;

    }

    @Override
    public void start() {
        // before kicking off a bunch of tile readers, reset the connectivity
        // check in the map source to re-enable downloading if network
        // connectivity was restored
        this.doConnectivityCheck.set(true);
        //if(this.connectivityChecker.isQueued.compareAndSet(false, true))
        //    this.asyncRun(this.connectivityChecker);
    }

    /*************************************************************************/

    private static int getGridWidth(MobacMapSource mapSource) {
        if(mapSource.getSRID() == 4326)
            return 2;
        else
            return 1;
    }

    private static int getGridHeight(MobacMapSource mapSource) {
        return 1;
    }
    
    /*************************************************************************/
    
    private class ConnectivityChecker implements Runnable {
        AtomicBoolean isQueued = new AtomicBoolean(false);
        @Override
        public void run() {
            isQueued.set(false);
            MobacTileReader.this.source.checkConnectivity();
        }
    }
    
    private class TileDownloader implements Runnable {
        private MobacTileClientRef client;

        private LinkedList<TileLoader> queue = new LinkedList<TileLoader>();
        private TileLoader loading;
        private boolean disposed;

        public TileDownloader(MobacTileClientRef client) {
            this.client = client;
            
            this.queue = new LinkedList<TileLoader>();
            this.loading = null;
            this.disposed = false;
        }

        /** caller holds lock on 'readLock' */
        public Bitmap load(long level, long tilex, long tiley, BitmapFactory.Options opts) {
            synchronized(this) {
                TileLoader loader = new TileLoader();
                loader.level = (int)level;
                loader.tileX = (int)tilex;
                loader.tileY = (int)tiley;
                loader.canceled = false;
                loader.bitmap = null;
                loader.opts = opts;

                this.queue.addLast(loader);
                this.notifyAll();
                while(!loader.done && !loader.canceled && !this.disposed) {
                    try {
                        this.wait();
                    } catch(InterruptedException ignored) {}
                }
                
                return loader.bitmap;
            }
        }
        
        public synchronized void cancel() {
            if(this.loading != null)
                this.loading.canceled = true;
            this.notifyAll();
        }

        public synchronized void dispose() {
            for(TileLoader loader : this.queue)
                loader.canceled = true;
            this.queue.clear();

            this.disposed = true;

            this.cancel();
        }
        
        @Override
        public void run() {
            this.client.reference();
            try {
                TileLoader loader;
                while(true) {
                    synchronized(this) {
                        this.loading = null;
                        // XXX - periodically recompute dirty region
    
                        // if reader has been disposed, break
                        if(MobacTileReader.this.backgroundDownloader != this)
                            break;
    
                        // wait on queue if empty
                        if(this.queue.isEmpty()) {
                            try {
                                this.wait();
                            } catch(InterruptedException ignored) {}
                            continue;
                        }
    
                        // dequeue tile to be downloaded
                        this.loading = this.queue.removeFirst();
                        loader = this.loading;
                    }
                    
                    loader.run();
                }
            } finally {
                this.client.dereference();
            }
        }
        
        final class TileLoader implements Runnable {
            BitmapFactory.Options opts;
            int level;
            int tileX;
            int tileY;
            Bitmap bitmap;
            boolean canceled;
            boolean done;

            @Override
            public void run() {
                if(MobacTileReader.this.doConnectivityCheck.compareAndSet(true, false))
                    MobacTileReader.this.source.checkConnectivity();

                try {
                    final long currentTime = System.currentTimeMillis();
                    final long expiration = TileDownloader.this.client.value.checkTileExpiration(this.level, this.tileX, this.tileY);
                    final long downloaded = MobacTileClient.getTileDownloadTime(expiration);
                    
                    final boolean isExpired = (expiration>0) && (currentTime>expiration);
                    final boolean needsRefresh = (expiration>0) && ((currentTime-downloaded)>MobacTileReader.this.tilesControl.refreshInterval);
                    
                    if(!TileDownloader.this.client.value.isOfflineMode() && (isExpired || needsRefresh)) {
                        TileDownloader.this.client.value.cacheTile(this.level,
                                                                   this.tileX,
                                                                   this.tileY,
                                                                   true,
                                                                   null);
                    }

                    if(opts == null)
                        opts = DECODE_OPTS;
                    else if(opts.inPreferredConfig == null)
                        opts.inPreferredConfig = DECODE_OPTS.inPreferredConfig;

                    this.bitmap = TileDownloader.this.client.value.loadTile(
                                        this.level,
                                        this.tileX,
                                        this.tileY,
                                        opts,
                                        null);
                } catch(Throwable t) {
                    Log.e("MobacTileReader", "Failed to load tile " + this.level + "," + this.tileX + "," + this.tileY, t );
                } finally {
                    this.done = true;
                }

                
                synchronized(TileDownloader.this) {
                    TileDownloader.this.notifyAll();
                }
            }
            
            public Bitmap get() {
                return this.bitmap;
            }
        }
    }
    

    private class TileServerControlImpl implements TileClientControl {

        private boolean offlineOnlyMode;
        private boolean forceRefresh;
        private long refreshInterval = Long.MAX_VALUE;
        private long lastReportedRefresh = 0L;
        
        @Override
        public synchronized void setOfflineOnlyMode(boolean offlineOnly) {
            if(offlineOnly != this.offlineOnlyMode) {
                this.offlineOnlyMode = offlineOnly;
                if(!this.offlineOnlyMode)
                    MobacTileReader.this.version++;
            }
        }

        @Override
        public synchronized boolean isOfflineOnlyMode() {
            return this.offlineOnlyMode;
        }

        @Override
        public synchronized void refreshCache() {
            this.forceRefresh = true;
        }

        @Override
        public synchronized void setCacheAutoRefreshInterval(long milliseconds) {
            if(milliseconds == 0L)
                milliseconds = Long.MAX_VALUE;
            this.refreshInterval = milliseconds;
        }
        
        @Override
        public synchronized long getCacheAutoRefreshInterval() {
            return (this.refreshInterval == Long.MAX_VALUE) ? 0L : this.refreshInterval;
        }

        public synchronized void checkRefresh() {
            if(this.forceRefresh) {
                this.forceRefresh = false;
                this.lastReportedRefresh = System.currentTimeMillis();
                MobacTileReader.this.version++;
            } else {
                final long currentTime = System.currentTimeMillis();
                if((currentTime-this.lastReportedRefresh) > this.refreshInterval) {
                    this.lastReportedRefresh = System.currentTimeMillis();
                    MobacTileReader.this.version++;
                }
            }
        }
    }
    
    private static class MobacTileClientRef extends ReferenceCount<MobacTileClient> {

        public MobacTileClientRef(MobacTileClient value) {
            super(value);
        }
        
        
        @Override
        protected void onDereferenced() {
            this.value.close();
        }
    }
}
