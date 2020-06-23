
package com.atakmap.android.routes;

import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.nav.NavigationCue;

import java.util.List;
import java.util.Map;

/**
 * Serves as a data class to provide pertinent details for the points that were generated for a route.
 */

public class RoutePointPackage {
    //-------------------- Fields and Properties ---------------------------

    private final List<PointMapItem> routePoints;
    private final Map<String, NavigationCue> pointCues;

    public List<PointMapItem> getRoutePoints() {
        return routePoints;
    }

    public Map<String, NavigationCue> getPointCues() {
        return pointCues;
    }

    //-------------------- Ctor ---------------------------

    public RoutePointPackage(List<PointMapItem> routePoints,
            Map<String, NavigationCue> pointCues) {
        this.routePoints = routePoints;
        this.pointCues = pointCues;
    }
}
