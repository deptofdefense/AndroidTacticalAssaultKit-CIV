
package com.atakmap.android.routes;

import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.atakmap.android.coordoverlay.CoordOverlayMapReceiver;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.ActionBarView;
import com.atakmap.android.user.CamLockerReceiver;
import com.atakmap.android.user.FocusBroadcastReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.dropdown.DropDownManager;
import com.atakmap.android.editableShapes.EditablePolyline;
import com.atakmap.android.editableShapes.EditablePolylineEditTool;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher.MapEventDispatchListener;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTouchController.DeconflictionListener;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.routes.elevation.model.RouteCache;
import com.atakmap.android.selfcoordoverlay.SelfCoordOverlayUpdater;
import com.atakmap.android.toolbar.ToolbarBroadcastReceiver;
import com.atakmap.android.util.EditAction;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.spatial.SpatialCalculator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.UUID;

public class RouteEditTool extends EditablePolylineEditTool implements
        DeconflictionListener, Shape.OnPointsChangedListener {

    public static final String TAG = "RouteEditTool";

    private final Context _context;
    private final SpatialCalculator _spatialCalculator;
    private final MapEventDispatcher _dispatcher;
    private final RouteMapReceiver _routeMapReceiver;

    // Draw mode
    private final Button _drawButton;
    private boolean _drawMode;
    private DrawingShape _drawShape;
    private int _alignIndex = -1;

    private Intent _onToolEnd;
    private boolean _firstTime = true, _creatingRoute = false,
            _dimRoutes = true;
    private Route _route;

    public static final String TOOL_IDENTIFIER = "com.atakmap.android.maps.route.EDIT_ROUTE";

    public RouteEditTool(MapView mapView, Button undoButton, Button drawButton,
            RouteMapReceiver routeMapReceiver) {
        super(mapView, null, undoButton, TOOL_IDENTIFIER);
        _context = mapView.getContext();
        _dispatcher = mapView.getMapEventDispatcher();
        _drawButton = drawButton;
        _routeMapReceiver = routeMapReceiver;
        _spatialCalculator = new SpatialCalculator.Builder().inMemory().build();
        MAIN_PROMPT = _context.getString(R.string.route_edit);
    }

    @Override
    public void dispose() {
        _spatialCalculator.dispose();
    }

    @Override
    public boolean onToolBegin(Bundle extras) {

        Intent intent = new Intent();
        intent.setAction(CamLockerReceiver.UNLOCK_CAM);
        AtakBroadcast.getInstance().sendBroadcast(intent);

        // Close any active drop-downs
        String routeUID = extras.getString("routeUID");
        _onToolEnd = extras.getParcelable("onToolEnd");

        _poly = _routeMapReceiver.getRoute(routeUID);

        if (_poly == null) {
            MapItem found = _mapView.getMapItem(extras.getString("uid"));

            if (found instanceof PointMapItem)
                _poly = _routeMapReceiver.getRouteWithPoint(
                        (PointMapItem) found);
        }

        if (_poly == null) {
            Log.d(TAG, "one last try to look up the route: " + routeUID);
            MapItem mi = _mapView.getMapItem(extras.getString("routeUID"));
            if (mi instanceof Route) {
                _poly = (Route) mi;
                Log.d(TAG, "got it: " + routeUID);
            }
        }

        if (_poly == null) {
            Log.e(TAG, "Route not found! UID:" + routeUID);
            return false;
        }

        if (extras.getBoolean("hidePane", true))
            DropDownManager.getInstance().hidePane();

        _route = (Route) _poly;
        _creatingRoute = _route.getNumPoints() == 0;
        _route.addOnPointsChangedListener(this);

        // Route is modified, delete it from cache
        RouteCache.getInstance().invalidate(_route.getTitle());

        // send end nav here and in routecreationtool, send endtool in the hnadler for startnav; in
        // order to bridgebetween tools -> [navigation] mode
        // since they're at least currently two very seperate concepts. nav mode could become a tool
        // at some point maybe?
        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(RouteMapReceiver.END_NAV));

        // Show route toolbar unless we've been told to ignore it
        openToolbar(extras);

        // Undo button starts off disabled; undo stack is empty
        _undoButton.setVisibility(View.VISIBLE);
        _undoButton.setEnabled(false);

        // Draw mode button isn't visible unless edit mode is active
        _drawButton.setVisibility(View.VISIBLE);
        _drawButton.setSelected(_drawMode = false);

        boolean retval = super.onToolBegin(extras);

        _mapView.getMapTouchController().addDeconflictionListener(this);

        //Needs to be performed after super.onToolBegin because that implementation clears the
        // listeners.

        // fade all other routes
        _dimRoutes = extras.getBoolean("dimRoutes", true);
        if (_dimRoutes)
            _routeMapReceiver.dimRoutes(_route, true, true);
        else
            _routeMapReceiver.toggleTouchEvents(_route, true);

        SelfCoordOverlayUpdater.getInstance().showGPSWidget(false);

        _dispatcher.addMapEventListener(MapEvent.MAP_LONG_PRESS,
                _defaultListener);
        _dispatcher.addMapEventListener(MapEvent.MAP_CLICK,
                _defaultListener);
        _dispatcher.addMapEventListener(MapEvent.ITEM_LONG_PRESS,
                _defaultListener);

        // using the click listener that is enabled in a general editablepolylinetool
        // is counter intuitive for the routes, may also be counterintuitive for general
        // editable polygons.   disable here for now and simulate a click for waypoints 
        // and control points.

        _dispatcher.clearListeners(MapEvent.ITEM_CLICK);
        _dispatcher.addMapEventListener(MapEvent.ITEM_CLICK, _defaultListener);

        return retval;
    }

    private void openToolbar(Bundle extras) {
        // Ignore toolbar
        if (extras.getBoolean("ignoreToolbar", false))
            return;

        // Check if the route toolbar is already active
        ActionBarView abv = ActionBarReceiver.getInstance().getToolView();
        if (abv != null && abv.getId() == R.id.route_toolbar_view)
            return;

        // Request to open edit toolbar
        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                ToolbarBroadcastReceiver.OPEN_TOOLBAR)
                        .putExtra("toolbar",
                                RouteToolbarBroadcastReceiver.TOOLBAR_IDENTIFIER));
    }

    // Default edit mode behavior
    private final MapEventDispatchListener _defaultListener = new MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            String e = event.getType();

            // Add first waypoint or control point on map click
            switch (e) {
                case MapEvent.MAP_CLICK:
                    if (_route.getNumPoints() == 0)
                        addWayPoint(event, 0);
                    else
                        addControlPoint(event, _route.getNumPoints());
                    break;

                // Add way point on long press
                case MapEvent.MAP_LONG_PRESS:
                    addWayPoint(event, _route.getNumPoints());
                    break;

                // Add waypoint when long pressing a marker
                case MapEvent.ITEM_LONG_PRESS: {
                    MapItem mi = event.getItem();
                    if (!(mi instanceof PointMapItem)
                            || _route.hasMarker((PointMapItem) mi))
                        return;
                    addWayPoint(((PointMapItem) mi).getGeoPointMetaData(),
                            _route.getNumPoints());
                    break;
                }

                // Map item tapped - multiple actions
                case MapEvent.ITEM_CLICK: {
                    MapItem mi = event.getItem();

                    if (mi == null)
                        return;

                    String type = mi.getType();
                    if (type.equals(Route.WAYPOINT_TYPE)
                            || type.equals(Route.CONTROLPOINT_TYPE)
                            || type.equals(_route.getType())) {

                        if (!type.equals(Route.WAYPOINT_TYPE)
                                && multipleVerticesHit())
                            return;

                        final String uid = mi.getUID();
                        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                                FocusBroadcastReceiver.FOCUS)
                                        .putExtra("uid", uid));
                        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                                MapMenuReceiver.SHOW_MENU)
                                        .putExtra("uid", uid));
                        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                                CoordOverlayMapReceiver.SHOW_DETAILS)
                                        .putExtra("uid", uid));
                        return;
                    }

                    GeoPointMetaData point = findPoint(event);
                    if (point != null) {
                        if (mi instanceof PointMapItem)
                            addWayPoint(point, _route.getNumPoints());
                        else
                            addControlPoint(point, _route.getNumPoints());
                    }
                    break;
                }
            }
        }
    };

    @Override
    public void onToolEnd() {
        SelfCoordOverlayUpdater.getInstance().showGPSWidget(true);

        if (_route.getNumPoints() >= 2)
            // Add first/last way point to route if none was added by user
            _route.fixSPandVDO();
        else
            // Remove empty route
            _route.removeFromGroup();

        // Remove the temporary drawing shape
        if (_drawShape != null)
            _drawShape.removeFromGroup();
        _drawShape = null;

        if (_dimRoutes)
            _routeMapReceiver.dimRoutes(false);
        else
            _routeMapReceiver.toggleTouchEvents(_route, false);

        _route.removeOnPointsChangedListener(this);

        AtakBroadcast.getInstance().sendBroadcast(
                new Intent(MapMenuReceiver.HIDE_MENU));

        // hide route toolbar
        Intent myLocationIntent = new Intent();
        myLocationIntent.setAction(ToolbarBroadcastReceiver.UNSET_TOOLBAR);
        AtakBroadcast.getInstance().sendBroadcast(myLocationIntent);

        // undo button disabled; undo stack is empty
        _undoButton.setEnabled(false);
        _drawButton.setSelected(_drawMode = false);
        _drawButton.setVisibility(View.GONE);

        _mapView.getMapTouchController()
                .removeDeconflictionListener(this);

        _spatialCalculator.clear();

        super.onToolEnd();

        DropDownManager.getInstance().unHidePane();

        if (_onToolEnd != null)
            AtakBroadcast.getInstance().sendBroadcast(_onToolEnd);
        _onToolEnd = null;

        _firstTime = false;
    }

    @Override
    public void onPointsChanged(Shape shp) {
        if (shp instanceof Route && !((Route) shp).isBulkOperation()) {
            Route r = (Route) shp;
            Intent i = new Intent(RouteMapReceiver.POINTS_CHANGED_ACTION);
            i.putExtra("uid", r.getUID());
            i.putExtra("title", r.getTitle());
            i.putExtra("isPointAdjusting", r.hasMetaValue("dragInProgress"));
            AtakBroadcast.getInstance().sendBroadcast(i);
        }
    }

    @Override
    protected void insertPoint(EditablePolyline poly, int index) {

        twoListenerPushesDeep = true;
        _insertPointIndex = index + 1;

        // Tell the user we're ready to insert

        container.displayPrompt(R.string.insert_point_route);

        // push all the dispatch listeners
        _dispatcher.pushListeners();

        // clear all the listeners listening for a click
        clearExtraListeners();

        // place a waypoint where the user long tapped
        _dispatcher.addMapEventListener(MapEvent.MAP_LONG_PRESS,
                _insertListener);
        _dispatcher.addMapEventListener(MapEvent.MAP_CLICK, _insertListener);
        _dispatcher.addMapEventListener(MapEvent.ITEM_CLICK, _insertListener);
    }

    // Insert point listener
    private final MapEventDispatchListener _insertListener = new MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            String e = event.getType();
            MapItem item = event.getItem();
            Bundle extras = event.getExtras();

            // Insert waypoint
            if (e.equals(MapEvent.MAP_LONG_PRESS) && item == null) {
                addWayPoint(event, _insertPointIndex);
                twoListenerPushesDeep = false;
                showMainPrompt();
                _dispatcher.popListeners();
            }

            // Insert control point
            else if (e.equals(MapEvent.MAP_CLICK) && item == null) {
                if (_route.getNumPoints() == 0)
                    addWayPoint(event, _route.getNumPoints());
                else
                    addControlPoint(event, _route.getNumPoints());

                twoListenerPushesDeep = false;
                showMainPrompt();
                _dispatcher.popListeners();
            }

            // Tapped item - multiple actions
            else if (e.equals(MapEvent.ITEM_CLICK)) {
                GeoPointMetaData point = findPoint(event);
                if (extras != null)
                    extras.putBoolean("eventNotHandled", point == null);
                if (point != null) {
                    if (item instanceof PointMapItem)
                        addWayPoint(point, _route.getNumPoints());
                    else
                        addControlPoint(point, _route.getNumPoints());
                }
            }
        }
    };

    @Override
    protected void showMainPrompt() {
        String prompt = MAIN_PROMPT;
        if (_creatingRoute) {
            prompt = _context.getString(_firstTime ? R.string.routes_prompt
                    : R.string.routes_prompt_2);
        }
        if (!_vertsVisible && (!_creatingRoute
                || _firstTime && _route.getNumPoints() > 0))
            prompt += "\n" + _context.getString(R.string.route_zoom_in_prompt);

        if (_drawMode)
            prompt = _context.getString(R.string.route_draw_mode_prompt);
        container.displayPrompt(prompt);
    }

    private boolean addControlPoint(GeoPointMetaData gp, int index) {
        if (gp == null)
            return false;
        EditAction act = _route.new InsertPointAction(gp, index);
        return run(act);
    }

    private boolean addControlPoint(MapEvent event, int index) {
        return addControlPoint(
                _mapView.inverseWithElevation(event.getPointF().x,
                        event.getPointF().y),
                index);
    }

    private boolean addWayPoint(GeoPointMetaData gp, int index) {
        if (gp == null)
            return false;
        String lastName = _route.getLastWaypointName();
        Marker waypoint = Route.createWayPoint(gp, UUID.randomUUID()
                .toString());
        waypoint.setMetaString("callsign", lastName);
        waypoint.setTitle(lastName);
        EditAction act = _route.new InsertPointAction(waypoint, index);
        return run(act);
    }

    private boolean addWayPoint(MapEvent event, int index) {
        GeoPointMetaData gp = _mapView.inverseWithElevation(event.getPointF().x,
                event.getPointF().y);
        return addWayPoint(gp, index);
    }

    @Override
    public void undo() {
        super.undo();
        _route.fixSPandVDO(false);
    }

    @Override
    public void onConflict(SortedSet<MapItem> hitItems) {
        Iterator<MapItem> iter = hitItems.iterator();

        // original behavior was to return only the route and did not account for 
        // waypoints - relied on a bug in MapTouchListener.
        //
        // perform a first pass through the hitItems to see if a waypoint was touched
        // if so, remove everything but the waypoint - otherwise remove everything 
        // but the route.
        MapItem save = _route;

        // find out if a b-m-p-w was hit
        while (iter.hasNext()) {
            MapItem hitItem = iter.next();
            String type = hitItem.getType();
            if (type.equals("b-m-p-w")) {
                MapItem shape = ATAKUtilities.findAssocShape(hitItem);
                if (shape == _route)
                    save = hitItem;
            }
        }

        while (iter.hasNext()) {
            MapItem hitItem = iter.next();
            if (hitItem != save)
                iter.remove();
        }
    }

    public void toggleDrawMode() {
        _drawMode = !_drawMode;
        _drawButton.setSelected(_drawMode);

        if (_drawMode) {
            _dispatcher.pushListeners();
            clearExtraListeners();
            twoListenerPushesDeep = true;
            _mapView.getMapEventDispatcher()
                    .clearListeners(MapEvent.MAP_SCROLL);
            _mapView.getMapEventDispatcher().clearListeners(MapEvent.MAP_MOVED);
            _dispatcher.addMapEventListener(MapEvent.MAP_DRAW,
                    _drawListener);
            _dispatcher.addMapEventListener(MapEvent.MAP_RELEASE,
                    _drawListener);
            _dispatcher.addMapEventListener(MapEvent.ITEM_DRAG_CONTINUED,
                    _drawListener);
            _dispatcher.addMapEventListener(MapEvent.ITEM_DRAG_DROPPED,
                    _drawListener);
            // Allow default behavior as well
            _dispatcher.addMapEventListener(MapEvent.MAP_CLICK,
                    _defaultListener);
            _dispatcher.addMapEventListener(MapEvent.MAP_LONG_PRESS,
                    _defaultListener);
            _dispatcher.addMapEventListener(MapEvent.ITEM_LONG_PRESS,
                    _defaultListener);
            _dispatcher.addMapEventListener(MapEvent.ITEM_CLICK,
                    _defaultListener);
        } else {
            twoListenerPushesDeep = false;
            _dispatcher.popListeners();
        }
        showMainPrompt();
    }

    public boolean inDrawMode() {
        return _drawMode;
    }

    // Draw mode listener
    private final MapEventDispatchListener _drawListener = new MapEventDispatchListener() {
        @Override
        public void onMapEvent(MapEvent event) {
            String e = event.getType();
            PointF p = event.getPointF();
            if (p == null)
                return;
            GeoPointMetaData gpEvent = _mapView.inverse(p.x, p.y,
                    MapView.InverseMode.RayCast);
            if (!gpEvent.get().isValid())
                return;

            // Draw route on map
            if (e.equals(MapEvent.MAP_DRAW)
                    || e.equals(MapEvent.ITEM_DRAG_CONTINUED)) {

                int numPoints = _route.getNumPoints();
                if (e.equals(MapEvent.ITEM_DRAG_CONTINUED)) {
                    MapItem mi = event.getItem();
                    if (mi instanceof PointMapItem) {
                        int index = _route.getIndexOfMarker((PointMapItem) mi);
                        if (index >= 0 && index < numPoints - 1)
                            _alignIndex = index;
                    }
                }

                if (_drawShape == null) {
                    // Start new segment
                    int color = _route.getColor() & 0xFFFFFF;
                    _drawShape = new DrawingShape(_mapView, _route.getGroup(),
                            UUID.randomUUID().toString());
                    _drawShape.setMetaBoolean("addToObjList", false);
                    _drawShape.setColor(0xFF000000 + color);
                    _drawShape.setStrokeWeight(_route.getStrokeWeight());
                    _drawShape.setClickable(false);
                    if (_alignIndex == -1) {
                        if (numPoints > 0)
                            _drawShape.addPoint(_route.getPoint(numPoints - 1));
                        else
                            addWayPoint(gpEvent, 0);
                    } else {
                        _drawShape.addPoint(_route.getPoint(_alignIndex));
                        _route.setColor(0x99000000 + color);
                    }
                    _route.getGroup().addItem(_drawShape);
                }
                _drawShape.addPoint(gpEvent);
            }

            // Finish drawing route segment
            else if (e.equals(MapEvent.MAP_RELEASE)
                    || e.equals(MapEvent.ITEM_DRAG_DROPPED)) {
                if (_drawShape == null)
                    return;

                // Simplify geometry
                List<GeoPoint> simplified = ATAKUtilities.simplifyPoints(
                        _spatialCalculator,
                        Arrays.asList(_drawShape.getPoints()));
                List<GeoPoint> newPoints = new ArrayList<>();
                for (GeoPoint gp : simplified) {
                    double hae = ElevationManager.getElevation(
                            gp.getLatitude(), gp.getLongitude(), null);
                    if (!Double.isNaN(hae))
                        gp = new GeoPoint(gp.getLatitude(),
                                gp.getLongitude(),
                                hae, GeoPoint.AltitudeReference.HAE);
                    newPoints.add(gp);
                }
                _route.setColor(_drawShape.getColor());

                // Remove temp shape
                if (_drawShape != null)
                    _drawShape.removeFromGroup();
                _drawShape = null;
                int startIndex = _route.getNumPoints();
                if (_alignIndex > -1)
                    startIndex = _alignIndex + 1;
                run(_route.new RouteAddSegment(newPoints, startIndex, true));
                _alignIndex = -1;
            }
        }
    };
}
