
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

        if (key == null)
            return;

        if (key.equals("fade_notification")) {
            String s = prefs.getString(key, "" + DEFAULT_FADE);
            fadeTimeout = DEFAULT_FADE * 1000;
            try {
                if (s != null)
                    fadeTimeout = Long.parseLong(s) * 1000;
            } catch (Exception ignored) {
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
                    Iterator<Integer> it = idMap.keySet().iterator();
                    while (it.hasNext()) {
                        Integer id = it.next();
                        long curr = SystemClock.elapsedRealtime();

                        Long time = idMap.get(id);
                        if (time != null && time + fadeTimeout < curr) {
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
