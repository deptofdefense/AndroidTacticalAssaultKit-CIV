
package com.atakmap.coremap.conversions;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class AngleTest extends ATAKInstrumentedTest {

    @org.junit.Test
    public void getValue() {
        assertEquals(0, Angle.DEGREE.getValue());
    }

    @org.junit.Test
    public void getAbbrev() {
    }

    @org.junit.Test
    public void getName() {
        assertEquals("Degrees", Angle.DEGREE.getName());
    }

    @org.junit.Test
    public void findFromValue() {
    }

    @org.junit.Test
    public void findFromAbbrev() {
    }

    @org.junit.Test
    public void angleConversion() {
        assert (Double.compare(AngleUtilities.convert(
                AngleUtilities.convert(45, Angle.DEGREE, Angle.MIL),
                Angle.MIL, Angle.DEGREE), 45d) == 0);
        assert (Double.compare(AngleUtilities.convert(
                AngleUtilities.convert(90, Angle.DEGREE, Angle.MIL),
                Angle.MIL, Angle.DEGREE), 90d) == 0);

        assert (Double.compare(AngleUtilities.convert(
                AngleUtilities.convert(90, Angle.MIL, Angle.DEGREE),
                Angle.DEGREE, Angle.MIL), 90d) == 0);

        assert (Double.compare(AngleUtilities.convert(
                AngleUtilities.convert(90, Angle.MIL, Angle.RADIAN),
                Angle.RADIAN, Angle.MIL), 90d) == 0);
    }

}
