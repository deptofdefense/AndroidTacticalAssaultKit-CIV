
package com.atakmap.app.preferences.json;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.net.AtakAuthenticationDatabase;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Handles generic shared preferences
 */
public class SharedPreferencesSerializer implements JSONPreferenceSerializer {

    private static final String TAG = "SharedPreferencesSerializer";
    private static final String PREFS_KEY = "sharedPreferences";

    private static final Set<String> PREF_BLACKLIST = new HashSet<>();

    static {
        PREF_BLACKLIST.add("locationCallsign");
        PREF_BLACKLIST.add("bestDeviceUID");
        try {
            PREF_BLACKLIST.add(Base64.encodeToString(
                    AtakAuthenticationDatabase.TAG
                            .getBytes(FileSystemUtils.UTF8_CHARSET),
                    Base64.NO_WRAP));
        } catch (Exception ignored) {
        }
    }

    private final SharedPreferences _prefs;

    public SharedPreferencesSerializer(SharedPreferences prefs) {
        _prefs = prefs;
    }

    public SharedPreferencesSerializer(Context appContext) {
        this(PreferenceManager.getDefaultSharedPreferences(appContext));
    }

    @Override
    public boolean writeJSON(JSONObject root) {
        try {
            JSONObject prefs = root.has(PREFS_KEY)
                    ? root.getJSONObject(PREFS_KEY)
                    : new JSONObject();
            Map<String, ?> pairs = _prefs.getAll();
            for (Map.Entry<String, ?> e : pairs.entrySet()) {
                String key = e.getKey();
                if (PREF_BLACKLIST.contains(key))
                    continue;

                // XXX - One of the WORST issues with shared preferences is
                // that it craps itself when you try to read/write a
                // preference as a type different from the existing
                // value (i.e. read an integer as a long), so we need to
                // remember the type for every damn value so Android doesn't
                // panic and crash when we try to cast an integer to a float...
                Object v = e.getValue();
                JSONObject jo = new JSONObject();
                jo.put("type", v.getClass().getSimpleName()
                        .toLowerCase(LocaleUtil.getCurrent()));
                jo.put("value", v);
                prefs.put(key, jo);
            }
            root.put(PREFS_KEY, prefs);
        } catch (Exception e) {
            Log.e(TAG, "Failed to write shared preferences JSON", e);
            return false;
        }
        return true;
    }

    @Override
    public boolean readJSON(JSONObject root) {
        if (!root.has(PREFS_KEY))
            return false;

        try {
            JSONObject prefs = root.getJSONObject(PREFS_KEY);
            Iterator<String> iter = prefs.keys();
            SharedPreferences.Editor e = _prefs.edit();
            while (iter.hasNext()) {
                String key = iter.next();
                if (PREF_BLACKLIST.contains(key))
                    continue;

                Object value = prefs.get(key);
                if (!(value instanceof JSONObject))
                    continue;

                JSONObject v = (JSONObject) value;
                if (!v.has("type") || !v.has("value"))
                    continue;

                String vs = String.valueOf(v.get("value"));
                String type = v.getString("type");
                switch (type) {
                    case "string":
                        e.putString(key, vs);
                        break;
                    case "integer":
                        e.putInt(key, Integer.parseInt(vs));
                        break;
                    case "long":
                        e.putLong(key, Long.parseLong(vs));
                        break;
                    case "float":
                        e.putFloat(key, Float.parseFloat(vs));
                        break;
                    case "boolean":
                        e.putBoolean(key, Boolean.parseBoolean(vs));
                        break;
                }
            }
            e.apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read shared preferences JSON", e);
            return false;
        }

        return true;
    }
}
