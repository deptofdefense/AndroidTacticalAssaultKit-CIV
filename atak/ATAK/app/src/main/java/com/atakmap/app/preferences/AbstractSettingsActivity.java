
package com.atakmap.app.preferences;

import android.app.ActionBar;
import android.os.Bundle;
import androidx.core.app.NavUtils;
import android.view.MenuItem;

import com.atakmap.android.metrics.activity.MetricActivity;
import com.atakmap.coremap.log.Log;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.util.ATAKConstants;

/**
 * Serves as a baseclass for both <code>SettingsActivity</code> and <code>NetworkSettingsActivity</code>.
 */
abstract public class AbstractSettingsActivity extends MetricActivity {
    private static final String TAG = "AbstractSettingsActivity";

    protected abstract int getSubtitle();

    protected abstract AtakPreferenceFragment getPreferenceFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        com.atakmap.android.gui.PanEditTextPreference.setContext(this);
        com.atakmap.android.gui.PanListPreference.setContext(this);
        com.atakmap.android.gui.PanMultiSelectListPreference.setContext(this);
        com.atakmap.android.gui.SMSNumberPreference.setContext(this);
        com.atakmap.android.network.ui.CredentialsPreference.setContext(this);

        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(ATAKConstants.getVersionName());
            actionBar.setSubtitle(getSubtitle());
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setIcon(ATAKConstants.getIcon());
        }
        AtakPreferenceFragment.setSoftKeyIllumination(this);

        getFragmentManager()
                .beginTransaction()
                .replace(
                        com.atakmap.app.SettingsActivity
                                .getAcceptableLookupId(),
                        getPreferenceFragment())
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            try {
                NavUtils.navigateUpFromSameTask(this);
            } catch (IllegalArgumentException iae) {
                Log.d(TAG, "error occurred", iae);
                finish();
            }
        }

        return true;
    }
}
