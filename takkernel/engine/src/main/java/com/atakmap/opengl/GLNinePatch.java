
package com.atakmap.opengl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import gov.tak.api.engine.Shader;

import com.atakmap.map.opengl.GLMapView;

// XXX - 
import com.atakmap.android.maps.graphics.GLImageCache;

import gov.tak.platform.commons.opengl.GLES30;

public class GLNinePatch {

    private final static int NUM_VERTS_PER_CORNER = 8;
    private final static int NUM_VERTS_PER_PATCH = (NUM_VERTS_PER_CORNER * 4) + 1;
    private final static double RADIANS_PER_VERT = (Math.PI / 2) / NUM_VERTS_PER_CORNER;

    private final static ShortBuffer TEX_INDICES;
    static {
        ByteBuffer buf = com.atakmap.lang.Unsafe.allocateDirect(18*3 * 2);
        buf.order(ByteOrder.nativeOrder());
        TEX_INDICES = buf.asShortBuffer();
        
        final short[] indices = new short[] { 4,  0,  5,   0,  5,  1,
                                              5,  1,  6,   1,  6,  2,
                                              6,  2,  7,   2,  7,  3,
                                              8,  4,  9,   4,  9,  5,
                                              9,  5, 10,   5, 10,  6,
                                             10,  6, 11,   6, 11,  7,
                                             12,  8, 13,   8, 13,  9,
                                             13,  9, 14,   9, 14, 10,
                                             14, 10, 15,  10, 15, 11, };
        
        TEX_INDICES.put(indices);
        TEX_INDICES.flip();
    }

    private FloatBuffer triangleVerts;

    private float lastTriangleX;
    private float lastTriangleY;
    private float lastTriangleZ;
    private float lastTriangleWidth;
    private float lastTriangleHeight;

    private float lastTextureX;
    private float lastTextureY;
    private float lastTextureZ;
    private float lastTextureWidth;
    private float lastTextureHeight;

    protected float radius;

    private GLImageCache.Entry texEntry;
    private int textureId;

    private float textureDataWidth;
    private float textureDataHeight;

    private float x0;
    private float y0;
    private float x1;
    private float y1;

    private FloatBuffer texVerts;
    private FloatBuffer texCoords;
    

    public GLNinePatch(float width, float height) {
        this(null, width, height, 0, 0, 0, 0);
    }

    public GLNinePatch(GLImageCache.Entry texEntry, float width, float height, float x0, float y0,
            float x1, float y1) {
        this.texEntry = texEntry;

        this.textureDataWidth = width;
        this.textureDataHeight = height;

        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;

        ByteBuffer bb;
        
        bb = com.atakmap.lang.Unsafe.allocateDirect(NUM_VERTS_PER_PATCH * 3 * 4);
        bb.order(ByteOrder.nativeOrder());
        this.triangleVerts = bb.asFloatBuffer();

        this.lastTriangleX = -1;
        this.lastTriangleY = -1;
        this.lastTriangleZ = -1;
        this.lastTriangleWidth = -1;
        this.lastTriangleHeight = -1;

        this.lastTextureX = -1;
        this.lastTextureY = -1;
        this.lastTextureZ = -1;
        this.lastTextureWidth = -1;
        this.lastTextureHeight = -1;

        final float hWidth = (width - 4) / 2.0f;
        final float hHeight = (height - 4) / 2.0f;
        this.radius = (float) Math.sqrt(hWidth * hWidth + hHeight * hHeight);
    }

    private void validateTextureEntry() {
        this.textureId = this.texEntry.getTextureId();
        if (this.textureId != 0 || this.texEntry.isInvalid()) {
            if (this.textureId != 0)
                this.initTexBuffers();
            this.texEntry = null;
        }
    }

