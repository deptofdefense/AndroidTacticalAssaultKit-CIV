
package com.atakmap.android.maps;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

public class MapCoreIntentsComponent extends AbstractMapComponent {

    public final static String ACTION_DELETE_GROUP = "com.atakmap.android.maps.MapView.DELETE_GROUP";
    public final static String ACTION_DELETE_ITEM = "com.atakmap.android.maps.MapView.DELETE_ITEM";
    public final static String ACTION_PAN_ZOOM = "com.atakmap.android.maps.MapController.PAN_ZOOM";

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        this.registerReceiver(context,
                new MapGroupDeleteReceiver(view),
                new DocumentedIntentFilter(ACTION_DELETE_GROUP));

        this.registerReceiver(context,
                new MapItemDeleteReceiver(view),
                new DocumentedIntentFilter(ACTION_DELETE_ITEM));

        this.registerReceiver(context,
                new PanZoomReceiver(view),
                new DocumentedIntentFilter(ACTION_PAN_ZOOM));
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
    }
}
