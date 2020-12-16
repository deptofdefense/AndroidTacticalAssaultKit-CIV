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
import com.atakmap.map.layer.raster.tilepyramid.AbstractTilePyramidTileReader;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderSpi;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory.Options;

import java.io.File;

public class OSMDroidTileReader extends AbstractTilePyramidTileReader {

    public final static TileReaderSpi SPI = new TileReaderSpi() {
        @Override
        public String getName() {
            return "osmdroid";
        }

        @Override
        public TileReader create(String uri, Options options) {
            // XXX - post 3.4 test File::isFile, debugging

            if(uri.charAt(0) != '/')
                return null;

            DatabaseIface database;
            
            // try spatialite first
            database = null;
            try {
                database = IOProviderFactory.createDatabase(new File(uri), DatabaseInformation.OPTION_READONLY);
                final TileReader retval = createImpl(uri, database, options);
                if(retval != null) {
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
        
        @Override
        public boolean isSupported(String uri) {
            if(uri.charAt(0) != '/')
                return false;

            DatabaseIface database = null;
            try {
                database = IOProviderFactory.createDatabase(new File(uri), DatabaseInformation.OPTION_READONLY);
                return OSMUtils.isOSMDroidSQLite(database);
            } catch(Exception e) {
                return false;
            } finally {
                if(database != null)
                    database.close();
            }
        }
    };

    private DatabaseIface database;
    private int maxLevel;
    private int gridOffsetX;
    private int gridOffsetY;
    private final boolean hasMetadata;

    public OSMDroidTileReader(String uri,
                               AsynchronousIO asyncIO,
                               DatabaseIface database,
                               int minOsmLevel,
                               int numAvailableLevels,
                               int minLevelMinGridX,
                               int minLevelMinGridY,
                               int minLevelMaxGridX,
                               int minLevelMaxGridY,
                               int tileSize,
                               boolean hasMetadata) {

        super(uri,
              null,
              asyncIO,
              numAvailableLevels,
              ((long)(minLevelMaxGridX-minLevelMinGridX+1)<<(long)(numAvailableLevels-1))*(long)tileSize,
              ((long)(minLevelMaxGridY-minLevelMinGridY+1)<<(long)(numAvailableLevels-1))*(long)tileSize,
              tileSize, tileSize);

        this.database = database;
        this.maxLevel = minOsmLevel+numAvailableLevels-1;
        this.gridOffsetX = (minLevelMinGridX<<(numAvailableLevels-1));
        this.gridOffsetY = (minLevelMinGridY<<(numAvailableLevels-1));
        this.hasMetadata = hasMetadata;
    }

    @Override
    protected Bitmap getTileImpl(int level, long tileColumn, long tileRow, ReadResult[] code) {
        return getTileImpl(level, tileColumn, tileRow, null, code);
    }

    @Override
    protected Bitmap getTileImpl(int level, long tileColumn, long tileRow, BitmapFactory.Options opts, ReadResult[] code) {
        QueryIface stmt = null;
        try {
            stmt = this.database.compileQuery("SELECT tile FROM tiles WHERE key = ? LIMIT 1");
            stmt.bind(1, OSMUtils.getOSMDroidSQLiteIndex(this.maxLevel-level,
                                                         (int)tileColumn + (this.gridOffsetX>>level),
                                                         (int)tileRow + (this.gridOffsetY>>level)));
            
            if(!stmt.moveToNext()) {
                code[0] = ReadResult.ERROR;
                return null;
            }
            
            final byte[] compressed = stmt.getBlob(0);
            code[0] = ReadResult.SUCCESS;
            return BitmapFactory.decodeByteArray(compressed, 0, compressed.length, opts);
        } catch(Exception e) {
            code[0] = ReadResult.ERROR;
            return null;
        } finally {
            if(stmt != null)
                stmt.close();
        }
    }

    
    @Override
    protected void disposeImpl() {
        super.disposeImpl();
        
        this.database.close();
    }
    
    @Override
    public long getTileVersion(int version, long tileColumn, long tileRow) {
        if(this.hasMetadata) {
            // XXX - DB query; periodic? caching?
        }
        
        return super.getTileVersion(version, tileColumn, tileRow);
    }
    
    /**************************************************************************/
    
    private static TileReader createImpl(String uri, DatabaseIface database, Options options) {
        try {
            if(!OSMUtils.isOSMDroidSQLite(database))
                return null;
            
            // OSM Droid SQLite
            CursorIface result;

            int minLevel = -1;
            result = null;
            try {
                result = database.query("SELECT min(key) FROM tiles",
                        null);
                if (result.moveToNext())
                    minLevel = OSMUtils.getOSMDroidSQLiteZoomLevel(result.getLong(0));
            } finally {
                if (result != null)
                    result.close();
            }
            // no tiles present in database
            if (minLevel < 0)
                return null;

            // bounds discovery
            int gridMinX;
            int gridMinY;
            int gridMaxX;
            int gridMaxY;

            // XXX - brute force bounds -- reimplement to do 4 queries
            // for MBB discovery (min x,y / max x,y)
            result = null;
            long index;
            int tileX;
            int tileY;
            try {
                result = database.query(
                        "SELECT key FROM tiles WHERE key <= "
                                + OSMUtils.getOSMDroidSQLiteMaxIndex(minLevel), null);
                result.moveToNext();
                index = result.getLong(0);
                gridMinX = OSMUtils.getOSMDroidSQLiteTileX(index);
                gridMaxX = gridMinX;
                gridMinY = OSMUtils.getOSMDroidSQLiteTileY(index);
                gridMaxY = gridMinY;
                while (result.moveToNext()) {
                    index = result.getLong(0);
                    tileX = OSMUtils.getOSMDroidSQLiteTileX(index);
                    if (tileX < gridMinX)
                        gridMinX = tileX;
                    else if (tileX > gridMaxX)
                        gridMaxX = tileX;
                    tileY = OSMUtils.getOSMDroidSQLiteTileY(index);
                    if (tileY < gridMinY)
                        gridMinY = tileY;
                    else if (tileY > gridMaxY)
                        gridMaxY = tileY;
                }
            } finally {
                if (result != null)
                    result.close();
            }

            int maxLevel = -1;
            result = null;
            try {
                result = database.query("SELECT max(key) FROM tiles",
                        null);
                if (result.moveToNext())
                    maxLevel = OSMUtils.getOSMDroidSQLiteZoomLevel(result.getLong(0));
                else
                    throw new IllegalStateException();
            } finally {
                if (result != null)
                    result.close();
            }

            boolean hasMetadata = false;

            TileReader.AsynchronousIO io = TileReader.getMasterIOThread();
            if(options != null && options.asyncIO != null)
                io = options.asyncIO;
            
            return new OSMDroidTileReader(uri,
                                          io,
                                          database,
                                          minLevel,
                                          (maxLevel-minLevel+1),
                                          gridMinX,
                                          gridMinY,
                                          gridMaxX,
                                          gridMaxY,
                                          256, 
                                          hasMetadata);
        } catch(Exception e) {
            Log.w("OSMDroidTileReader", "Unexpected error creating OSMDroid Tile Reader", e);
            return null;
        }
    }
}
