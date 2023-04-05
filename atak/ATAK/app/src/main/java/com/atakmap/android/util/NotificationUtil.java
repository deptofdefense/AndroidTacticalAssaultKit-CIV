
package com.atakmap.android.util;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.app.R;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.android.metrics.MetricsApi;
import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.os.Build;
import android.app.NotificationChannel;
import com.atakmap.annotations.ModifierApi;

public class NotificationUtil {

    /**
     * Plugins using the NotificationUtil will need to use resources that are built into ATAK.   
     * This is a general set of icons that can be used as part of a notification.   If a plugin does not 
     * desire to use one of the preselected icons, you will need to look at the helloworld example for 
     * how to use plugin supplied icons.
     */
    public enum GeneralIcon {
        DOWNLOAD_ERROR(R.drawable.download_complete_errors),
        DOWNLOAD_COMPLETE(R.drawable.download_complete),
        SYNC_ORIGINAL(R.drawable.sync_original),
        SYNC_DOWNLOAD(R.drawable.sync_dload),
        SYNC_SUCCESS(R.drawable.sync_success),
        SYNC_COMPETE(R.drawable.sync_success),
        SYNC_ERROR(R.drawable.sync_error),
        CAMERA(R.drawable.camera),
        CHAT(R.drawable.chatsmall),
        HOSTILE(R.drawable.ic_notify_target),
        FRIENDLY(R.drawable.ic_notify_friendly),
        NEUTRAL(R.drawable.ic_notify_neutral),
        UNKNOWN(R.drawable.ic_notify_unknown),
        STATUS_GREEN(R.drawable.importmgr_status_green),
        STATUS_RED(R.drawable.importmgr_status_red),
        STATUS_YELLOW(R.drawable.importmgr_status_yellow),
        LIST(R.drawable.sync_list),
        STATUS_DOT_RED(R.drawable.status_dot_red),
        STATUS_DOT_GREEN(R.drawable.status_dot_green),
        STATUS_DOT_GREY(R.drawable.status_dot_gray),
        STATUS_DOT_YELLOW(R.drawable.status_dot_yellow),
        DIGITAL_FIRES(R.drawable.digital_fires),
        DOCUMENT(R.drawable.document),
        EXPORT(R.drawable.export),
        ATAK(com.atakmap.android.util.ATAKConstants.getIconId()),
        IMPORT_DATABASE(R.drawable.import_database),
        IMPORT_DATABASE_COMPLETE(R.drawable.import_database_complete),
        NETWORK_ERROR(R.drawable.ic_network_error_notification_icon),
        // temporary
        NEUTRON_NOTIFICATION(R.drawable.ic_neutron_alarm_notification),
        GAMMA_NOTIFICATION(R.drawable.ic_gamma_alarm_notification),
        RAD_SENSOR_DELETE(R.drawable.radsensor_delete),
        RAD_SENSOR_PERMISSIONS(R.drawable.radsensor_permissions),
        NUC_EFFECT(R.drawable.nuc_effect),
        CHEM_EFFECT(R.drawable.chem_effect),
        IWMDT(R.drawable.iwmdt_logo),
        //
        X(R.drawable.x),
        CROSSHAIR(R.drawable.survey_entry_normal);

        final private int resid;

        GeneralIcon(final int resid) {
            this.resid = resid;
        }

        public int getID() {
            return resid;
        }
    }

    /**
     * Class used to represent the color of the icon backed by the integer representation in ARGB format.
     */
    final public static class NotificationColor {
        int color;

        public NotificationColor(int color) {
            this.color = color;
        }

        public int toArgb() {
            return color;
        }
    }

    /**
     * Color choices for icons.
     */
    public static final NotificationColor GREEN = new NotificationColor(
            Color.GREEN);
    public static final NotificationColor RED = new NotificationColor(
            Color.RED);
    public static final NotificationColor YELLOW = new NotificationColor(
            Color.YELLOW);
    public static final NotificationColor WHITE = new NotificationColor(
            Color.WHITE);
    public static final NotificationColor BLUE = new NotificationColor(
            Color.BLUE);

