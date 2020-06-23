
package com.atakmap.android.nightvision;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v4.app.NotificationCompat;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
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

    private Notification notification;
    private MapView mapView;

    private final static String ON_TEXT = "Click to disable Night Vision";
    private final static String OFF_TEXT = "Click to enable Night Vision";

    private NotificationManager notificationManager;
    private final static int NOTIFICATION_ID = 223419;

    private final static String TAG = "NightVisionNotification";

    public static final String NOTIFICATION_CONTROL = "control_notification_night_vision";
    public static final int P_REQUEST_CODE = 47312;

    public NightVisionNotification() {
        //empty constructor because we assigned this receiver in the manifest
        //so the pending intent will find this receiver and the intents
    }

    public NightVisionNotification(MapView mapView) {
        this.mapView = mapView;
        createNotification();
        notificationManager = (NotificationManager) mapView.getContext()
                .getSystemService(
                        Context.NOTIFICATION_SERVICE);

    }

    private Bitmap getNotificationIcon() {
        //the large icon with the android dimensions provided by specific device

        return Bitmap
                .createScaledBitmap(
                        BitmapFactory.decodeResource(mapView.getContext()
                                .getResources(), R.drawable.nightvision),
                        mapView.getContext()
                                .getResources()
                                .getDimensionPixelSize(
                                        android.R.dimen.notification_large_icon_width),
                        mapView.getContext()
                                .getResources()
                                .getDimensionPixelSize(
                                        android.R.dimen.notification_large_icon_height),
                        true);
    }

    /**called when a user clicks the notification from the system tray
     * we send the intent broadcast to the external NV applciation to enable/disable the service
     * create a new notification and display based on the new state of the NV service.
     */
    private void updateNotification() {
        //send out intent to night vision application to turn on or off
        AtakBroadcast.getInstance().sendSystemBroadcast(
                new Intent("nightvision.com.atak.NVG_MODE"));

        //delay the posting of the new notification so the intent to handle the
        //night vision enable/disable in the external app can have a chance to run
        //if we dont there is a chance that the notification will create before the service is handled
        //and that can create problems with the checking of the current state of Night Vision Service running
        MapView.getMapView().postDelayed(new Runnable() {
            @Override
            public void run() {
                dispatchNotification();
            }
        }, 99);
    }

    public void createNotification() {
        mapView = MapView.getMapView(); //get static version not local
        if (mapView == null)
            return;
        final Context c = mapView.getContext().getApplicationContext();

        //wrap the broadcast to invoke when the notification is selected from the system tray
        Intent intent = new Intent(NOTIFICATION_CONTROL);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(c,
                P_REQUEST_CODE, intent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(c);
        builder.setContentTitle("Night Vision Mode");
        builder.setContentText(NightVisionPreferenceFragment
                .isServiceRunning(c) ? ON_TEXT : OFF_TEXT);
        builder.setContentIntent(pendingIntent);
        builder.setSmallIcon(R.drawable.nightvision);
        builder.setLargeIcon(getNotificationIcon());
        builder.setAutoCancel(false);
        builder.setOngoing(true);
        builder.setPriority(Notification.PRIORITY_MAX);

        notification = builder.build();
    }

    /**Sends the notification created to the android notification system
     * to show and display the created notification, cancel the current notification so
     * the new notification created will not overlap with same IDs
     */
    public void dispatchNotification() {
        createNotification();
        if (notificationManager == null) {
            notificationManager = (NotificationManager) mapView.getContext()
                    .getSystemService(Context.NOTIFICATION_SERVICE);
        }
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    public void cancelNotification() {
        Log.d(TAG, "cancelling notification");
        notificationManager.cancel(NOTIFICATION_ID);
        if (notification != null) {
            if (notification.largeIcon != null)
                notification.largeIcon.recycle();
            notification = null;
        }
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
