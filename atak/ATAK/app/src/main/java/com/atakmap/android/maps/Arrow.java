
package com.atakmap.android.maps;

import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;

import com.atakmap.android.imagecapture.CanvasHelper;
import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.android.imagecapture.PointA;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.coords.Vector3D;
import com.atakmap.math.MathUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Arrow extends Shape {

    public static final String TAG = "Arrow";
    protected GeoPointMetaData _point1;
    protected GeoPointMetaData _point2;

    protected MapView _mapView;

    protected float _hitRadius = Float.NaN;
    protected float _hitRadiusSq = Float.NaN;
    protected GeoPoint _touchPoint;

    protected String text;
    protected int _textColor = Color.WHITE;
    private final MutableGeoBounds minimumBoundingBox = new MutableGeoBounds(
            0, 0,
            0, 0);

    private final ConcurrentLinkedQueue<OnTextChangedListener> _onTextChanged = new ConcurrentLinkedQueue<>();
    private final List<GeoPointMetaData> _pts = new ArrayList<>(2);

    public Arrow(final String uid) {
        this(MapItem.createSerialId(), new DefaultMetaDataHolder(), uid);
    }

    public Arrow(long serialId, MetaDataHolder metadata, String uid) {
        super(serialId, metadata, uid);
    }

    public void setHitRadius(float radius) {
        _hitRadius = radius;
        _hitRadiusSq = radius * radius;
    }

    @Override
    public boolean testOrthoHit(int xpos, int ypos, GeoPoint point,
            MapView view) {

        float hitRadius = !Float.isNaN(_hitRadius)
                ? _hitRadius
                : getHitRadius(view);
        float hitRadiusSq = !Float.isNaN(_hitRadiusSq)
                ? _hitRadiusSq
                : (hitRadius * hitRadius);

        // if either endpoint is null, we do not have a segment, return
        if (_point1 == null || _point2 == null)
            return false;

        GeoPoint p1 = _point1.get();
        GeoPoint p2 = _point2.get();

        // Determine whether or not the line is clamped to the ground
        boolean clampToGround = getMetaBoolean("forceClampToGround", false)
                || view.getMapTouchController().isNadirClamped();

        // Remove elevation so terrain is used instead
        if (clampToGround) {
            p1 = new GeoPoint(p1.getLatitude(), p1.getLongitude());
            p2 = new GeoPoint(p2.getLatitude(), p2.getLongitude());
        }

        // adjust the endpoints to account for terrain offset/scale if tilted
        p1 = view.getRenderElevationAdjustedPoint(p1);
        p2 = view.getRenderElevationAdjustedPoint(p2);

        double unwrap = view.getIDLHelper().getUnwrap(this.minimumBoundingBox);

        // Check for touches on the actual arrow
        Vector3D touch = new Vector3D(xpos, ypos, 0);

        PointF temp = view.forward(p1, unwrap);
        if (Float.isNaN(temp.x) || Float.isNaN(temp.y))
            return false;
        // bleed through on endpoint item
        if (com.atakmap.math.Rectangle.contains(xpos - hitRadius,
                ypos - hitRadius,
                xpos + hitRadius,
                ypos + hitRadius,
                temp.x, temp.y)) {

            return false;
        }
        PointF temp2 = view.forward(p2, unwrap);
        if (Float.isNaN(temp2.x) || Float.isNaN(temp2.y))
            return false;
        if (com.atakmap.math.Rectangle.contains(xpos - hitRadius,
                ypos - hitRadius,
                xpos + hitRadius,
                ypos + hitRadius,
                temp2.x, temp2.y)) {

            return false;
        }

        // Check if the touch hit the line
        Vector3D nearest = Vector3D.nearestPointOnSegment(touch,
                new Vector3D(temp.x, temp.y, 0),
                new Vector3D(temp2.x, temp2.y, 0));
        if (hitRadiusSq > nearest.distanceSq(touch)) {
            // Compute geodetic touch point
            _touchPoint = view.inverse(nearest.x, nearest.y,
                    MapView.InverseMode.RayCast).get();

            double seg_px = MathUtils.distance(temp.x, temp.y, temp2.x,
                    temp2.y);
            double seg_pct = MathUtils.distance(nearest.x, nearest.y, temp.x,
                    temp.y);
            double seg_ratio = seg_pct / seg_px;

            _touchPoint = GeoCalculations.pointAtDistance(p1,
                    p1.bearingTo(p2), p1.distanceTo(p2) * seg_ratio);

            // Altitude correction
            if (p1.isAltitudeValid() && p2.isAltitudeValid()) {
                // Compute altitude at the touched point on the line
                double touchAlt = p1.getAltitude() + (p2.getAltitude()
                        - p1.getAltitude()) * seg_ratio;
                _touchPoint = new GeoPoint(_touchPoint.getLatitude(),
                        _touchPoint.getLongitude(), touchAlt);
            } else {
                // Remove altitude if either point is missing it
                _touchPoint = new GeoPoint(_touchPoint.getLatitude(),
                        _touchPoint.getLongitude());
            }

            // Save touch point
            setMetaString("menu_point", _touchPoint.toString());
            setTouchPoint(_touchPoint);
            return true;
        }

        return false;
    }

    public GeoPointMetaData getPoint1() {
        return _point1;
    }

    public void setPoint1(GeoPointMetaData p) {
        _point1 = p;
        update();
    }

    public GeoPointMetaData getPoint2() {
        return _point2;
    }

    public void setPoint2(GeoPointMetaData p) {
        _point2 = p;
        update();
    }

    public void setText(String text) {
        if (!FileSystemUtils.isEquals(this.text, text)) {
            this.text = text;
            for (OnTextChangedListener l : _onTextChanged)
                l.onTextChanged(this);
        }
    }

    public String getText() {
        return this.text != null ? this.text : "";
    }

    public void setTextColor(int color) {
        if (_textColor != color) {
            _textColor = color;
            for (OnTextChangedListener l : _onTextChanged)
                l.onTextChanged(this);
        }
    }

    public int getTextColor() {
        return _textColor;
    }

    public interface OnTextChangedListener {
        void onTextChanged(Arrow arrow);
    }

    final protected void update() {
        if (_point1 == null || _point2 == null)
            return;

        synchronized (_pts) {
            _pts.clear();
            _pts.add(_point1);
            _pts.add(_point2);

            MapView mv = MapView.getMapView();
            GeoPointMetaData[] _ptarray = _pts.toArray(new GeoPointMetaData[0]);
            this.minimumBoundingBox.set(GeoPointMetaData.unwrap(_ptarray),
                    mv != null
                            && mv.isContinuousScrollEnabled());
        }
        this.onPointsChanged();
    }

    public void addOnTextChangedListener(OnTextChangedListener l) {
        if (l != null) {
            _onTextChanged.add(l);
            if (this.text != null)
                l.onTextChanged(this);
        }
    }

    public void removeOnTextChangedListener(OnTextChangedListener l) {
        _onTextChanged.remove(l);
    }

    @Override
    public GeoPointMetaData getCenter() {
        MapView mv = MapView.getMapView();
        return GeoPointMetaData
                .wrap(GeoCalculations.midPointCartesian(_point1.get(),
                        _point2.get(), mv != null
                                && mv.isContinuousScrollEnabled()));
    }

    @Override
    public GeoPointMetaData[] getMetaDataPoints() {
        GeoPointMetaData[] retval = null;
        synchronized (_pts) {
            retval = _pts.toArray(new GeoPointMetaData[0]);
        }
        return retval;
    }

    @Override
    public GeoPoint[] getPoints() {
        GeoPointMetaData[] p = getMetaDataPoints();
        if (p == null)
            p = new GeoPointMetaData[0];
        return GeoPointMetaData.unwrap(p);
    }

    @Override
    public GeoBounds getBounds(MutableGeoBounds bounds) {
        return new GeoBounds(this.minimumBoundingBox);
    }

    @Override
    public Bundle preDrawCanvas(CapturePP cap) {
        Bundle data = new Bundle();
        GeoPoint[] points = getPoints();
        int pLen;
        if (points == null || (pLen = points.length) < 2)
            return null;
        PointF[] p = new PointF[pLen + 3];
        int i = 0;
        for (GeoPoint gp : points)
            p[i++] = cap.forward(gp);

        // Add arrow head to end of last point
        PointF firstP = p[pLen - 2], lastP = p[pLen - 1];
        double arrowDir = Math
                .atan((lastP.y - firstP.y) / (lastP.x - firstP.x));
        if (lastP.x >= firstP.x)
            arrowDir += Math.PI;
        double dirPlus45 = arrowDir + Math.PI / 4, dirMinus45 = arrowDir
                - Math.PI / 4;

        p[pLen + 1] = lastP;
        p[pLen] = new PointF(
                lastP.x + (float) Math.cos(dirPlus45) * 16,
                lastP.y + (float) Math.sin(dirPlus45) * 16);
        p[pLen + 2] = new PointF(
                lastP.x + (float) Math.cos(dirMinus45) * 16,
                lastP.y + (float) Math.sin(dirMinus45) * 16);
        data.putSerializable("points", p);

        // Include label
        float deg = CanvasHelper.angleTo(p[0], p[1]) + 90;
        data.putParcelable("labelPoint", new PointA(
                cap.forward(getCenter().get()), deg));
        return data;
    }

    @Override
    public void drawCanvas(CapturePP cap, Bundle data) {
        super.drawCanvas(cap, data);
        PointF[] points = (PointF[]) data.getSerializable("points");

        // Draw label
        String label = getText();
        PointA labelPoint = data.getParcelable("labelPoint");
        if (labelPoint != null && cap.shouldDrawLabel(label, points))
            cap.drawLabel(label, labelPoint);
    }
}
