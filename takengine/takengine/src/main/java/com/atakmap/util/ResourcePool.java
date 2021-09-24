package com.atakmap.util;

public class ResourcePool<T> {

    protected final int capacity;
    private Object[] pool;
    private int size;

    public ResourcePool(int capacity) {
        this.capacity = capacity;
        this.pool = new Object[capacity];
        this.size = 0;
    }
    
    public synchronized boolean put(T obj) {
        if(this.size == this.capacity)
            return false;
        this.pool[this.size++] = obj;
        return true;
    }
    
    public synchronized T get() {
        if(this.size == 0)
            return null;
        return (T)this.pool[--this.size];
    }
}
