
package com.atakmap.android.util;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import com.atakmap.coremap.log.Log;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

class NotificationFader implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = "NotificationFader";

    private final TimerTask timerTask;
    private final Timer timer = new Timer();
    private final Map<Integer, Long> idMap = new HashMap<>();

    private final static long DEFAULT_FADE = 90;

    private long fadeTimeout = DEFAULT_FADE * 1000;

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences prefs, String key) {
        if (key.equals("fade_notification")) {
            String s = prefs.getString(key, "" + DEFAULT_FADE);
            try {
                fadeTimeout = Long.parseLong(s) * 1000;
            } catch (Exception e) {
                fadeTimeout = DEFAULT_FADE * 1000;
            }
            Log.d(TAG,
                    "changing the fade timeout for notifications (cotservice) to "
                            + fadeTimeout);
        }
    }

    NotificationFader(final NotificationManager nm, final Context ctx) {

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(ctx);

        prefs.registerOnSharedPreferenceChangeListener(this);
        this.onSharedPreferenceChanged(prefs,
                "fade_notification");

        timerTask = new TimerTask() {
            @Override
            public void run() {
                synchronized (idMap) {
                    Iterator it = idMap.keySet().iterator();
                    while (it.hasNext()) {
                        Integer id = (Integer) it.next();
                        long curr = SystemClock.elapsedRealtime();
                        if (idMap.get(id) + fadeTimeout < curr) {
                            nm.cancel(id);
                            it.remove();
                        }

                    }
                }
            }
        };

        timer.schedule(timerTask, 1000, 1000);

    }

    void notifyFader(int id) {
        synchronized (idMap) {
            idMap.put(id, SystemClock.elapsedRealtime());
        }
    }

    void removeFader(int id) {
        synchronized (idMap) {
            idMap.remove(id);
        }

    }

    public void dispose() {
        if (timerTask != null)
            timerTask.cancel();
    }

}
