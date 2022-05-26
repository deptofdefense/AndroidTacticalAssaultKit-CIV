
package com.atakmap.coremap.maps.coords;

import java.text.DecimalFormat;

import com.atakmap.coremap.locale.LocaleUtil;

public class UTMPoint {

    protected int latZone; // [0, 19]
    protected int lngZone; // [0, 59] maps to 1-60
    protected double northing; // absolute northing from equator (Northern hemi) or from 10,000,00
    // meters south of equator (Southern Hemi)
    protected double easting; // easting from central meridian of UTM lng zone
    private static final String _LAT_ZONE_CHARS = "CDEFGHJKLMNPQRSTUVWX";
    private static final DecimalFormat _XY_FORMAT = LocaleUtil
            .getDecimalFormat("0");
    private static final double div_6d = 1d / 6d;
    private static final double div_24d = 1d / 24d;
    private static final double div_96d = 1d / 96d;
    private static final double div_120d = 1d / 120d;
    private static final double div_720d = 1d / 720d;
    private static final double div_3072d = 1d / 3072d;
    private static final double div_180_pi = 180d / Math.PI;
    private static final double div_pi_180d = Math.PI / 180d;

    public UTMPoint() {
        lngZone = 0;
        latZone = 10;
        easting = 5000000d;
        northing = 0d;
    }

    public UTMPoint(final String zoneDescriptor, final double atEasting,
            final double atNorthing) {
        lngZone = Integer.parseInt(zoneDescriptor.substring(0, 2));
        latZone = _LAT_ZONE_CHARS.indexOf(zoneDescriptor.charAt(2));
        easting = atEasting;
        northing = atNorthing;
    }

    public UTMPoint(UTMPoint utmPoint) {
        lngZone = utmPoint.lngZone;
        latZone = utmPoint.latZone;
        easting = utmPoint.easting;
        northing = utmPoint.northing;
    }

    public static UTMPoint decodeString(String utmZone, String easting,
            String northing) {

        if (utmZone.length() != 3)
            return null;

        try {
            return new UTMPoint(utmZone,
                    Double.parseDouble(LocaleUtil.getNaturalNumber(easting)),
                    Double.parseDouble(LocaleUtil.getNaturalNumber(northing)));
        } catch (NumberFormatException nfe) {
            return null;
        }

    }

