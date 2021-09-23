
package com.atakmap.android.imagecapture;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.gdal.VSIFileFileSystemHandler;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconst;
import org.gdal.gdalconst.gdalconstConstants;

import java.io.File;
import java.nio.ByteBuffer;

/**
 * Image which is split into tiles stored within a file
 * This is a method of writing to/reading from bitmaps without
 * loading the entire image into memory.
 * Also allows canvas drawing over multiple tiles
 *
 * Supported inputs: Monochrome, RGB, RGBA
 * Supported outputs: RGB, RGBA
 */

public class TiledCanvas extends Canvas {

    private static final String TAG = "TiledCanvas";
    private static final int ARGB = 4;

    // For tracking progress when working with multiple tiles
    public interface ProgressCallback {
        void onProgress(int progress, int max);
    }

    protected final File _file;
    protected final int _fullWidth;
    protected final int _fullHeight;
    protected final int _tileWidth;
    protected final int _tileHeight;
    protected final int _channels;
    protected final boolean _valid;
    protected boolean _postDrawn;

    public TiledCanvas(final File file, final int tileWidth,
            final int tileHeight) {
        _file = file;
        Dataset ds = GdalLibrary.openDatasetFromFile(_file,
                gdalconst.GA_ReadOnly);
        if (ds != null) {
            _fullWidth = ds.GetRasterXSize();
            _fullHeight = ds.GetRasterYSize();
            _channels = ds.GetRasterCount();
            ds.delete();
            _tileWidth = Math.min(tileWidth, _fullWidth);
            _tileHeight = Math.min(tileHeight, _fullHeight);
        } else {
            _tileWidth = tileWidth;
            _tileHeight = tileHeight;
            _fullWidth = -1;
            _fullHeight = -1;
            _channels = 0;
            Log.e(TAG, "Failed to open image " + _file);
        }
        _valid = _channels > 0 && _tileWidth >= 0 && _tileWidth <= _fullWidth
                && _tileHeight >= 0 && _tileHeight <= _fullHeight;
        _postDrawn = false;
    }

    public File getFile() {
        return _file;
    }

    @Override
    public int getWidth() {
        return _fullWidth;
    }

    @Override
    public int getHeight() {
        return _fullHeight;
    }

    public int getTileWidth() {
        return _tileWidth;
    }

    public int getTileHeight() {
        return _tileHeight;
    }

    public int getChannels() {
        return _channels;
    }

    public int getTileCount() {
        return getTileCountX() * getTileCountY();
    }

    public int getTileCountX() {
        return (int) Math.ceil((float) _fullWidth / (float) _tileWidth);
    }

    public int getTileCountY() {
        return (int) Math.ceil((float) _fullHeight / (float) _tileHeight);
    }

    public boolean valid() {
        return _valid;
    }

    /**
     * Read a tile based on its cell position
     * I.e. For a 3x3 tiled image the middle tile cell position
     * would be tileX = 1, tileY = 1
     * @param tileX X-position of the tile
     * @param tileY Y-position of the tile
     * @return Bitmap copy of the tile or null if failed
     */
    public Bitmap readTile(int tileX, int tileY) {
        return readTile(tileX * _tileWidth, tileY * _tileHeight,
                _tileWidth, _tileHeight);
    }

    /**
     * Read a tile from the image into a bitmap
     * @param x Left-wise position to read from
     * @param y Top-wise position to read from
     * @param width Width of the tile to read
     * @param height Height of the tile to read
     * @return Bitmap copy of the tile or null if failed
     */
    public Bitmap readTile(int x, int y, int width, int height) {
        if (!_valid)
            return null;
        Dataset ds = GdalLibrary.openDatasetFromFile(_file,
                gdalconst.GA_ReadOnly);
        if (ds == null)
            return null;
        width = Math.min(width, _fullWidth - x);
        height = Math.min(height, _fullHeight - y);
        ByteBuffer byteData = Unsafe.allocateDirect(width * height * _channels);
        int err = ds.ReadRaster_Direct(x, y, width, height, width, height,
                gdalconstConstants.GDT_Byte, byteData, null,
                _channels, width * _channels, 1);
        ds.delete();
        try {
            if (err == gdalconst.CE_Failure)
                return null;
            return bytesToBitmap(byteData, width, height, _channels);
        } finally {
            Unsafe.free(byteData);
        }
    }

    /**
     * Write a bitmap to the tile at the specified cell position
     * See {@link TiledCanvas#readTile(int, int)} for more info.
     * @param bmp Bitmap to read from
     * @param tileX X-position of the tile
     * @param tileY Y-position of the tile
     * @return True if successful
     */
    public boolean writeTile(Bitmap bmp, int tileX, int tileY) {
        return writeTile(bmp, tileX, tileY, true, true);
    }

