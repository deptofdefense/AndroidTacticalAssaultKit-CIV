
package com.atakmap.android.util;

import android.content.SharedPreferences;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Store Most Recently Used String history Stored internally as JSON encoded preference
 *
 * 
 */
public class MRUStringCache {

    private static final String TAG = "MRUStringCache";

    private final static int MAX_HISTORY = 10;

    /**
     * Get list of recently used Strings, for given key
     *
     * @param prefs the history based on the shared preference storage of a specific key
     * @return the list of recently used strings.
     */
    public static List<String> GetHistory(SharedPreferences prefs, String key) {
        String json = prefs.getString(key, null);
        List<String> urls = new ArrayList<>();
        if (!FileSystemUtils.isEmpty(json)) {
            try {
                JSONArray a = new JSONArray(json);
                for (int i = 0; i < a.length(); i++) {
                    String value = a.optString(i);
                    if (FileSystemUtils.isEmpty(value)) {
                        Log.w(TAG, "Unable to parse string history");
                        continue;
                    }
                    urls.add(value);
                }
            } catch (JSONException e) {
                Log.w(TAG, "Unable to get string history", e);
            }
        }
        return urls;
    }

    /**
     * Update the history to include the specified recently used string
     *
     * @param prefs add a string to the most recently used history
     * @param key the key that is used to hold the most recently used history.
     * @param value the string that is most recently used.
     */
    public static void UpdateHistory(SharedPreferences prefs, String key,
            String value) {
        List<String> history = GetHistory(prefs, key);
        if (history.contains(value)) {
            // move to top of list
            Log.d(TAG, "string already in history: " + value);
            history.remove(value);
        }

        // add to beginning (most recent)
        Log.d(TAG, "Adding string to history: " + value);
        history.add(0, value);

        // bump off the end if too many in history
        while (history.size() > MAX_HISTORY) {
            String bump = history.get(history.size() - 1);
            history.remove(history.size() - 1);
            Log.d(TAG, "Bumping string from history: " + bump);
        }

        // write out to pref
        SharedPreferences.Editor editor = prefs.edit();
        JSONArray a = new JSONArray();
        for (int i = 0; i < history.size(); i++) {
            a.put(history.get(i));
        }
        if (!history.isEmpty()) {
            editor.putString(key, a.toString());
        } else {
            editor.putString(key, null);
        }
        editor.apply();
    }

    /**
     * Replace the history to include the specified recently used strings
     *
     * @param prefs set the entire most recently used history list.
     * @param key the key used to store the most recently used history.
     * @param values the list of strings
     */
    public static void SetHistory(SharedPreferences prefs, String key,
            List<String> values) {
        // write out to pref
        SharedPreferences.Editor editor = prefs.edit();
        JSONArray a = new JSONArray();
        for (int i = 0; i < values.size(); i++) {
            a.put(values.get(i));
        }
        if (!values.isEmpty()) {
            String str = a.toString();
            Log.d(TAG, "Set string to history: " + str);
            editor.putString(key, str);
        } else {
            editor.putString(key, null);
        }
        editor.apply();
    }
}
