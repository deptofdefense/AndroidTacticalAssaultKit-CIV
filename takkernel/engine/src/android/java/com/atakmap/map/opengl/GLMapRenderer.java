
package com.atakmap.map.opengl;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.os.Bundle;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;
import com.atakmap.opengl.GLES20FixedPipeline;

public class GLMapRenderer implements GLSurfaceView.Renderer {

    public static final String TAG = "GLMapRenderer";

    private GLMapView mapView;
    private long targetMillisPerFrame = 0;
    private long timeCall;
    private long lastCall;
    private long currCall;
    private long count = 0;
    private double currentFramerate = 0;
    private final Bundle b = new Bundle();

    public void setView(GLMapView view) {
        this.mapView = view;
    }

    public void setFrameRate(float frameRate) {
        // set the number of milliseconds we want to spend rendering each frame
        // based on the specified frame rate
        if (frameRate <= 0.0f)
            this.targetMillisPerFrame = 0;
        else
            this.targetMillisPerFrame = (long) Math.ceil(1000.0f / frameRate);
    }

    public double getFramerate() {
        return currentFramerate;
    }

    public Bundle getGPUInfo() { 
        return b;
    }

    /**
     * @deprecated does nothing
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public void pauseRender(boolean state) {
    }

    long lastReport = 0L;

    @Override
    public void onDrawFrame(GL10 arg0) {
        if (count == 0) {
            timeCall = SystemClock.uptimeMillis();
        } else if (count > 1000) {
            currentFramerate = (1000000.0 / (SystemClock.uptimeMillis() - timeCall));
            Log.v(TAG, "map framerate (f/s) = " + currentFramerate);
            timeCall = SystemClock.uptimeMillis();
            count = 0;
            lastReport = timeCall;
        } else if ((SystemClock.uptimeMillis() - lastReport) > 1000) {
            currentFramerate = ((count * 1000.0d) / (SystemClock.uptimeMillis() - timeCall));
            Log.v(TAG, "map framerate (f/s) = " + currentFramerate);
            lastReport = SystemClock.uptimeMillis();

            if ((SystemClock.uptimeMillis() - timeCall) > 5000) {
                timeCall = SystemClock.uptimeMillis();
                count = 0;
                lastReport = timeCall;
            }
        }
        count++;

        GLES20FixedPipeline.glClearColor(_bgRed, _bgGreen, _bgBlue, 1f);
        GLES20FixedPipeline.glClear(GLES20FixedPipeline.GL_COLOR_BUFFER_BIT
                | GLES20FixedPipeline.GL_STENCIL_BUFFER_BIT
                | GLES20FixedPipeline.GL_DEPTH_BUFFER_BIT);

        GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_MODELVIEW);
        GLES20FixedPipeline.glLoadIdentity();

        final long tick = SystemClock.elapsedRealtime();
        this.mapView.animationDelta = tick-this.mapView.animationLastTick;
        this.mapView.animationLastTick = tick;

        mapView.addRenderDiagnostic(String.format("FPS %.2f", currentFramerate));
        this.mapView.render();

        // slows the pipeline down to effect the desired frame rate
        currCall = SystemClock.uptimeMillis();
        while (lastCall > 0L && this.targetMillisPerFrame > (currCall-lastCall)) {
            final long frameRemaining = this.targetMillisPerFrame - (currCall - lastCall);
            SystemClock.sleep(Math.min(frameRemaining, this.targetMillisPerFrame/2));
            currCall = SystemClock.uptimeMillis();
        }
        lastCall = currCall;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20FixedPipeline.glViewport(0, 0, width, height);
        GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_PROJECTION); // select projection
                                                                             // matrix
        GLES20FixedPipeline.glLoadIdentity(); // reset projection matrix
        GLES20FixedPipeline.glOrthof(0f, width, 0f, height, 0.01f, -1f);
        GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_MODELVIEW);
        GLES20FixedPipeline.glLoadIdentity();
        
        // update the GLMapView bounds when the surface changes
        this.mapView._left = 0;
        this.mapView._bottom = 0;
        this.mapView._right = GLMapView.MATCH_SURFACE;
        this.mapView._top = GLMapView.MATCH_SURFACE;

        fillInfo(gl);
    }

    private void fillInfo(GL10 gl) { 
        b.putString("gl_renderer", gl.glGetString( GL10.GL_RENDERER));
        b.putString("gl_vendor", gl.glGetString(GL10.GL_VENDOR));
        b.putString("gl_version", gl.glGetString(GL10.GL_VERSION));
        b.putString("gl_extensions", gl.glGetString(GL10.GL_EXTENSIONS));
    }

    @Override
    public void onSurfaceCreated(GL10 arg0, EGLConfig arg1) {
        this.mapView.animationLastTick = SystemClock.elapsedRealtime();
        this.mapView.animationDelta = 0L;
        
        // estimate the threshold where hardware transforms can be used
        int[] range = new int[2];
        int[] precision = new int[2];
        GLES20FixedPipeline.glGetShaderPrecisionFormat(GLES20FixedPipeline.GL_VERTEX_SHADER, GLES20FixedPipeline.GL_HIGH_FLOAT, range, 0, precision, 0);

        if(precision[0] < 50) {
            // assuming IEEE-754 encoding, a base of 128 will get us [-180,+180]
            // so we'll use that as our numerator
            final double numer = 128;
            final double denom = Math.pow(2d, precision[0]);
            final double step = numer/denom;
            // compute a coarse meters estimate
            this.mapView.hardwareTransformResolutionThreshold = step*111111d;
        } else {
            this.mapView.hardwareTransformResolutionThreshold = 0d;
        }

        fillInfo(arg0);
        b.putDouble("hardware_transform_treshold", this.mapView.hardwareTransformResolutionThreshold);
        // start GLMapView to sync with the globe and start receiving events
        this.mapView.start();
    }

    public void setBgColor(int color) {
        _bgRed = Color.red(color) / 255f;
        _bgGreen = Color.green(color) / 255f;
        _bgBlue = Color.blue(color) / 255f;
    }

    private float _bgRed = 0f, _bgGreen = 0f, _bgBlue = 0f;
}
