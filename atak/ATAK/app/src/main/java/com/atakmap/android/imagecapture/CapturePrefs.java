
package com.atakmap.android.imagecapture;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.preference.PreferenceManager;
import android.view.Display;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;

/**
 * Set and get overlay manager preferences
 */
public class CapturePrefs {
    public static final String TAG = "CapturePrefs";
    public static final String PREF_SHOW_IMAGERY = "img_cap_imagery";
    public static final String PREF_FONT_SIZE = "img_cap_font_size";
    public static final String PREF_LABEL_SIZE = "img_cap_label_size";
    public static final String PREF_ICON_SIZE = "img_cap_icon_size";
    public static final String PREF_LINE_WEIGHT = "img_cap_line_weight";
    public static final String PREF_INFOBOX_POS = "img_cap_info_pos";
    public static final String PREF_INFOBOX_TALIGN = "img_cap_info_talign";
    public static final String PREF_LAST_TITLE = "img_cap_title";
    public static final String PREF_RES = "img_cap_res";
    public static final String PREF_FORMAT = "img_cap_format";
    public static final String PREF_IMG_QUALITY = "img_cap_quality";
    public static final String PREF_RECT = "img_cap_rect";
    public static final String PREF_ARROWS = "img_cap_arrows";
    public static final String PREF_INFO = "img_cap_info";
    public static final String PREF_SCALE = "img_cap_scale";

    public static final String PREF_THEME = "img_cap_theme";
    public static final String PREF_THEME_LIGHT = "light";
    public static final String PREF_THEME_DARK = "dark";

    private static SharedPreferences _prefs;

    public static SharedPreferences getPrefs() {
        if (_prefs == null) {
            try {
                _prefs = PreferenceManager.getDefaultSharedPreferences(
                        MapView.getMapView().getContext());
            } catch (Exception e) {
                _prefs = null;
            }
        }
        return _prefs;
    }

    public static int get(String key, int defaultVal) {
        if (getPrefs() != null) {
            try {
                return _prefs.getInt(key, defaultVal);
            } catch (Exception e) {
                try {
                    return Integer.parseInt(_prefs.getString(key,
                            String.valueOf(defaultVal)));
                } catch (Exception e2) {
                    Log.e(TAG, "Failed to get preference int: " + key, e);
                }
            }
        }
        return defaultVal;
    }

    public static boolean get(String key, boolean defaultVal) {
        if (getPrefs() != null) {
            try {
                return _prefs.getBoolean(key, defaultVal);
            } catch (Exception e) {
                try {
                    return Boolean.parseBoolean(_prefs.getString(key,
                            String.valueOf(defaultVal)));
                } catch (Exception e2) {
                    Log.e(TAG, "Failed to get preference boolean: " + key, e);
                }
            }
        }
        return defaultVal;
    }

    public static String get(String key, String defaultVal) {
        if (getPrefs() != null) {
            try {
                return _prefs.getString(key, defaultVal);
            } catch (Exception e) {
                Log.e(TAG, "Failed to get preference string: " + key, e);
            }
        }
        return defaultVal;
    }

    public static void set(String key, String value) {
        if (getPrefs() != null)
            _prefs.edit().putString(key, value).apply();
    }

    public static void set(String key, int value) {
        if (getPrefs() != null)
            _prefs.edit().putInt(key, value).apply();
    }

    public static void set(String key, boolean value) {
        if (getPrefs() != null)
            _prefs.edit().putBoolean(key, value).apply();
    }

    public static boolean inPortraitMode() {
        Display disp = ((Activity) MapView.getMapView().getContext())
                .getWindowManager().getDefaultDisplay();
        Point size = new Point();
        disp.getSize(size);
        return size.y > size.x;
    }
}
