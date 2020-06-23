
package com.atakmap.android.image.nitf.overlays;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;

import com.atakmap.android.image.nitf.CGM.EllipseCommand;

/**
 * Overlay for circles
 */
public class EllipseOverlay extends Overlay {

    public static final String ID = "Ellipse";

    private PointF _center;
    private float _xRadius, _yRadius;

    public EllipseOverlay(EllipseCommand ellipse) {
        super(ID);
        setup(new PointF(ellipse.center),
                new PointF(ellipse.firstConjugateDiameterEndPoint),
                new PointF(ellipse.secondConjugateDiameterEndPoint));
    }

    /**
     * An ellipse has a center point and 2 radius points
     * @param center Center point
     * @param xRad X-aligned radius point
     * @param yRad Y-aligned radius point
     */
    public void setup(PointF center, PointF xRad, PointF yRad) {
        float xDist = Math.max(Math.abs(center.x - xRad.x),
                Math.abs(center.x - yRad.x));
        float yDist = Math.max(Math.abs(center.y - xRad.y),
                Math.abs(center.y - yRad.y));
        setup(center, xDist, yDist);
    }

    public void setup(PointF center, float xRad, float yRad) {
        _center = center;
        _xRadius = Math.abs(xRad);
        _yRadius = Math.abs(yRad);
        bounds.set(_center.x - xRad, _center.y - yRad,
                _center.x + xRad, _center.y + yRad);
    }

    @Override
    public PointF[] getPoints() {
        return new PointF[] {
                _center,
                new PointF(_center.x + _xRadius, _center.y),
                new PointF(_center.x - _xRadius, _center.y),
                new PointF(_center.x, _center.y + _yRadius),
                new PointF(_center.x, _center.y - _yRadius)
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
            canvas.drawArc(bounds, 0, 360, true, paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(getColor());
        canvas.drawArc(bounds, 0, 360, true, paint);
    }
}
