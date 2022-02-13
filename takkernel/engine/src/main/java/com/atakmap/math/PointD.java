
package com.atakmap.math;

import android.graphics.Point;
import android.graphics.PointF;

import java.util.Objects;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public class PointD {
    public double x;
    public double y;
    public double z;

    public PointD() {
        this(0, 0, 0);
    }

    public PointD(Point p) {
        this(p.x, p.y);
    }

    public PointD(PointF p) {
        this(p.x, p.y);
    }

    public PointD(PointD p) {
        this(p.x, p.y, p.z);
    }

    public PointD(double x, double y) {
        this(x, y, 0);
    }

    public PointD(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Point toPoint() {
        return new Point((int) this.x, (int) this.y);
    }

    public PointF toPointF() {
        return new PointF((float) this.x, (float) this.y);
    }

    @Override
    public String toString() {
        return "PointD {x=" + x + "," + "y=" + y + ",z=" + z + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PointD pointD = (PointD) o;
        return Double.compare(pointD.x, x) == 0 &&
                Double.compare(pointD.y, y) == 0 &&
                Double.compare(pointD.z, z) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
}
