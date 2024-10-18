
package com.atakmap.android.drawing.mapItems;

import android.graphics.Color;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.gpx.GpxTrack;
import com.atakmap.android.gpx.GpxTrackSegment;
import com.atakmap.android.gpx.GpxWaypoint;
import com.atakmap.android.importexport.ExportFilters;
import com.atakmap.android.importexport.Exportable;
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
import com.atakmap.android.missionpackage.export.MissionPackageExportWrapper;
import com.atakmap.android.preference.UnitPreferences;
import com.atakmap.android.routes.RouteGpxIO;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.AngleUtilities;
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
import java.util.List;
import java.util.UUID;

/**
 * An ellipse with one or more rings of variable radii
 */
public class DrawingEllipse extends Shape implements AnchoredMapItem,
        ParentMapItem, Exportable,
        MapItem.OnGroupChangedListener,
        PointMapItem.OnPointChangedListener {

    private static final String TAG = "DrawingEllipse";

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
        _childGroup.setMetaString("shapeUID", uid);
        setType(COT_TYPE);
        setMetaBoolean("removable", true);
        setMetaBoolean("editable", true);
        setMetaBoolean("archive", true);
        setMetaBoolean("ignoreRender", true);
        setMovable(true);
        setMetaString("iconUri", ATAKUtilities.getResourceUri(
                mapView.getContext(), R.drawable.ic_circle));
        setRadialMenu("menus/drawing_ellipse_menu.xml");
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
            // Track which ellipses were removed
            _rings.removeAll(ellipses);
            List<Ellipse> removed = new ArrayList<>(_rings);

            // Add new ellipses
            _rings.clear();
            _rings.addAll(ellipses);

            // Clean up old ones
            for (Ellipse el : removed) {
                if (el.getGroup() == getChildMapGroup())
                    el.removeFromGroup();
            }
        }
        refresh();
    }

    /**
     * Get the list of ellipses part of this group
     * @return List of ellipses
     */
    public synchronized List<Ellipse> getEllipses() {
        return new ArrayList<>(_rings);
    }

    /**
     * Get the outer-most ellipse which is used for reading and writing
     * @return Outer-most ellipse
     */
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
        boolean labelsOn = getMetaBoolean("labels_on", false);

        Marker center = getCenterMarker();
        if (center != null && isCenterShapeMarker()) {
            center.setTitle(getTitle());
            center.setZOrder(zOrder - 1);
            center.setClickable(getClickable());
            center.setEditable(getEditable());
            center.setMovable(getMovable());
            center.setHeight(getHeight());
            if (strokeColor != center.getMetaInteger("color", Color.WHITE)) {
                center.setMetaInteger("color", strokeColor);
                center.refresh(_mapView.getMapEventDispatcher(),
                        null, getClass());
            }
            center.toggleMetaData("labels_on", labelsOn);
        }

        UnitPreferences prefs = new UnitPreferences(_mapView);
        int rangeSystem = prefs.getRangeSystem();

        List<Ellipse> rings = getEllipses();
        double zInc = 1d / rings.size();
        for (Ellipse e : rings) {
            e.setTitle(getTitle());
            e.setMetaString("shapeUID", getUID());
            e.setRadialMenu(getRadialMenuPath());
            e.setEditable(getEditable());
            e.setClickable(getClickable());
            e.setCenter(_center);
            e.setStyle(getStyle());
            e.setStrokeStyle(getStrokeStyle());
            e.setStrokeColor(getStrokeColor());
            e.setStrokeWeight(getStrokeWeight());
            e.setFillColor(getFillColor());
            e.setZOrder(zOrder += zInc);
            e.setHeight(getHeight());
            e.setHeightStyle(Polyline.HEIGHT_STYLE_POLYGON
                    | Polyline.HEIGHT_STYLE_OUTLINE_SIMPLE);
            e.setMetaBoolean("addToObjList", false);
            e.toggleMetaData("labels_on", labelsOn);
            if (labelsOn) {
                String width = SpanUtilities.formatType(rangeSystem,
                        e.getWidth(), Span.METER);
                String length = SpanUtilities.formatType(rangeSystem,
                        e.getLength(), Span.METER);
                e.setLineLabel(_mapView.getContext().getString(
                        R.string.ellipse_label_format, width, length));
            } else
                e.setLineLabel(null);
            if (e.getGroup() == null)
                _childGroup.addItem(e);
        }
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

    /**
     * Get the center {@link GeoPoint} for this ellipse
     * @return Center point
     */
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
            marker.setRadialMenu(getRadialMenuPath());
            marker.setMetaBoolean("nevercot", true);
            marker.setMetaBoolean("removable", true);
            _childGroup.addItem(marker);
        }
        _centerMarker = marker;
        setCenterPoint(marker.getGeoPointMetaData());
        marker.addOnPointChangedListener(this);
        marker.addOnGroupChangedListener(this);
        marker.setMetaString("shapeUID", getUID());
        refresh();
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
     * Get angle/heading of this ellipse
     * @return Angle in degrees
     */
    public double getAngle() {
        Ellipse outer = getOutermostEllipse();
        return outer != null ? outer.getAngle() : 0;
    }

    /**
     * Set the angle/heading of this ellipse
     * @param angle Angle in degrees
     */
    public void setAngle(double angle) {
        Ellipse outer = getOutermostEllipse();
        if (outer != null)
            outer.setAngle(angle);
    }

    /**
     * Get the width of the outer ellipse (perpendicular to the angle)
     * @return Width in meters
     */
    public double getWidth() {
        Ellipse outer = getOutermostEllipse();
        return outer != null ? outer.getWidth() : 0;
    }

    /**
     * Set the width of the outer ellipse (perpendicular to the angle)
     * @param width Width in meters
     */
    public void setWidth(double width) {
        Ellipse outer = getOutermostEllipse();
        if (outer != null)
            outer.setWidth(width);
    }

    /**
     * Get the length of the outer ellipse (parallel to the angle)
     * @return Length in meters
     */
    public double getLength() {
        Ellipse outer = getOutermostEllipse();
        return outer != null ? outer.getLength() : 0;
    }

    /**
     * Set the length of the outer ellipse (parallel to the angle)
     * @param length Length in meters
     */
    public void setLength(double length) {
        Ellipse outer = getOutermostEllipse();
        if (outer != null)
            outer.setLength(length);
    }

    /**
     * Set the dimensions of the outer ellipse
     * @param center Center point
     * @param width Width of the ellipse in meters
     * @param length Length of the ellipse in meters
     * @param angle Angle/heading in degrees
     */
    public void setDimensions(GeoPointMetaData center, double width,
            double length, double angle) {
        Ellipse outer = getOutermostEllipse();
        if (outer != null)
            outer.setDimensions(center, width / 2, length / 2, angle);
        setCenterPoint(center);
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
     * Bounds is equivalent to the bonds of the outermost ellipse
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
     * Set the title of the ellipse, including the name of its center marker
     * @param title Ellipse title
     */
    @Override
    public void setTitle(String title) {
        super.setTitle(title);
        refresh();
    }

    @Override
    protected void onStyleChanged() {
        refresh();
        super.onStyleChanged();
    }

    @Override
    protected void onStrokeStyleChanged() {
        super.onStrokeStyleChanged();
        refresh();
    }

    @Override
    protected void onVisibleChanged() {
        _childGroup.setVisible(getVisible());
        super.onVisibleChanged();
    }

    @Override
    protected void onClickableChanged() {
        refresh();
        super.onClickableChanged();
    }

    @Override
    protected void onZOrderChanged() {
        refresh();
        super.onZOrderChanged();
    }

    @Override
    protected void onFillColorChanged() {
        refresh();
        super.onFillColorChanged();
    }

    @Override
    protected void onStrokeColorChanged() {
        refresh();
        super.onStrokeColorChanged();
    }

    @Override
    protected void onStrokeWeightChanged() {
        refresh();
        super.onStrokeWeightChanged();
    }

    @Override
    protected void onHeightChanged() {
        refresh();
        super.onHeightChanged();
    }

    @Override
    public MapGroup getChildMapGroup() {
        return _childGroup;
    }

    @Override
    public double getArea() {
        Ellipse outer = getOutermostEllipse();
        return outer != null ? outer.getArea() : Double.NaN;
    }

    @Override
    public double getPerimeterOrLength() {
        Ellipse outer = getOutermostEllipse();
        return outer != null ? outer.getPerimeterOrLength() : Double.NaN;
    }

    @Override
    public void toggleMetaData(String key, boolean value) {
        super.toggleMetaData(key, value);

        if (key.equals("labels_on"))
            refresh();
    }

    /**
     * Keep the child map group with whichever group this ellipse is moved to
     * @param added True if ellipse added to group
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
            return toOgrGeometry();

        return null;
    }

    /**
     * Generate a CoT event for this ellipse
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

        // Store ellipse data in the XSD CoT shape schema format
        // for backwards compatibility
        CotDetail shape = new CotDetail("shape");
        for (Ellipse ellipse : getEllipses()) {

            // Extra property used to maintain the same displayed width, length,
            // and angle across restarts in cases where width > length
            // Major/minor is not an intuitive way to store ellipse data for
            // this purpose
            boolean swapAxis = ellipse.getWidth() > ellipse.getLength();

            CotDetail ellipseDetail = new CotDetail("ellipse");
            ellipseDetail.setAttribute("major", String.valueOf(
                    ellipse.getMajorAxis()));
            ellipseDetail.setAttribute("minor", String.valueOf(
                    ellipse.getMinorAxis()));

            double angle = AngleUtilities.wrapDeg(getAngle()
                    + (swapAxis ? 90 : 0));
            ellipseDetail.setAttribute("angle", String.valueOf(angle));
            if (swapAxis)
                ellipseDetail.setAttribute("swapAxis", "true");
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

        // Marker reference
        if (!isCenterShapeMarker())
            shape.addChild(
                    createLinkDetail(getCenterMarker(), "p-p-CenterAnchor"));

        detail.addChild(shape);

        CotDetailManager.getInstance().addDetails(this, event);

        return event;
    }

    private static CotDetail createLinkDetail(MapItem item, String relation) {
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

    protected Folder toKml() {
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

            List<Ellipse> rings = getEllipses();
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
                                pts, getUID() + ".Ellipse" + (r + 1),
                                clampToGroundKMLElevation,
                                continuousScroll && GeoCalculations
                                        .crossesIDL(pts, 0, pts.length),
                                getHeight());

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
                            getUID() + ".Ellipse1", clampToGroundKMLElevation,
                            continuousScroll && GeoCalculations.crossesIDL(pts,
                                    0, pts.length),
                            getHeight());
                    innerGeomtries.add(polygon);
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

            List<Geometry> outerGeomtries = new ArrayList<>();
            outerPlacemark.setGeometryList(outerGeomtries);
            outerGeomtries.add(polygon);
            folderFeatures.add(outerPlacemark);

            return folder;
        } catch (Exception e) {
            Log.e(TAG, "Export to KML failed", e);
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
        List<Ellipse> rings = getEllipses();
        for (Ellipse ellipse : rings) {
            GpxTrackSegment seg = new GpxTrackSegment();
            trkseg.add(seg);
            List<GpxWaypoint> trkpt = new ArrayList<>();
            seg.setPoints(trkpt);

            GeoPoint[] points = ellipse.getPoints();
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

    protected OGRFeatureExportWrapper toOgrGeometry() {
        String name = ATAKUtilities.getDisplayName(this);
        String groupName = name;
        if (getGroup() != null)
            groupName = getGroup().getFriendlyName();
        OGRFeatureExportWrapper folder = new OGRFeatureExportWrapper(groupName);
        List<OGRFeatureExportWrapper.NamedGeometry> geometries = new ArrayList<>();

        boolean continuousScroll = _mapView != null
                && _mapView.isContinuousScrollEnabled();
        int ringCount = 1;
        List<Ellipse> rings = getEllipses();
        for (Ellipse ellipse : rings) {
            org.gdal.ogr.Geometry geometry = new org.gdal.ogr.Geometry(
                    org.gdal.ogr.ogrConstants.wkbLineString);
            GeoPoint[] points = ellipse.getPoints();
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
                ringName += " Ellipse " + (ringCount++);
            geometries.add(new OGRFeatureExportWrapper.NamedGeometry(geometry,
                    ringName));
        }

        folder.addGeometries(org.gdal.ogr.ogrConstants.wkbLineString,
                geometries);
        return folder;
    }
}
