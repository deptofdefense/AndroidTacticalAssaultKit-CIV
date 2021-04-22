
package com.atakmap.map.opengl;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.opengl.GLAntiMeridianHelper;

import org.junit.Test;

import java.nio.DoubleBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GLAntiMeridianHelperTest {
    @Test
    public void unwrapped_west_hemi_does_not_cross() {
        final GeoPoint a = new GeoPoint(-17, -179);
        final GeoPoint b = new GeoPoint(-17, 181);

        final DoubleBuffer src = DoubleBuffer.wrap(new double[] {
                a.getLongitude(), a.getLatitude(), b.getLongitude(),
                b.getLatitude()
        });
        final DoubleBuffer dst = DoubleBuffer.allocate(src.capacity());

        final int result = GLAntiMeridianHelper.normalizeHemisphere(2, src,
                dst);
        assertEquals(0, result & GLAntiMeridianHelper.MASK_IDL_CROSS);
        assertEquals(GLAntiMeridianHelper.HEMISPHERE_WEST,
                result & GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);
    }

    @Test
    public void unwrapped_east_hemi_does_not_cross() {
        final GeoPoint a = new GeoPoint(-17, 179);
        final GeoPoint b = new GeoPoint(-17, -181);

        final DoubleBuffer src = DoubleBuffer.wrap(new double[] {
                a.getLongitude(), a.getLatitude(), b.getLongitude(),
                b.getLatitude()
        });
        final DoubleBuffer dst = DoubleBuffer.allocate(src.capacity());

        final int result = GLAntiMeridianHelper.normalizeHemisphere(2, src,
                dst);
        assertEquals(0, result & GLAntiMeridianHelper.MASK_IDL_CROSS);
        assertEquals(GLAntiMeridianHelper.HEMISPHERE_EAST,
                result & GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);
    }

    @Test
    public void wrapped_west_hemi_does_not_cross() {
        final GeoPoint a = new GeoPoint(-17, -178);
        final GeoPoint b = new GeoPoint(-17, -179);

        final DoubleBuffer src = DoubleBuffer.wrap(new double[] {
                a.getLongitude(), a.getLatitude(), b.getLongitude(),
                b.getLatitude()
        });
        final DoubleBuffer dst = DoubleBuffer.allocate(src.capacity());

        final int result = GLAntiMeridianHelper.normalizeHemisphere(2, src,
                dst);
        assertEquals(0, result & GLAntiMeridianHelper.MASK_IDL_CROSS);
        assertEquals(GLAntiMeridianHelper.HEMISPHERE_WEST,
                result & GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);
    }

    @Test
    public void wrapped_east_hemi_does_not_cross() {
        final GeoPoint a = new GeoPoint(-17, 178);
        final GeoPoint b = new GeoPoint(-17, 179);

        final DoubleBuffer src = DoubleBuffer.wrap(new double[] {
                a.getLongitude(), a.getLatitude(), b.getLongitude(),
                b.getLatitude()
        });
        final DoubleBuffer dst = DoubleBuffer.allocate(src.capacity());

        final int result = GLAntiMeridianHelper.normalizeHemisphere(2, src,
                dst);
        assertEquals(0, result & GLAntiMeridianHelper.MASK_IDL_CROSS);
        assertEquals(GLAntiMeridianHelper.HEMISPHERE_EAST,
                result & GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);
    }

    @Test
    public void unwrapped_west_cross_to_east() {
        final GeoPoint a = new GeoPoint(-17, -179);
        final GeoPoint b = new GeoPoint(-17, -181);

        final DoubleBuffer src = DoubleBuffer.wrap(new double[] {
                a.getLongitude(), a.getLatitude(), b.getLongitude(),
                b.getLatitude()
        });
        final DoubleBuffer dst = DoubleBuffer.allocate(src.capacity());

        final int result = GLAntiMeridianHelper.normalizeHemisphere(2, src,
                dst);
        // confirm metadata
        assertTrue((result & GLAntiMeridianHelper.MASK_IDL_CROSS) != 0);
        assertEquals(GLAntiMeridianHelper.HEMISPHERE_WEST,
                result & GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);

        // confirm all longitudes shifted into west hemi
        assertTrue(dst.get(0) < 0d);
        assertTrue(dst.get(2) < 0d);
    }

    @Test
    public void unwrapped_east_cross_to_west() {
        final GeoPoint a = new GeoPoint(-17, 179);
        final GeoPoint b = new GeoPoint(-17, 181);

        final DoubleBuffer src = DoubleBuffer.wrap(new double[] {
                a.getLongitude(), a.getLatitude(), b.getLongitude(),
                b.getLatitude()
        });
        final DoubleBuffer dst = DoubleBuffer.allocate(src.capacity());

        final int result = GLAntiMeridianHelper.normalizeHemisphere(2, src,
                dst);
        assertTrue((result & GLAntiMeridianHelper.MASK_IDL_CROSS) != 0);
        assertEquals(GLAntiMeridianHelper.HEMISPHERE_EAST,
                result & GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);

        // confirm all longitudes shifted into west hemi
        assertTrue(dst.get(0) >= 0d);
        assertTrue(dst.get(2) >= 0d);
    }

    @Test
    public void wrapped_west_cross_to_east() {
        final GeoPoint a = new GeoPoint(-17, -179);
        final GeoPoint b = new GeoPoint(-17, 179);

        final DoubleBuffer src = DoubleBuffer.wrap(new double[] {
                a.getLongitude(), a.getLatitude(), b.getLongitude(),
                b.getLatitude()
        });
        final DoubleBuffer dst = DoubleBuffer.allocate(src.capacity());

        final int result = GLAntiMeridianHelper.normalizeHemisphere(2, src,
                dst);
        // confirm metadata
        assertTrue((result & GLAntiMeridianHelper.MASK_IDL_CROSS) != 0);
        assertEquals(GLAntiMeridianHelper.HEMISPHERE_WEST,
                result & GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);

        // confirm all longitudes shifted into west hemi
        assertTrue(dst.get(0) < 0d);
        assertTrue(dst.get(2) < 0d);
    }

    @Test
    public void wrapped_east_cross_to_west() {
        final GeoPoint a = new GeoPoint(-17, 179);
        final GeoPoint b = new GeoPoint(-17, -179);

        final DoubleBuffer src = DoubleBuffer.wrap(new double[] {
                a.getLongitude(), a.getLatitude(), b.getLongitude(),
                b.getLatitude()
        });
        final DoubleBuffer dst = DoubleBuffer.allocate(src.capacity());

        final int result = GLAntiMeridianHelper.normalizeHemisphere(2, src,
                dst);
        assertTrue((result & GLAntiMeridianHelper.MASK_IDL_CROSS) != 0);
        assertEquals(GLAntiMeridianHelper.HEMISPHERE_EAST,
                result & GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);

        // confirm all longitudes shifted into west hemi
        assertTrue(dst.get(0) >= 0d);
        assertTrue(dst.get(2) >= 0d);
    }

    @Test
    public void west_cross_to_east_cross_to_west() {
        final GeoPoint a = new GeoPoint(-17, -179);
        final GeoPoint b = new GeoPoint(-17, 179);
        final GeoPoint c = new GeoPoint(-116, -179);

        final DoubleBuffer src = DoubleBuffer.wrap(new double[] {
                a.getLongitude(), a.getLatitude(), b.getLongitude(),
                b.getLatitude(), c.getLongitude(), c.getLatitude()
        });
        final DoubleBuffer dst = DoubleBuffer.allocate(src.capacity());

        final int result = GLAntiMeridianHelper.normalizeHemisphere(2, src,
                dst);
        assertTrue((result & GLAntiMeridianHelper.MASK_IDL_CROSS) != 0);
        assertEquals(GLAntiMeridianHelper.HEMISPHERE_WEST,
                result & GLAntiMeridianHelper.MASK_PRIMARY_HEMISPHERE);

        // confirm all longitudes shifted into west hemi
        assertTrue(dst.get(0) < 0d);
        assertTrue(dst.get(2) < 0d);
        assertTrue(dst.get(4) < 0d);
    }
}
