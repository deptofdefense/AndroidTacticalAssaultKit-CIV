
package com.atakmap.android.tilecapture;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.atakmap.android.data.URIScheme;
import com.atakmap.android.layers.RasterUtils;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.graphics.GLCapture;
import com.atakmap.android.tilecapture.imagery.ImageryCaptureTask;
import com.atakmap.android.tilecapture.reader.BitmapReader;
import com.atakmap.android.tilecapture.reader.DatasetTileReader;
import com.atakmap.android.tilecapture.reader.GdalTileReader;
import com.atakmap.android.tilecapture.reader.MobileTileReader;
import com.atakmap.android.tilecapture.reader.MosaicTileReader;
import com.atakmap.android.tilecapture.reader.MultiLayerTileReader;
import com.atakmap.android.tilecapture.reader.NativeTileReader;
import com.atakmap.android.tilecapture.reader.TileBitmap;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.DefaultDatasetProjection2;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.layer.raster.MosaicDatasetDescriptor;
import com.atakmap.map.layer.raster.mobac.MobacMapSource;
import com.atakmap.map.layer.raster.mobac.MobacMapSourceFactory;
import com.atakmap.map.layer.raster.mobac.MobacTileClient2;
import com.atakmap.map.layer.raster.mobileimagery.MobileImageryRasterLayer2;
import com.atakmap.map.projection.Projection;
import com.atakmap.math.PointD;
import com.atakmap.util.ConfigOptions;

import org.gdal.gdal.Dataset;
import org.gdal.gdalconst.gdalconst;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads tiles from a mobile or local tileset with the intent of capturing
 * each tile to a file
 *
 * Use with {@link ImageryCaptureTask} for asynchronous capture of map imagery
 */
public class TileCapture extends DatasetTileReader {

    private static final String TAG = "TileCapture";

    /**
     * Callback fired when capturing tiles
     */
    public interface Callback {

        /**
         * Capture process started - this is where you would create your
         * destination file based on the arguments provided
         * @param numTiles The total number of tiles being captured
         * @param tileWidth Tile width in pixels
         * @param tileHeight Tile height in pixels
         * @param fullWidth Full capture width in pixels
         * @param fullHeight Full capture height in pixels
         * @return True to continue, false to stop
         */
        boolean onStartCapture(int numTiles, int tileWidth, int tileHeight,
                int fullWidth, int fullHeight);

        /**
         * Tile has been captured
         * @param tile Tile bitmap
         * @param tileNum The tile number (out of the total number of tiles)
         * @param tileColumn Tile column
         * @param tileRow Tile row
         * @return True to continue, false to stop
         */
        boolean onCaptureTile(Bitmap tile, int tileNum, int tileColumn,
                int tileRow);
    }

    /**
     * Create a tile reader given an image dataset descriptor
     *
     * @param info Image dataset descriptor
     * @return Tile capture instance or null if failed
     */
    public static TileCapture create(ImageDatasetDescriptor info) {
        if (info == null)
            return null;
        TileCapture tc;
        try {
            DatasetTileReader r = createTileReader(info);
            if (r == null)
                return null;

            int srid = info.getSpatialReferenceID();
            if (srid == -1)
                srid = 4326; // Default

            tc = new TileCapture(r);
            tc._srid = srid;

            GeoPoint ll = info.getLowerLeft();
            GeoPoint ur = info.getUpperRight();
            GeoPoint ul = info.getUpperLeft();
            GeoPoint lr = info.getLowerRight();

            if (info.getProvider().equals("mobac")) {
                long width = (info.getWidth() & 0xFFFFFFFFL)
                        * (1L << (long) tc._levelOffset);
                long height = (info.getHeight() & 0xFFFFFFFFL)
                        * (1L << (long) tc._levelOffset);
                tc._imprecise = new DefaultDatasetProjection2(
                        srid, width, height,
                        ul, ur, lr, ll);
            } else
                tc._imprecise = new DefaultProjection(srid);

            // GSD
            tc._gsd = info.getMaxResolution(null);
            if (Double.isNaN(tc._gsd))
                tc._gsd = DatasetDescriptor.computeGSD(info.getWidth(),
                        info.getHeight(), ul, ur, lr, ll);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse tile parameters: " + info.getName(), e);
            return null;
        }
        return tc;
    }

