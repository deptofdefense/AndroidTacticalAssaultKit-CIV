
package com.atakmap.coremap.maps.coords;

public class Vector3D {
    public float x;
    public float y;
    public final float z;

    public Vector3D() {
        x = y = z = 0;
    }

    public Vector3D(final float x, final float y, final float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public float dot(final Vector3D v) {
        return x * v.x + y * v.y + z * v.z;
    }

    public Vector3D cross(final Vector3D v) {
        return new Vector3D(y * v.z - z * v.y, z * v.x - x * v.z, x * v.y - y
                * v.x);
    }

    public Vector3D normalize() {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        float div_len = 1f / len;
        return new Vector3D(x * div_len, y * div_len, z * div_len);
    }

    public float distance(final Vector3D v) {
        return (float) Math.sqrt((x - v.x) * (x - v.x) + (y - v.y) * (y - v.y)
                + (z - v.z)
                        * (z - v.z));
    }

    public float distanceSq(final Vector3D v) {
        return (x - v.x) * (x - v.x) + (y - v.y) * (y - v.y) + (z - v.z)
                * (z - v.z);
    }

    public Vector3D add(final Vector3D v) {
        return new Vector3D(x + v.x, y + v.y, z + v.z);
    }

    public Vector3D subtract(final Vector3D v) {
        return new Vector3D(x - v.x, y - v.y, z - v.z);
    }

    public static Vector3D nearestPointOnSegment(final Vector3D point,
            final Vector3D seg0,
            final Vector3D seg1) {
        Vector3D v = seg1.subtract(seg0);
        Vector3D w = point.subtract(seg0);
        float c1 = w.dot(v);
        if (c1 <= 0)
            return seg0;
        float c2 = v.dot(v);
        if (c2 <= c1)
            return seg1;
        float b = c1 / c2;
        return new Vector3D(seg0.x + b * v.x, seg0.y + b * v.y, seg0.z + b
                * v.z);
    }

    public static float distanceSqToSegment(final Vector3D point,
            final Vector3D seg0,
            final Vector3D seg1) {
        return point.distanceSq(nearestPointOnSegment(point, seg0, seg1));
    }

    public static float distanceToSegment(final Vector3D point,
            final Vector3D seg0,
            final Vector3D seg1) {
        return (float) Math.sqrt(distanceSqToSegment(point, seg0, seg1));
    }

    public Vector3D multiply(final float v) {
        return new Vector3D(x * v, y * v, z * v);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }
}
