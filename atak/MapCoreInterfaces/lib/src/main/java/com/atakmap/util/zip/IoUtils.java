/*
 * Internally implemented support class fulfilling contract for imported Android sources.
 */

package com.atakmap.util.zip;

import android.util.Log;

import java.io.Closeable;
import java.io.IOException;

public final class IoUtils {
    public static void close(Closeable ref, String tag, String msg) {
        try {
            if (ref != null)
                ref.close();
        } catch (IOException e) {
            if(tag != null) {
                if(msg != null) {
                    Log.w(tag, msg, e);
                } else {
                    Log.e(tag, "close: ", e);
                }
            }
        }
    }

    public static void close(Closeable ref) {
        close(ref, null);
    }

    public static void close(Closeable ref, String tag) {
        close(ref, tag, null);
    }
}
