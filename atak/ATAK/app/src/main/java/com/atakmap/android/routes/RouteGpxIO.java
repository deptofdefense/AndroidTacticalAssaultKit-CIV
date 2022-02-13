
package com.atakmap.android.routes;

import android.content.SharedPreferences;

import com.atakmap.android.maps.Marker;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.android.gpx.Gpx;
import com.atakmap.android.gpx.GpxRoute;
import com.atakmap.android.gpx.GpxTrack;
import com.atakmap.android.gpx.GpxTrackSegment;
import com.atakmap.android.gpx.GpxWaypoint;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoBounds;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Support converting ATAK <code>Route</code> objects to and from GPX Heavily based on RouteKmlIO
 * 
 * 
 */
public class RouteGpxIO {

    private static final String TAG = "RouteGpxIO";

    /**
     * Convert route to KML
     * 
     * @param route
     * @return
     */
    public static Gpx toGpx(Route route) {

        // TODO set route metadata e.g. infil vs exfil in "Extensions"

        // create route
        Gpx gpx = new Gpx();
        List<GpxRoute> routes = new ArrayList<>();
        gpx.setRoutes(routes);
        GpxRoute gpxroute = new GpxRoute();
        // TODO set desc/cmt/etc
        gpxroute.setName(route.getTitle());

        double unwrap = 0;
        GeoBounds bounds = route.getBounds(null);
        if (bounds.crossesIDL())
            unwrap = 360;
        List<GpxWaypoint> points = new ArrayList<>();
        for (GeoPoint p : route.getPoints()) {
            if (p == null)
                continue;
            points.add(convertPoint(p, unwrap));
        }

        if (points.size() < 1) {
            Log.e(TAG, "Unable to create GPX Route with no points");
            return null;
        } else {
            gpxroute.setPoints(points);
            routes.add(gpxroute);
        }

        // add checkpoints as waypoint
        List<GpxWaypoint> checkpoints = new ArrayList<>();
        for (int i = 0; i < route.getNumPoints(); i++) {
            PointMapItem marker = route.getMarker(i);

            // perhaps we could wrap this as a method in Route...
            if (marker == null) {
                continue;
            }

            GeoPointMetaData agp = route.getPoint(i);
            GpxWaypoint wp = convertPoint(agp.get());

            // TODO what other fields to set?
            wp.setName(marker.getMetaString("callsign", null));
            wp.setDesc(marker.getUID());

            Log.d(TAG,
                    "Adding KML route placemark: "
                            + marker.getMetaString("callsign", null));
            checkpoints.add(wp);
        }

        if (checkpoints.size() > 0) {
            Log.d(TAG,
                    "Creating GPX Route checkpoint count: "
                            + checkpoints.size());
            gpx.setWaypoints(checkpoints);
        }

        return gpx;
    }

    /**
     * Write KML route to specified file
     * 
     * @param gpx
     * @param file
     * @throws Exception
     */
    public static void write(Gpx gpx, File file) throws Exception {
        File parent = file.getParentFile();
        if (!IOProviderFactory.exists(parent))
            if (!IOProviderFactory.mkdirs(parent)) {
                Log.d(TAG, "Failed to make dir at " + parent.getAbsolutePath());
            }

        // TODO for performance reuse Serializer? Or use PullParser
        Serializer serializer = new Persister();
        try (FileOutputStream fos = IOProviderFactory.getOutputStream(file)) {
            serializer.write(gpx, fos);
        }
    }

