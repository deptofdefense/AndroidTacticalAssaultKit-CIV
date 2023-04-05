
package com.atakmap.android.geofence.data;

import com.atakmap.android.geofence.monitor.GeoFenceManager;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages GeoFence database
 */
public class GeoFenceDatabase {

    private static final String TAG = "GeoFenceDatabase";

    private static GeoFenceDatabase instance;

    synchronized public static GeoFenceDatabase instance() {
        if (instance == null) {
            instance = new GeoFenceDatabase();
        }
        return instance;
    }

    private GeoFenceDatabase() {
        Log.d(TAG, "creating instance");
    }

    public synchronized int getCount() {
        return fenceList.size();
    }

    public synchronized List<GeoFence> getGeoFences(GeoFenceManager manager) {

        for (GeoFence f : fenceList) {
            f.setTracking(manager.isTracking(f));
        }
        Log.d(TAG, "Loaded geofences " + fenceList.size());
        return new ArrayList<>(fenceList);
    }

    public enum InsertOrUpdateResult {
        Insert,
        Updated,
        Failure,
        AlreadyUpToDate
    }

    /** 
     * Returns true if the GeoFenceDatabase contains the fence.
     * @param uid is the unique identifier that describes both the item and the 
     * contained fence.
     */
    public synchronized boolean hasFence(String uid) {
        if (FileSystemUtils.isEmpty(uid)) {
            Log.w(TAG, "Unable to get geofence without UID");
            return false;
        }

        //Log.d(TAG, "looking for: " + uid);
        for (GeoFence f : fenceList) {
            //Log.d(TAG, "found fence: " + f);
            if (FileSystemUtils.isEquals(f.getMapItemUid(), uid)) {
                return true;
            }
        }
        return false;
    }

    public synchronized GeoFence getGeoFence(String uid, boolean isTracking) {
        if (FileSystemUtils.isEmpty(uid)) {
            Log.w(TAG, "Unable to get geofence without UID");
            return null;
        }

        GeoFence ret = null;
        //Log.d(TAG, "looking for: " + uid);
        for (GeoFence f : fenceList) {
            //Log.d(TAG, "found fence: " + f);
            if (FileSystemUtils.isEquals(f.getMapItemUid(), uid)) {
                ret = f;
                ret.setTracking(isTracking);
            }
        }
        if (ret != null) {
            Log.d(TAG, "Loaded geofence: " + ret);
            return ret;
        }

        Log.d(TAG, "Geofence does not exist: " + uid);
        return null;

    }

    /**
     * Insert or update Geo Fence in DB
     * @param fence the geofence to insert or update
     * @return the result of the insert or update
     */
    public synchronized InsertOrUpdateResult insertOrUpdate(GeoFence fence) {
        if (fence == null || !fence.isValid()) {
            Log.w(TAG, "cannot insert invalid fence");
            return InsertOrUpdateResult.Failure;
        }

        GeoFence oldFence = null;
        for (GeoFence f : fenceList) {
            if (FileSystemUtils.isEquals(f.getMapItemUid(),
                    fence.getMapItemUid())) {
                oldFence = f;
            }
        }

        if (oldFence != null && oldFence.equals(fence)) {
            Log.d(TAG, "duplicative fence, do not actually remove and add it: "
                    + fence);
            return InsertOrUpdateResult.AlreadyUpToDate;
        }
        if (oldFence != null) {
            Log.d(TAG, "updated: " + fence);
            fenceList.remove(oldFence);
            fenceList.add(fence);
            return InsertOrUpdateResult.Updated;
        } else {
            Log.d(TAG, "add: " + fence);
            fenceList.add(fence);
            return InsertOrUpdateResult.Insert;
        }
    }

    public synchronized void remove(final String uid) {
        Log.d(TAG, "removing Geofence: " + uid);

        MapItem item = MapView.getMapView().getRootGroup().deepFindUID(uid);

        if (item != null) {
            Log.d(TAG, "scrubbing metadata for: " + uid);
            item.removeMetaData(GeoFenceConstants.MARKER_ELEVATION_MONITORED);
            item.removeMetaData(GeoFenceConstants.MARKER_TRIGGER);
            item.removeMetaData(GeoFenceConstants.MARKER_MONITOR);
            item.removeMetaData(GeoFenceConstants.MARKER_BOUNDING_SPHERE);
            item.removeMetaData(GeoFenceConstants.MARKER_ELEVATION_MIN);
            item.removeMetaData(GeoFenceConstants.MARKER_ELEVATION_MAX);
            item.removeMetaData(GeoFenceConstants.MARKER_MONITOR_UIDS);
        } else {
            Log.d(TAG, "item not found: " + uid);
        }

        GeoFence toRemove = null;
        for (GeoFence fence : fenceList) {
            if (fence.getMapItemUid().equals(uid)) {
                Log.d(TAG, "removing from the fence list: " + uid);
                toRemove = fence;
            }
        }
        if (toRemove != null)
            fenceList.remove(toRemove);
    }

    public synchronized void clearAll() {
        fenceList.clear();
    }

    private final List<GeoFence> fenceList = new ArrayList<>();
}
