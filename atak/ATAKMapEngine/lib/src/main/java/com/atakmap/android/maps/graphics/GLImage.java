
package com.atakmap.android.maps.graphics;

import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.atakmap.opengl.GLES20FixedPipeline;

public class GLImage {

    private int vertexBufferId;

    private int _textureId;
    private float _width;
    private float _height;

    private static Map<List<Float>, Integer> vboIdCache = new HashMap<>();

    public final float u0;
    public final float v0;
    public final float u1;
    public final float v1;

    public GLImage(int texId, int textureWidth, int textureHeight, float tx, float ty, float tw,
            float th, float x, float y,
            float width, float height) {

        this.u0 = tx / (float) textureWidth;
        this.v0 = ty / (float) textureHeight;
        this.u1 = (tx + tw) / (float) textureWidth;
        this.v1 = (ty + th) / (float) textureHeight;

        // set up VBO element
        List<Float> key = Arrays.asList(tx, ty, tw, th, x, y, width, height);
        if (vboIdCache.containsKey(key)) {
            vertexBufferId = vboIdCache.get(key);
        } else {
            float[] vertices = new float[16];

            // GL_VERTEX_ARRAY
            vertices = GLTriangle.Strip.createRectangle(x, y, width, height, vertices);

            // GL_TEXTURE_COORD_ARRAY
            float[] texVerts = GLTriangle.Strip.createRectangle(
                    tx / (float) textureWidth,
                    ty / (float) textureHeight,
                    tw / (float) textureWidth,
                    th / (float) textureHeight,
                    null);

            // merge GL_VERTEX_ARRAY and GL_TEXTURE_COORD_ARRAY for a single VBO reference
            System.arraycopy(texVerts, 0, vertices, 8, 8);

            vboIdCache.put(key, createVertexBuffer(vertices));
        }

        _textureId = texId;
        _width = width;
        _height = height;
    }

    private int createVertexBuffer(float[] vertices) {
        int[] buffers = {
                0
        };
        GLES20FixedPipeline.glGenBuffers(1, buffers, 0);
        vertexBufferId = buffers[0];

        int size = vertices.length * 4; // float
        FloatBuffer fb = com.atakmap.lang.Unsafe.allocateDirect(size).order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        fb.put(vertices);
        fb.position(0);

        GLES20FixedPipeline.glBindBuffer(GLES20FixedPipeline.GL_ARRAY_BUFFER, vertexBufferId);
        // capacity * 4 bytes per float
        GLES20FixedPipeline.glBufferData(GLES20FixedPipeline.GL_ARRAY_BUFFER, fb.capacity() * 4, fb, GLES20FixedPipeline.GL_STATIC_DRAW);
        GLES20FixedPipeline.glBindBuffer(GLES20FixedPipeline.GL_ARRAY_BUFFER, 0);

        return vertexBufferId;
    }

    public float getWidth() {
        return _width;
    }

    public float getHeight() {
        return _height;
    }

    public void draw() {
        draw(1f, 1f, 1f, 1f);
    }

    public void draw(float red, float green, float blue, float alpha) {
        this.drawImpl(true, red, green, blue, alpha);
    }

    public void drawNoColorSet() {
        this.drawImpl(false, 0, 0, 0, 0);
    }

    private void drawImpl(boolean colorSet, float red, float green, float blue, float alpha) {
        if (_textureId != 0) {
            GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_TEXTURE_COORD_ARRAY);
            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);

            GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, _textureId);
            if (colorSet)
                GLES20FixedPipeline.glColor4f(red, green, blue, alpha);

            GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0, 0);
            // offset by 8 * 4 bytes per float
            GLES20FixedPipeline.glTexCoordPointer(2, GLES20FixedPipeline.GL_FLOAT, 0, 8 * 4);

            GLES20FixedPipeline.glBindBuffer(GLES20FixedPipeline.GL_ARRAY_BUFFER, vertexBufferId);

            GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_STRIP, 0, 4);

            GLES20FixedPipeline.glBindBuffer(GLES20FixedPipeline.GL_ARRAY_BUFFER, 0);

            GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_TEXTURE_COORD_ARRAY);
            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        }
    }

    public int getTexId() {
        return _textureId;
    }

    public void setTexId(int textureId) {
        _textureId = textureId;
    }

    public int getVertTexVBOPointer() {
        return this.vertexBufferId;
    }
}
