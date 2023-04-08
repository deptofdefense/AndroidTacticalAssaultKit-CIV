
package com.atakmap.android.fires;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;

import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.gui.PanListPreference;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;
import com.atakmap.app.system.ResourceUtil;

import java.util.List;

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

    public static List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                FiresPreferenceFragment.class,
                R.string.civ_fire_control_prefs,
                R.drawable.ic_overlay_gridlines);
    }

    /**
     * Set the title correctly for a Preference given a resource.
     *
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

        PanEditTextPreference spiUpdateDelay = (PanEditTextPreference) findPreference(
                "spiUpdateDelay");
        spiUpdateDelay.setTitle(
                ResourceUtil.getResource(R.string.civ_spi_update_delay,
                        R.string.spi_update_delay));
        spiUpdateDelay.setSummary(
                ResourceUtil.getResource(R.string.civ_spi_update_delay_summary,
                        R.string.spi_update_delay_summary));

        PanListPreference firesNumberOfSpis = (PanListPreference) findPreference(
                "firesNumberOfSpis");

        firesNumberOfSpis
                .setTitle(ResourceUtil.getResource(R.string.civ_fireSpiNumber,
                        R.string.fireSpiNumber));
        firesNumberOfSpis.setSummary(
                ResourceUtil.getResource(R.string.civ_fireSpiNumberSummary,
                        R.string.fireSpiNumberSummary));

        PanEditTextPreference spiFahSize = (PanEditTextPreference) findPreference(
                "spiFahSize");
        spiFahSize.setValidIntegerRange(0, 180);
        spiFahSize.setTitle(ResourceUtil.getResource(R.string.civ_spi_redx_fah,
                R.string.spi_redx_fah));

        spiFahSize.setSummary(
                ResourceUtil.getResource(R.string.civ_spi_redx_fah_summary,
                        R.string.spi_redx_fah_summary));

    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.toolPreferences),
                getSummary());
    }
}
