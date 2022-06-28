
package com.atakmap.android.image.equirectangular;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import com.atakmap.coremap.log.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ImmersiveGLRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "ImmersiveGLRenderer";

    // The following view parameters are chosen to match the prior iteration
    // of this class that was not GL based.
    /** Field of view in Y direction, in degrees */
    private static final float FOV = 90;
    /** Aspect ratio for viewport/field of view as width / height */
    private static final float FOV_AR = 4.0f / 3.0f;
    /**
     * Distance from viewer that sphere is rendered.
     * This is a relative unit to the base radius of the rendered sphere.
     * 1.0 puts you basically tangent to the sphere (you won't see much!)
     */
    private static final float VIEW_DISTANCE = 560;

    // Render thread only
    private GLTexturedSphereProgram program;
    // Render thread only
    // Matrix defining the current view
    private final float[] viewMatrix;
    // Render thread only
    // No persistent state; used during renders to hold
    // model/view matrix computation results
    private final float[] mvpMatrix;
    // Tracks last supplied and loaded bitmap.
    // Render thread only
    private Bitmap lastBitmap;

    // Non-null when render thread should load new bitmap.
    // Shared on render and external threads, synchronize access
    private Bitmap newBitmap;
    // Shared on render and external threads, synchronize access
    // Units: radians
    private double curRotX, curRotY;

    public ImmersiveGLRenderer() {
        viewMatrix = new float[16];
        mvpMatrix = new float[16];
    }

    /**
     * Set the view rotation to the given values
     * @param x amount to rotate about the X axis, in radians
     * @param y amount to rotate about the Y axis, in radians
     */
    public synchronized void setRotation(double x, double y) {
        curRotX = x;
        curRotY = y;
    }

    /**
     * Load a new bitmap into the view.  Replaces the prior bitmap.
     * The supplied bitmap should not be recycled by the caller.
     * This method is safe to call from any thread.
     * @param sphereImage new image to load
     * @return true if loaded successfully and caller should tell the renderer it is now dirty
     *          false if image load fails
     */
    public boolean loadNewImage(Bitmap sphereImage) {
        // spherical representation of the image
        if (sphereImage == null)
            return false;

        synchronized (this) {
            this.newBitmap = sphereImage;
        }
        return true;
    }

    /**
     * Called when the viewer is no longer open.
     */
    public void dispose() {
        if (newBitmap != null) {
            newBitmap.recycle();
            newBitmap = null;
        }

        if (lastBitmap != null) {
            lastBitmap.recycle();
            lastBitmap = null;
        }
    }

    public void onSurfaceChanged(GL10 gl, int width, int height) {
        //gl.glViewport(width/4, 0, width/2, height);
        // Probably actually want this but using above to match old renderer
        gl.glViewport(0, 0, width, height);
        recomputeViewMatrix();
    }

    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        try {
            synchronized (this) {
                if (newBitmap == null)
                    // Any former texture will be lost
                    // Tell render thread to reload last loaded
                    // bitmap into our new context
                    newBitmap = lastBitmap;
            }
            program = new GLTexturedSphereProgram(null);
        } catch (GLTexturedSphereProgram.GLProgramException ex) {
            Log.e(TAG, "Failed to create GL program on surface creation", ex);
        }
    }

    public void onDrawFrame(GL10 gl) {
        GLES20.glClearColor(0.5f, 0, 0, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (program != null) {
            synchronized (this) {
                if (newBitmap != null) {
                    try {
                        program.uploadTexture(newBitmap);
                        lastBitmap = newBitmap;
                    } catch (GLTexturedSphereProgram.GLProgramException ex) {
                        Log.e(TAG, "Error loading new image", ex);
                    }

                    newBitmap = null;
                }
            }

            try {
                Matrix.setLookAtM(mvpMatrix, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0);
                Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, mvpMatrix, 0);
                synchronized (this) {
                    Matrix.rotateM(mvpMatrix, 0,
                            (float) Math.toDegrees(-curRotX), 1, 0, 0);
                    Matrix.rotateM(mvpMatrix, 0,
                            (float) Math.toDegrees(curRotY), 0, 1, 0);
                }
                Matrix.scaleM(mvpMatrix, 0, VIEW_DISTANCE, VIEW_DISTANCE,
                        VIEW_DISTANCE);
                program.draw(mvpMatrix);
            } catch (GLTexturedSphereProgram.GLProgramException ex) {
                Log.e(TAG, "Drawing failed", ex);
            }
        }
    }

    private void recomputeViewMatrix() {
        // This is effectively fixed at present, but is compartmentalized
        // out for future enhancement based on additional factors
        Matrix.setIdentityM(viewMatrix, 0);
        Matrix.perspectiveM(viewMatrix, 0, FOV, FOV_AR, 1, VIEW_DISTANCE + 5);
    }

}
