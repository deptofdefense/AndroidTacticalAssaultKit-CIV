
package com.atakmap.coremap.cot.event;

/**
 * An attribute of a detail
 * 
 * 
 */
public class CotAttribute {

    private final String _name;
    private final String _value;

    /**
     * Create a new attribute
     * 
     * @param name the name of the attribute
     * @param value the value of the attribute
     */
    CotAttribute(final String name, final String value) {
        //XXX-- do we allow a null attribute name? (see CotAttributeTest Contract Questions)
        _name = name;
        _value = value;
    }

    /**
     * Get this attribute's name
     * 
     * @return the attribute name
     */
    public String getName() {
        return _name;
    }

    /**
     * Get this attribute's value
     * 
     * @return the attribute value as a string, empty if no attribute value exists.
     */
    public String getValue() {
        if (_value != null)
            return _value;
        else
            return "";
    }
}
