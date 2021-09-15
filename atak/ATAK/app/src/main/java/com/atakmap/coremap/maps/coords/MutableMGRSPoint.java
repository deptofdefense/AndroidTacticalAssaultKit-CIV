
package com.atakmap.coremap.maps.coords;

public class MutableMGRSPoint extends MGRSPoint {

    public MutableMGRSPoint(MGRSPoint mgrsPoint) {
        super(mgrsPoint);
    }

    public MutableMGRSPoint(String mgrsString, Ellipsoid geoRef) {
        decodeString(mgrsString, geoRef, this);
    }

    public MutableMGRSPoint(double lat, double lng) {
        super(lat, lng);
    }

    public MutableMGRSPoint(double lat, double lng, Ellipsoid geoRef) {
        super(lat, lng, geoRef);
    }

    public MutableMGRSPoint(UTMPoint utmPoint) {
        super(utmPoint);
    }

    public void offset(double byEastingMeters, double myNorthingMeters) {
        if (utmPoint != null) {
            utmPoint.offset(byEastingMeters, myNorthingMeters);
            calcMGRSFromUTM();
        }
    }

    public void alignXMeters(double xmeters) {
        offset(-easting % xmeters, 0);
    }

    public void alignYMeters(double ymeters) {
        offset(0, -northing % ymeters);
    }

    public void alignMeters(double xmeters, double ymeters) {
        offset(-(easting % xmeters), -(northing % ymeters));
    }

    public void offsetGrid(int xoffset, int yoffset) {
        offset(100000d * xoffset, 100000d * yoffset);
    }

    public void alignToYGrid() {
        offset(0d, -northing);
    }

    public void alignToXGrid() {
        offset(-easting, 0d);
    }

    public void alignToGrid() {
        offset(-easting, -northing);
    }
}
