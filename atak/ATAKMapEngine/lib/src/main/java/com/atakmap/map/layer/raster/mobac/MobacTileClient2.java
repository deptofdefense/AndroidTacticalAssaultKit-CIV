package com.atakmap.map.layer.raster.mobac;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.contentservices.CacheRequest;
import com.atakmap.map.contentservices.CacheRequestListener;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.raster.controls.TileClientControl;
import com.atakmap.map.layer.raster.mobileimagery.MobileImageryRasterLayer2;
import com.atakmap.map.layer.raster.osm.OSMDroidTileContainer;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.map.layer.raster.tilematrix.TileClient;
import com.atakmap.map.layer.raster.tilematrix.TileClientSpi;
import com.atakmap.map.layer.raster.tilematrix.TileContainer;
import com.atakmap.map.layer.raster.tilematrix.TileContainerFactory;
import com.atakmap.map.layer.raster.tilematrix.TileEncodeException;
import com.atakmap.map.layer.raster.tilematrix.TileMatrix;
import com.atakmap.map.layer.raster.tilematrix.TileScraper;
import com.atakmap.map.projection.Projection;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.util.ReferenceCount;

public class MobacTileClient2 implements TileClient, TileClientControl {

    public final static TileClientSpi SPI = new TileClientSpi() {

        @Override
        public String getName() {
            return "mobac";
        }

        @Override
        public TileClient create(String path, String offlineCachePath, TileClientSpi.Options opts) {
            try {
                MobacMapSource.Config c = new MobacMapSource.Config();
                if(opts != null) {
                    c.dnsLookupTimeout = opts.dnsLookupTimeout;
                    c.connectTimeout = (int)opts.connectTimeout;
                }
                MobacMapSource src = MobacMapSourceFactory.create(new File(path), c);
                if(src == null)
                    return null;
                return new MobacTileClient2(src, offlineCachePath);
            } catch(IOException e) {
                return null;
            }
        }

        @Override
        public int getPriority() {
            // octet stream : XML : Mobac XML
            return 2;
        }
        
    };
    
    public final static long ONE_WEEK_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    private final static BitmapFactory.Options CACHE_OPTS = new BitmapFactory.Options();
    static {
        CACHE_OPTS.inJustDecodeBounds = true;
    }

    private final static String TAG = "MobacTileClient2";

    private static Map<String, ReferenceCount<TileContainer>> caches = new HashMap<String, ReferenceCount<TileContainer>>();

    private final MobacMapSource source;
    TileContainer offlineCache;
    private boolean offlineMode;
    private Envelope bounds;
    private PointD origin;
    private ZoomLevel[] levels;
    private int srid;

