
package com.atakmap.app.preferences;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;

import com.atakmap.android.importfiles.sort.ImportMissionPackageSort;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;
import com.atakmap.app.SettingsActivity;
import com.atakmap.net.CertificateEnrollmentClient;

public class PromptNetworkPreferenceFragment extends AtakPreferenceFragment {
    public PromptNetworkPreferenceFragment() {
        super(R.xml.promt_network_preference, R.string.network_connections);
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                NetworkPreferenceFragment.class,
                R.string.networkPreferences,
                R.drawable.ic_menu_network);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());
        Preference dataPackage = findPreference("dataPackage");
        dataPackage.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        ImportMissionPackageSort
                                .importMissionPackage(getActivity());
                        return true;
                    }
                });

        Preference quickConnect = findPreference("quickConnect");
        quickConnect.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        CertificateEnrollmentClient.getInstance().enroll(
                                getActivity(),
                                null, null, null, null,
                                null, true);
                        return true;
                    }
                });
        Preference advanced_network_settings = findPreference(
                "advanced_network_settings");
        advanced_network_settings.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        SettingsActivity
                                .start(NetworkConnectionPreferenceFragment.class);
                        return true;
                    }
                });
    }
}
