
package com.atakmap.app.preferences.json;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.preferences.PreferenceControl;
import com.atakmap.comms.TAKServerSerializer;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cleaner and extendable version of {@link PreferenceControl} that uses
 * JSON instead of XML
 */
public class JSONPreferenceControl {

    private static final String TAG = "JSONPreferenceControl";

    public static final String PREFERENCE_CONTROL = "PreferenceControl";
    public static final int VERSION = 1;

    // Root object has been modified during load and should be saved
    // Used for removing sensitive information from server configs and preferences
    public static final String SAVE_MODIFIED = "__saveModified";

    private static final JSONPreferenceControl instance = new JSONPreferenceControl();

    public static JSONPreferenceControl getInstance() {
        return instance;
    }

    private final Set<JSONPreferenceSerializer> _serializers = new HashSet<>();

    public void initDefaults(MapView mapView) {
        addSerializer(new SharedPreferencesSerializer(mapView.getContext()));
        addSerializer(new TAKServerSerializer());
    }

    public void addSerializer(JSONPreferenceSerializer s) {
        synchronized (_serializers) {
            _serializers.add(s);
        }
    }

    public void removeSerializer(JSONPreferenceSerializer s) {
        synchronized (_serializers) {
            _serializers.remove(s);
        }
    }

    public List<JSONPreferenceSerializer> getSerializers() {
        synchronized (_serializers) {
            return new ArrayList<>(_serializers);
        }
    }

    /**
     * Save preferences to file
     * @param file File to save to
     * @param serializer Serializer to use (null to use all registered)
     * @return True if successful, false if failed
     */
    public boolean save(File file, JSONPreferenceSerializer serializer) {

        // Write preferences to JSON object
        JSONObject root = new JSONObject();
        try {
            root.put("name", PREFERENCE_CONTROL);
            root.put("version", VERSION);
            if (serializer == null) {
                List<JSONPreferenceSerializer> serializers = getSerializers();
                for (JSONPreferenceSerializer s : serializers)
                    s.writeJSON(root);
            } else
                serializer.writeJSON(root);
        } catch (Exception e) {
            Log.e(TAG, "Failed to serialize preferences", e);
            return false;
        }

        // Write JSON object to file
        return writeJSONFile(file, root);
    }

    /**
     * Save all preferences to a file
     * @param file File to save to
     * @return True if successful, false if not
     */
    public boolean save(File file) {
        return save(file, null);
    }

    /**
     * Load preferences using a specific serializer
     * @param file Preferences file
     * @param serializer Preference reader (null to run through all)
     * @param readOnly Do not allow modification of the preferences here
     * @return True if handled
     */
    public boolean load(File file, JSONPreferenceSerializer serializer,
            boolean readOnly) {

        // Read preferences from file
        JSONObject root;
        try {
            root = new JSONObject(new String(FileSystemUtils.read(file),
                    FileSystemUtils.UTF8_CHARSET));
        } catch (Exception e) {
            Log.e(TAG, "Failed to read preferences from file: " + file, e);
            return false;
        }

        // Load from JSON
        if (serializer == null) {
            List<JSONPreferenceSerializer> serializers = getSerializers();
            for (JSONPreferenceSerializer s : serializers)
                s.readJSON(root);
        } else
            serializer.readJSON(root);

        // Re-save preferences, if we should
        try {
            if (!readOnly && root.has(SAVE_MODIFIED)
                    && root.getBoolean(SAVE_MODIFIED))
                writeJSONFile(file, root);
        } catch (Exception ignore) {
        }

        return true;
    }

    public boolean load(File file, JSONPreferenceSerializer serializer) {
        return load(file, serializer, true);
    }

    public boolean load(File file, boolean readOnly) {
        return load(file, null, readOnly);
    }

    public boolean load(File file) {
        return load(file, null);
    }

    public static boolean writeJSONFile(File file, JSONObject root) {
        File dir = file.getParentFile();
        if (!IOProviderFactory.exists(dir) && !IOProviderFactory.mkdirs(dir)) {
            Log.d(TAG, "Failed to create directory: " + dir);
            return false;
        }
        try (FileWriter fw = IOProviderFactory.getFileWriter(file)) {
            fw.write(root.toString(2));
        } catch (Exception e) {
            Log.e(TAG, "Failed to write preferences to file: " + file, e);
            return false;
        }
        return true;
    }
}