    /**
     * Write a bitmap to the image at a specified position
     * @param bmp Bitmap to read from
     * @param x Left-wise position to write to
     * @param y Top-wise position to write to
     * @param tileCell True if the x,y pair is a tile cell location
     * @param recycle True if it's okay to recycle the bitmap
     * @return True if successful
     */
    public boolean writeTile(Bitmap bmp, int x, int y, boolean tileCell,
            boolean recycle) {
        if (!_valid || bmp == null || bmp.isRecycled())
            return false;
        int width = bmp.getWidth(), height = bmp.getHeight();
        if (tileCell) {
            x *= _tileWidth;
            y *= _tileHeight;
        }
        Dataset ds = GdalLibrary.openDatasetFromFile(_file,
                gdalconst.GA_Update);
        if (ds == null)
            return false;
        ByteBuffer byteData = Unsafe.allocateDirect(width * height * ARGB);
        bitmapToBytes(bmp, byteData, recycle);
        int err = ds.WriteRaster_Direct(x, y, width, height,
                width, height, gdalconstConstants.GDT_Byte, byteData,
                null, ARGB, width * ARGB, 1);
        ds.delete();
        Unsafe.free(byteData);
        return err != gdalconst.CE_Failure;
    }

    /**
     * Copy the entire image to another file type
     * @param file File to copy to
     * @param format File format
     * @param options List of options (key=value)
     * @return True if successful
     */
    public boolean copyToFile(File file, String format, String[] options) {
        if (!_valid || file == null)
            return false;
        Dataset dsIn = null, dsOut = null;
        try {
            // Open input file
            dsIn = GdalLibrary.openDatasetFromFile(_file,
                    gdalconst.GA_ReadOnly);
            if (dsIn == null)
                return false;

            // Create output file (delete if already exists)
            Driver driver = gdal.GetDriverByName(format);
            if (driver == null)
                return false;

            if (IOProviderFactory.exists(file))
                FileSystemUtils.delete(file);
            String dstPath = file.getAbsolutePath();
            if (!IOProviderFactory.isDefault())
                dstPath = VSIFileFileSystemHandler.PREFIX + dstPath;
            dsOut = driver.CreateCopy(dstPath, dsIn, options);
            return dsOut != null;
        } finally {
            // Close everything
            if (dsIn != null)
                dsIn.delete();
            if (dsOut != null)
                dsOut.delete();
        }
    }

    public boolean copyToFile(File file, CompressFormat format, int quality) {
        String[] options = new String[] {
                "QUALITY=" + quality,
        };
        return copyToFile(file, format.name(), options);
    }

    /**
     * Create a thumbnail of the entire image
     * Aspect ratio will be maintained, meaning desired width/height
     * may not be the same as the output width/height
     * @param width Desired width
     * @param height Desired height
     * @return The thumbnail as a bitmap
     */
    public Bitmap createThumbnail(int width, int height) {
        if (!_valid)
            return null;
        Dataset ds = GdalLibrary.openDatasetFromFile(_file,
                gdalconst.GA_ReadOnly);
        if (ds == null)
            return null;
        // Maintain aspect ratio for thumbnail
        double shrink = Math.max((float) _fullWidth / width,
                (float) _fullHeight / height);
        width = (int) Math.round(_fullWidth / shrink);
        height = (int) Math.round(_fullHeight / shrink);
        Bitmap ret = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        Canvas c = new Canvas(ret);
        Paint paint = new Paint();
        paint.setFilterBitmap(true);
        for (int y = 0; y < getTileCountY(); y++) {
            for (int x = 0; x < getTileCountX(); x++) {
                // Read tile
                int tWidth = Math.min(_tileWidth, _fullWidth - x * _tileWidth);
                int tHeight = Math.min(_tileHeight, _fullHeight - y
                        * _tileHeight);
                ByteBuffer byteData = Unsafe.allocateDirect(tWidth * tHeight
                        * _channels);
                int err = ds.ReadRaster_Direct(x * _tileWidth, y * _tileHeight,
                        tWidth, tHeight, tWidth, tHeight,
                        gdalconstConstants.GDT_Byte, byteData,
                        null, _channels, tWidth * _channels, 1);
                if (err == gdalconst.CE_Failure) {
                    ret.recycle();
                    ds.delete();
                    Unsafe.free(byteData);
                    return null;
                }
                // Write tile
                Bitmap tile = bytesToBitmap(byteData, tWidth,
                        tHeight, _channels);
                Unsafe.free(byteData);
                Rect inRect = new Rect(x * _tileWidth, y * _tileHeight,
                        x * _tileWidth + tWidth, y * _tileHeight + tHeight);
                RectF outRect = new RectF((float) (inRect.left / shrink),
                        (float) (inRect.top / shrink),
                        (float) (inRect.right / shrink),
                        (float) (inRect.bottom / shrink));
                c.drawBitmap(tile, null, outRect, paint);
            }
        }
        ds.delete();
        return ret;
    }

