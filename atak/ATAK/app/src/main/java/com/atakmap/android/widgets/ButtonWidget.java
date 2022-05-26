
package com.atakmap.android.widgets;

import com.atakmap.annotations.DeprecatedApi;

import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.api.widgets.IButtonWidget;

@Deprecated
@DeprecatedApi(since = "4.4")
public class ButtonWidget extends AbstractButtonWidget
        implements IButtonWidget {

    private float _width;
    private float _height;
    private final ConcurrentLinkedQueue<IButtonWidget.OnSizeChangedListener> _sizeChanged = new ConcurrentLinkedQueue<>();

    public interface OnSizeChangedListener {
        void onButtonSizeChanged(ButtonWidget button);
    }

    public void addOnSizeChangedListener(
            IButtonWidget.OnSizeChangedListener l) {
        _sizeChanged.add(l);
    }

    public void removeOnSizeChangedListener(
            IButtonWidget.OnSizeChangedListener l) {
        _sizeChanged.remove(l);
    }

    @Override
    public boolean setSize(float width, float height) {
        if (_width != width || _height != height) {
            _width = width;
            _height = height;
            onSizeChanged();
        }

        return super.setSize(width, height);
    }

    public float getButtonWidth() {
        return _width;
    }

    public float getButtonHeight() {
        return _height;
    }

    public void onSizeChanged() {
        for (IButtonWidget.OnSizeChangedListener l : _sizeChanged) {
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
