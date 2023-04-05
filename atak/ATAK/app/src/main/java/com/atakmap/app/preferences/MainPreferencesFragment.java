
package com.atakmap.app.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.app.R;

public class MainPreferencesFragment extends AtakPreferenceFragment implements
        OnPreferenceClickListener {

    public static final String TAG = "MainPreferencesFragment";

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                MainPreferencesFragment.class,
                R.string.settings,
                R.drawable.ic_menu_settings);
    }

    public MainPreferencesFragment() {
        super(R.xml.main_preferences, R.string.settings);

    }

    @Override
    public String getSubTitle() {
        return getSummary();
    }

    @Override
    public boolean onPreferenceClick(Preference pref) {
        String key = pref.getKey();
        switch (key) {
            case "deviceSettings":
                showScreen(new DevicePreferenceFragment());
                break;
            case "settingsPref":
                showScreen(new NetworkPreferenceFragment());
                break;
            case "generalDisplayPref":
                showScreen(new DisplayPrefsFragment());
                break;
            case "toolsPref":
                Intent intent = getActivity().getIntent();
                final Bundle externalPrefs = intent
                        .getBundleExtra("externalPrefs");
                ToolsPreferenceFragment frag = new ToolsPreferenceFragment();
                showScreen(frag);
                printBundleContents(TAG, externalPrefs);
                if (externalPrefs != null
                        && externalPrefs.containsKey("tools")) {
                    frag.addPreferences(externalPrefs.getBundle("tools"));
                }
                break;
            case "bluetoothPref":
                showScreen(new BluetoothPrefsFragment());
                break;
            case "atakControlOptions":
                showScreen(new ControlPrefsFragment());
                break;
            case "atakAccounts":
                showScreen(new AtakAccountsFragment());
                break;
            case "documentation":
                showScreen(new SupportPreferenceFragment());
                break;
            case "about":
                ATAKConstants.displayAbout(getActivity(),
                        true);
                return false;
        }

        return true;

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());
        getActivity().setResult(Activity.RESULT_CANCELED, null);

        Preference devicePreferences = findPreference("deviceSettings");
        devicePreferences.setOnPreferenceClickListener(this);

        Preference settingsPref = findPreference("settingsPref");
        settingsPref.setOnPreferenceClickListener(this);

        Preference displayPref = findPreference("generalDisplayPref");
        displayPref.setOnPreferenceClickListener(this);

        Preference toolsPref = findPreference("toolsPref");
        toolsPref.setOnPreferenceClickListener(this);

        Preference bluetoothPref = findPreference("bluetoothPref");
        bluetoothPref.setOnPreferenceClickListener(this);

        Preference controlPreference = findPreference("atakControlOptions");
        controlPreference.setOnPreferenceClickListener(this);

        Preference accountsPreference = findPreference("atakAccounts");
        accountsPreference.setOnPreferenceClickListener(this);

        Preference helpPreference = findPreference("documentation");
        helpPreference.setOnPreferenceClickListener(this);

        Preference aboutPref = findPreference("about");
        aboutPref.setOnPreferenceClickListener(this);
        aboutPref.setIcon(ATAKConstants.getIcon());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        if (requestCode == SETTINGS_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                getActivity().setResult(resultCode, data);
            }
        }
    }

    private static final int SETTINGS_REQUEST_CODE = 555;

}
