
package com.atakmap.android.util;

/**
 * Reuse notification IDs
 */
public class NotificationIdRecycler {

    private int curId;
    private final int baseId;
    private final int maxNotifications;

    public NotificationIdRecycler(final int baseId, final int max) {
        this.baseId = baseId;
        this.curId = baseId;
        this.maxNotifications = max;
    }

    /**
     * Reuse from just a few IDs to avoid too many notifications
     * 
     * @return a new motification id based on the base and max.
     */
    synchronized public int getNotificationId() {
        curId++;
        if (curId >= baseId + maxNotifications)
            curId = baseId;

        return curId;
    }
}
