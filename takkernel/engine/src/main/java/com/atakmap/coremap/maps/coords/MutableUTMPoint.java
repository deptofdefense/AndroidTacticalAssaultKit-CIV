
package com.atakmap.coremap.maps.coords;

public class MutableUTMPoint extends UTMPoint {

    public MutableUTMPoint() {
    }

    public MutableUTMPoint(String zoneDescriptor, double atEasting,
            double atNorthing) {
        super(zoneDescriptor, atEasting, atNorthing);
    }

    public MutableUTMPoint(double lat, double lng) {
        this(lat, lng, Ellipsoid.WGS_84);
    }

    public MutableUTMPoint(double lat, double lng, Ellipsoid geoRef) {
        fromLatLng(geoRef, lat, lng, this);
    }

    public MutableUTMPoint(UTMPoint utmPoint) {
        super(utmPoint);
    }

    public void set(UTMPoint point) {
        latZone = point.latZone;
        lngZone = point.lngZone;
        easting = point.easting;
        northing = point.northing;
    }

    public void offset(double byEastingMeters, double byNorthingMeters) {
        easting += byEastingMeters;
        northing += byNorthingMeters;
    }
}
