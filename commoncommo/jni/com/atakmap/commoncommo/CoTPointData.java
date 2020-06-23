package com.atakmap.commoncommo;

/**
 * Identifies a geographic point in/destined for a CoT message.
 */
public class CoTPointData {
    public final double lat;
    public final double lon;
    public final double hae;
    public final double ce;
    public final double le;

    public CoTPointData(double lat, double lon)
    {
        this.lat = lat;
        this.lon = lon;
        hae = ce = le = getNoValue();
    }

    public CoTPointData(double lat, double lon, double hae, double ce, double le)
    {
        this.lat = lat;
        this.lon = lon;
        this.hae = hae;
        this.ce = ce;
        this.le = le;
    }

    /**
     * Obtains the value to be used for hae, ce, and/or le
     * if they are to be considered as not initialized to a meaningful
     * value.
     */
    public static double getNoValue()
    {
        return getNoValueNative();
    }
    
    
    private static native double getNoValueNative();
    
}
