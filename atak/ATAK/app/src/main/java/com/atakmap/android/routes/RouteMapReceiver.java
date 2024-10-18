
package com.atakmap.android.routes;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.atakmap.android.attachment.layer.AttachmentBillboardLayer;
import com.atakmap.android.cot.exporter.DispatchMapItemTask;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.gpx.Gpx;
import com.atakmap.android.gui.ImportFileBrowserDialog;
import com.atakmap.android.gui.RangeBearingInputViewPilots;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.importexport.ExportMarshal;
import com.atakmap.android.importexport.ExportMarshalAdapter;
import com.atakmap.android.importexport.Exportable;
import com.atakmap.android.importexport.ExporterManager;
import com.atakmap.android.importexport.ExporterManager.ExportMarshalMetadata;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapActivity;
import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.MapView.RenderStack;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.export.MissionPackageExportMarshal;
import com.atakmap.android.missionpackage.file.MissionPackageContent;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.NameValuePair;
import com.atakmap.android.routes.nav.NavigationCue;
import com.atakmap.android.routes.nav.NavigationUxManager;
import com.atakmap.android.routes.nav.RerouteDialog;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.util.AfterTextChangedWatcher;
import com.atakmap.android.util.EditAction;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.android.util.Undoable;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MarkerIconWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.spatial.file.GpxFileSpatialDb;
import com.atakmap.spatial.file.KmlFileSpatialDb;
import com.ekito.simpleKML.model.Kml;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RouteMapReceiver extends BroadcastReceiver implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "RouteMapReceiver";

    public static final String DELETE_ACTION = "com.atakmap.android.maps.ROUTE_DELETE";
    public static final String SHOW_ACTION = "com.atakmap.android.maps.ROUTE_DETAILS";
    public static final String SHARE_ACTION = "com.atakmap.android.maps.ROUTE_SHARE";
    public static final String SHOW_EDIT_ACTION = "com.atakmap.android.maps.EDIT_ROUTE_DETAILS";

    public static final String EDIT_CUES_ACTION = "com.atakmap.android.maps.ROUTE_EDIT_CUE";

    public static final String MANAGE_ACTION = "com.atakmap.android.maps.MANAGE_ROUTES";

    public static final String START_NAV = "com.atakmap.android.maps.START_NAV";
    public static final String END_NAV = "com.atakmap.android.maps.END_NAV";

    public static final String INSERT_WAYPOINT = "com.atakmap.android.maps.ROUTE_INSERT_WAYPOINT";
    public static final String ADD_PT_FROM_BEARING = "com.atakmap.android.maps.ADD_PT_FROM_BEARING";

    public static final String ROUTE_IMPORT = "com.atakmap.android.maps.ROUTE_IMPORT";
    public static final String ROUTE_TRANSFER = "com.atakmap.android.maps.ROUTE_TRANSFER";
    public static final String ROUTE_EXPORT = "com.atakmap.android.maps.ROUTE_EXPORT";

    public static final String UNDO = "com.atakmap.android.maps.toolbar.UNDO";

    public static final String PROCESSING_DONE = "com.atakmap.android.maps.ROUTE_PROCESSING_DONE";

    public static final String POINTS_CHANGED_ACTION = "com.atakmap.android.maps.ROUTE_POINTS_CHANGED";

    public final static String ACTION_ROUTE_IMPORT_FINISHED = "com.atakmap.android.maps.ROUTE_IMPORT_FINISHED";

    public final static String EXTRA_ROUTE_UID = "com.atakmap.android.maps.ROUTE_UID";
    public final static String EXTRA_ROUTE_IS_NEW = "com.atakmap.android.maps.ROUTE_IS_NEW";
    public final static String EXTRA_ROUTE_TITLE = "com.atakmap.android.maps.ROUTE_TITLE";
    public final static String EXTRA_ROUTE_TYPE = "com.atakmap.android.maps.ROUTE_TYPE";
    public final static String EXTRA_ROUTE_UPDATED = "com.atakmap.android.maps.ROUTE_UPDATED";

    private static final int REROUTE_BUTTON_OFF = 0;
    private static final int REROUTE_BUTTON_ON = 1;

    private static final String REVERSED_SUFFIX = " Reversed";

    private final MapGroup _routeGroup;
    private final MapGroup _waypointGroup;
    public final Context _context;
    public final MapView _mapView;

    protected RoutePlannerView _plannerView;

    protected final RouteNavigator _navigator;
    private final NavigationUxManager _uxManager;
    private final AttachmentBillboardLayer _billboardLayer;

    private final SharedPreferences _navPrefs;

    private final ArrayList<Route> routes_ = new ArrayList<>();
    private final Map<String, Tool> _tools = new HashMap<>();

    private final PolylineSelectTool pst;
    private RouteEditTool _editTool;

    private final static int notiIndx = 8879;

    private static RouteMapReceiver _instance;

    // Reroute pieces
    private MarkerIconWidget rerouteButton;
    private Icon rerouteActiveIcon;
    private Icon rerouteInactiveIcon;

    static protected class RouteDropDownReceiver extends DropDownReceiver {

        RouteDropDownReceiver(MapView _mapView, MapItem mi) {
            super(_mapView);
            setSelected(mi, "");
        }

        @Override
        public void disposeImpl() {
        }

        @Override
        public void onReceive(final Context c, final Intent intent) {
        }
    }

    public RouteMapReceiver(final MapView mapView,
            final MapGroup routeGroup,
            final MapGroup waypointGroup,
            final MapGroup navGroup,
            final Context context) {
        _mapView = mapView;
        _routeGroup = routeGroup;
        _waypointGroup = waypointGroup;

        Collection<MapItem> items = _routeGroup.getItems();
        for (MapItem item : items) {
            if (item instanceof Route)
                routes_.add((Route) item);
        }

        _billboardLayer = new AttachmentBillboardLayer(mapView);
        mapView.addLayer(RenderStack.RASTER_OVERLAYS, _billboardLayer);

        _navigator = new RouteNavigator(mapView, navGroup, _billboardLayer);
        _uxManager = new NavigationUxManager(_navigator);
        _context = context;
        _navPrefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());
        _navPrefs.registerOnSharedPreferenceChangeListener(this);

        _mapView.getMapEventDispatcher().addMapEventListener(_routeListener);

        // self registers, no need to register later
        pst = new PolylineSelectTool(_mapView);

        _instance = this;
    }

    public static RouteMapReceiver getInstance() {
        return _instance;
    }

    public void dispose() {
        _navigator.stopNavigating();
        _mapView.removeLayer(RenderStack.RASTER_OVERLAYS, _billboardLayer);
        _billboardLayer.dispose();
        _navPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    public MapGroup getRouteGroup() {
        return _routeGroup;
    }

    public SharedPreferences getPreferences() {
        return _navPrefs;
    }

    public boolean isNavigating() {
        return _navigator.isNavigating();
    }

    /**
     * Perform a non invasive test to see if the network is available for 
     * the purpose of Route Lookup.
     */
    public static boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) MapView
                .getMapView().getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager
                .getActiveNetworkInfo();
        return !(activeNetworkInfo == null || !activeNetworkInfo.isConnected());
    }

    final MapEventDispatcher.MapEventDispatchListener _routeListener = new MapEventDispatcher.MapEventDispatchListener() {

        @Override
        public void onMapEvent(MapEvent event) {
            if (event.getItem() instanceof Route) {
                final Route route = (Route) event.getItem();
                if (event.getType().equals(MapEvent.ITEM_REMOVED)) {
                    // XXX - why is this different from delete route intent ???
                    deleteRoute(route);
                } else if (event.getType().equals(MapEvent.ITEM_ADDED)) {
                    addRoute(route);
                    route.fixSPandVDO();
                }
            }
        }
    };

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {

        if (key == null)
            return;

        if (isNavigating() && key.equals("route_billboard_enabled"))
            _billboardLayer.setVisible(prefs.getBoolean(key, true));
    }

    void addTools(Tool... tools) {
        _tools.clear();
        for (Tool t : tools)
            _tools.put(t.getIdentifier(), t);
        _editTool = (RouteEditTool) _tools.get(RouteEditTool.TOOL_IDENTIFIER);
    }

    void addRoute(Route route) {
        synchronized (routes_) {
            routes_.add(route);
        }
    }

    Route findRoute(String uid) {
        synchronized (routes_) {
            for (Route r : routes_)
                if (r.getUID().equals(uid))
                    return r;
        }
        return null;
    }

    private void deleteRoute(final Route toDelete) {
        if (toDelete == null) {
            return;
        }

        // check if this route is being navigated. If so, stop it.
        if (_navigator.isNavigating()) {
            if (_navigator.getRoute().getUID().equals(toDelete.getUID())) {
                _navigator.requestStopNavigating();
            }
        }

        // toDelete.delete(deleteMapItems);

        synchronized (routes_) {
            routes_.remove(toDelete);
        }
    }

    public ArrayList<Route> getCompleteRoutes() {
        ArrayList<Route> completed = new ArrayList<>();
        synchronized (routes_) {
            for (Route r : routes_) {
                if (r.getNumPoints() > 1)
                    completed.add(r);
            }
        }

        return completed;
    }

    /**
     * Find the route based on it's UID.
     * 
     * @param routeUID
     * @return
     */
    protected Route getRoute(String routeUID) {
        synchronized (routes_) {
            for (Route r : routes_) {
                final String u = r.getUID();
                if (u.equals(routeUID))
                    return r;
            }
        }

        return null;
    }

    /**
     * Returns first route with the given name, null if no such route exists
     * 
     * @param name
     * @return
     */
    public Route getRouteWithName(String name) {
        synchronized (routes_) {
            for (Route r : routes_) {
                if (r.getTitle() != null && r.getTitle().equals(name)) {
                    return r;
                }
            }
        }
        return null;
    }

    private int getNumRoutes() {
        return routes_.size();
    }

    public Route getNewRoute(final String uid) {
        int routeNameIncrement = 1;
        String routeName = _context.getString(R.string.route)
                + (getNumRoutes() + routeNameIncrement);
        while (getRouteWithName(routeName) != null) {
            routeNameIncrement++;
            routeName = _context.getString(R.string.route)
                    + (getNumRoutes() + routeNameIncrement);
        }

        String prefix = getPreferences().getString("waypointPrefix", "CP");
        int color = Integer
                .parseInt(getPreferences().getString("defaultRouteColor",
                        String.valueOf(Route.DEFAULT_ROUTE_COLOR)));

        final Route route = new Route(_mapView, routeName, color, prefix, uid);
        return route;
    }

    /**
     * Retrieve the map group for waypoints associated with all routes.
     */
    public MapGroup getWaypointGroup() {
        return _waypointGroup;
    }

    @Override
    public void onReceive(Context ignoreCtx, final Intent intent) {

        final Context context = _mapView.getContext();

        final String action = intent.getAction();
        if (action == null)
            return;

        switch (action) {
            case "com.atakmap.android.maps.ROUTE_NOTI_CLICKED":

                String rtId = intent.getStringExtra("NOTI_ROUTE_ID");
                if (rtId != null) {
                    Route rt = getRoute(rtId);
                    if (rt != null)
                        ATAKUtilities.scaleToFit(_mapView, rt, false,
                                _mapView.getWidth(), _mapView.getHeight());
                }

                NotificationUtil.getInstance().clearNotification(notiIndx);
                break;
            case EDIT_CUES_ACTION: {
                String routeUID = intent.getStringExtra("routeUID");
                String navUID = intent.getStringExtra("uid");
                MapItem mapItem = _mapView.getRootGroup().deepFindUID(navUID);

                if (mapItem == null)
                    return; //Item not found, nothing further to do.

                Route route = getRoute(routeUID);
                if (route == null) {
                    if (mapItem instanceof Route)
                        route = (Route) mapItem;
                    else
                        return;
                }

                PointMapItem currentItem = null;
                int currentItemIndex;

                //Sometimes control points can be hidden under waypoints, but the waypoint should be preferred over the control point for selection purposes.
                //Accordingly, we must check to see if a waypoint was hit, if not, we'll defer to the results of the hit-test
                if (mapItem instanceof PointMapItem
                        && mapItem.getType().equals(Route.WAYPOINT_TYPE)) {
                    currentItem = (PointMapItem) mapItem;

                    currentItemIndex = route.getIndexOfMarker(currentItem);

                    if (currentItemIndex == -1)
                        return; //The waypoint isn't on this route.

                } else {
                    //The passed in UID is likely not the correct UID, so rely on hit-testing.
                    currentItemIndex = route.getMetaInteger("hit_index", -1);

                    if (currentItemIndex != -1) {
                        currentItem = route.getPointMapItem(currentItemIndex);
                    }
                }

                //If we were able to successfully get the current item, pass it and the previous item, if available, to the editor
                if (currentItem != null) {
                    //Get the previous item if we can expect there to be one
                    PointMapItem previousItem = currentItemIndex > 0
                            ? route.getPointMapItem(currentItemIndex - 1)
                            : null;

                    editCues(route, currentItem, previousItem);
                }

                break;
            }
            case ADD_PT_FROM_BEARING: {

                String navUID = intent.getStringExtra("uid");
                String routeUID = intent.getStringExtra("routeUID");
                final Route route = getRoute(intent.getStringExtra("routeUID"));

                final PointMapItem waypoint;
                if (navUID != null) {
                    MapItem item = _mapView.getRootGroup()
                            .deepFindUID(intent.getStringExtra("uid"));
                    if (item instanceof PointMapItem)
                        waypoint = (PointMapItem) item;
                    else
                        waypoint = null;
                } else {
                    waypoint = null;
                }

                if (waypoint == null) {
                    Log.d(TAG, "error occurred, waypoint was null for uid: "
                            + navUID);
                    return;
                }

                int newRouteIndex = -1;
                if (route != null) {
                    for (int wp = 0; wp < route.getNumPoints(); wp++) {
                        if (waypoint.getPoint().equals(
                                route.getPoint(wp).get()))
                            newRouteIndex = wp;
                    }
                }

                final int insertPt = newRouteIndex;

                AlertDialog.Builder b = new AlertDialog.Builder(
                        _mapView.getContext());
                LayoutInflater inflater = LayoutInflater
                        .from(_mapView.getContext());

                final RangeBearingInputViewPilots rbView = (RangeBearingInputViewPilots) inflater
                        .inflate(R.layout.route_add_pt_from_bearing, null);
                b.setTitle(R.string.routes_text1);
                b.setView(rbView);
                b.setPositiveButton(R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (route == null)
                            return;
                        Double range = rbView.getRange();
                        Double bearing = rbView.getBearing();
                        if (range != null && bearing != null) {
                            double tBearing = ATAKUtilities
                                    .convertFromMagneticToTrue(
                                            waypoint.getPoint(), bearing);
                            GeoPoint newPoint = GeoCalculations.pointAtDistance(
                                    waypoint.getPoint(), tBearing, range);

                            final Marker addMarker = Route.createWayPoint(
                                    GeoPointMetaData.wrap(newPoint),
                                    UUID.randomUUID().toString());
                            addMarker.setTitle(route.getLastWaypointName());
                            addMarker.setMetaString("callsign",
                                    route.getLastWaypointName());
                            EditAction a = route.new InsertPointAction(
                                    addMarker,
                                    insertPt + 1, null);
                            _editTool.run(a);
                        } else {
                            // Invalid input handled in RangeBearingInputViewPilots
                        }
                    }
                });
                b.setNegativeButton(R.string.cancel, null);
                b.show();
                break;
            }
            case INSERT_WAYPOINT: {
                MapItem mi = _routeGroup.deepFindUID(
                        intent.getStringExtra("uid"));

                if (mi instanceof Route) {
                    GeoPointMetaData point = GeoPointMetaData
                            .wrap(GeoPoint.parseGeoPoint(intent
                                    .getStringExtra("point")));

                    if (point == null) {
                        Log.w(TAG,
                                "Last touch point is null. Using map center point.");
                        point = _mapView.getCenterPoint();
                    }

                    Route route = (Route) mi;
                    int index = route.getMetaInteger("hit_index", -1);
                    String hitType = route.getMetaString("hit_type", "");

                    // Do a sanity check
                    if (index >= 0 && index < route.getNumPoints()
                            && !hitType.equals("")) {

                        EditAction a;
                        Marker wayPoint = Route.createWayPoint(point,
                                UUID.randomUUID().toString());

                        if (hitType.equals("line")) {
                            a = route.new InsertPointAction(wayPoint,
                                    index + 1, null);
                        } else {
                            a = route.getActionProvider()
                                    .newExchangePointAction(
                                            index, wayPoint,
                                            null);
                        }

                        _editTool.run(a);
                    } else {
                        Log.e(TAG, "Invalid route hit index or type: " + index
                                + " / " + hitType);
                    }
                }
                break;
            }
            case SHOW_ACTION: {
                Route route = getRoute(intent.getStringExtra("routeUID"));
                if (route != null)
                    showRouteDetails(route);
                break;
            }
            case RouteEditTool.TOOL_IDENTIFIER: {
                String uid = intent.getStringExtra("routeUID");
                Route route = getRoute(uid);
                if (route == null)
                    return;
                if (_plannerView != null && _plannerView.isOpen()) {
                    if (route != _plannerView.getRoute())
                        // Open different route details and start edit from there
                        showRouteDetails(route, null, true);
                    else
                        _plannerView.startEdit();
                    return;
                }
                startEdit(uid);
                break;
            }
            case SHARE_ACTION: {

                String routeUID = intent.getStringExtra("routeUID");

                synchronized (routes_) {
                    for (Route r : routes_) {
                        if (r.getUID().equals(routeUID)) {
                            r.setMetaBoolean("shared", true);
                            new DispatchMapItemTask(_mapView, r).execute();
                        }

                    }
                }
                break;
            }
            case DELETE_ACTION: {
                String routeUID = intent.getStringExtra("routeUID");

                final Route toDelete = getRoute(routeUID);

                if (toDelete != null) {

                    AlertDialog.Builder b = new AlertDialog.Builder(context);
                    b.setTitle(
                            _context.getString(R.string.confirmation_dialogue));
                    b.setMessage(context.getString(R.string.routes_text3)
                            + toDelete.getTitle());
                    b.setPositiveButton(R.string.yes, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface arg0, int arg1) {
                            getRouteGroup().removeItem(toDelete);
                            toDelete.delete(null);
                        }
                    });
                    b.setNegativeButton(R.string.cancel, null);
                    b.show();
                }
                break;
            }
            case START_NAV: {

                Intent mIntent = new Intent();
                mIntent.setAction("com.atakmap.android.maps.HIDE_DETAILS");
                AtakBroadcast.getInstance().sendBroadcast(mIntent);

                final String navUID = intent.getStringExtra("uid");
                final String routeUID = intent.getStringExtra("routeUID");

                Log.d(TAG, "START_NAV, routeUID: " + routeUID);

                PointMapItem foundNewWayPtToNavTowards = null;
                if (routeUID != null) {

                    // Start at route beginning point
                    Route route = getRoute(routeUID);
                    if (route != null && route.getNumPoints() > 0) {
                        route.fixSPandVDO();
                        if (route.isReversedDir())
                            foundNewWayPtToNavTowards = route
                                    .getPointMapItem(route
                                            .getNumPoints() - 1);
                        else
                            foundNewWayPtToNavTowards = route
                                    .getPointMapItem(0);
                    }

                } else if (navUID != null) {
                    // Start at clicked checkpoint
                    MapItem item = _mapView.getRootGroup()
                            .deepFindUID(navUID);

                    if (item instanceof PointMapItem)
                        foundNewWayPtToNavTowards = (PointMapItem) item;
                }

                if (_navigator.isNavigating()) {
                    Log.d(TAG,
                            "Processing a START_NAV, navigator is already navigating");
                } else {
                    Log.d(TAG,
                            "Processing a START_NAV, navigator not currently navigating");
                }

                if (_navigator.isNavigating()) {
                    String activeRouteUID = _navigator.getRoute() != null
                            ? _navigator
                                    .getRoute().getUID()
                            : "";

                    Log.d(TAG,
                            "START_NAV - nav active, old uid: " + activeRouteUID
                                    + ", new: " + routeUID);

                    if (routeUID != null && !activeRouteUID.equals(routeUID)) {

                        Log.d(TAG, "Start with a new route");

                        // Update to nav a different route
                        Route route = getRoute(routeUID);
                        if (route != null)
                            _navigator.updateNavigation(route);

                    } else {
                        if (foundNewWayPtToNavTowards != null) {

                            Log.d(TAG,
                                    "new point to navigate towards: "
                                            +
                                            foundNewWayPtToNavTowards
                                                    .getMetaString("callsign",
                                                            foundNewWayPtToNavTowards
                                                                    .getUID()));

                            // Specify a new point to the navigator that we should navigate to instead
                            final PointMapItem nwptnt = foundNewWayPtToNavTowards;
                            _navigator.navigateToNewWayPoint(nwptnt);
                        }
                    }

                } else if ((ATAKUtilities.findSelf(_mapView) == null)) {

                    Toast.makeText(
                            context,
                            R.string.routes_text4,
                            Toast.LENGTH_LONG).show();
                } else {

                    Route routeToNav = null;
                    int startIndex = 0;
                    PointMapItem found = null;

                    if (navUID != null) {
                        MapItem item = _mapView.getRootGroup()
                                .deepFindUID(intent
                                        .getStringExtra("uid"));
                        if (item instanceof PointMapItem)
                            found = (PointMapItem) item;
                    }

                    if (found != null) {
                        {
                            Map<String, Object> foundMeta = new HashMap<>();
                            found.getMetaData(foundMeta);
                            for (Map.Entry e : foundMeta
                                    .entrySet()) {
                                Log.e(TAG,
                                        "Key: " + e.getKey()
                                                + " = "
                                                + e.getValue());
                            }
                        }
                        routeToNav = getRouteWithPoint(found);
                        if (routeToNav != null)
                            startIndex = routeToNav
                                    .getIndexOfMarker(found);
                    }

                    if (routeToNav == null) {
                        routeToNav = getRoute(routeUID);
                        if (routeToNav != null) {
                            // Begin at last waypoint instead
                            if (routeToNav.isReversedDir())
                                startIndex = routeToNav
                                        .getNumPoints() - 1;
                        }
                    }

                    if (routeToNav == null && navUID == null) {
                        Toast.makeText(context,
                                R.string.routes_text5,
                                Toast.LENGTH_LONG).show();

                        return;
                    }

                    // Clear any previous re-route state
                    _navPrefs.edit()
                            .putBoolean(
                                    RerouteDialog.PREF_IS_REROUTE_ACTIVE_KEY,
                                    false)
                            .apply();

                    // Start up nav
                    startNewNav(context, navUID, routeToNav, startIndex);

                    // If there are re-route capable route planning engines,
                    // then we will put up the re-route button.
                    setupRerouteButton();
                }

                break;
            }
            case END_NAV:
                // Mark all assoc markers as nav in progress
                synchronized (routes_) {
                    for (Route r : routes_) {
                        r.setMetaBoolean("navigating", false);
                        r.setMetaBoolean("ready_to_nav", true);
                    }
                }

                removeRerouteButton();

                _navigator.stopNavigating();
                break;
            case MANAGE_ACTION:
                showManageRoutes();
                break;

            // Register menu items in the tool selector
            case "com.atakmap.android.maps.TOOLSELECTOR_READY":
                // Route Navigator
                // intent to run when tool is selected
                Intent myLocationIntent = new Intent();
                myLocationIntent
                        .setAction(
                                "com.atakmap.android.maps.toolbar.SET_TOOLBAR");
                myLocationIntent.putExtra("toolbar",
                        "com.atakmap.android.routes.NAVIGATION");
                // *need* a request code, or we'll overwrite other pending intents with the same action!
                // Hopefully hash code is unique enough?
                PendingIntent act = PendingIntent.getBroadcast(_context,
                        _navigator.hashCode(),
                        myLocationIntent, 0);

                // register with selector
                Intent toolSelectorRegisterIntent = new Intent();
                toolSelectorRegisterIntent
                        .setAction(
                                "com.atakmap.android.maps.TOOLSELECTION_NOTIFY");
                toolSelectorRegisterIntent
                        .addCategory("com.atakmap.android.maps.INTEGRATION"); // what

                // does
                // the
                // category
                // do?
                toolSelectorRegisterIntent.putExtra("title", "Route Navigator");
                toolSelectorRegisterIntent.putExtra("action", act);

                AtakBroadcast.getInstance().sendBroadcast(
                        toolSelectorRegisterIntent);

                // Hide navigation toolbar entry in the tool selector menu until user is actually
                // navigating
                myLocationIntent = new Intent();
                myLocationIntent
                        .setAction(
                                "com.atakmap.android.maps.TOOLSELECTION_HIDE");
                myLocationIntent.putExtra("title", "Route Navigator");
                AtakBroadcast.getInstance().sendBroadcast(myLocationIntent);
                break;

            // Register menu items in the preferences screen
            case "com.atakmap.android.maps.EXTERNAL_PREFS_READY":

                // Route Builder

                break;
            case ROUTE_IMPORT:
                final String file = intent.getStringExtra("filename");
                if (file != null) {
                    new ImportRouteTask(new File(
                            FileSystemUtils.sanitizeWithSpacesAndSlashes(file)),
                            false).execute();
                } else {
                    importRoute();
                }
                break;
            case ROUTE_TRANSFER: {
                //Mission Package route transfer handler. In this case we want to import as a route
                //explicitly, not as KML/GPX overlay
                String sender = intent.getStringExtra(
                        MissionPackageApi.INTENT_EXTRA_SENDERCALLSIGN);
                if (sender == null) {
                    Log.w(TAG, "Received invalid attachment sender callsign");
                    return;
                }

                MissionPackageManifest manifest = intent
                        .getParcelableExtra(
                                MissionPackageApi.INTENT_EXTRA_MISSIONPACKAGEMANIFEST);
                if (manifest == null || manifest.isEmpty()) {
                    Log.w(TAG, "Received invalid route manifest");
                    return;
                }

                int notificationid = intent.getIntExtra(
                        MissionPackageApi.INTENT_EXTRA_NOTIFICATION_ID,
                        67478);

                //find files to import
                List<File> toImport = new ArrayList<>();
                for (MissionPackageContent content : manifest.getFiles()) {
                    if (content.isIgnore())
                        continue; // skip it

                    NameValuePair p = content
                            .getParameter(
                                    MissionPackageContent.PARAMETER_LOCALPATH);
                    if (p == null || !p.isValid()
                            || !FileSystemUtils.isFile(p.getValue())) {
                        Log.e(TAG,
                                "Route Transfer Download Failed - Failed to extract Package: "
                                        + manifest.getName()
                                        + ", "
                                        + (p == null ? ""
                                                : p
                                                        .getValue()));
                        continue;
                    }

                    if (!isImportSupported(p.getValue())) {
                        Log.e(TAG,
                                "Route Transfer File not supported: "
                                        + manifest.getName()
                                        + ", "
                                        + (p == null ? ""
                                                : p
                                                        .getValue()));
                        continue;
                    }

                    toImport.add(new File(p.getValue()));
                }

                if (FileSystemUtils.isEmpty(toImport)) {
                    Log.w(TAG, "No routes to import from Mission Package");
                    return;
                }

                // now build and update the ongoing notification
                String message = String.format("%s sent route: %s", sender,
                        manifest.getName());

                // view route list
                Intent notificationIntent = new Intent();
                notificationIntent.setAction(MANAGE_ACTION);
                NotificationUtil.getInstance().postNotification(notificationid,
                        R.drawable.share_route, NotificationUtil.WHITE,
                        context.getString(R.string.routes_text7), message,
                        notificationIntent, true);

                Log.d(TAG,
                        "Updated notification for routes: "
                                + manifest);

                //kick off tasks to import
                for (File importFile : toImport) {
                    new ImportRouteTask(importFile, false).execute();
                }
                break;
            }
            case ROUTE_EXPORT: {
                final String routeUID = intent.getStringExtra("routeUID");
                if (FileSystemUtils.isEmpty(routeUID)) {
                    Log.w(TAG, "Unable to export route with no routeUID");
                    new AlertDialog.Builder(_context)
                            .setTitle(R.string.export_failed)
                            .setMessage(R.string.routes_text8)
                            .setPositiveButton(R.string.ok, null)
                            .show();
                    return;
                }

                MapItem mi = _mapView.getRootGroup().deepFindUID(routeUID);
                if (!(mi instanceof Route)) {
                    Log.w(TAG,
                            "Failed to export route that isn't a route: " + mi);
                    return;
                }

                showExportPrompt((Route) mi);
                break;
            }
            case ACTION_ROUTE_IMPORT_FINISHED: {
                Intent notificationIntent = new Intent();
                notificationIntent
                        .setAction(
                                "com.atakmap.android.maps.ROUTE_NOTI_CLICKED");
                notificationIntent
                        .putExtra("NOTI_ROUTE_ID",
                                intent.getStringExtra(EXTRA_ROUTE_UID));
                if (intent.getBooleanExtra(EXTRA_ROUTE_IS_NEW, false)) {
                    NotificationUtil.getInstance().postNotification(notiIndx,
                            R.drawable.share_route, NotificationUtil.WHITE,
                            context.getString(R.string.routes_text9),
                            intent.getStringExtra(EXTRA_ROUTE_TITLE),
                            intent.getStringExtra(EXTRA_ROUTE_TYPE),
                            notificationIntent, false, false, false, true);
                } else if (intent.getBooleanExtra(EXTRA_ROUTE_UPDATED, false)) {
                    NotificationUtil.getInstance().postNotification(notiIndx,
                            R.drawable.share_route, NotificationUtil.WHITE,
                            context.getString(R.string.routes_text32),
                            intent.getStringExtra(EXTRA_ROUTE_TITLE),
                            intent.getStringExtra(EXTRA_ROUTE_TYPE),
                            notificationIntent, false, false, false, true);
                }
                break;
            }
            case PROCESSING_DONE:
                if (_plannerView != null)
                    _plannerView.processingDone();
                break;
        }
    }

    private RootLayoutWidget getRootLayoutWidget() {
        return (RootLayoutWidget) _mapView
                .getComponentExtra("rootLayoutWidget");

    }

    private LinearLayoutWidget getRightLayoutWidget() {
        return getRootLayoutWidget().getLayout(RootLayoutWidget.RIGHT_EDGE);
    }

    private void showRerouteDialog(final RoutePlannerManager rpm) {

        final RerouteDialog dlg = new RerouteDialog(
                (MapActivity) MapView.getMapView().getContext(),
                MapView.getMapView(), rpm);

        dlg.setListener(new Dialog.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == Dialog.BUTTON_NEGATIVE) {
                    rerouteButton.setIcon(rerouteInactiveIcon);
                    rerouteButton.setState(REROUTE_BUTTON_OFF);
                    _navPrefs.edit()
                            .putBoolean(
                                    RerouteDialog.PREF_IS_REROUTE_ACTIVE_KEY,
                                    false)
                            .apply();
                    dialog.dismiss();
                } else if (which == Dialog.BUTTON_POSITIVE) {
                    rerouteButton.setIcon(rerouteActiveIcon);
                    rerouteButton.setState(REROUTE_BUTTON_ON);
                    _navPrefs.edit()
                            .putBoolean(
                                    RerouteDialog.PREF_IS_REROUTE_ACTIVE_KEY,
                                    true)
                            .apply();
                    dialog.dismiss();
                }
            }
        });

        dlg.show();
    }

    private void startNewNav(Context context, String navUID, Route routeToNav,
            int startIndex) {
        // exit route creation / editing tool and go_to_map_tool
        //
        // HMM: putting this here so there's a chance the map zoom will make sense in the
        // end... but it could go anywhere while we start nav
        // TODO: maybe in the case where you go creation--> nav the route details screen
        // shouldn't be shown for the newly created route?
        Intent myIntent = new Intent(
                "com.atakmap.android.routes.GOTO_NAV_END");
        AtakBroadcast.getInstance()
                .sendBroadcast(myIntent);

        myIntent.setAction(
                ToolManagerBroadcastReceiver.END_TOOL);
        /**/
        myIntent.putExtra("tool", new String[] {
                RouteEditTool.TOOL_IDENTIFIER,
        });
        AtakBroadcast.getInstance()
                .sendBroadcast(myIntent);

        // int startingIndex = 0;
        // if (uid != null) {
        // // TODO??: if user selected start nav from a radial menu of a point in this
        // route, then start navigating to that point.
        // startingIndex = routeToNav.getIndexOfMarker(found);
        // }

        if (routeToNav == null) {
            Intent gotoTool = new Intent(
                    "com.atakmap.android.routes.GOTO_NAV_BEGIN");
            gotoTool.putExtra("target", navUID);
            AtakBroadcast.getInstance()
                    .sendBroadcast(gotoTool);
        } else if (_navigator.startNavigating(routeToNav, startIndex)) {

            // zoom map to fit all waypoints of this route and the current device location
            // disabled because this interupts the user's flow if they already have the map
            // set up how they want it!
            // _scaleToFit(routeToNav, true, false);

            // Mark all routes as nav in progress
            synchronized (routes_) {
                for (Route r : routes_) {
                    r.setMetaBoolean("navigating",
                            true);
                    r.setMetaBoolean("ready_to_nav",
                            false);
                }
            }
            DropDownManager.getInstance().closeAllDropDowns();
        } else {
            if (routeToNav.getNumPoints() == 1) {
                // Attempting to navigate to single-point route
            } else
                Toast.makeText(context,
                        R.string.routes_text6,
                        Toast.LENGTH_LONG).show();
        }

    }

    /**
     * Provided a UID, launch the cue editing workflow
     */
    void editCues(final Route route, final PointMapItem pmi,
            final PointMapItem from) {
        if (route == null || pmi == null) {
            Log.d(TAG, "cannot edit a queue for a null item");
            return;
        }
        //final String fromUID = (from != null) ? from.getUID() : "*";
        final String fromUID = "*"; //For now we aren't supporting fromUIDs
        Log.d(TAG,
                pmi.getType() + " "
                        + pmi.getMetaString("callsign", "unknown")
                        + " with ID "
                        + pmi.getUID() + " from " + fromUID);
        AlertDialog.Builder b = new AlertDialog.Builder(
                _mapView.getContext());
        LayoutInflater inflater = LayoutInflater
                .from(_mapView.getContext());
        View v = inflater.inflate(R.layout.nav_edit_cues, _mapView, false);
        final EditText et = v.findViewById(R.id.messageBox);

        // required for a multi line edit text to have a DONE button
        et.setRawInputType(InputType.TYPE_CLASS_TEXT);

        final NavigationCue nc = route.getCueForPoint(pmi.getUID());

        // Cue buttons
        final List<View> btns = new ArrayList<>();
        btns.add(v.findViewById(R.id.navcue_btn_slight_left));
        btns.add(v.findViewById(R.id.navcue_btn_straight));
        btns.add(v.findViewById(R.id.navcue_btn_slight_right));
        btns.add(v.findViewById(R.id.navcue_btn_left));
        btns.add(v.findViewById(R.id.navcue_btn_right));
        btns.add(v.findViewById(R.id.navcue_btn_hard_left));
        btns.add(v.findViewById(R.id.navcue_btn_hard_right));
        btns.add(v.findViewById(R.id.navcue_btn_danger));
        btns.add(v.findViewById(R.id.navcue_btn_stop));
        btns.add(v.findViewById(R.id.navcue_btn_speedup));
        btns.add(v.findViewById(R.id.navcue_btn_slowdown));
        View.OnClickListener cueEditor = new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (!v.isSelected()) {
                    String desc = String.valueOf(v.getContentDescription());
                    if (!FileSystemUtils.isEmpty(desc))
                        et.setText(desc);
                } else
                    et.setText("");
                updateSelectedCue(btns, String.valueOf(et.getText()));
            }
        };
        for (View btn : btns) {
            btn.setOnClickListener(cueEditor);
            btn.setTag(RoutePlannerView.getNavigationIcon(String.valueOf(
                    btn.getContentDescription())));
        }

        // Cue text
        et.addTextChangedListener(new AfterTextChangedWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateSelectedCue(btns, String.valueOf(s));
            }
        });
        if (nc != null) {
            if (nc.getVoiceCue() != null)
                et.setText(nc.getVoiceCue());
            else
                et.setText("");
        }

        String callsign = pmi.getMetaString("callsign", "Unlabeled Point");
        if (callsign.length() == 0) {
            callsign = "Unlabeled Point";
        }

        b.setTitle("Cue: " + callsign);
        b.setView(v);
        b.setPositiveButton(R.string.ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                final String cue = et.getText().toString().trim();
                String cpUid = pmi.getUID();
                NavigationCue newCue = null;
                Undoable undo = route.getUndoable();
                EditAction act = null;
                if (!FileSystemUtils.isEmpty(cue)) {
                    // do not reuse the original cue since
                    // the near and far might have changed
                    newCue = new NavigationCue(pmi.getUID(), cue, cue);
                    newCue.addCue(NavigationCue.TriggerMode.DISTANCE,
                            70);
                    PointMapItem wp = pmi;
                    if (wp.getType().equals(Route.CONTROLPOINT_TYPE)) {
                        // Workaround for route nav line skipping control points w/ cues
                        // Turn the control point into a way point when we add a cue to it

                        // Make sure we can retrieve the CP index
                        int index = route.getIndexOfPoint(wp);
                        if (index == -1)
                            // Then check last hit index
                            index = route.getMetaInteger("hit_index",
                                    -1);
                        if (index == -1) {
                            Toast.makeText(_context,
                                    R.string.route_cannot_find_point,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        wp = Route.createWayPoint(route.getPoint(index),
                                UUID.randomUUID().toString());

                        // Set waypoint using undoable action
                        act = route.getActionProvider()
                                .newExchangePointAction(index,
                                        wp, null);
                    }
                    cpUid = wp.getUID();
                }
                if (act == null) {
                    // Set cue using undoable action
                    act = route.new RouteSetCueAction(cpUid, newCue);
                }
                if (undo != null)
                    undo.run(act);
                else
                    act.run();
                if (!(act instanceof Route.RouteSetCueAction))
                    route.setNavigationCueForPoint(cpUid, newCue);
            }
        });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    private void updateSelectedCue(List<View> cueViews, String cue) {
        int cueRes = RoutePlannerView.getNavigationIcon(cue);
        for (View cueView : cueViews) {
            cueView.setSelected(false);
            Object o = cueView.getTag();
            if (!(o instanceof Integer))
                continue;
            if ((Integer) o == cueRes)
                cueView.setSelected(true);
        }
    }

    public static void startEdit(String uid) {
        Bundle bundle = new Bundle();
        bundle.putString("routeUID", uid);
        bundle.putString("uid", uid);
        bundle.putBoolean("ignoreToolbar", false);
        bundle.putBoolean("scaleToFit", false);
        ToolManagerBroadcastReceiver.getInstance().startTool(
                RouteEditTool.TOOL_IDENTIFIER,
                bundle);
    }

    private void importRoute() {
        TileButtonDialog d = new TileButtonDialog(_mapView);
        d.addButton(R.drawable.ic_menu_import_file, R.string.file_select);
        d.addButton(R.drawable.select_from_map, R.string.map_select);
        d.setOnClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    importRouteFile();
                } else if (which == 1) {
                    ToolManagerBroadcastReceiver.getInstance().startTool(
                            PolylineSelectTool.TOOL_IDENTIFIER, new Bundle());
                }
            }
        });
        d.show(R.string.route_import, R.string.route_import_msg, true);

    }

    private void importRouteFile() {
        ImportFileBrowserDialog.show(
                _context.getString(R.string.routes_text10),
                new String[] {
                        "kml", "kmz", "gpx"
                },
                new ImportFileBrowserDialog.DialogDismissed() {
                    @Override
                    public void onFileSelected(final File file) {
                        if (file != null) {
                            final String path = file.getAbsolutePath();
                            if (!isImportSupported(path)) {
                                Log.w(TAG,
                                        "unsupported file selected: " + path);
                                return;
                            }
                            Log.w(TAG, "file selected: " + file);
                            new ImportRouteTask(file, true, true).execute();
                        } else {
                            Log.w(TAG, "no file selected for import: " + file);
                            showManageRoutes();
                        }
                    }

                    @Override
                    public void onDialogClosed() {
                        // navigate back to OM
                        showManageRoutes();
                    }
                }, _mapView.getContext());

    }

    public Route getRouteWithPoint(PointMapItem item) {
        synchronized (routes_) {
            for (Route r : routes_) {
                if (r.hasMarker(item)) {
                    return r;
                }
            }
        }
        return null;
    }

    public static void showManageRoutes() {
        AtakBroadcast.getInstance().sendBroadcast(
                RouteListModel.getRoutesIntent());
    }

    /**
     * Show route details drop-down
     * @param route Route to show details for
     * @param autoPlan The route planner interface to use if this route
     *                 is being created in automatic mode
     */
    public void showRouteDetails(Route route, RoutePlannerInterface autoPlan,
            boolean edit) {

        // send a hide menu intent in order to hide the toolbar if it's still open
        Intent myIntent = new Intent();
        myIntent.setAction("com.atakmap.android.maps.HIDE_MENU");
        AtakBroadcast.getInstance().sendBroadcast(myIntent);

        // Shouldn't be navigating and editing route details at the same time
        _navigator.requestStopNavigating();

        int ddWidth = (int) (_mapView.getWidth()
                * DropDownReceiver.THREE_EIGHTHS_WIDTH);

        // zoom to the route
        ATAKUtilities.scaleToFit(_mapView, route, false,
                _mapView.getWidth() - ddWidth, _mapView.getHeight());

        if (_plannerView != null)
            _plannerView.close();
        _plannerView = (RoutePlannerView) LayoutInflater.from(
                _mapView.getContext()).inflate(R.layout.route_planner,
                        _mapView, false);
        _plannerView.init(_mapView, route, this, autoPlan);
        if (edit)
            _plannerView.startEdit();
    }

    public void showRouteDetails(Route route) {
        showRouteDetails(route, null, false);
    }

    /**
     * Dim or Undim all routes, except for the route specified
     * @param excludedUID The UID of the route that isn't dimmed
     *                    Pass null to dim all
     * @param state to dim or undim all routes
     */
    public void dimRoutes(String excludedUID, boolean state,
            boolean stopTouch) {
        synchronized (routes_) {
            for (Route r : routes_) {
                if (state && (FileSystemUtils.isEmpty(excludedUID)
                        || !excludedUID.equals(r.getUID()))) {
                    r.setAlpha(50);
                    r.hideLabels(true);
                    if (stopTouch)
                        r.setClickable(false);
                } else {
                    r.resetAlpha();
                    r.hideLabels(false);
                    r.setClickable(true);
                }
            }
        }
    }

    public void dimRoutes(Route excluded, boolean state, boolean stopTouch) {
        dimRoutes(excluded != null ? excluded.getUID() : null, state,
                stopTouch);
    }

    public void dimRoutes(boolean state) {
        if (!state)
            dimRoutes((String) null, false, false);
    }

    public void toggleTouchEvents(Route excluded, boolean stopTouch) {
        String uid = excluded != null ? excluded.getUID() : null;
        synchronized (routes_) {
            for (Route r : routes_)
                r.setClickable(!stopTouch
                        || !FileSystemUtils.isEmpty(uid)
                                && uid.equals(r.getUID()));
        }
    }

    /**
     * Constructs a complete mirror image of the route without doing much.
     */
    public void mirrorImage(Route route, String newUID) {
        Route existing = getRoute(newUID);
        if (existing != null)
            getRouteGroup().removeItem(existing);
        final Route r = getNewRoute(newUID);

        Map<String, NavigationCue> newCues = new HashMap<>(
                r.getNavigationCues().size());
        List<PointMapItem> points = route.getPointMapItems();
        List<PointMapItem> revpoints = new ArrayList<>();
        for (int i = points.size() - 1; i >= 0; i--) {
            PointMapItem p = points.get(i);

            if (p.getType().equals(Route.WAYPOINT_TYPE)) {
                PointMapItem wp = Route.createWayPoint(p.getGeoPointMetaData(),
                        UUID.randomUUID().toString());

                // Get our old cue, make the new version (if we have an old one), and stow it
                // for later
                NavigationCue oldCue = route.getCueForPoint(p.getUID());

                if (oldCue != null) {
                    NavigationCue newCue = NavigationCue.inverseCue(wp.getUID(),
                            oldCue);
                    newCues.put(wp.getUID(), newCue);
                }

                revpoints.add(wp);
            } else if (p.getType().equals(Route.CONTROLPOINT_TYPE)) {
                revpoints.add(Route.createControlPoint(p.getPoint(),
                        UUID.randomUUID().toString()));
            }
        }

        r.addMarkers(0, revpoints);
        r.setNavigationCues(newCues);
        r.setMetaString("entry", "user");
        getRouteGroup().addItem(r);
        r.setVisible(true);
        r.setColor(route.getStrokeColor());
        r.setRouteType(route.getRouteType().text);
        r.setRouteDirection(route.getRouteDirection().text);
        r.setRouteMethod(route.getRouteMethod().text);
        r.setRouteOrder(route.getRouteOrder().text);
        // Update the title
        String title = route.getTitle();
        if (title.endsWith(REVERSED_SUFFIX))
            title = title.substring(0, title.lastIndexOf(REVERSED_SUFFIX));
        if (newUID.endsWith(".reversed"))
            r.setTitle(title + REVERSED_SUFFIX);
        else
            r.setTitle(title);
    }

    /**
     * Constructs a reverse route of the existing route utilizing a planner
     */
    public void createReversedRoute(Route route, String newUID,
            RoutePlannerInterface rpi, boolean includeCP, boolean showDetails) {

        // First check if route with newUID already exists
        Route existing = getRoute(newUID);
        if (existing != null)
            getRouteGroup().removeItem(existing);

        // Create new reversed route
        List<PointMapItem> points = route.getPointMapItems();
        final Route r = getNewRoute(newUID);
        for (int i = points.size() - 1; i >= 0; i--) {
            PointMapItem p = points.get(i);
            if (p == null)
                continue;
            if (i == 0 || i == points.size() - 1 || includeCP
                    && p.getType().equals(Route.WAYPOINT_TYPE))
                r.addMarker(Route.createWayPoint(p.getGeoPointMetaData(),
                        UUID.randomUUID().toString()));
        }
        r.setMetaString("entry", "user");
        getRouteGroup().addItem(r);
        r.setVisible(true);
        r.setColor(route.getStrokeColor());

        // Update the title
        String title = route.getTitle();
        if (title.endsWith(REVERSED_SUFFIX))
            title = title.substring(0, title.lastIndexOf(REVERSED_SUFFIX));
        if (newUID.endsWith(".reversed"))
            r.setTitle(title + REVERSED_SUFFIX);
        else
            r.setTitle(title);

        if (showDetails)
            showRouteDetails(r, rpi, false);
    }

    /**
     * Show a list of export types for a route (pulled from Overlay Manager)
     * @param route Route
     */
    private void showExportPrompt(final Route route) {
        List<ExportMarshalMetadata> candidates = ExporterManager
                .getExporterTypes();
        List<ExportMarshalMetadata> exportTypes = new ArrayList<>(
                candidates.size());

        // Remove exporters that don't support routes
        for (ExportMarshalMetadata type : candidates) {
            ExportMarshal marshal = type.getExportMarshal(_context);

            // Marshal failed initialization
            if (marshal == null)
                continue;

            // Routes not supported
            if (marshal.filterItem(route))
                continue;

            // Exclude DP export - it's not meant to be used outside of OM
            // XXX - The Data Package system is a mess when it comes to
            // accessing and updating packages. The package itself is
            // constantly being de-serialized upon usage and has little access
            // outside of the OM UI. I should be able to request a package
            // manifest by its UID without de-serializing an entire list of
            // XML files saved in a local file database...
            // Not to mention the heavy reliance on intents as opposed to
            // direct calls. Intents which use OM as a bridge to the package,
            // instead of just accessing it directly.
            if (marshal instanceof MissionPackageExportMarshal)
                continue;

            exportTypes.add(type);
        }

        // Can't continue without any valid exporters
        if (FileSystemUtils.isEmpty(exportTypes)) {
            Toast.makeText(_context, R.string.no_exporters_registered,
                    Toast.LENGTH_SHORT).show();
            Log.w(TAG,
                    "Failed to export selected items, no exporters registered");
            return;
        }

        ExportMarshalMetadata[] metaArray = exportTypes.toArray(
                new ExportMarshalMetadata[0]);
        final ExportMarshalAdapter exportAdapter = new ExportMarshalAdapter(
                _context, R.layout.exportdata_item, metaArray);

        LayoutInflater inflater = LayoutInflater.from(_context);
        View v = inflater.inflate(R.layout.exportdata_list, _mapView, false);
        ListView list = v.findViewById(R.id.exportDataList);
        list.setAdapter(exportAdapter);

        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setIcon(R.drawable.export_menu_default);
        b.setTitle(R.string.selection_export_dialogue);
        b.setView(v);
        b.setNegativeButton(R.string.cancel, null);
        final AlertDialog d = b.show();

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> pr, View v, int p, long id) {
                d.dismiss();

                ExportMarshalMetadata meta = exportAdapter.getItem(p);
                if (meta == null) {
                    Log.w(TAG,
                            "Failed to export selected items, no type selected");
                    return;
                }

                //go ahead and select items
                final ExportMarshal marshal = meta.getExportMarshal(_context);
                if (marshal == null) {
                    Log.w(TAG,
                            "Failed to export " + route
                                    + " - marshal could not be created");
                    return;
                }

                List<Exportable> exports = new ArrayList<>(1);
                exports.add(route);
                try {
                    marshal.execute(exports);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to export " + route, e);
                }
            }
        });
    }

    private static boolean isImportSupported(String filepath) {
        if (FileSystemUtils.isEmpty(filepath))
            return false;

        String f = filepath.toLowerCase(LocaleUtil.getCurrent());
        return f.endsWith(".gpx") || f.endsWith(".kml") || f.endsWith(".kmz");
    }

    /**
     * Simple background task import ATAK Route from KML or GPX
     * 
     * 
     */
    private class ImportRouteTask extends AsyncTask<Void, Void, String> {

        private static final String TAG = "ImportRouteTask";

        private final File _file;
        private final List<Route> _importRoutes;
        private final String contentType;
        private final boolean _displayResults, _userEntry;

        public ImportRouteTask(File file, boolean displayResults,
                boolean userEntry) {
            _file = file;
            _importRoutes = new ArrayList<>();
            _displayResults = displayResults;
            _userEntry = userEntry;

            String path = file.getAbsolutePath();
            if (path.endsWith(".kml") || path.endsWith(".kmz")) {
                contentType = KmlFileSpatialDb.KML_CONTENT_TYPE;
            } else if (path.endsWith(".gpx")) {
                contentType = GpxFileSpatialDb.GPX_CONTENT_TYPE;
            } else
                contentType = null;
        }

        public ImportRouteTask(File file, boolean displayResults) {
            this(file, displayResults, false);
        }

        @Override
        protected void onPreExecute() {
            Log.d(TAG,
                    "Importing " + contentType + " file: "
                            + _file.getAbsolutePath());
            Toast.makeText(
                    _context,
                    _context.getString(R.string.importing) + contentType
                            + _context.getString(R.string.routes_text18)
                            + _file.getName(),
                    Toast.LENGTH_LONG).show();
        }

        @Override
        protected String doInBackground(Void... params) {
            Thread.currentThread().setName("ImportRouteTask");
            if (KmlFileSpatialDb.KML_CONTENT_TYPE.equalsIgnoreCase(contentType))
                return importKml();
            else if (GpxFileSpatialDb.GPX_CONTENT_TYPE
                    .equalsIgnoreCase(contentType))
                return importGpx();
            else
                return "Unsupported Import Format";
        }

        protected String importKml() {

            // Read KML file
            Kml kml = RouteKmlIO.read(_file, _context);
            if (kml == null) {
                Log.w(TAG,
                        "Unable to read KML file: " + _file.getAbsolutePath());
                return "Unable to read KML file: " + _file.getName();
            }

            // convert KML to Route
            Route route = RouteKmlIO.toRoute(_mapView, kml, _routeGroup,
                    _waypointGroup, _navPrefs);
            if (route == null) {
                Log.w(TAG,
                        "Unable to convert KML to route: "
                                + _file.getAbsolutePath());
                return "Invalid KML Route file: " + _file.getName();
            }

            _importRoutes.add(route);
            return null;
        }

        protected String importGpx() {

            // Read GPX file
            Gpx gpx = RouteGpxIO.read(_file);
            if (gpx == null) {
                Log.w(TAG,
                        "Unable to read GPX file: " + _file.getAbsolutePath());
                return "Unable to read GPX file: " + _file.getName();
            }

            // convert GPX to Route
            List<Route> routes = RouteGpxIO.toRoute(_mapView, gpx, _routeGroup,
                    _waypointGroup,
                    _navPrefs);
            if (routes == null || routes.size() < 1) {
                Log.w(TAG,
                        "Unable to convert GPX to route: "
                                + _file.getAbsolutePath());
                return "Invalid GPX Route file: " + _file.getName();
            }

            _importRoutes.addAll(routes);
            return null;
        }

        @Override
        protected void onPostExecute(String error) {
            if (!FileSystemUtils.isEmpty(error)) {
                if (_displayResults)
                    Toast.makeText(_context, error, Toast.LENGTH_LONG).show();
            } else if (_importRoutes == null || _importRoutes.size() < 1) {
                Log.w(TAG, "Failed to create " + contentType + " Route");

                if (_displayResults) {
                    new AlertDialog.Builder(_context)
                            .setTitle(R.string.import_failed)
                            .setMessage(R.string.routes_text19)
                            .setPositiveButton(R.string.ok,
                                    new OnClickListener() {

                                        @Override
                                        public void onClick(
                                                DialogInterface dialog,
                                                int which) {
                                            showManageRoutes();
                                            dialog.dismiss();
                                        }
                                    })
                            .show();
                }
            } else {
                // add route to map and persist to file system
                for (Route r : _importRoutes) {
                    if (_userEntry)
                        r.setMetaString("entry", "user");
                    getRouteGroup().addItem(r);
                    r.persist(_mapView.getMapEventDispatcher(), null,
                            RouteMapReceiver.class);
                    Log.d(TAG,
                            "Imported " + contentType + " Route: "
                                    + r.getTitle());
                }

                if (_displayResults) {
                    if (_importRoutes.size() > 1) {
                        Log.d(TAG, "Imported " + _importRoutes.size()
                                + " Routes");
                        Toast.makeText(_context,
                                _context.getString(R.string.imported)
                                        + _importRoutes.size()
                                        + _context.getString(
                                                R.string.routes_text20),
                                Toast.LENGTH_LONG).show();
                        showManageRoutes();

                    } else {
                        Toast.makeText(_context,
                                _context.getString(R.string.routes_text21)
                                        + _importRoutes.get(0).getTitle(),
                                Toast.LENGTH_LONG).show();
                        showRouteDetails(_importRoutes.get(0));
                    }
                }
            }
        }
    }

    /**
     * Add the reroute button to the screen
     */
    private void setupRerouteButton() {
        MapComponent mc = ((MapActivity) _context).getMapComponent(
                RouteMapComponent.class);
        if (mc == null)
            return;

        final RoutePlannerManager routePlannerManager = ((RouteMapComponent) mc)
                .getRoutePlannerManager();

        // No reroute capable route planners - hide the button
        if (routePlannerManager.getReroutePlanners().size() == 0) {
            removeRerouteButton();
            return;
        }

        // Button is already setup
        if (rerouteButton != null)
            return;

        rerouteButton = new MarkerIconWidget();

        String activeIconImageUri = ATAKUtilities.getResourceUri(
                R.drawable.reroute_icon_inactive);

        String inactiveIconImageUri = ATAKUtilities.getResourceUri(
                R.drawable.reroute_icon_active);

        int width = 64;
        int height = 64;

        Icon.Builder builder = new Icon.Builder();
        builder.setAnchor(0, 0);
        builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
        builder.setSize(width, height);
        builder.setImageUri(Icon.STATE_DEFAULT, activeIconImageUri);
        builder.setColor(Icon.STATE_DEFAULT, Color.argb(216, 255, 255, 255));
        rerouteInactiveIcon = builder.build();

        rerouteButton.setIcon(rerouteInactiveIcon);
        rerouteButton.setPadding(20f);
        rerouteButton.setState(REROUTE_BUTTON_OFF);

        builder.setImageUri(Icon.STATE_DEFAULT, inactiveIconImageUri);
        rerouteActiveIcon = builder.build();

        rerouteButton.addOnPressListener(new MapWidget.OnPressListener() {
            @Override
            public void onMapWidgetPress(MapWidget widget, MotionEvent event) {

                // Make sure there are route planners available
                if (routePlannerManager.getReroutePlanners().size() == 0) {
                    // No reroute capable route planners - hide the button
                    // XXX - Ideally we'd hide the button when the last planner
                    // is unregistered but there's no hook for this...
                    removeRerouteButton();
                    Toast.makeText(_context, R.string.bloodhound_no_planners,
                            Toast.LENGTH_LONG).show();
                    return;
                }

                if (rerouteButton.getState() == REROUTE_BUTTON_OFF) {
                    try {
                        // showRerouteDialog can throw an IllegalStateException
                        // handle it by removing the reroute button and toasting
                        showRerouteDialog(routePlannerManager);
                    } catch (Exception e) {
                        removeRerouteButton();
                        Toast.makeText(_context, R.string.bloodhound_no_planners,
                                Toast.LENGTH_LONG).show();
                    }
                } else if (rerouteButton.getState() == REROUTE_BUTTON_ON) {
                    rerouteButton.setIcon(rerouteInactiveIcon);
                    rerouteButton.setState(REROUTE_BUTTON_OFF);

                    // Turn off re-routing
                    _navPrefs.edit().putBoolean(
                            RerouteDialog.PREF_IS_REROUTE_ACTIVE_KEY,
                            false).apply();

                    Log.d(TAG, "re-route button off");
                }
            }
        });

        getRightLayoutWidget().addWidget(rerouteButton);
    }

    private void removeRerouteButton() {
        if (rerouteButton != null)
            getRightLayoutWidget().removeWidget(rerouteButton);
        rerouteButton = null;
        _navPrefs.edit().putBoolean(RerouteDialog.PREF_IS_REROUTE_ACTIVE_KEY,
                false).apply();
    }

    /**
     * Quick way to call into the Plan route mechanism using an existing start and end point with the route name and color.
     * @param mapView the mapView required for this to work
     * @param start the start geopoint.
     * @param end the end geopoint
     * @param name name of the route
     * @param color color of the route.
     * @return route created from the two geopoints provided
     */
    public static Route promptPlanRoute(final MapView mapView,
            final GeoPoint start, final GeoPoint end, final String name,
            final int color) {
        Route r = new Route(mapView, name, color,
                "CP",
                UUID.randomUUID().toString());

        final Marker startMarker, endMarker;

        r.addMarker(0, startMarker = Route
                .createWayPoint(GeoPointMetaData.wrap(start),
                        UUID.randomUUID().toString()));
        r.addMarker(1, endMarker = Route
                .createWayPoint(GeoPointMetaData.wrap(end),
                        UUID.randomUUID().toString()));
        MapGroup _mapGroup = mapView.getRootGroup()
                .findMapGroup("Route");
        _mapGroup.addItem(r);

        promptPlanRoute(mapView, startMarker, endMarker, r, true);

        r.persist(mapView.getMapEventDispatcher(), null,
                RouteMapReceiver.class);
        return r;
    }

    /**
     * Allows for external parties to call a route planner given two points and a route to fill.
     * @param _mapView the mapView that is required for the proper function of the method.
     * @param origin must exist as part of the _route
     * @param dest must exist as part of the _route
     * @param _route the route to utilize when performing rerouting of part of all of the route
     * @param entireRoute boolean to express that the entire route is being used despite the origin and destination being supplied.
     */
    public static void promptPlanRoute(final MapView _mapView,
            final PointMapItem origin,
            final PointMapItem dest, final Route _route,
            final boolean entireRoute) {

        if (origin == null || dest == null)
            return;

        final Context _context = _mapView.getContext();

        String oName = ATAKUtilities.getDisplayName(origin);
        String dName = ATAKUtilities.getDisplayName(dest);
        String cpNames = entireRoute ? "" : (": " + oName + " -> " + dName);

        Log.d(TAG, "Route to the next wp; origin=" + oName + ", dest=" + dName);

        // Find the way points between our orig and dest so that we can request the calculated
        // route to go through them
        final List<GeoPoint> byWayOf = new ArrayList<>();
        List<PointMapItem> points = _route.getPointMapItems();
        int originIndex = points.indexOf(origin);
        int destIndex = points.indexOf(dest);

        Log.d(TAG, "Found indexes of " + originIndex + " and " + destIndex);

        // Route Planner choices
        final List<Map.Entry<String, RoutePlannerInterface>> routePlanners = new ArrayList<>();

        // Get the RouteMapComponent handle
        MapComponent mc = ((MapActivity) _context)
                .getMapComponent(RouteMapComponent.class);
        RoutePlannerManager _routeManager = mc != null
                ? ((RouteMapComponent) mc)
                        .getRoutePlannerManager()
                : null;

        if (_routeManager != null) {
            final boolean network = RouteMapReceiver.isNetworkAvailable();
            if (!network)
                Toast.makeText(_context,
                        "network not available",
                        Toast.LENGTH_SHORT).show();
            for (Map.Entry<String, RoutePlannerInterface> k : _routeManager
                    .getRoutePlanners()) {
                if (!k.getValue().isNetworkRequired() || network) {
                    routePlanners.add(k);
                }
            }
        }

        if (routePlanners.isEmpty())
            return;
        Collections.sort(routePlanners, RPI_COMP);

        final SharedPreferences _prefs = PreferenceManager
                .getDefaultSharedPreferences(_context);

        // If there's only one, no need to prompt
        if (routePlanners.size() == 1) {
            Map.Entry<String, RoutePlannerInterface> plannerEntry = routePlanners
                    .get(0);
            final RoutePlannerInterface planner = plannerEntry.getValue();
            Log.d(TAG, "Planner \"" + planner.getDescriptiveName()
                    + "\" was selected");

            AlertDialog.Builder b = new AlertDialog.Builder(_context);
            b.setTitle(_context.getString(R.string.route_plan) + cpNames);
            b.setMessage(_context.getString(R.string.route_plan_confirm_msg,
                    planner.getDescriptiveName()));
            b.setPositiveButton(R.string.yes,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            RouteGenerationHandler handler = new RouteGenerationHandler(
                                    _mapView,
                                    origin, dest, _route);

                            RouteGenerationPackage routeGenerationPackage = new RouteGenerationPackage(
                                    _prefs, origin.getPoint(), dest.getPoint(),
                                    byWayOf);

                            planner.getRouteGenerationTask(handler)
                                    .execute(routeGenerationPackage);
                        }
                    });
            b.setNegativeButton(R.string.cancel, null);
            b.show();
        } else {
            final String[] plannerNames = new String[routePlanners.size()];
            for (int i = 0; i < routePlanners.size(); i++) {
                Map.Entry<String, RoutePlannerInterface> entry = routePlanners
                        .get(i);
                plannerNames[i] = entry.getValue().getDescriptiveName();
            }

            // Prompt the user for their preferred route planner
            AlertDialog.Builder plannerPicker = new AlertDialog.Builder(
                    _context);
            plannerPicker.setTitle(R.string.route_plan_select_planner);
            plannerPicker.setItems(plannerNames,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog,
                                final int which) {
                            Map.Entry<String, RoutePlannerInterface> plannerEntry = routePlanners
                                    .get(which);
                            RoutePlannerInterface planner = plannerEntry
                                    .getValue();

                            Log.d(TAG,
                                    "Planner \"" + planner.getDescriptiveName()
                                            + "\" was selected");

                            RouteGenerationHandler handler = new RouteGenerationHandler(
                                    _mapView,
                                    origin, dest, _route);

                            RouteGenerationPackage routeGenerationPackage = new RouteGenerationPackage(
                                    _prefs, origin.getPoint(), dest.getPoint(),
                                    byWayOf);

                            planner.getRouteGenerationTask(handler)
                                    .execute(routeGenerationPackage);
                        }
                    });
            plannerPicker.show();
        }
    }

    static final Comparator<Map.Entry<String, RoutePlannerInterface>> RPI_COMP = new Comparator<Map.Entry<String, RoutePlannerInterface>>() {
        @Override
        public int compare(Map.Entry<String, RoutePlannerInterface> lhs,
                Map.Entry<String, RoutePlannerInterface> rhs) {
            String lName = lhs.getValue().getDescriptiveName();
            String rName = rhs.getValue().getDescriptiveName();
            if (lName == null)
                return 1;
            else if (rName == null)
                return -1;
            int comp = lName.compareToIgnoreCase(rName);
            if (comp != 0)
                return comp;

            // Fallback to comparing keys
            String lKey = lhs.getKey(), rKey = rhs.getKey();
            if (lKey == null)
                return 1;
            else if (rKey == null)
                return -1;
            return lKey.compareToIgnoreCase(rKey);
        }
    };

}
