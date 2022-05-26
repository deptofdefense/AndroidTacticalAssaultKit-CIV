
package com.atakmap.android.vehicle.model;

import com.atakmap.android.math.MathUtils;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.math.PointD;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.atakmap.android.vehicle.model.VehicleModelAssetUtils;

/**
 * Cache vehicle model data for re-use across multiple instances
 */
public class VehicleModelCache {

    private static final String TAG = "VehicleModelCache";

    // ATAK tools directory
    private static final File TOOLS = FileSystemUtils.getItem("tools");

    private static final String ASSET_DIR = "vehicle_models";

    // The main vehicle models directory (on the filesystem)
    public static final File DIR = new File(TOOLS, ASSET_DIR);

    // Cached vehicle thumbnails
    public static final File ICON_DIR = new File(DIR, ".iconcache");

    private static final Comparator<String> COMP_CATEGORY = new Comparator<String>() {
        @Override
        public int compare(String o1, String o2) {
            return o1.compareTo(o2);
        }
    };

    private static final VehicleModelCache _instance = new VehicleModelCache();

    public static VehicleModelCache getInstance() {
        return _instance;
    }

    // Vehicle category+name -> vehicle model cache
    private final Map<String, VehicleModelInfo> _cache = new HashMap<>();

    // Vehicle category -> list of vehicles in that category
    private final Map<String, List<VehicleModelInfo>> _categories = new HashMap<>();

    // Vehicle category+name -> list of usages
    private final Map<String, Set<String>> _usages = new HashMap<>();

    /**
     * Get specific vehicle model based on the vehicle category and type
     * @param category Vehicle category
     * @param vehicle Vehicle type (i.e. C-130)
     * @return Vehicle model data
     */
    public VehicleModelInfo get(String category, String vehicle) {
        synchronized (_cache) {
            return _cache.get(category + "/" + vehicle);
        }
    }

    /**
     * Get all vehicle model data
     * @return Vehicle model data
     */
    public List<VehicleModelInfo> getAll() {
        synchronized (_cache) {
            return new ArrayList<>(_cache.values());
        }
    }

    /**
     * Get all vehicle model data for a given category
     * @param category Vehicle category
     * @return List of vehicle model data
     */
    public List<VehicleModelInfo> getAll(String category) {
        List<VehicleModelInfo> models;
        synchronized (_cache) {
            models = _categories.get(category);
        }
        if (models == null)
            return new ArrayList<>();
        models = new ArrayList<>(models);
        Collections.sort(models, VehicleModelInfo.COMP_NAME);
        return models;
    }

    /**
     * Get a list of all vehicle categories sorted alphabetically
     * @return List of vehicle categories
     */
    public List<String> getCategories() {
        List<String> categories;
        synchronized (_cache) {
            categories = new ArrayList<>(_categories.keySet());
        }
        Collections.sort(categories, COMP_CATEGORY);
        return categories;
    }

    /**
     * Add a vehicle to the cache (typically used by plugins)
     * @param vehicle Vehicle info to add
     */
    public void addVehicle(VehicleModelInfo vehicle) {
        synchronized (_cache) {
            addVehicleNoSync(vehicle);
        }
    }

    private void addVehicleNoSync(VehicleModelInfo c) {
        _cache.put(c.getUID(), c);
        List<VehicleModelInfo> vehicles = _categories.get(c.category);
        if (vehicles == null)
            _categories.put(c.category, vehicles = new ArrayList<>());
        vehicles.add(c);
    }

    /**
     * Remove vehicle from the cache (typically used by plugins)
     * @param vehicle Vehicle info to remove
     */
    public void removeVehicle(VehicleModelInfo vehicle) {
        synchronized (_cache) {
            removeVehicleNoSync(vehicle);
        }
    }

    private void removeVehicleNoSync(VehicleModelInfo c) {
        _cache.remove(c.getUID());
        List<VehicleModelInfo> vehicles = _categories.get(c.category);
        if (vehicles != null) {
            vehicles.remove(c);
            if (vehicles.isEmpty())
                _categories.remove(c.category);
        }
    }

    /**
     * Register usage of a specific vehicle model
     * @param info Vehicle model info
     * @param uid The UID of the object using it
     * @return True if this the first registered of this model (since being empty)
     */
    public boolean registerUsage(VehicleModelInfo info, String uid) {
        boolean firstUse = false;
        synchronized (_cache) {
            String key = info.getUID();
            Set<String> uids = _usages.get(key);
            if (uids == null) {
                _usages.put(key, uids = new HashSet<>());
                firstUse = true;
            }
            uids.add(uid);
        }
        return firstUse;
    }

