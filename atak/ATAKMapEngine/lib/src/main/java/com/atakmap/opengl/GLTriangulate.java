
package com.atakmap.opengl;

import android.opengl.GLES30;

import java.nio.Buffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Simple polygon triangulation, based on Triangulation by Ear Clipping by David Eberly.
 * <P>
 * http://www.geometrictools.com/Documentation/TriangulationByEarClipping.pdf
 * 
 * @author Developer
 */
public class GLTriangulate {

    public final static int TRIANGLE_FAN = 0;
    public final static int INDEXED = 1;
    public final static int STENCIL = 2;

    /**
     *
     * @param verts     The vertices, XY pairs. Does not expect
     *                  first-point-as-last
     * @param numVerts  The vertex count. Like <code>verts</code> this should
     *                  not include the first point repeated as last to "close"
     *                  the polygon
     * @param indices   Should have a capacity of at least
     *                  <code>(numVerts-2)*3</code>
     * @return  One of {@link #TRIANGLE_FAN}, {@link #INDEXED} or
     *          {@link #STENCIL}
     */
    public static int triangulate(FloatBuffer verts, int numVerts, ShortBuffer indices) {
        return triangulate(verts, 2, numVerts, indices);
    }

    public static int triangulate(FloatBuffer verts, int size, int numVerts, ShortBuffer indices) {
        Polygon poly = new Polygon(verts, size, numVerts);
        if (poly.convex)
            return TRIANGLE_FAN;
        if (triangulate(poly, indices))
            return INDEXED;
        else
            return STENCIL;
    }

    /**
     *
     * @param verts     The vertices, XY pairs. Does not expect
     *                  first-point-as-last
     * @param numVerts  The vertex count. Like <code>verts</code> this should
     *                  not include the first point repeated as last to "close"
     *                  the polygon
     * @param indices   Should have a capacity of at least
     *                  <code>(numVerts-2)*3</code>
     * @return  One of {@link #TRIANGLE_FAN}, {@link #INDEXED} or
     *          {@link #STENCIL}
     */
    public static int triangulate(DoubleBuffer verts, int numVerts, ShortBuffer indices) {
        return triangulate(verts, 2, numVerts, indices);
    }

    /**
     *
     * @param verts     The vertices. Does not expect first-point-as-last
     * @param size      The number of components per vertex. Use <code>2</code>
     *                  for XY or <code>3</code> for XYZ
     * @param numVerts  The vertex count. Like <code>verts</code> this should
     *                  not include the first point repeated as last to "close"
     *                  the polygon
     * @param indices   Should have a capacity of at least
     *                  <code>(numVerts-2)*3</code>
     * @return  One of {@link #TRIANGLE_FAN}, {@link #INDEXED} or
     *          {@link #STENCIL}
     */
    public static int triangulate(DoubleBuffer verts, int size, int numVerts, ShortBuffer indices) {
        Polygon poly = new Polygon(verts, size, numVerts);
        if (poly.convex)
            return TRIANGLE_FAN;
        if (triangulate(poly, indices))
            return INDEXED;
        else
            return STENCIL;
    }
    
    private static boolean triangulate(Polygon poly, ShortBuffer indices) {
        Vertex iter = poly.vertices;
        Vertex start = iter;
        while (poly.numVertices > 3) {
            if (isConvex(iter, poly.winding) && isEar(iter, poly.vertices)) {
                Vertex v = iter;

                indices.put((short) v.previous.index);
                indices.put((short) v.index);
                indices.put((short) v.next.index);

                // remove from the linked list
                v.previous.next = v.next;
                v.next.previous = v.previous;
                // recompute convexness
                v.previous.convex = isConvex(v.previous, poly.winding);
                v.next.convex = isConvex(v.next, poly.winding);

                // if we removed the head of the list, update the head
                if (iter == poly.vertices)
                    poly.vertices = iter.next;

                // decrement the vertices
                poly.numVertices--;

                // move back to the previous vertex since its convexness may
                // have changed
                iter = v.previous;
                start = iter;
            } else {
                iter = iter.next;
                // XXX - THIS NEEDS TO BE FIXED !!!
                // we're going to enter an infinite loop so indicate that
                // legacy fill should be used.
                if (iter == start)
                    return false;

            }
        }

        // three vertices left, make the final triangle
        indices.put((short) poly.vertices.previous.index);
        indices.put((short) poly.vertices.index);
        indices.put((short) poly.vertices.next.index);

        return true;
    }

