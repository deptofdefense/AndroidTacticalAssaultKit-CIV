
package com.atakmap.android.widgets;

import android.view.MotionEvent;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Map widget with view properties such as width, height, margin, and padding
 */
public class MapWidget2 extends MapWidget {

    public static final int LEFT = 0;
    public static final int TOP = 1;
    public static final int RIGHT = 2;
    public static final int BOTTOM = 3;

    private final ConcurrentLinkedQueue<OnWidgetSizeChangedListener> _onSizeChanged = new ConcurrentLinkedQueue<>();

    protected float _width;
    protected float _height;
    protected float[] _margin = new float[] {
            0, 0, 0, 0
    };
    protected float[] _padding = new float[] {
            0, 0, 0, 0
    };
    protected boolean _touchable = true;

    public boolean setWidth(float width) {
        return setSize(width, _height);
    }

    public float getWidth() {
        return _width;
    }

    public boolean setHeight(float height) {
        return setSize(_width, height);
    }

    public float getHeight() {
        return _height;
    }

    public boolean setSize(float width, float height) {
        if (Float.compare(width, _width) != 0
                || Float.compare(height, _height) != 0) {
            _width = width;
            _height = height;
            onSizeChanged();
            return true;
        }
        return false;
    }

    public float[] getSize(boolean incPadding, boolean incMargin) {
        float width = _width;
        float height = _height;
        if (width <= 0 || height <= 0)
            return new float[] {
                    0, 0
            };
        if (incPadding) {
            width += _padding[LEFT] + _padding[RIGHT];
            height += _padding[TOP] + _padding[BOTTOM];
        }
        if (incMargin) {
            width += _margin[LEFT] + _margin[RIGHT];
            height += _margin[TOP] + _margin[BOTTOM];
        }
        return new float[] {
                Math.max(0, width), Math.max(0, height)
        };
    }

    public void setMargins(float left, float top, float right, float bottom) {
        if (Float.compare(left, _margin[LEFT]) != 0
                || Float.compare(top, _margin[TOP]) != 0
                || Float.compare(right, _margin[RIGHT]) != 0
                || Float.compare(bottom, _margin[BOTTOM]) != 0) {
            _margin[LEFT] = left;
            _margin[TOP] = top;
            _margin[RIGHT] = right;
            _margin[BOTTOM] = bottom;
            onSizeChanged();
        }
    }

    public float[] getMargins() {
        return new float[] {
                _margin[LEFT], _margin[TOP],
                _margin[RIGHT], _margin[BOTTOM]
        };
    }

    public boolean setPadding(float left, float top, float right,
            float bottom) {
        if (Float.compare(left, _padding[LEFT]) != 0
                || Float.compare(top, _padding[TOP]) != 0
                || Float.compare(right, _padding[RIGHT]) != 0
                || Float.compare(bottom, _padding[BOTTOM]) != 0) {
            _padding[LEFT] = left;
            _padding[TOP] = top;
            _padding[RIGHT] = right;
            _padding[BOTTOM] = bottom;
            onSizeChanged();
            return true;
        }
        return false;
    }

    public boolean setPadding(float p) {
        return setPadding(p, p, p, p);
    }

    public float[] getPadding() {
        return new float[] {
                _padding[LEFT], _padding[TOP],
                _padding[RIGHT], _padding[BOTTOM]
        };
    }

    @Override
    public boolean testHit(float x, float y) {
        return x >= 0 && x < _width + _padding[LEFT] + _padding[RIGHT]
                && y >= 0 && y < _height + _padding[TOP] + _padding[BOTTOM];
    }

    public MapWidget seekHit(MotionEvent event, float x, float y) {
        MapWidget r = null;
        if (isVisible() && isTouchable() && testHit(x, y))
            r = this;
        return r;
    }

    /**
     * Set whether this widget can be touched
     * @param touchable True if touchable
     */
    public void setTouchable(boolean touchable) {
        _touchable = touchable;
    }

    public boolean isTouchable() {
        return _touchable;
    }

    @Override
    public MapWidget seekHit(float x, float y) {
        return seekHit(null, x, y);
    }

    public void addOnWidgetSizeChangedListener(
            OnWidgetSizeChangedListener l) {
        _onSizeChanged.add(l);
    }

    public void removeOnWidgetSizeChangedListener(
            OnWidgetSizeChangedListener l) {
        _onSizeChanged.remove(l);
    }

    protected void onSizeChanged() {
        for (OnWidgetSizeChangedListener l : _onSizeChanged) {
            l.onWidgetSizeChanged(this);
        }
    }

    public interface OnWidgetSizeChangedListener {
        void onWidgetSizeChanged(MapWidget2 widget);
    }

    /**
     * Helper method for retrieving size of a widget with included
     * visibility and class checks
     * @param w Map widget
     * @param incPadding Include padding in size
     * @param incMargin Include margin in size
     * @return [width, height]
     */
    public static float[] getSize(MapWidget w, boolean incPadding,
            boolean incMargin) {
        if (w != null && w.isVisible() && w instanceof MapWidget2)
            return ((MapWidget2) w).getSize(incPadding, incMargin);
        return new float[] {
                0, 0
        };
    }
}
