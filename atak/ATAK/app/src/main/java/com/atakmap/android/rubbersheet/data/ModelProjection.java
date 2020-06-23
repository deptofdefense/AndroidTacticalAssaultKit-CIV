
package com.atakmap.android.rubbersheet.data;

/**
 * 3D model projection - chosen by the user on import
 */
public enum ModelProjection {

    ENU("East North Up"),
    ENU_FLIP_YZ("East North Up - Flipped Y/Z"),
    LLA("Longitude Latitude Altitude");

    // For informational purposes
    public final String desc;

    ModelProjection(String desc) {
        this.desc = desc;
    }
}
