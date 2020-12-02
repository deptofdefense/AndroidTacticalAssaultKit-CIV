
package com.atakmap.map.projection;

import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.math.PointD;

public abstract class AbstractProjection implements Projection {
    
    protected final int spatialReferenceId;
    protected final boolean is3D;

    protected AbstractProjection(int srid, boolean is3D) {
        this.spatialReferenceId = srid;
        this.is3D = is3D;
    }

    @Override
    public final PointD forward(GeoPoint g, PointD p) {
        if (p == null)
            p = new PointD(0, 0);
        this.forwardImpl(g, p);
        return p;
    }

    protected abstract void forwardImpl(GeoPoint g, PointD p);

    @Override
    public final GeoPoint inverse(PointD p, GeoPoint g) {
        if (g == null || !g.isMutable())
            g = GeoPoint.createMutable();
        this.inverseImpl(p, g);
        return g;
    }

    protected abstract void inverseImpl(PointD p, GeoPoint g);

    @Override
    public final int getSpatialReferenceID() {
        return this.spatialReferenceId;
    }
    
    @Override
    public final boolean is3D() {
        return is3D;
    }
}
