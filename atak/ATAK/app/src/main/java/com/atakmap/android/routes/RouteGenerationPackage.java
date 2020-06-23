
package com.atakmap.android.routes;

import android.content.SharedPreferences;

import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.List;

/**
 * Serves as a data class to provide pertinent details for the generation of a route.
 */

public class RouteGenerationPackage {
    //-------------------- Fields and Properties ---------------------------
    private final GeoPoint startPoint;
    private final GeoPoint endPoint;
    private final SharedPreferences preferences;
    private final List<GeoPoint> byWayOf;

    public GeoPoint getStartPoint() {
        return startPoint;
    }

    public GeoPoint getEndPoint() {
        return endPoint;
    }

    public SharedPreferences getPreferences() {
        return preferences;
    }

    public List<GeoPoint> getByWayOf() {
        return byWayOf;
    }

    //-------------------- Ctor ---------------------------

    public RouteGenerationPackage(SharedPreferences prefs, GeoPoint origin,
            GeoPoint dest, List<GeoPoint> byWayOf) {
        this.preferences = prefs;
        this.startPoint = origin;
        this.endPoint = dest;
        this.byWayOf = byWayOf;
    }
}
