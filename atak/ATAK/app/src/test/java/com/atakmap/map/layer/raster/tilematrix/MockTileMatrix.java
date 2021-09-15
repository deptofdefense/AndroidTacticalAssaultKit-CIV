
package com.atakmap.map.layer.raster.tilematrix;

import android.graphics.Bitmap;

import com.atakmap.map.layer.feature.geometry.Envelope;

import java.util.HashMap;
import java.util.Map;

public class MockTileMatrix implements TileMatrix {
    Map<String, Bitmap> tiles = new HashMap<>();
    Map<String, byte[]> tileData = new HashMap<>();
    final String name;
    final int srid;
    final ZoomLevel[] levels;
    final double originX;
    final double originY;
    final Envelope bounds;

    public MockTileMatrix(String name, int srid, ZoomLevel[] levels,
            double originX, double originY, Envelope bounds) {
        this.name = name;
        this.srid = srid;
        this.levels = levels;
        this.originX = originX;
        this.originY = originY;
        this.bounds = bounds;
    }

    void setTile(int zoom, int x, int y, Bitmap tile) {
        tiles.put(getTileKey(zoom, x, y), tile);
    }

    void setTileData(int zoom, int x, int y, byte[] tile) {
        tileData.put(getTileKey(zoom, x, y), tile);
    }

    @Override
    public final String getName() {
        return this.name;
    }

    @Override
    public final int getSRID() {
        return this.srid;
    }

    @Override
    public final ZoomLevel[] getZoomLevel() {
        return this.levels;
    }

    @Override
    public final double getOriginX() {
        return this.originX;
    }

    @Override
    public final double getOriginY() {
        return this.originY;
    }

    @Override
    public final Bitmap getTile(int zoom, int x, int y, Throwable[] error) {
        return tiles.get(getTileKey(zoom, x, y));
    }

    @Override
    public final byte[] getTileData(int zoom, int x, int y, Throwable[] error) {
        return tileData.get(getTileKey(zoom, x, y));
    }

    @Override
    public final Envelope getBounds() {
        return bounds;
    }

    @Override
    public final void dispose() {
    }

    static String getTileKey(int z, int x, int y) {
        return String.valueOf(z) + "/" + String.valueOf(y) + "/"
                + String.valueOf(x);
    }
}
