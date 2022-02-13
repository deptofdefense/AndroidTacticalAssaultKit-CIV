
package com.atakmap.android.maps.graphics;

import java.nio.FloatBuffer;

public class GLFloatArray extends GLArray {
    public GLFloatArray(int pointSize, int pointCount) {
        super(4, pointSize, pointCount);

        this._shadow = _buffer.asFloatBuffer();
    }

    public GLFloatArray makeScaledCopyXY(float scalex, float scaley) {
        GLFloatArray fa = new GLFloatArray(getPointSize(), getPointCount());
        for (int i = 0; i < getPointCount(); ++i) {
            fa.setX(i, getX(i) * scalex);
            fa.setY(i, getY(i) * scaley);
        }
        return fa;
    }

    public void setX(int pointIndex, float x) {
        _shadow.put((pointIndex * _pointSize), x);
    }

    public float getX(int pointIndex) {
        return _shadow.get(pointIndex * _pointSize);
    }

    public float getY(int pointIndex) {
        return _shadow.get(pointIndex * _pointSize + 1);
    }

    public void setY(int pointIndex, float y) {
        _shadow.put((pointIndex * _pointSize) + 1, y);
    }

    public void setZ(int pointIndex, float z) {
        _shadow.put((pointIndex * _pointSize) + 2, z);
    }

    private FloatBuffer _shadow;
}
