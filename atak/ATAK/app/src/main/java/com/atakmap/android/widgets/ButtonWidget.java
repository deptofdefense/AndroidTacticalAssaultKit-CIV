
package com.atakmap.android.widgets;

import java.util.concurrent.ConcurrentLinkedQueue;

public class ButtonWidget extends AbstractButtonWidget {

    private float _width;
    private float _height;
    private final ConcurrentLinkedQueue<OnSizeChangedListener> _sizeChanged = new ConcurrentLinkedQueue<>();

    public interface OnSizeChangedListener {
        void onButtonSizeChanged(ButtonWidget button);
    }

    public void addOnSizeChangedListener(OnSizeChangedListener l) {
        _sizeChanged.add(l);
    }

    public void removeOnSizeChangedListener(OnSizeChangedListener l) {
        _sizeChanged.remove(l);
    }

    public void setSize(float width, float height) {
        if (_width != width || _height != height) {
            _width = width;
            _height = height;
            onSizeChanged();
        }
    }

    public float getButtonWidth() {
        return _width;
    }

    public float getButtonHeight() {
        return _height;
    }

    protected void onSizeChanged() {
        for (OnSizeChangedListener l : _sizeChanged) {
            l.onButtonSizeChanged(this);
        }
    }

    @Override
    public MapWidget seekHit(float x, float y) {
        MapWidget hit = null;

        if (isVisible() &&
                testHit(x, -y)) {
            hit = this;
        }

        return hit;
    }

    @Override
    public boolean testHit(float x, float y) {
        return x >= 0 && x < getButtonWidth() &&
                y >= 0 && y < getButtonHeight();
    }
}
