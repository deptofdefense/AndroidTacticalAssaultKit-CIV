
package com.atakmap.android.drawing.mapItems;

import android.graphics.Color;

import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.hierarchy.filters.FOVFilter;
import com.atakmap.android.importexport.handlers.ParentMapItem;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.preference.UnitPreferences;
import com.atakmap.coremap.conversions.AngleUtilities;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLTriangulate;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Minimum safe distance ring around a shape
 */
public class MsdShape extends Polyline implements
        MapItem.OnGroupChangedListener,
        Shape.OnPointsChangedListener,
        Shape.OnStyleChangedListener,
        MapItem.OnVisibleChangedListener {

    private static final int CIRCLE_RES = 16;
    private static final float CIRCLE_STEP = 360f / CIRCLE_RES;

    @NonNull
    private final UnitPreferences _prefs;

    @NonNull
    private final Shape _shape;

    private double _range;

    public MsdShape(@NonNull MapView mapView, @NonNull Shape shape) {
        super(shape.getUID() + ".msd");
        _prefs = new UnitPreferences(mapView);
        _shape = shape;
        setMetaBoolean("addToObjList", false);
        setMetaBoolean("nevercot", true);
        setMetaString("shapeUID", shape.getUID());
        setClickable(false);
        setStrokeColor(Color.RED);
        setStrokeWeight(3d);
        setStyle(STYLE_STROKE_MASK | STYLE_CLOSED_MASK);
        setBasicLineStyle(BASIC_LINE_STYLE_OUTLINED);
    }

    /**
     * Set the minimum safe distance
     * @param range Range in meters
     */
    public void setRange(double range) {
        if (Double.compare(_range, range) != 0.0) {
            _range = range;
            recalculate();
        }
    }

    /**
     * Get the range of the boundary
     * @return Range in meters
     */
    public double getRange() {
        return _range;
    }

    /**
     * Add this MSD boundary shape to a map group related to the parent shape
     * This is either the same group as the parent shape or its child group
     * if one exists.
     */
    public void addToShapeGroup() {
        if (getGroup() != null)
            return;

        // Find a suitable group for this item
        MapGroup group = _shape.getGroup();
        if (_shape instanceof ParentMapItem) {
            MapGroup cGroup = ((ParentMapItem) _shape).getChildMapGroup();
            if (cGroup != null)
                group = cGroup;
        }

        if (group != null)
            group.addItem(this);
    }

    @Override
    public void onAdded(MapGroup parent) {
        super.onAdded(parent);
        _shape.addOnGroupChangedListener(this);
        _shape.addOnPointsChangedListener(this);
        _shape.addOnStyleChangedListener(this);
        _shape.addOnVisibleChangedListener(this);
        recalculate();
    }

    @Override
    public void onRemoved(MapGroup parent) {
        super.onRemoved(parent);
        _shape.removeOnGroupChangedListener(this);
        _shape.removeOnPointsChangedListener(this);
        _shape.removeOnStyleChangedListener(this);
        _shape.removeOnVisibleChangedListener(this);
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        // Remove this item when the shape has been removed
        if (item == _shape)
            removeFromGroup();
    }

    @Override
    public void onPointsChanged(Shape s) {
        recalculate();
    }

    @Override
    public void onVisibleChanged(MapItem item) {
        setVisible(item.getVisible());
    }

    @Override
    public void onStyleChanged(Shape s) {
        recalculate();
    }

    /**
     * Recalculate the MSD shape
     */
    private void recalculate() {
        if (getGroup() == null)
            return;

        GeoPoint[] points = _shape.getPoints();

        // Only the first 4 points of the rectangle is used
        if (_shape instanceof Rectangle) {
            points = new GeoPoint[] {
                    points[0], points[1], points[2], points[3]
            };
        }

        int len = points.length;

        // Invalid shape
        if (len < 2)
            return;

        // Just use the shape's points
        if (_range <= 0) {
            toggleMetaData("labels_on", false);
            setLineLabel("");
            setPoints(points);
            return;
        }

        // Determine if this shape is closed
        boolean closed = MathUtils.hasBits(_shape.getStyle(),
                Polyline.STYLE_CLOSED_MASK);
        if (points[0].equals(points[len - 1])) {
            closed = true;
            len--; // Ignore redundant last point
        }

        // Number of edges
        final int edges = closed ? len : len - 1;

        // Determine if the shape winding is clockwise or counter-clockwise
        // Also save each bearing for later
        // We only care about this for closed shapes
        double sum = 0;
        LineBearings lb = new LineBearings(edges);
        for (int i = 0; i < edges; i++) {
            final boolean lastPt = i == len - 1;
            final GeoPoint p1 = points[i];
            final GeoPoint p2 = points[lastPt ? 0 : i + 1];
            lb.bearings[i] = p1.bearingTo(p2);
            sum += (p2.getLongitude() - p1.getLongitude())
                    * (p2.getLatitude() + p1.getLatitude());
        }
        boolean clockwise = sum >= 0;

        // Intersection testing
        GeoPoint firstO1 = null, lastO1 = null;
        GeoPoint firstO2 = null, lastO2 = null;
        boolean lastIntersects = false;

        // Extrude shape outward
        int i = 0;
        int dir = 1;
        List<GeoPoint> pts = new ArrayList<>(edges * 2);
        while (i < len && i >= 0) {

            // Flip direction when we hit the end of the line on an open shape
            if (i == len - 1 && !closed)
                dir = -1;

            // Finished with open shape
            if (dir == -1 && i == 0)
                break;

            // Last point in the shape
            final int next = i == len - 1 && closed ? 0 : i + dir;

            // Get the points that make up this edge
            final GeoPoint p1 = points[i];
            final GeoPoint p2 = points[next];

            // Calculate edge bearing
            double b1 = lb.getBearing(i, dir);
            double b2 = lb.getBearing(next, dir);

            // Calculate outer boundary edge
            final double bOut = b1 + (clockwise ? -90 : 90);
            GeoPoint o1 = GeoCalculations.pointAtDistance(p1, bOut, _range);
            GeoPoint o2 = GeoCalculations.pointAtDistance(p2, bOut, _range);

            // Check if the current edge convergence is clockwise
            double bDiff = b2 - b1;
            if (bDiff < 0)
                bDiff += 360;
            boolean bClockwise = bDiff < 180;
            boolean nextIntersects = Math.abs(bDiff - 180) > 1e-6
                    && clockwise != bClockwise;

            // Check if the last edge is intersecting this edge
            if (lastIntersects) {
                GeoPoint inter = getIntersection(lastO1, lastO2, o1, o2);
                if (inter != null)
                    pts.add(inter);
                else {
                    // Not actually intersecting
                    pts.add(lastO2);
                    lastIntersects = false;
                }
            }

            // Check if the first edge is intersecting this edge
            if (nextIntersects && next == 0) {
                GeoPoint inter = getIntersection(o1, o2, firstO1, firstO2);
                if (inter != null)
                    pts.set(0, inter);
                else
                    nextIntersects = false; // Not actually intersecting
            }

            // If the last edge isn't intersecting this edge then add the
            // first point of the current edge
            if (!lastIntersects)
                pts.add(o1);

            // If the next edge isn't intersecting this edge then add the
            // second point of the current edge
            if (!nextIntersects)
                pts.add(o2);

            // Create an arc leading from the edge of the first boundary
            // edge to the next boundary edge
            if (!nextIntersects) {
                final double bEnd = b2 + (clockwise ? -90 : 90);
                addArc(p2, bOut, bEnd, clockwise, pts);
            }

            lastIntersects = nextIntersects;

            // Remember the first edge in case an intersection between the
            // last boundary edge and first boundary edge needs to be resolved
            if (i == 0) {
                firstO1 = o1;
                firstO2 = o2;
            }

            // Remember the last edge
            lastO1 = o1;
            lastO2 = o2;
            i += dir;
        }

        // We may need to remove self-intersecting pieces from the outer bounds
        setPoints(getOuterPolygon(pts.toArray(new GeoPoint[0])));

        // Set line label showing range
        toggleMetaData("labels_on", true);
        setLineLabel(SpanUtilities.formatType(_prefs.getRangeSystem(),
                _range, Span.METER));
    }

    /**
     * Add an arc to connect the ends of 2 lines
     * @param pt Start point
     * @param b1 Start angle
     * @param b2 End angle
     * @param clockwise True if clockwise
     * @param pts Points list to add to
     */
    private void addArc(final GeoPoint pt,
            double b1,
            double b2,
            final boolean clockwise,
            final List<GeoPoint> pts) {

        if (clockwise && b1 > b2)
            b2 += 360;
        else if (!clockwise && b1 < b2)
            b2 -= 360;

        int s1 = getCircleStep(b1);
        int s2 = getCircleStep(b2);

        if (clockwise) {
            if (s1 * CIRCLE_STEP <= b1)
                s1++;
            if (s2 * CIRCLE_STEP >= b2)
                s2--;
        } else {
            if (s1 * CIRCLE_STEP >= b1)
                s1--;
            if (s2 * CIRCLE_STEP <= b2)
                s2++;
        }

        int s = s1;
        int dir = clockwise ? 1 : -1;
        while (clockwise && s <= s2 || !clockwise && s >= s2) {
            double b = s * CIRCLE_STEP;
            pts.add(GeoCalculations.pointAtDistance(pt, b, _range));
            s += dir;
        }
    }

    /**
     * Convert a geo point to a 2D vector
     * @param p Point
     * @return Vector
     */
    private static Vector2D toVec(GeoPoint p) {
        return FOVFilter.geo2Vector(p);
    }

    /**
     * Get a piece of the circle based on a resolution
     * @param angle Angle
     * @return Circle step
     */
    private static int getCircleStep(double angle) {
        return (int) Math.round((angle / 360) * CIRCLE_RES);
    }

    /**
     * Get the intersection between 2 lines
     * @param p00 Line 1 start point
     * @param p01 Line 1 end point
     * @param p10 Line 2 start point
     * @param p11 Line 2 end point
     * @return Intersection point or null if not intersecting
     */
    @Nullable
    private static GeoPoint getIntersection(GeoPoint p00, GeoPoint p01,
            GeoPoint p10, GeoPoint p11) {
        if (p00 == null || p01 == null || p10 == null || p11 == null)
            return null;
        Vector2D seg00 = toVec(p00);
        Vector2D seg01 = toVec(p01);
        Vector2D seg10 = toVec(p10);
        Vector2D seg11 = toVec(p11);
        Vector2D intersection = Vector2D.segmentToSegmentIntersection(
                seg10, seg11, seg01, seg00);
        if (intersection == null)
            return null;
        return new GeoPoint(intersection.y, intersection.x, intersection.alt);
    }

    /**
     * Extract the outer shape from a self-intersecting polygon
     *
     * If the polygon is not self-intersecting then this will return the input
     * array of points.
     *
     * @param pts Array of points forming a closed polygon
     * @return Array of points for the outer polygon
     */
    private static GeoPoint[] getOuterPolygon(GeoPoint[] pts) {

        // Convert list of points to vectors
        Vector2D[] verts = new Vector2D[pts.length];
        for (int i = 0; i < pts.length; i++)
            verts[i] = toVec(pts[i]);

        // Extract individual polygons from an intersecting one
        ArrayList<ArrayList<Double>> polygons = GLTriangulate
                .extractIntersectingPolygons(verts);

        // If there's only one result then the shape is already normal
        if (polygons.size() < 2)
            return pts;

        // If more than one polygon was returned then the shape has
        // self-intersections

        // Find the enclosing polygon with the largest area
        ArrayList<Double> polygon = polygons.get(0);
        double maxArea = 0;
        for (ArrayList<Double> p : polygons) {

            // Calculate the area of the polygon using Gauss's area formula
            final int numPts = p.size() / 3;
            double area = 0;
            for (int i = 0; i < numPts; i++) {
                int cur = i * 3;
                int next = (i == numPts - 1 ? 0 : i + 1) * 3;
                double lng1 = p.get(cur);
                double lat1 = p.get(cur + 1);
                double lng2 = p.get(next);
                double lat2 = p.get(next + 1);
                area += lng1 * lat2 - lng2 * lat1;
            }
            area = Math.abs(area) / 2;

            // Track the polygon with the largest area
            if (area > maxArea) {
                maxArea = area;
                polygon = p;
            }
        }

        // Copy polygon coordinates back into the shape
        GeoPoint[] ret = new GeoPoint[polygon.size() / 3];
        int p = 0;
        for (int i = 0; i < polygon.size(); i += 3) {
            double x = polygon.get(i);
            double y = polygon.get(i + 1);
            double z = polygon.get(i + 2);
            ret[p++] = new GeoPoint(y, x, z);
        }

        return ret;
    }

    /**
     * Helper class for reading line bearings in both directions of the
     * {@link #recalculate()} loop
     */
    private static class LineBearings {

        private final double[] bearings;

        LineBearings(int size) {
            this.bearings = new double[size];
        }

        /**
         * Get the bearing for a given line starting at index
         * @param index Index of the point to start at
         * @param dir 1 = bearing from index to next index
         *            -1 = bearing from index to previous index
         * @return Bearing in degrees
         */
        double getBearing(int index, int dir) {
            if (dir == 1 && index >= bearings.length) {
                index = bearings.length;
                dir = -1;
            } else if (dir == -1 && index <= 0) {
                index = 0;
                dir = 1;
            }
            if (dir == 1)
                return bearings[index];
            else
                return AngleUtilities.wrapDeg(bearings[index - 1] + 180);
        }
    }
}
