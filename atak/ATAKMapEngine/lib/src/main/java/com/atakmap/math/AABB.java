package com.atakmap.math;

import com.atakmap.interop.Pointer;

public final class AABB extends NativeGeometryModel {

    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        super(AABB_create(minX, minY, minZ, maxX, maxY, maxZ), null);

        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    AABB(Pointer pointer, Object owner) {
        super(pointer, owner);

        this.minX = AABB_getMinX(pointer.raw);
        this.minY = AABB_getMinY(pointer.raw);
        this.minZ = AABB_getMinZ(pointer.raw);
        this.maxX = AABB_getMaxX(pointer.raw);
        this.maxY = AABB_getMaxY(pointer.raw);
        this.maxZ = AABB_getMaxZ(pointer.raw);
    }
}
