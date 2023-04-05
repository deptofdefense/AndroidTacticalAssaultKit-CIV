
package com.atakmap.app.preferences;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class LegacyPreferencesFragment extends AtakPreferenceFragment {

    public LegacyPreferencesFragment() {
        super(R.xml.legacy_prefs, R.string.legacy_preferences);
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                LegacyPreferencesFragment.class,
                R.string.legacy_preferences,
                R.drawable.ic_menu_network);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());
        Preference pointDroppingBehavior = (Preference) findPreference(
                "pointDroppingBehavior");
        pointDroppingBehavior.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(
                                new PointDroppingBehaviorPreferenceFragment());
                        return true;
                    }
                });
    }
}
