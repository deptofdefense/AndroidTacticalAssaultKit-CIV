package com.atakmap.map.layer.elevation;

import android.util.Pair;
import com.atakmap.map.Interop;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.Layer2;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.opengl.GLLayerSpi2;

import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.Map;

public final class TerrainSlopeLayer implements Layer2 {

    final static Interop<Layer> Layer_interop = Interop.findInterop(Layer.class);

    static {
        GLLayerFactory.register(new GLLayerSpi2() {
            @Override
            public int getPriority() {
                return 1;
            }

            @Override
            public GLLayer2 create(Pair<MapRenderer, Layer> object) {
                if(!(object.second instanceof TerrainSlopeLayer))
                    return null;
                return GLLayerFactory.create3(object.first, ((TerrainSlopeLayer)object.second).impl);
            }
        });
    }
    final Map<OnLayerVisibleChangedListener, OnLayerVisibleChangedListener> listenerWrappers = new IdentityHashMap<>();
    final Layer impl;

    public TerrainSlopeLayer(String name) {
        this.impl = newInstance(name);
    }

    @Override
    public void setVisible(boolean visible) {
        impl.setVisible(visible);
    }

    @Override
    public boolean isVisible() {
        return impl.isVisible();
    }

    @Override
    public void addOnLayerVisibleChangedListener(final OnLayerVisibleChangedListener l) {
        final OnLayerVisibleChangedListener wrapper;
        synchronized(listenerWrappers) {
            if (listenerWrappers.containsKey(l))
                return;
            wrapper = new OnLayerVisibleChangedListenerWrapper(this, l);
        }
        impl.addOnLayerVisibleChangedListener(wrapper);
    }

    @Override
    public void removeOnLayerVisibleChangedListener(OnLayerVisibleChangedListener l) {
        final OnLayerVisibleChangedListener wrapper;
        synchronized(listenerWrappers) {
            wrapper =listenerWrappers.remove(l);
            if(wrapper == null)
                return;
        }
        impl.removeOnLayerVisibleChangedListener(wrapper);
    }

    @Override
    public String getName() {
        return impl.getName();
    }

    @Override
    public <T extends Extension> T getExtension(Class<T> clazz) {
        return null;
    }

    final static class OnLayerVisibleChangedListenerWrapper implements OnLayerVisibleChangedListener {
        final WeakReference<Layer> ref;
        final OnLayerVisibleChangedListener cb;

        OnLayerVisibleChangedListenerWrapper(Layer impl, OnLayerVisibleChangedListener cb) {
            this.ref = new WeakReference<>(impl);
            this.cb = cb;
        }

        @Override
        public void onLayerVisibleChanged(Layer layer) {
            Layer impl = ref.get();
            if(impl == null)
                return;
            cb.onLayerVisibleChanged(impl);
        }
    }
    public float getAlpha() {
        return getAlpha(Layer_interop.getPointer(impl));
    }
    public void setAlpha(float alpha) {
        setAlpha(Layer_interop.getPointer(impl), alpha);
    }

    static native Layer newInstance(String name);
    static native float getAlpha(long ptr);
    static native void setAlpha(long ptr, float alpha);
}