    /**
     * Create a tile capture instance given a mosaic dataset descriptor
     *
     * @param info Mosaic dataset descriptor
     * @param bounds Geo bounds of the query area
     * @return Tile capture instance or null if failed
     */
    public static TileCapture create(MosaicDatasetDescriptor info,
            GeoBounds bounds) {
        TileCapture tc;
        try {
            MosaicTileReader reader = new MosaicTileReader(info, bounds);
            tc = new TileCapture(reader);
            tc._srid = info.getSpatialReferenceID();
            tc._imprecise = new DefaultProjection(tc._srid);
            tc._gsd = reader.getMaxResolution();
            return tc;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse tile parameters: " + info.getName(), e);
            return null;
        }
    }

    /**
     * Create a tile capture instance given a set of geobounds
     * @param bounds Bounds to query
     * @return Tile capture instance or null if failed
     */
    public static TileCapture create(GeoBounds bounds) {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return null;

        // Create tile capture instances for all applicable layers
        TileCapture lastCap = null;
        List<TileCapture> captures = new ArrayList<>();
        List<DatasetDescriptor> datasets = RasterUtils.queryDatasets(bounds,
                true);
        for (DatasetDescriptor info : datasets) {

            TileCapture tc = null;

            if (info instanceof ImageDatasetDescriptor) {
                // Single tile reader instance
                ImageDatasetDescriptor img = (ImageDatasetDescriptor) info;
                tc = create(img);
                if (tc == null || tc.tooSmall(img, bounds))
                    continue;
            } else if (info instanceof MosaicDatasetDescriptor) {
                // Mosaic is made up of multiple files
                tc = create((MosaicDatasetDescriptor) info, bounds);
            }

            if (tc == null || lastCap != null && !lastCap.compatible(tc))
                continue;

            captures.add(tc);
            lastCap = tc;
        }

        // No layers - fallback to basemap
        if (captures.isEmpty())
            return createBasemapReader(mv);

        // If there's only one layer then use it
        else if (captures.size() == 1)
            return captures.get(0);

        // Multi-layer reader
        List<BitmapReader> readers = new ArrayList<>();
        for (TileCapture tc : captures)
            readers.add(tc.getReader());
        return new TileCapture(new MultiLayerTileReader(readers),
                captures.get(0));
    }

    public static TileCapture createBasemapReader(MapView mv) {
        Drawable dr = mv.getResources().getDrawable(R.drawable.worldmap_4326);
        if (!(dr instanceof BitmapDrawable))
            return null;

        Bitmap bmp = ((BitmapDrawable) dr).getBitmap();
        if (bmp == null)
            return null;

        File bmFile = new File(FileSystemUtils.getItem(
                FileSystemUtils.TMP_DIRECTORY), "worldmap_4326.png");
        if (!IOProviderFactory.exists(bmFile)) {
            try {
                GLCapture.compress(bmp, 100, Bitmap.CompressFormat.PNG,
                        bmFile, false);
            } catch (Exception e) {
                Log.e(TAG, "Failed to save basemap to " + bmFile, e);
                return null;
            }
        }

        GeoPoint[] corners = {
                new GeoPoint(90, -180), new GeoPoint(90, 180),
                new GeoPoint(-90, 180), new GeoPoint(-90, -180)
        };

        Dataset ds = GdalLibrary.openDatasetFromFile(bmFile);
        if (ds == null) {
            Log.e(TAG, "gdal.Open failed " + bmFile);
            return null;
        }
        TileCapture tc = new TileCapture(new GdalTileReader(4326, corners,
                ds));
        tc._srid = 4326;
        tc._imprecise = new DefaultProjection(4326);
        tc._gsd = DatasetDescriptor.computeGSD(bmp.getWidth(), bmp.getHeight(),
                corners[0], corners[1], corners[2], corners[3]);
        return tc;
    }

