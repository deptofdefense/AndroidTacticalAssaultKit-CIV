
package com.atakmap.android.missionpackage.ui;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

import java.util.List;

public class MissionPackagePreferenceFragment extends AtakPreferenceFragment {

    public static List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                MissionPackagePreferenceFragment.class,
                R.string.missionpackage_control_prefs,
                R.drawable.ic_menu_settings);
    }

    public MissionPackagePreferenceFragment() {
        super(R.xml.missionpackage_preferences,
                R.string.missionpackage_control_prefs);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());
        ((PanEditTextPreference) findPreference("filesharingSizeThresholdNoGo"))
                .checkValidInteger();
        ((PanEditTextPreference) findPreference("fileshareDownloadAttempts"))
                .checkValidInteger();
        ((PanEditTextPreference) findPreference("filesharingWebServerPort"))
                .setValidIntegerRange(0, 65535);
        ((PanEditTextPreference) findPreference(
                "filesharingConnectionTimeoutSecs"))
                        .setValidIntegerRange(10, 900);
        ((PanEditTextPreference) findPreference(
                "filesharingTransferTimeoutSecs"))
                        .setValidIntegerRange(10, 900);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.toolPreferences),
                getSummary());
    }
}
