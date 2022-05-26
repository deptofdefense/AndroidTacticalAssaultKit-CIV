
package com.atakmap.coremap.maps.coords;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class GeoBoundsTest extends ATAKInstrumentedTest {

    @org.junit.Test
    public void testToString() {
        GeoPoint pointA = new GeoPoint(34.0, -79.1, 100.2);
        GeoPoint pointB = new GeoPoint(34.3, -79.4, 100.5);
        GeoBounds geoBounds = new GeoBounds(pointA, pointB);
        String expectedResult = "GeoBounds {north=" + pointB.getLatitude()
                + ", west=" + pointB.getLongitude() +
                ", south=" + pointA.getLatitude() + ", east="
                + pointA.getLongitude() + ", minHAE=" +
                pointA.getAltitude() + ", maxHAE=" + pointB.getAltitude() + "}";
        assertEquals(expectedResult, geoBounds.toString());
    }

    @org.junit.Test
    public void testEqual() {
        GeoPoint pointA = new GeoPoint(34.0, -79.1, 100.2);
        GeoPoint pointB = new GeoPoint(34.3, -79.4, 100.5);
        GeoPoint pointC = new GeoPoint(34.6, -79.7, 100.8);
        GeoBounds geoBounds1 = new GeoBounds(pointA, pointB);
        GeoBounds geoBounds2 = new GeoBounds(pointB, pointA);
        GeoBounds geoBounds3 = new GeoBounds(pointA, pointC);
        assertEquals(geoBounds1, geoBounds2);
        assertNotEquals(geoBounds1, geoBounds3);
    }

    @org.junit.Test
    public void testIntersects() {
        GeoPoint pointA = new GeoPoint(34.0, -79.1, 100.2);
        GeoPoint pointB = new GeoPoint(34.3, -79.4, 100.5);
        GeoBounds geoBounds = new GeoBounds(pointA, pointB);
        assertFalse(geoBounds.intersects(34.3, -79.4, 34.0, -79.1, false, 100.6,
                100.8)); // too high
        assertFalse(geoBounds.intersects(34.3, -79.4, 34.0, -79.1, false, 100.0,
                100.1)); // too low
        assertFalse(geoBounds.intersects(34.5, -79.4, 34.4, -79.1, false, 100.2,
                100.5)); // too north
        assertFalse(geoBounds.intersects(33.9, -79.4, 33.8, -79.1, false, 100.2,
                100.5)); // too south
        assertFalse(geoBounds.intersects(34.3, -79.0, 34.0, -78.9, false, 100.2,
                100.5)); // too east
        assertFalse(geoBounds.intersects(34.3, -79.6, 34.0, -79.5, false, 100.2,
                100.5)); // too west
        assertTrue(geoBounds.intersects(34.3, -79.4, 34.0, -79.1, false, 100.2,
                100.5)); // intersect
    }

    @org.junit.Test
    public void testContains() {
        GeoPoint pointA = new GeoPoint(34.0, -79.1, 100.2);
        GeoPoint pointB = new GeoPoint(34.3, -79.4, 100.5);
        GeoBounds geoBounds = new GeoBounds(pointA, pointB);

        // contains geoPoint
        GeoPoint tooHighPoint = new GeoPoint(34.1, -79.2, 100.6);
        GeoPoint tooLowPoint = new GeoPoint(34.1, -79.2, 100.1);
        GeoPoint tooNorthPoint = new GeoPoint(34.4, -79.2, 100.3);
        GeoPoint tooSouthPoint = new GeoPoint(33.9, -79.2, 100.3);
        GeoPoint tooEastPoint = new GeoPoint(34.1, -79.0, 100.3);
        GeoPoint tooWestPoint = new GeoPoint(34.1, -79.5, 100.3);
        GeoPoint containedPoint = new GeoPoint(34.1, -79.2, 100.3);
        assertFalse(geoBounds.contains(tooHighPoint));
        assertFalse(geoBounds.contains(tooLowPoint));
        assertFalse(geoBounds.contains(tooNorthPoint));
        assertFalse(geoBounds.contains(tooSouthPoint));
        assertFalse(geoBounds.contains(tooEastPoint));
        assertFalse(geoBounds.contains(tooWestPoint));
        assertTrue(geoBounds.contains(containedPoint));

        // contains geoBounds
        GeoBounds tooHighBounds = new GeoBounds(34.0, -79.4, 100.2, 34.3, -79.1,
                100.6);
        GeoBounds tooLowBounds = new GeoBounds(34.0, -79.4, 100.1, 34.3, -79.1,
                100.5);
        GeoBounds tooNorthBounds = new GeoBounds(34.0, -79.4, 100.2, 34.4,
                -79.1, 100.5);
        GeoBounds tooSouthBounds = new GeoBounds(33.9, -79.4, 100.2, 34.3,
                -79.1, 100.5);
        GeoBounds tooEastBounds = new GeoBounds(34.0, -79.4, 100.2, 34.3, -79.0,
                100.5);
        GeoBounds tooWestBounds = new GeoBounds(34.0, -79.5, 100.2, 34.3, -79.1,
                100.5);
        GeoBounds containedBounds = new GeoBounds(34.0, -79.4, 100.2, 34.3,
                -79.1, 100.5);
        assertFalse(geoBounds.contains(tooHighBounds));
        assertFalse(geoBounds.contains(tooLowBounds));
        assertFalse(geoBounds.contains(tooNorthBounds));
        assertFalse(geoBounds.contains(tooSouthBounds));
        assertFalse(geoBounds.contains(tooEastBounds));
        assertFalse(geoBounds.contains(tooWestBounds));
        assertTrue(geoBounds.contains(containedBounds));
    }

    @org.junit.Test
    public void testGetCenter() {
        GeoPoint pointA = new GeoPoint(34.0, -79.1, 100.2);
        GeoPoint pointB = new GeoPoint(34.3, -79.4, 100.5);
        GeoBounds geoBounds = new GeoBounds(pointA, pointB);
        double midPointLat = (pointA.getLatitude() + pointB.getLatitude())
                / 2.0d;
        double midPointLng = (pointA.getLongitude() + pointB.getLongitude())
                / 2.0d;
        double midPointAlt = (pointA.getAltitude() + pointB.getAltitude())
                / 2.0d;
        GeoPoint midPoint = new GeoPoint(midPointLat, midPointLng, midPointAlt);
        assertEquals(geoBounds.getCenter(null), midPoint);
        GeoPoint rtnPoint = GeoPoint.createMutable();
        geoBounds.getCenter(rtnPoint);
        assertEquals(rtnPoint, midPoint);
    }

    @org.junit.Test
    public void testCreateFromPoints() {
        GeoPoint[] points = new GeoPoint[4];
        points[0] = new GeoPoint(34.1, -79.6, 101.5); // southeast
        points[1] = new GeoPoint(34.2, -79.7, 100.5); // lowest
        points[2] = new GeoPoint(34.3, -79.8, 102.2); // highest
        points[3] = new GeoPoint(34.4, -79.9, 101.1); // northwest
        GeoBounds geoBounds = GeoBounds.createFromPoints(points);
        GeoBounds expBounds = new GeoBounds(34.1, -79.9, 100.5, 34.4, -79.6,
                102.2);
        assertEquals(geoBounds, expBounds);
    }

    @org.junit.Test
    public void testCreateFromMixedPoints() {
        GeoPoint[] points = new GeoPoint[4];
        points[0] = new GeoPoint(34.1, -79.6, 101.5,
                GeoPoint.AltitudeReference.HAE); // southeast
        points[1] = new GeoPoint(34.2, -79.7, 100.5,
                GeoPoint.AltitudeReference.AGL); // lowest
        points[2] = new GeoPoint(34.3, -79.8, 102.2,
                GeoPoint.AltitudeReference.HAE); // highest
        points[3] = new GeoPoint(34.4, -79.9, 101.1,
                GeoPoint.AltitudeReference.AGL); // northwest
        GeoBounds geoBounds = GeoBounds.createFromPoints(points);

        // with a mixture of AGL and HAE altitudes we don't expect min/max altitude to be defined for the resulting GeoBounds
        GeoBounds notExpBounds = new GeoBounds(34.1, -79.9, 100.5, 34.4, -79.6,
                102.2);
        GeoBounds expBounds = new GeoBounds(34.1, -79.9, 34.4, -79.6);
        assertNotEquals(geoBounds, notExpBounds);
        assertEquals(geoBounds, expBounds);
    }
}
