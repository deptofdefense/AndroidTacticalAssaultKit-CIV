
package com.atakmap.android.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;

import com.atakmap.android.maps.MapView;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.List;

public class UnitDisplayPreferenceFragment extends AtakPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    public static final String TAG = "UnitDisplayPreferenceFragment";

    public static List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                UnitDisplayPreferenceFragment.class,
                R.string.unitDisplayPreferences,
                R.drawable.ic_menu_ruler);
    }

    public UnitDisplayPreferenceFragment() {
        super(R.xml.unit_display_preferences, R.string.unitDisplayPreferences);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.displayPreferences),
                getSummary());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Put your create code here
        this.addPreferencesFromResource(getResourceID());

        ListPreference domainSelectListPreference = (ListPreference) findPreference(
                "set_domain_pref");
        if (domainSelectListPreference != null) {
            domainSelectListPreference.setOnPreferenceChangeListener(this);
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference,
            Object newValue) {

        if (newValue instanceof String) {
            String newVal = (String) newValue;
            // Get a SharedPreferences Editor to write out changed preference
            SharedPreferences.Editor editor = _mainControlPrefs
                    .edit();

            final Context context = MapView.getMapView().getContext();
            // Preference has been changed on Domain, set other preferences
            if (newVal.equals(context.getString(R.string.ground))) {
                // Set Imagery tab preference to Imagery
                editor.putString("imagery_tab_name",
                        context.getString(R.string.imagery));
                Log.d(TAG,
                        "Domain is Ground. Imagery tab preference name changed to Imagery");

            } else if (newVal.equals("Aviation")) {
                // Set Imagery tab preferences to Maps
                editor.putString("imagery_tab_name",
                        context.getString(R.string.maps));
                Log.d(TAG,
                        "Domain is Aviation. Imagery tab preference name changed to Maps");

            } else if (newVal.equals("Maritime")) {
                // Set Imagery tab preferences to Imagery
                editor.putString("imagery_tab_name",
                        context.getString(R.string.imagery));
                Log.d(TAG,
                        "Domain is Maritime. Imagery tab preference name changed to Imagery");

            }
            // Commit changes to preferences
            editor.apply();
        }
        return true;
    }

}
