package com.atakmap.map.layer.raster.tilereader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.io.ZipVirtualFile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;

public class AndroidTileReader extends TileReader {

    private File file;
    private int width;
    private int height;
    private int tileWidth;
    private int tileHeight;
    private FileDecoderImpl decoder;

    public AndroidTileReader(File file, int tileWidth, int tileHeight, String cacheUri, AsynchronousIO asyncIO) throws IOException {
        // XXX - 
        super(file.getAbsolutePath(), null, Integer.MAX_VALUE, asyncIO);

        this.file = file;

        InputStream stream = null;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            if(file instanceof ZipVirtualFile)
                stream = ((ZipVirtualFile)file).openStream();
            else
                stream = IOProviderFactory.getInputStream(file);
            BitmapFactory.decodeStream(stream, null, opts);
            this.width = opts.outWidth;
            this.height = opts.outHeight;
        } finally {
            if(stream != null)
                stream.close();
        }
        
        if(tileWidth > 0)
            this.tileWidth = tileWidth;
        else
            this.tileWidth = this.width;
        if(tileHeight > 0)
            this.tileHeight = tileHeight;
        else
            this.tileHeight = height;
        
        if(this.file instanceof ZipVirtualFile)
            this.decoder = new ZipDecoderImpl();
        else
            this.decoder = new FileDecoderImpl();
        
        this.readLock = this.decoder;
    }

    /**************************************************************************/
    // Tile Reader

    @Override
    public long getWidth() {
        return this.width;
    }

    @Override
    public long getHeight() {
        return this.height;
    }

    @Override
    public int getTileWidth() {
        return this.tileWidth;
    }

    @Override
    public int getTileHeight() {
        return this.tileHeight;
    }

    @Override
    public ReadResult read(long srcX, long srcY, long srcW, long srcH, int dstW, int dstH, byte[] buf) {
        try {
            if(this.decoder.read(srcX, srcY, srcW, srcH, dstW, dstH, buf))
                return ReadResult.SUCCESS;
            else
                return ReadResult.CANCELED;
        } catch(IOException e) {
            return ReadResult.ERROR;
        }
    }

    @Override
    protected int getTransferSize(int width, int height) {
        return 4*width*height;
    }

    @Override
    public Format getFormat() {
        return Format.RGBA;
    }

    @Override
    public Interleave getInterleave() {
        return Interleave.BIP;
    }
    
    @Override
    protected void disposeImpl() {
        super.disposeImpl();
        
        this.decoder.dispose();
    }
    
    @Override
    protected void cancel() {
        if(this.decoder.decodeOpts != null) {
            this.decoder.decodeOpts.requestCancelDecode();
            this.decoder.decodeOpts = null;
        }
    }

    /**************************************************************************/

    private class FileDecoderImpl {

        private BitmapFactory.Options decodeOpts;
        private Rect region;
        protected BitmapRegionDecoder impl;
        
        public FileDecoderImpl() {
            this.region = new Rect();
        }
        
        protected Bitmap decode(long srcX, long srcY, long srcW, long srcH, int dstW, int dstH) throws IOException {
            if(this.impl == null)
                this.impl = BitmapRegionDecoder.newInstance(AndroidTileReader.this.uri, false);

            this.region.left = (int)srcX;
            this.region.top = (int)srcY;
            this.region.right = (int)(srcX+srcW);
            this.region.bottom = (int)(srcY+srcH);
            
            if(this.decodeOpts == null) {
                this.decodeOpts = new BitmapFactory.Options();
                this.decodeOpts.inPreferredConfig = Bitmap.Config.ARGB_8888;                
            }

            if(srcW != dstW || srcH != dstH) {
                final int sampleX = (int)Math.ceil(Math.log((double)srcW/(double)dstW) / Math.log(2d));
                final int sampleY = (int)Math.ceil(Math.log((double)srcH/(double)dstH) / Math.log(2d));
                
                final int sample = (1<<Math.min(sampleX, sampleY));
                
                this.decodeOpts.inScaled = (sample > 1);
                this.decodeOpts.inSampleSize = sample;
            } else {
                this.decodeOpts.inScaled = false;
                this.decodeOpts.inSampleSize = 1;
            }
            this.decodeOpts.inJustDecodeBounds = false;

            return this.impl.decodeRegion(this.region, this.decodeOpts);
        }

        public boolean read(long srcX, long srcY, long srcW, long srcH, int dstW, int dstH,
                byte[] buf) throws IOException {
            
            Bitmap decoded = null;
            try {
                synchronized(AndroidTileReader.this.readLock) {
                    decoded = this.decode(srcX, srcY, srcW, srcH, dstW, dstH);
                }
                if(decoded.getWidth() != dstW || decoded.getHeight() != dstH) {
                    // XXX - don't use Bitmap.createScaledBitmap; see bug 2045
                    Bitmap scaled = Bitmap.createBitmap(dstW, dstH,
                            Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(scaled);
                    canvas.drawBitmap(decoded,
                            new android.graphics.Rect(0, 0, decoded.getWidth(), decoded.getHeight()),
                            new android.graphics.Rect(0, 0, dstW, dstH),
                            null);
                    decoded.recycle();
                    decoded = scaled;
                }
                
                decoded.copyPixelsToBuffer(ByteBuffer.wrap(buf));
            } finally {
                if(decoded != null)
                    decoded.recycle();
            }

            return true;
        }
        
        public void dispose() {
            if(this.impl != null) {
                this.impl.recycle();
                this.impl = null;
            }
        }
    }
    
    private class ZipDecoderImpl extends FileDecoderImpl {

        @Override
        protected Bitmap decode(long srcX, long srcY, long srcW, long srcH, int dstW, int dstH) throws IOException {
            InputStream stream = null;
            try {
                stream = ((ZipVirtualFile)AndroidTileReader.this.file).openStream();
                
                this.impl = BitmapRegionDecoder.newInstance(stream, false);
                
                return super.decode(srcX, srcY, srcW, srcH, dstW, dstH);
            } finally {
                if(this.impl != null) {
                    this.impl.recycle();
                    this.impl = null;
                }
                if(stream != null)
                    stream.close();
            }
        }
        
    }

}
