package com.atakmap.map.layer;

public final class LegacyAdapters {
    private LegacyAdapters() {}

    public static Layer2 adapt(final Layer impl) {
        if(impl instanceof Layer2)
            return (Layer2)impl;
        return new Layer2() {
            @Override
            public void setVisible(boolean visible) { impl.setVisible(visible); }
            @Override
            public boolean isVisible() { return impl.isVisible(); }
            // XXX - next two should be implemented via forwarding to pass correct layer reference
            @Override
            public void addOnLayerVisibleChangedListener(OnLayerVisibleChangedListener l) { impl.addOnLayerVisibleChangedListener(l); }
            @Override
            public void removeOnLayerVisibleChangedListener(OnLayerVisibleChangedListener l) { impl.removeOnLayerVisibleChangedListener(l); }
            @Override
            public String getName() { return impl.getName(); }
            @Override
            public <T extends Extension> T getExtension(Class<T> clazz) { return null; }
        };
    }
}
