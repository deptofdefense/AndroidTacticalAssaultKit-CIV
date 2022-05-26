
package com.atakmap.app;

import android.annotation.SuppressLint;
import android.os.Build;
import android.content.Intent;
import android.content.Context;
import android.os.PowerManager;
import android.net.Uri;
import android.widget.TextView;

import android.net.ConnectivityManager;

import com.atakmap.coremap.log.Log;
import com.atakmap.android.gui.HintDialogHelper;

import android.provider.Settings;
import android.content.BroadcastReceiver;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.util.NotificationUtil;

/**
 * https://developer.android.com/training/monitoring-device-state/doze-standby.html
 * see support for other use cases.
 *
 * An app holding the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission can trigger a system dialog to 
 * let the user add the app to the whitelist directly, without going to settings. 
 * The app fires a ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS Intent to trigger the dialog. 
 *
 * 
 * See:
 * Task automation app     
 *    App's core function is scheduling automated actions, such as for instant messaging, 
 *    voice calling, new photo management, or location actions.     Acceptable     
 * Peripheral device companion app     
 *    App's core function is maintaining a persistent connection with the peripheral device 
 *    for the purpose of providing the peripheral device internet access.     Acceptable
 */

class DozeManagement {

    //public static final String ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS =
    //        "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS";

    private static final String ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS";
    private static final String LAUNCH_DATA_USAGE = "com.atakmap.app.doze.LaunchDataUsage";

    private static final int NETWORK_NOTIFICATION = 3365188;
    private static final int PS_NOTIFICATION = 3365189;

    private static BroadcastReceiver nwRestrictionChange;
    private static BroadcastReceiver psRestrictionChange;

    public static final String TAG = "DozeManagement";

    static void dispose() {
        if (nwRestrictionChange != null) {
            AtakBroadcast.getInstance()
                    .unregisterSystemReceiver(nwRestrictionChange);
            AtakBroadcast.getInstance()
                    .unregisterReceiver(nwRestrictionChange);
        }
        if (psRestrictionChange != null) {
            AtakBroadcast.getInstance()
                    .unregisterSystemReceiver(psRestrictionChange);

        }
    }

    static void checkDoze(final Context context) {

        final int build = Build.VERSION.SDK_INT;
        final String packageName = context.getPackageName();

        if (build >= 24) {

            // register the listener

            AtakBroadcast.getInstance().registerSystemReceiver(
                    nwRestrictionChange = new BroadcastReceiver() {
                        public void onReceive(final Context c,
                                final Intent intent) {
                            final String action = intent.getAction();
                            if (action == null)
                                return;
                            if (action.equals(
                                    ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)) {
                                checkAndWarnRestrictingBackgroundData(context);
                            } else if (action.equals(LAUNCH_DATA_USAGE)) {
                                launchDataUsageScreen(context);
                            }
                        }
                    }, new DocumentedIntentFilter(
                            ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED));

            AtakBroadcast.getInstance().registerReceiver(nwRestrictionChange,
                    new DocumentedIntentFilter(LAUNCH_DATA_USAGE));

            checkAndWarnRestrictingBackgroundData(context);

        }

        if (build < 23)
            return;

        final Intent intent = new Intent();

        Log.d(TAG,
                "attempting to ignore battery optimizations: " + packageName);

        boolean retval = isIgnoringBatteryOptimizations(context, packageName);
        if (!retval) {
            intent.setAction(ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + packageName));

            TextView tv = new TextView(context);
            tv.setText(context.getString(R.string.preferences_text421b));

            HintDialogHelper
                    .showHint(
                            context,
                            context.getString(R.string.preferences_text420b),
                            tv,
                            "batoptimization.issue",
                            new HintDialogHelper.HintActions() {
                                @Override
                                public void preHint() {
                                }

                                @Override
                                public void postHint() {
                                    context.startActivity(intent);
                                }
                            }, false);
        }