    public MobacTileClient2(MobacMapSource src, String offlineCachePath) {
        this.source = src;
        this.offlineMode = false;
        
        Projection proj = MobileImageryRasterLayer2.getProjection(src.getSRID());
        this.origin = proj.forward(new GeoPoint(proj.getMaxLatitude(), proj.getMinLongitude()), null);

        GeoBounds bndswgs84 = this.source.getBounds();
        this.bounds = new Envelope();
        if(bndswgs84 == null)
            bndswgs84 = new GeoBounds(proj.getMinLatitude(), proj.getMinLongitude(), proj.getMaxLatitude(), proj.getMaxLongitude());
        
        PointD ul = proj.forward(new GeoPoint(proj.getMaxLatitude(), proj.getMinLongitude()), null);
        PointD ur = proj.forward(new GeoPoint(proj.getMaxLatitude(), proj.getMaxLongitude()), null);
        PointD lr = proj.forward(new GeoPoint(proj.getMinLatitude(), proj.getMaxLongitude()), null);
        PointD ll = proj.forward(new GeoPoint(proj.getMinLatitude(), proj.getMinLongitude()), null);
        
        this.bounds = new Envelope(MathUtils.min(ul.x, ur.x, lr.x, ll.x),
                                   MathUtils.min(ul.y, ur.y, lr.y, ll.y),
                                   0d,
                                   MathUtils.max(ul.x, ur.x, lr.x, ll.x),
                                   MathUtils.max(ul.y, ur.y, lr.y, ll.y),
                                   0);

        // create zoom levels
        int gridRows = 1;
        int gridCols = 1;
        if(source.getSRID() == 4326)
            gridCols = 2;
        this.levels = Util.createQuadtree(createLevel0(proj, gridCols, gridRows), Math.min(source.getMaxZoom()+1, 30));
        
        this.srid = source.getSRID();
        switch(this.srid) {
            case 90094326 :
                this.srid = 4326;
                break;
            case 900913 :
                this.srid = 3857;
                break;
            default :
                break;
        }

        if(offlineCachePath != null) {
            // prefer an OSMDroid container, if possible
            String hint = OSMDroidTileContainer.SPI.getName();
            
            // try to open/create the cache from the path
            this.offlineCache = openOrCreateCache(offlineCachePath, this, hint);

            // if no cache could be opened from the file then attempt to delete
            // the file and create a new cache
            if(this.offlineCache == null && IOProviderFactory.exists(new File(offlineCachePath))) {
                FileSystemUtils.delete(offlineCachePath);
                this.offlineCache = openOrCreateCache(offlineCachePath, this, hint);
            }
        }
    }

    private synchronized static TileContainer openOrCreateCache(final String path, TileMatrix spec, String preferredProvider) {
        // XXX - should cache key be provider+path ??? not sure it's that
        // important in this context but something to keep in mind

        // check the cache
        ReferenceCount<TileContainer> cached = caches.get(path);
        if(cached != null)
            return new SharedTileContainer(cached);
        
        TileContainer offlineCache = null;

        // attempt to open with hint, then without if necessary
        do {
            offlineCache = TileContainerFactory.openOrCreateCompatibleContainer(path, spec, preferredProvider);
            if(preferredProvider == null)
                break;
            preferredProvider = null;
        } while(offlineCache == null);
        
        // create a cache entry
        if(offlineCache != null) {
            cached = new ReferenceCount<TileContainer>(offlineCache, false) {
                @Override
                protected void onDereferenced() {
                    super.onDereferenced();
                    
                    try {
                        value.dispose();
                    } catch(Throwable ignored) {}
                    synchronized(MobacTileClient2.class) {
                        caches.remove(path);
                    }
                }
            };
            
            offlineCache = new SharedTileContainer(cached);
            caches.put(path, cached);
        }
        return offlineCache;
    }

    @Override
    public String getName() {
        return this.source.getName();
    }

    @Override
    public int getSRID() {
        return this.srid;
    }

    @Override
    public ZoomLevel[] getZoomLevel() {
        return this.levels;
    }

    @Override
    public double getOriginX() {
        return this.origin.x;
    }

    @Override
    public double getOriginY() {
        return this.origin.y;
    }

    @Override
    public Bitmap getTile(int zoom, int x, int y, Throwable[] error) {
        byte[] data = this.getTileData(zoom, x, y, error);
        if(data == null)
            return null;
        Bitmap retval = BitmapFactory.decodeByteArray(data, 0, data.length);
        
        // XXX - resize to 256x256. This obviously isn't the most efficient
        // solution, however, it's no worse than doing the flip on the
        // legacy tilesets
        if (retval != null
                && (retval.getWidth() != 256 || retval.getHeight() != 256)) {
            Bitmap scaled = Bitmap.createScaledBitmap(retval, 256, 256, false);
            retval.recycle();
            retval = scaled;
        }
        
        return retval;
    }

