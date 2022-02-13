
package com.atakmap.util;

import java.lang.ref.Reference;
import java.util.Iterator;

public final class DereferencingIterator<V> implements Iterator<V> {

    private Iterator<? extends Reference<V>> impl;

    public DereferencingIterator(Iterator<? extends Reference<V>> impl) {
        this.impl = impl;
    }

    @Override
    public boolean hasNext() {
        return this.impl.hasNext();
    }

    @Override
    public V next() {
        final Reference<V> ref = this.impl.next();
        if (ref == null)
            return null;
        return ref.get();
    }

    @Override
    public void remove() {
        this.impl.remove();
    }
}
