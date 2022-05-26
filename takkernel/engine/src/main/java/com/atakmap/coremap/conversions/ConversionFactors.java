
package com.atakmap.coremap.conversions;

/**
 * Centralized location for conversion factors. Distance conversion factors obtained from the
 * SpanUtilities.  Most of these conversion factors were pulled from
 * tables such as https://www.wsdot.wa.gov/Reference/metrics/factors.htm
 * or conversion.org
 */
public class ConversionFactors {

    // Factor for converting meters^2 to acres
    public static final double METERS_TO_ACRES = 0.000247105381467;

    // Factor for converting acre to meters^2
    public static final double ACRES_TO_METERS = 4046.8564224;

    // Factor for converting meters to a distance
    public static final double METERS_TO_MILES = 0.000621371192d;
    public static final double METERS_TO_YARDS = 1.09361d;
    public static final double METERS_TO_FEET = 3.280839895d;
    public static final double METERS_TO_KM = 0.001d;
    public static final double METERS_TO_NM = 0.000539957d;

    // Factor for converting a distance to meters
    public static final double MILES_TO_METERS = 1609.344d;
    public static final double MILES_TO_KM = 1.609344d;
    public static final double YARDS_TO_METERS = 0.9144d;
    public static final double FEET_TO_METERS = 0.3048d;
    public static final double KM_TO_METERS = 1000d;
    public static final double NM_TO_METERS = 1852d;

    // Factor for converting feet to a distance
    public static final double FEET_TO_MILES = 0.000189394d;
    public static final double FEET_TO_YARDS = 0.3333333d;

    // Factor for converting a distance to feet
    public static final double MILES_TO_FEET = 5280d;
    public static final double YARDS_TO_FEET = 3;

    // Factor for converting a distance to feet
    public static final double INHG_TO_MBAR = 33.8639;

    // Factor for converting speed (obtained from CompassDropDown)
    public static final double METERS_PER_S_TO_MILES_PER_H = 2.2369362920544025d;
    public static final double METERS_PER_S_TO_KILOMETERS_PER_H = 3.6d;
    public static final double METERS_PER_S_TO_KNOTS = 1.94384d;
    public static final double METERS_PER_S_TO_FEET_PER_SEC = 3.28084d;
    public static final double KNOTS_TO_METERS_PER_S = 0.514444d;

    public static final double DEGREES_TO_RADIANS = 180.0d / Math.PI;
    public static final double RADIANS_TO_DEGREES = Math.PI / 180.0d;

    // defined as MILS being 6400 
    public static final double DEGREES_TO_MRADIANS = 6400.0d / 360.0d;
    public static final double MRADIANS_TO_DEGREES = 360.0d / 6400.0d;

}
