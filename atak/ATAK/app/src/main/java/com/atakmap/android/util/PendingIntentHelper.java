
package com.atakmap.android.util;

import android.os.Build;

public class PendingIntentHelper {

    /**
     * As of Android 31 and higher a Pending Intent needs to have FLAG_MUTABLE
     * or FLAG_IMMUTABLE.   This will adapt a legacy flag so that it is set to be
     * MUTABLE.
     * @param flags the pending intent flag
     * @return return the flags with the FLAG_MUTABLE set.
     */
    public static int adaptFlags(int flags) {

        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        //    flags|= PendingIntent.FLAG_MUTABLE;

        // XXX Use this until the compileSdk is bumped to 31 
        if (Build.VERSION.SDK_INT >= 32) {
            flags |= 33554432;
        }
        return flags;
    }

}
