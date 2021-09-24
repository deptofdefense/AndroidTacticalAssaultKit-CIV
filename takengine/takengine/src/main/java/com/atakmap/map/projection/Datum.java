package com.atakmap.map.projection;

public class Datum {

    public final static Datum WGS84 = new Datum(Ellipsoid.WGS84, 0d, 0d, 0d);

    public final double deltaX;
    public final double deltaY;
    public final double deltaZ;
    
    public final Ellipsoid referenceEllipsoid;
    
    public Datum(Ellipsoid ref, double dx, double dy, double dz) {
        this.referenceEllipsoid = ref;
        this.deltaX = dx;
        this.deltaY = dy;
        this.deltaZ = dz;
    }    
}
