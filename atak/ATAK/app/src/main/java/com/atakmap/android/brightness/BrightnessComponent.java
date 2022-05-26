
package com.atakmap.android.brightness;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.DocumentedExtra;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;

/**
 * Responsible for setting the map brightness.
 */
public class BrightnessComponent extends AbstractMapComponent {

    public final static String SHOW_BRIGHTNESS_TOOL = "com.atakmap.android.brightness.BrightnessComponent.SHOW_TOOL";

    protected BrightnessReceiver _brightnessReceiver;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {

        _brightnessReceiver = new BrightnessReceiver(view);

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(SHOW_BRIGHTNESS_TOOL,
                "The intent to show the brightness tool",
                new DocumentedExtra[] {
                        new DocumentedExtra("value",
                                "Integer between 0..100 to describe the current brightness of the map",
                                true, Integer.class)
                });
        AtakBroadcast.getInstance().registerReceiver(_brightnessReceiver,
                filter);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        if (_brightnessReceiver != null) {
            _brightnessReceiver.dismiss();
            AtakBroadcast.getInstance().unregisterReceiver(_brightnessReceiver);
            _brightnessReceiver = null;
        }
    }

}
