
package com.atakmap.android.widgets;

import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

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

    public void addChangeListener(DrawableWidget.OnChangedListener l) {
        synchronized (_changeListeners) {
            _changeListeners.add(l);
        }
    }

    public final void removeChangeListener(DrawableWidget.OnChangedListener l) {
        synchronized (_changeListeners) {
            _changeListeners.remove(l);
        }
    }

    private List<DrawableWidget.OnChangedListener> getChangeListeners() {
        synchronized (_changeListeners) {
            return new ArrayList<>(_changeListeners);
        }
    }

    public void fireChangeListeners() {
        for (DrawableWidget.OnChangedListener l : getChangeListeners())
            l.onDrawableChanged(this);
    }
}
