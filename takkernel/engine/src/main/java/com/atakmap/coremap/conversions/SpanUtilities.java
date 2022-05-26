
package com.atakmap.coremap.conversions;

import java.text.DecimalFormat;
import com.atakmap.coremap.locale.LocaleUtil;

/**
 * Format spans given a value and a Span type.
 * 
 */
public class SpanUtilities {

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

    private static int ft_to_m_threshold = (int) ConversionFactors.MILES_TO_FEET;
    private static int m_to_km_threshold = 2000;

    /**
     * Sets the threshold used, that when reached that when calls to SpanUtilities will switch
     * from return feet to miles.
     */
    public static void setFeetToMileThreshold(int value) {
        ft_to_m_threshold = value;
    }

    /**
     * Sets the threshold used, that when reached that when calls to SpanUtilities will switch
     * from return feet to miles.
     */
    public static void setMetersToKilometersThreshold(int value) {
        m_to_km_threshold = value;
    }

    /**
     * Provided a value and a span, format the value based on common formatting
     * within the ATAK system.
     * @param value the value in the specified units
     * @param units the unit that describes the value
     * @return the formatted string that represents the value in the same unit 
     * type.   For example if the value is meters, then the return type would 
     * metric in either meters or kilometers.
     */
    public static String formatType(double value, Span units) {
        return formatType(units.getType(), value, units);
    }

    /**
     * @param unitsType one of Span.METRIC, Span.ENGLISH, Span.NM
     * @param value the value in the specified units
     * @param units the unit that describes the value
     * @return the formatted string that represents the value in a new unit
     * type of either METRIC, ENGLISH, or NM.
     */
    public static String formatType(int unitsType, double value, Span units) {
        return formatType(unitsType, value, units, false);
    }

