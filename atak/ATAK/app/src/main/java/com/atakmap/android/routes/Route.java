
package com.atakmap.android.routes;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.editableShapes.EditablePolyline;
import com.atakmap.android.gpx.Gpx;
import com.atakmap.android.image.ImageDropDownReceiver;
import com.atakmap.android.maps.DefaultMetaDataHolder;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.MetaDataHolder;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.nav.NavigationCue;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.android.util.EditAction;
import com.atakmap.app.R;
import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.spatial.file.export.GPXExportWrapper;
import com.atakmap.spatial.file.export.KMZFolder;
import com.atakmap.spatial.kml.FeatureHandler;
import com.atakmap.spatial.kml.KMLUtil;
import com.ekito.simpleKML.model.Feature;
import com.ekito.simpleKML.model.Folder;
import com.ekito.simpleKML.model.Placemark;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Pair;

/**
 * Handles automatic waypoint naming, stores metadata about the route.
 */
public class Route extends EditablePolyline {

    private static final String TAG = "Route";

    /**
     * Route changed listener
     */
    public interface OnRoutePointsChangedListener {
        void onRoutePointsChanged(Route route);
    }

    public interface OnRouteMethodChangedListener {
        void onRouteMethodChanged(Route route);
    }

    public interface OnRouteDirectionChangedListener {
        void onRouteDirectionChanged(Route route);
    }

    public interface OnRouteTypeChangedListener {
        void onRouteTypeChanged(Route route);
    }

    public interface OnRouteOrderChangedListener {
        void onRouteOrderChanged(Route route);
    }

    public static final String WAYPOINT_TYPE = "b-m-p-w";
    public static final String CONTROLPOINT_TYPE = "b-m-p-c";

    public static final double DEFAULT_STROKE_WEIGHT = 3.0d;
    public static final double EDITABLE_STROKE_WEIGHT = 4.0d;
    public static final double NAVIGATING_STROKE_WEIGHT = 6.0d;

    public static final int DEFAULT_ROUTE_COLOR = Color.WHITE;

    private String prefix_ = "CP";

    private RouteMethod routeMethod_ = RouteMethod.Driving;
    private RouteDirection routeDirection_ = RouteDirection.Infil;
    private RouteType routeType_ = RouteType.Primary;
    private RouteOrder routeOrder_ = RouteOrder.Ascending;
    private String planningMethod_; // first, last
    final MapGroup waypointGroup;

    private String transportationType = context
            .getString(R.string.routes_text33);

    public enum RouteMethod {
        Driving(0,
                "Driving",
                R.string.driving,
                R.drawable.ic_route_driving),
        Walking(1,
                "Walking",
                R.string.walking,
                R.drawable.ic_route_walking),
        Flying(2,
                "Flying",
                R.string.flying,
                R.drawable.ic_route_flying),
        Swimming(3,
                "Swimming",
                R.string.swimming,
                R.drawable.ic_route_swimming),
        Watercraft(4,
                "Watercraft",
                R.string.watercraft,
                R.drawable.ic_route_watercraft);

        final public String text;
        final public int id;
        final public int resourceid;
        final public int iconId;

        RouteMethod(int i, String t, int ri, int ic) {
            id = i;
            text = t;
            resourceid = ri;
            iconId = ic;
        }
    }

    public enum RouteDirection {
        Infil(0, "Infil", R.string.infil),
        Exfil(1, "Exfil", R.string.exfil);

        final public String text;
        final public int id;
        final public int resourceid;

        RouteDirection(int i, String t, int ri) {
            id = i;
            text = t;
            resourceid = ri;
        }
    }

    /**
     * Given a resource id, obtain the associated string. 
     * This should only be used with the enums in this class.
     */
    static String getLocalName(int resourceId) {
        return MapView.getMapView().getResources().getString(resourceId);
    }

    public enum RouteType {
        Primary(0, "Primary", R.string.primary),
        Secondary(1, "Secondary", R.string.secondary);

        final public String text;
        final public int id;
        final public int resourceid;

        RouteType(int i, String t, int ri) {
            text = t;
            id = i;
            resourceid = ri;
        }
    }

    public enum RouteOrder {
        Ascending(0, "Ascending Check Points", R.string.ascending_cp),
        Descending(1, "Descending Check Points", R.string.descending_cp);

        final String text;
        final int id;
        final int resourceid;

        RouteOrder(int i, String t, int ri) {
            text = t;
            id = i;
            resourceid = ri;
        }
    }

    private class RouteExchangePointAction extends ExchangePointAction {
        private final PointMapItem _newItem;
        private final PointMapItem _oldItem;

        public RouteExchangePointAction(int index, PointMapItem newItem,
                MapGroup addToOnSuccess) {
            super(index, newItem, addToOnSuccess);

            _newItem = newItem;
            _oldItem = getPointMapItem(index);
        }

        @Override
        public boolean run() {

            // Get the old nav cue if any
            NavigationCue oldCue = (_oldItem != null) ? getNavigationCues()
                    .get(_oldItem.getUID()) : null;

            boolean retValue = super.run();

            if (oldCue != null && _newItem.getType().equals(WAYPOINT_TYPE)) {
                setNavigationCueForPoint(_newItem.getUID(), oldCue);
            }

            return retValue;
        }
    }

    class RouteRemoveMarkerAction extends RemoveMarkerAction {
        private final PointMapItem _item;
        private final NavigationCue _cue;

        public RouteRemoveMarkerAction(PointMapItem item) {
            super(item);
            _item = item;
            _cue = getCueForPoint(item.getUID());
        }

        @Override
        public boolean run() {
            boolean retValue = super.run();
            removeNavigationCueForPoint(_item.getUID());
            return retValue;
        }

        @Override
        public void undo() {
            super.undo();
            if (_item.getGroup() != null)
                setNavigationCueForPoint(_item.getUID(), _cue);
        }
    }

    private class RouteRemovePointAction extends RemovePointAction {
        private final PointMapItem _item;
        private final NavigationCue _cue;

        public RouteRemovePointAction(int index) {
            super(index);
            _item = getPointMapItem(index);
            _cue = _item != null ? getCueForPoint(_item.getUID()) : null;
        }

        @Override
        public boolean run() {
            boolean retValue = super.run();

            // Remove the nav cue if there was one
            if (_item != null)
                removeNavigationCueForPoint(_item.getUID());

            return retValue;
        }

        @Override
        public void undo() {
            super.undo();
            if (_item != null && _item.getGroup() != null && _cue != null)
                setNavigationCueForPoint(_item.getUID(), _cue);
        }
    }

    public class RouteSetCueAction extends EditAction {
        private final String _uid;
        private final NavigationCue _oldCue, _newCue;

        public RouteSetCueAction(String cpUid, NavigationCue cue) {
            _uid = cpUid;
            _newCue = cue;
            _oldCue = getCueForPoint(cpUid);
        }

        @Override
        public boolean run() {
            if (_newCue == null)
                removeNavigationCueForPoint(_uid);
            else
                setNavigationCueForPoint(_uid, _newCue);
            return true;
        }

        @Override
        public void undo() {
            if (_oldCue == null)
                removeNavigationCueForPoint(_uid);
            else
                setNavigationCueForPoint(_uid, _oldCue);
        }

        @Override
        public String getDescription() {
            return null;
        }
    }

    public class RouteSetPointName extends EditAction {
        private final int _index;
        private final String _oldName, _newName;

