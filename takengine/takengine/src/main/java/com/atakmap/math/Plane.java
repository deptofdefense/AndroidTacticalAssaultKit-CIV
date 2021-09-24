 package com.atakmap.math;

 import com.atakmap.interop.Pointer;

 public final class Plane extends NativeGeometryModel {
    public Plane(Vector3D normal, PointD point) {
        super(Plane_create(normal.X, normal.Y, normal.Z, point.x, point.y, point.z), null);
    }

    Plane(Pointer pointer, Object owner) {
        super(pointer, owner);
    }
}