    public static final String TAG = "NotificationUtil";

    static public final int DEFAULT_NOTIFY_ID = 1338;

    private int currentNotifyId = DEFAULT_NOTIFY_ID;

    private final Map<String, Integer> _notificationMap = new HashMap<>();

    private final ConcurrentLinkedQueue<NotificationListener> notificationListeners = new ConcurrentLinkedQueue<>();

    private final Map<Integer, Notification.Builder> builders = new HashMap<>();

    static NotificationUtil _instance = null;

    NotificationFader nf = null;
    NotificationManager nm = null;
    Context ctx = null;

    private final boolean nwDevice;

    private NotificationUtil() {

        /**
         * Narrow change for NW deployment.   Please revert once NW fixes the issue in thier ROM.
         */
        final String model = android.os.Build.MODEL;
        final File f = new File("/proc/NettWarrior/nwplatform");
        if (model.equals("SM-G900T") && IOProviderFactory.exists(f)) {
            nwDevice = true;
            Log.d(TAG,
                    "NettWarrior S5 Detected with possible notification bug.");
        } else {
            nwDevice = false;
        }

    }

    synchronized static public NotificationUtil getInstance() {
        if (_instance == null) {
            _instance = new NotificationUtil();
        }
        return _instance;
    }

    @SuppressLint("WrongConstant")
    public synchronized void initialize(Context svc) {
        if (nm == null) {
            Log.i(TAG, "init notifications");
            nm = (NotificationManager) svc
                    .getSystemService(Context.NOTIFICATION_SERVICE);

            // make sure to rename the channel and delete the old one if you want to
            // modify the importance and / sound capabilities by default
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        "com.atakmap.app.def",
                        "TAK Notifications",
                        NotificationManager.IMPORTANCE_DEFAULT); // correct Constant
                channel.setSound(null, null);
                nm.createNotificationChannel(channel);

                channel = new NotificationChannel(
                        "com.atakmap.app.vib_nosound",
                        "TAK Notifications",
                        NotificationManager.IMPORTANCE_DEFAULT);
                channel.setSound(null, null);
                channel.enableVibration(true);
                nm.createNotificationChannel(channel);

                channel = new NotificationChannel(
                        "com.atakmap.app.vib_sound",
                        "TAK Notifications",
                        NotificationManager.IMPORTANCE_DEFAULT);
                channel.enableVibration(true);

                nm.createNotificationChannel(channel);
            }

