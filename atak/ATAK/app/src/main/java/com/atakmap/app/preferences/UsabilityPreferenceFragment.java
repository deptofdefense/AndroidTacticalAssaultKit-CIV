
package com.atakmap.app.preferences;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

public class UsabilityPreferenceFragment extends AtakPreferenceFragment {
    private CheckBoxPreference largeTextModePreference;
    private CheckBoxPreference largeActionBarPreference;
    private ListPreference relativeOverlaysScalingRadioListPreference;
    private ListPreference label_text_sizePreference;

    public UsabilityPreferenceFragment() {
        super(R.xml.usability_settings, R.string.usability_settings);
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                UsabilityPreferenceFragment.class,
                R.string.usability_settings,
                R.drawable.ic_menu_network);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());
        SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(getActivity());

        Preference usableSettings = findPreference("displaySettings");
        usableSettings
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {

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

        largeTextModePreference = (CheckBoxPreference) findPreference(
                "largeTextMode");
        largeActionBarPreference = (CheckBoxPreference) findPreference(
                "largeActionBar");
        Preference relativeOverlaysScalingRadioList = findPreference(
                "relativeOverlaysScalingRadioList");
        relativeOverlaysScalingRadioListPreference = (ListPreference) relativeOverlaysScalingRadioList;
        Preference label_text_size = findPreference("label_text_size");
        label_text_sizePreference = (ListPreference) label_text_size;

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
