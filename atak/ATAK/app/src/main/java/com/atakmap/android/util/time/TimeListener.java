
package com.atakmap.android.util.time;

import com.atakmap.coremap.maps.time.CoordinatedTime;

/**
 * General purpose listener for when a time has changed
 * or when a moment has elapsed
 */
public interface TimeListener {
    void onTimeChanged(CoordinatedTime oldTime, CoordinatedTime newTime);
}
