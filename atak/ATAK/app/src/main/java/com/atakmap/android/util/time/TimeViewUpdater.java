
package com.atakmap.android.util.time;

import android.os.SystemClock;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Task which is meant for periodically updating time-based views
 * (i.e. TextView that contains a relative timestamp)
 * Not meant for precise timing events
 */
public class TimeViewUpdater implements Runnable {

    private static final String TAG = "TimeViewUpdater";

    private final MapView _mapView;
    private final long _interval;
    private final Set<TimeListener> _listeners = new HashSet<>();

    private CoordinatedTime _oldTime;
    private long _target;
    private boolean _running;

    public TimeViewUpdater(MapView mapView, long interval) {
        _mapView = mapView;
        _interval = interval;
    }

    /**
     * Register a listener to the updater
     * If the delay loop isn't running then it will begin once this
     * listener is added
     *
     * @param listener Time listener
     */
    public synchronized void register(TimeListener listener) {
        if (_listeners.add(listener) && !_running) {
            _running = true;
            _oldTime = new CoordinatedTime();
            _target = SystemClock.elapsedRealtime() + _interval;
            _mapView.postDelayed(this, _interval);
        }
    }

    /**
     * Unregister a listener from the updater
     *
     * @param listener Time listener
     */
    public synchronized void unregister(TimeListener listener) {
        _listeners.remove(listener);
    }

    /**
     * Time interval has elapsed
     */
    @Override
    public void run() {
        List<TimeListener> users;
        long target;
        synchronized (this) {
            if (_listeners.isEmpty()) {
                // No more listeners - end the delay loop
                _running = false;
                return;
            }
            users = new ArrayList<>(_listeners);
            _target += _interval;
            target = _target;
        }

        // Fire time changed listeners
        CoordinatedTime newTime = new CoordinatedTime();
        for (TimeListener u : users)
            u.onTimeChanged(_oldTime, newTime);
        _oldTime = newTime;

        // Continue the delay loop while minimizing time drift between updates
        long delay = target - SystemClock.elapsedRealtime();
        if (delay > 0)
            _mapView.postDelayed(this, delay);
        else
            _mapView.post(this);
    }
}
