package com.atakmap.math;

import com.atakmap.interop.InteropCleaner;
import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReadWriteLock;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public class NativeGeometryModel implements GeometryModel, Disposable {
    final static NativePeerManager.Cleaner CLEANER = new InteropCleaner(GeometryModel.class);

    final ReadWriteLock rwlock = new ReadWriteLock();
    private final Cleaner cleaner;
    Pointer pointer;
    private Object owner;

    NativeGeometryModel(Pointer pointer, Object owner) {
        cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);

        this.pointer = pointer;
        this.owner = owner;
    }
    @Override
    public final PointD intersect(Ray ray) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();

            PointD isect = new PointD(0d, 0d, 0d);
            return intersect(this.pointer.raw,
                             ray.origin.x, ray.origin.y, ray.origin.z,
                             ray.direction.X, ray.direction.Y, ray.direction.Z,
                             isect)
                    ? isect : null;
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public final void dispose() {
        if(this.cleaner != null)
            this.cleaner.clean();
    }

    static native void destruct(Pointer pointer);
    private static native boolean intersect(long pointer,
                                            double rox, double roy, double roz,
                                            double rdx, double rdy, double rdz,
                                            PointD result);
    static native int getGeomClass(long pointer);
    static native Pointer clone(long pointer);

    static long getPointer(GeometryModel m) {
        if(m instanceof NativeGeometryModel)
            return ((NativeGeometryModel)m).pointer.raw;
        else
            return 0L;
    }

    static GeometryModel create(Pointer pointer, Object owner) {
        if(pointer == null)
            return null;

        final int gc = getGeomClass(pointer.raw);
        if(gc == getGeometryModel2_GeometryClass_PLANE())
            return new Plane(pointer, owner);
        else if(gc == getGeometryModel2_GeometryClass_ELLIPSOID())
            return new Ellipsoid(pointer, owner);
        else if(gc == getGeometryModel2_GeometryClass_SPHERE())
            return new Sphere(pointer, owner);
        else if(gc == getGeometryModel2_GeometryClass_MESH())
            return new Mesh(pointer, owner);
        else if(gc == getGeometryModel2_GeometryClass_AABB())
            return new AABB(pointer, owner);
        else if(isWrapped(pointer.raw))
            return unwrap(pointer.raw);
        else
            return new NativeGeometryModel(pointer, owner);
    }

    static native boolean isWrapped(long pointer);
    static native GeometryModel unwrap(long pointer);
    static native Pointer wrap(GeometryModel managed);

    static native Pointer Ellipsoid_create(double cx, double cy, double cz, double rx, double ry, double rz);
    static native double Ellipsoid_getCenterX(long pointer);
    static native double Ellipsoid_getCenterY(long pointer);
    static native double Ellipsoid_getCenterZ(long pointer);
    static native double Ellipsoid_getRadiusX(long pointer);
    static native double Ellipsoid_getRadiusY(long pointer);
    static native double Ellipsoid_getRadiusZ(long pointer);
    static native Pointer Sphere_create(double cx, double cy, double cz, double radius);
    static native double Sphere_getCenterX(long pointer);
    static native double Sphere_getCenterY(long pointer);
    static native double Sphere_getCenterZ(long pointer);
    static native double Sphere_getRadius(long pointer);
    static native Pointer AABB_create(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
    static native double AABB_getMinX(long pointer);
    static native double AABB_getMinY(long pointer);
    static native double AABB_getMinZ(long pointer);
    static native double AABB_getMaxX(long pointer);
    static native double AABB_getMaxY(long pointer);
    static native double AABB_getMaxZ(long pointer);
    static native Pointer Plane_create(double nx, double ny, double nz, double px, double py, double pz);

    static native int getGeometryModel2_GeometryClass_PLANE();
    static native int getGeometryModel2_GeometryClass_ELLIPSOID();
    static native int getGeometryModel2_GeometryClass_SPHERE();
    static native int getGeometryModel2_GeometryClass_TRIANGLE();
    static native int getGeometryModel2_GeometryClass_MESH();
    static native int getGeometryModel2_GeometryClass_AABB();
    static native int getGeometryModel2_GeometryClass_UNDEFINED();
}
