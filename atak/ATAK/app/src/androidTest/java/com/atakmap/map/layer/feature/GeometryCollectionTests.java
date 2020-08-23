
package com.atakmap.map.layer.feature;

import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;

import org.junit.Assert;
import org.junit.Test;

import java.util.Iterator;

public class GeometryCollectionTests extends AbstractGeometryTests {
    @Override
    Geometry createTestGeometry(int dimension) {
        return new GeometryCollection(dimension);
    }

    @Override
    Geometry createRandomGeometry(int dimension) {
        return randomGeometryCollection(dimension);
    }

    // constructor tests

    @Test
    public void GeometryCollection_2d_construtor_empty() {
        final int dimension = 2;
        GeometryCollection c = new GeometryCollection(dimension);

        Assert.assertTrue(c.getGeometries().isEmpty());
    }

    @Test
    public void GeometryCollection_3d_construtor_empty() {
        final int dimension = 3;
        GeometryCollection c = new GeometryCollection(dimension);

        Assert.assertTrue(c.getGeometries().isEmpty());
    }

    // data model tests

    // add geometries

    @Test
    public void GeometryCollection_add_geometry_instance() {
        GeometryCollection c = new GeometryCollection(3);
        Assert.assertEquals(c.getGeometries().size(), 0);
        final Geometry g = c.addGeometry(randomPoint(c.getDimension()));
        Assert.assertEquals(c.getGeometries().size(), 1);
        Assert.assertTrue(c.getGeometries().contains(g));
    }

    @Test
    public void GeometryCollection_add_geometry_geometries() {
        GeometryCollection c = new GeometryCollection(3);
        Assert.assertEquals(c.getGeometries().size(), 0);
        final Geometry g = randomPoint(c.getDimension());
        c.getGeometries().add(g);
        Assert.assertEquals(c.getGeometries().size(), 1);

        Iterator<Geometry> iter = c.getGeometries().iterator();
        Assert.assertTrue(iter.hasNext());
        assertEqual(g, iter.next());
        Assert.assertFalse(iter.hasNext());
    }

    // remove geometries

    @Test
    public void GeometryCollection_remove_geometry_instance() {
        GeometryCollection c = new GeometryCollection(3);
        Assert.assertEquals(c.getGeometries().size(), 0);
        final Geometry g = c.addGeometry(randomPoint(c.getDimension()));
        Assert.assertEquals(c.getGeometries().size(), 1);
        Assert.assertTrue(c.getGeometries().contains(g));
        c.removeGeometry(g);
        Assert.assertEquals(c.getGeometries().size(), 0);
    }

    @Test
    public void GeometryCollection_remove_geometry_geometries() {
        GeometryCollection c = new GeometryCollection(3);
        Assert.assertEquals(c.getGeometries().size(), 0);
        final Geometry g = c.addGeometry(randomPoint(c.getDimension()));
        Assert.assertEquals(c.getGeometries().size(), 1);
        Assert.assertTrue(c.getGeometries().contains(g));
        c.getGeometries().remove(g);
        Assert.assertEquals(c.getGeometries().size(), 0);
    }

    // clear geometries

    @Test
    public void GeometryCollection_clear_single() {
        GeometryCollection c = new GeometryCollection(3);
        Assert.assertEquals(c.getGeometries().size(), 0);
        final Geometry g = c.addGeometry(randomPoint(c.getDimension()));
        Assert.assertEquals(c.getGeometries().size(), 1);
        Assert.assertTrue(c.getGeometries().contains(g));
        c.getGeometries().clear();
        Assert.assertEquals(c.getGeometries().size(), 0);
    }

    @Test
    public void GeometryCollection_clear_random() {
        GeometryCollection c = randomGeometryCollection(3);
        Assert.assertTrue(c.getGeometries().size() > 0);
        c.getGeometries().clear();
        Assert.assertEquals(c.getGeometries().size(), 0);
    }

    // wkb tests
}