    @Override
    public byte[] getTileData(int zoom, int x, int y, Throwable[] error) {
        byte[] retval = null;
        long expiration = -1;
        if(this.offlineCache != null) {
            if(this.offlineCache.hasTileExpirationMetadata()) {
                expiration = this.offlineCache.getTileExpiration(zoom, x, y);
            } else {
                retval = this.offlineCache.getTileData(zoom, x, y, error);
                if(retval != null)
                    expiration = Long.MAX_VALUE;
            }
        }
        boolean haveCatalogEntry = (expiration > 0) || (retval != null);

        // if data is null or expiration exceeded, load from URL
        if (!this.offlineMode && this.source != null
                && System.currentTimeMillis() > expiration) {
            try {
                MobacMapTile tile = this.source.loadTile(zoom, x, y, CACHE_OPTS);
                if (tile != null && tile.data != null) {
                    retval = tile.data;
                    if (tile.expiration < (System.currentTimeMillis() + ONE_WEEK_MILLIS))
                        expiration = (System.currentTimeMillis() + ONE_WEEK_MILLIS);
                    else
                        expiration = tile.expiration;

                    // update cache with downloaded data
                    if (this.offlineCache != null && tile.data != null) {
                        this.offlineCache.setTile(zoom, x, y, tile.data, expiration);
                    }
                }
            } catch (IOException e) {
                IOException ex = e;
                String reason = "IO Error";
                if (e instanceof java.net.SocketTimeoutException) {
                    ex = null; 
                    reason = "Timeout";
                }
                Log.e(TAG, reason + " during tile download, "
                                + this.getName() + " (" + zoom + ", "
                                + x + ", " + y + ")", ex);
                if (error != null)
                    error[0] = e;
            } catch(Throwable t) {
                Log.e(TAG,
                        "Unspecified Error during tile download, "
                                + this.getName() + " (" + zoom + ", "
                                + x + ", " + y + ")", t);
                if (error != null)
                    error[0] = t;
            }
        }

        if (retval == null && haveCatalogEntry) {
            try {
                retval = this.offlineCache.getTileData(zoom, x, y, error);
            } catch (Exception e) {
                if(error != null)
                    error[0] = e;
            }
            
            if(error != null && error[0] != null) {
                Log.e(TAG,
                        "Offline cache tile query failed, "
                                + this.getName() + " (" + zoom + ", "
                                + x + ", " + y + ")", error[0]);
            }
        }

        return retval;
    }

    @Override
    public Envelope getBounds() {
        return this.bounds;
    }

    @Override
    public void dispose() {
        if(this.offlineCache != null) {
            this.offlineCache.dispose();
            this.offlineCache = null;
        }
        
        // XXX - 
    }

    @Override
    public void checkConnectivity() {
        this.source.checkConnectivity();
    }
    
    @Override
    public void clearAuthFailed() {
        this.source.clearAuthFailed();
    }

    @Override
    public <T> T getControl(Class<T> controlClazz) {
        if(controlClazz.isAssignableFrom(this.getClass()))
            return controlClazz.cast(this);
        return null;
    }

    @Override
    public void getControls(Collection<Object> controls) {
        controls.add(this);
    }

    @Override
    public void cache(CacheRequest request, CacheRequestListener listener) {
        String preferredProvider = OSMDroidTileContainer.SPI.getName();
        if(request != null && request.preferredContainerProvider != null)
            preferredProvider = request.preferredContainerProvider;
        
        TileContainer sink;

        if (request == null) { 
            Log.e(TAG, "Unable to create tile container for cache request == null");
            if(listener != null)
                listener.onRequestError(null, "Unable to create tile container for cache request == null", true);
            return;
        }

        if(request.preferredContainerProvider == null)
            sink = openOrCreateCache(request.cacheFile.getAbsolutePath(), this, preferredProvider);
        else
            sink = TileContainerFactory.openOrCreateCompatibleContainer(request.cacheFile.getAbsolutePath(), this, preferredProvider);

        if(sink == null) {
            Log.e(TAG, "Unable to create tile container for cache request");
            if(listener != null)
                listener.onRequestError(null, "Unable to create tile container for cache request", true);
            return;
        }
        TileScraper scraper = new TileScraper(this, sink, request, listener);
        scraper.run();
    }
    