    /**
     * Unregister usage of a specific vehicle model
     * @param info Vehicle model info
     * @param uid UID of the object using it
     * @return True if this was the last usage of this model
     */
    public boolean unregisterUsage(VehicleModelInfo info, String uid) {
        boolean lastUsage = false;
        synchronized (_cache) {
            String key = info.getUID();
            Set<String> uids = _usages.get(key);
            if (uids != null) {
                uids.remove(uid);
                if (uids.isEmpty()) {
                    _usages.remove(key);
                    info.dispose();
                    lastUsage = true;
                }
            }
        }
        return lastUsage;
    }

    public void dispose() {
        synchronized (_cache) {
            for (VehicleModelInfo info : _cache.values()) {
                info.dispose();
            }
        }
        _categories.clear();
        _cache.clear();
        _usages.clear();
    }

    /**
     * Rescan the atak/tools/vehicles/models directory
     */
    public void rescan() {

        // Make sure the tools dir exists first
        if (!IOProviderFactory.exists(DIR) && !IOProviderFactory.mkdirs(DIR)) {
            Log.e(TAG, "Failed to make vehicle models directory: " + DIR);
            return;
        }

        // Read version
        File verFile = new File(DIR, "version.txt");
        int curVersion = 0;
        if (IOProviderFactory.exists(verFile))
            curVersion = MathUtils.parseInt(
                    VehicleModelAssetUtils.readFileString(verFile, false), 0);
        int newVersion = MathUtils.parseInt(
                VehicleModelAssetUtils.readFileString(verFile, true), 0);

        // Root metadata file
        File metaFile = new File(DIR, "metadata.json");

        // Check if we need to update files
        boolean updating = curVersion != newVersion;
        if (updating) {
            Log.d(TAG, "Vehicle models need an update (v" + curVersion
                    + " != v" + newVersion + ")");
            if (!VehicleModelAssetUtils.copyAssetToFile(metaFile))
                return;

            // Delete icon cache if we're updating
            if (ICON_DIR.exists())
                FileSystemUtils.delete(ICON_DIR);
        }

        // Read categories we need to load from
        List<VehicleModelInfo> vehicles = new ArrayList<>();
        JSONObject rootMeta = VehicleModelAssetUtils.readFileJSON(metaFile,
                false);
        try {
            JSONArray categories = rootMeta.getJSONArray("categories");
            for (int i = 0; i < categories.length(); i++) {
                JSONObject o = categories.getJSONObject(i);
                String dirName = o.getString("directory");
                vehicles.addAll(readVehicles(dirName, updating));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read metadata", e);
            return;
        }

        // Now that we've finished reading everything properly, update version
        if (updating)
            VehicleModelAssetUtils.copyAssetToFile(verFile);

        // Store vehicles in the cache
        synchronized (_cache) {
            for (VehicleModelInfo c : vehicles)
                addVehicleNoSync(c);
        }

        // Finished
        Log.d(TAG, "Finished reading " + vehicles.size()
                + " vehicle model refs into the cache");
    }

    /**
     * Read vehicles from a given directory
     * @param dirName Directory name
     * @param updating True if the metadata needs to be updated from the assets
     * @return List of vehicle info
     * @throws JSONException Failed to find JSON element
     */
    private List<VehicleModelInfo> readVehicles(String dirName,
            boolean updating)
            throws JSONException {

        // Get vehicle directory
        List<VehicleModelInfo> ret = new ArrayList<>();
        File dir = new File(DIR, dirName);
        if (!IOProviderFactory.exists(dir) && !IOProviderFactory.mkdirs(dir)) {
            Log.e(TAG, "Failed to find category directory: " + dir);
            return ret;
        }

        // Category metadata
        File catFile = new File(dir, "metadata.json");
        if (updating)
            VehicleModelAssetUtils.copyAssetToFile(catFile);
        if (!IOProviderFactory.exists(catFile)) {
            Log.e(TAG, "Category file does not exist in directory: " + catFile);
            return ret;
        }

        // Generate vehicle info
        JSONObject catMeta = VehicleModelAssetUtils.readFileJSON(catFile,
                false);
        if (catMeta == null) {
            Log.e(TAG, "Failed to read category file: " + catFile);
            return ret;
        }

        String catName = catMeta.getString("category");
        JSONArray vehicles = catMeta.getJSONArray("vehicles");
        for (int i = 0; i < vehicles.length(); i++) {
            JSONObject vehicle = vehicles.getJSONObject(i);
            String name = vehicle.getString("name");
            String path = vehicle.getString("file");
            File vehFile = new File(dir, path);
            if (updating && vehFile.exists()) {
                // File is no longer up to date - delete so we can load from
                // assets later
                FileSystemUtils.delete(vehFile);
            }

            // Optional correction offset
            PointD offset = null;
            if (vehicle.has("offset")) {
                JSONArray arr = vehicle.getJSONArray("offset");
                offset = new PointD(arr.getDouble(0), arr.getDouble(1),
                        arr.getDouble(2));
            }

            ret.add(new VehicleModelInfo(catName, name, vehFile, offset));
        }

        return ret;
    }
}
