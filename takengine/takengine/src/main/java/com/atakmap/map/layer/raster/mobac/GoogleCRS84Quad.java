package com.atakmap.map.layer.raster.mobac;

import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.projection.AbstractProjection;
import com.atakmap.map.projection.EquirectangularMapProjection;
import com.atakmap.map.projection.Projection;
import com.atakmap.math.PointD;

public final class GoogleCRS84Quad extends AbstractProjection {
    public final static Projection INSTANCE = new GoogleCRS84Quad();
    
    public GoogleCRS84Quad() {
        super(90094326, false);
    }

    @Override
    public double getMinLatitude() {
        return -180d;
    }
    @Override
    public double getMaxLatitude() {
        return 180d;
    }
    @Override
    public double getMinLongitude() {
        return -180d;
    }
    @Override
    public double getMaxLongitude() {
        return 180d;
    }
    @Override
    protected void forwardImpl(GeoPoint g, PointD p) {
        EquirectangularMapProjection.INSTANCE.forward(g, p);
    }

    @Override
    protected void inverseImpl(PointD p, GeoPoint g) {
        EquirectangularMapProjection.INSTANCE.inverse(p, g);
    }
}