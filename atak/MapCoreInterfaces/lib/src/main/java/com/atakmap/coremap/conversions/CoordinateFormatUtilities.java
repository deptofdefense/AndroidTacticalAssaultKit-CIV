
package com.atakmap.coremap.conversions;

import com.atakmap.coremap.maps.coords.Ellipsoid;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.MGRSPoint;
import com.atakmap.coremap.maps.coords.UTMPoint;

import com.atakmap.coremap.log.Log;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.ArrayList;

/**
 * Provides a common set of conversions from valid Coordinates in ATAK to the appropriate coordinate
 * format requested.
 */
public class CoordinateFormatUtilities {
    public static final String TAG = "CoordinateFormatUtilities";

    /**
     * @param point the point to be formatted
     * @param entry the coordinate format for the formatted string.
     * @return returns the formatted string in the requested coordinate format
     */
    public static String formatToString(final GeoPoint point,
            final CoordinateFormat entry) {
        if (point == null || entry == null)
            return null;

        switch (entry) {
            case MGRS:
                return _convertToMGRSString(point);
            case ADDRESS:
            case DD:
                return _convertToDecDegString(point);
            case DMS:
                return _convertToDegMinSecString(point, true);
            case DM:
                return _convertToDegMinString(point, true);
            case UTM:
                return _convertToUTMString(point);
        }
        return null;
    }

    /**
     * @param point the point to be formatted
     * @param entry the coordinate format for the formatted string.
     * @return returns the formatted string in the requested coordinate format (if applicable will
     * request that the formatted string be in "short form"
     */
    public static String formatToShortString(final GeoPoint point,
            final CoordinateFormat entry) {
        if (point == null || entry == null)
            return null;

        switch (entry) {
            case MGRS:
                return _convertToMGRSString(point);
            case ADDRESS:
            case DD:
                return _convertToDecDegString(point);
            case DMS:
                return _convertToDegMinSecString(point, false);
            case DM:
                return _convertToDegMinString(point, false);
            case UTM:
                return _convertToUTMString(point);
        }
        return null;
    }

    /**
     * @param p GeoPoint that describes a specific location
     * @param precise
     * @return The String formatted version of the Decimal Entry in Lat Form
     */
    public static String _convertToLatDegString(GeoPoint p, boolean precise) {
        if (p == null)
            return null;

        String[] coord = _convertToDegMinStrings(p, precise);
        String latDegS = coord[0];
        // Change the negative signs to the proper letter
        if (latDegS.contains(CHAR_NEG)) {
            latDegS = latDegS.replace(CHAR_NEG, CHAR_S + CHAR_SPACE);
        } else {
            latDegS = CHAR_N + CHAR_SPACE + latDegS;
        }
        return latDegS + CHAR_DEG + CHAR_SPACE;
    }

    /**
     * @param p GeoPoint that describes a specific location
     * @param precise
     * @return The String formatted version of the Decimal Entry in Lng Form
     */
    public static String _convertToLngDegString(GeoPoint p, boolean precise) {
        if (p == null)
            return null;

        String[] coord = _convertToDegMinStrings(p, precise);
        String lonDegS = coord[2];
        // Change the negative signs to the proper letter
        if (lonDegS.contains(CHAR_NEG)) {
            lonDegS = lonDegS.replace(CHAR_NEG, CHAR_W + CHAR_SPACE);
        } else {
            lonDegS = CHAR_E + CHAR_SPACE + lonDegS;
        }
        return lonDegS + CHAR_DEG + CHAR_SPACE;
    }

    /**
     * @param point the point to be formatted
     * @param entry the coordinate format for the formatted string.
     * @return returns the formatted string in the requested coordinate format.   The coordinate
     * strings return break apart the coordinate elements into individual strings.
     */
    public static String[] formatToStrings(final GeoPoint point,
            final CoordinateFormat entry) {
        if (point == null || entry == null)
            return null;

        switch (entry) {
            case MGRS:
                return _convertToMGRSStrings(point);
            case ADDRESS:
            case DD:
                return _convertToDecDegStrings(point);
            case DMS:
                return _convertToDegMinSecStrings(point, true);
            case DM:
                return _convertToDegMinStrings(point, true);
            case UTM:
                return _convertToUTMStrings(point);
        }
        return null;
    }

    /**
     * @param coordinate the coordinate in string format
     * @param from the format of the coordinate string
     * @param to the format to convert the string to
     * @return the converted string in the format defined by to
     * @throws IllegalArgumentException
     */
    public static String format(String coordinate, CoordinateFormat from,
            CoordinateFormat to)
            throws IllegalArgumentException {
        GeoPoint p = convert(coordinate, from);
        if (p != null) {
            return formatToString(p, to);
        }
        return null;
    }

    /**
     * @param coordinate the initial coordinate in object array format.
     * @param from the coordinate of the object array
     * @param to the format to convert to
     * @return the string in the format specified by the to parameter.
     * @throws IllegalArgumentException
     */
    public static String format(final Object[] coordinate,
            final CoordinateFormat from,
            final CoordinateFormat to)
            throws IllegalArgumentException {
        GeoPoint p = convert(coordinate, from);
        if (p != null) {
            return formatToString(p, to);
        }
        return null;
    }

