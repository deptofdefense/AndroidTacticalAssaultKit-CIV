package com.atakmap.map.layer.raster.drg;

import android.opengl.GLES30;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.gdal.GdalDatasetProjection2;
import com.atakmap.map.layer.raster.gdal.GdalGraphicUtils;
import com.atakmap.map.layer.raster.gdal.GdalTileReader;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory;
import com.atakmap.map.layer.raster.tilereader.TileReaderSpi;
import com.atakmap.map.layer.raster.tilereader.TileReaderSpi2;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.math.RectD;
import com.atakmap.math.Rectangle;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;

import java.io.File;
import java.nio.ByteBuffer;

public final class DRGTileReader extends TileReader {
    public final static TileReaderSpi SPI = new TileReaderSpi2() {

        @Override
        public String getName() {
            return "gdal";
        }

        @Override
        public TileReader create(String uri, TileReaderFactory.Options opts) {
            final File f = new File(uri);
            final DRGInfo drg = DRGInfo.parse(f);
            if(drg == null)
                return null;

            // borrow impl detail from GdalTileReader$Spi
            if(uri.charAt(0) != '/')
                return null;

            int tileWidth = 0;
            int tileHeight = 0;
            String cacheUri = null;
            AsynchronousIO asyncIO = null;
            if(opts != null) {
                tileWidth = opts.preferredTileWidth;
                tileHeight = opts.preferredTileHeight;
                cacheUri = opts.cacheUri;
                asyncIO = opts.asyncIO;
            }

            GdalTileReader impl = null;
            Dataset dataset = null;
            DatasetProjection2 proj = null;
            try {
                dataset = GdalLibrary.openDatasetFromFile(f);
                if(dataset == null)
                    return null;
                if(tileWidth <= 0)
                    tileWidth = dataset.GetRasterBand(1).GetBlockXSize();
                if(tileHeight <= 0)
                    tileHeight = dataset.GetRasterBand(1).GetBlockYSize();
                impl = new GdalTileReader(dataset, uri, tileWidth, tileHeight, cacheUri, asyncIO);
                proj = GdalDatasetProjection2.getInstance(dataset);
            } catch(Throwable ignored) {}
            if(impl == null)
                return null;
            if(proj == null)
                return null;

            // confirm parsed bounds lie completely within actual bounds. the
            // standard indicates that cells may not always be grid aligned
            GeoPoint ul = GeoPoint.createMutable();
            if(!proj.imageToGround(new PointD(0, 0), ul))
                return null;
            GeoPoint ur = GeoPoint.createMutable();
            if(!proj.imageToGround(new PointD(impl.getWidth(), 0), ur))
                return null;
            GeoPoint lr = GeoPoint.createMutable();
            if(!proj.imageToGround(new PointD(impl.getWidth(), impl.getHeight()), lr))
                return null;
            GeoPoint ll = GeoPoint.createMutable();
            if(!proj.imageToGround(new PointD(0, impl.getHeight()), ll))
                return null;
            final double minLat = MathUtils.min(ul.getLatitude(), ur.getLatitude(), lr.getLatitude(), ll.getLatitude());
            final double minLng = MathUtils.min(ul.getLongitude(), ur.getLongitude(), lr.getLongitude(), ll.getLongitude());
            final double maxLat = MathUtils.max(ul.getLatitude(), ur.getLatitude(), lr.getLatitude(), ll.getLatitude());
            final double maxLng = MathUtils.max(ul.getLongitude(), ur.getLongitude(), lr.getLongitude(), ll.getLongitude());
            if(!Rectangle.contains(minLng, minLat, maxLng, maxLat, drg.minLng, drg.minLat, drg.maxLng, drg.maxLat))
                return null;

            return new DRGTileReader(drg, proj, impl, asyncIO);
        }

        @Override
        public boolean isSupported(String uri) {
            File f = new File(uri);
            if(DRGInfo.parse(f) == null)
                return false;
            return GdalTileReader.SPI.isSupported(f.getAbsolutePath());
        }

        @Override
        public int getPriority() {
            return 2;
        }
    };

    final DRGInfo drg;
    final TileReader impl;
    final DatasetProjection2 proj;
    final RectD[] outerMask;

