
package com.atakmap.android.maps.graphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import com.atakmap.opengl.GLES20FixedPipeline;

import android.graphics.Color;

public class GLDebugDraw {

    private static ByteBuffer _debugDotBuffer;
    private static ByteBuffer _debugBoxBuffer;

    public static void dot(int color) {
        if (_debugDotBuffer == null) {
            _debugDotBuffer = GLTriangle.Strip.createBuffer(
                    GLTriangle.Strip.createRectangle(0f, 0f, 5f, 5f, null),
                    _debugDotBuffer);
        }
        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);

        float r = Color.red(color) / 255f;
        float g = Color.green(color) / 255f;
        float b = Color.blue(color) / 255f;
        float a = Color.alpha(color) / 255f;
        GLES20FixedPipeline.glColor4f(r, g, b, a);

        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                _debugDotBuffer);
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_STRIP,
                0, 4);
        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline
                .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
    }

    public static void box(int color) {
        if (_debugBoxBuffer == null) {
            _debugBoxBuffer = com.atakmap.lang.Unsafe.allocateDirect(4 * 2 * 4);
            _debugBoxBuffer.order(ByteOrder.nativeOrder());
            float[] fs = {
                    0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f,
            };
            FloatBuffer fb = _debugBoxBuffer.asFloatBuffer();
            fb.put(fs);
        }
        GLES20FixedPipeline
                .glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);

        float r = Color.red(color) / 255f;
        float g = Color.green(color) / 255f;
        float b = Color.blue(color) / 255f;
        float a = Color.alpha(color) / 255f;
        GLES20FixedPipeline.glColor4f(r, g, b, a);

        GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0,
                _debugBoxBuffer);
        GLES20FixedPipeline
                .glDrawArrays(GLES20FixedPipeline.GL_LINE_LOOP, 0, 4);
        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline
                .glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
    }
}
