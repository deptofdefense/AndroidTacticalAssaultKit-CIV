
package com.atakmap.android.maps.graphics;

import android.graphics.Color;

import com.atakmap.android.maps.Shape;
import com.atakmap.map.MapRenderer;

public abstract class GLShape extends GLMapItem implements
        Shape.OnFillColorChangedListener,
        Shape.OnStrokeColorChangedListener,
        Shape.OnStyleChangedListener,
        Shape.OnStrokeWeightChangedListener {

    public GLShape(final MapRenderer surface, final Shape subject) {
        super(surface, subject);
        fillColor = subject.getFillColor();
        strokeColor = subject.getStrokeColor();

        strokeRed = Color.red(strokeColor) / 255f;
        strokeGreen = Color.green(strokeColor) / 255f;
        strokeBlue = Color.blue(strokeColor) / 255f;
        strokeAlpha = Color.alpha(strokeColor) / 255f;

        _updateStyle(subject.getStyle());
        strokeWeight = (float) subject.getStrokeWeight();
    }

    @Override
    public void startObserving() {
        final Shape shape = (Shape) this.subject;
        super.startObserving();
        shape.addOnStrokeColorChangedListener(this);
        shape.addOnFillColorChangedListener(this);
        shape.addOnStyleChangedListener(this);
        shape.addOnStrokeWeightChangedListener(this);
    }

    @Override
    public void stopObserving() {
        final Shape shape = (Shape) this.subject;
        super.stopObserving();
        shape.removeOnStrokeColorChangedListener(this);
        shape.removeOnFillColorChangedListener(this);
        shape.removeOnStyleChangedListener(this);
        shape.removeOnStrokeWeightChangedListener(this);
    }

    @Override
    public void onFillColorChanged(Shape shape) {
        final int f = shape.getFillColor();
        fillColor = f;
    }

    @Override
    public void onStrokeColorChanged(Shape shape) {
        final int s = shape.getStrokeColor();
        strokeColor = s;
        strokeRed = Color.red(strokeColor) / 255f;
        strokeGreen = Color.green(strokeColor) / 255f;
        strokeBlue = Color.blue(strokeColor) / 255f;
        strokeAlpha = Color.alpha(strokeColor) / 255f;
    }

    @Override
    public void onStyleChanged(Shape shape) {
        final int s = shape.getStyle();
        _updateStyle(s);
    }

    @Override
    public void onStrokeWeightChanged(Shape shape) {
        final double s = shape.getStrokeWeight();
        strokeWeight = (float) s;
    }

    private void _updateStyle(int style) {
        if ((style & Shape.STYLE_FILLED_MASK) != 0) {
            fill = true;
        }
        if ((style & Shape.STYLE_STROKE_MASK) != 0) {
            stroke = true;
        }
    }

    protected boolean fill;
    protected boolean stroke;
    protected int fillColor;
    protected int strokeColor;

    protected float strokeRed;
    protected float strokeGreen;
    protected float strokeBlue;
    protected float strokeAlpha;

    protected float strokeWeight = 1f;
}
