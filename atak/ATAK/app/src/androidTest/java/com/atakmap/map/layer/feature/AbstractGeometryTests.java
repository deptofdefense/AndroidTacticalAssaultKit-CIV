
package com.atakmap.map.layer.feature;

import com.atakmap.android.androidtest.util.RandomUtils;
import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;

import org.gdal.ogr.ogrConstants;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

public abstract class AbstractGeometryTests extends ATAKInstrumentedTest {

    abstract Geometry createTestGeometry(int dimension);

    abstract Geometry createRandomGeometry(int dimension);

    org.gdal.ogr.Geometry toOgr(Geometry geom) {
        if (geom == null)
            return null;
        if (geom instanceof Point) {
            final Point point = (Point) geom;
            org.gdal.ogr.Geometry ogr = new org.gdal.ogr.Geometry(
                    ogrConstants.wkbPoint);
            if (geom.getDimension() == 2) {
                ogr.SetPoint_2D(0, point.getX(), point.getY());
            } else {
                ogr.Set3D(1);
                ogr.SetPoint(0, point.getX(), point.getY(), point.getZ());
            }
            return ogr;
        } else if (geom instanceof LineString) {
            return toOgr((LineString) geom, ogrConstants.wkbLineString);
        } else if (geom instanceof Polygon) {
            final Polygon linestring = (Polygon) geom;
            org.gdal.ogr.Geometry ogr = new org.gdal.ogr.Geometry(
                    ogrConstants.wkbPolygon);
            if (geom.getDimension() == 3)
                ogr.Set3D(1);

            ogr.AddGeometry(toOgr(linestring.getExteriorRing(),
                    ogrConstants.wkbLinearRing));
            for (LineString ring : linestring.getInteriorRings())
                ogr.AddGeometry(toOgr(ring, ogrConstants.wkbLinearRing));
            return ogr;
        } else if (geom instanceof GeometryCollection) {
            final GeometryCollection linestring = (GeometryCollection) geom;
            org.gdal.ogr.Geometry ogr = new org.gdal.ogr.Geometry(
                    ogrConstants.wkbGeometryCollection);
            if (geom.getDimension() == 3)
                ogr.Set3D(1);

            for (Geometry ring : linestring.getGeometries())
                ogr.AddGeometry(toOgr(ring));
            return ogr;
        } else {
            throw new IllegalStateException();
        }
    }

    org.gdal.ogr.Geometry toOgr(LineString linestring, int rep) {
        org.gdal.ogr.Geometry ogr = new org.gdal.ogr.Geometry(rep);
        if (linestring.getDimension() == 2) {
            for (int i = 0; i < linestring.getNumPoints(); i++)
                ogr.AddPoint_2D(linestring.getX(i), linestring.getY(i));
        } else {
            ogr.Set3D(1);
            for (int i = 0; i < linestring.getNumPoints(); i++)
                ogr.AddPoint(linestring.getX(i), linestring.getY(i),
                        linestring.getZ(i));
        }
        return ogr;
    }

    // constructor tests [simple]

    @Test
    public void Geometry_2d_constructor() {
        Geometry g = createTestGeometry(2);
        Assert.assertNotNull(g);
    }

    @Test
    public void Geometry_3d_constructor() {
        Geometry g = createTestGeometry(3);
        Assert.assertNotNull(g);
    }

    @Test
    public void Geometry_2d_constructor_dimension_check() {
        Geometry p = createTestGeometry(2);
        Assert.assertEquals(p.getDimension(), 2);
    }

    @Test
    public void Geometry_3d_constructor_dimension_check() {
        Geometry p = createTestGeometry(3);
        Assert.assertEquals(p.getDimension(), 3);
    }

    @Test
    public void Geometry_2d_clone_roundtrip_test() {
        Geometry p = createTestGeometry(2);
        Geometry c = p.clone();
        Assert.assertEquals(p, c);
    }

    @Test
    public void Geometry_3d_clone_roundtrip_test() {
        Geometry p = createTestGeometry(3);
        Geometry c = p.clone();
        Assert.assertEquals(p, c);
    }

