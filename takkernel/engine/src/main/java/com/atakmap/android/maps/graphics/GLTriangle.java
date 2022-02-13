
package com.atakmap.android.maps.graphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import com.atakmap.lang.Unsafe;
import com.atakmap.opengl.GLES20FixedPipeline;

import gov.tak.api.engine.Shader;
import gov.tak.platform.commons.opengl.GLES30;

public abstract class GLTriangle extends GLArray {

    protected int _maxPointCount;
    protected int defaultMode;

    protected GLTriangle(int defaultMode, int elementSize, int pointSize,
            int pointCount) {
        super(elementSize, pointSize, pointCount);
        this.defaultMode = defaultMode;
    }

    public void release() {
        if (_buffer != null) {
            Unsafe.free(_buffer);
            _buffer = null;
        }
    }

    protected void resizeBuffer(int newCount) {
        ByteBuffer b = com.atakmap.lang.Unsafe.allocateDirect(_pointSize * newCount
                * _elemSize);
        b.order(ByteOrder.nativeOrder());
        b.put(_buffer);
        b.rewind();
        _buffer = b;
        _maxPointCount = _pointCount = newCount;
    }

    synchronized public void setPoints(float[] points) {
        _pointCount = points.length / _pointSize;
        if (_pointCount > _maxPointCount) {
            resizeBuffer(_pointCount);
        }
        int idx = 0;
        for (int i = 0; i < _pointCount; i++) {
            for (int j = 0; j < _pointSize; j++)
                this.set(i, j, points[idx++]);
        }
    }
    public void draw(Shader shader, int mode) {
        if (mode != this.defaultMode)
            switch (mode) {
                case GLES20FixedPipeline.GL_LINE_LOOP:
                case GLES20FixedPipeline.GL_LINE_STRIP:
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        GLES30.glEnableVertexAttribArray(shader.getAVertexCoords());
        GLES30.glVertexAttribPointer(shader.getAVertexCoords(), _pointSize, GLES30.GL_FLOAT, false, 0, _buffer);
        GLES30.glDrawArrays(mode, 0, _pointCount);
        GLES30.glDisableVertexAttribArray(shader.getAVertexCoords());
    }

    public void setX(int pointIndex, float x) {
        set(pointIndex, 0, x);
    }

    public void setY(int pointIndex, float y) {
        set(pointIndex, 1, y);
    }

    public void setZ(int pointIndex, float z) {
        set(pointIndex, 2, z);
    }

    protected abstract void set(int pointIndex, int off, float v);

    public void draw() {
        draw(this.defaultMode);
    }

    public synchronized void draw(int mode) {
        if (mode != this.defaultMode)
            switch (mode) {
                case GLES20FixedPipeline.GL_LINE_LOOP:
                case GLES20FixedPipeline.GL_LINE_STRIP:
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glVertexPointer(_pointSize,
                GLES20FixedPipeline.GL_FLOAT, 0, _buffer);
        GLES20FixedPipeline.glDrawArrays(mode, 0, _pointCount);
        GLES20FixedPipeline
                .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
    }

    public void setPointCount(int pointCount) {
        _pointCount = pointCount;
        if (_pointCount > _maxPointCount) {
            resizeBuffer(pointCount);
        }
    }

    public final static class Fan extends GLTriangle {

        private IntBuffer _shadow;

        public Fan(int pointSize, int pointCount) {
            super(GLES20FixedPipeline.GL_TRIANGLE_FAN, 4, pointSize, pointCount);
            _shadow = _buffer.asIntBuffer();
        }

        @Override
        protected void set(int pointIndex, int off, float v) {
            if (pointIndex < _pointCount && _pointSize > off)
                _shadow.put((pointIndex * _pointSize) + off,
                        Float.floatToIntBits(v));
        }

        @Override
        protected void resizeBuffer(int newSize) {
            super.resizeBuffer(newSize);
            _shadow = _buffer.asIntBuffer();
        }
    }

    public final static class Strip extends GLTriangle {

        private FloatBuffer _shadow;

        public Strip(int pointSize, int pointCount) {
            super(GLES20FixedPipeline.GL_TRIANGLE_STRIP, 4, pointSize,
                    pointCount);
            _shadow = _buffer.asFloatBuffer();
        }

        @Override
        protected void set(int pointIndex, int off, float v) {
            if (pointIndex < _pointCount && _pointSize > off)
                _shadow.put((pointIndex * _pointSize) + off, v);
        }

        @Override
        protected void resizeBuffer(int newSize) {
            super.resizeBuffer(newSize);
            _shadow = _buffer.asFloatBuffer();
        }

        public static float[] createRectangle(float x, float y, float width,
                float height, float[] out) {
            if (out == null || out.length < 8) {
                out = new float[8];
            }

            out[0] = x;
            out[1] = y;

            out[2] = x;
            out[3] = y + height;

            out[4] = x + width;
            out[5] = y;

            out[6] = x + width;
            out[7] = y + height;

            return out;
        }

        public static ByteBuffer createBuffer(float[] strip, ByteBuffer out) {
            if (out == null) {
                out = com.atakmap.lang.Unsafe.allocateDirect(strip.length * 4);
                out.order(ByteOrder.nativeOrder());
            }
            FloatBuffer fb = out.asFloatBuffer();
            fb.put(strip);
            return out;
        }
    }
}
