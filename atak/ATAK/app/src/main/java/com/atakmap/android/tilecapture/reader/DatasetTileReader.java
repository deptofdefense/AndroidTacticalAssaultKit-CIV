
package com.atakmap.android.tilecapture.reader;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;

import com.atakmap.android.math.MathUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.math.PointD;

public abstract class DatasetTileReader implements BitmapReader {

    private static final String TAG = "DatasetTileReader";

    protected int _levelOffset, _maxLevels;
    protected int _tileWidth, _tileHeight;
    protected int[] _pixelBuf;
    protected final Paint _paint = new Paint();

    protected DatasetTileReader() {
        _paint.setFilterBitmap(true);
    }

    protected DatasetTileReader(int levelOffset, int maxLevels, int tw,
            int th) {
        _levelOffset = levelOffset;
        _maxLevels = maxLevels;
        _tileWidth = tw;
        _tileHeight = th;
        _paint.setFilterBitmap(true);
    }

    protected DatasetTileReader(ImageDatasetDescriptor d) {
        this(MathUtils.parseInt(d.getExtraData("levelOffset"), 0),
                MathUtils.parseInt(d.getExtraData("_levelCount"), 1),
                MathUtils.parseInt(d.getExtraData("_tilePixelWidth"), 256),
                MathUtils.parseInt(d.getExtraData("_tilePixelHeight"), 256));
    }

    public int getTileWidth() {
        return _tileWidth;
    }

    public int getTileHeight() {
        return _tileHeight;
    }

    public int getLevelOffset() {
        return _levelOffset;
    }

    public int getMaxLevels() {
        return _maxLevels;
    }

    @Override
    public TileBitmap getTile(int level, int col, int row) {
        TileBitmap tile = getTileImpl(level, col, row);
        if (isEmpty(tile)) {
            // Tile data not found - check if the tile is covered at a
            // lower resolution and pull from that
            PointD dstPt = new PointD(0, 0);
            int lvl = (_maxLevels + _levelOffset - 1) - level;
            getSourcePoint(lvl, col, row, dstPt);
            int lvl2 = lvl + 1;
            if (lvl2 >= _maxLevels)
                return null; // Max level exceeded - no tile found

            // Attempt to get the lower res tile
            Point src = new Point(0, 0);
            getTilePoint(lvl2, dstPt, src);
            tile = getTile(level - 1, src.x, src.y);
            if (tile == null)
                return null;

            // Scale the lower res tile up so it matches the coverage
            // of the requested tile
            Point dst = new Point(src);
            getSourcePoint(lvl2, dst.x, dst.y, dstPt);
            getTilePoint(lvl, dstPt, dst);

            Bitmap dstBmp = Bitmap.createBitmap(_tileWidth,
                    _tileHeight, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(dstBmp);
            c.scale(2, 2);

            // Translate into place
            int trX = col - dst.x;
            int trY = row - dst.y;

            if (trX < 0 || trX > 1 || trY < 0 || trY > 1)
                Log.w(TAG, "Out of bounds tile: " + level + ", " + col
                        + ", " + row);

            trX *= _tileWidth;
            trY *= _tileHeight;
            c.translate(trX / -2f, trY / -2f);
            c.drawBitmap(tile.bmp, 0, 0, _paint);

            tile = new TileBitmap(dstBmp, tile.level, tile.column, tile.row);
        }
        return tile;
    }

    protected TileBitmap getTileImpl(int level, int col, int row) {
        return null;
    }

    private boolean isEmpty(TileBitmap tile) {
        if (tile == null || tile.bmp == null)
            return true;

        // Quick check for black pixel on the top-left
        if (tile.bmp.getPixel(0, 0) != Color.BLACK)
            return false;

        // Check the rest of the image
        if (_pixelBuf == null)
            _pixelBuf = new int[_tileWidth * _tileHeight];
        tile.bmp.getPixels(_pixelBuf, 0, _tileWidth, 0, 0,
                _tileWidth, _tileHeight);
        for (int p : _pixelBuf) {
            if (p != Color.BLACK)
                return false;
        }
        return true;
    }
}
