
package com.atakmap.android.fires;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.maps.MapView;

/**
 * Provides for a management capability for all CAS 9-lines within the
 * ATAK system.   This allows a user to quickly visualize important aspects
 * of the 9-line and sort in a list form.
 */
public class HostileManagerMapComponent extends DropDownMapComponent {

    private HostileManagerDropDownReceiver _receiver;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {

        super.onCreate(context, intent, view);
        _receiver = new HostileManagerDropDownReceiver(view);
        DocumentedIntentFilter intentFilter = new DocumentedIntentFilter();
        intentFilter
                .addAction(
                        "com.atakmap.android.maps.MANAGE_HOSTILES",
                        "The event to bring up the hostile manager within the ATAK system.   This allows for manipulation of all CAS 9-lines within the system.");
        this.registerDropDownReceiver(_receiver, intentFilter);

    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        super.onDestroyImpl(context, view);
        _receiver = null;
    }

}
