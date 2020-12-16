
package com.atakmap.android.nightvision;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

/**
 *
 * a notification class that handles the creation and display of the notification system
 * for controlling NV mode in ATAK using a outside source that is controlled via the system.
 * when the preference to control NV mode is enabled in the NV preferences
 * we display a persistent notification that is only enabled when ATAK is is a run state
 * the user can swipe down and select the notification to enable/disable NV mode service for the
 * device.
 */

public class NightVisionNotification extends BroadcastReceiver {

    private final static String ON_TEXT = "Click to disable Night Vision";
    private final static String OFF_TEXT = "Click to enable Night Vision";
    private final static String TAG = "NightVisionNotification";

    public static final String NOTIFICATION_CONTROL = "control_notification_night_vision";
    private final int NotificationId = 99123;

    public NightVisionNotification() {
        //empty constructor because we assigned this receiver in the manifest
        //so the pending intent will find this receiver and the intents
    }

    /**called when a user clicks the notification from the system tray
     * we send the intent broadcast to the external NV applciation to enable/disable the service
     * create a new notification and display based on the new state of the NV service.
     */
    private void updateNotification() {
        //send out intent to night vision application to turn on or off
        AtakBroadcast.getInstance().sendSystemBroadcast(
                new Intent("nightvision.com.atak.NVG_MODE"));

        dispatchNotification();
    }

    /**Sends the notification created to the android notification system
     * to show and display the created notification, cancel the current notification so
     * the new notification created will not overlap with same IDs
     */
    public void dispatchNotification() {
        //delay the updating of notification so we can give the external app service a change to update itself
        MapView.getMapView().postDelayed(new Runnable() {
            @Override
            public void run() {
                String text = (NightVisionPreferenceFragment.isServiceRunning(
                        MapView.getMapView().getContext()) ? ON_TEXT
                                : OFF_TEXT);
                Intent intent = new Intent(NOTIFICATION_CONTROL);
                NotificationUtil.getInstance().postNotification(NotificationId,
                        R.drawable.nightvision,
                        "Night Vision", "", text, intent, true, false, false,
                        false);
            }
        }, 3000);
    }

    public void cancelNotification() {
        Log.d(TAG, "cancelling notification");
        NotificationUtil.getInstance().clearNotification(NotificationId);
    }

    /**
     * Receives the broadcast from the user clicking the notification from the
     * system tray.
     */
    @Override
    public void onReceive(Context ignored, Intent intent) {
        Log.d(TAG, intent.getAction());
        String action = intent.getAction();
        if (action != null && action.equals(NOTIFICATION_CONTROL)) {
            updateNotification();
        }
    }

    public void onPause() {
        cancelNotification();
    }
}
