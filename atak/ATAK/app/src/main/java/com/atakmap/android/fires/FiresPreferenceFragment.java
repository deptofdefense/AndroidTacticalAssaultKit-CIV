
package com.atakmap.android.fires;

import android.os.Bundle;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;

/*
 * 
 * 
 * Used to access the preferences for the Fires Tool Bar
 */

public class FiresPreferenceFragment extends AtakPreferenceFragment {

    public FiresPreferenceFragment() {
        super(R.xml.fires_preferences, R.string.fire_control_prefs);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.addPreferencesFromResource(getResourceID());
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.toolPreferences),
                getSummary());
    }
}
