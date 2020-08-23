
package com.atakmap.math;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class RectangleTests {
    @Test
    public void point_intersects_box_valid_intersection() {
        final double aX1 = -78.79421174316589;
        final double aY1 = 35.76361300654775;
        final double aX2 = -78.77226063807349;
        final double aY2 = 35.77466250764105;
        final double bX1 = -78.78946;
        final double bY1 = 35.77202;
        final double bX2 = -78.78946;
        final double bY2 = 35.77202;

        assertTrue(
                Rectangle.intersects(aX1, aY1, aX2, aY2, bX1, bY1, bX2, bY2));
    }

    @Test
    public void box_intersects_point_valid_intersection() {
        final double aX1 = -78.79421174316589;
        final double aY1 = 35.76361300654775;
        final double aX2 = -78.77226063807349;
        final double aY2 = 35.77466250764105;
        final double bX1 = -78.78946;
        final double bY1 = 35.77202;
        final double bX2 = -78.78946;
        final double bY2 = 35.77202;

        assertTrue(
                Rectangle.intersects(bX1, bY1, bX2, bY2, aX1, aY1, aX2, aY2));
    }
}
