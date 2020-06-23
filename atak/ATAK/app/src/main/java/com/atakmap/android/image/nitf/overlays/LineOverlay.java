
package com.atakmap.android.image.nitf.overlays;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;

import com.atakmap.android.image.nitf.CGM.PolylineCommand;

/**
 * Overlay for 2-point lines
 */
public class LineOverlay extends Overlay {

    public static final String ID = "Line";

    protected PointF[] _p = new PointF[2];

    public LineOverlay(PolylineCommand line) {
        super(ID);
        if (line.points.size() == 2)
            setup(new PointF(line.points.get(0)),
                    new PointF(line.points.get(1)));
    }

    /**
     * a line has two points and can have an arrowhead
     */
    public void setup(PointF p1, PointF p2) {
        _p[0] = p1;
        _p[1] = p2;
        float right = Math.max(p1.x, p2.x);
        float bottom = Math.max(p1.y, p2.y);
        float left = Math.min(p1.x, p2.x);
        float top = Math.min(p1.y, p2.y);
        bounds.set(left, top, right, bottom);
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
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(_p[0].x, _p[0].y, _p[1].x, _p[1].y, paint);
    }
}
