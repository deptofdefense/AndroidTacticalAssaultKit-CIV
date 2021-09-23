
package com.atakmap.android.tilecapture.reader;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;

import com.atakmap.android.math.MathUtils;
import com.atakmap.android.tilecapture.TileCapture.DefaultProjection;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReader.Format;
import com.atakmap.math.PointD;

import org.gdal.gdal.Dataset;

/**
 * Reads bitmap tiles from a GDAL-supported image
 */
public class GdalTileReader extends DatasetTileReader {

    private static final int TILE_SIZE = 256;
    private static final double EPSILON = 1e-9;

    protected final Dataset _dataset;

    // Width and height of the imagery
    protected final int _width, _height;

    // Upper-left and lower-right projection coordinate
    protected final PointD _ul = new PointD(0, 0);
    protected final PointD _lr = new PointD(0, 0);

    // Map tile coordinates to pixel coordinates
    // Typically this is a 1:1 mapping, however if the imagery is rotated
    // or flipped then this matrix is needed to get the correct tile
    private final Matrix _tileToPixel = new Matrix();
    private final Matrix _pixelToTile = new Matrix();

    protected final PointD[] _sizes;
    private final byte[] _bytes;
    private final Paint _paint = new Paint();

    // Underlying reader implementation (same as what the map renderer uses)
    private final com.atakmap.map.layer.raster.gdal.GdalTileReader _reader;
    private final Format _format;

    /**
     * Create a new tile reader for a imagery source that is GDAL-compatible
     *
     * @param srid Spatial reference ID
     * @param corners Corner points in clockwise order [UL, UR, BR, BL]
     * @param dataset GDAL dataset
     */
    public GdalTileReader(int srid, GeoPoint[] corners, Dataset dataset) {
        _dataset = dataset;
        _width = _dataset.getRasterXSize();
        _height = _dataset.getRasterYSize();
        _tileWidth = _tileHeight = TILE_SIZE;
        _paint.setFilterBitmap(true);

        // Calculate the maximum number of levels and the number of columns
        // and rows per level
        int level = (int) MathUtils.log2(TILE_SIZE);
        int maxDim = Math.max(_width, _height);
        _levelOffset = 0;
        _maxLevels = (int) Math.ceil(MathUtils.log2(maxDim) - level);
        _sizes = new PointD[_maxLevels];
        for (int i = 0; i < _maxLevels; i++) {
            int l2 = 1 << (i + level);
            PointD p = new PointD(0, 0);
            p.x = (double) _width / l2;
            p.y = (double) _height / l2;
            _sizes[i] = p;
        }

        // Read buffer
        _bytes = new byte[TILE_SIZE * TILE_SIZE * 4 * 4];

        // Transformation parameters
        if (srid == -1)
            srid = 4326; // Default
        DefaultProjection proj = new DefaultProjection(srid);

        double minLng = Double.MAX_VALUE, minLat = Double.MAX_VALUE;
        double maxLng = -Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        PointD[] src = new PointD[4];
        for (int i = 0; i < corners.length; i++) {
            src[i] = new PointD(0, 0);
            proj.groundToImage(corners[i], src[i]);
            minLng = Math.min(minLng, corners[i].getLongitude());
            minLat = Math.min(minLat, corners[i].getLatitude());
            maxLng = Math.max(maxLng, corners[i].getLongitude());
            maxLat = Math.max(maxLat, corners[i].getLatitude());
        }

        proj.groundToImage(new GeoPoint(maxLat, minLng), _ul);
        proj.groundToImage(new GeoPoint(minLat, maxLng), _lr);

        PointD[] dst = {
                new PointD(_ul), new PointD(_lr.x, _ul.y),
                new PointD(_lr), new PointD(_ul.x, _lr.y)
        };

        double sx = _width / (_lr.x - _ul.x);
        double sy = _height / (_lr.y - _ul.y);
        float[] srcFlt = new float[8], dstFlt = new float[8];
        for (int i = 0; i < 4; i++) {
            int j = i * 2;
            srcFlt[j] = (float) ((src[i].x - _ul.x) * sx);
            srcFlt[j + 1] = (float) ((src[i].y - _ul.y) * sy);
            dstFlt[j] = (float) ((dst[i].x - _ul.x) * sx);
            dstFlt[j + 1] = (float) ((dst[i].y - _ul.y) * sy);
        }

        _tileToPixel.setPolyToPoly(srcFlt, 0, dstFlt, 0, 4);
        _pixelToTile.setPolyToPoly(dstFlt, 0, srcFlt, 0, 4);

        // Underlying tile reader used to properly read all the various
        // image formats GDAL supports
        _reader = new com.atakmap.map.layer.raster.gdal.GdalTileReader(
                _dataset, "", TILE_SIZE, TILE_SIZE, "",
                new TileReader.AsynchronousIO());
        _format = _reader.getFormat();
    }

