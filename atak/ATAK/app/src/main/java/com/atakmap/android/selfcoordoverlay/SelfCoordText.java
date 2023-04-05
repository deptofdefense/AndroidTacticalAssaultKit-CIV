
package com.atakmap.android.selfcoordoverlay;

/**
 * Struct that contains self coordinate data for use with
 * {@link SelfCoordOverlayReceiver}
 */
public class SelfCoordText {

    // The GPS source and its color coding
    public String source;
    public int sourceColor = 0;

    // The user's callsign
    public String callsign;

    // Formatted location and altitude
    public String location;
    public String altitude;

    // Formatted heading, speed, and GPS accuracy
    public String heading;
    public String speed;
    public String accuracy;
}
