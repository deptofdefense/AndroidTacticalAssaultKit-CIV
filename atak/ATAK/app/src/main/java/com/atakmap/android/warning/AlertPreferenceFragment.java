
package com.atakmap.android.warning;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;

import com.atakmap.android.emergency.tool.EmergencyManager;
import com.atakmap.android.maps.MapView;
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

        try {
            int res = MapView.getMapView().getContext()
                    .checkCallingOrSelfPermission(Manifest.permission.SEND_SMS);
            if (res != PackageManager.PERMISSION_GRANTED
                    && !EmergencyManager.hasSmsSendPlugin()) {
                Preference p = findPreference("sms_numbers");
                p.setEnabled(false);
            }
        } catch (Exception ignored) {
        }
    }
}
