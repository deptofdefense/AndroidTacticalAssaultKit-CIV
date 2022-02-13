
package com.atakmap.android.video.legacy;

/**
 * GV2F<P>
 * Copyright 2011 PAR Government Systems <P>
 * <P>
 * Restricted Rights:<P>
 * Use, reproduction, or disclosure of executable code, application interface
 * (API), source code, or related information is subject to restrictions set
 * forth in the contract and/or license agreement.    The Government's rights
 * to use, modify, reproduce, release, perform, display, or disclose this
 * software are restricted as identified in the purchase contract. Any
 * reproduction of computer software or portions thereof marked with this
 * legend must also reproduce the markings. Any person who has been provided
 * access to this software must be aware of the above restrictions.
 *
 * For the TAK program applications, this class can be used without restriction.
 * <P>
 */

import android.app.ActivityManager;
import android.content.pm.ConfigurationInfo;
import android.content.Context;

import android.os.SystemClock;

import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.egl.EGLConfig;

import android.graphics.Canvas;

import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.view.View;

import com.atakmap.coremap.log.Log;
import java.nio.FloatBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.Buffer;

import com.partech.pgscmedia.*;
import com.partech.pgscmedia.frameaccess.*;
import com.partech.pgscmedia.consumers.*;

/**
 * Given a software surface and a hardware (OpenGL) surface with a valid processor,
 * and video format, paint all of the frame data into the hardware surface if available and
 * fallback to a software surface if not available.
 */
public class RenderFactory {

    static final String TAG = "RenderFactory";

    static private boolean isEGL20(Context c) {
        ActivityManager am = (ActivityManager) c
                .getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo info = am.getDeviceConfigurationInfo();
        if (info != null) {
            Log.v(TAG, "The EGL version is (" + info.reqGlEsVersion + ")");
            return (info.reqGlEsVersion >= 0x20000);
        }
        return false;
    }

    /**
     * Initialize a GLSurfaceView capable of used as a Renderer for a
     * Gv2F processor.
     * @param fmt the video track to use
     * @param gsv the open GL surface to draw to.
     * @param sv the software surfce to be used as a backup.
     * @param c the context to use to determine the OpenGLES version.
     */
    public static VideoConsumer initialize(
            VideoMediaFormat fmt,
            GLSurfaceView gsv,
            SurfaceView sv,
            Context c) throws MediaException {

        SurfaceHolder surfaceHolder = sv.getHolder();

        // Create renderer (used when pure software is used or when the HardwareRenderer
        // switches back over to software rendering.
        SurfaceRenderer softwareRenderer = new SurfaceRenderer(surfaceHolder,
                fmt);

        GLRenderer glRenderer;

        gsv.setDebugFlags(GLSurfaceView.DEBUG_CHECK_GL_ERROR
                | GLSurfaceView.DEBUG_LOG_GL_CALLS);

        if (isEGL20(c)) {
            gsv.setEGLContextClientVersion(2);
            Log.v(TAG, "wrap the GLRenderer == 2");
            glRenderer = new GLRenderer(fmt, gsv, sv, softwareRenderer);
            gsv.setRenderer(glRenderer);
            return glRenderer;
        } else {
            Log.v(TAG, "GLEs < 2.0");
            gsv.setVisibility(View.INVISIBLE);
            Log.v(TAG, "wrap the GLRenderer < 2.0");
            glRenderer = new GLRenderer();
            gsv.setRenderer(glRenderer);
            return softwareRenderer;
        }

    }

    // Software video renderer

