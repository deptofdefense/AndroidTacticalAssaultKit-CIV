
package com.atakmap.coremap.conversions;

/**
 * Commonly used terms for the measurement of Area.
 */
public enum Area {

    KILOMETER2(0, 1, "kilometer squared", "kilometers squared", "km\u00B2"),
    METER2(1, 1, "meter squared", "meters squared", "m\u00B2"),
    MILE2(2, 0, "mile squared", "miles squared", "mi\u00B2"),
    YARD2(3, 0, "yard squared", "yards squared", "yd\u00B2"),
    FOOT2(4, 0, "foot squared", "feet squared", "ft\u00B2"),
    NAUTICALMILE2(5,
            2,
            "nautical mile squared",
            "nautical miles squared",
            "NM\u00B2"),
    ACRES(6, 0, "acres", "acres", "acres");

    public static final int ENGLISH = Span.ENGLISH; // matches span
    public static final int METRIC = Span.METRIC; // matches span
    public static final int NM = Span.NM; // matches span
    public static final int AC = 3; //acres

    private final int _type; //type of units (english, metric, nm)
    private final String _singular; //singular name
    private final String _plural; //plural name
    private final String _abbrev; //abbreviation
    private final int _value; //index

    Area(final int value, final int type, final String singular,
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

    public static Area findFromPluralName(String pluralName) {
        for (Area s : Area.values()) {
            if (s._plural.equals(pluralName))
                return s;
        }
        return null;
    }

    public static Area findFromAbbrev(final String abbrev) {
        for (Area s : Area.values()) {
            if (s._abbrev.equalsIgnoreCase(abbrev))
                return s;
        }
        return null;
    }

    public static Area findFromValue(final int value) {
        for (Area s : Area.values()) {
            if (s._value == value)
                return s;
        }
        return null;
    }

}
