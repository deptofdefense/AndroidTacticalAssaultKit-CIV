
package com.atakmap.android.image.nitf.overlays;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;

import com.atakmap.android.image.nitf.CGM.RectangleCommand;

/**
 * Overlay that draws a rectangle
 */
public class RectangleOverlay extends Overlay {

    public static final String ID = "Rectangle";

    private static final int TOP_LEFT = 0;
    private static final int TOP_RIGHT = 1;
    private static final int BOTTOM_RIGHT = 2;
    private static final int BOTTOM_LEFT = 3;

    private final PointF[] _p = new PointF[4];

    public RectangleOverlay(RectangleCommand rect) {
        super(ID);
        setup(new PointF(rect.firstCorner), new PointF(rect.secondCorner));
    }

    public void setup(PointF p1, PointF p2) {
        float right = Math.max(p1.x, p2.x);
        float bottom = Math.max(p1.y, p2.y);
        float left = Math.min(p1.x, p2.x);
        float top = Math.min(p1.y, p2.y);
        bounds.set(left, top, right, bottom);
        _p[TOP_LEFT] = new PointF(left, top);
        _p[TOP_RIGHT] = new PointF(right, top);
        _p[BOTTOM_RIGHT] = new PointF(right, bottom);
        _p[BOTTOM_LEFT] = new PointF(left, bottom);
    }

    @Override
    public PointF[] getPoints() {
        return _p;
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        if (canvas == null)
            return;
        super.draw(canvas, paint);

        if (isFilled()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(getFillColor());
            canvas.drawRect(_p[TOP_LEFT].x, _p[TOP_LEFT].y,
                    _p[BOTTOM_RIGHT].x, _p[BOTTOM_RIGHT].y, paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(getColor());
        canvas.drawRect(_p[TOP_LEFT].x, _p[TOP_LEFT].y,
                _p[BOTTOM_RIGHT].x, _p[BOTTOM_RIGHT].y, paint);
    }
}
