
package com.atakmap.map.layer.feature;

import com.atakmap.android.androidtest.util.RandomUtils;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;

import org.junit.Assert;
import org.junit.Test;

import java.util.Random;

public class LineStringTests extends AbstractGeometryTests {
    @Override
    Geometry createTestGeometry(int dimension) {
        if (dimension == 2)
            return new LineString(2);
        else if (dimension == 3)
            return new LineString(3);
        else
            throw new IllegalArgumentException();
    }

    @Override
    Geometry createRandomGeometry(int dimension) {
        return randomLineString(dimension, false);
    }

    // constructor tests

    // *** covered by base

    // data model tests

    @Test(expected = IndexOutOfBoundsException.class)
    public void LineString_2d_empty_get_x_out_of_bounds_throws() {
        LineString linestring = new LineString(2);
        final double x = linestring.getX(0);
        Assert.fail();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void LineString_2d_empty_get_y_out_of_bounds_throws() {
        LineString linestring = new LineString(2);
        final double y = linestring.getY(0);
        Assert.fail();
    }

    @Test(expected = IllegalStateException.class)
    public void LineString_2d_empty_get_z_out_of_bounds_throws() {
        LineString linestring = new LineString(2);
        final double z = linestring.getZ(0);
        Assert.fail();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void LineString_3d_empty_get_x_out_of_bounds_throws() {
        LineString linestring = new LineString(3);
        final double x = linestring.getX(0);
        Assert.fail();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void LineString_3d_empty_get_y_out_of_bounds_throws() {
        LineString linestring = new LineString(3);
        final double y = linestring.getY(0);
        Assert.fail();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void LineString_3d_empty_get_z_out_of_bounds_throws() {
        LineString linestring = new LineString(3);
        final double z = linestring.getZ(0);
        Assert.fail();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void LineString_2d_get_x_out_of_bounds_throws() {
        Random r = RandomUtils.rng();
        final int dimension = 2;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);
        linestring.addPoints(xy, 0, numPoints, dimension);
        final double x = linestring.getX(numPoints);
        Assert.fail();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void LineString_2d_get_y_out_of_bounds_throws() {
        Random r = RandomUtils.rng();
        final int dimension = 2;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);
        linestring.addPoints(xy, 0, numPoints, dimension);
        final double y = linestring.getY(numPoints);
        Assert.fail();
    }

    @Test(expected = RuntimeException.class)
    public void LineString_2d_get_z_out_of_bounds_throws() {
        Random r = RandomUtils.rng();
        final int dimension = 2;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);
        linestring.addPoints(xy, 0, numPoints, dimension);
        final double z = linestring.getZ(numPoints);
        Assert.fail();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void LineString_3d_get_x_out_of_bounds_throws() {
        Random r = RandomUtils.rng();
        final int dimension = 3;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);
        linestring.addPoints(xy, 0, numPoints, dimension);

        final double x = linestring.getX(numPoints);
        Assert.fail();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void LineString_3d_get_y_out_of_bounds_throws() {
        Random r = RandomUtils.rng();
        final int dimension = 3;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);

        linestring.addPoints(xy, 0, numPoints, dimension);
        final double y = linestring.getY(numPoints);
        Assert.fail();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void LineString_3d_get_z_out_of_bounds_throws() {
        Random r = RandomUtils.rng();
        final int dimension = 3;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);

        linestring.addPoints(xy, 0, numPoints, dimension);
        final double z = linestring.getZ(numPoints);
        Assert.fail();
    }

    @Test
    public void LineString_2d_add_point_xy_roundtrip() {
        double[] xy = RandomUtils.randomDoubleArray(2);
        LineString linestring = new LineString(2);
        linestring.addPoint(xy[0], xy[1]);

        Assert.assertTrue(xy[0] == linestring.getX(0));
        Assert.assertTrue(xy[1] == linestring.getY(0));
    }

    @Test
    public void LineString_3d_add_point_xy_roundtrip() {
        double[] xyz = RandomUtils.randomDoubleArray(2);
        LineString linestring = new LineString(3);
        linestring.addPoint(xyz[0], xyz[1]);

        Assert.assertTrue(xyz[0] == linestring.getX(0));
        Assert.assertTrue(xyz[1] == linestring.getY(0));
        Assert.assertTrue(0d == linestring.getZ(0));
    }

    @Test(expected = IllegalStateException.class)
    public void LineString_2d_add_point_xyz_throws() {
        double[] xy = RandomUtils.randomDoubleArray(3);
        LineString linestring = new LineString(2);
        linestring.addPoint(xy[0], xy[1], xy[2]);

        Assert.fail();
    }

    @Test
    public void LineString_3d_add_point_xyz_roundtrip() {
        double[] xyz = RandomUtils.randomDoubleArray(3);
        LineString linestring = new LineString(3);
        linestring.addPoint(xyz[0], xyz[1], xyz[2]);

        Assert.assertTrue(xyz[0] == linestring.getX(0));
        Assert.assertTrue(xyz[1] == linestring.getY(0));
        Assert.assertTrue(xyz[2] == linestring.getZ(0));
    }

    @Test
    public void LineString_2d_add_points_xy_roundtrip() {
        Random r = RandomUtils.rng();
        final int numPoints = 10 + r.nextInt(11);

        double[] xy = RandomUtils.randomDoubleArray(numPoints * 2);
        LineString linestring = new LineString(2);
        for (int i = 0; i < numPoints; i++)
            linestring.addPoint(xy[i * 2], xy[i * 2 + 1]);

        for (int i = 0; i < numPoints; i++) {
            Assert.assertTrue(xy[i * 2] == linestring.getX(i));
            Assert.assertTrue(xy[i * 2 + 1] == linestring.getY(i));
        }
    }

    @Test
    public void LineString_3d_add_points_xy_roundtrip() {
        Random r = RandomUtils.rng();
        final int numPoints = 10 + r.nextInt(11);

        double[] xy = RandomUtils.randomDoubleArray(numPoints * 2);
        LineString linestring = new LineString(3);
        for (int i = 0; i < numPoints; i++)
            linestring.addPoint(xy[i * 2], xy[i * 2 + 1]);

        for (int i = 0; i < numPoints; i++) {
            Assert.assertTrue(xy[i * 2] == linestring.getX(i));
            Assert.assertTrue(xy[i * 2 + 1] == linestring.getY(i));
            Assert.assertTrue(0d == linestring.getZ(i));
        }
    }

    @Test(expected = IllegalStateException.class)
    public void LineString_2d_add_points_xyz_throws() {
        Random r = RandomUtils.rng();
        final int numPoints = 10 + r.nextInt(11);

        double[] xyz = RandomUtils.randomDoubleArray(numPoints * 3);
        LineString linestring = new LineString(2);
        for (int i = 0; i < numPoints; i++)
            linestring.addPoint(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 1]);

        Assert.fail();
    }

    @Test
    public void LineString_3d_add_points_xyz_roundtrip() {
        Random r = RandomUtils.rng();
        final int numPoints = 10 + r.nextInt(11);

        double[] xyz = RandomUtils.randomDoubleArray(numPoints * 3);
        LineString linestring = new LineString(3);
        for (int i = 0; i < numPoints; i++)
            linestring.addPoint(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]);

        for (int i = 0; i < numPoints; i++) {
            Assert.assertTrue(xyz[i * 3] == linestring.getX(i));
            Assert.assertTrue(xyz[i * 3 + 1] == linestring.getY(i));
            Assert.assertTrue(xyz[i * 3 + 2] == linestring.getZ(i));
        }
    }

    @Test
    public void LineString_2d_add_points_xy_point_count() {
        Random r = RandomUtils.rng();
        final int numPoints = 10 + r.nextInt(11);

        double[] xy = RandomUtils.randomDoubleArray(numPoints * 2);
        LineString linestring = new LineString(2);
        for (int i = 0; i < numPoints; i++)
            linestring.addPoint(xy[i * 2], xy[i * 2 + 1]);

        Assert.assertEquals(numPoints, linestring.getNumPoints());
    }

    @Test
    public void LineString_3d_add_points_xy_point_count() {
        Random r = RandomUtils.rng();
        final int numPoints = 10 + r.nextInt(11);

        double[] xy = RandomUtils.randomDoubleArray(numPoints * 2);
        LineString linestring = new LineString(2);
        for (int i = 0; i < numPoints; i++)
            linestring.addPoint(xy[i * 2], xy[i * 2 + 1]);

        Assert.assertEquals(numPoints, linestring.getNumPoints());
    }

    @Test(expected = IllegalStateException.class)
    public void LineString_2d_add_points_xyz_point_count() {
        Random r = RandomUtils.rng();
        final int numPoints = 10 + r.nextInt(11);

        double[] xyz = RandomUtils.randomDoubleArray(numPoints * 3);
        LineString linestring = new LineString(2);
        for (int i = 0; i < numPoints; i++)
            linestring.addPoint(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]);

        Assert.fail();
    }

