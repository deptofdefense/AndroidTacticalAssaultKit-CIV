
package com.atakmap.android.bloodhound;

import android.graphics.Color;
import androidx.annotation.ColorInt;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.UnitPreferences;
import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Preference helper for bloodhound
 */
public class BloodHoundPreferences extends UnitPreferences {

    private static final int FLASH_ETA_DEFAULT = 360;
    private static final int OUTER_MIDDLE_DEFAULT = 180;
    private static final int MIDDLE_INNER_DEFAULT = 60;
    private static final int FLASH_COLOR_DEFAULT = Color.BLACK;
    private static final int OUTER_COLOR_DEFAULT = Color.GREEN;
    private static final int MIDDLE_COLOR_DEFAULT = Color.YELLOW;
    private static final int INNER_COLOR_DEFAULT = Color.RED;

    public BloodHoundPreferences(MapView mapView) {
        super(mapView);
    }

    /**
     * Get the value for a color string preference
     * @param key Preference key
     * @param defColor Default color
     * @return Color int
     */
    private @ColorInt int getColor(String key, @ColorInt int defColor) {
        try {
            String colorStr = _prefs.getString(key, null);
            if (FileSystemUtils.isEmpty(colorStr))
                return defColor;
            return Color.parseColor(colorStr);
        } catch (Exception e) {
            return defColor;
        }
    }

    /**
     * Returns the time in seconds when the bloodhound tool will begin to flash based on the
     * estimated time of arrival.
     * @return the time in seconds
     */
    public int getFlashETA() {
        return get("bloodhound_flash_eta", FLASH_ETA_DEFAULT);
    }

    /**
     * Returns the time in seconds when the color should change specifying the outer distance
     * has been reached
     * @return the time in seconds
     */
    public int getOuterETA() {
        return get("bloodhound_outer_eta", OUTER_MIDDLE_DEFAULT);
    }

    /**
     * Returns the time in seconds when the color should change specifying the inner distance
     * has been reached
     * @return the time in seconds
     */
    public int getInnerETA() {
        return get("bloodhound_inner_eta", MIDDLE_INNER_DEFAULT);
    }

    /**
     * Returns the determined flash color
     * @return the color in ARGB
     */
    public int getFlashColor() {
        return getColor("bloodhound_flash_color_pref", FLASH_COLOR_DEFAULT);
    }

    public int getOuterColor() {
        return getColor("bloodhound_outer_color_pref", OUTER_COLOR_DEFAULT);
    }

    public int getMiddleColor() {
        return getColor("bloodhound_middle_color_pref", MIDDLE_COLOR_DEFAULT);
    }

    public int getInnerColor() {
        return getColor("bloodhound_inner_color_pref", INNER_COLOR_DEFAULT);
    }
}
