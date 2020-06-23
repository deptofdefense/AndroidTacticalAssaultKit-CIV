
package com.atakmap.app.preferences;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;

/**
 * Based on <code>SettingsActivity</code>
 */
public class AppMgmtSettingsActivity extends AbstractSettingsActivity {
    public static final String TAG = "AppMgmtSettingsActivity";

    @Override
    protected int getSubtitle() {
        return R.string.app_mgmt_settings;
    }

    @Override
    protected AtakPreferenceFragment getPreferenceFragment() {
        return new AppMgmtPreferenceFragment();
    }
}
