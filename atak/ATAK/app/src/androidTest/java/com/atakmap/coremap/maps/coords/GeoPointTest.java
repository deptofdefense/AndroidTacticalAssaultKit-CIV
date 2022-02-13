
package com.atakmap.coremap.maps.coords;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class GeoPointTest extends ATAKInstrumentedTest {

    @org.junit.Test
    public void createMutable() {
        assertTrue(GeoPoint.createMutable().isMutable());
    }

    @org.junit.Test
    public void isValid() {
        assertTrue(new GeoPoint(0, 0).isValid());
        assertTrue(new GeoPoint(90, 180).isValid());
        assertTrue(new GeoPoint(-90, 180).isValid());
        assertTrue(new GeoPoint(-90, -180).isValid());
        assertTrue(new GeoPoint(90, -180).isValid());

    }

    @org.junit.Test
    public void isAltitudeValid() {
        assertTrue(new GeoPoint(0, 0, 0).isAltitudeValid());
        assertTrue(new GeoPoint(0, 0, -3600).isAltitudeValid());
        assertTrue(new GeoPoint(0, 0, 72600).isAltitudeValid());
        assertFalse(new GeoPoint(0, 0, -3601).isAltitudeValid());
        assertFalse(new GeoPoint(0, 0, 76001).isAltitudeValid());
        assertFalse(new GeoPoint(0, 0).isAltitudeValid());
    }

    @org.junit.Test
    public void isMutable() {
        assertTrue(GeoPoint.createMutable().isMutable());
    }

    @org.junit.Test
    public void distanceTo() {
        assertEquals(0, new GeoPoint(0, 0).distanceTo(new GeoPoint(0, 0)), 0.0);
        assertTrue(new GeoPoint(90, 90).distanceTo(new GeoPoint(90, 0)) < 1);
        assertTrue(new GeoPoint(0, -180).distanceTo(new GeoPoint(0, 180)) < 1);
        assertTrue(new GeoPoint(0, 0).distanceTo(new GeoPoint(1, 1)) > 0);
        assertTrue(new GeoPoint(0, 0).distanceTo(new GeoPoint(-1, -1)) > 0);
    }

    @org.junit.Test
    public void bearingTo() {
    }

    @org.junit.Test
    public void getLatitude() {
        assertEquals(90, new GeoPoint(90, 90).getLatitude(), 0.0);
    }

    @org.junit.Test
    public void getLongitude() {
        assertEquals(90, new GeoPoint(90, 90).getLongitude(), 0.0);
    }

    @org.junit.Test
    public void getAltitude() {
        assertEquals(0, new GeoPoint(90, 90, 0).getAltitude(), 0.0);
        assertTrue(Double
                .isNaN(new GeoPoint(90, 90, GeoPoint.UNKNOWN).getAltitude()));
        assertTrue(Double.isNaN(new GeoPoint(90, 90).getAltitude()));
    }

    @org.junit.Test
    public void getAltitudeReference() {
        assertSame(new GeoPoint(90, 90)
                .getAltitudeReference(), GeoPoint.AltitudeReference.HAE);
        assertSame(new GeoPoint(90, 90, 0, GeoPoint.AltitudeReference.AGL)
                .getAltitudeReference(), GeoPoint.AltitudeReference.AGL);
    }

    @org.junit.Test
    public void getCE() {
        assertTrue(Double.isNaN(new GeoPoint(90, 90).getCE()));

    }

    @org.junit.Test
    public void getLE() {
        assertTrue(Double.isNaN(new GeoPoint(90, 90).getLE()));
    }

    @org.junit.Test
    public void toString1() {
    }

    @org.junit.Test
    public void toStringRepresentation() {
    }

    @org.junit.Test
    public void parseGeoPoint() {
    }

    @org.junit.Test
    public void describeContents() {
    }

    @org.junit.Test
    public void writeToParcel() {
    }

    @org.junit.Test
    public void readFromParcel() {
    }

    @org.junit.Test
    public void set() {
        GeoPoint gp = GeoPoint.createMutable();
        gp.set(new GeoPoint(45, 46, 47));
        assertEquals(0, Double.compare(gp.getLatitude(), 45d));
        assertEquals(0, Double.compare(gp.getLongitude(), 46d));
        assertEquals(0, Double.compare(gp.getAltitude(), 47d));

        gp.set(100d);
        assertEquals(0, Double.compare(gp.getAltitude(), 100d));

        gp.set(50, 51);
        assertEquals(0, Double.compare(gp.getLatitude(), 50d));
        assertEquals(0, Double.compare(gp.getLongitude(), 51d));

        gp.set(60, 61, 62);
        assertEquals(0, Double.compare(gp.getLatitude(), 60d));
        assertEquals(0, Double.compare(gp.getLongitude(), 61d));
        assertEquals(0, Double.compare(gp.getAltitude(), 62d));

        GeoPoint gp2 = new GeoPoint(10, 11, 12);
        gp2.set(60, 61, 62);
        assertEquals(0, Double.compare(gp2.getLatitude(), 10d));
        assertEquals(0, Double.compare(gp2.getLongitude(), 11d));
        assertEquals(0, Double.compare(gp2.getAltitude(), 12d));

    }

}
