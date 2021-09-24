package com.atakmap.math;

import com.atakmap.interop.Pointer;

public final class Sphere extends NativeGeometryModel {
    
    public final PointD center;
    public final double radius;

    public Sphere(PointD center, double radius) {
        this(Sphere_create(center.x, center.y, center.z, radius), null, center.x, center.y, center.z, radius);
    }

    Sphere(Pointer pointer, Object owner) {
        this(pointer, owner, Sphere_getCenterX(pointer.raw), Sphere_getCenterY(pointer.raw), Sphere_getCenterZ(pointer.raw), Sphere_getRadius(pointer.raw));
    }

    private Sphere(Pointer pointer, Object owner, double cx, double cy, double cz, double r) {
        super(pointer, owner);

        this.center = new PointD(cx, cy, cz);
        this.radius = r;
    }
}
