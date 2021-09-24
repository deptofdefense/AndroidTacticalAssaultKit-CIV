package com.atakmap.map.layer.feature.geometry;

import com.atakmap.interop.Pointer;

public final class LineString extends Geometry {

    public LineString(int dimension) {
        this(Linestring_create(dimension));
    }

    LineString(Pointer pointer) {
        super(pointer);
    }

    public void addPoint(double x, double y) {
        this.rwlock.acquireRead();
        try {
            Linestring_addPoint(this.pointer, x, y);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    public void addPoint(double x, double y, double z) {
        this.rwlock.acquireRead();
        try {
            Linestring_addPoint(this.pointer, x, y, z);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    public void addPoints(double[] pts, int off, int numPts, int ptsDim) {
        this.rwlock.acquireRead();
        try {
            Linestring_addPoints(this.pointer, pts, off, numPts, ptsDim);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    public int getNumPoints() {
        this.rwlock.acquireRead();
        try {
            return Linestring_getNumPoints(this.pointer);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    public double getX(int i) {
        this.rwlock.acquireRead();
        try {
            return Linestring_getX(this.pointer, i);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    public double getY(int i) {
        this.rwlock.acquireRead();
        try {
            return Linestring_getY(this.pointer, i);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    public double getZ(int i) {
        this.rwlock.acquireRead();
        try {
            return Linestring_getZ(this.pointer, i);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    public void get(Point point, int i) {
        point.setDimension(this.getDimension());
        if(this.getDimension() == 3)
            point.set(this.getX(i), this.getY(i), this.getZ(i));
        else
            point.set(this.getX(i), this.getY(i));
    }

    public void setX(int i, double x) {
        this.rwlock.acquireRead();
        try {
            Linestring_setX(this.pointer, i, x);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    public void setY(int i, double y) {
        this.rwlock.acquireRead();
        try {
            Linestring_setY(this.pointer, i, y);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    public void setZ(int i, double z) {
        this.rwlock.acquireRead();
        try {
            Linestring_setZ(this.pointer, i, z);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public boolean isClosed() {
        this.rwlock.acquireRead();
        try {
            return Linestring_isClosed(this.pointer);
        } finally {
            this.rwlock.releaseRead();
        }
    }
}
