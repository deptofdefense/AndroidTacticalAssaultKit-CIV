
package com.atakmap.android.imagecapture.opengl;

import android.graphics.Bitmap;

/**
 * Used with {@link GLOffscreenCaptureService} to draw content to the OpenGL canvas
 */
public interface GLOffscreenCaptureRequest {

    /**
     * Called on the GL thread before any other calls are made
     */
    void onStart();

    /**
     * Get the desired width of the bitmap capture
     * @return Width in pixels
     */
    int getWidth();

    /**
     * Get the desired height of the bitmap capture
     * @return Height in pixels
     */
    int getHeight();

    /**
     * Draw content to the current canvas provided by the render service
     * @param params Parameters used by capture service
     */
    void onDraw(GLOffscreenCaptureParams params);

    /**
     * The content is finished being drawn and converted to a bitmap
     * @param bmp Bitmap output
     */
    void onFinished(Bitmap bmp);
}