    /**
     * @param coordinate the coordinate specified in string format
     * @param entry the coordinate format of the string
     * @return the corresponding geospatial point
     * @throws IllegalArgumentException
     */
    public static GeoPoint convert(final String coordinate,
            final CoordinateFormat entry)
            throws IllegalArgumentException {

        if (coordinate == null || entry == null)
            return null;

        switch (entry) {
            case MGRS:
                return _convertFromMGRS(coordinate);
            case ADDRESS:
            case DD:
                return _convertFromDecDeg(coordinate);
            case DMS:
                return _convertFromDegMinSec(coordinate);
            case DM:
                return _convertFromDegMin(coordinate);
            case UTM:
                return _convertFromUTM(coordinate);
        }
        return null;
    }

    /**
     * @param coordinate the coordinate in object array form.
     * @param entry the format of the coordinate object array
     * @return the corresponding geospatial point
     * @throws IllegalArgumentException
     */
    public static GeoPoint convert(Object[] coordinate, CoordinateFormat entry)
            throws IllegalArgumentException {

        if (coordinate == null || entry == null)
            return null;

        switch (entry) {
            case MGRS:
                // Check if the input is appropriate for MGRS
                if (coordinate.length == 4
                        && coordinate[0] instanceof String
                        && coordinate[1] instanceof String
                        && (coordinate[2] instanceof String
                                || coordinate[2] instanceof Double)
                        && (coordinate[3] instanceof String
                                || coordinate[3] instanceof Double)) {
                    if (coordinate[2] instanceof String) {
                        return _convertFromMGRS((String) coordinate[0],
                                (String) coordinate[1],
                                (String) coordinate[2], (String) coordinate[3]);
                    } else {
                        return _convertFromMGRS((String) coordinate[0],
                                (String) coordinate[1],
                                Double.toString((Double) coordinate[2]),
                                Double.toString((Double) coordinate[3]));
                    }
                }
                break;
            case UTM:
                // Check if the input is appropriate for UTM
                if (coordinate.length == 3
                        && coordinate[0] instanceof String
                        && (coordinate[1] instanceof String
                                || coordinate[1] instanceof Double)
                        && (coordinate[2] instanceof String
                                || coordinate[2] instanceof Double)) {
                    if (coordinate[1] instanceof String) {
                        return _convertFromUTM((String) coordinate[0],
                                (String) coordinate[1],
                                (String) coordinate[2]);
                    } else {
                        return _convertFromUTM((String) coordinate[0],
                                Double.toString((Double) coordinate[1]),
                                Double.toString((Double) coordinate[2]));
                    }
                }
                break;
            case ADDRESS:
            case DD:
                // Check if the input is appropriate for Decimal Degrees
                if (coordinate.length == 2
                        && coordinate[0] instanceof Double
                        && coordinate[1] instanceof Double) {
                    return _convertFromDecDeg((Double) coordinate[0],
                            (Double) coordinate[1]);
                } else if (coordinate.length == 2
                        && coordinate[0] instanceof String
                        && coordinate[1] instanceof String) {
                    return _convertFromDecDeg((String) coordinate[0],
                            (String) coordinate[1]);
                }
                break;
            case DMS:
                // Check if the input is appropriate for Degrees, Minutes, Seconds
                if (coordinate.length == 6
                        && coordinate[0] instanceof Double
                        && coordinate[1] instanceof Double
                        && coordinate[2] instanceof Double
                        && coordinate[3] instanceof Double
                        && coordinate[4] instanceof Double
                        && coordinate[5] instanceof Double) {
                    return _convertFromDegMinSec((Double) coordinate[0],
                            (Double) coordinate[1],
                            (Double) coordinate[2],
                            (Double) coordinate[3], (Double) coordinate[4],
                            (Double) coordinate[5]);
                } else if (coordinate.length == 6
                        && coordinate[0] instanceof String
                        && coordinate[1] instanceof String
                        && coordinate[2] instanceof String
                        && coordinate[3] instanceof String
                        && coordinate[4] instanceof String
                        && coordinate[5] instanceof String) {
                    return _convertFromDegMinSec((String) coordinate[0],
                            (String) coordinate[1],
                            (String) coordinate[2],
                            (String) coordinate[3], (String) coordinate[4],
                            (String) coordinate[5]);
                }
                break;
            case DM:
                // Check if the input is appropriate for Degrees, Minutes
                if (coordinate.length == 4
                        && coordinate[0] instanceof Double
                        && coordinate[1] instanceof Double
                        && coordinate[2] instanceof Double
                        && coordinate[3] instanceof Double) {
                    return _convertFromDegMin((Double) coordinate[0],
                            (Double) coordinate[1],
                            (Double) coordinate[2], (Double) coordinate[3]);
                } else if (coordinate.length == 4
                        && coordinate[0] instanceof String
                        && coordinate[1] instanceof String
                        && coordinate[2] instanceof String
                        && coordinate[3] instanceof String) {
                    return _convertFromDegMin((String) coordinate[0],
                            (String) coordinate[1],
                            (String) coordinate[2], (String) coordinate[3]);
                }
                break;
        }
        return null;
    }

