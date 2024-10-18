
package com.atakmap.app.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PreferenceSearchIndex;
import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.List;

public class SpecificPreferenceFragment extends AtakPreferenceFragment {
    public SpecificPreferenceFragment() {
        super(R.xml.specific_tool_preference, R.string.spec_tool_pref);
    }

    public static java.util.List<PreferenceSearchIndex> index(Context context) {
        return index(context,
                SpecificPreferenceFragment.class,
                R.string.spec_tool_pref,
                R.drawable.ic_menu_network);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(getResourceID());
        createPreferenceScreen();
    }

    @Override
    public void onResume() {
        super.onResume();
        createPreferenceScreen();
    }

    final private List<Preference> removeList = new ArrayList<>();

    private synchronized void createPreferenceScreen() {
        for (Preference r : removeList)
            removePreference(r);
        removeList.clear();

        final SharedPreferences _mainControlPrefs = PreferenceManager
                .getDefaultSharedPreferences(getActivity());
        for (final ToolsPreferenceFragment.ToolPreference tp : ToolsPreferenceFragment
                .getPreferenceFragments()) {
            Preference p = new Preference(getActivity());
            p.setTitle(tp.title);
            p.setKey(tp.key);
            p.setIcon(tp.icon);
            p.setOnPreferenceClickListener(
                    new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(
                                final Preference preference) {
                            showScreen(
                                    tp.pFrag,
                                    getSubTitle(
                                            getString(R.string.toolPreferences),
                                            tp.title));
                            return true;
                        }
                    });
            final String statePref = "showPreferenceItem_" + tp.key;

            //Log.d(TAG, "'" + statePref + "'");
            if (!_mainControlPrefs.getBoolean(statePref, true)) {
                p.setEnabled(false);
                p.setShouldDisableView(true);
            }

            getPreferenceScreen().addPreference(p);
            removeList.add(p);
        }
    }
}
