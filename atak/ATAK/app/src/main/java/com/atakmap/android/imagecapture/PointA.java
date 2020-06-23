
package com.atakmap.android.imagecapture;

import android.graphics.PointF;
import android.os.Parcel;

import java.util.Objects;

/**
 * Float x,y point with angle
 */
public class PointA extends PointF {

    public float angle;

    /**
     * Create a point with Angle.
     */
    public PointA() {
        super();
        this.angle = 0.0f;
    }

    /**
     * Constructor with the ability to x, y, angle.
     * @param x the x value of the PointF
     * @param y the y value of the PointF
     * @param angle the angle associated with the PointF.
     */
    public PointA(final float x, final float y, final float angle) {
        super(x, y);
        this.angle = angle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        PointA pointA = (PointA) o;
        return Float.compare(pointA.angle, angle) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), angle);
    }

    /**
     * Constructor for a provided a PointF and an angle.
     * @param p the PointF
     * @param angle the angle assicated with the PointF.
     */
    public PointA(final PointF p, final float angle) {
        this(p.x, p.y, angle);
    }

    public static final Creator<PointA> CREATOR = new Creator<PointA>() {
        /**
         * Return a new point from the data in the specified parcel.
         */
        @Override
        public PointA createFromParcel(Parcel in) {
            PointA r = new PointA();
            r.readFromParcel(in);
            return r;
        }

        /**
         * Return an array of rectangles of the specified size.
         */
        @Override
        public PointA[] newArray(int size) {
            return new PointA[size];
        }
    };
}
