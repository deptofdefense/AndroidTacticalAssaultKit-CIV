package com.atakmap.map.projection;

public class Ellipsoid {

    public final static Ellipsoid WGS84 = new Ellipsoid(6378137d, 1d/298.257223563);
    
    public final double semiMajorAxis;
    public final double flattening;
    
    public final double semiMinorAxis;

    public Ellipsoid(double semiMajorAxis, double flattening) {
        this.semiMajorAxis = semiMajorAxis;
        this.flattening = flattening;
        
        this.semiMinorAxis = (this.semiMajorAxis*(1d-this.flattening));
    }
}
