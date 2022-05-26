package com.atakmap.map.layer.raster.controls;

import com.atakmap.coremap.maps.coords.GeoPoint;

public interface TileCacheControl {
    interface OnTileUpdateListener {
        void onTileUpdated(int level, int x, int y);
    }

    /**
     * Requests that the cache queue be prioritized to satisfy tiles nearest
     * to the specified point of interest.
     * @param p
     */
    void prioritize(GeoPoint p);

    /**
     * Requests that any operation to cache the specified tile be aborted. If
     * the specified tile is not currently queued for caching, this call is
     * ignored.
     * @param level
     * @param x
     * @param y
     */
    void abort(int level, int x, int y);
    boolean isQueued(int level, int x, int y);
    void setOnTileUpdateListener(OnTileUpdateListener l);

    /**
     * Any tile with a timestamp less than the expiry time will be refetched
     * when requested.
     * @param expiry    Measured in epoch milliseconds
     */
    void expireTiles(long expiry);
}
