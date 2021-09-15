package com.atakmap.map.formats.mapbox;

import android.util.LruCache;

import com.atakmap.map.elevation.ElevationChunk;

final class TerrainRGBCache extends LruCache<Long, ElevationChunk> {
    /**
     * @param maxSize for caches that do not override {@link #sizeOf}, this is
     *                the maximum number of entries in the cache. For all other caches,
     *                this is the maximum sum of the sizes of the entries in this cache.
     */
    public TerrainRGBCache(int maxSize) {
        super(maxSize);
    }

    @Override
    protected int sizeOf(Long key, ElevationChunk value) {
        return (256*256*3);
    }

    @Override
    protected void entryRemoved(boolean evicted, Long key, ElevationChunk oldValue, ElevationChunk newValue) {
        super.entryRemoved(evicted, key, oldValue, newValue);
        oldValue.dispose();
    }
}
