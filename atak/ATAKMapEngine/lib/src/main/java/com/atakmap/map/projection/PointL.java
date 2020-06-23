package com.atakmap.map.projection;

public class PointL {

    public long x;
    public long y;
    
    public PointL() {
        this(0L, 0L);
    }
    
    public PointL(long x, long y) {
        this.x = x;
        this.y = y;
    }
    
    public PointL(PointL p) {
        this(p.x, p.y);
    }
}
