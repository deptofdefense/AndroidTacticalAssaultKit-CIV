//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.atakmap.android.imagecapture.opengl;

import android.graphics.Bitmap;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES30;

import com.atakmap.lang.Unsafe;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for rendering content using OpenGL without the use of
 * GLSurfaceView or the like. Drawn content is then captured to a bitmap.
 *
 * Mostly copied from GLOffscreenDirector in the PGSC video library
 */
public class GLOffscreenCaptureService implements Runnable {

    private static final String TAG = "GLOffscreenRenderService";

    private static GLOffscreenCaptureService _instance;

    public static GLOffscreenCaptureService getInstance() {
        return _instance;
    }

    private final List<GLOffscreenCaptureRequest> _requests = new ArrayList<>();

    private EGLDisplay _display;
    private EGLContext _eglContext;
    private EGLSurface _eglSurface;
    private int _texWidth, _texHeight;
    private int[] _texIdBuf, _fboBuf, _depthBuf;
    private final GLOffscreenCaptureParams _params = new GLOffscreenCaptureParams();
    private IntBuffer _pixelBuf;
    private boolean _active = true;

    public GLOffscreenCaptureService() {
        Thread thread = new Thread(this);
        thread.setPriority(5);
        thread.setName(TAG);
        thread.start();
        _instance = this;
    }

    public void request(GLOffscreenCaptureRequest req) {
        synchronized (_requests) {
            _requests.add(req);
        }
    }

    public void dispose() {
        _active = false;
    }

    @Override
    public void run() {
        // Need to initialize the EGL display and context first so OpenGL knows
        // this is a render thread
        if (_display == null)
            initDisplay();

        while (_active) {
            // Get the latest request
            GLOffscreenCaptureRequest request = null;
            synchronized (_requests) {
                if (!_requests.isEmpty())
                    request = _requests.remove(_requests.size() - 1);
            }

            // Process next request
            if (request != null)
                processRequest(request);

            // Sleep a bit before reading the next requests
            try {
                Thread.sleep(100);
            } catch (Exception ignored) {
            }
        }

        // No longer running - release and stop
        release();
    }

