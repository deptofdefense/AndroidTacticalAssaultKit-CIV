
package com.atakmap.android.fires;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;

import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.gui.PanListPreference;
import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.gui.PanListPreference;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;
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

    /**
     * Set the title correctly for a Preference given a resource.
     * @param p
     * @param resource
     */
    private static void setTitle(Preference p, int resource) {
        p.setTitle(resource);
        if (p instanceof PanEditTextPreference)
            ((PanEditTextPreference) p).setDialogTitle(resource);
        else if (p instanceof PanListPreference)
            ((PanListPreference) p).setDialogTitle(resource);

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.addPreferencesFromResource(getResourceID());
        Preference p = findPreference("fire_prefs_category");
        setTitle(p, ResourceUtil.getResource(R.string.civ_fires_prefs,
                R.string.fires_prefs));

        PanEditTextPreference spiUpdateDelay = (PanEditTextPreference) findPreference(
                "spiUpdateDelay");
        setTitle(p, ResourceUtil.getResource(R.string.civ_spi_update_delay,
                R.string.spi_update_delay));
        p.setSummary(
                ResourceUtil.getResource(R.string.civ_spi_update_delay_summary,
                        R.string.spi_update_delay_summary));

        PanListPreference firesNumberOfSpis = (PanListPreference) findPreference(
                "firesNumberOfSpis");

        setTitle(firesNumberOfSpis,
                ResourceUtil.getResource(R.string.civ_fireSpiNumber,
                        R.string.fireSpiNumber));
        firesNumberOfSpis.setSummary(
                ResourceUtil.getResource(R.string.civ_fireSpiNumberSummary,
                        R.string.fireSpiNumberSummary));

        PanEditTextPreference spiFahSize = (PanEditTextPreference) findPreference(
                "spiFahSize");
        spiFahSize.setValidIntegerRange(0, 180);

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
