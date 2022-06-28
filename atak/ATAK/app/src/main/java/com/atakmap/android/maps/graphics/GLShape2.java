
package com.atakmap.android.maps.graphics;

import android.graphics.Color;

import com.atakmap.android.maps.Shape;
import com.atakmap.map.MapRenderer;
import com.atakmap.math.MathUtils;

public abstract class GLShape2 extends AbstractGLMapItem2 implements
        Shape.OnFillColorChangedListener,
        Shape.OnStrokeColorChangedListener,
        Shape.OnStyleChangedListener,
        Shape.OnStrokeWeightChangedListener {

    protected GLShape2(final MapRenderer surface, final Shape subject,
            int renderPass) {
        super(surface, subject, renderPass);

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
        fill = MathUtils.hasBits(style, Shape.STYLE_FILLED_MASK);
        stroke = MathUtils.hasBits(style, Shape.STYLE_STROKE_MASK);
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
