
package com.atakmap.android.preference;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import android.content.res.Configuration;

import com.atakmap.android.metrics.MetricsApi;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.app.preferences.ToolsPreferenceFragment;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.app.SettingsActivity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AtakPreferenceFragment extends PreferenceFragment {

    public static final String TAG = "AtakPreferenceFragment";

    // this should be set up once by the main ATAK activity. we need to keep
    // a strong reference to the listener because Google got too cute with the
    // impl:
    // http://stackoverflow.com/questions/2542938/sharedpreferences-onsharedpreferencechangelistener-not-being-called-consistently
    static SharedPreferences.OnSharedPreferenceChangeListener listener;
    static SharedPreferences _mainControlPrefs; // ugg

    protected Bundle _externalPreferenceBundle;

    private static Context appContext;

    private final Map<String, Preference> allkeys = new HashMap<>();

    /**
     * For plugins we are REQUIRED to set the application context to the
     * ATAK owned Activity and not the context owned by the plugin.
     */
    public static void setContext(Context c) {
        appContext = c;
    }

    /**
     * XML prefs resource ID
     */
    private final int resourceID;

    /**
     * Summary string resource ID
     */
    private final int summaryID;

    /**
     * Construct a preference fragment with a provided resource identifier
     * @param resourceID the resource id that describes the preference fragment.
     * @param summaryID the resource id that describes the summary/label.
     */
    public AtakPreferenceFragment(final int resourceID, final int summaryID) {
        this.resourceID = resourceID;
        this.summaryID = summaryID;
    }

    final public int getResourceID() {
        return resourceID;
    }

    final public int getSummaryID() {
        return summaryID;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (getActivity() != null) {
            super.onCreate(null);
            setOrientation(getActivity());
        } else {
            Log.d(TAG, "error occurred: " + getClass() +
                    " does not have an associated activity");
        }
    }

    @Override
    public void addPreferencesFromResource(int preferencesResId) {
        super.addPreferencesFromResource(preferencesResId);
        processPreferences();
    }

    @Override
    public void addPreferencesFromIntent(Intent intent) {
        super.addPreferencesFromIntent(intent);
        processPreferences();
    }

    private void processPreferences() {
        try {
            final PreferenceScreen screen = getPreferenceScreen();
            final int count = getPreferenceScreen().getPreferenceCount();
            final List<Preference> pList = new ArrayList<>();
            for (int i = 0; i < count; ++i) {
                pList.add(screen.getPreference(i));
            }
            for (Preference p : pList)
                processPreference(p);
        } catch (Exception e) {
            Log.e(TAG, "error processing preferences", e);

        }
    }

    @Override
    public Preference findPreference(final CharSequence key) {

        if (_mainControlPrefs == null)
            _mainControlPrefs = PreferenceManager
                    .getDefaultSharedPreferences(getActivity());

        Preference p = super.findPreference(key);
        if (p == null)
            p = allkeys.get(key.toString());
        return p;
    }

    private void processPreference(final Preference p) {

        if (_mainControlPrefs == null)
            _mainControlPrefs = PreferenceManager
                    .getDefaultSharedPreferences(getActivity());

        if (p == null)
            return;
        if (p instanceof PreferenceCategory) {

            try {
                final PreferenceCategory pc = (PreferenceCategory) p;
                final int count = pc.getPreferenceCount();
                final List<Preference> pList = new ArrayList<>();
                for (int i = 0; i < count; ++i) {
                    pList.add(pc.getPreference(i));
                }
                for (Preference subp : pList)
                    processPreference(subp);
            } catch (Exception e) {
                Log.e(TAG, "error processing preferences", e);
            }

        }

        final String key = p.getKey();
        if (key != null) {
            allkeys.put(key, p);

            final String disablePref = "disablePreferenceItem_" + key;
            //Log.d(TAG, "'" + p.getTitle().toString().replaceAll("'","") + "','" + disablePref + "'");
            if (_mainControlPrefs.getBoolean(disablePref, false)) {
                final Activity a = getActivity();
                final Runnable r = new Runnable() {
                    public void run() {
                        try {
                            p.setEnabled(false);
                            p.setShouldDisableView(true);
                        } catch (Exception e) {
                            Log.e(TAG, "error occurred disabling: " + key);
                        }
                    }
                };
                if (a == null)
                    r.run();
                else
                    a.runOnUiThread(r);
            }

            final String hidePref = "hidePreferenceItem_" + key;
            if (_mainControlPrefs.getBoolean(hidePref, false)) {
                try {
                    //Log.d(TAG, "removing: " + p.getKey());
                    removePreference(p);
                } catch (Exception e) {
                    Log.e(TAG, "error occurred hiding: " + key);
                }
            }

            String dependsOnKey = p.getDependency();
            if (dependsOnKey != null) {
                final String dependsHidePref = "hidePreferenceItem_"
                        + dependsOnKey;
                if (_mainControlPrefs.getBoolean(dependsHidePref, false)) {
                    //Log.d(TAG, "removing dependency: " + key);
                    try {
                        removePreference(p);
                    } catch (Exception e) {
                        Log.e(TAG,
                                "error occurred hiding dependent preference: "
                                        + key);
                    }
                }
            }
        }

    }

    @Override
    public void onResume() {
        super.onResume();

        String title = getSubTitle();
        if (FileSystemUtils.isEmpty(title))
            title = ATAKConstants.getVersionName();
        ActionBar actionBar = getActivity().getActionBar();
        if (actionBar != null)
            actionBar.setSubtitle(title);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    protected void showScreen(PreferenceFragment pf) {
        showScreen(pf, null);
    }

    protected void showScreen(PreferenceFragment pf, String title) {
        if (!FileSystemUtils.isEmpty(title)) {
            ActionBar actionBar = getActivity().getActionBar();
            if (actionBar != null)
                actionBar.setSubtitle(title);
        }

        if (MetricsApi.shouldRecordMetric()) {
            Bundle b = new Bundle();
            b.putString("title", getSubTitle());
            MetricsApi.record("preference_screen", b);
        }

        Log.d(TAG, "call to show screen: " + pf.getClass());

        // a bit of incest, SettingsActivity.getAcceptableLookupId should be something I can lookup here
        // refactor to remove code dependency.   Should not need this if the insertion of the FauxNavBar
        // logic is always sucessfull.
        getFragmentManager()
                .beginTransaction()
                .replace(SettingsActivity.getAcceptableLookupId(), pf)
                .addToBackStack("settings")
                .commit();
    }

    public String getSummary() {
        return getString(summaryID);
    }

    /**
     * Fragment implementations should return the sub title for display in action bar
     * @return returns a subtitle.
     */
    public String getSubTitle() {
        return getSubTitle(null, getSummary());
    }

    public String getSubTitle(String parent, String title) {
        return getSubTitle(getActivity(), parent, title);
    }

    public static String getSubTitle(final Context context, String parent,
            String title) {

        String path = "";
        if (context != null) {
            path = context.getString(com.atakmap.app.R.string.settings)
                    + "/";
            if (!FileSystemUtils.isEmpty(parent))
                path += parent + "/";
        }

        path += title;

        return path;
    }

    protected void printBundleContents(String tag, Bundle bundle) {
        Log.d(tag, "begin bundle contents");
        if (bundle != null) {
            Log.d(tag, "start");
            for (String key : bundle.keySet()) {
                Object value = bundle.get(key);
                if (value != null) {
                    Log.d(tag, "key: " + key + " value: "
                            + value);
                }
            }
            Log.d(tag, "end");
        }
        Log.d(tag, "finished");
    }

    static public int getOrientation(Context context) {
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);
        if (prefs.getBoolean("atakControlForcePortrait", false)) {
            return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        } else if (prefs.getBoolean("atakControlReverseOrientation", false)) {
            return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        }

        return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    }

    /**
     * Based on the curent preferences, set the soft illumination key based
     * according to the google android documentation.  
     */
    static public void setSoftKeyIllumination(final Activity activity) {
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(activity);
        final boolean disable = prefs
                .getBoolean("atakDisableSoftkeyIllumination", true);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final Window win = activity.getWindow();
                final WindowManager.LayoutParams winParams = win
                        .getAttributes();
                if (disable) {
                    winParams.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
                } else {
                    winParams.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
                }
                win.setAttributes(winParams);
            }
        });
        // last ditch effort
        try {
            if (android.os.Build.MANUFACTURER.equals("samsung")) {
                try {
                    android.provider.Settings.System.putInt(activity
                            .getApplicationContext()
                            .getContentResolver(), "button_key_light",
                            disable ? 0
                                    : 1500);
                } catch (Exception e) {
                    // if this fails, then it fails - do not go through the 
                    // process of launching a screen so a user can enable
                    // system preferences.
                    Log.d(TAG,
                            "WRITE_SETTINGS is not enabled for this application");
                }
            } else {
                try {
                    //native android OS uses a binary 0 , 1 switch to set hardware lights
                    Runtime r = Runtime.getRuntime();
                    r.exec("echo "
                            + (disable ? '0' : '1')
                            + " > /system/class/leds/keyboard-backlight/brightness");
                } catch (Exception e) {
                    Log.d(TAG, "error occurred setting class leds", e);
                }
            }
        } catch (Exception ignored) {
            Log.d(TAG, "error occurred setting led brightness");
        }
    }

    static public void setOrientation(Activity activity) {
        setOrientation(activity, false);
    }

    /**
     * sets the orientation of the screen.
     * @param activity the activity to set the orientation on.
     * @param force forces the orientation change request even if the orientation is already set correctly.
     */
    static public void setOrientation(final Activity activity,
            final boolean force) {
        int desiredOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        SharedPreferences _controlPrefs = PreferenceManager
                .getDefaultSharedPreferences(activity);
        if (_controlPrefs.getBoolean("atakControlForcePortrait", false)) {
            if (_controlPrefs
                    .getBoolean("atakControlReverseOrientation", false)) {
                desiredOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            } else {
                desiredOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            }
        } else if (_controlPrefs.getBoolean("atakControlReverseOrientation",
                false)) {
            desiredOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
        }

        activity.getWindow().addFlags(LayoutParams.FLAG_DISMISS_KEYGUARD);
        checkScreenLock(activity);

        final Configuration configuration = activity.getResources()
                .getConfiguration();
        if (configuration.orientation != desiredOrientation) {
            Log.d(TAG, "current orientation != desired orienation");
            activity.setRequestedOrientation(desiredOrientation);
        } else {
            Log.d(TAG, "current orientation == desired orienation");
            if (force)
                activity.setRequestedOrientation(desiredOrientation);
        }
    }

    static public void checkScreenLock(Activity activity) {
        SharedPreferences _controlPrefs = PreferenceManager
                .getDefaultSharedPreferences(activity);
        boolean screenLock = _controlPrefs.getBoolean("atakScreenLock", false);
        if (screenLock) {
            activity.getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            activity.getWindow().clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @SuppressLint({
            "MissingPermission"
    })
    static public void checkKeyGuard(Activity activity) {
        KeyguardManager km = (KeyguardManager) activity
                .getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null)
            return;

        // TODO - migrate to using android.view.WindowManager.LayoutParams#FLAG_DISMISS_KEYGUARD
        // per KeyGuardManager::KeyGuardLock

        KeyguardLock kl = km.newKeyguardLock("atakKeyGuard");

        SharedPreferences _controlPrefs = PreferenceManager
                .getDefaultSharedPreferences(activity);

        try {
            if (_controlPrefs.getBoolean("atakDisableKeyguard", false)) {
                kl.disableKeyguard();
            } else {
                kl.reenableKeyguard();
            }
        } catch (Exception e) {
            Log.d(TAG, "error", e);
        }
    }

    static public void setMainWindowActivity(final Activity activity) {
        _mainControlPrefs = PreferenceManager
                .getDefaultSharedPreferences(activity);
        AtakPreferenceFragment.listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs,
                    String key) {

                if (key == null)
                    return;

                if (key.compareToIgnoreCase("atakDisableKeyGuard") == 0) {
                    checkKeyGuard(activity);
                } else if (key.compareToIgnoreCase("atakScreenLock") == 0) {
                    checkScreenLock(activity);
                }
            }
        };

        _mainControlPrefs.registerOnSharedPreferenceChangeListener(listener);

        AtakPreferenceFragment.checkKeyGuard(activity);

    }

    static public void removeMainWindowActivity() {
        if (_mainControlPrefs != null)
            _mainControlPrefs
                    .unregisterOnSharedPreferenceChangeListener(listener);
        listener = null;
        _mainControlPrefs = null;
    }

    public void addPreferences(Bundle bundle) {
        _externalPreferenceBundle = bundle;
    }

    /**
     * Ability to programatically toggle on or off the state of the preference
     * @param p the preference to enable or disable.
     * @param state the state of the preference (true enabled, false disabled)
     */
    protected void setPreferenceState(final Preference p,
            final boolean state) {
        if (p == null)
            return;

        final Activity a = getActivity();
        final Runnable r = new Runnable() {
            public void run() {
                try {
                    p.setShouldDisableView(true);
                    p.setEnabled(state);
                } catch (Exception e) {
                    Log.e(TAG, "error occurred disabling: " + p.getTitle());
                }
            }
        };
        if (a == null)
            r.run();
        else
            a.runOnUiThread(r);
    }

    /**
     * Method to assist in removal of a preference.   Once removed there really
     * is no way to add it back in.
     * @param preference the preference to remove
     */
    protected void removePreference(final Preference preference) {
        PreferenceGroup parent = getParent(getPreferenceScreen(), preference);
        if (parent == null)
            throw new RuntimeException("Couldn't find preference");

        parent.removePreference(preference);
    }

    private PreferenceGroup getParent(final PreferenceGroup groupToSearchIn,
            final Preference preference) {
        for (int i = 0; i < groupToSearchIn.getPreferenceCount(); ++i) {
            Preference child = groupToSearchIn.getPreference(i);

            if (child == preference)
                return groupToSearchIn;

            if (child instanceof PreferenceGroup) {
                PreferenceGroup childGroup = (PreferenceGroup) child;
                PreferenceGroup result = getParent(childGroup, preference);
                if (result != null)
                    return result;
            }
        }

        return null;
    }

    /**
     * Inflates a {@link android.preference.PreferenceScreen PreferenceScreen} from the specified
     * resource. Based on:
     *     http://pilcrowpipe.blogspot.com/2012/12/modular-approach-to-implementing.html
     *
     * The resource should come from {@code R.xml}.
     *
     * @param context The context.
     * @param resId The ID of the XML file.
     * @return The preference screen or null on failure.
     */
    @SuppressLint("PrivateApi")
    static PreferenceScreen inflatePreferenceScreenFromResource(
            Context context, int resId) {
        try {
            // The Android API doesn't provide a publicly available method to inflate preference
            // screens from an XML resource into a PreferenceScreen object so we use reflection here
            // to get access to PreferenceManager's private inflateFromResource method.
            Constructor<PreferenceManager> preferenceManagerCtor = PreferenceManager.class
                    .getDeclaredConstructor(Context.class);
            preferenceManagerCtor.setAccessible(true);
            PreferenceManager preferenceManager = preferenceManagerCtor
                    .newInstance(context);
            Method inflateFromResourceMethod = PreferenceManager.class
                    .getDeclaredMethod(
                            "inflateFromResource", Context.class, int.class,
                            PreferenceScreen.class);
            return (PreferenceScreen) inflateFromResourceMethod.invoke(
                    preferenceManager, context, resId, null);
        } catch (Exception e) {
            Log.w("PluginPreferenceActivity",
                    "Could not inflate preference screen from XML resource ID "
                            + resId,
                    e);
        }

        return null;
    }

    protected static List<PreferenceSearchIndex> index(Context context,
            Class<? extends AtakPreferenceFragment> clazz,
            int summaryResourceId, int drawableResourceId) {
        return index(context, clazz, summaryResourceId, summaryResourceId,
                drawableResourceId);
    }

    /**
     * Create search index for the specific preference fragment
     *
     * @param context
     * @param clazz
     * @param summaryResourceId
     * @param drawableResourceId
     * @return null if there is an error, otherwise returns a PreferenceSearchIndex object created
     * from the provided AtakPreferenceFragment.class.
     */
    protected static List<PreferenceSearchIndex> index(Context context,
            Class<? extends AtakPreferenceFragment> clazz,
            int summaryResourceId, Integer parentSummaryResourceId,
            int drawableResourceId) {

        List<PreferenceSearchIndex> retval = new ArrayList<>();

        String parentSummary = (parentSummaryResourceId == null ? null
                : context.getString(parentSummaryResourceId));
        PreferenceSearchIndex parent = new PreferenceSearchIndex(clazz,
                null, null,
                context.getString(summaryResourceId),
                parentSummary,
                context.getDrawable(drawableResourceId),
                Collections
                        .singletonList(context.getString(summaryResourceId)));

        retval.add(parent);
        try {
            index(retval, null,
                    inflatePreferenceScreenFromResource(context,
                            clazz.newInstance().getResourceID()),
                    clazz, parentSummary);
        } catch (Exception e) {
            Log.d(TAG, "error", e);
        }
        return retval;
    }

    protected static List<PreferenceSearchIndex> index(Context context,
            Class<? extends AtakPreferenceFragment> clazz,
            String key,
            int summaryResourceId, int parentSummaryResourceId,
            int drawableResourceId, String... terms) {
        return Collections.singletonList(new PreferenceSearchIndex(clazz,
                key, key,
                context.getString(summaryResourceId),
                context.getString(parentSummaryResourceId),
                context.getDrawable(drawableResourceId),
                Arrays.asList(terms)));
    }

    public static List<PreferenceSearchIndex> index(
            ToolsPreferenceFragment.ToolPreference pref) {
        final AtakPreferenceFragment pFrag = pref.getFrag();
        final int xmlResourceId = pFrag.getResourceID();

        final Context context;
        if (pFrag instanceof PluginPreferenceFragment) {
            context = ((PluginPreferenceFragment) pref.getFrag()).pluginContext;
        } else {
            // TODO - look at making this better
            context = appContext;
        }

        if (context == null || xmlResourceId <= 0) {
            Log.w(TAG, "tool pref not supported: " + pref.getTitle());
            return null;
        }

        List<PreferenceSearchIndex> retval = new ArrayList<>();

        String parentSummary = (pFrag.summaryID == PluginPreferenceFragment.DEFAULT_PREF_SUMMARY_ID)
                ? appContext.getString(pFrag.summaryID)
                : context.getString(pFrag.summaryID);
        PreferenceSearchIndex parent = new PreferenceSearchIndex(
                pFrag.getClass(),
                pref.getKey(),
                pref.getKey(),
                pref.getTitle(),
                parentSummary,
                pref.getIcon(),
                Collections.singletonList(pref.getTitle()));
        retval.add(parent);

        index(retval, pref.getKey(),
                inflatePreferenceScreenFromResource(context, xmlResourceId),
                pFrag.getClass(), parentSummary);
        return retval;
    }

    private static void index(List<PreferenceSearchIndex> psiList,
            String parentKey, Preference p,
            Class<? extends AtakPreferenceFragment> clazz,
            String parentSummary) {

        if (p == null) {
            Log.d(TAG, "error occurred indexing a sub preference for: " + clazz
                    + " key: " + parentKey);
            return;
        }

        final String key = p.getKey();

        String t = (p.getTitle() == null ? null : p.getTitle().toString());
        String s = (p.getSummary() == null ? null : p.getSummary().toString());

        PreferenceSearchIndex psi = new PreferenceSearchIndex(clazz, parentKey,
                key, t, parentSummary, p.getIcon(),
                Arrays.asList(t, s));
        psiList.add(psi);

        if (p instanceof PreferenceGroup) {
            PreferenceGroup c = (PreferenceGroup) p;

            for (int j = 0; j < c.getPreferenceCount(); j++) {
                Preference cur = c.getPreference(j);
                if (cur == null) {
                    Log.w(TAG, "index pref invalid: " + j);
                    continue;
                }
                index(psiList, parentKey, cur, clazz, parentSummary);
            }

        }
    }
}
