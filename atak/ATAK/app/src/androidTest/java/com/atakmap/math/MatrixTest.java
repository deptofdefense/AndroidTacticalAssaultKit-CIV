
package com.atakmap.math;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.Assert;
import org.junit.Test;

public class MatrixTest extends ATAKInstrumentedTest {
    @Test
    public void rotate_axis() {
        Matrix mx = Matrix.getIdentity();

        // rotate 45deg clockwise about X axis
        {
            mx.setToIdentity();
            mx.rotate(Math.toRadians(-45d), 1d, 0d, 0d);

            PointD result = mx.transform(new PointD(0d, 1d, 0d), null);
            Assert.assertEquals(0d, result.x, 0.00001);
            Assert.assertEquals(0.70711d, result.y, 0.00001);
            Assert.assertEquals(-0.70711d, result.z, 0.00001);
        }

        // rotate 45deg clockwise about Y axis
        {
            mx.setToIdentity();
            mx.rotate(Math.toRadians(-45d), 0d, 1d, 0d);

            PointD result = mx.transform(new PointD(0d, 0d, 1d), null);
            Assert.assertEquals(-0.70711d, result.x, 0.00001);
            Assert.assertEquals(0d, result.y, 0.00001);
            Assert.assertEquals(0.70711d, result.z, 0.00001);
        }

        // rotate 45deg clockwise about Z axis
        {
            mx.setToIdentity();
            mx.rotate(Math.toRadians(-45d), 0d, 0d, 1d);

            PointD result = mx.transform(new PointD(0d, 1d, 0d), null);
            Assert.assertEquals(0.70711d, result.x, 0.00001);
            Assert.assertEquals(0.70711d, result.y, 0.00001);
            Assert.assertEquals(0d, result.z, 0.00001);
        }
    }
}
