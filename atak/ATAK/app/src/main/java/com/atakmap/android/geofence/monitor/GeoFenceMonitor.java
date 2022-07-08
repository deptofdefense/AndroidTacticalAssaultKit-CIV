
package com.atakmap.android.geofence.monitor;

import android.widget.Toast;

import com.atakmap.android.drawing.mapItems.DrawingCircle;
import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.editableShapes.Rectangle;
import com.atakmap.android.geofence.alert.GeoFenceAlerting;
import com.atakmap.android.geofence.data.GeoFence;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.spatial.SpatialCalculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class GeoFenceMonitor {

    private static final String TAG = "GeoFenceMonitor";

    /**
     * Cache fence and its corresponding shape
     */
    GeoFence _fence;

    /**
     * Shape e.g. Circle, Rectangle, DrawingShape
     */
    protected final MapItem _item;
    protected final MapView _view;

    /**
     * List of specific items to track. If empty, then use TrackedTypes.getItems()
     */
    private List<PointMapItem> _itemsToTrack;
    private final List<String> _uidsToTrack;

    /**
     * List of dismissed UIDs. Note we remove the UID from _itemsToTrack, but the UID
     * may still be in fences MonitoredTypes.getItems()
     */
    private final Set<String> _uidsToIgnore;

    /**
     * Map item UID to last state (true=inside fence, false=outside fence)
     * Note, currently used for TriggerType.Both to see if an item entered or exited
     */
    protected final Map<String, Boolean> _uidsLastState;

    public GeoFenceMonitor(MapView view, GeoFence fence, MapItem item) {
        _fence = fence;
        _item = item;
        _view = view;
        _uidsToIgnore = new HashSet<>();
        _uidsLastState = new HashMap<>();
        _itemsToTrack = new ArrayList<>();
        _uidsToTrack = new ArrayList<>();
    }

    /**
     * Check if any tracked items have breached the Geo Fence
     *
     * @return the list of alerts for items that have breached the geofence
     */
    public List<GeoFenceAlerting.Alert> check() {
        return check(_fence.getTrigger());
    }

    /**
     * Check if any tracked items have breached the Geo Fence
     * @param trigger the trigger to be used
     *
     * @return the list of alerts for the items that have breached the geofence
     */
    protected List<GeoFenceAlerting.Alert> check(GeoFence.Trigger trigger) {
        if (!_fence.isTracking()) {
            //Log.d(TAG, "Fence not tracking: " + toString());
            return null;
        }

        return check(trigger, getItems(), true);
    }

    /**
     * Check if the item has breached the Geo Fence
     *
     * @param trigger the trigger to be checked
     * @param item the map item
     * @return true if the item has breached the geofence
     */
    boolean check(GeoFence.Trigger trigger, PointMapItem item,
            boolean bCheckPrevious) {
        if (item == null) {
            Log.w(TAG, "Cannot check null item");
            return false;
        }

        return !FileSystemUtils.isEmpty(check(trigger,
                Collections.singletonList(item), bCheckPrevious));
    }

    /**
     * Check if any tracked items have breached the Geo Fence
     * All items passed in so subclasses can optimize shape comparison math
     *
     * @param trigger the trigger to be used
     * @param items the list of items
     * @param bCheckPrevious    if true, only alert if current state differs from previous state
     * @return
     */
    protected abstract List<GeoFenceAlerting.Alert> check(
            GeoFence.Trigger trigger, List<PointMapItem> items,
            boolean bCheckPrevious);

    /**
     * Get center of monitored area
     */
    public abstract GeoPointMetaData getCenter();

    public abstract double getfurthestPointRange();

    /**
     * Subclasses should invoke this for each item to see if user has already
     * dismissed these alerts
     *
     * @param item the item
     * @return true if the items alert has been previously dismissed
     */
    protected synchronized boolean checkDismissed(PointMapItem item) {
        if (_uidsToIgnore.size() > 0 && _uidsToIgnore.contains(item.getUID())) {
            Log.d(TAG,
                    "check item point was previously dismissed: "
                            + item.getUID());
            return true;
        }

        return false;
    }

    public boolean isValid() {
        if (_fence == null || !_fence.isValid())
            return false;

        if (_item == null || FileSystemUtils.isEmpty(_item.getUID()))
            return false;

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GeoFenceMonitor))
            return super.equals(o);

        GeoFenceMonitor rhs = (GeoFenceMonitor) o;
        if (!FileSystemUtils.isEquals(_item.getUID(), rhs._item.getUID())) {
            return false;
        }

        if (!_fence.equals(rhs._fence))
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return (_fence.hashCode() + _item.getUID()).hashCode();
    }

    public void dispose() {
    }

    /**
     * Get UID of the Map item which identifies this GeoFence
     * This class caches the shape (e.g. Rectangle or Rings)
     * But the UID may be the shapes UID (rectangle) or the shape's center
     * marker UID (circle)
     * See GeoFenceReceiver.onReceive for more info
     *
     * @return the uid of the geofence.
     */
    public String getMapItemUid() {
        if (_fence == null)
            return null;

        return _fence.getMapItemUid();
    }

    public MapItem getItem() {
        return _item;
    }

    /**
     * Persist this monitor via its metadata item
     */
    public void persist() {
        MapItem item = getItem();
        if (item != null)
            item.persist(_view.getMapEventDispatcher(), null,
                    GeoFenceManager.class);
    }

    public synchronized void setUidsLastState(
            Map<String, Boolean> uidsLastState) {
        this._uidsLastState.clear();
        this._uidsLastState.putAll(uidsLastState);
    }

    public synchronized void clearUidsLastState() {
        _uidsLastState.clear();
    }

    /**
     * Special handling for Self marker b/c it doesn't have a type and does not
     * show up in the Overlay Manager for selection by user
     *
     * @param items the list of items
     */
    public synchronized void setSelectItems(List<PointMapItem> items) {
        _itemsToTrack = items;
        _uidsToIgnore.clear();
    }

    public synchronized void clearSelectItems() {
        setSelectItems(new ArrayList<PointMapItem>());
        _uidsToTrack.clear();
    }

    /**
     * List of UIDs to track that have yet to be resolved
     * @param uids UIDs to track
     */
    public synchronized void setSelectUids(List<String> uids) {
        _uidsToTrack.clear();
        _uidsToTrack.addAll(uids);
    }

    public synchronized boolean checkSelectedItem(PointMapItem item) {
        if (_uidsToTrack.remove(item.getUID())) {
            _itemsToTrack.add(item);
            return true;
        }
        return false;
    }

    public synchronized ArrayList<String> getSelectedItemUids() {
        ArrayList<String> uids = new ArrayList<>();
        for (PointMapItem item : _itemsToTrack)
            uids.add(item.getUID());
        return uids;
    }

    public synchronized boolean hasTrackedItems() {
        return !FileSystemUtils.isEmpty(_uidsToTrack)
                || !FileSystemUtils.isEmpty(_itemsToTrack);
    }

    /**
     * Get items to track/compare
     * @return an immuatable copy list (may not be modified, but will not throw
     *      ConcurrentModificationException for iteration
     */
    public synchronized List<PointMapItem> getItems() {
        //Log.d(TAG, "Tracking select item count: " + _itemsToTrack.size());
        return Collections.unmodifiableList(new ArrayList<>(
                _itemsToTrack));
    }

    public synchronized int size() {
        if (!FileSystemUtils.isEmpty(_itemsToTrack)) {
            //Log.d(TAG, "Tracking select item count: " + _itemsToTrack.size());
            return _itemsToTrack.size();
        }

        int size = _fence.getMonitoredTypes().size();
        //Log.d(TAG, "Tracking typed item count: " + size);
        return size;
    }

    @Override
    public String toString() {
        return _fence.toString();
    }

    public void setFence(GeoFence fence) {
        //Log.d(TAG, "Updated fence");
        this._fence = fence;
    }

    public GeoFence getFence() {
        return _fence;
    }

    /**
     * Remove tracking for the dismissed item
     *
     * @param item
     * @return  true if this is the last item being tracked
     */
    public synchronized boolean removeItem(PointMapItem item) {
        //mark that future alerts should be ignored
        _uidsToIgnore.add(item.getUID());
        _uidsLastState.remove(item.getUID());

        if (!FileSystemUtils.isEmpty(_itemsToTrack)) {
            int toRemove = itemsToTrackIndex(item);
            if (toRemove >= 0) {
                Log.d(TAG, "No longer tracking: " + item.getUID()
                        + " for fence: " + this);
                _itemsToTrack.remove(toRemove);
            } else {
                Log.d(TAG, "Already not explicitly tracking: " + item.getUID()
                        + ", type: " + item.getType()
                        + " for fence: " + this);
            }

            return _itemsToTrack.size() < 1;
        }

        //nothing to remove
        return false;
    }

    /**
     * Get index for item
     * this iteration necessary b/c PointMapItem does not implement .equals so
     * contains/remove operations may fail
     *
     * @param item
     * @return  -1 if not tracked, otherwise 0-based index/position in list
     */
    private synchronized int itemsToTrackIndex(PointMapItem item) {
        if (FileSystemUtils.isEmpty(_itemsToTrack))
            return -1;

        for (int i = 0; i < _itemsToTrack.size(); i++) {
            if (_itemsToTrack.get(i).getUID().equals(item.getUID())) {
                return i;
            }
        }

        return -1;
    }

    public synchronized boolean isMonitoring(PointMapItem item) {
        return itemsToTrackIndex(item) >= 0;
    }

    public synchronized boolean addItem(PointMapItem item) {
        Log.d(TAG, "Adding monitoring for item: " + item.getUID()
                + " for fence: " + this);

        int index = itemsToTrackIndex(item);
        if (index >= 0) {
            Log.d(TAG, "Item already monitored: " + item.getUID()
                    + " for fence: " + this);
        } else {
            //add to tracked list
            _itemsToTrack.add(item);
        }

        //don't ignore if previously done so
        _uidsToIgnore.remove(item.getUID());

        //check if currently inside & set initial state. For "Both" fences set opposite from
        //current state, so we alert immediately
        getLastState(item.getUID());
        return true;
    }

    /**
     * Check if we should trigger
     *
     * @param trigger
     * @param bInside
     * @return
     */
    protected static boolean checkTrigger(GeoFence.Trigger trigger,
            boolean bInside) {
        switch (trigger) {
            case Entry: {
                return bInside;
            }
            case Exit: {
                return !bInside;
            }
            case Both: {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if we should trigger. Do not re-trigger, based on previous state
     * @param trigger
     * @param bInside
     * @param bPreviouslyInside
     * @return
     */
    protected static boolean checkTrigger(GeoFence.Trigger trigger,
            boolean bInside, Boolean bPreviouslyInside) {
        switch (trigger) {
            case Entry: {
                if (bInside && !bPreviouslyInside) {
                    return true;
                }
            }
                break;
            case Exit: {
                if (!bInside && bPreviouslyInside) {
                    return true;
                }
            }
                break;
            case Both: {
                if (bInside != bPreviouslyInside) {
                    return true;
                }
            }
                break;
        }

        return false;
    }

    /**
     * Get previous state for the specified UID
     * @param uid Item UID
     */
    protected synchronized boolean getLastState(String uid) {
        Boolean bPreviouslyIInside = _uidsLastState.get(uid);
        if (bPreviouslyIInside == null) {
            // If a marker just popped up inside the geo fence
            // then it makes sense to alert the user
            // However if a marker just popped up outside the geo fence
            // then there's no reason the user should be alerted of an "exit"
            // See ATAK-10716
            // If a user wants to know about newly tracked items, then create
            // and trigger a new alert called "outside".
            // Don't call it "exit" because that's confusing, obviously.
            _uidsLastState.put(uid, false);
            return false;
        }
        return bPreviouslyIInside;
    }

    /**
     * Default impl return entire list based on MonitoredTypes
     * @return an immuatable copy list (may not be modified, but will not throw
     *      ConcurrentModificationException for iteration
     */
    public List<PointMapItem> getRescanItems() {
        return _fence.getMonitoredTypes().getItems();
    }

    protected boolean inElevationRange(GeoPoint point) {
        if (!_fence.isElevationMonitored())
            return true;
        double minElev = _fence.getMinElevation();
        double maxElev = _fence.getMaxElevation();
        if (!point.isAltitudeValid())
            return false;
        double pointElev = EGM96.getHAE(point);
        return (Double.isNaN(minElev) || minElev <= pointElev)
                && (Double.isNaN(maxElev) || pointElev <= maxElev);
    }

    /**
     * Factory to create proper monitor, based on Map Item
     * Map Item should be the reference shape (see GeoFenceReceiver.getReferenceShape)
     */
    public static class Factory {
        private static final String TAG = "GeoFenceMonitor.Factory";

        public static GeoFenceMonitor Create(MapView view, MapGroup group,
                SpatialCalculator spatialCalc, GeoFence fence, MapItem item) {
            if (fence == null || !fence.isValid()) {
                Log.w(TAG, "Unable to Create monitor for invalid fence");
                return null;
            }

            if (item == null || FileSystemUtils.isEmpty(item.getUID())) {
                Log.w(TAG, "Unable to Create monitor for invalid map item");
                return null;
            }

            MapItem shape = ATAKUtilities.findAssocShape(item);
            if (shape instanceof DrawingCircle) {
                CircleGeoFenceMonitor monitor = new CircleGeoFenceMonitor(view,
                        fence, (DrawingCircle) shape);
                if (!monitor.isValid()) {
                    Log.w(TAG, "Invalid CircleGeoFenceMonitor");
                    return monitor;
                }

                Log.d(TAG,
                        "creating CircleGeoFenceMonitor: "
                                + monitor);
                return monitor;
            } else if (shape instanceof Rectangle) {
                RectangleGeoFenceMonitor monitor = new RectangleGeoFenceMonitor(
                        view, spatialCalc, fence, (Rectangle) shape);
                if (!monitor.isValid()) {
                    Log.w(TAG, "Invalid RectangleGeoFenceMonitor");
                    return monitor;
                }

                Log.d(TAG,
                        "creating RectangleGeoFenceMonitor: "
                                + monitor);
                return monitor;
            } else if (shape instanceof DrawingShape) {
                DrawingShape freeform = (DrawingShape) shape;
                if (freeform.getNumPoints() < 3 || !freeform.isClosed()) {
                    Log.w(TAG,
                            "Unable to Create ClosedShaped monitor for non closed map item: "
                                    + item.getUID());
                    toast(view, R.string.geofence_shape_must_be_closed);
                    return null;
                }

                ClosedShapeGeoFenceMonitor monitor = new ClosedShapeGeoFenceMonitor(
                        view, spatialCalc, fence, freeform);
                if (!monitor.isValid()) {
                    Log.w(TAG, "Invalid ClosedShapeGeoFenceMonitor");
                    return monitor;
                }

                Log.d(TAG,
                        "creating ClosedShapeGeoFenceMonitor: "
                                + monitor);
                return monitor;
            } else {
                Log.w(TAG, "Unsupported fence map item: " + item.getUID()
                        + " of type: " + item.getType());
                toast(view, R.string.unsupported_geo_fence_shape);
                return null;
            }
        }
    }

    private static void toast(final MapView view, final int strId) {
        view.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(view.getContext(), strId, Toast.LENGTH_LONG)
                        .show();
            }
        });
    }
}