            ctx = svc;
            nf = new NotificationFader(nm, ctx);
        }
    }

    public void dispose() {
        _instance = null;
        if (nf != null)
            nf.dispose();
    }

    /**
     * Cancels all notification managed by the notification manager.
     */
    public void cancelAll() {
        if (nm != null)
            nm.cancelAll();
    }

    /**
     * Add a listener to remove a notification should the marker be removed.
     *
     * @param marker The marker that needs to be watched.
     */

    public void addOnRemoveListener(final MapItem marker) {
        if (marker != null && marker.getGroup() != null) { //NPE found by Matt.
            marker.getGroup().addOnItemListChangedListener(
                    new MapGroup.OnItemListChangedListener() {
                        @Override
                        public void onItemAdded(MapItem mapItem,
                                MapGroup mapGroup) {

                        }

                        @Override
                        public void onItemRemoved(MapItem mapItem,
                                MapGroup mapGroup) {
                            if (mapItem.getUID().equals(marker.getUID())) {
                                if (_notificationMap.containsKey(marker
                                        .getUID()))
                                    clearNotification(_notificationMap
                                            .get(marker.getUID()));
                                mapGroup.removeOnItemListChangedListener(this);
                            }
                        }
                    });
        }
    }

    /**
     * Determine what notification id to use, based on the current list of notifications.
     *
     * @param uid UID that we are creating the notification for
     * @param id ID that has been provided.  Return this if the notification is not present.
     * @return Best notification ID determined.
     */

    private int getIndex(String uid, int id) {
        int output = id; //Save current index
        if (_notificationMap.containsKey(uid))
            output = _notificationMap.get(uid); //Check for index in current set and save
        if (indexInUse(uid, output) != null) { //check if the index is already in use
            output = id; //use the input value if it is
            String key = indexInUse(uid, output); //check the input value to see if it is in use
            if (key != null) { //and remove it if it is
                synchronized (_notificationMap) {
                    _notificationMap.remove(key);
                }
            }

        }
        synchronized (_notificationMap) {
            _notificationMap.put(uid, output); //Update the list
        }
        return output;
    }

    /**
     * Check to see if a notification ID is already marked as being in use by another UID.
     *
     * @param uid UID we are looking for (exclude this UID from the check)
     * @param index Notification ID we're looking for
     * @return The UID that is currently using the specified notification UID, or null if unfound.
     */

    private String indexInUse(String uid, int index) {
        synchronized (_notificationMap) {
            for (String key : _notificationMap.keySet()) { //check to see if the index is in use
                if (!key.equals(uid) && _notificationMap.get(key).equals(index))
                    return key;
            }
            return null;
        }
    }

    /**
     * Produces a standard notification without a ticker, no sound, vibrate, or flashing.   The notificatiion
     * can be dismissed.   The notification identifier is automatically generated.
     */
    public int postNotification(int icon, String title, String ticker,
            String msg) {
        final int id = reserveNotifyId();
        postNotification(id, icon, null, title, ticker, msg, null,
                false, false, false, true);
        return id;
    }

    /**
     * Produces a standard notification without a ticker, no sound, vibrate, or flashing.   The notificatiion
     * can be dismissed.   
     */
    public void postNotification(int notifyId, int icon, String title,
            String ticker, String msg) {
        postNotification(notifyId, icon, null, title, ticker, msg, null, false,
                false, false, true);
    }

    /**
     * Produces a standard notification without a ticker, no sound, vibrate, or flashing.   The notificatiion
     * can be dismissed.   The user supplied notification is honored within the application.   The notification 
     * identifier is automatically generated.
     */
    public int postNotification(int icon, String title, String ticker,
            String msg,
            Intent notificationIntent) {
        final int id = reserveNotifyId();
        postNotification(id, icon, null, title, ticker, msg,
                notificationIntent, false, false, false, true);
        return id;
    }

    /**
     * Produces a standard notification without a ticker, no sound, vibrate, or flashing.   The notificatiion
     * can be dismissed.   The user supplied notification is honored within the application.
     */
    public void postNotification(final int notifyId,
            final int icon,
            final String title,
            final String msg,
            final Intent notificationIntent,
            final boolean user) {
        postNotification(notifyId, icon, null, title, null, msg,
                notificationIntent,
                false, false, false, user);
    }

    /**
     * Produces a standard notification without a ticker, no sound, vibrate, or flashing.   The notificatiion
     * can be dismissed.   The notification identifier is automatically generated.
     */
    public int postNotification(int icon, NotificationColor color, String title,
            String ticker,
            String msg) {
        final int id = reserveNotifyId();
        postNotification(id, icon, color, title, ticker, msg, null,
                false, false, false, true);
        return id;
    }

    /**
     * Produces a standard notification without a ticker, no sound, vibrate, or flashing.   The notificatiion
     * can be dismissed.   
     */
    public void postNotification(int notifyId, int icon,
            NotificationColor color, String title,
            String ticker, String msg) {
        postNotification(notifyId, icon, color, title, ticker, msg, null, false,
                false, false, true);
    }

    /**
     * Produces a standard notification without a ticker, no sound, vibrate, or flashing.   The notificatiion
     * can be dismissed.   The user supplied notification is honored within the application.   The notification 
     * identifier is automatically generated.
     */
    public int postNotification(int icon, NotificationColor color, String title,
            String ticker,
            String msg,
            Intent notificationIntent) {
        final int id = reserveNotifyId();
        postNotification(id, icon, color, title, ticker, msg,
                notificationIntent, false, false, false, true);
        return id;
    }

    /**
     * Produces a standard notification without a ticker, no sound, vibrate, or flashing.   The notificatiion
     * can be dismissed.   The user supplied notification is honored within the application.
     */
    public void postNotification(final int notifyId,
            final int icon,
            final NotificationColor color,
            final String title,
            final String msg,
            final Intent notificationIntent,
            final boolean user) {
        postNotification(notifyId, icon, color, title, null, msg,
                notificationIntent,
                false, false, false, user);
    }

    /**
     * Essentially a wrapper to postNotification in NotificationUtils.  All it does is update the
     * notifyID to the correct value, to prevent duplicates.
     *
     * @param notifyId the identifying number for the notification
     * @param icon The resource id of the icon to put in the status bar.
     * @param color The color used to display the icon.
     * @param title The title that goes in the expanded entry.
     * @param ticker The text that flows by in the status bar when the notification first activates
     *               or null, if no ticker is to be used.
     * @param msg The text that goes in the expanded entry.
     * @param notificationIntent the intent to fire when the notification is selected within ATAK.
     *                           If ATAK is not in the front, it will bring ATAK to the front and
     *                           pass the specified intent within the system.
     * @param user specify if the user can dismiss the notification.
     */

    public void postMarkerNotification(MapItem mapItem,
            final int notifyId,
            final int icon,
            final NotificationColor color,
            final String title,
            final String ticker,
            final String msg,
            final Intent notificationIntent,
            final boolean user) {
        int updatedNotifyID = getIndex(mapItem.getUID(), notifyId);
        NotificationUtil.getInstance().postNotification(updatedNotifyID, icon,
                color,
                title, ticker, msg, notificationIntent, user);
        mapItem.setMetaBoolean("notificationCreated", true);
        addOnRemoveListener(mapItem);
    }

    /**
     * Produces a standard notification with a ticker, no sound, vibrate, or flashing.   The notification
     * can be dismissed.   The user supplied notification is honored within the application.
     */
    public void postNotification(final int notifyId,
            final int icon,
            final NotificationColor color,
            final String title,
            final String ticker,
            final String msg,
            final Intent notificationIntent,
            final boolean user) {
        postNotification(notifyId, icon, color, title, ticker, msg,
                notificationIntent, false, false, false, user);
    }

    /**
     * Produces a standard notification with a ticker, no sound, vibrate, or flashing without a specified color.   
     * The notification can be dismissed.   The user supplied notification is honored within the application.
     */
    public void postNotification(final int notifyId,
            final int icon,
            final String title,
            final String ticker,
            final String msg,
            final Intent notificationIntent,
            final boolean user) {
        postNotification(notifyId, icon, null, title, ticker, msg,
                notificationIntent, false, false, false, user);
    }

    /**
     * Produces a standard notification with a ticker, no sound, vibrate, or flashing without a specified color.   
     * The notification can be dismissed.   The user supplied notification is honored within the application.
     */
    public void postNotification(final int notifyId,
            final int icon,
            final String title,
            final String ticker,
            final String msg,
            final Intent notificationIntent,
            final boolean vibrate,
            final boolean chime,
            final boolean blink,
            final boolean user) {
        postNotification(notifyId, icon, null, title, ticker, msg,
                notificationIntent, vibrate, chime, blink, user);
    }

    /**
     * Produces a standard notification with or without a ticker.
     *
     * @param notifyId the identifying number for the notification
     * @param icon The resource id of the icon to put in the status bar.
     * @param color The color that may be applied to the icon.  
     * Can be null to indicate that the default behavior should be used.
     * @param title The title that goes in the expanded entry.
     * @param ticker The text that flows by in the status bar when the notification first activates or
                     null, if no ticker is to be used.
     * @param msg The text that goes in the expanded entry.
     * @param notificationIntent the intent to fire when the notification is selected within ATAK.   If ATAK
     * is not in the front, it will bring ATAK to the front and pass the specified intent within the 
     * system.
     * @param vibrate true to vibrate false to not.
     * @param chime true to chime false to not.
     * @param blink true to blink false to not.
     * @param user set true if the user can dismiss the notification.
     */
    public void postNotification(final int notifyId,
            final int icon,
            final NotificationColor color,
            final String title,
            final String ticker,
            final String msg,
            final Intent notificationIntent,
            final boolean vibrate,
            final boolean chime,
            final boolean blink,
            final boolean user) {

        if (ctx == null) {
            Log.d(TAG, "not initialized, ignore: " + title + ": " + msg);
            return;
        }

        Intent atakFrontIntent = new Intent();
        atakFrontIntent.setComponent(ATAKConstants.getComponentName());

        if (notificationIntent != null) {
            atakFrontIntent.putExtra("internalIntent", notificationIntent);
        }

        atakFrontIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // requires the use of currentTimeMillis
        PendingIntent contentIntent = PendingIntent.getActivity(ctx,
                (int) System.currentTimeMillis(),
                atakFrontIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        int valid_icon = R.drawable.ic_menu_plus;
        try {
            Drawable d = ctx.getResources().getDrawable(icon);
            if (d != null)
                valid_icon = icon;
        } catch (Exception e) {
            Log.e(TAG, "invalid icon (" + icon
                    + ") passed in to notification util, must be a core resource",
                    new Exception());
        }

        synchronized (builders) {
            Notification.Builder nBuilder = builders.get(notifyId);
            if (nBuilder == null) {
                if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    nBuilder = new Notification.Builder(ctx);
                } else {
                    if (chime) {
                        nBuilder = new Notification.Builder(ctx,
                                "com.atakmap.app.vib_sound");
                    } else if (vibrate) {
                        nBuilder = new Notification.Builder(ctx,
                                "com.atakmap.app.vib_nosound");
                    } else {
                        nBuilder = new Notification.Builder(ctx,
                                "com.atakmap.app.def");
                    }
                }
                builders.put(notifyId, nBuilder);
            }

            nBuilder.setContentTitle((title == null) ? "" : title)
                    .setContentText((msg == null) ? "" : msg)
                    .setSmallIcon(valid_icon)
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true);
            if (color != null)
                nBuilder.setColor(color.toArgb());

            setGroup(nBuilder);

            if (!nwDevice)
                nBuilder.setStyle(new Notification.BigTextStyle()
                        .bigText((msg == null) ? "" : msg));

            if (ticker != null)
                nBuilder.setTicker(ticker);

            if (!user) {
                nBuilder.setOngoing(true);
                nBuilder.setAutoCancel(false);
            } else {
                // if we are recycling a notification, reset the state of the 
                // nBuilder
                nBuilder.setOngoing(false);
                nBuilder.setAutoCancel(true);
            }

            final Notification notification = nBuilder.build();

            if (vibrate)
                notification.defaults |= Notification.DEFAULT_VIBRATE;
            else {
                notification.defaults &= ~Notification.DEFAULT_VIBRATE;
            }

            if (blink) {
                notification.ledARGB = BLUE.toArgb();
                notification.ledOnMS = 100;
                notification.ledOffMS = 300;
            }

            if (chime)
                notification.defaults |= Notification.DEFAULT_SOUND;
            else {
                notification.defaults &= ~Notification.DEFAULT_SOUND;
            }

            postNotification(notifyId, notification, user);

            if (MetricsApi.shouldRecordMetric()) {
                Bundle b = new Bundle();
                b.putInt("notifyId", notifyId);
                b.putInt("icon", icon);
                b.putString("title", (title != null) ? title : "");
                b.putString("ticker", (ticker != null) ? ticker : "");
                b.putString("msg", (msg != null) ? msg : "");
                if (notificationIntent != null) {
                    b.putString("notificationIntentAction",
                            notificationIntent.getAction());
                    if (notificationIntent.getExtras() != null)
                        b.putBundle("notificationIntentExtras",
                                notificationIntent.getExtras());
                }
                b.putBoolean("vibrate", vibrate);
                b.putBoolean("chime", chime);
                b.putBoolean("blink", blink);
                b.putBoolean("user", user);
                MetricsApi.record("notification", b);
            }

        }

    }

    // fix for ATAK-6992 Android 7 notification display incorrect/misleading icon
    private void setGroup(Notification.Builder nb) {
        String gid = java.util.UUID.randomUUID().toString();
        nb.setGroup(gid);
    }

    /**
     * Obtains the notification builder currently being used by the specified Notification Identifier.
     * @param notifyId the notification id used.
     */
    public Notification.Builder getNotificationBuilder(final int notifyId) {
        synchronized (builders) {
            return builders.get(notifyId);
        }
    }

    /**
     * Clears a notification managed referenced by its notification identifier.
     * @param notifyId the notification id used.
     */
    public void clearNotification(final int notifyId) {
        try {
            if (nm != null)
                nm.cancel(notifyId);
        } catch (Exception e) {
            Log.d(TAG, "device error occurred during notification cancelation");
        }
        synchronized (builders) {
            builders.remove(notifyId);
        }
    }

    /**
     * Reserves a notification identifier that is unique for the current run of the application.
     */
    synchronized public int reserveNotifyId() {
        final int retval = this.currentNotifyId;
        this.currentNotifyId++;
        return retval;
    }

    /**
     * Given a notification, post it either under the control of the notification fader or not.
     * @param notifyId the notification id used.
     * @param notification the actual notification.
     * @param fade false will remove this notification from the fader, true will begin or reset
     * the time for this notification to be removed.
     */
    public void postNotification(final int notifyId,
            final Notification notification, boolean fade) {
        if (nf != null) {
            if (fade) {
                nf.notifyFader(notifyId);
            } else {
                nf.removeFader(notifyId);
            }
        }

        try {
            if (nm != null) {
                this.nm.notify(notifyId, notification);
                this.fireNotificationChanged(notifyId, notification);
            }
        } catch (Exception dse) {
            // catch android.os.DeadSystemException 
            Log.e(TAG, "error", dse);
        }
    }

    public interface NotificationListener {
        /**
         * Allows for notification listeners from plugins to be registered to listen for notifications
         * changes.   This will only work with notifications that are posted through the NotificationUtil
         * class.
         * @param notificationIdentifier the notification identifier, can be used in conjunction with
         *                               notification util to cancel or dismiss the notification
         * @param notification the notification that has changed.   At the time it is processed it
         *                     may have already been changed or cancelled.
         */
        void onNotificationReceived(int notificationIdentifier,
                Notification notification);
    }

    /**
     * Registers a notification listener with the centralized notification class.
     * @param listener the listener to register
     */
    public void registerNotificationListener(NotificationListener listener) {
        notificationListeners.add(listener);
    }

    /**
     * Unregisters a notification listener with the centralized notification class.
     * @param listener the listener to unregister
     */
    public void unregisterNotificationListener(NotificationListener listener) {
        notificationListeners.remove(listener);
    }

    private void fireNotificationChanged(int notificationIdentifier,
            Notification notification) {
        for (NotificationListener notificationListener : notificationListeners) {
            try {
                notificationListener.onNotificationReceived(
                        notificationIdentifier, notification);
            } catch (Exception e) {
                Log.e(TAG, "error notifying: " + notificationListener, e);
            }
        }
    }

}
