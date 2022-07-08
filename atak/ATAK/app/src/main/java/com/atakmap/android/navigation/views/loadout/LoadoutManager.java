
package com.atakmap.android.navigation.views.loadout;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

import com.atakmap.android.navigation.models.LoadoutItemModel;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Manages the list of tool loadouts (the tool buttons in the top-left)
 */
public class LoadoutManager implements OnSharedPreferenceChangeListener {

    public static final String PREF_SELECTED_LOADOUT = "selected_loadout_key";
    public static final String DEFAULT_LOADOUT_UID = "default_loadout_key";
    public static final String LOADOUT_PREFIX = "loadout_entry_key_";

    private static LoadoutManager _instance;

    public static LoadoutManager getInstance() {
        return _instance;
    }

    /**
     * Loadout event listener
     */
    public interface OnLoadoutChangedListener {

        /**
         * A new loadout has been added
         * @param loadout Loadout
         */
        void onLoadoutAdded(LoadoutItemModel loadout);

        /**
         * An existing loadout has been modified
         * @param loadout Loadout
         */
        void onLoadoutModified(LoadoutItemModel loadout);

        /**
         * A loadout has been removed
         * @param loadout Loadout
         */
        void onLoadoutRemoved(LoadoutItemModel loadout);

        /**
         * A loadout has been selected
         * @param loadout Loadout
         */
        void onLoadoutSelected(LoadoutItemModel loadout);
    }

    private final Context _context;
    private final AtakPreferences _prefs;
    private final Map<String, LoadoutItemModel> _loadouts = new HashMap<>();
    private final ConcurrentLinkedQueue<OnLoadoutChangedListener> _listeners = new ConcurrentLinkedQueue<>();
    private final Set<String> _prefEditing = new HashSet<>();

    private LoadoutItemModel _current;

    public LoadoutManager(Context appContext) {
        _context = appContext;
        _prefs = new AtakPreferences(appContext);
        _prefs.registerListener(this);

        readLoadouts();

        if (_instance == null)
            _instance = this;
    }

    /**
     * Get the currently active loadout
     * @return Current loadout
     */
    public LoadoutItemModel getCurrentLoadout() {
        return _current;
    }

    /**
     * Set the currently selected loadout
     * @param loadout Loadout to select
     */
    public void setCurrentLoadout(LoadoutItemModel loadout) {
        if (loadout != null && _current != loadout) {
            _current = loadout;
            _prefs.set(PREF_SELECTED_LOADOUT, loadout.getUID());
            for (OnLoadoutChangedListener l : _listeners)
                l.onLoadoutSelected(loadout);
        }
    }

    /**
     * Set the currently selected loadout
     * @param uid Loadout UID (falls back to default if not found)
     */
    public void setCurrentLoadout(String uid) {
        LoadoutItemModel loadout = getLoadout(uid);
        if (loadout == null)
            loadout = getLoadout(DEFAULT_LOADOUT_UID);
        setCurrentLoadout(loadout);
    }

    /**
     * Add a loadout to the list of available
     * @param loadout Loadout to add
     */
    public void addLoadout(LoadoutItemModel loadout) {
        if (loadout == null)
            return;
        synchronized (_loadouts) {
            _loadouts.put(loadout.getUID(), loadout);
        }
        for (OnLoadoutChangedListener l : _listeners)
            l.onLoadoutAdded(loadout);
        persistLoadout(loadout, false);
    }

    /**
     * Remove a loadout from the list of available
     * @param uid Loadout UID
     */
    public void removeLoadout(String uid) {
        // Not allowed to remove the default
        if (DEFAULT_LOADOUT_UID.equals(uid))
            return;

        LoadoutItemModel removed;
        synchronized (_loadouts) {
            removed = _loadouts.remove(uid);
        }
        if (removed != null) {
            setLoadoutPref(removed, true);
            for (OnLoadoutChangedListener l : _listeners)
                l.onLoadoutRemoved(removed);

            // Switch to default if the current loadout has been removed
            if (_current == removed)
                setCurrentLoadout(DEFAULT_LOADOUT_UID);
        }
    }

    /**
     * Remove a loadout from the list of available
     * @param loadout Loadout to remove
     */
    public void removeLoadout(LoadoutItemModel loadout) {
        removeLoadout(loadout != null ? loadout.getUID() : null);
    }

    /**
     * Get a loadout given its UID
     * @param uid Loadout UID
     * @return Loadout or null if not found
     */
    public LoadoutItemModel getLoadout(String uid) {
        synchronized (_loadouts) {
            LoadoutItemModel model = _loadouts.get(uid);
            if (model == null && DEFAULT_LOADOUT_UID.equals(uid))
                _loadouts.put(DEFAULT_LOADOUT_UID,
                        model = createDefaultLoadout());
            return model;
        }
    }

    /**
     * Get the list of available loadouts
     * @return List of loadouts
     */
    public List<LoadoutItemModel> getLoadouts() {
        synchronized (_loadouts) {
            return new ArrayList<>(_loadouts.values());
        }
    }

