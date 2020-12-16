
package com.atakmap.android.tilecapture.reader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;

import com.atakmap.android.data.URIScheme;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.QueryIface;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.map.layer.raster.tilematrix.TileMatrix;
import com.atakmap.math.PointD;

import java.io.File;

/**
 * Read bitmap tiles from a native dataset
 */
public class NativeTileReader extends DatasetTileReader {

    private static final String TAG = "NativeTileReader";

    private static final BitmapFactory.Options DECODE_OPTS = new BitmapFactory.Options();
    static {
        DECODE_OPTS.inPreferredConfig = Bitmap.Config.ARGB_8888;
    }

    private final DatabaseIface _db;
    private final TileMatrix.ZoomLevel[] _zoomLevels;

    public NativeTileReader(ImageDatasetDescriptor info) {
        super(info);
        String path = info.getUri();
        if (path.startsWith(URIScheme.FILE))
            path = path.substring(URIScheme.FILE.length());
        _db = IOProviderFactory.createDatabase(new File(path),
                DatabaseInformation.OPTION_READONLY);

        double pixelSize = OSMUtils.mapnikTileResolution(_levelOffset);

        TileMatrix.ZoomLevel minZoom = new TileMatrix.ZoomLevel();
        minZoom.level = _levelOffset;
        minZoom.pixelSizeX = minZoom.pixelSizeY = minZoom.resolution = pixelSize;
        minZoom.tileWidth = _tileWidth;
        minZoom.tileHeight = _tileHeight;

        _zoomLevels = TileMatrix.Util.createQuadtree(minZoom, _maxLevels);
    }

    @Override
    public void dispose() {
        _db.close();
    }

    private TileMatrix.ZoomLevel getZoomLevel(int level) {
        level = _maxLevels - level - 1;
        if (level < 0)
            level = 0;
        if (level >= _zoomLevels.length)
            level = _zoomLevels.length - 1;
        return _zoomLevels[level];
    }

    @Override
    public void getTilePoint(int level, PointD src, Point dst) {
        TileMatrix.ZoomLevel zoom = getZoomLevel(level);
        dst.x = (int) (float) (src.x / (zoom.pixelSizeX * zoom.tileWidth));
        dst.y = (int) (float) (src.y / (zoom.pixelSizeY * zoom.tileHeight));
        if (dst.x < 0)
            dst.x--;
    }

    @Override
    public void getSourcePoint(int level, int col, int row, PointD dst) {
        TileMatrix.ZoomLevel zoom = getZoomLevel(level);
        dst.x = col * zoom.pixelSizeX * zoom.tileWidth;
        dst.y = row * zoom.pixelSizeY * zoom.tileHeight;
    }

    @Override
    protected TileBitmap getTileImpl(int level, int col, int row) {
        QueryIface query = null;
        try {
            // First attempt to pull the tile from the database
            long index = OSMUtils.getOSMDroidSQLiteIndex(level, col, row);
            query = _db
                    .compileQuery("SELECT tile FROM tiles WHERE key=? LIMIT 1");
            query.bind(1, index);
            if (query.moveToNext()) {
                // Tile data found
                byte[] blob = query.getBlob(0);
                Bitmap tile = BitmapFactory.decodeByteArray(blob, 0,
                        blob.length, DECODE_OPTS);
                if (tile == null)
                    return null;
                return new TileBitmap(tile, level, col, row);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to query " + level + ", " + col
                    + ", " + row, e);
        } finally {
            if (query != null)
                query.close();
        }
        return null;
    }
}
