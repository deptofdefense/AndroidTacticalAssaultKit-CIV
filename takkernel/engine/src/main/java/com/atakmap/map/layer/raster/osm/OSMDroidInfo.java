package com.atakmap.map.layer.raster.osm;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.database.QueryIface;
import com.atakmap.map.projection.WebMercatorProjection;

import java.io.File;

public final class OSMDroidInfo {
    public enum BoundsDiscovery {
        /**
         * Full bounds discovery will be performed. This will require a full
         * table scan. This is the slowest option.
         */
        Full,
        /**
         * Bounds are computed based on the minimum and maximum keys for the
         * minimum level. This will be accurate for datasets with a single,
         * contiguous extent that is rectangular in shape. The grid min/max Y
         * will not be accurate in the event that the contained tiles are non-
         * contiguous or form a non-rectangular extent.
         */
        Quick,
        /**
         * Bounds computation is skipped altogether. Grid extents are populated
         * with the maximum possible values.
         */
        Skip,
    };

    public int srid;
    public int minLevel;
    public int maxLevel;
    public int minLevelGridMinX;
    public int minLevelGridMinY;
    public int minLevelGridMaxX;
    public int minLevelGridMaxY;
    public String provider;
    public int tileWidth;
    public int tileHeight;
    public int gridZeroWidth;
    public int gridZeroHeight;
 
    /**************************************************************************/

    public static OSMDroidInfo get(String path, BoundsDiscovery bounds, DatabaseIface[] returnRef) {
        DatabaseIface database;
        
        // try spatialite first
        database = null;
        try {
            database = IOProviderFactory.createDatabase(new File(path), DatabaseInformation.OPTION_READONLY);
            final OSMDroidInfo retval = get(database, bounds);
            if(retval != null) {
                if(returnRef != null) {
                    returnRef[0] = database;
                    database = null;
                }
                database = null;
                return retval;
            }
        } catch(Throwable ignored) {
        } finally {
            if(database != null)
                database.close();
        }
        
        return null;
    }

