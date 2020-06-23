
package com.atakmap.android.elev;

import android.os.Bundle;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;

public class ElevationOverlaysPreferenceFragment
        extends AtakPreferenceFragment {

    public ElevationOverlaysPreferenceFragment() {
        super(R.xml.elevation_preferences, R.string.elevationPreferences);
    }

    @Override
    public void onCreate(Bundle savedInstanceBundle) {
        super.onCreate(savedInstanceBundle);
        addPreferencesFromResource(getResourceID());
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.toolPreferences),
                getSummary());
    }

}
