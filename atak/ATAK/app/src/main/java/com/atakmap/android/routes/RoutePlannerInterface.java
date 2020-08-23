
package com.atakmap.android.routes;

import android.app.AlertDialog;

public interface RoutePlannerInterface {
    /**
     * Gets the descriptive name of the planner.
     *
     * @return the descriptive name of the planner
     */
    String getDescriptiveName();

    /**
     * Planner requires a network to be used.
     *
     * @return true if an active network is required.
     */
    boolean isNetworkRequired();

    /**
     * Gets the RouteGenerationTask for this planner that is run when initially generating a route.
     * @param routeGenerationEventListener The listener that should be associated with this task.
     * @return A RouteGenerationTask for this planner.
     */
    RouteGenerationTask getRouteGenerationTask(
            RouteGenerationTask.RouteGenerationEventListener routeGenerationEventListener);

    /**
     * Gets the additional options specific for the planner that may effect the 
     * results.
     */
    RoutePlannerOptionsView getOptionsView(AlertDialog parent);

    /**
     * Gets any additional options for the planner that are needed at the time of navigating a route.
     *
     * @return Null if the planner does not support additional options, the options otherwise
     */
    RoutePlannerOptionsView getNavigationOptions(AlertDialog parent);

    /**
     * Gets whether or not the planner is capable of supporting re-routing.
     */
    boolean isRerouteCapable();

    /**
     * Gets whether or not the planner is capable of supporting routing around regions.
     */
    boolean canRouteAroundRegions();
}
