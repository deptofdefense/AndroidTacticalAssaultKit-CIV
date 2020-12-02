
package com.atakmap.android.routes;

import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.nav.NavigationCue;

import java.util.List;
import java.util.Map;

/**
 * Serves as a data class to provide pertinent details for the points that were generated for a route.
 *
 * A RoutePointPackage can either denote that a route was generated successfully, in which case it holds
 * a list of points, and the point cues for the generated route, or that route generation fails, in which
 * case it holds an error message to be displayed to the user explaining why the route generation has failed.
 */
public class RoutePointPackage {
    //-------------------- Fields and Properties ---------------------------

    private List<PointMapItem> routePoints;
    // A map from UIDs of point map items to navigation cues associated with
    // that item.
    private Map<String, NavigationCue> pointCues;
    private String errorMessage;

    /** Returns the points on the route if this is a successful route point package. Returns null otherwise. */
    public List<PointMapItem> getRoutePoints() {
        return routePoints;
    }

    /** Returns the point cues if this is successful route point package. Returns null otherwise. The format of
     * the returned point cues is a map from point map items on the route to the navigation cue associated
     * with that point map item on the route. */
    public Map<String, NavigationCue> getPointCues() {
        return pointCues;
    }

    /** Returns the error message that was set for this route point package (if any). Returns null otherwise. */
    public String getError() {
        return errorMessage;
    }

    //-------------------- Ctor ---------------------------

    /** Constructor for a successful route point package
     * @param routePoints list of point map items of the route, in order.
     * @param pointCues map from UIDs of point map items on the route to the navigation
     * cues set for those points. */
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
