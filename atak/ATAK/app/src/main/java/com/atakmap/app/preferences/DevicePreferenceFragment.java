
package com.atakmap.app.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;

import com.atakmap.android.cot.NetworkGPSPreferenceFragment;
import com.atakmap.android.gui.PanCheckBoxPreference;
import com.atakmap.android.gui.PanListPreference;

import com.atakmap.android.lrf.LRFPreferenceFragment;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class DevicePreferenceFragment extends AtakPreferenceFragment {

    public static final String TAG = "DevicePreferenceFragment";

    private static DevicePreferenceFragment _instance;
    private PanCheckBoxPreference useWRCallsign;

    private PanListPreference myTeamListPreference;

    private SharedPreferences.OnSharedPreferenceChangeListener spChanged;

    public synchronized static DevicePreferenceFragment getInstance() {
        if (_instance == null) {
            _instance = new DevicePreferenceFragment();
        }
        return _instance;
    }

    public DevicePreferenceFragment() {
        super(R.xml.device_preferences, R.string.devicePreferences);
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                DevicePreferenceFragment.class,
                R.string.devicePreferences,
                R.drawable.my_prefs_settings);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());

        getActivity().setResult(Activity.RESULT_CANCELED, null);

        Preference gpsSettings = (Preference) findPreference("gpsSettings");
        gpsSettings
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(Preference pref) {
                                showScreen(new NetworkGPSPreferenceFragment());
                                return true;
                            }
                        });

        Preference lrfSettings = (Preference) findPreference("lrfSettings");
        lrfSettings
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(Preference pref) {
                                showScreen(new LRFPreferenceFragment());
                                return true;
                            }
                        });

        useWRCallsign = (PanCheckBoxPreference) findPreference(
                "locationUseWRCallsign");
        final SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(getActivity());
        if (!sp.getString("mockingOption", "WRGPS").equals("WRGPS")) { // If the mockingOPtion isn't
            // defined, then we assume
            // that screen hasn't been
            // visited so it goes to it's
            // default value
            useWRCallsign.setEnabled(false);
            useWRCallsign.setChecked(false);
        }
        spChanged = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(
                    SharedPreferences sharedPreferences, String key) {

                if (key == null)
                    return;

                switch (key) {
                    case "locationTeam":
                    case "atakRoleType":
                    case "locationUnitType":
                    case "loadPrefs":
                        String s = sharedPreferences.getString(key, null);
                        if (s != null) {
                            ListPreference attribute = (ListPreference) findPreference(
                                    key);
                            attribute.setValue(s);
                        }
                        break;
                }
            }
        };

        sp.registerOnSharedPreferenceChangeListener(spChanged);

        /*
            catches team color pref change
         */
        myTeamListPreference = (PanListPreference) findPreference(
                "locationTeam");
        myTeamListPreference
                .setOnPreferenceChangeListener(
                        new Preference.OnPreferenceChangeListener() {
                            @Override
                            public boolean onPreferenceChange(
                                    Preference preference,
                                    Object newValue) {
                                //when preference changes launch the customselficon class to create a new icon
                                //and store on device
                                return true;
                            }
                        });
        // Attach a file observer to the preference file and watch for changes
    }

    @Override
    public void onDestroy() {

        //pc.dispose();  //Do not want to destroy it here, just disconnect so we can access it later.  AS.

        final SharedPreferences sp = PreferenceManager
                .getDefaultSharedPreferences(getActivity());
        sp.unregisterOnSharedPreferenceChangeListener(spChanged);
        super.onDestroy();
    }

    public void setUseWRCallsign(boolean enabled) {
        if (useWRCallsign != null) {
            useWRCallsign.setEnabled(enabled);
            useWRCallsign.setChecked(false);
        }
    }

}
