
package com.atakmap.opengl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.opengl.GLES30;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.Test;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

public class SkirtTest extends ATAKInstrumentedTest {
    // public static void create(int mode, int stride, FloatBuffer vertices, ShortBuffer edgeIndices, int count, ShortBuffer skirtIndices, float height) {

    @Test
    public void getNumOutputVertices_verify_result() {
        final int count = 6;
        assertEquals(count, Skirt.getNumOutputVertices(count));
    }

    @Test
    public void getNumOutputIndices_verify_result() {
        final int count = 6;
        assertEquals(count * 2,
                Skirt.getNumOutputIndices(GLES30.GL_TRIANGLE_STRIP, count));
        assertEquals((count - 1) * 6,
                Skirt.getNumOutputIndices(GLES30.GL_TRIANGLES, count));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getNumOutputIndices_badMode_throws() {
        Skirt.getNumOutputIndices(GLES30.GL_TRIANGLE_FAN, 6);
    }

    // argument validation
    @Test(expected = IllegalArgumentException.class)
    public void create_badVertexCount_throws() {
        final int mode = GLES30.GL_TRIANGLE_STRIP;
        final int count = 2;
        final FloatBuffer srcVertices = FloatBuffer
                .allocate(count + Skirt.getNumOutputVertices(count) * 3);
        final ShortBuffer edgeIndices = ShortBuffer.allocate(count);
        final ShortBuffer skirtIndices = ShortBuffer
                .allocate(Skirt.getNumOutputIndices(mode, count));
        final float height = 100;

        srcVertices.limit(count * 3);

        Skirt.create(mode, 0, srcVertices, edgeIndices, 1, skirtIndices,
                height);
    }

    @Test(expected = IllegalArgumentException.class)
    public void create_badMode__throws() {
        final int mode = GLES30.GL_TRIANGLE_STRIP;
        final int count = 2;
        final FloatBuffer srcVertices = FloatBuffer
                .allocate(count + Skirt.getNumOutputVertices(count) * 3);
        final ShortBuffer edgeIndices = ShortBuffer.allocate(count);
        final ShortBuffer skirtIndices = ShortBuffer
                .allocate(Skirt.getNumOutputIndices(mode, count));
        final float height = 100;

        srcVertices.limit(count * 3);

        Skirt.create(GLES30.GL_TRIANGLE_FAN, 0, srcVertices, edgeIndices, count,
                skirtIndices, height);
    }

    @Test(expected = IllegalArgumentException.class)
    public void create_nullSrcVertices__throws() {
        final int mode = GLES30.GL_TRIANGLE_STRIP;
        final int count = 2;
        final FloatBuffer srcVertices = null;
        final ShortBuffer edgeIndices = ShortBuffer.allocate(count);
        final ShortBuffer skirtIndices = ShortBuffer
                .allocate(Skirt.getNumOutputIndices(mode, count));
        final float height = 100;

        Skirt.create(mode, 0, srcVertices, edgeIndices, count, skirtIndices,
                height);
    }

    @Test(expected = IllegalArgumentException.class)
    public void create_nullSkirtIndices__throws() {
        final int mode = GLES30.GL_TRIANGLE_STRIP;
        final int count = 2;
        final FloatBuffer srcVertices = FloatBuffer
                .allocate(count + Skirt.getNumOutputVertices(count) * 3);
        final ShortBuffer edgeIndices = ShortBuffer.allocate(count);
        final ShortBuffer skirtIndices = null;
        final float height = 100;

        srcVertices.limit(count * 3);

        Skirt.create(mode, 0, srcVertices, edgeIndices, count, skirtIndices,
                height);
    }

    @Test(expected = IllegalArgumentException.class)
    public void create_badStride_throws() {
        final int mode = GLES30.GL_TRIANGLE_STRIP;
        final int count = 2;
        final FloatBuffer srcVertices = FloatBuffer
                .allocate(count + Skirt.getNumOutputVertices(count) * 3);
        final ShortBuffer edgeIndices = ShortBuffer.allocate(count);
        final ShortBuffer skirtIndices = ShortBuffer
                .allocate(Skirt.getNumOutputIndices(mode, count));
        final float height = 100;

        srcVertices.limit(count * 3);

        Skirt.create(mode, 2, srcVertices, edgeIndices, count, skirtIndices,
                height);
    }

    // geometry generation
    @Test
    public void create_trianglestrip_firstskirtindexisedgeindices0() {
        final int mode = GLES30.GL_TRIANGLE_STRIP;
        final int count = 4;
        final FloatBuffer vertices = FloatBuffer
                .allocate(count * 3 + Skirt.getNumOutputVertices(count) * 3);
        final ShortBuffer edgeIndices = ShortBuffer.allocate(count);
        final ShortBuffer skirtIndices = ShortBuffer
                .allocate(Skirt.getNumOutputIndices(mode, count));
        final float height = 100;

        constructMeshGeometry(0, vertices, edgeIndices);

        // shift the edge indices so that edgeIndices[0] is non-zero
        final short aidx = edgeIndices.get(0);
        final short bidx = edgeIndices.get(1);
        final short cidx = edgeIndices.get(2);
        final short didx = edgeIndices.get(3);
        edgeIndices.put(0, bidx);
        edgeIndices.put(1, cidx);
        edgeIndices.put(2, didx);
        edgeIndices.put(3, aidx);

        assertNotEquals(0, edgeIndices.get(0));

        Skirt.create(mode, 0, vertices, edgeIndices, count, skirtIndices,
                height);
        assertEquals(edgeIndices.get(0), skirtIndices.get(0));
    }

    @Test
    public void create_trianglestrip_nulledgeindices_firstskirtindexis0() {
        final int mode = GLES30.GL_TRIANGLE_STRIP;
        final int count = 4;
        final FloatBuffer vertices = FloatBuffer
                .allocate(count * 3 + Skirt.getNumOutputVertices(count) * 3);
        final ShortBuffer edgeIndices = ShortBuffer.allocate(count);
        final ShortBuffer skirtIndices = ShortBuffer
                .allocate(Skirt.getNumOutputIndices(mode, count));
        final float height = 100;

        constructMeshGeometry(0, vertices, edgeIndices);

        Skirt.create(mode, 0, vertices, edgeIndices, count, skirtIndices,
                height);
        assertEquals(0, skirtIndices.get(0));
    }

    @Test
    public void create_trianglestrip_implicitstride() {
        final int mode = GLES30.GL_TRIANGLE_STRIP;
        final int count = 4;
        final FloatBuffer vertices = FloatBuffer
                .allocate(count * 3 + Skirt.getNumOutputVertices(count) * 3);
        final ShortBuffer edgeIndices = ShortBuffer.allocate(count);
        final ShortBuffer skirtIndices = ShortBuffer
                .allocate(Skirt.getNumOutputIndices(mode, count));
        final float height = 100;

        constructMeshGeometry(0, vertices, edgeIndices);

        Skirt.create(mode, 0, vertices, edgeIndices, count, skirtIndices,
                height);
        validateTriangleStripOutput(count, 0, vertices, edgeIndices,
                skirtIndices, height);
    }

    @Test
    public void create_triangles_explicitstride() {
        final int mode = GLES30.GL_TRIANGLES;
        final int count = 4;
        final int stride = 3;
        final FloatBuffer vertices = FloatBuffer.allocate(
                count * stride + Skirt.getNumOutputVertices(count) * stride);
        final ShortBuffer edgeIndices = ShortBuffer.allocate(count);
        final ShortBuffer skirtIndices = ShortBuffer
                .allocate(Skirt.getNumOutputIndices(mode, count));
        final float height = 100;

        constructMeshGeometry(stride, vertices, edgeIndices);

        Skirt.create(mode, stride, vertices, edgeIndices, count, skirtIndices,
                height);
        validateTrianglesOutput(count, stride, vertices, edgeIndices,
                skirtIndices, height);
    }

    @Test
    public void create_triangles_largestride() {
        final int mode = GLES30.GL_TRIANGLES;
        final int count = 4;
        final int stride = 4;
        final FloatBuffer vertices = FloatBuffer.allocate(
                count * stride + Skirt.getNumOutputVertices(count) * stride);
        final ShortBuffer edgeIndices = ShortBuffer.allocate(count);
        final ShortBuffer skirtIndices = ShortBuffer
                .allocate(Skirt.getNumOutputIndices(mode, count));
        final float height = 100;

        constructMeshGeometry(stride, vertices, edgeIndices);

        Skirt.create(mode, stride, vertices, edgeIndices, count, skirtIndices,
                height);
        validateTrianglesOutput(count, stride, vertices, edgeIndices,
                skirtIndices, height);
    }

    private static void validateOutputBase(int count, int stride,
            FloatBuffer vertices, float skirtHeight) {
        if (stride == 0)
            stride = 3;

        // verify output vertex count
        assertEquals(stride * (count + Skirt.getNumOutputVertices(count)),
                vertices.limit());

        Set<float[]> pts = new TreeSet<>(new Comparator<float[]>() {
            @Override
            public int compare(float[] a, float[] b) {
                for (int i = 0; i < 3; i++) {
                    if (a[i] > b[i])
                        return 1;
                    else if (a[i] < b[i])
                        return -1;
                }
                return 0;
            }
        });

        /** validate vertices **/
        for (int i = 0; i < vertices.limit() / stride; i++) {
            float[] xyz = new float[3];
            xyz[0] = vertices.get((i * stride));
            xyz[1] = vertices.get((i * stride) + 1);
            xyz[2] = vertices.get((i * stride) + 2);
            pts.add(xyz);
        }

        // verify the source vertices are present
        assertTrue(pts.remove(new float[] {
                100, 100, 1
        }));
        assertTrue(pts.remove(new float[] {
                200, 100, 2
        }));
        assertTrue(pts.remove(new float[] {
                200, 200, 3
        }));
        assertTrue(pts.remove(new float[] {
                300, 200, 4
        }));
        // verify skirt vertices are present
        assertTrue(pts.remove(new float[] {
                100, 100, 1 - skirtHeight
        }));
        assertTrue(pts.remove(new float[] {
                200, 100, 2 - skirtHeight
        }));
        assertTrue(pts.remove(new float[] {
                200, 200, 3 - skirtHeight
        }));
        assertTrue(pts.remove(new float[] {
                300, 200, 4 - skirtHeight
        }));

        assertTrue(pts.isEmpty());
    }

    private static void validateTrianglesOutput(int count, int stride,
            FloatBuffer vertices, ShortBuffer edgeIndices,
            ShortBuffer skirtIndices, float skirtHeight) {
        validateOutputBase(count, stride, vertices, skirtHeight);

        Set<short[]> tris = new TreeSet<>(new Comparator<short[]>() {
            @Override
            public int compare(short[] a, short[] b) {
                short[] as = new short[] {
                        a[0], a[1], a[2]
                };
                java.util.Arrays.sort(as);
                short[] bs = new short[] {
                        b[0], b[1], b[2]
                };
                java.util.Arrays.sort(bs);

                for (int i = 0; i < 3; i++) {
                    if (a[i] > b[i])
                        return 1;
                    else if (a[i] < b[i])
                        return -1;
                }
                return 0;
            }
        });
        /** validate skirt indices **/
        for (int i = 0; i < skirtIndices.limit(); i += 3) {
            short[] tri = new short[3];
            tri[0] = skirtIndices.get(i);
            tri[1] = skirtIndices.get(i + 1);
            tri[2] = skirtIndices.get(i + 2);
            tris.add(tri);
        }

        // verify triangles are present; whitebox per-quad triangle vertex
        // selection, ideally should be blackbox

        // a --- b
        // | \   |
        // |  \  |
        // |   \ |
        // c --- d
        // C-D-A, A-D-B
        for (int i = 0; i < (count - 1); i++) {
            assertTrue(tris.remove(new short[] {
                    (short) (count + i), (short) (count + i + 1),
                    edgeIndices.get(i)
            }));
            assertTrue(tris.remove(new short[] {
                    edgeIndices.get(i), (short) (count + i + 1),
                    edgeIndices.get(i + 1)
            }));
        }

        assertTrue(tris.isEmpty());
    }

    private static void validateTriangleStripOutput(int count, int stride,
            FloatBuffer vertices, ShortBuffer edgeIndices,
            ShortBuffer skirtIndices, float skirtHeight) {
        validateOutputBase(count, stride, vertices, skirtHeight);

        Set<short[]> tris = new TreeSet<>(new Comparator<short[]>() {
            @Override
            public int compare(short[] a, short[] b) {
                short[] as = new short[] {
                        a[0], a[1]
                };
                java.util.Arrays.sort(as);
                short[] bs = new short[] {
                        b[0], b[1]
                };
                java.util.Arrays.sort(bs);

                for (int i = 0; i < 2; i++) {
                    if (a[i] > b[i])
                        return 1;
                    else if (a[i] < b[i])
                        return -1;
                }
                return 0;
            }
        });
        /** validate skirt indices **/
        for (int i = 0; i < skirtIndices.limit(); i += 2) {
            short[] tri = new short[2];
            tri[0] = skirtIndices.get(i);
            tri[1] = skirtIndices.get(i + 1);
            tris.add(tri);
        }

        // verify triangles are present; whitebox per-quad triangle vertex
        // selection, ideally should be blackbox
        for (int i = 0; i < count; i++) {
            assertTrue(tris.remove(new short[] {
                    edgeIndices.get(i), (short) (count + i)
            }));
        }

        assertTrue(tris.isEmpty());
    }

    private static void constructMeshGeometry(int stride, FloatBuffer vertices,
            ShortBuffer edgeIndices) {
        if (stride == 0)
            stride = 3;
        final int skip = stride - 3;
        vertices.put(100);
        vertices.put(100);
        vertices.put(1);
        if (skip > 0)
            vertices.position(vertices.position() + skip);
        edgeIndices.put((short) 0);

        vertices.put(200);
        vertices.put(100);
        vertices.put(2);
        if (skip > 0)
            vertices.position(vertices.position() + skip);
        edgeIndices.put((short) 1);

        vertices.put(200);
        vertices.put(200);
        vertices.put(3);
        if (skip > 0)
            vertices.position(vertices.position() + skip);
        edgeIndices.put((short) 2);

        vertices.put(300);
        vertices.put(200);
        vertices.put(4);
        if (skip > 0)
            vertices.position(vertices.position() + skip);
        edgeIndices.put((short) 3);

        vertices.flip();
        edgeIndices.flip();
    }
}
