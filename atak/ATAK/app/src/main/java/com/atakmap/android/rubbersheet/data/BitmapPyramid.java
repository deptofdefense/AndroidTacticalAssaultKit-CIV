
package com.atakmap.android.rubbersheet.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.atakmap.android.math.MathUtils;
import com.atakmap.coremap.concurrent.NamedThreadFactory;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.map.opengl.GLMapView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bitmap at varying resolutions
 */
public class BitmapPyramid {

    private static final String TAG = "BitmapPyramid";

    public static void disposeStatic() {
        bmpService.shutdown();
    }

    private final File _file;
    private final String _path;
    private final int _srcWidth, _srcHeight;
    private final int _numPixels;

    private Bitmap _bmp;
    private double _lastScale = 0, _lastWidthM, _lastLengthM;
    private int _lastLatitude = 180;
    private int _inSampleSize = 0;
    private boolean _disposed = false;
    private boolean _loading = false;

    public BitmapPyramid(File f) {
        _file = f;
        _path = f.getAbsolutePath();
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (FileInputStream fis = IOProviderFactory
                .getInputStream(new File(_path))) {
            BitmapFactory.decodeStream(fis, null, opts);
        } catch (IOException ignored) {
        }
        _srcWidth = opts.outWidth;
        _srcHeight = opts.outHeight;
        _numPixels = _srcWidth * _srcHeight;
    }

    public File getFile() {
        return _file;
    }

    public synchronized Bitmap getBitmap(GLMapView ortho, double widthM,
            double lengthM) {
        if (_disposed || _numPixels <= 0)
            return null;

        if (_loading)
            return _bmp;

        double scale = ortho.currentPass.drawMapResolution;
        double latitude = ortho.currentPass.drawLat;
        if (_bmp != null && Double.compare(scale, _lastScale) == 0
                && Double.compare(widthM, _lastWidthM) == 0
                && Double.compare(lengthM, _lastLengthM) == 0
                && _lastLatitude == (int) latitude)
            return _bmp;

        _lastScale = scale;
        _lastLatitude = (int) latitude;
        _lastWidthM = widthM;
        _lastLengthM = lengthM;

        double mercatorscale = Math
                .cos(latitude / ConversionFactors.DEGREES_TO_RADIANS);
        if (mercatorscale < 0.0001)
            mercatorscale = 0.0001;
        double meters = scale * mercatorscale;
        float scaleX = (float) (widthM / (meters * _srcWidth));
        float scaleY = (float) (lengthM / (meters * _srcHeight));
        float minScale = 1 / Math.min(scaleX, scaleY);
        final int sampleSize = minScale > 1 ? 1 << (int) MathUtils.log2(
                minScale) : 1;
        if (_bmp == null || sampleSize != _inSampleSize) {
            _inSampleSize = sampleSize;
            _loading = true;
            bmpService.execute(new Runnable() {
                @Override
                public void run() {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inPreferredConfig = Bitmap.Config.RGB_565;
                    o.inSampleSize = sampleSize;
                    Bitmap bmp;
                    try (FileInputStream fis = IOProviderFactory
                            .getInputStream(new File(_path))) {
                        bmp = BitmapFactory.decodeStream(fis, null, o);
                    } catch (IOException e) {
                        _loading = false;
                        return;
                    }
                    if (bmp.getConfig() == null) {
                        // No defined config - attempt to correct
                        int[] p = new int[bmp.getWidth() * bmp.getHeight()];
                        bmp.getPixels(p, 0, bmp.getWidth(), 0, 0,
                                bmp.getWidth(), bmp.getHeight());
                        bmp = Bitmap.createBitmap(p, bmp.getWidth(),
                                bmp.getHeight(), Bitmap.Config.ARGB_8888);
                    } else if (o.outWidth > 1
                            && (Math.abs(o.outWidth) % 2) == 1) {
                        // Width needs to be mod2 or else drawn texture
                        // has some weird horizontal shifting
                        bmp = Bitmap.createBitmap(bmp, 0, 0, o.outWidth - 1,
                                o.outHeight);
                    }
                    synchronized (BitmapPyramid.this) {
                        if (!_disposed)
                            setBitmap(bmp);
                        else
                            bmp.recycle();
                        _loading = false;
                    }
                }
            });
        }
        return _bmp;
    }

    public synchronized boolean isEmpty() {
        return _bmp != null;
    }

    public synchronized void dispose() {
        _disposed = true;
        setBitmap(null);
    }

    public synchronized void setBitmap(Bitmap bmp) {
        if (_bmp != bmp) {
            /*int oldW = 0, oldH = 0, newW = 0, newH = 0;
            if (_bmp != null) {
                oldW = _bmp.getWidth();
                oldH = _bmp.getHeight();
                _bmp.recycle();
            }
            if (bmp != null) {
                newW = bmp.getWidth();
                newH = bmp.getHeight();
            }
            Log.d(TAG, _file.getName() + " changing from " + oldW + "x"
                    + oldH + " -> " + newW + "x" + newH);*/
            _bmp = bmp;
        }
    }

    private static final ExecutorService bmpService = Executors
            .newFixedThreadPool(5, new NamedThreadFactory(
                    "RubberSheetBitmapService"));
}
