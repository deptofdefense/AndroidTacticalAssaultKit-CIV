
package com.atakmap.android.routes;

/**
 * The purpose of this class is to manage the state throughout the course of navigation for a specific route.
 */

import android.os.SystemClock;
import android.util.Pair;

import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.nav.NavigationCue;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/*******************************************************************************
 * ROLE/RESPONSIBILITIES:
 * -> Thread safe
 * -> Owns RouteNavigatorEngine
 * -> Manages Navigation State (Current Nav Point, Next Nav Point, etc.)
 * -> Fires State Related Events
 * -> Tracks
 *
 * ASSUMPTIONS:
 * -> Only Navigating Forward
 * -> Route CANNOT be Edited While Navigating
 *
 *******************************************************************************/

public final class RouteNavigationManager {

    /*******************************************************************************
     * Fields and Properties
     *******************************************************************************/

    private final static String TAG = "RouteNavigationManager";
    private static final String WAYPOINT_TYPE = "b-m-p-w";
    private final Object syncRoot = new Object();
    private final RouteNavigatorEngine engine;

    private double bubbleRadius;
    private double offRouteBubbleRadius;

    private final AtomicReference<PointMapItem> currentObjective = new AtomicReference<>(
            null);
    private final AtomicInteger currentObjectiveIndex = new AtomicInteger(-1);

    private final Set<Integer> arrivedAtPoints;
    private final Set<Integer> departedPoints;
    private final Set<Integer> pointsTargetedButNotDepartedFrom;
    private final Map<Integer, Queue<Integer>> triggerPointsFired; //Key = Index, Value List of Triggers Fired

    private final Route route;

    private boolean isOffRoute = false;

    // when the engine is initialized, it is also marked as dirty - this will allow
    // for a bit of logic to be run that then advances the marker to the correct
    // position in the route.
    private boolean engineDirty = false;

    private volatile boolean engineInitialized = false;

    private GeoPoint location = null;

    private GeoPoint offRouteLocation = null;
    private final long offRouteEventTimeDelay = 3000;

    private final ConcurrentLinkedQueue<RouteNavigationManagerEventListener> registeredListeners = new ConcurrentLinkedQueue<>();

    /**
     * Constructs a new RouteNavigatorManager instance.
     *
     * @param route The route we are navigating
     */
    public RouteNavigationManager(final Route route) {
        this.route = route;

        try {
            engine = new RouteNavigatorEngine();
        } catch (Exception ex) {
            throw new RuntimeException("Engine failed to initialize", ex);
        }

        Thread t = new Thread("route initialization") {
            @Override
            public void run() {
                long start = SystemClock.elapsedRealtime();
                try {
                    // lock the engine during insert
                    //synchronized(engine) {
                    engine.beginMarkerTransaction();
                    engine.addRoutePoints(route);
                    engine.setMarkerTransactionSuccessful();
                    engine.endMarkerTransaction();
                    //}
                    engineInitialized = true;
                    engineDirty = true;
                } catch (Exception e) {
                    Log.d(TAG,
                            "engine disposed prior to the full initialization",
                            e);
                }
                Log.d(TAG,
                        "finished initializing engine for " + route.getTitle()
                                + " in "
                                + (SystemClock.elapsedRealtime() - start)
                                + "ms");
            }
        };
        t.start();

        int wayPointCount = route.getNumWaypoint();

        arrivedAtPoints = Collections.newSetFromMap(
                new ConcurrentHashMap<Integer, Boolean>(wayPointCount));
        departedPoints = Collections
                .newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
        pointsTargetedButNotDepartedFrom = Collections
                .newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
        triggerPointsFired = new ConcurrentHashMap<>(
                wayPointCount);
    }

    public double getBubbleRadius() {
        synchronized (syncRoot) {
            return bubbleRadius;
        }
    }

    public void setBubbleRadius(double bubbleRadius) {
        synchronized (syncRoot) {
            this.bubbleRadius = bubbleRadius;
        }
    }

    /**
     * Gets the bubble radius for being considered off route, in meters.
     * @return
     */
    public double getOffRouteBubbleRadius() {
        synchronized (syncRoot) {
            return offRouteBubbleRadius;
        }
    }

