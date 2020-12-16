
package com.atakmap.app.preferences;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.gridlines.GridLinesPreferenceFragment;
import com.atakmap.android.layers.app.ImportStyleDefaultPreferenceFragment;
import com.atakmap.android.layers.app.LayerPreferenceFragment;
import com.atakmap.android.offscreenindicators.OffscreenIndicatorsPrefsFragment;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.android.preference.UnitDisplayPreferenceFragment;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

public class DisplayPrefsFragment extends AtakPreferenceFragment {

    public static final String TAG = "DisplayPrefsFragment";
    private CheckBoxPreference largeTextModePreference;
    private CheckBoxPreference largeActionBarPreference;
    private ListPreference relativeOverlaysScalingRadioListPreference;
    private ListPreference label_text_sizePreference;
    private Preference my_location_icon_preference;
    private ListPreference overlayManagerWidthHeight;

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                DisplayPrefsFragment.class,
                R.string.displayPreferences,
                R.drawable.ic_display_settings);
    }

    public DisplayPrefsFragment() {
        super(R.xml.display_preferences, R.string.displayPreferences);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());

        SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(getActivity());

        my_location_icon_preference = findPreference("my_location_icon_color");
        my_location_icon_preference
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new SelfMarkerCustomFragment());
                        return true;
                    }
                });

        Preference actionbarCustomize = findPreference("my_actionbar_settings");
        actionbarCustomize
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new ActionBarPreferences());
                        return true;
                    }
                });

        Preference rabPreference = findPreference("unitPreferences");
        rabPreference
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new UnitDisplayPreferenceFragment());
                        return true;
                    }
                });

        Preference p = findPreference("displaySettings");
        p.setOnPreferenceClickListener(new OnPreferenceClickListener() {

            @Override
            public boolean onPreferenceClick(Preference preference) {

                try {
                    startActivityForResult(new Intent(
                            android.provider.Settings.ACTION_DISPLAY_SETTINGS),
                            0);
                } catch (ActivityNotFoundException ignored) {

                    // TODO: Translate this after it has been backported to 4.1.1
                    Toast.makeText(getActivity(),
                            "This program was unable to launch the system level display preference.",
                            Toast.LENGTH_SHORT).show();

                }
                return true;
            }
        });

        Preference gridLinesPreference = findPreference("gridLinesPreference");
        gridLinesPreference
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new GridLinesPreferenceFragment());
                        return true;
                    }
                });

        Preference layerOutlinesPreference = findPreference(
                "layerOutlinesPreference");
        layerOutlinesPreference
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new LayerPreferenceFragment());
                        return true;
                    }
                });

        Preference overlayStylePreference = findPreference(
                "overlayStylePreference");
        overlayStylePreference
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new ImportStyleDefaultPreferenceFragment());
                        return true;
                    }
                });

        Preference offscreenIndicatorsPreference = findPreference(
                "atakOffScrIndiOptions");
        offscreenIndicatorsPreference
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new OffscreenIndicatorsPrefsFragment());
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

        largeTextModePreference = (CheckBoxPreference) findPreference(
                "largeTextMode");
        largeActionBarPreference = (CheckBoxPreference) findPreference(
                "largeActionBar");
        relativeOverlaysScalingRadioListPreference = (ListPreference) findPreference(
                "relativeOverlaysScalingRadioList");
        label_text_sizePreference = (ListPreference) findPreference(
                "label_text_size");

        largeTextModePreference
                .setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {

                            @Override
                            public boolean onPreferenceChange(Preference arg0,
                                    Object arg1) {

                                if (arg1 == null) {
                                    Log.w(TAG, "onPreferenceChange: " + arg0 +
                                            " but the argument is null");
                                    return false;
                                }
                                if (!(arg1 instanceof Boolean)) {
                                    Log.w(TAG, "onPreferenceChange: " + arg1
                                            + " of type: "
                                            + arg1.getClass().getName());
                                    return false;
                                }

                                setSizePrefs((Boolean) arg1);
                                return true;
                            }
                        });

        boolean b = sp.getBoolean("largeTextMode", false);
        largeActionBarPreference.setEnabled(!b);
        relativeOverlaysScalingRadioListPreference.setEnabled(!b);
        label_text_sizePreference.setEnabled(!b);
    }

    private void setSizePrefs(boolean b) {
        Log.d(TAG, "Set large text mode: " + b);

        //set pref, of 3 child prefs
        largeActionBarPreference.setChecked(b);
        relativeOverlaysScalingRadioListPreference
                .setValue(b ? "1.50" : "1.00");
        label_text_sizePreference.setValue(b ? "18" : "14");

        //set enabled, of 3 child prefs
        largeActionBarPreference.setEnabled(!b);
        relativeOverlaysScalingRadioListPreference.setEnabled(!b);
        label_text_sizePreference.setEnabled(!b);
    }
}
