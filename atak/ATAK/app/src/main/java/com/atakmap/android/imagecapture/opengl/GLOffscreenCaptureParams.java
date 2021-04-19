
package com.atakmap.android.imagecapture.opengl;

/**
 * Parameters used by the offscreen capture service
 */
public class GLOffscreenCaptureParams {

    public int width, height;
    public int frameBufferID;
    public int renderBufferID;

    public GLOffscreenCaptureParams() {
    }

    public GLOffscreenCaptureParams(GLOffscreenCaptureParams other) {
        this.width = other.width;
        this.height = other.height;
        this.frameBufferID = other.frameBufferID;
        this.renderBufferID = other.renderBufferID;
    }
}
