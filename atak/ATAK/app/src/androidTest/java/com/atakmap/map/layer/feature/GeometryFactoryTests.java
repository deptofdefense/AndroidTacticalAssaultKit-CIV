
package com.atakmap.map.layer.feature;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GeometryFactoryTests extends ATAKInstrumentedTest {
    private void geometry_wkb_roundtrip(Geometry original, ByteOrder order) {
        byte[] wkb = new byte[original.computeWkbSize()];
        original.toWkb(ByteBuffer.wrap(wkb).order(order));
        Geometry parsed = GeometryFactory.parseWkb(wkb);
        assertNotNull(parsed);
        assertTrue(original.equals(parsed));
    }

    private void geometry_blob_roundtrip(Geometry original, ByteOrder order) {
        byte[] blob = new byte[GeometryFactory
                .computeSpatiaLiteBlobSize(original)];
        GeometryFactory.toSpatiaLiteBlob(original, 4326,
                ByteBuffer.wrap(blob).order(order));
        Geometry parsed = GeometryFactory.parseSpatiaLiteBlob(blob);
        assertNotNull(parsed);
        assertTrue(original.equals(parsed));
    }

    // WKB
    @Test
    public void point_2d_wkb_be_roundtrip() {
        geometry_wkb_roundtrip(new Point(1.2, 2.3), ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void point_2d_wkb_le_roundtrip() {
        geometry_wkb_roundtrip(new Point(1.2, 2.3), ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void point_3d_wkb_be_roundtrip() {
        geometry_wkb_roundtrip(new Point(1.2, 2.3, 4.5), ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void point_3d_wkb_le_roundtrip() {
        geometry_wkb_roundtrip(new Point(1.2, 2.3, 4.5),
                ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void linestring_2d_wkb_be_roundtrip() {
        LineString linestring = new LineString(2);
        linestring.addPoint(1.2, 2.3);
        linestring.addPoint(4.5, 6.7);
        geometry_wkb_roundtrip(linestring, ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void linestring_2d_wkb_le_roundtrip() {
        LineString linestring = new LineString(2);
        linestring.addPoint(1.2, 2.3);
        linestring.addPoint(4.5, 6.7);
        geometry_wkb_roundtrip(linestring, ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void linestring_3d_wkb_be_roundtrip() {
        LineString linestring = new LineString(3);
        linestring.addPoint(1.2, 2.3, 4.5);
        linestring.addPoint(6.7, 8.9, 10.11);
        geometry_wkb_roundtrip(linestring, ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void linestring_3d_wkb_le_roundtrip() {
        LineString linestring = new LineString(3);
        linestring.addPoint(1.2, 2.3, 4.5);
        linestring.addPoint(6.7, 8.9, 10.11);
        geometry_wkb_roundtrip(linestring, ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void polygon_2d_wkb_be_roundtrip() {
        LineString linestring = new LineString(2);
        linestring.addPoint(1.2, 2.3);
        linestring.addPoint(4.5, 6.7);
        linestring.addPoint(8.9, 10.11);
        linestring.addPoint(1.2, 2.3);
        geometry_wkb_roundtrip(new Polygon(linestring), ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void polygon_2d_wkb_le_roundtrip() {
        LineString linestring = new LineString(2);
        linestring.addPoint(1.2, 2.3);
        linestring.addPoint(4.5, 6.7);
        linestring.addPoint(8.9, 10.11);
        linestring.addPoint(1.2, 2.3);
        geometry_wkb_roundtrip(new Polygon(linestring),
                ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void polygon_3d_wkb_be_roundtrip() {
        LineString linestring = new LineString(3);
        linestring.addPoint(1.2, 2.3, 4.5);
        linestring.addPoint(6.7, 8.9, 10.11);
        linestring.addPoint(12.13, 14.15, 16.17);
        linestring.addPoint(1.2, 2.3, 4.5);
        geometry_wkb_roundtrip(new Polygon(linestring), ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void polygon_3d_wkb_le_roundtrip() {
        LineString linestring = new LineString(3);
        linestring.addPoint(1.2, 2.3, 4.5);
        linestring.addPoint(6.7, 8.9, 10.11);
        linestring.addPoint(12.13, 14.15, 16.17);
        linestring.addPoint(1.2, 2.3, 4.5);
        geometry_wkb_roundtrip(new Polygon(linestring),
                ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void geometrycollection_2d_wkb_be_roundtrip() {
        Point point = new Point(0.1, 2.3);
        LineString linestring = new LineString(2);
        linestring.addPoint(4.5, 6.7);
        linestring.addPoint(8.9, 10.11);
        Polygon polygon = new Polygon(2);
        polygon.addRing(new LineString(polygon.getDimension()));
        polygon.getExteriorRing().addPoint(12.13, 14.15);
        polygon.getExteriorRing().addPoint(16.17, 18.19);
        polygon.getExteriorRing().addPoint(20.21, 22.23);
        polygon.getExteriorRing().addPoint(12.13, 14.15);

        GeometryCollection geometrycollection = new GeometryCollection(2);
        geometrycollection.addGeometry(point);
        geometrycollection.addGeometry(linestring);
        geometrycollection.addGeometry(polygon);
        geometry_wkb_roundtrip(geometrycollection, ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void geometrycollection_2d_wkb_le_roundtrip() {
        Point point = new Point(0.1, 2.3);
        LineString linestring = new LineString(2);
        linestring.addPoint(4.5, 6.7);
        linestring.addPoint(8.9, 10.11);
        Polygon polygon = new Polygon(2);
        polygon.addRing(new LineString(polygon.getDimension()));
        polygon.getExteriorRing().addPoint(12.13, 14.15);
        polygon.getExteriorRing().addPoint(16.17, 18.19);
        polygon.getExteriorRing().addPoint(20.21, 22.23);
        polygon.getExteriorRing().addPoint(12.13, 14.15);

        GeometryCollection geometrycollection = new GeometryCollection(2);
        geometrycollection.addGeometry(point);
        geometrycollection.addGeometry(linestring);
        geometrycollection.addGeometry(polygon);
        geometry_wkb_roundtrip(geometrycollection, ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void geometrycollection_3d_wkb_be_roundtrip() {
        Point point = new Point(0.1, 2.3, 4.5);
        LineString linestring = new LineString(3);
        linestring.addPoint(6.7, 8.9, 10.11);
        linestring.addPoint(12.13, 14.15, 16.17);
        Polygon polygon = new Polygon(3);
        polygon.addRing(new LineString(polygon.getDimension()));
        polygon.getExteriorRing().addPoint(18.19, 20.21, 22.23);
        polygon.getExteriorRing().addPoint(24.25, 26.27, 28.29);
        polygon.getExteriorRing().addPoint(30.31, 32.33, 34.35);
        polygon.getExteriorRing().addPoint(18.19, 20.21, 22.23);

        GeometryCollection geometrycollection = new GeometryCollection(3);
        geometrycollection.addGeometry(point);
        geometrycollection.addGeometry(linestring);
        geometrycollection.addGeometry(polygon);
        geometry_wkb_roundtrip(geometrycollection, ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void geometrycollection_3d_wkb_le_roundtrip() {
        Point point = new Point(0.1, 2.3, 4.5);
        LineString linestring = new LineString(3);
        linestring.addPoint(6.7, 8.9, 10.11);
        linestring.addPoint(12.13, 14.15, 16.17);
        Polygon polygon = new Polygon(3);
        polygon.addRing(new LineString(polygon.getDimension()));
        polygon.getExteriorRing().addPoint(18.19, 20.21, 22.23);
        polygon.getExteriorRing().addPoint(24.25, 26.27, 28.29);
        polygon.getExteriorRing().addPoint(30.31, 32.33, 34.35);
        polygon.getExteriorRing().addPoint(18.19, 20.21, 22.23);

        GeometryCollection geometrycollection = new GeometryCollection(3);
        geometrycollection.addGeometry(point);
        geometrycollection.addGeometry(linestring);
        geometrycollection.addGeometry(polygon);
        geometry_wkb_roundtrip(geometrycollection, ByteOrder.LITTLE_ENDIAN);
    }

    // SpatiaLite Blob
    @Test
    public void point_2d_blob_be_roundtrip() {
        geometry_blob_roundtrip(new Point(1.2, 2.3), ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void point_2d_blob_le_roundtrip() {
        geometry_blob_roundtrip(new Point(1.2, 2.3), ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void point_3d_blob_be_roundtrip() {
        geometry_blob_roundtrip(new Point(1.2, 2.3, 4.5), ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void point_3d_blob_le_roundtrip() {
        geometry_blob_roundtrip(new Point(1.2, 2.3, 4.5),
                ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void linestring_2d_blob_be_roundtrip() {
        LineString linestring = new LineString(2);
        linestring.addPoint(1.2, 2.3);
        linestring.addPoint(4.5, 6.7);
        geometry_blob_roundtrip(linestring, ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void linestring_2d_blob_le_roundtrip() {
        LineString linestring = new LineString(2);
        linestring.addPoint(1.2, 2.3);
        linestring.addPoint(4.5, 6.7);
        geometry_blob_roundtrip(linestring, ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void linestring_3d_blob_be_roundtrip() {
        LineString linestring = new LineString(3);
        linestring.addPoint(1.2, 2.3, 4.5);
        linestring.addPoint(6.7, 8.9, 10.11);
        geometry_blob_roundtrip(linestring, ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void linestring_3d_blob_le_roundtrip() {
        LineString linestring = new LineString(3);
        linestring.addPoint(1.2, 2.3, 4.5);
        linestring.addPoint(6.7, 8.9, 10.11);
        geometry_blob_roundtrip(linestring, ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void polygon_2d_blob_be_roundtrip() {
        LineString linestring = new LineString(2);
        linestring.addPoint(1.2, 2.3);
        linestring.addPoint(4.5, 6.7);
        linestring.addPoint(8.9, 10.11);
        linestring.addPoint(1.2, 2.3);
        geometry_blob_roundtrip(new Polygon(linestring), ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void polygon_2d_blob_le_roundtrip() {
        LineString linestring = new LineString(2);
        linestring.addPoint(1.2, 2.3);
        linestring.addPoint(4.5, 6.7);
        linestring.addPoint(8.9, 10.11);
        linestring.addPoint(1.2, 2.3);
        geometry_blob_roundtrip(new Polygon(linestring),
                ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void polygon_3d_blob_be_roundtrip() {
        LineString linestring = new LineString(3);
        linestring.addPoint(1.2, 2.3, 4.5);
        linestring.addPoint(6.7, 8.9, 10.11);
        linestring.addPoint(12.13, 14.15, 16.17);
        linestring.addPoint(1.2, 2.3, 4.5);
        geometry_blob_roundtrip(new Polygon(linestring), ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void polygon_3d_blob_le_roundtrip() {
        LineString linestring = new LineString(3);
        linestring.addPoint(1.2, 2.3, 4.5);
        linestring.addPoint(6.7, 8.9, 10.11);
        linestring.addPoint(12.13, 14.15, 16.17);
        linestring.addPoint(1.2, 2.3, 4.5);
        geometry_blob_roundtrip(new Polygon(linestring),
                ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void geometrycollection_2d_blob_be_roundtrip() {
        Point point = new Point(0.1, 2.3);
        LineString linestring = new LineString(2);
        linestring.addPoint(4.5, 6.7);
        linestring.addPoint(8.9, 10.11);
        Polygon polygon = new Polygon(2);
        polygon.addRing(new LineString(polygon.getDimension()));
        polygon.getExteriorRing().addPoint(12.13, 14.15);
        polygon.getExteriorRing().addPoint(16.17, 18.19);
        polygon.getExteriorRing().addPoint(20.21, 22.23);
        polygon.getExteriorRing().addPoint(12.13, 14.15);

        GeometryCollection geometrycollection = new GeometryCollection(2);
        geometrycollection.addGeometry(point);
        geometrycollection.addGeometry(linestring);
        geometrycollection.addGeometry(polygon);
        geometry_blob_roundtrip(geometrycollection, ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void geometrycollection_2d_blob_le_roundtrip() {
        Point point = new Point(0.1, 2.3);
        LineString linestring = new LineString(2);
        linestring.addPoint(4.5, 6.7);
        linestring.addPoint(8.9, 10.11);
        Polygon polygon = new Polygon(2);
        polygon.addRing(new LineString(polygon.getDimension()));
        polygon.getExteriorRing().addPoint(12.13, 14.15);
        polygon.getExteriorRing().addPoint(16.17, 18.19);
        polygon.getExteriorRing().addPoint(20.21, 22.23);
        polygon.getExteriorRing().addPoint(12.13, 14.15);

        GeometryCollection geometrycollection = new GeometryCollection(2);
        geometrycollection.addGeometry(point);
        geometrycollection.addGeometry(linestring);
        geometrycollection.addGeometry(polygon);
        geometry_blob_roundtrip(geometrycollection, ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void geometrycollection_3d_blob_be_roundtrip() {
        Point point = new Point(0.1, 2.3, 4.5);
        LineString linestring = new LineString(3);
        linestring.addPoint(6.7, 8.9, 10.11);
        linestring.addPoint(12.13, 14.15, 16.17);
        Polygon polygon = new Polygon(3);
        polygon.addRing(new LineString(polygon.getDimension()));
        polygon.getExteriorRing().addPoint(18.19, 20.21, 22.23);
        polygon.getExteriorRing().addPoint(24.25, 26.27, 28.29);
        polygon.getExteriorRing().addPoint(30.31, 32.33, 34.35);
        polygon.getExteriorRing().addPoint(18.19, 20.21, 22.23);

        GeometryCollection geometrycollection = new GeometryCollection(3);
        geometrycollection.addGeometry(point);
        geometrycollection.addGeometry(linestring);
        geometrycollection.addGeometry(polygon);
        geometry_wkb_roundtrip(geometrycollection, ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void geometrycollection_3d_blob_le_roundtrip() {
        Point point = new Point(0.1, 2.3, 4.5);
        LineString linestring = new LineString(3);
        linestring.addPoint(6.7, 8.9, 10.11);
        linestring.addPoint(12.13, 14.15, 16.17);
        Polygon polygon = new Polygon(3);
        polygon.addRing(new LineString(polygon.getDimension()));
        polygon.getExteriorRing().addPoint(18.19, 20.21, 22.23);
        polygon.getExteriorRing().addPoint(24.25, 26.27, 28.29);
        polygon.getExteriorRing().addPoint(30.31, 32.33, 34.35);
        polygon.getExteriorRing().addPoint(18.19, 20.21, 22.23);

        GeometryCollection geometrycollection = new GeometryCollection(3);
        geometrycollection.addGeometry(point);
        geometrycollection.addGeometry(linestring);
        geometrycollection.addGeometry(polygon);
        geometry_blob_roundtrip(geometrycollection, ByteOrder.LITTLE_ENDIAN);
    }

    // bad inputs
    @Test
    public void parse_wkb_null_array_returns_null_geometry() {
        final Geometry result = GeometryFactory.parseWkb((byte[]) null);
        assertNull(result);
    }

    @Test
    public void parse_wkb_null_buffer_returns_null_geometry() {
        final Geometry result = GeometryFactory.parseWkb((ByteBuffer) null);
        assertNull(result);
    }

    @Test
    public void parse_blob_null_array_returns_null_geometry() {
        final Geometry result = GeometryFactory
                .parseSpatiaLiteBlob((byte[]) null);
        assertNull(result);
    }

    @Test
    public void parse_blob_null_buffer_returns_null_geometry() {
        final Geometry result = GeometryFactory
                .parseSpatiaLiteBlob((ByteBuffer) null);
        assertNull(result);
    }

    @Test
    public void parse_wkb_invalid_array_throws() {
        final Geometry result = GeometryFactory.parseWkb(new byte[1]);
        assertNull(result);
    }

    @Test
    public void parse_wkb_invalid_buffer_throws() {
        final Geometry result = GeometryFactory
                .parseWkb(ByteBuffer.allocate(1));
        assertNull(result);
    }

    @Test
    public void parse_blob_invalid_array_throws() {
        final Geometry result = GeometryFactory
                .parseSpatiaLiteBlob(new byte[1]);
        assertNull(result);
    }

    @Test
    public void parse_blob_invalid_buffer_throws() {
        final Geometry result = GeometryFactory
                .parseSpatiaLiteBlob(ByteBuffer.allocate(1));
        assertNull(result);
    }

    @Test(expected = RuntimeException.class)
    public void to_wkb_null_geom_throws() {
        GeometryFactory.toWkb(null, ByteBuffer.wrap(new byte[1]));
        fail();
    }

    @Test(expected = RuntimeException.class)
    public void to_blob_null_geom_throws() {
        GeometryFactory.toSpatiaLiteBlob(null, 4326,
                ByteBuffer.wrap(new byte[1]));
        fail();
    }

    @Test(expected = RuntimeException.class)
    public void to_wkb_null_buffer_throws() {
        GeometryFactory.toWkb(new Point(0, 1, 2), null);
        fail();
    }

    @Test(expected = RuntimeException.class)
    public void to_blob_null_buffer_throws() {
        GeometryFactory.toSpatiaLiteBlob(new Point(0, 1, 2), 4326, null);
        fail();
    }

    @Test(expected = RuntimeException.class)
    public void to_wkb_small_buffer_throws() {
        GeometryFactory.toWkb(new Point(0, 1, 2), ByteBuffer.allocate(1));
        fail();
    }

    @Test(expected = RuntimeException.class)
    public void to_blob_small_buffer_throws() {
        GeometryFactory.toSpatiaLiteBlob(new Point(0, 1, 2), 4326,
                ByteBuffer.allocate(1));
        fail();
    }

    @Test
    public void createEllipse() {
        final Geometry result = GeometryFactory.createEllipse(
                new Point(0, 1, 2), 1, 1, 1,
                GeometryFactory.Algorithm.cartesian.ordinal());
        assert (result != null);
        assertTrue(result instanceof Polygon);

        Polygon polygon = (Polygon) result;
        LineString lineString = polygon.getExteriorRing();
        assertEquals(362, lineString.getNumPoints());
        assertEquals(0.01745240643728351, lineString.getX(0), 0);
        assertEquals(1.9998476951563913, lineString.getY(0), 0);
        assertEquals(2.0, lineString.getZ(0), 0);

        assertEquals(0.10452846326765346, lineString.getX(5), 0);
        assertEquals(1.9945218953682733, lineString.getY(5), 0);
        assertEquals(2.0, lineString.getZ(5), 0);

        Envelope envelope = lineString.getEnvelope();
        assertEquals(-1, envelope.minX, 0);
        assertEquals(0, envelope.minY, 0);
        assertEquals(1, envelope.maxX, 0);
        assertEquals(2, envelope.maxY, 0);
    }

    @Test
    public void createEllipse2() {
        final Geometry result = GeometryFactory.createEllipse2(
                new Envelope(-1, -2, -3, 1, 2, 3),
                GeometryFactory.Algorithm.wgs84.ordinal());
        assert (result != null);
        assertTrue(result instanceof Polygon);

        Polygon polygon = (Polygon) result;
        LineString lineString = polygon.getExteriorRing();
        assertEquals(362, lineString.getNumPoints());
        assertEquals(-0.8743334798155771, lineString.getX(222), 0);
        assertEquals(-0.9709150725244005, lineString.getY(222), 0);
        assertEquals(0, lineString.getZ(222), 0);

        assertEquals(0, lineString.getX(361), 0.00000002);
        assertEquals(2, lineString.getY(361), 0.00000002);
        assertEquals(0, lineString.getZ(361), 0.00000002);

        Envelope envelope = lineString.getEnvelope();
        assertNotNull(envelope);
        assertEquals(-1, envelope.minX, 0);
        assertEquals(-2, envelope.minY, 0);
        assertEquals(1, envelope.maxX, 0);
        assertEquals(2, envelope.maxY, 0);
    }

    @Test
    public void extrude_point() {
        final Geometry result = GeometryFactory.extrude(new Point(0, 1, 2), 1,
                GeometryFactory.ExtrusionHints.TEEH_None);
        assertTrue(result instanceof LineString);
        LineString lineStringResults = (LineString) result;
        assertEquals(0, lineStringResults.getX(0), 0);
        assertEquals(1, lineStringResults.getY(0), 0);
        assertEquals(2, lineStringResults.getZ(0), 0);
        assertEquals(0, lineStringResults.getX(1), 0);
        assertEquals(1, lineStringResults.getY(1), 0);
        assertEquals(3, lineStringResults.getZ(1), 0);
    }

    @Test
    public void extrude_point_wrongdim() {
        final Geometry result = GeometryFactory.extrude(new Point(0, 1), 1,
                GeometryFactory.ExtrusionHints.TEEH_None);
        assertNull(result);
    }

    @Test
    public void extrude_point_invalidhints() {
        final Geometry result = GeometryFactory.extrude(new Point(0, 1, 2), 1,
                GeometryFactory.ExtrusionHints.TEEH_GeneratePolygons);
        assertNull(result);
    }

    @Test
    public void extrude_line_polygons() {
        LineString lineString = new LineString(3);
        lineString.addPoint(0, 1, 2);
        lineString.addPoint(1, 2, 3);
        final Geometry result = GeometryFactory.extrude(lineString, 1,
                GeometryFactory.ExtrusionHints.TEEH_GeneratePolygons);
        assertTrue(result instanceof Polygon);
        Polygon polygon = (Polygon) result;
        LineString lineStringResult = polygon.getExteriorRing();
        assertEquals(0, lineStringResult.getX(0), 0);
        assertEquals(1, lineStringResult.getY(0), 0);
        assertEquals(2, lineStringResult.getZ(0), 0);
    }

    @Test
    public void extrude_line_polygons_wrongdim() {
        LineString lineString = new LineString(2);
        lineString.addPoint(0, 1);
        final Geometry result = GeometryFactory.extrude(lineString, 1,
                GeometryFactory.ExtrusionHints.TEEH_GeneratePolygons);
        assertNull(result);
    }

    @Test
    public void extrude_line_collection() {
        LineString lineString = new LineString(3);
        lineString.addPoint(0, 1, 2);
        lineString.addPoint(1, 2, 3);
        lineString.addPoint(2, 3, 4);
        final Geometry result = GeometryFactory.extrude(lineString, 1,
                GeometryFactory.ExtrusionHints.TEEH_None);
        assertTrue(result instanceof GeometryCollection);
        GeometryCollection geometryCollection = (GeometryCollection) result;
        int size = geometryCollection.getGeometries().size();
        assert (size > 0);
        Iterator<Geometry> iterC = geometryCollection.getGeometries()
                .iterator();
        assertTrue(iterC.hasNext());
        Polygon polygonResult = (Polygon) iterC.next();

        LineString lineStringResult = polygonResult.getExteriorRing();
        assertEquals(0, lineStringResult.getX(0), 0);
        assertEquals(1, lineStringResult.getY(0), 0);
        assertEquals(2, lineStringResult.getZ(0), 0);
    }

    @Test
    public void extrude_line_collection_invalidhints() {
        LineString lineString = new LineString(3);
        lineString.addPoint(0, 1, 2);
        lineString.addPoint(1, 2, 3);
        lineString.addPoint(2, 3, 4);
        final Geometry result = GeometryFactory.extrude(lineString, -1,
                GeometryFactory.ExtrusionHints.TEEH_IncludeBottomFace);

        assertNull(result);
    }

    @Test
    public void extrude_line_collection_null() {
        LineString lineString = new LineString(3);
        lineString.addPoint(0, 1, 2);

        final Geometry result = GeometryFactory.extrude(lineString, -1,
                GeometryFactory.ExtrusionHints.TEEH_None);
        //expected bad result
        assertNull(result);
    }

    @Test
    public void extrude_polygon() {
        LineString lineString = new LineString(3);
        lineString.addPoint(0, 1, 2);
        lineString.addPoint(1, 2, 3);
        lineString.addPoint(2, 3, 4);

        LineString lineString2 = new LineString(3);
        lineString2.addPoint(0, 1, 2);
        lineString2.addPoint(1, 2, 3);
        lineString2.addPoint(2, 3, 4);

        LineString lineString3 = new LineString(3);
        lineString3.addPoint(0, 1, 2);
        lineString3.addPoint(1, 2, 3);
        lineString3.addPoint(2, 3, 4);

        Polygon polygon = new Polygon(lineString);
        polygon.addRing(lineString2);
        polygon.addRing(lineString3);

        final Geometry result = GeometryFactory.extrude(polygon, -1,
                GeometryFactory.ExtrusionHints.TEEH_None);
        assertTrue(result instanceof GeometryCollection);
        GeometryCollection geometryCollection = (GeometryCollection) result;
        int size = geometryCollection.getGeometries().size();
        assert (size > 0);

        Iterator<Geometry> iterC = geometryCollection.getGeometries()
                .iterator();
        assertTrue(iterC.hasNext());
        Polygon polygonResult = (Polygon) iterC.next();

        LineString exteriorRing = polygonResult.getExteriorRing();

        assertEquals(0, exteriorRing.getX(0), 0);
        assertEquals(1, exteriorRing.getY(0), 0);
        assertEquals(2, exteriorRing.getZ(0), 0);

        Polygon polygonResult2 = (Polygon) iterC.next();
        assertEquals(1, polygonResult2.getExteriorRing().getX(0), 0);
        assertEquals(2, polygonResult2.getExteriorRing().getY(0), 0);
        assertEquals(3, polygonResult2.getExteriorRing().getZ(0), 0);
    }

    @Test
    public void extrude_polygon_null() {
        LineString lineString = new LineString(3);
        lineString.addPoint(0, 1, 2);

        Polygon polygon = new Polygon(lineString);
        final Geometry result = GeometryFactory.extrude(polygon, -1,
                GeometryFactory.ExtrusionHints.TEEH_GeneratePolygons);

        //bad result
        assertNull(result);

    }

    @Test
    public void extrude_linestring_per_vertex() {
        LineString lineString = new LineString(3);
        lineString.addPoint(100, 200, 300);
        lineString.addPoint(500, 200, 300);

        final Geometry result = GeometryFactory.extrude(lineString,
                new double[] {
                        50d, -50d
                },
                GeometryFactory.ExtrusionHints.TEEH_None);

        assertNotNull(result);

        // we expect a GeometryCollection with one quad
        GeometryCollection c = new GeometryCollection(3);
        LineString ls = new LineString(3);
        ls.addPoint(100, 200, 300); // extruded point 1
        ls.addPoint(500, 200, 300); // extruded point 1
        ls.addPoint(500, 200, 250); // extruded point 1
        ls.addPoint(100, 200, 350); // extruded point 1
        ls.addPoint(100, 200, 300); // extruded point 1
        c.addGeometry(new Polygon(ls));

        AbstractGeometryTests.assertEqual(c, result);
    }

    @Test
    public void createRectangle() {
        final Geometry result = GeometryFactory.createRectangle(
                new Point(0, 1, 2), new Point(2, 3, 4),
                GeometryFactory.Algorithm.wgs84.ordinal());
        Polygon polygon = (Polygon) result;
        LineString lineString = polygon.getExteriorRing();

        assertEquals(0, lineString.getX(0), 0);
        assertEquals(1, lineString.getY(0), 0);
        assertEquals(4, lineString.getZ(0), 0);

        assertEquals(2, lineString.getX(1), 0);
        assertEquals(1, lineString.getY(1), 0);
        assertEquals(4, lineString.getZ(1), 0);

        assertEquals(2, lineString.getX(2), 0);
        assertEquals(3, lineString.getY(2), 0);
        assertEquals(4, lineString.getZ(2), 0);

        assertEquals(0, lineString.getX(3), 0);
        assertEquals(3, lineString.getY(3), 0);
        assertEquals(4, lineString.getZ(3), 0);

        assertEquals(0, lineString.getX(4), 0);
        assertEquals(1, lineString.getY(4), 0);
        assertEquals(4, lineString.getZ(4), 0);

        assertNotNull(result);
    }

    @Test
    public void createRectangle_bad() {
        final Geometry result = GeometryFactory.createRectangle(
                new Point(0, 1, 2), new Point(0, 1, 2),
                GeometryFactory.Algorithm.wgs84.ordinal());

        assertNull(result);
    }

    @Test
    public void createRectangle2() {
        final Geometry result = GeometryFactory.createRectangle(
                new Point(-.03, -.02, 1), new Point(.03, -.02, 1),
                new Point(0.0, 0.02, 1),
                GeometryFactory.Algorithm.cartesian.ordinal());
        Polygon polygon = (Polygon) result;
        LineString lineString = polygon.getExteriorRing();

        assertEquals(5, lineString.getNumPoints());

        assertEquals(-0.03, lineString.getX(0), 0);
        assertEquals(-0.02, lineString.getY(0), 0);
        assertEquals(1.0, lineString.getZ(0), 0);

        assertEquals(0.03, lineString.getX(1), 0);
        assertEquals(-0.02, lineString.getY(1), 0);
        assertEquals(1.0, lineString.getZ(1), 0);

        assertEquals(0.03, lineString.getX(2), 0);
        assertEquals(0.02, lineString.getY(2), 2e-3);
        assertEquals(1.0, lineString.getZ(2), 0);

        assertEquals(-0.03, lineString.getX(3), 0);
        assertEquals(0.02, lineString.getY(3), 2e-3);
        assertEquals(1.0, lineString.getZ(3), 0);

        assertEquals(-0.03, lineString.getX(4), 0);
        assertEquals(-0.02, lineString.getY(4), 0);
        assertEquals(1.0, lineString.getZ(4), 0);
    }

    @Test
    public void createRectangle3() {
        final Geometry result = GeometryFactory.createRectangle(
                new Point(0, 1, 2), 12, 13, 10,
                GeometryFactory.Algorithm.wgs84.ordinal());
        Polygon polygon = (Polygon) result;
        LineString lineString = polygon.getExteriorRing();

        assertEquals(-5.608285380740403E-5, lineString.getX(0), 0);
        assertEquals(0.999933546966112, lineString.getY(0), 0);
        assertEquals(2, lineString.getZ(0), 0);

        assertEquals(3.179902348572112E-5, lineString.getX(1), 0);
        assertEquals(0.9999522239910728, lineString.getY(1), 0);
        assertEquals(2, lineString.getZ(1), 0);

        assertEquals(5.608285600909064E-5, lineString.getX(2), 0);
        assertEquals(1.0000664530332553, lineString.getY(2), 0);
        assertEquals(2, lineString.getZ(2), 0);

        assertEquals(-3.179902434229664E-5, lineString.getX(3), 0);
        assertEquals(1.0000477760082942, lineString.getY(3), 0);
        assertEquals(2, lineString.getZ(3), 0);

        assertEquals(-5.608285380740403E-5, lineString.getX(4), 0);
        assertEquals(0.9999335469661117, lineString.getY(4), 0);
        assertEquals(2, lineString.getZ(4), 0);
        assertNotNull(result);
    }
}
