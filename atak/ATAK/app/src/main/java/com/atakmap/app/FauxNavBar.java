
package com.atakmap.app;

import android.os.Build;
import android.view.KeyEvent;
import android.widget.ImageButton;
import android.content.ComponentName;
import android.os.IBinder;
import android.content.DialogInterface;
import android.annotation.SuppressLint;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;

import android.view.View;
import android.app.Activity;

import java.lang.reflect.Method;
import com.atakmap.coremap.log.Log;

import android.app.AlertDialog;

import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.view.ViewGroup;

import android.view.LayoutInflater;

public class FauxNavBar implements OnSharedPreferenceChangeListener {
    private final Activity activity;
    private final SharedPreferences prefs;

    public static final String TAG = "FauxNavBar";

    /**
     * Given an activity and a shared preference, create a fake navigation
     * bar if the preference is enabled.
     */
    public FauxNavBar(final Activity a) {
        this.activity = a;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        prefs.registerOnSharedPreferenceChangeListener(this);
        setupFauxKeys();
    }

    @Override
    public void onSharedPreferenceChanged(
            final SharedPreferences prefs, final String key) {

        if (key == null)
            return;

        if (key.equals("atakControlForcePortrait")
                || key.equals("faux_nav_bar")
                || key.equals("faux_nav_bar_reverse")) {
            if (prefs.getBoolean("faux_nav_bar", false)) {
                show();
            } else {
                hide();
            }
        }
    }

