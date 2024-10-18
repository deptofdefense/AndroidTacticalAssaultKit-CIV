
package com.atakmap.android.channels;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.atakmap.android.channels.ui.overlay.ChannelsOverlay;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.navigation.NavButtonManager;
import com.atakmap.android.navigation.models.NavButtonModel;
import com.atakmap.android.preference.AtakPreferences;
import com.atakmap.app.R;

public class ChannelsMapComponent extends AbstractMapComponent implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "ChannelsMapComponent";
    public static final String PREFERENCE_ENABLE_CHANNELS_UI_KEY = "prefs_enable_channels";
    public static final String PREFERENCE_ENABLE_CHANNELS_HOST_KEY = "prefs_enable_channels_host";

    private MapView _mapView;
    private Context _context;
    private AtakPreferences _prefs;

    private ChannelsReceiver _receiver;
    private ChannelsOverlay _overlay;
    private NavButtonModel _model;

    @Override
    public void onCreate(final Context context, Intent intent,
            final MapView view) {

        _mapView = view;
        _context = context;
        _prefs = new AtakPreferences(view);
        _receiver = new ChannelsReceiver(view, context);

        // Toolbar button model
        _model = new NavButtonModel.Builder()
                .setReference("channels.xml")
                .setName(_context.getString(R.string.actionbar_channels))
                .setImage(_context.getDrawable(R.drawable.nav_channels))
                .setAction(ChannelsReceiver.OPEN_CHANNELS_OVERLAY)
                .build();

        // Toolbar button intent receiver
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(ChannelsReceiver.OPEN_CHANNELS_OVERLAY);
        AtakBroadcast.getInstance().registerReceiver(_receiver, filter);

        // watch for the shared preference to create the channels overlay on demand
        _prefs.registerListener(this);

        // Check if the preference is already set
        checkPreferenceEnabled();
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        AtakBroadcast.getInstance().unregisterReceiver(_receiver);
        setEnabled(false);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key == null)
            return;

        if (key.equals(PREFERENCE_ENABLE_CHANNELS_UI_KEY))
            checkPreferenceEnabled();
    }

    private void checkPreferenceEnabled() {
        setEnabled(_prefs.get(PREFERENCE_ENABLE_CHANNELS_UI_KEY, false));
    }

    private void setEnabled(boolean enabled) {
        if (enabled == (_overlay == null)) {
            if (enabled) {
                NavButtonManager.getInstance().addButtonModel(_model);
                _overlay = new ChannelsOverlay(_mapView, _context);
                _mapView.getMapOverlayManager().addOverlay(_overlay);
            } else {
                NavButtonManager.getInstance().removeButtonModel(_model);
                _mapView.getMapOverlayManager().removeOverlay(_overlay);
                _overlay.dispose();
                _overlay = null;
            }
        }
    }
}
