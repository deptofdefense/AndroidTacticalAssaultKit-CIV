
package com.atakmap.android.drawing.mapItems;

import android.graphics.Color;

import com.atakmap.android.importexport.handlers.ParentMapItem;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.Ellipse;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.Circle;
import com.atakmap.app.R;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An ellipse with one or more rings of variable radii
 * This class is only meant for display purposes for incoming CoT events
 * that utilize differing minor and major axis
 *
 * If we ever create an ellipse tool then this class should extend
 * or be extended by {@link DrawingCircle} - For now it's an isolated class
 * due to the difference in implementation between {@link Circle} and {@link Ellipse}
 */
public class DrawingEllipse extends Shape implements AnchoredMapItem,
        ParentMapItem, MapItem.OnGroupChangedListener,
        PointMapItem.OnPointChangedListener {

    public static final String COT_TYPE = "u-d-c-e";
    public static final int DEFAULT_STYLE = Polyline.STYLE_CLOSED_MASK
            | STYLE_STROKE_MASK | STYLE_FILLED_MASK;

    protected final MapView _mapView;

    // Child map group where rings and markers are stored
    protected final MapGroup _childGroup;

    // Each of the rings in the ellipse - should contain at least one
    protected final List<Ellipse> _rings = new ArrayList<>();

    // Center point
    protected GeoPointMetaData _center = new GeoPointMetaData();

    // The center marker
    protected Marker _centerMarker;

    /**
     * Create a new drawing ellipse
     * @param mapView Map view instance
     * @param uid UID
     * @param childGroup The child map group
     */
    public DrawingEllipse(MapView mapView, String uid, MapGroup childGroup) {
        super(uid);
        _mapView = mapView;
        _childGroup = childGroup;
        _childGroup.setMetaBoolean("addToObjList", false);
        setType(COT_TYPE);
        setMetaBoolean("removable", true);
        setMetaBoolean("movable", true);
        setClickable(true);
        setMetaString("iconUri", ATAKUtilities.getResourceUri(
                mapView.getContext(), R.drawable.ic_circle));
        setStyle(DEFAULT_STYLE);
    }

    public DrawingEllipse(MapView mapView, String uid) {
        this(mapView, uid, new DefaultMapGroup(uid));
    }

    public DrawingEllipse(MapView mapView) {
        this(mapView, UUID.randomUUID().toString());
    }

    /**
     * Directly set the ellipses for this group
     * Note: Only the minor axis, major axis, and angle are utilized here
     * Colors and styles must be set directly on parent ellipse
     * @param ellipses List of ellipses
     */
    public void setEllipses(List<Ellipse> ellipses) {
        if (ellipses.isEmpty())
            return;
        synchronized (this) {
            _rings.clear();
            _rings.addAll(ellipses);
        }
        refresh();
    }

    public synchronized List<Ellipse> getEllipses() {
        return new ArrayList<>(_rings);
    }

    public synchronized Ellipse getOutermostEllipse() {
        if (_rings.isEmpty())
            return null;
        return _rings.get(_rings.size() - 1);
    }

    /**
     * Refresh child ellipses
     */
    public void refresh() {
        double zOrder = getZOrder();
        int strokeColor = getStrokeColor();

        Marker center = getCenterMarker();
        if (center != null && isCenterShapeMarker()) {
            center.setTitle(getTitle());
            center.setZOrder(zOrder - 1);
            if (strokeColor != center.getMetaInteger("color", Color.WHITE)) {
                center.setMetaInteger("color", strokeColor);
                center.refresh(_mapView.getMapEventDispatcher(),
                        null, getClass());
            }
        }

        List<Ellipse> rings = getEllipses();
        double zInc = 1d / rings.size();
        for (Ellipse e : rings) {
            e.setCenter(_center);
            e.setStyle(getStyle());
            e.setStrokeColor(getStrokeColor());
            e.setStrokeWeight(getStrokeWeight());
            e.setFillColor(getFillColor());
            e.setZOrder(zOrder += zInc);
            e.setMetaBoolean("addToObjList", false);
            e.setClickable(false);
            if (e.getGroup() == null)
                _childGroup.addItem(e);
        }
    }

    @Override
    public void setStyle(int style) {
        super.setStyle(style);
        refresh();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        _childGroup.setVisible(visible);
    }

    /**
     * Set the center point of this ellipse
     * This will move the anchor marker as well
     * @param point Center point
     */
    public void setCenterPoint(GeoPointMetaData point) {
        _center = point;

        // Update center marker position
        Marker center = getCenterMarker();
        if (center != null && !center.getPoint().equals(point.get()))
            center.setPoint(point);

        refresh();
        onPointsChanged();
    }

    @Override
    public GeoPointMetaData getCenter() {
        return _center;
    }

    public GeoPoint getCenterPoint() {
        return getCenter().get();
    }

    /**
     * Set the center marker
     * @param marker Center marker
     */
    public void setCenterMarker(Marker marker) {
        if (marker != null && _centerMarker == marker)
            return;
        if (_centerMarker != null) {
            _centerMarker.removeOnPointChangedListener(this);
            _centerMarker.removeOnGroupChangedListener(this);
            if (isCenterShapeMarker())
                _centerMarker.removeFromGroup();
            else if (getUID().equals(_centerMarker.getMetaString(
                    "shapeUID", "")))
                _centerMarker.removeMetaData("shapeUID");
        }
        if (marker == null) {
            // Create the default shape marker
            marker = new Marker(getCenter(), UUID.randomUUID().toString());
            marker.setType("shape_marker");
            marker.setMetaString("menu", "menus/default_item.xml");
            marker.setMetaBoolean("nevercot", true);
            marker.setMetaBoolean("removable", true);
            _childGroup.addItem(marker);
        }
        _centerMarker = marker;
        setCenterPoint(marker.getGeoPointMetaData());
        marker.addOnPointChangedListener(this);
        marker.addOnGroupChangedListener(this);
        marker.setMetaString("shapeUID", getUID());
        setTitle(getTitle());
    }

    /**
     * Get the center marker for this ellipse
     * @return Center marker
     */
    public Marker getCenterMarker() {
        return _centerMarker;
    }

    /**
     * Helper method to check if the center marker is a generic shape marker
     * as opposed to a marker explicitly set by the user
     * @return True if the center marker is a generic shape marker
     */
    public boolean isCenterShapeMarker() {
        Marker marker = getCenterMarker();
        return marker != null && marker.getType().equals("shape_marker");
    }

    /**
     * The center marker acts as the anchor for this ellipse
     * @return Center marker or underlying shape marker
     */
    @Override
    public PointMapItem getAnchorItem() {
        return getCenterMarker();
    }

    /**
     * Get all points of the outermost ring
     * @return Array of points
     */
    @Override
    public GeoPointMetaData[] getMetaDataPoints() {
        Ellipse outer = getOutermostEllipse();
        if (outer != null)
            return outer.getMetaDataPoints();
        return new GeoPointMetaData[0];
    }

    /**
     * Get all points of the outermost ring
     * @return Array of points
     */
    @Override
    public GeoPoint[] getPoints() {
        Ellipse outer = getOutermostEllipse();
        if (outer != null)
            return outer.getPoints();
        return new GeoPoint[0];
    }

    /**
     * Bounds is equivalent to the bonds of the outermost circle
     */
    @Override
    public GeoBounds getBounds(MutableGeoBounds bounds) {
        Ellipse outer = getOutermostEllipse();
        if (outer != null)
            return outer.getBounds(bounds);
        if (bounds != null) {
            bounds.clear();
            return bounds;
        }
        return new GeoBounds(0, 0, 0, 0);
    }

    /**
     * Keep the rings at the same z-order
     * @param zOrder Z-order
     */
    @Override
    public void setZOrder(double zOrder) {
        super.setZOrder(zOrder);
        refresh();
    }

    /**
     * Apply fill color to the outermost ring only
     * @param fillColor An argb packed by {@link Color}
     */
    @Override
    public void setFillColor(int fillColor) {
        super.setFillColor(fillColor);
        refresh();
    }

    /**
     * Apply stroke color to all rings
     * @param strokeColor An argb packed by {@link Color}
     */
    @Override
    public void setStrokeColor(int strokeColor) {
        super.setStrokeColor(strokeColor);
        refresh();
    }

    @Override
    public void setStrokeWeight(double weight) {
        super.setStrokeWeight(weight);
        refresh();
    }

    /**
     * Redirect hit detection to the rings
     */
    @Override
    public boolean testOrthoHit(int xpos, int ypos, GeoPoint point,
            MapView view) {
        GeoPoint touch = null;
        for (Ellipse ellipse : getEllipses()) {
            if (ellipse.testOrthoHit(xpos, ypos, point, view)) {
                touch = ellipse.findTouchPoint();
                break;
            }
        }
        if (touch != null) {
            setTouchPoint(touch);
            setMetaString("menu_point", touch.toString());
            return true;
        }
        return false;
    }

    @Override
    public MapGroup getChildMapGroup() {
        return _childGroup;
    }

    /**
     * Keep the child map group with whichever group this circle is moved to
     * @param added True if circle added to group
     * @param group Map group it was added to
     */
    @Override
    protected void onGroupChanged(boolean added, MapGroup group) {
        MapGroup parent = _childGroup.getParentGroup();
        if (parent != null)
            parent.removeGroup(_childGroup);
        if (added) {
            group.addGroup(_childGroup);
            setCenterMarker(getCenterMarker());
        }
        super.onGroupChanged(added, group);
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        if (item == _centerMarker) {
            if (isCenterShapeMarker())
                removeFromGroup();
            else
                setCenterMarker(null);
        }
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        if (item == _centerMarker)
            setCenterPoint(item.getGeoPointMetaData());
    }
}
