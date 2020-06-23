
package com.atakmap.android.maps.graphics;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class GLArray {

    public GLArray(int elementSize, int pointSize, int pointCount) {
        _elemSize = elementSize;
        _pointSize = pointSize;
        _pointCount = pointCount;

        ByteBuffer b = com.atakmap.lang.Unsafe.allocateDirect(_pointCount * _pointSize
                * _elemSize);
        b.order(ByteOrder.nativeOrder());
        _buffer = b;
    }

    public Buffer getBuffer() {
        return _buffer;
    }

    public int getPointSize() {
        return _pointSize;
    }

    public int getPointCount() {
        return _pointCount;
    }

    protected int _pointCount;
    protected int _pointSize;
    protected int _elemSize;

    protected ByteBuffer _buffer;
}
