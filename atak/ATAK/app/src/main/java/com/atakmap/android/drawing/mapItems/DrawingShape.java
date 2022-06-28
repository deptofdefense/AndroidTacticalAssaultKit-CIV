
package com.atakmap.android.drawing.mapItems;

import android.graphics.Color;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.drawing.DrawingToolsMapComponent;
import com.atakmap.android.editableShapes.EditablePolyline;
import com.atakmap.android.importexport.KmlMapItemImportFactory;
import com.atakmap.android.importexport.handlers.ParentMapItem;
import com.atakmap.android.maps.Association;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.AreaUtilities;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.Coordinates;
import com.ekito.simpleKML.model.Geometry;
import com.ekito.simpleKML.model.LineString;
import com.ekito.simpleKML.model.Placemark;
import com.ekito.simpleKML.model.Polygon;
import com.ekito.simpleKML.model.Style;

import java.util.UUID;

public class DrawingShape extends EditablePolyline implements ParentMapItem {

    private String _shapeMarkerType;
    private final MapGroup childItemMapGroup;

    /**
     * Create a new drawing shape
     * @param mapView Map view instance
     * @param mapGroup The map group this item will be added to
     * @param uid UID
     */
    public DrawingShape(MapView mapView, MapGroup mapGroup, String uid) {
        super(mapView, uid);
        this.childItemMapGroup = mapGroup.addGroup();
        this.childItemMapGroup.setMetaString("shapeUID", uid);
        setMetaBoolean("editable", true);
        setMetaString("menu", getShapeMenu());
        this.setShapeMenu("menus/drawing_shape_geofence_menu.xml");
        this.setMetaString("iconUri", getDefaultIconUri());
        // Set fill to transparent; ATM we key off of this to not
        setFillColor(Color.TRANSPARENT);
        // show the fill by default when reloading from CoT
        setType(getCotType());
        setMetaBoolean("archive", true);
    }

    public DrawingShape(MapView mapView, String uid) {
        this(mapView, DrawingToolsMapComponent.getGroup(), uid);
    }

    @Override
    public void setTitle(final String title) {
        super.setTitle(title);

        Marker center = getShapeMarker();
        if (center != null) {
            center.setTitle(getTitle());
            center.refresh(MapView.getMapView().getMapEventDispatcher(), null,
                    this.getClass());
        }
    }

    @Override
    public void toggleMetaData(String key, boolean value) {
        super.toggleMetaData(key, value);

        if (key.equals("labels_on")) {
            // For the center marker radial highlight
            Marker center = getShapeMarker();
            if (center != null)
                center.toggleMetaData(key, value);
        }
    }

    /**
     * Ignored for open (non closed) Shapes
     * Invoke <code>setClosed(true)</code> prior to this method
     *
     * @param color the fill color in ARGB format.
     */
    @Override
    public void setFillColor(int color) {
        if (isClosed())
            super.setFillColor(color);
    }

    private String getDefaultIconUri() {
        return "android.resource://"
                + mapView.getContext().getPackageName()
                + "/" + (isClosed() ? R.drawable.shape
                        : R.drawable.polyline);
    }

    @Override
    public MapGroup getChildMapGroup() {
        return this.childItemMapGroup;
    }

    @Override
    public void setClosed(final boolean closed) {
        super.setClosed(closed);

        // All closed shapes have a marker
        if (closed && getMarker() == null) {
            this.setMetaString("closed_line", "true");

            String uid = UUID.randomUUID().toString();

            Marker m = new Marker(getCenter(), uid);
            m.setTitle(getTitle());
            m.setType(getMarkerPointType());
            m.setMetaBoolean("drag", false);
            m.setMetaBoolean("editable", true);
            m.setMovable(getMovable());

            // always hide these types of waypoints on the objects list
            m.setMetaBoolean("addToObjList", false);
            m.setMetaString("how", "h-g-i-g-o"); // don't autostale it

            m.setRadialMenu(getShapeMenu());
            m.setColor(getStrokeColor());
            m.setMetaString(getUIDKey(), getUID());
            setShapeMarker(m);
        }
        if (closed) {
            // if the item has already been added to the map, and the shape marker is created,
            // then setClosed is called.
            if (getGroup() != null && _shapeMarker != null
                    && _shapeMarker.getGroup() == null)
                childItemMapGroup.addItem(_shapeMarker);
        }

        if (getMarker() != null)
            getMarker().setVisible(closed);

        setMetaString("iconUri", getDefaultIconUri());
        this.refresh(this.mapView.getMapEventDispatcher(), null,
                this.getClass());
    }

    protected String getMarkerPointType() {
        if (_shapeMarkerType == null) {
            _shapeMarkerType = "shape_marker";
        }
        return _shapeMarkerType;
    }

    @Override
    public void setHeight(double height) {
        super.setHeight(height);
        Marker marker = getMarker();
        if (marker != null)
            marker.setHeight(height);
    }

    @Override
    public void setMovable(boolean movable) {
        super.setMovable(movable);
        Marker sm = getShapeMarker();
        if (sm != null)
            sm.setMovable(movable);
    }

    public void setMarkerPointType(String type) {
        _shapeMarkerType = type;
        this.setMetaString("marker_type", type);
    }

    protected String getCotType() {
        return "u-d-f";
    }

    public void setShapeMenu(String menu) {
        if (menu != null)
            setMetaString("shapeMenu", menu);
        else
            removeMetaData("shapeMenu");
    }

