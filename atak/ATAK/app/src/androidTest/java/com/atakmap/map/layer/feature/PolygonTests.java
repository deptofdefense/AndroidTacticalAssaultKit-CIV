
package com.atakmap.map.layer.feature;

import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Polygon;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

public class PolygonTests extends AbstractGeometryTests {
    @Override
    Geometry createTestGeometry(int dimension) {
        return new Polygon(dimension);
    }

    @Override
    Geometry createRandomGeometry(int dimension) {
        return randomPolygon(dimension);
    }

    // constructor tests

    @Test
    public void Polygon_construtor_empty() {
        final int dimension = 2;
        Polygon polygon = new Polygon(dimension);

        Assert.assertNull(polygon.getExteriorRing());
        Assert.assertTrue(polygon.getInteriorRings().isEmpty());
    }

    @Test
    public void Polygon_2d_construtor_ext_ring_roundtrip() {
        final int dimension = 2;
        LineString linestring = randomLineString(dimension, true);
        Polygon polygon = new Polygon(linestring);
        Assert.assertEquals(polygon.getDimension(), linestring.getDimension());
        Assert.assertNotNull(polygon.getExteriorRing());
        Assert.assertSame(linestring, polygon.getExteriorRing());
    }

    @Test
    public void Polygon_3d_construtor_ext_ring_roundtrip() {
        final int dimension = 3;
        LineString linestring = randomLineString(dimension, true);
        Polygon polygon = new Polygon(linestring);
        Assert.assertEquals(polygon.getDimension(), linestring.getDimension());
        Assert.assertNotNull(polygon.getExteriorRing());
        Assert.assertSame(linestring, polygon.getExteriorRing());
    }

    @Test
    public void Polygon_construtor_empty_ext_ring() {
        final int dimension = 2;
        LineString linestring = new LineString(dimension);
        Polygon polygon = new Polygon(linestring);
    }

    @Test(expected = NullPointerException.class)
    public void Polygon_construtor_null_ext_ring_throws() {
        Polygon polygon = new Polygon(null);
        Assert.fail();
    }

    @Test
    public void Polygon_2d_construtor_ext_ring_interior_rings_roundtrip() {
        final int dimension = 2;
        LineString linestring = randomLineString(dimension, true);
        final int numInnerRings = 3;
        ArrayList<LineString> innerRings = new ArrayList<LineString>(
                numInnerRings);
        for (int i = 0; i < numInnerRings; i++) {
            innerRings.add(randomLineString(dimension, true));
        }
        Polygon polygon = new Polygon(linestring, innerRings);
        Assert.assertEquals(polygon.getDimension(), linestring.getDimension());
        Assert.assertNotNull(polygon.getExteriorRing());
        Assert.assertSame(linestring, polygon.getExteriorRing());
        Assert.assertTrue(innerRings.containsAll(polygon.getInteriorRings()));
        Assert.assertTrue(polygon.getInteriorRings().containsAll(innerRings));
    }

    @Test
    public void Polygon_3d_construtor_ext_ring_interior_rings_roundtrip() {
        final int dimension = 2;
        LineString linestring = randomLineString(dimension, true);
        final int numInnerRings = 3;
        ArrayList<LineString> innerRings = new ArrayList<LineString>(
                numInnerRings);
        for (int i = 0; i < numInnerRings; i++) {
            innerRings.add(randomLineString(dimension, true));
        }
        Polygon polygon = new Polygon(linestring, innerRings);
        Assert.assertEquals(polygon.getDimension(), linestring.getDimension());
        Assert.assertNotNull(polygon.getExteriorRing());
        Assert.assertSame(linestring, polygon.getExteriorRing());
        Assert.assertTrue(innerRings.containsAll(polygon.getInteriorRings()));
        Assert.assertTrue(polygon.getInteriorRings().containsAll(innerRings));
    }

    @Test(expected = NullPointerException.class)
    public void Polygon_construtor_ext_ring_null_interior_rings_throws() {
        final int dimension = 2;
        LineString linestring = randomLineString(dimension, true);
        Polygon polygon = new Polygon(linestring, null);
        Assert.fail();
    }

    // data model tests

    @Test
    public void Polygon_2d_ext_ring_post_add_modify_roundtrip() {
        final int dimension = 2;
        LineString linestring = new LineString(dimension);
        Polygon polygon = new Polygon(linestring);
        Assert.assertEquals(polygon.getDimension(), linestring.getDimension());

        linestring.addPoint(10, 10);
        linestring.addPoint(10, 20);
        linestring.addPoint(20, 20);
        linestring.addPoint(10, 10);

        assertEqual(polygon, polygon.clone());
    }

    @Test
    public void Polygon_2d_add_inner_ring_roundtrip() {
        final int dimension = 2;
        LineString linestring = randomLineString(dimension, true);
        Polygon polygon = new Polygon(linestring);
        Assert.assertEquals(polygon.getDimension(), linestring.getDimension());

        LineString added = randomLineString(2, true);
        polygon.addRing(added);

        Assert.assertTrue(polygon.getInteriorRings().contains(added));
    }

    @Test
    public void Polygon_2d_add_inner_ring_mismatch_dimension_updated() {
        final int dimension = 2;
        LineString linestring = randomLineString(dimension, true);
        Polygon polygon = new Polygon(linestring);
        Assert.assertEquals(polygon.getDimension(), linestring.getDimension());

        LineString added = randomLineString(3, true);
        Assert.assertNotEquals(added.getDimension(), polygon.getDimension());
        polygon.addRing(added);

        Assert.assertEquals(added.getDimension(), polygon.getDimension());
    }

    @Test(expected = NullPointerException.class)
    public void Polygon_add_null_inner_ring_throws() {
        final int dimension = 2;
        LineString linestring = randomLineString(dimension, true);
        Polygon polygon = new Polygon(linestring);
        Assert.assertEquals(polygon.getDimension(), linestring.getDimension());

        polygon.addRing(null);
        Assert.fail();
    }

    @Test
    public void Polygon_remove_null_inner_ring() {
        final int dimension = 2;
        LineString linestring = randomLineString(dimension, true);
        Polygon polygon = new Polygon(linestring);
        Assert.assertEquals(polygon.getDimension(), linestring.getDimension());

        LineString added = randomLineString(3, true);
        Assert.assertNotEquals(added.getDimension(), polygon.getDimension());
        polygon.addRing(added);

        final boolean modified = polygon.getInteriorRings().remove(null);
        Assert.assertFalse(modified);
    }

    // envelope tests

    @Test
    public void Polygon_2d_envelope() {
        final int dimension = 2;
        LineString linestring = randomLineString(dimension, true);
        final int numInnerRings = 3;
        ArrayList<LineString> innerRings = new ArrayList<LineString>(
                numInnerRings);
        for (int i = 0; i < numInnerRings; i++) {
            innerRings.add(randomLineString(dimension, true));
        }
        Polygon polygon = new Polygon(linestring, innerRings);

        Envelope truth = linestring.getEnvelope();
        envelope_test(polygon, truth);
    }

    @Test
    public void Polygon_3d_envelope() {
        final int dimension = 3;
        LineString linestring = randomLineString(dimension, true);
        final int numInnerRings = 3;
        ArrayList<LineString> innerRings = new ArrayList<LineString>(
                numInnerRings);
        for (int i = 0; i < numInnerRings; i++) {
            innerRings.add(randomLineString(dimension, true));
        }
        Polygon polygon = new Polygon(linestring, innerRings);

        Envelope truth = linestring.getEnvelope();
        envelope_test(polygon, truth);

    }

    // wkb tests
}
