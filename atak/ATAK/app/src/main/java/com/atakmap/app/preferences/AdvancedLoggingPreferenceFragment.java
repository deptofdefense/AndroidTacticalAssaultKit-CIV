
package com.atakmap.app.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class AdvancedLoggingPreferenceFragment extends AtakPreferenceFragment {

    private static final String TAG = "AdvancedLoggingPreferenceFragment";
    private SharedPreferences _prefs;
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
        _prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }
}