    /**
     * Provides a formatted display of a given distance value.
     * @param unitsType one of Span.METRIC, Span.ENGLISH, Span.NM
     * @param value the value in the specified units
     * @param units the unit that describes the value
     * @param showDecimalFtM indicates if the return formatting should provide 
     * fractional feet/meters in the display.
     * @return the formatted string that represents the value in a new unit
     * type of either METRIC, ENGLISH, or NM.   
     */
    public static String formatType(int unitsType, double value, Span units,
            boolean showDecimalFtM) {
        double out;
        Span outSpan;
        switch (unitsType) {
            case Span.METRIC:
                out = convert(value, units, Span.METER);
                if (out > m_to_km_threshold) {
                    // Change to kilometers
                    out = convert(out, Span.METER, Span.KILOMETER);
                    outSpan = Span.KILOMETER;
                } else {
                    // Keep as meters
                    outSpan = Span.METER;
                }
                break;
            case Span.ENGLISH:
                out = convert(value, units, Span.FOOT);
                if (out > ft_to_m_threshold) {
                    out = convert(out, Span.FOOT, Span.MILE);
                    outSpan = Span.MILE;
                } else {
                    // Keep as feet
                    outSpan = Span.FOOT;
                }
                break;
            case Span.NM:
                out = convert(value, units, Span.NAUTICALMILE);
                outSpan = Span.NAUTICALMILE;
                break;
            default:
                return "Formatting Error";
        }

        // we do not do fractional meters/feet in this program for display to the user.
        if (outSpan == Span.METER || outSpan == Span.FOOT) {
            if (showDecimalFtM)
                return ONE_DEC_FORMAT.format(out) + " " + outSpan.getAbbrev();
            else
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
     * Provides a formatted display of a given distance value.
     * @param value the value in the specified units
     * @param units the unit that describes the value
     * @param decimalPoints indicates the precision of the return
     * fractional feet/meters in the display.
     * @return the formatted string that represents the value in the same 
     * type as the value passed in, meters would be metric, etc.
     */
    public static String formatType(double value, Span units,
            int decimalPoints) {
        return formatType(units.getType(), value, units, decimalPoints);
    }

    /**
     * Provides a formatted display of a given distance value.
     * @param unitsType one of Span.METRIC, Span.ENGLISH, Span.NM
     * @param value the value in the specified units
     * @param units the unit that describes the value
     * @param decimalPoints indicates the precision of the formatted return value.
     * How many decimal places to print.
     * fractional feet/meters in the display.
     * @return the formatted string that represents the value in a new unit
     * type of either METRIC, ENGLISH, or NM.
     */
    public static String formatType(int unitsType, double value, Span units,
            int decimalPoints) {
        double out;
        switch (unitsType) {
            case Span.METRIC:
                out = convert(value, units, Span.METER);
                if (out > m_to_km_threshold) {
                    // Change to kilometers
                    return format(out, Span.METER, Span.KILOMETER,
                            decimalPoints);
                } else {
                    // Keep as meters
                    return format(out, Span.METER, Span.METER, decimalPoints);
                }
            case Span.ENGLISH:
                out = convert(value, units, Span.FOOT);
                if (out > ft_to_m_threshold) {
                    return format(out, Span.FOOT, Span.MILE, decimalPoints);
                } else {
                    // Keep as feet
                    return format(out, Span.FOOT, Span.FOOT, decimalPoints);
                }
            case Span.NM:
                return format(value, units, Span.NAUTICALMILE, decimalPoints);
        }
        return "Formatting Error";
    }

    /**
     * @param value the value in the specified units
     * @param units the unit that describes the value
     * @return the common representation for the value and units.
     */
    public static String format(double value, Span units) {
        return format(value, units, units);
    }

    /**
     * @param value the value in the specified units
     * @param from the unit that describes the value
     * @param to the units to convert to
     * @return the common representation for the value after conversion
     */
    public static String format(double value, Span from, Span to) {
        // convert the span to the output format
        double out = convert(value, from, to);

        // we do not do fractional meters/feet in this program for display to the user.
        if (to == Span.METER || to == Span.FOOT) {
            return NO_DEC_FORMAT.format(Math.round(out)) + " " + to.getAbbrev();
        }

        if (out < 100) {
            return TWO_DEC_FORMAT.format(Math.round(out * 100) / 100d) + " "
                    + to.getAbbrev();
        } else if (out < 1000) {
            return ONE_DEC_FORMAT.format(Math.round(out * 10) / 10d) + " "
                    + to.getAbbrev();
        } else {
            return NO_DEC_FORMAT.format(Math.round(out)) + " " + to.getAbbrev();
        }
    }

    /**
     * Given a value format it into the correct number of decimal points with the correct span label.
     * @param value the value
     * @param units the units to use for formatting
     * @param decimalPoints the number of decimals
     * @return the string with the correct units with the specified number of decimal points.
     */
    public static String format(double value, Span units, int decimalPoints) {
        return format(value, units, units, decimalPoints);
    }

    /**
     * Given a value format it into the correct number of decimal points with the correct span label.
     * @param value the value
     * @param from the unit of the value
     * @param to the unit to conver to
     * @param decimalPoints the number of decimals
     * @return the string with the correct units with the specified number of decimal points.
     */
    public static String format(double value, Span from, Span to,
            int decimalPoints) {
        // convert the span to the output format
        double out = convert(value, from, to);
        // format the string based on the decimalPoints passed in
        switch (decimalPoints) {
            case 1:
                return ONE_DEC_FORMAT.format(Math.round(out * 10) / 10d) + " "
                        + to.getAbbrev();
            case 2:
                return TWO_DEC_FORMAT.format(Math.round(out * 100) / 100d)
                        + " " + to.getAbbrev();
            case 3:
                return THREE_DEC_FORMAT.format(Math.round(out * 1000) / 1000d)
                        + " " + to.getAbbrev();
            case 4:
                return FOUR_DEC_FORMAT.format(Math.round(out * 10000) / 10000d)
                        + " " + to.getAbbrev();
            default:
                return NO_DEC_FORMAT.format(Math.round(out)) + " "
                        + to.getAbbrev();
        }
    }

    /**
     /**
     * Given a value format it correctly.
     * @param value the value
     * @param units the unit of the value
     * @param format the format string to be used
     * @return the string with the correct units as indicated by the format string using standard
     * decimal formatting notation.
     */
    public static String formatType(double value, Span units, String format) {
        return formatType(units.getType(), value, units, format);
    }

    /**
     * @param value
     * @param units
     * @param unitsType
     * @param format
     * @return
     */
    public static String formatType(int unitsType, double value, Span units,
            String format) {
        double out;
        switch (unitsType) {
            case Span.METRIC:
                out = convert(value, units, Span.METER);
                if (out > m_to_km_threshold) {
                    // Change to kilometers
                    return format(out, Span.METER, Span.KILOMETER, format);
                } else {
                    // Keep as meters
                    return format(out, Span.METER, Span.METER, format);
                }
            case Span.ENGLISH:
                out = convert(value, units, Span.FOOT);
                if (out > ft_to_m_threshold) {
                    return format(out, Span.FOOT, Span.MILE, format);
                } else {
                    // Keep as feet
                    return format(out, Span.FOOT, Span.FOOT, format);
                }
            case Span.NM:
                return format(value, units, Span.NAUTICALMILE, format);
        }
        return "Formatting Error";
    }

    /**
     * @param value
     * @param units
     * @param format
     * @return
     */
    public static String format(double value, Span units, String format) {
        return format(value, units, units, format);
    }

    /**
     * @param value
     * @param from
     * @param to
     * @param format
     * @return
     */
    public static String format(double value, Span from, Span to,
            String format) {
        // convert the span to the output format
        double out = convert(value, from, to);
        // create a decimalformat from the format string passed in
        DecimalFormat f = LocaleUtil.getDecimalFormat(format);
        // format the value into a string
        String formatted = f.format(out);
        // Return the formatted value with a spaced abbreviation
        return formatted + " " + to.getAbbrev();
    }

    /**
     * Provided for the purposes of our international versions of TAK.
     * @param value
     * @param from
     * @param to
     */
    public static double convert(final String value, final Span from,
            final Span to) {
        return convert(Double.parseDouble(LocaleUtil.getNaturalNumber(value)),
                from, to);
    }

    /**
     * @param value
     * @param from
     * @param to
     * @return
     */
    public static double convert(double value, Span from, Span to) {
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
                    case MILE:
                        out *= ConversionFactors.MILES_TO_FEET;
                        break;
                    case YARD:
                        out *= ConversionFactors.YARDS_TO_FEET;
                        break;
                    case FOOT:
                        break;
                    default:
                        break;
                }

                // Out is now in feet, so convert it to the output unit
                switch (to) {
                    case MILE:
                        out *= ConversionFactors.FEET_TO_MILES;
                        break;
                    case YARD:
                        out *= ConversionFactors.FEET_TO_YARDS;
                        break;
                    case FOOT:
                        break;
                    default:
                        break;
                }
            } else if (from.getType() == Span.METRIC) {
                // Convert the input span into meters first
                switch (from) {
                    case KILOMETER:
                        out *= ConversionFactors.KM_TO_METERS;
                        break;
                    case METER:
                        break;
                    default:
                        break;
                }

                // Out is now in meters, so convert it to the output unit
                switch (to) {
                    case KILOMETER:
                        out *= ConversionFactors.METERS_TO_KM;
                        break;
                    case METER:
                        break;
                    default:
                        break;
                }
            }

        } else {
            // Convert the input span into meters first
            switch (from) {
                case MILE:
                    out *= ConversionFactors.MILES_TO_METERS;
                    break;
                case YARD:
                    out *= ConversionFactors.YARDS_TO_METERS;
                    break;
                case FOOT:
                    out *= ConversionFactors.FEET_TO_METERS;
                    break;
                case NAUTICALMILE:
                    out *= ConversionFactors.NM_TO_METERS;
                    break;
                case KILOMETER:
                    out *= ConversionFactors.KM_TO_METERS;
                    break;
                case METER:
                    break;
                default:
                    // default does nothing
                    break;
            }

            // Out is now in meters, so convert it to the output unit
            switch (to) {
                case MILE:
                    out *= ConversionFactors.METERS_TO_MILES;
                    break;
                case YARD:
                    out *= ConversionFactors.METERS_TO_YARDS;
                    break;
                case FOOT:
                    out *= ConversionFactors.METERS_TO_FEET;
                    break;
                case NAUTICALMILE:
                    out *= ConversionFactors.METERS_TO_NM;
                    break;
                case KILOMETER:
                    out *= ConversionFactors.METERS_TO_KM;
                    break;
                case METER:
                    break;
                default:
                    break;
            }
        }

        return out;
    }

}
