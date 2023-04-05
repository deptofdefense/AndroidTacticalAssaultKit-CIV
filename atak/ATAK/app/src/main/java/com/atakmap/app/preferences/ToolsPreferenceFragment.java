
package com.atakmap.app.preferences;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;

import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.update.AppMgmtActivity;
import com.atakmap.app.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Class used to describe a tools preferences dynamically by plugins.   The resulting preference is
 * made available under the Tools and Preferences sub menu.   The preference implemementation cannot
 * have Preferences nested inside a PreferenceCategory.
 */
public class ToolsPreferenceFragment extends AtakPreferenceFragment {

    public static final String TAG = "ToolsPreferenceFragment";
    public static final int APP_MGMT_REQUEST_CODE = 53279; //arbitrary

    /**
     * Describes the Tool Preference that is to be dynamically registered.
     */
    public static class ToolPreference {
        final String title;
        final String summary;
        final String key;
        final Drawable icon;
        final AtakPreferenceFragment pFrag;

        public ToolPreference(final String title,
                final String summary,
                final String key,
                final Drawable icon,
                final AtakPreferenceFragment pFrag) {

            this.title = title;
            this.summary = summary;
            this.key = key;
            this.icon = icon;
            this.pFrag = pFrag;
        }

        public AtakPreferenceFragment getFrag() {
            return pFrag;
        }

        public String getKey() {
            return key;
        }

        public String getTitle() {
            return title;
        }

        public Drawable getIcon() {
            return icon;
        }

    }

    static final List<ToolPreference> prefs = new ArrayList<>();

    public ToolsPreferenceFragment() {
        super(R.xml.tools_preferences, R.string.toolPreferences);
    }

    /**
     * Dynamically register a Tools preference under the Tool Preference screen.
     */
    public static void register(final ToolPreference tPref) {

        //final String statePref = "showPreferenceItem_"+tPref.key; 
        //Log.d(TAG, "'" + tPref.title.toString().replaceAll("'","") + "','" + statePref + "'");

        for (ToolPreference tprefs : prefs) {
            if (tprefs.key.equals(tPref.key)) {
                prefs.remove(tprefs);//use latest
                break;
            }
        }

        prefs.add(tPref);
        sortPrefsByName();
    }

    /**
     * Dynamically remove a Tools preference under the Tool Preference screen
     * @param key - the key of Tool Preference object to remove
     */
    public static void unregister(final String key) {
        for (ToolPreference tprefs : prefs) {
            if (tprefs.key.equals(key)) {
                prefs.remove(tprefs);
                return;
            }
        }
    }

    private static void sortPrefsByName() {
        if (prefs.size() > 0) {
            Collections.sort(prefs, new Comparator<ToolPreference>() {
                @Override
                public int compare(final ToolPreference object1,
                        final ToolPreference object2) {
                    return object1.title.compareTo(object2.title);
                }
            });
        }
    }

    public static List<ToolPreference> getPreferenceFragments() {
        return new ArrayList<>(prefs);
    }

    public static ToolPreference getPreferenceFragment(final String key) {
        for (final ToolPreference tp : prefs) {
            if (key.equals(tp.key)) {
                return tp;
            }
        }
        return null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Intent intent = getActivity().getIntent();
        //        Bundle externalPrefs = null;
        //        if (intent != null)
        //            externalPrefs = intent.getBundleExtra("externalPrefs");
        //        final Bundle fExternalPrefs = externalPrefs;

        addPreferencesFromResource(getResourceID());

        Preference appsPref = findPreference("appsPref");
        appsPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference pref) {
                Intent mgmtPlugins = new Intent(getActivity(),
                        AppMgmtActivity.class);
                startActivityForResult(mgmtPlugins,
                        APP_MGMT_REQUEST_CODE);
                return true;
            }
        });

        Preference specificToolPref = findPreference("specTools");
        specificToolPref
                .setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    @Override
                    public boolean onPreferenceClick(Preference preference) {
                        showScreen(new SpecificPreferenceFragment());
                        return true;
                    }
                });
    }

    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
    }

}
