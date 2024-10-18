
package com.atakmap.android.emergency.tool;

import android.content.Context;

import com.atakmap.android.maps.MapView;

public class EmergencyToolCompat {
    public static EmergencyTool getInstance(MapView mapView, Context context) {
        return new EmergencyTool(mapView, context);
    }
}
