
package com.atakmap.coremap.maps.coords;

import com.atakmap.coremap.log.Log;

import java.text.DecimalFormat;

import com.atakmap.coremap.locale.LocaleUtil;

/**
 * Military Grid Reference System point. Currently only UTM subset is supported, but UPS will come
 * if needed (implementation is left open for it).
 * 
 * MGRS is only valid for latitude values ranging from -80 < x < 84.   If the value falls outside
 * of this range UPS will need to be used.
 * 
 */
public class MGRSPoint {

    final public static String TAG = "MGRSPoint";

    /** 
     * Parse a valid MGRS String into the appropriate MGRS point.
     * A NumberFormatException may be trown if an invalid mgrsString is passed
     * in. Examples of valid MRGS coordinates would be 4QFJ12345678 or 04QFJ12345678.
     * @param mgrsString in the appropriate format of grid zone designator square identifier and then
     * easting and northing.
     * @param geoRef can be Ellipsoid.WGS_84
     * @param out can be null.
     *
     */
    public static MGRSPoint decodeString(String mgrsString, Ellipsoid geoRef,
            MutableMGRSPoint out) {
        // Remove any formatting.
        mgrsString = mgrsString.replace(" ", "").replace("\u200e", "");

        if (!Character.isDigit(mgrsString.charAt(1)))
            mgrsString = "0" + mgrsString;

        int lngZone = Integer.parseInt(mgrsString.substring(0, 2));
        int latZone = UTMPoint.getLatZoneIndex(mgrsString.charAt(2));

        // find the center of the zone
        double lat = UTMPoint.getZoneLatitude(latZone)
                + UTMPoint.getZoneLatitudeSpan(latZone) / 2d;
        double lng = UTMPoint.getZoneLongitude(lngZone)
                + UTMPoint.getZoneLongitudeSpan(lngZone, latZone) / 2d;

        MutableUTMPoint mutUtm = new MutableUTMPoint(lat, lng, geoRef);

        MGRSPoint r = out;
        if (r == null) {
            r = new MGRSPoint();
        }

        r.utmPoint = mutUtm;
        r.calcMGRSFromUTM();

        String eastingSubstr = "0";
        String northingSubstr = "0";
        double e = 1;
        if (mgrsString.length() > 5) {
            int compSize = (mgrsString.length() - 5) / 2;
            if (compSize > 0) {
                eastingSubstr = LocaleUtil.getNaturalNumber(mgrsString
                        .substring(5, 5 + compSize));
                northingSubstr = LocaleUtil.getNaturalNumber(mgrsString
                        .substring(5 + compSize, 5
                                + compSize + compSize));
            }

            switch (compSize) {
                case 1:
                    e = 10000;
                    break;
                case 2:
                    e = 1000;
                    break;
                case 3:
                    e = 100;
                    break;
                case 4:
                    e = 10;
                    break;
            }
        }

        // calculate the offset from the current MGRS point
        double eastingOffset = getXGridOffset(r.xGrid, mgrsString.charAt(3))
                * 100000d - r.easting
                + Double.parseDouble(eastingSubstr) * e;

        double northingOffset = getYGridOffset(r.yGrid, mgrsString.charAt(4))
                * 100000d
                - r.northing
                + Double.parseDouble(northingSubstr) * e;

        // offset the UTM point and recalculate MGRS components
        mutUtm.offset(eastingOffset, northingOffset);
        r.calcMGRSFromUTM();

        return r;
    }