    /**
     * A VideoConsumer implementation that is based around an
     * Android SurfaceView
     */
    private static class SurfaceRenderer implements SurfaceHolder.Callback,
            VideoConsumer {
        private boolean surfaceActive;
        private final SurfaceHolder surfaceHolder;
        int dx, dy;

        private final VideoFrameConverter converter;
        private NativeIntArray output;
        private int outputOffset;
        private int outputStride;

        private long time;
        private int count;

        private final VideoMediaFormat fmt;

        /**
         * Create a SurfaceRenderer against the Surface in the passed
         * SurfaceHolder and that expects frames in the format described
         * by the passed VideMediaFormat.
         * Due to surface setup, this MUST be called on SurfaceView main
         * thread.
         */
        SurfaceRenderer(SurfaceHolder holder, VideoMediaFormat fmt)
                throws MediaException {
            this.fmt = fmt;
            surfaceHolder = holder;
            surfaceActive = false;
            // Surface expects packed RGB pixels, so set up our converter
            // to provided packed RGB
            converter = new VideoFrameConverter(fmt,
                    VideoMediaFormat.PixelFormat.PIXELS_RGB_PACKED);
            converter.setScaleForAspect(false);
            surfaceHolder.addCallback(this);
        }

        /**
         * (Re-)configures our VideoFrameConverter to provide frames for display
         * on a surface of the given size. Adjusts for aspect ratio as described
         * by the video track's format we got at construction.
         */
        private synchronized void setupConverter(int displayWidth,
                int displayHeight) {
            // Compute our desired width and height based on the display size,
            // frame size, and aspect ratio. Also find x/y that will
            // center in our surface.
            int aspectWidth = (int) (fmt.frameWidth * fmt.aspectRatio);
            int dw = fmt.frameWidth - displayWidth;
            int dh = fmt.frameHeight - displayHeight;
            if (dh > dw) {
                float f = (float) displayHeight / fmt.frameHeight;
                dh = displayHeight;
                dy = 0;
                dw = (int) (f * aspectWidth);
                dx = (displayWidth - dw) / 2;
            } else {
                float f = (float) displayWidth / aspectWidth;
                dw = displayWidth;
                dx = 0;
                dh = (int) (f * fmt.frameHeight);
                dy = (displayHeight - dh) / 2;
            }

            try {
                // (Re)configure the VideoFrameConverter
                converter.setScaleOutputSize(dw, dh);

                // Because we changed the scaler output size, we must
                // re-acquire the output buffer parameters.
                // The old ones are no longer valid.
                // The hardcoded zero-offset is because our converter
                // is setup to output packed RGB, which is only a single
                // plane of output data.
                output = (NativeIntArray) converter.getOutputArray();
                outputOffset = output.offset + converter.getOutputOffsets()[0];
                outputStride = converter.getOutputStrides()[0];
            } catch (MediaException me) {
                Log.v(getClass().getName(), "Failed to create converter");
                output = null;
            }
        }

        //  SurfaceHolder.Callback
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format,
                int width, int height) {
            Log.v(getClass().getName(), "Surface Changed!");
            synchronized (this) {
                setupConverter(width, height);
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.v(getClass().getName(), "Surface Created!");
            synchronized (this) {
                surfaceActive = true;
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.v(getClass().getName(), "Surface destroyed!");
            synchronized (this) {
                surfaceActive = false;
            }
        }

        // VideoConsumer
        @Override
        public void mediaVideoFrame(final VideoFrameData frame) {

            // just for framerate calculation
            long curr = SystemClock.uptimeMillis();
            if (curr - time > 1000) {
                //if (curr != 0) Log.d(TAG, "video framerate[" + frame.getWidth() + "x" + frame.getHeight() + "]: " +  ((count * 1000d) / (curr - time)));
                count = -1;
                time = curr;
            }
            count++;

            //Log.v(getClass().getName(), "Frame");
            synchronized (this) {
                // Don't proceed if we have not yet had our
                // surface activated by Android.
                if (!surfaceActive || output == null)
                    return;

                // Lock the canvas and paint to it.
                Canvas canvas = surfaceHolder.lockCanvas();
                try {
                    // Convert the frame into the output format
                    // Result is placed in 'output' (extracted during converter
                    // setup in setupConverter())
                    converter.convert(frame);
                    // Draw to the canvas
                    canvas.drawBitmap(output.intArray, outputOffset,
                            outputStride, dx, dy,
                            converter.getScaleOutputWidth(),
                            converter.getScaleOutputHeight(),
                            false, null);
                } catch (MediaException e) {
                    Log.v(getClass().getName(), "Exception during conversion");
                } finally {
                    surfaceHolder.unlockCanvasAndPost(canvas);
                }

            }
        }

    }

    // GL video renderer

    private enum TextureLayouts {
        SOURCE_COMPAT, // Stride = width, all data contiguous
        STRIDED, // No copy needed, but must account for per-line stride. Stride is uniform across Y and U and V
        FULL_COPY // Need to copy everything piece by piece - handles all oddities
    }

    static private class GLRenderer implements GLSurfaceView.Renderer,
            VideoConsumer {

        private static final int FLOAT_SIZE_BYTES = 4;
        private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5
                * FLOAT_SIZE_BYTES;
        private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
        private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
        private final float[] mTriangleVerticesData = {
                // X, Y, Z, U, V
                -1.0f, -1.0f, 0, 0.0f, 1.0f,
                1.0f, -1.0f, 0, 0.0f, 0.0f,
                -1.0f, 1.0f, 0, 1.0f, 1.0f,
                1.0f, 1.0f, 0, 1.0f, 0.0f
        };

        private FloatBuffer mTriangleVertices;

        private final static String mVertexShader = "uniform mat4 uMVPMatrix;\n"
                +
                "attribute vec4 aPosition;\n" +
                "attribute vec2 aTextureCoord;\n" +
                "varying vec2 vTextureCoord;\n" +
                "void main() {\n" +
                "  gl_Position = uMVPMatrix * aPosition;\n" +
                "  vTextureCoord = aTextureCoord;\n" +
                "}\n";

        private final static String mFragmentShader = "precision mediump float;\n"
                +
                "varying mediump vec2 vTextureCoord;\n"
                +
                // dims.x == Y stride and 2x chroma stride also == size of texture
                // strideFactor is normalized fraction of that to actually draw from
                "uniform vec2 dims;\n"
                +
                "uniform float strideFactor;\n"
                +
                // This single texture holds YUV in 420 format.
                // Layout using a 6x4 pixel image
                //  Y Y Y Y Y Y
                //  Y Y Y Y Y Y
                //  Y Y Y Y Y Y
                //  Y Y Y Y Y Y
                //  U U U U U U   <--  first 3 U for row 0, second 3 for row 2
                //  V V V V V V   <--  similar
                "uniform sampler2D YUVTex;\n"
                +
                "void main(){\n"
                +
                "   float nx,ny,r,g,b,y,u,v;\n"
                +
                // Scale x coord to be only within the part we care about (actual width v. stride)
                "   nx=vTextureCoord.x * strideFactor;\n"
                +
                "   ny=vTextureCoord.y;\n"
                +
                // Y values only cover 2/3 total height
                "   y=texture2D(YUVTex,vec2(nx, ny * 2.0 / 3.0)).r;\n"
                +
                // Chroma is double-rowed, so move odd rows halfway across and consider
                // the stride-adjusted X as a movement through half the width
                // For U, Y begins 2/3 the way down and is spread across 1/6
                // V is same, but starts at 5/6
                "   u=texture2D(YUVTex,vec2( (mod(floor(ny * dims.y/2.0), 2.0)) * 0.5 + nx/2.0, (2.0 / 3.0 + ny / 6.0))).r;\n"
                +
                "   v=texture2D(YUVTex,vec2( (mod(floor(ny * dims.y/2.0), 2.0)) * 0.5 + nx/2.0, (5.0 / 6.0 + ny / 6.0))).r;\n"
                +
                // Scale and convert per MPEG-2 specs
                "   y = 1.1643*(y-0.0625);\n" +
                "   u = u - 0.5;\n" +
                "   v = v - 0.5;\n" +
                "   r = y + 1.5958*v;\n" +
                "   g = y - 0.39173*u-0.8190*v;\n" +
                "   b = y + 2.017*u;\n" +
                "   gl_FragColor = vec4(r, g, b, 1.0);\n" +
                "}\n";

        private final float[] mMVPMatrix = new float[16];
        private final float[] mProjMatrix = new float[16];
        private final float[] mMMatrix = new float[16];
        private final float[] mVMatrix = new float[16];

        private int mTexture;
        private int mProgram;
        private int muMVPMatrixHandle;
        private int maPositionHandle;
        private int maTextureHandle;
        private int dimsHandle;
        private int strideFactorHandle;
        private int texWidth;

        private TextureLayouts textureLayout;
        private Buffer texel;
        private byte[] texBuf;
        private float strideFactor;
        private boolean newData = false;
        private boolean passToSW = false;

        private VideoMediaFormat vidFormat;
        private VideoFrameConverter converter;

        // Used just for fps stats
        private boolean firstFrame;
        private int decodedFrameCount;
        private int drawnFrameCount;
        private long startTime;

        private GLSurfaceView gsv;
        private SurfaceView sv;
        private SurfaceRenderer softwareRenderer;

        private long time;
        private int count;

        GLRenderer() {
            firstFrame = true;
        }

        GLRenderer(VideoMediaFormat fmt,
                GLSurfaceView gsv, SurfaceView sv,
                SurfaceRenderer softwareRenderer) {
            vidFormat = fmt;
            firstFrame = true;
            this.gsv = gsv;
            this.sv = sv;
            this.softwareRenderer = softwareRenderer;

            mTriangleVertices = ByteBuffer
                    .allocateDirect(mTriangleVerticesData.length
                            * FLOAT_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            mTriangleVertices.put(mTriangleVerticesData).position(0);
        }

        public synchronized void printAndResetStats() {
            firstFrame = true;
            double fps = drawnFrameCount;
            fps /= SystemClock.elapsedRealtime() - startTime;
            fps *= 1000;
            Log.v(getClass().getName(), "GL Renderer stats:");
            Log.v(getClass().getName(), "Decoded frames: " + decodedFrameCount);
            Log.v(getClass().getName(), "Drawn frames: " + drawnFrameCount);
            Log.v(getClass().getName(), "Framerate: " + fps);
            drawnFrameCount = decodedFrameCount = 0;
        }

        public void destroy() {
        }

        public void createProjection(int tWidth, int tHeight, int wWidth,
                int wHeight) {
            float ratioT = (float) tWidth / tHeight;
            float ratioW = (float) wWidth / wHeight;
            float left = (float) (-1 * (1.0f - ((wWidth - Math.atan(ratioT)
                    * wWidth) / wWidth))),
                    right = (float) (1.0f - ((wWidth - Math
                            .atan(ratioT)
                            * wWidth) / wWidth)),
                    top = -1, bottom = 1;

            if (ratioT > ratioW) {
                top = -1;
                bottom = 1;
                left = -ratioW / (ratioT - ratioW);
                //right = ratioW / (ratioT - ratioW);
            }

            Matrix.orthoM(mProjMatrix, 0, -1, 1, -1, 1, 2, 5);
            if (ratioW > 1.0) {
                GLES30.glViewport((wWidth - (int) ((wHeight * ratioT))) / 2, 0,
                        (int) (wHeight * ratioT), wHeight);
            } else {
                GLES30.glViewport(0, (wHeight - (int) (wWidth / ratioT)) / 2,
                        wWidth, (int) (wWidth / ratioT));
            }
        }

        @Override
        public void onDrawFrame(GL10 na) {
            GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT
                    | GLES30.GL_COLOR_BUFFER_BIT);

            int tWidth = 0, tHeight = 0;
            boolean dirty;
            synchronized (this) {
                if (converter != null) {
                    tWidth = texWidth;
                    tHeight = converter.getScaleOutputHeight();
                }
                dirty = newData;
            }

            drawnFrameCount++;

            // texture Width or height are zero, then no reason to render anything.
            if (tWidth == 0 || tHeight == 0)
                return;

            GLES30.glUseProgram(mProgram);
            checkGlError("glUseProgram");

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTexture);
            GLES30.glUniform1i(mTexture, 0);
            checkGlError("glUniform1i");
            if (dirty) {
                synchronized (this) {
                    GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0,
                            GLES30.GL_LUMINANCE,
                            tWidth, tHeight * 3 / 2, 0,
                            GLES30.GL_LUMINANCE, GLES30.GL_UNSIGNED_BYTE,
                            texel);
                    newData = false;
                    GLES30.glUniform1f(strideFactorHandle, strideFactor);
                    GLES30.glUniform2f(dimsHandle, tWidth, tHeight);
                }
            }

            int wWidth = gsv.getWidth(), wHeight = gsv.getHeight();

            createProjection(tWidth, tHeight, wWidth, wHeight);

            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
            GLES30.glVertexAttribPointer(maPositionHandle, 3, GLES30.GL_FLOAT,
                    false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
            checkGlError("glVertexAttribPointer maPosition");
            mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
            GLES30.glEnableVertexAttribArray(maPositionHandle);
            checkGlError("glEnableVertexAttribArray maPositionHandle");
            GLES30.glVertexAttribPointer(maTextureHandle, 2, GLES30.GL_FLOAT,
                    false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
            checkGlError("glVertexAttribPointer maTextureHandle");
            GLES30.glEnableVertexAttribArray(maTextureHandle);
            checkGlError("glEnableVertexAttribArray maTextureHandle");

            Matrix.setRotateM(mMMatrix, 0, 90, 0, 0, 1.0f);
            Matrix.multiplyMM(mMVPMatrix, 0, mVMatrix, 0, mMMatrix, 0);
            Matrix.multiplyMM(mMVPMatrix, 0, mProjMatrix, 0, mMVPMatrix, 0);

            GLES30.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix,
                    0);
            checkGlError("glUniformMatrix4fv");
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
            checkGlError("glDrawArrays");

        }

        @Override
        public void onSurfaceChanged(GL10 na, int width, int height) {
            GLES30.glViewport(0, 0, width, height);
        }

        @Override
        public void onSurfaceCreated(GL10 na, EGLConfig config) {
            String s = GLES30.glGetString(GLES30.GL_RENDERER);

            if (false) {
                Log.v(getClass().getName(),
                        "Falling back to software render! (" + s + ")");
                gsv.post(new Runnable() {
                    @Override
                    public void run() {
                        gsv.setVisibility(View.INVISIBLE);
                        passToSW = true;
                        sv.setVisibility(View.VISIBLE);
                    }
                });
            } else {
                Log.v(getClass().getName(),
                        "Continuing with hardware render! (" + s + ")");
                gsv.post(new Runnable() {
                    @Override
                    public void run() {
                        gsv.setVisibility(View.VISIBLE);
                        sv.setVisibility(View.INVISIBLE);
                    }
                });

            }

            maPositionHandle = 0;
            maTextureHandle = 0;
            muMVPMatrixHandle = 0;

            mProgram = createProgram(mVertexShader, mFragmentShader);
            maPositionHandle = GLES30
                    .glGetAttribLocation(mProgram, "aPosition");
            checkGlError("glGetAttribLocation aPosition");
            if (maPositionHandle == -1) {
                throw new RuntimeException(
                        "Could not get attrib location for aPosition");
            }

            maTextureHandle = GLES30.glGetAttribLocation(mProgram,
                    "aTextureCoord");
            checkGlError("glGetAttribLocation aTextureCoord");
            if (maTextureHandle == -1) {
                throw new RuntimeException(
                        "Could not get attrib location for aTextureCoord");
            }

            muMVPMatrixHandle = GLES30.glGetUniformLocation(mProgram,
                    "uMVPMatrix");
            checkGlError("glGetUniformLocation uMVPMatrix");
            if (muMVPMatrixHandle == -1) {
                throw new RuntimeException(
                        "Could not get attrib location for uMVPMatrix");
            }

            dimsHandle = GLES30.glGetUniformLocation(mProgram, "dims");
            checkGlError("glGetUniformLocation dims");
            if (dimsHandle == -1) {
                throw new RuntimeException(
                        "Could not get attrib location for dims");
            }

            strideFactorHandle = GLES30.glGetUniformLocation(mProgram,
                    "strideFactor");
            checkGlError("glGetUniformLocation strideFactor");
            if (strideFactorHandle == -1) {
                throw new RuntimeException(
                        "Could not get attrib location for strideFactor");
            }

            //wrap the texture
            mTexture = GLES30.glGetUniformLocation(mProgram, "YUVTex");
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTexture);

            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_MIN_FILTER,
                    GLES30.GL_NEAREST);
            GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_MAG_FILTER,
                    GLES30.GL_LINEAR);

            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_WRAP_S,
                    GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_WRAP_T,
                    GLES30.GL_CLAMP_TO_EDGE);

            Matrix.setLookAtM(mVMatrix, 0, 0, 0, -3.0f, 0f, 0f, 0f, 0f, 1.0f,
                    0.0f);

        }

        private int loadShader(int shaderType, String source) {
            int shader = GLES30.glCreateShader(shaderType);
            if (shader != 0) {
                GLES30.glShaderSource(shader, source);
                GLES30.glCompileShader(shader);
                int[] compiled = new int[1];
                GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS,
                        compiled, 0);
                if (compiled[0] == 0) {
                    Log.e("GLRenderer", "Could not compile shader "
                            + shaderType + ":");
                    Log.e("GLRenderer",
                            "Shader log: " + GLES30.glGetShaderInfoLog(shader));
                    GLES30.glDeleteShader(shader);
                    shader = 0;
                }
            }
            return shader;
        }

        private int createProgram(String vertexSource, String fragmentSource) {

            Log.v(getClass().getName(), "Compiling vertex!");
            int vertexShader = loadShader(GLES30.GL_VERTEX_SHADER,
                    vertexSource);
            if (vertexShader == 0) {
                return 0;
            }

            Log.v(getClass().getName(), "Compiling fragment!");
            int pixelShader = loadShader(GLES30.GL_FRAGMENT_SHADER,
                    fragmentSource);
            if (pixelShader == 0) {
                return 0;
            }

            int program = GLES30.glCreateProgram();
            if (program != 0) {
                GLES30.glAttachShader(program, vertexShader);
                checkGlError("glAttachShader");
                GLES30.glAttachShader(program, pixelShader);
                checkGlError("glAttachShader");
                GLES30.glLinkProgram(program);
                int[] linkStatus = new int[1];
                GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS,
                        linkStatus, 0);
                if (linkStatus[0] != GLES30.GL_TRUE) {
                    Log.e("GLRenderer", "Could not link program: ");
                    Log.e("GLRenderer", GLES30.glGetProgramInfoLog(program));
                    GLES30.glDeleteProgram(program);
                    program = 0;
                }
            }
            return program;
        }

        @SuppressWarnings("LoopStatementThatDoesntLoop")
        private void checkGlError(String op) {
            int error;
            while ((error = GLES30.glGetError()) != GLES30.GL_NO_ERROR) {
                Log.e("GLRenderer", op + ": glError " + error);
                throw new RuntimeException(op + ": glError " + error);
            }
        }

        @Override
        public void mediaVideoFrame(VideoFrameData frame) {

            // just for framerate calculation
            long curr = SystemClock.uptimeMillis();
            if (curr - time > 1000) {
                //if (curr != 0) Log.d(TAG, "video framerate[" + frame.getWidth() + "x" + frame.getHeight() + "]: " +  ((count * 1000d) / (curr - time)));
                count = -1;
                time = curr;
            }
            count++;

            //Log.v(getClass().getName(), "Frame");
            if (passToSW) {
                softwareRenderer.mediaVideoFrame(frame);
                return;
            }
            synchronized (this) {
                if (firstFrame) {
                    firstFrame = false;
                    startTime = SystemClock.elapsedRealtime();
                }
                decodedFrameCount++;

                // Lock the canvas and paint to it.
                try {
                    if (converter == null) {
                        try {
                            converter = new VideoFrameConverter(vidFormat,
                                    VideoMediaFormat.PixelFormat.PIXELS_YUV_420);
                            converter.setScaleForAspect(false);
                            createTexel();
                        } catch (MediaException me) {
                            Log.e(getClass().getName(), me.toString());
                        }
                    }

                    // Convert the frame into the output format
                    // Result is placed in 'output' (extracted during converter
                    // setup in setupConverter())
                    if (converter != null)
                        converter.convert(frame);
                    newData = true;

                    // If we need to copy source data, do so now
                    if (converter != null
                            && textureLayout == TextureLayouts.FULL_COPY) {
                        int[] offsets = converter.getOutputOffsets();
                        int[] strides = converter.getOutputStrides();
                        int h = converter.getScaleOutputHeight();
                        int w = converter.getScaleOutputWidth();
                        NativeByteArray nba = (NativeByteArray) converter
                                .getOutputArray();

                        int doff = 0;
                        for (int comp = 0; comp < 3; ++comp) {
                            int soff = nba.offset + offsets[comp];
                            int step = comp > 0 ? 2 : 1;

                            for (int i = 0; i < h; i += step) {
                                System.arraycopy(nba.byteArray,
                                        soff, texBuf, doff, w / step);
                                soff += strides[comp];
                                doff += w / step;
                            }
                        }
                    }

                    gsv.requestRender();
                } catch (MediaException e) {
                    Log.v(getClass().getName(), "Exception during conversion");
                }

            }
        }

        private void createTexel() {
            //Log.v(getClass().getName(),"framewidth = " + getVideoFormat().frameWidth + "/" + getVideoFormat().frameHeight + ",u = " + converter.getOutputOffsets()[1] + "/" + converter.getOutputStrides()[1] + ", v = " + converter.getOutputOffsets()[2] + "/" + converter.getOutputStrides()[2]);
            NativeByteArray nba = (NativeByteArray) converter.getOutputArray();
            int[] offsets = converter.getOutputOffsets();
            int[] strides = converter.getOutputStrides();

            int h = converter.getScaleOutputHeight();
            int w = converter.getScaleOutputWidth();
            // The FULL_COPY cases work around shortcomings of OGL Android bindings not accepting
            // a byte array with offset for the texture data.
            // The STRIDE cases all for our OGL program to deal with strides
            // not equal to image width
            TextureLayouts layout;
            if (nba.offset != 0 || offsets[0] != 0) {
                Log.w(getClass().getName(),
                        "initial offset not zero ("
                                + nba.offset
                                + ", "
                                + offsets[0]
                                + "), falling back to full-copy, will be slower than if aligned");
                layout = TextureLayouts.FULL_COPY;
            } else if (strides[0] * h != offsets[1] ||
                    offsets[1] + strides[1] * h / 2 != offsets[2]) {
                Log.w(getClass().getName(),
                        "U or V offset does not match end of Y or U data, falling back to full-copy, will be slower than if contiguous");
                layout = TextureLayouts.FULL_COPY;
            } else if (strides[1] * 2 != strides[0] ||
                    strides[2] * 2 != strides[0]) {
                Log.w(getClass().getName(),
                        "U or V stride is not half Y stride falling back to full-copy, will be slower than if layout were uniform");
                layout = TextureLayouts.FULL_COPY;
            } else if (strides[0] != w) {
                Log.w(getClass().getName(),
                        "One or more component strided not equal to image width");
                layout = TextureLayouts.STRIDED;
            } else {
                layout = TextureLayouts.SOURCE_COMPAT;
            }
            changeTexStrat(layout, nba, strides, w, h);

            Log.v(getClass().getName(), "Stride: " + strides[0] + " "
                    + strides[1] + " " + strides[2]);
            Log.v(getClass().getName(), "offsets: " + offsets[0] + " "
                    + offsets[1] + " " + offsets[2]);
            Log.v(getClass().getName(), "wxh: " + w + " x " + h);
        }

        private void changeTexStrat(TextureLayouts texStrat,
                NativeByteArray nba, int[] strides, int w, int h) {
            strideFactor = 1.0f;

            switch (texStrat) {
                case STRIDED:
                    strideFactor = (float) w / strides[0];
                    // fall
                case SOURCE_COMPAT:
                    texBuf = null;
                    texel = ByteBuffer.wrap(nba.byteArray, 0,
                            strides[0] * h +
                                    strides[1] * h / 2 +
                                    strides[2] * h / 2);
                    texWidth = strides[0];
                    break;
                case FULL_COPY:
                    texBuf = new byte[w * h * 2];
                    texel = ByteBuffer.wrap(texBuf);
                    texWidth = w;
                    break;
            }
            textureLayout = texStrat;
        }

    }

}
