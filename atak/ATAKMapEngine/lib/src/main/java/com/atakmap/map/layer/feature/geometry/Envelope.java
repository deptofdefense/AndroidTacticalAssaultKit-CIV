package com.atakmap.map.layer.feature.geometry;

public final class Envelope {

    public double minX;
    public double minY;
    public double minZ;
    
    public double maxX;
    public double maxY;
    public double maxZ;
    
    public Envelope() {
        this(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    public Envelope(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public Envelope(Envelope other) {
        this(other.minX, other.minY, other.minZ, other.maxX, other.maxY, other.maxZ);
    }

    public boolean crossesIDL() {
        return minX < -180 && maxX >= -180 || minX <= 180 && maxX > 180;
    }

    @Override
    public String toString() {
        return "Envelope {minX=" + minX + ",minY=" + minY + ",minZ=" + minZ + ",maxX=" + maxX + ",maxY=" + maxY + ",maxZ=" + maxZ + "}";         
    }

    // For dynamically building an envelope from points or other envelopes
    public static class Builder {

        private Envelope e = null;

        // For IDL calculations
        private double eastMinX = Double.MAX_VALUE;
        private double westMaxX = -Double.MAX_VALUE;
        private boolean handleIdlCross = true;

        public void setHandleIdlCross(boolean b) {
            handleIdlCross = b;
        }

        public void add(double x, double y, double z) {
            if (e == null) {
                e = new Envelope(x, y, z, x, y, z);
                return;
            }
            if (x < e.minX) e.minX = x;
            if (y < e.minY) e.minY = y;
            if (z < e.minZ) e.minZ = z;
            if (x > e.maxX) e.maxX = x;
            if (y > e.maxY) e.maxY = y;
            if (z > e.maxZ) e.maxZ = z;
            if (x > 0 && x < eastMinX)
                eastMinX = x;
            if (x < 0 && x > westMaxX)
                westMaxX = x;
        }

        public void add(double x, double y) {
            add(x, y, 0);
        }

        public void add(Envelope other) {
            if (other == null)
                return;
            add(other.minX, other.minY, other.minZ);
            add(other.maxX, other.maxY, other.maxZ);
        }

        public Envelope build() {
            if (e == null)
                return null;

            // IDL bounds correction
            if(handleIdlCross) {
                if (e.minX < -180 && westMaxX > e.minX)
                    e.maxX = westMaxX;
                else if (e.maxX > 180 && eastMinX < e.maxX)
                    e.minX = eastMinX;
            }
            return e;
        }
    }
}
