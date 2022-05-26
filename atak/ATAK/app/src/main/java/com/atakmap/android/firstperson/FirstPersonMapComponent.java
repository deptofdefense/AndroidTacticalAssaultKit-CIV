
package com.atakmap.android.firstperson;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;

public class FirstPersonMapComponent extends AbstractMapComponent {

    FirstPersonReceiver receiver = null;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {

        AtakBroadcast.DocumentedIntentFilter filter = new AtakBroadcast.DocumentedIntentFilter();
        filter.addAction(FirstPersonReceiver.FIRSTPERSON);
        filter.addAction(FirstPersonReceiver.MAP_CLICKED);
        AtakBroadcast.getInstance().registerReceiver(
                receiver = new FirstPersonReceiver(view), filter);

    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        if (receiver != null)
            AtakBroadcast.getInstance().unregisterReceiver(receiver);
    }

}
