
package com.atakmap.coremap.maps.coords;

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

}
