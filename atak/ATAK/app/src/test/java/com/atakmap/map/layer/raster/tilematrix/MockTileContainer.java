
package com.atakmap.map.layer.raster.tilematrix;

import android.graphics.Bitmap;

import com.atakmap.map.layer.feature.geometry.Envelope;

import java.util.HashMap;
import java.util.Map;

public final class MockTileContainer extends MockTileMatrix
        implements TileContainer {
    final boolean readOnly;
    final Map<String, Long> tileExpirations = new HashMap<>();

    public MockTileContainer(String name, int srid, ZoomLevel[] levels,
            double originX, double originY, Envelope bounds, boolean readOnly) {
        super(name, srid, levels, originX, originY, bounds);

        this.readOnly = readOnly;
    }

    @Override
    public final boolean isReadOnly() {
        return this.readOnly;
    }

    @Override
    public void setTile(int level, int x, int y, byte[] data, long expiration) {
        setTileData(level, x, y, data);
        tileExpirations.put(getTileKey(level, x, y), expiration);
    }

    @Override
    public void setTile(int level, int x, int y, Bitmap data, long expiration)
            throws TileEncodeException {
        setTile(level, x, y, data);
        tileExpirations.put(getTileKey(level, x, y), expiration);
    }

    @Override
    public boolean hasTileExpirationMetadata() {
        return true;
    }

    @Override
    public long getTileExpiration(int level, int x, int y) {
        final Long expiration = tileExpirations.get(getTileKey(level, x, y));
        return (expiration != null) ? expiration : -1L;
    }
}
