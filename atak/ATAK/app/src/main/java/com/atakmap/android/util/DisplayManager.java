
package com.atakmap.android.util;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.WindowManager;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;

import java.util.HashSet;
import java.util.Set;

public class DisplayManager {

    private final static Set<String> tempScreenLockHolders = new HashSet<>();

    private final static String TAG = "DisplayManager";

    /**
     * Acquires a temporary screen lock.   To be used when a tool would like to turn off
     * the screen lock and keyguard for a period of time.
     * @param mapView the mapView to be used.
     * @param name the name associated with the request
     */
    static public void acquireTemporaryScreenLock(MapView mapView,
            String name) {

        Log.d(TAG, "acquire the temporary screen lock for: " + name);
        final Activity activity = ((Activity) mapView.getContext());
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tempScreenLockHolders.add(name);
                activity.getWindow()
                        .addFlags(
                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                final KeyguardManager km = (KeyguardManager) activity
                        .getSystemService(Context.KEYGUARD_SERVICE);
                if (km != null) {
                    KeyguardManager.KeyguardLock kl = km
                            .newKeyguardLock("atakKeyGuard");
                    kl.reenableKeyguard();
                }
            }
        });

    }

    /**
     * Release a held screen lock.
     * @param mapView the mapView to be used.
     * @param name the name associated with the request
     */
    static public void releaseTemporaryScreenLock(MapView mapView,
            String name) {

        final Activity activity = ((Activity) mapView.getContext());

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tempScreenLockHolders.remove(name);

                Log.d(TAG, "release the temporary screen lock for: " + name);
                if (tempScreenLockHolders.isEmpty()) {

                    final SharedPreferences pref = PreferenceManager
                            .getDefaultSharedPreferences(activity);

                    if (!pref.getBoolean("atakScreenLock", false))
                        activity.getWindow().clearFlags(
                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                    if (!pref.getBoolean("atakDisableKeyguard", false)) {
                        final KeyguardManager km = (KeyguardManager) activity
                                .getSystemService(Context.KEYGUARD_SERVICE);
                        if (km != null) {
                            KeyguardManager.KeyguardLock kl = km
                                    .newKeyguardLock("atakKeyGuard");
                            kl.disableKeyguard();
                        }
                    }
                }
            }
        });
    }

}
