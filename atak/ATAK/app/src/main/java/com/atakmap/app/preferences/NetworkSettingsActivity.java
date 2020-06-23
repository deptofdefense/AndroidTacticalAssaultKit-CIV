
package com.atakmap.app.preferences;

import com.atakmap.android.preference.AtakPreferenceFragment;

import com.atakmap.app.R;

/**
 * Based on <code>SettingsActivity</code>
 */
public class NetworkSettingsActivity extends AbstractSettingsActivity {
    private static final String TAG = "NetworkSettingsActivity";

    @Override
    protected int getSubtitle() {
        return R.string.preferences_text411;
    }

    @Override
    protected AtakPreferenceFragment getPreferenceFragment() {
        return new NetworkConnectionPreferenceFragment();
    }
}