    DRGTileReader(DRGInfo drg, DatasetProjection2 proj, TileReader impl, AsynchronousIO asyncio) {
        super(impl.getUri(), null, Integer.MAX_VALUE, asyncio);

        this.drg = drg;
        this.proj = proj;
        this.impl = impl;

        PointD ul = new PointD(0, 0);
        proj.groundToImage(new GeoPoint(drg.maxLat, drg.minLng), ul);
        PointD ur = new PointD(0, 0);
        proj.groundToImage(new GeoPoint(drg.maxLat, drg.maxLng), ur);
        PointD lr = new PointD(0, 0);
        proj.groundToImage(new GeoPoint(drg.minLat, drg.maxLng), lr);
        PointD ll = new PointD(0, 0);
        proj.groundToImage(new GeoPoint(drg.minLat, drg.minLng), ll);

        final double innerTop = Math.max(ul.y, ur.y);
        final double innerLeft = Math.max(ul.x, ll.x);
        final double innerBottom = Math.min(lr.y, ll.y);
        final double innerRight = Math.min(ur.x, lr.x);

        // define the outer regions of the image that may contain masked pixels

        outerMask = new RectD[4];
        // top
        outerMask[0] = new RectD();
        outerMask[0].left = 0;
        outerMask[0].top = 0;
        outerMask[0].right = impl.getWidth();
        outerMask[0].bottom = innerTop;
        // right
        outerMask[1] = new RectD();
        outerMask[1].left = innerRight;
        outerMask[1].top = innerTop;
        outerMask[1].right = impl.getWidth();
        outerMask[1].bottom = innerBottom;
        // bottom
        outerMask[2] = new RectD();
        outerMask[2].left = 0;
        outerMask[2].top = innerBottom;
        outerMask[2].right = impl.getWidth();
        outerMask[2].bottom = impl.getHeight();
        // left
        outerMask[3] = new RectD();
        outerMask[3].left = 0;
        outerMask[3].top = innerTop;
        outerMask[3].right = innerLeft;
        outerMask[3].bottom = innerBottom;
    }

    @Override
    public long getWidth() {
        return impl.getWidth();
    }

    @Override
    public long getHeight() {
        return impl.getHeight();
    }

    @Override
    public int getTileWidth() {
        return impl.getTileWidth();
    }

    @Override
    public int getTileHeight() {
        return impl.getTileHeight();
    }

    @Override
    public ReadResult read(long srcX, long srcY, long srcW, long srcH, int dstW, int dstH, byte[] buf) {
        int srcTransferSize = dstW*dstH*impl.getPixelSize();
        byte[] srcData = new byte[srcTransferSize];
        final ReadResult retval = impl.read(srcX, srcY, srcW, srcH, dstW, dstH, srcData);
        if(retval != ReadResult.SUCCESS)
            return retval;
        // convert to RGBA from source format/interleave
        GdalGraphicUtils.fillBuffer(ByteBuffer.wrap(buf), srcData, dstW, dstH, impl.getInterleave(), impl.getFormat(), GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE);
        // alpha mask if necessary
        for(int i = 0; i < outerMask.length; i++) {
            // if the source region does not intersect the mask region, continue
            if(!Rectangle.intersects(srcX, srcY, srcX+srcW, srcY+srcH, outerMask[i].left, outerMask[i].top, outerMask[i].right, outerMask[i].bottom))
                continue;

            final double srcIsectMinX = Math.max(srcX, outerMask[i].left);
            final double srcIsectMinY = Math.max(srcY, outerMask[i].top);
            final double srcIsectMaxX = Math.max(srcX+srcW, outerMask[i].right);
            final double srcIsectMaxY = Math.max(srcY+srcH, outerMask[i].bottom);

            final double srcToDstX = (double)dstW / (double)srcW;
            final double srcToDstY = (double)dstH / (double)srcH;
            final double dstToSrcX = (double)srcW / (double)dstW;
            final double dstToSrcY = (double)srcH / (double)dstH;

            final int dstMinX = (int)((srcIsectMinX-srcX)*srcToDstX);
            final int dstMinY = (int)((srcIsectMinY-srcY)*srcToDstY);
            final int dstMaxX = (int)Math.min(Math.ceil((srcIsectMaxX-srcX)*srcToDstX), dstW-1);
            final int dstMaxY = (int)Math.min(Math.ceil((srcIsectMaxY-srcY)*srcToDstY), dstH-1);

            // iterate the source data and mask
            PointD img = new PointD(0d, 0d);
            GeoPoint geo = GeoPoint.createMutable();
            for (int y = dstMinY; y <= dstMaxY; y++) {
                for (int x = dstMinX; x <= dstMaxX; x++) {
                    img.x = srcX + x * dstToSrcX;
                    img.y = srcY + y * dstToSrcY;
                    if (!proj.imageToGround(img, geo))
                        continue;
                    // if the pixel is outside of the data region, mask it
                    if (!Rectangle.contains(drg.minLng, drg.minLat, drg.maxLng, drg.maxLat, geo.getLongitude(), geo.getLatitude()))
                        buf[((y * dstW) + x) * 4 + 3] = 0x00;
                }
            }
        }
        return retval;
    }

    @Override
    public Format getFormat() {
        return Format.RGBA;
    }

    @Override
    public Interleave getInterleave() {
        return Interleave.BIP;
    }
}
