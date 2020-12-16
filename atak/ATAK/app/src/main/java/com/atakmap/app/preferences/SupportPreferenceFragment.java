
package com.atakmap.app.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.app.Activity;
import android.os.Bundle;
import android.preference.Preference;
import androidx.core.app.NavUtils;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.app.ATAKActivity;
import com.atakmap.android.gui.WebViewer;
import com.atakmap.android.gui.HintDialogHelper;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;
import com.atakmap.app.system.FlavorProvider;
import com.atakmap.app.system.SystemComponentLoader;
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

        Preference atakSupport = findPreference("atakSupport");
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

        Preference actionbarCustomize = findPreference("settingsLogging");
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

        configureDocumentPreference("atakDocumentation",
                FileSystemUtils.SUPPORT_DIRECTORY + File.separatorChar +
                        "docs" + File.separatorChar + "ATAK_User_Guide.pdf",
                false);

        configureDocumentPreference("atakChangeLog",
                FileSystemUtils.SUPPORT_DIRECTORY + File.separatorChar +
                        "docs" + File.separatorChar + "ATAK_Change_Log.pdf",
                true);

        configureDocumentPreference("atakFlavorAddendum",
                FileSystemUtils.SUPPORT_DIRECTORY + File.separatorChar +
                        "docs" + File.separatorChar
                        + "ATAK_Flavor_Addendum.pdf",
                true);

        Preference atakDatasets = findPreference("atakDatasets");

        if (atakDatasets != null) {
            atakDatasets
                    .setOnPreferenceClickListener(
                            new Preference.OnPreferenceClickListener() {
                                @Override
                                public boolean onPreferenceClick(
                                        Preference preference) {
                                    try {
                                        File file = FileSystemUtils
                                                .getItem(
                                                        FileSystemUtils.SUPPORT_DIRECTORY
                                                                + File.separatorChar
                                                                + "README.txt");

                                        WebViewer.show(
                                                file,
                                                context, 250);
                                    } catch (Exception e) {
                                        Log.e(TAG, "error loading readme.txt",
                                                e);
                                    }
                                    return true;
                                }
                            });
        }

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

        Preference resetDeviceConfig = findPreference("resetDeviceConfig");
        resetDeviceConfig
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
        resetDeviceConfig.setIcon(ATAKConstants.getIcon());
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

    private void configureDocumentPreference(String preferenceKey,
            String fileName,
            boolean requireFlavor) {
        try {
            final FlavorProvider fp = SystemComponentLoader.getFlavorProvider();

            final Preference preference = findPreference(preferenceKey);
            final File file = FileSystemUtils
                    .getItem(fileName);
            if (!file.exists() || file.length() == 0
                    || (fp == null && requireFlavor))
                removePreference(preference);

            if (preference != null) {
                preference.setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                com.atakmap.android.util.PdfHelper
                                        .checkAndWarn(context,
                                                file.toString());
                                return true;
                            }
                        });
            }
        } catch (Exception e) {
            Log.e(TAG, "error configuring preference", e);
        }

    }
}
