
package com.atakmap.map.layer.raster;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.math.PointD;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DefaultDatasetProjection2Test extends ATAKInstrumentedTest {
    @Test
    public void test_web_mercator_bounds() {
        final int srid = 3857;
        final int width = -2147483648;
        final int height = -2147483648;
        final GeoPoint ul = new GeoPoint(85.05112877980659, -180.0);
        final GeoPoint ur = new GeoPoint(85.05112877980659, 180.0);
        final GeoPoint lr = new GeoPoint(-85.05112877980659, 180.0);
        final GeoPoint ll = new GeoPoint(-85.05112877980659, -180.0);

        DefaultDatasetProjection2 imprecise = new DefaultDatasetProjection2(
                srid, width, height, ul, ur, lr, ll);

        final long tileSrcHeight = 2147483648L;
        final long tileSrcWidth = 2147483648L;
        final long tileSrcX = 0L;
        final long tileSrcY = 0L;

        double minLat = 90;
        double maxLat = -90;
        double minLng = 180;
        double maxLng = -180;

        PointD scratchP = new PointD(0d, 0d, 0d);
        GeoPoint scratchG = GeoPoint.createMutable();

        scratchP.x = tileSrcX;
        scratchP.y = tileSrcY;
        imprecise.imageToGround(scratchP, scratchG);
        if (scratchG.getLatitude() < minLat)
            minLat = scratchG.getLatitude();
        if (scratchG.getLatitude() > maxLat)
            maxLat = scratchG.getLatitude();
        if (scratchG.getLongitude() < minLng)
            minLng = scratchG.getLongitude();
        if (scratchG.getLongitude() > maxLng)
            maxLng = scratchG.getLongitude();

        scratchP.x = tileSrcX + tileSrcWidth;
        scratchP.y = tileSrcY;
        imprecise.imageToGround(scratchP, scratchG);
        if (scratchG.getLatitude() < minLat)
            minLat = scratchG.getLatitude();
        if (scratchG.getLatitude() > maxLat)
            maxLat = scratchG.getLatitude();
        if (scratchG.getLongitude() < minLng)
            minLng = scratchG.getLongitude();
        if (scratchG.getLongitude() > maxLng)
            maxLng = scratchG.getLongitude();

        scratchP.x = tileSrcX + tileSrcWidth;
        scratchP.y = tileSrcY + tileSrcHeight;
        imprecise.imageToGround(scratchP, scratchG);
        if (scratchG.getLatitude() < minLat)
            minLat = scratchG.getLatitude();
        if (scratchG.getLatitude() > maxLat)
            maxLat = scratchG.getLatitude();
        if (scratchG.getLongitude() < minLng)
            minLng = scratchG.getLongitude();
        if (scratchG.getLongitude() > maxLng)
            maxLng = scratchG.getLongitude();

        scratchP.x = tileSrcX;
        scratchP.y = tileSrcY + tileSrcHeight;
        imprecise.imageToGround(scratchP, scratchG);
        if (scratchG.getLatitude() < minLat)
            minLat = scratchG.getLatitude();
        if (scratchG.getLatitude() > maxLat)
            maxLat = scratchG.getLatitude();
        if (scratchG.getLongitude() < minLng)
            minLng = scratchG.getLongitude();
        if (scratchG.getLongitude() > maxLng)
            maxLng = scratchG.getLongitude();

        assertEquals(ul.getLatitude(), maxLat, 0.000001d);
        assertEquals(ul.getLongitude(), minLng, 0.000001d);
        assertEquals(lr.getLatitude(), minLat, 0.000001d);
        assertEquals(lr.getLongitude(), maxLng, 0.000001d);
    }
}
