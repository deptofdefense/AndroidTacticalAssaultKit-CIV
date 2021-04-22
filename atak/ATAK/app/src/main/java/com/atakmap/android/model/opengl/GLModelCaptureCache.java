
package com.atakmap.android.model.opengl;

import android.graphics.Bitmap;

import com.atakmap.android.imagecapture.opengl.GLOffscreenCaptureService;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Helper class for writing to and reading from a cached model capture
 */
public class GLModelCaptureCache {

    private static final String TAG = "GLModelCaptureCache";

    // Temporary storage for render captures, so we can read from a file
    // instead of capturing every time
    public static final File RENDER_CACHE_DIR = FileSystemUtils.getItem(
            FileSystemUtils.TMP_DIRECTORY + "/model_captures");

    private final String _subPath;

    public GLModelCaptureCache(String subPath) {
        _subPath = subPath;
    }

    /**
     * Get or create bitmap display of the vehicle
     * If a cached image of the bitmap does not exist, it is created
     * @param req The capture request to use if the bitmap does not exist
     *            Null to return null if cached bitmap does not exist
     * @return Bitmap of the vehicle
     */
    public Bitmap getBitmap(GLModelCaptureRequest req) {
        File cacheFile = new File(RENDER_CACHE_DIR, _subPath + ".bmp");

        // Check if cached screenshot already exists we can quickly read from
        if (IOProviderFactory.exists(cacheFile)) {
            try {
                // Need to convert from bytes to integers for createBitmap
                byte[] d = FileSystemUtils.read(cacheFile);
                int[] pixels = new int[(d.length / 4) - 1];
                int p = 0;
                int width = ((d[0] & 0xFF) << 8) | (d[1] & 0xFF);
                int height = ((d[2] & 0xFF) << 8) | (d[3] & 0xFF);
                for (int i = 4; i < d.length; i += 4) {
                    int a = (d[i] & 0xFF) << 24;
                    int r = d[i + 1] * 65536;
                    int g = d[i + 2] * 256;
                    int b = d[i + 3];
                    pixels[p++] = a | (r + g + b);
                }
                return Bitmap.createBitmap(pixels, width, height,
                        Bitmap.Config.ARGB_8888);
            } catch (Exception e) {
                FileSystemUtils.delete(cacheFile);
                Log.e(TAG, "Failed to read vehicle render cache: " + cacheFile);
            }
        }

        // No cached bitmap and no request to make
        if (req == null)
            return null;

        // Setup capture request for the model
        final Bitmap[] image = new Bitmap[1];
        req.setCallback(new GLModelCaptureRequest.Callback() {
            @Override
            public void onCaptureFinished(File file, Bitmap bmp) {
                image[0] = bmp;
                synchronized (GLModelCaptureCache.this) {
                    GLModelCaptureCache.this.notify();
                }
            }
        });
        GLOffscreenCaptureService.getInstance().request(req);

        // Wait for renderer for finish
        synchronized (this) {
            while (image[0] == null) {
                try {
                    this.wait();
                } catch (Exception ignored) {
                }
            }
        }

        // Save bitmap data straight to file (no header data needed)
        File cacheDir = cacheFile.getParentFile();
        if (IOProviderFactory.exists(cacheDir)
                || IOProviderFactory.mkdirs(cacheDir)) {
            int width = image[0].getWidth();
            int height = image[0].getHeight();
            FileOutputStream fos = null;
            try {
                int[] pixels = new int[width * height];
                image[0].getPixels(pixels, 0, width, 0, 0, width, height);
                byte[] b = new byte[(pixels.length + 1) * 4];
                b[0] = (byte) (width >> 8);
                b[1] = (byte) (width & 0xFF);
                b[2] = (byte) (height >> 8);
                b[3] = (byte) (height & 0xFF);
                int i = 4;
                for (int p : pixels) {
                    b[i] = (byte) (p >> 24);
                    b[i + 1] = (byte) ((p >> 16) & 0xFF);
                    b[i + 2] = (byte) ((p >> 8) & 0xFF);
                    b[i + 3] = (byte) (p & 0xFF);
                    i += 4;
                }
                fos = IOProviderFactory.getOutputStream(cacheFile);
                fos.write(b);
            } catch (Exception e) {
                FileSystemUtils.delete(cacheFile);
                Log.e(TAG, "Failed to read model render cache: " + cacheFile);
            } finally {
                try {
                    if (fos != null)
                        fos.close();
                } catch (Exception ignored) {
                }
            }
        }

        return image[0];
    }

    public Bitmap getBitmap() {
        return getBitmap(null);
    }
}
