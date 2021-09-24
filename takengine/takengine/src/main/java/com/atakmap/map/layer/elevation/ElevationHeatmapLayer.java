package com.atakmap.map.layer.elevation;

import android.util.Pair;
import com.atakmap.map.Interop;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.AbstractLayer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.Layer2;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayer3;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.opengl.GLLayerSpi2;

import java.util.IdentityHashMap;
import java.util.Map;

public final class ElevationHeatmapLayer implements Layer2 {

    final static Interop<Layer> Layer_interop = Interop.findInterop(Layer.class);

    static {
        GLLayerFactory.register(new GLLayerSpi2() {
            @Override
            public int getPriority() {
                return 1;
            }

            @Override
            public GLLayer2 create(Pair<MapRenderer, Layer> object) {
                if(!(object.second instanceof ElevationHeatmapLayer))
                    return null;
                return GLLayerFactory.create3(object.first, ((ElevationHeatmapLayer)object.second).impl);
            }
        });
    }
    final Map<OnLayerVisibleChangedListener, OnLayerVisibleChangedListener> listenerWrappers = new IdentityHashMap<>();
    final Layer impl;

    public ElevationHeatmapLayer(String name) {
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
            if(listenerWrappers.containsKey(l))
                return;
            wrapper = new OnLayerVisibleChangedListener() {
                @Override
                public void onLayerVisibleChanged(Layer layer) {
                    l.onLayerVisibleChanged(ElevationHeatmapLayer.this);
                }
            };
            listenerWrappers.put(l, wrapper);
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

    public float getAlpha() {
        return getAlpha(Layer_interop.getPointer(impl));
    }
    public float getSaturation() {
        return getSaturation(Layer_interop.getPointer(impl));
    }
    public float getValue() {
        return getValue(Layer_interop.getPointer(impl));
    }
    public void setAlpha(float alpha) {
        setAlpha(Layer_interop.getPointer(impl), alpha);
    }
    public void setSaturation(float saturation) {
        setSaturation(Layer_interop.getPointer(impl), saturation);
    }
    public void setValue(float value) {
        setValue(Layer_interop.getPointer(impl), value);
    }
    public double getRangeMinElevation() {
        return getRangeMinElevation(Layer_interop.getPointer(impl));
    }
    public double getRangeMaxElevation() {
        return getRangeMaxElevation(Layer_interop.getPointer(impl));
    }
    public void setAbsoluteRange(double min, double max) {
        setAbsoluteRange(Layer_interop.getPointer(impl), min, max);
    }
    public void setDynamicRange() {
        setDynamicRange(Layer_interop.getPointer(impl));
    }
    public boolean isDynamicRange() {
        return isDynamicRange(Layer_interop.getPointer(impl));
    }

    static native Layer newInstance(String name);
    static native float getAlpha(long ptr);
    static native float getSaturation(long ptr);
    static native float getValue(long ptr);
    static native void setAlpha(long ptr, float alpha);
    static native void setSaturation(long ptr, float saturation);
    static native void setValue(long ptr, float value);
    static native double getRangeMinElevation(long ptr);
    static native double getRangeMaxElevation(long ptr);
    static native void setAbsoluteRange(long ptr, double min, double max);
    static native void setDynamicRange(long ptr);
    static native boolean isDynamicRange(long ptr);
}
