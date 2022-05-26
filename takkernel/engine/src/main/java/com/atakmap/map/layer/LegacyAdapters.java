package com.atakmap.map.layer;

import java.lang.ref.WeakReference;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import gov.tak.api.engine.map.ILayer;

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

    public static gov.tak.api.engine.map.ILayer adapt2(com.atakmap.map.layer.Layer layer) {
        if(layer == null)   return null;
        else if(layer instanceof gov.tak.api.engine.map.ILayer) return (gov.tak.api.engine.map.ILayer)layer;
        else    return LayerAdapterCache.INSTANCE.adapt(layer);
    }

    public static com.atakmap.map.layer.Layer adapt(gov.tak.api.engine.map.ILayer layer) {
        if(layer == null)   return null;
        else if(layer instanceof com.atakmap.map.layer.Layer) return (com.atakmap.map.layer.Layer)layer;
        else    return LayerAdapterCache.INSTANCE.adapt(layer);
    }

    final static class LayerForwardAdapter implements gov.tak.api.engine.map.ILayer {
        final Map<OnLayerVisibleChangedListener, Layer.OnLayerVisibleChangedListener> _visibilityChangedListeners = new IdentityHashMap<>();
        final com.atakmap.map.layer.Layer _impl;

        LayerForwardAdapter(com.atakmap.map.layer.Layer impl) {
            _impl = impl;
        }

        @Override
        public void setVisible(boolean visible) {
            _impl.setVisible(visible);
        }

        @Override
        public boolean isVisible() {
            return _impl.isVisible();
        }

        @Override
        public void addOnLayerVisibleChangedListener(OnLayerVisibleChangedListener l) {
            final com.atakmap.map.layer.Layer.OnLayerVisibleChangedListener adapter;
            synchronized(_visibilityChangedListeners) {
                if(_visibilityChangedListeners.containsKey(l))  return;
                adapter = new VisibilityCallback(this, l);
                _visibilityChangedListeners.put(l, adapter);
            }
            _impl.addOnLayerVisibleChangedListener(adapter);
        }

        @Override
        public void removeOnLayerVisibleChangedListener(OnLayerVisibleChangedListener l) {
            final com.atakmap.map.layer.Layer.OnLayerVisibleChangedListener adapter;
            synchronized(_visibilityChangedListeners) {
                adapter = _visibilityChangedListeners.remove(l);
                if(adapter == null) return;
            }
            _impl.removeOnLayerVisibleChangedListener(adapter);
        }

        @Override
        public String getName() {
            return _impl.getName();
        }

        @Override
        public <T> T getExtension(Class<T> clazz) {
            if(!com.atakmap.map.layer.Layer2.Extension.class.isAssignableFrom(clazz)) return null;
            return (_impl instanceof com.atakmap.map.layer.Layer2) ?
                    (T)((com.atakmap.map.layer.Layer2)_impl).getExtension((Class)clazz) : null;
        }

        static final class VisibilityCallback implements com.atakmap.map.layer.Layer.OnLayerVisibleChangedListener {
            final WeakReference<ILayer> _target;
            final gov.tak.api.engine.map.ILayer.OnLayerVisibleChangedListener _cb;

            VisibilityCallback(gov.tak.api.engine.map.ILayer target, gov.tak.api.engine.map.ILayer.OnLayerVisibleChangedListener cb) {
                _target = new WeakReference<>(target);
                _cb = cb;
            }

            @Override
            public void onLayerVisibleChanged(com.atakmap.map.layer.Layer layer) {
                final gov.tak.api.engine.map.ILayer target = _target.get();
                if(target != null)  _cb.onLayerVisibleChanged(target, layer.isVisible());
            }
        }
    }

    final static class LayerBackwardAdapter implements com.atakmap.map.layer.Layer2 {
        final Map<com.atakmap.map.layer.Layer.OnLayerVisibleChangedListener, gov.tak.api.engine.map.ILayer.OnLayerVisibleChangedListener> _visibilityChangedListeners = new IdentityHashMap<>();
        final gov.tak.api.engine.map.ILayer _impl;

        LayerBackwardAdapter(gov.tak.api.engine.map.ILayer impl) {
            _impl = impl;
        }

        @Override
        public void setVisible(boolean visible) {
            _impl.setVisible(visible);
        }

        @Override
        public boolean isVisible() {
            return _impl.isVisible();
        }

        @Override
        public void addOnLayerVisibleChangedListener(OnLayerVisibleChangedListener l) {
            final gov.tak.api.engine.map.ILayer.OnLayerVisibleChangedListener adapter;
            synchronized(_visibilityChangedListeners) {
                if(_visibilityChangedListeners.containsKey(l))  return;
                adapter = new VisibilityCallback(this, l);
                _visibilityChangedListeners.put(l, adapter);
            }
            _impl.addOnLayerVisibleChangedListener(adapter);
        }

        @Override
        public void removeOnLayerVisibleChangedListener(OnLayerVisibleChangedListener l) {
            final gov.tak.api.engine.map.ILayer.OnLayerVisibleChangedListener adapter;
            synchronized(_visibilityChangedListeners) {
                adapter = _visibilityChangedListeners.remove(l);
                if(adapter == null) return;
            }
            _impl.removeOnLayerVisibleChangedListener(adapter);
        }

        @Override
        public String getName() {
            return _impl.getName();
        }

        @Override
        public <T extends Extension> T getExtension(Class<T> clazz) {
            return (T)_impl.getExtension(clazz);
        }

        static final class VisibilityCallback implements gov.tak.api.engine.map.ILayer.OnLayerVisibleChangedListener {
            final WeakReference<com.atakmap.map.layer.Layer> _target;
            final com.atakmap.map.layer.Layer.OnLayerVisibleChangedListener _cb;

            VisibilityCallback(com.atakmap.map.layer.Layer target, com.atakmap.map.layer.Layer.OnLayerVisibleChangedListener cb) {
                _target = new WeakReference<>(target);
                _cb = cb;
            }

            @Override
            public void onLayerVisibleChanged(gov.tak.api.engine.map.ILayer layer, boolean viisble) {
                final com.atakmap.map.layer.Layer target = _target.get();
                if(target != null)  _cb.onLayerVisibleChanged(target);
            }
        }
    }

    final static class LayerAdapterCache {
        final static LayerAdapterCache INSTANCE = new LayerAdapterCache();

        final Map<com.atakmap.map.layer.Layer, WeakReference<gov.tak.api.engine.map.ILayer>> layer_ilayer = new WeakHashMap<>();
        final Map<gov.tak.api.engine.map.ILayer, WeakReference<com.atakmap.map.layer.Layer>> ilayer_layer = new WeakHashMap<>();

        synchronized com.atakmap.map.layer.Layer adapt(gov.tak.api.engine.map.ILayer in) {
            do {
                final WeakReference<com.atakmap.map.layer.Layer> ref = ilayer_layer.get(in);
                if(ref == null) break;
                final com.atakmap.map.layer.Layer value = ref.get();
                if(value == null) break;

                return value;
            } while(false);
            final com.atakmap.map.layer.Layer adapted = new LayerBackwardAdapter(in);
            ilayer_layer.put(in, new WeakReference<>(adapted));
            layer_ilayer.put(adapted, new WeakReference<>(in));
            return adapted;
        }

        synchronized gov.tak.api.engine.map.ILayer adapt(com.atakmap.map.layer.Layer in) {
            do {
                final WeakReference<gov.tak.api.engine.map.ILayer> ref = layer_ilayer.get(in);
                if(ref == null) break;
                final gov.tak.api.engine.map.ILayer value = ref.get();
                if(value == null) break;

                return value;
            } while(false);
            final gov.tak.api.engine.map.ILayer adapted = new LayerForwardAdapter(in);
            layer_ilayer.put(in, new WeakReference<>(adapted));
            ilayer_layer.put(adapted, new WeakReference<>(in));
            return adapted;
        }
    }
}
