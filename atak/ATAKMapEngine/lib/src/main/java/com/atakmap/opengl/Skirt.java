package com.atakmap.opengl;

import android.opengl.GLES20;
import android.opengl.GLES30;

import com.atakmap.lang.Unsafe;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

public final class Skirt {
    private Skirt() {}

    /**
     * Creates a new skirt.
     *
     * <P>Skirt vertices will be appended to the supplied vertex buffer,
     * starting at the current limit. On return, the <I>position</I> of the
     * vertex buffer will be unmodified, but the <I>limit</I> will be updated,
     * reflecting the vertex data for skirt that was appended.
     *
     * <P>If the exterior edge forms a loop, the supplied
     * <code>edgeIndices</code> should repeat the first-index-as-last-index to
     * close.
     *
     * <P>If the <code>mode</code> is {@link GLES30#GL_TRIANGLE_STRIP} and the
     * skirt indices are being appended to draw indices for the entire mesh,
     * the caller <B>MUST</B> insert the indices for a degenerate triangle
     * before invoking this method. The first index emitted will always be
     * the first edge vertex index (<code>0</code> if <code>edgeIndices</code>
     * is <code>null</code> or <code>edgeIndices.get(0)</code> otherwise).
     *
     * @param mode          The mode, accepts {@link GLES30#GL_TRIANGLES} or {@link GLES30#GL_TRIANGLE_STRIP}
     * @param stride        The stride, in <I>elements</I>. If <code>0</code> a stride of <code>3</code> elements is assumed.
     * @param vertices      The source vertices, may not be <code>null</code>
     * @param edgeIndices   The indices defining the exterior edge of the mesh, if <code>null</code>, the first <code>count</code> vertices are assumed to be the exterior edge vertices
     * @param count         The exterior edge vertex count, must be greater than <code>1</code>.
     * @param skirtIndices  The buffer to store the generated indices for the skirt mesh geometry, may not be <code>null</code>
     */
    public static void create(int mode, int stride, FloatBuffer vertices, ShortBuffer edgeIndices, int count, ShortBuffer skirtIndices, float height) {
        if(vertices == null)
            throw new IllegalArgumentException();
        FloatBuffer dvertices = asDirect(vertices, FloatBuffer.class);
        ShortBuffer dedgeIndices = asDirect(edgeIndices, ShortBuffer.class);
        if(skirtIndices == null)
            throw new IllegalArgumentException();
        ShortBuffer dskirtIndices = asDirect(skirtIndices, ShortBuffer.class);

        if(stride ==0)
            stride = 3;

        create(mode,
                stride*4,
                GLES20.GL_FLOAT,
                Unsafe.getBufferPointer(dvertices)+(4*dvertices.position()),
                (dvertices.limit()-dvertices.position())*4,
                (dvertices.capacity()-dvertices.position())*4,
                GLES20.GL_UNSIGNED_SHORT,
                (edgeIndices != null) ? Unsafe.getBufferPointer(dedgeIndices)+(2*dedgeIndices.position()) : null,
                (edgeIndices != null) ? (dedgeIndices.capacity()-dedgeIndices.position())*2 : 0,
                count,
                Unsafe.getBufferPointer(dskirtIndices)+(2*dskirtIndices.position()),
                (dskirtIndices.capacity()-dskirtIndices.position())*2,
                height);

        // data was written, update limit
        dvertices.limit(dvertices.limit()+(stride*getNumOutputVertices(count)));
        if(dvertices != vertices) {
            // copy back
            final int basePos = vertices.position();
            vertices.limit(dvertices.limit());
            vertices.put(dvertices);
            vertices.position(basePos);

            // free
            Unsafe.free(dvertices);
        }
        if(dedgeIndices != edgeIndices) {
            // read-only
            Unsafe.free(dedgeIndices);
        }
        // data was written, update limit
        dskirtIndices.limit(dskirtIndices.position()+getNumOutputIndices(mode, count));
        if(dskirtIndices != skirtIndices) {
            // copy back
            final int basePos = skirtIndices.position();
            skirtIndices.limit(dskirtIndices.limit());
            skirtIndices.put(dskirtIndices);

            // free
            Unsafe.free(dskirtIndices);
        } else {
            dskirtIndices.position(dskirtIndices.limit());
        }

        // XXX - consistency with original implementation, flip may not
        //       actually be desired here
        skirtIndices.flip();
    }

    private static native void create(int mode, int stride, int verticesType, long verticesPtr, int verticesLim, int verticesSize, int indicesType, long edgeIndicesPtr, int edgeIndicesSize, int count, long skirtIndicesPtr, int skirtIndicesSize, float height);

    /**
     * Returns the number of skirt vertices that will be output.
     * @param count The number of exterior edge vertices
     * @return  The number of skirt vertices that will be output to the vertex
     *          buffer as a result of
     *          {@link #create(int, int, FloatBuffer, ShortBuffer, int, ShortBuffer, float)}}
     */
    public static native int getNumOutputVertices(int count);

    /**
     * Returns the number of indices that will be output to define the skirt mesh.
     * @param mode  The mesh draw mode
     * @param count The number of exterior edge vertices
     * @return
     */
    public static native int getNumOutputIndices(int mode, int count);

    private static <T extends Buffer> T asDirect(T src, Class<T> buftype) {
        if(src == null)
            return null;
        if(src.isDirect())
            return src;
        T retval = Unsafe.allocateDirect(src.capacity(), buftype);
        retval.position(src.position());
        if(buftype == DoubleBuffer.class) {
            ((DoubleBuffer)retval).put(((DoubleBuffer)src).duplicate());
        } else if(buftype == FloatBuffer.class) {
            ((FloatBuffer)retval).put(((FloatBuffer)src).duplicate());
        } else if(buftype == ShortBuffer.class) {
            ((ShortBuffer)retval).put(((ShortBuffer)src).duplicate());
        } else if(buftype == IntBuffer.class) {
            ((IntBuffer)retval).put(((IntBuffer)src).duplicate());
        } else if(buftype == LongBuffer.class) {
            ((LongBuffer)retval).put(((LongBuffer)src).duplicate());
        } else if(buftype == CharBuffer.class) {
            ((CharBuffer)retval).put(((CharBuffer)src).duplicate());
        } else if(buftype == ByteBuffer.class) {
            ((ByteBuffer)retval).put(((ByteBuffer)src).duplicate());
        } else {
            throw new IllegalArgumentException();
        }
        retval.position(src.position());
        retval.limit(src.limit());
        return retval;
    }
}
