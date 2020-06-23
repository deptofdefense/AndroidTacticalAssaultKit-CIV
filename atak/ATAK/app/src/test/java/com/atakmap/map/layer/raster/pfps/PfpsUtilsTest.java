
package com.atakmap.map.layer.raster.pfps;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PfpsUtilsTest {
    @Test
    public void PfpsUtils_base34_decode_bad_input() {
        int val = PfpsUtils.base34Decode("12345o");
        assertTrue(val < 0);
    }

    @Test
    public void PfpsUtils_base34_decode_valid_input() {
        int val = PfpsUtils.base34Decode("X340S");
        assertEquals(val, 41548978);
    }

    @Test(expected = NullPointerException.class)
    public void PfpsUtils_base34_decode_null_input() {
        int val = PfpsUtils.base34Decode(null);
        fail();
    }
}
