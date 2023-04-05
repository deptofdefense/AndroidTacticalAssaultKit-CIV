
package com.atakmap.app.preferences;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;

import com.atakmap.android.image.MediaPreferenceFragment;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class ControlPrefsFragment extends AtakPreferenceFragment {

    private static final String TAG = "ControlPrefsFragment";

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                ControlPrefsFragment.class,
                R.string.controlPreferences,
                R.drawable.ic_menu_settings);
    }

    public ControlPrefsFragment() {
        super(R.xml.control_preferences, R.string.controlPreferences);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());
        Preference mediaPreference = findPreference("mediaPreference");
        mediaPreference
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                showScreen(new MediaPreferenceFragment());
                                return false;
                            }
                        });

        Preference staleData = findPreference("staleData");
        staleData.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new StaleDataPreferenceFragment());
                        return true;
                    }
                });

        Preference lockBehavior = findPreference("lockBehavior");
        lockBehavior.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new LockingBehaviorFragment());
                        return true;
                    }
                });

        Preference userTouch = findPreference("userTouch");
        userTouch.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new UserTouchPreferenceFragment());
                        return true;
                    }
                });

        Preference selfCoordinate = findPreference("selfCoordinate");
        selfCoordinate.setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new SelfCoordinatePreferenceFragment());
                        return true;
                    }
                });
    }

}
