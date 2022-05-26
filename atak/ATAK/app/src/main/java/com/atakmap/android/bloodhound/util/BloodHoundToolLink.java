
package com.atakmap.android.bloodhound.util;

import android.os.Handler;
import android.os.Looper;

import com.atakmap.android.bloodhound.BloodHoundPreferences;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteGenerationHandler;
import com.atakmap.android.routes.RouteGenerationPackage;
import com.atakmap.android.routes.RouteGenerationTask;
import com.atakmap.android.routes.RouteMapReceiver;
import com.atakmap.android.routes.RoutePlannerInterface;
import com.atakmap.android.routes.RoutePointPackage;
import com.atakmap.android.toolbars.RangeAndBearingMapItem;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.NorthReference;

import java.util.ArrayList;
import java.util.UUID;

import androidx.core.util.Consumer;

/**
 * This class handles the behavior of the bloodhound link
 * managed by the bloodhound tool. This is in contrast to {@see BloodHoundLink},
 * which handles bloodhound links which were created by selecting the bloodhound
 * button in the radial menu for range and bearing lines.
 *
 * Currently, this class has the capability to be in "R&B line mode", or "route mode",
 * whereas a {@see BloodHoundLink} is always an R&B line.
 */
public class BloodHoundToolLink implements MapItem.OnGroupChangedListener,
        MapItem.OnVisibleChangedListener {

    private final static String TAG = "BloodHoundToolLink";
    private final BloodHoundPreferences _prefs;
    private final int outerColor;
    private final Angle _bearingUnits;
    private final NorthReference _northReference;
    private final OnDeleteListener _onDelete;
    private final PointMapItem _startItem;
    private final PointMapItem _endItem;

    private RerouteThread rerouteTask;

    public RangeAndBearingMapItem line;
    public final Route route;

    // Cached values of the start/end item points
    // So we know whether or not we need to calculate a reroute.
    private GeoPoint _startItemPoint;
    private GeoPoint _endItemPoint;

    private boolean _calculatingRoute = false;

    // Cached route planner, for planning reroutes.
    private RoutePlannerInterface _routePlanner = null;

    // Runs in the background to update
    // the bloodhound route when appropriate.

    class RerouteThread extends Thread {
        private boolean cancelled;

        /**
         * Signals the RerouteThread that it has been cancelled.
         */
        public void cancel() {
            cancelled = true;
            interrupt();
        }

        @Override
        public void run() {
            while (!cancelled) {
                try {
                    long waitTime = Math.round(Double
                            .parseDouble(_prefs.get(
                                    "bloodhound_reroute_timer_pref", "1.0"))
                            * 1000.0);
                    Thread.sleep(waitTime);
                } catch (InterruptedException ignored) {
                }

                synchronized (route) {
                    if (cancelled)
                        return;

                    Log.d(TAG, "start rerouting loop...");

                    PointMapItem endOfRoute = route
                            .getPointMapItem(route.getNumPoints() - 2);
                    PointMapItem beginningOfRoute = route
                            .getPointMapItem(1);

                    if (_endItem != null && _endItem.getPoint() != null
                            && endOfRoute != null) {

                        final double distanceToEnd = GeoCalculations.distanceTo(
                                _endItem.getPoint(), endOfRoute.getPoint());
                        final double distanceToBeginning = GeoCalculations
                                .distanceTo(_startItem.getPoint(),
                                        beginningOfRoute.getPoint());

                        // Threshold for a point to deviate from the route before recalculating
                        double rerouteDistance = Double.parseDouble(_prefs.get(
                                "bloodhound_reroute_distance_pref", "20.0"));

                        if ((distanceToEnd >= rerouteDistance
                                || distanceToBeginning >= rerouteDistance)
                                && pointsChanged()) {
                            Log.d(TAG, "rerouting bloodhound link");
                            calculateRoute();
                        }
                    }
                }
            }
        }
    }

    /** Sets the color of the bloodhound link. */
    public void setColor(int color) {
        line.setStrokeColor(color);
        route.setStrokeColor(color);
        route.setColor(color);
        for (PointMapItem point : route.getPointMapItems()) {
            if (point instanceof Marker) {
                ((Marker) point).setColor(color);
            }
        }
    }

    private final PointMapItem.OnPointChangedListener _endItemListener = new PointMapItem.OnPointChangedListener() {
        private GeoPoint lastPoint;

        @Override
        public void onPointChanged(PointMapItem item) {
            synchronized (route) {
                if (item != null) {
                    // broken
                    //if (lastPoint != null && lastPoint.distanceTo(item.getPoint()) < 2)
                    //    return;
                    lastPoint = item.getPoint();

                    PointMapItem endMarker = route
                            .getPointMapItem(route.getNumPoints() - 1);
                    if (endMarker != null) {
                        endMarker.setPoint(item.getPoint());
                        RouteUpdating.truncateRouteEnding(item.getPoint(),
                                route);
                    }
                }
            }
        }
    };

    private final PointMapItem.OnPointChangedListener _beginItemListener = new PointMapItem.OnPointChangedListener() {
        private GeoPoint lastPoint;

        @Override
        public void onPointChanged(PointMapItem item) {
            if (route != null && item != null) {
                //broken
                //if (lastPoint != null && lastPoint.distanceTo(item.getPoint()) < 2)
                //    return;
                lastPoint = item.getPoint();

                Marker startMarker = (Marker) route.getPointMapItem(0);

                if (startMarker != null) {
                    Log.d(TAG, "Item UID: " + item.getUID());
                    Log.d(TAG, "Start marker UID: " + startMarker.getUID());

                    // TODO: startMarker.setPoint(item.getPoint()) warrants further attention here
                    //                    startMarker.setPoint(item.getPoint());
                    RouteUpdating.truncateRouteBeginning(_startItem.getPoint(),
                            route);
                }
            }
        }
    };

    /** Returns whether or not this link is currently in line mode. */
    public boolean isLine() {
        return line.getVisible();
    }

    /** Returns whether or not this link is currently in route moe. */
    public boolean isRoute() {
        return route.getVisible();
    }

    /** Sets the route planner to use for route mode for this link. */
    public void setPlanner(RoutePlannerInterface planner) {
        this._routePlanner = planner;
    }

    /**
     * Toggles the state of the bloodhound link. If it is a range and bearing line,
     * after executing this function it will be a route. If it is a route, after executing
     * this function it will be a range and bearing line.
     */
    public synchronized void toggleRoute() {
        toggleRoute(new Runnable() {
            @Override
            public void run() {
            }
        }, new Runnable() {
            @Override
            public void run() {
            }
        }, new Consumer<Exception>() {
            @Override
            public void accept(Exception e) {

            }
        });
    }

    /**
     * Toggles the state of the bloodhound link. If it is a range and bearing line,
     * after executing this function it will be a route. If it is a route, after executing
     * this function it will be a range and bearing line.
     *
     * @param onSuccessListener Callback to preform some action on successful route generation.
     */
    public synchronized void toggleRoute(final Runnable onSuccessListener,
            final Runnable onRouteCancelled,
            final Consumer<Exception> onException) {
        if (isLine()) {
            if (!_calculatingRoute) {
                // Plan the route
                synchronized (route) {

                    RouteMapReceiver.getInstance().getRouteGroup()
                            .addItem(route);
                    if (route.getNumPoints() == 0) {
                        // Make the last point on the route a waypoint instead of a control point.
                        route.addMarker(Route.createWayPoint(
                                _endItem.getGeoPointMetaData(),
                                UUID.randomUUID().toString()));
                        // Make the first point on the route a waypoint instead of a control point.
                        route.addMarker(0,
                                Route.createWayPoint(
                                        _startItem.getGeoPointMetaData(),
                                        UUID.randomUUID().toString()));
                    }
                    calculateRoute(new Runnable() {
                        @Override
                        public void run() {
                            // Hide the line
                            line.setVisible(false);
                            // Show the route
                            route.setVisible(true);

                            // Make it so users cannot interact with the route
                            route.setEditable(false);
                            route.setMovable(false);

                            // Modify the route if the start or end points change.
                            _startItem.addOnPointChangedListener(
                                    _beginItemListener);

                            _endItem.addOnPointChangedListener(
                                    _endItemListener);

                            _calculatingRoute = false;

                            // Start a new rerouting thread
                            rerouteTask = new RerouteThread();
                            rerouteTask.start();

                            onSuccessListener.run();
                        }
                    }, onRouteCancelled, onException);
                }
            }
        } else {
            // Hide the route (This should be cached so we don't have to re-calculate
            // the route unnecessarily.
            synchronized (route) {
                route.setVisible(false);

                // Stop the rerouting task.
                if (rerouteTask != null) {
                    rerouteTask.cancel();
                    rerouteTask = null;
                }
            }

            _startItem.removeOnPointChangedListener(_beginItemListener);
            _endItem.removeOnPointChangedListener(_endItemListener);

            // Unhide the line
            line.setVisible(true);
        }
    }

    private synchronized void calculateRoute() {
        calculateRoute(new Runnable() {
            @Override
            public void run() {
            }
        }, new Runnable() {
            @Override
            public void run() {
            }
        }, new Consumer<Exception>() {
            @Override
            public void accept(Exception e) {

            }
        });
    }

    private synchronized void calculateRoute(final Runnable onRouteCalculated,
            final Runnable onRouteCancelled,
            final Consumer<Exception> onException) {
        if (_routePlanner != null && !_calculatingRoute) {
            Log.d(TAG,
                    "Route not currently being calculated, continuing to calculate route");
            synchronized (route) {
                // Update cached points
                _startItemPoint = _startItem.getPoint();
                _endItemPoint = _endItem.getPoint();

                // Plan the route
                if (!RouteMapReceiver.getInstance().getRouteGroup()
                        .containsItem(route)) {
                    RouteMapReceiver.getInstance().getRouteGroup()
                            .addItem(route);
                    if (route.getNumPoints() == 0) {
                        // Make the last point on the route a waypoint instead of a control point.
                        route.addMarker(
                                Route.createWayPoint(
                                        _endItem.getGeoPointMetaData(),
                                        UUID.randomUUID().toString()));
                        // Make the first point on the route a waypoint instead of a control point.
                        route.addMarker(0,
                                Route.createWayPoint(
                                        _startItem.getGeoPointMetaData(),
                                        UUID.randomUUID().toString()));
                    }
                }

                final RouteGenerationHandler handler = new RouteGenerationHandler(
                        MapView.getMapView(),
                        _startItem, _endItem, route) {
                    @Override
                    public void onBeforeRouteGenerated(RouteGenerationTask task,
                            boolean displayDialog) {
                        super.onBeforeRouteGenerated(task, displayDialog);
                        _calculatingRoute = true;
                        Log.d(TAG, "Calculating route");
                    }

                    @Override
                    public void onRouteGenerated(
                            RoutePointPackage routePointPackage) {
                        super.onRouteGenerated(routePointPackage);
                    }

                    @Override
                    public void onAfterRouteGenerated(
                            RoutePointPackage routePointPackage) {
                        super.onAfterRouteGenerated(routePointPackage);
                        if (routePointPackage != null) {
                            synchronized (route) {
                                RouteUpdating.truncateRouteBeginning(
                                        _startItem.getPoint(), route);
                            }
                            onRouteCalculated.run();
                            _calculatingRoute = false;
                        }
                    }

                    @Override
                    public void onException(Exception e) {
                        onException.accept(e);
                        _calculatingRoute = false;
                    }

                    @Override
                    public void onCancelled() {
                        onRouteCancelled.run();
                        _calculatingRoute = false;
                    }
                };

                ArrayList<GeoPoint> waypoints = new ArrayList<>();

                final RouteGenerationPackage routeGenerationPackage = new RouteGenerationPackage(
                        _prefs.getSharedPrefs(), _startItem.getPoint(),
                        _endItem.getPoint(),
                        waypoints);

                final RouteGenerationTask task = _routePlanner
                        .getRouteGenerationTask(handler);
                task.setAlertOnCreation(false);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        task.execute(routeGenerationPackage);
                    }
                });
            }
        }
    }

    public void delete() {
        _onDelete.onDelete(this);
    }

    private boolean pointsChanged() {
        return _startItem.getPoint() != _startItemPoint
                || _endItem.getPoint() != _endItemPoint;
    }

    // What is this the uid of?
    public String uid;

    // A _Link consists of a uid
    public BloodHoundToolLink(BloodHoundPreferences prefs, String uid,
            PointMapItem startItem, PointMapItem endItem,
            OnDeleteListener onDelete) {
        this.uid = uid;
        this._onDelete = onDelete;
        this._prefs = prefs;
        this.outerColor = _prefs.getOuterColor();
        this._bearingUnits = _prefs.getBearingUnits();
        this._northReference = _prefs.getNorthReference();

        this._startItem = startItem;
        this._endItem = endItem;
        this._startItemPoint = startItem.getPoint();
        this._endItemPoint = endItem.getPoint();

        this.line = this.createLine(startItem, endItem);
        this.route = RouteMapReceiver.getInstance()
                .getNewRoute(UUID.randomUUID().toString());

        // very important to solve ATAK-13102
        this.route.setMetaBoolean("nevercot", true);

        this.route.setEditable(false);
        this.route.setClickable(false);

        this.route.setMetaBoolean("removable", false);

        this.route.setColor(outerColor);
        this.route.setFillColor(outerColor);
        this.route.setStrokeColor(outerColor);
        this.route.setVisible(false);

        this.line.setStrokeWeight(3d);
    }

    private RangeAndBearingMapItem createLine(final PointMapItem start,
            final PointMapItem end) {

        RangeAndBearingMapItem rb = RangeAndBearingMapItem
                .createOrUpdateRABLine(start.getUID() + "" + end.getUID(),
                        start, end, false);
        rb.setType("rb");
        rb.setZOrder(-1000d);
        rb.setStrokeColor(outerColor);
        rb.setMetaBoolean("removable", false);
        rb.setBearingUnits(_bearingUnits);
        rb.setNorthReference(_northReference);
        rb.setMetaBoolean("displayBloodhoundEta", true);
        rb.setMetaBoolean("disable_polar", true);
        return rb;
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup newParent) {
    }

    @Override
    // From group changed listener
    public void onItemRemoved(MapItem item, MapGroup oldParent) {
        _onDelete.onDelete(this);
    }

    @Override
    // What does this do? What type of items does this actually track?
    public void onVisibleChanged(MapItem item) {
        if (item == null || !item.getVisible())
            _onDelete.onDelete(this);
    }

    public abstract static class OnDeleteListener {
        public abstract void onDelete(BloodHoundToolLink linkListener);
    }

}
