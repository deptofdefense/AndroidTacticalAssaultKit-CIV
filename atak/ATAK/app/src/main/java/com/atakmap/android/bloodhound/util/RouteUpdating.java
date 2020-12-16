
package com.atakmap.android.bloodhound.util;

import android.util.Pair;

import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.routes.Route;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.coords.Vector2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Utility class with methods for calculating updates to the
 *  bloodhound route based on where the start/end points have moved. */
public class RouteUpdating {

    private static final String TAG = "com.atakmap.android.bloodhound.RouteUpdating";

    /** Update a route by truncating both the beginning and ending of the route. */
    private static void updateRoute(final GeoPoint origin, final GeoPoint dest,
            final Route route) {
        truncateRouteBeginning(origin, route);
        truncateRouteEnding(dest, route);
    }

    /** Helper function to get the points of a route. */
    private static List<GeoPoint> getRoutePoints(Route route) {
        List<GeoPoint> geoPoints = new ArrayList<>();
        for (PointMapItem pmi : route.getPointMapItems()) {
            geoPoints.add(pmi.getPoint());
        }
        return geoPoints;
    }

    /** Helper function to calculate the segments of a route. */
    private static List<RouteSegment> getRouteSegments(Route route) {
        List<GeoPoint> routePoints = getRoutePoints(route);
        List<RouteSegment> routeSegments = new ArrayList<>();
        for (int i = 0; i < routePoints.size(); i++) {
            if (i + 1 < routePoints.size()) {
                routeSegments.add(new RouteSegment(routePoints.get(i),
                        routePoints.get(i + 1)));
            }
        }
        return routeSegments;
    }

    /** Takes a route, and the current location of a point navigating along that route,
     *  and truncates the beginning of route by removing points that have already been travelled,
     *  and drawing a line from the point navigating along the route,
     *  to the closest point on the route to that point. */
    public synchronized static void truncateRouteBeginning(
            final GeoPoint origin,
            final Route route) {
        synchronized (route) {
            Log.d(TAG, "Truncating route beginning");
            if (route.getNumPoints() > 2) {
                Pair<GeoPoint, Integer> pair = closestPointOnRoute(origin,
                        route, true);
                GeoPoint closestPoint = pair.first;
                int closestPointBefore = pair.second;
                // Remove everything up and including the beginning
                // of the segment closest to the origin
                route.removePoints(0, closestPointBefore + 2);
                // Add back in the new part of the segment.
                route.addMarker(0,
                        Route.createWayPoint(new GeoPointMetaData(closestPoint),
                                UUID.randomUUID().toString()));
                route.addMarker(0,
                        Route.createWayPoint(new GeoPointMetaData(origin),
                                UUID.randomUUID().toString()));
            }
        }
    }

    /** Takes a route, and the current location of a point being navigated to by the roue, and
     *  updates the end of the route so that the last route segement is a line from the point
     *  being navigated to, to the closest point on the route to the point being navigated to. */
    public synchronized static void truncateRouteEnding(final GeoPoint dest,
            final Route route) {
        Log.d(TAG, "Truncating route ending");
        synchronized (route) {
            if (route.getNumPoints() > 2) {
                Pair<GeoPoint, Integer> pair = closestPointOnRoute(dest, route,
                        false);
                GeoPoint closestPoint = pair.first;
                int closestPointAfter = pair.second + 1;

                // Remove everything from the end of the segment closest to dest,
                // up until the end of the route
                route.removePoints(closestPointAfter, route.getNumPoints());

                // Add back in the new part of the segment.

                route.addMarker(
                        Route.createWayPoint(new GeoPointMetaData(closestPoint),
                                UUID.randomUUID().toString()));
                route.addMarker(Route.createWayPoint(new GeoPointMetaData(dest),
                        UUID.randomUUID().toString()));
            }
        }
    }

    /**
     * Helper function to find the closest point on a route to the given point.
     *
     * @param fromBeginning Specifies whether or not the point is being tracked from the beginning of the route,
     *                      or the end of the route. This helps to avoid edge-cases that can occur when {@param point}
     *                      is the same as the last point or the first point on the given route. */
    private static Pair<GeoPoint, Integer> closestPointOnRoute(GeoPoint point,
            Route route, Boolean fromBeginning) {
        List<RouteSegment> routeSegments = getRouteSegments(route);
        List<Double> distances = new ArrayList<>();
        List<GeoPoint> closestPoints = new ArrayList<>();

        int startIndex;
        int endIndex;
        if (fromBeginning) {
            startIndex = 1;
            endIndex = routeSegments.size();
        } else {
            startIndex = 0;
            endIndex = routeSegments.size() - 1;
        }

        for (int i = startIndex; i < endIndex; i++) {
            Vector2D seg0 = toVector(routeSegments.get(i).origin);
            Vector2D seg1 = toVector(routeSegments.get(i).dest);
            Vector2D pointVector = toVector(point);
            GeoPoint nearestPoint = fromVector(
                    Vector2D.nearestPointOnSegment(pointVector, seg0, seg1));
            distances.add(GeoCalculations.distanceTo(point, nearestPoint));
            closestPoints.add(nearestPoint);
        }

        int indexOfClosestSegment = distances
                .indexOf(Collections.min(distances));

        return new Pair<>(
                closestPoints.get(indexOfClosestSegment),
                indexOfClosestSegment);
    }

    // Helper functions for conversions to/from a Vector2D.
    private static Vector2D toVector(GeoPoint point) {
        return new Vector2D(point.getLatitude(), point.getLongitude());
    }

    private static GeoPoint fromVector(Vector2D point) {
        return new GeoPoint(point.x, point.y);
    }

    // Data class representing a segement of a route.
    private static class RouteSegment {

        public final GeoPoint origin;
        public final GeoPoint dest;

        public RouteSegment(GeoPoint origin, GeoPoint dest) {
            this.origin = origin;
            this.dest = dest;
        }
    }
}
