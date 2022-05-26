package com.atakmap.map.layer.raster.tilepyramid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.math.MathUtils;
import com.atakmap.util.ResourcePool;

public abstract class AbstractTilePyramidTileReader extends TileReader {

    protected final static BitmapFactory.Options DECODE_OPTS = new BitmapFactory.Options();
    static {
        DECODE_OPTS.inPreferredConfig = Bitmap.Config.ARGB_8888;
    }

    protected static TileReader.Format format = null;
    
    protected final int tileWidth;
    protected final int tileHeight;
    protected final int numAvailableLevels;
    protected final long width;
    protected final long height;
    
    private ResourcePool<int[]> tileArgb;
    private ResourcePool<Bitmap> tileData;
    
    protected boolean debugTile;

    protected AbstractTilePyramidTileReader(String uri,
                                            String cacheUri,
                                            AsynchronousIO asyncIO,
                                            int numAvailableLevels,
                                            long width,
                                            long height,
                                            int tileWidth,
                                            int tileHeight) {

        super(uri, cacheUri, numAvailableLevels, asyncIO);

        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        
        this.numAvailableLevels = numAvailableLevels;
        
        this.width = width;
        this.height = height;
        
        this.debugTile = false;
        
        this.tileArgb = new ResourcePool<>(1);
        this.tileData = new ResourcePool<>(2);

        // test the pack of Bitmap ARGB_8888 data to see if conversion is
        // required
        synchronized(AbstractTilePyramidTileReader.class) {
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
    }

    @Override
    protected void disposeImpl() {
        super.disposeImpl();

        // clear the bitmap pool
        do {
            Bitmap pooled = tileData.get();
            if(pooled == null)
                break;
            pooled.recycle();
        } while(true);
    }

    @Override
    public final boolean isMultiResolution() {
        return true;
    }

    @Override
    public final long getWidth() {
        return this.width;
    }

    @Override
    public final long getHeight() {
        return this.height;
    }

    @Override
    public final int getTileWidth() {
        return this.tileWidth;
    }

    @Override
    public final int getTileHeight() {
        return this.tileHeight;
    }

    protected abstract Bitmap getTileImpl(int level, long tileColumn, long tileRow, ReadResult[] code);

    protected Bitmap getTileImpl(int level, long tileColumn, long tileRow, BitmapFactory.Options opts, ReadResult[] code) {
        return getTileImpl(level, tileColumn, tileRow, code);
    }

    @Override
    public final ReadResult read(int level, long tileColumn, long tileRow, byte[] data) {
        if (level < 0)
            throw new IllegalArgumentException();

        int[] tileArgb = null;
        Bitmap retval = null;
        ReadResult[] code = new ReadResult[1];
        boolean forceRecycle = false;
        try {
            synchronized(this.readLock) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inBitmap = tileData.get();
                opts.inMutable = true;
                retval = this.getTileImpl(level, tileColumn, tileRow, opts, code);
                // if the pooled bitmap wasn't used, re-pool or recycle a
                // appropriate
                if(opts.inBitmap != null && retval != opts.inBitmap) {
                    if(retval != null)
                        opts.inBitmap.recycle();
                    else
                        tileData.put(opts.inBitmap);
                }
            }
            if(retval != null) {
                if(retval.getWidth() != this.tileWidth || retval.getHeight() != this.tileHeight) {
                    final Bitmap scaled = Bitmap.createScaledBitmap(retval, tileWidth, tileHeight, true);
                    tileData.put(retval);
                    forceRecycle = true;
                    retval = scaled;
                }
                
                if(this.debugTile) {
                    Bitmap scratch = Bitmap.createBitmap(retval.getWidth(), retval.getHeight(), retval.getConfig());
                    (new Canvas(scratch)).drawBitmap(retval, 0, 0, null);
                    retval.recycle();
                    retval = scratch;
        
                    debugTile(new Canvas(retval), 0xFF0000FF, retval.getWidth(), retval.getHeight(), level, (int)tileColumn, (int)tileRow);
                }

                if(format == Format.RGBA) {
                    ByteBuffer dataBuffer = ByteBuffer.wrap(data);
                    dataBuffer.order(ByteOrder.nativeOrder());
                    retval.copyPixelsToBuffer(dataBuffer);
                    dataBuffer = null;
                } else {
                    tileArgb = this.tileArgb.get();
                    if (tileArgb == null)
                        tileArgb = new int[this.tileWidth * this.tileHeight];
                    else
                        Arrays.fill(tileArgb, 0);
                    retval.getPixels(tileArgb, 0, this.tileWidth, 0, 0, this.tileWidth, this.tileHeight);

                    final int numPixels = (retval.getWidth() * retval.getHeight());
                    ByteBuffer dataBuffer = ByteBuffer.wrap(data);
                    dataBuffer.order(ByteOrder.BIG_ENDIAN);
                    dataBuffer.asIntBuffer().put(tileArgb, 0, numPixels);
                    dataBuffer = null;
                }
            }

            return code[0];
        } finally {
            if(retval != null && (forceRecycle || !tileData.put(retval)))
                retval.recycle();
            if(tileArgb != null)
                this.tileArgb.put(tileArgb);
        }
    }

