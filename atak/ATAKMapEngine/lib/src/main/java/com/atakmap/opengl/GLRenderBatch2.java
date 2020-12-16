package com.atakmap.opengl;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public final class GLRenderBatch2 {

    public final static int HINT_UNTEXTURED             = 0x00000001;
    public final static int HINT_SOFTWARE_TRANSFORMS    = 0x00000002;
    public final static int HINT_LINES                  = 0x00000004;
    public final static int HINT_TWO_DIMENSION          = 0x00000008;
            
    long pointer;
    
    public GLRenderBatch2() {
        this(0xFFFF);
    }

    public GLRenderBatch2(int limit) {
        this.pointer = create(limit);
    }

    private void checkValidNoSync() {
        if(this.pointer == 0L)
            throw new IllegalStateException();
    }

    public synchronized void dispose() {
        if(this.pointer != 0L) {
            destroy(this.pointer);
            this.pointer = 0L;
        }
    }
    
    /**
     * Begins a new batching operation, without any hints specified. Content
     * that is passed to the overloaded <code>batch(...)</code> method will be
     * stored in a large, single buffer and drawn in a single GLES call when
     * the {@link #end()} method is invoked.
     * 
     * <P>Note that a flush <I>may</I> occur before {@link #end()} is invoked
     * in the case that the buffer is filled.
     */
    public void begin() {
        this.begin(0);
    }
    
    /**
     * Begins a new batching operation, using the specified hints to potentially
     * improve performance. Content that is passed to the overloaded
     * <code>batch(...)</code> method will be stored in a large, single buffer
     * and drawn in a single GLES call when the {@link #end()} method is
     * invoked.
     * 
     * <P>Note that a flush <I>may</I> occur before {@link #end()} is invoked
     * in the case that the buffer is filled.
     * 
     * @param hints A bitwise-OR of the following hint flags:
     *              <UL>
     *                  <LI>{@link #HINT_HARDWARE_TRANSFORMS}</LI>
     *                  <LI>{@link #HINT_LINES}</LI>
     *                  <LI>{@link #HINT_UNTEXTURED}</LI>
     *              </UL>
     */
    public synchronized void begin(int hints) {
        this.checkValidNoSync();
        begin(this.pointer, hints);
    }

    /**
     * Signals that the current batching operation is completed and batched
     * content should be drawn.
     */
    public synchronized void end() {
        this.checkValidNoSync();
        end(this.pointer);
    }

    public synchronized void release() {
        this.checkValidNoSync();
        release(this.pointer);
    }

    /**************************************************************************/
    // transform operations
    
    /**
     * Sets the matrix for the specified mode
     * 
     * @param mode      One of:
     *                  <UL>
     *                      <LI>{@link android.opengl.GLES10#GL_PROJECTION GLES10.GL_PROJECTION}</LI>
     *                      <LI>{@link android.opengl.GLES10#GL_MODELVIEW GLES10.GL_MODELVIEW}</LI>
     *                      <LI>{@link android.opengl.GLES10#GL_TEXTURE GLES10.GL_TEXTURE}</LI>
     *                  </UL> 
     * @param mx        The 4x4 matrix, column-major order
     * @param offset    The offset into <code>mx</code> of the first element of
     *                  the matrix
     */
    public synchronized void setMatrix(int mode, float[] mx, int offset) {
        this.checkValidNoSync();
        setMatrix(this.pointer, mode, mx, offset);
    }
    
    /**
     * Pushes the specified matrix stack. The current matrix is saved and
     * copied.  The copy may be modified and subsequently returned to its
     * previous state by invoking {@link #popMatrix(int)}. 
     * 
     * @param mode      One of:
     *                  <UL>
     *                      <LI>{@link android.opengl.GLES10#GL_PROJECTION GLES10.GL_PROJECTION}</LI>
     *                      <LI>{@link android.opengl.GLES10#GL_MODELVIEW GLES10.GL_MODELVIEW}</LI>
     *                      <LI>{@link android.opengl.GLES10#GL_TEXTURE GLES10.GL_TEXTURE}</LI>
     *                  </UL>
     */
    public synchronized void pushMatrix(int mode) {
        this.checkValidNoSync();
        pushMatrix(this.pointer, mode);
    }
    
    /**
     * Pops the specified matrix stack. The current matrix is reset to the value
     * of the matrix at the time of the last invocation of
     * {@link #pushMatrix(int)}.
     * 
     * @param mode      One of:
     *                  <UL>
     *                      <LI>{@link android.opengl.GLES10#GL_PROJECTION GLES10.GL_PROJECTION}</LI>
     *                      <LI>{@link android.opengl.GLES10#GL_MODELVIEW GLES10.GL_MODELVIEW}</LI>
     *                      <LI>{@link android.opengl.GLES10#GL_TEXTURE GLES10.GL_TEXTURE}</LI>
     *                  </UL> 
     */
    public synchronized void popMatrix(int mode) {
        this.checkValidNoSync();
        popMatrix(this.pointer, mode);
    }

    /**************************************************************************/

    public synchronized void setLineWidth(float width) {
        this.checkValidNoSync();
        if (Float.compare(width, 0.0f) == 0) { 
           return;
        }

        setLineWidth(this.pointer, width);
    }
    
    /**************************************************************************/

    /**
     * Adds the specified content to the current batch buffer.
     * 
     * @param texId     The texture ID, ignored if <code>texCoords</code> is
     *                  <code>null</code>
     * @param mode      The drawing mode for the vertices. Should be one of
     *                  <UL>
     *                      <LI>{@link android.opengl.GLES30#GL_LINES GLES30.GL_LINES}</LI>
     *                      <LI>{@link android.opengl.GLES30#GL_LINE_STRIP GLES30.GL_LINE_STRIP}</LI>
     *                      <LI>{@link android.opengl.GLES30#GL_LINE_LOOP GLES30.GL_LINE_LOOP}</LI>
     *                      <LI>{@link android.opengl.GLES30#GL_TRIANGLES GLES30.GL_TRIANGLES}</LI>
     *                      <LI>{@link android.opengl.GLES30#GL_TRIANGLE_STRIP GLES30.GL_TRIANGLE_STRIP}</LI>
     *                      <LI>{@link android.opengl.GLES30#GL_TRIANGLE_FAN GLES30.GL_TRIANGLE_FAN}</LI>
     *                  </UL>
     * @param size      The size of each vertex (number of components)
     * @param vStride   The stride of each vertex, in bytes. If <code>0</code>
     *                  the stride will be interpreted as the number of bytes
     *                  per component times the number of components
     * @param vertices  The buffer containing the vertices
     * @param tcStride  The stride of each texture coordinate, in bytes. If
     *                  <code>0</code>, the stride will be interpreted as the
     *                  number of bytes per component times <code>2</code>.
     *                  Ignored if <code>texCoords</code> is
     *                  <code>null</code>.
     * @param texCoords The buffer containing the texture coordinates, or
     *                  <code>null</code> if the geometry should not be textured
     * @param r         Red color component, <code>0f</code> to <code>1f</code>
     * @param g         Green color component, <code>0f</code> to <code>1f</code>
     * @param b         Blue color component, <code>0f</code> to <code>1f</code>
     * @param a         Alpha color component, <code>0f</code> to <code>1f</code>
     */
    public synchronized void batch(int texId, int mode, int size, int vStride, FloatBuffer vertices, int tcStride, FloatBuffer texCoords, float r, float g, float b, float a) {
        if(texCoords != null && (texCoords.remaining()/2) != (vertices.remaining()/size))
            throw new IllegalArgumentException();
        
        batch(this.pointer,
              texId,
              mode,
              vertices.remaining() / size,
              size,
              vStride, vertices, vertices.position(),
              tcStride, texCoords, (texCoords != null) ? texCoords.position() : 0,
              r, g, b, a);
    }
    
    /**
     * Adds the specified indexed content to the current batch buffer.
     * 
     * @param texId     The texture ID, ignored if <code>texCoords</code> is
     *                  <code>null</code>
     * @param mode      The drawing mode for the vertices. Should be one of
     *                  <UL>
     *                      <LI>{@link android.opengl.GLES30#GL_LINES GLES30.GL_LINES}</LI>
     *                      <LI>{@link android.opengl.GLES30#GL_LINE_STRIP GLES30.GL_LINE_STRIP}</LI>
     *                      <LI>{@link android.opengl.GLES30#GL_LINE_LOOP GLES30.GL_LINE_LOOP}</LI>
     *                      <LI>{@link android.opengl.GLES30#GL_TRIANGLES GLES30.GL_TRIANGLES}</LI>
     *                      <LI>{@link android.opengl.GLES30#GL_TRIANGLE_STRIP GLES30.GL_TRIANGLE_STRIP}</LI>
     *                      <LI>{@link android.opengl.GLES30#GL_TRIANGLE_FAN GLES30.GL_TRIANGLE_FAN}</LI>
     *                  </UL>
     * @param size      The size of each vertex (number of components)
     * @param vStride   The stride of each vertex, in bytes. If <code>0</code>
     *                  the stride will be interpreted as the number of bytes
     *                  per component times the number of components
     * @param vertices  The buffer containing the vertices
     * @param tcStride  The stride of each texture coordinate, in bytes. If
     *                  <code>0</code>, the stride will be interpreted as the
     *                  number of bytes per component times <code>2</code>.
     *                  Ignored if <code>texCoords</code> is
     *                  <code>null</code>.
     * @param texCoords The buffer containing the texture coordinates, or
     *                  <code>null</code> if the geometry should not be textured
     * @param indices   The indices
     * @param r         Red color component, <code>0f</code> to <code>1f</code>
     * @param g         Green color component, <code>0f</code> to <code>1f</code>
     * @param b         Blue color component, <code>0f</code> to <code>1f</code>
     * @param a         Alpha color component, <code>0f</code> to <code>1f</code>
     */
    public synchronized void batch(int texId, int mode, int size, int vStride, FloatBuffer vertices, int tcStride, FloatBuffer texCoords, ShortBuffer indices, float r, float g, float b, float a) {
        if(texCoords != null && (texCoords.remaining()/2) != (vertices.remaining()/size))
            throw new IllegalArgumentException();

        batch(this.pointer,
              texId,
              mode,
              vertices.remaining() / size,
              size,
              vStride, vertices, vertices.position(),
              tcStride, texCoords, (texCoords != null) ? texCoords.position() : 0,
              indices.remaining(), indices, indices.position(),
              r, g, b, a);
    }

    public synchronized void batch(float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        batch(this.pointer,
              x0, y0,
              x1, y1,
              r, g, b, a);
    }
    
    public synchronized void batch(float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b, float a) {
        batch(this.pointer,
              x0, y0, z0,
              x1, y1, z1,
              r, g, b, a);
    }
    
    public synchronized void batch(int texId,
                                   float x0, float y0,
                                   float x1, float y1,
                                   float x2, float y2,
                                   float x3, float y3,
                                   float u0, float v0,
                                   float u1, float v1,
                                   float u2, float v2,
                                   float u3, float v3,
                                   float r, float g, float b, float a) {
    
        this.checkValidNoSync();
        batch(this.pointer,
              texId,
              x0, y0,
              x1, y1,
              x2, y2,
              x3, y3,
              u0, v0,
              u1, v1,
              u2, v2,
              u3, v3,
              r, g, b, a);
    }
    
    public synchronized void batch(int texId,
                                   float x0, float y0, float z0,
                                   float x1, float y1, float z1,
                                   float x2, float y2, float z2,
                                   float x3, float y3, float z3,
                                   float u0, float v0,
                                   float u1, float v1,
                                   float u2, float v2,
                                   float u3, float v3,
                                   float r, float g, float b, float a) {

        this.checkValidNoSync();
        batch(this.pointer,
              texId,
              x0, y0, z0,
              x1, y1, z1,
              x2, y2, z2,
              x3, y3, z3,
              u0, v0,
              u1, v1,
              u2, v2,
              u3, v3,
              r, g, b, a);
    }

    /**************************************************************************/
    
    @Override
    protected void finalize() throws Throwable {
        this.dispose();

        super.finalize();
    }
    /**************************************************************************/
    
    public static native void setMaxTextureUnits(int texUnits);

    private static native long create(int limit);
    private static native void destroy(long pointer);
    private static native void begin(long pointer, int hints);
    private static native void end(long pointer);
    private static native void release(long pointer);
    private static native void setMatrix(long pointer, int mode, float[] mx, int offset);
    private static native void pushMatrix(long pointer, int mode);
    private static native void popMatrix(long pointer, int mode);
    private static native void setLineWidth(long pointer, float width);
    private static native void batch(long pointer, int texId, int mode, int count, int size, int vStride, FloatBuffer vertices, int verticesOff, int tcStride, FloatBuffer texCoords, int texCoordsOff, float r, float g, float b, float a);
    private static native void batch(long pointer, int texId, int mode, int count, int size, int vStride, FloatBuffer vertices, int verticesOff, int tcStride, FloatBuffer texCoords, int texCoordsOff, int indexCount, ShortBuffer indices, int indexOff, float r, float g, float b, float a);
    private static native void batch(long pointer,
                                     float x0, float y0,
                                     float x1, float y1,
                                     float r, float g, float b, float a);
    private static native void batch(long pointer,
                                     float x0, float y0, float z0,
                                     float x1, float y1, float z1,
                                     float r, float g, float b, float a);
    private static native void batch(long pointer,
                                     int texId,
                                     float x0, float y0,
                                     float x1, float y1,
                                     float x2, float y2,
                                     float x3, float y3,
                                     float u0, float v0,
                                     float u1, float v1,
                                     float u2, float v2,
                                     float u3, float v3,
                                     float r, float g, float b, float a);
    private static native void batch(long pointer,
                                     int texId,
                                     float x0, float y0, float z0,
                                     float x1, float y1, float z1,
                                     float x2, float y2, float z2,
                                     float x3, float y3, float z3,
                                     float u0, float v0,
                                     float u1, float v1,
                                     float u2, float v2,
                                     float u3, float v3,
                                     float r, float g, float b, float a);
}
