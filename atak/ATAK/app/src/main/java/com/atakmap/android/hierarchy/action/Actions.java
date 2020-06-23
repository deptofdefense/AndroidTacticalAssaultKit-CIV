
package com.atakmap.android.hierarchy.action;

public final class Actions {
    public final static long ACTION_VISIBILITY = 0x0000000000000001L;
    public final static long ACTION_GOTO = 0x0000000000000002L;
    public final static long ACTION_DELETE = 0x0000000000000004L;
    public final static long ACTION_EXPORT = 0x0000000000000008L;
    public final static long ACTION_SEARCH = 0x0000000000000010L;
    public final static long ACTION_ASYNCHRONOUS = 0x0100000000000008L;

    private Actions() {
    }

    public static boolean hasBits(int value, int mask) {
        return ((value & mask) == mask);
    }
}