    private final BitmapReader _reader;

    private DatasetProjection2 _imprecise;
    private final double _levelTransitionAdj;
    private double _gsd;
    private int _srid;
    private final Vector2D[] _tmpVec = new Vector2D[] {
            new Vector2D(0, 0), new Vector2D(0, 0), new Vector2D(0, 0)
    };

    public TileCapture(DatasetTileReader r) {
        super(r.getLevelOffset(), r.getMaxLevels(),
                r.getTileWidth(), r.getTileHeight());
        _reader = r;
        _levelTransitionAdj = 0.5d - ConfigOptions.getOption(
                "imagery.relative-scale", 0d);
    }

    public TileCapture(BitmapReader reader, int srid,
            DatasetProjection2 imprecise,
            double levelTransitionAdj, double gsd,
            int tileWidth, int tileHeight,
            int maxLevels, int levelOffset) {
        super(levelOffset, maxLevels, tileWidth, tileHeight);
        _reader = reader;
        _imprecise = imprecise;
        _levelTransitionAdj = levelTransitionAdj;
        _gsd = gsd;
        _srid = srid;
    }

    public TileCapture(BitmapReader reader, TileCapture other) {
        this(reader, other._srid, other._imprecise, other._levelTransitionAdj,
                other._gsd, other._tileWidth, other._tileHeight,
                other._maxLevels, other._levelOffset);
    }

    @Override
    public void dispose() {
        _reader.dispose();
    }

    public BitmapReader getReader() {
        return _reader;
    }

    public DatasetProjection2 getProjection() {
        return _imprecise;
    }

    /**
     * Check if a tile capture is compatible with another, in such a way that
     * tiles can be read from either properly
     * @param tc Tile capture
     * @return True if compatible
     */
    public boolean compatible(TileCapture tc) {
        int maxLvl1 = getMaxLevels() + getLevelOffset();
        int maxLvl2 = tc.getMaxLevels() + tc.getLevelOffset();
        return Double.compare(_levelTransitionAdj, tc._levelTransitionAdj) == 0
                && Double.compare(_gsd, tc._gsd) == 0
                && getTileWidth() == tc.getTileWidth()
                && getTileHeight() == tc.getTileHeight()
                && _srid == tc._srid
                && maxLvl1 == maxLvl2;
    }

    public boolean tooSmall(ImageDatasetDescriptor info, GeoBounds b) {
        GeoPoint[] fullPts = {
                new GeoPoint(b.getNorth(), b.getWest()),
                new GeoPoint(b.getNorth(), b.getEast()),
                new GeoPoint(b.getSouth(), b.getEast()),
                new GeoPoint(b.getSouth(), b.getWest())
        };
        GeoPoint[] infoPts = {
                info.getUpperLeft(), info.getUpperRight(),
                info.getLowerRight(), info.getLowerLeft()
        };
        PointD pt = new PointD(0, 0);
        PointD infoMin = new PointD(Double.MAX_VALUE, Double.MAX_VALUE);
        PointD infoMax = new PointD(-Double.MAX_VALUE, -Double.MAX_VALUE);
        PointD fullMin = new PointD(Double.MAX_VALUE, Double.MAX_VALUE);
        PointD fullMax = new PointD(-Double.MAX_VALUE, -Double.MAX_VALUE);
        for (int i = 0; i < 4; i++) {
            _imprecise.groundToImage(infoPts[i], pt);
            infoMin.x = Math.min(infoMin.x, pt.x);
            infoMin.y = Math.min(infoMin.y, pt.y);
            infoMax.x = Math.max(infoMax.x, pt.x);
            infoMax.y = Math.max(infoMax.y, pt.y);
            _imprecise.groundToImage(fullPts[i], pt);
            fullMin.x = Math.min(fullMin.x, pt.x);
            fullMin.y = Math.min(fullMin.y, pt.y);
            fullMax.x = Math.max(fullMax.x, pt.x);
            fullMax.y = Math.max(fullMax.y, pt.y);
        }
        double infoWidth = infoMax.x - infoMin.x;
        double infoHeight = infoMax.y - infoMin.y;
        double fullWidth = fullMax.x - fullMin.x;
        double fullHeight = fullMax.y - fullMin.y;
        double widthScale = infoWidth / fullWidth;
        double heightScale = infoHeight / fullHeight;
        return widthScale < 0.1 || heightScale < 0.1;
    }

