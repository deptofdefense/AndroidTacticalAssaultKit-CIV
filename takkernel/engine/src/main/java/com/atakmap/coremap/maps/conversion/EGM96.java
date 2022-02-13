
package com.atakmap.coremap.maps.conversion;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.conversions.Span;
import com.atakmap.coremap.conversions.SpanUtilities;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPoint.AltitudeReference;

/**
 * Computes EGM96 geoid offsets.
 * 
 */
public class EGM96 {
    static public final String TAG = "EGM96";

    private final static EGM96 instance = new EGM96();

    /**
     * The string used when the altitude is unknown.
     */
    public static final String UNKNOWN_MSL = "-- ft MSL";
    public static final String UNKNOWN_HAE = "-- ft HAE";

    /**
     * @deprecated  use static methods of {@link EGM96}
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static EGM96 getInstance() {
        return instance;

    }

    private EGM96() {
    }

    /**
     * Format the altitude in ft MSL for display.
     * 
     * @param point GeoPoint instance with the HAE altitude value specified
     * @return A String containing altitude in MSL in the format of "NN.NN ft MSL" where N is a
     *         number or if the altitude is unknown, return -- ft MSL.
     */
    public static String formatMSL(final GeoPoint point) {
        if (point == null)
            return UNKNOWN_MSL;

        if (!point.isAltitudeValid())
            return UNKNOWN_MSL;

        return SpanUtilities.format(getMSL(point), Span.METER,
                Span.FOOT) + " MSL";
    }

    /**
     * Format the altitude in ft MSL for display given a geopoint in AGL.
     * 
     * @param point GeoPoint instance with the AGL altitude value specified
     * @param surfaceHAE the HAE value of the current location and is used only if the 
     * point is AGL otherwise will be ignored.
     * @return A String containing altitude in MSL in the format of "NN.NN ft MSL" where N is a
     *         number or if the altitude is unknown, return -- ft MSL.
     */
    public static String formatMSL(final GeoPoint point,
            final double surfaceHAE) {
        if (point == null)
            return UNKNOWN_MSL;

        if (!point.isAltitudeValid())
            return UNKNOWN_MSL;

        if (point.getAltitudeReference() == AltitudeReference.AGL) {
            return SpanUtilities.format(
                    getMSL(point.getLatitude(), point.getLongitude(),
                            point.getAltitude()) + surfaceHAE,
                    Span.METER,
                    Span.FOOT) + " MSL";
        } else {
            return SpanUtilities.format(getMSL(point), Span.METER,
                    Span.FOOT) + " MSL";
        }
    }

    /**
     * Format the altitude in the specified units for the specified reference
     * for display.
     * 
     * <P>Currently the only references supported are
     * {@link AltitudeReference#MSL} and {@link AltitudeReference#HAE}. Use of
     * other references will cause an {@link IllegalArgumentException} to be
     * thrown.
     *  
     * @param point     The point containing the altitude to be formatted
     * @param span      The conversion of the MSL units (feet/meters/yards)
     * 
     * @return  A formatted string with the altitude units in the specified
     *          units based on the specified reference with the altitude
     *          reference abbreviation as a suffix.
     * 
     * @throws IllegalArgumentException If <code>formatRef</code> is not one of
     *                                  the references supported by this method.
     */
    public static String formatMSL(final GeoPoint point, Span span) {
        if (point == null)
            return UNKNOWN_MSL;

        if (!point.isAltitudeValid())
            return UNKNOWN_MSL;

        return SpanUtilities.format(getMSL(point), Span.METER,
                span) + " MSL";
    }

    public static String formatHAE(final GeoPoint point, Span span) {
        if (point == null)
            return UNKNOWN_HAE;

        if (!point.isAltitudeValid())
            return UNKNOWN_HAE;

        return SpanUtilities.format(point.getAltitude(), Span.METER,
                span) + " HAE";
    }

    /**
     * Retrieve the MSL in meters from a point. Does NOT support AGL. 
     * 
     * @param p the point with an altitude not in AGL. If the altitude is unknown, 
     * return altitude unknown.
     */
    public static double getMSL(final GeoPoint p) {
        return getMSL(p.getLatitude(), p.getLongitude(), p.getAltitude(),
                p.getAltitudeReference());
    }

    /**
     * Retrieve the MSL in meters from a  lat,lon,alt in HAE.
     *
     * @param lat - Latitude of location to retrieve
     * @param lon - Longitude of location to retrieve
     * @param alt - existing Altitude HAE only
     */
    public static double getMSL(final double lat, final double lon,
            final double alt) {
        return getMSL(lat, lon, alt, AltitudeReference.HAE);
    }

