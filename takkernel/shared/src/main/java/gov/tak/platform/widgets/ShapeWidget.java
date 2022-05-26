
package gov.tak.platform.widgets;

import android.graphics.Color;

import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.api.widgets.IShapeWidget;

public class ShapeWidget extends MapWidget implements IShapeWidget {

    private final ConcurrentLinkedQueue<OnStrokeColorChangedListener> _onStrokeColorChanged = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<OnStrokeWeightChangedListener> _onStrokeWeightChanged = new ConcurrentLinkedQueue<>();
    private int _strokeColor = Color.BLACK;
    private float _strokeWeight = 1f;



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
