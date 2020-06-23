package com.atakmap.map.layer.opengl;

import android.util.Pair;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLResolvableMapRenderable;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLResolvable;
import com.atakmap.spi.PriorityServiceProviderRegistry2;

public final class GLLayerFactory {
    private final static PriorityServiceProviderRegistry2<GLLayer2, Pair<MapRenderer, Layer>, GLLayerSpi2> REGISTRY = new PriorityServiceProviderRegistry2<GLLayer2, Pair<MapRenderer, Layer>, GLLayerSpi2>();
    
    private GLLayerFactory() {}
    
    public static void register(GLLayerSpi2 spi) {
            REGISTRY.register(spi, spi.getPriority());
    }
    
    public static void unregister(GLLayerSpi2 spi) {
        REGISTRY.unregister(spi);
    }
    
    public synchronized static GLLayer2 create3(MapRenderer surface, Layer layer) {
        return adapt(REGISTRY.create(Pair.create(surface, layer))); 
    }
    
    public synchronized static GLLayer3 create4(MapRenderer surface, Layer layer) {
        return adapt2(REGISTRY.create(Pair.create(surface, layer))); 
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
}
