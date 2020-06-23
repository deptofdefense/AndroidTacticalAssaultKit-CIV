
package com.atakmap.map.projection;


import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.math.PointD;

public class WebMercatorProjection extends AbstractProjection {

    public final static MapProjectionDisplayModel DISPLAY_MODEL = MapProjectionDisplayModel.createDefaultENAPlanarModel(3857);

    private final static double a = 6378137.0d;

    /**
     * This is the value of Pi/4 so that it can be precomputed to speed up the algorithm
     */
    private final static double ONE_FOURTH_PI = Math.PI / 4.0d;

    /**
     * This is the value of Pi/2 so that it can be precomputed to speed up the algorithm
     */
    private final static double ONE_HALF_PI = Math.PI / 2.0d;

    /**
     * This is an optimized computation which performs (a * Math.toRadians(degrees)) when this
     * value is multiplied by degrees
     */
    private final static double QUICK_A_TIMES_TO_RADIANS = a * ( Math.PI / 180d );

    /**
     * This is an optimized computation which performs (Math.toDegrees(radians / a)) when this
     * value is multiplied by radians
     */
    private final static double QUICK_DIVIDE_BY_A_TO_DEGREES = 180d / ( a * Math.PI );

    /**
     * This is an optimized computation which performs (Math.toDegrees(radians)) when this
     * value is multiplied by radians
     */
    private final static double QUICK_TO_DEGREES = ( 180d / Math.PI );

    public final static Projection INSTANCE = new WebMercatorProjection();

    public WebMercatorProjection() {
        super(3857, false);
    }

    // bounds derived from
    // http://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
    @Override
    public double getMinLatitude() {
        return -85.0511;
    }

    @Override
    public double getMinLongitude() {
        return -180;
    }

    @Override
    public double getMaxLatitude() {
        return 85.0511;
    }

    @Override
    public double getMaxLongitude() {
        return 180;
    }

    // derived from OGP Publication 373-7-2 section 1.3.3.2

    @Override
    protected void forwardImpl(GeoPoint g, PointD p) {
        p.x = QUICK_A_TIMES_TO_RADIANS * g.getLongitude( );
        p.y = a * Math.log(
                    Math.tan( ONE_FOURTH_PI + Math.toRadians( g.getLatitude( ) ) / 2.0d ) );
        double altitude = 0.0d;
        if(g.isAltitudeValid()) {
            final AltitudeReference altRef = g.getAltitudeReference();
            final double value = g.getAltitude();
            if(altRef == AltitudeReference.HAE)
                altitude = value;
            // XXY what is done with AGL?
        }
        p.z = altitude;
    }

    @Override
    protected void inverseImpl(PointD p, GeoPoint g) {
        final double lng = QUICK_DIVIDE_BY_A_TO_DEGREES * p.x;
        final double lat = QUICK_TO_DEGREES * ( ONE_HALF_PI - 2.0d * Math
                    .atan( Math.exp( -p.y / a ) ) );
        g.set( lat, lng );
        if(p.z != 0d)
            g.set(p.z);
        else
            g.set(GeoPoint.UNKNOWN);
    }
}
