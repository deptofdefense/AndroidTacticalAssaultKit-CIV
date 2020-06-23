
package com.atakmap.android.maps.graphics;

import java.nio.ShortBuffer;

public class GLShortArray extends GLArray {
    public GLShortArray(int pointSize, int pointCount) {
        super(2, pointSize, pointCount);

        _shadow = _buffer.asShortBuffer();
    }

    public void setX(int pointIndex, short x) {
        if (_pointSize > 0) {
            _shadow.put((pointIndex * _pointSize), x);
        }
    }

    public void setY(int pointIndex, short y) {
        if (_pointSize > 1) {
            _shadow.put((pointIndex * _pointSize) + 1, y);
        }
    }

    public void setZ(int pointIndex, short z) {
        if (_pointSize > 2) {
            _shadow.put((pointIndex * _pointSize) + 2, z);
        }
    }

    public void setW(int pointIndex, short w) {
        if (_pointSize > 3) {
            _shadow.put((pointIndex * _pointSize) + 3, w);
        }
    }

    public void offset(int pointIndex) {
        _buffer.position(pointIndex * _pointSize * _elemSize);
    }

    private ShortBuffer _shadow;
}
