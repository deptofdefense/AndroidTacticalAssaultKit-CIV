
package com.atakmap.android.util;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;

import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.android.imagecapture.PointA;
import com.atakmap.android.maps.Polyline;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.math.RectD;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Circle extends Polyline {
    private static final int RESOLUTION = 31; //Changing this value will impact the label
    public static final double MAX_RADIUS = 40075160.0 / 4.0 + 10.0; //10 is an error value to make SG/WinTAK happy

    private GeoPointMetaData _center;
    private double _radius;
    private final int res;
    private final boolean offset;
    private final MutableGeoBounds relativeBounds;

    private String _text;
    private Map<String, Object> _labels = new HashMap<>();
    private Map<String, Object> _segment = new HashMap<>();

    public Circle(final GeoPointMetaData center, final double radius,
            final String uid) {
        this(uid, center, radius, RESOLUTION, false);
    }

    public Circle() {
        this(null, 0, UUID.randomUUID().toString());
    }

    /**
     * Create a circle
     *
     * @param center - the center of the circle
     * @param radius - the radius of the circle in Meters
     */
    public Circle(final GeoPointMetaData center, final double radius) {
        this(center, radius, UUID.randomUUID().toString());
    }

    /**
     * create a circle with advanced options.
     *
     * @param center - the center of the circle
     * @param radius - the radius of the circle in Meters
     * @param resolution - how many line segments to use to make the circle
     * @param offset - set to true if the top of the circle should be the
     *                 middle of a line segment instead of a joint of 2 segments
     */
    public Circle(final GeoPointMetaData center, final double radius,
            final int resolution, final boolean offset) {
        this(UUID.randomUUID().toString(), center, radius, resolution, offset);
    }

    private Circle(String uid, GeoPointMetaData center, double radius,
            int resolution,
            boolean offset) {
        super(uid);
        _center = center;
        _radius = radius;
        res = resolution;
        this.offset = offset;
        this.relativeBounds = new MutableGeoBounds(
                (_center != null) ? _center.get() : GeoPoint.ZERO_POINT,
                (_center != null) ? _center.get() : GeoPoint.ZERO_POINT);

        createCircle();
        setHeightStyle(HEIGHT_STYLE_POLYGON | HEIGHT_STYLE_OUTLINE_SIMPLE);
    }

    /**
     * Returns the radius in meters for the circle.
     * @return the radius in meters.
     */
    public double getRadius() {
        return _radius;
    }

    /**
     * Sets the radius of the circle in meters.
     * @param radius the radius in meters.
     */
    public void setRadius(double radius) {
        _radius = radius;
        calculateCircle();
        setLabel(_text);
    }

    @Override
    public final GeoPointMetaData getCenter() {
        return _center;
    }

    /**
     * Sets the center point of the circle.
     * @param point the point with metadata used as the center point.
     */
    public void setCenterPoint(GeoPointMetaData point) {
        _center = point;
        calculateCircle();
    }

    @Override
    public void getRelativeBounds(MutableGeoBounds bounds) {
        bounds.set(this.relativeBounds.getNorth(),
                this.relativeBounds.getWest(),
                this.relativeBounds.getSouth(),
                this.relativeBounds.getEast());
    }

    private void createCircle() {
        setStyle(Polyline.STYLE_CLOSED_MASK | STYLE_STROKE_MASK
                | STYLE_FILLED_MASK);
        setStrokeWeight(2d);
        setStrokeColor(0xFFFFFFFF);
        setFillColor(0x00FFFFFF); // new rings are automatically transparent
        setMetaBoolean("unwrapLongitude", true);
        calculateCircle();
    }

    /**
     * Sets the label for a circle.
     * @param text the actual text to be displayed.
     */
    public void setLabel(final String text) {
        _text = text;
        if (_radius == 0)
            return; //Don't display label if there's no circle
        if (_labels == null)
            _labels = new HashMap<>();
        if (_segment == null)
            _segment = new HashMap<>();
        _segment.put("segment", res / 2);
        _segment.put("text", _text);
        if (_segment != null)
            _labels.put("segment", _segment);
        this.setMetaMap("labels", _labels);
        this.setMetaBoolean("centerLabel", true);
        this.setLabels(_labels);
    }

    /**
     * Call to show the label for the circle
     * @param show true if the label is to be shown.
     */
    public void showLabel(final boolean show) {
        if (show)
            setLabel(_text);
        else
            this.removeMetaData("labels");
        this.setLabels(null);
    }

    private void calculateCircle() {
        if (_radius >= MAX_RADIUS || _center == null || _radius < 0) {
            setPoints(new GeoPointMetaData[0]);
            Log.w("Circle", "Failed to calculate Circle");
            return;
        }
        double offsetLength = 0;
        if (offset) {
            offsetLength = (360d / res) / 2d;
        }

        GeoPoint center = _center.get();

        RectD extents = new RectD();
        extents.top = center.getLatitude();
        extents.left = center.getLongitude();
        extents.bottom = center.getLatitude();
        extents.right = center.getLongitude();

        GeoPointMetaData[] points = new GeoPointMetaData[res];
        for (int i = 0; i < res; i++) {
            final double theta = (i * (360.0 / res)) - offsetLength;
            GeoPoint point = GeoCalculations.pointAtDistance(center,
                    theta, _radius);
            point = new GeoPoint(point.getLatitude(), point.getLongitude(),
                    center.getAltitude());
            points[i] = GeoPointMetaData.wrap(point);

            double lat = point.getLatitude();
            double lng = point.getLongitude();

            // check if the radius crosses the IDL
            if (center.getLongitude() >= 0d) {
                // point to the east is in western hemisphere
                if (theta < 180d && lng < 0d) {
                    lng += 360d;
                }
            } else { // _center.getLongitude() < 0d
                // point to the west is in eastern hemisphere
                if (theta > 180d && lng > 0d) {
                    lng -= 360d;
                }
            }

            if (lat > extents.top)
                extents.top = lat;
            else if (lat < extents.bottom)
                extents.bottom = lat;
            if (lng > extents.right)
                extents.right = lng;
            else if (lng < extents.left)
                extents.left = lng;
        }
        this.relativeBounds.set(extents.top - center.getLatitude(),
                extents.left - center.getLongitude(),
                extents.bottom - center.getLatitude(),
                extents.right - center.getLongitude());
        setPoints(points);
    }

    @Override
    public Bundle preDrawCanvas(CapturePP cap) {
        // Store center point and radius x,y
        Bundle data = new Bundle();
        GeoPointMetaData center = getCenter();
        double radius = getRadius();
        GeoPoint edgeY = GeoCalculations.pointAtDistance(center.get(), 0,
                radius);
        GeoPoint edgeX = GeoCalculations.pointAtDistance(center.get(), 90,
                radius);
        PointF c = cap.forward(center.get()), rx = cap.forward(edgeX), ry = cap
                .forward(edgeY);
        PointF[] p = new PointF[2];
        p[0] = c;
        p[1] = new PointF(
                (float) Math.hypot(rx.x - c.x, rx.y - c.y),
                (float) Math.hypot(ry.x - c.x, ry.y - c.y));
        data.putSerializable("points", p);

        // Store label point
        GeoPoint[] lp = new GeoPoint[2];
        GeoBounds bounds = cap.getBounds();
        float angle = 0;
        for (int i = 0; i < 4; i++) {
            switch (i) {
                case 0:
                    angle = 180;
                    break;
                case 1:
                    angle = 0;
                    break;
                case 2:
                    angle = 90;
                    break;
                case 3:
                    angle = 270;
                    break;
            }
            lp[0] = GeoCalculations.pointAtDistance(center.get(), angle + 6,
                    radius);
            lp[1] = GeoCalculations.pointAtDistance(center.get(), angle - 6,
                    radius);
            if (bounds.contains(lp[0]) || bounds.contains(lp[1]))
                break;
        }

        final GeoPoint ctrPt = GeoCalculations
                .centerOfExtremes(lp, 0, lp.length);
        if (ctrPt != null) {
            PointA labelPoint = new PointA(cap.forward(ctrPt), angle);
            data.putParcelable("labelPoint", labelPoint);
        }
        return data;
    }

    @Override
    public void drawCanvas(CapturePP cap, Bundle data) {
        PointF[] lines = (PointF[]) data.getSerializable("points");
        if (lines == null || lines.length != 2)
            return;
        Canvas can = cap.getCanvas();
        Paint paint = cap.getPaint();
        Path path = cap.getPath();
        float dr = cap.getResolution();
        float lineWeight = cap.getLineWeight();
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(getStrokeColor());
        paint.setStrokeWidth((float) getStrokeWeight() * lineWeight);

        boolean closed = (getStyle() & Polyline.STYLE_FILLED_MASK) > 0;
        PointF c = lines[0];
        float rx = lines[1].x, ry = lines[1].y;
        path.addOval(new RectF(
                dr * (c.x - rx), dr * (c.y - ry),
                dr * (c.x + rx), dr * (c.y + ry)),
                Path.Direction.CW);
        can.drawPath(path, paint);
        if (closed) {
            paint.setColor(getFillColor());
            paint.setAlpha(Color.alpha(getFillColor()));
            paint.setStyle(Paint.Style.FILL);
            can.drawPath(path, paint);
        }
        path.reset();

        // Draw text label
        PointA labelPoint = data.getParcelable("labelPoint");
        if (labelPoint == null)
            return;
        String label = getLineLabel();
        if (FileSystemUtils.isEmpty(label)) {
            Map<String, Object> labels = getLabels();
            if (labels == null)
                return;
            Map<String, Object> labelBundle = null;
            for (Map.Entry<String, Object> e : labels.entrySet())
                labelBundle = (Map<String, Object>) e.getValue();
            if (labelBundle != null)
                label = (String) labelBundle.get("text");
        }
        if (cap.shouldDrawLabel(this, label))
            cap.drawLabel(label, labelPoint);
    }
}