    /**
     * Given a zone, sqare, easting and northing produce a MGRSPoint object.   If the 
     * provided information is syntactically invalid then decode will return null.
     */
    public static MGRSPoint decode(String zone,
            final String square,
            final String easting,
            final String northing,
            final Ellipsoid geoRef,
            final MutableMGRSPoint out) {

        try {
            if (!Character.isDigit(zone.charAt(1)))
                zone = "0" + zone;

            final int latZone = UTMPoint.getLatZoneIndex(zone.charAt(2));
            final int lngZone = Integer.parseInt(zone.substring(0, 2));

            // find the center of the zone
            final double lat = UTMPoint.getZoneLatitude(latZone)
                    + UTMPoint.getZoneLatitudeSpan(latZone) / 2d;
            final double lng = UTMPoint.getZoneLongitude(lngZone)
                    + UTMPoint.getZoneLongitudeSpan(lngZone, latZone) / 2d;

            final MutableUTMPoint mutUtm = new MutableUTMPoint(lat, lng,
                    geoRef);

            MGRSPoint r = out;
            if (r == null) {
                r = new MGRSPoint();
            }

            r.utmPoint = mutUtm;
            r.calcMGRSFromUTM();

            int e = 1;
            int n = 1;

            switch (easting.length()) {
                case 1:
                    e = 10000;
                    break;
                case 2:
                    e = 1000;
                    break;
                case 3:
                    e = 100;
                    break;
                case 4:
                    e = 10;
                    break;
            }

            switch (northing.length()) {
                case 1:
                    n = 10000;
                    break;
                case 2:
                    n = 1000;
                    break;
                case 3:
                    n = 100;
                    break;
                case 4:
                    n = 10;
                    break;
            }

            // calculate the offset from the current MGRS point
            double eastingOffset = getXGridOffset(r.xGrid, square.charAt(0))
                    * 100000d
                    - r.easting
                    + Double.parseDouble(LocaleUtil.getNaturalNumber(easting))
                            * e;

            double northingOffset = getYGridOffset(r.yGrid, square.charAt(1))
                    * 100000d
                    - r.northing
                    + Double.parseDouble(LocaleUtil.getNaturalNumber(northing))
                            * n;

            // offset the UTM point and recalculate MGRS components
            mutUtm.offset(eastingOffset, northingOffset);
            r.calcMGRSFromUTM();

            return r;
        } catch (Exception e) {
            Log.d(TAG, "error occurred creating a MGRSPoint.", e);
            return null;
        }
    }

    public static MGRSPoint fromLatLng(Ellipsoid geoRef, double lat,
            double lng,
            MutableMGRSPoint out) {
        MGRSPoint r = null;
        if (out == null)
            r = new MGRSPoint();
        if (r != null) {
            if (r.utmPoint == null) {
                r.utmPoint = new MutableUTMPoint();
            }

            UTMPoint.fromLatLng(geoRef, lat, lng, r.utmPoint);
            r.calcMGRSFromUTM();
        }
        return r;
    }

    public MGRSPoint(double lat, double lng) {
        this(lat, lng, Ellipsoid.WGS_84);
    }

    public MGRSPoint(double lat, double lng, Ellipsoid geoRef) {
        utmPoint = new MutableUTMPoint(lat, lng, geoRef);
        calcMGRSFromUTM();
    }

    public MGRSPoint(UTMPoint utmPoint) {
        this.utmPoint = new MutableUTMPoint(utmPoint);
        calcMGRSFromUTM();
    }

    public MGRSPoint(MGRSPoint mgrsPoint) {
        if (mgrsPoint.utmPoint != null) {
            utmPoint = new MutableUTMPoint(mgrsPoint.utmPoint);
        }
        yGrid = mgrsPoint.yGrid;
        xGrid = mgrsPoint.xGrid;
        easting = mgrsPoint.easting;
        northing = mgrsPoint.northing;
        checkValues();
    }

    /**
     * Obtain the corresponding latitude and longitude from the MGRS point. 
     * @param out can be null.
     * @return double[] of length 2 representing latitude in array position 0 and longitude in 
     * array position 1.
     */
    public double[] toLatLng(double[] out) {
        double[] r = null;
        if (utmPoint != null) {
            r = utmPoint.toLatLng(out);
        }
        return r;
    }

    protected void calcMGRSFromUTM() {
        int lngZone = utmPoint.getLngZone();

        // latGrids alternate A, F for odd, even lngZones
        int evenOffset = 0; // A
        if ((lngZone % 2) == 0) {
            evenOffset = 5; // F
        }

        double utmNorthing = utmPoint.getNorthing();
        if (utmPoint.getLatZone() < 10) {
            double equatorOffset = (utmNorthing - 10000000d);
            double yGridOffset = Math.floor(equatorOffset / 100000d);

            yGrid = (evenOffset + (int) yGridOffset) % 20;
            if (yGrid < 0) {
                yGrid += 20;
            }

            // yGrid = 20 + (evenOffset + (int)yGridOffset % 20);
            northing = equatorOffset - (yGridOffset * 100000d);
        } else {
            yGrid = ((int) (utmNorthing / 100000) + evenOffset) % 20;
            northing = utmNorthing % 100000;
        }

        int lngGridMeridian = 4; // E
        switch ((lngZone - 1) % 3) {
            case 1:
                lngGridMeridian = 12;
                break; // N
            case 2:
                lngGridMeridian = 20;
                break; // W
        }

        double utmEasting = utmPoint.getEasting();
        double meridianOffset = utmEasting - 500000;
        double xGridOffset = Math.floor(meridianOffset / 100000d);
        xGrid = lngGridMeridian + (int) xGridOffset;
        easting = meridianOffset - (xGridOffset * 100000d);
        checkValues();
    }

