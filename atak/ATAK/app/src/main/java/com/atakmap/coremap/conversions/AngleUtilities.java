
package com.atakmap.coremap.conversions;

import java.text.DecimalFormat;

import com.atakmap.coremap.locale.LocaleUtil;

/**
 * Provides common utilities for formatting and converting Angles within ATAK.
 */
public class AngleUtilities {

    /**
     * Wrap degree value to range 1 - 360 and add degree sign
     * @param value Angle degree value
     * @return Angle as string
     */
    public static String format(final double value) {
        return format(value, Angle.DEGREE);
    }

    /**
     * @param value The value as a raw number
     * @param units The unit unit for the raw number
     * @return a properly formatted string representing the raw number in the correct units.
     */
    public static String format(final double value, final Angle units) {
        return format(value, Angle.DEGREE, units);
    }

    /**
     * @param value The value as a raw number
     * @param units The unit unit for the raw number
     * @param isBearing if the value is used for bearing  1-360 for the range (isBearing = true)
     * @return a properly formatted string representing the raw number in the correct units.
     */
    public static String format(final double value, final Angle units,
            final boolean isBearing) {
        return format(value, Angle.DEGREE, units, isBearing);
    }

    /**
     * @param value The value as a raw number
     * @param from The unit unit for the raw number
     * @param to is the unit to convert to prior to formatting.
     * @return a properly formatted string representing the raw number in the correct units.
     */
    public static String format(final double value, final Angle from,
            final Angle to) {
        return format(value, from, to, true);
    }

    /**
     * @param value The value as a raw number
     * @param from The unit unit for the raw number
     * @param to is the unit to convert to prior to formatting.
     * @return a properly formatted string in the correct units without any unit label or decimal places.
     */
    public static String formatNoUnitsNoDecimal(final double value,
            final Angle from,
            final Angle to) {
        double out = convert(value, from, to);
        if (to == Angle.DEGREE)
            out = roundDeg(out, 0);

        return NO_DEC_FORMAT.format(out);
    }

    /**
     * @param value a value as a raw number
     * @param from the unit for the value
     * @param to the units to convert to prior to formatting
     * @param isBearing if the unit is used for bearing
     * @return formatted the value in the requested new unit.
     */
    public static String format(final double value, final Angle from,
            final Angle to,
            final boolean isBearing) {
        // Convert to the appropriate units
        double out = convert(value, from, to);
        if (to == Angle.DEGREE)
            out = roundDeg(out, isBearing);
        return NO_DEC_FORMAT.format(Math.round(out))
                + (to == Angle.DEGREE ? "" : " ") + to.getAbbrev();
    }

    /**
     * @param value a raw number in Angle.DEGREE
     * @param decimalPoints the number of decimal points to use when formatting
     * @return the properly formated string of the DEGREE with the requested number of decimal points.
     */
    public static String format(final double value, final int decimalPoints) {
        return format(value, Angle.DEGREE, decimalPoints);
    }

    /**
     * @param value a raw number described by the units
     * @param units The unit describing the value
     * @param decimalPoints the number of decimal points to use when formatting
     * @return the properly formatted string of the DEGREE with the requested number of decimal points.
     */
    public static String format(final double value, final Angle units,
            final int decimalPoints) {
        return format(value, Angle.DEGREE, units, decimalPoints);
    }

    /**
     * @param value a raw number describe by the supplied from field
     * @param from the units describing the value
     * @param to the unit to convert to prior to formatting
     * @param decimalPoints the number of decimal points to use during formatting where it is no
     *                      greater than 4.
     * @return the properly string in the requested units with the correct number of decimal points.
     */
    public static String format(double value, Angle from, Angle to,
            int decimalPoints) {
        // Convert to the appropriate units
        double out = convert(value, from, to);
        // return the requested decimal points
        // not going to go past 4
        if (to == Angle.DEGREE)
            out = roundDeg(out, decimalPoints);
        switch (decimalPoints) {
            case 1:
                return ONE_DEC_FORMAT.format(out) + " " + to.getAbbrev();
            case 2:
                return TWO_DEC_FORMAT.format(out) + " " + to.getAbbrev();
            case 3:
                return THREE_DEC_FORMAT.format(out) + " " + to.getAbbrev();
            case 4:
                return FOUR_DEC_FORMAT.format(out) + " " + to.getAbbrev();
            default:
                return NO_DEC_FORMAT.format(out) + " " + to.getAbbrev();
        }
    }

