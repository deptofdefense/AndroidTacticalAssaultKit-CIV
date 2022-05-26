package com.atakmap.map.layer.opengl;

import android.util.Pair;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.ProxyLayer;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLResolvableMapRenderable;

public class GLProxyLayer extends GLAbstractLayer2 implements
        ProxyLayer.OnProxySubjectChangedListener,
        GLResolvableMapRenderable {

    public final static GLLayerSpi2 SPI2 = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            // ProxyLayer : Layer
            return 1;
        }
        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
            final MapRenderer surface = arg.first;
            final Layer layer = arg.second;
            if(layer instanceof ProxyLayer)
                return new GLProxyLayer(surface, (ProxyLayer)layer);
            return null;
        }
    };
    
    protected Pair<Layer, GLLayer2> renderer;
    protected GLLayer impl;

    protected ProxyLayer subject;

    protected GLProxyLayer(MapRenderer surface, ProxyLayer subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SURFACE|GLMapView.RENDER_PASS_SPRITES|GLMapView.RENDER_PASS_SCENES|GLMapView.RENDER_PASS_UI);
        
        this.subject = subject;
        this.renderer = new Pair<Layer, GLLayer2>(null, null);
    }

    public final void refreshSubject() {
        final Layer layer = this.subject.get();
        
        // the layer has not changed
        if(layer == this.renderer.first)
            return;

        // stop the previous renderer
        if(this.renderer.second != null)
            this.renderer.second.stop();
        
        // create the new renderer
        this.renderer = new Pair<Layer, GLLayer2>(layer, GLLayerFactory.create4(this.renderContext, layer));
        if(this.renderer.second != null) {
            this.renderer.second.start();
        }

        if(this.renderContext.isRenderThread()) {
            this.refreshSubjectImpl2(this.renderer.second);
        } else {
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLProxyLayer.this.refreshSubjectImpl2(renderer.second);
                }
            });
        }
    }
    
    protected void refreshSubjectImpl2(GLLayer2 layer) {
        if(this.impl != null)
            this.impl.release();
        this.impl = layer;
    }

    @Override
    public State getState() {
        if (this.impl != null && this.impl instanceof GLResolvableMapRenderable)
            return ((GLResolvableMapRenderable) this.impl).getState();
        return State.RESOLVED;
    }

    @Override
    public void suspend() {
    }

    @Override
    public void resume() {
    }

    /**************************************************************************/
    // GL Abstract Layer

    @Override
    public void start() {
        super.start();
        
        // start receiving callbacks
        this.subject.addOnProxySubjectChangedListener(this);

        this.refreshSubject();
    }
    
    @Override
    public void stop() {
        super.stop();

        this.subject.removeOnProxySubjectChangedListener(this);

        if(this.renderer.second != null)
            this.renderer.second.stop();
    }

    @Override
    protected void init() {
        super.init();
        
        this.refreshSubject();
    }

    @Override
    protected void drawImpl(GLMapView view, int renderPass) {
        if(this.impl != null) {
            final GLLayer3 impl3 = (GLLayer3)this.impl;
            if((impl3.getRenderPass()&renderPass) != 0)
                impl3.draw(view, renderPass);
        }
    }
    
    @Override
    public void release() {
        if(this.impl != null)
            this.impl.release();

        super.release();        
    }

    /**************************************************************************/
    // On Proxy Subject Changed Listener

    @Override
    public void onProxySubjectChanged(ProxyLayer layer) {
        this.refreshSubject();
    }

} // GLProxyLayer