    @Override
    public int estimateTileCount(CacheRequest request) {
        return TileScraper.estimateTileCount(this, request);
    }

    @Override
    public void setOfflineOnlyMode(boolean offlineOnly) {
        this.offlineMode = offlineOnly;
    }

    @Override
    public boolean isOfflineOnlyMode() {
        return this.offlineMode;
    }

    @Override
    public void refreshCache() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setCacheAutoRefreshInterval(long milliseconds) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public long getCacheAutoRefreshInterval() {
        // TODO Auto-generated method stub
        return 0;
    }

    /**************************************************************************/
    
    private static TileMatrix.ZoomLevel createLevel0(Projection proj, int gridCols, int gridRows) {
        PointD upperLeft = proj.forward(new GeoPoint(proj.getMaxLatitude(), proj.getMinLongitude()), null);
        PointD lowerRight = proj.forward(new GeoPoint(proj.getMinLatitude(), proj.getMaxLongitude()), null);
        
        // XXX - better resolution for 4326???

        TileMatrix.ZoomLevel retval = new ZoomLevel();
        retval.level = 0;
        retval.resolution = OSMUtils.mapnikTileResolution(retval.level);
        retval.tileWidth = 256;
        retval.tileHeight = 256;
        retval.pixelSizeX = (lowerRight.x-upperLeft.x) / (retval.tileWidth*gridCols);
        retval.pixelSizeY = (upperLeft.y-lowerRight.y) / (retval.tileHeight*gridRows);
        return retval;
    }

    /*************************************************************************/
    
    private static class SharedTileContainer implements TileContainer {

        private ReferenceCount<TileContainer> impl;
        private boolean disposed;

        SharedTileContainer(ReferenceCount<TileContainer> impl) {
            this.impl = impl;
            this.impl.reference();
            
            disposed = false;
        }

        @Override
        public String getName() {
            return this.impl.value.getName();
        }

        @Override
        public int getSRID() {
            return this.impl.value.getSRID();
        }

        @Override
        public ZoomLevel[] getZoomLevel() {
            return this.impl.value.getZoomLevel();
        }

        @Override
        public double getOriginX() {
            return this.impl.value.getOriginX();
        }

        @Override
        public double getOriginY() {
            return this.impl.value.getOriginY();
        }

        @Override
        public Bitmap getTile(int zoom, int x, int y, Throwable[] error) {
            return this.impl.value.getTile(zoom, x, y, error);
        }

        @Override
        public byte[] getTileData(int zoom, int x, int y, Throwable[] error) {
            return this.impl.value.getTileData(zoom, x, y, error);
        }

        @Override
        public Envelope getBounds() {
            return this.impl.value.getBounds();
        }

        @Override
        public synchronized void dispose() {
            if(disposed)
                Log.w(TAG, "double dispose detected");
            disposed = true;

            // must obtain static lock before dereferencing to preserve lock
            // acquisition order and prevent deadlock
            synchronized(MobacTileClient2.class) {
                this.impl.dereference();
            }
        }

        @Override
        public boolean isReadOnly() {
            return this.impl.value.isReadOnly();
        }

        @Override
        public void setTile(int level, int x, int y, byte[] data, long expiration) {
            this.impl.value.setTile(level, x, y, data, expiration);
        }

        @Override
        public void setTile(int level, int x, int y, Bitmap data, long expiration)
                throws TileEncodeException {
            this.impl.value.setTile(level, x, y, data, expiration);
        }

        @Override
        public boolean hasTileExpirationMetadata() {
            return this.impl.value.hasTileExpirationMetadata();
        }

        @Override
        public long getTileExpiration(int level, int x, int y) {
            return this.impl.value.getTileExpiration(level, x, y);
        }
        
    }
}