    // envelope test support

    void envelope_test(Geometry g, Envelope truth) {
        Envelope test = g.getEnvelope();
        Assert.assertNotNull(test);
        Assert.assertEquals(truth.minX, test.minX, 0.0);
        Assert.assertEquals(truth.maxX, test.maxX, 0.0);
        Assert.assertEquals(truth.minY, test.minY, 0.0);
        Assert.assertEquals(truth.maxY, test.maxY, 0.0);
        Assert.assertEquals(truth.minZ, test.minZ, 0.0);
        Assert.assertEquals(truth.maxZ, test.maxZ, 0.0);
    }

    // wkb test support

    void wkb_size_test(Geometry g, int truth) {
        Assert.assertEquals(g.computeWkbSize(), truth);
    }

    void wkb_encode_contents_test(Geometry g, byte[] truth, ByteOrder endian) {
        byte[] test = new byte[g.computeWkbSize()];
        g.toWkb(ByteBuffer.wrap(test).order(endian));
        Assert.assertTrue(Arrays.equals(test, truth));
    }

    void wkb_encode_size_test(Geometry g, ByteOrder endian) {
        final int computeSize = g.computeWkbSize();
        byte[] test = new byte[computeSize];
        ByteBuffer buf = ByteBuffer.wrap(test).order(endian);
        Assert.assertEquals(computeSize, buf.limit());
    }

