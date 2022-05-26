/*
 * Internally implemented support class fulfilling contract for imported Android sources.
 */

package com.atakmap.util.zip;

final class IntegralToString {
    public static String intToHexString(int value, boolean unknown, int len) {
        return String.format("0x%08X", value);
    }
}
