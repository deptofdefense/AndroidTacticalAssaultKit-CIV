
package com.atakmap.android.image.nitf.overlays;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;

import com.atakmap.android.image.nitf.CGM.TextCommand;

/**
 * Overlay for text labels
 */
public class LabelOverlay extends Overlay {

    public static final String ID = "Label";

    protected PointF _anchor;
    protected String _label;
    protected boolean _boundsNeedUpdate = false;

    public LabelOverlay(TextCommand text) {
        super(ID);
        setup(new PointF(text.position), text.getString(), null);
    }

    public void setup(PointF point, String label, Paint paint) {
        _anchor = point;
        setLabel(label);
        recalcBounds(paint);
    }

    public void setup(PointF center, String label) {
        setup(center, label, null);
    }

    public void setLabel(String newLabel) {
        if (_label == null || newLabel != null && !newLabel.equals(_label)) {
            _label = newLabel;
            _boundsNeedUpdate = true;
        }
    }

    @Override
    public PointF[] getPoints() {
        return new PointF[] {
                _anchor
        };
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
    public void draw(Canvas canvas, Paint paint) {
        if (canvas == null || paint == null || _label == null
                || _anchor == null)
            return;

        super.draw(canvas, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);

        // Update bounds in case text size has been changed
        if (_boundsNeedUpdate)
            recalcBounds(paint);
        canvas.drawText(_label, _anchor.x, _anchor.y, paint);
    }

    public void recalcBounds(Paint paint) {
        if (paint != null) {
            Rect tmpRect = new Rect();
            paint.getTextBounds(_label, 0, _label.length(), tmpRect);
            this.bounds.set(tmpRect);
        }
        this.bounds.set(_anchor.x, _anchor.y - bounds.height(),
                _anchor.x + bounds.width(), _anchor.y);
        _boundsNeedUpdate = false;
    }
}
