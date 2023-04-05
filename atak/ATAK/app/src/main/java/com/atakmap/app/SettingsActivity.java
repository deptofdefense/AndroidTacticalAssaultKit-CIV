
package com.atakmap.app;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.atakmap.android.gui.PanEditTextPreference;
import com.atakmap.android.gui.PanListPreference;
import com.atakmap.android.gui.PanMultiSelectListPreference;
import com.atakmap.android.gui.SMSNumberPreference;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.metrics.activity.MetricPreferenceActivity;
import com.atakmap.android.network.ui.CredentialsPreference;
import com.atakmap.android.nightvision.NightVisionNotification;
import com.atakmap.android.preference.AtakPreferenceFragment;
import com.atakmap.android.preference.PluginPreferenceFragment;
import com.atakmap.android.update.AppMgmtUtils;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.app.preferences.MainPreferencesFragment;
import com.atakmap.app.preferences.MyPreferenceFragment;
import com.atakmap.app.preferences.PreferenceSearchDialog;
import com.atakmap.app.preferences.SearchPreferenceActivity;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.List;

public class SettingsActivity extends MetricPreferenceActivity implements
        FragmentManager.OnBackStackChangedListener {

    public static final String TAG = "SettingsActivity";

    private NightVisionNotification nvn;
    private Menu menu = null;
    private boolean fakeTrail = false;

    //    private FauxNavBar fnb;
    private static final int lookupID = android.R.id.content;

    private SharedPreferences mainControlPrefs;
    private PreferenceFragment toolPreferenceViaShortCut = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);
        AtakPreferenceFragment.setOrientation(SettingsActivity.this);

        setPreferenceContext();

        AtakPreferenceFragment.setSoftKeyIllumination(this);

        mainControlPrefs = PreferenceManager
                .getDefaultSharedPreferences(this);

        PreferenceFragment frag = null;
        Intent intent = getIntent();
        String fragClassName = intent.getStringExtra("frag_class_name");
        if (fragClassName != null) {
            Object object = com.atak.plugins.impl.ClassLoaderHelper
                    .createObject(fragClassName);
            if (object instanceof PreferenceFragment) {
                frag = (PreferenceFragment) object;
            }
            if (frag == null) {
                throw new RuntimeException(
                        "SettingsActivity could not load class \""
                                + fragClassName + "\"");
            }
        }

        ActionBar actionBar = getActionBar();
        if (actionBar != null)
            actionBar.setTitle(ATAKConstants.getVersionName());

        String toolPreference = intent.getStringExtra("toolkey");

        // determine if this is currently a disabled menu item
        if (toolPreference != null) {

            final String statePref = "showPreferenceItem_" + toolPreference;

            //Log.d(TAG, "'" + statePref + "'");
            if (!mainControlPrefs.getBoolean(statePref, true)) {
                toolPreference = null;
            }
        }

        if (toolPreference != null) {
            ToolsPreferenceFragment.ToolPreference tf = ToolsPreferenceFragment
                    .getPreferenceFragment(toolPreference);
            if (tf != null && actionBar != null) {
                frag = tf.getFrag();
                toolPreferenceViaShortCut = frag;
                String title = tf.getTitle();
                if (!FileSystemUtils.isEmpty(title))
                    actionBar.setSubtitle(AtakPreferenceFragment.getSubTitle(
                            this,
                            getString(R.string.toolPreferences), title));
                else
                    actionBar.setSubtitle(R.string.preferences_text416);
                tempkey = intent.getStringExtra("prefkey");
            }
        }

        if (frag == null) {
            frag = getDefaultHomePreferences();
            if (actionBar != null)
                actionBar.setSubtitle(this.getString(R.string.settings));
        }

        if (MapView.getMapView() != null)
            nvn = new NightVisionNotification();

        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true);

        final FragmentManager fm = getFragmentManager();

        fm.addOnBackStackChangedListener(this);

        if (toolPreferenceViaShortCut != null) {

            // Make sure the tool preference isn't already pushed to another
            // fragment manager - pop until it's off the stack
            FragmentManager otherFM;
            while ((otherFM = toolPreferenceViaShortCut
                    .getFragmentManager()) != null)
                otherFM.popBackStackImmediate();

            Log.d(TAG, "fake construction of the trail");
            if (!isLegacyPreferenceLayout()) {
                fm.beginTransaction()
                        .replace(lookupID, new MyPreferenceFragment())
                        .commit();
            }
            fm.beginTransaction()
                    .replace(lookupID, new MainPreferencesFragment())
                    .commit();
            fm.beginTransaction()
                    .replace(lookupID, new ToolsPreferenceFragment())
                    .addToBackStack(null)
                    .commit();
            fm.beginTransaction()
                    .replace(lookupID, toolPreferenceViaShortCut)
                    .addToBackStack(null)
                    .commit();

            fakeTrail = true;
            return;
        } else {
            fakeTrail = false;
        }

        // Removing for 3.10.1 release, revisit for 3.11
        //fnb = FauxNavBar.constructFauxNavBar(this);
        //if (fnb != null)
        //    lookupID = com.atakmap.app.R.id.visual_content;

        fm.beginTransaction()
                .replace(lookupID, frag)
                .commit();

        tempkey = intent.getStringExtra("prefkey");

        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(
                "com.atakmap.app.QUITAPP",
                "Intent to start the quiting process, if the boolean extra FORCE_QUIT is set, the application will not prompt the user before quitting");

        if (MapView.getMapView() != null)
            AtakBroadcast.getInstance().registerReceiver(_quitReceiver, filter);

    }

    // XXX - Is there ever a situation where any of these would be different
    // from one another? Why 6 different calls?
    private void setPreferenceContext() {
        AtakPreferenceFragment.setContext(this);
        PanEditTextPreference.setContext(this);
        PanListPreference.setContext(this);
        PanMultiSelectListPreference.setContext(this);
        SMSNumberPreference.setContext(this);
        CredentialsPreference.setContext(this);
    }

    private String tempkey;

    private final BroadcastReceiver _quitReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            SettingsActivity.this.finish();
        }
    };

    /**
     * If the FauxNavBar initialization succeeded or failed, get the acceptable lookup identifer.
     * @return either com.atakmap.app.R.id.visual_content or android.R.id.content respectively.
     */
    public static int getAcceptableLookupId() {
        return lookupID;
    }

    @Override
    protected void onPause() {
        if (nvn != null)
            nvn.cancelNotification();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        AtakBroadcast.getInstance().unregisterReceiver(_quitReceiver);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.setting_menu, menu);
        this.menu = menu;
        onBackStackChanged();
        return true;
    }

    /**
     * handles showing the night vision when changing from MapActivity to Settings Activity
     * if night vision notification is enabled then show it
     */
    private void handleNightVision() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        if (prefs.getBoolean("loc_notification", false)
                && prefs.getBoolean("night_vision_widget", false)) {
            if (AppMgmtUtils.isInstalled(this, "com.atak.nightvision")
                    && nvn != null)
                nvn.dispatchNotification();
        }
    }

    /**
     * handles showing the night vision when changing from MapActivity to Settings Activity
     * if night vision notification is enabled then show it this handles it when going out of atak and back in
     * and returning to this activity and not the mapview activiity
     */
    @Override
    protected void onResume() {
        setPreferenceContext();
        AtakPreferenceFragment.setOrientation(SettingsActivity.this);
        handleNightVision();
        super.onResume();
    }

    private void showMenuOptions(boolean home, boolean search) {
        if (menu != null) {
            MenuItem item = menu.findItem(R.id.action_home_settings);
            item.setVisible(home);
            if (BuildConfig.FLAVOR == "civUIMods") {
                item = menu.findItem(R.id.backBtn);
                item.setVisible(home);
            }
            item = menu.findItem(R.id.action_home_search);
            item.setVisible(search);

        }
    }

    @Override
    public void onBackStackChanged() {
        Fragment f = getFragmentManager()
                .findFragmentById(lookupID);
        if (f != null) {
            boolean home = isLegacyPreferenceLayout()
                    ? (f instanceof MainPreferencesFragment)
                    : (f instanceof MyPreferenceFragment);
            boolean search = (f instanceof MyPreferenceFragment);
            showMenuOptions(!home, search);
        }

        if (tempkey != null) {
            scrollToItem((PreferenceFragment) f, tempkey);
            tempkey = null;
        }
    }

    /**
     * Method that provided a fragment with a valid activity will scroll to the preference
     * described by the preferenceKey.
     * @param preferenceFragment the fragment used to look up the preference.
     * @param preferenceKey the key that describes the preference.
     */
    public void scrollToItem(final PreferenceFragment preferenceFragment,
            final String preferenceKey) {
        if (preferenceFragment == null)
            return;

        final Activity activity = preferenceFragment.getActivity();
        if (activity == null)
            return;

        final Preference p = preferenceFragment.findPreference(preferenceKey);

        if (p == null)
            return;

        final ListAdapter la = preferenceFragment.getPreferenceScreen()
                .getRootAdapter();

        final View v = preferenceFragment.getView();
        if (v == null)
            return;

        final ListView lv = v.findViewById(android.R.id.list);
        if (lv == null)
            return;

        for (int i = 0; i < la.getCount(); ++i) {
            if (la.getItem(i) == p) {
                final int fi = i;
                v.post(new Runnable() {
                    @Override
                    public void run() {
                        lv.setSelection(fi);
                        blink(v, p);
                    }
                });
            }
        }
    }

    private void blink(final View v, final Preference preference) {
        boolean enabled = preference.isEnabled();
        int[] pattern = {
                100, 150, 350, 400
        };
        for (int i = 0; i < 4; ++i) {
            enabled = !enabled;
            final boolean fenabled = enabled;
            v.postDelayed(new Runnable() {
                @Override
                public void run() {
                    preference.setShouldDisableView(true);
                    preference.setEnabled(fenabled);
                }
            }, pattern[i]);
        }
    }

    private boolean isLegacyPreferenceLayout() {
        if (mainControlPrefs == null) {
            mainControlPrefs = PreferenceManager
                    .getDefaultSharedPreferences(this);
        }

        return mainControlPrefs.getBoolean("legacy_settings", false);
    }

    private PreferenceFragment getDefaultHomePreferences() {
        if (isLegacyPreferenceLayout()) {
            return new MainPreferencesFragment();
        } else {
            return new MyPreferenceFragment();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == R.id.action_home_settings) {
            try {
                ActionBar actionBar = getActionBar();
                PreferenceFragment frag = getDefaultHomePreferences();
                if (actionBar != null)
                    actionBar.setSubtitle(this.getString(R.string.settings));
                getFragmentManager().popBackStackImmediate(null,
                        FragmentManager.POP_BACK_STACK_INCLUSIVE);
                getFragmentManager().beginTransaction()
                        .replace(lookupID, frag)
                        .commit();
            } catch (IllegalStateException ise) {
                Log.e(TAG,
                        "error occurred wehn attempting to go back to home, just close out of settings");
                finish();
            }
        } else if (i == android.R.id.home) {
            try {
                super.onBackPressed();
            } catch (java.lang.IllegalStateException e) {
                Log.d(TAG, "warning", e);
                finish();
            }
        } else if (i == R.id.action_home_search) {
            search();
        } else if (i == R.id.closeButton) {
            finish();
        } else if (i == R.id.backBtn) {
            super.onBackPressed();
        }
        return true;
    }

    private void search() {
        if (BuildConfig.FLAVOR == "civUIMods") {
            Intent mgmtPlugins = new Intent(MapView.getMapView().getContext(),
                    SearchPreferenceActivity.class);
            startActivityForResult(mgmtPlugins,
                    SearchPreferenceActivity.PREFERENCE_SEARCH_CODE);
        } else {
            new PreferenceSearchDialog(this).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (fakeTrail && toolPreferenceViaShortCut != null &&
                toolPreferenceViaShortCut.isVisible()) {
            finish();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Start a new settings activity with a preference fragment class
     * @param clazz Preference fragment class
     */
    public static void start(Class<?> clazz) {
        start(clazz, null);
    }

    /**
     * Start a new settings activity with a preference fragment class
     * @param clazz Preference fragment class
     * @param key the preference key to scroll to.
     */
    public static void start(Class<?> clazz, String key) {
        MapView mv = MapView.getMapView();
        if (mv == null || clazz == null
                || !AtakPreferenceFragment.class.isAssignableFrom(clazz)) {
            Log.e(TAG, "cannot start with invalid preference fragment: "
                    + clazz);
            return;
        }

        //to avoid hard crash in onCreate, error out if not possible to create fragment
        if (PluginPreferenceFragment.class.isAssignableFrom(clazz)) {

            List<ToolsPreferenceFragment.ToolPreference> list = ToolsPreferenceFragment
                    .getPreferenceFragments();
            boolean found = false;
            for (ToolsPreferenceFragment.ToolPreference tp : list) {
                if (tp.getFrag().getClass().getName().equals(clazz.getName())) {
                    found = true;
                }
            }
            if (!found) {
                Log.d(TAG, "could not find the preference to launch: "
                        + clazz.getName());
                return;
            }
        }

        Intent intent = new Intent(mv.getContext(), SettingsActivity.class);
        intent.putExtra("frag_class_name", clazz.getName());
        if (key != null)
            intent.putExtra("prefkey", key);
        mv.getContext().startActivity(intent);
    }

    /**
     * Start a new settings activity with a preference fragment class
     * @param toolkey the preference toolkey to show.
     * @param prefkey the key for the preference to scroll to
     */
    public static void start(String toolkey, String prefkey) {
        Intent i = new Intent(
                "com.atakmap.app.ADVANCED_SETTINGS");

        i.putExtra("toolkey", toolkey);
        if (prefkey != null)
            i.putExtra("prefkey", prefkey);

        AtakBroadcast.getInstance().sendBroadcast(i);
    }
}
