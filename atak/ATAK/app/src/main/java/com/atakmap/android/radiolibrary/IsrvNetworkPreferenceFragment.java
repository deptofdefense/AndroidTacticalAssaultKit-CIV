
package com.atakmap.android.radiolibrary;

import com.atakmap.android.preference.AtakPreferenceFragment;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Patterns;

import com.atakmap.app.R;

public class IsrvNetworkPreferenceFragment extends AtakPreferenceFragment
        implements
        OnSharedPreferenceChangeListener {

    public IsrvNetworkPreferenceFragment() {
        super(R.xml.isrv_preferences, R.string.isrv_control_prefs);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());
    }

    @Override
    public void onResume() {
        super.onResume();
        // Set up a listener whenever a key changes
        SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
        if (prefs != null) {
            prefs.registerOnSharedPreferenceChangeListener(this);
        }

        // update ui
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(this
                        .getActivity());
        boolean isDhcp = sharedPreferences.getBoolean(
                "network_dhcp", true);

        if (isDhcp) {
            updateDhcpDetails();
        } else {
            updateStaticDetails();
        }

        // update dchp summary
        updateDhcpSummary(isDhcp);
    }

    @Override
    public void onPause() {
        super.onPause();

        // Unregister the listener whenever a key changes
        SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
        if (prefs != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onSharedPreferenceChanged(
            final SharedPreferences sharedPreferences,
            final String key) {

        if (key == null)
            return;

        switch (key) {
            case "network_dhcp":
                boolean isDhcp = sharedPreferences.getBoolean(key, true);

                if (isDhcp) {
                    updateDhcpDetails();
                } else {
                    updateStaticDetails();
                }

                // update dchp summary
                updateDhcpSummary(isDhcp);
                break;
            case "network_static_ip_address":
            case "network_static_subnet_mask":
            case "network_static_gateway":
            case "network_static_dns1":
            case "network_static_dns2":

                // get value
                String prefValue = sharedPreferences.getString(key, "");

                // validate
                if (prefValue.equals("")
                        || Patterns.IP_ADDRESS.matcher(prefValue).matches()) {
                    getPreferenceScreen().findPreference(key)
                            .setSummary(prefValue);
                } else {
                    // set default value
                    SharedPreferences.Editor prefEditor = sharedPreferences
                            .edit();
                    prefEditor.putString(key, "").apply();

                    // notify invalid
                    final AlertDialog.Builder builder = new AlertDialog.Builder(
                            this.getActivity());
                    builder.setTitle(R.string.invalid_address);
                    builder.setMessage(R.string.radio_enter_valid_address);
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                }
                break;
            default:
                break;

        }
    }

    private void updateDhcpSummary(boolean isDhcp) {
        Preference pref = findPreference("network_dhcp");
        pref.setSummary(isDhcp ? getString(R.string.radio_uncheck_dhcp)
                : getString(R.string.radio_check_dhcp));
    }

    private void updateDhcpDetails() {
        PreferenceScreen screen = getPreferenceScreen();

        screen.findPreference(
                "network_static_ip_address").setSummary(
                        "");
        screen.findPreference(
                "network_static_subnet_mask")
                .setSummary("");
        screen.findPreference("network_static_gateway")
                .setSummary("");
        screen.findPreference("network_static_dns1")
                .setSummary("");
        screen.findPreference("network_static_dns2")
                .setSummary("");
    }

    private void updateStaticDetails() {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(this
                        .getActivity());
        PreferenceScreen screen = getPreferenceScreen();

        String pref = "network_static_ip_address";
        screen.findPreference(pref).setSummary(
                sharedPreferences.getString(pref, ""));

        pref = "network_static_subnet_mask";
        screen.findPreference(pref).setSummary(
                sharedPreferences.getString(pref, ""));

        pref = "network_static_gateway";
        screen.findPreference(pref).setSummary(
                sharedPreferences.getString(pref, ""));

        pref = "network_static_dns1";
        screen.findPreference(pref).setSummary(
                sharedPreferences.getString(pref, ""));

        pref = "network_static_dns2";
        screen.findPreference(pref).setSummary(
                sharedPreferences.getString(pref, ""));

    }
}