    /**
     * Convert GPX to a list of ATAK routes (one route per GPX Route and GPX Track) All segments for
     * a given track are part of the same ATAK route
     * 
     * @param gpx
     * @return
     */
    static List<Route> toRoute(MapView mapView, Gpx gpx,
            MapGroup routeGroup,
            MapGroup waypointGroup, SharedPreferences prefs) {
        double TOLERANCE_DISTANCE_METERS = 1;

        List<Route> routes = new ArrayList<>();

        if (gpx == null
                || (gpx.getRoutes() == null && gpx.getTracks() == null)) {
            Log.e(TAG,
                    "Unable to create route from invalid GPX, at least one route or track is required");
            return routes;
        }

        // grab GPX routes
        int routeCount = 0;
        long skippedPoints = 0;
        double curDistance;
        if (gpx.getRoutes() != null && gpx.getRoutes().size() > 0) {
            for (GpxRoute gpxroute : gpx.getRoutes()) {
                if (gpxroute == null) {
                    Log.e(TAG, "Unable to create route with no GPX route");
                    continue;
                }

                List<GpxWaypoint> routepoints = gpxroute.getPoints();
                if (routepoints == null || routepoints.size() < 1) {
                    Log.e(TAG, "Unable to create route with no GPX points");
                    continue;
                }

                routeCount++;

                // get list waypoints to be considered as checkpoints (if on the route)
                List<GpxWaypoint> waypoints = gpx.getWaypoints();

                // create route
                String prefix = prefs.getString("waypointPrefix", "CP");
                String routeName = gpxroute.getName();
                if (FileSystemUtils.isEmpty(routeName)) {
                    routeName = "GPX Route " + routeCount;
                }

                MapGroup group = routeGroup.addGroup(routeName);
                group.setMetaBoolean("addToObjList", false);
                int color = Integer.parseInt(prefs.getString(
                        "defaultRouteColor",
                        String.valueOf(Route.DEFAULT_ROUTE_COLOR)));

                Route route = new Route(mapView, routeName, color, prefix, UUID
                        .randomUUID()
                        .toString());
                // XXX - temporary hack for bug 2331 -- disable refreshes
                route.setMetaBoolean("__ignoreRefresh", true);

                // TODO pull route parameters (eg Infil vs Exfil) e.g. from Extensions

                // maintain a list of checkpoints already included in route, include each only once
                List<GpxWaypoint> checkpoints = new ArrayList<>();

                // walk all route points
                GeoPoint geoPoint;
                GeoPoint previousGeoPoint = null;
                boolean bCheckpointsForNamedRoutePoints = prefs.getBoolean(
                        "gpxImportCheckpointsForNamedRoutePoints", true);

                List<PointMapItem> pmiList = new ArrayList<>();

                for (GpxWaypoint point : routepoints) {
                    if (point == null) {
                        Log.w(TAG, "Skipping null route point");
                        continue;
                    }

                    try {
                        geoPoint = convertPoint(point);
                    } catch (NullPointerException npe) {
                        Log.e(TAG, "erorr occurred converting", npe);
                        geoPoint = null;
                    }
                    if (geoPoint == null) {
                        Log.w(TAG, "Skipping null converted route point");
                        continue;
                    }

                    // filter out sufficiently close consecutive points
                    if (previousGeoPoint != null) {
                        curDistance = GeoCalculations
                                .distanceTo(previousGeoPoint, geoPoint);
                        if (curDistance < TOLERANCE_DISTANCE_METERS) {
                            // Log.d(TAG, "Skipping duplicate route point: " + geoPoint.toString());
                            skippedPoints++;
                            continue;
                        }
                        // Log.d(TAG, "dist=" + dist);
                    }

                    // see if point is a checkpoint, first check "named" route point option
                    boolean bIsCheckpoint = false;
                    if (!FileSystemUtils.isEmpty(point.getName())
                            && bCheckpointsForNamedRoutePoints) {
                        // route point has a name and setting is turned on, lets make a checkpoint
                        bIsCheckpoint = true;
                        checkpoints.add(point);
                        Marker m = Route.createWayPoint(
                                GeoPointMetaData.wrap(geoPoint), UUID
                                        .randomUUID().toString());
                        String callsign = point.getName();
                        if (!FileSystemUtils.isEmpty(callsign)) {
                            m.setMetaString("callsign", callsign);
                            m.setTitle(callsign);
                        }
                        Log.d(TAG, "adding route checkpoint: " + callsign);
                        pmiList.add(m);
                    } else {
                        // now check to see if there is a corresponding waypoint we can pull in
                        GpxWaypoint checkpoint = match(waypoints, point);
                        if (checkpoint != null
                                && !checkpoints.contains(checkpoint)) {
                            bIsCheckpoint = true;
                            checkpoints.add(checkpoint);
                            Marker m = Route.createWayPoint(
                                    GeoPointMetaData.wrap(geoPoint), UUID
                                            .randomUUID().toString());
                            String callsign = checkpoint.getName();
                            if (!FileSystemUtils.isEmpty(callsign)) {
                                m.setMetaString("callsign", callsign);
                                m.setTitle(callsign);
                            }
                            Log.d(TAG, "adding route waypoint: " + callsign);
                            pmiList.add(m);
                        }
                    }

                    if (!bIsCheckpoint) {
                        // just add as point (non checkpoint)
                        pmiList.add(Route.createControlPoint(geoPoint));
                    }

                    previousGeoPoint = geoPoint;
                }

                PointMapItem[] pmiArray = new PointMapItem[pmiList.size()];
                pmiList.toArray(pmiArray);
                route.addMarkers(0, pmiArray);

                if (route.getNumPoints() < 2) {
                    Log.w(TAG,
                            "Unable to add at least 2 GPX route points to route "
                                    + routeName);
                    continue;
                }

                Log.d(TAG,
                        "Added route " + route.getTitle() + " with "
                                + route.getNumPoints()
                                + " points from GPX route");
                routes.add(route);

                // XXX - hack for bug 2331 -- reenable refreshes
                route.setMetaBoolean("__ignoreRefresh", false);
            }
        }

        // grab GPX tracks, all segments of a single track comprise a single ATAK route
        if (gpx.getTracks() != null && gpx.getTracks().size() > 0) {
            for (GpxTrack gpxtrack : gpx.getTracks()) {

                if (gpxtrack == null || gpxtrack.getSegments() == null
                        || gpxtrack.getSegments().size() < 1) {
                    Log.e(TAG,
                            "Unable to create route with no GPX track segments");
                    continue;
                }

                List<GpxWaypoint> trackwaypoints = new ArrayList<>();
                for (GpxTrackSegment segment : gpxtrack.getSegments()) {
                    if (segment == null || segment.getWaypoints() == null
                            || segment.getWaypoints().size() < 1) {
                        Log.e(TAG,
                                "Unable to create route with no GPX track segment waypoints");
                        continue;
                    }

                    Log.d(TAG, "Adding " + segment.getWaypoints().size()
                            + " waypoints for track segment");
                    trackwaypoints.addAll(segment.getWaypoints());
                }

                if (trackwaypoints.size() < 1) {
                    Log.e(TAG,
                            "Unable to create route with no GPX track segment waypoints");
                    continue;
                }

                // get list waypoints to be considered as checkpoints (if on the route)
                routeCount++;
                List<GpxWaypoint> waypoints = gpx.getWaypoints();

                // create route
                String prefix = prefs.getString("waypointPrefix", "CP");
                String routeName = gpxtrack.getName();
                if (FileSystemUtils.isEmpty(routeName)) {
                    routeName = "GPX Track " + routeCount;
                }

                MapGroup group = routeGroup.addGroup(routeName);
                group.setMetaBoolean("addToObjList", false);
                int color = Integer.parseInt(prefs.getString(
                        "defaultRouteColor",
                        String.valueOf(Route.DEFAULT_ROUTE_COLOR)));

                Route route = new Route(mapView, routeName, color, prefix, UUID
                        .randomUUID()
                        .toString());
                // XXX - hack for bug 2331 -- disable refreshes
                route.setMetaBoolean("__ignoreRefresh", true);

                // TODO pull route parameters (eg Infil vs Exfil) e.g. from Extensions

                // maintain a list of checkpoints already included in route, include each only once
                List<GpxWaypoint> checkpoints = new ArrayList<>();

                // walk all route points
                GeoPoint geoPoint = null, previousGeoPoint = null;
                PointMapItem routePoint;

                for (GpxWaypoint point : trackwaypoints) {
                    if (point == null) {
                        Log.w(TAG, "Skipping null track point");
                        continue;
                    }

                    geoPoint = convertPoint(point);
                    if (geoPoint == null) {
                        Log.w(TAG, "Skipping null converted track point");
                        continue;
                    }

                    // filter out sufficiently close consecutive points
                    if (previousGeoPoint != null) {
                        curDistance = GeoCalculations
                                .distanceTo(previousGeoPoint, geoPoint);
                        if (curDistance < TOLERANCE_DISTANCE_METERS) {
                            // Log.d(TAG, "Skipping duplicate track point: " + geoPoint.toString());
                            skippedPoints++;
                            continue;
                        }
                        // Log.d(TAG, "dist=" + dist);
                    }

                    // see if point is a checkpoint
                    GpxWaypoint checkpoint = match(waypoints, point);
                    if (checkpoint != null
                            && !checkpoints.contains(checkpoint)) {
                        checkpoints.add(checkpoint);
                        routePoint = Route.createWayPoint(
                                GeoPointMetaData.wrap(geoPoint), UUID
                                        .randomUUID().toString());
                        String callsign = checkpoint.getName();
                        if (!FileSystemUtils.isEmpty(callsign))
                            routePoint.setMetaString("callsign", callsign);
                        Log.d(TAG, "Adding track waypoint: " + callsign);
                        if (route.addMarker(routePoint))
                            waypointGroup.addItem(routePoint);
                    } else {
                        // just add as point (non checkpoint)
                        route.addPoint(GeoPointMetaData.wrap(geoPoint));
                    }

                    previousGeoPoint = geoPoint;
                }

                if (route.getNumPoints() < 2) {
                    Log.w(TAG,
                            "Unable to add at least 2 GPX track points to route "
                                    + routeName);
                    continue;
                }

                Log.d(TAG,
                        "Added route " + route.getTitle() + " with "
                                + route.getNumPoints()
                                + " points from GPX track");
                routes.add(route);

                // XXX - hack for bug 2331 -- reenable refreshes
                route.setMetaBoolean("__ignoreRefresh", false);
            }
        }

        Log.d(TAG, "Created " + routes.size()
                + " routes from GPX, total skipped points: "
                + skippedPoints);
        return routes;
    }

