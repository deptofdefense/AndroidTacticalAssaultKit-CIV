package com.atakmap.map.layer.raster.sqlite;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderSpi;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory.Options;
import com.atakmap.util.ReferenceCount;
import com.atakmap.util.ResourcePool;

public class SQLiteSingleTileReader extends TileReader {

    private final static Map<String, SharedDb> sharedDbs = new HashMap<String, SharedDb>();

    public final static TileReaderSpi SPI = new SpiImpl();

    protected final static BitmapFactory.Options DECODE_OPTS = new BitmapFactory.Options();
    static {
        DECODE_OPTS.inPreferredConfig = Bitmap.Config.ARGB_8888;
    }

    static Format format;

    protected SharedDb db;
    protected final String query;
    protected final int tileWidth;
    protected final int tileHeight;

    protected SQLiteSingleTileReader(String uri, AsynchronousIO asyncIO, SharedDb db, String query, int tileWidth, int tileHeight) {
        super(uri, null, Integer.MAX_VALUE, asyncIO);
        
        this.db = db;
        this.db.reference();
        this.query = query;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        
        this.readLock = this.db;

        if(format == null) {
            Bitmap px = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            px.setPremultiplied(false);
            px.setPixel(0, 0, 0xF1F2F3F4);

            ByteBuffer buf = ByteBuffer.allocate(4);
            buf.order(ByteOrder.BIG_ENDIAN);
            px.copyPixelsToBuffer(buf);

            final int val = buf.getInt(0);
            if(val == 0xF2F3F4F1) {
                // packed RGBA
                format = Format.RGBA;
            } else {
                // default to ARGB
                format = Format.ARGB;
            }
        }
    }

    @Override
    protected void disposeImpl() {
        super.disposeImpl();
        if(this.db != null) {
            this.db.dereference();
            this.db = null;
        }
    }

    @Override
    public long getWidth() {
        return this.tileWidth;
    }

    @Override
    public long getHeight() {
        return this.tileHeight;
    }

    @Override
    public int getTileWidth() {
        return this.tileWidth;
    }

    @Override
    public int getTileHeight() {
        return this.tileHeight;
    }

    /**
     * Decodes the tile bitmap.
     * 
     * <P><B>Note:</B> This method is always externally synchronized on
     * <code>this.readLock</code>.
     * 
     * @param result    The row data
     * 
     * @return  The decoded tile or <code>null</code> if the tile could not be
     *          decoded.
     */
    protected Bitmap decodeTileBitmap(CursorIface result) {
        final byte[] blob = result.getBlob(0);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inBitmap = db.tileData.get();
        opts.inMutable = true;
        opts.inPreferredConfig = DECODE_OPTS.inPreferredConfig;
        Bitmap retval;
        try {
            retval = BitmapFactory.decodeByteArray(blob, 0, blob.length, opts);
        } catch(IllegalArgumentException e) {
            // an IllegalArgumentException may be raised if the input bitmap is
            // for some reason incompatible. If that ends up occurring clear
            // the input
            opts.inBitmap = null;
            retval = BitmapFactory.decodeByteArray(blob, 0, blob.length, opts);
        }
        // if the pooled bitmap wasn't used, re-pool or recycle a
        // appropriate
        if(opts.inBitmap != null && retval != opts.inBitmap) {
            if(retval != null)
                opts.inBitmap.recycle();
            else
                db.tileData.put(opts.inBitmap);
        }
        return retval;
    }

