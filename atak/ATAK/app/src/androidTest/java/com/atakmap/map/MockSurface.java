
package com.atakmap.map;

import com.atakmap.util.Collections2;

import java.util.Set;

public class MockSurface implements RenderSurface {
    int width;
    int height;
    double dpi;
    final Set<OnSizeChangedListener> listeners = Collections2
            .newIdentityHashSet();

    public MockSurface(int width, int height, double dpi) {
        this.width = width;
        this.height = height;
        this.dpi = dpi;
    }

    @Override
    public double getDpi() {
        return this.dpi;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public void addOnSizeChangedListener(OnSizeChangedListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    @Override
    public void removeOnSizeChangedListener(OnSizeChangedListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    public void setSize(int w, int h) {
        synchronized (listeners) {
            this.width = w;
            this.height = h;
            for (OnSizeChangedListener l : listeners)
                l.onSizeChanged(this, width, height);
        }
    }
}
