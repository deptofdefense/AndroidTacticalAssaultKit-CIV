package com.atakmap.util;

public final class Disposer implements AutoCloseable {
    final gov.tak.api.util.Disposable value;

    public Disposer(Disposable value) {
        this.value = value;
    }
    public Disposer(gov.tak.api.util.Disposable value) {
        this.value = value;
    }

    @Override
    public void close() {
        this.value.dispose();
    }
}
