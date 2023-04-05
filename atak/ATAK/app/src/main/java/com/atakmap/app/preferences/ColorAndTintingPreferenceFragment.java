
package com.atakmap.app.preferences;

import android.os.Bundle;
import android.preference.Preference;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;

public class ColorAndTintingPreferenceFragment extends AtakPreferenceFragment {
    public ColorAndTintingPreferenceFragment() {
        super(R.xml.color_and_tinting, R.string.coloring_and_tinting);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());
        Preference my_location_icon_preference = findPreference(
                "my_location_icon_color");
        my_location_icon_preference
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                showScreen(new SelfMarkerCustomFragment());
                                return true;
                            }
                        });

        Preference actionbarCustomize = findPreference("my_actionbar_settings");
        actionbarCustomize
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                showScreen(new ActionBarPreferences());
                                return true;
                            }
                        });
    }
}