    public static GeoPoint convertPoint(GpxWaypoint in) {
        // TODO set any other fields? error?
        GeoPoint gp = null;

        // TODO any elevation conversion?
        double alt;

        if (in.getEle() == null)
            alt = Double.NaN;
        else
            alt = in.getEle().doubleValue();

        if (in.getLat() != null && in.getLon() != null) {

            double lat = in.getLat().doubleValue();
            double lng = in.getLon().doubleValue();

            if (lng > 180)
                lng -= 360;
            else if (lng < -180)
                lng += 360;

            if (!Double.isNaN(alt))
                gp = new GeoPoint(lat, lng, alt);
            else
                gp = new GeoPoint(lat, lng);
        }

        return gp;
    }

    public static GpxWaypoint convertPoint(GeoPoint in, double unwrap) {
        GpxWaypoint wp = new GpxWaypoint();
        // TODO set any other fields? ce/le error?
        wp.setLat(in.getLatitude());
        wp.setLon(in.getLongitude() + (in.getLongitude() < 0 && unwrap > 0
                || in.getLongitude() > 0 && unwrap < 0 ? unwrap : 0));

        if (in.isAltitudeValid()) {
            // This seems like it should be MSL.   Not documented in the spec
            // https://productforums.google.com/forum/#!topic/maps/ThUvVBoHAvk
            final double alt = EGM96.getMSL(in);
            wp.setEle(alt);
        }

        return wp;
    }

