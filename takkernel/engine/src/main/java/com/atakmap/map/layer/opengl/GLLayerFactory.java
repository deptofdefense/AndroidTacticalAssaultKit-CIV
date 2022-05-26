package com.atakmap.map.layer.opengl;

import android.util.Pair;

import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.map.Interop;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLResolvableMapRenderable;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLResolvable;
import com.atakmap.spi.PriorityServiceProviderRegistry2;

import java.util.IdentityHashMap;
import java.util.Map;

public final class GLLayerFactory {
    final static NativePeerManager.Cleaner layer2Cleaner = new NativePeerManager.Cleaner() {
        @Override
        protected void run(Pointer pointer, Object opaque) {
            destructLayer2(pointer);
        }
    };

    static Interop<GLLayerSpi2> GLLayerSpi2_interop = Interop.findInterop(GLLayerSpi2.class);
    static Interop<GLMapView> GLMapView_interop = Interop.findInterop(GLMapView.class);
    static Interop<Layer> Layer_interop = Interop.findInterop(Layer.class);

    private final static PriorityServiceProviderRegistry2<GLLayer2, Pair<MapRenderer, Layer>, GLLayerSpi2> REGISTRY = new PriorityServiceProviderRegistry2<GLLayer2, Pair<MapRenderer, Layer>, GLLayerSpi2>();
    final static Map<GLLayerSpi2, Pointer> spis = new IdentityHashMap<>();

    private GLLayerFactory() {}
    
    public static void register(GLLayerSpi2 spi) {
        synchronized(spis) {
            if(spis.containsKey(spi))
                return;
            final Pointer pointer = GLLayerSpi2_interop.wrap(spi);
            spis.put(spi, pointer);
            register(pointer, spi.getPriority());
        }
    }
    
    public static void unregister(GLLayerSpi2 spi) {
        synchronized(spis) {
            final Pointer pointer = spis.remove(spi);
            if(pointer == null)
                return;
            unregister(pointer.raw);
            GLLayerSpi2_interop.destruct(pointer);
        }
    }
    
    public static GLLayer2 create3(MapRenderer surface, Layer layer) {
        return adapt(createImpl(surface, layer));
    }

    private static GLLayer2 createImpl(MapRenderer surface, Layer layer) {
        // XXX -
        if(!(surface instanceof GLMapView))
            return null;

        Pointer layerPointer = null;
        try {
            Pointer layer2Pointer;
            if (Layer_interop.hasPointer(layer)) {
                layer2Pointer = adaptNativeToLayer2(Layer_interop.getPointer(layer));
            } else {
                // the Layer is not backed by a native pointer, we will wrap
                // it to pass it through the factory. Note that this *must*
                // return a Java GLLayer2 instance as only a Java GLLayerSpi2
                // will accept a Java Layer instance.  We'll keep the wrapping
                // native pointer valid until the instance is returned, then
                // destruct it, as all resources should be pure Java after
                // 'create(long, long)' returns
                layerPointer = Layer_interop.wrap(layer);
                layer2Pointer = adaptJavaToLayer2(layerPointer);
            }

            final GLLayer2 retval = create(GLMapView_interop.getPointer((GLMapView) surface), layer2Pointer.raw);
            // if a result was produced, track with the NativePeerManager and
            // cleanup the native wrapper once no longer live, else destruct
            // immediately
            if(retval != null)
                NativePeerManager.register(retval, layer2Pointer, null, null, layer2Cleaner);
            else
                layer2Cleaner.run(layer2Pointer, null, null);
            return retval;
        } finally {
            if(layerPointer != null)
                Layer_interop.destruct(layerPointer);
        }
    }

    public synchronized static GLLayer3 create4(MapRenderer surface, Layer layer) {
        return adapt2(createImpl(surface, layer));
    }
    
    public static GLLayer2 adapt(final GLLayer layer) {
        return adapt2(layer); 
    }

    public static GLLayer3 adapt2(final GLLayer layer) {
        if(layer == null)
            return null;
        else if(layer instanceof GLLayer3)
            return (GLLayer3)layer;
        else if(layer instanceof GLResolvable)
            return new ResolvableAdapter(layer);
        else
            return new Adapter(layer);
    }
    
    private static class Adapter implements GLLayer3 {
        private final GLLayer layer1;
        private final GLLayer2 layer2;
        
        public Adapter(GLLayer layer1) {
            this.layer1 = layer1;
            this.layer2 = ((layer1 instanceof GLLayer2) ? (GLLayer2)layer1 : null);
        }
        
        @Override
        public final void start() { 
             if(layer2 != null) layer2.start();
        }

        @Override
        public final void stop() { 
             if(layer2 != null) layer2.stop(); 
        }

        @Override
        public final Layer getSubject() { 
             return layer1.getSubject(); 
        }

        @Override
        public final void draw(GLMapView view) { 
             layer1.draw(view); 
        }

        @Override
        public final void release() { 
             layer1.release(); 
        }

        @Override
        public final int getRenderPass() { 
             return GLMapView.RENDER_PASS_SURFACE; 
        }

        @Override
        public final void draw(GLMapView view, int renderPass) {
            if(MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SURFACE))
                this.draw(view);
        }
    }
    
    private static class ResolvableAdapter extends Adapter implements GLResolvableMapRenderable {
        private final GLResolvable resolvable;
        
        public ResolvableAdapter(GLLayer layer1) {
            super(layer1);
            
            this.resolvable = (GLResolvable)layer1;
        }

        @Override
        public State getState() { return this.resolvable.getState(); }
        @Override
        public void suspend() { this.resolvable.suspend(); }
        @Override
        public void resume() { this.resolvable.resume(); }
    }

    static native void register(Pointer pointer, int priority);
    static native void unregister(long pointer);
    static native GLLayer2 create(long viewptr, long layer2ptr);

    static native Pointer adaptJavaToLayer2(Pointer pointer);
    static native Pointer adaptNativeToLayer2(long ptr);
    static native void destructLayer2(Pointer pointer);
}
