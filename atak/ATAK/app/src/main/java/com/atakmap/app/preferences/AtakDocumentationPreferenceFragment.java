
package com.atakmap.app.preferences;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;

import com.atakmap.android.gui.WebViewer;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;
import com.atakmap.app.system.FlavorProvider;
import com.atakmap.app.system.SystemComponentLoader;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;

public class AtakDocumentationPreferenceFragment
        extends AtakPreferenceFragment {
    private Context context;

    public AtakDocumentationPreferenceFragment() {
        super(R.xml.atak_docs_preference, R.string.documentation);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());

        context = getActivity();
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
