
package com.atakmap.android.metricreport;

import android.content.Context;

import com.atakmap.android.preference.PluginPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;
import android.os.Bundle;

public class MetricReportPreferenceFragment extends PluginPreferenceFragment {

    private static final String TAG = "MetricReportPreferenceFragment";
    private static Context staticPluginContext;

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                MetricReportPreferenceFragment.class,
                R.string.metricreport_pref,
                R.drawable.ic_menu_settings);
    }

    /**
     * Only will be called after this has been instantiated with the 1-arg constructor.
     * Fragments must has a zero arg constructor.
     */
    public MetricReportPreferenceFragment() {
        super(staticPluginContext, R.xml.metrics_preferences);
    }

    @SuppressWarnings("ValidFragment")
    public MetricReportPreferenceFragment(final Context pluginContext) {
        super(pluginContext, R.xml.metrics_preferences);
        staticPluginContext = pluginContext;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle("Tool Preferences", "MetricReport Preferences");
    }

}
