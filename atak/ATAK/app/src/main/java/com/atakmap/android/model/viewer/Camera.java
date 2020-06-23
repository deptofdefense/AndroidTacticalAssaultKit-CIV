
package com.atakmap.android.model.viewer;

import android.opengl.Matrix;

import com.atakmap.coremap.log.Log;
import com.atakmap.math.Plane;
import com.atakmap.math.PointD;
import com.atakmap.math.Ray;
import com.atakmap.math.Vector3D;

public class Camera {
    public float xPos, yPos, zPos; //camera position
    public float xView, yView, zView; //look at position
    public float xUp, yUp, zUp; //up direction

    float[] matrix = new float[16];
    //the allocated memory breaks down how the size of the array grows after matrix multiplication
    float[] buffer = new float[12 + 12 + 16 + 16];

    private boolean changed = false;

    float[] projection;
    float width;
    float height;
    float maxDim;

    public Camera() {
        this(0, 0, 2, 0, 0, -1, 0, 1, 0);
    }

    /*
     * Helper constructor to set up the default position of the camera
     */
    private Camera(float xPos, float yPos, float zPos, float xView, float yView,
            float zView, float xUp, float yUp, float zUp) {
        this.xPos = xPos;
        this.yPos = yPos;
        this.zPos = zPos;

        this.xView = xView;
        this.yView = yView;
        this.zView = zView;

        this.xUp = xUp;
        this.yUp = yUp;
        this.zUp = zUp;
    }

    /**
     * Calculates how far away the camera should be from the 3D model
     *
     * @param direction The zoom factor - (+) indicates moving towards the model
     *                                    (-) indicates moving away from the model
     */
    public synchronized void moveCameraZ(float direction) {
        if (direction == 0)
            return;

        float xLookDirection = xView - xPos;
        float yLookDirection = yView - yPos;
        float zLookDirection = zView - zPos;

        float dp = Matrix.length(xLookDirection, yLookDirection,
                zLookDirection);
        xLookDirection /= dp;
        yLookDirection /= dp;
        zLookDirection /= dp;

        final float threshold = (maxDim != 0f) ? maxDim / 12f : 0f;
        if (dp <= threshold && direction < 1f) {
            // once we cross the zoom threshold, simply move the lookat and camera
            xView += (xLookDirection * direction);
            yView += (yLookDirection * direction);
            zView += (zLookDirection * direction);

            xPos = xView - (xLookDirection * dp);
            yPos = yView - (yLookDirection * dp);
            zPos = zView - (zLookDirection * dp);
        } else {
            //UpdateCamera(xLookDirection, yLookDirection, zLookDirection, direction);
            xPos = xView - (xLookDirection * dp * direction);
            yPos = yView - (yLookDirection * dp * direction);
            zPos = zView - (zLookDirection * dp * direction);
        }
        Log.d("Camera", "Update zoom, factor=" + direction);

        setChanged(true);
    }

