
package com.atakmap.android.gridlines;

import android.graphics.Color;

import com.atakmap.map.layer.AbstractLayer;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class GridLinesOverlay extends AbstractLayer {

    private int _color = Color.WHITE;
    private String _type = "MGRS"; //default type
    private final Set<OnGridLinesColorChangedListener> gridLinesColorChangedListeners;
    private final Set<OnGridLinesTypeChangedListener> gridLinesTypeChangedListeners;

    public interface OnGridLinesColorChangedListener {
        void onGridLinesColorChanged(GridLinesOverlay layer);
    }

    public interface OnGridLinesTypeChangedListener {
        void onGridLinesTypeChanged(GridLinesOverlay layer);
    }

    GridLinesOverlay() {
        super("Grid Lines");

        this.gridLinesColorChangedListeners = Collections
                .newSetFromMap(
                        new IdentityHashMap<OnGridLinesColorChangedListener, Boolean>());
        this.gridLinesTypeChangedListeners = Collections.newSetFromMap(
                new IdentityHashMap<OnGridLinesTypeChangedListener, Boolean>());
    }

    public synchronized void setColor(int color) {
        _color = color;
        this.dispatchGridLinesColorChangedNoSync();
    }

    public synchronized int getColor() {
        return _color;
    }

    public synchronized void setType(String type) {
        _type = type;
        this.dispatchGridLinesTypeChangedNoSync();
    }

    public synchronized String getType() {
        return _type;
    }

    private void dispatchGridLinesTypeChangedNoSync() {
        for (OnGridLinesTypeChangedListener l : this.gridLinesTypeChangedListeners)
            l.onGridLinesTypeChanged(this);
    }

    public synchronized void addGridLinesTypeChangedListener(
            OnGridLinesTypeChangedListener l) {
        this.gridLinesTypeChangedListeners.add(l);
    }

    private void dispatchGridLinesColorChangedNoSync() {
        for (OnGridLinesColorChangedListener l : this.gridLinesColorChangedListeners)
            l.onGridLinesColorChanged(this);
    }

    public synchronized void addGridLinesColorChangedListener(
            OnGridLinesColorChangedListener l) {
        this.gridLinesColorChangedListeners.add(l);
    }

    public synchronized void removeGridLinesColorChangedListener(
            OnGridLinesColorChangedListener l) {
        this.gridLinesColorChangedListeners.remove(l);
    }

    public synchronized void removeGridLinesTypeChangedListener(
            OnGridLinesTypeChangedListener l) {
        this.gridLinesTypeChangedListeners.remove(l);
    }
}
