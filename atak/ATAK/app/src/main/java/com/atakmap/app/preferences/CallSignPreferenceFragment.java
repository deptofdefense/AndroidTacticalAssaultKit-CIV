
package com.atakmap.app.preferences;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.text.InputFilter;

import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

public class CallSignPreferenceFragment extends AtakPreferenceFragment {
    public CallSignPreferenceFragment() {
        super(R.xml.call_sign_preference, R.string.callsign);
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                CallSignPreferenceFragment.class,
                R.string.callsign,
                R.drawable.ic_menu_network);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());
        Preference lc = findPreference("locationCallsign");
        ((PanEditTextPreference) lc).setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(40)
        }, false);
    }
}
