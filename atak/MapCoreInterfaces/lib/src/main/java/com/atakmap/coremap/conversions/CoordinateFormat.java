
package com.atakmap.coremap.conversions;

import android.content.SharedPreferences;

/**
 * Acceptable designations for displaying Coordinates within ATAK.
 */
public enum CoordinateFormat {
    MGRS("MGRS", "Military Grid Reference System", 0),
    DD("DD", "Decimal Degrees", 1),
    DM("DM", "Degrees, Minutes", 2),
    DMS("DMS", "Degrees, Minutes, Seconds", 3),
    ADDRESS("ADDR", "Address", 4),
    UTM("UTM", "Universal Transverse Mercator Coordinate System", 5);

    private final String _displayName;
    private final String _fullName;
    private final int _value;

    CoordinateFormat(String displayName, String fullName, int value) {
        _displayName = displayName;
        _fullName = fullName;
        _value = value;
    }

    public int getValue() {
        return _value;
    }

    public String getDisplayName() {
        return _displayName;
    }

    public String getFullName() {
        return _fullName;
    }

    public static CoordinateFormat find(String displayName) {
        for (CoordinateFormat cf : values()) {
            if (cf.getDisplayName().equals(displayName)) {
                return cf;
            }
        }
        return MGRS;
    }

    public static CoordinateFormat find(int value) {
        for (CoordinateFormat cf : values()) {
            if (cf.getValue() == value) {
                return cf;
            }
        }
        return MGRS;
    }

    public static CoordinateFormat find(SharedPreferences prefs) {
        return find(prefs.getString("coord_display_pref", null));
    }

    @Override
    public String toString() {
        return _displayName;
    }
}
