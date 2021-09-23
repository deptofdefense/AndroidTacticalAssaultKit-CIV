package com.atakmap.util;

public interface Filter<T> {
    public boolean accept(T arg);
}