    private void initDisplay() {
        _display = EGL14.eglGetDisplay(0);
        int[] version = new int[2];
        EGL14.eglInitialize(_display, version, 0, version, 1);
        int[] attribList = new int[] {
                12324, 8, 12323, 8, 12322, 8, 12321, 8, 12352, 4, 12344
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        EGL14.eglChooseConfig(_display, attribList, 0, configs, 0,
                configs.length, numConfigs, 0);
        int[] attribList2 = new int[] {
                12440, 2, 12344
        };
        _eglContext = EGL14.eglCreateContext(_display, configs[0],
                EGL14.EGL_NO_CONTEXT, attribList2, 0);
        int[] surfaceAttribs = new int[] {
                12375, 1, 12374, 1, 12344
        };
        _eglSurface = EGL14.eglCreatePbufferSurface(_display, configs[0],
                surfaceAttribs, 0);
        EGL14.eglMakeCurrent(_display, _eglSurface, _eglSurface, _eglContext);
    }

    private void initFrameRenderBuffers() {

        if (_fboBuf != null)
            GLES30.glDeleteFramebuffers(1, _fboBuf, 0);

        if (_depthBuf != null)
            GLES30.glDeleteRenderbuffers(1, _depthBuf, 0);

        _fboBuf = new int[1];
        GLES30.glGenFramebuffers(1, _fboBuf, 0);
        if (_fboBuf[0] == 0)
            throw new RuntimeException("Failed to create FBO");

        _depthBuf = new int[1];
        GLES30.glGenRenderbuffers(1, _depthBuf, 0);
        if (_depthBuf[0] == 0)
            throw new RuntimeException("Failed to create Depth Buffer");

        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, _depthBuf[0]);

        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER,
                GLES30.GL_DEPTH_COMPONENT16, _texWidth, _texHeight);

        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, _fboBuf[0]);

        // bind texture to FBO
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                _texIdBuf[0], 0);
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER,
                GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER,
                _depthBuf[0]);

        if (GLES30.glCheckFramebufferStatus(
                GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException(
                    "Failed to set up FBO, code=0x"
                            + Integer.toString(
                                    GLES30.glGetError(), 16));
        }

        _params.frameBufferID = _fboBuf[0];
        _params.renderBufferID = _depthBuf[0];
    }

    private void initTexture(int width, int height) {
        if (_texIdBuf != null)
            GLES30.glDeleteTextures(1, _texIdBuf, 0);

        if (_pixelBuf != null)
            Unsafe.free(_pixelBuf);

        ByteBuffer buf = Unsafe.allocateDirect(width * height * 4);
        buf.order(ByteOrder.nativeOrder());
        _pixelBuf = buf.asIntBuffer();

        _texIdBuf = new int[1];
        GLES30.glGenTextures(1, _texIdBuf, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, _texIdBuf[0]);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D,
                GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S,
                GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T,
                GLES30.GL_CLAMP_TO_EDGE);

        // Initialize the texture buffer
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE,
                null);

        // Unbind texture
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);

        _texWidth = width;
        _texHeight = height;
    }

    private void release() {
        // Release textures and buffers
        if (_texIdBuf != null) {
            GLES30.glDeleteTextures(1, _texIdBuf, 0);
            _texIdBuf = null;
        }
        if (_fboBuf != null) {
            GLES30.glDeleteFramebuffers(1, _fboBuf, 0);
            _fboBuf = null;
        }
        if (_depthBuf != null) {
            GLES30.glDeleteRenderbuffers(1, _depthBuf, 0);
            _depthBuf = null;
        }
        if (_pixelBuf != null) {
            Unsafe.free(_pixelBuf);
            _pixelBuf = null;
        }

        // Release context and surface
        if (_display != null) {
            EGL14.eglDestroySurface(_display, _eglSurface);
            EGL14.eglMakeCurrent(_display, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroyContext(_display, _eglContext);
            EGL14.eglTerminate(_display);
            _display = null;
        }
    }

    private void processRequest(GLOffscreenCaptureRequest request) {

        // Allow the request to prepare any initial GL-specific stuff here
        request.onStart();

        int width = request.getWidth(), height = request.getHeight();

        // Initialize the texture we'll be drawing to and the frame/render buffers
        if (_texIdBuf == null || _texWidth != width || _texHeight != height) {
            initTexture(width, height);
            initFrameRenderBuffers();
        }

        _params.width = width;
        _params.height = height;

        // Allow the request to draw what it needs to
        request.onDraw(new GLOffscreenCaptureParams(_params));

        // Convert texture buffer data to bitmap
        Bitmap bitmap = readToBitmap();

        // Pass finished bitmap to request
        request.onFinished(bitmap);
    }

    private Bitmap readToBitmap() {
        Bitmap bmp = Bitmap.createBitmap(_texWidth, _texHeight,
                Bitmap.Config.ARGB_8888);

        if (_pixelBuf == null)
            return bmp;

        // Wait until GL operations are complete before reading pixels
        GLES30.glFinish();

        _pixelBuf.clear();
        GLES30.glReadPixels(0, 0, _texWidth, _texHeight, GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE, _pixelBuf);

        int[] scan = new int[_texWidth];

        // vertical flip and reorder BGRA -> ARGB
        int v;
        for (int i = 0; i < _texHeight; i++) {
            _pixelBuf.clear();
            _pixelBuf.position(i * _texWidth);
            _pixelBuf.limit(_pixelBuf.position() + _texWidth);
            _pixelBuf.get(scan);

            for (int j = 0; j < _texWidth; j++) {
                v = scan[j];
                scan[j] = (v & 0xFF00FF00) | ((v & 0x00FF0000) >> 16)
                        | ((v & 0xFF) << 16);
            }
            bmp.setPixels(scan, 0, _texWidth, 0, (_texHeight - i - 1),
                    _texWidth, 1);
        }

        return bmp;
    }
}
