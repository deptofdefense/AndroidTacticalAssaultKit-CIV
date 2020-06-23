
package com.atakmap.android.radiolibrary;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.view.View;

import com.atakmap.android.dropdown.DropDownMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.app.preferences.ToolsPreferenceFragment;

/**
 * Provides a generalized tab for Radio control, this can be extended 
 * for use by plugins offering additional radio controls.
 */
public class RadioMapComponent extends DropDownMapComponent {

    static public final String TAG = "RadioMapComponent";

    private RadioDropDownReceiver rddr;
    private static RadioMapComponent _instance;

    private WaveRelayControlLite wrcl;

    public static RadioMapComponent getInstance() {
        return _instance;
    }

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        super.onCreate(context, intent, view);
        DocumentedIntentFilter radioControlFilter = new DocumentedIntentFilter();
        radioControlFilter.addAction("com.atakmap.radiocontrol.RADIO_CONTROL");

        registerDropDownReceiver(
                rddr = new RadioDropDownReceiver(view),
                radioControlFilter);

        ToolsPreferenceFragment.register(
                new ToolsPreferenceFragment.ToolPreference(
                        context.getString(R.string.isrv_control_prefs),
                        context.getString(R.string.adjust_ISRV_settings),
                        "isrvNetworkPreference",
                        context.getResources().getDrawable(
                                R.drawable.ic_menu_radio),
                        new IsrvNetworkPreferenceFragment()));

        wrcl = WaveRelayControlLite.getInstance(view);

        _instance = this;
    }

    /**
     * Allows for external addition of control view for a radio.
     * @param view the control as an Android View.
     */
    public void registerControl(final View view) {
        rddr.registerControl(view);
    }

    /**
     * Allows for external remove of control view for a radio.
     * @param view the registered control as an Android View.
     */
    public void unregisterControl(final View view) {
        rddr.unregisterControl(view);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        super.onDestroyImpl(context, view);
        if (wrcl != null)
            wrcl.dispose();

        _instance = null;
    }
}
