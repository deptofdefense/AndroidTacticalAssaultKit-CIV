package com.atakmap.map.layer.feature.geometry;

import com.atakmap.interop.Pointer;

public final class Point extends Geometry {

    public Point(double x, double y) {
        this(Point_create(x, y));
    }
    
    public Point(double x, double y, double z) {
        this(Point_create(x, y, z));
    }
    
    Point(Pointer pointer) {
        super(pointer);
    }

    public double getX() {
        this.rwlock.acquireRead();
        try {
            return Point_getX(this.pointer);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public double getY() {
        this.rwlock.acquireRead();
        try {
            return Point_getY(this.pointer);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public double getZ() {
        this.rwlock.acquireRead();
        try {
            return Point_getZ(this.pointer);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public void set(double x, double y) {
        this.rwlock.acquireRead();
        try {
            Point_set(this.pointer, x, y);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    public void set(double x, double y, double z) {
        this.rwlock.acquireRead();
        try {
            Point_set(this.pointer, x, y, z);
        } finally {
            this.rwlock.releaseRead();
        }
    }
}
