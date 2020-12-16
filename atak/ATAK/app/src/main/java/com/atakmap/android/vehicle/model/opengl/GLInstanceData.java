
package com.atakmap.android.vehicle.model.opengl;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.math.Matrix;

/**
 * Data used for rendering a single mesh instance
 */
public class GLInstanceData {

    // UID incrementation
    private static long UID_INC = 0;

    private final long _uid;

    // Local frame used by the mesh (optional)
    private final Matrix _localFrame = Matrix.getIdentity();
    private final Matrix _localECEF = Matrix.getIdentity();

    // LLA center point of this model (optional)
    public GeoPoint _anchor;

    // Color modulation
    private final float[] _color = {
            1.0f, 1.0f, 1.0f, 1.0f
    };

    // Flag that this instance needs to be read again by the mesh renderer
    private boolean _dirty;

    public GLInstanceData() {
        synchronized (GLInstanceData.class) {
            _uid = UID_INC++;
        }
    }

    /**
     * Set the local frame transformation
     * @param localFrame Local frame matrix (LLA)
     */
    public void setLocalFrame(Matrix localFrame, Matrix localECEF) {
        _localFrame.set(localFrame);
        _localECEF.set(localECEF);
    }

    /**
     * Set the anchor point
     * @param anchor Anchor point (LLA)
     */
    public void setAnchor(GeoPoint anchor) {
        _anchor = anchor;
    }

    /**
     * Set the color modulation
     * @param r Red value (0.0 to 1.0)
     * @param g Green value
     * @param b Blue value
     * @param a Alpha value
     */
    public void setColor(float r, float g, float b, float a) {
        _color[0] = r;
        _color[1] = g;
        _color[2] = b;
        _color[3] = a;
    }

    /**
     * Flag this instance data for an update
     * @param dirty True to update
     */
    public void setDirty(boolean dirty) {
        _dirty = dirty;
    }

    /**
     * Get the UID of this instance data
     * @return UID
     */
    public long getUID() {
        return _uid;
    }

    /**
     * Get the local frame matrix
     * @return Matrix
     */
    public Matrix getLocalFrame() {
        return _localFrame;
    }

    /**
     * Get the matrix for converting to ECEF (3D globe mode)
     * @return Local ECEF matrix
     */
    public Matrix getLocalECEF() {
        return _localECEF;
    }

    /**
     * Get the position of this instance
     * @return Anchor point
     */
    public GeoPoint getAnchor() {
        return _anchor;
    }

    /**
     * Get the color modulation
     * @return Color floats [red, green, blue, alpha] from 0.0 to 1.0
     */
    public float[] getColor() {
        return _color;
    }

    /**
     * Check if this instance data has been updated and needs to be re-read
     * @return True if "dirty"
     */
    public boolean isDirty() {
        return _dirty;
    }
}
