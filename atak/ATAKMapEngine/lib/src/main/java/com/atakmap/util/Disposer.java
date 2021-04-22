package com.atakmap.util;

public final class Disposer implements AutoCloseable {
    final Disposable value;

    public Disposer(Disposable value) {
        this.value = value;
    }

    @Override
    public void close() {
        this.value.dispose();
    }
}
