
package com.atakmap.map.layer.raster.pfps;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.coremap.maps.coords.GeoPoint;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PfpsMapTypeFrameTest extends ATAKInstrumentedTest {
    @Test
    public void PfpsMapTypeFrame_getPrettyName_type_codes_valid() {
        String value = PfpsMapTypeFrame.getRpfPrettyName("JN");
        assertNotNull(value);
        assertEquals("JNC", value);
    }

    @Test
    public void PfpsMapTypeFrame_getPrettyName_type_codes_lowercase_invalid() {
        String value = PfpsMapTypeFrame.getRpfPrettyName("jn");
        assertNull(value);
    }

    @Test
    public void PfpsMapTypeFrame_getPrettyName_type_codes_invalid_invalid() {
        String value = PfpsMapTypeFrame.getRpfPrettyName("BB");
        assertNull(value);
    }

    @Test
    public void PfpsMapTypeFrame_getPrettyName_type_codes_null_invalid() {
        String value = PfpsMapTypeFrame.getRpfPrettyName((String) null);
        assertNull(value);
    }

    @Test
    public void PfpsMapTypeFrame_getPrettyName_valid_file() {
        String value;
        value = PfpsMapTypeFrame
                .getRpfPrettyName(new File("/dev/null/00000003qj0013.ja2"));
        assertNotNull(value);
        assertEquals("JOG-A", value);

        value = PfpsMapTypeFrame.getRpfPrettyName(new File("001hek16.i12"));
        assertNotNull(value);
        assertEquals("CIB 10m", value);
    }

    @Test
    public void PfpsMapTypeFrame_getPrettyName_file_bad_name_length_returns_null() {
        String value = PfpsMapTypeFrame
                .getRpfPrettyName(new File("/dev/null/3qj0013.ja2"));
        assertNull(value);
    }

    @Test
    public void PfpsMapTypeFrame_coverage_from_file_name_valid_rpf_name() {
        File f = new File("/dev/null/003qj1y3.ja2");
        GeoPoint ul = GeoPoint.createMutable();
        GeoPoint ur = GeoPoint.createMutable();
        GeoPoint lr = GeoPoint.createMutable();
        GeoPoint ll = GeoPoint.createMutable();
        final boolean value = PfpsMapTypeFrame.coverageFromFilename(f, ul, ur,
                lr, ll);
        assertTrue(value);

        assertEquals(36.2416107383, ul.getLatitude(), 0.0000001);
        assertEquals(-115.5837563452, ul.getLongitude(), 0.0000001);

        assertEquals(36.2416107383, ur.getLatitude(), 0.0000001);
        assertEquals(-114.8984771574, ur.getLongitude(), 0.0000001);

        assertEquals(35.7238734420, lr.getLatitude(), 0.0000001);
        assertEquals(-114.8984771574, lr.getLongitude(), 0.0000001);

        assertEquals(35.7238734420, ll.getLatitude(), 0.0000001);
        assertEquals(-115.5837563452, ll.getLongitude(), 0.0000001);
    }

    @Test
    public void PfpsMapTypeFrame_coverage_from_file_name_valid_ecrg_name() {
        File f = new File("/dev/null/00000003qj0013.ja2");
        GeoPoint ul = GeoPoint.createMutable();
        GeoPoint ur = GeoPoint.createMutable();
        GeoPoint lr = GeoPoint.createMutable();
        GeoPoint ll = GeoPoint.createMutable();
        final boolean value = PfpsMapTypeFrame.coverageFromFilename(f, ul, ur,
                lr, ll);
        assertTrue(value);

        assertEquals(36.2416107383, ul.getLatitude(), 0.0000001);
        assertEquals(-115.5837563452, ul.getLongitude(), 0.0000001);

        assertEquals(36.2416107383, ur.getLatitude(), 0.0000001);
        assertEquals(-114.8984771574, ur.getLongitude(), 0.0000001);

        assertEquals(35.7238734420, lr.getLatitude(), 0.0000001);
        assertEquals(-114.8984771574, lr.getLongitude(), 0.0000001);

        assertEquals(35.7238734420, ll.getLatitude(), 0.0000001);
        assertEquals(-115.5837563452, ll.getLongitude(), 0.0000001);
    }

    @Test
    public void PfpsMapTypeFrame_coverage_from_file_name_valid_cib_name() {
        File f = new File("/dev/null/001hek16.i12");
        GeoPoint ul = GeoPoint.createMutable();
        GeoPoint ur = GeoPoint.createMutable();
        GeoPoint lr = GeoPoint.createMutable();
        GeoPoint ll = GeoPoint.createMutable();
        final boolean value = PfpsMapTypeFrame.coverageFromFilename(f, ul, ur,
                lr, ll);
        assertTrue(value);

        assertEquals(36.1841432233, ul.getLatitude(), 0.0000001);
        assertEquals(-115.8578680203, ul.getLongitude(), 0.0000001);

        assertEquals(36.18414322333, ur.getLatitude(), 0.0000001);
        assertEquals(-115.6751269036, ur.getLongitude(), 0.0000001);

        assertEquals(36.0460358064, lr.getLatitude(), 0.0000001);
        assertEquals(-115.6751269036, lr.getLongitude(), 0.0000001);

        assertEquals(36.0460358064, ll.getLatitude(), 0.0000001);
        assertEquals(-115.8578680203, ll.getLongitude(), 0.0000001);
    }

    @Test
    public void PfpsMapTypeFrame_coverage_from_file_name_invalid_cib_name() {
        File f = new File("/dev/null/zzzzzz16.i12");
        GeoPoint ul = GeoPoint.createMutable();
        GeoPoint ur = GeoPoint.createMutable();
        GeoPoint lr = GeoPoint.createMutable();
        GeoPoint ll = GeoPoint.createMutable();
        final boolean value = PfpsMapTypeFrame.coverageFromFilename(f, ul, ur,
                lr, ll);
        assertFalse(value);
    }

    @Test
    public void PfpsMapTypeFrame_coverage_from_char_array_bad_length_returns_false() {
        GeoPoint ul = GeoPoint.createMutable();
        GeoPoint ur = GeoPoint.createMutable();
        GeoPoint lr = GeoPoint.createMutable();
        GeoPoint ll = GeoPoint.createMutable();
        final boolean value = PfpsMapTypeFrame.coverageFromFilename(new char[8],
                ul, ur, lr, ll);
        assertFalse(value);
    }

    @Test
    public void PfpsMapTypeFrame_coverage_from_char_array_null_returns_false() {
        GeoPoint ul = GeoPoint.createMutable();
        GeoPoint ur = GeoPoint.createMutable();
        GeoPoint lr = GeoPoint.createMutable();
        GeoPoint ll = GeoPoint.createMutable();
        final boolean value = PfpsMapTypeFrame
                .coverageFromFilename((char[]) null, ul, ur, lr, ll);
        assertFalse(value);
    }

    @Test
    public void PfpsMapTypeFrame_coverage_from_file_name_invalid_base34_cib_name() {
        File f = new File("/dev/null/12o34I16.i12");
        GeoPoint ul = GeoPoint.createMutable();
        GeoPoint ur = GeoPoint.createMutable();
        GeoPoint lr = GeoPoint.createMutable();
        GeoPoint ll = GeoPoint.createMutable();
        final boolean value = PfpsMapTypeFrame.coverageFromFilename(f, ul, ur,
                lr, ll);
        assertFalse(value);
    }

    @Test
    public void PfpsMapTypeFrame_coverage_from_file_name_null_file_invalid() {
        File f = null;
        GeoPoint ul = GeoPoint.createMutable();
        GeoPoint ur = GeoPoint.createMutable();
        GeoPoint lr = GeoPoint.createMutable();
        GeoPoint ll = GeoPoint.createMutable();
        final boolean value = PfpsMapTypeFrame.coverageFromFilename(f, ul, ur,
                lr, ll);
        assertFalse(value);
    }

    @Test
    public void PfpsMapTypeFrame_coverage_from_file_name_short_file_invalid() {
        File f = new File("/dev/null/3qj1y3.ja2"); // missing leading zeros
        GeoPoint ul = GeoPoint.createMutable();
        GeoPoint ur = GeoPoint.createMutable();
        GeoPoint lr = GeoPoint.createMutable();
        GeoPoint ll = GeoPoint.createMutable();
        final boolean value = PfpsMapTypeFrame.coverageFromFilename(f, ul, ur,
                lr, ll);
        assertFalse(value);
    }

    @Test
    public void PfpsMapTypeFrame_coverage_from_file_name_file_southern_hemi() {
        File f = new File("/dev/null/003qj1y3.jaa");
        GeoPoint ul = GeoPoint.createMutable();
        GeoPoint ur = GeoPoint.createMutable();
        GeoPoint lr = GeoPoint.createMutable();
        GeoPoint ll = GeoPoint.createMutable();
        final boolean value = PfpsMapTypeFrame.coverageFromFilename(f, ul, ur,
                lr, ll);
        assertTrue(value);

        assertEquals(-28.47555129434324, ul.getLatitude(), 0.0000001);
        assertEquals(72.40197351337318, ul.getLongitude(), 0.0000001);

        assertEquals(-28.47555129434324, ur.getLatitude(), 0.0000001);
        assertEquals(72.96286678784735, ur.getLongitude(), 0.0000001);

        assertEquals(-28.993288590604028, lr.getLatitude(), 0.0000001);
        assertEquals(72.96286678784735, lr.getLongitude(), 0.0000001);

        assertEquals(-28.993288590604028, ll.getLatitude(), 0.0000001);
        assertEquals(72.40197351337318, ll.getLongitude(), 0.0000001);

    }

    @Test
    public void PfpsMapTypeFrame_coverage_from_file_name_file_zone_j() {
        File f = new File("/dev/null/003qj1y3.jaj");
        GeoPoint ul = GeoPoint.createMutable();
        GeoPoint ur = GeoPoint.createMutable();
        GeoPoint lr = GeoPoint.createMutable();
        GeoPoint ll = GeoPoint.createMutable();
        final boolean value = PfpsMapTypeFrame.coverageFromFilename(f, ul, ur,
                lr, ll);
        assertFalse(value);
    }

    @Test
    public void PfpsMapTypeFrame_coverage_from_file_name_file_invalid_zone_2() {
        File f = new File("/dev/null/003qj1y3.jaz");
        GeoPoint ul = GeoPoint.createMutable();
        GeoPoint ur = GeoPoint.createMutable();
        GeoPoint lr = GeoPoint.createMutable();
        GeoPoint ll = GeoPoint.createMutable();
        final boolean value = PfpsMapTypeFrame.coverageFromFilename(f, ul, ur,
                lr, ll);
        assertFalse(value);
    }

    @Test
    public void PfpsMapTypeFrame_coverage_from_file_name_valid_CG_name_various_scales_returns_false() {
        File f = new File("/dev/null/001hek16.cg2");
        GeoPoint ul = GeoPoint.createMutable();
        GeoPoint ur = GeoPoint.createMutable();
        GeoPoint lr = GeoPoint.createMutable();
        GeoPoint ll = GeoPoint.createMutable();
        final boolean value = PfpsMapTypeFrame.coverageFromFilename(f, ul, ur,
                lr, ll);
        assertFalse(value);
    }

    @Test
    public void PfpsMapTypeFrame_coverage_from_file_name_valid_D1_returns_false() {
        File f = new File("/dev/null/001hek16.d12");
        GeoPoint ul = GeoPoint.createMutable();
        GeoPoint ur = GeoPoint.createMutable();
        GeoPoint lr = GeoPoint.createMutable();
        GeoPoint ll = GeoPoint.createMutable();
        final boolean value = PfpsMapTypeFrame.coverageFromFilename(f, ul, ur,
                lr, ll);
        assertFalse(value);
    }

    @Test
    public void PfpsMapTypeFrame_coverage_from_file_name_invalid_typecode_returns_false() {
        File f = new File("/dev/null/001hek16.ss2");
        GeoPoint ul = GeoPoint.createMutable();
        GeoPoint ur = GeoPoint.createMutable();
        GeoPoint lr = GeoPoint.createMutable();
        GeoPoint ll = GeoPoint.createMutable();
        final boolean value = PfpsMapTypeFrame.coverageFromFilename(f, ul, ur,
                lr, ll);
        assertFalse(value);
    }
}
