
package com.atakmap.android.geofence.alert;

import com.atakmap.android.geofence.component.GeoFenceComponent;
import com.atakmap.android.geofence.monitor.GeoFenceMonitor;
import com.atakmap.android.geofence.ui.GeoFenceListModel;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.warning.WarningComponent;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;

public class GeoFenceAlerting {
    private static final String TAG = "GeoFenceAlerting";

    /**
     * Container for alert details
     */
    public static class Alert extends WarningComponent.Alert {
        private final GeoFenceMonitor monitor;
        private final PointMapItem item;
        private final long timestamp;

        /**
         * The point where the breach was detected (may not be the exact breach point
         * especially for fast movers)
         */
        private final GeoPoint detectedPoint;

        /**
         * True for entered, false for exited
         */
        private final boolean entered;

        public Alert(GeoFenceMonitor monitor, PointMapItem item,
                long timestamp, GeoPoint detectedPoint,
                boolean entered) {
            this.monitor = monitor;
            this.item = item;
            this.timestamp = timestamp;
            this.detectedPoint = detectedPoint;
            this.entered = entered;
        }

        /**
         * Returns the item associated with this alert.
         * @return the item
         */
        public PointMapItem getItem() {
            return item;
        }

        public MapItem getMonitorItem() {
            return monitor.getItem();
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isEntered() {
            return entered;
        }

        public GeoPoint getDetectedPoint() {
            return detectedPoint;
        }

        public boolean isValid() {
            return !(item == null || FileSystemUtils.isEmpty(item.getUID()));
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Alert))
                return super.equals(o);

            Alert ao = ((Alert) o);
            if (!isValid() || !ao.isValid())
                return false;

            if (timestamp != ao.timestamp)
                return false;

            if (detectedPoint == null
                    || !detectedPoint.equals(ao.detectedPoint))
                return false;

            if (entered != ao.entered)
                return false;

            //TODO verify geofence UID also?

            if (!FileSystemUtils.isEquals(item.getUID(), ao.item.getUID()))
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            String hash = monitor.getFence().getMapItemUid()
                    + timestamp
                    + entered;
            if (item != null)
                hash = hash + item.getUID();
            return hash.hashCode();
        }

        @Override
        public String toString() {
            return getMessage();
        }

        @Override
        public String getMessage() {
            if (!isValid()) {
                Log.w(TAG, "Skipping invalid getMessage");
                return null;
            }
            String shapeName = ATAKUtilities
                    .getDisplayName(getMonitorItem());
            String callsign = ATAKUtilities
                    .getDisplayName(this.getItem());
            if (ATAKUtilities.isSelf(MapView.getMapView(), this.getItem()))
                callsign = String.format(
                        LocaleUtil.getCurrent(),
                        "(%1$s) %2$s",
                        MapView.getMapView().getContext()
                                .getString(R.string.you),
                        callsign);

            String header = MapView.getMapView().getContext()
                    .getString(R.string.geo_fence_);
            switch (monitor.getFence().getTrigger()) {
                case Entry: {
                    header += MapView.getMapView().getContext()
                            .getString(R.string.entered);
                }
                    break;
                case Exit: {
                    header += MapView.getMapView().getContext()
                            .getString(R.string.exited);
                }
                    break;
                case Both: {
                    header += MapView.getMapView().getContext()
                            .getString(R.string.breached);
                }
                    break;
            }

            return header + "\n" + shapeName + ", " + callsign;
        }

        @Override
        public void onClick() {
            if (!isValid()) {
                Log.w(TAG, "Skipping invalid onClick");
                return;
            }

            //default to focus on map item
            PointMapItem pmi = getItem();
            if (pmi == null)
                return;
            panTo(pmi.getPoint(), MapMenuReceiver.getCurrentItem() != pmi);
        }

        @Override
        public String getAlertGroupName() {
            return MapView.getMapView().getContext()
                    .getString(R.string.geo_fences);
        }

