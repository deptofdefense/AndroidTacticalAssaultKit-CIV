
package com.atakmap.android.vehicle.model.opengl;

import android.graphics.PointF;
import android.opengl.GLES30;

import com.atakmap.lang.Unsafe;
import com.atakmap.map.opengl.GLMapView;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * A single mesh used for multiple instances of a map item
 */
public class GLInstancedPolyline extends GLInstancedRenderable {

    private static final String TAG = "GLInstancedPolyline";

    // Vertex shader locations
    private static final int LOC_POSITION = 0,
            LOC_COLOR = 1,
            LOC_MODEL = 2;

    private static final String VERTEX_SHADER_SOURCE = "" +
            "#version 300 es\n" +
            "uniform mat4 projection;\n" +
            "uniform mat4 view;\n" +
            "layout(location = " + LOC_POSITION + ") in vec2 position;\n" +
            "layout(location = " + LOC_COLOR + ") in vec3 color;\n" +
            "layout(location = " + LOC_MODEL + ") in mat4 model;\n" +
            "out vec3 oColor;\n" +
            "void main() {\n" +
            "   mat4 pvm = projection * view * model;\n" +
            "   gl_Position = pvm * vec4(position, 0.0, 1.0);\n" +
            "   oColor = color;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER_SOURCE = "" +
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "out vec4 fragmentColor;\n" +
            "in vec3 oColor;\n" +
            "void main() {\n" +
            "  fragmentColor = vec4(oColor, 1.0);\n" +
            "}\n";

    private static Integer _programID;

    private final FloatBuffer _points;
    private final int _numPoints;
    private final int _pointBufferSize;
    private int[] _posBuffer;

    // Color buffer
    private int[] _colorBufferID;
    private int _colorBufferSize;
    private long _colorBufferPtr;
    private FloatBuffer _colorBuffer;

    public GLInstancedPolyline(String name, List<PointF> points) {
        super(name, GLMapView.RENDER_PASS_SURFACE);
        _numPoints = points.size();
        _pointBufferSize = _numPoints * 2 * 4;
        _points = createFloatBuffer(_pointBufferSize);
        for (PointF p : points) {
            _points.put(p.x);
            _points.put(p.y);
        }
        _points.clear();
    }

    /**
     * Compile the main program shader
     */
    @Override
    protected Integer compileShader() {
        return _programID != null ? _programID
                : (_programID = compileShader(VERTEX_SHADER_SOURCE,
                        FRAGMENT_SHADER_SOURCE));
    }

    @Override
    protected int getPositionPointer() {
        return LOC_MODEL;
    }

    @Override
    protected void setupVertexBuffers() {
        // Positions buffer
        _points.clear();
        _posBuffer = new int[1];
        GLES30.glGenBuffers(1, _posBuffer, 0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, _posBuffer[0]);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, _pointBufferSize,
                _points, GLES30.GL_STATIC_DRAW);
        GLES30.glVertexAttribPointer(LOC_POSITION, 2, GLES30.GL_FLOAT,
                false, SIZE_VEC2, 0);
        GLES30.glEnableVertexAttribArray(LOC_POSITION);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    @Override
    protected void expandInstanceBuffers() {
        super.expandInstanceBuffers();

        // Create color modulation buffers
        deleteBuffers(_colorBuffer, _colorBufferID);
        _colorBufferSize = SIZE_VEC3 * _instanceLimit;
        _colorBuffer = createFloatBuffer(_colorBufferSize);
        _colorBufferPtr = Unsafe.getBufferPointer(_colorBuffer);
        _colorBufferID = setupInstanceBuffer(_colorBuffer, _colorBufferSize);
    }

    @Override
    protected void updateMatrices(GLMapView view) {
        super.updateMatrices(view);

        // Color modulation
        long cPtr = _colorBufferPtr;
        for (GLInstanceData data : _drawInstances) {
            float[] color = data.getColor();
            Unsafe.setFloats(cPtr, color[0], color[1], color[2]);
            cPtr += SIZE_VEC3;
        }

        // Setup instance color modulation
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, _colorBufferID[0]);
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, _colorBufferSize,
                _colorBuffer);

        setupInstancePointer(LOC_COLOR, 3, SIZE_VEC3, 0);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
    }

    @Override
    protected void drawInstanced(GLMapView view) {
        // Depth testing for proper face draw order
        boolean enableDepth = GLES30.glIsEnabled(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);

        // Alpha blending
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

        // Draw the line
        // TODO: Variable stroke width? Doesn't seem possible with instances
        //  unless we write our own stroke fragment shader
        GLES30.glLineWidth(3);
        GLES30.glDrawArraysInstanced(GLES30.GL_LINE_STRIP, 0, _numPoints,
                getNumInstances());

        GLES30.glDisable(GLES30.GL_BLEND);

        if (enableDepth)
            GLES30.glEnable(GLES30.GL_DEPTH_TEST);
    }

    @Override
    public void release() {
        _colorBufferID = deleteBuffers(_colorBuffer, _colorBufferID);
        _colorBuffer = null;
        _posBuffer = deleteBuffers(_posBuffer);
        super.release();
    }
}
