
package com.atakmap.android.maps.graphics;

import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.opengl.GLES20FixedPipeline;

import gov.tak.api.engine.Shader;
import gov.tak.api.engine.map.RenderContext;
import gov.tak.platform.commons.opengl.GLES30;

public class GLImage {

    private int vertexBufferId;

    private int _textureId;
    private float _width;
    private float _height;

    private static Map<List<Float>, Map<RenderContext, Integer>> vboIdCache = new HashMap<>();

    public final float u0;
    public final float v0;
    public final float u1;
    public final float v1;

    public GLImage(int texId, int textureWidth, int textureHeight, float tx, float ty, float tw,
                   float th, float x, float y,
                   float width, float height) {
        this(null, texId, textureWidth, textureHeight, tx, ty, tw, th, x, y, width, height);
    }

    public GLImage(RenderContext ctx, int texId, int textureWidth, int textureHeight, float tx, float ty, float tw,
                   float th, float x, float y,
                   float width, float height) {

        this.u0 = tx / (float) textureWidth;
        this.v0 = ty / (float) textureHeight;
        this.u1 = (tx + tw) / (float) textureWidth;
        this.v1 = (ty + th) / (float) textureHeight;

        // set up VBO element
        List<Float> key = Arrays.asList(tx, ty, tw, th, x, y, width, height);
        Map<RenderContext, Integer> vboEntry = vboIdCache.get(key);
        if(vboEntry == null)
            vboIdCache.put(key, vboEntry=new HashMap<>());
        if (vboEntry.containsKey(ctx)) {
            vertexBufferId = vboEntry.get(ctx);
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

            vboEntry.put(ctx, createVertexBuffer(vertices));
        }

        _textureId = texId;
        _width = width;
        _height = height;
    }
    public void draw(Shader shader){draw(shader, 1f, 1f, 1f, 1f);}

    public void draw(Shader shader, float red, float green, float blue, float alpha) {
        if (_textureId != 0) {
            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBufferId);

            GLES30.glUniform1i (shader.getUTexture(), 0);
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, _textureId);

            GLES30.glUniform4f(shader.getUColor(), red, green, blue, alpha);

            GLES30.glEnableVertexAttribArray(shader.getAVertexCoords());
            GLES30.glEnableVertexAttribArray(shader.getATextureCoords());
            GLES30.glVertexAttribPointer(shader.getAVertexCoords(), 2, GLES30.GL_FLOAT, false, 0, 0);
            GLES30.glVertexAttribPointer(shader.getATextureCoords(), 2, GLES30.GL_FLOAT, false, 0, 4*4*2);

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
            GLES30.glDisableVertexAttribArray(shader.getATextureCoords());
            GLES30.glDisableVertexAttribArray(shader.getAVertexCoords());

            GLES30.glDisable(GLES30.GL_BLEND);
        }
    }


    private int createVertexBuffer(float[] vertices) {
        int[] buffers = {
                0
        };
        GLES30.glGenBuffers(1, buffers, 0);
        vertexBufferId = buffers[0];

        int size = vertices.length * 4; // float
        FloatBuffer fb = com.atakmap.lang.Unsafe.allocateDirect(size).order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        fb.put(vertices);
        fb.position(0);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBufferId);
        // capacity * 4 bytes per float
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, fb.capacity() * 4, fb, GLES30.GL_STATIC_DRAW);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);

        return vertexBufferId;
    }

    public float getWidth() {
        return _width;
    }

    public float getHeight() {
        return _height;
    }

    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void draw() {
        draw(1f, 1f, 1f, 1f);
    }

    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void draw(float red, float green, float blue, float alpha) {
        this.drawImpl(true, red, green, blue, alpha);
    }

    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    public void drawNoColorSet() {
        this.drawImpl(false, 0, 0, 0, 0);
    }

    @Deprecated
    @DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
    private void drawImpl(boolean colorSet, float red, float green, float blue, float alpha) {
        if (_textureId != 0) {
            Shader shader = Shader.create(Shader.FLAG_TEXTURED | Shader.FLAG_2D);
            int prevProgram = shader.useProgram(true);
            GLES20FixedPipeline.useFixedPipelineMatrices(shader);
            if(colorSet)
                draw(shader, red, green, blue, alpha);
            else
                draw(shader, 1f, 1f, 1f, 1f);
            GLES30.glUseProgram(prevProgram);
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
