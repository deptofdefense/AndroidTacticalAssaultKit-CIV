
package com.atakmap.android.maps.graphics;

import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLTexture;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Abstract class used to capture GL rendering in a
 * {@link android.graphics.Bitmap Bitmap}. The capture will happen
 * asynchronously on the GL thread; delivery of the captured pixels will occur
 * on a separate user thread.
 * 
 * <P>This class takes care of the setup and teardown of the offscreen textures
 * and buffer objects; subclasses are responsible for implementing the rendering
 * code to produce the scene to be captured.
 * 
 * 
 */
public abstract class GLCapture {

    private static final String TAG = "GLCapture";

    protected final GLSurfaceView view;
    protected final int x;
    protected final int y;
    protected final int width;
    protected final int height;

    /**
     * Creates a new instance. The capture region is set to the extents of the
     * specified {@link GLSurfaceView}.
     * 
     * @param view  A {@link GLSurfaceView} instance. The instance will be used
     *              to queue the offscreen capture on the GL context thread. 
     */
    public GLCapture(GLSurfaceView view) {
        this(view, 0, 0, view.getWidth(), view.getHeight());
    }

    /**
     * Creates a new instance.
     * 
     * @param view      A {@link GLSurfaceView} instance. The instance will be
     *                  used to queue the offscreen capture on the GL context
     *                  thread.
     * @param x         The x-coordinate of the region of the frame buffer to
     *                  capture
     * @param y         The y-coordinate of the region of the frame buffer to
     *                  capture
     * @param width     The width of the region of the frame buffer to capture
     * @param height    The height of the region of the frame buffer to capture
     */
    public GLCapture(GLSurfaceView view, int x, int y, int width, int height) {
        this.view = view;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * Subclasses should override to perform the GL rendering they want captured
     * for the callback.
     */
    protected abstract void draw();

    /**
     * Performs an asynchronous capture. The callback will always receive an
     * invocation of {@link CaptureCallback#onCaptureStarted(GLCapture)} when
     * the capture begins and one of
     * {@link CaptureCallback#onCaptureComplete(GLCapture, Bitmap)} or
     * {@link CaptureCallback#onCaptureError(GLCapture, Throwable)} when the
     * capture has either completed successfully or terminated due to an error.
     * 
     * @param callback  The callback for the capture. May not be
     *                  <code>null</code>.
     */
    public final void capture(final CaptureCallback callback) {
        if (callback == null)
            throw new IllegalArgumentException();

        this.view.queueEvent(new Runnable() {
            @Override
            public void run() {
                GLCapture.this.captureImpl(callback);
            }
        });
    }

    // XXX - consider retaining FBO/depth buffer/texture if instance will be
    //       used more than once
    private void captureImpl(final CaptureCallback callback) {
        int[] fbo = new int[1];
        int[] depthBuffer = new int[1];
        GLTexture texture = null;
        try {
            callback.onCaptureStarted(this);

            // create texture
            texture = new GLTexture(this.width, this.height,
                    Bitmap.Config.ARGB_8888);
            texture.init();

            // create FBO/depth buffer
            GLES20FixedPipeline.glGenFramebuffers(1, fbo, 0);
            if (fbo[0] == 0)
                throw new RuntimeException("Failed to create FBO");

            GLES20FixedPipeline.glGenRenderbuffers(1, depthBuffer, 0);
            if (depthBuffer[0] == 0)
                throw new RuntimeException("Failed to create Depth Buffer");

            GLES20FixedPipeline.glBindRenderbuffer(
                    GLES20FixedPipeline.GL_RENDERBUFFER,
                    depthBuffer[0]);

            GLES20FixedPipeline.glRenderbufferStorage(
                    GLES20FixedPipeline.GL_RENDERBUFFER,
                    GLES20FixedPipeline.GL_DEPTH_COMPONENT16,
                    texture.getTexWidth(),
                    texture.getTexHeight());

            GLES20FixedPipeline.glBindRenderbuffer(
                    GLES20FixedPipeline.GL_RENDERBUFFER, 0);
            GLES20FixedPipeline.glBindFramebuffer(
                    GLES20FixedPipeline.GL_FRAMEBUFFER, fbo[0]);

            // bind texture to FBO
            GLES20FixedPipeline.glFramebufferTexture2D(
                    GLES20FixedPipeline.GL_FRAMEBUFFER,
                    GLES20FixedPipeline.GL_COLOR_ATTACHMENT0,
                    GLES20FixedPipeline.GL_TEXTURE_2D, texture.getTexId(), 0);
            GLES20FixedPipeline.glFramebufferRenderbuffer(
                    GLES20FixedPipeline.GL_FRAMEBUFFER,
                    GLES20FixedPipeline.GL_DEPTH_ATTACHMENT,
                    GLES20FixedPipeline.GL_RENDERBUFFER, depthBuffer[0]);

            if (GLES20FixedPipeline
                    .glCheckFramebufferStatus(
                            GLES20FixedPipeline.GL_FRAMEBUFFER) != GLES20FixedPipeline.GL_FRAMEBUFFER_COMPLETE) {
                throw new RuntimeException(
                        "Failed to set up FBO, code=0x"
                                + Integer.toString(
                                        GLES20FixedPipeline.glGetError(), 16));
            }

            // clear the frame
            GLES20FixedPipeline.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GLES20FixedPipeline
                    .glClear(GLES20FixedPipeline.GL_COLOR_BUFFER_BIT);

            // draw the data to be captured
            this.draw();

            // read pixels from texture
            this.readPixels(callback);

            // clear the render target
            GLES20FixedPipeline.glBindFramebuffer(
                    GLES20FixedPipeline.GL_FRAMEBUFFER, 0);

            // release FBO/depthBuffer
            GLES20FixedPipeline.glDeleteFramebuffers(1, fbo, 0);
            fbo[0] = 0;
            GLES20FixedPipeline.glDeleteRenderbuffers(1, depthBuffer, 0);
            depthBuffer[0] = 0;

            // release texture
            texture.release();
            texture = null;
        } catch (Throwable t) {
            callback.onCaptureError(this, t);
        } finally {
            // cleanup in the event of error
            if (fbo[0] != 0)
                GLES20FixedPipeline.glBindFramebuffer(
                        GLES20FixedPipeline.GL_FRAMEBUFFER, 0);

            if (texture != null)
                texture.release();

            if (fbo[0] != 0)
                GLES20FixedPipeline.glDeleteFramebuffers(1, fbo, 0);
            if (depthBuffer[0] != 0)
                GLES20FixedPipeline.glDeleteRenderbuffers(1, depthBuffer, 0);
        }
    }

    protected void readPixels(final CaptureCallback callback) {
        ByteBuffer buf = com.atakmap.lang.Unsafe.allocateDirect(this.width
                * this.height
                * 4);
        buf.order(ByteOrder.nativeOrder());
        final IntBuffer rgba = buf.asIntBuffer();
        buf = null;

        // XXX - need to vertical flip y to account for GL coordinate space?
        GLES20FixedPipeline.glReadPixels(this.x, this.y, this.width,
                this.height, GLES20FixedPipeline.GL_RGBA,
                GLES20FixedPipeline.GL_UNSIGNED_BYTE, rgba);
        rgba.clear();

        // compress off of GL thread
        Thread t = new Thread(TAG + "-Complete") {
            @Override
            public void run() {
                GLCapture.this.completeCapture(rgba, callback);
            }
        };
        t.setPriority(Thread.NORM_PRIORITY);
        t.start();
    }

    private void completeCapture(IntBuffer rgba, CaptureCallback callback) {
        Bitmap bitmap = Bitmap.createBitmap(this.width, this.height,
                Bitmap.Config.ARGB_8888);

        int[] scan = new int[this.width];

        // vertical flip and reorder RGBA -> ARGB
        int v;
        for (int i = 0; i < this.height; i++) {
            rgba.clear();
            rgba.position(i * this.width);
            rgba.limit(rgba.position() + this.width);
            rgba.get(scan);

            for (int j = 0; j < this.width; j++) {
                v = scan[j];
                scan[j] = (v & 0xFF00FF00) | ((v & 0x00FF0000) >> 16)
                        | ((v & 0xFF) << 16);
            }
            bitmap.setPixels(scan, 0, this.width, 0, (this.height - i - 1),
                    this.width, 1);
        }
        rgba = null;
        scan = null;

        callback.onCaptureComplete(this, bitmap);
    }

    /**
     * Compresses a bitmap to a target file.
     * 
     * @param bitmap        A bitmap
     * @param quality       The quality, <code>0</code> for lowest quality,
     *                      <code>100</code> for best quality
     * @param compressFmt   The compression format
     * @param target        The target file
     * @param recycleOnExit <code>true</code> to recycle the bitmap before the
     *                      method returns, <code>false</code> otherwise.
     * @throws IOException  if there is an issue writing the bitmap to a file.
     */
    public static void compress(Bitmap bitmap, int quality,
            Bitmap.CompressFormat compressFmt, File target,
            boolean recycleOnExit) throws IOException {
        try (OutputStream outputStream = IOProviderFactory
                .getOutputStream(target)) {
            bitmap.compress(compressFmt, quality, outputStream);
        } finally {
            if (recycleOnExit)
                bitmap.recycle();
        }
    }

    /**************************************************************************/

    /**
     * Callback for an offscreen capture.
     * 
     * 
     */
    public interface CaptureCallback {
        /**
         * This method is invoked when the capture is started. This method is
         * always invoked on the GL context thread.
         * 
         * @param capture   The {@link GLCapture} instance
         */
        void onCaptureStarted(GLCapture capture);

        /**
         * This method is invoked when the capture is complete. This method is
         * always invoked on a separate user thread.
         * 
         * @param capture   The {@link GLCapture} instance
         * @param bitmap    The captured data
         */
        void onCaptureComplete(GLCapture capture, Bitmap bitmap);

        /**
         * This method is invoked if an error occurs during the capture process.
         * 
         * @param capture   The {@link GLCapture} instance
         * @param t the throwable that has occurred during the capture.
         */
        void onCaptureError(GLCapture capture, Throwable t);
    }
}
