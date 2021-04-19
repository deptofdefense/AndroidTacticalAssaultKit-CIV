
package com.atakmap.android.routes.routearound;

import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class RouteAroundRegionViewModel {

    private final RouteAroundRegionEventRelay relay = RouteAroundRegionEventRelay
            .getInstance();
    private RouteAroundRegionManager manager;

    private static String TAG = "RouteAroundRegionViewModel";

    // The file where we save and load the state of our views
    public static final File SERIALIZATION_FILE = FileSystemUtils
            .getItem(FileSystemUtils.ATAK_ROOT_DIRECTORY
                    + "/routeAroundRegions.json");

    /******* Public API *********/

    /** Saves the state of the manager to a file */
    public void saveState() {
        try {
            manager.saveManagerStateToFile(SERIALIZATION_FILE);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "error has occurred", e);
        }
    }

    /** Sets the state of the "route around geofences" checkbox. */
    public void setRouteAroundGeoFences(boolean value) {
        manager.routeAroundGeoFences = value;
        relay.onRouteAroundGeoFencesSet(value);
    }

    /** Adds a new region to the list of route around regions. */
    public void addRegion(Shape region) {
        manager.addRegion(region);
        relay.onRegionAdded(region);
    }

    /** Removes a region from the list of route around regions.
     *
     * @param region The region to remove from the list
     * @return Whether or not the region was successfully removed.
     *         Returns false if the specified region was not in the list.
     */
    public boolean removeRegion(Shape region) {
        if (manager.removeRegion(region)) {
            relay.onRegionRemoved(region);
            return true;
        } else {
            return false;
        }
    }

    public RouteAroundRegionViewModel(RouteAroundRegionManager manager) {
        this.manager = manager;
    }

    /******* Getters for view state *******/

    public boolean isLoaded() {
        return RouteAroundRegionManager.getIsLoaded();
    }

    public boolean getRouteAroundGeoFences() {
        return manager.routeAroundGeoFences;
    }

    public List<Shape> getRegions() {
        return manager.getRegions();
    }

    /** Load the serialized state from a file, and initialize the view. */
    void loadState() {
        try {
            manager.restoreManagerStateFromFile(SERIALIZATION_FILE);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "error encountered", e);
        }
        initalizeViews();
    }

    /** Set the view state to the state of the model. */
    void initalizeViews() {
        setRouteAroundGeoFences(manager.routeAroundGeoFences);
        Lock lock = new ReentrantLock();
        lock.lock();
        List<Shape> regions = manager.getRegions();
        lock.unlock();
        for (Shape region : regions) {
            relay.onRegionAdded(region);
        }
    }
}
