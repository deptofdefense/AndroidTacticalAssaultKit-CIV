
package com.atakmap.android.navigation.views.loadout;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.NavBarPluginReceiver;

/**
 * Map component that handles creation and intents for all registered loadout tools
 */
public class LoadoutToolsMapComponent extends AbstractMapComponent {

    final public static String TAG = "NavSettingsMapComponent";

    private NavBarPluginReceiver pluginReceiver;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        AtakBroadcast.DocumentedIntentFilter filter = new AtakBroadcast.DocumentedIntentFilter();
        // need action bar receiver ready to go before components are launched
        filter.addAction(ActionBarReceiver.ADD_NEW_TOOL);
        filter.addAction(ActionBarReceiver.ADD_NEW_TOOLS);
        filter.addAction(ActionBarReceiver.REMOVE_TOOLS);
        pluginReceiver = new NavBarPluginReceiver();
        registerReceiver(context, pluginReceiver, filter);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        unregisterReceiver(context, pluginReceiver);
    }

}
