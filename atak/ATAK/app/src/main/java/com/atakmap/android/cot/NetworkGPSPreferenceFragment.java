
package com.atakmap.android.cot;

import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;

import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;
import com.atakmap.app.preferences.DevicePreferenceFragment;

public class NetworkGPSPreferenceFragment extends AtakPreferenceFragment {
    private PreferenceCategory networkCategory;
    private ListPreference mockCheckPref;
    private EditTextPreference listenPort;
    private String _previous = "";

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                NetworkGPSPreferenceFragment.class,
                R.string.networkGpsPreferences,
                R.drawable.ic_menu_compass);
    }

    public NetworkGPSPreferenceFragment() {
        super(R.xml.network_gps_preferences, R.string.networkGpsPreferences);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.networkPreferences),
                getSummary());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());

        networkCategory = (PreferenceCategory) findPreference(
                "networkGpsCategory");
        mockCheckPref = (ListPreference) findPreference("mockingOption");

        listenPort = (EditTextPreference) findPreference("listenPort");

        if (mockCheckPref != null) {

            String value = mockCheckPref.getValue();
            if (value == null) {
                value = "WRGPS";
                //mockCheckPref.setValue("WRGPS");
            }
            changeVisibility(value);

            mockCheckPref
                    .setOnPreferenceChangeListener(
                            new OnPreferenceChangeListener() {

                                @Override
                                public boolean onPreferenceChange(
                                        Preference arg0,
                                        Object arg1) {
                                    changeVisibility((String) arg1);
                                    return true;
                                }

                            });
        }

        //Log.d(TAG, "listenPort: " + listenPort);

        if (listenPort instanceof PanEditTextPreference) {
            ((PanEditTextPreference) listenPort)
                    .setValidIntegerRange(0, 65535);
        }
    }

    private void changeVisibility(String selection) {
        if (selection.equals(_previous)) {
            return;
        }
        switch (selection) {
            case "LocalGPS":
            case "None":
                _previous = selection;
                mockCheckPref
                        .setSummary(getString(R.string.preferences_text378));
                networkCategory.removePreference(listenPort);
                DevicePreferenceFragment.getInstance().setUseWRCallsign(false);
                break;
            case "WRGPS":
                _previous = selection;
                mockCheckPref
                        .setSummary(getString(R.string.wave_relay_mocking));
                networkCategory.addPreference(listenPort);
                DevicePreferenceFragment.getInstance().setUseWRCallsign(true);
                break;
            case "GMECIGPS":
                _previous = selection;
                mockCheckPref.setSummary(getString(R.string.gmeci_mocking));
                networkCategory.removePreference(listenPort);
                DevicePreferenceFragment.getInstance().setUseWRCallsign(true);
                break;
            case "NetworkGPS":
                _previous = selection;
                mockCheckPref
                        .setSummary(getString(R.string.preferences_text379));
                networkCategory.removePreference(listenPort);
                DevicePreferenceFragment.getInstance().setUseWRCallsign(false);
                break;
            case "GPSService":
                _previous = selection;
                mockCheckPref
                        .setSummary(getString(R.string.preferences_text380));
                networkCategory.addPreference(listenPort);
                DevicePreferenceFragment.getInstance().setUseWRCallsign(true);
                break;
        }

    }

}
