package com.atakmap.map.layer.raster.mbtiles;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.atakmap.database.CursorIface;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.raster.sqlite.SQLiteSingleTileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderSpi;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory.Options;

public class MBTilesSingleTileReader extends SQLiteSingleTileReader {

    public final static TileReaderSpi SPI = new SpiImpl("mbtiles-tile", "mbtiles") {

        @Override
        protected SQLiteSingleTileReader createInstance(Uri uri,
                                                  Options opts,
                                                  SQLiteSingleTileReader.SharedDb db,
                                                  String query,
                                                  int width,
                                                  int height) {

            // XXX - kind of hacky
            final boolean queryHasTileAlpha = query.contains("tile_alpha");
            if(queryHasTileAlpha) {
                AsynchronousIO asyncio;
                if(opts != null && opts.asyncIO != null)
                    asyncio = opts.asyncIO;
                else
                    asyncio = GdalLibrary.getMasterIOThread();
                return new MBTilesSingleTileReader(uri.toString(),
                                             asyncio,
                                             db,
                                             query,
                                             width,
                                             height,
                                             queryHasTileAlpha);
            } else {
                return super.createInstance(uri, opts, db, query, width, height);
            }
        }
        
    };

    private final boolean queryHasTileAlpha;

    public MBTilesSingleTileReader(String uri, AsynchronousIO asyncIO, SharedDb db, String query, int tileWidth, int tileHeight, boolean queryHasTileAlpha) {
        super(uri, asyncIO, db, query, tileWidth, tileHeight);
        
        this.queryHasTileAlpha = queryHasTileAlpha;
    }
    
    @Override
    protected Bitmap decodeTileBitmap(CursorIface result) {
        Bitmap tileData = super.decodeTileBitmap(result);

        if (tileData == null)
            return null;

        if(!this.queryHasTileAlpha)
            return tileData;
        
        Bitmap tileAlpha = null;
        try {
            try {
                byte[] data = result.getBlob(1);
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
    }
}
