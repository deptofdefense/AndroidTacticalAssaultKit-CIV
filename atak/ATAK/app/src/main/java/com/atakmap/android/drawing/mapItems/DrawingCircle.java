
package com.atakmap.android.drawing.mapItems;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.gpx.GpxTrack;
import com.atakmap.android.gpx.GpxTrackSegment;
import com.atakmap.android.gpx.GpxWaypoint;
import com.atakmap.android.imagecapture.CapturePP;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.handlers.ParentMapItem;
import com.atakmap.android.maps.AnchoredMapItem;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.preference.UnitPreferences;
import com.atakmap.android.routes.RouteGpxIO;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.Circle;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;
import com.atakmap.spatial.file.export.GPXExportWrapper;
import com.atakmap.spatial.file.export.KMZFolder;
import com.atakmap.spatial.file.export.OGRFeatureExportWrapper;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.Geometry;
import com.ekito.simpleKML.model.LineStyle;
import com.ekito.simpleKML.model.MultiGeometry;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.PolyStyle;
import com.ekito.simpleKML.model.Polygon;
import com.ekito.simpleKML.model.Style;
import com.ekito.simpleKML.model.StyleSelector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import gov.tak.api.annotation.DontObfuscate;

/**
 * A circle with one or more rings, a center marker, and radius marker
 */
