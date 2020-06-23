
package com.atakmap.android.gpx;

import org.simpleframework.xml.Element;

/**
 * GPX base type
 * 
 * 
 */
public abstract class GpxBase {

    /**
     * The GPS name of the waypoint. This field will be transferred to and from the GPS. GPX does
     * not place restrictions on the length of this field or the characters contained in it. It is
     * up to the receiving application to validate the field before sending it to the GPS.
     */
    @Element(required = false)
    private String name;

    /**
     * GPS waypoint comment. Sent to GPS as comment.
     */
    @Element(required = false)
    private String cmt;

    /**
     * A text description of the element. Holds additional information about the element intended
     * for the user, not the GPS.
     */
    @Element(required = false)
    private String desc;

    /**
     * Source of data. Included to give user some idea of reliability and accuracy of data.
     * "Garmin eTrex", "USGS quad Boston North", e.g.
     */
    @Element(required = false)
    private String src;

    // TODO build out linkType schema
    /**
     * Link to additional information about the waypoint.
     */
    // @Element
    // private String link;

    /**
     * Type (classification) of the waypoint.
     */
    @Element(required = false)
    private String type;

    // <xsd:element name="extensions" type="extensionsType" minOccurs="0">
    // You can add extend GPX by adding your own elements from another schema here.

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCmt() {
        return cmt;
    }

    public void setCmt(String cmt) {
        this.cmt = cmt;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getSrc() {
        return src;
    }

    public void setSrc(String src) {
        this.src = src;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