        public RouteSetPointName(int pointIndex, String name) {
            _index = pointIndex;
            _newName = name;
            PointMapItem pmi;
            synchronized (Route.this) {
                pmi = indexToMarker.get(pointIndex);
            }
            _oldName = ATAKUtilities.getDisplayName(pmi);
        }

        @Override
        public boolean run() {
            PointMapItem pmi;
            synchronized (Route.this) {
                pmi = indexToMarker.get(_index);
            }
            if (pmi != null) {
                pmi.setMetaString("callsign", _newName);
                if (pmi instanceof Marker)
                    pmi.setTitle(_newName);
            }
            return true;
        }

        @Override
        public void undo() {
            PointMapItem pmi;
            synchronized (Route.this) {
                pmi = indexToMarker.get(_index);
            }
            if (pmi != null) {
                pmi.setMetaString("callsign", _oldName);
                if (pmi instanceof Marker)
                    pmi.setTitle(_oldName);
            }
        }

        @Override
        public String getDescription() {
            return null;
        }
    }

    public class RouteAddSegment extends EditAction {

        private final Collection<GeoPoint> _newPoints;
        private List<PointMapItem> _oldPoints;
        private boolean _skipFirst;
        private int _startIndex, _endIndex;

        public RouteAddSegment(Collection<GeoPoint> points, int startIndex,
                boolean skipFirst) {
            _newPoints = points;
            _skipFirst = skipFirst;
            _startIndex = startIndex;
            _endIndex = -1;
        }

        @Override
        public boolean run() {
            if (_newPoints == null || _newPoints.isEmpty())
                return false;
            // Convert points to control points
            Iterator<GeoPoint> iter = _newPoints.iterator();
            List<PointMapItem> cps = new ArrayList<>();
            while (iter.hasNext()) {
                GeoPoint gp = iter.next();

                // Skip first point since it's the last point of the route
                if (_skipFirst) {
                    _skipFirst = false;
                    continue;
                }

                // Create new control point
                cps.add(Route.createControlPoint(gp));
            }

            // Add new segment
            synchronized (Route.this) {
                int numPoints = getNumPoints();
                if (_startIndex < 0 || _startIndex > numPoints)
                    _startIndex = numPoints;
                if (_startIndex < numPoints && _endIndex == -1) {
                    SortedMap<Integer, PointMapItem> tail = indexToMarker
                            .tailMap(_startIndex);
                    if (!tail.isEmpty())
                        _endIndex = tail.firstKey();
                    else
                        _endIndex = numPoints;
                    if (_endIndex > _startIndex)
                        _oldPoints = removePoints(_startIndex, _endIndex);
                    _endIndex = _startIndex + _newPoints.size() - 1;
                }
            }
            addMarkers(_startIndex, cps);
            return true;
        }

        @Override
        public void undo() {
            int endIndex = _endIndex;
            if (endIndex == -1)
                endIndex = getNumPoints();
            removePoints(_startIndex, endIndex);
            if (!FileSystemUtils.isEmpty(_oldPoints))
                addMarkers(_startIndex, _oldPoints);
        }

        @Override
        public String getDescription() {
            return null;
        }
    }

    class RouteActionProvider implements ActionProviderInterface {
        @Override
        public EditAction newExchangePointAction(int index,
                PointMapItem newItem, MapGroup addToOnSuccess) {
            return new RouteExchangePointAction(index, newItem, addToOnSuccess);
        }

        @Override
        public EditAction newInsertPointAction(PointMapItem item, int index,
                MapGroup addToOnSuccess) {
            return new InsertPointAction(item, index, addToOnSuccess);
        }

        @Override
        public EditAction newMovePointAction(int index,
                GeoPointMetaData oldPoint,
                GeoPointMetaData newPoint) {
            return new MovePointAction(index, oldPoint, newPoint);
        }

        @Override
        public EditAction newRemovePointAction(int index) {
            return new RouteRemovePointAction(index);
        }

        @Override
        public EditAction newRemoveMarkerAction(PointMapItem item) {
            return new RouteRemoveMarkerAction(item);
        }
    }

    private final ArrayList<OnRoutePointsChangedListener> routePointsChangedListeners_ = new ArrayList<>();

    private final ArrayList<OnRouteMethodChangedListener> routeMethodChangedListeners_ = new ArrayList<>();
    private final ArrayList<OnRouteDirectionChangedListener> routeDirectionChangedListeners_ = new ArrayList<>();
    private final ArrayList<OnRouteTypeChangedListener> routeTypeChangedListeners_ = new ArrayList<>();
    private final ArrayList<OnRouteOrderChangedListener> routeOrderChangedListeners_ = new ArrayList<>();

    private boolean _refreshingMapItems = false;
    private boolean _ignorePointsChanged = false;

    private final Set<PointMapItem> waypoints;
    private SharedPreferences _prefs;

    private boolean addWaypointsToGroup = true;

    private final Map<String, NavigationCue> navigationCues = new ConcurrentHashMap<>();

    private final DefaultMetaDataHolder cpMeta; // used to describe control points for a route

    /**
     * A lighter weight PointMapItem capable of sharing metadata across the entire route.
     */
    public static class ControlPointMapItem extends PointMapItem {
        public ControlPointMapItem(final GeoPoint gp, final String uid) {
            super(gp, uid);
            setType(CONTROLPOINT_TYPE);
            setVisible(false);
        }

        void replaceMetaHolder(DefaultMetaDataHolder cpMeta) {
            metadata = cpMeta;
        }
    }

    /**
     * @param
     */
    protected Route(MapView mapView, long serialId, MetaDataHolder metadata,
            String uid) {
        super(mapView, serialId, metadata, uid);
        _actionProvider = new RouteActionProvider();

        MapGroup parentGroup = mapView.getRootGroup();
        waypointGroup = parentGroup.findMapGroup("Route");

        // XXX - see note in EditablePolyline:513
        this.setMetaBoolean("__ignoreRefresh", true);

        this.waypoints = Collections
                .newSetFromMap(new IdentityHashMap<PointMapItem, Boolean>());
        prefix_ = "";

        // XXX - see note in EditablePolyline:513
        this.removeMetaData("__ignoreRefresh");

        _prefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());
        this.setMetaString("iconUri", "android.resource://" + mapView
                .getContext().getPackageName() + "/" + R.drawable.ic_route);

