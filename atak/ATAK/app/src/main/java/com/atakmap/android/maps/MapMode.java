
package com.atakmap.android.maps;

public enum MapMode {

    UNDEFINED(0, "com.atakmap.android.maps.UNDEFINED"),
    TRACK_UP(1, "com.atakmap.android.maps.TRACK_UP"),
    NORTH_UP(2, "com.atakmap.android.maps.NORTH_UP"),
    MAGNETIC_UP(3, "com.atakmap.android.maps.MAGNETIC_UP"),
    USER_DEFINED_UP(4, "com.atakmap.android.maps.USER_DEFINED_UP"),;

    private final int _value;
    private final String _intent;

    MapMode(final int value, final String intent) {
        _value = value;
        _intent = intent;
    }

    /**
     * Gets the integer representation of the MapMode.
     */
    public int getValue() {
        return _value;
    }

    /**
     * Gets the human readable full string representation of the MapMode
     */
    public String getIntent() {
        return _intent;
    }

    @Override
    public String toString() {
        return _intent;
    }

    /**
     * @param value
     * @return
     */
    public static MapMode findFromValue(final int value) {
        for (MapMode mm : MapMode.values()) {
            if (mm._value == value) {
                return mm;
            }
        }
        return UNDEFINED;
    }

    /**
     * @param intent
     * @return
     */
    public static MapMode findFromIntent(final String intent) {
        for (MapMode mm : MapMode.values()) {
            if (mm._intent.equalsIgnoreCase(intent)) {
                return mm;
            }
        }
        return UNDEFINED;
    }

}
