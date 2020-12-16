
package com.atakmap.spatial;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.geometry.Point;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * In theory, this shouldn't have to be an instrumented test.
 * However, the calculator is tied to the Android system through
 * the use of the `Environment` class for setting the default
 * temporary directory.
 */
@RunWith(AndroidJUnit4.class)
public class SpatialCalculatorTest extends ATAKInstrumentedTest {

    private static SpatialCalculator calculator;

    @BeforeClass
    public static void initializeCalculator() {
        calculator = new SpatialCalculator.Builder()
                .includePointZDimension()
                .inMemory()
                .build();
    }

    @Test
    public void testConstructionSuccessful() {
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        new Runnable() {
                            @Override
                            public void run() {
                                assertFalse(calculator.isDisposed());
                            }
                        });
    }

    @Test
    public void testIsDisposed() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                SpatialCalculator local = new SpatialCalculator.Builder()
                        .inMemory().build();
                assertFalse(local.isDisposed());
                local.dispose();
                assertTrue(local.isDisposed());
            }
        };

        post(runnable);
    }

    /**
     * Test the point Z dimension passes through the calculator
     */
    @Test
    public void testPointZDimension() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Point expected = new Point(0.0, 0.0, 100.0);
                long handle = calculator.createPoint(expected);
                Point actual = (Point) calculator.getGeometry(handle);
                assertNotNull(actual);
                assertEquals(expected.getZ(), actual.getZ(), 0.0);
            }
        };

        post(runnable);
    }

    @Test
    public void testGeoPointZDimension() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                GeoPoint expected = new GeoPoint(0.0, 0.0, 100.0);
                long handle = calculator.createPoint(expected);
                Point actual = (Point) calculator.getGeometry(handle);
                assertNotNull(actual);
                assertEquals(expected.getAltitude(), actual.getZ(), 0.01);
            }
        };

        post(runnable);
    }

    @Test
    public void testGeoPointWithInvalidAltitudeGetsClamped() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                GeoPoint geoPoint = new GeoPoint(0.0, 0.0, Double.NaN);
                long handle = calculator.createPoint(geoPoint);
                Point actual = (Point) calculator.getGeometry(handle);
                assertNotNull(actual);
                assertEquals(0.0d, actual.getZ(), 0.0);
            }
        };

        post(runnable);
    }

    @Test
    public void testPointZDimensionWith3DDisabled() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                SpatialCalculator local = new SpatialCalculator.Builder()
                        .inMemory().build();
                Point pt = new Point(0.0, 0.0, 100.0);
                long handle = local.createPoint(pt);
                Point actual = (Point) local.getGeometry(handle);
                assertNotNull(actual);
                assertEquals(0.0, actual.getZ(), 0.0);
                local.dispose();
                assertTrue(local.isDisposed());
            }
        };

        post(runnable);
    }

    @AfterClass
    public static void disposeCalculator() {
        calculator.dispose();
    }

    private void post(Runnable runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable);
    }
}
