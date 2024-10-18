
package com.atakmap.android.warning;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.DirectionType;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DangerCloseCalculator implements Runnable,
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = "DangerCloseCalculator";

    public interface CustomFilter {
        /**
         * Ability to define a custom danger close filter for a plugin to implement.
         * @param target the target
         * @param mapItem the map item to consider
         * @param distance the distance from
         * @param bearing the bearing from
         * @return true if the item is considered danger close.
         */
        boolean accept(MapItem target, MapItem mapItem, double distance,
                double bearing);
    }

    /**
     * Map listener to the hostile target being monitored
     */
    final private Map<ClosestItemListener, MapItem> listeners = new HashMap<>();

    /**
     * List of all danger close items (across all hostile targets being monitored)
     * 'Set' storage: only alert once on a single friendly/hostile pair. Even if multiple
     * weapons/rings are enabled for that hostile
     */
    final private Set<DangerCloseAlert> results = new HashSet<>();

    private static final long PERIOD = 2500;
    private boolean cancelled = false;
    private Thread t = null;

    private final SharedPreferences _prefs;
    private static boolean expandedDangerClose = false;

    private static DangerCloseCalculator _instance;

    private static CustomFilter customFilter;

    /**
     * Store information about an item that is "danger close" to a given hostile
     */
    static public class DangerCloseAlert extends WarningComponent.Alert {
        final private PointMapItem hostile;
        final private PointMapItem friendly;
        final private double distance;
        final private double bearing;
        final String direction;

        DangerCloseAlert(final PointMapItem hostile,
                final PointMapItem friendly, final double distance,
                final double bearing) {
            this.hostile = hostile;
            this.friendly = friendly;
            this.distance = distance;
            this.bearing = bearing;
            this.direction = DirectionType.getDirection(bearing)
                    .getAbbreviation();
        }

        public PointMapItem getHostile() {
            return hostile;
        }

        public PointMapItem getFriendly() {
            return friendly;
        }

        /**
         * returns the computed distance in meters between the hostile and friendly.
         */
        public double getDistance() {
            return distance;
        }

        public double getBearing() {
            return bearing;
        }

        public String getDirection() {
            return direction;
        }

        @Override
        public String toString() {
            return getMessage();
        }

        public boolean isValid() {
            return !(friendly == null
                    || FileSystemUtils.isEmpty(friendly.getUID()))
                    && !(hostile == null || FileSystemUtils.isEmpty(hostile
                            .getUID()));

        }

        @Override
        public String getMessage() {
            if (!isValid()) {
                Log.w(TAG, "Skipping invalid getMessage");
                return null;
            }

            return "Danger Close\n"
                    + friendly.getMetaString("callsign", "unk friendly") + " " +
                    (int) Math.round(distance) + "m " + direction;
        }

        @Override
        public void onClick() {
            if (!isValid()) {
                Log.w(TAG, "Skipping invalid onClick");
                return;
            }
            panTo(friendly.getPoint(),
                    MapMenuReceiver.getCurrentItem() != friendly);
        }

        @Override
        public String getAlertGroupName() {
            return MapView.getMapView().getContext().getString(
                    R.string.danger_close);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof DangerCloseAlert))
                return super.equals(o);

            DangerCloseAlert a = (DangerCloseAlert) o;
            return !(!isValid() || !a.isValid())
                    &&
                    FileSystemUtils.isEquals(friendly.getUID(),
                            a.friendly.getUID())
                    &&
                    FileSystemUtils.isEquals(hostile.getUID(),
                            a.hostile.getUID());

        }

        @Override
        public int hashCode() {
            if (!isValid())
                return super.hashCode();

            String hash = friendly.getUID() + hostile.getUID();
            return hash.hashCode();
        }
    }

    /**
     * Listener to be notified of closest item for a given point/hostile
     */
    public interface ClosestItemListener {

        /**
         * Provides the range within which to monitor
         * Return less than or equal to 0 to monitor only the single closest
         * Return greater than 0 to monitor all items within a range
         *
         * @return the distance
         */
        double getRange();

        /**
         * Invoked periodically to update the "closest" item
         *
         * @param ic the alert to be triggered when a closest item is detected
         */
        void onClosestItem(DangerCloseAlert ic);
    }

    /**
     * ctor
     *
     */
    DangerCloseCalculator(Context c) {

        _instance = this;
        _prefs = PreferenceManager
                .getDefaultSharedPreferences(c);

        _prefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(_prefs, "expandedDangerClose");
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {

        if (key == null)
            return;

        if (key.equals("expandedDangerClose")) {
            expandedDangerClose = _prefs.getBoolean(key, false);
        }
    }

    /**
     * Kickoff monitoring thread
     */
    synchronized public void start() {
        if (t == null) {
            t = new Thread(this, "DangerCloseCalculatorThread");
            t.start();
        }
    }

    public static DangerCloseCalculator getInstance() {
        return _instance;
    }

    public void cancel() {
        cancelled = true;
        t = null;
    }

    /**
     * Register listener for updates on closest items to 'hostile'
     * E.g. learn about closets friendly to a given hostile
     *
     * @param icl closest item listener for callback
     * @param hostile the hostile that is used for the closest item.
     */
    public void registerListener(final ClosestItemListener icl,
            final MapItem hostile) {
        final String type = hostile.getType();
        if (type == null || !type.startsWith("a-h"))
            return;

        synchronized (listeners) {
            listeners.put(icl, hostile);
        }
    }

    public void unregisterListener(final ClosestItemListener icl) {
        synchronized (listeners) {
            listeners.remove(icl);
        }
    }

    /**
     * Sets a custom filter for the DangerClose calculator.
     * @param filter the filter to be used.
     */
    public void setCustomFilter(CustomFilter filter) {
        customFilter = filter;
    }

    /**
     * Get list of Danger Close alerts
     * @return returns a list of alerts
     */
    public List<DangerCloseAlert> getAlerts() {

        //sync on listeners throughout
        synchronized (listeners) {
            List<DangerCloseAlert> alerts = new ArrayList<>(results);
            return Collections.unmodifiableList(alerts);
        }
    }

    /**
     * Thread, runs periodically to monitor list of hostiles for danger close
     * Alerts the corresponding listener, and notifies WarningComponent
     */
    @Override
    public void run() {
        while (!cancelled) {
            try {
                Thread.sleep(PERIOD);
            } catch (InterruptedException ignored) {
            }

            // XXX - could be more efficient
            synchronized (listeners) {
                results.clear();
                for (Map.Entry<ClosestItemListener, MapItem> pair : listeners
                        .entrySet()) {
                    if (pair.getValue() instanceof PointMapItem) {
                        if (pair.getValue().getType().startsWith("a-h")) {
                            //add all danger close alerts
                            List<DangerCloseAlert> inRange = find(
                                    ((PointMapItem) pair.getValue()), pair
                                            .getKey().getRange());
                            if (!FileSystemUtils.isEmpty(inRange)) {
                                //Log.d(TAG, "" + inRange.size() + " items in range of: " + pair.getValue().getUID());
                                results.addAll(inRange);
                            }
                            //else {
                            //Log.d(TAG, "No items in range of: " + pair.getValue().getUID());
                            //}

                            //update listener of the single closest friendly
                            final PointMapItem closestFriendly = closestObject;
                            if (closestFriendly != null) {
                                double distance = closestFriendly
                                        .getPoint()
                                        .distanceTo(
                                                ((PointMapItem) pair.getValue())
                                                        .getPoint());
                                double bearing = ((PointMapItem) pair
                                        .getValue())
                                                .getPoint().bearingTo(
                                                        closestFriendly
                                                                .getPoint());
                                DangerCloseAlert closest = new DangerCloseAlert(
                                        (PointMapItem) pair.getValue(),
                                        closestFriendly, distance, bearing);
                                //Log.d(TAG, closest.toString() + " is closest to: " + pair.getValue().getUID());
                                pair.getKey().onClosestItem(closest);
                            } else {
                                Log.d(TAG, "no closest friendly found for: "
                                        + pair.getValue().getUID());
                            }
                        } else {
                            Log.d(TAG, "type of hostile no longer a-h");
                        }
                    }
                } //end listeners loop

                //now updates alerts, add new, remove old
                WarningComponent.addAlerts(DangerCloseAlert.class, results);
            }
        }
    }

    /**
     * Obtain a list of all friendlies in the area that are within the specified range of the target
     * Also track the closest friendly
     *
     * @param target the point o begin measuring from.
     * @param range the range of items within a specific point
     * @return returns a list of alerts
     */
    public static synchronized List<DangerCloseAlert> find(
            final PointMapItem target, final double range) {

        final List<DangerCloseAlert> list = new ArrayList<>();

        //reset scratch space
        closestDistance = Double.MAX_VALUE;
        closestObject = null;

        // Target is a surface-to-air threat
        final boolean airDefense = target.getType().startsWith("a-h-G-U-C-D");

        final CustomFilter localCustomFilter = customFilter;
        //find self marker
        final PointMapItem self = MapView.getMapView().getSelfMarker();
        if (localCustomFilter != null) {

            final double d = target.getPoint().distanceTo(self.getPoint());
            final double b = target.getPoint().bearingTo(self.getPoint());
            if (localCustomFilter.accept(target, self, d, b)) {
                closestObject = self;
                closestDistance = self.getPoint().distanceTo(target.getPoint());
                list.add(new DangerCloseAlert(target, self, d, b));
            }

        } else if (!airDefense && self.getGroup() != null
                && self.getPoint().isValid()) {

            //initialize closest with self
            closestObject = self;
            closestDistance = self.getPoint().distanceTo(target.getPoint());

            //see if self is within range
            final double d = target.getPoint().distanceTo(self.getPoint());
            if (d <= range) {
                final double b = target.getPoint().bearingTo(self.getPoint());
                list.add(new DangerCloseAlert(target, self, d, b));
            }
        }

        //search root group
        final MapGroup rg = MapView.getMapView().getRootGroup();
        if (rg == null)
            return list;

        rg.deepForEachItem(new MapGroup.OnItemCallback<PointMapItem>(
                PointMapItem.class) {

            @Override
            public boolean onMapItem(final PointMapItem mapItem) {
                final String type = mapItem.getType();

                // If a custom filter has been installed, utilize that instead of the
                // default implementation.
                if (localCustomFilter != null) {
                    final double d = target.getPoint().distanceTo(
                            mapItem.getPoint());
                    final double b = target.getPoint().bearingTo(
                            mapItem.getPoint());
                    if (localCustomFilter.accept(target, mapItem, d, b)) {
                        list.add(new DangerCloseAlert(target, mapItem, d, b));
                        if (d < closestDistance) {
                            //this is the new closest item
                            closestDistance = d;
                            closestObject = mapItem;
                        }
                    }

                    return false;
                }

                // Friendlies
                boolean isCorrectTypePrefix = type.startsWith("a-f");

                // Add in ground neutrals and ground unknowns if the prefix is checked.
                if (expandedDangerClose)
                    isCorrectTypePrefix = isCorrectTypePrefix ||
                            type.startsWith("a-n-G") ||
                            type.startsWith("a-u-G");

                // Air defense hostiles only affect aircraft
                // and non-air defense only affect non-aircraft
                isCorrectTypePrefix &= airDefense == type.startsWith("a-f-A");

                if (isCorrectTypePrefix) {
                    final double d = target.getPoint().distanceTo(
                            mapItem.getPoint());
                    if (d <= range) {
                        //this map item is within range
                        final double b = target.getPoint().bearingTo(
                                mapItem.getPoint());
                        list.add(new DangerCloseAlert(target, mapItem, d, b));
                    }

                    if (d < closestDistance) {
                        //this is the new closest item
                        closestDistance = d;
                        closestObject = mapItem;
                    }
                }
                return false;
            }

        });

        return list;
    }

    // tmp usage by the find method.   Scratch space for variables.   Synchronized for in the 
    // find method.
    private static double closestDistance = Double.MAX_VALUE;
    private static PointMapItem closestObject = null;
    private static GeoPoint point = null;

    /**
     * Searches for the closest map item of type a-f and a-n.
     * @param gp the point used for searching against.
     * TODO: use the method Chris described for finding the closest.
     */
    private static synchronized PointMapItem find(final GeoPoint gp) {

        // suggest new way, not able to get the wild card to work exactly right (tried both % and *).
        //       final MapItem friendly = _mapGroup.deepFindClosestItem(gp, "type", "a-f-G-%");
        //       return friendly;

        point = gp;
        closestDistance = Double.MAX_VALUE;
        closestObject = null;

        final PointMapItem self = MapView.getMapView().getSelfMarker();
        if (self.getGroup() != null && self.getPoint().isValid()) {
            closestObject = self;
            closestDistance = self.getPoint().distanceTo(gp);
        }

        // brute brute force from the old ClosestFriendlyLink
        if (MapView.getMapView().getRootGroup() != null) {
            MapView.getMapView().getRootGroup().deepForEachItem(comparator);
        }
        return closestObject;

    }

    static private final MapGroup.OnItemCallback<PointMapItem> comparator = new MapGroup.OnItemCallback<PointMapItem>(
            PointMapItem.class) {

        @Override
        public boolean onMapItem(final PointMapItem mapItem) {
            // not sure that the a-n-G needs to be there, but we will give it a whirl.
            boolean isCorrectTypePrefix = mapItem.getType().startsWith("a-f")
                    && !mapItem.getType().startsWith("a-f-A");
            //       || mapItem.getType().startsWith("a-n-G");
            if (isCorrectTypePrefix) {
                double distance = mapItem.getPoint().distanceTo(point);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestObject = mapItem;
                }
            }
            return false;
        }

    };

}
