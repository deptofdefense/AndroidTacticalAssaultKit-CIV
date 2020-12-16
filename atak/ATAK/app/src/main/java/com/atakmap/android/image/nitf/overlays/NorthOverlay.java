
package com.atakmap.android.image.nitf.overlays;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;

import com.atakmap.android.image.nitf.CGM.PolylineCommand;

/**
 * Overlay for north arrow
 */
public class NorthOverlay extends ArrowOverlay {

    public static final String ID = "North";
    public static final String SYMBOL = "N";

    private boolean _boundsNeedUpdate = false;
    private final Rect _txtBounds = new Rect();

    public NorthOverlay(PolylineCommand line) {
        super(line);
        this.name = ID;
    }

    public void setup(PointF tail, PointF head, Paint paint) {
        super.setup(tail, head);
        recalcBounds(paint);
    }

    @Override
    public void setup(PointF tail, PointF head) {
        setup(tail, head, (Paint) null);
    }

    protected void recalcBounds(Paint paint) {
        super.recalcBounds();
        if (paint != null)
            paint.getTextBounds(SYMBOL, 0, 1, _txtBounds);
        float halfWidth = _txtBounds.width() / 2f;
        float height = _txtBounds.height();
        boolean downAngle = _p[TAIL].y <= _p[HEAD].y;
        float top = downAngle ? _p[TAIL].y - height : _p[HEAD].y;
        float bottom = downAngle ? _p[HEAD].y : _p[TAIL].y + height;
        bounds.set(Math.min(_p[TAIL].x - halfWidth, bounds.left),
                Math.min(top, bounds.top),
                Math.max(_p[TAIL].x + halfWidth, bounds.right),
                Math.max(bottom, bounds.bottom));
        _txtBounds.set((int) (_p[TAIL].x - halfWidth),
                (int) (downAngle ? top : _p[TAIL].y),
                (int) (_p[TAIL].x + halfWidth),
                (int) (downAngle ? _p[TAIL].y : bottom));
        _boundsNeedUpdate = false;
    }

    @Override
    public void setFontSize(int size) {
        super.setFontSize(size);
        _boundsNeedUpdate = true;
    }

    @Override
    public void setDpiScale(float scale) {
        super.setDpiScale(scale);
        _boundsNeedUpdate = true;
    }

    @Override
    public void offset(float x, float y) {
        super.offset(x, y);
        _boundsNeedUpdate = true;
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        if (canvas == null)
            return;
        super.draw(canvas, paint);

        // Draw "N"
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        if (_boundsNeedUpdate)
            recalcBounds(paint);
        canvas.drawText(SYMBOL, _p[TAIL].x,
                _p[TAIL].y <= _p[HEAD].y ? _p[TAIL].y : bounds.bottom, paint);
    }
}
