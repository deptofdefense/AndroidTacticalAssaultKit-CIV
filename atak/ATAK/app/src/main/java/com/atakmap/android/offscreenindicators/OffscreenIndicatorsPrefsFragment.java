
package com.atakmap.android.offscreenindicators;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

import java.util.List;

public class OffscreenIndicatorsPrefsFragment extends AtakPreferenceFragment {

    public static List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                OffscreenIndicatorsPrefsFragment.class,
                R.string.offscreenIndPreferences,
                R.drawable.ic_overlay_offscrind);
    }

    public OffscreenIndicatorsPrefsFragment() {
        super(R.xml.off_scr_indi_preferences, R.string.offscreenIndPreferences);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.addPreferencesFromResource(getResourceID());
        ((PanEditTextPreference) findPreference(
                "offscreen_indicator_dist_threshold"))
                        .checkValidDouble();
        ((PanEditTextPreference) findPreference(
                "offscreen_indicator_timeout_threshold"))
                        .checkValidDouble();
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.displayPreferences),
                getSummary());
    }
}
