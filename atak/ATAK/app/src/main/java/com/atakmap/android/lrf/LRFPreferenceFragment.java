
package com.atakmap.android.cot;

import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;

import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;
import com.atakmap.app.preferences.DevicePreferenceFragment;

public class LRFPreferenceFragment extends AtakPreferenceFragment {
    private PreferenceCategory networkCategory;
    private ListPreference mockCheckPref;
    private EditTextPreference listenPort;
    private String _previous = "";

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                LRFPreferenceFragment.class,
                R.string.lrf_preferences,
                R.drawable.lrf_slide);
    }

    public LRFPreferenceFragment() {
        super(R.xml.lrf_preferences, R.string.lrf_preferences);
    }

    @Override
    public String getSubTitle() {
        return getSubTitle(getString(R.string.lrf_preferences),
                getSummary());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(getResourceID());

    }

}
