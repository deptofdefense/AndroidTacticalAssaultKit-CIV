package com.atakmap.map.layer;

public interface Layer2 extends Layer {
    public <T extends Extension> T getExtension(Class<T> clazz);

    public static interface Extension {}
}
