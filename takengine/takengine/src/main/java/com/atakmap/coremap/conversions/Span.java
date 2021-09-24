
package com.atakmap.coremap.conversions;

/**
 * Designate valid span types within ATAK.
 */
public enum Span {

    KILOMETER(0, 1, "kilometer", "kilometers", "km"),
    METER(1, 1, "meter", "meters", "m"),
    MILE(2, 0, "mile", "miles", "mi"),
    YARD(3, 0, "yard", "yards", "yd"),
    FOOT(4, 0, "foot", "feet", "ft"),
    NAUTICALMILE(5, 2, "nautical mile", "nautical miles", "NM");

    public static final int ENGLISH = 0;
    public static final int METRIC = 1;
    public static final int NM = 2;

    private final int _type; //type of units (english, metric, nm)
    private final String _singular; //singular name
    private final String _plural; //plural name
    private final String _abbrev; //abbreviation
    private final int _value; //index

    Span(final int value, final int type, final String singular,
            final String plural, final String abbrev) {
        _type = type;
        _singular = singular;
        _plural = plural;
        _abbrev = abbrev;
        _value = value;
    }

    public int getValue() {
        return _value;
    }

    public int getType() {
        return _type;
    }

    public String getSingular() {
        return _singular;
    }

    public String getPlural() {
        return _plural;
    }

    public String getAbbrev() {
        return _abbrev;
    }

    @Override
    public String toString() {
        return _plural;
    }

    public static Span findFromPluralName(String pluralName) {
        for (Span s : Span.values()) {
            if (pluralName != null && s._plural.equals(pluralName))
                return s;
        }
        return null;
    }

    public static Span findFromAbbrev(final String abbrev) {
        for (Span s : Span.values()) {
            if (abbrev != null && s._abbrev.equalsIgnoreCase(abbrev))
                return s;
        }
        return null;
    }

    public static Span findFromValue(final int value) {
        for (Span s : Span.values()) {
            if (s._value == value)
                return s;
        }
        return null;
    }

}
