
package com.atakmap.android.image.nitf.overlays;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;

import com.atakmap.android.image.nitf.CGM.PolygonCommand;
import com.atakmap.android.image.nitf.CGM.PolylineCommand;
import java.util.List;

/**
 * Free-form shapes such as polylines, polygons, and telestrations
 */
public class FreeformOverlay extends Overlay {

    public static final String ID = "Freeform";

    protected PointF[] _p = new PointF[0];
    protected Path _path = new Path();

    public FreeformOverlay(PolylineCommand line) {
        super(ID);
        setup(line.points.toArray(new Point[0]));
    }

    public FreeformOverlay(PolygonCommand shape) {
        super(ID);
        setup(shape.points.toArray(new Point[0]));
    }

    /**
     * a triangle has three points
     */
    public void setup(PointF... points) {
        _p = new PointF[points.length];
        for (int i = 0; i < points.length; i++)
            _p[i] = new PointF(points[i].x, points[i].y);
        recalcBounds();
    }

    public void setup(Point... points) {
        PointF[] pf = new PointF[points.length];
        for (int i = 0; i < pf.length; i++)
            pf[i] = new PointF(points[i]);
        setup(pf);
    }

    public void setup(List<PointF> points) {
        setup(points.toArray(new PointF[0]));
    }

    protected void recalcBounds() {
        bounds.set(Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE);
        recalcPath();
    }

    /**
     * Recalculate path object based on updated vertices
     */
    protected void recalcPath() {
        _path.reset();
        _path.moveTo(_p[0].x, _p[0].y);
        boolean closed = isClosed();
        for (int i = 1; i < _p.length - (closed ? 1 : 0); i++)
            _path.lineTo(_p[i].x, _p[i].y);
        if (closed)
            _path.close();
    }

    @Override
    public PointF[] getPoints() {
        return _p;
    }

    @Override
    public void offset(float x, float y) {
        for (PointF p : _p)
            p.offset(x, y);
        recalcBounds();
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        if (canvas == null || _p.length < 2)
            return;
        super.draw(canvas, paint);

        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.BUTT);

        if (isClosed() && isFilled()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(getFillColor());
            canvas.drawPath(_path, paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(getColor());
        canvas.drawPath(_path, paint);
    }

    public boolean isClosed() {
        return _p.length > 3 && Math.hypot(_p[0].x - _p[_p.length - 1].x,
                _p[0].y - _p[_p.length - 1].y) < 1;
    }
}
