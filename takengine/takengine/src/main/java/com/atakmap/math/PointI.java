package com.atakmap.math;

public final class PointI {
    public int x;
    public int y;
    public int z;

    public PointI() {
        this(0, 0, 0);
    }

    public PointI(PointI other) {
        this(other.x, other.y, other.z);
    }

    public PointI(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public int hashCode() {
        // there will be collisions...
        return hash(hash(x, y), z);
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof PointI))
            return false;
        PointI other = (PointI)o;
        return x == other.x && y == other.y && z == other.z;
    }

    static int hash(int a, int b) {
        // use the long hashing function
        return (a^b);
    }
}