    public GdalTileReader(ImageDatasetDescriptor info, Dataset dataset) {
        this(info.getSpatialReferenceID(), new GeoPoint[] {
                info.getUpperLeft(),
                info.getUpperRight(),
                info.getLowerRight(),
                info.getLowerLeft()
        }, dataset);
    }

    @Override
    public void dispose() {
        _dataset.delete();
    }

    @Override
    public void getTilePoint(int level, PointD src, Point dst) {
        double x = src.x - _ul.x;
        double y = src.y - _ul.y;
        x /= _lr.x - _ul.x;
        y /= _lr.y - _ul.y;
        PointD s = getSize(level);
        x *= s.x;
        y *= s.y;
        x += EPSILON;
        y += EPSILON;
        dst.x = (int) Math.floor(x);
        dst.y = (int) Math.floor(y);
    }

    @Override
    public void getSourcePoint(int level, int col, int row, PointD dst) {
        PointD s = getSize(level);
        dst.x = (double) col / s.x;
        dst.y = (double) row / s.y;
        dst.x *= _lr.x - _ul.x;
        dst.y *= _lr.y - _ul.y;
        dst.x += _ul.x;
        dst.y += _ul.y;
    }

    private PointD getSize(int level) {
        if (level < 0)
            level = 0;
        else if (level >= _sizes.length)
            level = _sizes.length - 1;
        return _sizes[level];
    }

    @Override
    public TileBitmap getTile(int level, int c, int r) {
        // Out of bounds
        if (c < 0 || r < 0)
            return null;

        // Determine the size of the source tile to read and the x,y offset
        int levelInv = _maxLevels - level - 1;
        int size = TILE_SIZE << levelInv;
        int x = c * size;
        int y = r * size;
        float scaleFactor = (float) size / TILE_SIZE;

        // Out of bounds
        if (x >= _width || y >= _height)
            return null;

        Bitmap bmp;
        if (_tileToPixel.isIdentity()) {
            // Clamp the size to the dimensions of the full image
            int srcW = Math.min(size, _width - x);
            int srcH = Math.min(size, _height - y);
            int dstW = (int) Math.min(TILE_SIZE, srcW / scaleFactor);
            int dstH = (int) Math.min(TILE_SIZE, srcH / scaleFactor);
            if (srcW <= 0 || srcH <= 0)
                return null;

            // Read the bitmap tile out of the image dataset
            _reader.read(x, y, srcW, srcH, dstW, dstH, _bytes);
            bmp = bytesToBitmap(_bytes, dstW, dstH, _format);

            // Pad edge tiles with empty space so they fit the 256x256 tile size
            if (dstW < TILE_SIZE || dstH < TILE_SIZE) {
                Bitmap full = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE,
                        Bitmap.Config.ARGB_8888);
                Canvas can = new Canvas(full);
                can.drawBitmap(bmp, 0, 0, _paint);
                bmp = full;
            }
        } else {
            // Need to transform the tile coordinates to pixel coordinates
            float[] src = {
                    x, y,
                    x + size, y,
                    x + size, y + size,
                    x, y + size
            };
            _tileToPixel.mapPoints(src);

            Point min = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);
            Point max = new Point(-Integer.MAX_VALUE, -Integer.MAX_VALUE);
            for (int i = 0; i < 8; i += 2) {
                min.x = (int) Math.min(min.x, Math.floor(src[i]));
                min.y = (int) Math.min(min.y, Math.floor(src[i + 1]));
                max.x = (int) Math.max(max.x, Math.ceil(src[i]));
                max.y = (int) Math.max(max.y, Math.ceil(src[i + 1]));
            }

