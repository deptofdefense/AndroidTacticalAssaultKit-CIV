
package com.atakmap.coremap.conversions;

/**
 * Provides an enumeration for the value of the angle.
 */
public enum Angle {

    DEGREE(0, "\u00B0", "Degrees"),
    MIL(1, "mils", "Angular mil"),
    RADIAN(2, "rad", "Radians");

    public static final String DEGREE_SYMBOL = "\u00B0";

    private final int _value;
    private final String _abbrev;
    private final String _name;

    Angle(int value, String abbrev, String name) {
        _value = value;
        _abbrev = abbrev;
        _name = name;
    }

    /**
     * @return the integer value representation of the enumeration.
     */
    public int getValue() {
        return _value;
    }

    /**
     * @return the abbreviated string for the enumeration
     */
    public String getAbbrev() {
        return _abbrev;
    }

    /**
     * @return the full string representation of the enumeration
     */
    public String getName() {
        return _name;
    }

    @Override
    public String toString() {
        return _name;
    }

    /**
     * @param value the integer value to be turned back into an enumerated type.
     * @return the enumerated type, null if the value is not representable.
     */
    public static Angle findFromValue(int value) {
        for (Angle a : Angle.values()) {
            if (a._value == value) {
                return a;
            }
        }
        return null;
    }

    /**
     * @param abbrev the abbreviation to be turned back into a enumerated type.
     * @return the enumerated type, null if the abbrev is not representable.
     */
    public static Angle findFromAbbrev(final String abbrev) {
        for (Angle a : Angle.values()) {
            if (a._abbrev.equalsIgnoreCase(abbrev)) {
                return a;
            }
        }
        return null;
    }
}
