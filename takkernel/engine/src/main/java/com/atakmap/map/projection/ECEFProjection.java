package com.atakmap.map.projection;

import com.atakmap.coremap.log.Log;


import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.math.PointD;

public class ECEFProjection extends AbstractProjection {

    public final static MapProjectionDisplayModel DISPLAY_MODEL =
            new MapProjectionDisplayModel(4978,
                                          new com.atakmap.math.Ellipsoid(
                                                 new PointD(0, 0, 0),
                                                 Ellipsoid.WGS84.semiMajorAxis,
                                                 Ellipsoid.WGS84.semiMajorAxis,
                                                 Ellipsoid.WGS84.semiMinorAxis),
                                          1d,
                                          1d,
                                          1d,
                                          false);

    // number is based on observations, average convergence is below 10
    private final static int MAX_ITERATION = 20;
    
    public final static Projection INSTANCE = new ECEFProjection();

    protected ECEFProjection() {
        super(4978, true);
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

    @Override
    protected void forwardImpl(GeoPoint g, PointD p) {
        final double a = 6378137;
        final double b = 6356752.3142;
        
        final double latRad = Math.toRadians(g.getLatitude());
        final double cosLat = Math.cos(latRad);
        final double sinLat = Math.sin(latRad);
        final double lonRad = Math.toRadians(g.getLongitude());
        final double cosLon = Math.cos(lonRad);
        final double sinLon = Math.sin(lonRad);

        final double a2_b2 = (a*a)/(b*b);
        final double b2_a2 = (b*b)/(a*a);

        final double cden = Math.sqrt((cosLat*cosLat) + (b2_a2 * (sinLat*sinLat)));
        final double lden = Math.sqrt((a2_b2 * (cosLat*cosLat)) + (sinLat*sinLat));

        final double altitude = g.getAltitude();
        if(GeoPoint.isAltitudeValid(altitude)) {
             // xxy how to handle AGL
        }
            

        final double X = ((a/cden) + altitude) * (cosLat*cosLon);
        final double Y = ((a/cden) + altitude) * (cosLat*sinLon);
        final double Z = ((b/lden) + altitude) * sinLat;
        
        p.x = X;
        p.y = Y;
        p.z = Z;
    }

    @Override
    protected void inverseImpl(PointD ecef, GeoPoint g) {
        // convert from ECEF to LLA. try the iterative method first as that
        // method appears to yield better results when considering reversability
        // accuracy, which is a critical criteria
        if(!inverseIterative(ecef, g))
            inverseNumeric(ecef, g);
    }
     
    /**
     * Converts from ECEF to LLA using a numeric method.
     * 
     * <P>Based on testing, this method is <B>NOT</B> as accurate as the
     * iterative method with respect to reversability.
     * 
     * @param ecef  An ECEF x,y,z
     * @param g     Returns the LLA coordinate
     */
    private static void inverseNumeric(PointD ecef, GeoPoint g) {
        final double a = 6378137;
        final double b = 6356752.3142;
        
        final double lon = Math.toDegrees(Math.atan2(ecef.y, ecef.x));
        
        // numeric method
        final double e = 8.1819190842622e-2;

        // calculations:
        final double ep = Math.sqrt(((a*a)-(b*b))/(b*b));
        final double p = Math.sqrt((ecef.x*ecef.x)+(ecef.y*ecef.y));
        final double th = Math.atan2(a*ecef.z,b*p);
        final double sinth = Math.sin(th);
        final double costh = Math.cos(th);
        final double latRad = Math.atan2((ecef.z+(ep*ep)*b*(sinth*sinth*sinth)),(p-(e*e)*a*(costh*costh*costh)));
        final double lat = Math.toDegrees(latRad);
        final double sinlat = Math.sin(latRad);
        final double N = a/Math.sqrt(1-(e*e)*(sinlat*sinlat));
        final double alt = p/Math.cos(latRad)-N;

        // XXX - why are we getting NaN for 'h' on startup???

        g.set(lat, lon);
        g.set(alt);
    }

    /**
     * Converts from ECEF to LLA using an interative estimation method.
     * 
     * @param ecef  An ECEF x,y,z
     * @param g     Returns the LLA coordinate
     * 
     * @return  <code>false</code> if failed to converge, <code>true</code> if
     *          successful
     */
    private static boolean inverseIterative(PointD ecef, GeoPoint g) {
        final double a = 6378137;
        final double b = 6356752.3142;
        
        final double lon = Math.toDegrees(Math.atan2(ecef.y, ecef.x));
        
        final double a2 = (a*a);
        final double b2 = (b*b);
        final double x2plusy2 = (ecef.x*ecef.x)+(ecef.y*ecef.y);
            
        int cnt = 0;
        
        double latRadEst = Math.atan((a2/b2) * (ecef.z / Math.sqrt((ecef.x*ecef.x) + (ecef.y*ecef.y))));
        double scratch;
        double diff;
        do {
            double sinLat = Math.sin(latRadEst);
            double sin2Lat = sinLat*sinLat;
            double cosLat = Math.cos(latRadEst);
            double cos2Lat = cosLat*cosLat;

            scratch = Math.atan((a2*sin2Lat) / ((b2*sinLat*cosLat) + (((Math.sqrt(x2plusy2)*sinLat) - (ecef.z*cosLat)) * Math.sqrt((a2*cos2Lat) + (b2*sin2Lat)))));
            diff = Math.abs(scratch-latRadEst);
            latRadEst = scratch;
            cnt++;
            
            if(cnt > MAX_ITERATION) {
                Log.w("ECEFProjection", "Failing to converge, ECEF {x=" + ecef.x + ",y=" + ecef.y + ",z=" + ecef.z + "}");
                return false;
            }
        } while(diff > 0.000001);
                
        final double lat = Math.toDegrees(latRadEst);
        final double cosLat = Math.cos(latRadEst);
        final double sinLat = Math.sin(latRadEst);
        
        final double h = (Math.sqrt(x2plusy2) / cosLat) - (a2 / Math.sqrt((a2*cosLat*cosLat) + (b2*sinLat*sinLat)));

        // XXX - why are we getting NaN for 'h' on startup???

        g.set(lat, lon);
        g.set(h);
        return true;
    }
}
