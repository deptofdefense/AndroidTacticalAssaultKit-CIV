
package com.atakmap.android.routes.routearound;

import com.atakmap.android.maps.Shape;

import java.util.ArrayList;
import java.util.List;

/** A singleton event relay for the route around region manager. */
public class RouteAroundRegionEventRelay {

    private static RouteAroundRegionEventRelay _instance = null;
    private final List<RouteAroundRegionEventSubscriber> subscribers = new ArrayList<>();

    public static synchronized RouteAroundRegionEventRelay getInstance() {
        if (_instance == null) {
            _instance = new RouteAroundRegionEventRelay();
        }
        return _instance;
    }

    /** Register an event listener with the relay.
     *
     * @param subscriber The event listener to subscribe.
     */
    public void addRouteAroundRegionEventListener(
            RouteAroundRegionEventSubscriber subscriber) {
        synchronized (subscribers) {
            subscribers.add(subscriber);
        }
    }

    /** Unregister an event listener with the relay.
     *
     * @param subscriber The event listener to unsubscribe
     * @return Whether or not the event listener was successfully unsubscribed.
     */
    public synchronized boolean removeRouteAroundRegionEventListener(
            RouteAroundRegionEventSubscriber subscriber) {
        synchronized (subscribers) {
            return subscribers.remove(subscriber);
        }
    }

    /**
     * Broadcast an event that a region was added to the manager.
     * @param region the shape to add.
     */
    public void onRegionAdded(Shape region) {
        synchronized (subscribers) {
            for (RouteAroundRegionEventSubscriber subscriber : subscribers) {
                subscriber.onEvent(new Event.RegionAdded(region));
            }
        }
    }

    /**
     * Broadcast an event that a region was removed from the manager.
     * @param region the shape to remove.
     */
    public void onRegionRemoved(Shape region) {
        synchronized (subscribers) {
            for (RouteAroundRegionEventSubscriber subscriber : subscribers) {
                subscriber.onEvent(new Event.RegionRemoved(region));
            }
        }
    }

    /**
     * Broadcast an event that the "route around geofences" option was set or unset.
     */
    public void onRouteAroundGeoFencesSet(boolean value) {
        synchronized (subscribers) {
            for (RouteAroundRegionEventSubscriber subscriber : subscribers) {
                subscriber.onEvent(new Event.RouteAroundGeoFencesSet(value));
            }
        }
    }

    /**
     * A subscriber that can be registered with the RouteAroundEventRelay
     *  to react to events relevant to the route-around region manager
     */
    public interface RouteAroundRegionEventSubscriber {
        /**
         * When a region event is fired
         * @param event the event.
         */
        void onEvent(Event event);
    }

    /** Underlying datatype representing the different event variants
     * for the route around region manager
     */
    public static abstract class Event {
        public static class RegionAdded extends Event {
            public Shape region;

            public RegionAdded(Shape region) {
                this.region = region;
            }
        }

        public static class RegionRemoved extends Event {
            public Shape region;

            public RegionRemoved(Shape region) {
                this.region = region;
            }
        }

        public static class RouteAroundGeoFencesSet extends Event {
            public Boolean value;

            public RouteAroundGeoFencesSet(boolean value) {
                this.value = value;
            }
        }
    }
}
