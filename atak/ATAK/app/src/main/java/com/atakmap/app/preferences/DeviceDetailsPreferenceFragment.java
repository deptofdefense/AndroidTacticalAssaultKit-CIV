
package com.atakmap.app.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.Preference;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.atakmap.android.gui.AlertDialogHelper;

import com.atakmap.android.gui.WebViewer;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.app.system.FlavorProvider;
import com.atakmap.app.system.SystemComponentLoader;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.Map;

public class DeviceDetailsPreferenceFragment extends AtakPreferenceFragment {
    private Context context;

    public DeviceDetailsPreferenceFragment() {
        super(R.xml.device_details_preference, R.string.device_details);
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                DeviceDetailsPreferenceFragment.class,
                R.string.device_details,
                R.drawable.ic_menu_network);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());

        context = getActivity();
        Preference deviceDetails = (Preference) findPreference(
                "settingsDebugging");
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

        Preference atakDatasets = (Preference) findPreference("deviceDetails");

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
    }

    private String getKeyValue(final String key,
            final Map<String, String> information) {
        StringBuilder sb = new StringBuilder("\n" + key + ":");
        final String val = information.get(key);

        final int remainder = 20 - key.length();
        for (int i = 0; i < remainder; ++i)
            sb.append(" ");
        if (val != null)
            sb.append(val.trim());
        return sb.toString();
    }

    private String getTitle(String title) {
        StringBuilder sb = new StringBuilder(title);
        sb.append("\n----------------------------------------");
        return sb.toString();
    }

    private String getRows() {
        Map<String, String> information = ATAKConstants
                .getGeneralInformation(context);

        StringBuilder retVal = new StringBuilder();

        retVal.append(getTitle("Identifier"));
        retVal.append(getKeyValue("id.wifi", information));
        retVal.append(getKeyValue("id.telephonyid", information));
        retVal.append(getKeyValue("id.serialno", information));

        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                retVal.append(getKeyValue("id.app", information));
            }
        } catch (Exception ignored) {
        }

        retVal.append("\n\n").append(getTitle("Android"));
        retVal.append(getKeyValue("android.sdk", information));
        retVal.append(getKeyValue("android.version", information));
        retVal.append(getKeyValue("android.release", information));

        retVal.append("\n\n").append(getTitle("Device"));
        retVal.append(getKeyValue("device.model", information));
        retVal.append(getKeyValue("device.manufacturer", information));
        retVal.append(getKeyValue("device.hardware", information));
        retVal.append(getKeyValue("device.id", information));
        retVal.append(getKeyValue("device.brand", information));
        retVal.append(getKeyValue("device.host", information));
        retVal.append(getKeyValue("device.display", information));
        retVal.append(getKeyValue("device.fingerprint", information));
        retVal.append(getKeyValue("device.build_time", information));

        retVal.append("\n\n").append(getTitle("TAK"));
        retVal.append(getKeyValue("tak.uid", information));
        retVal.append(getKeyValue("tak.brand", information));
        retVal.append(getKeyValue("tak.flavor", information));
        retVal.append(getKeyValue("tak.type", information));
        retVal.append(getKeyValue("tak.version", information));

        retVal.append("\n\n").append(getTitle("FileSystem"));
        for (String key : information.keySet()) {
            if (key.startsWith("filesystem.mount.")) {
                retVal.append(getKeyValue(key, information));
            }
        }

        retVal.append("\n\n").append(getTitle("Plugins"));

        boolean found = false;
        for (String key : information.keySet()) {
            if (key.startsWith("plugin.")) {
                found = true;
                retVal.append(getKeyValue(key, information));
            }
        }

        if (!found)
            retVal.append("no plugins found");

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
