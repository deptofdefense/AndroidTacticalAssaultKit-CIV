package com.atakmap.map.opengl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Rect;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.Ellipsoid;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.RenderContext;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.DefaultDatasetProjection2;
import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory;
import com.atakmap.map.layer.raster.tilereader.opengl.GLQuadTileNode2;
import com.atakmap.map.layer.raster.tilereader.opengl.GLQuadTileNode4;
import com.atakmap.map.layer.raster.tilereader.opengl.PrefetchedInitializer;
import com.atakmap.map.projection.Projection;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.math.Matrix;


public final class GLBaseMap implements GLMapRenderable {
    
    private Matrix img2geo;
    private Matrix geo2img;
    private TileReader srcReader;
    private ImageInfo srcInfo;
    private boolean srcInit; 
    
    private GLMapRenderable impl;
    private int implSrid;
    
    
    public GLBaseMap() {
        this.img2geo = null;
        this.geo2img = null;
        this.srcReader = null;
        this.srcInfo = null;
        this.srcInit = false;
        this.impl = null;
        this.implSrid = -1;
    }
    
    private GLMapRenderable createRenderer(RenderContext ctx, Projection proj) {
        // obtain the bounds of the projection
        GeoPoint ul = GeoPoint.createMutable().set(proj.getMaxLatitude(), proj.getMinLongitude());
        GeoPoint ur = GeoPoint.createMutable().set(proj.getMaxLatitude(), proj.getMaxLongitude());
        GeoPoint lr = GeoPoint.createMutable().set(proj.getMinLatitude(), proj.getMaxLongitude());
        GeoPoint ll = GeoPoint.createMutable().set(proj.getMinLatitude(), proj.getMinLongitude());
        
        // obtain the projection bounds in image space
        PointD geo = new PointD(0d, 0d);
        
        geo.x = ul.getLongitude();
        geo.y = ul.getLatitude();
        PointD imgUL = this.geo2img.transform(geo, null);
        geo.x = ur.getLongitude();
        geo.y = ur.getLatitude();
        PointD imgUR = this.geo2img.transform(geo, null);
        geo.x = lr.getLongitude();
        geo.y = lr.getLatitude();
        PointD imgLR = this.geo2img.transform(geo, null);
        geo.x = ll.getLongitude();
        geo.y = ll.getLatitude();
        PointD imgLL = this.geo2img.transform(geo, null);
        
        // if projection bounds exceed image space, intersect
        if(imgUL.x < 0 || imgUL.y < 0) {
            imgUL.x = Math.max(imgUL.x, 0d);
            imgUL.y = Math.max(imgUL.y, 0d);
            
            this.img2geo.transform(imgUL, geo);
            ul.set(geo.y, geo.x);
        }
        if(imgUR.x > this.srcReader.getWidth() || imgUR.y < 0) {
            imgUR.x = Math.min(imgUR.x, this.srcReader.getWidth());
            imgUR.y = Math.max(imgUR.y, 0d);
            
            this.img2geo.transform(imgUR, geo);
            ur.set(geo.y, geo.x);
        }
        if(imgLR.x > this.srcReader.getWidth() || imgLR.y > this.srcReader.getHeight()) {
            imgLR.x = Math.min(imgLR.x, this.srcReader.getWidth());
            imgLR.y = Math.min(imgLR.y, this.srcReader.getHeight());
            
            this.img2geo.transform(imgLR, geo);
            lr.set(geo.y, geo.x);
        }
        if(imgLL.x < 0 || imgLL.y > this.srcReader.getHeight()) {
            imgLL.x = Math.max(imgLL.x, 0d);
            imgLL.y = Math.min(imgLL.y, this.srcReader.getHeight());
            
            this.img2geo.transform(imgLL, geo);
            ll.set(geo.y, geo.x);
        }
        
        // compute the region of the source image that satisfies the bounds of
        // the projection
        Rect srcImgRegion = new Rect();
        srcImgRegion.top = (int)MathUtils.min(imgUL.y, imgUR.y, imgLR.y, imgLL.y);
        srcImgRegion.left = (int)MathUtils.min(imgUL.x, imgUR.x, imgLR.x, imgLL.x);
        srcImgRegion.bottom = (int)MathUtils.max(imgUL.y, imgUR.y, imgLR.y, imgLL.y);
        srcImgRegion.right = (int)MathUtils.max(imgUL.x, imgUR.x, imgLR.x, imgLL.x);
        
        // reset the corners to the region
        imgUL.x = srcImgRegion.left;
        imgUL.y = srcImgRegion.top;
        imgUR.x = srcImgRegion.right;
        imgUR.y = srcImgRegion.top;
        imgLR.x = srcImgRegion.right;
        imgLR.y = srcImgRegion.bottom;
        imgLL.x = srcImgRegion.left;
        imgLL.y = srcImgRegion.bottom;
        
        // recompute the geo corners
        this.img2geo.transform(imgUL, geo);
        ul.set(geo.y, geo.x);
        this.img2geo.transform(imgUR, geo);
        ur.set(geo.y, geo.x);
        this.img2geo.transform(imgLR, geo);
        lr.set(geo.y, geo.x);
        this.img2geo.transform(imgLL, geo);
        ll.set(geo.y, geo.x);

        ImageInfo info = new ImageInfo(this.srcReader.getUri(),
                                       "bitmap",
                                       false,
                                       ul, ur, lr, ll,
                                       this.srcInfo.maxGsd,
                                       (srcImgRegion.right-srcImgRegion.left),
                                       (srcImgRegion.bottom-srcImgRegion.top),
                                       this.srcInfo.srid);
        
        // create a dataset projection
        DatasetProjection2 datasetProj = new DefaultDatasetProjection2(
                info.srid,
                info.width,
                info.height,
                ul, ur, lr, ll);
        
        GLQuadTileNode2.Initializer init =
                new PrefetchedInitializer(
                        new SubimageTileReader(this.srcReader,
                                               srcImgRegion,
                                               TileReader.getMasterIOThread()),
                        datasetProj,
                        false);
        
        TileReaderFactory.Options readerOpts = null;
        GLQuadTileNode2.Options opts = null;

        return new GLQuadTileNode4(ctx, info, readerOpts, opts, init);
    }

