
package com.atakmap.coremap.conversions;

import android.opengl.GLES30;

import com.atakmap.opengl.Skirt;

import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

public class AngleUtilitiesTest {

    @Test
    public void testAngleConversion() {
        assertEquals("45 degrees to radians",
                AngleUtilities.convert(45, Angle.DEGREE, Angle.RADIAN),
                0.7853981633974483, 0.00000000001);
        assertEquals("formatting 45 degrees", AngleUtilities.format(45), "45°");
        assertEquals("formatting 45 degrees", AngleUtilities.format(45, 2),
                "45.00 °");

    }
}