    /**
     * Attempt to simulate the current disapearing nav bar, so that the user experience within
     * ATAK does not suffer.   When the action bar is set to not hide, the colors are too bright 
     * for night usage.
     */
    void setupFauxKeys() {

        // turn this off for testing
        if (false && !prefs.contains("faux_nav_bar")) {
            final String model = android.os.Build.MODEL;
            if (model.contains("SM-G892") ||
                    model.startsWith("SM-G950") ||
                    model.startsWith("SM-G955") ||
                    model.startsWith("SM-N960") ||
                    model.startsWith("SM-N965") ||
                    model.startsWith("SM-N970") ||
                    model.startsWith("SM-N975")) {
                final View v = LayoutInflater.from(activity)
                        .inflate(R.layout.faux_nav_bar, null);

                new AlertDialog.Builder(activity)
                        .setTitle("Faux Navigation Bar")
                        .setCancelable(false)
                        .setView(v)
                        .setNegativeButton(R.string.no,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        prefs.edit()
                                                .putBoolean("faux_nav_bar",
                                                        false)
                                                .apply();
                                    }
                                })
                        .setPositiveButton(R.string.yes,
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                            int which) {
                                        prefs.edit()
                                                .putBoolean("faux_nav_bar",
                                                        true)
                                                .apply();
                                    }
                                })
                        .show();

            }

        } else if (prefs.getBoolean("faux_nav_bar", false)) {
            show();
        } else {
            hide();
        }

    }

    /**
     * Show the current fake actionbar.
     */
    private void show() {
        final LinearLayout port = activity
                .findViewById(R.id.faux_button_portrait);
        final LinearLayout land = activity
                .findViewById(R.id.faux_button_landscape);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setup();
                boolean portrait = prefs.getBoolean(
                        "atakControlForcePortrait", false);
                setVisible(port, portrait);
                setVisible(land, !portrait);
            }
        });
    }

    /**
     * Hide the fake action bar.
     */
    private void hide() {
        final LinearLayout port = activity
                .findViewById(R.id.faux_button_portrait);
        final LinearLayout land = activity
                .findViewById(R.id.faux_button_landscape);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setVisible(port, false);
                setVisible(land, false);
            }
        });
    }

    private void setup() {
        // some devices like the Pixel actually use a sliding nav bar that 
        // has the back button at the bottom.

        if (!prefs.getBoolean("faux_nav_bar_reverse", false)) {
            setup(R.id.btn1_portrait, R.id.btn1_landscape, backAction,
                    backLongAction, R.drawable.sh_back);
            setup(R.id.btn2_portrait, R.id.btn2_landscape, homeAction, null,
                    R.drawable.sh_home);
            setup(R.id.btn3_portrait, R.id.btn3_landscape, recentAction, null,
                    R.drawable.sh_recent);
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.findViewById(R.id.btn3_portrait)
                        .setVisibility(View.INVISIBLE);
                activity.findViewById(R.id.btn3_landscape)
                        .setVisibility(View.INVISIBLE);
            }

        } else {
            setup(R.id.btn3_portrait, R.id.btn3_landscape, backAction,
                    backLongAction, R.drawable.sh_back);
            setup(R.id.btn2_portrait, R.id.btn2_landscape, homeAction, null,
                    R.drawable.sh_home);
            setup(R.id.btn1_portrait, R.id.btn1_landscape, recentAction, null,
                    R.drawable.sh_recent);
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.findViewById(R.id.btn1_portrait)
                        .setVisibility(View.INVISIBLE);
                activity.findViewById(R.id.btn1_landscape)
                        .setVisibility(View.INVISIBLE);
            }
        }

    }

    private void setup(final int port,
            final int land,
            final View.OnClickListener ocl,
            final View.OnLongClickListener olcl, final int icon) {
        for (int v : new int[] {
                port, land
        }) {
            final ImageButton b = activity.findViewById(v);
            b.setOnClickListener(ocl);
            b.setOnLongClickListener(olcl);
            b.setImageResource(icon);
        }
    }

    private final View.OnClickListener backAction = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_BACK));
            activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_BACK));
        }
    };
    private final View.OnLongClickListener backLongAction = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            activity.openOptionsMenu();
            return true;
        }
    };

    private final View.OnClickListener homeAction = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                final Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_HOME);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                i.setComponent(new ComponentName("com.sec.android.app.launcher",
                        "com.sec.android.app.launcher.activities.LauncherActivity"));
                activity.startActivity(i);
            } catch (Exception ignored) {
            }
        }
    };

    /**
     * Implementation of the recent action key. Makes use of the intent 
     * com.android.systemui.recent.action.TOGGLE_RECENTS in order to start the recent activity 
     * cards.
     */
    @SuppressLint("PrivateApi")
    private final View.OnClickListener recentAction = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                Intent intent = new Intent(
                        "com.android.systemui.recents.TOGGLE_RECENTS");
                intent.setComponent(new ComponentName(
                        "com.android.systemui",
                        "com.android.systemui.recents.RecentsActivity"));
                activity.startActivity(intent);
                return;
            } catch (Exception ignored) {
                try {
                    Intent intent = new Intent(
                            "com.android.systemui.recent.action.TOGGLE_RECENTS");
                    intent.setComponent(new ComponentName(
                            "com.android.systemui",
                            "com.android.systemui.recent.RecentsActivity"));
                    activity.startActivity(intent);
                    return;
                } catch (Exception ignored1) {
                }
            }

            try {
                Class serviceManagerClass = Class
                        .forName("android.os.ServiceManager");
                Method getService = serviceManagerClass.getMethod(
                        "getService", String.class);
                IBinder retbinder = (IBinder) getService.invoke(
                        serviceManagerClass, "statusbar");
                Class statusBarClass = Class.forName(retbinder
                        .getInterfaceDescriptor());
                Object statusBarObject = statusBarClass.getClasses()[0]
                        .getMethod("asInterface", IBinder.class).invoke(
                                null, retbinder);
                Method clearAll = statusBarClass
                        .getMethod("toggleRecentApps");
                clearAll.setAccessible(true);
                clearAll.invoke(statusBarObject);
            } catch (Exception e) {
                Log.d(TAG, "error showing the recently used", e);
            }
        }
    };

    /**
     * Constructs a FauxNavBar for any number of our SettingActivity's within the TAK system.
     * The purpose of this is to allow for quick construction of a faux nav bar without the headache
     * traditionally associated with its construction.
     * @param activity is the SettingsActivity to be used during the construction.
     * @return if the return is a FauxNavBar, the value com.atakmap.app.R.id.visual_content
     */
    public static FauxNavBar constructFauxNavBar(final Activity activity) {
        try {
            View vc = activity.findViewById(R.id.visual_content);
            if (vc != null) {
                Log.d(TAG,
                        "faux nav bar already initialized for this activty, skipping");
                return null;
            }

            View v = activity.findViewById(android.R.id.content);
            if (v instanceof FrameLayout) {
                View atak_frag_pref = LayoutInflater.from(activity)
                        .inflate(R.layout.atak_frag_container, null);
                View bl = atak_frag_pref.findViewById(R.id.btn1_landscape);
                ViewGroup.LayoutParams p = bl.getLayoutParams();
                if (p instanceof LinearLayout.LayoutParams) {
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) p;
                    lp.setMargins(0, -45, 0, 0);
                    bl.setLayoutParams(lp);
                }

                ((FrameLayout) v).addView(atak_frag_pref);
                return new FauxNavBar(activity);
            }
        } catch (Exception e) {
            Log.d(TAG, "error adding in the faux nav bar", e);
        }
        return null;
    }

    /**
     * Set visibility by manipulating width/height rather than using
     * {@link View#setVisibility(int)}
     *
     * This is to keep the main ATAK layout from breaking since it uses a
     * {@link android.widget.RelativeLayout}, which does not like when
     * relational components have visibility set to {@link View#GONE}.
     *
     * @param v View
     * @param visible True if visible
     */
    private static void setVisible(View v, boolean visible) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        int size = visible ? ViewGroup.LayoutParams.WRAP_CONTENT : 0;
        if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT)
            lp.height = size;
        else
            lp.width = size;
        v.setLayoutParams(lp);
    }
}
