
package com.atakmap.android.imagecapture;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;

import com.atakmap.coremap.maps.coords.Vector2D;
import com.atakmap.math.MathUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Canvas/path drawing functions
 */
public class CanvasHelper {
    public static void drawRectBorder(Canvas can, Paint paint, float weight,
            RectF rect, int borderColor) {
        Paint blackRect = new Paint(paint);
        blackRect.setColor(borderColor);
        can.drawRect(rect.left - weight, rect.top - weight,
                rect.right + weight, rect.bottom + weight, blackRect);
        can.drawRect(rect.left, rect.top, rect.right, rect.bottom, paint);
    }

    public static void drawRectBorder(Canvas can, Paint paint, float weight,
            RectF rect) {
        drawRectBorder(can, paint, weight, rect, Color.BLACK);
    }

    public static void drawPathBorder(Canvas can, Path path, float weight,
            Paint paint, int borderColor) {
        Paint boldPath = new Paint(paint);
        boldPath.setStyle(Paint.Style.STROKE);
        boldPath.setColor(borderColor);
        boldPath.setStrokeWidth(paint.getStrokeWidth() + weight);
        can.drawPath(path, boldPath);
        can.drawPath(path, paint);
    }

    public static void drawPathBorder(Canvas can, Path path, float weight,
            Paint paint) {
        drawPathBorder(can, path, weight, paint, Color.BLACK);
    }

    public static void drawTextBorder(Canvas can, String text, float x,
            float y, float weight, Paint textPaint, int borderColor) {
        Paint strokePaint = new Paint(textPaint);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(borderColor);
        strokePaint.setStrokeWidth(weight);
        can.drawText(text, x, y, strokePaint);
        can.drawText(text, x, y, textPaint);
    }

    public static void drawTextBorder(Canvas can, String text, float x,
            float y, float weight, Paint textPaint) {
        drawTextBorder(can, text, x, y, weight, textPaint, Color.BLACK);
    }

    /**
     * Setup an arrow on a path
     * @param path Path to use
     * @param tail Arrow tail position
     * @param head Arrow head position
     * @param tipLen Tip/head length
     */
    public static void buildArrow(Path path, PointF tail, PointF head,
            float tipLen, float tipDeg) {
        path.moveTo(tail.x, tail.y);
        path.lineTo(head.x, head.y);
        float deg = (float) Math.toDegrees(Math.atan2(head.y - tail.y,
                head.x - tail.x)) + 90f;
        CanvasHelper.lineToOffset(path, head, deg + tipDeg, tipLen);
        CanvasHelper.lineToOffset(path, head, deg - tipDeg, tipLen);
    }

    public static void buildArrow(Path path, PointF source, float deg,
            float len, float tipLen, float tipDeg) {
        buildArrow(path, source, degOffset(source, deg, len), tipLen, tipDeg);
    }

    public static void rLineToAng(Path path, float x, float y, float deg,
            float len) {
        path.moveTo(x, y);
        PointF offset = degOffset(deg, len);
        path.rLineTo(offset.x, offset.y);
    }

    public static void lineToOffset(Path path, PointF origin, float deg,
            float len) {
        rLineToAng(path, origin.x, origin.y, deg, len);
    }

    public static void lineToPoint(Path path, PointF p1, PointF p2) {
        path.moveTo(p1.x, p1.y);
        path.lineTo(p2.x, p2.y);
    }

    public static float degCos(double deg) {
        return (float) Math.cos(Math.toRadians(deg));
    }

    public static float degSin(double deg) {
        return (float) Math.sin(Math.toRadians(deg));
    }

    public static PointF degOffset(double deg, float xLen, float yLen) {
        return new PointF(degSin(deg) * xLen, -degCos(deg) * yLen);
    }

    public static PointF degOffset(double deg, float len) {
        return degOffset(deg, len, len);
    }

    public static PointF degOffset(PointF start, double deg, float xLen,
            float yLen) {
        PointF end = degOffset(deg, xLen, yLen);
        end.x += start.x;
        end.y += start.y;
        return end;
    }

    public static PointF degOffset(PointF start, double deg, float len) {
        return degOffset(start, deg, len, len);
    }

    public static float angleTo(PointF start, PointF end) {
        return (float) Math.toDegrees(Math.atan2(end.x - start.x,
                start.y - end.y));
    }

    public static float length(PointF... points) {
        double len = 0;
        for (int i = 0; i < points.length - 1; i++)
            len += Math.hypot(points[i].x - points[i + 1].x,
                    points[i].y - points[i + 1].y);
        return (float) len;
    }

    /**
     * Return true if the capture canvas and the line intersect.
     * @param cap the capture canvas
     * @param line the line
     * @return true if they intersect.
     */
    public static boolean intersecting(CapturePP cap, PointF[] line) {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE,
                minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (PointF p : line) {
            minX = Math.min(p.x, minX);
            maxX = Math.max(p.x, maxX);
            minY = Math.min(p.y, minY);
            maxY = Math.max(p.y, maxY);
        }
        return !(maxX < 0 || minX > cap.getWidth()
                || minY > cap.getHeight() || maxY < 0);
    }

    public static void clampToLine(PointF p, PointF... l) {
        if (l.length < 2)
            return;
        p.x = MathUtils.clamp(p.x, Math.min(l[0].x, l[1].x),
                Math.max(l[0].x, l[1].x));
        p.y = MathUtils.clamp(p.y, Math.min(l[0].y, l[1].y),
                Math.max(l[0].y, l[1].y));
    }

    public static float validate(float num, float fallback) {
        return Float.isNaN(num) || Float.isInfinite(num) ? fallback : num;
    }

    public static double deg360(double deg) {
        return deg % 360 + (deg < 0 ? 360 : 0);
    }

    /**
     * Provides the top and the bottom most intersection points on a line
     * clipped with a rectangle
     *
     * @param r The rectangle to clip the line to
     * @param sp The start point on the line segment
     * @param ep The end point on the line segment
     */
    public static List<PointF> findIntersectionPoints(RectF r, PointF sp,
            PointF ep) {

        List<PointF> ret = new ArrayList<>();

        // Convert to Vector2D to utilize Vector2D.segmentIntersectionsWithPolygon
        Vector2D sv = new Vector2D(sp.x, sp.y);
        Vector2D ev = new Vector2D(ep.x, ep.y);
        Vector2D[] rectPoly = {
                new Vector2D(r.left, r.top),
                new Vector2D(r.right, r.top),
                new Vector2D(r.right, r.bottom),
                new Vector2D(r.left, r.bottom),
                new Vector2D(r.left, r.top)
        };

        // Perform intersection test and convert result back to PointF
        List<Vector2D> ips = Vector2D.segmentIntersectionsWithPolygon(sv, ev,
                rectPoly);
        for (Vector2D ip : ips)
            ret.add(new PointF((float) ip.x, (float) ip.y));
        return ret;
    }
}