        // values common across all control points in a route.
        cpMeta = new DefaultMetaDataHolder();
        cpMeta.setMetaString("type", CONTROLPOINT_TYPE);
        cpMeta.setMetaString("entry", "user");
        cpMeta.setMetaString("callsign", "");
        cpMeta.setMetaBoolean("editable", true);
        cpMeta.setMetaBoolean("movable", true);
        cpMeta.setMetaBoolean("removable", true);
        cpMeta.setMetaBoolean("navigating", false);
        cpMeta.setMetaBoolean("ready_to_nav", true);
        cpMeta.setMetaString("how", "h-g-i-g-o");
        cpMeta.setMetaBoolean("nevercot", true);
        cpMeta.setMetaBoolean("routeWaypoint", false);
        cpMeta.setMetaBoolean("addToObjList", false);
        setMetaBoolean("archive", true);
    }

    public Route(MapView mapView, String routeName, int color, String prefix,
            String uid, boolean addGrouplessWaypointsToGroup) {
        this(mapView, routeName, color, prefix, uid);
        addWaypointsToGroup = addGrouplessWaypointsToGroup;
    }

    public Route(MapView mapView, String routeName, int color, String prefix,
            String uid) {
        this(mapView, MapItem.createSerialId(), new DefaultMetaDataHolder(),
                uid);

        // XXX - see note in EditablePolyline:513
        this.setMetaBoolean("__ignoreRefresh", true);

        // Cot values
        this.setType("b-m-r");
        this.setMetaBoolean("addToObjList", true);

        prefix_ = prefix;

        setRouteOrder(RouteOrder.Ascending.text);
        setRouteMethod(RouteMethod.Driving.toString());
        setRouteDirection(RouteDirection.Infil.toString());
        setRouteType(RouteType.Primary.toString());
        setPlanningMethod("Infil");

        setTitle(routeName);
        setColor(color);
        setMetaString("assocSetUID", uid);

        setMetaString("callsign", routeName);

        // XXX - see note in EditablePolyline:513
        this.removeMetaData("__ignoreRefresh");

        _prefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());
    }

    @Override
    public void setFillColor(int color) {
        // Routes should not be filled
    }

    @Override
    public void setClosed(boolean closed) {
        // Routes should not be closed
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public void setZOrder(double zOrder) {
        // We shouldn't persist the route when changing its z-order
        boolean ignoreRefresh = getMetaBoolean("__ignoreRefresh", false);
        setMetaBoolean("__ignoreRefresh", true);
        super.setZOrder(zOrder);
        super.refresh(this.mapView.getMapEventDispatcher(), null,
                this.getClass());
        setMetaBoolean("__ignoreRefresh", ignoreRefresh);
    }

    @Override
    public void refresh(MapEventDispatcher d, Bundle b, Class<?> clazz) {
        if (this.getMetaBoolean("__ignoreRefresh", false))
            return;
        super.refresh(d, b, clazz);
    }

    public String getPrefix() {
        return prefix_;
    }

    public String setPrefix(String value) {
        return prefix_ = value;
    }

    /**
     * Add a route points changed listener
     *
     * @param listener the listener
     */
    public void addOnRoutePointsChangedListener(
            OnRoutePointsChangedListener listener) {
        routePointsChangedListeners_.add(listener);
    }

    /**
     * Remove a route points changed listener
     *
     * @param listener the listener
     */
    public void removeOnRoutePointsChangedListener(
            OnRoutePointsChangedListener listener) {
        routePointsChangedListeners_.remove(listener);
    }

    /**
     * Add a route method changed listener
     *
     * @param listener the listener
     */
    public synchronized void addOnRouteMethodChangedListener(
            OnRouteMethodChangedListener listener) {
        routeMethodChangedListeners_.add(listener);
    }

    /**
     * Add a route direction changed listener
     *
     * @param listener the listener
     */
    public synchronized void addOnRouteDirectionChangedListener(
            OnRouteDirectionChangedListener listener) {
        routeDirectionChangedListeners_.add(listener);
    }

    /**
     * Add a route type changed listener
     *
     * @param listener the listener
     */
    public synchronized void addOnRouteTypeChangedListener(
            OnRouteTypeChangedListener listener) {
        routeTypeChangedListeners_.add(listener);
    }

    /**
     * Add a route order changed listener
     *
     * @param listener the listener
     */
    public synchronized void addOnRouteOrderChangedListener(
            OnRouteOrderChangedListener listener) {
        routeOrderChangedListeners_.add(listener);
    }

    @Override
    public synchronized void setEditable(boolean editable) {
        super.setEditable(editable);
        final Set<PointMapItem> items = markerToIndex.keySet();
        for (final PointMapItem item : items)
            setEditable(item, editable);
    }

    private void setEditable(PointMapItem pmi, boolean editable) {
        if (pmi == null || !pmi.getType().equals(WAYPOINT_TYPE))
            return;
        if (editable) {
            pmi.setMetaString("menu",
                    getMetaString("editMenu", "menus/waypoint_edit.xml"));
            pmi.setMovable(true);
            pmi.setMetaBoolean("removable", true);
            pmi.setMetaBoolean("drag", true);
        } else {
            pmi.removeMetaData("menu");
            pmi.removeMetaData("movable");
            pmi.removeMetaData("removable");
            pmi.removeMetaData("drag");
        }
    }

    /**  
     * During navigation only show the navigation flag.   No other menu items make sense.
     */
    public synchronized void setNavigating(boolean state) {
        final Set<PointMapItem> items = markerToIndex.keySet();

        for (final PointMapItem item : items) {
            if (item.getType().equals(WAYPOINT_TYPE)) {
                if (state) {
                    item.setMetaString("menu", getMetaString("navMenu",
                            "menus/route_nav_menu.xml"));
                } else {
                    item.removeMetaData("menu");
                }
            }
        }
    }

    /**
     * Remove a route Method changed listener
     *
     * @param listener the listener
     */
    public synchronized void removeOnRouteMethodChangedListener(
            OnRouteMethodChangedListener listener) {
        routeMethodChangedListeners_.remove(listener);
    }

    /**
     * Remove a route Direction changed listener
     *
     * @param listener the listener
     */
    public synchronized void removeOnRouteDirectionChangedListener(
            OnRouteDirectionChangedListener listener) {
        routeDirectionChangedListeners_.remove(listener);
    }

    /**
     * Remove a route Type changed listener
     *
     * @param listener the listener
     */
    public synchronized void removeOnRouteTypeChangedListener(
            OnRouteTypeChangedListener listener) {
        routeTypeChangedListeners_.remove(listener);
    }

    /**
     * Remove a route Order changed listener
     *
     * @param listener the listener
     */
    public synchronized void removeOnRouteOrderChangedListener(
            OnRouteOrderChangedListener listener) {
        routeOrderChangedListeners_.remove(listener);
    }

    /**
     * Notifies listeners that a point's changed
     */
    protected void onRoutePointsChanged() {
        // Hack so we don't infinitely loop when we update a map item ourselves
        _refreshingMapItems = true;

        for (OnRoutePointsChangedListener listener : routePointsChangedListeners_)
            listener.onRoutePointsChanged(this);

        _refreshingMapItems = false;
    }

    /**
     * Get the next waypoint after the given index
     * @param index Current waypoint index
     * @return Next waypoint or null if none
     */
    public synchronized PointMapItem getNextWaypoint(int index) {
        SortedMap<Integer, PointMapItem> tail = this.indexToMarker
                .tailMap(index + 1);
        if (tail.isEmpty())
            return null;
        Integer nextIndex = tail.firstKey();
        if (nextIndex == null)
            return null;
        return this.indexToMarker.get(nextIndex);
    }

    @Override
    public void removePoint(final int index) {
        PointMapItem point = getMarker(index);
        super.removePoint(index);

        if (point != null)
            waypoints.remove(point);
    }

    @Override
    public boolean removeMarker(PointMapItem item) {
        boolean ret = super.removeMarker(item);
        waypoints.remove(item);
        if (validateWaypointNames())
            onRoutePointsChanged();
        return ret;
    }

    @Override
    public boolean removeMarker(final int index) {
        final PointMapItem point;
        synchronized (this) {
            point = getMarker(index);
            if (point != null) {
                // Remove it from our list.
                super.removeMarker(index);

                if (this.getIndexOfMarker(point) == -1
                        && this.waypoints.remove(point)) {

                    // Remove the listener.
                    this.mapView.getMapEventDispatcher()
                            .removeMapItemEventListener(point,
                                    this.nameChangedListener);
                }

                // Replace with control point
                if (point.getType().equals(WAYPOINT_TYPE))
                    setMarkerNoSync(index, Route.createControlPoint(
                            point.getPoint()));

                // Update names
                this.validateWaypointNamesNoSync();
            }
        }
        // We removed a point, so the route points have changed regardless of if names have
        // changed
        if (point != null)
            this.onRoutePointsChanged();
        return (point != null);
    }

    @Override
    protected PointMapItem removeMarkerNoSync(int index) {
        PointMapItem point = super.removeMarkerNoSync(index);
        if (point != null)
            this.waypoints.remove(point);
        return point;
    }

    @Override
    public synchronized void clearPoints() {
        this.waypoints.clear();
        super.clearPoints();
    }

    @Override
    public boolean addPoint(final GeoPointMetaData point) {
        return addMarker(getNumPoints(), createControlPoint(point.get()));
    }

    @Override
    public synchronized boolean addPoint(final int index,
            final GeoPointMetaData point) {
        return addMarker(index, createControlPoint(point.get()));
    }

    /**
     * Optimized method for bulk-adding markers to a Route
     * @param index Starting index
     * @param items List of markers to add
     * @return True if successful
     */
    @Override
    public boolean addMarkers(final int index, final PointMapItem[] items) {
        synchronized (this) {
            _ignorePointsChanged = true;

            // Get route state
            boolean editable = getEditable();
            boolean visible = getVisible();
            int numPoints = getNumPoints();

            // Flatten all markers to a single list so we can insert the new
            // markers appropriately

            List<PointMapItem> markers = new ArrayList<>(numPoints);
            for (int i = 0; i < numPoints; i++) {
                PointMapItem pmi = this.indexToMarker.get(i);
                if (pmi == null)
                    pmi = this.indexToMarker2.get(i);
                markers.add(pmi);
            }
            markers.addAll(index, Arrays.asList(items));

            // Process new markers
            List<GeoPointMetaData> newPoints = new ArrayList<>();
            for (PointMapItem item : items) {
                if (item.getType().equals(WAYPOINT_TYPE)) {
                    setEditable(item, editable);
                    if (editable) {
                        item.setZOrder(this.getZOrder() - 50000);
                        item.setVisible(visible);
                    }
                    item.setMetaInteger("color", getColor());
                    item.setClickable(getClickable());
                    if (addWaypointsToGroup && item.getGroup() == null)
                        waypointGroup.addItem(item);
                    addListeners(item);
                }
                item.setMetaString(getUIDKey(), getUID());
                item.setMetaString("parent_route_uid", getUID());
                newPoints.add(item.getGeoPointMetaData());
            }

            // Merge with existing route geometry
            _points.addAll(index, newPoints);

            // Update all the marker maps and lists
            this.indexToMarker.clear();
            this.indexToMarker2.clear();
            this.waypoints.clear();
            this.controlPoints.clear();
            this.markerToIndex.clear();
            for (int i = 0; i < markers.size(); i++) {
                PointMapItem m = markers.get(i);
                if (m == null)
                    continue;
                if (m.getType().equals(WAYPOINT_TYPE)) {
                    this.indexToMarker.put(i, m);
                    this.markerToIndex.put(m, i);
                    this.waypoints.add(m);
                } else if (m.getType().equals(CONTROLPOINT_TYPE)) {
                    this.indexToMarker2.put(i, m);
                    this.controlPoints.put(m.getUID(), m);
                }
            }
            _ignorePointsChanged = false;
        }

        onPointsChanged();

        if (validateWaypointNames())
            onRoutePointsChanged();
        this.refresh(mapView.getMapEventDispatcher(), null,
                this.getClass());

        return true;
    }

    /**
     * Given a list of PointMapItems, add them using a bulk add method.
     * @param index the index where to start the insertion which must be greater than or equal to zero.
     * @param items the array of point map items to add.
     */
    public boolean addMarkers(final int index, final List<PointMapItem> items) {
        return addMarkers(index, items.toArray(new PointMapItem[0]));
    }

    /**
     * Remove multiple points from route
     * @param start Start index (must be >= 0)
     * @param end End index (exclusive; must be <= point count)
     */
    public synchronized List<PointMapItem> removePoints(final int start,
            final int end) {
        List<PointMapItem> removed = new ArrayList<>();
        if (start < 0 || end > _points.size() || start >= end)
            return removed;
        _ignorePointsChanged = true;
        setBulkOperation(true);
        int remCount = end - start;
        for (int i = start; i < end; i++) {
            PointMapItem wp = indexToMarker.get(i);
            if (wp != null) {
                super.removeMarker(i, false, true);
                waypoints.remove(wp);
                navigationCues.remove(wp.getUID());
                markerToIndex.remove(wp);
                removed.add(wp);
            } else {
                PointMapItem cp = indexToMarker2.get(i);
                if (cp != null) {
                    controlPoints.remove(cp.getUID());
                    navigationCues.remove(cp.getUID());
                    indexToMarker2.remove(i);
                    removed.add(cp);
                }
            }
            if (start < _points.size())
                _points.remove(start);
        }

        // Update indices for way points and control points
        SortedMap<Integer, PointMapItem> tail = this.indexToMarker
                .tailMap(start);
        SortedMap<Integer, PointMapItem> updatedTail = new TreeMap<>();
        Iterator<Map.Entry<Integer, PointMapItem>> entryIter = tail.entrySet()
                .iterator();
        Map.Entry<Integer, PointMapItem> entry;
        while (entryIter.hasNext()) {
            entry = entryIter.next();
            int ind = entry.getKey() - remCount;
            this.markerToIndex.put(entry.getValue(), ind);
            updatedTail.put(ind, entry.getValue());
            entryIter.remove();
        }
        this.indexToMarker.putAll(updatedTail);

        tail = this.indexToMarker2.tailMap(start);
        entryIter = tail.entrySet().iterator();
        updatedTail.clear();
        while (entryIter.hasNext()) {
            entry = entryIter.next();
            int ind = entry.getKey() - remCount;
            updatedTail.put(ind, entry.getValue());
            entryIter.remove();
        }
        this.indexToMarker2.putAll(updatedTail);

        setBulkOperation(false);
        _ignorePointsChanged = false;
        onPointsChanged();

        return removed;
    }

    @Override
    public void onPointsChanged() {
        if (_ignorePointsChanged)
            return;
        super.onPointsChanged();
    }

    /**
     * Associates a NavigationCue for a point in the route with the given UID.
     *
     * @param uid UID of the point map item on the route to associate the cue with
     * @param cue Cue to associate with point
     */
    public void setNavigationCueForPoint(String uid, NavigationCue cue) {
        if (FileSystemUtils.isEmpty(uid) || cue == null)
            return;
        navigationCues.put(uid, cue);
        onRoutePointsChanged();
    }

    public void setNavigationCues(Map<String, NavigationCue> cues) {
        navigationCues.clear();
        navigationCues.putAll(cues);
        onRoutePointsChanged();
    }

    /**
     * Clears all navigation cues associated with the point with the given UID.
     *
     * @param uid UID of the point to clear
     * @return The cue that was unassociated from the point, if any, null otherwise
     */
    public NavigationCue removeNavigationCueForPoint(String uid) {
        NavigationCue ret = navigationCues.remove(uid);
        onRoutePointsChanged();
        return ret;
    }

    /**
     * Gets the cue associated with the point with the given UID.
     *
     * @param uid UID of point
     * @return Associated cue, null otherwise
     */
    public NavigationCue getCueForPoint(String uid) {
        return navigationCues.get(uid);
    }

    /**
     * Gets a copy of the map associating cues with route points by UID.
     *
     * @return Copy of the map, may be empty.
     */
    public Map<String, NavigationCue> getNavigationCues() {
        return new HashMap<>(navigationCues);
    }

    // Make these avaliable for current implementation of CoT in RouteMapReceiver
    @Override
    protected void setMarkerNoSync(final int index, final PointMapItem item) {
        if (index == -1)
            return;

        setEditable(item, getEditable());

        boolean isWaypoint = item.getType().equals(WAYPOINT_TYPE);
        if (isWaypoint)
            item.setMetaInteger("color", getColor());

        // keep a relationship betwen the parent and the item
        item.setMetaString("parent_route_uid", getUID());

        if (addWaypointsToGroup && item.getGroup() == null && isWaypoint) {
            waypointGroup.addItem(item);
        } else if (item instanceof ControlPointMapItem) {
            // control points only need to use a shared holder
            ((ControlPointMapItem) item).replaceMetaHolder(cpMeta);
        }

        super.setMarkerNoSync(index, item);

        // Update some data about the point that's affected by the route it's in
        item.setMetaString(getUIDKey(), getUID()); // TODO: update if uid changes?

        // Name the waypoint
        if (isWaypoint) {
            if (item.getMetaBoolean("routeWaypoint", true)) {
                this.waypoints.add(item);
                if (isAutomaticName(item.getMetaString("callsign", null)))
                    item.setMetaString("callsign", getNextWaypointName());
                item.setClickable(getClickable());

                // Listen for marker name change events so we can re-validate the waypoint names.
                this.mapView.getMapEventDispatcher().addMapItemEventListener(
                        item, nameChangedListener);
            }
            // Update names
            this.validateWaypointNamesNoSync();

            // We added a point, so the route points have changed regardless of if names have changed
            onRoutePointsChanged();
        }
    }

    // ported over to trunk
    @Override
    public boolean setPoint(int index, GeoPointMetaData point,
            boolean skipIfEquals) {
        if (index == -1)
            return false;

        GeoPointMetaData gp = getPoint(index);
        final boolean retval = super.setPoint(index, point, skipIfEquals);

        // When a route point changes latitude and longitude go ahead and call that a change.
        // Do not fire on altitude changes.
        if (skipIfEquals && gp != null
                && gp.get().getLatitude() == point.get().getLatitude()
                && gp.get().getLongitude() == point.get().getLongitude()) {
            return retval;
        } else {
            this.onRoutePointsChanged();
            return retval;
        }
    }

    @Override
    protected String getCornerMenu() {
        return getMetaString("cornerMenu", "menus/route_corner_menu.xml");
    }

    @Override
    protected String getLineMenu() {
        return getMetaString("lineMenu", "menus/route_line_menu.xml");
    }

    @Override
    protected String getShapeMenu() {
        return getMetaString("shapeMenu", "menus/b-m-r.xml");
    }

    /**
     * Using the currently active route, name the last point appropriately.
     * @param createIfMissing True to create VDO if the last point isn't a marker
     *                        False to only rename the last marker
     */
    synchronized void fixSPandVDO(boolean createIfMissing) {

        int first = 0;
        int last = getNumPoints() - 1;

        if (last < 1)
            return;

        // Turn the last point into a waypoint if it isn't already
        PointMapItem firstPoint = getMarker(first);
        PointMapItem lastPoint = getMarker(last);

        String firstCPName = getFirstWaypointName();
        String lastCPName = getLastWaypointName(); // used to be VDO

        boolean firstPointBad = firstPoint == null || !firstPoint.getType()
                .equals(WAYPOINT_TYPE);
        boolean lastPointBad = lastPoint == null || !lastPoint.getType()
                .equals(WAYPOINT_TYPE);

        final ArrayList<MapItem> pointsToRefresh = new ArrayList<>(4);
        if (!createIfMissing) {
            PointMapItem[] cps = getContactPoints();
            if (firstPointBad) {
                // Check the first marker (as opposed to the first point)
                if (cps.length > 0)
                    firstPoint = cps[0];
            }
            if (lastPointBad) {
                // Check the last marker (as opposed to the last point)
                if (cps.length > 1)
                    lastPoint = cps[cps.length - 1];
            }
        } else {
            if (firstPointBad) {
                GeoPointMetaData firstGeoPoint = getPoint(first);

                Marker addMarker = Route.createWayPoint(firstGeoPoint,
                        UUID.randomUUID().toString());
                addMarker.setMetaString("callsign", firstCPName);
                addMarker.setTitle(firstCPName);

                // turn the last waypoint into VDO

                setMarker(first, addMarker);

                if (addWaypointsToGroup)
                    waypointGroup.addItem(addMarker);

                pointsToRefresh.add(addMarker);
            }
            if (lastPointBad) {
                GeoPointMetaData lastGeoPoint = getPoint(last);

                Marker addMarker = Route.createWayPoint(lastGeoPoint,
                        UUID.randomUUID().toString());
                addMarker.setMetaString("callsign", lastCPName);
                addMarker.setTitle(lastCPName);

                // turn the last waypoint into VDO

                setMarker(last, addMarker);

                if (addWaypointsToGroup)
                    waypointGroup.addItem(addMarker);

                pointsToRefresh.add(addMarker);
            }
        }

        if (firstPoint != null && isAutomaticName(firstPoint.getMetaString(
                "callsign", null))) {
            firstPoint.setMetaString("callsign", firstCPName);
            firstPoint.setClickable(getClickable());
            if (firstPoint instanceof Marker)
                firstPoint.setTitle(firstCPName);
            pointsToRefresh.add(firstPoint);
        }

        if (lastPoint != null && isAutomaticName(lastPoint.getMetaString(
                "callsign", null))) {
            lastPoint.setMetaString("callsign", lastCPName);
            lastPoint.setClickable(getClickable());
            if (lastPoint instanceof Marker)
                lastPoint.setTitle(lastCPName);
            pointsToRefresh.add(lastPoint);
        }

        if (validateWaypointNames())
            onRoutePointsChanged();

        // if either end point needs refresh, post to the main dispatch thread
        // for invocation on the next pump. execution in the current stack may
        // deadlock if a point is shared between two routes and they are being
        // imported by separate mechanisms (e.g. one from statesaver and the
        // other from mission package)
        if (!pointsToRefresh.isEmpty())
            mapView.post(new Runnable() {
                @Override
                public void run() {
                    for (MapItem item : pointsToRefresh) {
                        item.refresh(mapView.getMapEventDispatcher(), null,
                                Route.this.getClass());
                    }
                }
            });

    }

    synchronized void fixSPandVDO() {
        fixSPandVDO(true);
    }

    @Override
    public synchronized void setMarker(int pointIndex,
            final PointMapItem item) {
        item.setMetaString("parent_route_uid", getUID());
        super.setMarker(pointIndex, item);
    }

    @Override
    public void setTitle(final String title) {
        super.setTitle(title);
        // We need to validate the waypoint names after changing this route's name
        // since the route name is appended to the first waypoint name.
        // If we move the route name to a text balloon, this validation can be removed.
        if (validateWaypointNames()) {
            onRoutePointsChanged();
        }
    }

    public String getTransportationType() {
        return transportationType;
    }

    public void setTransportationType(final String transportationType) {
        this.transportationType = transportationType;
    }

    public synchronized int getNumWaypoint() {
        return this.waypoints.size();
    }

    public static boolean isRouteMethod(String method) {
        if (FileSystemUtils.isEmpty(method))
            return false;

        for (RouteMethod r : RouteMethod.values()) {
            if (r.text.equals(method)
                    || method.equals(getLocalName(r.resourceid)))
                return true;
        }

        return false;
    }

    /**
     * See enum RouteMethod
     */
    public void setRouteMethod(String method) {
        if (!isRouteMethod(method)) {
            Log.w(TAG, "Ignoring invalid route method: " + method);
            return;
        }
        for (RouteMethod r : RouteMethod.values()) {
            if (r.text.equals(method)
                    || method.equals(getLocalName(r.resourceid)))
                routeMethod_ = r;
        }

        if (routeMethod_ == RouteMethod.Walking
                || routeMethod_ == RouteMethod.Swimming) {
            this.transportationType = context.getString(R.string.routes_text34);
        } else {
            this.transportationType = context.getString(R.string.routes_text33);
        }

        // auto naming may have changed
        if (validateWaypointNames())
            onRoutePointsChanged();

        List<OnRouteMethodChangedListener> listeners;
        synchronized (this) {
            listeners = new ArrayList<>(routeMethodChangedListeners_);
        }
        for (OnRouteMethodChangedListener r : listeners)
            r.onRouteMethodChanged(this);
    }

    public RouteMethod getRouteMethod() {
        return routeMethod_;
    }

    public static boolean isRouteDirection(String direction) {
        if (FileSystemUtils.isEmpty(direction))
            return false;

        for (RouteDirection r : RouteDirection.values()) {
            if (r.text.equals(direction)
                    || direction.equals(getLocalName(r.resourceid)))
                return true;
        }

        return false;
    }

    /**
     * See enum RouteDirection.   This is a generalized label given to the route and not the actual route direction.
     * This will not reverse the direction or order of the route.
     */
    public void setRouteDirection(String direction) {
        if (!isRouteDirection(direction)) {
            Log.w(TAG, "Ignoring invalid route direction: " + direction);
            return;
        }

        for (RouteDirection r : RouteDirection.values()) {
            if (r.text.equals(direction)
                    || direction.equals(getLocalName(r.resourceid)))
                routeDirection_ = r;
        }
        // auto naming may have changed
        if (validateWaypointNames())
            onRoutePointsChanged();

        synchronized (this) {
            for (OnRouteDirectionChangedListener r : routeDirectionChangedListeners_) {
                r.onRouteDirectionChanged(this);
            }
        }
    }

    public RouteDirection getRouteDirection() {
        return routeDirection_;
    }

    public boolean isReversedDir() {
        RouteDirection rdir = getRouteDirection();
        return (rdir == RouteDirection.Exfil);
    }

    public static boolean isRouteType(String type) {
        if (FileSystemUtils.isEmpty(type))
            return false;

        for (RouteType r : RouteType.values()) {
            if (r.text.equals(type) || type.equals(getLocalName(r.resourceid)))
                return true;
        }

        return false;
    }

    /**
     * See enum RouteType
     */
    public void setRouteType(String type) {
        if (!isRouteType(type)) {
            Log.w(TAG, "Ignoring invalid route type: " + type);
            return;
        }
        for (RouteType r : RouteType.values()) {
            if (r.text.equals(type) || type.equals(getLocalName(r.resourceid)))

                routeType_ = r;
        }

        synchronized (this) {
            for (OnRouteTypeChangedListener r : routeTypeChangedListeners_) {
                r.onRouteTypeChanged(this);
            }
        }
    }

    public RouteType getRouteType() {
        return routeType_;
    }

    /**
     * See enum PlanningMethod
     */
    public void setPlanningMethod(String method) {
        planningMethod_ = method;
    }

    public String getPlanningMethod() {
        return planningMethod_;
    }

    public static boolean isRouteOrder(String order) {
        if (FileSystemUtils.isEmpty(order))
            return false;

        for (RouteOrder r : RouteOrder.values()) {
            if (r.text.equals(order)
                    || order.equals(getLocalName(r.resourceid)))
                return true;
        }

        return false;
    }

    /**
     * See enum RouteOrder
     */
    public void setRouteOrder(String order) {
        if (!isRouteOrder(order)) {
            Log.w(TAG, "Ignoring invalid route order: " + order);
            return;
        }

        for (RouteOrder r : RouteOrder.values()) {
            if (r.text.equals(order)
                    || order.equals(getLocalName(r.resourceid)))
                routeOrder_ = r;
        }
        // auto naming may have changed
        if (validateWaypointNames())
            onRoutePointsChanged();

        synchronized (this) {
            for (OnRouteOrderChangedListener r : routeOrderChangedListeners_) {
                r.onRouteOrderChanged(this);
            }
        }
    }

    public RouteOrder getRouteOrder() {
        return routeOrder_;
    }

    public String getLastWaypointName() {
        String name;

        if (routeMethod_ == RouteMethod.Driving) {
            if (routeDirection_ == RouteDirection.Infil) {
                name = "VDO"; //Vehicle Drop Off
            } else // exfil
            {
                name = "EP"; //End Point
            }
        } else // walking, flying, swimming, watercraft
        {
            if (routeDirection_ == RouteDirection.Infil) {
                name = "TGT"; //Target
            } else // exfil
            {
                name = "EP";
            }
        }

        return name;
    }

    public String getFirstWaypointName() {
        if (routeMethod_ == RouteMethod.Driving) {
            if (routeDirection_ == RouteDirection.Infil) {
                return (getTitle() == null ? "" : getTitle() + " ") + "SP"; // Start Point
            } else // exfil
            {
                return (getTitle() == null ? "" : getTitle() + " ") + "VPU";
            }
        } else { //walking, flying, swimming, watercraft
            if (routeDirection_ == RouteDirection.Infil) {
                return (getTitle() == null ? "" : getTitle() + " ") + "SP";
            } else // exfil
            {
                return (getTitle() == null ? "" : getTitle() + " ") + "TGT";
            }
        }
    }

    public synchronized String getNextWaypointName() {
        if (getNumPoints() == 0) {
            return getFirstWaypointName();
        }
        if (routeOrder_ == RouteOrder.Descending) {
            // validateWaypointNames();
            return prefix_ + "1";
        }
        return prefix_ + this.waypoints.size();
    }

    private boolean isAutomaticName(final String name) {
        if (name == null) {
            return true;
        } else {
            if (name.equals("VDO") || name.equals("EP") || name.equals("TGT")
                    || name.equals("VPU") || name.equals("SP")
                    || name.endsWith(" VDO") || name.endsWith(" EP")
                    || name.endsWith(" TGT") || name.endsWith(" VPU")
                    || name.endsWith(" SP")) {
                return true;
            } else {

                if (name.startsWith(prefix_)) {
                    for (int i = prefix_.length(); i < name.length(); i++) {
                        if (!Character.isDigit(name.charAt(i)))
                            return false;
                    }
                    return true;
                } else {
                    return false;
                }

            }
        }
    }

    private synchronized boolean validateWaypointNames() {
        return this.validateWaypointNamesNoSync();
    }

    private boolean validateWaypointNamesNoSync() {
        if (this.getMetaBoolean("__ignoreRefresh", false))
            return false;

        if (_refreshingMapItems)
            return false;

        _refreshingMapItems = true;

        int waypointCount = 0;
        int autoNameCount;
        final int numWaypoints = this.waypoints.size();
        if (routeOrder_ == RouteOrder.Ascending) {
            autoNameCount = 1;
        } else {
            autoNameCount = numWaypoints - 2;
        }

        boolean changed = false;

        Set<PointMapItem> visited = Collections
                .newSetFromMap(new IdentityHashMap<PointMapItem, Boolean>());

        // Iterate through all the points, renaming as appropriate.
        for (PointMapItem point : this.indexToMarker.values()) {
            if (!this.waypoints.contains(point))
                continue;
            if (visited.contains(point))
                continue;
            visited.add(point);

            String name = point.getMetaString("callsign", null);

            if (waypointCount == 0) // First
            {
                String firstWaypointName = getFirstWaypointName();
                if (isAutomaticName(name)
                        && !firstWaypointName.equals(name)) {
                    point.setMetaString("callsign", firstWaypointName);
                    if (point instanceof Marker)
                        point.setTitle(firstWaypointName);
                    changed = true;
                }
            } else if (waypointCount == numWaypoints - 1) // End
            {
                String lastWaypointName = getLastWaypointName();
                if (isAutomaticName(name)
                        && !lastWaypointName.equals(name)) {
                    point.setMetaString("callsign", lastWaypointName);
                    if (point instanceof Marker)
                        point.setTitle(lastWaypointName);
                    changed = true;
                }
            } else // Middle
            {
                int autoCountIncr = 0;
                if (routeOrder_ == RouteOrder.Ascending)
                    autoCountIncr = 1;
                else if (routeOrder_ == RouteOrder.Descending)
                    autoCountIncr = -1;

                // XXX - else illegal state ???

                if (autoCountIncr != 0) {
                    String correctName = prefix_ + autoNameCount;
                    // Only change automatically named waypoints
                    // If the user changes the name, ignore it.
                    if (isAutomaticName(name)
                            && (!correctName.equals(name))) {
                        autoNameCount += autoCountIncr;
                        point.setMetaString("callsign", correctName);
                        if (point instanceof Marker)
                            point.setTitle(correctName);
                        changed = true;
                    } else /*if (name != null && correctName.equals(name))*/ {
                        autoNameCount += autoCountIncr;
                    }
                }

            }
            waypointCount++;
        }
        _refreshingMapItems = false;

        return changed;
    }

    public String getWaypointUID(int waypointNumber) {
        int count = 0;

        synchronized (this) {
            for (PointMapItem p : this.markerToIndex.keySet()) {
                if (count == waypointNumber) {
                    return p.getUID();
                }

                count++;
            }
        }

        return null;
    }

    /**
     * Returns a list of arrays that describe the route details where the array is as described:
     *   [0] - callsign (set last)
     *   [1] - distance in miles
     *   [2] - distance in kilometers
     *   [3] - elevation change
     *   [4] - uid
     */
    synchronized public List<String[]> getRouteDetails() {
        List<String[]> list = new ArrayList<>();

        PointMapItem lastWaypoint = null;

        double linkCost = 0.0;

        int segmentCount = 1;

        Location linkS = new Location("linkS");
        Location linkE = new Location("linkE");

        PointMapItem waypoint;
        int idx = 0;
        for (GeoPointMetaData gmd : _points) {
            GeoPoint g = gmd.get();
            // XXX - not a good idea, at least document this?
            if (idx == 0) {
                // index 0 means that the start of the link has not been set
                linkS.setLatitude(g.getLatitude());
                linkS.setLongitude(g.getLongitude());
            }

            // set the end of the link to the current point
            linkE.setLatitude(g.getLatitude());
            linkE.setLongitude(g.getLongitude());

            // see if this point is a marker
            waypoint = this.getMarker(idx++);

            // update the cost with the distance for the current link
            linkCost += linkS.distanceTo(linkE);

            if (waypoint != null) {
                String[] detail = new String[] {
                        "", "--", "--", "--", ""
                };
                if (!Double.isNaN(linkCost) && linkCost > 0) {
                    if (routeMethod_ == RouteMethod.Flying
                            || routeMethod_ == RouteMethod.Watercraft) {
                        // a valid context is not really required here, can pass in null;
                        detail[1] = SpanUtilities.formatType(Span.NM, linkCost,
                                Span.METER);
                        detail[2] = SpanUtilities.format(linkCost, Span.METER,
                                Span.KILOMETER);
                    } else {
                        detail[1] = SpanUtilities.format(linkCost, Span.METER,
                                Span.MILE);
                        detail[2] = SpanUtilities.format(linkCost, Span.METER,
                                Span.KILOMETER);
                    }
                }

                double lastWayAlt = GeoPoint.UNKNOWN;
                if (lastWaypoint != null)
                    lastWayAlt = lastWaypoint.getPoint().getAltitude();

                double wayAlt = waypoint.getPoint().getAltitude();

                if (GeoPoint.isAltitudeValid(wayAlt)
                        && GeoPoint.isAltitudeValid(lastWayAlt)) {
                    double gain = wayAlt - lastWayAlt;
                    if (gain > 0) {
                        detail[3] = "+ "
                                + SpanUtilities.format(gain, Span.METER,
                                        Span.FOOT);
                    }
                }

                final String waypointName = ATAKUtilities
                        .getDisplayName(waypoint);
                if (waypointName == null || waypointName.equals("")) {
                    detail[0] = "Wpt#" + segmentCount;
                } else {
                    detail[0] = waypointName;
                }

                detail[4] = waypoint.getUID();

                list.add(detail);

                segmentCount++;

                // reset the cost
                linkCost = 0.0;
                lastWaypoint = waypoint;
            }

            // the start of the link becomes the current point
            linkS.setLatitude(linkE.getLatitude());
            linkS.setLongitude(linkE.getLongitude());
        }

        return list;
    }

    /**
     * Finds the closest point distance, within this route, to the reference point.
     *
     * @param refPoint The reference point.
     * @return The minimum distance to the point of interest.
     */
    synchronized public double getMinimumDistance(PointMapItem refPoint) {
        double minDistance = Double.NaN;
        double distance = 0.0d;

        for (GeoPointMetaData p : _points) {
            distance = refPoint.getPoint().distanceTo(p.get());
            if (Double.isNaN(minDistance) || distance < minDistance)
                minDistance = distance;
        }

        return minDistance;
    }

    /**
     * Finds the farthest point distance, within this route, to the reference point.
     *
     * @param refPoint The reference point.
     * @return The maximum distance to the point of interest.
     */
    synchronized public double getMaximumDistance(PointMapItem refPoint) {
        double maxDistance = Double.NaN;
        double distance = 0.0d;

        for (GeoPointMetaData p : _points) {
            distance = refPoint.getPoint().distanceTo(p.get());
            if (Double.isNaN(maxDistance) || distance > maxDistance)
                maxDistance = distance;
        }

        return maxDistance;
    }

    /**
     * Compares two routes to determine if they're identical.
     *
     * @param route1 The first route to compare.
     * @param route2 The second route to compare.
     * @return True if the two routes are identical; False otherwise.
     * @note This does not check UIDs. Use the UID to determine if this is an existing route that
     *       changed.
     */
    public static boolean compare(Route route1, Route route2) {

        // Do the easy comparisons first before diving into the actual route components.

        // If the names are not the same, assume the routes are different.
        if (route1.getTitle() != null && route2.getTitle() != null
                && route1.getTitle().compareTo(route2.getTitle()) != 0)
            return false;

        if (route1._points.size() != route2._points.size())
            return false;

        if (route1.markerToIndex.size() != route2.markerToIndex.size())
            return false;

        // Now compare each point that make up the route.
        int indexBoxed;
        for (int i = 0; i < route1._points.size(); i++) {
            indexBoxed = i;
            GeoPoint point1 = route1._points.get(indexBoxed).get();
            GeoPoint point2 = route2._points.get(indexBoxed).get();
            PointMapItem marker1 = route1.indexToMarker.get(indexBoxed);
            PointMapItem marker2 = route2.indexToMarker.get(indexBoxed);

            // Is this even the same point?
            if ((marker1 != null && marker2 != null)
                    && marker1.getUID().compareTo(marker2.getUID()) != 0)
                return false;

            // Is this the same point, but it moved?
            // TODO: use GeoPoint.equals?
            if (point1.getLatitude() != point2.getLatitude() ||
                    point1.getLongitude() != point2.getLongitude())
                return false;
        }

        return true;
    }

    private final MapEventDispatcher.OnMapEventListener nameChangedListener = new MapEventDispatcher.OnMapEventListener() {
        @Override
        public void onMapItemMapEvent(MapItem item, MapEvent event) {
            if (event.getType().equals(MapEvent.ITEM_REFRESH) &&
                    !_refreshingMapItems) {
                _refreshingMapItems = true;
                if (validateWaypointNames()) {
                    // Always update so we save this waypoint name.
                    onRoutePointsChanged();
                }
                _refreshingMapItems = false;
            }
        }
    };

    /**
     * Creates a waypoint marker.
     *
     * @param geoPoint
     * @return Marker representing a waypoint.
     */
    public static Marker createWayPoint(final GeoPointMetaData geoPoint,
            String uid) {

        Marker waypointMarker = new Marker(geoPoint, uid);

        waypointMarker.setMetaString("entry", "user");
        waypointMarker.setMetaBoolean("editable", true);
        waypointMarker.setMetaBoolean("nevercot", true);
        waypointMarker.setMetaBoolean("navigating", false);
        waypointMarker.setMetaBoolean("ready_to_nav", true);
        waypointMarker.setMetaString("how", "h-g-i-g-o");
        waypointMarker.setMetaBoolean("routeWaypoint", true);

        waypointMarker.setType(WAYPOINT_TYPE);
        waypointMarker.setMetaBoolean("addToObjList", false);
        return waypointMarker;
    }

    /**
     * Creates a ControlPoint marker with a random uid.
     *
     * @param geoPoint
     * @return Marker representing a waypoint.
     */
    public static PointMapItem createControlPoint(final GeoPoint geoPoint) {
        return createControlPoint(geoPoint, UUID.randomUUID().toString());
    }

    /**
     * Creates a ControlPoint marker with an assigned uid.
     *
     * @param geoPoint
     * @param uid
     * @return Marker representing a waypoint.
     */
    public static PointMapItem createControlPoint(final GeoPoint geoPoint,
            String uid) {
        PointMapItem marker = new ControlPointMapItem(geoPoint, uid);
        return marker;
    }

    @Override
    protected CotEvent toCot() {
        CotEvent cotEvent = super.toCot();

        // set type to route
        cotEvent.setType("b-m-r");
        CotDetail detail = cotEvent.getDetail();

        // Save route specific details
        CotDetail linkAttr = new CotDetail("link_attr");
        linkAttr.setAttribute("color", String.valueOf(getColor()));
        linkAttr.setAttribute("stroke",
                String.valueOf((int) getStrokeWeight()));
        linkAttr.setAttribute("type", getTransportationType());

        final RouteMethod rm = getRouteMethod();
        linkAttr.setAttribute("method",
                (rm == null) ? RouteMethod.Driving.text : rm.text);

        final RouteDirection rd = getRouteDirection();
        linkAttr.setAttribute("direction",
                (rd == null) ? RouteDirection.Infil.text : rd.text);

        final RouteType rt = getRouteType();
        linkAttr.setAttribute("routetype",
                (rt == null) ? RouteType.Primary.text : rt.text);

        final RouteOrder ro = getRouteOrder();
        linkAttr.setAttribute("order",
                (ro == null) ? RouteOrder.Ascending.text : ro.text);

        linkAttr.setAttribute("planningmethod", getPlanningMethod());
        linkAttr.setAttribute("prefix", getPrefix());

        detail.addChild(linkAttr);

        CotDetailManager.getInstance().addDetails(this, cotEvent);

        return cotEvent;
    }

    @Override
    protected Folder toKml() {
        final boolean clampToGround = _prefs.getBoolean(
                "kmlExportGroundClamp", false);
        final String kmlExportCheckpointMode = _prefs.getString(
                "kmlExportCheckpointMode", "Both");

        RouteKmlIO.CheckpointExportMode mode = RouteKmlIO.CheckpointExportMode.Both;
        try {
            mode = RouteKmlIO.CheckpointExportMode
                    .valueOf(kmlExportCheckpointMode);
        } catch (Exception e) {
            Log.w(TAG, "Using default KML Export Checkpoint Mode", e);
            mode = RouteKmlIO.CheckpointExportMode.Both;
        }

        // convert route to KML
        Folder f = RouteKmlIO.toKml(mapView.getContext(), this, mode,
                clampToGround);
        if (f == null)
            return f;

        //wrap in parent "Routes" folder
        Folder parent = new Folder();
        parent.setFeatureList(new ArrayList<Feature>());
        parent.getFeatureList().add(f);
        parent.setName("Routes");
        return parent;
    }

    @Override
    protected KMZFolder toKmz() {
        //get KML and wrap in KMZ Folder
        Folder f = toKml();
        if (f == null)
            return null;

        KMZFolder folder = new KMZFolder(f);

        //Now check for checkpoints with attached images
        for (int i = 0; i < getNumPoints(); i++) {
            final PointMapItem cp = getMarker(i);
            if (cp == null) {
                continue;
            }

            final String cpUID = cp.getUID();
            if (FileSystemUtils.isEmpty(cpUID)) {
                continue;
            }

            final List<File> attachments = AttachmentManager
                    .getAttachments(cpUID);
            if (attachments.size() > 0) {
                for (File attachment : attachments) {
                    if (ImageDropDownReceiver.ImageFileFilter.accept(
                            attachment.getParentFile(), attachment.getName())) {
                        String kmzAttachmentsPath = "attachments"
                                + File.separatorChar
                                + cpUID + File.separatorChar
                                + attachment.getName();

                        Pair<String, String> pair = new Pair<>(
                                attachment.getAbsolutePath(),
                                kmzAttachmentsPath);
                        if (!folder.getFiles().contains(pair))
                            folder.getFiles().add(pair);
                    }
                }

                Log.d(TAG, "KMZ Exporting " + attachments.size()
                        + " files for checkpint: " + cpUID);

                //update description for this checkpoint
                KMLUtil.deepFeatures(f, new FeatureHandler<Placemark>() {

                    @Override
                    public boolean process(Placemark feature) {
                        if (feature == null)
                            return false;

                        if (!cpUID.equals(feature.getId()))
                            return false;

                        //we found the checkpoint placemark, now update description for images
                        //set an HTML description (e.g. for the Google Earth balloon)
                        String desc = Marker.getKMLDescription(cp,
                                feature.getName(),
                                attachments.toArray(new File[0]));
                        if (!FileSystemUtils.isEmpty(desc)) {
                            feature.setDescription(desc);
                        }

                        return false;
                    }
                }, Placemark.class);
            }
        } //end checkpoint loop

        return folder;
    }

    @Override
    protected GPXExportWrapper toGpx() {
        // convert route to KML
        Gpx f = RouteGpxIO.toGpx(this);
        if (f == null)
            return null;

        if (f.isEmpty())
            return null;

        return new GPXExportWrapper(f);
    }

    public synchronized PointMapItem[] getContactPoints() {
        List<PointMapItem> cps = new ArrayList<>();
        for (int i = 0; i < getNumPoints(); i++) {
            PointMapItem pmi = this.indexToMarker.get(i);
            if (pmi != null)
                cps.add(pmi);
        }
        return cps.toArray(new PointMapItem[0]);
    }
}
