
package com.atakmap.android.routes;

import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.nav.NavigationCue;

import java.util.List;
import java.util.Map;

/**
 * Serves as a data class to provide pertinent details for the points that were generated for a route.
 *
 * A RoutePointPackage can either denote that a route was generated successfully, in which case it holds
 * a list of points, and the point cues for the generated route, or that route generation failes, in which
 * case it holds an error message to be displayed to the user explaining why the route generation has failed.
 */
public class RoutePointPackage {
    //-------------------- Fields and Properties ---------------------------

    private List<PointMapItem> routePoints;
    private Map<String, NavigationCue> pointCues;
    private String errorMessage;

    /** Returns the points on the route if this is a successful route point package. Returns null otherwise. */
    public List<PointMapItem> getRoutePoints() {
        return routePoints;
    }

    /** Returns the point cues if this is successful route point package. Returns null otherwise. */
    public Map<String, NavigationCue> getPointCues() {
        return pointCues;
    }

    /** Returns the error message that was set for this route point package (if any). Returns null otherwise. */
    public String getError() {
        return errorMessage;
    }

    //-------------------- Ctor ---------------------------

    /** Constructor for a successful route point package */
    public RoutePointPackage(List<PointMapItem> routePoints,
            Map<String, NavigationCue> pointCues) {
        this.routePoints = routePoints;
        this.pointCues = pointCues;
    }

    /** Constructor for a route point package that has yielded an error */
    public RoutePointPackage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
