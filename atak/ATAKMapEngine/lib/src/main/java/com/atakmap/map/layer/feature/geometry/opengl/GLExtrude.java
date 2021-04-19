
package com.atakmap.map.layer.feature.geometry.opengl;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.geometry.GeometryFactory.ExtrusionHints;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.opengl.GLTriangulate;
import com.atakmap.opengl.GLTriangulate.HeightVector2D;
import com.atakmap.opengl.GLTriangulate.Segment;

import java.nio.DoubleBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implements a few utility functions for extruding shapes into 3d objects and outlines.
 */
public class GLExtrude {

    /**
     * Extrude the given shape using a relative altitude. The base of the shape will be baseAltitude
     * and the top will be baseAltitude + extrudeHeight.
     *
     * @param baseAltitude The altitude of the shape's base.
     * @param points The shape's points.
     * @param closed True if the shape is closed, false if open
     * @param extrudeHeights The height(s) to extrude to. If a single value is
     *                       passed in, the extrusion will be a fixed height
 *                           relative to the points
     * @return The extruded shape.
     */
    public static DoubleBuffer extrudeRelative(double baseAltitude,
            GeoPoint[] points, boolean closed, double... extrudeHeights) {
        int p = 0;
        double[] tmp = new double[points.length * 3];
        for (int i = 0; i < tmp.length; i += 3) {
            tmp[i] = points[p].getLongitude();
            tmp[i + 1] = points[p].getLatitude();
            tmp[i + 2] = points[p].getAltitude();
            p++;
        }
        DoubleBuffer newPoints = DoubleBuffer.wrap(tmp);
        return extrudeRelative(baseAltitude, newPoints, 3, closed, extrudeHeights);
    }

    /**
     * Extrude the given shape using a relative altitude. The base of the shape will be baseAltitude
     * and the top will be baseAltitude + extrudeHeight.
     *
     * @param baseAltitude The altitude of the shape's base.
     * @param extrudeHeight The height to extrude to.
     * @param points The shape's points.
     * @param closed True if the shape is closed, false if open
     * @return The extruded shape.
     */
    public static DoubleBuffer extrudeRelative(double baseAltitude,
            double extrudeHeight, GeoPoint[] points, boolean closed) {
        return extrudeRelative(baseAltitude, points, closed, extrudeHeight);
    }

    /**
     * Extrude the given shape using a relative altitude. The base of the shape will be baseAltitude
     * and the top will be baseAltitude + extrudeHeight.
     *
     * @param baseAltitude The altitude of the shape's base.
     * @param points The shape's points.
     * @param pointSize The number of components for the shape's points (should only be 2 or 3).
     * @param closed True if the shape is closed, false if open
     * @param extrudeHeights The height(s) to extrude to. If a single value is
     *                      passed in, the extrusion will be a fixed height
     *                      relative to the points
     * @return The extruded shape.
     */
    public static DoubleBuffer extrudeRelative(double baseAltitude,
            DoubleBuffer points, int pointSize, boolean closed,
            double... extrudeHeights) {

        // Make sure extrude heights match the number of points
        int numPoints = points.limit() / pointSize;
        boolean multiHeight = extrudeHeights.length > 1;
        if (multiHeight && extrudeHeights.length != numPoints)
            throw new IllegalStateException("extrudeHeights ("
                    + extrudeHeights.length
                    + ") must match the number of points (" + numPoints + ")");

        List<Double> vertices = new ArrayList<>();
        // If we find any self intersections then the polygon needs to be decomposed into simple
        // polygons
        if (closed && checkForSelfIntersection(points, pointSize)) {
            Vector2D[] verts = new Vector2D[points.limit() / pointSize];
            int v = 0;
            for (int i = 0; i < points.limit(); i += pointSize) {
                double x = points.get(i);
                double y = points.get(i + 1);
                double alt = pointSize == 3 ? points.get(i + 2) : 0;
                if (multiHeight)
                    verts[v] = new HeightVector2D(x, y, alt, extrudeHeights[v]);
                else
                    verts[v] = new Vector2D(x, y, alt);
                v++;
            }
            List<ArrayList<Double>> polygons = GLTriangulate
                    .extractIntersectingPolygons(verts);
            for (ArrayList<Double> polygon : polygons) {
                LineString ls = new LineString(3);
                int pSize = (multiHeight ? 4 : 3);
                double[] heights = multiHeight
                        ? new double[(polygon.size() / pSize) + 1]
                        : new double[] { extrudeHeights[0] };
                int h = 0;
                for (int i = 0; i < polygon.size(); i += pSize) {
                    ls.addPoint(polygon.get(i), polygon.get(i + 1),
                            pointSize == 3 ? polygon.get(i + 2) : baseAltitude);
                    if (pSize == 4)
                        heights[h++] = polygon.get(i + 3);
                }
                ls.addPoint(polygon.get(0), polygon.get(1), pointSize == 3
                        ? polygon.get(2) : baseAltitude);
                if (pSize == 4)
                    heights[h] = polygon.get(3);

                vertices.addAll(extrudePolygon(new Polygon(ls), true, heights));
            }
        } else {
            LineString ls = new LineString(3);
            for (int i = 0; i < points.limit(); i += pointSize) {
                ls.addPoint(points.get(i), points.get(i + 1),
                        pointSize == 3 ? points.get(i + 2) : baseAltitude);
            }
            if (closed && points.limit() > pointSize) {
                if (points.get(0) != points.get(points.limit() - pointSize)
                        || points.get(1) != points.get(points.limit() - (pointSize - 1))) {
                    ls.addPoint(points.get(0), points.get(1), pointSize == 3
                            ? points.get(2) : baseAltitude);
                    if (multiHeight) {
                        extrudeHeights = Arrays.copyOf(extrudeHeights, numPoints + 1);
                        extrudeHeights[numPoints] = extrudeHeights[0];
                    }
                }
            }

            Polygon polylinePolygon = new Polygon(ls);
            vertices.addAll(extrudePolygon(polylinePolygon, closed,
                    extrudeHeights));
        }
        double[] tmp = new double[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            tmp[i] = vertices.get(i);
        }
        DoubleBuffer extrudedBuffer = Unsafe.allocateDirect(tmp.length,
                DoubleBuffer.class);
        extrudedBuffer.put(tmp);
        return extrudedBuffer;
    }