    private static boolean isEar(Vertex test, Vertex vertices) {
        Vertex iter = vertices;
        Vertex v;
        do {
            v = iter;
            iter = iter.next;
            if (v == test || v == test.previous || v == test.next)
                continue;
            if (PointInTriangle(v, test.previous, test, test.next))
                return false;
        } while (iter != vertices);
        return true;
    }

    private static float sign(Vertex p1, Vertex p2, Vertex p3)
    {
        return (p1.x - p3.x) * (p2.y - p3.y) - (p2.x - p3.x) * (p1.y - p3.y);
    }

    private static boolean PointInTriangle(Vertex pt, Vertex v1, Vertex v2, Vertex v3)
    {
        boolean b1, b2, b3;

        b1 = sign(pt, v1, v2) < 0.0f;
        b2 = sign(pt, v2, v3) < 0.0f;
        b3 = sign(pt, v3, v1) < 0.0f;

        return ((b1 == b2) && (b2 == b3));
    }

    private static boolean isConvex(Vertex vertex, int winding) {
        final Vertex a = vertex.previous;
        final Vertex b = vertex;
        final Vertex c = vertex.next;

        // counter-clockwise, polygon interior is on left; clockwise, polygon
        // interior is on right

        // side greater than zero, point to left of line; side less than zero,
        // point to right of line
        final float side = (c.x - a.x) * (b.y - a.y) - (c.y - a.y) * (b.x - a.x);

        if (winding == Polygon.WINDING_COUNTER_CLOCKWISE && side > 0)
            return false;
        else if (winding == Polygon.WINDING_CLOCKWISE && side < 0)
            return false;
        return true;
    }

    private static class Polygon {
        final static int WINDING_CLOCKWISE = 0;
        final static int WINDING_COUNTER_CLOCKWISE = 1;

        private Vertex vertices;
        private int numVertices;
        boolean convex;
        int winding;

        public Polygon(FloatBuffer verts, int size, int numVerts) {
            this.numVertices = numVerts;
            switch(size) {
                case 2 :
                case 3 :
                    break;
                default :
                    throw new IllegalArgumentException();
            }

            int vIdx = 0;

            int idx = 0;
            this.vertices = new Vertex(verts.get(vIdx++), verts.get(vIdx++));
            if(size == 3)
                vIdx++;

            this.vertices.index = idx++;

            int convexness = 0;

            float edgeSum = 0.0f;
            float dx;
            float dy;

            int order = WINDING_COUNTER_CLOCKWISE;

            Vertex pointer = this.vertices;
            for (; idx < this.numVertices; idx++) {
                pointer.next = new Vertex(verts.get(vIdx++), verts.get(vIdx++));
                if(size == 3)
                    vIdx++;
                pointer.next.index = idx;
                pointer.next.previous = pointer;

                pointer = pointer.next;

                if (idx > 1) {
                    pointer.previous.convex = isConvex(pointer.previous, order);
                    convexness |= pointer.previous.convex ? 0x01 : 0x02;
                }

                dx = pointer.previous.x + pointer.x;
                dy = pointer.previous.y - pointer.y;

                edgeSum += dx * dy;
            }

            // link the tail to the head
            pointer.next = this.vertices;
            this.vertices.previous = pointer;

            // compute the convexness of the first two vertices
            this.vertices.convex = isConvex(this.vertices, order);
            convexness |= this.vertices.convex ? 0x01 : 0x02;
            pointer.convex = isConvex(pointer, order);
            convexness |= pointer.convex ? 0x01 : 0x02;

            // compute overall polygon convexness
            this.convex = (convexness < 3);

            // sum the last edge
            dx = this.vertices.previous.x + this.vertices.x;
            dy = this.vertices.previous.y - this.vertices.y;

            edgeSum += dx * dy;

            // determine polygon winding
            winding = (edgeSum >= 0) ? WINDING_CLOCKWISE : WINDING_COUNTER_CLOCKWISE;
        }

