
package com.atakmap.app.preferences;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;

import androidx.core.app.NavUtils;

import com.atakmap.android.importexport.ImportExportMapComponent;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.ATAKActivity;
import com.atakmap.app.R;

public class LoggingPreferenceFragment extends AtakPreferenceFragment {

    private static final String TAG = "LoggingPreferenceFragment";

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                LoggingPreferenceFragment.class,
                R.string.loggingPreferences,
                R.drawable.send_logs);
    }

    public LoggingPreferenceFragment() {
        super(R.xml.logging_preferences, R.string.loggingPreferences);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.support),
                getSummary());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());

        final Context context = getActivity();

        Preference collect_crashlogs = (Preference) findPreference(
                "collect_crashlogs");

        Preference atakExportCrashLogs = (Preference) findPreference(
                "atakExportCrashLogs");
        atakExportCrashLogs
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                new AlertDialog.Builder(context)
                                        .setIcon(
                                                com.atakmap.android.util.ATAKConstants
                                                        .getIconId())
                                        .setTitle(R.string.export_logs)
                                        .setMessage(
                                                R.string.preferences_text440)
                                        .setPositiveButton(R.string.ok,
                                                new DialogInterface.OnClickListener() {

                                                    @Override
                                                    public void onClick(
                                                            DialogInterface dialog,
                                                            int arg1) {
                                                        dialog.dismiss();

                                                        AtakBroadcast
                                                                .getInstance()
                                                                .sendBroadcast(
                                                                        new Intent(
                                                                                ImportExportMapComponent.EXPORT_LOGS));

                                                        // go back to app Activity so user can see the download
                                                        //dialog once the notification is touched
                                                        Intent upIntent = new Intent(
                                                                context,
                                                                ATAKActivity.class);
                                                        NavUtils.navigateUpTo(
                                                                (Activity) context,
                                                                upIntent);
                                                    }
                                                })
                                        .setNegativeButton(R.string.cancel,
                                                null)
                                        .show();

                                return true;
                            }
                        });

        Preference enableAutoUploadLogs = (Preference) findPreference(
                "enableAutoUploadLogs");
        enableAutoUploadLogs
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    final Preference preference) {
                                if (preference instanceof CheckBoxPreference
                                        && ((CheckBoxPreference) preference)
                                                .isChecked()) {
                                    Intent intent = new Intent(
                                            ImportExportMapComponent.SET_EXPORT_LOG_SERVER);
                                    AtakBroadcast.getInstance()
                                            .sendBroadcast(intent);

                                    // go back to app Activity so user can see the download
                                    //dialog once the notification is touched
                                    Intent upIntent = new Intent(
                                            context,
                                            ATAKActivity.class);
                                    NavUtils.navigateUpTo(
                                            (Activity) context,
                                            upIntent);
                                }
                                return true;
                            }
                        });
        Preference advanced_logging = (Preference) findPreference(
                "advanced_logging");
        advanced_logging
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                showScreen(
                                        new AdvancedLoggingPreferenceFragment());
                                return true;
                            }
                        });
    }
}
