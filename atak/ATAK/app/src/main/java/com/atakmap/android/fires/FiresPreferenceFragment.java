
package com.atakmap.android.fires;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;
import com.atakmap.app.preferences.PreferenceControl;
import com.atakmap.app.system.ResourceUtil;

/*
 * 
 * 
 * Used to access the preferences for the Fires Tool Bar
 */

public class FiresPreferenceFragment extends AtakPreferenceFragment {

    public FiresPreferenceFragment() {
        super(R.xml.fires_preferences,
                ResourceUtil.getResource(R.string.civ_fire_control_prefs,
                        R.string.fire_control_prefs));
    }

    private static void setTitle(Preference p, int resource) {
        p.setTitle(resource);
        if (p instanceof EditTextPreference)
            ((EditTextPreference)p).setDialogTitle(resource);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.addPreferencesFromResource(getResourceID());
        Preference p = findPreference("fire_prefs_category");
        setTitle(p, ResourceUtil.getResource(R.string.civ_fires_prefs,
                R.string.fires_prefs));


        p = findPreference("spiUpdateDelay");
        setTitle(p, ResourceUtil.getResource(R.string.civ_spi_update_delay,
                R.string.spi_update_delay));
        p.setSummary(
                ResourceUtil.getResource(R.string.civ_spi_update_delay_summary,
                        R.string.spi_update_delay_summary));

        p = findPreference("firesNumberOfSpis");

        setTitle(p, ResourceUtil.getResource(R.string.civ_fireSpiNumber,
                R.string.fireSpiNumber));
        p.setSummary(ResourceUtil.getResource(R.string.civ_fireSpiNumberSummary,
                R.string.fireSpiNumberSummary));

        p = findPreference("spiFahSize");

        setTitle(p, ResourceUtil.getResource(R.string.civ_spi_redx_fah,
                R.string.spi_redx_fah));
        p.setSummary(ResourceUtil.getResource(R.string.civ_spi_redx_fah_summary,
                R.string.spi_redx_fah_summary));

    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.toolPreferences),
                getSummary());
    }
}
