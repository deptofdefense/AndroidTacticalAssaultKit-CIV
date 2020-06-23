package com.atakmap.opengl;

import android.opengl.GLES30;

import java.nio.Buffer;
import java.nio.FloatBuffer;
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
        switch(mode) {
            case GLES30.GL_TRIANGLE_STRIP :
            case GLES30.GL_TRIANGLES :
                break;
            default :
                throw new IllegalArgumentException("mode is not correct");
        }
        if(count < 2)
            throw new IllegalArgumentException("count is less then 2");
        if(stride == 0)
            stride = 3;
        else if(stride < 3)
            throw new IllegalArgumentException("stride is less than 3");
        if(vertices == null)
            throw new IllegalArgumentException("vertices is null");
        if(skirtIndices == null)
            throw new IllegalArgumentException("skirtIndices is null");

        final int basePos = vertices.position();
        final int baseLim = vertices.limit();
        vertices.position(vertices.limit());
        vertices.limit(vertices.capacity());

        final int skip = stride-3;

        // write skirt vertices to vertex buffer
        for (int i = 0; i < count; i++) {
            final int index = (edgeIndices != null) ? (int)edgeIndices.get(edgeIndices.position()+i) : i;

            // top vertex (from exterior edge)
            final float x_t = vertices.get(basePos + (index * stride));
            final float y_t = vertices.get(basePos + (index * stride) + 1);
            final float z_t = vertices.get(basePos + (index * stride) + 2);

            // emit skirt vertex
            final float x_b = x_t;
            final float y_b = y_t;
            final float z_b = z_t - height;

            vertices.put(x_b);
            vertices.put(y_b);
            vertices.put(z_b);
            if(skip > 0)
                vertices.position(vertices.position()+skip);
        }

        // emit indices for skirt mesh geometry
        if(mode == GLES30.GL_TRIANGLE_STRIP) {
            // for a triangle strip, we emit the top and bottom for each vertex
            // in the exterior edge
            for (int i = 0; i < count; i++) {
                final int index = (edgeIndices != null) ? (int)edgeIndices.get(edgeIndices.position()+i) : i;

                skirtIndices.put((short)index);
                skirtIndices.put((short)(baseLim/stride+i));
            }
        } else if(mode == GLES30.GL_TRIANGLES) {
            // for triangles, we emit two triangles for each segment in the
            // exterior edge
            for (int i = 0; i < count-1; i++) {
                final int index0 = (edgeIndices != null) ? (int)edgeIndices.get(edgeIndices.position()+i) : i;
                final int index1 = (edgeIndices != null) ? (int)edgeIndices.get(edgeIndices.position()+(i+1)) : (i+1);

                // a --- b
                // | \   |
                // |  \  |
                // |   \ |
                // c --- d
                final int aidx = index0;
                final int bidx = index1;
                final int cidx = baseLim/stride+i;
                final int didx = baseLim/stride+(i+1);

                skirtIndices.put((short)cidx);
                skirtIndices.put((short)didx);
                skirtIndices.put((short)aidx);

                skirtIndices.put((short)aidx);
                skirtIndices.put((short)didx);
                skirtIndices.put((short)bidx);
            }
        } // else illegal state

        vertices.flip();
        vertices.position(basePos);
        skirtIndices.flip();
    }

    /**
     * Returns the number of skirt vertices that will be output.
     * @param count The number of exterior edge vertices
     * @return  The number of skirt vertices that will be output to the vertex
     *          buffer as a result of
     *          {@link #create(int, int, FloatBuffer, ShortBuffer, int, ShortBuffer, float)}}
     */
    public static int getNumOutputVertices(int count) {
        return count;
    }

    /**
     * Returns the number of indices that will be output to define the skirt mesh.
     * @param mode  The mesh draw mode
     * @param count The number of exterior edge vertices
     * @return the number of output indicies based on the mode and the count
     */
    public static int getNumOutputIndices(int mode, int count) {
        switch(mode) {
            case GLES30.GL_TRIANGLES :
                return 6*(count-1);
            case GLES30.GL_TRIANGLE_STRIP :
                return 2*count;
            default :
                throw new IllegalArgumentException("invalid mode");
        }
    }
}
