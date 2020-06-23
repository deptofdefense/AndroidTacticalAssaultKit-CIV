
package com.atakmap.android.widgets;

import android.graphics.Color;

import com.atakmap.android.maps.MapView;

import java.util.concurrent.ConcurrentLinkedQueue;

public class ShapeWidget extends MapWidget2 {

    private final ConcurrentLinkedQueue<OnStrokeColorChangedListener> _onStrokeColorChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnStrokeWeightChangedListener> _onStrokeWeightChanged = new ConcurrentLinkedQueue<>();
    private int _strokeColor = Color.BLACK;
    private float _strokeWeight = 1f;

    public interface OnStrokeColorChangedListener {
        void onStrokeColorChanged(ShapeWidget shape);
    }

    public void addOnStrokeColorChangedListener(
            OnStrokeColorChangedListener l) {
        _onStrokeColorChanged.add(l);
    }

    public void removeOnStrokeColorChangedListener(
            OnStrokeColorChangedListener l) {
        _onStrokeColorChanged.remove(l);
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
        for (OnStrokeColorChangedListener l : _onStrokeColorChanged) {
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

}
