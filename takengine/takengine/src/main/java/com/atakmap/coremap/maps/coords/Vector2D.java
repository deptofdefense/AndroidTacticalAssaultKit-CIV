
package com.atakmap.coremap.maps.coords;

import android.graphics.RectF;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Vector2D {

    private static final int INSIDE = 0;
    private static final int LEFT = 1;
    private static final int RIGHT = 2;
    private static final int BOTTOM = 4;
    private static final int TOP = 8;

    public double x;
    public double y;
    public double alt;

    public Vector2D() {
        alt = x = y = 0;
    }

    public Vector2D(final double x, final double y) {
        this.x = x;
        this.y = y;
        this.alt = 0d;
    }

    public Vector2D(final double x, final double y, final double alt) {
        this.x = x;
        this.y = y;
        this.alt = alt;
    }

    public void set(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double dot(Vector2D v) {
        return this.x * v.x + this.y * v.y;
    }

    public Vector2D cross() {
        return new Vector2D(-this.y, this.x);
    }

    public double cross(Vector2D v) {
        return this.x * v.y - this.y * v.x;
    }

    public double determinant(Vector2D v0, Vector2D v1) {
        return this.x * v0.y + v0.x * v1.y + v1.x * this.y - v1.x * v0.y - v0.x
                * this.y - this.x
                        * v1.y;
    }

    public Vector2D scale(double s) {
        return new Vector2D(this.x * s, this.y * s);
    }

    public Vector2D normalize() {
        double length = Math.sqrt(this.x * this.x + this.y * this.y);
        double div_l = 1d / length;
        return new Vector2D(this.x * div_l, this.y * div_l);
    }

    public double distance(Vector2D v) {
        return Math
                .sqrt((this.x - v.x) * (this.x - v.x) + (this.y - v.y)
                        * (this.y - v.y));
    }

    public double distanceSq(Vector2D v) {
        return (this.x - v.x) * (this.x - v.x) + (this.y - v.y)
                * (this.y - v.y);
    }

    public Vector2D add(Vector2D v) {
        return new Vector2D(this.x + v.x, this.y + v.y, Math.max(this.alt,
                v.alt));
    }

    public Vector2D subtract(Vector2D v) {
        return new Vector2D(this.x - v.x, this.y - v.y, Math.max(this.alt,
                v.alt));
    }

    public double mag() {
        return Math.sqrt(this.x * this.x + this.y * this.y);
    }

    public double angle(Vector2D v) {
        double dot = this.dot(v);
        double magV = v.mag();
        double mag = this.mag();
        return Math.acos(dot / magV / mag);
    }

    public static Vector2D nearestPointOnSegment(Vector2D point, Vector2D seg0,
            Vector2D seg1) {
        Vector2D v = seg1.subtract(seg0);
        Vector2D w = point.subtract(seg0);
        double c1 = w.dot(v);
        if (c1 <= 0d)
            return seg0;
        double c2 = v.dot(v);
        if (c2 <= c1)
            return seg1;
        double b = c1 / c2;
        return new Vector2D(seg0.x + b * v.x, seg0.y + b * v.y);
    }

    public static Vector2D segmentToSegmentIntersection(Vector2D seg10,
            Vector2D seg11,
            Vector2D seg01, Vector2D seg00) {
        Vector2D s0 = seg01.subtract(seg00);
        Vector2D s1 = seg11.subtract(seg10);
        double c1 = s1.cross(s0);
        if (c1 != 0d) {
            double t = seg00.subtract(seg10).cross(s0) / c1;
            double u = seg00.subtract(seg10).cross(s1) / c1;
            if ((t >= 0 && t <= 1) && (u >= 0 && u <= 1)) {
                return seg00.add(s0.scale(u));
            }
        }
        return null;
    }

    /**
     * Determines the intersection point of a line and a line segment
     * @param line0 Any point on the line
     * @param line1 Any other point on the line
     * @param seg0 The start of the line segment
     * @param seg1 The end of the line segment
     * @return The intersection point, or null if they do not intersect
     */
    public static Vector2D segmentToLineIntersection(Vector2D line0,
            Vector2D line1,
            Vector2D seg0, Vector2D seg1) {
        double a1 = line1.y - line0.y;
        double b1 = line0.x - line1.x;
        double c1 = a1 * line0.x + b1 * line0.y;

        double a2 = seg1.y - seg0.y;
        double b2 = seg0.x - seg1.x;
        double c2 = a2 * seg0.x + b2 * seg0.y;

        double det = a1 * b2 - a2 * b1;
        if (det != 0) {
            return new Vector2D((b2 * c1 - b1 * c2) / det, (a1 * c2 - a2 * c1)
                    / det);
        }
        return null;
    }

    public static Vector2D rayToSegmentIntersection(Vector2D p,
            Vector2D direction, Vector2D seg1,
            Vector2D seg0) {
        Vector2D s = seg1.subtract(seg0);
        double c1 = direction.cross(s);
        if (c1 == 0d) {
            return null; // The segment is parallel to the ray
        } else {
            double u = seg0.subtract(p).cross(direction) / c1;
            return seg0.add(s.scale(u));
        }
    }

    public static Vector2D rayToRayIntersection(Vector2D p0, Vector2D dir0,
            Vector2D p1,
            Vector2D dir1) {
        Vector2D s = dir0.subtract(p0);
        double c = s.cross(dir1);
        if (c == 0d) {
            return null; // The segment is parallel to the ray
        } else {
            double u = p1.subtract(p0).cross(s) / c;
            // The ray intersects the segment!
            return p1.add(dir1.scale(u));
        }
    }

    public static boolean polygonContainsPoint(Vector2D point,
            Vector2D[] polygon) {
        int i, j;
        boolean result = false;
        for (i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            if ((polygon[i].y > point.y) != (polygon[j].y > point.y)
                    &&
                    (point.x < (polygon[j].x - polygon[i].x)
                            * (point.y - polygon[i].y)
                            / (polygon[j].y - polygon[i].y) + polygon[i].x)) {
                result = !result;
            }
        }
        return result;
    }

    public static boolean segmentIntersectsOrContainedByPolygon(Vector2D seg1,
            Vector2D seg0,
            Vector2D[] polygon) {
        // Uses the Polygon Clipping Algorithm
        boolean[] segVertex = new boolean[polygon.length - 1];
        boolean[] intersectVertex = new boolean[polygon.length - 1];
        boolean[] sideSeg0 = new boolean[polygon.length - 1];
        boolean[] sideSeg1 = new boolean[polygon.length - 1];

        for (int i = 0; i < polygon.length - 1; i++) {
            double detVertex = polygon[i].determinant(seg0, seg1);
            double detSeg0 = seg0.determinant(polygon[i], polygon[i + 1]);
            double detSeg1 = seg1.determinant(polygon[i], polygon[i + 1]);
            segVertex[i] = detVertex > 0;
            intersectVertex[i] = detVertex == 0;
            sideSeg0[i] = detSeg0 > 0;
            sideSeg1[i] = detSeg1 > 0;
        }
        // If either point is inside the polygon then return true
        if (_areAllSame(sideSeg0) || _areAllSame(sideSeg1)) {
            return true;
        }
        // If there is an intersection with a vertex then return true
        if (!_areAllFalse(intersectVertex)) {
            return true;
        }
        ArrayList<Integer> intersect = _signChange(segVertex);
        if (!intersect.isEmpty()) {
            for (int i : intersect) {
                if ((sideSeg0[i] == sideSeg1[i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public static boolean segmentArraysIntersect(Vector2D[] segs1,
            Vector2D[] segs2) {
        Vector2D s0 = new Vector2D(), s1 = new Vector2D(), s2 = new Vector2D();
        for (int i = 0; i < segs1.length - 1; i++) {
            for (int j = 0; j < segs2.length - 1; j++) {
                s0.set(segs2[j].x - segs2[j + 1].x,
                        segs2[j].y - segs2[j + 1].y);
                s1.set(segs1[i + 1].x - segs1[i].x,
                        segs1[i + 1].y - segs1[i].y);
                double c1 = s1.cross(s0);
                if (c1 != 0d) {
                    s2.set(segs2[j + 1].x - segs1[i].x,
                            segs2[j + 1].y - segs1[i].y);
                    double t = s2.cross(s0) / c1;
                    double u = s2.cross(s1) / c1;
                    if (t >= 0 && t <= 1 && u >= 0 && u <= 1)
                        return true;
                }
            }
        }
        return false;
    }

    public static boolean segmentArrayIntersectsOrContainedByPolygon(
            Vector2D[] segments,
            Vector2D[] polygon) {
        for (int i = 0; i < segments.length - 1; i++) {
            if (segmentIntersectsOrContainedByPolygon(segments[i],
                    segments[i + 1], polygon))
                return true;
        }
        return false;
    }

    public static ArrayList<Vector2D> segmentArrayIntersectionsWithPolygon(
            Vector2D[] segments,
            Vector2D[] polygon) {
        ArrayList<Vector2D> intersections = new ArrayList<>();
        for (int i = 0; i < segments.length - 1; i++) {
            intersections.addAll(segmentIntersectionsWithPolygon(segments[i],
                    segments[i + 1],
                    polygon));
        }
        return intersections;
    }

    public static ArrayList<Vector2D> segmentIntersectionsWithPolygon(
            Vector2D seg1, Vector2D seg0,
            Vector2D[] polygon) {
        ArrayList<Vector2D> intersections = new ArrayList<>();
        // Uses the Polygon Clipping Algorithm
        boolean[] segVertex = new boolean[polygon.length - 1];
        boolean[] intersectVertex = new boolean[polygon.length - 1];
        boolean[] sideSeg0 = new boolean[polygon.length - 1];
        boolean[] sideSeg1 = new boolean[polygon.length - 1];

        for (int i = 0; i < polygon.length - 1; i++) {
            double detVertex = polygon[i].determinant(seg0, seg1);
            double detSeg0 = seg0.determinant(polygon[i], polygon[i + 1]);
            double detSeg1 = seg1.determinant(polygon[i], polygon[i + 1]);
            segVertex[i] = detVertex > 0;
            intersectVertex[i] = detVertex == 0;
            sideSeg0[i] = detSeg0 > 0;
            sideSeg1[i] = detSeg1 > 0;
        }
        // If either point is inside the polygon then add that point to the arraylist
        if (_areAllSame(sideSeg0)) {
            intersections.add(seg0);
        }
        if (_areAllSame(sideSeg1)) {
            intersections.add(seg1);
        }
        for (int i = 0; i < intersectVertex.length; i++) {
            if (intersectVertex[i]) {
                intersections.add(polygon[i]);
            }
        }
        ArrayList<Integer> intersect = _validSignChange(segVertex,
                intersectVertex);
        // There is possibly an intersection
        if (!intersect.isEmpty()) {
            int p = 0;
            int invalid = 0;
            for (int i : intersect) {
                // Only one point can be on both sides, this is the point that is inside
                // If more than that then something weird happened
                if ((sideSeg0[i] == sideSeg1[i])) {
                    p++;
                    invalid = i;
                }
            }
            // There is a point inside
            if ((_areAllSame(sideSeg0) || _areAllSame(sideSeg1)) && p == 1) {
                intersect.remove((Integer) invalid);
                for (int i : intersect) {
                    Vector2D point = Vector2D.segmentToSegmentIntersection(
                            polygon[i],
                            polygon[i + 1], seg1, seg0);
                    if (point != null)
                        intersections.add(point);
                }
            } else if (p == 0) {
                for (int i : intersect) {
                    Vector2D point = Vector2D.segmentToSegmentIntersection(
                            polygon[i],
                            polygon[i + 1], seg1, seg0);
                    if (point != null)
                        intersections.add(point);
                }
            }
        }
        return intersections;
    }

    /**
     * Clips a polygon using the Sutherlan-Hodgman algorithm. This algorithm favors speed over
     * accuracy, so there are some cases this algorithm is not suited for (For example, if clipping
     * a concave polygon would result in multiple output polygons).
     * @param clipRegionCCW The clip region. MUST be convex. MUST have counter-clockwise vertex order.
     * @param polyToClip The polygon to clip.
     * @param dupeRadius If multiple consecutive points in the output polygon are within this distance of eachother, consider them to be duplicates and remove the extra ones. Set to <= 0 to skip this check
     * @return The clipped polygon
     */
    public static Vector2D[] clipPolygonSutherlandHodgman(
            Vector2D[] clipRegionCCW, Vector2D[] polyToClip,
            double dupeRadius) {
        if (clipRegionCCW.length < 3) {
            throw new java.lang.IllegalArgumentException(
                    "clipRegion must have at least 3 points");
        }
        if (polyToClip.length < 3) {
            throw new java.lang.IllegalArgumentException(
                    "polyToClip must have at least 3 points");
        }
        ArrayList<Vector2D> out = new ArrayList<>(
                Arrays.asList(polyToClip));
        Vector2D es = clipRegionCCW[clipRegionCCW.length - 1];
        for (Vector2D ed : clipRegionCCW) {
            ArrayList<Vector2D> in = new ArrayList<>(out);
            out.clear();
            if (in.size() > 2) {
                Vector2D ps = in.get(in.size() - 1);
                for (int j = 0; j < in.size(); j++) {
                    Vector2D pd = in.get(j);
                    boolean pdInside = (es.x - pd.x)
                            * (ed.y - pd.y) > (es.y - pd.y)
                                    * (ed.x - pd.x);
                    boolean psInside = (es.x - ps.x)
                            * (ed.y - ps.y) > (es.y - ps.y)
                                    * (ed.x - ps.x);
                    if (pdInside) {
                        if (!psInside) {
                            out.add(Vector2D.segmentToLineIntersection(es, ed,
                                    ps, pd));
                        }
                        out.add(pd);
                    } else if (psInside) {
                        out.add(Vector2D.segmentToLineIntersection(es, ed, ps,
                                pd));
                    }
                    ps = pd;
                }
                es = ed;
            } else {
                break;
            }
        }
        if (dupeRadius > 0) {
            for (int i = 0; i < out.size() - 1; i++) {
                Vector2D c = out.get(i);
                Vector2D n = out.get(i + 1);
                if (Math.abs(c.x - n.x) < dupeRadius
                        && Math.abs(c.y - n.y) < dupeRadius) {
                    //Remove duplicates
                    out.remove(i);
                }
            }
        }
        return out.toArray(new Vector2D[0]);
    }

    private static int getPointRegion(RectF rect, float x, float y) {
        int rv = INSIDE;
        if (x < rect.left) {
            rv |= LEFT;
        } else if (x > rect.right) {
            rv |= RIGHT;
        }
        if (y > rect.top) {
            rv |= TOP;
        } else if (y < rect.bottom) {
            rv |= BOTTOM;
        }
        return rv;
    }

    /**
     * Clips a polyline against a rectangle using the Cohen-Sutherland algorithm.
     * @param rect The rectangle to clip against.
     * @param polyline The polyline to clip.
     * @return The clipped polyline(s).
     */
    public static List<List<Vector2D>> clipPolylineCohenSutherland(RectF rect,
            FloatBuffer polyline) {
        return clipPolylineCohenSutherland(rect, polyline, 2);
    }

    public static List<List<Vector2D>> clipPolylineCohenSutherland(RectF rect,
            FloatBuffer polyline, int size) {
        final int pos = polyline.position();
        // XXX - use of rewind here looks VERY problematic
        polyline.rewind();
        ArrayList<List<Vector2D>> rv = new ArrayList<>(
                polyline.remaining() >> 1);//Worst case scenario is each segment is visible but each vertex is not
        boolean add;
        int cq, cd, cs;
        float xs, ys, xd, yd, xq, yq, xr, yr;
        xs = polyline.get();
        ys = polyline.get();
        cs = getPointRegion(rect, xs, ys);

        ArrayList<Vector2D> out = new ArrayList<>(
                polyline.remaining() >> 1);

        while (polyline.remaining() > 1) {
            xd = polyline.get();
            yd = polyline.get();
            // skip the non-XY vertex components
            if (size > 2)
                polyline.position(polyline.position() + (size - 2));

            cd = getPointRegion(rect, xd, yd);

            if ((cd | cs) == INSIDE) {
                out.add(new Vector2D(xs, ys));
            } else if ((cd & cs) != INSIDE) {
                //Intentionally blank
            } else {
                cq = cs;
                xq = xs;
                yq = ys;
                while (cq != INSIDE) {
                    if ((cq & TOP) == TOP) {
                        xq = xq + (xd - xq) * (rect.top - yq) / (yd - yq);
                        yq = rect.top;
                    } else if ((cq & BOTTOM) == BOTTOM) {
                        xq = xq + (xd - xq) * (rect.bottom - yq) / (yd - yq);
                        yq = rect.bottom;
                    } else if ((cq & LEFT) == LEFT) {
                        yq = yq + (yd - yq) * (rect.left - xq) / (xd - xq);
                        xq = rect.left;
                    } else if ((cq & RIGHT) == RIGHT) {
                        yq = yq + (yd - yq) * (rect.right - xq) / (xd - xq);
                        xq = rect.right;
                    }

                    cq = getPointRegion(rect, xq, yq);
                    if ((cd & cq) != INSIDE) {
                        break;
                    }
                }
                if (cq == INSIDE) {
                    out.add(new Vector2D(xq, yq));
                    cq = cd;
                    xr = xd;
                    yr = yd;
                    add = false;
                    while (cq != INSIDE) {
                        add = true;
                        if ((cq & TOP) == TOP) {
                            xr = xq + (xr - xq) * (rect.top - yq) / (yr - yq);
                            yr = rect.top;
                        } else if ((cq & BOTTOM) == BOTTOM) {
                            xr = xq + (xr - xq) * (rect.bottom - yq)
                                    / (yr - yq);
                            yr = rect.bottom;
                        } else if ((cq & LEFT) == LEFT) {
                            yr = yq + (yr - yq) * (rect.left - xq) / (xr - xq);
                            xr = rect.left;
                        } else if ((cq & RIGHT) == RIGHT) {
                            yr = yq + (yr - yq) * (rect.right - xq) / (xr - xq);
                            xr = rect.right;
                        }

                        cq = getPointRegion(rect, xr, yr);
                        if ((cq & cs) != INSIDE) {
                            add = false;
                            break;
                        }
                    }
                    if (add) {
                        out.add(new Vector2D(xr, yr));
                        rv.add(out);
                        out = new ArrayList<>(
                                polyline.remaining() >> 1);
                    }
                }
            }
            cs = cd;
            xs = xd;
            ys = yd;
        }

        if (out.size() > 0 && getPointRegion(rect, xs, ys) == INSIDE) {
            out.add(new Vector2D(xs, ys));
            rv.add(out);
        }

        polyline.position(pos);
        return rv;
    }

    // Needed these boolean array functions, couldn't find anything like them in Boolean or
    // BooleanUtils classes
    private static boolean _areAllSame(boolean[] array) {
        boolean first = array[0];
        for (boolean b : array) {
            if (first != b)
                return false;
        }
        return true;
    }

    private static boolean _areAllFalse(boolean[] array) {
        for (boolean b : array) {
            if (b)
                return false;
        }
        return true;
    }

    private static boolean _areAllTrue(boolean[] array) {
        for (boolean b : array) {
            if (!b)
                return false;
        }
        return true;
    }

    private static ArrayList<Integer> _signChange(boolean[] array) {
        ArrayList<Integer> changed = new ArrayList<>();
        for (int i = 0; i < array.length; i++) {
            int j = (i == array.length - 1) ? 0 : i + 1;
            if (array[i] != array[j]) {
                changed.add(i);
            }
        }
        return changed;
    }

    private static ArrayList<Integer> _validSignChange(boolean[] array,
            boolean[] arrayV) {
        ArrayList<Integer> changed = new ArrayList<>();
        if (_areAllFalse(arrayV)) { // Don't worry about a false positive
            for (int i = 0; i < array.length; i++) {
                int j = (i == array.length - 1) ? 0 : i + 1;
                if ((array[i] || arrayV[i]) != (array[j] || arrayV[j])) {
                    changed.add(i);
                }
            }
        }

        return changed;
    }
}
