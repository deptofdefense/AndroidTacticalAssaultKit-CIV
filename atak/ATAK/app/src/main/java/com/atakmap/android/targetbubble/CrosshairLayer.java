
package com.atakmap.android.targetbubble;

import com.atakmap.map.layer.AbstractLayer;
import com.atakmap.util.Collections2;

import java.util.Set;

public final class CrosshairLayer extends AbstractLayer {
    public interface OnCrosshairColorChangedListener {
        void onCrosshairColorChanged(CrosshairLayer layer, int color);
    }

    private int color;
    private final Set<OnCrosshairColorChangedListener> listeners = Collections2
            .newIdentityHashSet();

    public CrosshairLayer(String name) {
        super(name);

        this.color = 0xFF000000;
    }

    public synchronized void setColor(int color) {
        if (this.color == color)
            return;
        this.color = color;
        for (OnCrosshairColorChangedListener l : listeners)
            l.onCrosshairColorChanged(this, this.color);
    }

    public synchronized int getCrosshairColor() {
        return this.color;
    }

    public synchronized void addOnCrosshairColorChangedListener(
            OnCrosshairColorChangedListener l) {
        this.listeners.add(l);
    }

    public synchronized void removeOnCrosshairColorChangedListener(
            OnCrosshairColorChangedListener l) {
        this.listeners.remove(l);
    }
}
