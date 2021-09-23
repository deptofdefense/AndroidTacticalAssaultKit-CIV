package com.atakmap.map.layer.raster.tilematrix;

import java.io.File;
import java.io.IOException;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.contentservices.CacheRequest;
import com.atakmap.map.contentservices.CacheRequestListener;

public abstract class AbstractTileClient implements TileClient {

    private final static String TAG = "AbstractTileClient";
    
    protected final static long ONE_WEEK_MILLIS = 7L * 24L * 60L * 60L * 1000L;
    
    protected String name;
    protected TileContainer offlineCache;
    protected boolean offlineMode;
    protected long expirationOffset;
    
    protected AbstractTileClient(String name, long expirationOffset) {
        this.name = name;
        this.offlineMode = false;
        this.expirationOffset = expirationOffset;
    }
    
    protected void initOfflineCache(String path, String preferredProvider, boolean forceCreate) {
        if(path == null)
            return;

        // try to open/create the cache from the path
        this.offlineCache = openOrCreateCache(path, this, preferredProvider);

        // if no cache could be opened from the file then attempt to delete
        // the file and create a new cache
        if(forceCreate &&
           this.offlineCache == null &&
           IOProviderFactory.exists(new File(path))) {

            FileSystemUtils.delete(path);
            this.offlineCache = openOrCreateCache(path, this, preferredProvider);
        }
    }

    private static TileContainer openOrCreateCache(String path, TileMatrix spec, String preferredProvider) {
        TileContainer offlineCache = null;
    
        // attempt to open with hint, then without if necessary
        do {
            offlineCache = TileContainerFactory.openOrCreateCompatibleContainer(path, spec, preferredProvider);
            if(preferredProvider == null)
                break;
            preferredProvider = null;
        } while(offlineCache == null);
        
        return offlineCache;
    }

    @Override
    public final String getName() {
        return this.name;
    }

    @Override
    public Bitmap getTile(int zoom, int x, int y, Throwable[] error) {
        byte[] data = getTileData(zoom, x, y, error);
        if(data == null)
            return null;
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    @Override
    public byte[] getTileData(int zoom, int x, int y, Throwable[] error) {
        byte[] retval = null;
        long expiration = -1;
        if(this.offlineCache != null && this.offlineCache.hasTileExpirationMetadata())
            expiration = this.offlineCache.getTileExpiration(zoom, x, y);

        // if data is null or expiration exceeded, load from URL
        if (!this.offlineMode &&
            System.currentTimeMillis() > expiration) {

            try {
                retval = this.getTileDataImpl(zoom, x, y);
                if (retval != null) {
                    expiration = (System.currentTimeMillis() + this.expirationOffset);

                    // update cache with downloaded data
                    if (this.offlineCache != null && !this.offlineCache.isReadOnly()) {
                        this.offlineCache.setTile(zoom, x, y, retval, expiration);
                    }
                }
            } catch (IOException e) {
                Log.e(TAG,
                        "IO Error during tile download, "
                                + this.getName() + " (" + zoom + ", "
                                + x + ", " + y + ")", e);
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

        // either we have an entry with an expiration, we are offline or tile
        // download has failed -- try to read a tile from the cache
        if (retval == null && this.offlineCache != null) {
            try {
                retval = this.offlineCache.getTileData(zoom, x, y, error);
                if(retval != null && error != null)
                    error[0] = null;
            } catch (Exception e) {
                if(error != null)
                    error[0] = e;

                if(error != null && error[0] != null) {
                    Log.e(TAG,
                            "Offline cache tile query failed, "
                                    + this.getName() + " (" + zoom + ", "
                                    + x + ", " + y + ")", error[0]);
                }
            }
        }

        return retval;
    }

    protected abstract byte[] getTileDataImpl(int zoom, int x, int y) throws IOException;

    @Override
    public void dispose() {
        if(this.offlineCache != null) {
            this.offlineCache.dispose();
        }
    }

    @Override
    public void cache(CacheRequest request, CacheRequestListener listener) {
        String preferredProvider = null;
        if(request != null) { 
            preferredProvider = request.preferredContainerProvider;

            TileContainer sink = 
                 TileContainerFactory.openOrCreateCompatibleContainer(request.cacheFile.getAbsolutePath(), this, preferredProvider);
            if(sink == null) {
                Log.e(TAG, "Unable to create tile container for cache request");
                if(listener != null)
                    listener.onRequestError(null, "Unable to create tile container for cache request", true);
                return;
            }
            TileScraper scraper = new TileScraper(this, sink, request, listener);
            scraper.run();
        }
    }
    
    @Override
    public int estimateTileCount(CacheRequest request) {
        return TileScraper.estimateTileCount(this, request);
    }

    /**************************************************************************/
    
    protected void setOfflineMode(boolean offlineOnly) {
        this.offlineMode = offlineOnly;
    }
    
    protected boolean isOfflineOnly() {
        return this.offlineMode;
    }
}
