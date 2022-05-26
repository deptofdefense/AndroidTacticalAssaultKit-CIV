
package com.atakmap.android.elev;

import com.atakmap.map.elevation.ElevationSource;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.util.Collections2;

import java.util.Set;

final class ElevationContentChangedDispatcher extends ElevationSource {

    final String _name;
    final Set<OnContentChangedListener> _listeners;

    ElevationContentChangedDispatcher(String name) {
        _name = name;
        _listeners = Collections2.newIdentityHashSet();
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public Cursor query(QueryParameters params) {
        return Cursor.EMPTY;
    }

    @Override
    public Envelope getBounds() {
        return new Envelope(0d, 0d, 0d, 0d, 0d, 0d);
    }

    @Override
    public synchronized void addOnContentChangedListener(
            OnContentChangedListener l) {
        _listeners.add(l);
    }

    @Override
    public synchronized void removeOnContentChangedListener(
            OnContentChangedListener l) {
        _listeners.remove(l);
    }

    synchronized void contentChanged() {
        for (OnContentChangedListener l : _listeners)
            l.onContentChanged(this);
    }
}
