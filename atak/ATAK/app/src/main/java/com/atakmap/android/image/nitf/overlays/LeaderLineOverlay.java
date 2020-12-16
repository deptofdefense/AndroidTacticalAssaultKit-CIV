
package com.atakmap.android.image.nitf.overlays;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Region;

import com.atakmap.android.image.nitf.CGM.Command;
import com.atakmap.android.image.nitf.CGM.PolylineCommand;
import com.atakmap.android.image.nitf.CGM.TextCommand;
import com.atakmap.android.imagecapture.CanvasHelper;

import java.util.List;

/**
 * Overlay for triangles
 */
public class LeaderLineOverlay extends FreeformOverlay {

    public static final String ID = "Leader Line";

    private String _label;
    private final RectF _labelBounds = new RectF();
    private boolean _boundsNeedUpdate = false;

    public LeaderLineOverlay(PolylineCommand line, List<Command> elements) {
        super(line);
        this.name = ID;
        for (Command c : elements) {
            if (c instanceof TextCommand) {
                setLabel(((TextCommand) c).getString());
                break;
            }
        }
        setup(line.points.toArray(new Point[0]));
    }

    @Override
    public void setup(PointF... p) {
        int l = p.length;
        if (l >= 2) {
            if (l > 5 && p[l - 2].equals(p[l - 4])) {
                float deg = CanvasHelper.angleTo(p[l - 5], p[l - 4]);
                float tipDeg = CanvasHelper.angleTo(p[l - 4], p[l - 3]);
                float tipLen = CanvasHelper.length(p[l - 4], p[l - 3]);
                if (tipDeg > deg + 180)
                    tipDeg = 180 - (tipDeg - deg - 180);
                else
                    tipDeg -= deg;
                setup(tipLen, tipDeg, p, l - 3);
            } else
                setup(CanvasHelper.length(p[l - 2], p[l - 1]) / 10f, 135f, p);
        }
    }

    public void setup(float tipLen, float tipDeg, PointF[] p, int limit) {
        _p = new PointF[limit + 3];
        for (int i = 0; i < limit; i++)
            _p[i] = new PointF(p[i].x, p[i].y);

        // Add arrow head
        int tip = limit - 1;
        float deg = CanvasHelper.angleTo(_p[limit - 2], _p[tip]);
        _p[tip + 1] = CanvasHelper.degOffset(_p[tip], deg - tipDeg, tipLen);
        _p[tip + 2] = new PointF(_p[tip].x, _p[tip].y);
        _p[tip + 3] = CanvasHelper.degOffset(_p[tip], deg + tipDeg, tipLen);
        recalcBounds();
    }

    public void setup(float tipLen, float tipDeg, PointF[] p) {
        setup(tipLen, tipDeg, p, p.length);
    }

    public void setLabel(String label) {
        _label = label;
        _boundsNeedUpdate = true;
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
    protected void recalcBounds() {
        super.recalcBounds();
        // Need to recalculate label bounds
        _boundsNeedUpdate = true;
    }

    protected void recalcBounds(Paint paint) {
        super.recalcBounds();
        if (_label != null && paint != null) {
            Path txtPath = new Path();
            paint.getTextPath(_label, 0, _label.length(),
                    _p[0].x, _p[0].y, txtPath);
            txtPath.computeBounds(_labelBounds, true);
            float w2 = _labelBounds.width() / 2f,
                    h2 = _labelBounds.height() / 2f;
            this.bounds.set(Math.min(bounds.left, _p[0].x - w2),
                    Math.min(bounds.top, _p[0].y - h2),
                    Math.max(bounds.right, _p[0].x + w2),
                    Math.max(bounds.bottom, _p[0].y + h2));
            _labelBounds.offsetTo(_p[0].x - w2, _p[0].y - h2);
        }
        _boundsNeedUpdate = false;
    }

    /**
     * Recalculate path object based on updated vertices
     */
    @Override
    protected void recalcPath() {
        _path.reset();
        if (_p.length >= 5) {
            int l = _p.length;
            _path.moveTo(_p[0].x, _p[0].y);
            for (int i = 1; i < l - 3; i++)
                _path.lineTo(_p[i].x, _p[i].y);
            _path.moveTo(_p[l - 3].x, _p[l - 3].y);
            _path.lineTo(_p[l - 2].x, _p[l - 2].y);
            _path.lineTo(_p[l - 1].x, _p[l - 1].y);
        }
    }

    private int arrowTail() {
        return _p.length - 5;
    }

    private int arrowHead() {
        return _p.length - 4;
    }

    private int arrowLeft() {
        return _p.length - 3;
    }

    private int arrowRight() {
        return _p.length - 1;
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        if (canvas == null || _p.length < 2)
            return;
        int saveCount = canvas.save();
        if (_label != null) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(getColor());
            paint.setTextSize(getFontSize() * dpiScale);
            paint.setTextAlign(Paint.Align.CENTER);
            if (_boundsNeedUpdate)
                recalcBounds(paint);
            canvas.drawText(_label, _p[0].x, _p[0].y +
                    _labelBounds.height() / 2f, paint);
            canvas.clipRect(_labelBounds, Region.Op.DIFFERENCE);
        }
        super.draw(canvas, paint);
        canvas.restoreToCount(saveCount);
    }

    @Override
    public boolean isClosed() {
        return false;
    }
}
