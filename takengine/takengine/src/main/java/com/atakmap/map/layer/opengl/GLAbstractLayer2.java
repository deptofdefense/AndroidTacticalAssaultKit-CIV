package com.atakmap.map.layer.opengl;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.opengl.GLMapView;

public abstract class GLAbstractLayer2 implements GLLayer3, Layer.OnLayerVisibleChangedListener {

    protected MapRenderer renderContext;
    protected Layer subject;
    protected boolean visible;
    protected boolean initialized;
    protected int renderPassMask;

    // Used to trigger surface draw updates
    // Should only be touched on the GL thread
    private SurfaceRendererControl surfaceControl;
    
    protected GLAbstractLayer2(MapRenderer surface, Layer subject, int renderPassMask) {
        this.renderContext = surface;
        this.subject = subject;
        this.renderPassMask = renderPassMask;

        this.initialized = false;
    }
    
    protected abstract void drawImpl(GLMapView view, int renderPass);
    
    protected void init() {}

    @Override
    public final void draw(GLMapView view) {
        this.draw(view, GLMapView.RENDER_PASS_SURFACE|GLMapView.RENDER_PASS_SPRITES);
    }

    @Override
    public void release() {
        this.initialized = false;
    }

    /**************************************************************************/
    // GLLayer3

    @Override
    public void draw(GLMapView view, int pass) {
        if(!this.initialized) {
            this.init();
            this.surfaceControl = view.getControl(SurfaceRendererControl.class);
            this.initialized = true;
        }
        
        if(!this.visible)
            return;

        this.drawImpl(view, pass);
    }

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

    @Override
    public int getRenderPass() {
        return this.renderPassMask;
    }

    /**
     * Run code on the GL thread
     * @param r Runnable
     */
    protected void runOnGLThread(final Runnable r) {
        if(this.renderContext.isRenderThread())
            r.run();
        else
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    r.run();
                }
            });
    }

    /**
     * Mark the surface as dirty to trigger a draw update
     */
    protected void invalidate() {
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                invalidateNoSync();
            }
        });
    }

    protected void invalidateNoSync() {
        if (surfaceControl != null)
            surfaceControl.markDirty();
    }

    /**************************************************************************/
    // On Layer Visible Changed Listener

    @Override
    public void onLayerVisibleChanged(Layer layer) {
        final boolean visible = layer.isVisible();
        runOnGLThread(new Runnable() {
            @Override
            public void run() {
                GLAbstractLayer2.this.visible = visible;
                invalidateNoSync();
            }
        });
    }
}
