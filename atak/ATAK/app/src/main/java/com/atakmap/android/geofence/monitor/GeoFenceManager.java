
package com.atakmap.android.geofence.monitor;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Pair;
import android.widget.Toast;

import com.atakmap.android.geofence.alert.GeoFenceAlerting;
import com.atakmap.android.geofence.component.GeoFenceComponent;
import com.atakmap.android.geofence.component.GeoFenceReceiver;
import com.atakmap.android.geofence.data.GeoFence;
import com.atakmap.android.geofence.data.GeoFence.MonitoredTypes;
import com.atakmap.android.geofence.data.GeoFenceConstants;
import com.atakmap.android.geofence.data.ShapeUtils;
import com.atakmap.android.geofence.ui.HierarchyListUserGeoFence;
import com.atakmap.android.gui.TileButtonDialog;
import com.atakmap.android.hierarchy.HierarchyListReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.missionpackage.MapItemSelectTool;
import com.atakmap.android.routes.RoutePlannerView;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.SpatialCalculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 *
 */
public class GeoFenceManager implements GeoFenceComponent.GeoFenceListener,
        MapEventDispatcher.MapEventDispatchListener,
        MapItem.OnGroupChangedListener {
    private static final String TAG = "GeoFenceManager";
    private static final long MONITOR_RATE = 2000; //2 seconds

    /**
     * How many iterations b/t re-scans of the monitored area
     */
    private static final long MONITOR_RESCAN_PERIOD = 10; //10*2=20 seconds
    private static final String LITMUS_TEST = "a-";

    private final MapView _view;
    private final Context _context;
    private final MapGroup _group;
    private final GeoFenceAlerting _alerting;
    private final GeoFenceComponent _component;
    private Timer _monitorTimer;

    /**
     * Keep count of how many iterations
     */
    private long _monitorIteration;

    /**
     * Spatial Calc not thread safe, but we check all monitors on a single thread, so we resuse
     * a single SpatialCalculator instance
     */
    private final SpatialCalculator _spatialCalc;

    /**
     * Map the geo fence map item's (shape) UID to the monitor
     */
    final Map<String, GeoFenceMonitor> _monitors;

    /**
     * Cache list to avoid recreating every repeatedly
     */
    final List<GeoFenceMonitor> _toRemove;

    /**
     * Deferrred add events - wait until the key UID is added to the map
     */
    private final Map<String, Pair<GeoFence, MapItem>> _deferredAdds;

    public GeoFenceManager(GeoFenceComponent component, MapView view) {
        _monitors = new HashMap<>();
        _view = view;
        _context = view.getContext();
        _group = _view.getRootGroup();
        _alerting = new GeoFenceAlerting();
        _component = component;
        _component.addGeoFenceChangedListener(this);
        _toRemove = new ArrayList<>();
        _deferredAdds = new HashMap<>();
        _spatialCalc = new SpatialCalculator.Builder().inMemory().build();
        _monitorIteration = 0;
        initialize();
    }

    public void dispose() {
        if (_monitorTimer != null) {
            _monitorTimer.cancel();
            _monitorTimer.purge();
            _monitorTimer = null;
        }
        _view.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_REMOVED, this);
        _view.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_ADDED, this);
        if (_alerting != null)
            _alerting.dispose();
        _spatialCalc.dispose();
    }

    public GeoFenceAlerting getAlerting() {
        return _alerting;
    }

    @Override
    public void onFenceAdded(GeoFence fence, MapItem item) {
        if (fence == null || !fence.isTracking()) {
            Log.d(TAG, "Skipping fence not tracking: "
                    + (fence == null ? "" : fence.toString()));
            return;
        }
        if (item == null) {
            Log.d(TAG, "fence item is null, no longer tracking: " + fence);
            return;
        }

        // This geo-fence is being imported from CoT event
        boolean imported = item
                .hasMetaValue(GeoFenceConstants.GEO_FENCE_IMPORTED);
        if (imported)
            item.removeMetaData(GeoFenceConstants.GEO_FENCE_IMPORTED);

        // Attempting to add geofence to a shape that hasn't been created
        // or added to the map yet
        String shapeUID = item.getMetaString("shapeUID", null);
        if (item instanceof PointMapItem && !FileSystemUtils.isEmpty(shapeUID)
                && !shapeUID.equals(item.getUID())
                && _group.deepFindUID(shapeUID) == null) {
            Log.d(TAG, "Deferring geofence add for " + item.getUID()
                    + " - missing shape " + shapeUID);
            synchronized (_deferredAdds) {
                _deferredAdds.put(shapeUID, new Pair<>(fence, item));
            }
            return;
        }

        final GeoFenceMonitor monitor = GeoFenceMonitor.Factory.Create(_view,
                _group, _spatialCalc, fence, item);
        if (monitor == null || !monitor.isValid()) {
            Log.w(TAG, "onFenceAdded monitor invalid");
            return;
        }

        Log.d(TAG, "onFenceAdded: " + monitor);

        //get distance from user and gather initial monitor list
        //only items in this range at "this" moment will be monitored (for performance)
        //pass control of monitor off to init task, go ahead and add monitor but don't alert
        monitor.getFence().setTracking(false);
        addMonitor(monitor);
        boolean bGetLastState = monitor.getFence()
                .getTrigger() == GeoFence.Trigger.Both;
        new InitGeoFenceTask(this, bGetLastState, imported).execute(monitor);
    }

    private void initialize() {
        Log.d(TAG, "Initializing...");

        //get initial
        final MapGroup baseGroup = _view.getRootGroup();
        MapGroup.deepMapItems(baseGroup, new MapGroup.MapItemsCallback() {

            @Override
            public boolean onItemFunction(MapItem item) {
                if (!(item instanceof PointMapItem))
                    return false;

                String type = item.getType();
                if (!FileSystemUtils.isEmpty(type)
                        && type.startsWith(LITMUS_TEST)) {
                    for (GeoFence.MonitoredTypes tt : GeoFence.MonitoredTypes
                            .values()) {
                        if (tt.getFilter().onItemFunction(item))
                            tt.add((PointMapItem) item);
                    }
                }

                return false;
            }
        });

        _view.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_REMOVED, this);
        _view.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_ADDED, this);

        _monitorTimer = new Timer("GeoFenceManager");
        _monitorTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                monitor();
            }
        }, 0, MONITOR_RATE);

        Log.d(TAG,
                String.format(LocaleUtil.getCurrent(),
                        "Found %d friendly, %d team, %d hostile, %d ALL",
                        GeoFence.MonitoredTypes.Friendly.size(),
                        GeoFence.MonitoredTypes.TAKUsers.size(),
                        GeoFence.MonitoredTypes.Hostile.size(),
                        GeoFence.MonitoredTypes.All.size()));
    }

    @Override
    public void onMapEvent(MapEvent event) {
        String type = event.getType();
        MapItem item = event.getItem();
        if (item == null || FileSystemUtils.isEmpty(type))
            return;

        if (type.equals(MapEvent.ITEM_ADDED)) {
            Pair<GeoFence, MapItem> reAdd;
            synchronized (_deferredAdds) {
                reAdd = _deferredAdds.remove(item.getUID());
            }
            if (reAdd != null)
                onFenceAdded(reAdd.first, reAdd.second);
            else
                addItem(item);
        } else if (type.equals(MapEvent.ITEM_REMOVED)) {
            removeItem(item);
        }
    }

    private void removeItem(final MapItem mi) {
        if (!(mi instanceof PointMapItem))
            return;
        _view.post(new Runnable() {
            @Override
            public void run() {
                removeItem_sync(mi);
            }
        });
    }

    synchronized private void removeItem_sync(MapItem mi) {
        //remove from global tracked lists
        //Log.d(TAG, "removeItem: " + mi.getUID());
        PointMapItem item = (PointMapItem) mi;
        for (GeoFence.MonitoredTypes tt : GeoFence.MonitoredTypes.values()) {
            if (tt.getFilter().onItemFunction(item)) {
                tt.remove(item);
            }
        }

        //remove from active monitors
        for (final GeoFenceMonitor monitor : _monitors.values()) {
            if (monitor.getFence().getMonitoredTypes().getFilter()
                    .onItemFunction(item)) {
                Log.d(TAG, "Removing item: " + item.getUID() + " for monitor: "
                        + monitor);
                if (monitor.removeItem(item)) {
                    Log.d(TAG, "Removed last item for monitor: " + monitor);
                    toast(_context.getString(R.string.deleted_last_tracked_item,
                            ATAKUtilities.getDisplayName(ATAKUtilities
                                    .findAssocShape(monitor.getItem()))));
                }
            }
        }
    }

    private void addItem(final MapItem item) {
        if (!(item instanceof PointMapItem))
            return;
        addItem_sync(item);
    }

    private synchronized void addItem_sync(final MapItem item) {
        PointMapItem pointMapItem = (PointMapItem) item;

        //add to global tracked lists
        for (GeoFence.MonitoredTypes tt : GeoFence.MonitoredTypes.values()) {
            if (tt.getFilter().onItemFunction(pointMapItem)) {
                tt.add(pointMapItem);
            }
        }

        int count = 0;
        GeoFenceMonitor lastAdded = null;
        for (GeoFenceMonitor monitor : _monitors.values()) {
            //add to active monitors with matching filter (skip Custom user lists)
            if (!monitor.getFence().getMonitoredTypes().getFilter()
                    .onItemFunction(pointMapItem))
                continue;
            if (monitor.getFence()
                    .getMonitoredTypes() == GeoFence.MonitoredTypes.Custom) {
                if (monitor.checkSelectedItem(pointMapItem)) {
                    Log.d(TAG, "Added previously selected item to monitor: "
                            + monitor);
                    addMonitor(monitor);
                    MapItem monitorItem = monitor.getItem();
                    monitorItem.setMetaStringArrayList(
                            GeoFenceConstants.MARKER_MONITOR_UIDS,
                            monitor.getSelectedItemUids());
                    monitor.persist();
                }
            } else {
                //first check if within monitored range. Note, for Exit fence where the item is
                //in monitor range, but not in fence, this will alert immediately
                if (CircleGeoFenceMonitor.QuickCheck(monitor.getCenter().get(),
                        monitor.getFence().getRangeKM(),
                        GeoFence.Trigger.Entry,
                        pointMapItem)) {
                    if (monitor.addItem(pointMapItem)) {
                        count++;
                        lastAdded = monitor;
                        Log.d(TAG, "Added item to monitor: " + monitor);
                    }
                }
            }
        }

        if (count == 1 && lastAdded != null) {
            Log.d(TAG,
                    "Added " + ATAKUtilities.getDisplayName(item)
                            + " " +
                            ATAKUtilities.getDisplayName(ATAKUtilities
                                    .findAssocShape(lastAdded.getItem())));
            //toast(_context.getString(R.string.geofence_now_monitoring_for_one,
            //        ATAKUtilities.getDisplayName(item),
            //        ShapeUtils.getShapeName(lastAdded.getItem())));
        } else if (count > 0) {
            Log.d(TAG, "Added item to monitor count: " + count);
            //toast(_context.getString(R.string.geofence_now_monitoring_for_many,
            //        ATAKUtilities.getDisplayName(item), count));
        }

    }

    @Override
    public void onFenceChanged(GeoFence fence, MapItem item) {

        if (item == null || FileSystemUtils.isEmpty(item.getUID())) {
            Log.w(TAG, "onFenceChanged invalid");
            return;
        }

        String uid = item.getUID();
        GeoFenceMonitor monitor = getMonitor(uid);
        if (monitor == null || !monitor.isValid()) {
            //Note, this happens during startup, first time GeoFence is accessed since
            //its already in DB, but not yet being tracked by this manager
            if (monitor != null)
                monitor.setFence(fence);

            Log.d(TAG, "onFenceChanged, adding new monitor");
            onFenceAdded(fence, item);
        } else if (!fence.equals(monitor.getFence())) {
            //See what changed
            if (!fence.isTracking()) {
                Log.d(TAG, "onFenceChanged, not tracking");
                removeMonitor(monitor.getMapItemUid());
            } else {
                //remove & prompt user again
                Log.d(TAG, "onFenceChanged, re-adding monitor");
                removeMonitor(monitor.getMapItemUid());
                onFenceAdded(fence, item);
            }
        } else {
            //nothing changed? just set fence
            Log.d(TAG, "onFenceChanged: " + fence);
            monitor.setFence(fence);
        }
    }

    @Override
    public void onFenceRemoved(String mapItemUid) {
        Log.d(TAG, "onFenceRemoved: " + mapItemUid);
        MapItem mi = _view.getMapItem(mapItemUid);
        if (mi != null)
            GeoFence.clearGeofenceState(_view, mi);

    }

    /**
     * Check if we currently tracking this Geo Fence
     * If the monitor is set to custom, but has no tracked items, then ignore tracking
     *
     * @param mapItemUid the uid for the map item
     * @return true if the item is currently being tracked
     */
    public synchronized boolean isTracking(String mapItemUid) {
        GeoFenceMonitor monitor = getMonitor(mapItemUid);
        return monitor != null && (monitor.getFence()
                .getMonitoredTypes() != MonitoredTypes.Custom
                || monitor.hasTrackedItems());
    }

    /**
     * Check if we currently tracking this Geo Fence
     *
     * @param geofence the geofence to be considered
     * @return true if the geofence map item is being tracked
     */
    public synchronized boolean isTracking(GeoFence geofence) {
        return isTracking(geofence.getMapItemUid());
    }

    /**
     * Look up monitor
     *
     * @param uid   UID of the fence reference map item
     * @return the monitor for the specified geofence
     */
    public synchronized GeoFenceMonitor getMonitor(String uid) {
        if (FileSystemUtils.isEmpty(uid)) {
            Log.w(TAG, "unable to get monitor without uid");
            return null;
        }

        return _monitors.get(uid);
    }

    /**
     * Add or replace monitor
     *
     * @param monitor the monitor
     * @return Previous monitor, or null if no previous monitor exists
     */
    private synchronized GeoFenceMonitor addMonitor(GeoFenceMonitor monitor) {
        if (monitor == null || !monitor.isValid()) {
            Log.w(TAG, "Cannot add invalid monitor");
            return null;
        }

        GeoFenceMonitor previous = _monitors.put(monitor.getMapItemUid(),
                monitor);
        if (previous != null) {
            Log.d(TAG, "Updating monitor: " + monitor);
            dispatchMonitorChanged(monitor);
        } else {
            Log.d(TAG, "Adding monitor: " + monitor);
            monitor.getItem().addOnGroupChangedListener(this);
            dispatchMonitorAdded(monitor);
        }

        return previous;
    }

    /**
     * Remove specified monitor
     *
     * @param uid the uid for the monitor
     * @return removed monitor, or null if none removed
     */
    private synchronized GeoFenceMonitor removeMonitor(String uid) {
        if (FileSystemUtils.isEmpty(uid)) {
            Log.w(TAG, "unable to remove monitor without uid");
            return null;
        }

        Log.d(TAG, "removeMonitor: " + uid);
        GeoFenceMonitor removed = _monitors.remove(uid);
        if (removed != null) {
            _alerting.dismiss(removed);
            removed.dispose();
            removed.getItem().removeOnGroupChangedListener(this);
            dispatchMonitorRemoved(uid);
        } else {
            Log.w(TAG, "No monitor removed for: " + uid);
        }

        return removed;
    }

    @Override
    public void onItemAdded(MapItem item, MapGroup group) {
    }

    @Override
    public void onItemRemoved(MapItem item, MapGroup group) {
        if (item == null || FileSystemUtils.isEmpty(item.getUID())) {
            Log.w(TAG, "onItemRemoved invalid");
            return;
        }

        //Find reference shape so we can remove the monitor
        String mapItemUid = item.getUID();
        MapItem ref = ShapeUtils.getReferenceShape(_view, false,
                mapItemUid, _group, null);
        if (ref == null || FileSystemUtils.isEmpty(ref.getUID())) {
            //TODO we are failing in here for items deleted, b/c we can't look up in the map
            //group, b/c the map item(s) have been been removed... So we currently do a sanity
            //check in monitor()
            Log.w(TAG, "onItemRemoved invalid reference");
        } else {
            mapItemUid = ref.getUID();
        }

        Log.d(TAG, "onItemRemoved: " + item.getUID() + ", ref: " + mapItemUid);
        deleteMonitor(mapItemUid);
    }

    /**
     * Run periodically in a thread to monitor the GeoFences.
     * Also (less) periodically re-scans the "search space" for new/moved items which should
     * now be monitored
     */
    private synchronized void monitor() {
        //long start = android.os.SystemClock.elapsedRealtime();
        _monitorIteration++;

        //TODO we could make this a pref, but may be confusing b/c I think we'd want to never
        //auto dismiss for "Both" trigger based on how we track "last state" and alert
        final boolean bAutoDismissAlerts = false;

        for (GeoFenceMonitor monitor : _monitors.values()) {
            //indicator that dispose has been called
            if (_group == null) {
                Log.w(TAG,
                        "Breaking out of monitor loop due to disposed manager");
                return;
            }

            //check that fence is still valid e.g. if edited down to 2 points
            if (!monitor.isValid()) {
                Log.w(TAG, "Fence no longer valid: " + monitor);
                _toRemove.add(monitor);
                continue;
            }

            //sanity check for GeoFence shape Map Item still exists
            //Note, we also have this check due to inconsistent behavior across Shapes...
            if (_group.deepFindItem("uid",
                    monitor.getItem().getUID()) == null) {
                Log.w(TAG, "Fence no longer exists: " + monitor);
                _toRemove.add(monitor);
                continue;
            }

            if (_monitorIteration % MONITOR_RESCAN_PERIOD == 0) {
                rescan(monitor);
            }

            List<GeoFenceAlerting.Alert> toAlert = monitor.check();
            if (!FileSystemUtils.isEmpty(toAlert)) {
                //TODO combine alerts for all monitors and alert all at once?
                _alerting.alert(monitor, toAlert, bAutoDismissAlerts);
                dispatchMonitorChanged(monitor);
            } else if (bAutoDismissAlerts) {
                _alerting.dismiss(monitor);
                dispatchMonitorChanged(monitor);
            } else {
                //no-op, no alerts, and not auto-dismissing
            }
        } //end monitor loop

        //see if any shapes were deleted but not yet removed from monitoring
        if (!FileSystemUtils.isEmpty(_toRemove)) {
            Log.w(TAG, _toRemove.size() + " fences being removed");
            for (GeoFenceMonitor r : _toRemove) {
                deleteMonitor(r.getMapItemUid());
            }

            _toRemove.clear();
        }

        //long stop = android.os.SystemClock.elapsedRealtime();
        //Log.d(TAG, "Checked " + _monitors.size() + " monitors in seconds: "
        //        + ((double) stop - start) / 1000D);
    }

    /**
     * Rescan to see if we should start monitoring any additional items
     * e.g. if matching types have wandered into our search space
     *
     * Note, these should be quick since we are just running a Circle QuickCheck, not comparing
     * any complex geometries
     *
     * @param monitor the monitor
     */
    private void rescan(final GeoFenceMonitor monitor) {
        if (monitor.getFence()
                .getMonitoredTypes() == GeoFence.MonitoredTypes.Custom
                ||
                !monitor.getFence().isTracking()) {
            Log.d(TAG, "Skipping re-scan of " + monitor);
            return;
        }

        //check to make sure our range shouldn't have changed
        //this would be better done on shape change instead of every 30 seconds
        int furthestPointKM = (int) (monitor.getfurthestPointRange() / 1000L)
                + 75;
        if (monitor.getFence().getRangeKM() != furthestPointKM) {
            monitor.getFence().setRangeKM(furthestPointKM);
        }

        //long start = android.os.SystemClock.elapsedRealtime();
        Log.d(TAG, "Re-scanning " + monitor);

        List<PointMapItem> itemsToScan = monitor.getRescanItems();
        if (FileSystemUtils.isEmpty(itemsToScan)) {
            Log.d(TAG, "No items to re-scan for " + monitor);
            return;
        }

        int count = 0;
        PointMapItem lastAdded = null;

        //loop all TAK Users and see if they match the monitor
        for (PointMapItem item : itemsToScan) {
            //see if already monitoring
            if (monitor.isMonitoring(item))
                continue;

            if (monitor.getFence().getMonitoredTypes().getFilter()
                    .onItemFunction(item)) {
                //first check if within monitored range. Note, for Exit fence where the item is
                //in monitor range, but not in fence, this will alert immediately
                if (CircleGeoFenceMonitor.QuickCheck(monitor.getCenter().get(),
                        monitor.getFence().getRangeKM(),
                        GeoFence.Trigger.Entry,
                        item)) {
                    if (monitor.addItem(item)) {
                        count++;
                        lastAdded = item;
                        Log.d(TAG, "Added item " + item.getUID()
                                + " to monitor: " + monitor);
                    }
                }
            }
        }

        if (count == 1 && lastAdded != null) {
            Log.d(TAG,
                    "Added " + ATAKUtilities.getDisplayName(lastAdded)
                            + " " +
                            ATAKUtilities.getDisplayName(ATAKUtilities
                                    .findAssocShape(monitor.getItem())));

            //toast(_context.getString(R.string.geofence_now_monitoring_for_one,
            //        ATAKUtilities.getDisplayName(lastAdded),
            //        ShapeUtils.getShapeName(monitor.getItem())));
        } else if (count > 0) {
            Log.d(TAG,
                    "Added " + count + " items to monitor: "
                            + monitor);
            //toast(_context.getString(
            //        R.string.geofence_now_monitoring_additional_items,
            //        count, ShapeUtils.getShapeName(monitor.getItem())));
        }

        //long stop = android.os.SystemClock.elapsedRealtime();
        //Log.d(TAG, "Re-scanned " + itemsToScan.size() + " items in seconds: "  + ((double)stop-start)/1000D);
    }

    public void deleteMonitor(String mapItemUid) {
        removeMonitor(mapItemUid);
        _component.getDatabase().remove(mapItemUid);

        //notify listeners
        _component.dispatchGeoFenceRemoved(mapItemUid);
    }

    private void notifyMonitoring(int size, GeoFenceMonitor monitor) {

        Log.d(TAG, "Monitoring " + size
                + " items currently inside fence search area: "
                + monitor.toString());
        String message = _context.getString(R.string.monitoring_items, size);
        if (size < 1)
            message = _context.getString(
                    R.string.geofence_no_items_within_fence_will_rescan,
                    ATAKUtilities.getDisplayName(
                            ATAKUtilities.findAssocShape(monitor.getItem())));
        //toast(message);
        Log.d(TAG, message);
    }

    /**
     * User has selected which map items to monitor via HierarchyListUserGeoFence
     * (may be a subset of the map items which passed the filter check of the geofence's MonitoredTypes
     *
     * @param monitorUid
     * @param uidsToTrack
     */
    public void onItemsSelected(String monitorUid, List<String> uidsToTrack) {
        if (FileSystemUtils.isEmpty(monitorUid)) {
            Log.w(TAG, "onItemsSelected but no monitor UID");
            return;
        }

        GeoFenceMonitor monitor = getMonitor(monitorUid);
        if (monitor == null || !monitor.isValid()) {
            Log.w(TAG, "onItemsSelected but no valid monitor for UID: "
                    + monitorUid);
            toast(_context.getString(R.string.unable_to_find_geo_fence));
            return;
        }

        MapItem item = monitor.getItem();

        final List<String> l = item.getMetaStringArrayList(
                GeoFenceConstants.MARKER_MONITOR_UIDS);

        List<String> previousUIDs;

        if (l != null) {
            previousUIDs = new ArrayList<>(l);
        } else {
            previousUIDs = new ArrayList<>();
        }

        //collect list of items selected for monitoring
        monitor.clearSelectItems();
        ArrayList<String> uids = new ArrayList<>();
        List<PointMapItem> selected = new ArrayList<>();
        if (!FileSystemUtils.isEmpty(uidsToTrack)) {
            for (PointMapItem pmi : monitor.getFence().getMonitoredTypes()
                    .getItems()) {
                for (String uid : uidsToTrack) {
                    if (pmi.getUID().equals(uid)) {
                        selected.add(pmi);
                        uids.add(uid);
                        uidsToTrack.remove(uid);
                        break;
                    }
                }
            }
        }

        monitor.setSelectItems(selected);
        monitor.setSelectUids(uidsToTrack);
        monitor.getFence().setTracking(true);

        if (!uidsToTrack.isEmpty())
            Log.d(TAG, "Could not find " + uidsToTrack.size()
                    + " selected UIDs for " + monitor);

        selected = monitor.getItems();

        //Note we only display items in the search space to the user in HierarchyListUserGeoFence
        //so if none selected, nothing to monitor
        if (FileSystemUtils.isEmpty(selected)) {
            if (uidsToTrack.isEmpty()) {
                monitor.getFence().setTracking(false);
                removeMonitor(monitor.getMapItemUid());
            }
            item.removeMetaData(GeoFenceConstants.MARKER_MONITOR_UIDS);
            Log.w(TAG, "Selected no items currently inside fence: "
                    + monitor);

            // people just want to know if the fence has been breached not when a item is being considered

            //toast(_context.getString(
            //       R.string.geofence_no_selected_items_inside_search_area));
        } else {
            //start monitoring
            addMonitor(monitor);
            item.setMetaStringArrayList(GeoFenceConstants.MARKER_MONITOR_UIDS,
                    uids);
            Log.d(TAG, "onItemsSelected Monitoring " + selected.size()
                    + " items currently inside fence search area: "
                    + monitor);

            // people just want to know if the fence has been breached not when a item is being considered
            //notifyMonitoring(selected.size(), monitor);
        }

        Collections.sort(uids, RoutePlannerView.ALPHA_SORT);
        Collections.sort(previousUIDs, RoutePlannerView.ALPHA_SORT);
        if (!FileSystemUtils.isEquals(previousUIDs, uids))
            monitor.persist();
    }

    public void dismiss(GeoFenceMonitor monitor, boolean bStopMonitoring) {
        //if last item removed, or user requested, then stop monitoring
        boolean allDismissed = _alerting.dismiss(monitor, bStopMonitoring);
        if (bStopMonitoring) {
            toast(_context.getString(
                    R.string.geofence_dismissed_all_tracked_items,
                    ATAKUtilities.getDisplayName(
                            ATAKUtilities.findAssocShape(monitor.getItem()))));
            removeMonitor(monitor.getMapItemUid());
            monitor.getFence().setTracking(false);
            monitor.persist();
            return;
        }

        if (allDismissed) {
            Log.d(TAG, "Removed last item but still monitoring: " + monitor);
        }

        //        Toast.makeText(_context,
        //                "Dismissed all tracked items. Will periodically rescan: " +
        //                        ShapeUtils.getShapeName(monitor.getItem()),
        //                Toast.LENGTH_LONG).show();
    }

    /**
     * Dismisses a specific alert for a monitor
     * @param monitor Monitor
     * @param alert Alert
     * @param bStopMonitoring True to stop monitoring the alert item
     *                        False to just dismiss the alert
     */
    public void dismiss(final GeoFenceMonitor monitor,
            GeoFenceAlerting.Alert alert,
            boolean bStopMonitoring) {
        _alerting.dismiss(monitor, alert, bStopMonitoring);
    }

    /**
     * Dismisses the last alert for a given map item
     * @param monitor Monitor
     * @param item  if null, dismiss all current alerts
     * @param bStopMonitoring if item is not null, this specifies whether to stop monitoring item
     *                        XXX - This is either misleading or not coded properly
     *                        It actually turns the entire monitor off, for some reason
     */
    public void dismiss(final GeoFenceMonitor monitor, PointMapItem item,
            boolean bStopMonitoring) {
        if (item == null) {
            Log.w(TAG, "Unable to dismiss empty item");
            return;
        }

        boolean allDismissed = _alerting
                .dismiss(monitor, item, bStopMonitoring);
        if (bStopMonitoring) {
            Log.d(TAG, "Removed last item for monitor: " + monitor);
            toast(_context.getString(
                    R.string.geofence_dismissed_all_tracked_items,
                    ATAKUtilities.getDisplayName(
                            ATAKUtilities.findAssocShape(monitor.getItem()))));
            removeMonitor(monitor.getMapItemUid());
            monitor.getFence().setTracking(false);
            monitor.persist();
            return;
        }

        if (allDismissed) {
            Log.d(TAG, "Removed last item but still monitoring: " + monitor);
        }

        //        Toast.makeText(_context,
        //                "Dismissed all tracked items. Will periodically rescan: " +
        //                        ShapeUtils.getShapeName(monitor.getItem()),
        //                Toast.LENGTH_LONG).show();
    }

    public synchronized void deleteAll() {
        Log.d(TAG, "Deleting all monitors");
        _alerting.dismissAll();
        _monitors.clear();
        _component.getDatabase().clearAll();

    }

    public synchronized void dismissAll() {
        Log.d(TAG, "Dismissing all monitors");
        for (GeoFenceMonitor monitor : _monitors.values()) {
            dismiss(monitor, false);
        }
    }

    public synchronized int getCount() {
        return _monitors.size();
    }

    /**
     * Task to search Geo Fence for map items of proper TrackedType, and prompt user for which
     * map items to monitor
     */
    private static abstract class InitGeoFenceBaseTask extends
            AsyncTask<GeoFenceMonitor, Void, List<PointMapItem>> {

        private static final String TAG = "InitGeoFenceBaseTask";

        protected final MapView _mapView;
        protected final Context _context;
        //protected ProgressDialog _progressDialog;
        protected GeoFenceMonitor _monitor;
        protected final GeoFenceManager _manager;
        protected final boolean _imported;

        InitGeoFenceBaseTask(GeoFenceManager manager, boolean imported) {
            _manager = manager;
            _mapView = manager._view;
            _context = _mapView.getContext();
            _imported = imported;
        }

        @Override
        protected void onPreExecute() {
            // See ATAK-9381 - This progress dialog is shown any time this task
            // is run on the UI thread, which can be annoying and confusing.
            // If this dialog is absolutely necessarily, consider moving this
            // code to the "Add Geo Fence" dialog and dismissing once the newly
            // added fence has begun monitoring
            /*if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
                _progressDialog = new ProgressDialog(_context);
                _progressDialog.setTitle(_context.getString(
                        R.string.initializing_geo_fence));
                _progressDialog.setMessage(_context.getString(
                        R.string.searching_fence_space));
                _progressDialog.setIndeterminate(true);
                _progressDialog.setCancelable(false);
                _progressDialog.show();
            }*/
        }

        @Override
        protected void onCancelled(List<PointMapItem> pointMapItems) {
            super.onCancelled(pointMapItems);

            /*if (_progressDialog != null) {
                _progressDialog.dismiss();
                _progressDialog = null;
            }*/
        }

        @Override
        protected List<PointMapItem> doInBackground(
                GeoFenceMonitor... monitors) {
            if (monitors == null || monitors.length < 1) {
                Log.w(TAG, "No monitors provided");
                return null;
            }

            _monitor = monitors[0];
            if (_monitor == null || !_monitor.isValid()) {
                Log.w(TAG, "Invalid monitor provided");
                return null;
            }

            //get list of matching items currently inside fence
            Log.d(TAG, "Running init search");
            _monitor.getFence().setTracking(false);
            _monitor.clearSelectItems();
            return search();
        }

        /**
         * Called on background thread to find map items within the search space
         * @return
         */
        protected abstract List<PointMapItem> search();

        @Override
        protected void onPostExecute(final List<PointMapItem> found) {
            /*if (_progressDialog != null) {
                _progressDialog.dismiss();
                _progressDialog = null;
            }*/

            if (_monitor == null) {
                _manager.toast(_context.getString(
                        R.string.failed_to_find_geo_fence_monitor));
                return;
            }

            // if the state is preserved for the geofence monitor do not toast that no found everytime
            // the application is loaded.

            _manager.beginMonitoring(found, _monitor, _imported);

            // this workflow confuses people 

            //if (found.size() > 0) {
            //    _manager.beginMonitoring(found, _monitor);
            //} else {
            //    _manager.promptMonitoring(_monitor);
            //}
        }
    }

    /**
     * Task to search Geo Fence for map items of proper TrackedType, and prompt user for which
     * map items to monitor. Optionally get current state (in/out) for each found item
     */
    private static class InitGeoFenceTask extends InitGeoFenceBaseTask {

        private static final String TAG = "InitGeoFenceTask";

        private final boolean _bGetLastState;

        InitGeoFenceTask(GeoFenceManager manager, boolean bGetLastState,
                boolean imported) {
            super(manager, imported);
            _bGetLastState = bGetLastState;
        }

        @Override
        protected List<PointMapItem> search() {
            //first get list inside search area
            List<PointMapItem> foundAll = CircleGeoFenceMonitor.QuickCheck(
                    _monitor.getCenter().get(),
                    _monitor.getFence().getRangeKM(),
                    GeoFence.Trigger.Entry,
                    _monitor.getFence().getMonitoredTypes().getItems());

            if (!_bGetLastState || FileSystemUtils.isEmpty(foundAll))
                return foundAll;

            //TODO may be more efficient to check all foundAll at once rather than individually
            //however this code runs only once per geofence, when monitoring of a geofence begins
            //now see which ones are inside the fence
            //List<PointMapItem> foundInside = _monitor.check(GeoFence.Trigger.Entry, foundAll);
            _monitor.clearUidsLastState();
            Map<String, Boolean> lastState = new HashMap<>();
            for (PointMapItem item : foundAll) {
                lastState.put(item.getUID(),
                        _monitor.check(GeoFence.Trigger.Entry, item, false));
            }
            _monitor.setUidsLastState(lastState);

            // Note, if this is a "custom" MonitoredTypes and the user selects a subset, then we
            // will be storing the "last state" for some UIDs that we don't care about... should
            // have no logical effect
            return foundAll;
        }
    }

    private void promptMonitoring(final GeoFenceMonitor monitor) {
        //no items found
        AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle(R.string.geo_fence_search_space_empty);
        b.setCancelable(false);
        b.setMessage(
                R.string.geofence_no_matching_items_adjust_or_begin_monitoring);
        b.setPositiveButton(R.string.adjust,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int i) {
                        Log.d(TAG,
                                "No matching map items, adjust monitoring parameters");
                        monitor.getFence().setTracking(false);
                        removeMonitor(monitor.getMapItemUid());
                        AtakBroadcast.getInstance().sendBroadcast(new Intent(
                                GeoFenceReceiver.EDIT)
                                        .putExtra("uid",
                                                monitor.getMapItemUid()));
                    }
                });
        b.setNeutralButton(R.string.monitor,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int i) {
                        Log.d(TAG,
                                "Begin monitoring with no matching map items");
                        beginMonitoring(new ArrayList<PointMapItem>(), monitor,
                                false);
                    }
                });
        b.setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int i) {
                        Log.d(TAG, "No matching map items, cancel monitoring");
                        monitor.getFence().setTracking(false);
                        removeMonitor(monitor.getMapItemUid());
                    }
                });
        b.show();
    }

    private void beginMonitoring(final List<PointMapItem> items,
            GeoFenceMonitor monitor, boolean imported) {
        //all found items are sides fence
        if (monitor.getFence()
                .getMonitoredTypes() == GeoFence.MonitoredTypes.Custom) {

            MapItem mItem = monitor.getItem();
            ArrayList<String> monitorUids = mItem.getMetaStringArrayList(
                    GeoFenceConstants.MARKER_MONITOR_UIDS);
            if (monitorUids != null && monitorUids.isEmpty()) {
                // Empty non-null monitor list means we should prompt
                //let user select which items to monitor
                Set<String> uidWhitelist = new HashSet<>();
                for (PointMapItem item : items)
                    uidWhitelist.add(item.getUID());

                Log.d(TAG, "Selecting Map Items to monitor, available items: "
                        + uidWhitelist.size());
                monitor.getFence().setTracking(false);
                promptSelectItems(monitor, uidWhitelist);
            } else if (monitorUids != null) {
                onItemsSelected(monitor.getMapItemUid(), monitorUids);
            }
        } else {
            //start monitoring
            monitor.setSelectItems(items);
            monitor.getFence().setTracking(true);
            addMonitor(monitor);
            notifyMonitoring(items.size(), monitor);
            if (!imported)
                monitor.persist();
        }
    }

    private void promptSelectItems(final GeoFenceMonitor monitor,
            final Set<String> uidWhitelist) {
        final Resources r = _view.getResources();
        TileButtonDialog d = new TileButtonDialog(_view);
        d.addButton(r.getDrawable(R.drawable.select_from_map),
                r.getString(R.string.map_select));
        d.addButton(r.getDrawable(R.drawable.select_from_overlay),
                r.getString(R.string.overlay_title));
        d.show(R.string.select_items_to_monitor,
                R.string.multiselect_dialogue);
        d.setOnClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Select from map
                if (which == 0) {
                    Intent i = new Intent(GeoFenceReceiver.ITEMS_SELECTED)
                            .putExtra("monitorUid", monitor.getMapItemUid());
                    Bundle b = new Bundle();
                    b.putString("prompt", r.getString(
                            R.string.select_items_to_monitor));
                    b.putStringArray("allowUIDs", uidWhitelist.toArray(
                            new String[0]));
                    b.putParcelable("callback", i);
                    ToolManagerBroadcastReceiver.getInstance().startTool(
                            MapItemSelectTool.TOOL_NAME, b);
                }

                // Select from OM
                else if (which == 1) {
                    // display map item hierarchy to get user selections
                    Intent i = new Intent(
                            HierarchyListReceiver.MANAGE_HIERARCHY);
                    i.putExtra("hier_userselect_handler",
                            HierarchyListUserGeoFence.class.getName());
                    // set tag so on return we can associate with proper Monitor
                    i.putExtra("hier_usertag", monitor.getMapItemUid());
                    //set list of UIDs that user may select from
                    i.putStringArrayListExtra("hier_userselect_mapitems_uids",
                            new ArrayList<>(uidWhitelist));
                    AtakBroadcast.getInstance().sendBroadcast(i);
                }
            }
        });
    }

    private void toast(final String str) {
        _view.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(_context, str, Toast.LENGTH_LONG)
                        .show();
            }
        });
    }

    private final ConcurrentLinkedQueue<MonitorListener> _monitorListeners = new ConcurrentLinkedQueue<>();

    public interface MonitorListener {
        void onMonitorAdded(GeoFenceMonitor monitor);

        void onMonitorChanged(GeoFenceMonitor monitor);

        void onMonitorRemoved(String uid);
    }

    public final void addMonitorListener(MonitorListener l) {
        _monitorListeners.add(l);
    }

    public final void removeMonitorListener(MonitorListener l) {
        _monitorListeners.remove(l);
    }

    final void dispatchMonitorAdded(GeoFenceMonitor monitor) {
        for (MonitorListener l : _monitorListeners)
            l.onMonitorAdded(monitor);
    }

    final void dispatchMonitorChanged(GeoFenceMonitor monitor) {
        for (MonitorListener l : _monitorListeners)
            l.onMonitorChanged(monitor);
    }

    final void dispatchMonitorRemoved(String uid) {
        for (MonitorListener l : _monitorListeners)
            l.onMonitorRemoved(uid);
    }
}
