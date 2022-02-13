
package com.atakmap.android.routes.elevation;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;

public class RouteElevationBroadcastReceiverCompat {
    public static void initialize(MapView mapView,
            MapGroup mapGroup) {
        RouteElevationBroadcastReceiver._instance = new RouteElevationBroadcastReceiver(
                mapView, mapGroup);
    }
}