    private static String _convertToDecDegString(final GeoPoint p)
            throws IllegalArgumentException {

        if (p == null)
            return null;

        String[] coord = _convertToDecDegStrings(p);
        String latS = coord[0];
        String lonS = coord[1];
        // Change the negative signs to the proper letter
        if (latS.contains(CHAR_NEG)) {
            latS = latS.replace(CHAR_NEG, CHAR_S + CHAR_SPACE);
        } else {
            latS = CHAR_N + CHAR_SPACE + latS;
        }
        if (lonS.contains(CHAR_NEG)) {
            lonS = lonS.replace(CHAR_NEG, CHAR_W + CHAR_SPACE);
        } else {
            lonS = CHAR_E + CHAR_SPACE + lonS;
        }

        latS += CHAR_DEG;
        lonS += CHAR_DEG;
        return latS + CHAR_SPACE + CHAR_SPACE + lonS;
    }

    private static String[] _convertToDecDegStrings(GeoPoint p)
            throws IllegalArgumentException {

        if (p == null)
            return null;

        String latS = DEC_DEG_FORMAT.format(p.getLatitude());
        // for Arabic localization the first character is a directional character 
        // and not the actual - sign
        if (isNegative(p.getLatitude()) && !latS.contains(CHAR_NEG)) {
            latS = CHAR_NEG + latS;
        }

        String lonS = DEC_DEG_FORMAT.format(p.getLongitude());
        // for Arabic localization the first character is a directional character 
        // and not the actual - sign
        if (isNegative(p.getLongitude()) && !lonS.contains(CHAR_NEG)) {
            lonS = CHAR_NEG + lonS;
        }

        return new String[] {
                latS, lonS
        };
    }

    private static GeoPoint _convertFromDecDeg(double latitude,
            double longitude)
            throws IllegalArgumentException {
        // If the values are correct than return a geopoint
        if (Math.abs(latitude) <= 90d && Math.abs(longitude) <= 180d) {
            return new GeoPoint(latitude, longitude);
            // If the values are out of scope then send a verbose error message
        } else {
            int error = 0;
            if (Math.abs(latitude) > 90d)
                error += ERROR_RANGE_LAT;
            if (Math.abs(longitude) > 180d)
                error += ERROR_RANGE_LON;
            throw new IllegalArgumentException(_buildErrorMessage(
                    "Decimal Degrees",
                    error, new String[0], new String[0]));
        }
    }

