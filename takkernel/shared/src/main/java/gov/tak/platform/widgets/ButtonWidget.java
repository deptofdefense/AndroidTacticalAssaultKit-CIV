
package gov.tak.platform.widgets;

import gov.tak.platform.ui.MotionEvent;

import java.util.concurrent.ConcurrentLinkedQueue;

import gov.tak.api.widgets.IButtonWidget;
import gov.tak.api.widgets.IMapWidget;

public class ButtonWidget extends AbstractButtonWidget implements IButtonWidget {

    private float _width;
    private float _height;
    private final ConcurrentLinkedQueue<OnSizeChangedListener> _sizeChanged = new ConcurrentLinkedQueue<>();

    public void addOnSizeChangedListener(OnSizeChangedListener l) {
        _sizeChanged.add(l);
    }

    public void removeOnSizeChangedListener(OnSizeChangedListener l) {
        _sizeChanged.remove(l);
    }

    public float getButtonWidth() {
        return _width;
    }

    public float getButtonHeight() {
        return _height;
    }

    public void onSizeChanged() {
        for (OnSizeChangedListener l : _sizeChanged) {
            l.onButtonSizeChanged(this);
        }
    }

    @Override
    public IMapWidget seekWidgetHit(MotionEvent event, float x, float y) {
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
