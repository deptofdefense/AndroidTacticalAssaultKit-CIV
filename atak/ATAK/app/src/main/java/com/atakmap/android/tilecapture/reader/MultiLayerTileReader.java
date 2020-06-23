
package com.atakmap.android.tilecapture.reader;

import android.graphics.Point;

import com.atakmap.math.PointD;

import java.util.List;

/**
 * Tile reader that reads from multiple cross-compatible bitmap readers
 *
 * Compatibility in this case means all readers use the same projection,
 * number of levels, etc. so that they can be interchangeably used for
 * reading in case one reader has insufficient data for a given tile coordinate
 */
public class MultiLayerTileReader implements BitmapReader {

    private final List<BitmapReader> _readers;

    public MultiLayerTileReader(List<BitmapReader> readers) {
        _readers = readers;
    }

    @Override
    public void dispose() {
        for (BitmapReader reader : _readers)
            reader.dispose();
    }

    @Override
    public void getTilePoint(int level, PointD src, Point dst) {
        _readers.get(0).getTilePoint(level, src, dst);
    }

    @Override
    public void getSourcePoint(int level, int col, int row, PointD dst) {
        _readers.get(0).getSourcePoint(level, col, row, dst);
    }

    @Override
    public TileBitmap getTile(int level, int column, int row) {
        TileBitmap ret = null;
        for (BitmapReader reader : _readers) {
            TileBitmap tile = reader.getTile(level, column, row);
            // Use the tile with the highest resolution
            if (ret == null || tile != null && tile.level > ret.level)
                ret = tile;
        }
        return ret;
    }
}
