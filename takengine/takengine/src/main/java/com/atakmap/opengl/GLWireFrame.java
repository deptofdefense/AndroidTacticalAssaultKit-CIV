package com.atakmap.opengl;

import android.opengl.GLES30;

import com.atakmap.lang.Unsafe;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public final class GLWireFrame {

    private static ByteBuffer allocate(int size) {
        ByteBuffer retval = Unsafe.allocateDirect(size);
        retval.order(ByteOrder.nativeOrder());
        return retval;
    }

    private static ShortBuffer allocateShorts(int count) {
        ByteBuffer buf = allocate(count*2);
        return buf.asShortBuffer();
    }

    private static IntBuffer allocateInts(int count) {
        ByteBuffer buf = allocate(count*4);
        return buf.asIntBuffer();
    }

    private static FloatBuffer allocateFloats(int count) {
        ByteBuffer buf = allocate(count*4);
        return buf.asFloatBuffer();
    }

    /**
     * Derives the indices appropriate for <code>GL_LINES</code> based drawing
     * of the some triangle based vertices, given the mode and count.
     *
     * @param mode  The draw mode for the vertices. One of:
     *              <code>GL_TRIANGLES</code>, <code>GL_TRIANGLE_FAN</code> or
     *              <code>GL_TRIANGLE_STRIP</code>
     * @param count The number of vertices
     *
     * @return  The indices that can be used to draw a wire frame of the triangle. The
     *          <I>position</I> will be set to the first index element and the
     *          <I>limit</I> set to the number of elements to be drawn.
     */
    public static Buffer deriveIndices(int mode, int count, int indexType) {
        if(indexType != GLES30.GL_UNSIGNED_SHORT)
            throw new UnsupportedOperationException();

        ShortBuffer retval;
        switch(mode) {
            case GLES20FixedPipeline.GL_TRIANGLE_FAN :
                retval = allocateShorts(6*(count-2)); // 6 vertices per triangle
                for(int i = 2; i < count; i++) {
                    final short a = (short)0;
                    final short b = (short)(i-1);
                    final short c = (short)i;
                    retval.put(a);
                    retval.put(b);
                    retval.put(b);
                    retval.put(c);
                    retval.put(c);
                    retval.put(a);
                }
                break;
            case GLES20FixedPipeline.GL_TRIANGLES :
                retval = allocateShorts(6*(count/3)); // 6 vertices per triangle
                for(int i = 0; i < (count/3); i++) {
                    final short a = (short)(i*3);
                    final short b = (short)(i*3 + 1);
                    final short c = (short)(i*3 + 2);
                    retval.put(a);
                    retval.put(b);
                    retval.put(b);
                    retval.put(c);
                    retval.put(c);
                    retval.put(a);
                }
                break;
            case GLES20FixedPipeline.GL_TRIANGLE_STRIP :
                retval = allocateShorts(6*(count-2)); // 6 vertices per triangle
                for(int i = 2; i < count; i++) {
                    final short a = (short)(i-2);
                    final short b = (short)(i-1);
                    final short c = (short)i;
                    retval.put(a);
                    retval.put(b);
                    retval.put(b);
                    retval.put(c);
                    retval.put(c);
                    retval.put(a);
                }
                break;
            default :
                throw new IllegalArgumentException();
        }
        retval.flip();
        return retval;
    }

    /**
     * Derives the indices appropriate for <code>GL_LINES</code> based drawing
     * of the some triangle based vertices, given the mode and count.
     *
     * @param srcIndices    The source indices
     * @param mode          The draw mode for the vertex data. One of:
     *                      <code>GL_TRIANGLES</code>,
     *                      <code>GL_TRIANGLE_FAN</code> or
     *                      <code>GL_TRIANGLE_STRIP</code>
     * @param count         The number of elements
     *
     * @return  The indices that can be used to draw a wire frame of the triangle. The
     *          <I>position</I> will be set to the first index element and the
     *          <I>limit</I> set to the number of elements to be drawn.
     */
    public static Buffer deriveIndices(ShortBuffer srcIndices, int mode, int count, int dstIndexType) {
        if(dstIndexType != GLES30.GL_UNSIGNED_SHORT)
            throw new UnsupportedOperationException();
        ShortBuffer retval;
        final int basePos = srcIndices.position();
        switch(mode) {
            case GLES20FixedPipeline.GL_TRIANGLE_FAN :
                retval = allocateShorts(6*(count-2)); // 6 vertices per triangle
                for(int i = 2; i < count; i++) {
                    final short a = srcIndices.get(basePos + 0);
                    final short b = srcIndices.get(basePos + i-1);
                    final short c = srcIndices.get(basePos + i);
                    retval.put(a);
                    retval.put(b);
                    retval.put(b);
                    retval.put(c);
                    retval.put(c);
                    retval.put(a);
                }
                break;
            case GLES20FixedPipeline.GL_TRIANGLES :
                retval = allocateShorts(6*(count/3)); // 6 vertices per triangle
                for(int i = 0; i < count/3; i++) {
                    final short a = srcIndices.get(basePos + i*3);
                    final short b = srcIndices.get(basePos + i*3 + 1);
                    final short c = srcIndices.get(basePos + i*3 + 2);
                    retval.put(a);
                    retval.put(b);
                    retval.put(b);
                    retval.put(c);
                    retval.put(c);
                    retval.put(a);
                }
                break;
            case GLES20FixedPipeline.GL_TRIANGLE_STRIP :
                retval = allocateShorts(6*(count-2)); // 6 vertices per triangle
                for(int i = 2; i < count; i++) {
                    final short a = srcIndices.get(basePos + i-2);
                    final short b = srcIndices.get(basePos + i-1);
                    final short c = srcIndices.get(basePos + i);
                    if(a == b || b == c)
                        continue;
                    retval.put(a);
                    retval.put(b);
                    retval.put(b);
                    retval.put(c);
                    retval.put(c);
                    retval.put(a);
                }
                break;
            default :
                throw new IllegalArgumentException();
        }
        retval.flip();
        return retval;
    }

    public static FloatBuffer deriveLines(int mode, int size, int stride, ByteBuffer srcVertices, int count) {
        if(stride == 0)
            stride = size*4;

        FloatBuffer retval;
        final VertexTransfer vtx;
        switch(size) {
            case 2 :
                vtx = VertexTransfer.Vertex2D;
                break;
            case 3 :
                vtx = VertexTransfer.Vertex3D;
                break;
            default :
                throw new IllegalArgumentException();
        }
        switch(mode) {
            case GLES20FixedPipeline.GL_TRIANGLE_FAN :
                retval = allocateFloats(6*(count-2)*size); // 6 vertices per triangle
                for(int i = 2; i < count; i++) {
                    final int a = 0;
                    final int b = (i-1);
                    final int c = i;
                    vtx.copyVertex(srcVertices, stride, retval, a, b, c);
                }
                break;
            case GLES20FixedPipeline.GL_TRIANGLES :
                retval = allocateFloats(6*count/3*size); // 6 vertices per triangle
                for(int i = 0; i < count/3; i++) {
                    final int a = (i*3);
                    final int b = (i*3 + 1);
                    final int c = (i*3 + 2);
                    vtx.copyVertex(srcVertices, stride, retval, a, b, c);
                }
                break;
            case GLES20FixedPipeline.GL_TRIANGLE_STRIP :
                retval = allocateFloats(6*(count-2)*size); // 6 vertices per triangle
                for(int i = 2; i < count; i++) {
                    final int a = (i-2);
                    final int b = (i-1);
                    final int c = i;
                    vtx.copyVertex(srcVertices, stride, retval, a, b, c);
                }
                break;
            default :
                throw new IllegalArgumentException();
        }
        retval.flip();
        return retval;
    }

    public static FloatBuffer deriveLines(int mode, int size, int stride, ByteBuffer srcVertices, int count, ShortBuffer srcIndices) {
        if(stride == 0)
            stride = size*4;

        FloatBuffer retval;
        final VertexTransfer vtx;
        switch(size) {
            case 2 :
                vtx = VertexTransfer.Vertex2D;
                break;
            case 3 :
                vtx = VertexTransfer.Vertex3D;
                break;
            default :
                throw new IllegalArgumentException();
        }
        final int basePos = srcIndices.position();
        switch(mode) {
            case GLES20FixedPipeline.GL_TRIANGLE_FAN :
                retval = allocateFloats(6*(count-2)*size); // 6 vertices per triangle
                for(int i = 2; i < count; i++) {
                    final short a = srcIndices.get(basePos + 0);
                    final short b = srcIndices.get(basePos + i-1);
                    final short c = srcIndices.get(basePos + i);
                    vtx.copyVertex(srcVertices, stride, retval, a, b, c);
                }
                break;
            case GLES20FixedPipeline.GL_TRIANGLES :
                retval = allocateFloats(6*count/3*size); // 6 vertices per triangle
                for(int i = 0; i < count/3; i++) {
                    final short a = srcIndices.get(basePos + i*3);
                    final short b = srcIndices.get(basePos + i*3 + 1);
                    final short c = srcIndices.get(basePos + i*3 + 2);
                    vtx.copyVertex(srcVertices, stride, retval, a, b, c);
                }
                break;
            case GLES20FixedPipeline.GL_TRIANGLE_STRIP :
                retval = allocateFloats(6*(count-2)*size); // 6 vertices per triangle
                for(int i = 2; i < count; i++) {
                    final short a = srcIndices.get(basePos + i-2);
                    final short b = srcIndices.get(basePos + i-1);
                    final short c = srcIndices.get(basePos + i);
                    if(a == b || b == c)
                        continue;
                    vtx.copyVertex(srcVertices, stride, retval, a, b, c);
                }
                break;
            default :
                throw new IllegalArgumentException();
        }
        retval.flip();
        return retval;
    }

    private interface VertexTransfer {
        VertexTransfer Vertex2D = new VertexTransfer() {
            @Override
            public void copyVertex(ByteBuffer src, int srcStride, FloatBuffer dst, int a, int b, int c) {
                final int srcPos = src.position();
                final float ax = src.getFloat(srcPos + a*srcStride);
                final float ay = src.getFloat(srcPos + a*srcStride + 4);
                final float bx = src.getFloat(srcPos + b*srcStride);
                final float by = src.getFloat(srcPos + b*srcStride + 4);
                final float cx = src.getFloat(srcPos + c*srcStride);
                final float cy = src.getFloat(srcPos + c*srcStride + 4);

                dst.put(ax);
                dst.put(ay);
                dst.put(bx);
                dst.put(by);
                dst.put(bx);
                dst.put(by);
                dst.put(cx);
                dst.put(cy);
                dst.put(cx);
                dst.put(cy);
                dst.put(ax);
                dst.put(ay);
            }
        };
        VertexTransfer Vertex3D = new VertexTransfer() {
            @Override
            public void copyVertex(ByteBuffer src, int srcStride, FloatBuffer dst, int a, int b, int c) {
                final int srcPos = src.position();
                final float ax = src.getFloat(srcPos + a*srcStride);
                final float ay = src.getFloat(srcPos + a*srcStride + 4);
                final float az = src.getFloat(srcPos + a*srcStride + 8);
                final float bx = src.getFloat(srcPos + b*srcStride);
                final float by = src.getFloat(srcPos + b*srcStride + 4);
                final float bz = src.getFloat(srcPos + b*srcStride + 8);
                final float cx = src.getFloat(srcPos + c*srcStride);
                final float cy = src.getFloat(srcPos + c*srcStride + 4);
                final float cz = src.getFloat(srcPos + c*srcStride + 8);

                // a-b
                dst.put(ax);
                dst.put(ay);
                dst.put(az);
                dst.put(bx);
                dst.put(by);
                dst.put(bz);
                // b-c
                dst.put(bx);
                dst.put(by);
                dst.put(bz);
                dst.put(cx);
                dst.put(cy);
                dst.put(cz);
                // c-a
                dst.put(cx);
                dst.put(cy);
                dst.put(cz);
                dst.put(ax);
                dst.put(ay);
                dst.put(az);
            }
        };
        void copyVertex(ByteBuffer src, int srcStride, FloatBuffer dst, int a, int b, int c);
    }
}
