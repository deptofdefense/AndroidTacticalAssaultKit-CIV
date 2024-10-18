
package com.atakmap.android.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;

import java.util.Map;
import java.util.Set;

/**
 * ATAK core preferences helper which is used to centralize all of the calls to get the shared
 * preference manager
 */
public class AtakPreferences {

    private static final String TAG = "AtakPreferences";

    protected final Context _context;
    protected final SharedPreferences _prefs;

    /**
     * Construct an ATAK core preference manager
     * @param appContext the application contrext used by the preference manager
     */
    public AtakPreferences(Context appContext) {
        _context = appContext;
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
    }

    /**
     * Construct an ATAK core preference manager
     * @param mapView the map view in order to get the application contrext
     *                used by the preference manager
     */
    public AtakPreferences(MapView mapView) {
        this(mapView.getContext());
    }

    /**
     * Get the underlying shared preferences object
     * @return Shared preferences
     */
    public SharedPreferences getSharedPrefs() {
        return _prefs;
    }

    /**
     * Set and apply a preference
     * @param key Preference key
     * @param value Preference value (any type)
     * @return True if successful, false if failed
     */
    public boolean set(final String key, final Object value) {
        if (value == null)
            return false;
        try {
            final SharedPreferences.Editor editor = _prefs.edit();
            if (value instanceof Integer)
                editor.putInt(key, (Integer) value);
            else if (value instanceof Long)
                editor.putLong(key, (Long) value);
            else if (value instanceof Double)
                editor.putFloat(key, ((Double) value).floatValue());
            else if (value instanceof Float)
                editor.putFloat(key, (Float) value);
            else if (value instanceof Boolean)
                editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Set<?>)
                editor.putStringSet(key, (Set<String>) value);
            else
                editor.putString(key, String.valueOf(value));
            editor.apply();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "set(" + key + ", " + value + ") failed", e);
        }
        return false;
    }

    /**
     * Remove a preference value from the set
     * @param key Preference key
     */
    public void remove(String key) {
        _prefs.edit().remove(key).apply();
    }

    /**
     * Check if a preference exists in the set
     * @param key Preference key
     * @return True if exists
     */
    public boolean contains(String key) {
        return _prefs.contains(key);
    }

    /**
     * Get a preference value
     * Value type is based on the default value type passed in
     * @param key Preference key
     * @param defVal Default value
     * @return Preference value or default value if failed/not found
     */
    private Object get(String key, Object defVal) {
        try {
            if (defVal instanceof Integer)
                return _prefs.getInt(key, (Integer) defVal);
            else if (defVal instanceof Long)
                return _prefs.getLong(key, (Long) defVal);
            else if (defVal instanceof Double)
                return (double) _prefs.getFloat(key,
                        ((Double) defVal).floatValue());
            else if (defVal instanceof Float)
                return _prefs.getFloat(key, (Float) defVal);
            else if (defVal instanceof Boolean)
                return _prefs.getBoolean(key, (Boolean) defVal);
            else if (defVal instanceof Set<?>)
                return _prefs.getStringSet(key, (Set<String>) defVal);
            else {
                if (defVal != null && !(defVal instanceof String))
                    defVal = String.valueOf(defVal);
                return _prefs.getString(key, (String) defVal);
            }
        } catch (Exception e) {
            // Type mismatch - Attempt to get as a string and convert
            if (!(defVal instanceof String)) {
                String ret = get(key, String.valueOf(defVal));
                try {
                    if (defVal instanceof Integer)
                        return Integer.parseInt(ret);
                    else if (defVal instanceof Long)
                        return Long.parseLong(ret);
                    else if (defVal instanceof Double)
                        return Double.parseDouble(ret);
                    else if (defVal instanceof Float)
                        return Float.parseFloat(ret);
                    else if (defVal instanceof Boolean)
                        return Boolean.parseBoolean(ret);
                    return defVal;
                } catch (Exception e2) {
                    e = e2;
                }
            }
            Log.e(TAG, "Failed to get preference string: " + key, e);
        }
        return defVal;
    }

    /**
     * Retrieve a String value from the preferences.
     *
     * @param key The name of the preference to retrieve.
     * @param defVal Value to return if this preference does not exist.
     */
    public String get(String key, String defVal) {
        return (String) get(key, (Object) defVal);
    }

    /**
     * Retrieve an int value from the preferences.
     *
     * @param key The name of the preference to retrieve.
     * @param defVal Value to return if this preference does not exist.
     */
    public int get(String key, int defVal) {
        return (Integer) get(key, (Integer) defVal);
    }

    /**
     * Retrieve a long value from the preferences.
     *
     * @param key The name of the preference to retrieve.
     * @param defVal Value to return if this preference does not exist.
     */
    public long get(String key, long defVal) {
        return (Long) get(key, (Long) defVal);
    }

    /**
     * Retrieve a double value from the preferences.
     *
     * @param key The name of the preference to retrieve.
     * @param defVal Value to return if this preference does not exist.
     */
    public double get(String key, double defVal) {
        return (Double) get(key, (Double) defVal);
    }

    /**
     * Retrieve a boolean value from the preferences.
     *
     * @param key The name of the preference to retrieve.
     * @param defVal Value to return if this preference does not exist.
     */
    public boolean get(String key, boolean defVal) {
        return (Boolean) get(key, (Boolean) defVal);
    }

    /**
     * Retrieve a string set  from the preferences
     *
     * @param key Preference key
     * @return String set or null if not found or wrong preference type
     */
    public Set<String> getStringSet(String key) {
        try {
            return _prefs.getStringSet(key, null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the map of all shared preferences
     * @return Shared preferences map
     */
    public Map<String, ?> getAll() {
        return _prefs.getAll();
    }

    /**
     * Registers a shared preference listener with the AtakPreferences.
     *
     * @param l the shared preference change listener
     */
    public void registerListener(OnSharedPreferenceChangeListener l) {
        _prefs.registerOnSharedPreferenceChangeListener(l);
    }

    /**
     * Unregisters a shared preference listener with the AtakPreferences.
     *
     * @param l the shared preference change listener
     */
    public void unregisterListener(OnSharedPreferenceChangeListener l) {
        _prefs.unregisterOnSharedPreferenceChangeListener(l);
    }
}
