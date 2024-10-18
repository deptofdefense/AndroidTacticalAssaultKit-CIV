
package com.atakmap.app.preferences;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class SelfCoordinatePreferenceFragment extends AtakPreferenceFragment {
    public SelfCoordinatePreferenceFragment() {
        super(R.xml.self_coordinate, R.string.preferences_text360);
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                SelfCoordinatePreferenceFragment.class,
                R.string.preferences_text360,
                R.drawable.ic_menu_network);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());
    }
}
