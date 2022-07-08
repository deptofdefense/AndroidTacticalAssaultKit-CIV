
package com.atakmap.util;

import com.atakmap.android.util.ATAKUtilities;

import org.junit.Assert;
import org.junit.Test;

public class ATAKUtilitiesTests {
    @Test
    public void bytes_to_hex_string_valid_input() {
        final byte[] in = new byte[] {
                (byte) 0x10, (byte) 0x2a, (byte) 0x79, (byte) 0x01, (byte) 0xFF
        };
        final String expected = "102a7901ff";
        final String result = ATAKUtilities.bytesToHex(in);
        Assert.assertEquals(expected, result);
    }

    @Test
    public void hex_string_to_bytes_valid_input() {
        final String in = "102a7901ff";
        final byte[] expected = new byte[] {
                (byte) 0x10, (byte) 0x2a, (byte) 0x79, (byte) 0x01, (byte) 0xFF
        };

        final byte[] result = ATAKUtilities.hexToBytes(in);
        Assert.assertArrayEquals(expected, result);
    }

    @Test
    public void hex_string_to_bytes_case_insensitive() {
        final String inLower = "102a7901ff";
        final String inUpper = inLower.toUpperCase();

        final byte[] resultLower = ATAKUtilities.hexToBytes(inLower);
        final byte[] resultUpper = ATAKUtilities.hexToBytes(inUpper);
        Assert.assertArrayEquals(resultLower, resultUpper);
    }

    @Test(expected = java.lang.RuntimeException.class)
    public void hex_string_to_bytes_invalid_throws() {
        final String in = "X  1027901ff";

        ATAKUtilities.hexToBytes(in);
        Assert.fail();
    }
}