    /**
     * Sets the bubble radius to be considered off the route.
     *
     * @param bubbleRadius bubble radius in meters
     */
    public void setOffRouteBubbleRadius(double bubbleRadius) {
        synchronized (syncRoot) {
            this.offRouteBubbleRadius = bubbleRadius;
        }
    }

    public boolean hasArrivedAtPoint(int index) {
        return arrivedAtPoints.contains(index);
    }

    public boolean hasDepartedPoint(int index) {
        return departedPoints.contains(index);
    }

    public boolean hasEnteredTrigger(int index, int triggerIndex) {
        final Queue collection = triggerPointsFired.get(index);

        if (collection != null) {
            return collection.contains(triggerIndex);
        }

        return false;
    }

    public Pair<Integer, PointMapItem> getCurrentObjective() {

        return new Pair<>(currentObjectiveIndex.get(),
                currentObjective.get());
    }

    public boolean setCurrentObjective(int index, PointMapItem item,
            boolean isFromRouteProgression) {

        final boolean indexNotChanged = currentObjectiveIndex
                .getAndSet(index) == index;
        final boolean pointNotChanged = this.currentObjective
                .getAndSet(item) == item;

        if (indexNotChanged && pointNotChanged)
            return false; //No change

        fireOnNavigationObjectiveChanged(item, isFromRouteProgression);
        return true;
    }

    public PointMapItem getObjectiveBefore(int index) {
        //NOTE: No, synchronization needed, route is immutable

        for (int i = index - 1; i > -1; i--) {
            PointMapItem wp = route.getMarker(i);
            if (wp != null)
                return wp;
        }

        return null;
    }

    public void moveCurrentObjectiveForward() {
        PointMapItem objective = getObjectiveAfter(currentObjectiveIndex.get());
        while (objective != null) {

            int index = route.getIndexOfMarker(objective);

            if (!departedPoints.contains(index)) {
                currentObjective.set(objective);
                currentObjectiveIndex.set(index);
                break;
            } else {
                objective = getObjectiveAfter(index);
            }
        }

        if (objective != null)
            fireOnNavigationObjectiveChanged(objective, false);
    }

    public void moveCurrentObjectiveBackward() {
        PointMapItem objective = getObjectiveBefore(
                currentObjectiveIndex.get());
        while (objective != null) {

            int index = route.getIndexOfMarker(objective);

            if (!departedPoints.contains(index)) {
                currentObjective.set(objective);
                currentObjectiveIndex.set(index);
                break;
            } else {
                objective = getObjectiveBefore(index);
            }
        }

        if (objective != null)
            fireOnNavigationObjectiveChanged(objective, false);
    }

    public PointMapItem getObjectiveAfter(int index) {
        //NOTE: No, synchronization needed, route is immutable

        for (int i = index + 1; i < route.getNumPoints(); i++) {
            PointMapItem wp = route.getMarker(i);
            if (wp != null)
                return wp;
        }

        return null;
    }

    public Route getRoute() {
        return route;
    }

    public boolean getIsOffRoute() {
        synchronized (syncRoot) {
            return isOffRoute;
        }
    }

    private boolean setIsOffRoute(boolean isOffRoute) {
        synchronized (syncRoot) {
            if (isOffRoute == this.isOffRoute)
                return false;

            this.isOffRoute = isOffRoute;
            return true;
        }
    }

    public GeoPoint getLocation() {
        synchronized (syncRoot) {
            return location;
        }
    }

    /**
     * Could be any number of things, but should indicate to the user that their has 
     * been a change in the positional status that would impact navigation
     * GPS lost/GPS found, etc.
     */
    public void setGpsStatus(final boolean found) {
        fireGpsStatusChanged(found);
    }

    public void setLocation(GeoPoint location) {
        boolean initialSet = false;
        GeoPoint prevLocation = this.location;

        synchronized (syncRoot) {
            initialSet = this.location == null;
            this.location = location;

        }

        if (initialSet) {
            //Set our initial objective
            int objectiveIndex = getNextClosestWaypoint(location);
            PointMapItem objectiveItem = route.getPointMapItem(objectiveIndex);
            setCurrentObjective(objectiveIndex, objectiveItem, true);
        }

        onLocationChanged(location);

        fireOnLocationChanged(prevLocation, location);
    }