            min.x = Math.max(min.x, 0);
            min.y = Math.max(min.y, 0);
            max.x = Math.min(max.x, _width);
            max.y = Math.min(max.y, _height);

            int srcW = max.x - min.x;
            int srcH = max.y - min.y;
            int dstW = (int) (srcW / scaleFactor);
            int dstH = (int) (srcH / scaleFactor);
            if (srcW <= 0 || srcH <= 0)
                return null;

            _reader.read(min.x, min.y, srcW, srcH, dstW, dstH, _bytes);
            bmp = bytesToBitmap(_bytes, dstW, dstH, _format);

            // Transform the pixel-space bitmap back to tile-space
            Matrix m = new Matrix();
            m.postScale(scaleFactor, scaleFactor);
            m.postTranslate(min.x, min.y);
            m.postConcat(_pixelToTile);
            m.postTranslate(-x, -y);
            m.postScale(1f / scaleFactor, 1f / scaleFactor);

            Bitmap crt = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE,
                    Bitmap.Config.ARGB_8888);
            Canvas can = new Canvas(crt);
            can.drawBitmap(bmp, m, _paint);
            bmp = crt;
        }

        return new TileBitmap(bmp, level, c, r);
    }

    private static Bitmap bytesToBitmap(byte[] bytes, int width, int height,
            Format fmt) {
        int[] pixels = new int[width * height];

        switch (fmt) {
            case MONOCHROME:
                getMonoPixels(bytes, pixels);
                break;
            case MONOCHROME_ALPHA:
                getMonoAlphaPixels(bytes, pixels);
                break;
            case RGB:
                getRGBPixels(bytes, pixels);
                break;
            case RGBA:
                getRGBAPixels(bytes, pixels);
                break;
            case ARGB:
                getARGBPixels(bytes, pixels);
                break;
        }

        Bitmap bmp = Bitmap.createBitmap(width, height,
                Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixels, 0, width, 0, 0, width, height);
        return bmp;
    }

    private static int uns(byte b) {
        return b < 0 ? b + 256 : b;
    }

    private static void getMonoPixels(byte[] from, int[] to) {
        for (int i = 0; i < to.length; i++) {
            int v = uns(from[i]);
            to[i] = rgb(v, v, v);
        }
    }

    private static void getMonoAlphaPixels(byte[] from, int[] to) {
        int p = 0;
        for (int i = 0; i < to.length; i++) {
            int v = uns(from[p]);
            to[i] = rgb(v, v, v);
            p += 2;
        }
    }

    private static void getRGBPixels(byte[] from, int[] to) {
        int p = 0;
        for (int i = 0; i < to.length; i++) {
            to[i] = rgb(uns(from[p]), uns(from[p + 1]), uns(from[p + 2]));
            p += 3;
        }
    }

    private static void getRGBAPixels(byte[] from, int[] to) {
        int p = 0;
        for (int i = 0; i < to.length; i++) {
            to[i] = rgb(uns(from[p]), uns(from[p + 1]), uns(from[p + 2]));
            p += 4;
        }
    }

    private static void getARGBPixels(byte[] from, int[] to) {
        int p = 0;
        for (int i = 0; i < to.length; i++) {
            to[i] = rgb(uns(from[p + 1]), uns(from[p + 2]), uns(from[p + 3]));
            p += 4;
        }
    }

    // Faster than Color.rgb
    private static int rgb(int red, int green, int blue) {
        return -16777216 + (red * 65536) + (green * 256) + blue;
    }
}
