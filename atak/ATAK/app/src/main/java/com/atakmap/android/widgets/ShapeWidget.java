
package com.atakmap.android.widgets;

import android.graphics.Color;

import com.atakmap.android.maps.MapView;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.api.widgets.IShapeWidget;

public class ShapeWidget extends MapWidget2 implements IShapeWidget {

    private final ConcurrentLinkedQueue<IShapeWidget.OnStrokeColorChangedListener> _onStrokeColorChanged = new ConcurrentLinkedQueue<>();
    private final Map<ShapeWidget.OnStrokeColorChangedListener, IShapeWidget.OnStrokeColorChangedListener> _onStrokeColorChangedForwarders = new IdentityHashMap<>();
    private final ConcurrentLinkedQueue<ShapeWidget.OnStrokeWeightChangedListener> _onStrokeWeightChanged = new ConcurrentLinkedQueue<>();

    private int _strokeColor = Color.BLACK;
    private float _strokeWeight = 1f;

    public interface OnStrokeColorChangedListener {
        void onStrokeColorChanged(ShapeWidget shape);
    }

    @Override
    public final void addOnStrokeColorChangedListener(
            IShapeWidget.OnStrokeColorChangedListener l) {
        _onStrokeColorChanged.add(l);
    }

    public void addOnStrokeColorChangedListener(
            ShapeWidget.OnStrokeColorChangedListener l) {
        registerForwardedListener(_onStrokeColorChanged,
                _onStrokeColorChangedForwarders, l,
                new StrokeColorChangedForwarder(l));
    }

    @Override
    public final void removeOnStrokeColorChangedListener(
            IShapeWidget.OnStrokeColorChangedListener l) {
        _onStrokeColorChanged.remove(l);
    }

    public void removeOnStrokeColorChangedListener(
            ShapeWidget.OnStrokeColorChangedListener l) {
        unregisterForwardedListener(_onStrokeColorChanged,
                _onStrokeColorChangedForwarders, l);
    }

    public void setStrokeColor(int strokeColor) {
        if (_strokeColor != strokeColor) {
            _strokeColor = strokeColor;
            onStrokeColorChanged();
        }
    }

    public int getStrokeColor() {
        return _strokeColor;
    }

    private void onStrokeColorChanged() {
        for (IShapeWidget.OnStrokeColorChangedListener l : _onStrokeColorChanged) {
            l.onStrokeColorChanged(this);
        }
    }

    public interface OnStrokeWeightChangedListener {
        void onStrokeWeightChanged(ShapeWidget shape);
    }

    public void addOnStrokeWeightChangedListener(
            OnStrokeWeightChangedListener l) {
        _onStrokeWeightChanged.add(l);
    }

    public void removeOnStrokeWeightChangedListener(
            OnStrokeWeightChangedListener l) {
        _onStrokeWeightChanged.remove(l);
    }

    public void setStrokeWeight(float strokeWeight) {
        if (Float.compare(strokeWeight, _strokeWeight) != 0) {
            _strokeWeight = strokeWeight * MapView.DENSITY;
            onStrokeWeightChanged();
        }
    }

    public float getStrokeWeight() {
        return _strokeWeight;
    }

    private void onStrokeWeightChanged() {
        for (OnStrokeWeightChangedListener l : _onStrokeWeightChanged) {
            l.onStrokeWeightChanged(this);
        }
    }

    private final static class StrokeColorChangedForwarder
            implements IShapeWidget.OnStrokeColorChangedListener {
        final ShapeWidget.OnStrokeColorChangedListener _cb;

        StrokeColorChangedForwarder(
                ShapeWidget.OnStrokeColorChangedListener cb) {
            _cb = cb;
        }

        @Override
        public void onStrokeColorChanged(IShapeWidget shape) {
            if (shape instanceof ShapeWidget)
                _cb.onStrokeColorChanged((ShapeWidget) shape);
        }
    }
}