    /**
     * Begin capturing map tiles
     * @param params Tile capture parameters
     * @param cb Callback
     */
    public void capture(TileCaptureParams params, Callback cb) {
        if (cb == null || FileSystemUtils.isEmpty(params.points))
            return;

        // Calculate level based on GSD, map resolution, and capture resolution
        int level = getLevel(params);

        // Calculate capture bounds
        int minCol = Integer.MAX_VALUE;
        int minRow = Integer.MAX_VALUE;
        int maxCol = -Integer.MAX_VALUE;
        int maxRow = -Integer.MAX_VALUE;
        PointD pd = new PointD(0, 0);
        Point cr = new Point(0, 0);
        Vector2D[] vPoints = new Vector2D[params.points.length];
        int lastIdx = vPoints.length - 1;
        for (int i = 0; i < params.points.length; i++) {
            _imprecise.groundToImage(params.points[i], pd);
            vPoints[i] = new Vector2D(pd.x, pd.y);
            getTilePoint(level, pd, cr);
            minCol = Math.min(cr.x, minCol);
            maxCol = Math.max(cr.x, maxCol);
            minRow = Math.min(cr.y, minRow);
            maxRow = Math.max(cr.y, maxRow);
        }

        // Check which tiles intersect the segment/shape
        PointD scrPoint = new PointD(0, 0);
        List<TilePoint> tiles = new ArrayList<>();
        Vector2D[] vTile = new Vector2D[4];
        for (int r = minRow; r <= maxRow; r++) {
            for (int c = minCol; c <= maxCol; c++) {

                // Tile quad
                getSourcePoint(level, c, r, scrPoint);
                vTile[0] = new Vector2D(scrPoint.x, scrPoint.y);
                getSourcePoint(level, c + 1, r, scrPoint);
                vTile[1] = new Vector2D(scrPoint.x, scrPoint.y);
                getSourcePoint(level, c + 1, r + 1, scrPoint);
                vTile[2] = new Vector2D(scrPoint.x, scrPoint.y);
                getSourcePoint(level, c, r + 1, scrPoint);
                vTile[3] = new Vector2D(scrPoint.x, scrPoint.y);

                boolean add = Vector2D.polygonContainsPoint(vPoints[0], vTile);
                if (!add) {
                    outer: for (int i = 0; i < 4; i++) {
                        Vector2D s = vTile[i];
                        Vector2D e = vTile[i == 3 ? 0 : i + 1];
                        for (int j = 0; j < vPoints.length; j++) {
                            if (!params.closedPoints && j == lastIdx)
                                break;
                            Vector2D sp = vPoints[j];
                            Vector2D se = vPoints[j == lastIdx ? 0 : j + 1];
                            if (segmentIntersects(s, e, sp, se)) {
                                add = true;
                                break outer;
                            }
                        }
                    }
                    if (!add && params.closedPoints)
                        add = Vector2D.polygonContainsPoint(vTile[0], vPoints);
                }
                if (add)
                    tiles.add(new TilePoint(r, c));
            }
        }

        // The bounds are completely inside a tile - include that one tile
        if (tiles.isEmpty() && minRow == maxRow && minCol == maxCol)
            tiles.add(new TilePoint(minRow, minCol));

        // Capture tiles
        int fw = _tileWidth * ((maxCol - minCol) + 1);
        int fh = _tileHeight * ((maxRow - minRow) + 1);
        if (!cb.onStartCapture(tiles.size(), _tileWidth, _tileHeight, fw, fh))
            return;

        // Check if this SRID corresponds to a world projection
        // If it doesn't, we don't need to clamp to IDL
        boolean worldProjection = _srid == 4326 || _srid == 3857
                || _srid == 900913 || _srid == 90094326;

        int eastLimit = 0, westLimit = 0, unwrap = 0;
        if (worldProjection) {
            // Get tile extents
            _imprecise.groundToImage(new GeoPoint(0, 180), pd);
            getTilePoint(level, pd, cr);
            eastLimit = cr.x;

            _imprecise.groundToImage(new GeoPoint(0, -180), pd);
            getTilePoint(level, pd, cr);
            westLimit = cr.x;

            unwrap = (eastLimit - westLimit) + 1;
        }

        int maxLevels = getMaxLevels() + getLevelOffset();
        for (int i = 0; i < tiles.size(); i++) {
            TilePoint tp = tiles.get(i);
            int col = tp.c;
            if (worldProjection) {
                // Unwrap column overflow
                if (col < westLimit)
                    col += unwrap;
                else if (col > eastLimit)
                    col -= unwrap;
            }
            TileBitmap tile = getTile(maxLevels - level - 1, col, tp.r);
            if (tile == null || tile.bmp == null)
                Log.w(TAG, "Tile at " + tp.c + ", " + tp.r + " is null");
            Bitmap bmp = tile != null ? tile.bmp : null;
            if (!cb.onCaptureTile(bmp, i, tp.c - minCol, tp.r - minRow))
                return;
        }
    }

