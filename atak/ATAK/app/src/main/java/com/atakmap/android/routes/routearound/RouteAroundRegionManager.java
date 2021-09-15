
package com.atakmap.android.routes.routearound;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Shape;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** Manages the state of the list of regions that a routing
 * engine will attempt to route around
 */
public class RouteAroundRegionManager {
    private static final String TAG = "RouteAroundRegionManager";
    private static RouteAroundRegionManager _instance = null;
    // Note: This is not ideal, because there is not a sub-class of
    // something like "ClosedRegion"
    private ArrayList<Shape> regions = new ArrayList<>();
    private static boolean _isLoaded = false;
    boolean routeAroundGeoFences = false;

    public static boolean getIsLoaded() {
        return _isLoaded;
    }

    public static RouteAroundRegionManager getInstance() {
        if (_instance == null) {
            _instance = new RouteAroundRegionManager();
        }
        return _instance;
    }

    private RouteAroundRegionManager() {
    }

    /**
     * Adds a region to the manager if it is not
     * already in the list of regions to avoid.
     */
    public void addRegion(Shape region) {
        Lock lock = new ReentrantLock();
        lock.lock();
        regions.add(region);
        lock.unlock();
    }

    /**
     * Attempts to remove the given region.
     *
     *  Returns a boolean result to
     *  indicate whether or not the result
     *  was actually removed or not
     */
    public boolean removeRegion(Shape region) {
        return regions.remove(region);
    }

    /**
     * Get the list of regions we are
     * avoiding */
    public ArrayList<Shape> getRegions() {
        return regions;
    }

    RegionManagerState getRegionManagerState() {
        List<String> regionUids = new ArrayList<>();
        for (Shape region : regions) {
            regionUids.add(region.getUID());
        }
        return new RegionManagerState(routeAroundGeoFences, regionUids);
    }

    private void restoreManagerFromState(RegionManagerState state) {
        Lock lock = new ReentrantLock();
        lock.lock();
        routeAroundGeoFences = state.routeAroundGeoFences;
        regions = new ArrayList<>();
        for (String uid : state.regionUids) {
            MapItem mapItem = MapView.getMapView().getMapItem(uid);
            if (mapItem != null)
                regions.add((Shape) mapItem);
        }
        lock.unlock();
    }

    /** Data class representing the state of the region manager. */
    public static class RegionManagerState {
        public boolean routeAroundGeoFences;
        public List<String> regionUids;

        public RegionManagerState(boolean routeAroundGeoFences,
                List<String> regionUids) {
            this.routeAroundGeoFences = routeAroundGeoFences;
            this.regionUids = regionUids;
        }
    }

    /** Restores the manager state from a file.
     *
     * @param f The file to read from when restoring the manager's state
     */
    public void restoreManagerStateFromFile(File f)
            throws IOException, JSONException {
        if (!IOProviderFactory.exists(f)) {
            return;
        }
        // Read a string from the file
        try (Reader r = IOProviderFactory
                .getFileReader(new File(f.getAbsolutePath()));
                BufferedReader reader = new BufferedReader(r)) {
            StringBuilder stringBuilder = new StringBuilder();
            char[] buffer = new char[10];
            while (reader.read(buffer) != -1) {
                stringBuilder.append(new String(buffer));
                buffer = new char[10];
            }
            String rawDoc = stringBuilder.toString();

            JSONObject doc = (JSONObject) new JSONTokener(rawDoc).nextValue();
            JSONArray regionUidsArray = doc.getJSONArray("regionUids");
            boolean routeAroundGeoFences = doc
                    .getBoolean("routeAroundGeoFences");

            ArrayList<String> regionUids = new ArrayList<>();

            for (int i = 0; i < regionUidsArray.length(); i++) {
                String entry = (String) regionUidsArray.get(i);
                regionUids.add(entry);
            }
            RegionManagerState state = new RegionManagerState(
                    routeAroundGeoFences,
                    regionUids);
            restoreManagerFromState(state);
            _isLoaded = true;
        }
    }

    /** Saves the manager state by serializing it to a file
     *
     * @param f The file to save the manager state to.
     */
    public void saveManagerStateToFile(final File f)
            throws IOException, JSONException {
        if (!IOProviderFactory.exists(f)) {
            if (f.getParentFile() != null
                    && !IOProviderFactory.exists(f.getParentFile())) {
                if (!IOProviderFactory.mkdirs(f.getParentFile())) {
                    Log.e(TAG,
                            "could not create directory: " + f.getParentFile());
                }
            }
        }
        JSONObject doc = new JSONObject();
        RegionManagerState state = getRegionManagerState();
        doc.put("routeAroundGeoFences", state.routeAroundGeoFences);
        doc.put("regionUids", new JSONArray(state.regionUids));
        String serializedState = doc.toString();

        // Write the serialized state to a string.
        try (BufferedWriter out = new BufferedWriter(
                IOProviderFactory
                        .getFileWriter(new File(f.getAbsolutePath())))) {
            out.write(serializedState);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }
}