    /**
     * Pans the camera across the model
     * @param tx    The x-translation, in screen pixel space
     * @param ty    The y-translation, in screen pixel space
     */
    public synchronized void moveCameraXY(float tx, float ty) {
        if (this.projection == null)
            return;

        // invert the y-movement to account for the flipped axes between android.graphics and gles
        ty *= -1f;

        // convert the translation into NDC
        tx /= (width / 2f);
        ty /= (height / 2f);

        // record the lookat vector direction and magnitude
        float xLookDirection = xView - xPos;
        float yLookDirection = yView - yPos;
        float zLookDirection = zView - zPos;

        final float dp = Matrix.length(xLookDirection, yLookDirection,
                zLookDirection);
        xLookDirection /= dp;
        yLookDirection /= dp;
        zLookDirection /= dp;

        // compose the MVP matrix
        Matrix.setLookAtM(matrix, 0, xPos, yPos, zPos, xView, yView, zView, xUp,
                yUp, zUp);
        Matrix.multiplyMM(buffer, 0, projection, 0, matrix, 0);

        float[] a = new float[4];
        float[] b = new float[4];

        // define the plane that we will be panning. the current implementation
        // utilizes the plane tangent to the look-at vector, passing through
        // the lookat location. future implementations could utilize
        Plane lookatPlane = new Plane(
                new Vector3D(xLookDirection, yLookDirection, zLookDirection),
                new PointD(xView, yView, zView));

        // invert the MVP to be able to transform from NDC into world coordinates
        Matrix.invertM(matrix, 0, buffer, 0);

        PointD p0 = new PointD(0d, 0d, 0d);
        PointD p1 = new PointD(0d, 0d, 0d);
        // project ray through 0,0,0, intersect with view plane at lookat
        a[0] = 0;
        a[1] = 0;
        a[2] = 0;
        a[3] = 1;
        Matrix.multiplyMV(b, 0, matrix, 0, a, 0);
        b[0] /= b[3];
        b[1] /= b[3];
        b[2] /= b[3];
        b[3] /= b[3];
        p0.x = b[0];
        p0.y = b[1];
        p0.z = b[2];
        a[0] = 0;
        a[1] = 0;
        a[2] = 1;
        a[3] = 1;
        Matrix.multiplyMV(b, 0, matrix, 0, a, 0);
        b[0] /= b[3];
        b[1] /= b[3];
        b[2] /= b[3];
        b[3] /= b[3];
        p1.x = b[0];
        p1.y = b[1];
        p1.z = b[2];
        PointD cIsect = lookatPlane.intersect(new Ray(p0,
                new Vector3D(p1.x - p0.x, p1.y - p0.y, p1.z - p0.z)));

        // intersect can potentially produce a null value
        if (cIsect == null)
            return;

        // project ray through NDC tx,ty,0, intersect with view plane at lookat
        a[0] = tx;
        a[1] = ty;
        a[2] = 0;
        a[3] = 1;
        Matrix.multiplyMV(b, 0, matrix, 0, a, 0);
        b[0] /= b[3];
        b[1] /= b[3];
        b[2] /= b[3];
        b[3] /= b[3];
        p0.x = b[0];
        p0.y = b[1];
        p0.z = b[2];
        a[0] = tx;
        a[1] = ty;
        a[2] = 1;
        a[3] = 1;
        Matrix.multiplyMV(b, 0, matrix, 0, a, 0);
        b[0] /= b[3];
        b[1] /= b[3];
        b[2] /= b[3];
        b[3] /= b[3];
        p1.x = b[0];
        p1.y = b[1];
        p1.z = b[2];
        PointD tIsect = lookatPlane.intersect(new Ray(p0,
                new Vector3D(p1.x - p0.x, p1.y - p0.y, p1.z - p0.z)));

        // intersect can potentially produce a null value
        if (tIsect == null)
            return;

        // translate lookat by delta intersect
        xView += tIsect.x - cIsect.x;
        yView += tIsect.y - cIsect.y;
        zView += tIsect.z - cIsect.z;

        // update the camera based on the new lookat location
        xPos = xView - (xLookDirection * dp);
        yPos = yView - (yLookDirection * dp);
        zPos = zView - (zLookDirection * dp);

        this.setChanged(true);
    }

    /**
     * Returns an array of the camera's position coordinates
     *
     * @return A vector representing the camera's position
     */
    public float[] getLocationVector() {
        return new float[] {
                xPos, yPos, zPos, 1f
        };
    }

    /**
     * Returns an array of the camera's view coordinates
     *
     * @return A vector representing the camera's view
     */
    public float[] getLocationViewVector() {
        return new float[] {
                xView, yView, zView, 1f
        };
    }

    /**
     * Returns an array of the camera's zoom coordinates
     *
     * @return A vector representing the camera's zoom
     */
    public float[] getLocationUpVector() {
        return new float[] {
                xUp, yUp, zUp, 1f
        };
    }

    /**
     * Let's us know if any of the camera's values have changed
     *
     * @return True if the camera changed, false otherwise
     */
    public boolean hasChanged() {
        return this.changed;
    }

    /**
     * Used to indicate whether or not we want to let other objects
     * know the camera's value has changed
     *
     * @param changed What the "changed" field should be set to
     */
    public void setChanged(boolean changed) {
        this.changed = changed;
    }
}
