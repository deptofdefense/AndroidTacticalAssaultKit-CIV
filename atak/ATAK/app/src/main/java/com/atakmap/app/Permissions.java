
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
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;

public class Permissions {

    private final static String TAG = "Permissions";

    final static int REQUEST_ID = 90402;

    final static int LOCATION_REQUEST_ID = 90403;

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
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,

            // 23 - protection in place
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,

            // 26 - protection in place
            Manifest.permission.REQUEST_DELETE_PACKAGES,

            // 26 - protections in place
            Manifest.permission.READ_PHONE_NUMBERS,

            "com.atakmap.app.ALLOW_TEXT_SPEECH",

    };

    final static String[] locationPermissionsList = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS,

            // 29 - protection in place
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    };

    static boolean checkPermissions(final Activity a) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        int result = 0;
        for (String permission : PermissionsList) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                    && Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            .equals(permission))
                continue;

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                    && Manifest.permission.REQUEST_DELETE_PACKAGES
                            .equals(permission))
                continue;

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                    && Manifest.permission.READ_PHONE_NUMBERS
                            .equals(permission))
                continue;

            // for this specific flavor, go ahead and do not enable SEND_SMS
            if (BuildConfig.FLAVOR.equalsIgnoreCase("civSmall")
                    && Manifest.permission.SEND_SMS.equals(permission))
                continue;

            result += a.checkSelfPermission(permission);
        }

        if (result != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG,
                    "permissions have not been granted for all of the things");
            a.requestPermissions(PermissionsList, REQUEST_ID);
            return false;
        } else {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (PackageManager.PERMISSION_GRANTED != a
                        .checkCallingOrSelfPermission(
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    Log.d(TAG,
                            "permission not granted for background location listening");
                    showWarning(a);
                    return false;
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    showFileSystemWarning(a);
                    return false;
                }
            }

            Log.d(TAG, "permissions have been granted for all of the things");
            return true;
        }

    }

    @TargetApi(30)
    private static void showFileSystemWarning(final Activity a) {
        LayoutInflater li = LayoutInflater.from(a);
        View v = li.inflate(R.layout.storage_permission_guidance, null);

        final AlertDialog.Builder builder = new AlertDialog.Builder(a);
        builder.setTitle(R.string.file_system_access_changes);
        builder.setView(v);
        builder.setIcon(R.drawable.ic_database);
        builder.setCancelable(false);

        builder.setPositiveButton(R.string.i_understand,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final Uri uri = Uri
                                .parse("package:" + BuildConfig.APPLICATION_ID);
                        final Intent intent = new Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                uri);
                        a.startActivityForResult(intent, REQUEST_ID);
                    }
                });

        builder.setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        a.finish();
                    }
                });

        builder.show();
    }

    @TargetApi(23)
    private static void showWarning(final Activity a) {
        LayoutInflater li = LayoutInflater.from(a);
        View v = li.inflate(R.layout.background_location, null);
        final AlertDialog.Builder builder = new AlertDialog.Builder(a);
        builder.setTitle(R.string.use_your_location_title);
        builder.setView(v);
        builder.setIcon(R.drawable.ic_menu_mylocation);

        builder.setCancelable(false);
        builder.setPositiveButton(R.string.i_understand,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            View view = LayoutInflater.from(a)
                                    .inflate(
                                            R.layout.location_permission_warning,
                                            null);

                            AlertDialog.Builder ab = new AlertDialog.Builder(a);
                            ab.setTitle(R.string.android_11_warning);
                            ab.setView(view);
                            ab.setCancelable(false);
                            ab.setPositiveButton(R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(
                                                DialogInterface dialog,
                                                int which) {
                                            a.requestPermissions(
                                                    locationPermissionsList,
                                                    LOCATION_REQUEST_ID);
                                        }
                                    });
                            ab.show();
                        } else {
                            a.requestPermissions(locationPermissionsList,
                                    LOCATION_REQUEST_ID);
                        }
                    }
                });
        AlertDialog ad = builder.create();
        try {
            ad.show();
        } catch (Exception ignored) {
        }
    }

    static void displayNeverAskAgainDialog(final Activity a) {

        View view = LayoutInflater.from(a).inflate(
                R.layout.general_permission_guidance,
                null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            final int result = a.checkSelfPermission(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            if (result != PackageManager.PERMISSION_GRANTED) {
                view = LayoutInflater.from(a).inflate(
                        R.layout.location_permission_guidance,
                        null);
            }
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(a);
        builder.setTitle(R.string.required_missing_permissions);
        builder.setView(view);
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.i_understand,
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
                        a.startActivityForResult(intent, REQUEST_ID);
                    }
                }).setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
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

    /**
     * Handles the mechanics of the permission request.
     * @param requestCode must be Permissions.REQUEST_ID
     * @param permissions the list of permissions requested
     * @param grantResults the results for each of the permissions
     * @return true if the permissions have all been granted
     */
    static boolean onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {

        Log.d(TAG, "onRequestPermissionsResult called: " + requestCode);
        switch (requestCode) {
            case Permissions.LOCATION_REQUEST_ID:
            case Permissions.REQUEST_ID:
                if (grantResults.length > 0) {
                    boolean b = true;
                    for (int i = 0; i < grantResults.length; ++i) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                                && Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                        .equals(permissions[i]))
                            continue;

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                                && Manifest.permission.REQUEST_DELETE_PACKAGES
                                        .equals(permissions[i]))
                            continue;

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                                && Manifest.permission.READ_PHONE_NUMBERS
                                        .equals(permissions[i]))
                            continue;

                        // for this specific flavor, go ahead and do not enable SEND_SMS
                        if (BuildConfig.FLAVOR.equalsIgnoreCase("civSmall")
                                && Manifest.permission.SEND_SMS
                                        .equals(permissions[i]))
                            continue;

                        b = b && (grantResults[i] == PackageManager.PERMISSION_GRANTED);
                        if (grantResults[i] != PackageManager.PERMISSION_GRANTED)
                            Log.d(TAG, "onRequestPermissionResult not granted: "
                                    + permissions[i]);
                    }

                    return b;

                }
        }
        return false;

    }

}
