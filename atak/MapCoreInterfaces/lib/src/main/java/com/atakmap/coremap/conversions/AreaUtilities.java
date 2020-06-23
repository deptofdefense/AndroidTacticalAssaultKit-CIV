
package com.atakmap.coremap.conversions;

import java.text.DecimalFormat;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.coremap.locale.LocaleUtil;

/**
 * 
 *  Allows for quick formatting of
 * area information.
 *
 */
public class AreaUtilities {

    // DECIMAL FORMATERS
    private static final DecimalFormat NO_DEC_FORMAT = LocaleUtil
            .getDecimalFormat(
                    "#,###,###,##0");
    private static final DecimalFormat ONE_DEC_FORMAT = LocaleUtil
            .getDecimalFormat(
                    "#,###,###,##0.0");
    private static final DecimalFormat TWO_DEC_FORMAT = LocaleUtil
            .getDecimalFormat(
                    "#,###,###,##0.00");
    private static final DecimalFormat THREE_DEC_FORMAT = LocaleUtil
            .getDecimalFormat(
                    "#,###,###,##0.000");
    private static final DecimalFormat FOUR_DEC_FORMAT = LocaleUtil
            .getDecimalFormat(
                    "#,###,###,##0.0000");

    private static int ft_to_m_threshold = (int) Math.pow(
            ConversionFactors.MILES_TO_FEET, 2) / 100;
    private static int m_to_km_threshold = 40000;

    /**
     * Sets the threshold used, that when reached that when calls to SpanUtilities will switch
     * from return feet to miles.
     */
    public static void setFeetToMileThreshold(int value) {
        ft_to_m_threshold = value;
    }

    /**
     * Sets the threshold used, that when reached that when calls to SpanUtilities will switch
     * from return meters to kilometers.
     */
    public static void setMetersToKilometersThreshold(int value) {
        m_to_km_threshold = value;
    }

    /**
     * Given a unit type and a value with the provided area units will produce the appropriate
     * string.
     * @param unitsType the unit types preferred for display.  ENGLISH, METRIC, NM, AC
     * @param value the value to use
     * @param units the units that the value is in.
     * @return
     */
    public static String formatArea(int unitsType, double value, Area units) {
        double out;
        Area outSpan;
        switch (unitsType) {
            case Area.METRIC:
                out = convertValue(value, units, Area.METER2);
                if (out > m_to_km_threshold) {
                    // Change to kilometers
                    out = convertValue(out, Area.METER2, Area.KILOMETER2);
                    outSpan = Area.KILOMETER2;
                } else {
                    // Keep as meters
                    outSpan = Area.METER2;
                }
                break;
            case Area.ENGLISH:
                out = convertValue(value, units, Area.FOOT2);
                if (out > ft_to_m_threshold) {
                    out = convertValue(out, Area.FOOT2, Area.MILE2);
                    outSpan = Area.MILE2;
                } else {
                    // Keep as feet
                    outSpan = Area.FOOT2;
                }
                break;
            case Area.NM:
                out = convertValue(value, units, Area.NAUTICALMILE2);
                outSpan = Area.NAUTICALMILE2;
                break;
            case Area.AC:
                out = convertValue(value, units, Area.ACRES);
                outSpan = Area.ACRES;
                break;
            default:
                return "Formatting Error";
        }

        // we do not do fractional meters/feet in this program for display to the user.
        if (outSpan == Area.METER2 || outSpan == Area.FOOT2) {
            return NO_DEC_FORMAT.format(out) + " " + outSpan.getAbbrev();
        }

        // Based on how many figures to the left of the decimal, format appropriately
        if (out < 100) {
            return TWO_DEC_FORMAT.format(Math.round(out * 100) / 100d) + " "
                    + outSpan.getAbbrev();
        } else if (out < 1000) {
            return ONE_DEC_FORMAT.format(Math.round(out * 10) / 10d) + " "
                    + outSpan.getAbbrev();
        } else {
            return NO_DEC_FORMAT.format(Math.round(out)) + " "
                    + outSpan.getAbbrev();
        }
    }

