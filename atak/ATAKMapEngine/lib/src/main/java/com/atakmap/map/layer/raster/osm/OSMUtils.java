
package com.atakmap.map.layer.raster.osm;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;

import android.database.sqlite.SQLiteDatabase;

import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.android.AndroidDatabaseAdapter;

public class OSMUtils {

    public final static String TAG = "OSMUtils";

    public final static String OSMDROID_SQLITE_TILES_TABLE_NAME = "tiles";

    public final static Set<String> OSMDROID_SQLITE_TILES_TABLE_COLUMNS;
    static {
        Set<String> s = new LinkedHashSet<String>();
        s.add("key");
        s.add("provider");
        s.add("tile");
        OSMDROID_SQLITE_TILES_TABLE_COLUMNS = Collections.unmodifiableSet(s);
    }

    private OSMUtils() {
    }

    /** @deprecated use {@link #isOSMDroidSQLite(DatabaseIface)} */
    @Deprecated
    @DeprecatedApi(since = "4.1.1", forRemoval = true, removeAt = "4.4")
    public static boolean isOSMDroidSQLite(SQLiteDatabase database) {
        return isOSMDroidSQLite(new AndroidDatabaseAdapter(database));
    }

    public static boolean isOSMDroidSQLite(DatabaseIface database) {
        try { 
            final Set<String> columns = Databases.getColumnNames(database,
                OSMDROID_SQLITE_TILES_TABLE_NAME);
            if (columns == null)
               return false;
            return columns.equals(OSMDROID_SQLITE_TILES_TABLE_COLUMNS);
        } catch (Exception e) { 
            Log.e(TAG, "bad database encountered: " + database, e);
            return false;
        }
    }

    // next 4 derived from
    // http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames#X_and_Y
    public static double mapnikTileLat(int level, int ytile) {
        final int n = 1 << level;
        return Math.toDegrees(Math.atan(Math.sinh(Math.PI
                * (1.0d - (2.0d * (double) ytile / (double) n)))));
    }

    public static double mapnikTileLng(int level, int xtile) {
        final int n = 1 << level;
        return ((double) xtile / (double) n) * 360.0d - 180.0d;
    }

    public static int mapnikTileX(int level, double lng) {
        return (int)mapnikTileXd(level, lng);
    }

    public static int mapnikTileY(int level, double lat) {
        return (int)mapnikTileYd(level, lat);
    }

    public static double mapnikTileXd(int level, double lng) {
        final int n = 1 << level;
        return (n * ((lng + 180.0d) / 360.0d));
    }

    public static double mapnikTileYd(int level, double lat) {
        final int n = 1 << level;
        final double lat_rad = Math.toRadians(lat);
        return (n*(1d-(Math.log(Math.tan(lat_rad)+(1d/Math.cos(lat_rad)))/Math.PI))/2d);
    }

    public static double mapnikPixelLat(int level, int ytile, int y) {
        return mapnikTileLat(level + 8, (ytile << 8) + y);
    }

    public static double mapnikPixelLng(int level, int xtile, int x) {
        return mapnikTileLng(level + 8, (xtile << 8) + x);
    }

    public static int mapnikPixelY(int level, int ytile, double lat) {
        return mapnikTileY(level + 8, lat) - (ytile << 8);
    }

    public static int mapnikPixelX(int level, int xtile, double lng) {
        return mapnikTileX(level + 8, lng) - (xtile << 8);
    }

    public static double mapnikPixelYd(int level, int ytile, double lat) {
        final double tiley = mapnikTileYd(level, lat);
        return (tiley-ytile)*256d;
    }

    public static double mapnikPixelXd(int level, int xtile, double lng) {
        final double tilex = mapnikTileXd(level, lng);
        return (tilex-xtile)*256d;
    }

    public static double mapnikTileResolution(int level) {
        return mapnikTileResolution(level, 0.0d);
    }

    public static double mapnikTileResolution(int level, double lat) {
        if(level >= 32)
            return 0.0d;
        return 156543.034 * Math.cos(lat) / (1 << level);
    }

    public static int mapnikTileLevel(double resolution) {
        return mapnikTileLevel(resolution, 0.0d);
    }

    public static int mapnikTileLevel(double resolution, double lat) {
        if(resolution == 0.0d)
            return Integer.MAX_VALUE;

        // XXX - not sure whether we want ceil or floor here.....
        return (int)mapnikTileLeveld(resolution, lat);
    }

    public static double mapnikTileLeveld(double resolution, double lat) {
        if(resolution == 0.0d)
            return Integer.MAX_VALUE;
        return Math.log(156543.034 * Math.cos(lat) / resolution) / Math.log(2);
    }

    /**************************************************************************/
    // OSM Droid SQLite

    public static long getOSMDroidSQLiteIndex(long level, long tilex, long tiley) {
        return (((level << level) + tilex) << level) + tiley;
    }

    public static int getOSMDroidSQLiteZoomLevel(long index) {
        for (int i = 0; i < 29; i++)
            if (index <= getOSMDroidSQLiteMaxIndex(i))
                return i;
        return -1;
    }

    public static int getOSMDroidSQLiteTileX(long index) {
        final int zoomLevel = getOSMDroidSQLiteZoomLevel(index);
        if (zoomLevel < 0)
            return -1;
        index >>>= zoomLevel;
        index -= (zoomLevel << zoomLevel);
        return (int) index;
    }

    public static int getOSMDroidSQLiteTileY(long index) {
        final int zoomLevel = getOSMDroidSQLiteZoomLevel(index);
        if (zoomLevel < 0)
            return -1;
        return (int) ((~(0xFFFFFFFFFFFFFFFFL << (long) zoomLevel)) & index);
    }

    public static long getOSMDroidSQLiteMaxIndex(int zoomLevel) {
        return getOSMDroidSQLiteMinIndex(zoomLevel + 1) - 1;

    }

    public static long getOSMDroidSQLiteMinIndex(int zoomLevel) {
        if (zoomLevel < 0 || zoomLevel > 28)
            return -1;
        return (long) zoomLevel << (long) (zoomLevel * 2);
    }
}
