
package com.atakmap.opengl;

import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLES30;

import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.Matrix;

import org.apache.commons.lang.ArrayUtils;

import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Provides a method for drawing anti aliased lines using the techniques described in
 * https://blog.mapbox.com/drawing-antialiased-lines-with-opengl-8766f34192dc
 */
public class GLAntiAliasedLine {
    public static final String TAG = "GLAntiAliasedLine";
    private DoubleBuffer _segmentVerts;
    private FloatBuffer _forwardSegmentVerts;
    private FloatBuffer _normals;
    private ShortBuffer _indices;

    /**
     * Uses the given vertex positions to populate the buffers used in drawing lines.
     * @param verts The vertex positions used to populate the buffers. Should be 2 component
     *              vertices and the first vertex should not be repeated.
     */
    public void setLineData(FloatBuffer verts) {
        if (_segmentVerts == null) {
            _segmentVerts = Unsafe.allocateDirect(verts.limit() * 4, DoubleBuffer.class);
            _forwardSegmentVerts= Unsafe.allocateDirect(verts.limit() * 4, FloatBuffer.class);
            _normals = Unsafe.allocateDirect(_segmentVerts.limit(), FloatBuffer.class);
            _indices = Unsafe.allocateDirect(verts.limit() * 3, ShortBuffer.class);
        } else if (_segmentVerts.limit() != (verts.limit() * 4)) {
            Unsafe.free(_segmentVerts);
            Unsafe.free(_forwardSegmentVerts);
            Unsafe.free(_normals);
            Unsafe.free(_indices);

            _segmentVerts = Unsafe.allocateDirect(verts.limit() * 4, DoubleBuffer.class);
            _forwardSegmentVerts= Unsafe.allocateDirect(verts.limit() * 4, FloatBuffer.class);
            _normals = Unsafe.allocateDirect(_segmentVerts.limit(), FloatBuffer.class);
            _indices = Unsafe.allocateDirect(verts.limit() * 3, ShortBuffer.class);
        }
        short baseIndex = 0;
        for (int currVert = 0; currVert < verts.limit(); currVert += 2) {
            int nextVert = (currVert + 2) % verts.limit();
            // start of line segment
            _segmentVerts.put(verts.get(currVert));
            _segmentVerts.put(verts.get(currVert + 1));
            _segmentVerts.put(verts.get(currVert));
            _segmentVerts.put(verts.get(currVert + 1));

            // end of line segment
            _segmentVerts.put(verts.get(nextVert));
            _segmentVerts.put(verts.get(nextVert + 1));
            _segmentVerts.put(verts.get(nextVert));
            _segmentVerts.put(verts.get(nextVert + 1));

            double dx = verts.get(nextVert) - verts.get(currVert);
            double dy = verts.get(nextVert + 1) - verts.get(currVert + 1);
            double length = Math.sqrt(dx * dx + dy * dy);
            dx /= length;
            dy /= length;

            // start point normals
            _normals.put((float) -dy);
            _normals.put((float) dx);

            _normals.put((float) dy);
            _normals.put((float) -dx);

            // end point normals
            _normals.put((float) -dy);
            _normals.put((float) dx);

            _normals.put((float) dy);
            _normals.put((float) -dx);


            _indices.put((short) (baseIndex % (_segmentVerts.limit() / 2)));
            _indices.put((short) ((baseIndex + 1) % (_segmentVerts.limit() / 2)));
            _indices.put((short) ((baseIndex + 2) % (_segmentVerts.limit() / 2)));

            _indices.put((short) ((baseIndex + 2) % (_segmentVerts.limit() / 2)));
            _indices.put((short) ((baseIndex + 1) % (_segmentVerts.limit() / 2)));
            _indices.put((short) ((baseIndex + 3) % (_segmentVerts.limit() / 2)));

            baseIndex += 4;
        }

        _segmentVerts.rewind();
        _normals.rewind();
        _indices.rewind();
    }


