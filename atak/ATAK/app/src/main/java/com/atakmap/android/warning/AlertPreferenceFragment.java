
package com.atakmap.android.warning;

import android.os.Bundle;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;

public class AlertPreferenceFragment extends AtakPreferenceFragment {

    public AlertPreferenceFragment() {
        super(R.xml.alert_preferences, R.string.alertPreferences);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.toolPreferences),
                getSummary());
    }

    @Override
    public void onCreate(Bundle savedInstanceBundle) {
        super.onCreate(savedInstanceBundle);
        addPreferencesFromResource(getResourceID());
    }
}