    /**
     * Extrude the given shape using a relative altitude. The base of the shape will be baseAltitude
     * and the top will be baseAltitude + extrudeHeight.
     *
     * @param baseAltitude The altitude of the shape's base.
     * @param extrudeHeight The height to extrude to.
     * @param points The shape's points.
     * @param pointSize The number of components for the shape's points (should only be 2 or 3).
     * @param closed True if the shape is closed, false if open
     * @return The extruded shape.
     */
    public static DoubleBuffer extrudeRelative(double baseAltitude, double extrudeHeight,
            DoubleBuffer points, int pointSize, boolean closed) {
        return extrudeRelative(baseAltitude, points, pointSize, closed,
                extrudeHeight);
    }

    private static boolean checkForSelfIntersection(DoubleBuffer points, int pointSize) {
        List<Segment> segments = new ArrayList<>();
        // construct our segment list
        for (int i = 0; i < points.limit(); i += pointSize) {
            int start = i;
            int end = (i + pointSize) % points.limit();
            segments.add(new Segment(
                    new Vector2D(points.get(start), points.get(start + 1)),
                    new Vector2D(points.get(end), points.get(end + 1))));
        }
        // check if the segments intersect each other, ignoring the segments that share endpoints
        // since they're guaranteed to intersect
        for (int i = 0; i < segments.size(); i++) {
            Segment first = segments.get(i);
            for (int j = i + 1; j < segments.size() + 1; j++) {
                Segment second = segments.get(j % segments.size());
                if (first.sharesPoint(second)) {
                    continue;
                }
                Vector2D intersection = Vector2D.segmentToSegmentIntersection(first.start,
                        first.end, second.start, second.end);
                if (intersection != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Generate an extruded polygon given the base polygon to extrude off of
     * @param polygon Base polygon
     * @param closed True if the base polygon is closed
     * @param extrudeHeights Array of extrude heights per vertices
     * Passing a single height value will extrude by a fixed height
     * @return List of vertices making up the extruded polygon
     */
    private static List<Double> extrudePolygon(Polygon polygon,
            boolean closed, double... extrudeHeights) {
        ExtrusionHints hint = ExtrusionHints.TEEH_None;
        if (!closed)
            hint = ExtrusionHints.TEEH_OmitTopFace;
        Geometry extrudedPolygon;
        if (extrudeHeights.length == 1)
            extrudedPolygon = GeometryFactory.extrude(polygon,
                    extrudeHeights[0], hint);
        else
            extrudedPolygon = GeometryFactory.extrude(polygon,
                    extrudeHeights, hint);
        List<Double> newPoints = new ArrayList<>();
        // Triangulate the polygons that were returned from the extrude() call
        for (Geometry g : ((GeometryCollection) extrudedPolygon).getGeometries()) {
            triangulatePolygon(newPoints, (Polygon) g);
        }
        return newPoints;
    }

    /**
     * Triangulate the given polygon by either passing it to GLTriangulate and extracting the
     * results into verts or by treating it as a triangle fan if it's a convex polygon.
     *
     * @param verts The ArrayList of vertices.
     * @param polygon The polygon that will be triangulated.
     */
    private static void triangulatePolygon(final List<Double> verts, final Polygon polygon) {
        LineString exterior = polygon.getExteriorRing();
        double[] tmp = new double[exterior.getNumPoints() * 3];
        // Don't add the last vertex since it's really the first vertex again
        for (int i = exterior.getNumPoints() - 1; i > -1; i--) {
            Point p = new Point(0, 0, 0);
            exterior.get(p, i);
            tmp[i * 3] = p.getX();
            tmp[(i * 3) + 1] = p.getY();
            tmp[(i * 3) + 2] = p.getZ();
        }
        DoubleBuffer vertBuffer = DoubleBuffer.wrap(tmp);
        ShortBuffer indexBuffer = ShortBuffer.wrap(new short[(exterior.getNumPoints() - 2) * 3]);
        int triangleType = GLTriangulate.triangulate(vertBuffer, 3, exterior.getNumPoints(),
                indexBuffer);
        if (triangleType == GLTriangulate.TRIANGLE_FAN) {
            for (int i = 1; i < exterior.getNumPoints() - 1; i++) {
                verts.add(tmp[0]);
                verts.add(tmp[1]);
                verts.add(tmp[2]);

                verts.add(tmp[i * 3]);
                verts.add(tmp[(i * 3) + 1]);
                verts.add(tmp[(i * 3) + 2]);

                verts.add(tmp[(i + 1) * 3]);
                verts.add(tmp[((i + 1) * 3) + 1]);
                verts.add(tmp[((i + 1) * 3) + 2]);
            }
        } else {
            for (int i = 0; i < indexBuffer.limit(); i++) {
                short index = indexBuffer.get(i);
                verts.add(tmp[index * 3]);
                verts.add(tmp[(index * 3) + 1]);
                verts.add(tmp[(index * 3) + 2]);
            }
        }
    }

    /**
     * Generates a vertex buffer for line segments (GL_LINES) for the outline of an object.
     *
     * @param baseAltitude Altitude of the lower horizontal outline segments.
     * @param points Input surface level geometry. Altitude is ignored.
     * @param closed True if the outline is closed, false if open
     * @param simplified True to reduce the amount of vertical lines
     * @param extrudeHeights Altitude(s) of the upper horizontal outline segments.
     *                       A single value will extrude by a fixed height.
     * @return line segment buffer with 3 components per vertex.
     */
    public static DoubleBuffer extrudeOutline(double baseAltitude,
            GeoPoint[] points, boolean closed, boolean simplified,
            double... extrudeHeights) {
        int pointSize = Double.isNaN(baseAltitude) ? 3 : 2;
        DoubleBuffer newPoints = DoubleBuffer.allocate(points.length
                * pointSize);
        for (GeoPoint pt : points) {
            newPoints.put(pt.getLongitude());
            newPoints.put(pt.getLatitude());
            if (pointSize == 3)
                newPoints.put(pt.getAltitude());
        }
        return extrudeOutline(baseAltitude, newPoints, pointSize,
                closed, simplified, extrudeHeights);
    }

    /**
     * Generates a vertex buffer for line segments (GL_LINES) for the outline of an object.
     *
     * @param baseAltitude Altitude of the lower horizontal outline segments.
     * @param extrudeHeight Altitude of the upper horizontal outline segments.
     * @param points Input surface level geometry. Altitude is ignored.
     * @param closed True if the outline is closed, false if open
     * @param simplified True to reduce the amount of vertical lines
     * @return line segment buffer with 3 components per vertex.
     */
    public static DoubleBuffer extrudeOutline(double baseAltitude, double extrudeHeight,
            GeoPoint[] points, boolean closed, boolean simplified) {
        return extrudeOutline(baseAltitude, points, closed, simplified,
                extrudeHeight);
    }

    /**
     * Generates a vertex buffer for line segments (GL_LINES) for the outline of an object.
     *
     * @param baseAltitude Altitude of the lower horizontal outline segments.
     * @param points Input surface level geometry.
     * @param pointSize Components per vertex in points. At least 2, altitude is ignored
     * @param closed True if the outline is closed, false if open
     * @param simplified True to reduce the amount of vertical lines
     * @param extrudeHeights Altitude of the upper horizontal outline segments.
     *                       A single value will extrude by a fixed height
     * @return line segment buffer with 3 components per vertex.
     */
    public static DoubleBuffer extrudeOutline(double baseAltitude,
            DoubleBuffer points, int pointSize, boolean closed,
            boolean simplified, double... extrudeHeights) {

        int limit = points.limit();

        // Make sure extrude heights match the number of input points
        int numPoints = limit / pointSize;
        if (extrudeHeights.length > 1 && extrudeHeights.length != numPoints)
            throw new IllegalStateException("extrudeHeights ("
                    + extrudeHeights.length
                    + ") must match the number of points (" + numPoints + ")");

        if(points.get(0) == points.get(limit - pointSize)
                && points.get(1) == points.get(limit - pointSize + 1)) {
            // ignore last point since it is a duplicate, don't need to generate extra segments
            limit -= pointSize;
            closed = true;
        }

        // Each segment is 2 points, each point maps to 1 vertical segment and 1 horizontal segment
        numPoints = limit / pointSize;
        int size = numPoints; // Horizontal top segments
        if (!closed)
            size--; // Exclude segment connecting last point to first
        size += simplified ? (closed ? 0 : 2) : numPoints; // Vertical segments
        size *= 6; // Multiply by point size (3) * 2 points per segment
        DoubleBuffer outlineBuffer = Unsafe.allocateDirect(size,
                DoubleBuffer.class);

        // generate horizontal segments
        int lim = limit - (closed ? 0 : pointSize);
        int h = 0;
        for(int i = 0; i < lim; i += pointSize) {
            double extrudeHeight = extrudeHeights.length == 1
                    ? extrudeHeights[0] : extrudeHeights[h];

            outlineBuffer.put(points.get(i));
            outlineBuffer.put(points.get(i + 1));
            outlineBuffer.put(extrudeHeight + (pointSize == 3
                    ? points.get(i + 2) : baseAltitude));

            int nextIdx = (i + pointSize) % limit;

            if (extrudeHeights.length > 1)
                extrudeHeight = extrudeHeights[nextIdx == 0 ? 0 : h + 1];

            outlineBuffer.put(points.get(nextIdx));
            outlineBuffer.put(points.get(nextIdx + 1));
            outlineBuffer.put(extrudeHeight + (pointSize == 3
                    ? points.get(nextIdx + 2) : baseAltitude));

            h++;
        }

        // generate vertical segments
        h = 0;
        for(int i = 0; i < limit; i += pointSize) {
            double extrudeHeight = extrudeHeights.length == 1
                    ? extrudeHeights[0] : extrudeHeights[h++];

            // Skip intermediate segments
            if (simplified && (closed || i > 0 && i < limit - pointSize))
                continue;

            double alt = pointSize == 3 ? points.get(i + 2) : baseAltitude;

            outlineBuffer.put(points.get(i));
            outlineBuffer.put(points.get(i + 1));
            outlineBuffer.put(alt);

            outlineBuffer.put(points.get(i));
            outlineBuffer.put(points.get(i + 1));
            outlineBuffer.put(alt + extrudeHeight);
        }

        return outlineBuffer;
    }

    /**
     * Generates a vertex buffer for line segments (GL_LINES) for the outline of an object.
     *
     * @param baseAltitude Altitude of the lower horizontal outline segments.
     * @param extrudeHeight Altitude of the upper horizontal outline segments.
     * @param points Input surface level geometry.
     * @param pointSize Components per vertex in points. At least 2, altitude is ignored
     * @param closed True if the outline is closed, false if open
     * @param simplified True to reduce the amount of vertical lines
     * @return line segment buffer with 3 components per vertex.
     */
    public static DoubleBuffer extrudeOutline(double baseAltitude,
            double extrudeHeight, DoubleBuffer points, int pointSize,
            boolean closed, boolean simplified) {
        return extrudeOutline(baseAltitude, points, pointSize, closed,
                simplified, extrudeHeight);
    }
}