    /**
     * Parse a valid UTM String into the appropriate UTM point.
     * A NumberFormatException may be trown if an invalid utmString is passed
     * in. Examples of valid UTM coordinates would be 17T6300844833438
     * @param utmString in the appropriate format of grid zone designator band identifier 
     * easting and northing.
     * @param geoRef can be Ellipsoid.WGS_84
     * @param out can be null.
     *
     */
    public static UTMPoint decodeString(String utmString) {
        utmString = utmString.replace(" ", "").replace("\u200e", "");

        if (!Character.isDigit(utmString.charAt(1)))
            utmString = "0" + utmString;

        String eastingSubstr = "0";
        String northingSubstr = "0";
        if (utmString.length() > 5) {
            int compSize = (utmString.length() - 3) / 2;
            if (compSize > 0) {
                eastingSubstr = utmString.substring(3, 3 + compSize);
                eastingSubstr = eastingSubstr.toLowerCase(
                        LocaleUtil.getCurrent()).replace("m", "");
                northingSubstr = utmString.substring(3 + compSize);
                northingSubstr = northingSubstr.toLowerCase(
                        LocaleUtil.getCurrent()).replace("m", "");
            }
        }

        try {
            return new UTMPoint(utmString.substring(0, 3),
                    Double.parseDouble(
                            LocaleUtil.getNaturalNumber(eastingSubstr)),
                    Double.parseDouble(
                            LocaleUtil.getNaturalNumber(northingSubstr)));
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    public int getLngZone() {
        return lngZone;
    }

    public int getLatZone() {
        return latZone;
    }

    public String getZoneDescriptor() {
        if (latZone < _LAT_ZONE_CHARS.length() && latZone >= 0) {
            return (lngZone < 10 ? "0" : "") + lngZone
                    + _LAT_ZONE_CHARS.charAt(latZone);
        } else
            return null;
    }

    public double getNorthing() {
        return northing;
    }

    public double getEasting() {
        return easting;
    }

    /**
     * Convert a geopoint into a valid UTM coordinate.   This will only provide a valid conversion for 
     * values -80.00 < x < 84.00 latitude.  For values not in that range, you will need to use UPS or 
     * Universal polar stereographic coordinate system
     */
    public static UTMPoint fromGeoPoint(final GeoPoint point) {
        return fromLatLng(Ellipsoid.WGS_84, point.getLatitude(),
                point.getLongitude(), null);
    }

    /**
     * Convert a geopoint into a valid UTM coordinate.   This will only provide a valid conversion for 
     * values -80.00 < x < 84.00 latitude.  For values not in that range, you will need to use UPS or 
     * Universal polar stereographic coordinate system
     */
    public static UTMPoint fromLatLng(final Ellipsoid geoRef, final double lat,
            final double lng, MutableUTMPoint out) {
        int lngZone = (int) ((lng + 180d) / 6d) + 1;
        int latZone = (int) ((lat + 80d) / 8d);
        switch (latZone) {
            case 17:
                if (lng >= 0 && lng < 3) {
                    lngZone = 31;
                } else if (lng >= 3 && lng < 12) {
                    lngZone = 32;
                }
                break;
            case 19:
                if (lng >= 0) {
                    if (lng < 9) {
                        lngZone = 31;
                    } else if (lng < 21) {
                        lngZone = 33;
                    } else if (lng < 33) {
                        lngZone = 35;
                    } else if (lng < 42) {
                        lngZone = 37;
                    }
                }
        }
        return fromLatLng(geoRef, lat, lng, lngZone, latZone, out);
    }

    /**
     * Function converts lat/lon to UTM zone coordinates. Equations from USGS
     * bulletin 1532.
     */
    private static UTMPoint fromLatLng(final Ellipsoid geoRef,
            final double lat, final double lng, final int lngZone,
            final int latZone, MutableUTMPoint out) {

        double a = geoRef.getSemiMajorAxis();
        double eccSquared = geoRef.getFirstEccentricitySquared();
        double k0 = 0.9996;

        double lngOrigin;
        double eccPrimeSquared;
        double N, T, C, A, M;

        double latRadians = lat * div_pi_180d;
        double lngRadians = lng * div_pi_180d;
        double lngOriginRadians;

        lngOrigin = (lngZone - 1) * 6 - 180 + 3;
        lngOriginRadians = lngOrigin * div_pi_180d;
        eccPrimeSquared = (eccSquared) / (1 - eccSquared);

        final double tanLatRadians = Math.tan(latRadians);
        final double sinLatRadians = Math.sin(latRadians);
        final double cosLatRadians = Math.cos(latRadians);

        N = a / Math.sqrt(1 - eccSquared * sinLatRadians * sinLatRadians);

        T = tanLatRadians * tanLatRadians;
        C = eccPrimeSquared * cosLatRadians * cosLatRadians;
        A = cosLatRadians * (lngRadians - lngOriginRadians);

        M = a * ((1 - eccSquared / 4 - 3 * eccSquared * eccSquared / 64 - 5
                * eccSquared * eccSquared * eccSquared / 256)
                * latRadians
                - (3 * eccSquared / 8 + 3 * eccSquared * eccSquared
                        / 32 + 45 * eccSquared * eccSquared
                                * eccSquared / 1024)
                        * Math.sin(2 * latRadians)
                + (15 * eccSquared * eccSquared / 256 + 45 * eccSquared
                        * eccSquared * eccSquared / 1024)
                        * Math.sin(4 * latRadians)
                - (35 * eccSquared * eccSquared
                        * eccSquared * div_3072d)
                        * Math.sin(6 * latRadians));

        double easting = (k0
                * N
                * (A + (1 - T + C) * A * A * A * div_6d + (5 - 18 * T + T * T
                        + 72 * C - 58 * eccPrimeSquared)
                        * A * A * A * A * A * div_120d)
                + 500000.0d);

        double northing = (k0 * (M + N
                * tanLatRadians
                * (A * A / 2 + (5 - T + 9 * C + 4 * C * C) * A * A * A * A
                        * div_24d
                        + (61 - 58 * T + T * T + 600 * C
                                - 330 * eccPrimeSquared)
                                * A * A * A * A * A * A * div_720d)));

        // southern hemisphere
        if (latZone < 10) {
            northing += 10000000.0f;
        }

        UTMPoint r = out;
        if (r == null) {
            r = new UTMPoint();
        }

        r.latZone = latZone;
        r.lngZone = lngZone;
        r.easting = easting;
        r.northing = northing;

        return r;
    }

    public GeoPoint toGeoPoint() {
        double[] ll = new double[2];
        toLatLng(ll);
        return new GeoPoint(ll[0], ll[1]);
    }

    public static double[] toLatLng(Ellipsoid geoRef, int latZone, int lngZone,
            double easting,
            double northing, double[] out) {
        double k0 = 0.9996;
        double a = geoRef.getSemiMajorAxis();
        double eccSquared = geoRef.getFirstEccentricitySquared();
        double eccPrimeSquared;
        double e1 = (1 - Math.sqrt(1 - eccSquared))
                / (1 + Math.sqrt(1 - eccSquared));
        double N1, T1, C1, R1, D, M;
        double mu, phi1Rad;

        // remove 500,000 meter offset for longitude
        double x = easting - 500000.0d;
        double y = northing;

        // southern hemi
        if (latZone < 10) {
            y -= 10000000.0d;
        }

        double LongOrigin = (lngZone - 1) * 6 - 180 + 3;

        eccPrimeSquared = (eccSquared) / (1 - eccSquared);

        M = y / k0;
        mu = M
                / (a * (1 - eccSquared / 4 - 3 * eccSquared * eccSquared / 64
                        - 5
                                * eccSquared * eccSquared * eccSquared / 256));

        phi1Rad = mu + (3 * e1 / 2 - 27 * e1 * e1 * e1 / 32) * Math.sin(2 * mu)
                + (21 * e1 * e1 / 16 - 55 * e1 * e1 * e1 * e1 / 32)
                        * Math.sin(4 * mu)
                + (151 * e1 * e1 * e1 * div_96d)
                        * Math.sin(6 * mu);
        // double phi1 = ProjMath.radToDeg(phi1Rad);

        final double sinPhi1Rad = Math.sin(phi1Rad);
        final double cosPhi1Rad = Math.cos(phi1Rad);
        final double tanPhi1Rad = Math.tan(phi1Rad);

        N1 = a / Math.sqrt(1 - eccSquared * sinPhi1Rad * sinPhi1Rad);
        T1 = tanPhi1Rad * tanPhi1Rad;
        C1 = eccPrimeSquared * cosPhi1Rad * cosPhi1Rad;
        R1 = a * (1 - eccSquared)
                / Math.pow(1 - eccSquared * sinPhi1Rad * sinPhi1Rad, 1.5);
        D = x / (N1 * k0);

        double lat = phi1Rad
                - (N1 * tanPhi1Rad / R1)
                        * (D
                                * D
                                / 2
                                - (5 + 3 * T1 + 10 * C1 - 4 * C1 * C1
                                        - 9 * eccPrimeSquared)
                                        * D * D * D * D / 24
                                + (61 + 90 * T1 + 298 * C1 + 45
                                        * T1 * T1 - 252 * eccPrimeSquared
                                        - 3 * C1 * C1)
                                        * D * D * D * D * D * D * div_720d);
        lat = lat * div_180_pi;

        double lon = (D - (1 + 2 * T1 + C1) * D * D * D * div_6d + (5 - 2 * C1
                + 28
                        * T1
                - 3 * C1 * C1 + 8 * eccPrimeSquared + 24 * T1 * T1)
                * D * D * D * D * D * div_120d)
                / cosPhi1Rad;
        lon = LongOrigin + lon * div_180_pi;

        if (out == null) {
            out = new double[2];
        }
        out[0] = lat;
        out[1] = lon;

        return out;
    }

    public static String getZoneDescriptorAt(double lat, double lng) {
        int lngZone = (int) ((lng + 180d) * div_6d) + 1;
        int latZone = (int) ((lat + 80d) / 8d);

        if (latZone > 19) {
            latZone = 19;
        }
        if (latZone < 0) {
            latZone = 0;
        }

        return (lngZone < 10 ? "0" : "") + lngZone
                + _LAT_ZONE_CHARS.charAt(latZone);
    }

    public static int getLatZoneIndex(char zoneChar) {
        return _LAT_ZONE_CHARS.indexOf(zoneChar);
    }

    public static double getZoneLatitude(int latZoneIndex) {
        return -80d + (latZoneIndex * 8d);
    }

    public static double getZoneLongitude(int zoneNumber) {
        return -180 + (zoneNumber - 1) * 6d;
    }

    public static double getZoneLongitudeSpan(int zoneNumber,
            int latZoneIndex) {
        return 6d;
    }

    public static double getZoneLatitudeSpan(int latZoneIndex) {
        return latZoneIndex == 19 ? 12d : 8d;
    }

    public double[] toLatLng(Ellipsoid geoRef, double[] out) {
        return toLatLng(geoRef, latZone, lngZone, easting, northing, out);
    }

    public double[] toLatLng(double[] out) {
        return toLatLng(Ellipsoid.WGS_84, out);
    }

    public String toString() {
        return (lngZone < 10 ? "0" : "") + lngZone +
                _LAT_ZONE_CHARS.charAt(latZone) +
                " " +
                _XY_FORMAT.format(Math.floor(easting)) +
                "mE " +
                _XY_FORMAT.format(Math.floor(northing)) +
                "mN";
    }

    public String getEastingDescriptor() {
        return _XY_FORMAT.format(Math.floor(easting));
    }

    public String getNorthingDescriptor() {
        return _XY_FORMAT.format(Math.floor(northing));
    }

}
