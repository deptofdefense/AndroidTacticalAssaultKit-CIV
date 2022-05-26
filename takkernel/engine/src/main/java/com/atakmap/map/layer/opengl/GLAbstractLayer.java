package com.atakmap.map.layer.opengl;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.opengl.GLMapView;

public abstract class GLAbstractLayer implements GLLayer2, Layer.OnLayerVisibleChangedListener {

    protected MapRenderer renderContext;
    protected Layer subject;
    protected boolean visible;
    protected boolean initialized;
    
    protected GLAbstractLayer(MapRenderer surface, Layer subject) {
        this.renderContext = surface;
        this.subject = subject;
        
        this.initialized = false;
    }
    
    protected abstract void drawImpl(GLMapView view);
    
    protected void init() {}

    @Override
    public void draw(GLMapView view) {
        if(!this.initialized) {
            this.init();
            this.initialized = true;
        }
        
        if(!this.visible)
            return;

        this.drawImpl(view);
    }

    @Override
    public void release() {
        this.initialized = false;
    }

    /**************************************************************************/
    // GL Layer

    @Override
    public Layer getSubject() {
        return this.subject;
    }


    @Override
    public void start() {
        this.subject.addOnLayerVisibleChangedListener(this);
        this.visible = this.subject.isVisible();
    }
    
    @Override
    public void stop() {
        this.subject.removeOnLayerVisibleChangedListener(this);
    }

    /**************************************************************************/
    // On Layer Visible Changed Listener

    @Override
    public void onLayerVisibleChanged(Layer layer) {
        final boolean visible = layer.isVisible();
        if(this.renderContext.isRenderThread())
            this.visible = visible;
        else
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLAbstractLayer.this.visible = visible;
                }
            });
    }
}
