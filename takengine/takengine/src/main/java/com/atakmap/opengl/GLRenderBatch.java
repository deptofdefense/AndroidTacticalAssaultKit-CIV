package com.atakmap.opengl;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import com.atakmap.map.opengl.GLRenderGlobals;

import android.opengl.GLES30;

/**
 * A batching implementation for efficient rendering of geometry and textures.
 * <P>
 * General usage of this class is as follows:
 * <UL>
 * <LI><code>beginBatch()</code></LI>
 * <LI><I>add geometry/texture (many times)</I></LI>
 * <LI><code>endBatch()</code></LI>
 * </UL>
 * In the best case, all content that is added will be rendered in a single draw
 * invocation when {@link #endBatch()} is invoked. A periodic flush may be
 * required between {@link #beginBatch()} and {@link #endBatch()} when content
 * is added if the capacity of the render buffer has been exhausted or if the
 * maximum number of available texture units are already in use.
 * 
 * <P>All methods should be invoked on the GL context thread unless specified
 * otherwise.
 * 
 * @author Developer
 */
public final class GLRenderBatch {

    private final static float[] SCRATCH_MATRIX = new float[16];

    private int originalTextureUnit;

    final GLRenderBatch2 impl;

    /**
     * Creates a new sprite batch.
     * 
     * @param capacity The maximum number of sprites that may be batched before a flush is required.
     *            Note that a flush may need to occur more frequently if triangle or triangle strip
     *            sprites are added to the batch.
     */
    public GLRenderBatch(int capacity) {
        this(new GLRenderBatch2(capacity*6));
    }    

    public GLRenderBatch(GLRenderBatch2 impl) {
        this.impl = impl;
        this.originalTextureUnit = 0;
    }

