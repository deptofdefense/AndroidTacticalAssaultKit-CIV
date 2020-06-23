
package com.atakmap.app.preferences;

import com.atakmap.android.preference.AtakPreferenceFragment;

import com.atakmap.app.R;

/**
 * Based on <code>SettingsActivity</code>
 */
public class LocationSettingsActivity extends AbstractSettingsActivity {
    private static final String TAG = "LocationSettingsActivity";

    @Override
    protected int getSubtitle() {
        return R.string.preferences_text382;
    }

    @Override
    protected AtakPreferenceFragment getPreferenceFragment() {
        return new DevicePreferenceFragment();
    }
}
