
package com.atakmap.app.preferences;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class StaleDataPreferenceFragment extends AtakPreferenceFragment {
    public StaleDataPreferenceFragment() {
        super(R.xml.stale_data_prefs, R.string.staledataPreferences);
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                StaleDataPreferenceFragment.class,
                R.string.staledataPreferences,
                R.drawable.ic_menu_network);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());
        Preference expireStaleItemsTime = findPreference(
                "expireStaleItemsTime");
    }

}
