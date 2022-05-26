
package com.atakmap.app.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import androidx.core.app.NavUtils;

import android.provider.Settings;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.atak.plugins.impl.AtakPluginRegistry;
import com.atakmap.android.gui.AlertDialogHelper;
import com.atakmap.android.location.LocationMapComponent;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.ATAKActivity;
import com.atakmap.android.gui.WebViewer;
import com.atakmap.android.gui.HintDialogHelper;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.BuildConfig;
import com.atakmap.app.R;
import com.atakmap.app.system.FlavorProvider;
import com.atakmap.app.system.SystemComponentLoader;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

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

        Preference deviceDetails = findPreference("settingsDebugging");
        deviceDetails.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {

                        AlertDialog.Builder builder = new AlertDialog.Builder(
                                context);
                        View v = LayoutInflater.from(context)
                                .inflate(R.layout.hint_screen, null);
                        TextView tv = v
                                .findViewById(R.id.message);
                        tv.setTextSize(16);
                        tv.setTypeface(Typeface.MONOSPACE);
                        tv.setText(getRows());
                        v.findViewById(R.id.showAgain).setVisibility(View.GONE);
                        builder.setCancelable(true)
                                .setTitle(R.string.device_information)
                                .setView(v)
                                .setPositiveButton(R.string.ok, null)
                                .setNeutralButton(getString(R.string.copy),
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(
                                                    DialogInterface dialog,
                                                    int which) {
                                                ATAKUtilities.copyClipboard(
                                                        getString(
                                                                R.string.device_information),
                                                        tv.getText().toString(),
                                                        true);
                                            }
                                        });

                        AlertDialog ad = builder.create();
                        ad.show();
                        AlertDialogHelper.adjustWidth(ad, .90);

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
                                        WebViewer.show(
                                                "file:///android_asset/support/README.txt",
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

    private String getKeyValue(String key, String val) {
        StringBuilder sb = new StringBuilder("\n" + key + ":");
        final int remainder = 20 - key.length();
        for (int i = 0; i < remainder; ++i)
            sb.append(" ");
        if (val != null)
            sb.append(val.trim());
        return sb.toString();
    }

    private String getTitle(String title) {
        StringBuffer sb = new StringBuffer(title);
        sb.append("\n----------------------------------------");
        return sb.toString();
    }

    private String getRows() {
        final SharedPreferences prefs = getPreferenceManager()
                .getSharedPreferences();

        StringBuilder retVal = new StringBuilder();

        retVal.append(getTitle("Identifier"));
        retVal.append(getKeyValue("id.wifi",
                LocationMapComponent.fetchWifiMacAddress(context)));
        retVal.append(getKeyValue("id.telephonyid",
                LocationMapComponent.fetchTelephonyDeviceId(context)));
        retVal.append(getKeyValue("id.serialno",
                LocationMapComponent.fetchSerialNumber(context)));
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                retVal.append(getKeyValue("id.app",
                        Settings.Secure.getString(context.getContentResolver(),
                                Settings.Secure.ANDROID_ID)));
            }
        } catch (Exception ignored) {
        }

        retVal.append("\n\n").append(getTitle("Android"));
        retVal.append(getKeyValue("android.sdk",
                Integer.toString(Build.VERSION.SDK_INT)));
        retVal.append(getKeyValue("android.version",
                System.getProperty("os.version")));
        retVal.append(getKeyValue("android.release", Build.VERSION.RELEASE));

        retVal.append("\n\n").append(getTitle("Device"));
        retVal.append(getKeyValue("device.model", Build.MODEL));
        retVal.append(getKeyValue("device.manufacturer", Build.MANUFACTURER));
        retVal.append(getKeyValue("device.hardware", Build.HARDWARE));
        retVal.append(getKeyValue("device.id", Build.ID));
        retVal.append(getKeyValue("device.brand", Build.BRAND));
        retVal.append(getKeyValue("device.host", Build.HOST));
        retVal.append(getKeyValue("device.display", Build.DISPLAY));
        retVal.append(getKeyValue("device.fingerprint", Build.FINGERPRINT));
        retVal.append(
                getKeyValue("device.build_time", Long.toString(Build.TIME)));

        retVal.append("\n\n").append(getTitle("TAK"));
        retVal.append(getKeyValue("tak.uid",
                prefs.getString("bestDeviceUID", "unknown")));
        retVal.append(
                getKeyValue("tak.brand", ATAKConstants.getVersionBrand()));
        retVal.append(getKeyValue("tak.flavor", BuildConfig.FLAVOR));
        retVal.append(getKeyValue("tak.type", BuildConfig.BUILD_TYPE));
        retVal.append(
                getKeyValue("tak.version", ATAKConstants.getFullVersionName()));

        retVal.append("\n\n").append(getTitle("FileSystem"));
        final File[] folders = context.getExternalCacheDirs();
        for (File f : folders) {
            retVal.append(getKeyValue("filesystem.mount", f.toString()));
        }

        retVal.append("\n\n").append(getTitle("Plugins"));

        final Map<String, ?> keys = prefs.getAll();
        boolean found = false;
        for (Map.Entry<String, ?> entry : keys.entrySet()) {
            final String key = entry.getKey();
            if (key.startsWith(AtakPluginRegistry.pluginLoadedBasename)
                    && entry.getValue() != null) {
                found = true;
                retVal.append(getKeyValue("plugin",
                        key.replace("plugin.version.loaded.", "") + " "
                                + entry.getValue().toString()));
            }
        }
        if (!found)
            retVal.append(getKeyValue("no plugins found", ""));

        return retVal.toString();
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
