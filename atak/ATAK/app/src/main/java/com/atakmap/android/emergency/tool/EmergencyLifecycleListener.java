
package com.atakmap.android.emergency.tool;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.maps.MapView;

public class EmergencyLifecycleListener extends DropDownMapComponent {

    public static final String TAG = "EmergencyLifecycleListener";

    @Override
    public void onCreate(Context context, Intent intent, MapView mapView) {
        EmergencyManagerCompat.initialize(mapView);

        final DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction("com.atakmap.android.emergency.tool");

        EmergencyTool et = EmergencyToolCompat.getInstance(mapView, context);
        registerDropDownReceiver(et, filter);
    }

}