    public static GpxWaypoint convertPoint(GeoPoint in) {
        return convertPoint(in, 0);
    }

    /**
     * See if the specified point matches one of the GPX Waypoints (within 20 meters) TODO currently
     * a checkpoint must be within 20 meters of a point in the route, instead allow a checkpoint to
     * be within 20 meters of the line between any two points in the route
     * 
     * @param points
     * @param point
     * @return
     */
    private static GpxWaypoint match(List<GpxWaypoint> points,
            GpxWaypoint point) {
        if (points == null || points.size() < 1 || point == null)
            return null;

        for (GpxWaypoint waypoint : points) {
            if (waypoint == null)
                continue;
            try {
                GeoPoint wp = convertPoint(waypoint);
                GeoPoint pt = convertPoint(point);
                if (wp != null && pt != null) {
                    if (GeoCalculations.distanceTo(wp,
                            pt) < RouteKmlIO.CHECKPOINT_TOLERANCE_DISTANCE_METERS)
                        return waypoint;
                }
            } catch (NullPointerException npe) {
                Log.e(TAG, "erorr occurred matching", npe);
            }
        }

        return null;
    }

    /**
     * Read Gpx from specified file
     * 
     * @param file the gpx file to read
     * @return the Gpx object
     */
    public static Gpx read(final File file) {
        Serializer serializer = new Persister();
        try (FileInputStream fis = IOProviderFactory.getInputStream(file)) {
            // Read, strict=false to ignore non-standard GPX/XML
            return serializer.read(Gpx.class, fis, false);
        } catch (Exception e) {
            Log.e(TAG, "Unable to read file: " + file.getAbsolutePath(), e);
        }

        return null;
    }
}
