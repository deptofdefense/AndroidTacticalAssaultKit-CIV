
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
import com.atakmap.map.layer.feature.Feature;

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

    /**
     * The hit radius for the arrow
     * @param radius the radius in pixels
     */
    public void setHitRadius(float radius) {
        _hitRadius = radius;
        _hitRadiusSq = radius * radius;
    }

    /**
     * Get the tail point of the arrow when not rendered in reverse.
     * @return the point position of the tail.
     */
    public GeoPointMetaData getPoint1() {
        return _point1;
    }

    /**
     * Set the tail point of the arrow when not rendered in reverse.
     * @param p the point for the tail to start at.
     */
    public void setPoint1(GeoPointMetaData p) {
        _point1 = p;
        update();
    }

    /**
     * Get the head point of the arrow when not rendered in reverse.
     * @return the point position of the head.
     */
    public GeoPointMetaData getPoint2() {
        return _point2;
    }

    /**
     * Set the head point of the arrow when not rendered in reverse.
     * @param p the point for the head to point to.
     */
    public void setPoint2(GeoPointMetaData p) {
        _point2 = p;
        update();
    }

    /**
     * The text displayed on the arrow.
     * @param text the text
     */
    public void setText(final String text) {
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
    public void setAltitudeMode(Feature.AltitudeMode altitudeMode) {
        super.setAltitudeMode(altitudeMode);
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
