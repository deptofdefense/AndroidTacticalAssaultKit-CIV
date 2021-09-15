
package com.atakmap.coremap.maps.coords;

/**
 * Description from:
 * <P>
 * https://code.google.com/p/openmap/source/browse/src/openmap/com/bbn/openmap/proj/coords/ECEFPoint
 * .java
 * <P>
 * The Cartesian coordinate frame of reference used in GPS/GLONASS is called Earth-Centered,
 * Earth-Fixed (ECEF). ECEF uses three-dimensional XYZ coordinates (in meters) to describe the
 * location of a GPS user or satellite. The term "Earth- Centered" comes from the fact that the
 * origin of the axis (0,0,0) is located at the mass center of gravity (determined through years of
 * tracking satellite trajectories). The term "Earth-Fixed" implies that the axes are fixed with
 * respect to the earth (that is, they rotate with the earth). The Z-axis pierces the North Pole,
 * and the XY-axis defines the equatorial plane (Figure 1).
 * <P>
 * ECEF coordinates are expressed in a reference system that is related to mapping representations.
 * Because the earth has a complex shape, a simple, yet accurate, method to approximate the earth's
 * shape is required. The use of a reference ellipsoid allows for the conversion of the ECEF
 * coordinates to the more commonly used geodetic-mapping coordinates of Latitude, Longitude, and
 * Altitude (LLA).
 */

public class ECEF {
    private final double _x;
    private final double _y;
    private final double _z;

    public double getX() {
        return _x;
    }

    public double getY() {
        return _y;
    }

    public double getZ() {
        return _z;
    }

    public ECEF() {
        _x = 0;
        _y = 0;
        _z = 0;
    }

    public ECEF(double x, double y, double z) {
        _x = x;
        _y = y;
        _z = z;
    }

    public static ECEF fromGeoPoint(GeoPoint pt) {
        return fromGeoPoint(pt, Ellipsoid.WGS_84);
    }

    public static ECEF fromGeoPoint(GeoPoint pt, Ellipsoid e) {

        double altitude;

        // XXY
        if (!pt.isAltitudeValid() || pt
                .getAltitudeReference() == GeoPoint.AltitudeReference.AGL) {
            altitude = 0;
        } else {

            altitude = pt.getAltitude();
        }

        double rlat = pt.getLatitude() * div_pi_180d;
        double rlon = pt.getLongitude() * div_pi_180d;

        double N = e.getSemiMajorAxis()
                / Math.sqrt(1 - e.getFirstEccentricitySquared()
                        * Math.pow(Math.sin(rlat), 2));

        double x = (N + altitude) * Math.cos(rlat) * Math.cos(rlon);
        double y = (N + altitude) * Math.cos(rlat) * Math.sin(rlon);
        double z = ((1 - Math.pow(e.getFirstEccentricity(), 2)) * N + altitude)
                * Math.sin(rlat);
        return new ECEF(x, y, z);
    }

    public GeoPoint toGeoPoint() {
        return toGeoPoint(Ellipsoid.WGS_84);
    }

    public GeoPoint toGeoPoint(Ellipsoid e) {
        double eSq = e.getFirstEccentricity() * e.getFirstEccentricity();
        double a = e.getSemiMajorAxis();
        double b = e.getSemiMinorAxis();
        double p = Math.sqrt(_x * _x + _y * _y);
        double th = Math.atan2(a * _z, b * p);

        double epSq = (a * a - b * b) / (b * b);

        double rlon = Math.atan2(_y, _x);
        double rlat = Math.atan2(
                _z + epSq * b * Math.pow(Math.sin(th), 3),
                p - eSq * a * Math.pow(Math.cos(th), 3));

        double N = e.getSemiMajorAxis()
                / Math.sqrt(1 - eSq
                        * Math.pow(Math.sin(rlat), 2));
        double alt = p / Math.cos(rlat) - N;

        return new GeoPoint(rlat * div_180_pi,
                rlon * div_180_pi, alt);
    }

    public String toString() {
        return "(" + _x + "," + _y + "," + _z + ")";
    }

    private static final double div_180_pi = 180d / Math.PI;
    private static final double div_pi_180d = Math.PI / 180d;

}
