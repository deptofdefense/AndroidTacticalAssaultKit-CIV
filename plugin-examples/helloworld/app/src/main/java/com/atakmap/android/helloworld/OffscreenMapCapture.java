
package com.atakmap.android.helloworld;

import android.graphics.Bitmap;

import javax.microedition.khronos.egl.EGL;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.widget.LinearLayout;
import android.view.ViewGroup;

import com.atakmap.android.maps.MapView;
import com.atakmap.map.opengl.GLMapView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class OffscreenMapCapture {

    final float[] squareVertices = {
            1.0f, 1.0f,
            -1.0f, 1.0f,
            1.0f, -1.0f,
            -1.0f, -1.0f
    };

    GLSurfaceView glView = null;
    final int[] prevFrameBuffer = new int[1];
    final int[] colorRenderBuffer = new int[1];
    final int[] textureFrameBuffer = new int[1];
    final int[] textures = new int[1];
    boolean isGettingAndSettingImages = false;

    FloatBuffer imageVertexBuffer = null;

    GLES20Renderer renderer = null;
    final Runnable stopGettingAndSettingImage = new Runnable() {
        public void run() {
            GLES20.glDeleteTextures(1, textures, 0);
            GLES20.glDeleteRenderbuffers(1, colorRenderBuffer, 0);
            GLES20.glDeleteFramebuffers(1, textureFrameBuffer, 0);
            imageVertexBuffer = null;
            textures[0] = 0;
            colorRenderBuffer[0] = 0;
            textureFrameBuffer[0] = 0;
        }
    };

    final Runnable getAndSetImage = new Runnable() {
        // for debugging purposes
        boolean glFlushErrors(String msg, boolean quiet) {
            boolean r = false;
            if (!quiet)
                System.out.println("*** " + msg);
            do {
                final int err = GLES20.glGetError();
                if (err == GLES20.GL_NO_ERROR)
                    break;
                if (!quiet)
                    System.out.println("err " + Integer.toString(err, 16));
                r = true;
            } while (true);
            return r;
        }

        private boolean initializeFBO(int mapWidth, int mapHeight) {
            glFlushErrors(null, true);

            // generate the frame buffer and render buffer
            GLES20.glGenFramebuffers(1, textureFrameBuffer, 0);
            glFlushErrors("glGenFramebuffers", false);
            GLES20.glGenRenderbuffers(1, colorRenderBuffer, 0);
            glFlushErrors("glGenRenderbuffers", false);

            // create the offscreen texture, using RGB565; dimensions are power
            // of 2 for best compatibility
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S,
                    GL10.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T,
                    GL10.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB,
                    _nextPowerOf2(mapWidth), _nextPowerOf2(mapHeight), 0,
                    GLES20.GL_RGB, GLES20.GL_UNSIGNED_SHORT_5_6_5, null);

            glFlushErrors("offscreen texture create", false);

            // bind the render buffer
            GLES20.glBindRenderbuffer(
                    GLES20.GL_RENDERBUFFER,
                    colorRenderBuffer[0]);
            glFlushErrors("glBindRenderbuffer", false);

            // configure for debug buffer
            GLES20.glRenderbufferStorage(
                    GLES20.GL_RENDERBUFFER,
                    GLES20.GL_DEPTH_COMPONENT16,
                    _nextPowerOf2(mapWidth),
                    _nextPowerOf2(mapHeight));
            glFlushErrors("glRenderbufferStorage", false);

            // unbind the render buffer
            GLES20.glBindRenderbuffer(
                    GLES20.GL_RENDERBUFFER, 0);
            glFlushErrors("glBindRenderbuffer", false);
            glFlushErrors("glBindRenderbuffer", false);

            // bind the frame buffer
            GLES20.glBindFramebuffer(
                    GLES20.GL_FRAMEBUFFER, textureFrameBuffer[0]);
            glFlushErrors("glBindFramebuffer", false);

            // bind texture to FBO
            GLES20.glFramebufferTexture2D(
                    GLES20.GL_FRAMEBUFFER,
                    GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, textures[0], 0);
            glFlushErrors("glFramebufferTexture2D", false);
            // bind the render buffer (depth) to the frame buffer
            GLES20.glFramebufferRenderbuffer(
                    GLES20.GL_FRAMEBUFFER,
                    GLES20.GL_DEPTH_ATTACHMENT,
                    GLES20.GL_RENDERBUFFER, colorRenderBuffer[0]);
            glFlushErrors("glFramebufferRenderbuffer", false);

            // FBO should be complete, check status
            final int fboStatus = GLES20
                    .glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            return (fboStatus == GLES20.GL_FRAMEBUFFER_COMPLETE);
        }

        public void run() {
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, prevFrameBuffer,
                    0);

            final GLMapView glMapView = MapView.getMapView().getGLSurface()
                    .getGLMapView();
            final int mapWidth = glMapView.getRight() - glMapView.getLeft();
            final int mapHeight = glMapView.getTop() - glMapView.getBottom();

            final float[] viewPort = new float[4];
            GLES20.glGetFloatv(GLES20.GL_VIEWPORT, viewPort, 0);

            // synchronize on the shared GL resource, the offscreen texture
            synchronized (textures) {
                if (textureFrameBuffer[0] == 0) {
                    // initialize the offscreen FBO
                    initializeFBO(mapWidth, mapHeight);
                } else {
                    // bind the offscreen FBO
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,
                            textureFrameBuffer[0]);
                }

                // set the viewport to the offscreen texture size
                GLES20.glViewport(0, 0, mapWidth, mapHeight);

                // clear the FBO
                GLES20.glClearColor(.0f, .0f, .0f, 1.0f);
                GLES20.glClear(
                        GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT
                                | GLES20.GL_STENCIL_BUFFER_BIT);

                // draw the map
                MapView.getMapView().getGLSurface().getGLMapView().render();

                // reset the frame buffer
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,
                        prevFrameBuffer[0]);
            }

            // reset the viewport
            GLES20.glViewport((int) viewPort[0], (int) viewPort[1],
                    (int) viewPort[2], (int) viewPort[3]);

            refreshQueued = false;
        }
    };

    Thread offscreenRefreshThread;
    boolean refreshQueued;
    Bitmap img = null;
    int prog = -1;
    public static final int COORDS_PER_VERTEX = 2;
    public final static int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    public class GLES20Renderer
            implements android.opengl.GLSurfaceView.Renderer {
        @Override
        public void onSurfaceCreated(GL10 gl,
                javax.microedition.khronos.egl.EGLConfig config) {

        }

        @Override
        public void onSurfaceChanged(GL10 unused, int width, int height) {
        }

        @Override
        public void onDrawFrame(GL10 notUsed) {
            if (imageVertexBuffer == null) {
                ByteBuffer bb2 = ByteBuffer
                        .allocateDirect(squareVertices.length * 4);
                bb2.order(ByteOrder.nativeOrder());
                imageVertexBuffer = bb2.asFloatBuffer();
                imageVertexBuffer.put(squareVertices);
                imageVertexBuffer.position(0);
            }
            if (prog < 0) {
                prog = ShaderInfo.loadImageShader();
            }
            GLES20.glUseProgram(prog);
            GLMapView glMapView = MapView.getMapView().getGLSurface()
                    .getGLMapView();
            int mapWidth = glMapView.getRight() - glMapView.getLeft();
            int mapHeight = glMapView.getTop() - glMapView.getBottom();
            // synchronize on the shared GL resource, the offscreen texture
            synchronized (textures) {
                if (textures[0] != 0) {
                    float viewSizeX = glView.getWidth();
                    float viewSizeY = glView.getHeight();
                    float imgratio = viewSizeX / viewSizeY;

                    float tmpS, tmpSize;
                    float plw = 2.f * viewSizeX,
                            plh = 2.f * viewSizeY;

                    if ((tmpS = plw / imgratio) < plh) {
                        /* size is bound by width */
                        tmpSize = tmpS;
                    } else {
                        /* size is bound by height */
                        tmpSize = plh;
                    }

                    float dw, dh;
                    dw = tmpSize * (imgratio);
                    dh = tmpSize;
                    float fwidth = .5f * dw / viewSizeX,
                            fheight = .5f * dh / viewSizeY;
                    int mXYScaleHandle = GLES20.glGetUniformLocation(prog,
                            "xyscale");
                    GLES20.glUniform2f(mXYScaleHandle, fwidth, fheight);
                    int mCenterHandle = GLES20.glGetUniformLocation(prog,
                            "center");
                    float centerX = 0.f, centerY = 0.f;
                    GLES20.glUniform2f(mCenterHandle, centerX, centerY);

                    float rotation = 0.f;
                    int mRotHandle = GLES20.glGetUniformLocation(prog, "rot");
                    float viewRot = (float) ((-Math.PI / 2.f)
                            + (rotation / 180.f) * Math.PI);
                    GLES20.glUniform1f(mRotHandle, viewRot);
                    int mNoAlphaHandle = GLES20.glGetUniformLocation(prog,
                            "no_alpha");
                    int mAlphaHandle = GLES20.glGetUniformLocation(prog,
                            "alpha");
                    float alpha = 1.f, no_alpha = 0.f;
                    GLES20.glUniform1f(mAlphaHandle, alpha);
                    if (mNoAlphaHandle >= 0)
                        GLES20.glUniform1f(mNoAlphaHandle, no_alpha);
                    int mEyeModeMultip = GLES20.glGetUniformLocation(prog,
                            "multip");
                    int eyeMode = 0;
                    GLES20.glUniform1f(mEyeModeMultip,
                            eyeMode == 0 ? 1.f : .5f);
                    int mEyeModeAdd = GLES20.glGetUniformLocation(prog, "addv");
                    GLES20.glUniform1f(mEyeModeAdd,
                            eyeMode == 0 ? 0.f : eyeMode * .5f);

                    int mPositionHandle = GLES20.glGetAttribLocation(prog,
                            "position");
                    GLES20.glEnableVertexAttribArray(mPositionHandle);
                    GLES20.glVertexAttribPointer(mPositionHandle,
                            COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                            false, vertexStride, imageVertexBuffer);
                    int mColorHandle = GLES20.glGetUniformLocation(prog,
                            "s_texture");
                    GLES20.glUniform1i(mColorHandle, 0);
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                    GLES20.glDisableVertexAttribArray(mPositionHandle);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
                }
            }
        }
    }

    public OffscreenMapCapture(LinearLayout ll) {

        // obtain the EGL Context associated with the ATAK GLSurfaceView
        final EGLContext[] shared_ctx = new EGLContext[1];
        MapView.getMapView().getGLSurface().queueEvent(new Runnable() {
            public void run() {
                synchronized (shared_ctx) {
                    EGL egl = EGLContext.getEGL();
                    shared_ctx[0] = ((EGL10) egl).eglGetCurrentContext();
                    shared_ctx.notify();
                }
            }
        });
        synchronized (shared_ctx) {
            if (shared_ctx[0] == null) {
                try {
                    shared_ctx.wait();
                } catch (InterruptedException ignored) {
                }
            }
        }
        if (shared_ctx[0] == null)
            shared_ctx[0] = EGL10.EGL_NO_CONTEXT;

        glView = new GLSurfaceView(ll.getContext());

        // use a custom EGLContextFactory to create a new context
        glView.setEGLContextFactory(new GLSurfaceView.EGLContextFactory() {
            @Override
            public void destroyContext(EGL10 egl, EGLDisplay display,
                    EGLContext context) {
                egl.eglDestroyContext(display, context);
            }

            @Override
            public EGLContext createContext(EGL10 egl, EGLDisplay display,
                    EGLConfig eglConfig) {
                final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
                final int[] attribs = new int[] {
                        // specify the desired GLES version
                        EGL_CONTEXT_CLIENT_VERSION, 2,
                        EGL10.EGL_NONE,
                };
                return egl.eglCreateContext(display,
                        eglConfig,
                        shared_ctx[0],
                        attribs);
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 500);
        glView.setLayoutParams(lp);
        glView.setPreserveEGLContextOnPause(true);
        glView.setRenderer(renderer = new GLES20Renderer());
        ll.addView(glView);

    }

    public void capture(boolean capture) {
        isGettingAndSettingImages = capture;

        if (isGettingAndSettingImages) {
            refreshQueued = false;
            // kick off a background thread to periodically refresh the
            // shared texture. note that this needs to be done via a
            // background thread as opposed to enqueueing at the end of
            // the event pump due to the way the GL thread is
            // implemented by Android.
            offscreenRefreshThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    final long interval = 30L;
                    // while the current thread is the refresh thread,
                    // periodically queue a refresh of the offscreen
                    // texture
                    while (Thread.currentThread() == offscreenRefreshThread) {
                        try {
                            Thread.sleep(interval);
                        } catch (InterruptedException ignored) {
                        }

                        // post the event to get/set the image
                        // hack to make sure that the thread was not killed while it was being slept
                        if (Thread.currentThread() == offscreenRefreshThread) {
                            if (isGettingAndSettingImages && !refreshQueued) {
                                refreshQueued = true;
                                MapView.getMapView().getGLSurface()
                                        .queueEvent(getAndSetImage);
                            }
                        }
                    }
                }
            });
            offscreenRefreshThread.setPriority(Thread.NORM_PRIORITY);
            offscreenRefreshThread.setDaemon(true);
            offscreenRefreshThread.start();
        } else {
            offscreenRefreshThread = null;
            MapView.getMapView().getGLSurface()
                    .queueEvent(stopGettingAndSettingImage);
        }
    }

    private static int _nextPowerOf2(int value) {
        --value;
        value = (value >> 1) | value;
        value = (value >> 2) | value;
        value = (value >> 4) | value;
        value = (value >> 8) | value;
        value = (value >> 16) | value;
        ++value;
        return value;
    }

}
