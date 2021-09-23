
package com.atakmap.android.channels;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.atakmap.android.channels.ui.overlay.ChannelsOverlay;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;

public class ChannelsMapComponent extends AbstractMapComponent {

    private static final String TAG = "ChannelsMapComponent";
    public static final String PREFERENCE_ENABLE_CHANNELS_UI_KEY = "prefs_enable_channels";
    public static final String PREFERENCE_ENABLE_CHANNELS_HOST_KEY = "prefs_enable_channels_host";

    private Context context;
    private ChannelsReceiver channelsReceiver;
    private ChannelsOverlay channelsOverlay;
    private SharedPreferences sharedPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener;

    public void onCreate(final Context context, Intent intent,
            final MapView view) {

        this.context = context;
        this.channelsReceiver = new ChannelsReceiver(view, context);

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(ChannelsReceiver.OPEN_CHANNELS_OVERLAY);

        AtakBroadcast.getInstance().registerReceiver(channelsReceiver, filter);

        sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);

        // watch for the shared preference to create the channels overlay on demand
        sharedPreferences.registerOnSharedPreferenceChangeListener(
                sharedPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                    @Override
                    public void onSharedPreferenceChanged(
                            SharedPreferences sharedPreferences, String key) {
                        if (key.equals(PREFERENCE_ENABLE_CHANNELS_UI_KEY)) {
                            if (sharedPreferences.getString(key, "false")
                                    .equals("true")) {
                                createChannelsOverlay(view);
                            }
                        }
                    }
                });

        // create the overlay on startup if we've already got the preference set
        if (sharedPreferences
                .getString(PREFERENCE_ENABLE_CHANNELS_UI_KEY, "false")
                .equals("true")) {
            createChannelsOverlay(view);
        }
    }

    private void createChannelsOverlay(MapView view) {
        channelsOverlay = new ChannelsOverlay(view, context);
        view.getMapOverlayManager().addOverlay(channelsOverlay);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
    }
}
