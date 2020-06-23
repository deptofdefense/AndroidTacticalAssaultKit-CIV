package com.atakmap.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

public final class ArrayIterator<T> implements Iterator<T> {

    private final T[] array;
    private int index;
    
    public ArrayIterator(T[] array) {
        this.array = array;
        this.index = 0;
    }

    @Override
    public boolean hasNext() {
        return (this.index < this.array.length);
    }

    @Override
    public T next() {
        if(this.index == this.array.length)
            throw new NoSuchElementException();
        return this.array[this.index++];
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