    /**
     * Begins a new batch. The various methods to add sprites may be invoked after this method
     * returns.
     */
    public void begin() {
        int[] i = new int[1];
        GLES20FixedPipeline.glGetIntegerv(GLES20FixedPipeline.GL_ACTIVE_TEXTURE, i, 0);
        this.originalTextureUnit = i[0];

        this.impl.begin(GLRenderBatch2.HINT_TWO_DIMENSION);
        
        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, SCRATCH_MATRIX, 0);
        this.impl.setMatrix(GLES20FixedPipeline.GL_PROJECTION, SCRATCH_MATRIX, 0);

        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, SCRATCH_MATRIX, 0);
        this.impl.setMatrix(GLES20FixedPipeline.GL_MODELVIEW, SCRATCH_MATRIX, 0);
    }

    /**
     * Ends the current batch. Any batched content will be rendered.
     */
    public void end() {
        this.impl.end();

        GLES20FixedPipeline.glActiveTexture(this.originalTextureUnit);
    }

    /**
     * Renders any currently batched content. The sprite buffer will be cleared before this method
     * returns.
     */
    protected void flush() {}

    /**************************************************************************/
    
    // Lines
    
    public void addLine(float x0, float y0, float x1, float y1, float width, float r, float g, float b, float a) {
        this.impl.batch(x0, y0,
                        x1, y1,
                        r, g, b, a);
    }
    
    public void addLines(FloatBuffer lines, float width, float r, float g, float b, float a) {
        this.impl.setLineWidth(width);
        this.impl.batch(0, GLES20FixedPipeline.GL_LINES, 2, 0, lines, 0, null, r, g, b, a);
    }
    
    public void addLineStrip(FloatBuffer linestrip, float width, float r, float g, float b, float a) {
        this.impl.setLineWidth(width);
        this.impl.batch(0, GLES20FixedPipeline.GL_LINE_STRIP, 2, 0, linestrip, 0, null, r, g, b, a);
    }

    public void addLineLoop(FloatBuffer lineLoop, float width, float r, float g, float b, float a) {
        this.impl.setLineWidth(width);
        this.impl.batch(0, GLES20FixedPipeline.GL_LINE_LOOP, 2, 0, lineLoop, 0, null, r, g, b, a);
    }
    
    // Polygons
    
    public void addTriangles(FloatBuffer vertexCoords,
                             float r, float g, float b, float a) {

        this.impl.batch(0,
                        GLES20FixedPipeline.GL_TRIANGLES,
                        2,
                        0, vertexCoords,
                        0, null,
                        r, g, b, a);
    }
    
    public void addTriangles(FloatBuffer vertexCoords,
                             ShortBuffer indices,
                             float r, float g, float b, float a) {

        this.impl.batch(0,
                        GLES20FixedPipeline.GL_TRIANGLES,
                        2,
                        0, vertexCoords,
                        0, null,
                        indices,
                        r, g, b, a);
    }

    public void addTriangleStrip(FloatBuffer vertexCoords,
                                 float r, float g, float b, float a) {

        this.impl.batch(0,
                        GLES20FixedPipeline.GL_TRIANGLE_STRIP,
                        2,
                        0, vertexCoords,
                        0, null,
                        r, g, b, a);
    }
    
    public void addTriangleStrip(FloatBuffer vertexCoords,
                                 ShortBuffer indices,
                                 float r, float g, float b, float a) {

        this.impl.batch(0,
                        GLES20FixedPipeline.GL_TRIANGLE_STRIP,
                        2,
                        0, vertexCoords,
                        0, null,
                        indices,
                        r, g, b, a);
    }

    public void addTriangleFan(FloatBuffer vertexCoords,
                               float r, float g, float b, float a) {

        this.impl.batch(0,
                        GLES20FixedPipeline.GL_TRIANGLE_FAN,
                        2,
                        0, vertexCoords,
                        0, null,
                        r, g, b, a);
    }
    
    public void addTriangleFan(FloatBuffer vertexCoords,
                               ShortBuffer indices,
                               float r, float g, float b, float a) {

        this.impl.batch(0,
                        GLES20FixedPipeline.GL_TRIANGLE_FAN,
                        2,
                        0, vertexCoords,
                        0, null,
                        r, g, b, a);
    }
    
    // Sprites / Textures
    
    /**
     * Adds the specified sprite to the batch.
     * 
     * @param textureId The sprite's texture ID
     * @param x1        The upper-left vertex x
     * @param y1        The upper-left vertex y
     * @param x2        The lower-right vertex x
     * @param y2        The lower-right vertex y
     * @param u1        The upper-left texture u
     * @param v1        The upper-left texture v
     * @param u2        The lower-right texture u
     * @param v2        The lower-right texture v
     * @param r         The red component for the color of the sprite
     * @param g         The green component for the color of the sprite
     * @param b         The blue component for the color of the sprite
     * @param a         The alpha component for the color of the sprite
     */
    public void addSprite(int textureId, float x1, float y1, float x2, float y2, float u1,
            float v1, float u2, float v2, float r, float g, float b, float a) {
        
        this.addSprite(textureId,
                       x1, y1,
                       x2, y1,
                       x2, y2,
                       x1, y2,
                       u1, v1,
                       u2, v2,
                       r, g, b, a);
    }

    /**
     * Adds the specified sprite to the batch.
     * 
     * @param textureId The sprite's texture ID
     * @param x1                The upper-left vertex x
     * @param y1                The upper-left vertex y
     * @param x2                The upper-right vertex x
     * @param y2                The upper-right vertex y
     * @param x3                The lower-right vertex x
     * @param y3                The lower-right vertex y
     * @param x4                The lower-left vertex x
     * @param y4                The lower-left vertex y
     * @param u1        The upper-left texture u
     * @param v1        The upper-left texture v
     * @param u2        The lower-right texture u
     * @param v2        The lower-right texture v
     * @param r         The red component for the color of the sprite
     * @param g         The green component for the color of the sprite
     * @param b         The blue component for the color of the sprite
     * @param a         The alpha component for the color of the sprite
     */
    public void addSprite(int textureId,
                          float x1, float y1,
                          float x2, float y2,
                          float x3, float y3,
                          float x4, float y4,
                          float u1, float v1,
                          float u2, float v2,
                          float r, float g, float b, float a) {

        this.impl.batch(textureId,
                        x1, y1,
                        x2, y2,
                        x3, y3,
                        x4, y4,
                        u1, v1,
                        u2, v1,
                        u2, v2,
                        u1, v2,
                        r, g, b, a);
    }
    
    /**
     * Adds the specified sprite to the sprite buffer. The provided vertex and
     * texture coordinates are expected to be consistent with a draw mode of
     * {@link GLES30#GL_TRIANGLES}. The coordinate buffers are required to have
     * the same number of elements remaining.
     * 
     * @param textureId     The sprite's texture ID
     * @param vertexCoords  The vertex coordinates
     * @param texCoords     The texture coordinates
     * @param r             The red component for the color of the sprite
     * @param g             The green component for the color of the sprite
     * @param b             The blue component for the color of the sprite
     * @param a             The alpha component for the color of the sprite
     */
    public void addTrianglesSprite(int textureId, FloatBuffer vertexCoords, FloatBuffer texCoords,
            float r, float g, float b, float a) {

        this.impl.batch(textureId,
                        GLES20FixedPipeline.GL_TRIANGLES,
                        2,
                        0, vertexCoords,
                        0, texCoords,
                        r, g, b, a);
    }

    public void addTrianglesSprite(int textureId,
                                   FloatBuffer vertexCoords,
                                   FloatBuffer texCoords,
                                   ShortBuffer indices,
                                   float r, float g, float b, float a) {

        this.impl.batch(textureId,
                        GLES20FixedPipeline.GL_TRIANGLES,
                        2,
                        0, vertexCoords,
                        0, texCoords,
                        indices,
                        r, g, b, a);
    }
    
    /**
     * Adds the specified sprite to the sprite buffer. The provided vertex and
     * texture coordinates are expected to be consistent with a draw mode of
     * {@link GLES30#GL_TRIANGLE_STRIP}; degenerate triangles are accepted. The
     * coordinate buffers are required to have the same number of elements
     * remaining.
     * 
     * @param textureId     The sprite's texture ID
     * @param vertexCoords  The vertex coordinates
     * @param texCoords     The texture coordinates
     * @param r             The red component for the color of the sprite
     * @param g             The green component for the color of the sprite
     * @param b             The blue component for the color of the sprite
     * @param a             The alpha component for the color of the sprite
     */
    public void addTriangleStripSprite(int textureId, FloatBuffer vertexCoords,
            FloatBuffer texCoords, float r, float g, float b, float a) {
        
        this.impl.batch(textureId,
                        GLES20FixedPipeline.GL_TRIANGLE_STRIP,
                        2,
                        0, vertexCoords,
                        0, texCoords,
                        r, g, b, a);
    }
    
    public void addTriangleStripSprite(int textureId,
                                       FloatBuffer vertexCoords,
                                       FloatBuffer texCoords,
                                       ShortBuffer indices,
                                       float r, float g, float b, float a) {

        this.impl.batch(textureId,
                        GLES20FixedPipeline.GL_TRIANGLE_STRIP,
                        2,
                        0, vertexCoords,
                        0, texCoords,
                        indices,
                        r, g, b, a);
    }

    /**
     * Adds the specified sprite to the sprite buffer. The provided vertex and
     * texture coordinates are expected to be consistent with a draw mode of
     * {@link GLES30#GL_TRIANGLE_FAN}; degenerate triangles are accepted. The
     * coordinate buffers are required to have the same number of elements
     * remaining.
     * 
     * @param textureId     The sprite's texture ID
     * @param vertexCoords  The vertex coordinates
     * @param texCoords     The texture coordinates
     * @param r             The red component for the color of the sprite
     * @param g             The green component for the color of the sprite
     * @param b             The blue component for the color of the sprite
     * @param a             The alpha component for the color of the sprite
     */
    public void addTriangleFanSprite(int textureId, FloatBuffer vertexCoords,
            FloatBuffer texCoords, float r, float g, float b, float a) {
        
        this.impl.batch(textureId,
                        GLES20FixedPipeline.GL_TRIANGLE_FAN,
                        2,
                        0, vertexCoords,
                        0, texCoords,
                        r, g, b, a);
    }
    
    public void addTriangleFanSprite(int textureId,
                                     FloatBuffer vertexCoords,
                                     FloatBuffer texCoords,
                                     ShortBuffer indices,
                                     float r, float g, float b, float a) {

        this.impl.batch(textureId,
                        GLES20FixedPipeline.GL_TRIANGLE_FAN,
                        2,
                        0, vertexCoords,
                        0, texCoords,
                        indices,
                        r, g, b, a);
    }


    /**
     * Returns the number of texture units that the implementation may use
     * during a single batch.
     * 
     * @return  The number of texture units that the implementation may use
     *          during a single batch.
     */
    public static int getBatchTextureUnitLimit() {
        if(GLRenderGlobals.isLimitTextureUnits())
            return 2;
        else
            return Math.min(GLRenderGlobals.getMaxTextureUnits(), 32);
    }

} // GLRenderBatch