    /*******************************************************************************
     * Methods
     *******************************************************************************/

    //-------------------- Public Methods ---------------------------
    public boolean isEngineInitialized() {
        return engineInitialized;
    }

    public List<GeoPoint> getSurroundingGeometry(GeoPoint point,
            double beforeDistance, double afterDistance) {
        try {
            return engine.getGeometryOfRouteSection(point, 0, beforeDistance,
                    afterDistance);
        } catch (Exception ex) {
            Log.e(TAG, "Unhandled Engine Exception", ex);
        }

        return null;
    }

    public boolean isPointWithinItemTrigger(GeoPoint point,
            PointMapItem targetPoint) {

        Pair<Integer, Integer> nextClosestPoint = getNextClosestTriggerIndexFromLocation(
                point);

        if (nextClosestPoint == null)
            return false;

        int index = route.getIndexOfMarker(targetPoint);
        return nextClosestPoint.first == index;
    }

    public boolean isPointWithinTrigger(GeoPoint point) {
        return getNextClosestTriggerIndexFromLocation(point) != null;
    }

    public PointMapItem getNextPointWithTrigger(GeoPoint location) {
        int index = getNextClosestWaypointWithTrigger(location);

        return index >= 0 ? route.getPointMapItem(index) : null;
    }

    public NavigationCue getCueFromItem(PointMapItem item) {
        if (item == null) {
            Log.d(TAG, "No PointMapItem.");

            return null;
        }

        NavigationCue cue = route.getCueForPoint(item.getUID());
        return cue;
    }

    public double getDistanceBetweenTwoPoints(GeoPoint pt1, GeoPoint pt2) {
        try {
            return engine.getDistanceBetweenTwoPointsAlongRoute(pt1, pt2);
        } catch (Exception ex) {
            Log.e(TAG, "Unhandled Engine Exception", ex);
        }

        return 0;
    }

    public double getDistanceFromRoute(GeoPoint location) {
        double distanceFromRoute = Double.MAX_VALUE;

        try {
            distanceFromRoute = engine.findDistanceFromRoute(location);
        } catch (Exception ex) {
            Log.e(TAG, "Unhandled Engine Exception", ex);
        }

        return distanceFromRoute;
    }

    public void dispose() {
        //Cleanup our listeners
        registeredListeners.clear();

        //Shutdown our engine.
        engine.shutdown();
    }

    //-------------------- Private Methods ---------------------------

