
package com.atakmap.coremap.cot.event;

import com.atakmap.coremap.maps.coords.GeoPoint;

import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import static org.junit.Assert.*;

public class CotPointTests {
    @Test
    public void test_valid_constructed_point() {
        CotPoint point = new CotPoint(1, 2, 3, 4, 5);
        assertEquals(point.getLat(), 1, 0);
        assertEquals(point.getLon(), 2, 0);
        assertEquals(point.getHae(), 3, 0);
        assertEquals(point.getCe(), 4, 0);
        assertEquals(point.getLe(), 5, 0);
    }

    @Test
    public void test_invalid_hae_ce_le_point() {
        CotPoint point = new CotPoint(1, 2, Double.NaN, Double.NaN, Double.NaN);
        assertEquals(point.getLat(), 1, 0);
        assertEquals(point.getLon(), 2, 0);
        assertEquals(point.getHae(), CotPoint.UNKNOWN, 0);
        assertEquals(point.getCe(), CotPoint.UNKNOWN, 0);
        assertEquals(point.getLe(), CotPoint.UNKNOWN, 0);
    }

    @Test
    public void test_construct_from_geo_point() {
        GeoPoint gp = new GeoPoint(1, 2, 3, GeoPoint.AltitudeReference.HAE, 4,
                5);
        CotPoint point = new CotPoint(gp);
        assertEquals(point.getLat(), 1, 0);
        assertEquals(point.getLon(), 2, 0);
        assertEquals(point.getHae(), 3, 0);
        assertEquals(point.getCe(), 4, 0);
        assertEquals(point.getLe(), 5, 0);
    }

    @Test
    public void test_construct_from_geo_point_invalid_hae_ce_le() {
        GeoPoint gp = new GeoPoint(1, 2, Double.NaN,
                GeoPoint.AltitudeReference.HAE, Double.NaN, Double.NaN);
        CotPoint point = new CotPoint(gp);
        assertEquals(point.getLat(), 1, 0);
        assertEquals(point.getLon(), 2, 0);
        assertEquals(point.getHae(), CotPoint.UNKNOWN, 0);
        assertEquals(point.getCe(), CotPoint.UNKNOWN, 0);
        assertEquals(point.getLe(), CotPoint.UNKNOWN, 0);
    }

    @Test
    public void test_construct_allow_wild_angles() {
        CotPoint point = new CotPoint(2000, 3000, 3, 4, 5);
        assertEquals(point.getLat(), 2000, 0);
        assertEquals(point.getLon(), 3000, 0);
        assertEquals(point.getHae(), 3, 0);
        assertEquals(point.getCe(), 4, 0);
        assertEquals(point.getLe(), 5, 0);
    }

    @Test
    public void test_to_geo_point() {
        CotPoint point = new CotPoint(1, 2, 3, 4, 5);
        GeoPoint gp = point.toGeoPoint();
        assertEquals(gp.getLatitude(), 1, 0);
        assertEquals(gp.getLongitude(), 2, 0);
        assertEquals(gp.getAltitude(), 3, 0);
        assertEquals(gp.getAltitudeReference(), GeoPoint.AltitudeReference.HAE);
        assertEquals(gp.getCE(), 4, 0);
        assertEquals(gp.getLE(), 5, 0);
    }

    @Test
    public void test_copy_constructor() {
        CotPoint point0 = new CotPoint(1, 2, 3, 4, 5);
        CotPoint point = new CotPoint(point0);
        assertEquals(point.getLat(), 1, 0);
        assertEquals(point.getLon(), 2, 0);
        assertEquals(point.getHae(), 3, 0);
        assertEquals(point.getCe(), 4, 0);
        assertEquals(point.getLe(), 5, 0);
    }

    @Test
    public void test_build_xml()
            throws ParserConfigurationException, SAXException {
        CotPoint point = new CotPoint(1, 2, 3, 4, 5);
        StringBuilder sb = new StringBuilder();
        try {
            point.buildXml(sb);
            SAXParserFactory fabrique = SAXParserFactory.newInstance();
            SAXParser parser = fabrique.newSAXParser();
            parser.parse(new ByteArrayInputStream(sb.toString().getBytes()),
                    new DefaultHandler() {
                        @Override
                        public void startElement(String uri, String localName,
                                String qName, Attributes attributes)
                                throws SAXException {
                            super.startElement(uri, localName, qName,
                                    attributes);
                            assertEquals(qName, "point");
                            int latIdx = attributes.getIndex("lat");
                            assertNotEquals(latIdx, -1);
                            int lngIdx = attributes.getIndex("lon");
                            assertNotEquals(lngIdx, -1);
                        }
                    });

        } catch (IOException e) {
            assertFalse(e.getMessage(), true);
        }
    }
}
