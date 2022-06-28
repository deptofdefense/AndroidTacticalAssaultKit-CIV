
package com.atakmap.android.tilecapture.reader;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;

import com.atakmap.android.maps.CardLayer;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.raster.AbstractRasterLayer2;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.MosaicDatasetDescriptor;
import com.atakmap.map.layer.raster.gdal.GdalLayerInfo;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabaseFactory2;
import com.atakmap.math.PointD;

import org.gdal.gdal.Dataset;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * Used for reading bitmap tiles from a {@link MosaicDatabase2}
 * Consists of multiple {@link GdalTileReader} per each dataset in the mosaic
 */
public class MosaicTileReader extends DatasetTileReader {

    private static final String TAG = "MosaicTileReader";
    private static final int TILE_SIZE = 256;
    private static final double EPSILON = 1e-9;

    // Sort tile readers by upper-left position
    private static final Comparator<GdalTileReader> SORT_POS = new Comparator<GdalTileReader>() {
        @Override
        public int compare(GdalTileReader o1, GdalTileReader o2) {
            int xComp = Double.compare(o1._ul.x, o2._ul.x);
            if (xComp == 0)
                return Double.compare(o1._ul.y, o2._ul.y);
            return xComp;
        }
    };

    private final List<GdalTileReader> _readers = new ArrayList<>();
    private final PointD _ul, _lr;
    private final PointD[] _sizes;
    private final Point[][] _offsets;

    private double _maxRes;

