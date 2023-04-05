
package com.atakmap.android.emergency.tool;

import com.atakmap.android.maps.MapView;

public class EmergencyManagerCompat {
    public static void initialize(MapView mapView) {
        EmergencyManager.initialize(mapView);
    }
}
