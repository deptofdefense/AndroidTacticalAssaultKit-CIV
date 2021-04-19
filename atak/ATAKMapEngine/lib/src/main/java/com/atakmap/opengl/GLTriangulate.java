
package com.atakmap.opengl;

import android.util.Pair;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.Vector2D;

import java.nio.Buffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

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

    public static class Segment {
        public Vector2D start;
        public Vector2D end;
        // Use these ids to add order to the segments. The endID should point to the startID of the
        // next segment.
        public int startID;
        public int endID;
        public double slope;
        public double yIntercept;
        public double key;

        public Segment(Vector2D start, Vector2D end) {
            this.start = start;
            this.end = end;
            this.startID = -1;
            this.endID = -1;
        }

        public Segment(Vector2D start, Vector2D end, int startID, int endID) {
            this.start = start;
            this.end = end;
            this.startID = startID;
            this.endID = endID;

            this.slope = calculateSlope(this.start, this.end);
            this.yIntercept = findYIntercept(slope, this.start);
            this.key = slope * this.start.x + yIntercept;
        }

        public void updateKey(double x) {
            key = slope * x + yIntercept;
        }

        public boolean hasPoint(Vector2D p) {
            return (start.x == p.x && start.y == p.y) || (end.x == p.x && end.y == p.y);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Segment) {
                Segment s = (Segment) o;
//                return start.x == s.start.x && start.y == s.start.y && end.x == s.end.x && end.y == s.end.y;
                return (startID == s.startID && endID == s.endID) || (startID == s.endID && endID == s.startID);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash( startID, endID);
        }

        public boolean sharesPoint(Segment s) {
            return hasPoint(s.start) || hasPoint(s.end);
        }

    }

    // Vector2D with a height attribute
    public static class HeightVector2D extends Vector2D {

        public double height;

        public HeightVector2D(double x, double y, double alt, double height) {
            super(x, y, alt);
            this.height = height;
        }

        public HeightVector2D(double x, double y, double height) {
            this(x, y, 0d, height);
        }
    }

    private static class Event {
        enum Type {
            INSERT,
            DELETE,
            INTERSECT,
        }

        Vector2D vec;
        Type type;
        ArrayList<Segment> segments;

        public Event(Vector2D vec, Type type, Segment segment) {
            this.vec = vec;
            this.type = type;
            this.segments = new ArrayList<>();
            segments.add(segment);
        }

        public Event(Vector2D vec, Type type, Segment a, Segment b) {
            this.vec = vec;
            this.type = type;
            this.segments = new ArrayList<>();
            segments.add(a);
            segments.add(b);
        }

        public Vector2D getVector() {
            return this.vec;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Event) {
                Event e = (Event)o;
                return vec.x == e.vec.x && vec.y == e.vec.y;
            }
            return false;
        }
        @Override
        public int hashCode() {
            return Objects.hash(vec.x, vec.y);
        }

    }

    private static Comparator<Double> leastToGreatestComparator = new Comparator<Double>() {
        @Override
        public int compare(Double lhs, Double rhs) {
            return Double.compare(lhs, rhs);
        }
    };

    private static class EventQueue {
        // Using a TreeMap of TreeMaps so we don't run into issues with duplicate keys. The first
        // key is the x coordinate of the event, the second key is the slope of the event's first
        // segment.
        private TreeMap<Double, TreeMap<Double, Event>> _queue;

        public EventQueue(LinkedList<Segment> segments) {
            _queue = new TreeMap<>(leastToGreatestComparator);
            insert(segments);

        }

        public Event peek() {
            return _queue.firstEntry().getValue().firstEntry().getValue();
        }

        public boolean isEmpty() {
            return _queue.isEmpty();
        }

        public void insert(Event e) {
            if (e == null) {
                return;
            }
            if (_queue.containsKey(e.vec.x)) {
                _queue.get(e.vec.x).put(e.segments.get(0).slope, e);
            } else {
                TreeMap<Double, Event> treeMap = new TreeMap<>(leastToGreatestComparator);
                treeMap.put(e.segments.get(0).slope, e);
                _queue.put(e.vec.x, treeMap);
            }
        }

        public void insert(List<Segment> segments) {
            for (Segment segment : segments) {
                add(segment);
            }
        }
        public void remove(ArrayList<Segment> segments) {
            for (Segment segment : segments) {
                delete(segment.start.x, segment.slope);
                delete(segment.end.x, segment.slope);
            }
        }

        public Event extract() {
            Event event = peek();
            delete(event);
            return event;
        }

        public void delete(Event e) {
            delete(e.vec.x, e.segments.get(0).slope);
        }

        private void delete(double x, double slope) {
            TreeMap<Double, Event> map = _queue.get(x);
            if (map != null) {
                map.remove(slope);
                if (map.isEmpty()) {
                    _queue.remove(x);
                }
            }
        }

        public void removeIntersectEvent(Segment above, Segment below) {
            ArrayList<Event> removeList = new ArrayList<>();
            for (TreeMap<Double, Event> treeMap: _queue.values()) {
                for (Event event : treeMap.values()) {
                    if (event.type == Event.Type.INTERSECT) {
                        Segment a = event.segments.get(0);
                        Segment b = event.segments.get(1);
                        if ((a.equals(above) || b.equals(above)) && (a.equals(below) || b.equals(below))) {
                            removeList.add(event);
                        }
                    }
                }
            }
            for (Event event : removeList) {
                delete(event);
            }
        }

        public void insertIntersectResult(Pair<Segment, Segment> oldSegments,
                                          Pair<Segment, Segment> newSegments) {
            ArrayList<Event> removeList = new ArrayList<>();
            for (TreeMap<Double, Event> treeMap : _queue.values()) {
                for (Event event : treeMap.values()) {
                    if (oldSegments.first.equals(event.segments.get(0))
                            || oldSegments.second.equals(event.segments.get(0))) {
                        removeList.add(event);
                    }
                }
            }
            for (Event event : removeList) {
                delete(event);
            }
            insert(new Event(newSegments.first.start, Event.Type.INSERT, newSegments.first));
            insert(new Event(newSegments.second.start, Event.Type.INSERT, newSegments.second));
            insert(new Event(newSegments.first.end, Event.Type.DELETE, newSegments.first));
            insert(new Event(newSegments.second.end, Event.Type.DELETE, newSegments.second));
        }

        public void updateAdjacencies(SweepLineStatus status, ArrayList<Event> intersections, Event event) {
            ArrayList<Event> removeList = new ArrayList<>();
            for (TreeMap<Double, Event> treeMap: _queue.values()) {
                for (Event e : treeMap.values()) {
                    if (e.type == Event.Type.INTERSECT) {
                        if (status.isNotAdjacent(e.segments)
                                || e.segments.contains(event.segments.get(0))
                                || e.segments.contains(event.segments.get(1))) {
                            removeList.add(e);
                        }
                    }
                }
            }
            for (Event e : removeList) {
                delete(e);
            }
            for (Pair<Segment, Segment> pair : status.getAdjacentSegment()) {
                Event intersect = checkForIntersection(pair.first, pair.second);
                if (!intersections.contains(intersect) && intersect != null)  {
                    insert(intersect);
                }
            }
        }
        private void add(Segment segment) {
            if (segment.start.x < segment.end.x) {
                insert(new Event(segment.start, Event.Type.INSERT, segment));
                insert(new Event(segment.end, Event.Type.DELETE, segment));
            } else {
                insert(new Event(segment.start, Event.Type.DELETE, segment));
                insert(new Event(segment.end, Event.Type.INSERT, segment));
            }
        }
    }



    private static class SweepLineStatus {
        // The current position of the sweep line
        double x;
        // Store the segments in a TreeMap of TreeMaps so we don't run into issues with keys being
        // the same. The first key is the y-value at the current sweep-line x value. The second key
        // is the slope of the Segment.
        TreeMap<Double, TreeMap<Double, Segment>> segments;

        public SweepLineStatus(double x) {
            this.x = x;
            this.segments = new TreeMap<>(leastToGreatestComparator);
        }

        public void insert(Segment segment) {

            double leftX = segment.start.x;
            if (segment.end.x < leftX) {
                leftX = segment.end.x;
            }
            if (x < leftX) {
                x = leftX;
            }
            updateKeys();
            segment.updateKey(x);
            add(segment);
        }

        private void add(Segment segment) {
            TreeMap<Double, Segment> map = segments.get(segment.key);
            if (map == null) {
                map = new TreeMap<>();
                segments.put(segment.key, map);
            }
            map.put(segment.slope, segment);
        }


        public void delete(Segment segment) {
            TreeMap<Double, Segment> map = segments.get(segment.key);
            if (map == null)
                return;
            map.remove(segment.slope);
            if (map.isEmpty()) {
                segments.remove(segment.key);
            }
        }

        public void swap(Segment a, Segment b, ArrayList<Segment> newSegments) {
            delete(a);
            delete(b);
            Segment newA = null;
            Segment newB = null;
            for (Segment segment : newSegments) {
                if (segment.startID == a.startID || segment.endID == a.endID
                        || segment.startID == a.endID) {
                    newA = segment;
                } else {
                    newB = segment;
                }
            }
            
            if (newA != null && newB != null) { 
                newA.key = b.key;
                newB.key = a.key;
                add(newA);
                add(newB);
            }
        }

        public Segment findAbove(Segment segment) {
            Segment above = null;
            if (segments.lastEntry().getValue().lastEntry().getValue().equals(segment)) {
                return null;
            }
            Collection<Double> keys = segments.descendingKeySet();
            for (double key : keys) {
                Collection<Double> innerKeys = segments.get(key).descendingKeySet();
                for (double innerkey : innerKeys) {
                    Segment segAbove = segments.get(key).get(innerkey);
                    if (segment.equals(segAbove)) {
                        return above;
                    }
                    if (!segment.equals(segAbove) && !segment.sharesPoint(segAbove)) {
                        above = segments.get(key).get(innerkey);
                    }
                }
            }
            return above;
        }

        public Segment findBelow(Segment segment) {
            Segment below = null;
            if (segments.firstEntry().getValue().firstEntry().getValue().equals(segment)) {
                return null;
            }
            for (TreeMap<Double, Segment> map : segments.values()) {
                for (Segment s : map.values()) {
                    if (segment.equals(s)) {
                        return below;
                    }
                    if (!segment.equals(s) && !segment.sharesPoint(s)) {
                        below = s;
                    }
                }
            }
            return below;

        }

        public ArrayList<Pair<Segment, Segment>> getAdjacentSegment() {
            ArrayList<Pair<Segment, Segment>> adjacentSegments = new ArrayList<>();
            for (TreeMap<Double, Segment> map : segments.values()) {
                for (Segment segment : map.values()) {
                    Segment above = findAbove(segment);
                    if (above != null) {
                        adjacentSegments.add(new Pair<>(segment, above));
                    }
                }
            }
            return adjacentSegments;
        }

        public boolean isNotAdjacent(ArrayList<Segment> segs) {
            Segment a = segs.get(0);
            Segment b = segs.get(1);
            if (a == null || b == null) {
                return true;
            }
            Segment above = findAbove(a);
            Segment below = findBelow(a);
            if (above ==null || below == null) {
                return true;
            }
            return !b.equals(above) && !b.equals(below);
        }

        private void updateKeys() {
            ArrayList<Segment> updateList = new ArrayList<>();
            for (TreeMap<Double, Segment> map : segments.values()) {
                updateList.addAll(map.values());
            }
            segments.clear();
            for (Segment segment : updateList) {
                segment.updateKey(x);
                add(segment);
            }
        }
    }

    private static double calculateSlope(Vector2D a, Vector2D b) {
        double yDelta = b.y - a.y;
        double xDelta = b.x - a.x;
        if (yDelta == 0.0 && xDelta == 0.0) {
            return 0.0;
        }
        return yDelta / xDelta;
    }

    private static double findYIntercept(double slope, Vector2D point) {
        return point.y - slope * point.x;
    }

    /**
     * Converts the given self-intersecting polygon and produces an ArrayList of simple polygons
     * that can be later triangulated. Makes use of the a slightly modified sweep-line algorithm
     * listed here under "Decompose into Simple Pieces":
     * https://geomalgorithms.com/a09-_intersect-3.html#Bentley-Ottmann-Algorithm
     *
     * @param vertices The array of 2d points that make up the self-intersecting polygon.
     * @return A list of simple polygons, represented as a list of doubles.
     */
    public static ArrayList<ArrayList<Double>> extractIntersectingPolygons(Vector2D[] vertices) {
        // We'll use a LinkedList of ordered Segments to store the polygon edges. As we do the line
        // sweep the LinkedList will be updated to contain the simple polygons.
        LinkedList<Segment> chains = new LinkedList<>();
        for (int i = 0; i < vertices.length; i++) {
            int next = (i + 1) % vertices.length;
            chains.add(new Segment(vertices[i], vertices[next], i, next));
        }

        EventQueue eventQueue = new EventQueue(chains);
        SweepLineStatus status = new SweepLineStatus(eventQueue.peek().vec.x);
        ArrayList<Event> intersections = new ArrayList<>();
        while (!eventQueue.isEmpty()) {
            Event event = eventQueue.extract();
            Segment eventSegment = event.segments.get(0);
            if (event.type == Event.Type.INSERT) {
                status.insert(eventSegment);
                // get the segments that are immediately above and below the segment from the current
                // event. It's possible for either of these to be null.
                Segment above = status.findAbove(eventSegment);
                Segment below = status.findBelow(eventSegment);
                if (above != null && below != null) {
                    eventQueue.removeIntersectEvent(above, below);
                    eventQueue.insert(checkForIntersection(eventSegment, above));
                    eventQueue.insert(checkForIntersection(eventSegment, below));
                } else if (above != null) {
                    eventQueue.insert(checkForIntersection(eventSegment, above));
                } else if (below != null) {
                    eventQueue.insert(checkForIntersection(eventSegment, below));
                }
            } else if (event.type == Event.Type.INTERSECT) {
                // Since the two segments intersected we need to swap their position in the
                // sweep line status and update what events are adjacent in the event queue
                ArrayList<Segment> newSegments = updateChains(chains, event);
                intersections.add(event);
                status.swap(event.segments.get(0), event.segments.get(1), newSegments);
                eventQueue.remove(event.segments);
                eventQueue.insert(newSegments);

                eventQueue.updateAdjacencies(status, intersections, event);
            } else { // event.type == Event.Type.DELETE
                // Remove the event's segment from the sweep line status and check if the segments
                // that may exist above or below the event's segment intersect.
                Segment above = status.findAbove(eventSegment);
                Segment below = status.findBelow(eventSegment);
                status.delete(event.segments.get(0));
                eventQueue.insert(checkForIntersection(above, below));
            }
        }
        // extract all the simple polygons
        ArrayList<ArrayList<Vector2D>> polygons = getPolygonsFromChains(chains);
        ArrayList<ArrayList<Double>> ret = new ArrayList<>();
        for (ArrayList<Vector2D> chain : polygons) {
            ArrayList<Double> points = new ArrayList<>();
            for (Vector2D point : chain) {
                points.add(point.x);
                points.add(point.y);
                points.add(point.alt);
                if (point instanceof HeightVector2D)
                    points.add(((HeightVector2D) point).height);
            }
            ret.add(points);
        }
        return ret;
    }

    /**
     * Updates the list of directed segment chains with the new segments resulting from the
     * intersection event.
     * @param chains The LinkedList of directed segments.
     * @param event The intersection event.
     * @return An ArrayList containing the two new right most segments.
     */
    private static ArrayList<Segment> updateChains(LinkedList<Segment> chains, Event event) {
        Segment first = event.segments.get(0);
        Segment second = event.segments.get(1);
        Vector2D intersection = event.getVector();
        int intersectionID = chains.size() + 1;
        Segment aFirstHalf = new Segment(first.start, intersection, first.startID, intersectionID);
        Segment aSecondHalf = new Segment(intersection, second.end, intersectionID, second.endID);
        chains.remove(event.segments.get(0));
        chains.add(aFirstHalf);
        chains.add(aSecondHalf);


        intersectionID = chains.size() + 1;
        Segment bFirstHalf = new Segment(second.start, intersection, second.startID, intersectionID);
        Segment bSecondHalf = new Segment(intersection, first.end, intersectionID, first.endID);
        chains.remove(event.segments.get(1));
        chains.add(bFirstHalf);
        chains.add(bSecondHalf);

        ArrayList<Segment> newSegments = new ArrayList<>();
        newSegments.add(aFirstHalf);
        newSegments.add(aSecondHalf);
        newSegments.add(bFirstHalf);
        newSegments.add(bSecondHalf);
        ArrayList<Segment> ret = new ArrayList<>();
        // find the two right most segments
        for (Segment segment : newSegments) {
            Vector2D leftMost = findLeftMostPoint(segment);
            if (leftMost.x >= intersection.x) {
                ret.add(segment);
            }
        }

        return ret;
    }

    private static Vector2D findLeftMostPoint(Segment segment) {
        if (segment.start.x < segment.end.x) {
            return segment.start;
        }
        return segment.end;
    }

    private static ArrayList<ArrayList<Vector2D>> getPolygonsFromChains(LinkedList<Segment> chains) {
        ArrayList<ArrayList<Vector2D>> ret = new ArrayList<>();
        while (!chains.isEmpty()) {
            ArrayList<Vector2D> polygon = new ArrayList<>();
            int startID = chains.getFirst().startID;
            int nextID = chains.getFirst().endID;
            Vector2D start = chains.getFirst().start;
            polygon.add(start);
            while (nextID != startID) {
                Pair<Integer, Vector2D> nextPair = getNextSegment(chains, nextID);
                nextID = nextPair.first;
                polygon.add(nextPair.second);
            }
            chains.remove(0);
            ret.add(polygon);
        }
        return ret;
    }

    private static Pair<Integer, Vector2D> getNextSegment(LinkedList<Segment> chains, int startID) {
        int removeIndex = -1;
        Vector2D next = null;
        int nextID = -1;
        // loop through the LinkedList of Segments until we find a segment that has the same startID
        // as the one we're given
        for (int i = 0; i < chains.size(); i++) {
            Segment segment = chains.get(i);
            if (segment.startID == startID) {
                removeIndex = i;
                next = segment.start;
                nextID = segment.endID;
                break;
            }
        }
        chains.remove(removeIndex);
        return new Pair<>(nextID, next);
    }

    private static Event checkForIntersection(Segment a, Segment b) {
        if (a == null || b == null) {
            return null;
        }
        if (a.sharesPoint(b)) {
            return null;
        }
        Vector2D intersection = segmentToSegmentIntersection(a.start, a.end, b.start, b.end);
        if (intersection != null) {
            if (!a.hasPoint(intersection) && !b.hasPoint(intersection)) {
                return new Event(intersection, Event.Type.INTERSECT, a, b);
            }
        } else {
            // Have to do this ugly check since the intersection function doesn't give the correct
            // answer if a segment's endpoint is on another segment
            if (pointOnSegment(a.start, b)) {
                return new Event(a.start, Event.Type.INTERSECT, a, b);
            } else if (pointOnSegment(a.end, b)) {
                return new Event(a.end, Event.Type.INTERSECT, a, b);
            } else if (pointOnSegment(b.start, a)) {
                return new Event(b.start, Event.Type.INTERSECT, a, b);
            } else if (pointOnSegment(b.end, a)) {
                return new Event(b.end, Event.Type.INTERSECT, a, b);
            }
        }
        return null;
    }

    private static boolean pointOnSegment(Vector2D point, Segment segment) {
        return Double.compare(segment.start.distance(point) + segment.end.distance(point),
                segment.start.distance(segment.end)) == 0;
    }

    // Copied from Vector2D class w/ added support for altitude and height
    private static Vector2D segmentToSegmentIntersection(Vector2D seg10,
            Vector2D seg11, Vector2D seg01, Vector2D seg00) {
        Vector2D s0 = seg01.subtract(seg00);
        Vector2D s1 = seg11.subtract(seg10);
        double c1 = s1.cross(s0);
        if (c1 != 0d) {
            double t = seg00.subtract(seg10).cross(s0) / c1;
            double u = seg00.subtract(seg10).cross(s1) / c1;
            if ((t >= 0 && t <= 1) && (u >= 0 && u <= 1)) {
                Vector2D ret = seg00.add(s0.scale(u));

                // Interpolate altitude
                ret.alt = seg01.alt * (1 - u) + seg00.alt * u;

                // Interpolate height
                if (seg00 instanceof HeightVector2D && seg01 instanceof HeightVector2D) {
                    double h1 = ((HeightVector2D) seg00).height;
                    double h2 = ((HeightVector2D) seg01).height;
                    return new HeightVector2D(ret.x, ret.y, ret.alt,
                            h2 * (1 - u) + h1 * u);
                }

                return ret;
            }
        }
        return null;
    }
}