    @Override
    public ReadResult read(long srcX, long srcY, long srcW, long srcH, int dstW, int dstH,
            byte[] buf) {
        
        if(srcX < 0 || srcY < 0)
            return ReadResult.ERROR;
        if((srcX+srcW) > this.tileWidth || (srcY+srcH > this.tileHeight))
            return ReadResult.ERROR;
        
        Bitmap retval = null;
        try {
            synchronized(this.readLock) {
                if(this.db == null)
                    return ReadResult.ERROR;
                CursorIface result = null;
                try {
                    result = this.db.value.query(this.query, null);
                    if(!result.moveToNext())
                        return ReadResult.ERROR;
                    retval = this.decodeTileBitmap(result);
                } finally {
                    if(result != null)
                        result.close();
                }
            }
            if(retval == null)
                return ReadResult.ERROR;
            
            // XXX - check to make sure Bitmap dimensions agree with reported
            //       tile size???
            if(srcX != 0 || srcY != 0 || srcW != this.tileWidth || srcH != this.tileHeight || srcW != dstW || srcH != dstH) {
                Bitmap scratch = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888);
                Canvas graphics = new Canvas(scratch);
                graphics.drawBitmap(retval,
                                    new Rect((int)srcX, (int)srcY, (int)(srcX+srcW), (int)(srcY+srcH)),
                                    new Rect(0, 0, dstW, dstH),
                                    null);
                synchronized(readLock) {
                    // clean up the decode tile -- if it is of nominal size,
                    // try to put it back into the resource pool
                    if ((retval.getWidth() != this.tileWidth ||
                         retval.getHeight() != this.tileHeight) ||
                        !this.db.tileData.put(retval)) {

                        retval.recycle();
                    }
                }
                retval = scratch;
            }
            
            if(format == Format.RGBA) {
                ByteBuffer dataBuffer = ByteBuffer.wrap(buf);
                dataBuffer.order(ByteOrder.nativeOrder());
                retval.copyPixelsToBuffer(dataBuffer);
                dataBuffer = null;
            } else {
                int[] tileArgb = db.tileArgb.get();
                if (tileArgb == null)
                    tileArgb = new int[this.tileWidth * this.tileHeight];
                else
                    Arrays.fill(tileArgb, 0);
                retval.getPixels(tileArgb, 0, this.tileWidth, 0, 0, this.tileWidth, this.tileHeight);

                final int numPixels = (retval.getWidth() * retval.getHeight());
                ByteBuffer dataBuffer = ByteBuffer.wrap(buf);
                dataBuffer.order(ByteOrder.BIG_ENDIAN);
                dataBuffer.asIntBuffer().put(tileArgb, 0, numPixels);
                dataBuffer = null;
                db.tileArgb.put(tileArgb);
            }

            return ReadResult.SUCCESS;
        } finally {
            synchronized(this.readLock) {
                // clean up the decode tile -- if it is of nominal size, try to
                // put it back into the resource pool
                if (retval != null && ((retval.getWidth() != this.tileWidth || retval.getHeight() != this.tileHeight) || !this.db.tileData.put(retval))) {
                    retval.recycle();
                }
            }
        }
        
    }

    @Override
    public Format getFormat() {
        return format;
    }

    @Override
    public Interleave getInterleave() {
        return Interleave.BIP;
    }
    
    /**************************************************************************/
    
    /**
     * Generates a URI that can be handled by a <code>SQLiteTileReader</code>
     * with a scheme of <code>sqlite</code>.
     * 
     * @param db            The database file
     * @param sql           The SQL select statement to retrieve the tile from
     *                      the database; the tile blob MUST be in column
     *                      <code>0</code>.
     * @param tileWidth     The width of the tile, in pixels
     * @param tileHeight    The height of the tile, in pixels
     * 
     * @return  A URI that can be handled by a <code>SQLiteTileReader</code>.
     */
    public static String generateTileUri(File db, String sql, int tileWidth, int tileHeight) {
        return generateTileUri("sqlite", db, sql, tileWidth, tileHeight);
    }

    /**
     * Generates a URI that can be handled by a <code>SQLiteTileReader</code>.
     * 
     * @param customScheme  The scheme for the URI
     * @param db            The database file
     * @param sql           The SQL select statement to retrieve the tile from
     *                      the database; the tile blob MUST be in column
     *                      <code>0</code>.
     * @param width         The width of the tile, in pixels
     * @param height        The height of the tile, in pixels
     * 
     * @return  A URI that can be handled by a <code>SQLiteTileReader</code>.
     */
    public static String generateTileUri(String customScheme, File db, String sql, int width, int height) {
        StringBuilder uri = new StringBuilder();
        uri.append(customScheme);
        uri.append("://");
        uri.append(db.getAbsolutePath());
        uri.append("?query=");
        try {
            uri.append(URLEncoder.encode(sql, FileSystemUtils.UTF8_CHARSET.name()));
        } catch(UnsupportedEncodingException e) {
            return null;
        }
        uri.append("&width=");
        uri.append(width);
        uri.append("&height=");
        uri.append(height);

        return uri.toString();
    }
    
    /**************************************************************************/

    protected static class SharedDb extends ReferenceCount<DatabaseIface> {
        private final String dbPath;

        public final ResourcePool<Bitmap> tileData = new ResourcePool<>(1);
        public final ResourcePool<int[]> tileArgb = new ResourcePool<>(1);

        public SharedDb(String dbPath, DatabaseIface value, boolean reference) {
            super(value, reference);
            
            this.dbPath = dbPath;
        }

        @Override
        protected void onDereferenced() {
            try {
                synchronized(sharedDbs) {
                    sharedDbs.remove(dbPath);
                }

                try {
                    this.value.close();
                } catch(Exception ignored) {}

                while(true) {
                    final Bitmap bitmap = tileData.get();
                    if(bitmap == null)
                        break;
                    bitmap.recycle();
                }
            } finally {
                super.onDereferenced();
            }
        }
    }
    
    protected static class SpiImpl implements TileReaderSpi {
        private final String name;
        private final String scheme;
        
        public SpiImpl() {
            this("sqlite-tile", "sqlite");
        }

        public SpiImpl(String name, String scheme) {
            this.name = name;
            this.scheme = scheme;
        }
        
        protected SQLiteSingleTileReader createInstance(Uri uri, Options opts, SharedDb db, String query, int width, int height) {
            AsynchronousIO asyncio;
            if(opts != null && opts.asyncIO != null)
                asyncio = opts.asyncIO;
            else
                asyncio = GdalLibrary.getMasterIOThread();
            return new SQLiteSingleTileReader(uri.toString(),
                                        asyncio,
                                        db,
                                        query,
                                        width,
                                        height);
        }
        
        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public TileReader create(String u, Options options) {
            if(!isSupported(u))
                return null;
            
            Uri uri = Uri.parse(u);
            final String dbPath = uri.getPath();
            final String query;
            try {
                query = URLDecoder.decode(uri.getQueryParameter("query"), FileSystemUtils.UTF8_CHARSET.name());
            } catch (UnsupportedEncodingException e) {
                return null;
            }

            
            final int width; 
            final int height; 

            final String widthStr = uri.getQueryParameter("width");
            final String heightStr = uri.getQueryParameter("height");
            if (FileSystemUtils.isEmpty(widthStr) || FileSystemUtils.isEmpty(heightStr))
                  return null;

            try { 
                width = Integer.parseInt(widthStr);
            } catch (NumberFormatException nfe) { 
                return null;
            }
            try { 
                height = Integer.parseInt(heightStr);
            } catch (NumberFormatException nfe) { 
                return null;
            }
            
            synchronized(sharedDbs) {
                SharedDb db = null;
                try {
                    // obtain or create the shared DB instance and reference it
                    // immediately.
                    db = sharedDbs.get(dbPath);
                    if(db == null) {
                        sharedDbs.put(dbPath, db= new SharedDb(dbPath, IOProviderFactory.createDatabase(new File(dbPath), DatabaseInformation.OPTION_READONLY), true));
                        Log.d("SQLiteTileReader", "Creating shared DB ref " + dbPath);
                    } else {
                        db.reference();
                    }
                    return this.createInstance(uri, options, db, query, width, height);
                } finally {
                    // dereference the DB to handle cleanup in case any
                    // exception has occurred
                    if(db != null)
                        db.dereference();
                }
            }
        }

        @Override
        public boolean isSupported(String u) {
            // if it's a file path, skip URI parsing
            if(u.charAt(0) == '/')
                return false;

            try {
                Uri uri = Uri.parse(u);
                if(!uri.getScheme().equals(this.scheme))
                    return false;
                if(uri.getQueryParameter("query") == null)
                    return false;
                if(uri.getQueryParameter("width") == null)
                    return false;
                if(uri.getQueryParameter("height") == null)
                    return false;
                return true;
            } catch(Throwable t) {
                return false;
            }
        }
    };
}
