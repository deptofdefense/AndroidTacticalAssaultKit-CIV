package com.atakmap.math;

import com.atakmap.interop.Pointer;
import com.atakmap.math.PointD;

public final class Ellipsoid extends NativeGeometryModel {

    public final PointD location;
    public final double radiusX;
    public final double radiusY;
    public final double radiusZ;

    public Ellipsoid(PointD location, double radiusX, double radiusY, double radiusZ) {
        this(Ellipsoid_create(location.x, location.y, location.z,
                              radiusX, radiusY, radiusZ),
             null,
             location.x, location.y, location.z,
             radiusX, radiusY, radiusZ);
    }

    Ellipsoid(Pointer pointer, Object owner) {
        this(pointer,
             owner,
             Ellipsoid_getCenterX(pointer.raw), Ellipsoid_getCenterY(pointer.raw), Ellipsoid_getCenterZ(pointer.raw),
             Ellipsoid_getRadiusX(pointer.raw), Ellipsoid_getRadiusY(pointer.raw), Ellipsoid_getRadiusZ(pointer.raw));
    }
    private Ellipsoid(Pointer pointer, Object owner, double cx, double cy, double cz, double rx, double ry, double rz) {
        super(pointer, owner);

        this.location = new PointD(cx, cy, cz);
        this.radiusX = rx;
        this.radiusY = ry;
        this.radiusZ = rz;
    }

}
