
package com.atakmap.app;

import android.annotation.TargetApi;
import android.content.Context;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.Manifest;
import com.atakmap.coremap.log.Log;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

public class Permissions {

    private final static String TAG = "Permissions";

    final static int REQUEST_ID = 90402;

    final static String ACCESS_BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"; // Android 29 Requirement

    final static String[] PermissionsList = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS,
            Manifest.permission.CAMERA,
            Manifest.permission.VIBRATE,
            Manifest.permission.SET_WALLPAPER,
            Manifest.permission.INTERNET,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.DISABLE_KEYGUARD,
            Manifest.permission.GET_TASKS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.NFC,
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            ACCESS_BACKGROUND_LOCATION,
            "com.atakmap.app.ALLOW_TEXT_SPEECH",
            //Manifest.permission.DEVICE_POWER,
            //Manifest.permission.READ_CONTACTS
    };

    static boolean checkPermissions(final Activity a) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        int result = 0;
        for (String permission : PermissionsList) {
            if (Build.VERSION.SDK_INT >= 29
                    || !ACCESS_BACKGROUND_LOCATION.equals(permission)) {
                result += a.checkSelfPermission(permission);
            }
        }

        if (Build.VERSION.SDK_INT >= 29) {
            if (PackageManager.PERMISSION_GRANTED != a
                    .checkCallingOrSelfPermission(ACCESS_BACKGROUND_LOCATION)) {
                Log.d(TAG,
                        "permission not granted for background location listenening");
                showWarning(a);
                return false;
            }
        }

        if (result != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG,
                    "permissions have not been granted for all of the things");
            a.requestPermissions(PermissionsList, REQUEST_ID);
            return false;
        } else {
            Log.d(TAG, "permissions have been granted for all of the things");

            // This is clunky and probably not needed since it does not seem 
            // to effect behavior in the application 12/6/2019 SHB

            //if (!Settings.System.canWrite(a.getApplicationContext())) {
            //    Intent intent = 
            //         new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, 
            //                    Uri.parse("package:" + a.getPackageName()));
            //    a.startActivityForResult(intent, 200);
            //}

            return true;
        }

    }

    @TargetApi(29)
    private static void showWarning(final Activity a) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(a);
        builder.setMessage(R.string.background_permission_warning);

        builder.setCancelable(false);
        builder.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        a.requestPermissions(PermissionsList, REQUEST_ID);
                    }
                });
        AlertDialog ad = builder.create();
        try {
            ad.show();
        } catch (Exception ignored) {
        }
    }

    static void displayNeverAskAgainDialog(final Activity a) {

        AlertDialog.Builder builder = new AlertDialog.Builder(a);
        builder.setMessage(R.string.permission_warning);

        builder.setCancelable(false);
        builder.setPositiveButton(R.string.permit_manually,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        Intent intent = new Intent();
                        intent.setAction(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", a.getPackageName(),
                                null);
                        intent.setData(uri);
                        a.startActivity(intent);
                        a.finish();
                    }
                });
        builder.show();
    }

    /**
     * Check to make sure that the required permission has been granted.
     * @param context the context
     * @param permission the permission
     * @return true if the permission has been granted.
     */
    public static boolean checkPermission(final Context context,
            final String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        final int result = context.checkSelfPermission(permission);
        if (result == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            Log.e(TAG, "permission denied: " + permission, new Exception());
            return false;
        }

    }

}
