
package com.atakmap.android.imagecapture;

import android.opengl.GLSurfaceView;

import com.atakmap.android.maps.graphics.GLCapture;
import com.atakmap.android.tilecapture.TileCapture;
import com.atakmap.lang.Unsafe;
import com.atakmap.opengl.GLES20FixedPipeline;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Custom version of map view capture that returns
 * raw pixels rather than a bitmap
 * @deprecated Use {@link TileCapture} instead for much
 * better performance and results
 */
@Deprecated
public class GLImageCapture extends GLCapture {

    private GLCapturableMapView imcap;

    public GLImageCapture(GLSurfaceView view) {
        super(view);
    }

    public GLImageCapture(GLCapturableMapView imcap, GLSurfaceView view,
            int x, int y, int width, int height) {
        super(view, x, y, width, height);
        this.imcap = imcap;
    }

    @Override
    protected void draw() {
        imcap.drawImpl();
    }

    @Override
    protected void readPixels(final CaptureCallback callback) {
        if (!(callback instanceof RawCaptureCallback)) {
            super.readPixels(callback);
            return;
        }
        // read pixels from texture
        ByteBuffer rgba = Unsafe.allocateDirect(this.width * this.height * 4);
        rgba.order(ByteOrder.nativeOrder());

        GLES20FixedPipeline.glReadPixels(this.x, this.y, this.width,
                this.height, GLES20FixedPipeline.GL_RGBA,
                GLES20FixedPipeline.GL_UNSIGNED_BYTE, rgba);
        rgba.clear();

        ((RawCaptureCallback) callback).onCaptureComplete(
                this, rgba, this.width, this.height);
    }

    public interface RawCaptureCallback extends CaptureCallback {
        void onCaptureComplete(GLCapture capture, ByteBuffer rgba, int width,
                int height);
    }
}
