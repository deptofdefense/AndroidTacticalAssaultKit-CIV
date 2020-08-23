
package com.atakmap.android.math;

import org.junit.Test;

import static org.junit.Assert.*;

public class MathUtilsTest {

    @Test
    public void getLengthStringTest() {
        assertEquals("lengthStringBytes", "500 B",
                MathUtils.GetLengthString(500L));
        assertEquals("lengthStringKiloBytes", "2.0 KB",
                MathUtils.GetLengthString(2048L));
        assertEquals("lengthStringMegaBytes", "2.0 MB",
                MathUtils.GetLengthString(2048L * 1024));
        assertEquals("lengthStringGigaBytes", "2.0 GB",
                MathUtils.GetLengthString(2048L * 1024 * 1024));
    }

    @Test
    public void getTimeRemainingString() {
        assertEquals("timeRemaining", ">1 day",
                MathUtils.GetTimeRemainingString(7 * 24 * 3600 * 1000));
        assertEquals("timeRemaining", "0s",
                MathUtils.GetTimeRemainingString(0));
        assertEquals("timeRemaining", "60m 0s",
                MathUtils.GetTimeRemainingString(3600 * 1000));
    }

    @Test
    public void getDownloadSpeedString() {
        assertEquals("downloadSpeedKiloBytes", "500.0 KB/s",
                MathUtils.GetDownloadSpeedString(500d));
        assertEquals("downloadSpeedKiloBytes", "2.0 MB/s",
                MathUtils.GetDownloadSpeedString(2048d));
        assertEquals("downloadSpeedMegaBytes", "2.0 GB/s",
                MathUtils.GetDownloadSpeedString(2048d * 1024));

    }
}
