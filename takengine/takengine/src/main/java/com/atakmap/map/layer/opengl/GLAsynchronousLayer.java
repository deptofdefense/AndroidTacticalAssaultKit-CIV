package com.atakmap.map.layer.opengl;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.opengl.GLAsynchronousMapRenderable;
import com.atakmap.map.opengl.GLMapView;

public abstract class GLAsynchronousLayer<Pending> extends GLAsynchronousMapRenderable<Pending> implements GLLayer2, Layer.OnLayerVisibleChangedListener {

    protected final MapRenderer renderContext;
    
    protected final Layer subject;

    private boolean stopped;
    private boolean suspendRequested;
    private boolean visible;
    
    protected GLAsynchronousLayer(MapRenderer surface, Layer subject) {
        this.renderContext = surface;
        this.subject = subject;
        this.visible = subject.isVisible();

        // we will always construct in the stopped state, requiring start
        this.stopped = true;
        this.suspendRequested = false;
    }

    /**************************************************************************/
    // GL Layer

    @Override
    public final Layer getSubject() {
        return this.subject;
    }
    
    @Override
    public synchronized void start() {
        this.visible = this.subject.isVisible();
        this.subject.addOnLayerVisibleChangedListener(this);

        // move out of the stopped state
        this.stopped = false;
        // if there is no suspend requested, resume on start
        if(!this.suspendRequested)
            this.resumeImpl();
    }
    
    @Override
    public synchronized void stop() {
        this.subject.removeOnLayerVisibleChangedListener(this);

        // move into the stopped state
        this.stopped = true;

        // move into the suspended state to prevent further queries
        this.suspendImpl();
        
        // wait until any currently servicing request has completed before
        // returning
        while(this.servicingRequest) {
            try {
                this.wait(100);
            } catch(InterruptedException ignored) {}
        }
    }

    /**
     * Invokes the base {@link GLAsynchronousMapRenderable#suspend()} method.
     */
    protected final void suspendImpl() {
        super.suspend();
    }
    
    /**
     * Invokes the base {@link GLAsynchronousMapRenderable#resume()} method.
     */
    protected final void resumeImpl() {
        super.resume();
    }

    /**************************************************************************/
    // GL Asynchronous Map Renderable

    @Override
    public void draw(GLMapView view) {
        if(!this.visible)
            return;
        super.draw(view);
    }

    @Override
    public synchronized void suspend() {
        // mark that a suspend has been requested so that we will not resume
        // if moving from the stopped to started states
        this.suspendRequested = true;
        this.suspendImpl();
    }
    
    @Override
    public synchronized void resume() {
        // mark that there is no suspend requested so that we will move out of
        // the suspended state when starting
        this.suspendRequested = false;
        
        // if we are not in a stopped state, effect the resume, otherwise we
        // will wait until 'start' is invoked
        if(!this.stopped)
            this.resumeImpl();
    }
    
    @Override
    public void release() {
        super.release();
        
        synchronized(this) {
            // 'release()' should move the object back into the unresolved
            // state, per the contract of GLResolvableMapRenderable. We do not
            // want re-initialization to kick off the query thread, so if
            // stopped, move back into the suspended state. 
            if(this.stopped) {
                this.suspendImpl();
            }
        }
    }

    /*************************************************************************/
    //


    @Override
    public void onLayerVisibleChanged(Layer layer) {
        final boolean vis = layer.isVisible();
        this.renderContext.queueEvent(new Runnable() {
            public void run() {
                visible = vis;
            }
        });
    }
} // GLAsynchronousLayer
