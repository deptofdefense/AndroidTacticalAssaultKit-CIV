
package com.atakmap.coremap.cot.event;

/**
 * Thrown when a CotEvent is missing required attributes or is malformed when parsing or building to
 * XML.
 * 
 * 
 */
public class CotIllegalException extends Exception {

    public CotIllegalException(String msg) {
        super(msg);
    }

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

}