    /**
     * Retrieve the MSL in meters. Does NOT support AGL. 
     * 
     * @param lat - Latitude of location to retrieve
     * @param lon - Longitude of location to retrieve
     * @param alt - existing Altitude in HAE only
     * @param altRef - at this time the altitude reference is HAE
     * @return Altitude in MSL at specified point
     */
    private static double getMSL(final double lat, final double lon,
            final double alt, AltitudeReference altRef) {

        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return GeoPoint.UNKNOWN;
        }

        if (!GeoPoint.isAltitudeValid(alt)) {
            return GeoPoint.UNKNOWN;
        }

        if (altRef == AltitudeReference.HAE) {
            try {
                double offset = getOffset(lat, lon);

                if (Double.isNaN(offset)) {
                    Log.e(TAG, "Bad computation for point: (lat: " + lat
                            + ",lon: "
                            + lon + ") hae=" + alt);
                    return GeoPoint.UNKNOWN;

                } else {
                    // perplexed by what the source sould be. after a caculation is it truely really
                    // from the original source, or should it say calculated.

                    return alt - offset;
                }
            } catch (Exception e) {
                Log.e(TAG, "Bad computation for point: (lat: " + lat + ",lon: "
                        + lon + ") hae=" + alt);
                return GeoPoint.UNKNOWN;
            }
        } else {
            Log.e(TAG,
                    "cannot convert from AGL to MSL, returning raw altitude: "
                            + alt,
                    new Exception());
            return alt;
        }
    }

    /**
     * Retrieve the HAE in meters from a point with an elevation reference of HAE. Does NOT support AGL. 
     * 
     * @param p the point with the altitude in HAE. No conversion is performed.
     */
    public static double getHAE(final GeoPoint p) {
        if (p == null) {
            return GeoPoint.UNKNOWN;
        }

        if (p.getAltitudeReference() == AltitudeReference.HAE) {
            return p.getAltitude();
        } else {
            Log.e(TAG,
                    "cannot convert from AGL to HAE, returning raw altitude: "
                            + p.getAltitude(),
                    new Exception());
            return p.getAltitude();
        }
    }

    /**
     * Given a latitude and longitude, convert the provided MSL ALtitude into the appropriate HAE representation.
     * @param lat the latitude
     * @param lon the longitude 
     * @param altMSL the altitude in MSL
     * @return altitude in HAE
     */
    public static double getHAE(final double lat, final double lon,
            final double altMSL) {
        double offset = getOffset(lat, lon);

        if (Double.isNaN(offset)) {
            Log.e(TAG, "Bad computation for point: (lat: " + lat
                    + ",lon: "
                    + lon + ") msl=" + altMSL);
            return GeoPoint.UNKNOWN;

        } else {
            return altMSL + offset;
        }
    }

    /**
     * Retrieve the offset between the HAE and MSL values
     * 
     * @param latitude Latitude of location to retrieve
     * @param longitude Longitude of location to retrieve
     * @return Delta between the HAE and MSL values, or Double.NaN if the area is not valid.
     */
    public static native double getOffset(final double latitude,
            final double longitude);

    /**
     * Get AGL in meters, given the specified ground reference
     *
     * @param point - the point (with valid elevation) to return the AGL altitude of
     * @param groundElev - the Altitude reference of the elevation of the ground at that point 
       in meters HAE.
     * @return meters above ground level
     */
    public static double getAGL(final GeoPoint point, final double groundElev) {
        if (point == null) {
            return GeoPoint.UNKNOWN;
        }

        if (point.getAltitudeReference() == AltitudeReference.AGL)
            return point.getAltitude();

        if (Double.isNaN(groundElev))
            return GeoPoint.UNKNOWN;

        if (!point.isAltitudeValid()) {
            return GeoPoint.UNKNOWN;
        }

        return point.getAltitude() - groundElev;
    }

    /**
     * Format the specified AGL meters as a string in the specified units
     *
     * @param aglMeters the altitude in agl
     * @param units the unit of measurement to convert to
     * @return the format in agl
     */
    public static String formatAGL(final double aglMeters, final Span units) {
        if (!GeoPoint.isAltitudeValid(aglMeters)) {
            return "-- " + units.getAbbrev() + " AGL";
        } else {
            return SpanUtilities.format(aglMeters, Span.METER, units)
                    + " AGL";
        }
    }
}
