
package com.atakmap.android.model.viewer.processing;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.opengl.GLES30;
import android.opengl.GLUtils;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.ZipVirtualFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class TextureHelper {

    private static final String TAG = "TextureHelper";

    public static File findTexture(String resourcePath) {
        String[] exts = {
                ".png", ".jpg", ".jpeg"
        };
        for (String ext : exts) {
            File image = new File(resourcePath.replace(".obj", ext));
            if (IOProviderFactory.exists(image))
                return image;
        }

        //pix4d style
        exts = new String[] {
                "_texture.png", "_texture.jpg", "_texture.jpeg"
        };
        for (String ext : exts) {
            File image = new File(
                    resourcePath.replace("_simplified_3d_mesh.obj", ext));
            if (IOProviderFactory.exists(image))
                return image;
        }

        return null;
    }

    public static int loadTextureForModel(String resourcePath) {
        File image = findTexture(resourcePath);
        if (image == null)
            return 0;
        return loadTextureFromPath(image.getAbsolutePath());
    }

    public static int loadTextureFromPath(String filePath) {
        if (filePath == null)
            return 0;

        // Read out texture bytes first
        byte[] bytes = null;
        try (InputStream is = getInputStream(filePath)) {
            bytes = FileSystemUtils.read(is);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read texture: " + filePath, e);
        }
        if (bytes == null)
            return 0;

        // Convert to bitmap
        Bitmap bitmap = null;
        try {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            options.inSampleSize = getSubsampleFactor(bytes, 4096);
            options.inPreferredConfig = Bitmap.Config.RGB_565;

            //read in the resource and bind texture to OpenGL
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length,
                    options);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read bitmap: " + filePath, e);
        }
        if (bitmap == null)
            return 0;

        final int[] textureHandle = new int[1];
        GLES30.glGenTextures(1, textureHandle, 0);

        if (textureHandle[0] == 0)
            throw new RuntimeException("Error generating texture name.");

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureHandle[0]);

        //set filtering
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST);

        //load the bitmap into the bound texture
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();

        return textureHandle[0];
    }

    private static InputStream getInputStream(String filePath)
            throws IOException {
        File f = new File(filePath);
        if (!IOProviderFactory.exists(f) && (filePath.contains(".zip/")
                || filePath.contains(".kmz/"))) {
            ZipVirtualFile zvf = new ZipVirtualFile(filePath);
            return zvf.openStream();
        } else
            return IOProviderFactory.getInputStream(f);
    }

    private static int getSubsampleFactor(byte[] bytes, int maxTexSize) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);

        // if the bounds are greater than the desired texture size
        if (opts.outWidth <= maxTexSize && opts.outHeight <= maxTexSize)
            return 0;

        // determine target subsample factor
        int rset = (int) Math.ceil(Math
                .log((double) Math.max(opts.outHeight, opts.outWidth)
                        / (double) maxTexSize)
                / Math.log(2));

        return 1 << rset;
    }
}
