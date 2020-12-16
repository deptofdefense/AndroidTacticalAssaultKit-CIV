
package com.atakmap.map.layer.raster.drg;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class DRGInfoTest extends ATAKInstrumentedTest {
    @Test(expected = RuntimeException.class)
    public void null_filename_throws() {
        DRGInfo.parse(null);
        fail();
    }

    @Test
    public void empty_filename_returns_null() {
        DRGInfo info = DRGInfo.parse(new File(""));
        assertNull(info);
    }

    @Test
    public void invalid_short_length_filename_returns_null() {
        //O37091H7.TIF
        DRGInfo info = DRGInfo.parse(new File("37091H7.TIF"));
        assertNull(info);
    }

    @Test
    public void invalid_long_length_filename_returns_null() {
        //O37091H7.TIF
        DRGInfo info = DRGInfo.parse(new File("O037091H7.TIF"));
        assertNull(info);
    }

    @Test
    public void invalid_bad_category_filename_returns_null() {
        DRGInfo info = DRGInfo.parse(new File("N37091H7.TIF"));
        assertNull(info);
    }

    @Test
    public void invalid_bad_degrees_lat_invalid_char_filename_returns_null() {
        DRGInfo info = DRGInfo.parse(new File("OA7091H7.TIF"));
        assertNull(info);
    }

    @Test
    public void invalid_bad_degrees_lat_invalid_deg_filename_returns_null() {
        DRGInfo info = DRGInfo.parse(new File("O91091H7.TIF"));
        assertNull(info);
    }

    @Test
    public void invalid_bad_degrees_lng_invalid_char_filename_returns_null() {
        DRGInfo info = DRGInfo.parse(new File("O370G1H7.TIF"));
        assertNull(info);
    }

    @Test
    public void invalid_bad_degrees_lng_invalid_deg_filename_returns_null() {
        DRGInfo info = DRGInfo.parse(new File("O37591H7.TIF"));
        assertNull(info);
    }

    @Test
    public void invalid_bad_map_index_row_filename_returns_null() {
        DRGInfo info = DRGInfo.parse(new File("O37091I7.TIF"));
        assertNull(info);
    }

    @Test
    public void invalid_bad_map_index_column_filename_returns_null() {
        DRGInfo info = DRGInfo.parse(new File("O37091H0.TIF"));
        assertNull(info);
    }

    @Test
    public void invalid_extension_filename_returns_null() {
        DRGInfo info = DRGInfo.parse(new File("f36089e1.tfw"));
        assertNull(info);
    }

    @Test
    public void invalid_no_extension_filename_returns_null() {
        DRGInfo info = DRGInfo.parse(new File("f36089e1"));
        assertNull(info);
    }

    @Test
    public void valid_lower_case_filename_returns_result() {
        DRGInfo info = DRGInfo.parse(new File("f36089e1.tif"));
        assertNotNull(info);

        assertEquals(37, info.maxLat, 0.000001);
        assertEquals(-90, info.minLng, 0.000001);
        assertEquals(36.5, info.minLat, 0.000001);
        assertEquals(-89, info.maxLng, 0.000001);
    }

    @Test
    public void valid_upper_case_filename_returns_result() {
        DRGInfo info = DRGInfo.parse(new File("F36089E1.TIF"));
        assertNotNull(info);

        assertEquals(37, info.maxLat, 0.000001);
        assertEquals(-90, info.minLng, 0.000001);
        assertEquals(36.5, info.minLat, 0.000001);
        assertEquals(-89, info.maxLng, 0.000001);
    }
}