    /**
     /**
     * @param value a raw number describe by the supplied from field
     * @param from the units describing the value
     * @param to the unit to convert to prior to formatting
     * @return a raw number representing the angle in the new units.
     */
    public static double convert(final double value, final Angle from,
            final Angle to) {
        double out = value;

        if (from == to) {
            return out;
        }

        switch (from) {
            case DEGREE:
                break;
            case MIL:
                out *= ConversionFactors.MRADIANS_TO_DEGREES;
                break;
            case RADIAN:
                out *= ConversionFactors.DEGREES_TO_RADIANS;
                break;
        }

        switch (to) {
            case DEGREE:
                break;
            case MIL:
                out *= ConversionFactors.DEGREES_TO_MRADIANS;
                break;
            case RADIAN:
                out *= ConversionFactors.RADIANS_TO_DEGREES;
                break;
        }
        return out;
    }

    /**
     * Wrap degree value to range
     * @param deg Degree value
     * @param isBearing True to use 1 to 360 wrapping, false to use -180 to 180
     * @return Degree between range
     */
    private static double wrapDeg(double deg, final boolean isBearing) {
        if (isBearing) {
            deg = (deg % 360) + (deg < 0 ? 360 : 0);
            if (deg < 1)
                deg += 360;
        } else {
            deg = deg % 360;
            if (deg > 180)
                deg = -180 + (deg % 180);
            else if (deg < -180)
                deg = 180 + (deg % 180);
        }
        return deg;
    }

    /**
     * Wrap degree value to range
     * @param deg Degree value
     * @return Degree between range using 1-360 for the range (isBearing = true)
     */
    public static double wrapDeg(double deg) {

        return wrapDeg(deg, true);
    }

    /**
     * Return rounded degree value
     * @param deg Degree value
     * @param dec Number of decimal places to round to
     * @return Degree between 1 and 360
     */
    public static double roundDeg(double deg, final int dec,
            final boolean isBearing) {
        double exp = Math.pow(10, dec);
        deg = Math.round(wrapDeg(deg, isBearing) * exp) / exp;
        if (deg >= 361)
            deg -= 360;
        return deg;
    }

    /**
     * Return rounded degree value
     * @param deg Degree value
     * @param dec Number of decimal places to round to
     */
    public static double roundDeg(final double deg, final int dec) {
        return roundDeg(deg, dec, true);
    }

    /**
     * Return rounded degree value
     * @param deg Degree value
     * @param isBearing 1-360 for the range (isBearing = true)
     * @return the rounded degree value with no decimal places.
     */
    public static int roundDeg(final double deg, final boolean isBearing) {
        return (int) roundDeg(deg, 0, isBearing);
    }

    /**
     * Return rounded degree value
     * @param deg Degree value
     * @return Degree between 1 and 360 with no decimal places.
     */
    public static int roundDeg(double deg) {
        return (int) roundDeg(deg, 0);
    }

    // DECIMAL FORMAT
    private static final DecimalFormat NO_DEC_FORMAT = LocaleUtil
            .getDecimalFormat(
                    "###,##0");
    private static final DecimalFormat ONE_DEC_FORMAT = LocaleUtil
            .getDecimalFormat(
                    "###,##0.0");
    private static final DecimalFormat TWO_DEC_FORMAT = LocaleUtil
            .getDecimalFormat(
                    "###,##0.00");
    private static final DecimalFormat THREE_DEC_FORMAT = LocaleUtil
            .getDecimalFormat(
                    "###,##0.000");
    private static final DecimalFormat FOUR_DEC_FORMAT = LocaleUtil
            .getDecimalFormat(
                    "#,###,###,##0.0000");
}
