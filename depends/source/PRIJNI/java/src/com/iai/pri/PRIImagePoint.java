package com.iai.pri;

/**
 * Simple container class for passing data back and forth over JNI. This class
 * does not contain getters or setters because it makes retrieval and storage
 * of data easier from the c++ side of the JNI interface.
**/
public class PRIImagePoint{
    public double line;
    public double sample;

    public PRIImagePoint(double line, double sample){
        this.line = line;
        this.sample = sample;
    }
}
