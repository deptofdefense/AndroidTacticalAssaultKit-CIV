
package com.atakmap.android.elev;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.gui.PanPreference;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

import java.util.List;

public class ElevationOverlaysPreferenceFragment
        extends AtakPreferenceFragment {
    public static List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                ElevationOverlaysPreferenceFragment.class,
                R.string.elevationPreferences,
                R.drawable.ic_menu_settings);
    }

    public ElevationOverlaysPreferenceFragment() {
        super(R.xml.elevation_preferences, R.string.elevationPreferences);
    }

    @Override
    public void onCreate(Bundle savedInstanceBundle) {
        super.onCreate(savedInstanceBundle);
        addPreferencesFromResource(getResourceID());

        final PanPreference p = (PanPreference) findPreference(
                "prefs_dted_download");
        ElevationDownloader.getInstance()
                .setupPreferenceDownloader(getActivity(), p);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.toolPreferences),
                getSummary());
    }

}
