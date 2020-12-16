
package com.atakmap.android.tilecapture.imagery;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

import com.atakmap.android.imagecapture.TiledCanvas;
import com.atakmap.android.tilecapture.TileCapture;
import com.atakmap.android.tilecapture.TileCaptureBounds;
import com.atakmap.android.tilecapture.TileCaptureParams;
import com.atakmap.android.tilecapture.TileCaptureTask;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.gdal.VSIFileFileSystemHandler;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * Task for capturing map tiles to a TIFF file for later use
 */
public class ImageryCaptureTask extends TileCaptureTask {

    public interface Callback {
        void onFinishCapture(TileCapture tileCapture,
                ImageryCaptureParams params,
                File outFile);
    }

    public interface ProgressCallback {
        void onCaptureProgress(int tileNum, int tileCount);
    }

    protected final ImageryCaptureParams _params;
    protected final Callback _callback;

    protected ProgressCallback _progressCallback;
    protected int _tileCount;
    protected Dataset _dataset;
    protected File _outFile;

    protected TileCaptureBounds _bounds;
    protected int _fullWidth, _fullHeight;

    public ImageryCaptureTask(TileCapture tileCap, ImageryCaptureParams params,
            Callback cb) {
        super(tileCap);
        _params = params;
        _callback = cb;
    }

    public void setProgressCallback(ProgressCallback cb) {
        _progressCallback = cb;
    }

    @Override
    public Boolean doInBackground(TileCaptureParams... params) {
        super.doInBackground(_params);
        if (_dataset != null)
            _dataset.delete(); // Close the file
        return FileSystemUtils.isFile(_outFile);
    }

    @Override
    public boolean onStartCapture(int tileCount, int tw, int th, int fw,
            int fh) {
        _tileCount = tileCount;

        _bounds = _tileCapture.getBounds(_params);
        _fullWidth = _bounds.imageWidth;
        _fullHeight = _bounds.imageHeight;

        File dir = _params.outputDirectory;

        if (dir == null)
            return false;

        if (!IOProviderFactory.exists(dir) && !IOProviderFactory.mkdirs(dir))
            return false;

        _outFile = new File(dir, "." + (new CoordinatedTime())
                .getMilliseconds() + "_tiles.tiff");
        Driver driver = gdal.GetDriverByName("GTiff");
        if (driver == null)
            return false;

        String path = _outFile.getAbsolutePath();
        if (!IOProviderFactory.isDefault()) {
            path = VSIFileFileSystemHandler.PREFIX + path;
        }
        _dataset = driver.Create(path,
                _fullWidth, _fullHeight, 3, new String[] {
                        "TILED=YES", "COMPRESS=DEFLATE", "ZLEVEL=9",
                        "BLOCKXSIZE=" + tw, "BLOCKYSIZE=" + th
                });
        return _dataset != null && FileSystemUtils.isFile(_outFile);
    }

    @Override
    public boolean onCaptureTile(Bitmap tile, int tileNum, int tileColumn,
            int tileRow) {
        if (_dataset == null || isCancelled())
            return false;

        if (_progressCallback != null)
            _progressCallback.onCaptureProgress(tileNum, _tileCount);

        // Skip tile
        if (tile == null)
            return true;

        writeTile(tile, tileColumn, tileRow);
        return true;
    }

    @Override
    public void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        if (_callback != null)
            _callback.onFinishCapture(_tileCapture, _params, _outFile);
    }

    protected void writeTile(Bitmap tile, int tileColumn, int tileRow) {
        int tw = tile.getWidth();
        int th = tile.getHeight();

        // At this point we need to perform a few corrections:
        // 1) Map to a 1:1 projection
        // 2) If grid is present, map imagery to the grid's exact coordinates

        // Map out the transformed rectangle of imagery we need to modify
        int tileX = tw * tileColumn;
        int tileY = th * tileRow;
        float[] pts = new float[8];
        pts[0] = pts[6] = tileX;
        pts[1] = pts[3] = tileY;
        pts[2] = pts[4] = tileX + tw;
        pts[5] = pts[7] = tileY + th;
        _bounds.tileToPixel.mapPoints(pts);

        // Get bounds of the transformed rectangle
        float minX = _fullWidth, maxX = 0, minY = _fullHeight, maxY = 0;
        for (int i = 0; i < pts.length; i += 2) {
            float x = pts[i], y = pts[i + 1];
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        // The final integer coordinates and dimensions of the tile
        int outX = (int) Math.floor(Math.max(minX, 0));
        int outY = (int) Math.floor(Math.max(minY, 0));
        int outWidth = (int) Math.ceil(Math.min(maxX, _fullWidth)) - outX;
        int outHeight = (int) Math.ceil(Math.min(maxY, _fullHeight)) - outY;

        // Coordinates are out of bounds. Grid is probably too thin to fit
        // more tiles. Just ignore the tile.
        if (outWidth <= 0 || outHeight <= 0)
            return;

        // First read any existing data in this space
        int channels = _dataset.GetRasterCount();
        ByteBuffer rgba = Unsafe
                .allocateDirect(outWidth * outHeight * channels);
        _dataset.ReadRaster_Direct(outX, outY, outWidth, outHeight, outWidth,
                outHeight, gdalconstConstants.GDT_Byte, rgba, null, channels,
                outWidth * channels, 1);

        // Use this as the base tile to draw upon
        Bitmap dstBmp = TiledCanvas.bytesToBitmap(rgba, outWidth, outHeight,
                channels);
        Unsafe.free(rgba);
        Paint p = new Paint();
        p.setFilterBitmap(true);
        Canvas c = new Canvas(dstBmp);

        Matrix m = new Matrix();

        // Map to exact grid coordinates and scale so projection is 1:1
        m.postTranslate(tileX, tileY);
        m.postConcat(_bounds.tileToPixel);
        m.postTranslate(-tileX, -tileY);

        // Draw the new tile into the space
        c.translate(tileX - outX, tileY - outY);
        c.drawBitmap(tile, m, p);

        // No longer need source tile
        tile.recycle();

        // Convert back to bytes while recycling output bitmap
        rgba = Unsafe.allocateDirect(outWidth * outHeight * 4);
        TiledCanvas.bitmapToBytes(dstBmp, rgba, true);

        // Save corrected tile to raster
        _dataset.WriteRaster_Direct(outX, outY, outWidth, outHeight,
                outWidth, outHeight, gdalconstConstants.GDT_Byte, rgba,
                null, 4, outWidth * 4, 1);
        Unsafe.free(rgba);
    }
}
