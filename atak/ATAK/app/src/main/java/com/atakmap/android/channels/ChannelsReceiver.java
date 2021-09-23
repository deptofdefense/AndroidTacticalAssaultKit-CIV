
package com.atakmap.android.channels;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.channels.ui.overlay.ChannelsOverlay;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.dropdown.DropDown.OnStateListener;
import com.atakmap.android.selfcoordoverlay.SelfCoordOverlayUpdater.ConnectedButtonWidgetCallback;
import com.atakmap.app.SettingsActivity;
import com.atakmap.app.preferences.NetworkConnectionPreferenceFragment;
import com.atakmap.comms.TAKServer;
import com.atakmap.comms.TAKServerListener;
import com.atakmap.coremap.log.Log;

public class ChannelsReceiver extends BroadcastReceiver implements
        OnStateListener, ConnectedButtonWidgetCallback {

    public static final String TAG = "ChannelsReceiver";
    public static final String OPEN_CHANNELS_OVERLAY = "com.atakmap.android.channels.OPEN_CHANNELS_OVERLAY";
    public static final String CHANNELS_UPDATED = "com.atakmap.android.channels.CHANNELS_UPDATED";

    private final Context context;

    /**************************** CONSTRUCTOR *****************************/

    public ChannelsReceiver(final MapView mapView,
            final Context context) {

        this.context = context;

        // TODO expose as preference
        //SelfCoordOverlayUpdater.setConnectedButtonWidgetCallback(this);
    }

    /**************************** PUBLIC METHODS *****************************/

    public void disposeImpl() {
    }

    /**************************** INHERITED METHODS *****************************/

    @Override
    public void onReceive(Context context, Intent intent) {

        final String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(OPEN_CHANNELS_OVERLAY)) {
            displayActiveGroupsOverlay();
        }
    }

    private void displayActiveGroupsOverlay() {
        final TAKServer[] servers = TAKServerListener.getInstance()
                .getConnectedServers();
        if (servers == null || servers.length == 0) {
            // If there are no servers, allow the user to configure one
            displayServerSetupDialog();
        } else {
            ChannelsOverlay.displayOverlay(context);
        }
    }

    private void displayServerSetupDialog() {
        try {
            new AlertDialog.Builder(MapView.getMapView().getContext())
                    .setTitle("TAK Server Setup")
                    .setMessage(
                            "No TAK Server configured. Would you like to go to Network Settings?")
                    .setPositiveButton(
                            "Yes",
                            (d, w) -> SettingsActivity.start(
                                    NetworkConnectionPreferenceFragment.class))
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    public void onConnectedButtonWidgetClick() {
        Log.d(TAG, "in onConnectedButtonWidgetClick!!!");
        displayActiveGroupsOverlay();
    }

    @Override
    public void onDropDownSelectionRemoved() {
    }

    @Override
    public void onDropDownVisible(boolean v) {
    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {
    }

    @Override
    public void onDropDownClose() {
    }
}
