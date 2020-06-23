package com.atakmap.spi;

public interface PriorityServiceProvider<T, V> extends ServiceProvider<T, V> {
    public int getPriority();
}
