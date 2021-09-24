package com.atakmap.map.layer.raster.mbtiles;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;

public class MBTilesInfo {
    private final static Set<String> TILES_TABLE_COLUMN_NAMES = new HashSet<String>();
    static {
        TILES_TABLE_COLUMN_NAMES.add("zoom_level");
        TILES_TABLE_COLUMN_NAMES.add("tile_column");
        TILES_TABLE_COLUMN_NAMES.add("tile_row");
        TILES_TABLE_COLUMN_NAMES.add("tile_data");
    }
    
    private final static Set<String> TILES_TABLE_COLUMN_NAMES2 = new HashSet<String>();
    static {
        TILES_TABLE_COLUMN_NAMES2.addAll(TILES_TABLE_COLUMN_NAMES);
        TILES_TABLE_COLUMN_NAMES.add("tile_alpha");
    }
    
    private final static String COLUMN_TILE_ALPHA = "tile_alpha";

    public int minLevel;
    public int maxLevel;
    public int minLevelGridMinX;
    public int minLevelGridMinY;
    public int minLevelGridMaxX;
    public int minLevelGridMaxY;
    public String name;
    public int tileWidth;
    public int tileHeight;
    public boolean hasTileAlpha;
 
    /**************************************************************************/

    public static MBTilesInfo get(String path, DatabaseIface[] returnRef) {
        DatabaseIface database;
        
        // try spatialite first
        database = null;
        try {
            database = IOProviderFactory.createDatabase(new File(path), DatabaseInformation.OPTION_READONLY);
            final MBTilesInfo retval = get(database);
            if(retval != null) {
                if(returnRef != null) {
                    returnRef[0] = database;
                    database = null;
                }
                return retval;
            }
        } catch(Throwable ignored) {
        } finally {
            if(database != null)
                database.close();
        }

        return null;
    }

    public static MBTilesInfo get(DatabaseIface database) {
        try {
            Map<String, Set<String>> databaseStructure;
            try { 
               databaseStructure = Databases.getColumnNames(database);
            } catch (Exception e) { 
                return null;
            }

            if (databaseStructure.get("tiles") == null)
                return null;

            Set<String> tilesTable = databaseStructure.get("tiles");
            if(!tilesTable.equals(TILES_TABLE_COLUMN_NAMES) && !tilesTable.equals(TILES_TABLE_COLUMN_NAMES2))
                return null;
            
            MBTilesInfo info = new MBTilesInfo();
            info.hasTileAlpha = tilesTable.contains(COLUMN_TILE_ALPHA);
            
            
            CursorIface result;

            info.minLevel = -1;
            info.maxLevel = -1;
            result = null;
            try {
                result = database.query("SELECT name, value FROM metadata", null);
                String name;
                while (result.moveToNext()) {
                    name = result.getString(0);
                    if (name.equals("maxZoomLevel"))
                        info.maxLevel = Integer.parseInt(result.getString(1));
                    else if (name.equals("minZoomLevel"))
                        info.minLevel = Integer.parseInt(result.getString(1));
                    else if(name.equals("name"))
                        info.name = result.getString(1);
                }
            } finally {
                if (result != null)
                    result.close();
            }

            if (info.minLevel < 0) {
                result = null;
                try {
                    // XXX - switch to min(zoom_level)
                    result = database
                            .query(
                                    "SELECT zoom_level FROM tiles ORDER BY zoom_level ASC LIMIT 1",
                                    null);
                    if (result.moveToNext())
                        info.minLevel = result.getInt(0);
                } finally {
                    if (result != null)
                        result.close();
                }
            }
            if (info.maxLevel < 0) {
                result = null;
                try {
                    result = database
                            .query(
                                    "SELECT zoom_level FROM tiles ORDER BY zoom_level DESC LIMIT 1",
                                    null);
                    if (result.moveToNext())
                        info.maxLevel = result.getInt(0);
                } finally {
                    if (result != null)
                        result.close();
                }
            }
            // no tiles present in database
            if (info.minLevel < 0 || info.maxLevel < 0)
                return null;

            // bounds discovery

            // XXX - NEXT 4 -- use min(...) / max(...)
            // do 4 queries for MBB discovery (min x,y / max x,y)
            result = null;
            try {
                result = database.query(
                        "SELECT tile_column FROM tiles WHERE zoom_level = "
                                + String.valueOf(info.minLevel)
                                + " ORDER BY tile_column ASC LIMIT 1", null);
                if (result.moveToNext())
                    info.minLevelGridMinX = result.getInt(0);
                else
                    throw new RuntimeException();
            } finally {
                if (result != null)
                    result.close();
            }

            result = null;
            try {
                result = database.query(
                        "SELECT tile_column FROM tiles WHERE zoom_level = "
                                + String.valueOf(info.minLevel)
                                + " ORDER BY tile_column DESC LIMIT 1", null);
                if (result.moveToNext())
                    info.minLevelGridMaxX = result.getInt(0);
                else
                    throw new RuntimeException();
            } finally {
                if (result != null)
                    result.close();
            }

            result = null;
            try {
                result = database.query(
                        "SELECT tile_row FROM tiles WHERE zoom_level = "
                                + String.valueOf(info.minLevel)
                                + " ORDER BY tile_row ASC LIMIT 1", null);
                if (result.moveToNext())
                    info.minLevelGridMinY = result.getInt(0);
                else
                    throw new RuntimeException();
            } finally {
                if (result != null)
                    result.close();
            }

            result = null;
            try {
                result = database.query(
                        "SELECT tile_row FROM tiles WHERE zoom_level = "
                                + String.valueOf(info.minLevel)
                                + " ORDER BY tile_row DESC LIMIT 1", null);
                if (result.moveToNext())
                    info.minLevelGridMaxY = result.getInt(0);
                else
                    throw new RuntimeException();
            } finally {
                if (result != null)
                    result.close();
            }

            // obtain and tile dimensions
            info.tileWidth = 256;
            info.tileHeight = 256;
            
            result = null;
            try {
                result = database.query("SELECT tile_data FROM tiles LIMIT 1",
                        null);
                if (result.moveToNext()) {
                    byte[] blob = result.getBlob(0);
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
            Log.w("MBTilesInfo", "Unexpected error parsing info", e);
            return null;
        }
    }
}