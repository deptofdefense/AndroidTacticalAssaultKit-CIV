package com.atakmap.math;

public final class RectD {
    public double left;
    public double top;
    public double right;
    public double bottom;

    public RectD(double left, double top, double right, double bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public RectD() {
        this(0d, 0d, 0d, 0d);
    }

    public boolean intersects(RectD other) {
        return !(this.right < other.left || this.left > other.right
                || this.bottom < other.top || this.top > other.bottom);
    }

    public boolean contains(double x, double y) {
        return left < right && top < bottom  // check for empty first
                && x >= left && x < right && y >= top && y < bottom;
    }
}
