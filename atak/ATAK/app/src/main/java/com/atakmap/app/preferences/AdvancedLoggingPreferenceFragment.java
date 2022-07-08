
package com.atakmap.app.preferences;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;

import com.atakmap.android.gui.HintDialogHelper;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class AdvancedLoggingPreferenceFragment extends AtakPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "AdvancedLoggingPreferenceFragment";
    private Context context;

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                AdvancedLoggingPreferenceFragment.class,
                R.string.advancedloggingPreferences,
                R.drawable.ic_menu_settings);
    }

    public AdvancedLoggingPreferenceFragment() {
        super(R.xml.advanced_logging_preferences,
                R.string.advancedloggingPreferences);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.loggingPreferences),
                getSummary());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());

        context = getActivity();

        Preference collectMetrics = findPreference("collect_metrics");
        if (collectMetrics != null)
            collectMetrics.setOnPreferenceChangeListener(this);

        Preference loggingDisclaimer = findPreference("loggingDisclaimer");
    }

    @Override
    public boolean onPreferenceChange(Preference pref, Object value) {
        if (pref.getKey().equals("collect_metrics") && value == Boolean.TRUE) {
            // Show metrics hint dialog
            HintDialogHelper.showHint(context,
                    getString(R.string.metric_plugin_hint),
                    getString(R.string.metric_plugin_hint_message),
                    "tak.hint.logging.metric");
        }
        return true;
    }
}
