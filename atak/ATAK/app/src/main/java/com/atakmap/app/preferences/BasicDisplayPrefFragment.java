
package com.atakmap.app.preferences;

import android.os.Bundle;
import android.preference.Preference;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.UnitDisplayPreferenceFragment;
import com.atakmap.app.R;

public class BasicDisplayPrefFragment extends AtakPreferenceFragment {

    public BasicDisplayPrefFragment() {
        super(R.xml.basic_display_settings, R.string.basic_display_settings);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());
        Preference rabPreference = findPreference("unitPreferences");
        rabPreference
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                showScreen(new UnitDisplayPreferenceFragment());
                                return true;
                            }
                        });
    }
}
