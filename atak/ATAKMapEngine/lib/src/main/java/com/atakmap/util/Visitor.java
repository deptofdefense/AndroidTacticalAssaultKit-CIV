package com.atakmap.util;

public interface Visitor<T> {
    void visit(T object);
}