    public void setCornerMenu(String menu) {
        if (menu != null)
            setMetaString("cornerMenu", menu);
        else
            removeMetaData("cornerMenu");
    }

    public void setLineMenu(String menu) {
        if (menu != null)
            setMetaString("lineMenu", menu);
        else
            removeMetaData("lineMenu");
    }

    @Override
    protected String getShapeMenu() {
        return getMetaString("shapeMenu",
                "menus/drawing_shape_geofence_menu.xml");
    }

    @Override
    protected String getCornerMenu() {
        return getMetaString("cornerMenu",
                "menus/drawing_shape_corner_menu.xml");
    }

    @Override
    protected String getLineMenu() {
        return getMetaString("lineMenu", "menus/drawing_shape_line_menu.xml");
    }

    @Override
    protected void onPointsChanged() {
        // this may be an inappropriate definition of closed for kml actually...
        // what is a "closed" line string in kml? other than strange?
        // setClosed(getPoint(0).getPoint().equals(getPoint(getNumPoints()-1).getPoint()));

        // TODO: go ahead and make them physically the same here?

        super.onPointsChanged();
    }

    @Override
    public void setLineStyle(final int style) {
        super.setLineStyle(style);
        this.setMetaString("lineStyle", Integer.toString(style));
        this.setMetaInteger("line_type", style);
        this.refresh(this.mapView.getMapEventDispatcher(), null,
                this.getClass());
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup oldParent) {
        if (childItemMapGroup != null) {
            if (_shapeMarker != null)
                childItemMapGroup.addItem(_shapeMarker);
        }
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup oldParent) {
        super.onItemRemoved(item, oldParent);

        // Do not process deletion when transferring groups
        if (getMetaBoolean("__groupTransfer", false))
            return;

        // XXX - not quite sure why there are two child map groups
        if (childItemMapGroup != null) {
            final MapGroup parent = childItemMapGroup.getParentGroup();
            if (parent != null)
                parent.removeGroup(childItemMapGroup);
        }
    }

    public static class KmlDrawingShapeImportFactory extends
            KmlMapItemImportFactory {

        private static final String TAG = "DrawingShapeImportFactory";

        private final MapView mapView;

        public KmlDrawingShapeImportFactory(MapView mapView) {
            this.mapView = mapView;
        }

        @Override
        public MapItem instanceFromKml(Placemark placemark, MapGroup mapGroup) {

            String title = placemark.getName();

            Polygon polygon = KMLUtil
                    .getFirstGeometry(placemark, Polygon.class);
            LineString line = KMLUtil.getFirstGeometry(placemark,
                    LineString.class);

            if (polygon == null && line == null) {
                Log.e(TAG, "Placemark does not have a Polygon or LineString");
                return null;
            }

            Geometry geometry;
            Coordinates coords;
            boolean closed;

            if (polygon != null) {
                geometry = polygon;
                closed = true;

                if (polygon.getOuterBoundaryIs() == null
                        || polygon.getOuterBoundaryIs().getLinearRing() == null
                        ||
                        polygon.getOuterBoundaryIs().getLinearRing()
                                .getCoordinates() == null
                        ||
                        polygon.getOuterBoundaryIs().getLinearRing()
                                .getCoordinates().getList() == null) {
                    Log.e(TAG,
                            "Placemark does not have a Polygon OuterBoundaryIs");
                    return null;
                }

                coords = polygon.getOuterBoundaryIs().getLinearRing()
                        .getCoordinates();
            } else {
                geometry = line;
                closed = false;

                if (line.getCoordinates() == null
                        || line.getCoordinates().getList() == null) {
                    Log.e(TAG, "Placemark does not have a LineString");
                    return null;
                }

                coords = line.getCoordinates();
            }
            String uid = geometry.getId();
            DrawingShape shape = new DrawingShape(this.mapView,
                    mapGroup.addGroup(title), uid);

            shape.setClosed(closed);

            GeoPointMetaData[] points = KMLUtil.convertCoordinates(coords);
            if (points == null || points.length < 1) {
                Log.e(TAG, "Placemark does not have any Polygon points");
                return null;
            }

            int stroke = -1;
            int fill = -1;
            boolean filled = false;
            Style style = KMLUtil.getFirstStyle(placemark, Style.class);
            if (style != null && style.getLineStyle() != null)
                stroke = KMLUtil.parseKMLColor(style.getLineStyle().getColor());
            if (style != null && style.getPolyStyle() != null) {
                fill = KMLUtil.parseKMLColor(style.getPolyStyle().getColor());
                filled = style.getPolyStyle().getFill();
            }

            // if the shape is closed the setPoints should take care of it
            // XXX didn't write this so not positive if it is going to work
            shape.setPoints(points);
            shape.setLineStyle(Association.STYLE_SOLID);
            shape.setColor(stroke);
            if (closed && filled) {
                shape.setFillColor(fill);
            }

            return shape;
        }

        @Override
        public String getFactoryName() {
            return FACTORY_NAME;
        }

        static final String FACTORY_NAME = "u-d-f";
    }

    @Override
    public CotEvent toCot() {

        CotEvent cotEvent = super.toCot();
        cotEvent.setType(getCotType());
        cotEvent.setPoint(new CotPoint(getCenter().get()));

        CotDetailManager.getInstance().addDetails(this, cotEvent);

        return cotEvent;
    }

    @Override
    public double getArea() {
        if (!isClosed())
            return Double.NaN;

        return AreaUtilities.calcShapeArea(getPoints());
    }

}
