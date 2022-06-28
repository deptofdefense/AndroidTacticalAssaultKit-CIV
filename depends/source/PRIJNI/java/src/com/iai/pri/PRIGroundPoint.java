package com.iai.pri;

/**
 * Simple container class for passing data back and forth over JNI. This class
 * does not contain getters or setters because it makes retrieval and storage
 * of data easier from the c++ side of the JNI interface.
**/
public class PRIGroundPoint {
    public double lat;
    public double lon;
    public double eleMeters;
    public double ce90Meters;
    public double le90Meters;

    public PRIGroundPoint(double lat, double lon, double eleMeters,
                    double ce90Meters, double le90Meters){
        this.lat = lat;
        this.lon = lon;
        this.eleMeters = eleMeters;
        this.ce90Meters = ce90Meters;
        this.le90Meters = le90Meters;
    }
}