    /**
     * Run post-processing drawing on the entire image (1 tile at a time)
     * Result is automatically saved back to the original image
     * NOTE: This is meant to be run once per canvas.
     *
     * @param postDraw The post-processor
     * @param callback Progress callback
     * @return True if successful
     */
    public boolean postDraw(CapturePP postDraw, ProgressCallback callback) {
        if (!_valid || postDraw == null)
            return false;
        if (_postDrawn)
            Log.w(TAG,
                    "Running postDraw on an image that was already drawn to!");

        Dataset ds = null;
        try {
            // Open input file
            ds = GdalLibrary.openDatasetFromFile(_file, gdalconst.GA_Update);
            if (ds == null)
                return false;
            // Copy data
            ByteBuffer byteData = Unsafe.allocateDirect(_tileWidth
                    * _tileHeight * ARGB);
            int prog = 0, total = getTileCount();
            for (int y = 0; y < _fullHeight; y += _tileHeight) {
                for (int x = 0; x < _fullWidth; x += _tileWidth) {
                    int tw = Math.min(_tileWidth, _fullWidth - x), th = Math
                            .min(_tileHeight, _fullHeight - y);
                    //Log.d(TAG, "Reading tile[" + x + "," + y + "]");
                    // Read input tile
                    byteData.clear();
                    int err = ds.ReadRaster_Direct(x, y, tw, th, tw, th,
                            gdalconstConstants.GDT_Byte, byteData, null,
                            _channels, tw * _channels, 1);
                    if (err == gdalconst.CE_Failure) {
                        Log.e(TAG, "Failed to read tile[" + x + "," + y + "]");
                        continue;
                    }

                    // Convert to bitmap and draw
                    Bitmap bmp = bytesToBitmap(byteData, tw, th, _channels);
                    setBitmap(bmp);
                    translate(-x, -y);
                    postDraw.drawElements(this);
                    translate(x, y);

                    // Convert back to bytes
                    bitmapToBytes(bmp, byteData, true);

                    //Log.d(TAG, "Writing tile[" + x + "," + y + "]");
                    // Write output tile
                    err = ds.WriteRaster_Direct(x, y, tw, th, tw, th,
                            gdalconstConstants.GDT_Byte, byteData,
                            null, ARGB, tw * ARGB, 1);
                    if (err == gdalconst.CE_Failure) {
                        Log.e(TAG, "Failed to write tile[" + x + "," + y + "]");
                        continue;
                    }
                    if (callback != null)
                        callback.onProgress(++prog, total);
                }
            }
            Unsafe.free(byteData);
            return _postDrawn = true;
        } finally {
            // Close everything
            if (ds != null)
                ds.delete();
        }
    }

    /**
     * Run post-processing drawing on the entire image (1 tile at a time)
     * Result is automatically saved back to the original image
     * NOTE: This is meant to be run once per canvas.
     *
     * @param postDraw The post-processor
     * @return True if successful
     */
    public boolean postDraw(CapturePP postDraw) {
        return postDraw(postDraw, null);
    }

    /**
     * Convert an array of RGB or RGBA data to an ARGB bitmap
     * @param byteData Byte buffer
     * @param width Width of the bitmap
     * @param height Height of the bitmap
     * @param channels Number of channels
     * @return A newly created bitmap
     */
    public static Bitmap bytesToBitmap(ByteBuffer byteData, int width,
            int height, int channels) {
        if (channels == ARGB) {
            // Faster than manual conversion of pixels
            Bitmap bmp = Bitmap.createBitmap(width, height, Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(byteData);
            byteData.clear();
            return bmp;
        }
        int[] pixels = new int[width * height];
        int[] rgb = new int[] {
                255, 255, 255
        };
        int p = 0;
        for (int i = 0; i < pixels.length; i++) {
            if (channels == 1) {
                // Monochrome
                int value = byteData.get(i);
                pixels[i] = rgb(value, value, value);
            } else {
                // RGB
                rgb[0] = rgb[1] = rgb[2] = 255;
                for (int c = 0; c < channels && c < rgb.length; c++) {
                    rgb[c] = byteData.get(p + c);
                    if (rgb[c] < 0)
                        rgb[c] += 256;
                }
                p += channels;
                pixels[i] = rgb(rgb[0], rgb[1], rgb[2]);
            }
        }
        Bitmap bmp = Bitmap.createBitmap(width, height, Config.ARGB_8888);
        bmp.setPixels(pixels, 0, width, 0, 0, width, height);
        byteData.clear();
        return bmp;
    }

    // Faster than Color.rgb
    private static int rgb(int red, int green, int blue) {
        return -16777216 + (red * 65536) + (green * 256) + blue;
    }

    /**
     * Convert a bitmap to an array of bytes
     * @param bmp Bitmap to convert
     * @param dest Byte array to store converted RGBA pixels
     * @param recycle True to recycle the bitmap
     */
    public static void bitmapToBytes(Bitmap bmp, ByteBuffer dest,
            boolean recycle) {
        if (bmp.isRecycled()) {
            Log.e(TAG, "Cannot read bytes from recycled bitmap.");
            return;
        }
        bmp.copyPixelsToBuffer(dest);
        if (recycle)
            bmp.recycle();
        dest.clear();
    }
}
