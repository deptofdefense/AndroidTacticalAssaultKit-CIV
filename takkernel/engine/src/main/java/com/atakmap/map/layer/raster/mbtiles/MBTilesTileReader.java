package com.atakmap.map.layer.raster.mbtiles;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.atakmap.database.DatabaseIface;
import com.atakmap.database.QueryIface;
import com.atakmap.map.layer.raster.tilepyramid.AbstractTilePyramidTileReader;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderSpi;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory.Options;
import com.atakmap.coremap.log.Log;


public class MBTilesTileReader extends AbstractTilePyramidTileReader {

    public final static TileReaderSpi SPI = new TileReaderSpi() {
        @Override
        public String getName() {
            return "mbtiles";
        }

        @Override
        public TileReader create(String uri, Options options) {
            // XXX - post 3.4 test File::isFile, debugging

            if(uri.charAt(0) != '/')
                return null;

            DatabaseIface[] databasePtr = new DatabaseIface[1];
            
            try {
                MBTilesInfo info = MBTilesInfo.get(uri, databasePtr);
                if(info == null)
                    return null;

                AsynchronousIO asyncio = getMasterIOThread();
                if(options != null && options.asyncIO != null)
                    asyncio = options.asyncIO;
                
                final DatabaseIface database = databasePtr[0];
                databasePtr[0] = null;
                return new MBTilesTileReader(uri,
                        asyncio,
                        database,
                        info);
            } catch(Throwable ignored) {
            } finally {
                if(databasePtr[0] != null)
                    databasePtr[0].close();
            }
            
            return null;
        }
        
        @Override
        public boolean isSupported(String uri) {
            if(uri.charAt(0) != '/')
                return false;

            try {
                // XXX - create some isMBTiles method
                return (MBTilesInfo.get(uri, null) != null);
            } catch(Exception e) {
                return false;
            }
        }
    };

    private DatabaseIface database;
    private int maxLevel;
    private int gridOffsetX;
    private int gridOffsetY;

    private boolean hasAlpha;

    private String tileQueryString;

    public MBTilesTileReader(String uri,
                             AsynchronousIO asyncIO,
                             DatabaseIface database,
                             MBTilesInfo info) {

        super(uri,
              null,
              asyncIO,
              info.maxLevel-info.minLevel+1,
              ((long)(info.minLevelGridMaxX-info.minLevelGridMinX+1)<<(long)(info.maxLevel-info.minLevel))*(long)info.tileWidth,
              ((long)(info.minLevelGridMaxY-info.minLevelGridMinY+1)<<(long)(info.maxLevel-info.minLevel))*(long)info.tileHeight,
              info.tileWidth, info.tileHeight);

        this.database = database;
        this.maxLevel = info.minLevel+this.numAvailableLevels-1;
        this.gridOffsetX = (info.minLevelGridMinX<<(this.numAvailableLevels-1));
        this.gridOffsetY = (info.minLevelGridMinY<<(this.numAvailableLevels-1));
        this.hasAlpha = info.hasTileAlpha;
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT tile_data");
        if(this.hasAlpha)
            sql.append(", tile_alpha");
        sql.append(" FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ? LIMIT 1");
        
        this.tileQueryString = sql.toString();
    }

    @Override
    protected Bitmap getTileImpl(int level, long tileColumn, long tileRow, ReadResult[] code) {
        return getTileImpl(level, tileColumn, tileRow, null, code);
    }

    @Override
    protected Bitmap getTileImpl(int level, long tileColumn, long tileRow, BitmapFactory.Options opts, ReadResult[] code) {
        tileRow = this.getNumTilesY(level)-tileRow-1;

        QueryIface stmt = null;
        try {
            stmt = this.database.compileQuery(this.tileQueryString);
            stmt.bind(1, this.maxLevel-level);
            stmt.bind(2, tileColumn + (this.gridOffsetX>>level));
            stmt.bind(3, tileRow + (this.gridOffsetY>>level));
            
            if(!stmt.moveToNext()) {
                code[0] = ReadResult.ERROR;
                return null;
            }
            
            final byte[] compressed = stmt.getBlob(0);
            code[0] = ReadResult.SUCCESS;

            Bitmap tileData = BitmapFactory.decodeByteArray(compressed, 0, compressed.length, opts);
            if(!this.hasAlpha)
                return tileData;
            
            Bitmap tileAlpha = null;
            try {
                try {
                    byte[] data = stmt.getBlob(1);
                    tileAlpha = BitmapFactory.decodeByteArray(data, 0, data.length);
                } catch(Exception ignored) {}
                if(tileAlpha == null)
                    return tileData;
                
                if(tileAlpha.getWidth() != tileData.getWidth() || tileAlpha.getHeight() != tileData.getHeight())
                    return tileData;

                final int width = tileData.getWidth();
                final int height = tileData.getHeight();
                final int length = (width * height);
                final int[] data = new int[length];
                final int[] alpha = new int[length];
                try {
                    tileData.getPixels(data, 0, width, 0, 0, width, height);
                    tileAlpha.getPixels(alpha, 0, width, 0, 0, width, height);
                } finally {
                    tileData.recycle();
                    tileData = null;
                    tileAlpha.recycle();
                    tileAlpha = null;
                }
                
                for(int i = 0; i < length; i++)
                    if((alpha[i]&0x00FFFFFF) == 0x00) data[i] = 0;
                return Bitmap.createBitmap(data, width, height, Bitmap.Config.ARGB_8888);
            } finally {
                if(tileAlpha != null)
                    tileAlpha.recycle();
            }
        } catch(Exception e) {
            Log.e(TAG, "error", e);
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
}
