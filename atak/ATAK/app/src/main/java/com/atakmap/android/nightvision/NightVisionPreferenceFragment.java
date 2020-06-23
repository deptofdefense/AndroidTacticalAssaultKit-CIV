
package com.atakmap.android.nightvision;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

import java.util.List;

/**"
 *
 */

public class NightVisionPreferenceFragment extends AtakPreferenceFragment
        implements Preference.OnPreferenceClickListener,
        Preference.OnPreferenceChangeListener {

    private Preference launchApp;
    private CheckBoxPreference locMapWidget, locNotification,
            locSelfOverlayBox, main;

    private SharedPreferences _prefs;

    public NightVisionPreferenceFragment() {
        super(R.xml.night_vision_preferences, R.string.toolpref_nightvision);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());

        _prefs = PreferenceManager.getDefaultSharedPreferences(MapView
                .getMapView().getContext());

        main = (CheckBoxPreference) findPreference("night_vision_widget");
        launchApp = findPreference("external_night_vision_launch");
        launchApp
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                //launch the applications main activity
                                Intent launchIntent = getActivity()
                                        .getPackageManager()
                                        .getLaunchIntentForPackage(
                                                "com.atak.nightvision");
                                if (launchIntent != null) {
                                    launchIntent
                                            .setFlags(
                                                    Intent.FLAG_ACTIVITY_NEW_TASK);
                                    startActivity(launchIntent);//null pointer check in case package name was not found
                                }
                                return true;
                            }
                        });

        locMapWidget = (CheckBoxPreference) findPreference("loc_map_widget");
        locNotification = (CheckBoxPreference) findPreference(
                "loc_notification");
        locSelfOverlayBox = (CheckBoxPreference) findPreference(
                "loc_self_overlay_box");

        locMapWidget.setOnPreferenceChangeListener(this);
        main.setOnPreferenceChangeListener(this);
        locNotification.setOnPreferenceChangeListener(this);
        locSelfOverlayBox.setOnPreferenceChangeListener(this);

        main.setOnPreferenceClickListener(this);
        locMapWidget.setOnPreferenceClickListener(this);
        locNotification.setOnPreferenceClickListener(this);
        locSelfOverlayBox.setOnPreferenceClickListener(this);

        //set a default location if one was not checked
        if (!locMapWidget.isChecked() && !locSelfOverlayBox.isChecked()
                && !locNotification.isChecked()) {
            //nothing is currently checked set a default
            locMapWidget.setChecked(true);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (main.isChecked()) {
            //we are doing some checking here to ake sure if the user has the main checked
            //that at least one location preference is selected if its now revert and force the default
            if (!locMapWidget.isChecked() && !locNotification.isChecked()
                    && !locSelfOverlayBox.isChecked()) {
                //none are checked revert to default
                locMapWidget.setChecked(true);
                Toast.makeText(
                        getActivity(),
                        "No Night Vision location set, reverting to default location"
                                +
                                " \"widget on left side of map\"",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Called when a Preference has been clicked.
     * only one of the dependants can be selected
     * so handle that logic here
     *
     * @param preference The Preference that was clicked.
     * @return True if the click was handled.
     */
    @Override
    public boolean onPreferenceClick(Preference preference) {

        if (preference.getKey().equals(main.getKey())) {
            //handles when no locations are selected and enabling the night vision locations
            //if nothing is selected then default to turning on the map widget
            if (main.isChecked()) {
                if (!locMapWidget.isChecked() && !locNotification.isChecked() &&
                        !locSelfOverlayBox.isChecked()) {
                    //set default when enabling the main preference
                    locMapWidget.setChecked(true);
                    return true;
                }
            }
        }
        if (preference.getKey().equals(locMapWidget.getKey())) {
            locSelfOverlayBox.setChecked(false);
            locNotification.setChecked(false);
        } else if (preference.getKey().equals(locNotification.getKey())) {
            locMapWidget.setChecked(false);
            locSelfOverlayBox.setChecked(false);
        } else if (preference.getKey().equals(locSelfOverlayBox.getKey())) {
            locMapWidget.setChecked(false);
            locNotification.setChecked(false);
        }
        return true;
    }

    /**
     * Checks if the service sent in is running state we need to use this method instead
     * of the original enum because we are calling from different packages and applciations that
     * does not access this applications structure
     * @param context the context for obtaining the activity service.
     * @return true if the service is running
     */
    public static boolean isServiceRunning(Context context) {
        ActivityManager manager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> services = manager
                .getRunningServices(Integer.MAX_VALUE);

        if (services == null)
            return false;

        for (ActivityManager.RunningServiceInfo service : services) {
            if ("com.atak.nightvision.NightVisionService"
                    .equals(service.service.getClassName())) {
                Log.d(TAG, "Running...");
                return true;
            }
        }
        Log.d(TAG, "Not Running...");
        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        _prefs.edit().putBoolean(preference.getKey(), (Boolean) newValue)
                .apply();
        return true;
    }
}
