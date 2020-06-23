
package com.atakmap.android.util;

import com.atakmap.coremap.log.Log;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.io.File;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import android.widget.TextView;
import android.view.View;

public class PdfHelper {

    public static final String TAG = "PdfHelper";

    /** 
    * Helper function used by most users of the PdfHelper to see if adobe or another pdf reader is 
    * installed.
    * @param context context used to check if something is installed.
    * @param pkg the package name
    */
    public static boolean isInstalled(final Context context, final String pkg) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Package not installed: " + pkg, e);
        }
        return false;
    }

    /**
     * Helper function that is used to launch Adobe or some other PDF reader.
     * @param context the context used to launch the application.
     * @param file the file to use when launching the activity
     */
    public static void launchAdobe(final Context context, final String file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);

            com.atakmap.android.util.FileProviderHelper.setDataAndType(context,
                    intent, new File(file), "application/pdf");

            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "error launching a pdf viewer", e);
        }
    }

    /**
     * Checks to see if adobe is installed and present. Issues a warning
     * otherwise.
     */
    public static void checkAndWarn(final Context context, final String file) {
        final SharedPreferences _prefs = PreferenceManager
                .getDefaultSharedPreferences(context);
        boolean displayHint = _prefs.getBoolean("atak.hint.missingadobe", true);

        if (!isInstalled(context, "com.adobe.reader") && displayHint) {

            View v = LayoutInflater.from(context)
                    .inflate(com.atakmap.app.R.layout.hint_screen, null);
            TextView tv = v
                    .findViewById(com.atakmap.app.R.id.message);
            tv.setText(
                    "It is recommended that you use the official Acrobat Reader.\nViewing the documents in other Android PDF applications may not work properly.");

            new AlertDialog.Builder(context).setTitle("Acrobat Reader Missing")
                    .setView(v).setCancelable(false).setPositiveButton("OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int id) {
                                    _prefs.edit()
                                            .putBoolean(
                                                    "atak.hint.missingadobe",
                                                    false)
                                            .apply();
                                    launchAdobe(context, file);
                                }
                            })
                    .create().show();
        } else {
            launchAdobe(context, file);
        }
    }

}
