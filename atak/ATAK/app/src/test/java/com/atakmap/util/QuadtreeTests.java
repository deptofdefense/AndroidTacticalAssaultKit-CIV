
package com.atakmap.util;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.math.PointD;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QuadtreeTests {
    @Test
    public void update_point_size_check() {
        Quadtree<GeoPoint> spatialIndex = new Quadtree<>(
                new Quadtree.Function<GeoPoint>() {
                    @Override
                    public void getBounds(GeoPoint object, PointD min,
                            PointD max) {
                        min.x = object.getLongitude();
                        min.y = object.getLatitude();
                        max.x = object.getLongitude();
                        max.y = object.getLatitude();
                    }
                }, -180d, -90d, 180d, 90d, 2);

        final double aoiMinX = -78.79421174316589;
        final double aoiMinY = 35.76361300654775;
        final double aoiMaxX = -78.77226063807349;
        final double aoiMaxY = 35.77466250764105;

        assertEquals(0, spatialIndex.size(aoiMinX, aoiMinY, aoiMaxX, aoiMaxY));

        GeoPoint p = GeoPoint.createMutable();

        p.set(35.77202, -78.78946);
        spatialIndex.add(p);

        assertEquals(1, spatialIndex.size(aoiMinX, aoiMinY, aoiMaxX, aoiMaxY));

        spatialIndex.refresh(p);

        assertEquals(1, spatialIndex.size(aoiMinX, aoiMinY, aoiMaxX, aoiMaxY));

        p.set(35.77202, -78.78945);
        spatialIndex.refresh(p);

        assertEquals(1, spatialIndex.size(aoiMinX, aoiMinY, aoiMaxX, aoiMaxY));
    }

    @Test
    public void update_point_size_get() {
        Quadtree<GeoPoint> spatialIndex = new Quadtree<>(
                new Quadtree.Function<GeoPoint>() {
                    @Override
                    public void getBounds(GeoPoint object, PointD min,
                            PointD max) {
                        min.x = object.getLongitude();
                        min.y = object.getLatitude();
                        max.x = object.getLongitude();
                        max.y = object.getLatitude();
                    }
                }, -180d, -90d, 180d, 90d, 19);

        final double aoiMinX = -78.79421174316589;
        final double aoiMinY = 35.76361300654775;
        final double aoiMaxX = -78.77226063807349;
        final double aoiMaxY = 35.77466250764105;

        assertEquals(0, spatialIndex.size(aoiMinX, aoiMinY, aoiMaxX, aoiMaxY));

        GeoPoint p = GeoPoint.createMutable();

        p.set(35.77202, -78.78946);
        spatialIndex.add(p);

        ArrayList<GeoPoint> g1 = new ArrayList<>(1);
        spatialIndex.get(aoiMinX, aoiMinY, aoiMaxX, aoiMaxY, g1);
        assertEquals(1, g1.size());
        assertTrue(g1.contains(p));

        spatialIndex.refresh(p);

        ArrayList<GeoPoint> g2 = new ArrayList<>(1);
        spatialIndex.get(aoiMinX, aoiMinY, aoiMaxX, aoiMaxY, g2);
        assertEquals(1, g2.size());
        assertTrue(g2.contains(p));

        p.set(35.77202, -78.78945);
        spatialIndex.refresh(p);

        ArrayList<GeoPoint> g3 = new ArrayList<>(1);
        spatialIndex.get(aoiMinX, aoiMinY, aoiMaxX, aoiMaxY, g3);
        assertEquals(1, g3.size());
        assertTrue(g3.contains(p));
    }
}
