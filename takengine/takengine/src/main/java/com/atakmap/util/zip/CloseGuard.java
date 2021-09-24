/*
 * Internally implemented support class fulfilling contract for imported Android sources.
 */

package com.atakmap.util.zip;

final class CloseGuard {
    public static CloseGuard get() {
        return new CloseGuard();
    }

    public void open(String close) {
    }

    public void warnIfOpen() {
    }

    public void close() {
    }
}