    /**
     * Create a new tile reader for a imagery source that is GDAL-compatible
     *
     * @param info Dataset descriptor
     * @param corners Corner points in clockwise order [UL, UR, BR, BL]
     */
    public MosaicTileReader(MosaicDatasetDescriptor info, GeoPoint[] corners)
            throws IllegalArgumentException {

        _paint.setFilterBitmap(true);
        _paint.setAntiAlias(true);

        // Need the map view for layer lookup
        final MapView mv = MapView.getMapView();
        if (mv == null)
            throw new IllegalArgumentException("Failed to get map view");

        // Find the raster layer in order to find out what types are visible/selected
        AbstractRasterLayer2 layer = null;
        List<Layer> layers = mv.getLayers(MapView.RenderStack.MAP_LAYERS);
        for (final Layer l : layers) {
            // ensure both conditions are met before assigning the layer
            if (l.getName().equals("Raster Layers") && l instanceof CardLayer) {
                CardLayer cd = (CardLayer) l;
                layer = (AbstractRasterLayer2) cd.get();
                break;
            }
        }
        if (layer == null)
            throw new IllegalArgumentException("Failed to find root map layer");

        // Get the selected layer
        String selected = layer.getSelection();

        // Open mosaic database
        String provider = info.getMosaicDatabaseProvider();
        MosaicDatabase2 db = MosaicDatabaseFactory2.create(provider);
        if (db == null)
            throw new IllegalArgumentException(
                    "Failed to create database: " + provider);

        db.open(info.getMosaicDatabaseFile());

        // Determine URI schema
        String baseUri = GdalLayerInfo.getGdalFriendlyUri(info);
        if (baseUri.length() > 0
                && baseUri.charAt(baseUri.length() - 1) == File.separatorChar)
            baseUri = baseUri.substring(0, baseUri.length() - 1);
        if (baseUri.startsWith("file:///"))
            baseUri = baseUri.substring(7);
        else if (baseUri.startsWith("file://"))
            baseUri = baseUri.substring(6);

        boolean relPath = info.getExtraData("relativePaths").equals("true");

        // Query datasets within the given database
        MosaicDatabase2.QueryParameters params = new MosaicDatabase2.QueryParameters();
        params.spatialFilter = DatasetDescriptor.createSimpleCoverage(
                corners[0], corners[1], corners[2], corners[3]);

        // Determine which types should be queried
        if (selected != null) {
            // Only query the selected type
            params.types = Collections.singleton(selected);
        } else {
            // Get all visible types
            params.types = new HashSet<>();
            for (String type : info.getImageryTypes()) {
                if (layer.isVisible(type))
                    params.types.add(type);
            }
        }

        _maxRes = Double.MAX_VALUE;

        try (MosaicDatabase2.Cursor c = db.query(params)) {
            while (c.moveToNext()) {

                // Get absolute path
                String path = c.getPath();
                if (relPath) {
                    path = baseUri + File.separator + path;
                } else {
                    if (path.startsWith("file:///"))
                        path = path.substring(7);
                    else if (path.startsWith("file://"))
                        path = path.substring(6);
                    else if (path.startsWith("zip://"))
                        path = path.replace("zip://", "/vsizip/");
                }

                // Open dataset
                Dataset dataset = GdalLibrary.openDatasetFromPath(path);
                if (dataset == null)
                    continue;

                // Create tile reader for dataset
                GdalTileReader reader = new GdalTileReader(c.getSrid(),
                        new GeoPoint[] {
                                c.getUpperLeft(), c.getUpperRight(),
                                c.getLowerRight(), c.getLowerLeft()
                        }, dataset);
                _readers.add(reader);

                // Track the maximum resolution for this type
                _maxRes = Math.min(_maxRes, c.getMaxGSD());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query dataset", e);
        } finally {
            // Close database now that we're done with it
            db.close();
        }

        if (_readers.isEmpty())
            throw new IllegalArgumentException("No datasets found");

        // Sort readers by upper-left position
        Collections.sort(_readers, SORT_POS);

        GdalTileReader first = _readers.get(0);
        _levelOffset = first.getLevelOffset();
        _maxLevels = first.getMaxLevels();
        _tileWidth = first.getTileWidth();
        _tileHeight = first.getTileHeight();

        _ul = new PointD(first._ul);
        _lr = new PointD(first._lr);
        int fullWidth = first._width;
        int fullHeight = first._height;
        PointD[] sizes = first._sizes;

        // Make sure other tile readers have compatible parameters
        // and calculate total imagery size
        for (int i = 1; i < _readers.size(); i++) {
            GdalTileReader r = _readers.get(i);

            if (_maxLevels != r.getMaxLevels()
                    || _levelOffset != r.getLevelOffset()
                    || _tileWidth != r.getTileWidth()
                    || _tileHeight != r.getTileHeight()
                    || r._width != first._width
                    || r._height != first._height
                    || !Arrays.equals(sizes, r._sizes))
                throw new IllegalArgumentException("Incompatible datasets");

            PointD ul = r._ul, lr = r._lr;

            if (ul.x < _ul.x || lr.x > _lr.x) {
                _ul.x = Math.min(_ul.x, ul.x);
                _lr.x = Math.max(_lr.x, lr.x);
                fullWidth += r._width;
            }

            if (ul.y < _ul.y || lr.y > _lr.y) {
                _ul.y = Math.min(_ul.y, ul.y);
                _lr.y = Math.max(_lr.y, lr.y);
                fullHeight += r._height;
            }
        }

        // Compute level sizes
        int level = (int) MathUtils.log2(TILE_SIZE);
        _sizes = new PointD[_maxLevels];
        for (int i = 0; i < _maxLevels; i++) {
            int l2 = 1 << (i + level);
            PointD p = new PointD(0, 0);
            p.x = (double) fullWidth / l2;
            p.y = (double) fullHeight / l2;
            _sizes[i] = p;
        }

        // Compute tile reader offsets
        _offsets = new Point[_readers.size()][_maxLevels];
        for (int i = 0; i < _readers.size(); i++) {
            GdalTileReader r = _readers.get(i);
            for (int l = 0; l < _maxLevels; l++)
                getTilePoint(l, r._ul,
                        _offsets[i][_maxLevels - l - 1] = new Point());
        }
    }

    public MosaicTileReader(MosaicDatasetDescriptor info, GeoBounds bounds) {
        this(info, new GeoPoint[] {
                new GeoPoint(bounds.getNorth(), bounds.getWest()),
                new GeoPoint(bounds.getNorth(), bounds.getEast()),
                new GeoPoint(bounds.getSouth(), bounds.getEast()),
                new GeoPoint(bounds.getSouth(), bounds.getWest())
        });
    }

    @Override
    public void dispose() {
        for (GdalTileReader reader : _readers)
            reader.dispose();
    }

    /**
     * Get the maximum resolution for the datasets in use by this reader
     * @return Maximum resolution (GSD)
     */
    public double getMaxResolution() {
        return _maxRes;
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
    public TileBitmap getTile(int level, int col, int row) {

        // For each reader query the tile bitmap at the corresponding offset
        // relative to the main reader
        Bitmap ret = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE,
                Bitmap.Config.ARGB_8888);
        Canvas can = new Canvas(ret);
        for (int i = 0; i < _readers.size(); i++) {
            GdalTileReader reader = _readers.get(i);
            Point offset = _offsets[i][level];
            TileBitmap tb = reader.getTile(level, col - offset.x,
                    row - offset.y);
            if (tb != null)
                can.drawBitmap(tb.bmp, 0, 0, _paint);
        }

        return new TileBitmap(ret, level, col, row);
    }
}