        void cleanUp() {
            String uid = GeoFenceListModel.getBreachMarkerUID(this);
            if (!FileSystemUtils.isEmpty(uid)) {
                MapItem breachMarker = GeoFenceComponent.getMapGroup()
                        .deepFindUID(uid);
                if (breachMarker != null) {
                    //Log.d(TAG, "Cleaning up breach markers for: " + this.toString());
                    GeoFenceComponent.getMapGroup().removeItem(breachMarker);
                }
            }
        }
    }

    /**
     * Map monitor to all its current alerts
     */
    private Map<GeoFenceMonitor, List<Alert>> _ongoingAlerts;

    public GeoFenceAlerting() {
        _ongoingAlerts = new HashMap<>();
    }

    public synchronized void dispose() {
        if (_ongoingAlerts != null) {
            _ongoingAlerts.clear();
            _ongoingAlerts = null;
        }

        WarningComponent.removeAlerts(Alert.class);
    }

    public void alert(GeoFenceMonitor monitor, List<Alert> items,
            boolean bAutoDismiss) {
        if (monitor == null || FileSystemUtils.isEmpty(items)) {
            Log.w(TAG, "No items to alert");
            return;
        }

        Log.d(TAG, "Alerting " + items.size() + " items, for monitor: "
                + monitor);

        //track list of alerts...
        if (bAutoDismiss) {
            //reset the whole list
            set(monitor, items);
        } else {
            //make additions as necessary
            add(monitor, items);
        }

        WarningComponent.addAlerts(Alert.class, getAlertList());
    }

    /**
     * Set the list of items alerting for the specified monitor
     * Removes alerting for items not in the list
     *
     * @param monitor the geofence monitor to set for the alert
     * @param items the list of items alerting for the specific monitor
     */
    private synchronized void set(GeoFenceMonitor monitor, List<Alert> items) {
        List<Alert> previous = _ongoingAlerts.put(monitor, items);
        if (FileSystemUtils.isEmpty(previous)) {
            Log.d(TAG, "Adding monitor: " + monitor.toString());
        } else {
            Log.d(TAG, "Updating monitor: " + monitor.toString() + " from "
                    + previous.size() +
                    " items to " + items.size());
        }
    }

    /**
     * Add alerting for any of the items which are not already alerting
     * Does not remove alerting for items not in the list
     *
     * @param monitor the monitor for the items
     * @param items the items to add.
     */
    private synchronized void add(GeoFenceMonitor monitor, List<Alert> items) {
        List<Alert> currents = _ongoingAlerts.get(monitor);
        if (FileSystemUtils.isEmpty(currents)) {
            set(monitor, items);
        } else {
            Log.d(TAG, "Adding alert count: " + items.size());
            currents.addAll(items);
        }
    }

    /**
     * Cleans up the current ongoing alerts.
     */
    public synchronized void dismissAll() {
        for (List<Alert> alertList : _ongoingAlerts.values()) {
            for (Alert alert : alertList) {
                alert.cleanUp();
            }
        }
        _ongoingAlerts.clear();
        WarningComponent.removeAlerts(Alert.class);
    }

    /**
     * Removes a monitor from the alerting mechanism.   If it the monitor has any ongoing alerts they
     * are cleaned up and removed.
     */
    public void dismiss(GeoFenceMonitor monitor) {
        List<Alert> alerts = remove(monitor);
        for (Alert alert : alerts) {
            alert.cleanUp();
        }
        if (!FileSystemUtils.isEmpty(alerts))
            WarningComponent.removeAlerts(alerts);
    }

    /**
     * Dismiss the alerts for specified monitor, optionally quit monitoring those alerts/items
     *
     * @param monitor the monitor to use
     * @param bStopMonitoring if the monitor also needs to stop monitoring
     * @return true if we quit monitoring, and it was the last item being monitored
     */
    public synchronized boolean dismiss(GeoFenceMonitor monitor,
            boolean bStopMonitoring) {
        List<Alert> items = _ongoingAlerts.get(monitor);
        if (FileSystemUtils.isEmpty(items)) {
            Log.w(TAG, "Found empty alert list while dismissing monitor: "
                    + monitor.toString());
            return false;
        }

        //Log.d(TAG, "Dismissing alert: " + item.getUID() + " for fence: " + monitor.toString());
        boolean ret = false;

        //see if we should stop monitoring
        if (bStopMonitoring) {
            ret = true;
            for (Alert alert : items) {
                ret &= monitor.removeItem(alert.getItem());
            }
        }

        for (Alert alert : items) {
            alert.cleanUp();
        }

        //now clear alerts
        WarningComponent.removeAlerts(items);
        items.clear();
        return ret;
    }

    /**
     * Dismiss a specific alert for a given monitor
     * @param monitor Monitor
     * @param alert Alert
     * @param bStopMonitoring True to stop monitoring the alert's item
     * @return True if there are no more items being monitored
     */
    public synchronized boolean dismiss(GeoFenceMonitor monitor,
            Alert alert, boolean bStopMonitoring) {
        List<Alert> items = _ongoingAlerts.get(monitor);
        if (FileSystemUtils.isEmpty(items) || alert == null
                || !items.remove(alert)) {
            Log.w(TAG, "Unable to dismiss alert: " + alert
                    + " for fence: " + monitor.toString());
            return false;
        }

        boolean ret = false;

        //see if we should stop monitoring
        PointMapItem item = alert.getItem();
        if (bStopMonitoring && item != null)
            ret = monitor.removeItem(item);

        WarningComponent.removeAlert(alert);
        alert.cleanUp();
        return ret;
    }

    /**
     * Dismiss the alert, optionally quit monitoring
     * @param monitor the monitor associated with the alert.
     * @param item the item associated with the alert
     * @param bStopMonitoring if the monitoring should stop when the alert is dismissed
     * @return true if we quit monitoring, and it was the last item being monitored
     */
    public synchronized boolean dismiss(GeoFenceMonitor monitor,
            PointMapItem item, boolean bStopMonitoring) {
        List<Alert> items = _ongoingAlerts.get(monitor);
        if (FileSystemUtils.isEmpty(items)) {
            Log.w(TAG,
                    "Found empty alert list while dismissing item: "
                            + item.getUID());
            return false;
        }

        Alert alert = null;
        int toRemove = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getItem().getUID().equals(item.getUID())) {
                alert = items.get(i);
                toRemove = i;
                break;
            }
        }

        if (toRemove >= 0) {
            //Log.d(TAG, "Dismissing alert: " + item.getUID() + " for fence: " + monitor.toString());
            items.remove(toRemove);
            boolean ret = false;

            //see if we should stop monitoring
            if (bStopMonitoring) {
                ret = monitor.removeItem(item);
            }

            if (alert != null) {
                WarningComponent.removeAlert(alert);
                alert.cleanUp();
            }
            return ret;
        } else {
            Log.w(TAG, "Unable to dismiss alert: " + item.getUID()
                    + " for fence: " + monitor.toString());
            return false;
        }
    }

    private synchronized List<Alert> remove(GeoFenceMonitor monitor) {
        if (_ongoingAlerts.containsKey(monitor)) {
            return _ongoingAlerts.remove(monitor);
            //Log.d(TAG, "Dismissing alert for monitor: " + monitor.toString() + " for item count: " +
            //        (FileSystemUtils.isEmpty(items) ? 0 : items.size()));
        } else {
            //Log.d(TAG, "Dismissing alert but wasn't tracking any items for: " + monitor.toString());
            return new ArrayList<>();
        }
    }

    public synchronized int getAlertCount() {
        int alertCount = 0;
        for (List<Alert> items : _ongoingAlerts.values()) {
            if (FileSystemUtils.isEmpty(items)) {
                Log.w(TAG, "No map items for monitor");
                continue;
            }

            alertCount += items.size();
        }

        return alertCount;
    }

    /**
     * Get list of ongoing alerts
     *
     * @return the list of alerts
     */
    public synchronized Map<GeoFenceMonitor, List<Alert>> getAlerts() {
        if (_ongoingAlerts == null || _ongoingAlerts.size() < 1)
            return new HashMap<>();

        return Collections.unmodifiableMap(_ongoingAlerts);
    }

    /**
     * Get list of ongoing alerts for monitor associated with the specified Shape Map Item UID
     *
     * @param mapItemUID find an ongoing alert for a specific map item.
     * @return the list of alerts for a map item.
     */
    public synchronized List<Alert> getAlerts(String mapItemUID) {
        for (Map.Entry<GeoFenceMonitor, List<Alert>> alert : _ongoingAlerts
                .entrySet()) {
            if (alert.getKey().getMapItemUid().equals(mapItemUID)) {
                return Collections.unmodifiableList(alert.getValue());
            }
        }

        //Log.d(TAG, "Unable to find alert monitor: " + mapItemUID);
        return new ArrayList<>();
    }

    private synchronized List<Alert> getAlertList() {
        List<Alert> alerts = new ArrayList<>();

        for (Map.Entry<GeoFenceMonitor, List<Alert>> alert : _ongoingAlerts
                .entrySet()) {
            alerts.addAll(alert.getValue());
        }

        return Collections.unmodifiableList(alerts);
    }
}
