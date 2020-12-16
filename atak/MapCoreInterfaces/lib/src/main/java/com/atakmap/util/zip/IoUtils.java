/*
 * Internally implemented support class fulfilling contract for imported Android sources.
 */

package com.atakmap.util.zip;

import java.io.IOException;
import java.io.RandomAccessFile;

final class IoUtils {
    public static void closeQuietly(RandomAccessFile raf) {
        try {
            if (raf != null)
                raf.close();
        } catch (IOException ignored) {
        }
    }
}
