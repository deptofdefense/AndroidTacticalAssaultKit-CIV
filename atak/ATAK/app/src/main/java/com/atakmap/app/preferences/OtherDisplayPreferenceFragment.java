
package com.atakmap.app.preferences;

import android.content.Context;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;

import com.atakmap.android.gridlines.GridLinesPreferenceFragment;
import com.atakmap.android.layers.app.ImportStyleDefaultPreferenceFragment;
import com.atakmap.android.layers.app.LayerPreferenceFragment;
import com.atakmap.android.offscreenindicators.OffscreenIndicatorsPrefsFragment;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class OtherDisplayPreferenceFragment extends AtakPreferenceFragment {
    private ListPreference overlayManagerWidthHeight;

    public OtherDisplayPreferenceFragment() {
        super(R.xml.other_display_prefs, R.string.otherPreferences);
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                OtherDisplayPreferenceFragment.class,
                R.string.otherPreferences,
                R.drawable.ic_menu_network);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());
        Preference gridLinesPreference = findPreference("gridLinesPreference");
        gridLinesPreference
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                showScreen(new GridLinesPreferenceFragment());
                                return true;
                            }
                        });

        Preference layerOutlinesPreference = findPreference(
                "layerOutlinesPreference");
        layerOutlinesPreference
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                showScreen(new LayerPreferenceFragment());
                                return true;
                            }
                        });

        Preference offscreenIndicatorsPreference = findPreference(
                "atakOffScrIndiOptions");
        offscreenIndicatorsPreference
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                showScreen(
                                        new OffscreenIndicatorsPrefsFragment());
                                return true;
                            }
                        });

        Preference overlayStylePreference = findPreference(
                "overlayStylePreference");
        overlayStylePreference
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                showScreen(
                                        new ImportStyleDefaultPreferenceFragment());
                                return true;
                            }
                        });

        overlayManagerWidthHeight = (ListPreference) findPreference(
                "overlay_manager_width_height");
        //only allow on tablet devices?????
        if (!getActivity().getResources()
                .getBoolean(R.bool.isTablet)) {
            overlayManagerWidthHeight.setEnabled(false);
        }
    }
}
