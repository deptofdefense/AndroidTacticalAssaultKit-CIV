package gov.tak.api.engine.math;

import com.atakmap.math.MathUtils;

public final class Vector {
    public double x;
    public double y;
    public double z;

    public Vector(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double length() {
        return MathUtils.distance(x, y, z, 0d, 0d, 0d);
    }

    public void normalize() {
        final double mag = length();
        x /= mag;
        y /= mag;
        z /= mag;
    }
}
