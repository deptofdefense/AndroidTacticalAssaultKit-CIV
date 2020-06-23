
package com.atakmap.app.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;

import com.atakmap.android.cot.TadilJListActivity;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class TadilJPreferenceFragment extends AtakPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                TadilJPreferenceFragment.class,
                R.string.tadilj_settings,
                R.drawable.tadilj_settings);
    }

    public TadilJPreferenceFragment() {
        super(R.xml.tadilj_preferences, R.string.tadilj_settings);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.networkPreferences),
                getSummary());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        super.addPreferencesFromResource(getResourceID());

        Preference manageIds = findPreference("tadiljPersistedIds");
        manageIds
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                startActivity(new Intent(getActivity(),
                                        TadilJListActivity.class));
                                return false;
                            }
                        });
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return true;
    }
}