    private void onLocationChanged(GeoPoint newLocation) {

        if (!engineInitialized) {
            Log.d(TAG, "Engine not initialized, skipping");

            return;
        }

        final double bubbleRadius = getBubbleRadius();
        final double offRouteBubbleRadius = getOffRouteBubbleRadius();
        final double distanceFromRoute = getDistanceFromRoute(newLocation);
        final boolean isOffRoute = distanceFromRoute > offRouteBubbleRadius;
        final boolean wasOffRoute = getIsOffRoute();

        Pair<Integer, PointMapItem> currentObjective = getCurrentObjective();

        // If we are going off route for the first time (vs we are still off route), save the location
        // for use in buffering superfluous off route events
        if (isOffRoute && (offRouteLocation == null)) {
            offRouteLocation = newLocation;
        }

        setIsOffRoute(isOffRoute);

        if (engineDirty) {
            engineDirty = false;

            int objectiveIndex = getNextClosestWaypoint(newLocation);
            PointMapItem objectiveItem = route
                    .getPointMapItem(objectiveIndex);
            setCurrentObjective(objectiveIndex, objectiveItem, true);
        }

        if (offRouteLocation != null) {
            double offRouteMovement = newLocation.distanceTo(offRouteLocation);
            double movementBuffer = 1.5 * getBubbleRadius();

            if (isOffRoute && wasOffRoute
                    && offRouteMovement < movementBuffer) {
                return; //No change, still off route.
            } else if (isOffRoute && wasOffRoute
                    && offRouteMovement >= movementBuffer) {
                Log.d(TAG,
                        "We are still off route, have moved " + offRouteMovement
                                + " since first going off route");
            }
        }

        if (isOffRoute) {
            //Remove our current objective from the captured triggers set, so that if we return to the route, we can
            //fire them as appropriate.
            if (triggerPointsFired.containsKey(currentObjective.first)) {
                triggerPointsFired.remove(currentObjective.first);
                Log.d(TAG, "Removing triggers for point index "
                        + currentObjective.first);
            }

            try {
                Thread.sleep(offRouteEventTimeDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            fireOnOffRoute();

            return; //Don't keep going or we'll get undesirable behavior due to route projections.
        } else {
            if (wasOffRoute) {
                offRouteLocation = null;
                fireOnReturnedToRoute();
            }
        }

        //NOTE: Assumed to be on the route from this point forward.

        //Ensure we have a valid objective, which can be impacted by:
        // -> If we haven't computed an objective yet
        // -> If we are returning to the route, we need to make sure we have a good objective
        if (currentObjective.first < 0
                || currentObjective.second == null
                || wasOffRoute) {
            int computedObjective = getNextClosestWaypoint(newLocation);
            PointMapItem objectiveItem = route
                    .getPointMapItem(computedObjective);
            setCurrentObjective(computedObjective, objectiveItem, true);
            currentObjective = getCurrentObjective();
        }

        //Check for arrivals
        List<Pair<Integer, Boolean>> closePoints = getPointsWithinADistance(
                newLocation, bubbleRadius);

        if (closePoints != null) {
            //True means we are about to arrive, false means we're past that point.
            //Note: We don't need to handle departures here, we will handle any points that we may have
            //arrived at and departed from in our check for departures, but we need to make sure we at
            //least update our arrived at list in this pass
            for (Pair<Integer, Boolean> closePoint : closePoints) {

                final boolean newPoint = arrivedAtPoints.add(closePoint.first);
                if (newPoint) {
                    Log.d(TAG, "Newly Arrived At Point: " + closePoint.first);
                    pointsTargetedButNotDepartedFrom.add(closePoint.first);
                    fireOnArrivedAtPoint(route
                            .getPointMapItem(closePoint.first));
                }
            }
        }

        //Check for departures
        Set<Integer> possibleDepartures = new HashSet<>(
                pointsTargetedButNotDepartedFrom);

        for (Integer p : possibleDepartures) {
            PointMapItem pmi = route.getPointMapItem(p);
            if (pmi != null && getHasPointBeenDepartedFrom(pmi.getPoint(),
                    newLocation)) {
                pointsTargetedButNotDepartedFrom.remove(p);
                departedPoints.add(p);
                fireOnDepartedPoint(pmi);
            }
        }

        //Determine if we need to progress our current objective.
        if (departedPoints.contains(currentObjective.first)) {
            PointMapItem newObjective = getObjectiveAfter(
                    currentObjective.first);

            if (newObjective != null) {
                int newObjectiveIndex = route.getIndexOfMarker(newObjective);
                setCurrentObjective(newObjectiveIndex, newObjective, true);
            }
        }

        //Check for triggers
        Pair<Integer, Integer> trigger = getNextClosestTriggerIndexFromLocation(
                newLocation);

        if (trigger != null) {
            boolean newTrigger = !triggerPointsFired.containsKey(trigger.first)
                    ||
                    !triggerPointsFired.get(trigger.first)
                            .contains(trigger.second);

            if (newTrigger) {
                Log.d(TAG, "New Trigger; Index = " + trigger.first
                        + "; Trigger Index = " + trigger.second);
                if (triggerPointsFired.containsKey(trigger.first)) {
                    triggerPointsFired.get(trigger.first).add(trigger.second);
                } else {
                    ConcurrentLinkedQueue<Integer> collection = new ConcurrentLinkedQueue<>();
                    collection.add(trigger.second);

                    triggerPointsFired.put(trigger.first, collection);
                }

                PointMapItem triggerItem = route
                        .getPointMapItem(trigger.first);

                //Ensure we're tracking this
                pointsTargetedButNotDepartedFrom.add(trigger.first);
                fireOnTriggerEntered(triggerItem, trigger.second);
            }
        }
    }

    private Pair<Integer, Integer> getNextClosestTriggerIndexFromLocation(
            GeoPoint location) {

        try {
            return engine.findTriggerHit(location);
        } catch (Exception ex) {
            Log.e(TAG, "Unhandled Engine Exception", ex);
        }

        return null;

    }

    private List<Pair<Integer, Boolean>> getPointsWithinADistance(
            GeoPoint location, double distanceInMeters) {
        try {
            return engine.findIndexOfPointsWithinDistance(location,
                    distanceInMeters, distanceInMeters);
        } catch (Exception ex) {
            Log.e(TAG, "Unhandled Engine Exception", ex);
        }

        return null;
    }

    private boolean getHasPointBeenDepartedFrom(GeoPoint point,
            GeoPoint location) {
        try {
            double locationPosition = engine
                    .getLocationOfPointAlongRoute(location);
            double pointPosition = engine.getLocationOfPointAlongRoute(point);

            return locationPosition >= pointPosition;
        } catch (Exception ex) {
            Log.e(TAG, "Unhandled Engine Exception", ex);
        }

        return false;
    }

    private int getNextClosestWaypointWithTrigger(GeoPoint location) {
        try {
            return engine.findNextClosestIndexWithTrigger(location);
        } catch (Exception ex) {
            Log.e(TAG, "Unhandled Engine Exception", ex);
        }

        return -1;
    }

    private int getNextClosestWaypoint(GeoPoint location) {
        try {
            return engine.findNextClosestWaypoint(location);
        } catch (Exception ex) {
            Log.e(TAG, "Unhandled Engine Exception", ex);
        }

        return -1;
    }

    private double getLocationOfPointAlongRoute(GeoPoint location) {
        try {
            return engine.getLocationOfPointAlongRoute(location);
        } catch (Exception ex) {
            Log.e(TAG, "Unhandled Engine Exception", ex);
        }

        return 0;
    }

    /*******************************************************************************
     * Event Management
     *******************************************************************************/
    /**
     * Registers a new event listener.
     *
     * @param listener
     */
    public void registerListener(RouteNavigationManagerEventListener listener) {
        registeredListeners.add(listener);
    }

    /**
     * Unregisters the given event listener.
     *
     * @param listener
     */
    public void unregisterListener(
            RouteNavigationManagerEventListener listener) {
        registeredListeners.remove(listener);
    }

    /**
     * Returns a new list of all the currently registered event listeners.
     *
     * @return
     */
    List<RouteNavigationManagerEventListener> getRegisteredListeners() {
        return new ArrayList<>(registeredListeners);
    }

    //-------------------- Event Trigger Methods ---------------------------

    private void fireGpsStatusChanged(boolean found) {
        for (RouteNavigationManagerEventListener listener : registeredListeners) {
            try {
                listener.onGpsStatusChanged(this, found);
            } catch (Exception e) {
                Log.d(TAG, "navigation finished prior to this call");
            }
        }
    }

    private void fireOnLocationChanged(GeoPoint oldLocation,
            GeoPoint newLocation) {
        for (RouteNavigationManagerEventListener listener : registeredListeners) {
            try {
                listener.onLocationChanged(this, oldLocation, newLocation);
            } catch (Exception e) {
                Log.d(TAG, "navigation finished prior to this call");
            }
        }
    }

    private void fireOnNavigationObjectiveChanged(
            final PointMapItem newObjective,
            final boolean isFromRouteProgression) {

        for (RouteNavigationManagerEventListener listener : registeredListeners) {
            try {
                listener.onNavigationObjectiveChanged(
                        RouteNavigationManager.this, newObjective,
                        isFromRouteProgression);
            } catch (Exception e) {
                Log.d(TAG, "navigation finished prior to this call");
            }
        }
    }

    private void fireOnOffRoute() {
        for (RouteNavigationManagerEventListener listener : registeredListeners) {
            try {
                listener.onOffRoute(RouteNavigationManager.this);
            } catch (Exception e) {
                Log.d(TAG, "navigation finished prior to this call");
            }
        }
    }

    private void fireOnReturnedToRoute() {

        for (RouteNavigationManagerEventListener listener : registeredListeners) {
            try {
                listener.onReturnedToRoute(RouteNavigationManager.this);
            } catch (Exception e) {
                Log.d(TAG, "navigation finished prior to this call");
            }
        }
    }

    private void fireOnTriggerEntered(final PointMapItem item,
            int triggerIndex) {
        for (RouteNavigationManagerEventListener listener : registeredListeners) {
            try {
                listener.onTriggerEntered(RouteNavigationManager.this,
                        item, triggerIndex);
            } catch (Exception e) {
                Log.d(TAG, "navigation finished prior to this call");
            }
        }
    }

    private void fireOnArrivedAtPoint(final PointMapItem item) {
        for (RouteNavigationManagerEventListener listener : registeredListeners) {
            try {
                listener.onArrivedAtPoint(RouteNavigationManager.this, item);
            } catch (Exception e) {
                Log.d(TAG, "navigation finished prior to this call");
            }
        }

    }

    private void fireOnDepartedPoint(final PointMapItem item) {
        for (RouteNavigationManagerEventListener listener : registeredListeners) {
            try {
                listener.onDepartedPoint(RouteNavigationManager.this, item);
            } catch (Exception e) {
                Log.d(TAG, "navigation finished prior to this call");
            }
        }
    }

    //-------------------- Listener Interface ---------------------------

    public interface RouteNavigationManagerEventListener {

        /**
         * Indicates the location has changed.
         * @param routeNavigationManager The {@link RouteNavigationManager}.
         * @param found if the gps is found or false if it has been lost.
         */
        void onGpsStatusChanged(RouteNavigationManager routeNavigationManager,
                boolean found);

        /**
         * Indicates the location has changed.
         * @param routeNavigationManager The {@link RouteNavigationManager}.
         * @param oldLocation The previous location.
         * @param newLocation The new location.
         */
        void onLocationChanged(RouteNavigationManager routeNavigationManager,
                GeoPoint oldLocation, GeoPoint newLocation);

        /**
         * Indicates that the navigation objective has changed.
         * @param routeNavigationManager The {@link RouteNavigationManager}.
         * @param newObjective The new navigation objective.
         * @param isFromRouteProgression Indicates whether the event was raised due to natural route progression (e.g. the previous checkpoint was hit), or from manual intervention (e.g. the user skipped to this checkpoint).
         */
        void onNavigationObjectiveChanged(
                RouteNavigationManager routeNavigationManager,
                PointMapItem newObjective,
                boolean isFromRouteProgression);

        /**
         * Indicates that the user has gone off route (Note: Only fired the first time the user has gone off route, not continuously).
         * @param routeNavigationManager
         */
        void onOffRoute(RouteNavigationManager routeNavigationManager);

        /**
         * Indicates that the user has returned to the route.
         * @param routeNavigationManager
         */
        void onReturnedToRoute(RouteNavigationManager routeNavigationManager);

        /**
         * Indicates that a navigation trigger has been entered.
         * @param routeNavigationManager The {@link RouteNavigationManager}.
         * @param item The {@link PointMapItem} that's trigger has been entered.
         * @param triggerIndex The index of the {@link PointMapItem}.
         */
        void onTriggerEntered(
                RouteNavigationManager routeNavigationManager,
                PointMapItem item, int triggerIndex);

        /**
         * Indicates that the user has arrived at a point (i.e. they have entered the bubble radius of a particular point).
         * @param routeNavigationManager The {@link RouteNavigationManager}.
         * @param item The item at which the user has arrived at.
         */
        void onArrivedAtPoint(RouteNavigationManager routeNavigationManager,
                PointMapItem item);

        /**
         * Indicates that the user has departed a point (i.e. they have passed the point).  Note: this will usually follow the `onArrivedAtPoint` event.
         * @param routeNavigationManager The {@link RouteNavigationManager}.
         * @param item The item from which the user has just departed.
         */
        void onDepartedPoint(RouteNavigationManager routeNavigationManager,
                PointMapItem item);
    }

}
