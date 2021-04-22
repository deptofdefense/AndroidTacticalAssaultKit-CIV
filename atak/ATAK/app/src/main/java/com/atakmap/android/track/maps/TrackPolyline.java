
package com.atakmap.android.track.maps;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Bundle;

import com.atakmap.android.imagecapture.CanvasHelper;
import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.android.maps.CrumbTrail;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.track.crumb.CrumbDatabase;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.layer.feature.Feature;

/**
 * Polyline used for displaying tracks
 */
public class TrackPolyline extends Polyline implements
        MapItem.OnGroupChangedListener {

    public static final String COT_TYPE = "b-m-t-h";

    // Default line style - breadcrumb arrows
    public static final int BASIC_LINE_STYLE_ARROWS = 10;

    private final Marker _startMarker;
    private final Marker _endMarker;
    private PointMapItem _user;
    private int _crumbSize;

    public TrackPolyline(long timestamp) {
        super(String.valueOf(timestamp));
        setType(COT_TYPE);
        setMetaLong("timestamp", timestamp);
        setMetaString("iconUri", ATAKUtilities.getResourceUri(
                com.atakmap.app.R.drawable.ic_track));
        setMetaString("menu", "menus/track_menu.xml");
        setMetaBoolean("removable", true);
        setStrokeWeight(4d);

        // Start marker
        _startMarker = new Marker(GeoPoint.ZERO_POINT, getUID() + "-start");
        _startMarker.setMetaBoolean("nevercot", true);
        _startMarker.setIcon(new Icon.Builder()
                .setImageUri(0, ATAKUtilities.getResourceUri(
                        R.drawable.ic_menu_goto_nav))
                .setAnchor(32, 32).build());
        _startMarker.setMetaString("shapeUID", getUID());

        // Destination marker
        _endMarker = new Marker(GeoPoint.ZERO_POINT, getUID() + "-end");
        _endMarker.setMetaBoolean("nevercot", true);
        _endMarker.setIcon(new Icon.Builder()
                .setImageUri(0, ATAKUtilities.getResourceUri(
                        R.drawable.ic_checkered_flag_white))
                .setAnchor(32, 32).build());
        _endMarker.setMetaString("shapeUID", getUID());

        setZOrder(-2);
        setBasicLineStyle(BASIC_LINE_STYLE_ARROWS);
        setAltitudeMode(Feature.AltitudeMode.Absolute);
        addOnGroupChangedListener(this);
    }

    @Override
    public void setTitle(String title) {
        super.setTitle(title);
        _startMarker.setTitle(title + " start");
        _endMarker.setTitle(title + " end");
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
        group.addItem(_startMarker);
        group.addItem(_endMarker);
        fixZLevels();
    }

    @Override
    public void onItemRemoved(final MapItem item, MapGroup group) {
        group.removeItem(_startMarker);
        group.removeItem(_endMarker);
    }

    @Override
    protected void onVisibleChanged() {
        boolean visible = getVisible();
        _startMarker.setVisible(visible && getStartPoint() != null);
        _endMarker.setVisible(visible && getEndPoint() != null);
        if (visible)
            fixZLevels();
        super.onVisibleChanged();
    }

    @Override
    public void setStrokeColor(int color) {
        if (color != getStrokeColor()) {
            super.setStrokeColor(color);
            _startMarker.setIcon(_startMarker.getIcon().buildUpon()
                    .setColor(0, color).build());
            _endMarker.setIcon(_endMarker.getIcon().buildUpon()
                    .setColor(0, color).build());
        }
    }

    @Override
    public void setZOrder(final double zOrder) {
        super.setZOrder(zOrder);
        if (_startMarker != null)
            _startMarker.setZOrder(zOrder - 0.1);
        if (_endMarker != null)
            _endMarker.setZOrder(zOrder - 0.1);
    }

    @Override
    public void setMetaBoolean(String key, boolean val) {
        super.setMetaBoolean(key, val);
        if (key.equals("detailsOpen"))
            fixZLevels();
    }

    @Override
    public void removeMetaData(String key) {
        super.removeMetaData(key);
        if (key.equals("detailsOpen"))
            fixZLevels();
    }

    @Override
    public void onPointsChanged() {
        super.onPointsChanged();

        GeoPointMetaData start = getStartPoint();
        _startMarker.setPoint(
                start == null ? GeoPointMetaData.wrap(GeoPoint.ZERO_POINT)
                        : start);

        GeoPointMetaData end = getEndPoint();
        _endMarker.setPoint(
                end == null ? GeoPointMetaData.wrap(GeoPoint.ZERO_POINT) : end);

        onVisibleChanged();
    }

    public void setCrumbSize(int crumbSize) {
        if (_crumbSize != crumbSize) {
            _crumbSize = crumbSize;
            super.onPointsChanged();
        }
    }

    public int getCrumbSize() {
        return _crumbSize;
    }

    public void addPoint(GeoPointMetaData point, boolean fireListener) {
        synchronized (this) {
            _points.add(point);
        }

        if (fireListener)
            refreshPoints();
    }

    public void addPoint(GeoPointMetaData point) {
        addPoint(point, true);
    }

    public void refreshPoints() {
        minimumBoundingBox.set(
                GeoPointMetaData
                        .unwrap(_points.toArray(new GeoPointMetaData[0])),
                0, _points.size());
        onPointsChanged();
    }

    public GeoPointMetaData getStartPoint() {
        return !_points.isEmpty() ? _points.get(0) : null;
    }

    public GeoPointMetaData getEndPoint() {
        return _points.size() > 1 ? _points.get(_points.size() - 1) : null;
    }

    public int getNumPoints() {
        return _points.size();
    }

    private PointMapItem getUserMarker() {
        MapView mv = MapView.getMapView();
        if (mv == null)
            return null;
        String userUID = getMetaString(CrumbDatabase.META_TRACK_NODE_UID,
                null);
        if (_user == null || _user.getGroup() == null
                || !_user.getUID().equals(userUID)) {
            MapItem mi = mv.getRootGroup().deepFindUID(userUID);
            if (mi instanceof PointMapItem)
                _user = (PointMapItem) mi;
        }
        return _user;
    }

    /**
     * Ensure track polyline is under the user self track crumb trail
     */
    private void fixZLevels() {
        if (!getVisible() || getGroup() == null)
            return;
        PointMapItem user = getUserMarker();
        CrumbTrail trail;
        if (user == null || (trail = user.getCrumbTrail()) == null)
            return;
        boolean detailsOpen = hasMetaValue("detailsOpen");
        if (trail.getZOrder() >= getZOrder() && !detailsOpen)
            setZOrder(trail.getZOrder() + 1);
        else if (trail.getZOrder() <= getZOrder() && detailsOpen)
            setZOrder(trail.getZOrder() - 1);
    }

    // Used for canvas drawing
    private static final Path _crumbPath = new Path();
    static {
        // Construct the arrow shape
        _crumbPath.moveTo(-0.5f, 0.75f);
        _crumbPath.lineTo(0.5f, 0.75f);
        _crumbPath.lineTo(0f, -0.75f);
        _crumbPath.close();
    }

    @Override
    public void drawCanvas(CapturePP cap, Bundle data) {
        PointF[] p = (PointF[]) data.getSerializable("points");
        if (p == null || p.length < 3)
            return;
        Canvas can = cap.getCanvas();
        Paint paint = cap.getPaint();
        Path path = cap.getPath();
        float dr = cap.getResolution();
        float size = getCrumbSize() * dr;
        float tolerance = size * 1.5f;
        int color = getStrokeColor();

        Matrix m = new Matrix();
        PointF last = p[0];
        for (int i = 0; i < p.length; i++) {
            // Ignore first and last point
            // Covered by the start and end markers
            if (i == 0 || i == p.length - 1)
                continue;

            // Ignore points too close together
            if (CanvasHelper.length(last, p[i]) < tolerance)
                continue;

            // Move crumb to point
            m.postScale(size, size);
            m.postRotate(CanvasHelper.angleTo(last, p[i]));
            m.postTranslate(p[i].x, p[i].y);
            path.addPath(_crumbPath);
            path.transform(m);

            // Draw outline
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(Color.BLACK);
            can.drawPath(path, paint);

            // Draw fill
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            can.drawPath(path, paint);

            path.reset();
            m.reset();
            last = p[i];
        }
    }
}