        public Polygon(DoubleBuffer verts, int size, int numVerts) {
            this.numVertices = numVerts;
            switch(size) {
                case 2 :
                case 3 :
                    break;
                default :
                    throw new IllegalArgumentException();
            }

            int idx = 0;
            this.vertices = new Vertex((float) verts.get(idx),
                    (float) verts.get(idx+1));
            this.vertices.index = idx++;

            int convexness = 0;

            float edgeSum = 0.0f;
            float dx;
            float dy;

            int order = WINDING_COUNTER_CLOCKWISE;

            Vertex pointer = this.vertices;
            for (; idx < this.numVertices; idx++) {
                pointer.next = new Vertex((float) verts.get(idx*size),
                        (float) verts.get(idx*size+1));
                pointer.next.index = idx;
                pointer.next.previous = pointer;

                pointer = pointer.next;

                if (idx > 1) {
                    pointer.previous.convex = isConvex(pointer.previous, order);
                    convexness |= pointer.previous.convex ? 0x01 : 0x02;
                }

                dx = pointer.previous.x + pointer.x;
                dy = pointer.previous.y - pointer.y;

                edgeSum += dx * dy;
            }

            // link the tail to the head
            pointer.next = this.vertices;
            this.vertices.previous = pointer;

            // compute the convexness of the first two vertices
            this.vertices.convex = isConvex(this.vertices, order);
            convexness |= this.vertices.convex ? 0x01 : 0x02;
            pointer.convex = isConvex(pointer, order);
            convexness |= pointer.convex ? 0x01 : 0x02;

            // compute overall polygon convexness
            this.convex = (convexness < 3);

            // sum the last edge
            dx = this.vertices.previous.x + this.vertices.x;
            dy = this.vertices.previous.y - this.vertices.y;

            edgeSum += dx * dy;

            // determine polygon winding
            winding = (edgeSum >= 0) ? WINDING_CLOCKWISE : WINDING_COUNTER_CLOCKWISE;
        }
    }

    /*************************************************************************/
    // fill drawing implementation

    public static void draw(int result, Buffer vertices, int vertType, int vertSize, Buffer indices, int idxType, int count, float r, float g, float b, float a) {
        switch(result) {
            case INDEXED :
                drawFillTriangulate(vertices,
                                    vertType,
                                    vertSize,
                                    indices,
                                    idxType,
                                    count,
                                    r, g, b, a);
                break;
            case TRIANGLE_FAN :
                drawFillConvex(vertices,
                               vertType,
                               vertSize,
                               count,
                               r, g, b, a);
                break;
            case STENCIL :
                drawFillStencil(vertices,
                        vertType,
                        vertSize,
                        count, r, g, b, a);
                break;
            default :
                throw new IllegalArgumentException();
        }
    }

    private static void drawFillTriangulate(Buffer v, int vertType, int vertSize, Buffer indices, int idxType, int count, float r, float g, float b, float a) {
        // count is the total number of points, including first-is-last vertex
        final int numVerts = (count-3)*3;

        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

        GLES20FixedPipeline.glVertexPointer(vertSize, vertType, 0, v);

        GLES20FixedPipeline.glColor4f(r, g, b, a);

        GLES20FixedPipeline.glDrawElements(GLES20FixedPipeline.GL_TRIANGLES,
                numVerts,
                idxType,
                indices);

        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
    }

    private static void drawFillConvex(Buffer v, int vertType, int size, int count, float r, float g, float b, float a) {
        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

        GLES20FixedPipeline.glVertexPointer(size, GLES20FixedPipeline.GL_FLOAT, 0, v);
        
        GLES20FixedPipeline.glColor4f(r, g, b, a);

        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_FAN, 0,
                count);

        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
    }

    private static void drawFillStencil(Buffer v, int vertType, int vertSize, int count, float r, float g, float b, float a) {
        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

        GLES20FixedPipeline.glVertexPointer(vertSize, vertType, 0, v);

        GLES20FixedPipeline.glClear(GLES20FixedPipeline.GL_STENCIL_BUFFER_BIT);

        GLES20FixedPipeline.glColorMask(false, false, false, false);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_STENCIL_TEST);
        GLES20FixedPipeline.glStencilFunc(GLES20FixedPipeline.GL_ALWAYS, 0x01, 0xFFFF);
        GLES20FixedPipeline.glStencilOp(GLES20FixedPipeline.GL_KEEP, GLES20FixedPipeline.GL_KEEP, GLES20FixedPipeline.GL_INCR);
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_FAN, 0, count);
        GLES20FixedPipeline.glColor4f(r, g, b, a);
        GLES20FixedPipeline.glColorMask(true, true, true, true);
        GLES20FixedPipeline.glStencilFunc(GLES20FixedPipeline.GL_EQUAL, 0x1, 0x1);
        GLES20FixedPipeline.glStencilOp(GLES20FixedPipeline.GL_KEEP, GLES20FixedPipeline.GL_KEEP, GLES20FixedPipeline.GL_ZERO);

        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_FAN, 0, count);

        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_STENCIL_TEST);

        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
    }

    /**************************************************************************/
    
    private static class Vertex {
        float x;
        float y;
        Vertex next;
        Vertex previous;
        int index;
        boolean convex;

        Vertex(float x, float y) {
            this.x = x;
            this.y = y;
            this.next = null;
            this.previous = null;
            this.index = -1;
            this.convex = false;
        }
    }
}
