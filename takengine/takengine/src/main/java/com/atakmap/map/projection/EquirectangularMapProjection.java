
package com.atakmap.map.projection;


import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.math.PointD;

public class EquirectangularMapProjection extends AbstractProjection {

    public final static MapProjectionDisplayModel DISPLAY_MODEL = MapProjectionDisplayModel.createDefaultLLAPlanarModel(4326);
    
    public final static Projection INSTANCE = new EquirectangularMapProjection();

    public EquirectangularMapProjection() {
        super(4326, false);
    }

    @Override
    protected void forwardImpl(GeoPoint g, PointD p) {
        p.x = g.getLongitude();
        p.y = g.getLatitude();


        double altitude = 0.0d;
        if(g.isAltitudeValid()) {
            final AltitudeReference altRef = g.getAltitudeReference();
            final double value = g.getAltitude();
            if(altRef == AltitudeReference.HAE)
                altitude = value;
            // XXY What about AGL
        }
        p.z = altitude;
    }

    @Override
    protected void inverseImpl(PointD p, GeoPoint g) {
        g.set(p.y, p.x);
        g.set(p.z);
    }

    @Override
    public double getMinLatitude() {
        return -90;
    }

    @Override
    public double getMinLongitude() {
        return -180;
    }

    @Override
    public double getMaxLatitude() {
        return 90;
    }

    @Override
    public double getMaxLongitude() {
        return 180;
    }
}
