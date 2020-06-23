
package com.atakmap.android.image.nitf.overlays;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;

import com.atakmap.android.image.nitf.CGM.PolylineCommand;
import com.atakmap.android.imagecapture.CanvasHelper;

/**
 * Overlay for line and arrow head
 */
public class ArrowOverlay extends LineOverlay {

    public static final String ID = "Arrow";

    protected static final int TAIL = 0;
    protected static final int HEAD = 1;
    protected static final int HEAD_LEFT = 2;
    protected static final int HEAD_RIGHT = 3;

    protected Path _path;

    public ArrowOverlay(PolylineCommand line) {
        super(line);
        this.name = ID;
        _p = new PointF[4];
        if (line.points.size() == 2)
            setup(new PointF(line.points.get(0)),
                    new PointF(line.points.get(1)));
        else if (line.points.size() > 2)
            setup(new PointF(line.points.get(0)),
                    new PointF(line.points.get(1)),
                    new PointF(line.points.get(2)));
    }

    @Override
    public void setup(PointF tail, PointF head) {
        setup(tail, head, 135f, CanvasHelper.length(tail, head) / 10f);
    }

    public void setup(PointF tail, PointF head, PointF tipOffset) {
        float deg = CanvasHelper.angleTo(tail, head);
        float tipDeg = CanvasHelper.angleTo(head, tipOffset);
        float tipLen = CanvasHelper.length(head, tipOffset);
        if (tipDeg > deg + 180)
            tipDeg = 180 - (tipDeg - deg - 180);
        else
            tipDeg -= deg;
        setup(tail, head, tipDeg, tipLen);
    }

    public void setup(PointF tail, PointF head, float tipDeg, float tipLen) {
        _p = new PointF[4];
        _p[TAIL] = tail;
        _p[HEAD] = head;
        float deg = CanvasHelper.angleTo(_p[TAIL], _p[HEAD]);
        _p[HEAD_LEFT] = CanvasHelper.degOffset(_p[HEAD], deg - tipDeg, tipLen);
        _p[HEAD_RIGHT] = CanvasHelper.degOffset(_p[HEAD], deg + tipDeg, tipLen);
        recalcBounds();
    }

    protected void recalcBounds() {
        bounds.set(Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE);
        for (PointF p : _p) {
            if (p == null)
                continue;
            bounds.left = Math.min(bounds.left, p.x);
            bounds.right = Math.max(bounds.right, p.x);
            bounds.top = Math.min(bounds.top, p.y);
            bounds.bottom = Math.max(bounds.bottom, p.y);
        }
        recalcPath();
    }

    protected void recalcPath() {
        if (_path == null)
            _path = new Path();
        _path.reset();
        _path.moveTo(_p[TAIL].x, _p[TAIL].y);
        _path.lineTo(_p[HEAD].x, _p[HEAD].y);
        _path.moveTo(_p[HEAD_LEFT].x, _p[HEAD_LEFT].y);
        _path.lineTo(_p[HEAD].x, _p[HEAD].y);
        _path.lineTo(_p[HEAD_RIGHT].x, _p[HEAD_RIGHT].y);
    }

    @Override
    public void offset(float x, float y) {
        for (PointF p : _p)
            p.offset(x, y);
        recalcBounds();
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        if (canvas == null)
            return;
        paint.setColor(getColor());
        paint.setStrokeWidth(getStrokeWidth());
        LineStyle style = LineStyle.get(getStrokeStyle());
        paint.setPathEffect(style != null ? style.effect : null);
        paint.setTextSize(getFontSize() * dpiScale);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(_path, paint);
    }
}
