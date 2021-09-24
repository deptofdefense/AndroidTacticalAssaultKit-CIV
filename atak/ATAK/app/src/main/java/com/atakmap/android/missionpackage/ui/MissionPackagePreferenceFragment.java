
package com.atakmap.android.missionpackage.ui;

import android.os.Bundle;

import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;

public class MissionPackagePreferenceFragment extends AtakPreferenceFragment {

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