    private void initSource(GLMapView view) {
        try {
            final Context context = GLRenderGlobals.appContext;
            if(context == null)
                return;

            int id = context.getResources().getIdentifier("worldmap_4326", "drawable", context.getPackageName());
            this.srcReader = new BitmapTileReader(context, id, 128, 128, null, TileReader.getMasterIOThread());
        
            this.srcInfo = new ImageInfo(
                    this.srcReader.getUri(),
                    "resource",
                    false,
                    new GeoPoint(90, -180),
                    new GeoPoint(90, 180),
                    new GeoPoint(-90, 180),
                    new GeoPoint(-90, -180),
                    Math.PI*2d*Ellipsoid.WGS_84.getSemiMajorAxis()/(double)this.srcReader.getWidth(),
                    (int)this.srcReader.getWidth(),
                    (int)this.srcReader.getHeight(),
                    4326);
    
            this.img2geo = Matrix.mapQuads(
                    0, 0,
                    this.srcInfo.width, 0,
                    this.srcInfo.width, this.srcInfo.height,
                    0, this.srcInfo.height,
                    this.srcInfo.upperLeft.getLongitude(),
                    this.srcInfo.upperLeft.getLatitude(),
                    this.srcInfo.upperRight.getLongitude(),
                    this.srcInfo.upperRight.getLatitude(),
                    this.srcInfo.lowerRight.getLongitude(),
                    this.srcInfo.lowerRight.getLatitude(),
                    this.srcInfo.lowerLeft.getLongitude(),
                    this.srcInfo.lowerLeft.getLatitude());
    
            this.geo2img = this.img2geo.createInverse();
        } catch(Throwable t) {
            Log.e("GLBaseMap", "Failed to initialize basemap", t);

            this.srcReader = null;
            this.srcInfo = null;
            this.geo2img = null;
            this.img2geo = null;
        }
    }

    @Override
    public void draw(GLMapView view) {
        if(this.impl == null || this.implSrid != view.drawSrid) {
            if(!this.srcInit) {
                this.initSource(view);
                this.srcInit = true;
            }

            if(this.srcReader != null) {
                this.impl = createRenderer(view.getRenderContext(), view.scene.mapProjection);
                this.implSrid = view.scene.mapProjection.getSpatialReferenceID();
            }
        }

        if(this.impl != null)
            this.impl.draw(view);
    }