    private static GeoPoint _convertFromDecDeg(final String latitude,
            final String longitude)
            throws IllegalArgumentException {
        String latS = LocaleUtil.getNaturalNumber(latitude.trim());
        String lonS = LocaleUtil.getNaturalNumber(longitude.trim());
        // Change the S W characters to be negative signs
        latS = latS.replace(CHAR_S, CHAR_NEG);
        lonS = lonS.replace(CHAR_W, CHAR_NEG);
        // Get rid of unwanted to characters N,E, and Deg
        latS = latS.replace(CHAR_N, CHAR_EMPTY);
        latS = latS.replace(CHAR_DEG, CHAR_EMPTY);
        lonS = lonS.replace(CHAR_E, CHAR_EMPTY);
        lonS = lonS.replace(CHAR_DEG, CHAR_EMPTY);

        // Convert the stripped strings to doubles
        try {
            return _convertFromDecDeg(
                    Double.parseDouble(LocaleUtil.getNaturalNumber(latS)),
                    Double.parseDouble(LocaleUtil.getNaturalNumber(lonS)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(_buildErrorMessage(null,
                    ERROR_NUMBER, null, null));
        }
    }

    private static GeoPoint _convertFromDecDeg(String coordinate)
            throws IllegalArgumentException {
        String coord = LocaleUtil.getNaturalNumber(coordinate);
        // Change the S W characters to be negative signs
        coord = coord.replace(CHAR_S + CHAR_SPACE, CHAR_NEG);
        coord = coord.replace(CHAR_W + CHAR_SPACE, CHAR_NEG);
        coord = coord.replace(CHAR_S, CHAR_NEG);
        coord = coord.replace(CHAR_W, CHAR_NEG);
        // Get rid of unwanted to characters N,E, and Deg
        coord = coord.replace(CHAR_N, CHAR_EMPTY);
        coord = coord.replace(CHAR_E, CHAR_EMPTY);
        coord = coord.replace(CHAR_DEG, CHAR_EMPTY);
        // Any delimiter characters changed to just white space so we can split it
        coord = coord.replace(CHAR_COMMA, CHAR_SPACE);
        coord = coord.replace(CHAR_COLON, CHAR_SPACE);
        coord = coord.replace(CHAR_SEMICOLON, CHAR_SPACE);

        String[] temp = coord.trim().split(WHITESPACE);
        if (temp.length == 2) {
            // Correctly formatted now
            try {
                return _convertFromDecDeg(
                        Double.parseDouble(
                                LocaleUtil.getNaturalNumber(temp[0])),
                        Double.parseDouble(
                                LocaleUtil.getNaturalNumber(temp[1])));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(_buildErrorMessage(null,
                        ERROR_NUMBER, null,
                        null));
            }
        }

        return null;
    }

    private static String _convertToMGRSString(GeoPoint p)
            throws IllegalArgumentException {
        if (p == null)
            return null;

        MGRSPoint mgrs = MGRSPoint.fromLatLng(Ellipsoid.WGS_84,
                p.getLatitude(), p.getLongitude(),
                null);
        return mgrs.getZoneDescriptor() + CHAR_SPACE + mgrs.getGridDescriptor()
                + CHAR_SPACE
                + mgrs.getEastingDescriptor() + CHAR_SPACE
                + mgrs.getNorthingDescriptor();
    }

    private static String _convertToUTMString(GeoPoint p)
            throws IllegalArgumentException {
        if (p == null)
            return null;

        UTMPoint utm = UTMPoint.fromLatLng(Ellipsoid.WGS_84,
                p.getLatitude(), p.getLongitude(),
                null);
        return utm.getZoneDescriptor()
                + CHAR_SPACE
                + utm.getEastingDescriptor() + CHAR_SPACE
                + utm.getNorthingDescriptor();
    }

    private static String[] _convertToMGRSStrings(GeoPoint p)
            throws IllegalArgumentException {
        if (p == null)
            return null;

        MGRSPoint mgrs = MGRSPoint.fromLatLng(Ellipsoid.WGS_84,
                p.getLatitude(), p.getLongitude(),
                null);
        return new String[] {
                mgrs.getZoneDescriptor(), mgrs.getGridDescriptor(),
                mgrs.getEastingDescriptor(), mgrs.getNorthingDescriptor()
        };
    }

    private static String[] _convertToUTMStrings(GeoPoint p)
            throws IllegalArgumentException {
        if (p == null)
            return null;

        UTMPoint utm = UTMPoint.fromLatLng(Ellipsoid.WGS_84,
                p.getLatitude(), p.getLongitude(),
                null);
        return new String[] {
                utm.getZoneDescriptor(),
                utm.getEastingDescriptor(), utm.getNorthingDescriptor()
        };
    }

    private static GeoPoint _convertFromMGRS(String coordinate)
            throws IllegalArgumentException {

        MGRSPoint mgrs = MGRSPoint.decodeString(
                LocaleUtil.getNaturalNumber(coordinate), Ellipsoid.WGS_84,
                null);
        double[] latlon = mgrs.toLatLng(null);
        return new GeoPoint(latlon[0], latlon[1]);
    }

    private static GeoPoint _convertFromUTM(String coordinate)
            throws IllegalArgumentException {

        UTMPoint utm = UTMPoint.decodeString(LocaleUtil
                .getNaturalNumber(coordinate));

        if (utm == null)
            return null;

        double[] latlon = utm.toLatLng(null);
        return new GeoPoint(latlon[0], latlon[1]);
    }

    private static GeoPoint _convertFromMGRS(final String zone,
            final String square,
            final String easting,
            final String northing)
            throws IllegalArgumentException {
        MGRSPoint mgrs = null;
        try {
            if ((zone.length() == 3 || zone.length() == 2)
                    && square.length() == 2) {
                mgrs = MGRSPoint.decode(zone, square,
                        LocaleUtil.getNaturalNumber(easting),
                        LocaleUtil.getNaturalNumber(northing),
                        Ellipsoid.WGS_84, null);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(_buildErrorMessage(null,
                    ERROR_NUMBER, null, null));
        }

        if (mgrs == null) {
            throw new IllegalArgumentException(_buildErrorMessage(null,
                    ERROR_NUMBER, null, null));
        }
        double[] latlon = mgrs.toLatLng(null);
        return new GeoPoint(latlon[0], latlon[1]);
    }

    private static GeoPoint _convertFromUTM(final String zone,
            final String easting,
            final String northing)
            throws IllegalArgumentException {
        UTMPoint utm = null;
        try {
            if (zone.length() == 3) {
                utm = UTMPoint.decodeString(zone, easting, northing);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(_buildErrorMessage(null,
                    ERROR_NUMBER, null, null));
        }

        if (utm == null) {
            throw new IllegalArgumentException(_buildErrorMessage(null,
                    ERROR_NUMBER, null, null));
        }
        double[] latlon = utm.toLatLng(null);
        return new GeoPoint(latlon[0], latlon[1]);
    }

    private static GeoPoint _convertFromDegMin(String coordinate)
            throws IllegalArgumentException {
        String coord = LocaleUtil.getNaturalNumber(coordinate);
        // Change the S W characters to be negative signs
        coord = coord.replace(CHAR_S + CHAR_SPACE, CHAR_NEG);
        coord = coord.replace(CHAR_W + CHAR_SPACE, CHAR_NEG);
        coord = coord.replace(CHAR_S, CHAR_NEG);
        coord = coord.replace(CHAR_W, CHAR_NEG);

        // Get rid of unwanted characters N, E and '
        coord = coord.replace(CHAR_N, CHAR_EMPTY);
        coord = coord.replace(CHAR_E, CHAR_EMPTY);
        coord = coord.replace(CHAR_MIN, CHAR_EMPTY);

        // Change the degree format to be a colon instead
        coord = coord.replace(CHAR_DEG + CHAR_SPACE, CHAR_COLON);
        coord = coord.replace(CHAR_DEG, CHAR_COLON);

        coord = coord.replace(CHAR_SEMICOLON, CHAR_SPACE);

        // Split up the coordinate string by variations of the formats:
        // -90:00.0000' or 72deg 00.0000' or 72:99.0000

        String[] lat = null;
        String[] lon = null;

        String[] latLon = coord.trim().split(WHITESPACE);
        if (latLon.length == 2) {
            lat = latLon[0].split(CHAR_COLON);
            lon = latLon[1].split(CHAR_COLON);
        }

        if (lat == null || (lat != null && lat.length != 2)
                || lon == null || (lon != null && lon.length != 2)) {
            // come up with a formatting error message
            throw new IllegalArgumentException(_buildErrorMessage(null,
                    ERROR_NUMBER, null, null));
        }

        return _convertFromDegMin(lat[0], lat[1], lon[0], lon[1]);
    }

    private static GeoPoint _convertFromDegMin(String latDeg, String latMin,
            String lonDeg, String lonMin)
            throws IllegalArgumentException {
        // Convert the strings to doubles and then convert them to a geopoint
        try {
            return _convertFromDegMin(
                    Double.parseDouble(LocaleUtil.getNaturalNumber(latDeg)),
                    Double.parseDouble(LocaleUtil.getNaturalNumber(latMin)),
                    Double.parseDouble(LocaleUtil.getNaturalNumber(lonDeg)),
                    Double.parseDouble(LocaleUtil.getNaturalNumber(lonMin)));
        } catch (NumberFormatException e) {
            Log.d(TAG, "error: ", e);
            throw new IllegalArgumentException(_buildErrorMessage(null,
                    ERROR_NUMBER, null, null));
        }
    }

    private static GeoPoint _convertFromDegMin(double latDeg, double latMin,
            double lonDeg, double lonMin) {
        // Make sure the entry is valid
        if (Math.abs(latDeg) <= 90d && latMin < 60 && latMin >= 0
                && Math.abs(lonDeg) <= 180d && lonMin < 60 && lonMin >= 0) {
            double lat = latDeg;
            double lon = lonDeg;
            boolean latNeg = isNegative(latDeg);
            boolean lonNeg = isNegative(lonDeg);

            if (latNeg) {
                lat -= latMin * DIV_MIN;
            } else {
                lat += latMin * DIV_MIN;
            }

            if (lonNeg) {
                lon -= lonMin * DIV_MIN;
            } else {
                lon += lonMin * DIV_MIN;
            }

            if (lat == 0 && latNeg)
                lat = -0.0;

            if (lon == 0 && lonNeg)
                lon = -0.0;

            return new GeoPoint(lat, lon);
        } else {
            int error = 0;
            ArrayList<String> latError = new ArrayList<>();
            ArrayList<String> lonError = new ArrayList<>();
            String[] lat = new String[1], lon = new String[1];
            if (Math.abs(latDeg) > 90d || latMin >= 60 || latMin < 0) {
                error += ERROR_RANGE_LAT;
                if (Math.abs(latDeg) > 90d)
                    latError.add("Degrees");
                if (latMin >= 60 || latMin < 0)
                    latError.add("Minutes");
            }
            if (Math.abs(lonDeg) > 180d || lonMin >= 60 || lonMin < 0) {
                error += ERROR_RANGE_LON;
                if (Math.abs(lonDeg) > 180d)
                    lonError.add("Degrees");
                if (lonMin >= 60 || lonMin < 0)
                    lonError.add("Minutes");
            }

            throw new IllegalArgumentException(_buildErrorMessage(
                    "Degrees, Minutes",
                    error, latError.toArray(lat), lonError.toArray(lon)));
        }
    }

    private static String _convertToDegMinString(GeoPoint p, boolean precise) {
        if (p == null)
            return null;

        String[] coord = _convertToDegMinStrings(p, precise);
        String latDegS = coord[0];
        String latMinS = coord[1];
        String lonDegS = coord[2];
        String lonMinS = coord[3];
        // Change the negative signs to the proper letter
        if (latDegS.contains(CHAR_NEG)) {
            latDegS = latDegS.replace(CHAR_NEG, CHAR_S + CHAR_SPACE);
        } else {
            latDegS = CHAR_N + CHAR_SPACE + latDegS;
        }
        if (lonDegS.contains(CHAR_NEG)) {
            lonDegS = lonDegS.replace(CHAR_NEG, CHAR_W + CHAR_SPACE);
        } else {
            lonDegS = CHAR_E + CHAR_SPACE + lonDegS;
        }

        return latDegS + CHAR_DEG + CHAR_SPACE + latMinS + CHAR_MIN
                + CHAR_SPACE + CHAR_SPACE + lonDegS + CHAR_DEG + CHAR_SPACE
                + lonMinS + CHAR_MIN;
    }

    private static String[] _convertToDegMinStrings(GeoPoint p,
            boolean precise) {
        if (p == null)
            return null;

        double[] lat = toDegreesMinutes(p.getLatitude());
        double[] lon = toDegreesMinutes(p.getLongitude());

        String latD = DM_DEG_FORMAT.format(lat[0]);
        if (lat[0] == 0d && isNegative(lat[0])) {
            latD = "-0";
        }
        String lonD = DM_DEG_FORMAT.format(lon[0]);
        if (lon[0] == 0d && isNegative(lon[0])) {
            lonD = "-0";
        }

        if (precise) {
            return new String[] {
                    latD,
                    DM_MIN_FORMAT.format(lat[1]),
                    lonD,
                    DM_MIN_FORMAT.format(lon[1])
            };
        } else {
            return new String[] {
                    latD,
                    DM_MIN_FORMAT_S.format(lat[1]),
                    lonD,
                    DM_MIN_FORMAT_S.format(lon[1])
            };
        }
    }

    private static GeoPoint _convertFromDegMinSec(String coordinate) {
        String coord = coordinate;
        // Change the S W characters to be negative signs
        coord = coord.replace(CHAR_S + CHAR_SPACE, CHAR_NEG);
        coord = coord.replace(CHAR_W + CHAR_SPACE, CHAR_NEG);
        coord = coord.replace(CHAR_S, CHAR_NEG);
        coord = coord.replace(CHAR_W, CHAR_NEG);

        // Get rid of unwanted characters N, E and "
        coord = coord.replace(CHAR_N, CHAR_EMPTY);
        coord = coord.replace(CHAR_E, CHAR_EMPTY);

        coord = coord.replace(CHAR_SEC, CHAR_EMPTY);

        coord = coord.replace(CHAR_DEG + CHAR_SPACE, CHAR_COLON);
        coord = coord.replace(CHAR_DEG, CHAR_COLON);
        coord = coord.replace(CHAR_MIN + CHAR_SPACE, CHAR_COLON);
        coord = coord.replace(CHAR_MIN, CHAR_COLON);

        coord = coord.replace(CHAR_SEMICOLON, CHAR_SPACE);

        // Split up the coordinate string by variations of the formats:
        // -90:00:00.00 or 72deg 00' 00.00" or 72:00' 00.00"

        String[] lat = null;
        String[] lon = null;
        String[] latLon = coord.trim().split(WHITESPACE);
        if (latLon.length == 2) {
            lat = latLon[0].split(CHAR_COLON);
            lon = latLon[1].split(CHAR_COLON);
        }

        if (lat == null || (lat != null && lat.length != 3)
                || lon == null || (lon != null && lon.length != 3)) {
            // come up with a formatting error message
            throw new IllegalArgumentException(_buildErrorMessage(null,
                    ERROR_NUMBER, null, null));
        }

        return _convertFromDegMinSec(lat[0], lat[1], lat[2], lon[0], lon[1],
                lon[2]);
    }

    private static GeoPoint _convertFromDegMinSec(String latDeg, String latMin,
            String latSec,
            String lonDeg, String lonMin, String lonSec) {
        // Convert the strings to doubles and then convert them to a geopoint
        try {
            return _convertFromDegMinSec(
                    Double.parseDouble(LocaleUtil.getNaturalNumber(latDeg)),
                    Double.parseDouble(LocaleUtil.getNaturalNumber(latMin)),
                    Double.parseDouble(LocaleUtil.getNaturalNumber(latSec)),
                    Double.parseDouble(LocaleUtil.getNaturalNumber(lonDeg)),
                    Double.parseDouble(LocaleUtil.getNaturalNumber(lonMin)),
                    Double.parseDouble(LocaleUtil.getNaturalNumber(lonSec)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(_buildErrorMessage(null,
                    ERROR_NUMBER, null, null));
        }
    }

    private static GeoPoint _convertFromDegMinSec(double latDeg, double latMin,
            double latSec,
            double lonDeg, double lonMin, double lonSec) {
        if (Math.abs(latDeg) <= 90d && latMin < 60 && latMin >= 0
                && latSec < 60 && latSec >= 0
                && Math.abs(lonDeg) <= 180d && lonMin < 60 && lonMin >= 0
                && lonSec < 60
                && lonSec >= 0) {
            double lat = latDeg;
            double lon = lonDeg;

            boolean latNeg = isNegative(latDeg);
            boolean lonNeg = isNegative(lonDeg);

            if (latNeg) {
                lat -= latMin * DIV_MIN;
                lat -= latSec * DIV_SEC;
            } else {
                lat += latMin * DIV_MIN;
                lat += latSec * DIV_SEC;
            }

            if (lonNeg) {
                lon -= lonMin * DIV_MIN;
                lon -= lonSec * DIV_SEC;
            } else {
                lon += lonMin * DIV_MIN;
                lon += lonSec * DIV_SEC;
            }

            if (lat == 0 && latNeg)
                lat = -0.0;

            if (lon == 0 && lonNeg)
                lon = -0.0;

            return new GeoPoint(lat, lon);
        } else {
            int error = 0;
            ArrayList<String> latError = new ArrayList<>();
            ArrayList<String> lonError = new ArrayList<>();
            String[] lat = new String[1], lon = new String[1];
            if (Math.abs(latDeg) > 90d || latMin >= 60 || latMin < 0
                    || latSec >= 60 || latSec < 0) {
                error += ERROR_RANGE_LAT;
                if (Math.abs(latDeg) > 90d)
                    latError.add("Degrees");
                if (latMin >= 60 || latMin < 0)
                    latError.add("Minutes");
                if (latSec >= 60 || latSec < 0)
                    latError.add("Seconds");
            }
            if (Math.abs(lonDeg) > 180d || lonMin >= 60 || lonMin < 0
                    || lonSec >= 60 || lonSec < 0) {
                error += ERROR_RANGE_LON;
                if (Math.abs(lonDeg) > 180d)
                    lonError.add("Degrees");
                if (lonMin >= 60 || lonMin < 0)
                    lonError.add("Minutes");
                if (lonSec >= 60 || lonSec < 0)
                    lonError.add("Seconds");
            }

            throw new IllegalArgumentException(_buildErrorMessage(
                    "Degrees, Minutes, Seconds",
                    error, latError.toArray(lat), lonError.toArray(lon)));
        }
    }

    private static String _convertToDegMinSecString(GeoPoint p,
            boolean precise) {
        if (p == null)
            return null;

        String[] coord = _convertToDegMinSecStrings(p, precise);
        String latDeg = coord[0];
        String latMin = coord[1];
        String latSec = coord[2];
        String lonDeg = coord[3];
        String lonMin = coord[4];
        String lonSec = coord[5];
        // Change the negative signs to the proper letter
        if (latDeg.contains(CHAR_NEG)) {
            latDeg = latDeg.replace(CHAR_NEG, CHAR_S + CHAR_SPACE);
        } else {
            latDeg = CHAR_N + CHAR_SPACE + latDeg;
        }
        if (lonDeg.contains(CHAR_NEG)) {
            lonDeg = lonDeg.replace(CHAR_NEG, CHAR_W + CHAR_SPACE);
        } else {
            lonDeg = CHAR_E + CHAR_SPACE + lonDeg;
        }

        return latDeg + CHAR_DEG + CHAR_SPACE + latMin + CHAR_MIN + CHAR_SPACE
                + latSec + CHAR_SEC
                + CHAR_SPACE + CHAR_SPACE + lonDeg + CHAR_DEG + CHAR_SPACE
                + lonMin + CHAR_MIN
                + CHAR_SPACE + lonSec + CHAR_SEC;
    }

    private static String[] _convertToDegMinSecStrings(GeoPoint p,
            boolean precise) {
        if (p == null)
            return null;

        double[] lat = toDegreesMinutesSeconds(p.getLatitude());
        double[] lon = toDegreesMinutesSeconds(p.getLongitude());

        String latD = DMS_DEG_FORMAT.format(lat[0]);
        if (lat[0] == 0d && isNegative(lat[0])) {
            latD = "-0";
        }
        String lonD = DMS_DEG_FORMAT.format(lon[0]);
        if (lon[0] == 0d && isNegative(lon[0])) {
            lonD = "-0";
        }
        if (precise) {
            return new String[] {
                    latD,
                    DMS_MIN_FORMAT.format(lat[1]),
                    DMS_SEC_FORMAT.format(lat[2]),
                    lonD,
                    DMS_MIN_FORMAT.format(lon[1]),
                    DMS_SEC_FORMAT.format(lon[2])
            };
        } else {
            return new String[] {
                    latD,
                    DMS_MIN_FORMAT.format(lat[1]),
                    DMS_SEC_FORMAT_S.format(lat[2]),
                    lonD,
                    DMS_MIN_FORMAT.format(lon[1]),
                    DMS_SEC_FORMAT_S.format(lon[2])
            };
        }
    }

    // private static Double[] _convertToDegMinSec(GeoPoint p)
    // {
    //
    // return null;
    // }

    private static String _buildErrorMessage(String type, int error,
            String[] lat, String[] lon) {
        StringBuilder b = new StringBuilder();
        switch (error) {
            case ERROR_INVALID:
                b.append(type).append(" is invalid. Enter a valid ")
                        .append(type)
                        .append(" coordinate.");
                break;
            case ERROR_RANGE_LAT:
                if (lat.length == 0) {
                    b.append("Latitude out of range. Enter a valid Latitude.");
                } else {
                    b.append("Latitude's ");
                    for (String s : lat) {
                        b.append(s).append(" ");
                    }
                    b.append("out of range. Enter ");
                    if (lat.length == 1)
                        b.append("a valid field.");
                    if (lat.length > 1)
                        b.append("valid fields.");
                }
                break;
            case ERROR_RANGE_LON:
                if (lon.length == 0) {
                    b.append(
                            "Longitude out of range. Enter a valid Longitude.");
                } else {
                    b.append("Longitude's ");
                    for (String s : lon) {
                        b.append(s).append(" ");
                    }
                    b.append("out of range. Enter ");
                    if (lon.length == 1)
                        b.append("a valid field.");
                    if (lon.length > 1)
                        b.append("valid fields.");
                }
                break;
            case (ERROR_RANGE_LAT + ERROR_RANGE_LON):
                if (lat.length == 0 && lon.length == 0) {
                    b.append(
                            "Latitude and Longitude out of range. Enter valid fields.");
                } else {
                    b.append("Latitude's ");
                    for (String s : lat) {
                        b.append(s).append(" ");
                    }
                    b.append(" and ");
                    b.append("Longitude's ");
                    for (String s : lon) {
                        b.append(s).append(" ");
                    }
                    b.append("out of range. Enter valid fields.");
                }
                break;
            case ERROR_NUMBER:
                b.append("Invalid input entered for a numerical field.");
                break;
        }
        return b.toString();
    }

    /**
     * Converts a decimal degree value to an array of degrees minutes seconds.
     * @param ddValue the decimal degree value
     * @return the resulting array with [0] = degrees, [1] = minutes, and [2] = seconds.
     */
    public static double[] toDegreesMinutesSeconds(double ddValue) {
        // Android's Location class copy
        boolean neg = isNegative(ddValue);

        double coordinate = Math.abs(ddValue);

        double degrees = Math.floor(coordinate);

        coordinate -= degrees;

        if (neg) {
            degrees *= -1d;
            if (degrees == 0d) {
                degrees = -0d;
            }
        }
        coordinate *= 60d;

        double minutes = Math.floor(coordinate);

        coordinate -= minutes;

        double seconds = coordinate *= 60d;

        double[] out = new double[3];

        out[0] = degrees;
        out[1] = minutes;
        out[2] = seconds;
        return out;
    }

    /**
     * Converts a decimal degree value to an array of degrees minutes.
     * @param ddValue the decimal degree value
     * @return the resulting array with [0] = degrees, [1] = minutes
     */
    public static double[] toDegreesMinutes(double ddValue) {
        // Android's Location class copy

        boolean neg = isNegative(ddValue);

        double coordinate = Math.abs(ddValue);

        double degrees = Math.floor(coordinate);

        coordinate -= degrees;

        if (neg) {
            degrees *= -1d;
        }

        coordinate *= 60d;

        double minutes = coordinate;

        double[] out = new double[2];

        out[0] = degrees;
        out[1] = minutes;
        return out;
    }

    private static boolean isNegative(double d) {
        return Double.doubleToRawLongBits(d) < 0 || Double.compare(d, 0.0) < 0;
    }

    // Decimal Formats
    private static final DecimalFormat DMS_DEG_FORMAT = LocaleUtil
            .getDecimalFormat("##0");
    private static final DecimalFormat DMS_SEC_FORMAT = LocaleUtil
            .getDecimalFormat(
                    "00.00");
    private static final DecimalFormat DMS_SEC_FORMAT_S = LocaleUtil
            .getDecimalFormat(
                    "00");
    private static final DecimalFormat DMS_MIN_FORMAT = LocaleUtil
            .getDecimalFormat("00");
    private static final DecimalFormat DM_DEG_FORMAT = LocaleUtil
            .getDecimalFormat("##0");
    private static final DecimalFormat DM_MIN_FORMAT = LocaleUtil
            .getDecimalFormat(
                    "00.0000");
    private static final DecimalFormat DM_MIN_FORMAT_S = LocaleUtil
            .getDecimalFormat(
                    "00.00");
    private static final DecimalFormat DEC_DEG_FORMAT = LocaleUtil
            .getDecimalFormat(
                    "##0.00000");
    private static final DecimalFormat MGRS_FORMAT = LocaleUtil
            .getDecimalFormat("00000");

    // For some reason DecimalFormat defaults to HALF_EVEN
    // we want HALF_UP instead
    static {
        DMS_DEG_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
        DMS_SEC_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
        DMS_MIN_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
        DM_DEG_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
        DM_MIN_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
        DEC_DEG_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
        MGRS_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
    }

    // Divisions
    private static final double DIV_MIN = 1d / 60d;
    private static final double DIV_SEC = 1d / 3600d;

    // Error Casts
    private static final int ERROR_INVALID = 0x00000001;
    private static final int ERROR_RANGE_LAT = 0x00000002;
    private static final int ERROR_RANGE_LON = 0x00000004;
    private static final int ERROR_NUMBER = 0x00000008;

    // Strings to use in parsing inputs
    private static final String CHAR_EMPTY = "";
    private static final String CHAR_SPACE = "\u200E "; // use left to right mark for BIDI text cases
    private static final String CHAR_DEG = "\u00B0";
    private static final String CHAR_N = "N";
    private static final String CHAR_S = "S";
    private static final String CHAR_W = "W";
    private static final String CHAR_E = "E";
    private static final String CHAR_MIN = "\u0027";
    private static final String CHAR_SEC = "\"";
    private static final String CHAR_COLON = "\u003A";
    private static final String CHAR_NEG = "\u002D";
    private static final String CHAR_BREAK = "\n";
    private static final String CHAR_DEC = ".";
    private static final String CHAR_COMMA = "\u002C";
    private static final String CHAR_SEMICOLON = "\u003B";

    private static final String WHITESPACE = "\\s+";
}
