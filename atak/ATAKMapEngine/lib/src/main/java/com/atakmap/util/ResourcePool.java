package com.atakmap.util;

import java.util.ArrayList;

public class ResourcePool<T> {

    protected final int capacity;
    private ArrayList<T> pool;

    public ResourcePool(int capacity) {
        this.capacity = capacity;
        this.pool = new ArrayList<T>(capacity);
    }
    
    public synchronized boolean put(T obj) {
        if(this.pool.size() == this.capacity)
            return false;
        return this.pool.add(obj);
    }
    
    public synchronized T get() {
        final int size = this.pool.size();
        if(size < 1)
            return null;
        return this.pool.remove(size-1);
    }
}
