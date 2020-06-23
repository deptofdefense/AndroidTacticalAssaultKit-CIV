
package com.atakmap.android.routes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class to manage Route Planners.
 */
public class RoutePlannerManager {
    private Map<String, RoutePlannerInterface> _routePlanners = new ConcurrentHashMap<>();

    /**
     * Gets the Route Planner registered with the given id.
     *
     * @param id the identifier for the specific route planner
     * @return null if no matching Route Planner
     */
    public RoutePlannerInterface getPlanner(String id) {
        return _routePlanners.get(id);
    }

    /**
     * Registers the Route Planner with the given id.
     *
     * @param id the identifier for a route planner
     * @param planner the route planner
     * @return The previously registered Route Planner with the same id, if any
     */
    public RoutePlannerInterface registerPlanner(String id,
            RoutePlannerInterface planner) {
        return _routePlanners.put(id, planner);
    }

    /**
     * Unregisters the Route Planner with the given id.
     *
     * @param id the identifier for the route planner
     * @return The Route Planner that has been unregistered, if any
     */
    public RoutePlannerInterface unregisterPlanner(String id) {
        return _routePlanners.remove(id);
    }

    /**
     * Gets a set of all id->Route Planner mapping entries representing all registered Route
     * Planners. The set is decoupled from the manager once it is returned.
     * 
     * @return the set of all route planners
     */
    public Set<Map.Entry<String, RoutePlannerInterface>> getRoutePlanners() {
        return new HashSet<>(
                _routePlanners.entrySet());
    }

    /**
     * Gets a set of all id->Route Planner mapping entries containing only entries that support
     * re-routing. The set is decoupled from the manager once it is returned.
     *
     * @return the set of all reroute planners
     */
    public Set<Map.Entry<String, RoutePlannerInterface>> getReroutePlanners() {
        Map<String, RoutePlannerInterface> results = new HashMap<>();

        for (Map.Entry<String, RoutePlannerInterface> entry : _routePlanners
                .entrySet()) {
            if (entry.getValue().isRerouteCapable()) {
                results.put(entry.getKey(), entry.getValue());
            }
        }

        return results.entrySet();
    }

    /**
     * Gets the number of registered Route Planners.
     *
     * @return The number of registered Route Planners.
     */
    public int getCount() {
        return _routePlanners.size();
    }
}
