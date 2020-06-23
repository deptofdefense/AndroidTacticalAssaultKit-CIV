
package com.atakmap.android.jumpbridge;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapView;

public class JumpBridgeMapComponent extends DropDownMapComponent {

    public static final String JM_WARNING = "com.atakmap.android.jumpbridge.JM_WARNING";

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        super.onCreate(context, intent, view);

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(JM_WARNING);
        registerDropDownReceiver(new JumpBridgeDropDownReceiver(view), filter);

    }
}