    @Test
    public void LineString_3d_add_points_xyz_point_count() {
        Random r = RandomUtils.rng();
        final int numPoints = 10 + r.nextInt(11);

        double[] xyz = RandomUtils.randomDoubleArray(numPoints * 3);
        LineString linestring = new LineString(3);
        for (int i = 0; i < numPoints; i++)
            linestring.addPoint(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]);

        Assert.assertEquals(numPoints, linestring.getNumPoints());
    }

    // bulk add points roundtrip
    // set point valid index
    @Test
    public void LineString_2d_set_x_roundtrip() {
        Random r = RandomUtils.rng();
        final int dimension = 2;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);
        linestring.addPoints(xy, 0, numPoints, dimension);
        final int index = numPoints / 2;
        final double oldvalue = linestring.getX(index);
        final double newvalue = Double
                .longBitsToDouble(~Double.doubleToLongBits(oldvalue));
        linestring.setX(index, newvalue);
        Assert.assertTrue(linestring.getX(index) == newvalue);
    }

    @Test
    public void LineString_2d_set_y_roundtrip() {
        Random r = RandomUtils.rng();
        final int dimension = 2;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);
        linestring.addPoints(xy, 0, numPoints, dimension);
        final int index = numPoints / 2;
        final double oldvalue = linestring.getY(index);
        final double newvalue = Double
                .longBitsToDouble(~Double.doubleToLongBits(oldvalue));
        linestring.setY(index, newvalue);
        Assert.assertTrue(linestring.getY(index) == newvalue);
    }

    @Test(expected = IllegalStateException.class)
    public void LineString_2d_set_z_roundtrip_throws() {
        Random r = RandomUtils.rng();
        final int dimension = 2;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);
        linestring.addPoints(xy, 0, numPoints, dimension);
        final int index = numPoints / 2;
        final double oldvalue = linestring.getZ(numPoints);
        final double newvalue = Double
                .longBitsToDouble(~Double.doubleToLongBits(oldvalue));
        linestring.setZ(index, newvalue);
        Assert.assertTrue(linestring.getZ(index) == newvalue);
    }

    @Test
    public void LineString_3d_set_x_roundtrip() {
        Random r = RandomUtils.rng();
        final int dimension = 3;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);
        linestring.addPoints(xy, 0, numPoints, dimension);
        final int index = numPoints / 2;
        final double oldvalue = linestring.getX(index);
        final double newvalue = Double
                .longBitsToDouble(~Double.doubleToLongBits(oldvalue));
        linestring.setX(index, newvalue);
        Assert.assertTrue(linestring.getX(index) == newvalue);
    }

    @Test
    public void LineString_3d_set_y_roundtrip() {
        Random r = RandomUtils.rng();
        final int dimension = 3;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);

        linestring.addPoints(xy, 0, numPoints, dimension);
        final int index = numPoints / 2;
        final double oldvalue = linestring.getY(index);
        final double newvalue = Double
                .longBitsToDouble(~Double.doubleToLongBits(oldvalue));
        linestring.setY(index, newvalue);
        Assert.assertTrue(linestring.getY(index) == newvalue);
    }

    @Test
    public void LineString_3d_set_z_roundtrip() {
        Random r = RandomUtils.rng();
        final int dimension = 3;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);

        linestring.addPoints(xy, 0, numPoints, dimension);
        final int index = numPoints / 2;
        final double oldvalue = linestring.getZ(index);
        final double newvalue = Double
                .longBitsToDouble(~Double.doubleToLongBits(oldvalue));
        linestring.setZ(index, newvalue);
        Assert.assertTrue(linestring.getZ(index) == newvalue);
    }

    // set point invalid index
    @Test(expected = IndexOutOfBoundsException.class)
    public void LineString_2d_set_x_out_of_bounds_throws() {
        Random r = RandomUtils.rng();
        final int dimension = 2;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);
        linestring.addPoints(xy, 0, numPoints, dimension);
        linestring.setX(numPoints, 0d);
        Assert.fail();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void LineString_2d_set_y_out_of_bounds_throws() {
        Random r = RandomUtils.rng();
        final int dimension = 2;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);
        linestring.addPoints(xy, 0, numPoints, dimension);
        linestring.setY(numPoints, 0d);
        Assert.fail();
    }

    @Test(expected = RuntimeException.class)
    public void LineString_2d_set_z_out_of_bounds_throws() {
        Random r = RandomUtils.rng();
        final int dimension = 2;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);
        linestring.addPoints(xy, 0, numPoints, dimension);
        linestring.setZ(numPoints, 0d);
        Assert.fail();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void LineString_3d_set_x_out_of_bounds_throws() {
        Random r = RandomUtils.rng();
        final int dimension = 3;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);
        linestring.addPoints(xy, 0, numPoints, dimension);

        linestring.setX(numPoints, 0d);
        Assert.fail();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void LineString_3d_set_y_out_of_bounds_throws() {
        Random r = RandomUtils.rng();
        final int dimension = 3;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);

        linestring.addPoints(xy, 0, numPoints, dimension);
        linestring.setY(numPoints, 0d);
        Assert.fail();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void LineString_3d_set_z_out_of_bounds_throws() {
        Random r = RandomUtils.rng();
        final int dimension = 3;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * dimension);
        LineString linestring = new LineString(dimension);

        linestring.addPoints(xy, 0, numPoints, dimension);
        linestring.setZ(numPoints, 0d);
        Assert.fail();
    }

    @Test
    public void LineString_2d_bulk_add_2d_points_roundtrip() {
        Random r = RandomUtils.rng();
        final int pointsDim = 2;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * pointsDim);
        LineString linestring = new LineString(2);

        linestring.addPoints(xy, 0, numPoints, pointsDim);
        for (int i = 0; i < numPoints; i++) {
            final double x = linestring.getX(i);
            final double y = linestring.getY(i);
            Assert.assertTrue(x == xy[i * pointsDim]);
            Assert.assertTrue(y == xy[i * pointsDim + 1]);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void LineString_2d_bulk_add_3d_points_throws() {
        Random r = RandomUtils.rng();
        final int pointsDim = 3;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * pointsDim);
        LineString linestring = new LineString(2);

        linestring.addPoints(xy, 0, numPoints, pointsDim);
        Assert.fail();
    }

    @Test
    public void LineString_3d_bulk_add_2d_points_roundtrip() {
        Random r = RandomUtils.rng();
        final int pointsDim = 2;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * pointsDim);
        LineString linestring = new LineString(3);

        linestring.addPoints(xy, 0, numPoints, pointsDim);
        for (int i = 0; i < numPoints; i++) {
            final double x = linestring.getX(i);
            final double y = linestring.getY(i);
            final double z = linestring.getZ(i);
            Assert.assertTrue(x == xy[i * pointsDim]);
            Assert.assertTrue(y == xy[i * pointsDim + 1]);
            Assert.assertTrue(z == 0d);
        }
    }

    @Test
    public void LineString_3d_bulk_add_3d_points_roundtrip() {
        Random r = RandomUtils.rng();
        final int pointsDim = 3;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * pointsDim);
        LineString linestring = new LineString(3);

        linestring.addPoints(xy, 0, numPoints, pointsDim);
        for (int i = 0; i < numPoints; i++) {
            final double x = linestring.getX(i);
            final double y = linestring.getY(i);
            final double z = linestring.getZ(i);
            Assert.assertTrue(x == xy[i * pointsDim]);
            Assert.assertTrue(y == xy[i * pointsDim + 1]);
            Assert.assertTrue(z == xy[i * pointsDim + 2]);
        }
    }

    @Test
    public void LineString_2d_get_point_2d() {
        Random r = RandomUtils.rng();
        final int pointsDim = 2;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * pointsDim);
        LineString linestring = new LineString(2);

        linestring.addPoints(xy, 0, numPoints, pointsDim);
        for (int i = 0; i < numPoints; i++) {
            Point p = new Point(0, 0);
            linestring.get(p, i);
            final double x = linestring.getX(i);
            final double y = linestring.getY(i);
            Assert.assertTrue(p.getX() == xy[i * pointsDim]);
            Assert.assertTrue(p.getY() == xy[i * pointsDim + 1]);
        }
    }

    @Test
    public void LineString_2d_get_point_3d() {
        Random r = RandomUtils.rng();
        final int pointsDim = 2;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * pointsDim);
        LineString linestring = new LineString(2);

        linestring.addPoints(xy, 0, numPoints, pointsDim);
        for (int i = 0; i < numPoints; i++) {
            Point p = new Point(0, 0, 0);
            linestring.get(p, i);
            final double x = linestring.getX(i);
            final double y = linestring.getY(i);
            Assert.assertTrue(p.getX() == xy[i * pointsDim]);
            Assert.assertTrue(p.getY() == xy[i * pointsDim + 1]);
            Assert.assertTrue(p.getZ() == 0d);
        }
    }

    @Test
    public void LineString_3d_get_point_2d() {
        Random r = RandomUtils.rng();
        final int pointsDim = 2;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * pointsDim);
        LineString linestring = new LineString(3);

        linestring.addPoints(xy, 0, numPoints, pointsDim);
        for (int i = 0; i < numPoints; i++) {
            Point p = new Point(0, 0);
            linestring.get(p, i);
            final double x = linestring.getX(i);
            final double y = linestring.getY(i);
            Assert.assertTrue(p.getX() == xy[i * pointsDim]);
            Assert.assertTrue(p.getY() == xy[i * pointsDim + 1]);
            Assert.assertTrue(p.getZ() == 0d);
        }
    }

    @Test
    public void LineString_3d_get_point_3d() {
        Random r = RandomUtils.rng();
        final int pointsDim = 3;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * pointsDim);
        LineString linestring = new LineString(3);

        linestring.addPoints(xy, 0, numPoints, pointsDim);
        for (int i = 0; i < numPoints; i++) {
            Point p = new Point(0, 0);
            linestring.get(p, i);
            final double x = linestring.getX(i);
            final double y = linestring.getY(i);
            Assert.assertTrue(p.getX() == xy[i * pointsDim]);
            Assert.assertTrue(p.getY() == xy[i * pointsDim + 1]);
            Assert.assertTrue(p.getZ() == xy[i * pointsDim + 2]);
        }
    }

    // envelope tests

    @Test
    public void LineString_2d_envelope() {
        Random r = RandomUtils.rng();
        final int pointsDim = 2;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * pointsDim);
        LineString linestring = new LineString(2);

        linestring.addPoints(xy, 0, numPoints, pointsDim);

        Envelope truth = new Envelope(xy[0], xy[1], 0d, xy[0], xy[1], 0d);

        for (int i = 0; i < numPoints; i++) {
            final double x = xy[i * pointsDim];
            final double y = xy[i * pointsDim + 1];
            truth.minX = Math.min(truth.minX, x);
            truth.maxX = Math.max(truth.maxX, x);
            truth.minY = Math.min(truth.minY, y);
            truth.maxY = Math.max(truth.maxY, y);
        }

        envelope_test(linestring, truth);
    }

    @Test
    public void LineString_3d_envelope() {
        Random r = RandomUtils.rng();
        final int pointsDim = 3;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * pointsDim);
        LineString linestring = new LineString(3);

        linestring.addPoints(xy, 0, numPoints, pointsDim);

        Envelope truth = new Envelope(xy[0], xy[1], xy[2], xy[0], xy[1],
                xy[2]);

        for (int i = 1; i < numPoints; i++) {
            final double x = xy[i * pointsDim];
            final double y = xy[i * pointsDim + 1];
            final double z = xy[i * pointsDim + 2];
            truth.minX = Math.min(truth.minX, x);
            truth.maxX = Math.max(truth.maxX, x);
            truth.minY = Math.min(truth.minY, y);
            truth.maxY = Math.max(truth.maxY, y);
            truth.minZ = Math.min(truth.minZ, z);
            truth.maxZ = Math.max(truth.maxZ, z);
        }

        envelope_test(linestring, truth);
    }

    // dimension tests

    @Test
    public void LineString_2d_to_3d() {
        Random r = RandomUtils.rng();
        final int pointsDim = 2;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * pointsDim);
        LineString linestring = new LineString(2);

        linestring.addPoints(xy, 0, numPoints, pointsDim);
        linestring.setDimension(3);

        for (int i = 0; i < numPoints; i++) {
            final double x = xy[i * pointsDim];
            final double y = xy[i * pointsDim + 1];

            Assert.assertTrue(x == linestring.getX(i));
            Assert.assertTrue(y == linestring.getY(i));
            Assert.assertTrue(0d == linestring.getZ(i));
        }
    }

    @Test
    public void LineString_3d_to_2d() {
        Random r = RandomUtils.rng();
        final int pointsDim = 3;
        final int numPoints = 5 + r.nextInt(6);
        double[] xy = RandomUtils.randomDoubleArray(numPoints * pointsDim);
        LineString linestring = new LineString(3);

        linestring.addPoints(xy, 0, numPoints, pointsDim);
        linestring.setDimension(2);

        for (int i = 0; i < numPoints; i++) {
            final double x = xy[i * pointsDim];
            final double y = xy[i * pointsDim + 1];

            Assert.assertTrue(x == linestring.getX(i));
            Assert.assertTrue(y == linestring.getY(i));
        }
    }

    // WKB tests
}