    private void checkValues() {
        // add check for easting and northing
        if (yGrid < 0 || yGrid > _LAT_GRID_CHARS.length() - 1) {
            Log.e(TAG, "yGrid value of " + yGrid
                    + " falls outside of acceptable range.  Set to 0.");
            yGrid = 0;
        }
        if (xGrid < 0 || xGrid > _LNG_GRID_CHARS.length() - 1) {
            Log.e(TAG, "xGrid value of " + xGrid
                    + " falls outside of acceptable range.  Set to 0.");
            xGrid = 0;
        }
    }

    public UTMPoint toUTMPoint(MutableUTMPoint out) {
        UTMPoint r = null;
        if (utmPoint != null) {
            if (out == null) {
                r = new UTMPoint(utmPoint);
            } else {
                r = out;
                out.set(utmPoint);
            }
        }
        return r;
    }

    public boolean isUTMBased() {
        return (utmPoint != null);
    }

    public boolean isUPSBased() {
        return false;
    }

    public String getZoneDescriptor() {
        String zoneDesc;
        if (utmPoint != null) {
            zoneDesc = utmPoint.getZoneDescriptor();
        } else {
            // TODO: UPSPoint
            zoneDesc = "";
        }

        if (zoneDesc == null) {
            zoneDesc = "---";
        }

        return zoneDesc;
    }

    public String getGridDescriptor() {
        char[] chars = {
                _LNG_GRID_CHARS.charAt(xGrid),
                _LAT_GRID_CHARS.charAt(yGrid)
        };
        return new String(chars);
    }

    public String toString() {
        return getZoneDescriptor() +
                getGridDescriptor() +
                getEastingDescriptor() +
                getNorthingDescriptor();
    }

    public String getFormattedString() {
        return getZoneDescriptor() +
                "  " +
                getGridDescriptor() +
                "  " +
                getEastingDescriptor() +
                "  " +
                getNorthingDescriptor();
    }

    public String getEastingDescriptor() {
        if (Math.floor(easting + EPSILON) >= 100000)
            return "99999";
        else
            return _EAST_NORTH_DEC_FMT.format(Math.floor(easting + EPSILON));
    }

    public String getNorthingDescriptor() {
        if (Math.floor(northing + EPSILON) >= 100000)
            return "99999";
        else
            return _EAST_NORTH_DEC_FMT.format(Math.floor(northing + EPSILON));
    }

    /**
     * @param zeroIndex
     * @param yGridChar
     * @return
     */
    public static int getYGridOffset(int zeroIndex, char yGridChar) {
        int offset = 0;
        int index = _LAT_GRID_CHARS.indexOf(yGridChar);
        if (index != -1) {
            offset = index - zeroIndex;
            if (offset < -9) {
                offset = 20 + offset;
            } else if (offset > 10) {
                offset = offset - 20;
            }
        }
        return offset;
    }

    public static final double GRID_METERS = 100000d;

    public static int getXGridOffset(int zeroIndex, char xGridChar) {
        int offset = 0;
        int index = _LNG_GRID_CHARS.indexOf(xGridChar);
        if (index != -1) {
            offset = index - zeroIndex;
            if (offset < -11) {
                offset = 24 + offset;
            } else if (offset > 22) {
                offset = offset - 24;
            }
        }
        return offset;
    }

    public int getXGrid() {
        return xGrid;
    }

    public int getYGrid() {
        return yGrid;
    }

    protected MGRSPoint() {
    }

    protected MutableUTMPoint utmPoint;
    /* TODO: UPSPoint _upsPoint; */
    protected int xGrid;
    protected int yGrid;
    protected double easting; /* into grid */
    protected double northing; /* into grid */

    private static final DecimalFormat _EAST_NORTH_DEC_FMT = LocaleUtil
            .getDecimalFormat(
                    "00000");
    private static final String _LAT_GRID_CHARS = "ABCDEFGHJKLMNPQRSTUV";
    private static final String _LNG_GRID_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ";

    // 1cm
    private final static double EPSILON = 0.01d;
}