    /**
     * Draws the antialiased line specified by a previous call to setLineData()
     * @param view The GLMapView used for rendering.
     * @param red The red component of the color that the line will be drawn with.
     * @param green The green component of the color that the line will be drawn with.
     * @param blue The blue component of the color that the line will be drawn with.
     * @param width The width of the line to be drawn.
     */
    public void draw(GLMapView view, float red, float green, float blue,
            float width) {

        AntiAliasingProgram program = AntiAliasingProgram.get();
        Matrix normalRotation = Matrix.getIdentity();
        normalRotation.rotate(-Math.toRadians(view.drawRotation));
        view.forward(_segmentVerts, _forwardSegmentVerts);
        GLES30.glUseProgram(program.handle);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

        GLES30.glEnableVertexAttribArray(program.aPosition);
        GLES30.glEnableVertexAttribArray(program.aNormal);

        GLES30.glVertexAttribPointer(program.aPosition, 2, GLES30.GL_FLOAT, false, 0, _forwardSegmentVerts);
        GLES30.glVertexAttribPointer(program.aNormal, 2, GLES30.GL_FLOAT, false, 0, _normals);

        normalRotation.get(view.scratch.matrixD, Matrix.MatrixOrder.COLUMN_MAJOR);
        for (int i = 0; i < 16; i++) {
            view.scratch.matrixF[i] = (float)view.scratch.matrixD[i];
        }
//        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);
        GLES30.glUniformMatrix4fv(program.uModelView, 1, false, view.scratch.matrixF, 0);

        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
        GLES30.glUniformMatrix4fv(program.uProjection, 1, false, view.scratch.matrixF, 0);

        GLES30.glUniform1f(program.uWidth, width * GLRenderGlobals.getRelativeScaling());
        GLES30.glUniform3f(program.uColor, red, green, blue);

        GLES30.glDrawElements(GLES30.GL_TRIANGLES, _indices.limit(), GLES30.GL_UNSIGNED_SHORT,
                _indices);
        GLES30.glDisableVertexAttribArray(program.aPosition);
        GLES30.glDisableVertexAttribArray(program.aNormal);
        GLES30.glDisable(GLES30.GL_BLEND);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        Unsafe.free(_segmentVerts);
        Unsafe.free(_forwardSegmentVerts);
        Unsafe.free(_normals);
        Unsafe.free(_indices);
    }

    /**
     * Helper class that creates a static instance of the shader program and contains the locations
     * of uniform and attribute variables.
     */
    private static class AntiAliasingProgram {
        static AntiAliasingProgram instance;
        static final String VERTEX_SHADER = "" +
                "#version 300 es\n" +
                "\n" +
                "layout(location = 0) uniform mat4 uModelView;\n" +
                "layout(location = 4) uniform mat4 uProjection;\n" +
                "layout(location = 8) uniform float uWidth;\n" +
                "\n" +
                "layout(location = 0) in vec2 aPosition;\n" +
                "layout(location = 1) in vec2 aNormal;\n" +
                "\n" +
                "out vec2 fNormal;\n" +
                "\n" +
                "void main() {\n" +
                "   // Make sure to transform the normal so it matches any rotation of the map\n" +
                "   vec4 transformedNormal = inverse(uModelView) * vec4(aNormal, 0, 0);\n" +
                "   // Calculate the length from the center of the line to the edge by scaling the normal\n" +
                "   vec4 halfLineWidth = vec4(transformedNormal.xy * uWidth, 0, 0);\n" +
                "   vec4 pos = vec4(aPosition, 0, 1);\n" +
                "   gl_Position = uProjection * (pos + halfLineWidth);\n" +
                "   fNormal = transformedNormal.xy;\n" +
                "}\n";
        static final String FRAGMENT_SHADER = "" +
                "#version 300 es\n" +
                "\n" +
                "in vec2 fNormal;\n" +
                "layout(location = 9) uniform vec3 uColor;\n" +
                "\n" +
                "out vec4 fragmentColor;\n" +
                "\n" +
                "void main() {\n" +
                "   // Set the alpha of the color based on the interpolated normal length\n" +
                "   fragmentColor = vec4(uColor, 1.0f - length(fNormal));\n" +
                "}\n";

        public int handle;
        public final int uModelView = 0;
        public final int uProjection = 4;
        public final int uWidth = 8;
        public final int uColor = 9;
        public final int aPosition = 0;
        public final int aNormal = 1;

        /**
         * Compiles the antialiasing shader program.
         */
        private AntiAliasingProgram() {
            int vsh = GLES20FixedPipeline.GL_NONE;
            int fsh = GLES20FixedPipeline.GL_NONE;
            try {
                vsh = GLES20FixedPipeline.loadShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER);
                fsh = GLES20FixedPipeline.loadShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
                this.handle = GLES20FixedPipeline.createProgram(vsh, fsh);
            } finally {
                if (vsh != GLES30.GL_NONE)
                    GLES30.glDeleteShader(vsh);
                if (fsh != GLES30.GL_NONE)
                    GLES30.glDeleteShader(fsh);
            }
        }

        /**
         * Retrieves a static instance of the antialiasing program.
         * @return The static antialiasing program.
         */
        static AntiAliasingProgram get() {
            if (instance == null) {
                instance = new AntiAliasingProgram();
            }
            return instance;
        }

    }
}
