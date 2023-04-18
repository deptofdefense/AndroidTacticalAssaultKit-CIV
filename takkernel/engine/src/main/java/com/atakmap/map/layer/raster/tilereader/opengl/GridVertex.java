package com.atakmap.map.layer.raster.tilereader.opengl;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.math.PointD;

class GridVertex {
    public GeoPoint value;
    public boolean resolved;
    public PointD projected;
    public int projectedSrid;

    public GridVertex() {
        value = GeoPoint.createMutable();
        resolved = false;
        projected = new PointD(0d, 0d);
        projectedSrid = -1;
    }
}
