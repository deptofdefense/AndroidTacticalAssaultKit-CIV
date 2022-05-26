package com.atakmap.util;

import java.util.Iterator;

public abstract class TransmuteIterator<T, V> implements Iterator<V> {
    protected final Iterator<T> impl;
    
    public TransmuteIterator(Iterator<T> impl) {
        this.impl = impl;
    }

    protected abstract V transmute(T arg);

    @Override
    public boolean hasNext() {
        return this.impl.hasNext();
    }

    @Override
    public V next() {
        return this.transmute(this.impl.next());
    }

    @Override
    public void remove() {
        this.impl.remove();
    }
}
