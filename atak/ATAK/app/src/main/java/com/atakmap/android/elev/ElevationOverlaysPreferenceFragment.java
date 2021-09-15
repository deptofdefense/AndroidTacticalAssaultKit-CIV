
package com.atakmap.android.elev;

import android.os.Bundle;
import android.preference.Preference;
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

        final Preference p = findPreference("prefs_dted_download");
        ElevationDownloader.getInstance()
                .setupPreferenceDownloader(getActivity(), p);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.toolPreferences),
                getSummary());
    }

}
