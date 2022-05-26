
package com.atakmap.android.routes;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.attachment.layer.AttachmentBillboardLayer;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.util.DisplayManager;
import com.atakmap.comms.ReportingRate;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RouteNavigator {

    public static final String TAG = "RouteNavigator";

    //-------------------- Keys ---------------------------
    public static final String NAV_TO_NEXT_INTENT = "com.atakmap.android.routes.NEXT_NAV";
    public static final String NAV_TO_PREV_INTENT = "com.atakmap.android.routes.PREV_NAV";
    public static final String QUIT_NAV_INTENT = "com.atakmap.android.routes.QUIT_NAV";

    /*******************************************************************************
     * Fields and Properties
     *******************************************************************************/

    //-------------------- Fields ---------------------------
    private PointMapItem self;
    private final Collection<RouteNavigatorListener> routeNavigatorListeners = new ConcurrentLinkedQueue<>();
    private final BroadcastReceiver navReceiver;
    private final BroadcastReceiver gpsReceiver;
    private final DocumentedIntentFilter navFilter;
    private final DocumentedIntentFilter gpsFilter;
    private final SharedPreferences navPrefs;
    private final AttachmentBillboardLayer billboardLayer;

    //-------------------- Properties ---------------------------
    private volatile boolean navigating = false;

    public synchronized boolean isNavigating() {
        return navigating;
    }

    private volatile Route route = null;

    public Route getRoute() {
        return route;
    }

    private final MapGroup navGroup;

    public MapGroup getNavGroup() {
        return navGroup;
    }

    private final MapView mapView;

    public MapView getMapView() {
        return mapView;
    }

    private RouteNavigationManager navManager = null;

    public RouteNavigationManager getNavManager() {
        return navManager;
    }

    private static RouteNavigator _instance;

    /**
     * Obtain the current instantiation of the route navigator that can be used to register a
     * callback listener.
     */
    public static RouteNavigator getInstance() {
        return _instance;
    }

    /**
     * Run the navigate loop. Not a Limiting Thread because it has to be
     * responsive to interruption
     */
    private Thread updateThread = null;

    RouteNavigator(final MapView mapView, final MapGroup navGroup,
            final AttachmentBillboardLayer billboardLayer) {
        this.mapView = mapView;
        this.navGroup = navGroup;
        this.billboardLayer = billboardLayer;

        navPrefs = PreferenceManager.getDefaultSharedPreferences(mapView
                .getContext());

        navFilter = new DocumentedIntentFilter();
        navFilter.addAction(NAV_TO_NEXT_INTENT,
                "Advances navigation to target the next way point.");
        navFilter.addAction(NAV_TO_PREV_INTENT,
                "Rewinds navigation to target the previous way point.");
        navFilter.addAction(QUIT_NAV_INTENT, "Quits route navigation.");

        gpsFilter = new DocumentedIntentFilter();
        gpsFilter.addAction(ReportingRate.REPORT_LOCATION,
                "Listens if GPS is lost or gained during navigation for further action.");
        gpsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String reason = intent.getStringExtra("reason");
                if (reason == null)
                    return;

                if (reason.startsWith("No GPS available")) {
                    Log.d(TAG, "gps lost during navigation");
                    if (navManager != null)
                        navManager.setGpsStatus(false);
                } else if (reason.startsWith("GPS now available")) {
                    Log.d(TAG, "gps regained during navigation");
                    if (navManager != null)
                        navManager.setGpsStatus(true);
                }

            }
        };

        navReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                switch (intent.getAction()) {
                    case NAV_TO_NEXT_INTENT:

                        if (navManager != null) {
                            navManager.moveCurrentObjectiveForward();
                        }
                        break;
                    case NAV_TO_PREV_INTENT:
                        if (navManager != null) {
                            navManager.moveCurrentObjectiveBackward();
                        }
                        break;
                    case QUIT_NAV_INTENT:
                        requestStopNavigating();
                        break;
                }
            }
        };

        _instance = this;
    }

    /**
     * Updates the navigator to begin navigating a different route in place without tearing down all
     * of the UI navigation components.
     *
     * @param updatedRoute New route to navigate
     * @return true if able to update navigation successfully
     */
    public synchronized boolean updateNavigation(final Route updatedRoute) {

        if (!isNavigating()) {
            throw new IllegalStateException(
                    "Navigation can only be updated when it is already running");
        }

        Log.d(TAG, "Updating navigation -- switching from route: "
                + route.getUID() + " to new route: " + updatedRoute.getUID());

        // Pause getting updates
        RouteNavigationManager oldManager = navManager;
        navManager = null;

        // Get a list of the listeners so we can be sure to preserve them
        List<RouteNavigationManager.RouteNavigationManagerEventListener> listeners = oldManager
                .getRegisteredListeners();

        // Shutdown current nav manager
        oldManager.dispose();

        // Replace route
        Route oldRoute = route;
        route = updatedRoute;
        cleanupRoute(oldRoute);

        // Begin building up our new Nav Manager
        RouteNavigationManager newManager = new RouteNavigationManager(
                updatedRoute);

        // Register listeners
        for (RouteNavigationManager.RouteNavigationManagerEventListener listener : listeners) {
            newManager.registerListener(listener);
        }

        // Set initial state
        PointMapItem startingItem = route.getPointMapItem(0);
        newManager.setCurrentObjective(0, startingItem, false);
        newManager.setLocation(self.getPoint());

        // Resume getting updates
        navManager = newManager;

        return true;
    }

    public synchronized boolean startNavigating(final Route route,
            final int startingIndex) {

        if (route == null) {
            Log.d(TAG, "call to startNavigating with a null route");
            return false;
        }

        stopNavigating();

        if (route.getNumPoints() < 2) {
            Log.d(TAG, "Cannot navigate to a route with less than 2 points");
            PointMapItem m;
            if (route.getNumPoints() == 1 && (m = route.getMarker(0)) != null) {
                // Bring up the bloodhound tool instead
                Intent i = new Intent(
                        "com.atakmap.android.toolbars.BLOOD_HOUND");
                i.putExtra("uid", m.getUID());
                AtakBroadcast.getInstance().sendBroadcast(i);
            }
            return false;
        }

        if (self == null)
            self = ATAKUtilities.findSelf(mapView);

        if (self == null)
            return false;

        //Setup our nav manager
        this.route = route;
        navManager = new RouteNavigationManager(route);
        PointMapItem startingItem = route.getPointMapItem(startingIndex);
        navManager.setCurrentObjective(startingIndex, startingItem, false);
        navManager.setLocation(self.getPoint());

        //Hook our pertinent callbacks
        AtakBroadcast.getInstance().registerReceiver(navReceiver, navFilter);
        AtakBroadcast.getInstance().registerReceiver(gpsReceiver, gpsFilter);

        DisplayManager.acquireTemporaryScreenLock(mapView, "RouteNavigator");

        //Set state
        fireOnNavigationStarting();
        navigating = true;
        navigate();
        billboardLayer.setVisible(navPrefs.getBoolean(
                "route_billboard_enabled", true));
        Log.d(TAG, "navigating route: " + route);
        fireOnNavigationStarted();
        return true;
    }

    /**
     * Sends intents to clean up all aspects of navigation, including those handled by other
     * components.
     */
    void requestStopNavigating() {
        stopNavigating();

        Intent myLocationIntent = new Intent();
        myLocationIntent.setAction("com.atakmap.android.maps.END_NAV");
        AtakBroadcast.getInstance().sendBroadcast(
                myLocationIntent);
    }

    /**
     * Cleans up just the internal aspects of navigating
     */
    synchronized public void stopNavigating() {
        Log.d(TAG, "stopping navigation");

        billboardLayer.setVisible(false);

        if (navigating && route != null) {

            DisplayManager.releaseTemporaryScreenLock(mapView,
                    "RouteNavigator");

            AtakBroadcast.getInstance().unregisterReceiver(gpsReceiver);
            AtakBroadcast.getInstance().unregisterReceiver(navReceiver);

            Log.d(TAG, "Stopping while a route was being navigated");
            fireOnNavigationStopping();

            if (navManager != null) {
                navManager.dispose();
                navManager = null;
            }

            if (updateThread != null) {
                updateThread.interrupt();
                try {
                    updateThread.join();
                } catch (Exception e) {
                    Log.e(TAG, "Exception on joining the update thread", e);
                }
            }

            updateThread = null;
            navigating = false;

            cleanupRoute(route);
            route = null;

            fireOnNavigationStopped();
        }
    }

    /**
     * Begins navigation to the new waypoint if present in the existing route.
     *
     * @param waypoint the waypoint to navigate to
     */
    synchronized void navigateToNewWayPoint(final PointMapItem waypoint) {

        if (route != null) {
            // We only need to do something if we are already navigating
            if (navigating) {

                // Look for the new waypoint in our existing route
                int index = route.getIndexOfMarker(waypoint);

                Log.d(TAG, "Found new way point in route as pos: " + index);

                // Update the route index if we found the marker in our existing route
                if (index > -1 && navManager != null) {
                    navManager.setCurrentObjective(index, waypoint, false);
                }
            }
        }

    }

    /**
     * Removes a route from the map if it is marked as a re-route. Marking as a re-route is denoted
     * by having "isReroute" as true in the metadata.
     *
     * @param r Route to remove
     */
    private static void cleanupRoute(Route r) {
        if (r.getMetaBoolean("isReroute", false)) {

            MapGroup mg = r.getGroup();
            if (mg != null) {
                mg.removeItem(r);
            }
        }
    }

    /**
     * Processes a navigation change request.
     */
    private void navigate() {

        if (updateThread == null) {
            updateThread = new Thread(
                    new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG,
                                    "navigation update requested (limiting at 1Hz)");

                            while (!Thread.currentThread().isInterrupted()) {

                                try {
                                    if (navManager != null) {

                                        GeoPoint pt = self.getPoint();
                                        double bubble = getBubbleRadius(
                                                route.getRouteMethod(),
                                                navPrefs,
                                                false);
                                        double offRouteBubble = getBubbleRadius(
                                                route.getRouteMethod(),
                                                navPrefs,
                                                true);

                                        if (!Double.isNaN(pt.getCE())) {
                                            bubble += pt.getCE();
                                        }

                                        navManager.setBubbleRadius(bubble);
                                        navManager.setOffRouteBubbleRadius(
                                                offRouteBubble);
                                        navManager.setLocation(self.getPoint());
                                    }
                                } catch (NullPointerException ignore) {
                                    // RouteNavigator: NullPointer Exception (ATAK-14231)
                                    // navManager can be set to null after the null check on this thread
                                    // minimize the patch for backporting
                                }

                                try {
                                    Thread.sleep(1500);
                                } catch (InterruptedException ignored) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }
                    });
            updateThread.setName("NavThread");
            updateThread.start();
        }

    }

    public static int getBubbleRadius(final Route.RouteMethod routeMethod,
            SharedPreferences prefs, boolean forOffRoute) {

        // Determine if a raw bubble preference has been stored value has been stored
        String radiusPrefKey;
        if (forOffRoute) {
            radiusPrefKey = "waypointOffRouteBubble." + routeMethod;
        } else {
            radiusPrefKey = "waypointBubble." + routeMethod;
        }
        String rawBubbleRadius = prefs.getString(radiusPrefKey, null);

        try {
            // Attempt to update our current bubble radius to that which was been stored as a
            // preference
            if (rawBubbleRadius != null) {
                return Integer.parseInt(rawBubbleRadius);
            }
        } catch (NumberFormatException nfe) {
            Log.e(TAG, "Found a " + radiusPrefKey + " preference of '"
                    + rawBubbleRadius +
                    "' but can't convert to a valid int.", nfe);
        }

        if (routeMethod == Route.RouteMethod.Walking)
            return 10;
        else if (routeMethod == Route.RouteMethod.Swimming)
            return 10;
        else if (routeMethod == Route.RouteMethod.Driving)
            return 20;
        else if (routeMethod == Route.RouteMethod.Watercraft)
            return 100;
        else if (routeMethod == Route.RouteMethod.Flying)
            return 1000;
        return 21;
    }

    /**
     * Get the next waypoint marker in the route, if applicable
     * @param pmi Waypoint item
     * @param range Range from src marker to target waypoint
     * @param mapView Map view instance
     * @param prefs Shared preferences
     * @return Next waypoint marker or null if N/A
     */
    public static PointMapItem getNextWaypoint(PointMapItem pmi, double range,
            MapView mapView, SharedPreferences prefs) {
        // Not a valid waypoint
        if (!pmi.getType().equals(Route.WAYPOINT_TYPE)
                || !pmi.hasMetaValue("parent_route_uid"))
            return null;

        String routeUID = pmi.getMetaString("parent_route_uid", null);
        if (FileSystemUtils.isEmpty(routeUID))
            return null;

        MapItem mi = mapView.getRootGroup().deepFindUID(routeUID);
        if (!(mi instanceof Route))
            return null;

        Route r = (Route) mi;

        int radius = getBubbleRadius(r.getRouteMethod(), prefs, false);
        if (range > radius)
            return null;

        int index = r.getIndexOfMarker(pmi);
        return index != -1 ? r.getNextWaypoint(index) : null;
    }

    /*******************************************************************************
     * Event Handling
     *******************************************************************************/

    /**
     * Interface allowing key navigation events to be listened for.
     */
    public interface RouteNavigatorListener {

        /**
         * Fires when navigation is starting.
         */
        void onNavigationStarting(RouteNavigator navigator);

        /**
         * Fires when navigation has started.
         */
        void onNavigationStarted(RouteNavigator navigator, Route route);

        /**
         * Fires when navigation is stopping.
         */
        void onNavigationStopping(RouteNavigator navigator, Route route);

        /**
         * Fires after navigation has been stopped.
         */
        void onNavigationStopped(RouteNavigator navigator);
    }

    /**
     * Registers a route navigation listener
     * @param listener the listener fired during route navigation
     */
    public void registerRouteNavigatorListener(
            RouteNavigatorListener listener) {
        routeNavigatorListeners.add(listener);
    }

    /**
     * Unregisters a route navigation listener
     * @param listener the listener fired during route navigation
     */
    public void unregisterRouteNavigatorListener(
            RouteNavigatorListener listener) {
        routeNavigatorListeners.remove(listener);
    }

    private void fireOnNavigationStarting() {
        for (RouteNavigatorListener listener : routeNavigatorListeners) {
            listener.onNavigationStarting(this);
        }
    }

    private void fireOnNavigationStarted() {
        for (RouteNavigatorListener listener : routeNavigatorListeners) {
            listener.onNavigationStarted(this, route);
        }
    }

    private void fireOnNavigationStopping() {
        for (RouteNavigatorListener listener : routeNavigatorListeners) {
            listener.onNavigationStopping(this, route);
        }
    }

    private void fireOnNavigationStopped() {
        for (RouteNavigatorListener listener : routeNavigatorListeners) {
            listener.onNavigationStopped(this);
        }
    }
}
