package com.atakmap.map.layer.feature.style.opengl;

import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.opengl.GLGeometry;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLRenderBatch;

public abstract class GLStyle {

    protected final Style style;
    
    public GLStyle(Style style) {
        this.style = style;
    }

    public abstract void draw(GLMapView view, GLGeometry geometry, StyleRenderContext ctx);
    public abstract void batch(GLMapView view, GLRenderBatch batch, GLGeometry geometry, StyleRenderContext ctx);
    public abstract boolean isBatchable(GLMapView view, GLGeometry geometry, StyleRenderContext ctx);
    
    public abstract StyleRenderContext createRenderContext(GLMapView view, GLGeometry geometry);
    public abstract void releaseRenderContext(StyleRenderContext ctx);
    
    public static class StyleRenderContext {}
}
