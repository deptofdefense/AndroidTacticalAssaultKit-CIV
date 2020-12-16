
package com.atakmap.android.widgets;

import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Widget used for rendering a {@link Drawable}
 */
public class DrawableWidget extends MapWidget2 {

    public interface OnChangedListener {
        void onDrawableChanged(DrawableWidget widget);
    }

    private Drawable _drawable;
    private ColorFilter _colorFilter;
    private final ConcurrentLinkedQueue<OnChangedListener> _changeListeners;

    public DrawableWidget() {
        _changeListeners = new ConcurrentLinkedQueue<>();
    }

    public DrawableWidget(Drawable drawable) {
        this();
        setDrawable(drawable);
    }

    public void setDrawable(Drawable drawable) {
        if (_drawable != drawable) {
            _drawable = drawable;
            fireChangeListeners();
        }
    }

    public Drawable getDrawable() {
        return _drawable;
    }

    public void setColorFilter(ColorFilter filter) {
        _colorFilter = filter;
        fireChangeListeners();
    }

    public void setColor(int color, PorterDuff.Mode mode) {
        setColorFilter(new PorterDuffColorFilter(color, mode));
    }

    public void setColor(int color) {
        setColor(color, PorterDuff.Mode.MULTIPLY);
    }

    public ColorFilter getColorFilter() {
        return _colorFilter;
    }

    public void addChangeListener(OnChangedListener l) {
        synchronized (_changeListeners) {
            _changeListeners.add(l);
        }
    }

    public void removeChangeListener(OnChangedListener l) {
        synchronized (_changeListeners) {
            _changeListeners.remove(l);
        }
    }

    private List<OnChangedListener> getChangeListeners() {
        synchronized (_changeListeners) {
            return new ArrayList<>(_changeListeners);
        }
    }

    protected void fireChangeListeners() {
        for (OnChangedListener l : getChangeListeners())
            l.onDrawableChanged(this);
    }
}
