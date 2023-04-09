
package com.atakmap.map.layer.feature;

import com.atakmap.android.androidtest.util.RandomUtils;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.Point;

import org.junit.Assert;
import org.junit.Test;

import java.util.Random;

public class PointTests extends AbstractGeometryTests {

    @Override
    Geometry createTestGeometry(int dimension) {
        if (dimension == 2)
            return new Point(0, 0);
        else if (dimension == 3)
            return new Point(0, 0, 0);
        else
            throw new IllegalArgumentException();
    }

    @Override
    Geometry createRandomGeometry(int dimension) {
        return randomPoint(dimension);
    }

    @Test
    public void Point_2d_constructor_roundtrip() {
        Random r = RandomUtils.rng();
        final double x = r.nextDouble();
        final double y = r.nextDouble();
        Point p = new Point(x, y);
        Assert.assertEquals(x, p.getX(), 0.0);
        Assert.assertEquals(y, p.getY(), 0.0);
    }

    @Test
    public void Point_3d_constructor_roundtrip() {
        Random r = RandomUtils.rng();
        final double x = r.nextDouble();
        final double y = r.nextDouble();
        final double z = r.nextDouble();
        Point p = new Point(x, y, z);
        Assert.assertEquals(x, p.getX(), 0.0);
        Assert.assertEquals(y, p.getY(), 0.0);
        Assert.assertEquals(z, p.getZ(), 0.0);
    }

    // data model tests

    @Test
    public void Point_set_xy_roundtrip() {
        Random r = RandomUtils.rng();
        final double x = r.nextDouble();
        final double y = r.nextDouble();
        Point p = new Point(0, 0, 0);
        p.set(x, y);
        Assert.assertEquals(x, p.getX(), 0.0);
        Assert.assertEquals(y, p.getY(), 0.0);
    }

    @Test
    public void Point_set_xyz_roundtrip() {
        Random r = RandomUtils.rng();
        final double x = r.nextDouble();
        final double y = r.nextDouble();
        final double z = r.nextDouble();
        Point p = new Point(0, 0, 0);
        p.set(x, y, z);
        Assert.assertEquals(x, p.getX(), 0.0);
        Assert.assertEquals(y, p.getY(), 0.0);
        Assert.assertEquals(z, p.getZ(), 0.0);
    }

    // envelope tests

    @Test
    public void Point_3d_envelope() {
        Random r = RandomUtils.rng();
        final double x = r.nextDouble();
        final double y = r.nextDouble();
        final double z = r.nextDouble();
        Point p = new Point(x, y, z);
        Envelope expected = new Envelope(x, y, z, x, y, z);
        envelope_test(p, expected);
    }

    @Test
    public void Point_2d_envelope() {
        Random r = RandomUtils.rng();
        final double x = r.nextDouble();
        final double y = r.nextDouble();
        Point p = new Point(x, y);
        Envelope expected = new Envelope(x, y, 0d, x, y, 0d);
        envelope_test(p, expected);
    }

    // wkb tests
}
