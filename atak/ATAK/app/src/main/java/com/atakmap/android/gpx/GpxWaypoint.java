
package com.atakmap.android.gpx;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * GPX wptType wpt represents a waypoint, point of interest, or named feature on a map.
 * 
 * 
 */
public class GpxWaypoint extends GpxBase {

    /**
     * Elevation (in meters) of the point.
     */
    @Element(required = false)
    private BigDecimal ele;

    // TODO dateTime type
    /**
     * Creation/modification timestamp for element. Date and time in are in Univeral Coordinated
     * Time (UTC), not local time! Conforms to ISO 8601 specification for date/time representation.
     * Fractional seconds are allowed for millisecond timing in tracklogs.
     */
    @Element(required = false)
    private String time;

    /**
     * Magnetic variation (in degrees) at the point
     */
    @Element(required = false)
    private BigDecimal magvar;

    /**
     * Height (in meters) of geoid (mean sea level) above WGS84 earth ellipsoid. As defined in NMEA
     * GGA message.
     */
    @Element(required = false)
    private BigDecimal geoidheight;

    /**
     * Text of GPS symbol name. For interchange with other programs, use the exact spelling of the
     * symbol as displayed on the GPS. If the GPS abbreviates words, spell them out.
     */
    @Element(required = false)
    private String sym;

    /**
     * Type of GPX fix.
     */
    @Element(required = false)
    private String fix;

    /**
     * Number of satellites used to calculate the GPX fix.
     */
    @Element(required = false)
    private Integer sat;

    /**
     * Horizontal dilution of precision.
     */
    @Element(required = false)
    private BigDecimal hdop;

    /**
     * Vertical dilution of precision.
     */
    @Element(required = false)
    private BigDecimal vdop;

    /**
     * Position dilution of precision.
     */
    @Element(required = false)
    private BigDecimal pdop;

    /**
     * Number of seconds since last DGPS update.
     */
    @Element(required = false)
    private BigDecimal ageofdgpsdata;

    // TODO many of these are types with range/bounds restrictions
    /**
     * ID of DGPS station used in differential correction.
     */
    @Element(required = false)
    private Integer dgpsid;

    // <xsd:element name="extensions" type="extensionsType" minOccurs="0">
    // You can add extend GPX by adding your own elements from another schema here.

    /**
     * The latitude of the point. This is always in decimal degrees, and always in WGS84 datum.
     */
    @Attribute(required = true)
    private BigDecimal lat;

    /**
     * The longitude of the point. This is always in decimal degrees, and always in WGS84 datum.
     */
    @Attribute(required = true)
    private BigDecimal lon;

    public BigDecimal getEle() {
        return ele;
    }

    public void setEle(double ele) {
        this.ele = new BigDecimal(ele, MathContext.DECIMAL64);
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public BigDecimal getMagvar() {
        return magvar;
    }

    public void setMagvar(double magvar) {
        this.magvar = new BigDecimal(magvar, MathContext.DECIMAL64);
    }

    public BigDecimal getGeoidheight() {
        return geoidheight;
    }

    public void setGeoidheight(double geoidheight) {
        this.geoidheight = new BigDecimal(geoidheight, MathContext.DECIMAL64);
    }

    public String getSym() {
        return sym;
    }

    public void setSym(String sym) {
        this.sym = sym;
    }

    public String getFix() {
        return fix;
    }

    public void setFix(String fix) {
        this.fix = fix;
    }

    public Integer getSat() {
        return sat;
    }

    public void setSat(int sat) {
        this.sat = sat;
    }

    public BigDecimal getHdop() {
        return hdop;
    }

    public void setHdop(double hdop) {
        this.hdop = new BigDecimal(hdop, MathContext.DECIMAL64);
    }

    public BigDecimal getVdop() {
        return vdop;
    }

    public void setVdop(double vdop) {
        this.vdop = new BigDecimal(vdop, MathContext.DECIMAL64);
    }

    public BigDecimal getPdop() {
        return pdop;
    }

    public void setPdop(double pdop) {
        this.pdop = new BigDecimal(pdop, MathContext.DECIMAL64);
    }

    public BigDecimal getAgeofdgpsdata() {
        return ageofdgpsdata;
    }

    public void setAgeofdgpsdata(double ageofdgpsdata) {
        this.ageofdgpsdata = new BigDecimal(ageofdgpsdata,
                MathContext.DECIMAL64);
    }

    public Integer getDgpsid() {
        return dgpsid;
    }

    public void setDgpsid(int dgpsid) {
        this.dgpsid = dgpsid;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = new BigDecimal(lat, MathContext.DECIMAL64);
    }

    public BigDecimal getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = new BigDecimal(lon, MathContext.DECIMAL64);
    }
}