    /**
     * Persists a loadout to the shared preferences and notifies modification
     * @param loadout Loadout to persist
     * @param notify True to notify listeners of a change
     */
    public void persistLoadout(LoadoutItemModel loadout, boolean notify) {
        if (loadout == null || loadout.isTemporary())
            return;
        setLoadoutPref(loadout, false);
        if (notify)
            notifyLoadoutChanged(loadout);
    }

    /**
     * Notify listeners a loadout has been modified
     * @param loadout Loadout that has been modified
     */
    public void notifyLoadoutChanged(LoadoutItemModel loadout) {
        for (OnLoadoutChangedListener l : _listeners)
            l.onLoadoutModified(loadout);
    }

    /**
     * Add a listener to the manager
     * @param l Loadout listener
     */
    public void addListener(OnLoadoutChangedListener l) {
        _listeners.add(l);
    }

    /**
     * Remove a listener from the manager
     * @param l Loadout listener
     */
    public void removeListener(OnLoadoutChangedListener l) {
        _listeners.remove(l);
    }

    /**
     * Serialize a loadout to preferences XML
     * @param loadout Loadout to serialize
     * @return Preferences XML
     */
    public String serializeToXML(LoadoutItemModel loadout) {

        final StringBuilder sb = new StringBuilder(
                "<?xml version='1.0' standalone='yes'?>\n");
        sb.append("<preferences>\n");
        sb.append("<preference version=\"1\" name=\"")
                .append(_context.getPackageName())
                .append("_preferences\">\n");

        String key = LOADOUT_PREFIX + loadout.getUID();

        // Set values
        sb.append("<entry key=\"");
        sb.append(key);
        sb.append("\" class=\"class java.util.Set\">\n");
        for (String str : loadout.toStringSet())
            sb.append("<element>").append(str).append("</element>\n");
        sb.append("</entry>\n");

        sb.append("</preference>\n");
        sb.append("</preferences>\n");

        return sb.toString();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs,
            final String key) {

        if (key == null)
            return;

        if (key.equals(PREF_SELECTED_LOADOUT)) {
            setCurrentLoadout(_prefs.get(key, DEFAULT_LOADOUT_UID));
        } else if (key.startsWith(LOADOUT_PREFIX)) {

            // Check if this change is in response to a local edit
            synchronized (_prefEditing) {
                if (_prefEditing.contains(key))
                    return;
            }

            // A loadout has been modified or removed outside of the manager

            String uid = key.substring(LOADOUT_PREFIX.length());

            // Read the loadout from the preferences
            LoadoutItemModel loadout = LoadoutItemModel.fromStringSet(
                    _prefs.getStringSet(key));

            // Get the cached loadout if one exists
            LoadoutItemModel existing = getLoadout(uid);

            if (loadout != null) {
                if (existing != null) {
                    // Loadout has been modified - copy to cached instance
                    existing.copy(loadout);
                    notifyLoadoutChanged(existing);
                } else {
                    // Loadout has been added
                    addLoadout(loadout);
                }
            } else if (existing != null) {
                // A loadout has been removed
                removeLoadout(existing);
            }
        }
    }

    /**
     * Persist or remove a loadout to/from preferences
     * @param loadout Loadout
     * @param remove True to remove the loadout
     *               False to persist it
     */
    private void setLoadoutPref(LoadoutItemModel loadout, boolean remove) {
        // We need to flag that this loadout is being edited by the manager
        // in order to distinguish between local and outside edits to the
        // preferences
        synchronized (_prefEditing) {
            String key = LOADOUT_PREFIX + loadout.getUID();
            _prefEditing.add(key);
            if (remove)
                _prefs.remove(key);
            else
                _prefs.set(key, loadout.toStringSet());
            _prefEditing.remove(key);
        }
    }

    /**
     * Create the default loadout
     * @return Default loadout
     */
    private LoadoutItemModel createDefaultLoadout() {
        LoadoutItemModel def = new LoadoutItemModel(
                DEFAULT_LOADOUT_UID,
                _context.getString(R.string.default_string));
        def.setButton(0, "bloodhound");
        def.setButton(1, "redx");
        def.setButton(2, "pointdropper");
        def.setButton(3, "mapsandfavorites");
        def.setButton(4, "overlay");
        def.showZoomButton(true);
        return def;
    }

    /**
     * Read loadouts from shared preferneces
     */
    private void readLoadouts() {
        synchronized (_loadouts) {
            for (Map.Entry<String, ?> entry : _prefs.getAll().entrySet()) {
                if (!entry.getKey().startsWith(LOADOUT_PREFIX))
                    continue;

                Object value = entry.getValue();
                if (!(value instanceof Set))
                    continue;

                Set<String> values = (Set<String>) value;
                LoadoutItemModel loadout = LoadoutItemModel
                        .fromStringSet(values);
                if (loadout != null)
                    _loadouts.put(loadout.getUID(), loadout);
            }
            // If there are no loadouts present then create the default
            if (_loadouts.isEmpty())
                addLoadout(createDefaultLoadout());
        }
        setCurrentLoadout(
                _prefs.get(PREF_SELECTED_LOADOUT, DEFAULT_LOADOUT_UID));
    }
}
