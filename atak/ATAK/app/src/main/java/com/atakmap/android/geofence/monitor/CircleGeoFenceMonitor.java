
package com.atakmap.android.geofence.monitor;

import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.geofence.alert.GeoFenceAlerting;
import com.atakmap.android.geofence.data.GeoFence;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class CircleGeoFenceMonitor extends GeoFenceMonitor {

    private static final String TAG = "CircleGeoFenceMonitor";

    /**
     * Cache rings so we don't have to constantly cache
     */
    private final DrawingCircle _circle;

    CircleGeoFenceMonitor(MapView view, GeoFence fence, DrawingCircle item) {
        super(view, fence, item);
        _circle = item;
    }

    @Override
    protected List<GeoFenceAlerting.Alert> check(GeoFence.Trigger trigger,
            List<PointMapItem> items, boolean bCheckPrevious) {
        List<GeoFenceAlerting.Alert> ret = new ArrayList<>();

        if (FileSystemUtils.isEmpty(items)) {
            Log.w(TAG, "No items to track: " + this);
            return ret;
        }

        GeoPointMetaData center = _circle.getCenter();
        if (center == null) {
            Log.w(TAG, "check center point item not valid: " + this);
            return ret;
        }

        //check if close or further based on trigger
        int numRings = _circle.getNumRings();
        if (numRings < 1 || numRings > DrawingCircle.MAX_RINGS) {
            Log.w(TAG, "Changing default number of rings to 1 from: "
                    + numRings);
            numRings = 1;
        }

        //get radius of outer circle
        double radiusMeters = _circle.getRadius() * numRings;
        Marker centerMarker = _circle.getCenterMarker();
        Marker radiusMarker = _circle.getRadiusMarker();

        //get timestamp once, for efficiency, even though we get each points location at slightly future times...
        long timestamp = new CoordinatedTime().getMilliseconds();
        //sync on 'this' for use of _uidsLastState
        synchronized (this) {
            for (PointMapItem item : items) {
                // Skip center marker
                if (item == centerMarker || item == radiusMarker)
                    continue;

                if (item == null) {
                    Log.w(TAG, "check point item not valid");
                    continue;
                }

                GeoPoint point = item.getPoint();
                if (point == null) {
                    Log.w(TAG, "check item point item not valid");
                    continue;
                }

                if (checkDismissed(item)) {
                    continue;
                }

                //get distance of point from circle center
                double distanceMeters = GeoCalculations.distanceTo(center.get(),
                        point);
                boolean bInside = inElevationRange(point)
                        && distanceMeters <= radiusMeters;

                //now check if we should alert based on trigger
                if (bCheckPrevious) {
                    //get previous state
                    boolean bPreviouslyInside = getLastState(item.getUID());

                    if (checkTrigger(trigger, bInside, bPreviouslyInside)) {
                        //                        Log.d(TAG, "ALERT Point " + item.getUID()
                        //                                + " distance " + distanceMeters + "m, radius: "
                        //                                + radiusMeters + "m, for fence: " + toString());
                        ret.add(new GeoFenceAlerting.Alert(this, item,
                                timestamp, new GeoPoint(point), bInside));
                    }

                    //only update state if bCheckPrevious
                    _uidsLastState.put(item.getUID(), bInside);
                } else {
                    if (checkTrigger(trigger, bInside)) {
                        //                        Log.d(TAG, "ALERT Point " + item.getUID()
                        //                                + " distance " + distanceMeters + "m, radius: "
                        //                                + radiusMeters + "m, for fence: " + toString());
                        ret.add(new GeoFenceAlerting.Alert(this, item,
                                timestamp, new GeoPoint(point), bInside));
                    }
                }
            } //end items loop
        }

        return ret;
    }

    @Override
    public GeoPointMetaData getCenter() {
        return _circle.getCenter();
    }

    /**
     * Run a quick check of the specified search parameters
     *
     * @param center the center point
     * @param radiusKM the radius to consider in kilometers
     * @param trigger   Note, should be Entry or Exit. Both returns all items searched
     * @param item the item to check
     * @return true if the item satisfies the criteria
     */
    static boolean QuickCheck(GeoPoint center, int radiusKM,
            GeoFence.Trigger trigger, PointMapItem item) {
        if (item == null) {
            Log.w(TAG, "Cannot check null item");
            return false;
        }

        return !FileSystemUtils.isEmpty(QuickCheck(center, radiusKM, trigger,
                Collections.singletonList(item)));
    }

    /**
     * Run a quick check of the specified search parameters
     *
     * @param center the center point
     * @param radiusKM the radius to consider in kilometers
     * @param trigger   Note, should be Entry or Exit. Both returns all items searched
     * @param items the items to check
     * @return true if the item satisfies the criteria
     */
    static List<PointMapItem> QuickCheck(GeoPoint center, int radiusKM,
            GeoFence.Trigger trigger, List<PointMapItem> items) {

        List<PointMapItem> ret = new ArrayList<>();

        if (center == null) {
            Log.w(TAG, "check center point item not valid");
            return ret;
        }

        //Log.d(TAG, String.format(LocaleUtil.getCurrent(),
        //        "Quick Check for items within %d KM of %s", radiusKM,
        //        center.toString()));

        if (FileSystemUtils.isEmpty(items)) {
            Log.w(TAG, "No items to track...");
            return ret;
        }

        double radiusMeters = (radiusKM * 1000L);

        for (PointMapItem item : items) {
            if (item == null) {
                Log.w(TAG, "check point item not valid");
                continue;
            }

            GeoPoint point = item.getPoint();
            if (point == null) {
                Log.w(TAG, "check item point item not valid");
                continue;
            }

            //get distance of point from circle center
            double distanceMeters = GeoCalculations.distanceTo(center, point);
            boolean bWithinRadius = distanceMeters <= radiusMeters;

            //now check if we should alert based on trigger
            if (checkTrigger(trigger, bWithinRadius)) {
                //Log.d(TAG, "ALERT Point " + item.getUID() + " distance "
                //        + distanceMeters + "m, radius: " + radiusMeters
                //        + "m");
                ret.add(item);
            }
        } //end items loop

        return ret;
    }

    //return the distance of the furthest point from center
    @Override
    public double getfurthestPointRange() {
        int numRings = _circle.getNumRings();
        if (numRings < 1 || numRings > DrawingCircle.MAX_RINGS) {
            Log.w(TAG, "Changing default number of rings to 1 from: "
                    + numRings);
            numRings = 1;
        }

        //get radius of outer circle
        return _circle.getRadius() * numRings;
    }

}
