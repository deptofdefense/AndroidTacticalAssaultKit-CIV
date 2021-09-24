
package com.atakmap.map.layer.raster.mobac;

import java.util.HashSet;
import java.util.Set;

import com.atakmap.map.layer.raster.osm.OSMUtils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.atakmap.coremap.log.Log;

public class MobacTileClient {

    public final static String TAG = "MobacTileClient";

    public final static long ONE_WEEK_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    private final static BitmapFactory.Options CACHE_OPTS = new BitmapFactory.Options();
    static {
        CACHE_OPTS.inJustDecodeBounds = true;
    }

    private MobacMapSource mapSource;
    private boolean offlineMode;

    private Set<Long> pendingCacheUpdates;

    private MobacTileClient2 impl;

    public MobacTileClient(MobacMapSource mapSource, String offlineCachePath) {
        this.mapSource = mapSource;
        this.impl = new MobacTileClient2(mapSource, offlineCachePath);

        this.pendingCacheUpdates = new HashSet<Long>();
    }
    
    public boolean hasCache() {
        return (this.impl.offlineCache != null);
    }

    public void setOfflineMode(boolean offlineOnly) {
        this.impl.setOfflineOnlyMode(offlineOnly);
    }

    public boolean isOfflineMode() {
        return this.impl.isOfflineOnlyMode();
    }

    public synchronized void close() {
        this.impl.dispose();
    }

    @Override
    protected void finalize() throws Throwable {
        this.close();

        super.finalize();
    }

    /**
     * Loads the specified tile as a bitmap. If an offline cache is available, an attempt to load
     * the cached tile is made. If no cached tile is available or the cached tile is marked as
     * expired new data is downloaded from the server and stored in the cache.
     * 
     * @param resolver
     * @param offlineCache
     * @param opts
     * @param zoom
     * @param x
     * @param y
     * @return
     */
    public Bitmap loadTile(int zoom, int x, int y, BitmapFactory.Options opts,
            DownloadErrorCallback callback) {
        return this.loadTile(zoom, x, y, opts, false, null, callback);
    }

    public Bitmap loadTile(int zoom, int x, int y, BitmapFactory.Options opts,
            boolean cacheOnly,
            long[] expirationPtr,
            DownloadErrorCallback callback) {

        try {
            Throwable[] error = null;
            if(callback != null)
                error = new Throwable[1];
            Bitmap retval = this.impl.getTile(zoom, x, y, error);
            if(callback != null && error[0] != null)
                callback.tileDownloadError(zoom, x, y, error[0]);
            if(retval != null && expirationPtr != null)
                expirationPtr[0] = this.impl.offlineCache.getTileExpiration(zoom, x, y);
            return retval;
        } catch(Throwable t) {
            if(callback != null)
                callback.tileDownloadError(zoom, x, y, t);
            return null;
        }
    }

    public boolean cacheTile(int zoom, int x, int y, DownloadErrorCallback callback) {
        return this.cacheTile(zoom, x, y, false, callback);
    }

    public boolean cacheTile(int zoom, int x, int y, boolean ignoreExpiration, DownloadErrorCallback callback) {
        final Long tileIndex = Long.valueOf(OSMUtils.getOSMDroidSQLiteIndex(zoom, x, y));

        // check for cache entry
        synchronized(this) {
            if(this.impl.offlineCache == null)
                return false;
            if(this.offlineMode)
                return false;
            if(this.mapSource == null)
                return false;

            // if we are attempting to cache the tile in another thread, return
            if(this.pendingCacheUpdates.contains(tileIndex))
                return false;

            final long expiration = this.checkTileExpiration(zoom, x, y);
            // if the cache entry is valid, return
            if(!ignoreExpiration && expiration >= System.currentTimeMillis())
                return true;
            this.pendingCacheUpdates.add(tileIndex);
        }

        // download tile
        final MobacMapTile tile;
        try {
            final BitmapFactory.Options cacheOpts = new BitmapFactory.Options();
            cacheOpts.inJustDecodeBounds = true;
            tile = this.mapSource.loadTile(zoom, x, y, cacheOpts);
        } catch(Throwable t) {
            synchronized(this) {
                this.pendingCacheUpdates.remove(tileIndex);
            }
            if(callback != null)
                callback.tileDownloadError(zoom, x, y, t);
            return false;
        }
        
        // update the cache
        synchronized(this) {
            if(this.impl.offlineCache == null)
                return false;
            if(this.mapSource == null)
                return false;
            
            try {
                long expiration;
                if (tile.expiration < (System.currentTimeMillis() + ONE_WEEK_MILLIS))
                    expiration = (System.currentTimeMillis() + ONE_WEEK_MILLIS);
                else
                    expiration = tile.expiration;

                this.impl.offlineCache.setTile(zoom, x, y, tile.data, expiration);

            } finally {
                this.pendingCacheUpdates.remove(tileIndex);
            }
        }
        
        return true;
    }
    
    public long checkTileExpiration(int zoom, int x, int y) {
        // query cache for expiration of target tile
        if (this.impl.offlineCache != null) {
            try {
                return this.impl.offlineCache.getTileExpiration(zoom, x, y);
            } catch (Exception e) {
                Log.e(TAG, "Offline cache catalog query failed, "
                        + this.mapSource.getName() + " (" + zoom + ", " + x
                        + ", " + y + ")", e);
            }
        }
        
        return -1L;
    }
    
    public static long getTileDownloadTime(long tileExpiration) {
        return tileExpiration-ONE_WEEK_MILLIS;
    }

    /**************************************************************************/

    public static interface DownloadErrorCallback {
        public void tileDownloadError(int zoom, int x, int y, Throwable error);
    }    
}