        boolean powerSaving = isPowerSaveMode(context);
        if (powerSaving) {
            Log.d(TAG, "device is in power save mode");
            warnAndCorrectBatterySavings(context);
        } else {
            Log.d(TAG, "device is not in power save mode");
        }
        AtakBroadcast.getInstance().registerSystemReceiver(
                psRestrictionChange = new BroadcastReceiver() {
                    public void onReceive(final Context c,
                            final Intent intent) {
                        final PowerManager pm = (PowerManager) context
                                .getSystemService(Context.POWER_SERVICE);
                        if (pm != null) {
                            if (pm.isPowerSaveMode()) {
                                Log.d(TAG, "device is in power save mode");
                                warnAndCorrectBatterySavings(context);
                            } else {
                                Log.d(TAG, "device is not in power save mode");
                                NotificationUtil.getInstance()
                                        .clearNotification(PS_NOTIFICATION);
                            }
                        }
                    }
                }, new DocumentedIntentFilter(
                        PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));

    }

    static private boolean isIgnoringBatteryOptimizations(Context context,
            String packageName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final PowerManager pm = (PowerManager) context
                    .getSystemService(Context.POWER_SERVICE);
            if (pm != null)
                return pm.isIgnoringBatteryOptimizations(packageName);
        }
        return false;
    }

    static private boolean isPowerSaveMode(Context context) {
        final PowerManager pm = (PowerManager) context
                .getSystemService(Context.POWER_SERVICE);
        if (pm != null)
            return pm.isPowerSaveMode();

        return false;
    }

    @SuppressLint("MissingPermission")
    static private void checkAndWarnRestrictingBackgroundData(
            final Context context) {

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }

        final ConnectivityManager connMgr = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr != null && connMgr.isActiveNetworkMetered()) {
            final int state = connMgr.getRestrictBackgroundStatus();

            if (state == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED) {
                // Device is restricting metered network activity while 
                // application is running on background. 
                Log.d(TAG,
                        "network status: RESTRICTED_BACKGROUND_STATUS_ENABLED");
                warnAndCorrect(context);
            } else if (state == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED) {

                // Device is restricting metered network activity while 
                // application is running on background, but application 
                // is allowed to bypass it. 
                Log.d(TAG,
                        "network status: RESTRICTED_BACKGROUND_STATUS_WHITELISTED");
                NotificationUtil.getInstance()
                        .clearNotification(NETWORK_NOTIFICATION);

            } else if (state == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED) {

                // Device is not restricting metered network activity while 
                // application is running on background. 
                Log.d(TAG,
                        "network status: RESTRICTED_BACKGROUND_STATUS_DISABLED");
                NotificationUtil.getInstance()
                        .clearNotification(NETWORK_NOTIFICATION);
            }

        } else {
            Log.d(TAG, "network status: CONNECTION_NOT_ACTIVELY_METERED");
            NotificationUtil.getInstance()
                    .clearNotification(NETWORK_NOTIFICATION);
        }
    }

    private static void warnAndCorrect(final Context context) {
        Intent i = new Intent();
        i.setAction(LAUNCH_DATA_USAGE);

        NotificationUtil.getInstance().postNotification(NETWORK_NOTIFICATION,
                R.drawable.caution,
                NotificationUtil.YELLOW,
                context.getString(R.string.network_data_restriction),
                context.getString(R.string.network_data_restriction_summary),
                i, false);

    }

    private static void warnAndCorrectBatterySavings(final Context context) {
        NotificationUtil.getInstance().postNotification(PS_NOTIFICATION,
                R.drawable.caution,
                NotificationUtil.YELLOW,
                context.getString(R.string.ps_restriction),
                context.getString(R.string.ps_restriction_summary),
                null, false);
    }

    private static void launchDataUsageScreen(final Context context) {

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }

        Intent intent = new Intent(
                Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                Uri.parse("package:" + context.getPackageName()));
        try {
            context.startActivity(intent);
        } catch (Exception ignored) {
        }

    }
}