@DontObfuscate
public class DrawingCircle extends Shape implements
        Exportable, AnchoredMapItem, ParentMapItem,
        MapItem.OnGroupChangedListener,
        PointMapItem.OnPointChangedListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "DrawingCircle";

    public static final String COT_TYPE = "u-d-c-c";
    public static final int MAX_RINGS = 25;
    public static final int DEFAULT_STYLE = Polyline.STYLE_CLOSED_MASK
            | STYLE_STROKE_MASK | STYLE_FILLED_MASK;

    // Apply menu metadata to shape marker
    protected static final Set<String> MENU_METADATA = new HashSet<>();
    static {
        MENU_METADATA.add("menu");
        MENU_METADATA.add("labels_on");
        MENU_METADATA.add("editable");
        MENU_METADATA.add("movable");
    }

    protected final MapView _mapView;
    protected final UnitPreferences _prefs;

    // Child map group where rings and markers are stored
    protected final MapGroup _childGroup;

    // Each of the rings in the circle - should contain at least one
    protected final List<Circle> _rings = new ArrayList<>();

    // The radius of the base circle in meters
    protected double _radius = 0d;

    // Center point
    protected GeoPointMetaData _center = new GeoPointMetaData();

    // The center and radius markers
    protected Marker _centerMarker, _radiusMarker;

    // Listeners
    protected final Set<OnRadiusChangedListener> _radiusListeners = new HashSet<>();

    // Utilized in the case where two rings are interlocked on ajoining markers, ie
    // the first ring center point marker is marker 1 with the radius marker being marker 2
    // and the second ring center point marker is marker 2 with the radius marker being marker 1
    // this breaks the infinite loop that occurs when recalculating positions of the radius
    // marker which in tern causes the recalculation of the center point.
    private boolean updatingRadiusMarker = false;

    /**
     * Create a new drawing circle
     * @param mapView Map view instance
     * @param uid UID
     * @param type Circle type
     * @param childGroup The child map group
     */
    public DrawingCircle(MapView mapView, String uid, String type,
            MapGroup childGroup) {
        super(uid);
        _mapView = mapView;
        _prefs = new UnitPreferences(mapView);
        _childGroup = childGroup;
        _childGroup.setMetaBoolean("addToObjList", false);
        _childGroup.setMetaString("shapeUID", uid);
        setType(type);
        setMetaBoolean("removable", true);
        setMovable(true);
        setMetaBoolean("archive", true);
        setMetaBoolean("labels_on", true);
        setMetaBoolean("editable", true);
        setMetaString("iconUri", ATAKUtilities.getResourceUri(
                mapView.getContext(), R.drawable.ic_circle));
        setMetaString("menu", "menus/drawing_circle_geofence_menu.xml");
        setStyle(DEFAULT_STYLE);
    }

    public DrawingCircle(MapView mapView, String uid, String type) {
        this(mapView, uid, type, new DefaultMapGroup(uid));
    }

    public DrawingCircle(MapView mapView, String uid) {
        this(mapView, uid, COT_TYPE);
    }

    public DrawingCircle(MapView mapView) {
        this(mapView, UUID.randomUUID().toString());
    }

    /**
     * Set the radius of the base circle (first ring)
     * @param radius Radius in meters
     * @return True if successful, false if too small/large
     */
    public boolean setRadius(double radius) {
        if (radius <= 0 || radius > Circle.MAX_RADIUS)
            return false;
        if (Double.compare(radius, _radius) == 0)
            return true;

        double oldRadius = _radius;
        _radius = radius;

        // Check if we need to move the radius marker
        Marker marker = getRadiusMarker();
        if (marker != null) {
            GeoPoint center = getCenterPoint();
            double distance = GeoCalculations.distanceTo(center,
                    marker.getPoint());
            if (Math.abs(distance - radius) > 0.1) {
                // Radius marker needs to be moved
                double bearing = GeoCalculations.bearingTo(center,
                        marker.getPoint());
                GeoPoint dest = GeoCalculations.pointAtDistance(center, bearing,
                        radius);
                marker.setPoint(new GeoPointMetaData(dest)
                        .setGeoPointSource(GeoPointMetaData.CALCULATED));
            }
        }

        // Update the rings
        refresh();
        onRadiusChanged(oldRadius);
        return true;
    }

    /**
     * Get the radius of the base circle (first ring)
     * @return Radius in meters
     */
    public double getRadius() {
        return _radius;
    }

    /**
     * Get the radius of a specific ring in this circle
     * @param index Ring index (starting at 0)
     * @return Radius of the ring in meters
     */
    public double getRingRadius(int index) {
        Circle ring = getRing(index);
        if (ring != null)
            return ring.getRadius();
        return getRadius() * (index + 1);
    }

    /**
     * Get the number of rings in this circle
     * @return Number of rings
     */
    public int getNumRings() {
        return _rings.size();
    }

    /**
     * Get the list of rings
     * @return Non-mutable list of rings
     */
    public synchronized List<Circle> getRings() {
        return new ArrayList<>(_rings);
    }

    /**
     * Get the ring at the specified index
     * @param index Index (starting at 0)
     * @return Ring circle
     */
    public synchronized Circle getRing(int index) {
        return index >= 0 && index < _rings.size() ? _rings.get(index) : null;
    }

    /**
     * Get the outermost ring
     * @return Outermost ring
     */
    public synchronized Circle getOutermostRing() {
        return !_rings.isEmpty() ? _rings.get(_rings.size() - 1) : null;
    }

    public int setNumRings(int numRings) {
        if (getRadius() <= 0)
            return 0;
        synchronized (this) {
            int size = _rings.size();
            if (numRings <= 0 || numRings > MAX_RINGS || numRings == size)
                return size;
            if (numRings > size) {
                double radius = getRadius();
                for (int i = size; i < numRings; i++)
                    _rings.add(createRing(radius * i));
            } else {
                while (_rings.size() > numRings) {
                    Circle c = _rings.remove(numRings);
                    c.removeFromGroup();
                }
            }
            refresh();
        }
        onRadiusChanged(getRadius());
        return getNumRings();
    }

    /**
     * Create a new ring for this circle
     * @param radius The radius of the new circle
     * @return New circle object or null if failed
     */
    protected Circle createRing(double radius) {
        if (radius <= 0)
            return null;
        GeoPointMetaData center = getCenter();
        return new Circle(center, radius);
    }

    /**
     * Refresh all child item states including rings and center marker
     */
    public void refresh() {
        double radius = getRadius();
        if (radius <= 0)
            return;

        double zOrder = getZOrder();
        int strokeColor = getStrokeColor();

        Marker center = getCenterMarker();
        if (center != null && isCenterShapeMarker()) {
            center.setZOrder(zOrder);
            setChildMetadata(center);
            if (strokeColor != center.getMetaInteger("color", Color.WHITE)) {
                center.setMetaInteger("color", strokeColor);
                center.refresh(_mapView.getMapEventDispatcher(),
                        null, getClass());
            }
        }

        int fillColor = getFillColor();
        int emptyColor = 0xFFFFFF & fillColor;
        int system = _prefs.getRangeSystem();
        synchronized (this) {
            // Make sure we have at least one ring first
            if (_rings.isEmpty())
                _rings.add(createRing(radius));
            double zInc = 1d / (_rings.size() + 1);
            for (int i = 0; i < _rings.size(); i++) {
                Circle c = _rings.get(i);
                double r = radius * (i + 1);
                c.setRadius(r);
                c.setLineLabel(SpanUtilities.formatType(system, r, Span.METER));
                c.setZOrder(zOrder += zInc);
                c.setStrokeColor(strokeColor);
                c.setStrokeWeight(getStrokeWeight());
                c.setBasicLineStyle(getBasicLineStyle());
                c.setStyle(getStyle());
                c.setMetaBoolean("addToObjList", false);
                setChildMetadata(c);
                if (i < _rings.size() - 1) {
                    c.setFillColor(emptyColor);
                    c.setHeightStyle(Polyline.HEIGHT_STYLE_OUTLINE_SIMPLE);
                } else {
                    c.setFillColor(fillColor);
                    c.setHeightStyle(Polyline.HEIGHT_STYLE_POLYGON
                            | Polyline.HEIGHT_STYLE_OUTLINE_SIMPLE);
                }
                if (c.getGroup() == null) {
                    c.setVisible(getVisible());
                    _childGroup.addItem(c);
                }
            }
        }
    }

    private void setChildMetadata(MapItem mi) {
        mi.setTitle(getTitle());
        mi.toggleMetaData("labels_on", hasMetaValue("labels_on"));
        mi.toggleMetaData("editable", getEditable());
        mi.setRadialMenu(getRadialMenuPath());
        mi.setMetaString("shapeUID", getUID());
        mi.setMovable(getMovable());
        mi.setHeight(getHeight());
        mi.setAltitudeMode(getAltitudeMode());
        mi.setClickable(getClickable());
    }

    /**
     * Set the title of the circle, including the name of its center marker
     * @param title Circle title
     */
    @Override
    public void setTitle(String title) {
        super.setTitle(title);
        refresh();
    }

    @Override
    public void setStyle(int style) {
        super.setStyle(style);
        refresh();
    }

    @Override
    protected void onVisibleChanged() {
        _childGroup.setVisible(getVisible());
        super.onVisibleChanged();
    }

    @Override
    public void setMetaString(String key, String value) {
        super.setMetaString(key, value);
        if (MENU_METADATA.contains(key))
            refresh();
    }

    @Override
    public void setMetaBoolean(String key, boolean value) {
        super.setMetaBoolean(key, value);
        if (MENU_METADATA.contains(key))
            refresh();
    }

    @Override
    public void removeMetaData(String key) {
        super.removeMetaData(key);
        if (MENU_METADATA.contains(key))
            refresh();
    }

    /**
     * Set the center point of this circle
     * This will move the anchor marker as well
     * @param point Center point
     */
    public void setCenterPoint(GeoPointMetaData point) {

        GeoPoint oldCenter = _center.get();
        _center = point;
        for (Circle c : getRings())
            c.setCenterPoint(point);

        // Update center marker position
        Marker center = getCenterMarker();

        if (center != null && !center.getPoint().equals(point.get()))
            center.setPoint(point);

        // Update radius marker position (relative to old center)
        Marker radius = getRadiusMarker();
        if (!updatingRadiusMarker && radius != null && oldCenter != null
                && !oldCenter.equals(point.get())) {

            double bearing = GeoCalculations.bearingTo(oldCenter,
                    radius.getPoint());
            GeoPoint rPoint = GeoCalculations.pointAtDistance(point.get(),
                    bearing, getRadius());

            // If the radius marker is being set, then do not allow for a
            // looping condition where it keeps getting set.
            updatingRadiusMarker = true;
            radius.setPoint(new GeoPointMetaData(rPoint)
                    .setGeoPointSource(GeoPointMetaData.CALCULATED));
            updatingRadiusMarker = false;
        }

        onPointsChanged();
    }

    @Override
    public GeoPointMetaData getCenter() {
        Marker center = getCenterMarker();
        if (center != null)
            return center.getGeoPointMetaData();
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
            marker.setMetaBoolean("nevercot", true);
            marker.setMetaString("shapeUID", getUID());
            marker.setVisible(getVisible());
            _childGroup.addItem(marker);
        }
        _centerMarker = marker;
        setCenterPoint(marker.getGeoPointMetaData());
        marker.addOnPointChangedListener(this);
        marker.addOnGroupChangedListener(this);
        setTitle(getTitle());
    }

    /**
     * Get the center marker for this circle
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
     * Set the radius using a point from the center
     * @param point Point
     */
    public void setRadiusPoint(GeoPoint point) {
        GeoPointMetaData center = getCenter();
        if (center != null)
            setRadius(center.get().distanceTo(point));
    }

    /**
     * Set the radius marker
     * @param marker Radius marker
     */
    public void setRadiusMarker(Marker marker) {
        if (_radiusMarker == marker)
            return;
        if (_radiusMarker != null) {
            _radiusMarker.removeOnPointChangedListener(this);
            _radiusMarker.removeOnGroupChangedListener(this);
        }
        _radiusMarker = marker;
        if (marker != null) {
            setRadiusPoint(marker.getPoint());
            marker.addOnPointChangedListener(this);
            marker.addOnGroupChangedListener(this);
        }
    }

    /**
     * Get the radius marker for this center
     * @return Radius marker
     */
    public Marker getRadiusMarker() {
        return _radiusMarker;
    }

    /**
     * The center marker acts as the anchor for this circle
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
    public GeoPoint[] getPoints() {
        Circle outer = getOutermostRing();
        if (outer != null)
            return outer.getPoints();
        return new GeoPoint[0];
    }

    @Override
    public GeoPointMetaData[] getMetaDataPoints() {
        Circle outer = getOutermostRing();
        if (outer != null)
            return outer.getMetaDataPoints();
        return new GeoPointMetaData[0];
    }

    /**
     * Bounds is equivalent to the bonds of the outermost circle
     */
    @Override
    public GeoBounds getBounds(MutableGeoBounds bounds) {
        Circle outer = getOutermostRing();
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
        // We need extra space to fit the rings and center marker on top
        // Negative infinity does not change if you add to it
        // Also we want to keep any custom markers on top
        if (Double.compare(zOrder, Double.NEGATIVE_INFINITY) == 0) {
            Marker center = getCenterMarker();
            Marker radius = getRadiusMarker();
            if (center == null || isCenterShapeMarker())
                zOrder = -1000d;
            else
                zOrder = center.getZOrder();
            if (radius != null)
                zOrder = Math.max(zOrder, radius.getZOrder());
        }
        super.setZOrder(zOrder);
        refresh();
    }

    @Override
    public void setHeight(double height) {
        super.setHeight(height);
        refresh();
    }

    @Override
    public void setAltitudeMode(AltitudeMode altitudeMode) {
        super.setAltitudeMode(altitudeMode);
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

    @Override
    protected void onStrokeStyleChanged() {
        super.onStrokeStyleChanged();
        refresh();
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
            // Make sure we have rings and a center marker
            setCenterMarker(getCenterMarker());
            _prefs.registerListener(this);
        } else
            _prefs.unregisterListener(this);
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
        if (item == _radiusMarker)
            setRadiusMarker(null);
        persist();
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        if (item == _centerMarker)
            setCenterPoint(item.getGeoPointMetaData());
        if (item == _radiusMarker)
            setRadiusPoint(item.getPoint());
        persist();
    }

    /**
     * Private scope persist call - should only be used when an outside event
     * changes the attributes of this circle
     */
    private void persist() {
        if (getGroup() != null && hasMetaValue("archive"))
            persist(_mapView.getMapEventDispatcher(), null, getClass());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {

        if (key == null)
            return;

        if (UnitPreferences.RANGE_SYSTEM.equals(key))
            refresh();
    }

    /**************************** Listeners ***************************/

    public interface OnRadiusChangedListener {
        void onRadiusChanged(DrawingCircle circle, double oldRadius);
    }

    public synchronized void addOnRadiusChangedListener(
            OnRadiusChangedListener l) {
        _radiusListeners.add(l);
    }

    public synchronized void removeOnRadiusChangedListener(
            OnRadiusChangedListener l) {
        _radiusListeners.remove(l);
    }

    protected void onRadiusChanged(double oldRadius) {
        List<OnRadiusChangedListener> listeners;
        synchronized (this) {
            listeners = new ArrayList<>(_radiusListeners);
        }
        for (OnRadiusChangedListener l : listeners)
            l.onRadiusChanged(this, oldRadius);
    }

    /***************************** Export *****************************/

    @Override
    public Bundle preDrawCanvas(CapturePP capture) {
        // Let the child items draw themselves
        return null;
    }

    @Override
    public void drawCanvas(CapturePP cap, Bundle data) {
        // Let the child items draw themselves
    }

    @Override
    public boolean isSupported(Class<?> target) {
        return CotEvent.class.equals(target) ||
                Folder.class.equals(target) ||
                KMZFolder.class.equals(target) ||
                MissionPackageExportWrapper.class.equals(target) ||
                GPXExportWrapper.class.equals(target) ||
                OGRFeatureExportWrapper.class.equals(target);
    }

    @Override
    public Object toObjectOf(Class<?> target, ExportFilters filters) {
        if (CotEvent.class.equals(target))
            return toCot();
        else if (MissionPackageExportWrapper.class.equals(target))
            return new MissionPackageExportWrapper(true, getUID());
        else if (Folder.class.equals(target))
            return toKml();
        else if (KMZFolder.class.equals(target))
            return toKmz();
        else if (GPXExportWrapper.class.equals(target))
            return toGpx();
        else if (OGRFeatureExportWrapper.class.equals(target))
            return toOgrGeomtry();

        return null;
    }

    /**
     * Generate a CoT event for this circle
     * @return New CoT event or null if failed
     */
    protected CotEvent toCot() {
        CotEvent event = new CotEvent();

        CoordinatedTime time = new CoordinatedTime();
        event.setTime(time);
        event.setStart(time);
        event.setStale(time.addDays(1));

        event.setUID(getUID());
        event.setVersion("2.0");
        event.setHow("h-e");

        event.setPoint(new CotPoint(getCenter().get()));
        event.setType(getType());

        CotDetail detail = new CotDetail("detail");
        event.setDetail(detail);

        // This uses the KML-friendly CoT detail format
        // It'd be nicer to rewrite this completely but for compatibility
        // we need to use this dusty old format
        CotDetail shape = new CotDetail("shape");

        for (Circle ring : getRings()) {
            double radius = ring.getRadius();
            CotDetail ellipseDetail = new CotDetail("ellipse");
            ellipseDetail.setAttribute("major", String.valueOf(radius));
            ellipseDetail.setAttribute("minor", String.valueOf(radius));
            ellipseDetail.setAttribute("angle", "360");
            shape.addChild(ellipseDetail);
        }

        CotDetail lineColor = new CotDetail("color");
        lineColor.setInnerText(getColorString(getStrokeColor()));
        CotDetail lineWidth = new CotDetail("width");
        lineWidth.setInnerText(String.valueOf(getStrokeWeight()));
        CotDetail lineStyle = new CotDetail("LineStyle");
        lineStyle.addChild(lineColor);
        lineStyle.addChild(lineWidth);

        CotDetail polyColor = new CotDetail("color");
        polyColor.setInnerText(getColorString(getFillColor()));
        CotDetail polyStyle = new CotDetail("PolyStyle");
        polyStyle.addChild(polyColor);

        CotDetail style = new CotDetail("Style");
        style.addChild(lineStyle);
        style.addChild(polyStyle);

        CotDetail styleLink = new CotDetail("link");
        styleLink.setAttribute("type", "b-x-KmlStyle");
        styleLink.setAttribute("uid", getUID() + ".Style");
        styleLink.setAttribute("relation", "p-c");
        styleLink.addChild(style);
        shape.addChild(styleLink);

        // Marker references
        CotDetail centerLink = createLinkDetail(getCenterMarker(),
                "p-p-CenterAnchor");
        if (centerLink != null)
            shape.addChild(centerLink);
        CotDetail radiusLink = createLinkDetail(getRadiusMarker(),
                "p-p-RadiusAnchor");
        if (radiusLink != null)
            shape.addChild(radiusLink);

        detail.addChild(shape);

        CotDetailManager.getInstance().addDetails(this, event);

        return event;
    }

    private static CotDetail createLinkDetail(MapItem item, String relation) {
        if (item == null || item.hasMetaValue("nevercot"))
            return null;
        CotDetail d = new CotDetail("link");
        d.setAttribute("relation", relation);
        d.setAttribute("type", item.getType());
        d.setAttribute("uid", item.getUID());
        return d;
    }

    private static String getColorString(int color) {
        String ZEROES = "00000000";
        String s = Integer.toHexString(color);
        return s.length() < 8 ? ZEROES.substring(s.length()) + s : s;
    }

    public Folder toKml() {
        MapGroup group = getGroup();
        String title = ATAKUtilities.getDisplayName(this);
        String groupTitle = group != null ? group.getFriendlyName() : title;
        try {
            // style inner ring
            Style irstyle = new Style();
            LineStyle lstyle = new LineStyle();
            lstyle.setColor(KMLUtil.convertKmlColor(getStrokeColor()));
            lstyle.setWidth(2F);
            irstyle.setLineStyle(lstyle);
            PolyStyle pstyle = new PolyStyle();
            pstyle.setColor(KMLUtil.convertKmlColor(getFillColor()));

            // if fully transparent, then no fill, otherwise check fill mask
            //Note Circle currently does have STYLE_FILLED_MASK set by default
            int a = (getFillColor() >> 24) & 0xFF;
            if (a == 0)
                pstyle.setFill(0);
            else
                pstyle.setFill(1);
            pstyle.setOutline(1);
            irstyle.setPolyStyle(pstyle);

            String innerRingId = KMLUtil.hash(irstyle);
            irstyle.setId(innerRingId);

            // style outer ring
            Style orstyle = new Style();
            lstyle = new LineStyle();
            lstyle.setColor(KMLUtil.convertKmlColor(getStrokeColor()));
            lstyle.setWidth(2F);
            orstyle.setLineStyle(lstyle);
            pstyle = new PolyStyle();
            pstyle.setColor(KMLUtil.convertKmlColor(getFillColor()));
            // if fully transparent, then no fill, otherwise check fill mask
            //Note Circle currently does have STYLE_FILLED_MASK set by default
            a = (getFillColor() >> 24) & 0xFF;
            if (a == 0)
                pstyle.setFill(0);
            else
                pstyle.setFill(1);

            pstyle.setOutline(1);
            orstyle.setPolyStyle(pstyle);

            String outerRingId = KMLUtil.hash(orstyle);
            orstyle.setId(outerRingId);

            Folder folder = new Folder();
            folder.setName(groupTitle);
            List<StyleSelector> styles = new ArrayList<>();
            styles.add(irstyle);
            styles.add(orstyle);
            folder.setStyleSelector(styles);
            List<Feature> folderFeatures = new ArrayList<>();
            folder.setFeatureList(folderFeatures);

            boolean continuousScroll = _mapView != null
                    && _mapView.isContinuousScrollEnabled();

            boolean clampToGroundKMLElevation = Double.isNaN(getHeight())
                    || Double.compare(getHeight(), 0.0) == 0;

            List<Circle> rings = getRings();
            if (rings.size() > 1) {
                Placemark innerPlacemark = new Placemark();
                innerPlacemark.setId(getUID() + title + " inner");
                innerPlacemark.setName(title);
                innerPlacemark.setStyleUrl("#" + innerRingId);
                innerPlacemark.setVisibility(getVisible() ? 1 : 0);

                List<Geometry> innerGeomtries = new ArrayList<>();
                innerPlacemark.setGeometryList(innerGeomtries);

                if (rings.size() > 2) {
                    // if we have more than 2 rings, the inner rings are placed in a Multi Geometry
                    List<Geometry> innerRings = new ArrayList<>();

                    // draw the inner rings
                    for (int r = 0; r < rings.size() - 1; r++) {
                        GeoPointMetaData[] pts = rings.get(r)
                                .getMetaDataPoints();
                        Polygon polygon = KMLUtil.createPolygonWithLinearRing(
                                pts, getUID() + ".Ring" + (r + 1),
                                clampToGroundKMLElevation,
                                continuousScroll && GeoCalculations
                                        .crossesIDL(pts, 0, pts.length),
                                getHeight());
                        if (polygon == null) {
                            Log.w(TAG,
                                    "Unable to create inner ring KML Polygon");
                            continue;
                        }

                        innerRings.add(polygon);
                    }

                    if (innerRings.size() > 0) {
                        MultiGeometry mg = new MultiGeometry();
                        mg.setGeometryList(innerRings);
                        innerGeomtries.add(mg);
                    } else
                        Log.w(TAG,
                                "Unable to add any of the inner rings KML MultiGeometry Polygon, skipping...");
                } else {
                    // just one inner ring, no need for Multi Geometry
                    GeoPointMetaData[] pts = rings.get(0).getMetaDataPoints();

                    Polygon polygon = KMLUtil.createPolygonWithLinearRing(pts,
                            getUID() + ".Ring1", clampToGroundKMLElevation,
                            continuousScroll && GeoCalculations.crossesIDL(pts,
                                    0, pts.length),
                            getHeight());
                    if (polygon == null) {
                        Log.w(TAG,
                                "Unable to create inner ring KML Polygon");
                    } else {
                        innerGeomtries.add(polygon);
                    }
                }

                if (innerGeomtries.size() > 0)
                    folderFeatures.add(innerPlacemark);
                else
                    Log.w(TAG,
                            "Unable to add any of the inner rings KML Polygon, skipping...");
            }

            // always create Placemark for outer ring
            Placemark outerPlacemark = new Placemark();
            outerPlacemark.setId(getUID() + title + " outer");
            outerPlacemark.setName(title);
            outerPlacemark.setStyleUrl("#" + outerRingId);
            outerPlacemark.setVisibility(getVisible() ? 1 : 0);

            GeoPointMetaData[] pts = rings.get(rings.size() - 1)
                    .getMetaDataPoints();
            Polygon polygon = KMLUtil.createPolygonWithLinearRing(pts,
                    title + " text", clampToGroundKMLElevation,
                    continuousScroll &&
                            GeoCalculations.crossesIDL(pts, 0, pts.length),
                    getHeight());
            if (polygon == null) {
                Log.w(TAG, "Unable to create outer ring KML Polygon");
                return null;
            }

            List<Geometry> outerGeomtries = new ArrayList<>();
            outerPlacemark.setGeometryList(outerGeomtries);
            outerGeomtries.add(polygon);
            folderFeatures.add(outerPlacemark);

            return folder;
        } catch (Exception e) {
            Log.e(TAG, "Export of DrawingCircle to KML failed", e);
        }
        return null;
    }

    protected KMZFolder toKmz() {
        Folder f = toKml();
        if (f == null)
            return null;
        return new KMZFolder(f);
    }

    protected GPXExportWrapper toGpx() {
        GPXExportWrapper folder = new GPXExportWrapper();

        //add center point
        String name = ATAKUtilities.getDisplayName(this);
        GeoPointMetaData point = getCenter();
        if (point != null) {
            GpxWaypoint wp = new GpxWaypoint();
            wp.setLat(point.get().getLatitude());
            wp.setLon(point.get().getLongitude());

            if (point.get().isAltitudeValid()) {
                // This seems like it should be MSL.   Not documented in the spec
                // https://productforums.google.com/forum/#!topic/maps/ThUvVBoHAvk
                final double alt = EGM96.getMSL(point.get());
                wp.setEle(alt);
            }

            wp.setName(name + " Center");
            wp.setDesc(getUID() + " " + getMetaString("remarks", null));
            folder.getExports().add(wp);
        }

        //now add rings
        GpxTrack t = new GpxTrack();
        List<GpxTrackSegment> trkseg = new ArrayList<>();
        t.setSegments(trkseg);

        boolean continuousScroll = _mapView != null
                && _mapView.isContinuousScrollEnabled();
        List<Circle> rings = getRings();
        for (Circle circle : rings) {
            GpxTrackSegment seg = new GpxTrackSegment();
            trkseg.add(seg);
            List<GpxWaypoint> trkpt = new ArrayList<>();
            seg.setPoints(trkpt);

            GeoPoint[] points = circle.getPoints();
            double unwrap = 0;
            if (continuousScroll && GeoCalculations.crossesIDL(points,
                    0, points.length))
                unwrap = 360;

            for (GeoPoint point1 : points)
                trkpt.add(RouteGpxIO.convertPoint(point1, unwrap));

            //loop back to the first point
            trkpt.add(RouteGpxIO.convertPoint(points[0], unwrap));
        }

        if (t.getSegments() != null && t.getSegments().size() > 0) {
            t.setName(name);
            t.setDesc(getUID());
            folder.getExports().add(t);
        }

        return folder;
    }

    protected OGRFeatureExportWrapper toOgrGeomtry() {
        String name = ATAKUtilities.getDisplayName(this);
        String groupName = name;
        if (getGroup() != null)
            groupName = getGroup().getFriendlyName();
        OGRFeatureExportWrapper folder = new OGRFeatureExportWrapper(groupName);
        List<OGRFeatureExportWrapper.NamedGeometry> geometries = new ArrayList<>();

        boolean continuousScroll = _mapView != null
                && _mapView.isContinuousScrollEnabled();
        int ringCount = 1;
        List<Circle> rings = getRings();
        for (Circle circle : rings) {
            org.gdal.ogr.Geometry geometry = new org.gdal.ogr.Geometry(
                    org.gdal.ogr.ogrConstants.wkbLineString);
            GeoPoint[] points = circle.getPoints();
            double unwrap = 0;
            if (continuousScroll && GeoCalculations.crossesIDL(points, 0,
                    points.length))
                unwrap = 360;
            for (GeoPoint point : points)
                OGRFeatureExportWrapper.addPoint(geometry, point, unwrap);

            //loop back to the first point
            OGRFeatureExportWrapper.addPoint(geometry, points[0], unwrap);
            String ringName = name;
            if (rings.size() > 1)
                ringName += " Ring " + (ringCount++);
            geometries.add(new OGRFeatureExportWrapper.NamedGeometry(geometry,
                    ringName));
        }

        folder.addGeometries(org.gdal.ogr.ogrConstants.wkbLineString,
                geometries);
        return folder;
    }

    @Override
    public double getArea() {
        return getRadius() * getRadius() * Math.PI;
    }

    @Override
    public double getPerimeterOrLength() {
        return 2 * Math.PI * getRadius();
    }
}
