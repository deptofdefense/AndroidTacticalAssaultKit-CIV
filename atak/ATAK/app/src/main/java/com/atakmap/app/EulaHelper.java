
package com.atakmap.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;

import com.atakmap.android.gui.WebViewer;
import com.atakmap.coremap.log.Log;
import android.preference.PreferenceManager;

class EulaHelper {

    public static final String TAG = "EulaHelper";

    /**
     * Responsible for generating the EULA acceptance screen for ATAK.
     * @param activity is the ATAK Activity to work with.
     */
    static boolean showEULA(final ATAKActivity activity) {

        final SharedPreferences _controlPrefs = PreferenceManager
                .getDefaultSharedPreferences(activity);

        if (false) {
            // skip the EULA
            SharedPreferences.Editor editor = _controlPrefs.edit();
            editor.putBoolean("AgreedToEULA", true).apply();
        }

        if (_controlPrefs.getBoolean("AgreedToEULA", false)) {
            return true;
        }

        final AlertDialog.Builder alertBuilder = new AlertDialog.Builder(
                activity);
        alertBuilder
                .setTitle(activity.getString(R.string.preferences_text422a))
                .setMessage(
                        activity.getString(R.string.preferences_text423)
                                + activity.getString(
                                        R.string.preferences_text424))
                .setPositiveButton(R.string.preferences_text425,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dialog.dismiss();
                                SharedPreferences.Editor editor = _controlPrefs
                                        .edit();
                                editor.putBoolean("AgreedToEULA", true);
                                editor.apply();
                                activity.onCreate(null);
                            }
                        })
                .setNeutralButton(R.string.preferences_text415,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                try {
                                    WebViewer.show(
                                            "file:///android_asset/support/license/LICENSE.txt",
                                            activity, 250, new Runnable() {
                                                public void run() {
                                                    showEULA(activity);
                                                }
                                            });
                                } catch (Exception e) {
                                    Log.e(TAG, "error loading license.txt",
                                            e);
                                }

                            }
                        })
                .setNegativeButton(R.string.preferences_text426, // implemented
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                activity.finish();
                            }
                        })
                .setOnCancelListener(
                        new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                // quit ATAK if user backs out of EULA dialog
                                activity.finish();
                            }
                        });
        AlertDialog ad = alertBuilder.create();
        ad.show();
        return false;
    }
}
