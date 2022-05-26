package com.atakmap.map.layer.opengl;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.util.Pair;

import com.atakmap.coremap.log.Log;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.MultiLayer;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLResolvableMapRenderable;

public class GLMultiLayer extends GLAbstractLayer2 implements
        MultiLayer.OnLayersChangedListener, GLResolvableMapRenderable {

    public final static GLLayerSpi2 SPI2 = new GLLayerSpi2() {

        @Override
        public int getPriority() {
            // MultiLayer : Layer
            return 1;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
            final MapRenderer surface = arg.first;
            final Layer layer = arg.second;
            if(layer instanceof MultiLayer)
                return new GLMultiLayer(surface, (MultiLayer)layer);
            return null;
        }
    };

    protected List<GLLayer> layers;
    protected Map<Layer, GLLayer2> renderers;
    protected MultiLayer subject;

    public GLMultiLayer(MapRenderer surface, MultiLayer subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SURFACE|GLMapView.RENDER_PASS_SPRITES|GLMapView.RENDER_PASS_SCENES|GLMapView.RENDER_PASS_UI);

        this.subject = subject;

        this.layers = new LinkedList<GLLayer>();
        this.renderers = new IdentityHashMap<Layer, GLLayer2>();
    }

    @Override
    public void start() {
        super.start();

        final List<Layer> layers = this.subject.getLayers();
        for(Layer layer : layers) {
        final GLLayer2 renderer = GLLayerFactory.create3(this.renderContext, layer);
            if(renderer != null) {
                // if a renderer was created, start it and do the GL refresh
                renderer.start();
                this.renderers.put(layer, renderer);
            }
        }

        this.refreshLayers2();
        this.subject.addOnLayersChangedListener(this);
    }

    @Override
    public void stop() {
        super.stop();

        for(GLLayer2 renderer : this.renderers.values())
            renderer.stop();

        this.subject.removeOnLayersChangedListener(this);
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void drawImpl(GLMapView view, int renderPass) {
        for(GLLayer r : this.layers) {
            final GLLayer3 l = (GLLayer3)r;
            if((l.getRenderPass()&renderPass) != 0)
                l.draw(view, renderPass);
        }
    }

    @Override
    public void release() {
        for(GLLayer l : this.layers)
            l.release();

        super.release();
    }

    @Override
    public State getState() {
        for (GLLayer r : this.layers) {
            if(r instanceof GLResolvableMapRenderable) {
                switch(((GLResolvableMapRenderable)r).getState()) {
                    case UNRESOLVED :
                    case RESOLVING :
                        // the renderable has not yet arrived at a terminal
                        // state
                        return State.RESOLVING;
                    default :
                        break;
                }
            }
        }
        return State.RESOLVED;
    }

    @Override
    public void suspend() {
    }

    @Override
    public void resume() {
    }
    
    protected void refreshLayers2() {
        final List<Layer> layers = this.subject.getLayers();
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                refreshLayersImpl2(layers, renderers);
                invalidateNoSync();
            }
        });
    }

    protected void refreshLayersImpl2(List<Layer> layers, Map<Layer, GLLayer2> renderers) {
        final Set<GLLayer> scratch = Collections.<GLLayer>newSetFromMap(new IdentityHashMap<GLLayer, Boolean>());
        scratch.addAll(this.layers);

        this.layers.clear();

        GLLayer glLayer;
        for(Layer layer : layers) {
            glLayer = renderers.get(layer);
            if(glLayer != null) {
                this.layers.add(glLayer);
                scratch.remove(glLayer);
            }
        }
        
        for(GLLayer layer : scratch)
            layer.release();
    }

    /**************************************************************************/
    // On Layers Changed Listener

    @Override
    public void onLayerAdded(MultiLayer parent, Layer layer) {
        if(this.renderers.containsKey(layer)) {
            Log.w("GLMultiLayer", "GLMultiLayer[" + this.subject.getName() + "] already has renderer for " + layer.getName());
        } else {
            final GLLayer3 renderer = GLLayerFactory.create4(this.renderContext, layer);
            if(renderer != null) {
                // if a renderer was created, start it and do the GL refresh
                renderer.start();
                this.renderers.put(layer, renderer);
                this.refreshLayers2();    
            }
        }
    }

    @Override
    public void onLayerRemoved(MultiLayer parent, Layer layer) {
        final GLLayer2 renderer = this.renderers.remove(layer);
        if(renderer != null) {
            renderer.stop();
            this.refreshLayers2();
        }
    }

    @Override
    public void onLayerPositionChanged(MultiLayer parent, Layer layer, int oldPosition,
            int newPosition) {

        this.refreshLayers2();
    }
}
