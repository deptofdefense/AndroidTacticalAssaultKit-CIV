
package com.atakmap.app.preferences;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.core.app.NavUtils;

import com.atakmap.android.gui.HintDialogHelper;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.android.user.feedback.UserFeedbackCollector;
import com.atakmap.app.ATAKActivity;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class SupportPreferenceFragment extends AtakPreferenceFragment {

    private static final String TAG = "SupportPreferenceFragment";
    private Context context;

    public SupportPreferenceFragment() {
        super(R.xml.support_preferences, R.string.support);
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                SupportPreferenceFragment.class,
                R.string.support,
                R.drawable.ic_menu_support_info);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());

        context = getActivity();

        Preference atakSupport = findPreference("takSupportInfo");
        atakSupport
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {

                                AlertDialog.Builder builder = new AlertDialog.Builder(
                                        context);
                                File f = FileSystemUtils
                                        .getItem(
                                                FileSystemUtils.SUPPORT_DIRECTORY
                                                        + File.separatorChar
                                                        + "support.inf");
                                View v = LayoutInflater.from(context)
                                        .inflate(R.layout.hint_screen, null);
                                TextView tv = v
                                        .findViewById(R.id.message);
                                tv.setText(Html.fromHtml(read(f)));
                                final CheckBox cb = v
                                        .findViewById(R.id.showAgain);
                                cb.setVisibility(View.GONE);

                                builder.setCancelable(true)
                                        .setTitle(R.string.preferences_text439)
                                        .setView(v)
                                        .setPositiveButton(R.string.ok, null)
                                        .show();

                                return true;
                            }
                        });

        Preference resetHints = findPreference("resetHints");
        resetHints
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                HintDialogHelper.resetHints(context);
                                new AlertDialog.Builder(context)
                                        .setTitle(R.string.preferences_text441)
                                        .setMessage(
                                                R.string.preferences_text442)
                                        .setPositiveButton(R.string.ok, null)
                                        .show();

                                return true;
                            }
                        });

        Preference takInitDeviceConfig = findPreference("takInitDeviceConfig");
        takInitDeviceConfig
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                Intent upIntent = new Intent(context,
                                        ATAKActivity.class);
                                NavUtils.navigateUpTo((Activity) context,
                                        upIntent);

                                AtakBroadcast.getInstance()
                                        .sendBroadcast(new Intent(
                                                "com.atakmap.app.DEVICE_SETUP"));
                                return true;
                            }
                        });

        Preference actionbarCustomize = findPreference("loggingPrefs");
        actionbarCustomize
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                showScreen(new LoggingPreferenceFragment());
                                return true;
                            }
                        });

        Preference deviceDetails = findPreference("deviceDetails");
        deviceDetails.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new DeviceDetailsPreferenceFragment());
                        return true;
                    }
                });

        Preference atakdocs = findPreference("atakdocs");
        atakdocs.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new AtakDocumentationPreferenceFragment());
                        return true;
                    }
                });

        UserFeedbackCollector userFeedbackCollector = new UserFeedbackCollector(
                context);
        Preference userFeedback = findPreference("userFeedback");
        userFeedback
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                userFeedbackCollector.showCollector();
                                return false;
                            }
                        });

    }

    static public String read(File f) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(f));
            String line;
            StringBuilder stringBuilder = new StringBuilder();
            String ls = System.getProperty("line.separator");

            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append(ls);
            }

            return stringBuilder.toString();
        } catch (Exception e) {
            Log.w(TAG, "Failed to read", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
        return "";
    }

}