    @Override
    public final ReadResult read(long srcX, long srcY, long srcW, long srcH, int dstW,
            int dstH, byte[] data) {
        
        
        final int level = MathUtils.clamp((int)(Math.log(Math.max((double)srcW / (double)dstW, (double)srcH / (double)dstH)) / Math.log(2d)), 0, this.numAvailableLevels-1);

        final double subsampleX = (double)dstW / ((double)srcW/(double)(1<<level));
        final double subsampleY = (double)dstH / ((double)srcH/(double)(1<<level));

        final long stx = MathUtils.clamp(this.getTileColumn(level, srcX), 0, this.getNumTilesX(level)-1);
        final long sty = MathUtils.clamp(this.getTileRow(level, srcY), 0, this.getNumTilesY(level)-1);
        final long ftx = MathUtils.clamp(this.getTileColumn(level, srcX+srcW-1), 0, this.getNumTilesX(level)-1);
        final long fty = MathUtils.clamp(this.getTileRow(level, srcY+srcH-1), 0, this.getNumTilesY(level)-1);

        // if we are reading exactly one tile, skip the compositing
        if(Double.compare(subsampleX,1d) == 0 && Double.compare(subsampleY,1d) == 0 &&
           stx == ftx && sty == fty &&
           dstW == this.tileWidth && dstH == this.tileHeight) {

            return this.read(level, stx, sty, data);
        } else if(subsampleX < 0.25d || subsampleY < 0.25d) {
            return ReadResult.ERROR;
        }

        ReadResult result = ReadResult.SUCCESS;

        Bitmap retval = null;
        int[] tileArgb;
        try {
            retval = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888);
            

            ReadResult[] code = new ReadResult[1];
            
            Canvas g = new Canvas(retval);
            Bitmap tile;
            RectF dst = new RectF();
outer:      for(long ty = sty; ty <= fty; ty++) {
                for(long tx = stx; tx <= ftx; tx++) {
                    tile = null;
                    try {
                        synchronized(this.readLock) {
                            tile = this.getTileImpl(level, tx, ty, code);
                        }
                        if(tile == null)
                            continue;
                        if(code[0] != ReadResult.SUCCESS) {
                            result = code[0];
                            break outer;
                        }

                        dst.top = (float)((double)((this.getTileSourceY(level, ty)-srcY)>>level) * subsampleY);
                        dst.left = (float)((double)((this.getTileSourceX(level, tx)-srcX)>>level) * subsampleX);
                        dst.bottom = (float)Math.ceil((double)((this.getTileSourceY(level, ty+1)-srcY)>>level) * subsampleY);
                        dst.right = (float)Math.ceil((double)((this.getTileSourceX(level, tx+1)-srcX)>>level) * subsampleX);

                        g.drawBitmap(tile,
                                     null,
                                     dst,
                                     null);
                    } finally {
                        if(tile != null)
                            tile.recycle();
                    }
                }
            }
            
            if(this.debugTile)
                debugTile(g, 0xFFFF0000, retval.getWidth(), retval.getHeight(), level, (int)stx, (int)sty);

            tileArgb = new int[dstW*dstH];
            retval.getPixels(tileArgb, 0, dstW, 0, 0, dstW, dstH);
        } finally {
            if(retval != null)
                retval.recycle();
        }

        if(result != ReadResult.SUCCESS)
            return result;

        if(format == Format.RGBA) {
            int argb;
            int idx = 0;
            for(int i = 0; i < tileArgb.length; i++) {
                argb = tileArgb[i];
                data[idx++] = (byte)((argb>>16)&0xFF);
                data[idx++] = (byte)((argb>>8)&0xFF);
                data[idx++] = (byte)(argb&0xFF);
                data[idx++] = (byte)((argb>>24)&0xFF);
            }
        } else {
            int argb;
            int idx = 0;
            for(int i = 0; i < tileArgb.length; i++) {
                argb = tileArgb[i];
                data[idx++] = (byte)((argb>>24)&0xFF);
                data[idx++] = (byte)((argb>>16)&0xFF);
                data[idx++] = (byte)((argb>>8)&0xFF);
                data[idx++] = (byte)(argb&0xFF);
            }
        }

        return result;
    }

    protected static void debugTile(Canvas g, int color, int width, int height, int level, int tileCol, int tileRow) {
        Paint p = new Paint();
        p.setStyle(Paint.Style.STROKE);
        p.setColor(color);
        p.setStrokeWidth(3f);
        
        Rect src = new Rect();
        src.top = 2;
        src.left = 2;
        src.right = width-3;
        src.bottom = height-3;
        
        g.drawRect(src, p);
        
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(16f);
        g.drawText("[" + String.valueOf(level) + "," + String.valueOf(tileCol) + "," + String.valueOf(tileRow) + "]", 10, height/2f, p);
    }

    @Override
    public final Format getFormat() {
        return format;
    }

    @Override
    public final Interleave getInterleave() {
        return Interleave.BIP;
    }

}