    private boolean segmentIntersects(Vector2D seg10, Vector2D seg11,
            Vector2D seg01, Vector2D seg00) {
        _tmpVec[0].set(seg01.x - seg00.x, seg01.y - seg00.y);
        _tmpVec[1].set(seg11.x - seg10.x, seg11.y - seg10.y);
        double c1 = _tmpVec[1].cross(_tmpVec[0]);
        if (c1 != 0d) {
            _tmpVec[2].set(seg00.x - seg10.x, seg00.y - seg10.y);
            double t = _tmpVec[2].cross(_tmpVec[0]) / c1;
            double u = _tmpVec[2].cross(_tmpVec[1]) / c1;
            return t >= 0 && t <= 1 && u >= 0 && u <= 1;
        }
        return false;
    }

    @Override
    public TileBitmap getTile(int level, int column, int row) {
        try {
            return _reader.getTile(level, column, row);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get tile at " + level + ", " + column + ", "
                    + row, e);
            return null;
        }
    }

    /**
     * Tile column and row
     */
    private static class TilePoint {
        int r, c;

        TilePoint(int row, int column) {
            this.r = row;
            this.c = column;
        }
    }

    /**
     * Calculate level given a quad and image size
     * @param quad Geopoint quad (clockwise starting from upper-left point)
     * @param minDim Minimum image size
     * @param captureRes Capture resolution
     * @return Level
     */
    public int calculateLevel(GeoPoint[] quad, int minDim, int captureRes) {
        if (quad == null || quad.length != 4)
            return 0;

        // Compute GSD for the quad and divide by capture resolution
        double gsd = DatasetDescriptor.computeGSD(minDim, minDim,
                quad[0], quad[1], quad[2], quad[3]);
        double scale = _gsd / (gsd / captureRes);
        double level = Math.max((Math.log(1.0d / scale)
                / Math.log(2.0)) + _levelTransitionAdj, 0d);

        return (int) Math.ceil(level);
    }

    public int getLevel(TileCaptureParams params) {
        if (params.level != -1)
            return params.level;
        double scale = _gsd / (params.mapResolution / params.captureResolution);
        return (int) Math.ceil(Math.max((Math.log(1.0d / scale)
                / Math.log(2.0)) + _levelTransitionAdj, 0d));
    }

    @Override
    public void getTilePoint(int level, PointD src, Point dst) {
        _reader.getTilePoint(level, src, dst);
    }

    @Override
    public void getSourcePoint(int level, int column, int row, PointD dst) {
        _reader.getSourcePoint(level, column, row, dst);
    }

    /**
     * Generate capture boundaries and matrices for a set of imagery
     * @param params Capture parameters
     * @return Capture bounds + correction matrix (if applicable)
     */
    public TileCaptureBounds getBounds(TileCaptureParams params) {

        // Get the projected min/max for the input set of points
        PointD min = new PointD(Double.MAX_VALUE, Double.MAX_VALUE);
        PointD max = new PointD(-Double.MAX_VALUE, -Double.MAX_VALUE);
        PointD[] src = new PointD[params.points.length];
        for (int i = 0; i < src.length; i++) {
            GeoPoint gp = params.points[i];
            src[i] = new PointD(0, 0, 0);
            _imprecise.groundToImage(gp, src[i]);
            src[i].z = 0; // Ignore Z
            min.x = Math.min(src[i].x, min.x);
            min.y = Math.min(src[i].y, min.y);
            max.x = Math.max(src[i].x, max.x);
            max.y = Math.max(src[i].y, max.y);
        }

        // Find the tile-aligned min/max
        PointD[] dst = new PointD[] {
                min, new PointD(max.x, min.y), max, new PointD(min.x, max.y)
        };
        GeoPoint[] dstPoints = new GeoPoint[4];
        Point minTP = new Point(Integer.MAX_VALUE, Integer.MAX_VALUE);
        Point maxTP = new Point(-Integer.MAX_VALUE, -Integer.MAX_VALUE);
        int level = getLevel(params);
        double north = Long.MAX_VALUE;
        double east = -Long.MAX_VALUE;
        double south = -Long.MAX_VALUE;
        double west = Long.MAX_VALUE;
        Point cr = new Point(0, 0);
        for (int i = 0; i < 4; i++) {
            getTilePoint(level, dst[i], cr);
            if (i == 1 || i == 2)
                cr.x += 1;
            if (i == 2 || i == 3)
                cr.y += 1;

            minTP.x = Math.min(minTP.x, cr.x);
            minTP.y = Math.min(minTP.y, cr.y);
            maxTP.x = Math.max(maxTP.x, cr.x);
            maxTP.y = Math.max(maxTP.y, cr.y);
            getSourcePoint(level, cr.x, cr.y, dst[i]);

            south = Math.max(dst[i].y, south);
            east = Math.max(dst[i].x, east);
            north = Math.min(dst[i].y, north);
            west = Math.min(dst[i].x, west);

            dstPoints[i] = GeoPoint.createMutable();
            _imprecise.imageToGround(dst[i], dstPoints[i]);
            dstPoints[i].set(Double.NaN); // Altitude should be undefined
        }

        // Create return value
        TileCaptureBounds b = new TileCaptureBounds(
                GeoBounds.createFromPoints(dstPoints, true));
        b.northImageBound = north;
        b.eastImageBound = east;
        b.southImageBound = south;
        b.westImageBound = west;
        b.imageWidth = b.tileImageWidth = (maxTP.x - minTP.x) * _tileWidth;
        b.imageHeight = b.tileImageHeight = (maxTP.y - minTP.y) * _tileHeight;

        // Create a projection matrix which maps imagery points to the quad bitmap
        // Assumes the source quad is in clockwise order starting from the top-left
        if (params.fitToQuad && src.length == 4) {

            // Normalize source and destination quads
            double startX = dst[0].x;
            double startY = dst[0].y;
            for (int i = 0; i < 4; i++) {
                src[i].x -= startX;
                src[i].y -= startY;
                dst[i].x -= startX;
                dst[i].y -= startY;
            }

            // Calculate the horizontal and vertical span of the projected area
            double mapWidth = Math.abs(dst[0].x - dst[1].x);
            double mapHeight = Math.abs(dst[1].y - dst[2].y);
            double sx = b.imageWidth / mapWidth;
            double sy = b.imageHeight / mapHeight;

            // Correct the aspect ratio if necessary
            // The purpose of this code is to make sure the output image width
            // and height match the desired output aspect ratio. This is why
            // the input aspect ratio should be calculated using the image
            // dimensions, NOT the map dimensions.
            double imgAR = (double) b.imageWidth / b.imageHeight;
            double sar = params.fitAspect / imgAR;
            b.imageWidth *= sar;

            // Check if the capture needs to be uniformly up-scaled
            double ms = 1;
            int minorDimen = Math.min(b.imageWidth, b.imageHeight);
            if (minorDimen < params.minImageSize) {
                ms = (double) params.minImageSize / minorDimen;
                b.imageWidth *= ms;
                b.imageHeight *= ms;
            }

            // Imagery -> bitmap projection
            // also convert points to floats for the matrix
            for (int i = 0; i < 4; i++) {
                src[i].x *= sx;
                src[i].y *= sy;
                dst[i].x *= sx * sar * ms;
                dst[i].y *= sy * ms;
            }

            // Build the correction matrix for tiles
            b.tileToPixel.setPolyToPoly(toFlt(src), 0, toFlt(dst), 0, 4);
        }

        return b;
    }

    private static float[] toFlt(PointD[] points) {
        float[] ret = new float[points.length * 2];
        for (int i = 0; i < points.length; i++) {
            int j = i * 2;
            ret[j] = (float) points[i].x;
            ret[j + 1] = (float) points[i].y;
        }
        return ret;
    }

    public static DatasetTileReader createTileReader(
            ImageDatasetDescriptor info) {
        try {
            if (info.getProvider().equals("mobac")) {
                MobacMapSource src = MobacMapSourceFactory.create(
                        new File(info.getUri()), new MobacMapSource.Config());
                if (src == null)
                    return null;

                // Path where offline tile cache is stored
                String cacheUri = info.getExtraData("offlineCache");

                return new MobileTileReader(info, new MobacTileClient2(
                        src, cacheUri));
            } else {
                String path = info.getUri();
                if (path.startsWith(URIScheme.FILE))
                    path = path.substring(URIScheme.FILE.length())
                            .replace("%20", " ")
                            .replace("%23", "#");
                else if (path.startsWith(URIScheme.ZIP))
                    path = path.replace(URIScheme.ZIP, "/vsizip/");
                Dataset ds = GdalLibrary.openDatasetFromFile(new File(path),
                        gdalconst.GA_ReadOnly);
                return ds != null ? new GdalTileReader(info, ds)
                        : new NativeTileReader(info);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load tile source: " + info.getUri(), e);
        }
        return null;
    }

    public static class DefaultProjection implements DatasetProjection2 {

        private final Projection _proj;
        private final PointD _origin;

        public DefaultProjection(Projection projection) {
            _proj = projection;
            _origin = _proj.forward(new GeoPoint(_proj.getMaxLatitude(),
                    _proj.getMinLongitude()), null);
        }

        public DefaultProjection(int srid) {
            this(getProjection(srid));
        }

        @Override
        public boolean imageToGround(PointD image, GeoPoint ground) {
            PointD p = new PointD(image.x + _origin.x, _origin.y - image.y);
            _proj.inverse(p, ground);
            return true;
        }

        @Override
        public boolean groundToImage(GeoPoint ground, PointD image) {
            _proj.forward(ground, image);
            image.x = image.x - _origin.x;
            image.y = _origin.y - image.y;
            return true;
        }

        @Override
        public void release() {
        }

        private static Projection getProjection(int srid) {
            // Use proper SRID for WGS-84 due to exception when calling forward
            if (srid > 32600 && srid <= 32660 || srid > 32700 && srid <= 32760)
                srid = 3857;

            return MobileImageryRasterLayer2.getProjection(srid);
        }
    }
}
