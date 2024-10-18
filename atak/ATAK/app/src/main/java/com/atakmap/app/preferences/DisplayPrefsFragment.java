
package com.atakmap.app.preferences;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class DisplayPrefsFragment extends AtakPreferenceFragment {

    public static final String TAG = "DisplayPrefsFragment";

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                DisplayPrefsFragment.class,
                R.string.displayPreferences,
                R.drawable.ic_display_settings);
    }

    public DisplayPrefsFragment() {
        super(R.xml.display_preferences, R.string.displayPreferences);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());

        Preference basicDisplayPreference = (Preference) findPreference(
                "basic_display_settings");
        basicDisplayPreference
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new BasicDisplayPrefFragment());
                        return true;
                    }
                });

        Preference usableSettings = (Preference) findPreference(
                "usableSettings");
        usableSettings
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new UsabilityPreferenceFragment());
                        return true;
                    }
                });

        Preference dexOptions = (Preference) findPreference("dexOptions");
        dexOptions
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new DexOptionsPreferenceFragment());
                        return true;
                    }
                });

        Preference threeDRendering = (Preference) findPreference("3DRendering");
        threeDRendering
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {

                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new ThreeDRenderingFragment());
                        return true;
                    }
                });

        Preference my_location_icon_preference = findPreference(
                "my_location_icon_color");
        my_location_icon_preference
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                showScreen(new SelfMarkerCustomFragment());
                                return true;
                            }
                        });

        Preference actionbarCustomize = findPreference("my_actionbar_settings");
        actionbarCustomize
                .setOnPreferenceClickListener(
                        new Preference.OnPreferenceClickListener() {
                            @Override
                            public boolean onPreferenceClick(
                                    Preference preference) {
                                showScreen(new ActionBarPreferences());
                                return true;
                            }
                        });

        Preference layerOutlinesPreference = (Preference) findPreference(
                "additionalDisplay");
        layerOutlinesPreference
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new OtherDisplayPreferenceFragment());
                        return true;
                    }
                });
    }
}
