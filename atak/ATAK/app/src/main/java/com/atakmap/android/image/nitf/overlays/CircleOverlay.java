
package com.atakmap.android.image.nitf.overlays;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;

import com.atakmap.android.image.nitf.CGM.CircleCommand;

/**
 * Overlay for circles
 */
public class CircleOverlay extends Overlay {

    public static final String ID = "Circle";

    private PointF _center;
    private float _radius;

    public CircleOverlay(CircleCommand circle) {
        super(ID);
        setup(new PointF(circle.center), (float) circle.radius);
    }

    /**
     * a circle has a center point and a radius
     * @param center Center point
     * @param radius Circle radius
     */
    public void setup(PointF center, float radius) {
        _center = center;
        _radius = radius;
        bounds.set(_center.x - _radius, _center.y - _radius,
                _center.x + _radius, _center.y + _radius);
    }

    @Override
    public PointF[] getPoints() {
        return new PointF[] {
                _center,
                new PointF(_center.x + _radius, _center.y)
        };
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        if (canvas == null)
            return;
        super.draw(canvas, paint);

        if (isFilled()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(getFillColor());
            canvas.drawCircle(_center.x, _center.y, _radius, paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(getColor());
        canvas.drawCircle(_center.x, _center.y, _radius, paint);
    }
}
