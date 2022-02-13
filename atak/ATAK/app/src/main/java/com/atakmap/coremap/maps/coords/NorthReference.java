
package com.atakmap.coremap.maps.coords;

import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.conversions.Angle;
import com.atakmap.coremap.conversions.AngleUtilities;

/**
 * 
 */
public enum NorthReference {

    TRUE(0, "T", "True North"),
    MAGNETIC(1, "M", "Magnetic North"),
    GRID(2, "G", "Grid North"),;

    private final int _value;
    private final String _abbrev;
    private final String _name;

    NorthReference(final int value, final String abbrev,
            final String name) {
        _value = value;
        _abbrev = abbrev;
        _name = name;
    }

    /**
     * Gets the integer representation of the NorthReference.
     */
    public int getValue() {
        return _value;
    }

    /**
     * Gets a human readable short string representation of the NorthReference
     */
    public String getAbbrev() {
        return _abbrev;
    }

    /**
     * Gets the human readable full string representation of the NorthReference
     */
    public String getName() {
        return _name;
    }

    @Override
    public String toString() {
        return _name;
    }

    /**
     * @param value the integer representation of the north reference
     * @return the north reference
     */
    public static NorthReference findFromValue(final int value) {
        for (NorthReference nr : NorthReference.values()) {
            if (nr._value == value) {
                return nr;
            }
        }
        return null;
    }

    /**
     * @param abbrev the abbreviation for the north reference
     * @return the north reference based on the abbreviation
     */
    public static NorthReference findFromAbbrev(final String abbrev) {
        for (NorthReference nr : NorthReference.values()) {
            if (nr._abbrev.equalsIgnoreCase(abbrev)) {
                return nr;
            }
        }
        return null;
    }

    /**
     * Convert from degrees in one north reference to another
     *
     * @param deg Degrees in input north reference
     * @param point Reference point (used in mag and grid calc)
     * @param range Range in meters (used in grid calc)
     * @param from Input north reference
     * @param to Output north reference
     * @return Degrees in output north reference
     */
    public static double convert(double deg, GeoPoint point, double range,
            NorthReference from, NorthReference to) {

        // Nothing to do
        if (from == to)
            return deg;

        // First convert degrees to true north
        double trueDeg = deg;
        switch (from) {
            case MAGNETIC:
                trueDeg = ATAKUtilities.convertFromMagneticToTrue(point, deg);
                break;
            case GRID:
                trueDeg += ATAKUtilities.computeGridConvergence(point, trueDeg,
                        range);
                break;
        }

        // Then convert to output reference
        switch (to) {
            case MAGNETIC:
                trueDeg = ATAKUtilities.convertFromTrueToMagnetic(point,
                        trueDeg);
                break;
            case GRID:
                trueDeg -= ATAKUtilities.computeGridConvergence(point, trueDeg,
                        range);
                break;
        }
        return AngleUtilities.wrapDeg(trueDeg);
    }

    /**
     * Format degrees to a specific north reference
     *
     * @param trueDeg Degrees value (true north)
     * @param point Reference point (used in mag and grid calc)
     * @param range Range in meters (used in grid calc)
     * @param units Units for output format
     * @param northRef North reference
     * @param decimalPoints Number of decimal points in output format
     * @return Formatted angle string
     */
    public static String format(double trueDeg, GeoPoint point, double range,
            Angle units, NorthReference northRef, int decimalPoints) {
        double deg = convert(trueDeg, point, range, NorthReference.TRUE,
                northRef);
        return AngleUtilities.format(deg, units, decimalPoints)
                + northRef.getAbbrev();
    }

    /**
     * Format degrees to a specific north reference
     *
     * @param trueDeg Degrees value (true north)
     * @param point Reference point (used in mag and grid calc)
     * @param units Units for output format
     * @param northRef North reference
     * @param decimalPoints Number of decimal points in output format
     * @return Formatted angle string
     */
    public static String format(double trueDeg, GeoPoint point, Angle units,
            NorthReference northRef, int decimalPoints) {
        return format(trueDeg, point, 1, units, northRef, decimalPoints);
    }
}
