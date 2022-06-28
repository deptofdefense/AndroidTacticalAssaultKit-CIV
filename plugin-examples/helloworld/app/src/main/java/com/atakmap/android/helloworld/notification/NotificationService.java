
package com.atakmap.android.helloworld.notification;

import com.atakmap.android.helloworld.plugin.BuildConfig;
import com.atakmap.android.helloworld.plugin.R;

import android.app.NotificationChannel;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import android.util.Log;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;

/** 
 * Please note, this Service cannot reference anything from ATAK CORE because it is started up by a 
 * classloader that knows nothing about the plugin interface.    So for example you cannot reference
 * Marker, GeoPoint, Android Support Library, etc.
 * This will start up, but when looking for the class from ATAK CORE will bomb out even if it compiled
 * successfully. 
 * This is because compilation for plugins weakly links against the ATAK classes and depends on 
 * them being found at runtime.
 * Compiling any other way will cause duplicative classes and the main plugin will not load properly.
 *
 * 
 * This service is not bound by any permissions or exceptions from the ATAK application.    One such 
 * example is with BatteryOptimization.  As an independent process, if battery optimization is desired
 * then it would need to request it separately.
 *
 *
 * This is an example on how to use a Notification with a plugin supplied icon.    if an ATAK supplied
 * resource / icon can be used, you can just use NotificationUtil.
 */

public class NotificationService extends Service {

    private final static String TAG = "NotificationService";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG,
                "getting ready to show the notification, can never use notification compat.");

        NotificationManager notificationManager = (NotificationManager) getSystemService(
                NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "com.atakmap.android.helloworld.def",
                    "Helloworld Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT); // correct Constant
            channel.setSound(null, null);
            notificationManager.createNotificationChannel(channel);
        }

        Intent atakFrontIntent = new Intent();

        atakFrontIntent.setComponent(new ComponentName(
                BuildConfig.ATAK_PACKAGE_NAME, "com.atakmap.app.ATAKActivity"));
        atakFrontIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        atakFrontIntent.putExtra("internalIntent",
                new Intent("com.atakmap.android.helloworld.SHOW_HELLO_WORLD"));
        PendingIntent appIntent = PendingIntent.getActivity(this, 0,
                atakFrontIntent, 0);

        Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nb = new Notification.Builder(this,
                    "com.atakmap.android.helloworld.def");
        } else {
            nb = new Notification.Builder(this);
        }

        nb.setContentTitle("Custom Notification").setContentText("Test Icon")
                .setSmallIcon(R.drawable.abc)
                .setContentIntent(appIntent);
        nb.setOngoing(false);
        nb.setAutoCancel(true);

        notificationManager.notify(9999, nb.build());

    }

}
