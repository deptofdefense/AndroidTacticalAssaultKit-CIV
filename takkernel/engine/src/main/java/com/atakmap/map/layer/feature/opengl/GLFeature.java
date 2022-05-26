package com.atakmap.map.layer.feature.opengl;

import android.util.Pair;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.opengl.GLGeometry;
import com.atakmap.map.layer.feature.style.opengl.GLStyle;
import com.atakmap.map.layer.feature.style.opengl.GLStyleFactory;
import com.atakmap.map.opengl.GLMapBatchable;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLRenderBatch;

/** @deprecated use the batch feature renderering framework */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public final class GLFeature implements GLMapRenderable, GLMapBatchable {

    private MapRenderer surface;
    
    private Feature subject;
    private boolean initialized;

    private GLStyle style;
    private GLGeometry geometry;
    private GLStyle.StyleRenderContext styleCtx;
    
    public GLFeature(GLMapSurface surface, Feature feature) {
        this(surface.getGLMapView(), feature);
    }

    public GLFeature(MapRenderer surface, Feature feature) {
        this.surface = surface;
        this.subject = feature;
        this.styleCtx = null;
        
        this.initialized = false;
    }

    public Feature getSubject() {
        return this.subject;
    }
    
    public GLGeometry getGeometry(){
        if(this.geometry == null){
            this.geometry = GLGeometry.createRenderer(this.subject.getGeometry());
        }
        
        return this.geometry;
    }

    private void init(GLMapView view) {
        this.initialized = true;
        
        this.geometry = GLGeometry.createRenderer(this.subject.getGeometry());
        this.style = GLStyleFactory.create(Pair.<Style, Geometry>create(this.subject.getStyle(), this.subject.getGeometry()));
        if(this.style != null)
            this.styleCtx = this.style.createRenderContext(view, this.geometry);
    }

    public void update(final Feature feature) {
        if(GLMapSurface.isGLThread() && !Feature.isSame(this.subject, feature)) {
            this.subject = feature;
            this.release();
        } else if(!GLMapSurface.isGLThread()) {
            // XXX - if feature IDs can be assumed to be the same, we can
            //       compare versions here as version must be monotonically
            //       increasing
            this.surface.queueEvent(new Runnable() {
                @Override
                public void run() {
                    if(!Feature.isSame(GLFeature.this.subject, feature)) {
                        GLFeature.this.subject = feature;
                        GLFeature.this.release();
                    }
                }
            });
        }
    }

    @Override
    public boolean isBatchable(GLMapView view) {
        if(!this.initialized)
            this.init(view);
        return (this.style == null || this.style.isBatchable(view, this.geometry, this.styleCtx));
    }

    @Override
    public void batch(GLMapView view, GLRenderBatch batch) {
        if(!this.initialized)
            this.init(view);
        if(this.style == null)
            return;
        
        this.style.batch(view, batch, this.geometry, this.styleCtx);
    }

    @Override
    public void draw(GLMapView view) {
        if(!this.initialized)
            this.init(view);
        if(this.style == null)
            return;
        this.style.draw(view, this.geometry, this.styleCtx);
    }

    @Override
    public void release() {
        if(this.style != null) {
            this.style.releaseRenderContext(this.styleCtx);
            this.styleCtx = null;
            this.style = null;
        }
        if(this.geometry != null)
            this.geometry = null;
        this.initialized = false;
    }
}