    @Test
    public void Geometry_2d_wkb_roundtrip_LE() {
        Geometry g = this.createRandomGeometry(2);
        org.gdal.ogr.Geometry ogr = toOgr(g);
        byte[] truth = ogr.ExportToIsoWkb(ogrConstants.wkbNDR);
        wkb_encode_contents_test(g, truth, ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void Geometry_2d_wkb_roundtrip_BE() {
        Geometry g = this.createRandomGeometry(2);
        org.gdal.ogr.Geometry ogr = toOgr(g);
        byte[] truth = ogr.ExportToIsoWkb(ogrConstants.wkbXDR);
        wkb_encode_contents_test(g, truth, ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void Geometry_3d_wkb_roundtrip_LE() {
        Geometry g = this.createRandomGeometry(3);
        org.gdal.ogr.Geometry ogr = toOgr(g);
        byte[] truth = ogr.ExportToIsoWkb(ogrConstants.wkbNDR);
        wkb_encode_contents_test(g, truth, ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void Geometry_3d_wkb_roundtrip_BE() {
        Geometry g = this.createRandomGeometry(3);
        org.gdal.ogr.Geometry ogr = toOgr(g);
        byte[] truth = ogr.ExportToIsoWkb(ogrConstants.wkbXDR);
        wkb_encode_contents_test(g, truth, ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void Geometry_2d_wkb_computeSize_equals_encode_size_LE() {
        Geometry g = this.createRandomGeometry(2);
        wkb_encode_size_test(g, ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void Geometry_2d_wkb_computeSize_equals_encode_size_BE() {
        Geometry g = this.createRandomGeometry(2);
        wkb_encode_size_test(g, ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void Geometry_3d_wkb_computeSize_equals_encode_size_LE() {
        Geometry g = this.createRandomGeometry(3);
        wkb_encode_size_test(g, ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void Geometry_3d_wkb_computeSize_equals_encode_size_BE() {
        Geometry g = this.createRandomGeometry(3);
        wkb_encode_size_test(g, ByteOrder.BIG_ENDIAN);
    }

    @Test
    public void Geometry_2d_wkb_computeSize() {
        Geometry g = this.createRandomGeometry(2);
        org.gdal.ogr.Geometry ogr = toOgr(g);
        byte[] truth = ogr.ExportToIsoWkb(ogrConstants.wkbNDR);
        Assert.assertEquals(truth.length, g.computeWkbSize());
    }

    @Test
    public void Geometry_3d_wkb_computeSize() {
        Geometry g = this.createRandomGeometry(3);
        org.gdal.ogr.Geometry ogr = toOgr(g);
        byte[] truth = ogr.ExportToIsoWkb(ogrConstants.wkbNDR);
        Assert.assertEquals(truth.length, g.computeWkbSize());
    }

    // dimension tests

    @Test
    public void Geometry_set_valid_dimension() {
        Geometry g = createTestGeometry(2);

        g.setDimension(3);
        Assert.assertEquals(g.getDimension(), 3);
        g.setDimension(2);
        Assert.assertEquals(g.getDimension(), 2);
    }

    /**
     * This method expects to throw {@link IllegalArgumentException}
     */
    @Test(expected = IllegalArgumentException.class)
    public void Geometry_set_invalid_dimension_throws() {
        Geometry g = createTestGeometry(2);
        g.setDimension(4);
        Assert.fail();
    }

    static boolean largeRandomGeometries = false;

    static LineString randomLineString(int dimension, boolean closed) {
        return randomLineString(dimension, closed ? 3 : 2,
                largeRandomGeometries ? 25000 : 10, closed);
    }

    static LineString randomLineString(int dimension, int numPoints,
            boolean closed) {
        if (closed && numPoints < 3)
            throw new IllegalArgumentException();

        final int pointsDim = dimension;
        double[] xy = RandomUtils.randomDoubleArray(numPoints * pointsDim);
        if (!closed && arrayRegionsEqual(xy, 0, xy, (numPoints - 1) * pointsDim,
                pointsDim)) {
            for (int i = 0; i < pointsDim; i++)
                xy[(numPoints - 1) * pointsDim + i] = not(
                        xy[(numPoints - 1) * pointsDim + i]);
        }

        LineString linestring = new LineString(dimension);
        linestring.addPoints(xy, 0, numPoints, pointsDim);
        if (closed) {
            if (dimension == 2)
                linestring.addPoint(linestring.getX(0), linestring.getY(0));
            else if (dimension == 3)
                linestring.addPoint(linestring.getX(0), linestring.getY(0),
                        linestring.getZ(0));
            else
                throw new IllegalStateException();
        }
        return linestring;
    }

    static LineString randomLineString(int dimension, int min, int max,
            boolean closed) {
        Random r = RandomUtils.rng();
        if (closed && min < 3)
            throw new IllegalArgumentException();
        final int numPoints = min + r.nextInt(max - min + 1);
        return randomLineString(dimension, numPoints, closed);
    }

    static Point randomPoint(int dimension) {
        final Random r = RandomUtils.rng();
        if (dimension == 2)
            return new Point(r.nextDouble(), r.nextDouble());
        else if (dimension == 3)
            return new Point(r.nextDouble(), r.nextDouble(), r.nextDouble());
        else
            throw new IllegalArgumentException();
    }

    static Polygon randomPolygon(int dimension) {
        LineString ext = randomLineString(dimension, true);
        if (RandomUtils.rng().nextBoolean())
            return new Polygon(ext);
        final int numIntRings = RandomUtils.rng()
                .nextInt(largeRandomGeometries ? 100 : 10);
        ArrayList<LineString> intRings = new ArrayList<LineString>(numIntRings);
        intRings.add(randomLineString(dimension, true));
        return new Polygon(ext, intRings);
    }

    static GeometryCollection randomGeometryCollection(int dimension,
            Collection<Class<? extends Geometry>> types, int maxNestingDepth) {
        final int numPoints = types.contains(Point.class)
                ? RandomUtils.rng().nextInt(largeRandomGeometries ? 10000 : 10)
                        + 1
                : 0;
        final int numLinestrings = types.contains(LineString.class)
                ? RandomUtils.rng().nextInt(largeRandomGeometries ? 100 : 10)
                        + 1
                : 0;
        final int numPolygons = types.contains(Polygon.class)
                ? RandomUtils.rng().nextInt(largeRandomGeometries ? 100 : 10)
                        + 1
                : 0;
        final int numNested = (maxNestingDepth > 0
                && types.contains(GeometryCollection.class))
                        ? RandomUtils.rng()
                                .nextInt(largeRandomGeometries ? 100 : 10) + 1
                        : 0;

        GeometryCollection retval = new GeometryCollection(dimension);
        for (int i = 0; i < numPoints; i++)
            retval.addGeometry(randomPoint(dimension));
        for (int i = 0; i < numLinestrings; i++)
            retval.addGeometry(randomLineString(dimension, false));
        for (int i = 0; i < numPolygons; i++)
            retval.addGeometry(randomPolygon(dimension));
        for (int i = 0; i < numNested; i++)
            retval.addGeometry(randomGeometryCollection(dimension, types,
                    maxNestingDepth - 1));

        return retval;
    }

    static GeometryCollection randomGeometryCollection(int dimension) {
        return randomGeometryCollection(dimension,
                Arrays.<Class<? extends Geometry>> asList(new Class[] {
                        Point.class, LineString.class, Polygon.class,
                        GeometryCollection.class
                }), 2);
    }

    static Geometry randomGeometry() {
        int geomType = RandomUtils.rng().nextInt(4);
        if (geomType == 0)
            return randomLineString();
        else if (geomType == 1)
            return AbstractGeometryTests.randomPoint();
        else if (geomType == 2)
            return AbstractGeometryTests.randomPolygon();
        else if (geomType == 3)
            return AbstractGeometryTests.randomGeometryCollection();
        else
            throw new IllegalStateException();
    }

    static Geometry randomLineString() {
        int dim = RandomUtils.rng().nextBoolean() ? 2 : 3;
        return AbstractGeometryTests.randomLineString(dim, false);
    }

    static Geometry randomPolygon() {
        int dim = RandomUtils.rng().nextBoolean() ? 2 : 3;
        return AbstractGeometryTests.randomPolygon(dim);
    }

    static Geometry randomPoint() {
        int dim = RandomUtils.rng().nextBoolean() ? 2 : 3;
        return AbstractGeometryTests.randomPoint(dim);
    }

    static Geometry randomGeometryCollection() {
        int dim = RandomUtils.rng().nextBoolean() ? 2 : 3;
        return AbstractGeometryTests.randomGeometryCollection(dim);
    }

    static boolean arrayRegionsEqual(double[] a, int aOff, double[] b, int bOff,
            int len) {
        for (int i = 0; i < len; i++)
            if (a[aOff + i] != b[bOff + i])
                return false;
        return true;
    }

    static double not(double v) {
        return Double.longBitsToDouble(~Double.doubleToLongBits(v));
    }

    static boolean equals(Geometry a, Geometry b) {
        if (a == null && b == null)
            return true;
        else if (a == null)
            return false;
        else if (b == null)
            return false;

        if (a.getDimension() != b.getDimension())
            return false;

        boolean aispoint = a instanceof Point;
        boolean bispoint = b instanceof Point;
        boolean aisline = a instanceof LineString;
        boolean bisline = b instanceof LineString;
        boolean aispoly = a instanceof Polygon;
        boolean bispoly = b instanceof Polygon;
        boolean aiscoll = a instanceof GeometryCollection;
        boolean biscoll = b instanceof GeometryCollection;

        if (aispoint && bispoint)
            return equalsImpl((Point) a, (Point) b);
        else if (aisline && bisline)
            return equalsImpl((LineString) a, (LineString) b);
        else if (aispoly && bispoly)
            return equalsImpl((Polygon) a, (Polygon) b);
        else if (aiscoll && biscoll)
            return equalsImpl((GeometryCollection) a, (GeometryCollection) b);
        else
            return (a == b);
    }

    static boolean equalsImpl(Point a, Point b) {
        if (a.getX() != b.getX())
            return false;
        if (a.getY() != b.getY())
            return false;
        if (a.getDimension() == 2)
            return true;
        return a.getZ() == b.getZ();
    }

    static boolean equalsImpl(LineString a, LineString b) {
        final int numPoints = a.getNumPoints();
        if (numPoints != b.getNumPoints())
            return false;
        for (int i = 0; i < numPoints; i++) {
            if (b.getX(i) != a.getX(i))
                return false;
            if (b.getY(i) != b.getY(i))
                return false;
            if (a.getDimension() == 2)
                continue;
            final double z = a.getZ(i);
            if (b.getZ(i) != a.getZ(i))
                return false;
        }

        return true;
    }

    static boolean equalsImpl(Polygon a, Polygon b) {
        if (!equals(a.getExteriorRing(), b.getExteriorRing()))
            return false;

        Collection<LineString> ainner = a.getInteriorRings();
        Collection<LineString> binner = b.getInteriorRings();

        if (ainner.size() != binner.size())
            return false;

        for (LineString aa : ainner) {
            boolean found = false;
            for (LineString bb : binner) {
                if (found)
                    break;
                found |= equals(aa, bb);
            }
            if (!found)
                return false;
        }

        return true;
    }

    static boolean equalsImpl(GeometryCollection a, GeometryCollection b) {
        Collection<Geometry> ainner = a.getGeometries();
        Collection<Geometry> binner = b.getGeometries();

        if (ainner.size() != binner.size())
            return false;

        for (Geometry aa : ainner) {
            boolean found = false;
            for (Geometry bb : binner) {
                if (found)
                    break;
                found |= equals(aa, bb);
            }
            if (!found)
                return false;
        }

        return true;
    }

    static void assertEqual(Geometry a, Geometry b) {
        if (a == null && b == null)
            return;

        Assert.assertTrue(a != null);
        Assert.assertTrue(b != null);

        Assert.assertEquals(a.getDimension(), b.getDimension());

        boolean aispoint = a instanceof Point;
        boolean bispoint = b instanceof Point;
        boolean aisline = a instanceof LineString;
        boolean bisline = b instanceof LineString;
        boolean aispoly = a instanceof Polygon;
        boolean bispoly = b instanceof Polygon;
        boolean aiscoll = a instanceof GeometryCollection;
        boolean biscoll = b instanceof GeometryCollection;

        if (aispoint && bispoint)
            assertEqualsImpl((Point) a, (Point) b);
        else if (aisline && bisline)
            assertEqualsImpl((LineString) a, (LineString) b);
        else if (aispoly && bispoly)
            assertEqualsImpl((Polygon) a, (Polygon) b);
        else if (aiscoll && biscoll)
            assertEqualsImpl((GeometryCollection) a, (GeometryCollection) b);
        else
            Assert.assertSame(a, b);
    }

    static void assertEqualsImpl(Point a, Point b) {
        Assert.assertTrue(a.getX() == b.getX());
        Assert.assertTrue(a.getY() == b.getY());
        if (a.getDimension() == 3)
            Assert.assertTrue(a.getZ() == b.getZ());
    }

    static void assertEqualsImpl(LineString a, LineString b) {
        final int numPoints = a.getNumPoints();
        Assert.assertEquals(numPoints, b.getNumPoints());

        for (int i = 0; i < numPoints; i++) {
            Assert.assertTrue(a.getX(i) == b.getX(i));
            Assert.assertTrue(a.getY(i) == b.getY(i));
            if (a.getDimension() == 3)
                Assert.assertTrue(a.getZ(i) == b.getZ(i));
        }
    }

    static void assertEqualsImpl(Polygon a, Polygon b) {
        assertEqual(a.getExteriorRing(), b.getExteriorRing());

        Collection<LineString> ainner = a.getInteriorRings();
        Collection<LineString> binner = b.getInteriorRings();

        Assert.assertEquals(ainner.size(), binner.size());

        for (LineString aa : ainner) {
            boolean found = false;
            for (LineString bb : binner) {
                if (found)
                    break;
                found |= equals(aa, bb);
            }
            Assert.assertTrue(found);
        }
    }

    static void assertEqualsImpl(GeometryCollection a, GeometryCollection b) {
        Collection<Geometry> ainner = a.getGeometries();
        Collection<Geometry> binner = b.getGeometries();

        Assert.assertEquals(ainner.size(), binner.size());

        for (Geometry aa : ainner) {
            boolean found = false;
            for (Geometry bb : binner) {
                if (found)
                    break;
                found |= equals(aa, bb);
            }
            Assert.assertTrue(found);
        }
    }
}
