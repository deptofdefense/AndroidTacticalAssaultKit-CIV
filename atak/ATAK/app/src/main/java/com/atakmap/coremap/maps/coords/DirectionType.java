
package com.atakmap.coremap.maps.coords;

/**
 * Allows for conversion between angular direction in degrees and the ordinal name.
 */
public enum DirectionType {
    // The ordinal values of these enums match up with the hyde native messaging
    // The int value is the angle of direction used in calculations
    NORTH(0, "N"),
    NORTHEAST(45, "NE"),
    EAST(90, "E"),
    SOUTHEAST(135, "SE"),
    SOUTH(180, "S"),
    SOUTHWEST(225, "SW"),
    WEST(270, "W"),
    NORTHWEST(315, "NW"),
    UNKNOWN(Double.NaN, "UNK");

    private final String abbreviation;
    private final double angle;

    DirectionType(double angle, String abbreviation) {
        this.angle = angle;
        this.abbreviation = abbreviation;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public double getAngle() {
        return angle;
    }

    /**
     * Provides for the ability to covert degrees into an ordinal type.
     * @param inBearing the value in degrees from 1-360
     * @return the direction type given a bearing.
     */
    public static DirectionType getDirection(double inBearing) {
        if (inBearing < 0) {
            inBearing += 360;
        }

        if (inBearing < 22.5) {
            return NORTH;
        } else if (inBearing < 67.5) {
            return NORTHEAST;
        } else if (inBearing < 112.5) {
            return EAST;
        } else if (inBearing < 157.5) {
            return SOUTHEAST;
        } else if (inBearing < 202.5) {
            return SOUTH;
        } else if (inBearing < 247.5) {
            return SOUTHWEST;
        } else if (inBearing < 292.5) {
            return WEST;
        } else if (inBearing < 337.5) {
            return NORTHWEST;
        } else {
            return NORTH;
        }
    }

    /**
     * @return the direction type enumeration derived from the corresponding abbreviation
     */
    public static DirectionType findFromAbbrev(final String abbrev) {
        for (DirectionType dt : DirectionType.values()) {
            if (dt.abbreviation.equalsIgnoreCase(abbrev)) {
                return dt;
            }
        }
        return UNKNOWN;
    }

}
