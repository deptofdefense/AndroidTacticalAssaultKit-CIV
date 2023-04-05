
package com.atakmap.android.preference;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

import com.atakmap.app.R;

public abstract class PluginPreferenceFragment extends AtakPreferenceFragment {

    public static final String TAG = "PluginPreferenceFragment";
    public static final int DEFAULT_PREF_SUMMARY_ID = R.string.pluginPreferences;

    protected final Context pluginContext;

    public PluginPreferenceFragment(final Context pluginContext,
            final int resourceID) {
        this(pluginContext, resourceID, DEFAULT_PREF_SUMMARY_ID);
    }

    public PluginPreferenceFragment(final Context pluginContext,
            final int resourceID, final int summaryID) {
        super(resourceID, summaryID);
        this.pluginContext = pluginContext;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceScreen settingsView;
        settingsView = inflatePreferenceScreenFromResource(pluginContext,
                getResourceID());

        settingsView = createContextLocalPreferenceScreen(
                getPreferenceManager(), getActivity(), settingsView);

        setPreferenceScreen(settingsView);
    }

    /**
     * May be overidden to provide more detailed action bar subtitle
     * @return the subtitle name
     */
    @Override
    public String getSubTitle() {
        return getSummary();
    }

    static PreferenceScreen createContextLocalPreferenceScreen(
            final PreferenceManager prefMgr, final Context applicationContext,
            final PreferenceScreen resCustomPreferenceScreen) {
        final PreferenceScreen retval = prefMgr
                .createPreferenceScreen(applicationContext);

        // Add the contents of the custom preference screen to the preference category
        if (resCustomPreferenceScreen != null) {
            for (int j = 0; j < resCustomPreferenceScreen
                    .getPreferenceCount(); j++) {
                final Preference preferenceWidget = resCustomPreferenceScreen
                        .getPreference(j);
                preferenceWidget.setOrder(j);
                retval.addPreference(preferenceWidget);
            }
        }
        return retval;
    }
}
