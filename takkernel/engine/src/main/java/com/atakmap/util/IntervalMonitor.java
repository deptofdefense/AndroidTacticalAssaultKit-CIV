package com.atakmap.util;

/**
 * Utility class for interval based events.
 */
public final class IntervalMonitor {
    long lastUpdate;

    /**
     * Determines if the specified interval is exceeded since the call to
     * {@link #check(long, long)} that returned <code>true</code>.
     *
     * @param interval  The event interval, in tick units
     * @param tick      The current tick
     * @return  <code>true</code> if the interval has passed since the last
     *          call to {@link #check(long, long)} that returned
     *          <code>true</code>, <code>false</code> otherwise.
     */
    public boolean check(long interval, long tick) {
        if(interval <= 0 || (tick-lastUpdate) < interval)
            return false;
        lastUpdate = tick;
        return true;
    }
}
