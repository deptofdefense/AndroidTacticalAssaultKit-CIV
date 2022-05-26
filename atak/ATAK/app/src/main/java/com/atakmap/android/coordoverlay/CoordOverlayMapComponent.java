
package com.atakmap.android.coordoverlay;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.selfcoordoverlay.SelfCoordOverlayUpdater;
import com.atakmap.android.widgets.AbstractWidgetMapComponent;

/**
 * Provides for the implementation of the Coordinate Overlay Widget in the 
 * upper right hand corner of the map display.
 */
public class CoordOverlayMapComponent extends AbstractWidgetMapComponent {
    protected CoordOverlayMapReceiver _coordMapReceiver;
    protected SelfCoordOverlayUpdater _selfCoordUpdater;

    @Override
    protected void onCreateWidgets(Context context, Intent intent,
            MapView view) {
        _coordMapReceiver = new CoordOverlayMapReceiver(view);
        DocumentedIntentFilter showFilter = new DocumentedIntentFilter();
        showFilter.addAction("com.atakmap.android.maps.SHOW_DETAILS");
        showFilter.addAction("com.atakmap.android.maps.HIDE_DETAILS");
        showFilter
                .addAction("com.atakmap.android.action.SHOW_POINT_DETAILS");
        showFilter
                .addAction("com.atakmap.android.action.HIDE_POINT_DETAILS");
        showFilter
                .addAction("com.atakmap.android.action.PRI_TRANSFORMED_COORDS");
        AtakBroadcast.getInstance().registerReceiver(_coordMapReceiver,
                showFilter);

        _selfCoordUpdater = new SelfCoordOverlayUpdater(view);
        _coordMapReceiver.setSelfCoordOverlayUpdater(_selfCoordUpdater);
    }

    @Override
    protected void onDestroyWidgets(Context context, MapView view) {
        AtakBroadcast.getInstance().unregisterReceiver(_coordMapReceiver);
        _coordMapReceiver.dispose();
        _coordMapReceiver = null;
        _selfCoordUpdater.dispose();
        _selfCoordUpdater = null;

    }

}
