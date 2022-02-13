
package com.atakmap.android.routes.nav;

import android.content.Intent;
import android.util.Pair;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.MapData;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.NavigationInstrumentPanel;
import com.atakmap.android.routes.Route;
import com.atakmap.android.routes.RouteNavigationManager;
import com.atakmap.android.routes.RouteNavigator;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import java.util.HashMap;
import java.util.Map;

/**
 * Responsible for providing feedback (speed, distance, eta, voice cues, vibration, etc.) throughout
 * navigation.
 */

public class NavigationFeedbackService
        implements RouteNavigator.RouteNavigatorListener,
        RouteNavigationManager.RouteNavigationManagerEventListener {

    /*******************************************************************************
     * Fields and Properties
     *******************************************************************************/

    public static final String TAG = "NavigationFeedbackService";

    // specifically cached to reduce updateNavInfo calls
    private GeoPoint last;
    private PointMapItem lastWayPoint;
    private double lastDistance;
    private RouteNavigator navigator;
    private RoutePanelViewModel routePanelViewModel;
    private NavigationInstrumentPanel instrumentPanel;
    private RouteNavigationViewModel routeNavigationViewModel;

    // uid for a distance from a specific waypoint to the end of the route.
    private final Map<String, Double> distanceToEnd = new HashMap<>();

    // last known state of the engine
    private boolean lastKnownState = false;

    /*******************************************************************************
     * Methods
     *******************************************************************************/

    private void updateNavInfo(RouteNavigator navigator,
            RoutePanelViewModel routePanelViewModel) {

        RouteNavigationManager navManager = navigator.getNavManager();

        Route route = navManager.getRoute();

        Pair<Integer, PointMapItem> currentObjective = navManager
                .getCurrentObjective();

        PointMapItem wayPoint = currentObjective.second;

        if (wayPoint == null) {
            return;
        }

        GeoPoint pt = wayPoint.getPoint();
        GeoPoint selfPt = navManager.getLocation();

        // short circuit calculations when the move has not been so big (2m)
        if (lastWayPoint != null
                && !lastWayPoint.getUID().equals(wayPoint.getUID()))
            last = null;

        // the state of the engine has changed, clear the cached last so that the
        // ui can be updated
        if (lastKnownState != navManager.isEngineInitialized()) {
            Log.d(TAG, "the nav engine has been initialized");
            lastKnownState = !lastKnownState;
            last = null;
        }

        double distanceToNextWP;

        // determine if we have moved enough to consider this a new distance;

        if (last != null && last.distanceTo(selfPt) < 2) {
            distanceToNextWP = lastDistance;
        } else {
            if (navManager.isEngineInitialized()) {
                //Note if we're off route, then we're concerned with distance as the crow flies.
                //If we're on route, then we're concerned with distance as the route goes.
                distanceToNextWP = navManager.getIsOffRoute()
                        ? pt.distanceTo(selfPt)
                        : navManager.getDistanceBetweenTwoPoints(pt,
                                selfPt);
                lastDistance = distanceToNextWP;
            } else {
                distanceToNextWP = pt.distanceTo(selfPt);
            }
        }

        try {
            Double dist = distanceToEnd.get(wayPoint.getUID());
            if (dist == null) {
                GeoPointMetaData endPt = route
                        .getPoint(route.getNumPoints() - 1);
                // only cache the value as soon as the engine is initialized
                // otherwise it is too short.
                if (navManager.isEngineInitialized()) {
                    dist = navManager
                            .getDistanceBetweenTwoPoints(pt, endPt.get());
                    distanceToEnd.put(wayPoint.getUID(), dist);
                } else {
                    dist = pt.distanceTo(endPt.get());
                }
            }

            updateNavInfo(navigator, routePanelViewModel,
                    distanceToNextWP + dist, distanceToNextWP, pt);

            last = selfPt;
            lastWayPoint = wayPoint;
        } catch (Exception e) {
            Log.d(TAG, "exception occurred computing distance", e);
        }

    }

    private void updateNavInfo(RouteNavigator navigator,
            RoutePanelViewModel navStateManager,
            final double remainingDistance,
            final double distanceToNextWP,
            final GeoPoint nextPoint) {

        if (!Double.isNaN(distanceToNextWP) && navStateManager != null) {
            navStateManager.setDistanceToNextWaypoint(distanceToNextWP);
        }

        if (!Double.isNaN(remainingDistance) && navStateManager != null) {
            navStateManager.setDistanceToVDO(remainingDistance);
        }

        Marker selfMarker = navigator.getMapView().getSelfMarker();

        try {

            if (navStateManager != null) {
                final double speed = selfMarker.getMetaDouble(
                        "Speed", Double.NaN);
                navStateManager.setSpeedInMetersPerSecond(speed);
            }

            double avgSpeed = selfMarker.getMetaDouble("avgSpeed30",
                    Double.NaN);
            //Log.d(TAG, "average speed: " + avgSpeed);
            if (Double.isNaN(avgSpeed)) {
                avgSpeed = selfMarker.getTrackSpeed();
            }
            if (!Double.isNaN(avgSpeed)
                    && Double.compare(avgSpeed, 0.0) != 0) {
                if (navStateManager != null) {
                    navStateManager.setAverageSpeedInMetersPerSecond(avgSpeed);
                }
            } else {
                // legacy
                MapData mapData = navigator.getMapView().getMapData();
                if (mapData.containsKey("fineLocationSpeed")) {
                    double speed = mapData.getDouble("fineLocationSpeed"); // m/s
                    if (speed >= .5) {
                        if (navStateManager != null) {
                            navStateManager
                                    .setAverageSpeedInMetersPerSecond(speed);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "caught an error obtaining the user speed", e);
        }

    }

    /*******************************************************************************
     * RouteNavigatorListener Interface Impl
     *******************************************************************************/

    @Override
    public void onNavigationStarting(RouteNavigator navigator) {

    }

    @Override
    public void onNavigationStarted(RouteNavigator navigator, Route route) {
        this.navigator = navigator;
        routePanelViewModel = new RoutePanelViewModel();
        instrumentPanel = new NavigationInstrumentPanel(navigator.getMapView(),
                routePanelViewModel);
        routeNavigationViewModel = new RouteNavigationViewModel(
                navigator.getMapView(), routePanelViewModel);

        navigator.getNavManager().registerListener(this);
        navigator.getNavManager().registerListener(routeNavigationViewModel);

        updateNavInfo(navigator, routePanelViewModel);
        Pair<Integer, PointMapItem> currentObjective = navigator
                .getNavManager().getCurrentObjective();

        if (currentObjective.second != null) {
            routeNavigationViewModel.onNavigationObjectiveChanged(
                    navigator.getNavManager(),
                    currentObjective.second, true);
        }

    }

    @Override
    public void onNavigationStopping(RouteNavigator navigator, Route route) {
        instrumentPanel.removeWidgets();

        navigator.getNavManager().unregisterListener(this);
        navigator.getNavManager().unregisterListener(routeNavigationViewModel);

        routePanelViewModel = null;
        routeNavigationViewModel.dispose();
        routeNavigationViewModel = null;
        instrumentPanel = null;

        last = null;
        lastWayPoint = null;
        distanceToEnd.clear();
        lastKnownState = false;
        this.navigator = null;
    }

    @Override
    public void onNavigationStopped(RouteNavigator navigator) {
    }

    /*******************************************************************************
     * RouteNavigationManagerEventListener Interface Impl
     *******************************************************************************/

    @Override
    public void onGpsStatusChanged(
            RouteNavigationManager routeNavigationManager, boolean found) {
    }

    @Override
    public void onLocationChanged(
            RouteNavigationManager routeNavigationManager,
            GeoPoint oldLocation, GeoPoint newLocation) {
        updateNavInfo(navigator, routePanelViewModel);
    }

    @Override
    public void onNavigationObjectiveChanged(
            RouteNavigationManager routeNavigationManager,
            PointMapItem newObjective, boolean isFromRouteProgression) {
        if (!isFromRouteProgression)
            updateNavInfo(navigator, routePanelViewModel);
    }

    @Override
    public void onOffRoute(RouteNavigationManager routeNavigationManager) {

    }

    @Override
    public void onReturnedToRoute(
            RouteNavigationManager routeNavigationManager) {

    }

    @Override
    public void onTriggerEntered(RouteNavigationManager routeNavigationManager,
            PointMapItem item, int triggerIndex) {

    }

    @Override
    public void onArrivedAtPoint(RouteNavigationManager routeNavigationManager,
            PointMapItem item) {

        final Route route = routeNavigationManager.getRoute();
        int hitIndex = route.getIndexOfPoint(item);
        //If it's the last point and it's our objective, end navigation
        if (hitIndex == route.getNumPoints() - 1) {

            Log.d(TAG, "We've reached the last point, fire the end nav intent");
            Intent endNavIntent = new Intent();
            endNavIntent.setAction("com.atakmap.android.maps.END_NAV");
            AtakBroadcast.getInstance().sendBroadcast(endNavIntent);
        }

    }

    @Override
    public void onDepartedPoint(RouteNavigationManager routeNavigationManager,
            PointMapItem item) {

    }
}
