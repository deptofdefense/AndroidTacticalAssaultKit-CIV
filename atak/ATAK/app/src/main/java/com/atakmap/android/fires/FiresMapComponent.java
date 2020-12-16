
package com.atakmap.android.fires;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.app.system.ResourceUtil;

/**
 * Provide for fires related preferences to be set within ATAK.   Fires
 * related preferences include general capabilities not necessarily
 * related to CAS or CFF.
 */
public class FiresMapComponent extends AbstractMapComponent {

    final public static String TAG = "FiresMapComponent";

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        FiresToolbar.getInstance(view);

        ToolsPreferenceFragment.register(
                new ToolsPreferenceFragment.ToolPreference(
                        ResourceUtil.getString(context,
                                R.string.civ_fire_control_prefs,
                                R.string.fire_control_prefs),
                        ResourceUtil.getString(context,
                                R.string.civ_adjust_fire_settings,
                                R.string.adjust_fire_settings),
                        "firesPreference",
                        context.getResources().getDrawable(
                                R.drawable.ic_menu_fires),
                        new FiresPreferenceFragment()));

    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        FiresToolbar.dispose();

    }

}