    public static OSMDroidInfo get(DatabaseIface database, BoundsDiscovery bounds) {
        try {
            if(!OSMUtils.isOSMDroidSQLite(database))
                return null;
            
            OSMDroidInfo info = new OSMDroidInfo();
            
            // OSM Droid SQLite
            CursorIface result;

            final long minKey;
            
            // min key discovery
            result = null;
            try {
                result = database.query("SELECT min(key) FROM tiles",
                        null);
                if (result.moveToNext() && !result.isNull(0)) {
                    minKey = result.getLong(0);
                    info.minLevel = OSMUtils.getOSMDroidSQLiteZoomLevel(minKey);
                } else {
                    // no tiles present in database
                    return null;
                }
            } finally {
                if (result != null)
                    result.close();
            }

            // max key discovery
            result = null;
            try {
                result = database.query("SELECT max(key) FROM tiles",
                        null);
                if (result.moveToNext() && !result.isNull(0)) {
                    info.maxLevel = OSMUtils.getOSMDroidSQLiteZoomLevel(result.getLong(0));
                } else {
                    throw new IllegalStateException();
                }
            } finally {
                if (result != null)
                    result.close();
            }

            // obtain the SRID
            info.srid = WebMercatorProjection.INSTANCE.getSpatialReferenceID();
            info.gridZeroWidth = 1;
            info.gridZeroHeight = 1;
            if (Databases.getColumnNames(database, "ATAK_metadata") != null) {
                result = null;
                try {
                    result = database.query(
                            "SELECT value FROM ATAK_metadata WHERE key = \'srid\'", null);
                    if (result.moveToNext()) {
                        info.srid = Integer.parseInt(result.getString(0));
                        if(info.srid == 4326) {
                            info.gridZeroWidth = 2;
                            info.gridZeroHeight = 1;
                        }
                    }
                } catch (Exception ignored) {
                    // quietly ignore
                } finally {
                    if (result != null)
                        result.close();
                }
            }
            
            // bounds discovery
            switch(bounds) {
                case Full :
                {
                    // find the min/max grid x -- this can be done with 2 single
                    // queries. assign initial min/max grid y.
                    result = null;
                    try {
                        result = database.compileQuery("SELECT min(key) FROM tiles WHERE key <= ?");
                        ((QueryIface)result).bind(1, OSMUtils.getOSMDroidSQLiteMaxIndex(info.minLevel));
                        
                        result.moveToNext();
                        info.minLevelGridMinX = OSMUtils.getOSMDroidSQLiteTileX(result.getLong(0));
                        
                        final int y = OSMUtils.getOSMDroidSQLiteTileY(result.getLong(0));
                        info.minLevelGridMinY = y;
                        info.minLevelGridMaxY = y;
                    } finally {
                        if (result != null)
                            result.close();
                    }
                    
                    result = null;
                    try {
                        result = database.compileQuery("SELECT max(key) FROM tiles WHERE key <= ?");
                        ((QueryIface)result).bind(1, OSMUtils.getOSMDroidSQLiteMaxIndex(info.minLevel));
                        
                        result.moveToNext();
                        info.minLevelGridMaxX = OSMUtils.getOSMDroidSQLiteTileX(result.getLong(0));
                        
                        final int y = OSMUtils.getOSMDroidSQLiteTileY(result.getLong(0));
                        if(y < info.minLevelGridMinY)
                            info.minLevelGridMinY = y;
                        if(y > info.minLevelGridMaxY)
                            info.minLevelGridMaxY = y;
                    } finally {
                        if (result != null)
                            result.close();
                    }
                    
                    // this method is quicker than brute force, but is not
                    // logarithmic time. iterate between the min/max x values
                    // doing min/max y discovery on each grid column, shrinking
                    // the search based on the current min/max y values
                    for(int x = info.minLevelGridMinX; x <= info.minLevelGridMaxX; x++) {
                        result = null;
                        try {
                            result = database.compileQuery("SELECT min(key) FROM tiles WHERE key >= ? AND key < ?");
                            ((QueryIface)result).bind(1, OSMUtils.getOSMDroidSQLiteIndex(info.minLevel, x, 0));
                            ((QueryIface)result).bind(2, OSMUtils.getOSMDroidSQLiteIndex(info.minLevel, x, info.minLevelGridMinY));
                            
                            if(result.moveToNext() && !result.isNull(0)) {
                                final int y = OSMUtils.getOSMDroidSQLiteTileY(result.getLong(0));
                                if(y < info.minLevelGridMinY)
                                    info.minLevelGridMinY = y;
                            }
                        } finally {
                            if (result != null)
                                result.close();
                        }
                        
                        result = null;
                        try {
                            result = database.compileQuery("SELECT max(key) FROM tiles WHERE key > ? AND key <= ?");
                            ((QueryIface)result).bind(1, OSMUtils.getOSMDroidSQLiteIndex(info.minLevel, x, info.minLevelGridMaxY));
                            ((QueryIface)result).bind(2, OSMUtils.getOSMDroidSQLiteIndex(info.minLevel, x, (1<<info.minLevel)-1));
                            
                            if(result.moveToNext() && !result.isNull(0)) {
                                final int y = OSMUtils.getOSMDroidSQLiteTileY(result.getLong(0));
                                if(y > info.minLevelGridMaxY)
                                    info.minLevelGridMaxY = y;
                            }
                        } finally {
                            if (result != null)
                                result.close();
                        }
                    }
                    break;
                }
                case Quick :
                {
                    result = null;
                    try {
                        final int mask = ~(0xFFFFFFFF<<info.minLevel);
                        result = database.compileQuery("SELECT min(key) FROM tiles WHERE key <= ?");
                        ((QueryIface)result).bind(1, OSMUtils.getOSMDroidSQLiteMaxIndex(info.minLevel));
                        
                        result.moveToNext();
                        info.minLevelGridMinX = OSMUtils.getOSMDroidSQLiteTileX(result.getLong(0));
                        info.minLevelGridMinY = OSMUtils.getOSMDroidSQLiteTileY(result.getLong(0));
                    } finally {
                        if (result != null)
                            result.close();
                    }
                    
                    result = null;
                    try {
                        final int mask = ~(0xFFFFFFFF<<info.minLevel);
                        result = database.compileQuery("SELECT max(key) FROM tiles WHERE key <= ?");
                        ((QueryIface)result).bind(1, OSMUtils.getOSMDroidSQLiteMaxIndex(info.minLevel));
                        
                        result.moveToNext();
                        info.minLevelGridMaxX = OSMUtils.getOSMDroidSQLiteTileX(result.getLong(0));
                        info.minLevelGridMaxY = OSMUtils.getOSMDroidSQLiteTileY(result.getLong(0));
                    } finally {
                        if (result != null)
                            result.close();
                    }
                    break;
                }
                case Skip :
                    info.minLevelGridMinX = 0;
                    info.minLevelGridMinY = 0;
                    info.minLevelGridMaxX = (info.gridZeroWidth << info.minLevel);
                    info.minLevelGridMaxY = (info.gridZeroHeight << info.minLevel);
                    break;
                default :
                    throw new IllegalArgumentException();
            }

            // obtain provider and tile dimensions
            info.provider = null;
            info.tileWidth = 256;
            info.tileHeight = 256;
            
            result = null;
            try {
                result = database.query("SELECT provider, tile FROM tiles LIMIT 1",
                        null);
                if (result.moveToNext()) {
                    info.provider = result.getString(0);
                    
                    byte[] blob = result.getBlob(1);
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    Bitmap tile = null;
                    try {
                        tile = BitmapFactory.decodeByteArray(blob, 0, blob.length, opts);
                        info.tileWidth = opts.outWidth;
                        info.tileHeight = opts.outHeight;
                    } finally {
                        if(tile != null)
                            tile.recycle();
                    }
                } else {
                    throw new IllegalStateException();
                }
            } finally {
                if (result != null)
                    result.close();
            }
            
            return info;
        } catch(Exception e) {
            Log.w("OSMDroidInfo", "Unexpected error parsing info", e);
            return null;
        }
    }    
}