    /**
     * Adds the specified 9-patch to the batch. This method will throw an
     * {@link UnsupportedOperationException} if {@link #isBatchable(GLMapView)} returns
     * <code>false</code>.
     * 
     * @param batch The batch
     * @param x The x location of the patch
     * @param y The y location of the patch
     * @param width The width of the patch
     * @param height The height of the patch
     * @param r The red component of the color of the patch
     * @param g The green component of the color of the patch
     * @param b The blue component of the color of the patch
     * @param a The alpha component of the color of the patch
     */
    public void batch(GLRenderBatch batch, float x, float y, float width, float height, float r,
            float g, float b, float a) {
        this.batch(batch.impl,
                   x, y, 0.0f,
                   width, height,
                   r, g, b, a);
    }

    public void draw(Shader shader, float width, float height) {
        draw(shader, width, height, false);
    }

    public void draw(Shader shader, float width, float height, boolean textured) {
        if (textured && this.texEntry != null)
            this.validateTextureEntry();

        if(!textured)
            drawTriangleFan(shader, 0,0,0, width, height);
        else
            this.drawTexture(shader, 0, 0, 0, width, height);
    }
    private void drawTexture(Shader shader, float x, float y, float z, float width, float height) {
        if (this.lastTextureX != x ||
                this.lastTextureY != y ||
                this.lastTextureZ != z ||
                this.lastTextureWidth != width ||
                this.lastTextureHeight != height) {

            final float scalingWidth = width - (this.x0 + (this.textureDataWidth - this.x1));
            final float scalingHeight = height - (this.y0 + (this.textureDataHeight - this.y1));

            if(z == 0.0f) {
                fillCoordsBuffer(x + 0.0f, y + 0.0f,
                        x + this.x0, y + this.y0,
                        x + this.x0 + scalingWidth, y + this.y0 + scalingHeight,
                        x + width, y + height,
                        this.texVerts);
                this.texVerts.limit(16 * 2);
            } else {
                fillCoordsBuffer3d(x + 0.0f, y + 0.0f,
                        x + this.x0, y + this.y0,
                        x + this.x0 + scalingWidth, y + this.y0 + scalingHeight,
                        x + width, y + height,
                        z,
                        this.texVerts);
                this.texVerts.limit(16 * 3);
            }

            this.lastTextureX = x;
            this.lastTextureY = y;
            this.lastTextureZ = z;
            this.lastTextureWidth = width;
            this.lastTextureHeight = height;
        }

        // enable all the state
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, this.textureId);

        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,
                GLES30.GL_ONE_MINUS_SRC_ALPHA);

        GLES30.glEnableVertexAttribArray(shader.getAVertexCoords());
        GLES30.glVertexAttribPointer(shader.getAVertexCoords(), (z == 0.0f) ? 2 : 3, GLES30.GL_FLOAT, false, 0, texVerts);

        GLES30.glEnableVertexAttribArray(shader.getAVertexCoords());
        GLES30.glVertexAttribPointer(shader.getAVertexCoords(), 2, GLES30.GL_FLOAT, false, 0, texCoords);

        GLES30.glDrawElements(GLES30.GL_TRIANGLES, 54, GLES30.GL_UNSIGNED_SHORT, TEX_INDICES);

        GLES30.glDisable(GLES30.GL_BLEND);
    }
    public void batch(GLRenderBatch2 batch, float x, float y, float z, float width, float height, float r,
            float g, float b, float a) {
        if (this.textureId == 0 && this.texEntry != null)
            this.validateTextureEntry();
        if(this.textureId != 0)
            this.batchTex(batch, x, y, z, width, height, r, g, b, a);
        else
            this.batchFan(batch, x, y, z, width, height, r, g, b, a);
    }
    
    private void batchTex(GLRenderBatch2 batch, float x, float y, float z, float width, float height, float r,
            float g, float b, float a) {
        if (this.textureId == 0)
            throw new UnsupportedOperationException();

        if (this.lastTextureX != x ||
                this.lastTextureY != y ||
                this.lastTextureZ != z ||
                this.lastTextureWidth != width ||
                this.lastTextureHeight != height) {

            final float scalingWidth = width - (this.x0 + (this.textureDataWidth - this.x1));
            final float scalingHeight = height - (this.y0 + (this.textureDataHeight - this.y1));

            if(z == 0.0f) {
                fillCoordsBuffer(x + 0.0f, y + 0.0f,
                                 x + this.x0, y + this.y0,
                                 x + this.x0 + scalingWidth, y + this.y0 + scalingHeight,
                                 x + width, y + height,
                                 this.texVerts);
                this.texVerts.limit(16*2);

            } else {
                fillCoordsBuffer3d(x + 0.0f, y + 0.0f,
                        x + this.x0, y + this.y0,
                        x + this.x0 + scalingWidth, y + this.y0 + scalingHeight,
                        x + width, y + height,
                        z,
                        this.texVerts);
                this.texVerts.limit(16*3);
            }

            this.lastTextureX = x;
            this.lastTextureY = y;
            this.lastTextureZ = z;
            this.lastTextureWidth = width;
            this.lastTextureHeight = height;
        }

        batch.batch(this.textureId,
                    GLES20FixedPipeline.GL_TRIANGLES,
                    (z==0.0f) ? 2 : 3,
                    0, this.texVerts,
                    0, this.texCoords,
                    TEX_INDICES,
                    r, g, b, a);
    }
    
    private void batchFan(GLRenderBatch2 batch, float x, float y, float z, float width, float height, float r,
            float g, float b, float a) {
        
        if (this.lastTriangleX != x ||
                this.lastTriangleY != y ||
                this.lastTriangleZ != z ||
                this.lastTriangleWidth != width ||
                this.lastTriangleHeight != height) {

            if(z == 0f) {
                buildPatchVerts(this.triangleVerts, NUM_VERTS_PER_CORNER, this.radius,
                        RADIANS_PER_VERT, x, y, width, height);
                this.triangleVerts.limit(2 * NUM_VERTS_PER_PATCH);
            } else {
                buildPatchVerts3d(this.triangleVerts, NUM_VERTS_PER_CORNER, this.radius,
                        RADIANS_PER_VERT, x, y, z, width, height);
                this.triangleVerts.limit(3 * NUM_VERTS_PER_PATCH);
            }
            this.lastTriangleX = x;
            this.lastTriangleY = y;
            this.lastTriangleZ = z;
            this.lastTriangleWidth = width;
            this.lastTriangleHeight = height;
        }

        batch.batch(-1,
                    GLES20FixedPipeline.GL_TRIANGLE_FAN,
                    (z == 0f) ? 2 : 3,
                    0, this.triangleVerts,
                    0, null,
                    r, g, b, a);
    }

    /**
     * Renders a 9-patch of the specified width and height. This method will use the triangle fan
     * implementation.
     * 
     * @param width The width of the patch
     * @param height The height of the patch
     */
    public void draw(float width, float height) {
        this.draw(0, 0, 0, width, height, false);
    }

    /**
     * Renders a 9-patch of the specified width and height. If the <code>textured</code> argument is
     * <code>true</code>, the patch will be rendered using a texture (if available). Rendering the
     * patch as a texture is generally slower.
     * 
     * @param x The x location of the patch
     * @param y The y location of the patch
     * @param width The width of the patch
     * @param height The height of the patch
     * @param textured A hint indicating whether or not the texture implementation should be used.
     */
    public void draw(float x, float y, float width, float height, boolean textured) {
        this.draw(x, y, 0f, width, height, textured);
    }

    public void draw(float x, float y, float z, float width, float height, boolean textured) {
        if (textured && this.texEntry != null)
            this.validateTextureEntry();

        Shader shader;
        int shaderFlags = 0;
        if(textured && this.textureId != 0)
            shaderFlags |= Shader.FLAG_TEXTURED;
        if(z == 0)
            shaderFlags |= Shader.FLAG_2D;
        shader = Shader.create(shaderFlags);
        int prevProgram = shader.useProgram(true);
        GLES20FixedPipeline.useFixedPipelineMatrices(shader);
        GLES20FixedPipeline.useFixedPipelineColor(shader);
        if (textured && this.textureId != 0) {
            this.drawTexture(shader, x, y, z, width, height);
        } else
            this.drawTriangleFan(shader, x, y, z, width, height);
        GLES30.glUseProgram(prevProgram);
    }

    private void drawTriangleFan(Shader shader, float x, float y, float z, float width, float height) {
        if (this.lastTriangleX != x ||
                this.lastTriangleY != y ||
                this.lastTriangleZ != z ||
                this.lastTriangleWidth != width ||
                this.lastTriangleHeight != height) {

            if(z == 0f) {
                buildPatchVerts(this.triangleVerts, NUM_VERTS_PER_CORNER, this.radius,
                        RADIANS_PER_VERT, x, y, width, height);
                this.triangleVerts.limit(2 * NUM_VERTS_PER_PATCH);
            } else {
                buildPatchVerts3d(this.triangleVerts, NUM_VERTS_PER_CORNER, this.radius,
                        RADIANS_PER_VERT, x, y, z, width, height);
                this.triangleVerts.limit(3 * NUM_VERTS_PER_PATCH);
            }
            this.lastTriangleX = x;
            this.lastTriangleY = y;
            this.lastTriangleZ = z;
            this.lastTriangleWidth = width;
            this.lastTriangleHeight = height;
        }

        // enable all the state
        GLES30.glEnable(GLES30.GL_BLEND);

        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,
                GLES30.GL_ONE_MINUS_SRC_ALPHA);

        GLES30.glEnableVertexAttribArray(shader.getAVertexCoords());
        GLES30.glVertexAttribPointer(shader.getAVertexCoords(), (z == 0) ? 2 : 3, GLES30.GL_FLOAT,
                false, 0, triangleVerts);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_FAN, 0,
                NUM_VERTS_PER_PATCH);

        GLES30.glDisableVertexAttribArray(shader.getAVertexCoords());
        GLES30.glDisable(GLES30.GL_BLEND);
    }

    private void initTexBuffers() {
        ByteBuffer buf;

        buf = com.atakmap.lang.Unsafe.allocateDirect(16 * 2 * 4);
        buf.order(ByteOrder.nativeOrder());
        this.texCoords = buf.asFloatBuffer();

        final float texX = this.texEntry.getImageTextureX();
        final float texY = this.texEntry.getImageTextureX();

        fillCoordsBuffer(texX / this.texEntry.getTextureWidth(),
                         texY / this.texEntry.getTextureHeight(),
                         (texX + this.x0) / this.texEntry.getTextureWidth(),
                         (texY + this.y0) / this.texEntry.getTextureHeight(),
                         (texX + this.x1) / this.texEntry.getTextureWidth(),
                         (texY + this.y1) / this.texEntry.getTextureHeight(),
                         (texX + this.textureDataWidth) / this.texEntry.getTextureWidth(),
                         (texY + this.textureDataHeight) / this.texEntry.getTextureHeight(),
                         this.texCoords);

        buf = com.atakmap.lang.Unsafe.allocateDirect(16 * 3 * 4);
        buf.order(ByteOrder.nativeOrder());
        this.texVerts = buf.asFloatBuffer();
    }

    private static native void buildPatchVerts(FloatBuffer verts, int numVertsPerCorner,
            float radius, double radiansPerVert, float x, float y, float width, float height);
    
    private static native void buildPatchVerts3d(FloatBuffer verts, int numVertsPerCorner,
            float radius, double radiansPerVert, float x, float y, float z, float width, float height);

    private static native void fillCoordsBuffer(float texPatchCol0, float texPatchRow0,
            float texPatchCol1, float texPatchRow1,
            float texPatchCol2, float texPatchRow2,
            float texPatchCol3, float texPatchRow3,
            FloatBuffer buffer);
    
    private static native void fillCoordsBuffer3d(float texPatchCol0, float texPatchRow0,
            float texPatchCol1, float texPatchRow1,
            float texPatchCol2, float texPatchRow2,
            float texPatchCol3, float texPatchRow3,
            float z,
            FloatBuffer buffer);
}