    @Override
    public void release() {
        if(this.impl != null) {
            this.impl.release();
            this.impl = null;
        }
        this.implSrid = -1;
        if(this.srcInfo != null)
            this.srcInfo = null;
        if(this.srcReader != null) {
            this.srcReader.dispose();
            this.srcReader = null;
        }

        this.img2geo = null;
        this.geo2img = null;
        
        this.srcInit = false;
    }
    
    /**************************************************************************/
    
    private static class BitmapTileReader extends TileReader {
        private Context ctx;
        private int id;
        private int width;
        private int height;
        private int tileWidth;
        private int tileHeight;
        private Rect region;

        public BitmapTileReader(Context ctx, int id, int tileWidth, int tileHeight, String cacheUri, AsynchronousIO asyncIO) throws IOException {
            // XXX - 
            super("resource://" + id, null, Integer.MAX_VALUE, asyncIO);

            this.id = id;
            this.ctx = ctx;

            InputStream stream = null;
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                stream = ctx.getResources().openRawResource(id);
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
                this.tileHeight = this.height;
            
            this.region = new Rect();
            
            this.readLock = this.region;
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
                Bitmap decoded = null;
                BitmapRegionDecoder impl = null;
                InputStream stream = null;
                try {
                    synchronized(this.readLock) {
                        stream = this.ctx.getResources().openRawResource(this.id);
                        impl = BitmapRegionDecoder.newInstance(stream, false);

                        this.region.left = (int)srcX;
                        this.region.top = (int)srcY;
                        this.region.right = (int)(srcX+srcW);
                        this.region.bottom = (int)(srcY+srcH);
                        
                        BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
                        decodeOpts.inPreferredConfig = Bitmap.Config.ARGB_8888;                

                        if(srcW != dstW || srcH != dstH) {
                            final int sampleX = (int)Math.ceil(Math.log((double)srcW/(double)dstW) / Math.log(2d));
                            final int sampleY = (int)Math.ceil(Math.log((double)srcH/(double)dstH) / Math.log(2d));
                            
                            final int sample = (1<<Math.min(sampleX, sampleY));
                            
                            decodeOpts.inScaled = (sample > 1);
                            decodeOpts.inSampleSize = sample;
                        } else {
                            decodeOpts.inScaled = false;
                            decodeOpts.inSampleSize = 1;
                        }
                        decodeOpts.inJustDecodeBounds = false;

                        decoded = impl.decodeRegion(this.region, decodeOpts);
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

                    ByteBuffer pxbuf = ByteBuffer.wrap(buf);
                    pxbuf.order(ByteOrder.BIG_ENDIAN);
                    decoded.copyPixelsToBuffer(pxbuf);
                } finally {
                    if(decoded != null)
                        decoded.recycle();
                    if(impl != null)
                        impl.recycle();
                    if(stream != null)
                        try {
                            stream.close();
                        } catch(IOException ignored) {}
                }

                return (decoded != null) ? ReadResult.SUCCESS : ReadResult.CANCELED;
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
        public boolean isMultiResolution() {
            return true;
        }
    }

    private static class SubimageTileReader extends TileReader {

        private final TileReader impl;
        private final Rect region;
        
        public SubimageTileReader(TileReader impl, Rect region,
                AsynchronousIO asyncIO) {
            super(impl.getUri(), null, Integer.MAX_VALUE, asyncIO);

            this.impl = impl;
            this.region = region;
        }

        @Override
        public long getWidth() {
            return (this.region.right-this.region.left);
        }

        @Override
        public long getHeight() {
            return (this.region.bottom-this.region.top);
        }

        @Override
        public int getTileWidth() {
            return this.impl.getTileWidth();
        }

        @Override
        public int getTileHeight() {
            return this.impl.getTileHeight();
        }

        @Override
        public ReadResult read(long srcX, long srcY, long srcW, long srcH, int dstW, int dstH,
                byte[] buf) {
            
            return this.impl.read(srcX+this.region.left,
                                  srcY+this.region.top,
                                  srcW,
                                  srcH,
                                  dstW,
                                  dstH,
                                  buf);
        }

        @Override
        public Format getFormat() {
            return this.impl.getFormat();
        }

        @Override
        public Interleave getInterleave() {
            return this.impl.getInterleave();
        }

        @Override
        public boolean isMultiResolution() {
            return this.impl.isMultiResolution();
        }
    }
}