    /**
     * Method to convert a value from an area type to another area type
     * @param value raw value in the format described by the from value
     * @param from the Area of the source value
     * @param to the value to convert to.
     * @return the value in the newly formatted area type.
     */
    public static double convertValue(double value, Area from, Area to) {
        double out = value;

        // Return the input value if the unit types are the same
        if (from == to) {
            return out;
        }

        // If the unit type is the same, keep the conversion in that type
        if (from.getType() == to.getType()) {
            // English unit conversion
            if (from.getType() == Span.ENGLISH) {
                // Convert the input span into feet first
                switch (from) {
                    case MILE2:
                        out *= ConversionFactors.MILES_TO_FEET
                                * ConversionFactors.MILES_TO_FEET;
                        break;
                    case YARD2:
                        out *= ConversionFactors.YARDS_TO_FEET
                                * ConversionFactors.YARDS_TO_FEET;
                        break;
                    case FOOT2:
                        break;
                    default:
                        break;
                }

                // Out is now in feet, so convert it to the output unit
                switch (to) {
                    case MILE2:
                        out *= ConversionFactors.FEET_TO_MILES
                                * ConversionFactors.FEET_TO_MILES;
                        break;
                    case YARD2:
                        out *= ConversionFactors.FEET_TO_YARDS
                                * ConversionFactors.FEET_TO_YARDS;
                        break;
                    case FOOT2:
                        break;
                    default:
                        break;
                }
            } else if (from.getType() == Span.METRIC) {
                // Convert the input span into meters first
                switch (from) {
                    case KILOMETER2:
                        out *= ConversionFactors.KM_TO_METERS
                                * ConversionFactors.KM_TO_METERS;
                        break;
                    case METER2:
                        break;
                    default:
                        break;
                }

                // Out is now in meters, so convert it to the output unit
                switch (to) {
                    case KILOMETER2:
                        out *= ConversionFactors.METERS_TO_KM
                                * ConversionFactors.METERS_TO_KM;
                        break;
                    case METER2:
                        break;
                    default:
                        break;
                }
            }

        } else {
            // Convert the input span into meters first
            switch (from) {
                case MILE2:
                    out *= ConversionFactors.MILES_TO_METERS
                            * ConversionFactors.MILES_TO_METERS;
                    break;
                case YARD2:
                    out *= ConversionFactors.YARDS_TO_METERS
                            * ConversionFactors.YARDS_TO_METERS;
                    break;
                case FOOT2:
                    out *= ConversionFactors.FEET_TO_METERS
                            * ConversionFactors.FEET_TO_METERS;
                    break;
                case NAUTICALMILE2:
                    out *= ConversionFactors.NM_TO_METERS
                            * ConversionFactors.NM_TO_METERS;
                    break;
                case KILOMETER2:
                    out *= ConversionFactors.KM_TO_METERS
                            * ConversionFactors.KM_TO_METERS;
                    break;
                case METER2:
                    break;
                default:
                    // default does nothing
                    break;
            }

            // Out is now in meters, so convert it to the output unit
            switch (to) {
                case MILE2:
                    out *= ConversionFactors.METERS_TO_MILES
                            * ConversionFactors.METERS_TO_MILES;
                    break;
                case YARD2:
                    out *= ConversionFactors.METERS_TO_YARDS
                            * ConversionFactors.METERS_TO_YARDS;
                    break;
                case FOOT2:
                    out *= ConversionFactors.METERS_TO_FEET
                            * ConversionFactors.METERS_TO_FEET;
                    break;
                case NAUTICALMILE2:
                    out *= ConversionFactors.METERS_TO_NM
                            * ConversionFactors.METERS_TO_NM;
                    break;
                case ACRES:
                    out *= ConversionFactors.METERS_TO_ACRES;
                    break;
                case KILOMETER2:
                    out *= ConversionFactors.METERS_TO_KM
                            * ConversionFactors.METERS_TO_KM;
                    break;
                case METER2:
                    break;
                default:
                    break;
            }
        }

        return out;
    }

    /**
     * Computes the area of a list of points assuming to be closed and also assume to be in order.
     * @param points the list of points.
     * @return area in meters squared.
     */
    public static double calcShapeArea(GeoPoint[] points) {
        double sum = 0;
        double prevcolat = 0;
        double prevaz = 0;
        double colat0 = 0;
        double az0 = 0;
        for (int i = 0; i < points.length; i++) {
            double latRad = points[i].getLatitude()
                    * ConversionFactors.RADIANS_TO_DEGREES;
            double lonRad = points[i].getLongitude()
                    * ConversionFactors.RADIANS_TO_DEGREES;
            double colat = 2 * Math.atan2(Math.sqrt(
                    Math.pow(Math.sin(latRad / 2), 2) +
                            Math.cos(latRad) *
                                    Math.pow(Math.sin(lonRad / 2), 2)),
                    Math.sqrt(
                            1 - Math.pow(Math.sin(latRad / 2), 2) -
                                    Math.cos(latRad) *
                                            Math.pow(Math.sin(lonRad / 2), 2)));
            double az;
            if (points[i].getLatitude() >= 90) {
                az = 0;
            } else if (points[i].getLatitude() <= -90) {
                az = Math.PI;
            } else {
                az = Math.atan2(Math.cos(latRad) * Math.sin(lonRad),
                        Math.sin(latRad)) % (2 * Math.PI);
            }
            if (i == 0) {
                colat0 = colat;
                az0 = az;
            }
            if (i > 0 && i < points.length) {
                sum = sum + (1 - Math.cos(prevcolat + (colat - prevcolat) / 2))
                        *
                        Math.PI * ((Math.abs(az - prevaz) / Math.PI) -
                                2 * Math.ceil(
                                        ((Math.abs(az - prevaz) / Math.PI) - 1)
                                                / 2))
                        *
                        Math.signum(az - prevaz);
            }
            prevcolat = colat;
            prevaz = az;
        }
        sum = sum + (1 - Math.cos(prevcolat + (colat0 - prevcolat) / 2))
                * (az0 - prevaz);
        return 5.10072E14 * Math.min(Math.abs(sum) / 4 / Math.PI,
                1 - Math.abs(sum) / 4 / Math.PI);
    }

    /**
     * Computes the area of a rectangle using the most popular algorithm hxw
     * @param h height
     * @param w width
     * @return area in meters squared.
     */
    public static double calcRectArea(final double h, final double w) {
        return h * w;
    }

    /**
     * Computes the area of a circle using the most popular algorithm pies are squared
     * @param r radius
     * @return area in meters squared.
     */
    public static double calcCircleArea(final double r) {
        return Math.PI * r * r;
    }

}
