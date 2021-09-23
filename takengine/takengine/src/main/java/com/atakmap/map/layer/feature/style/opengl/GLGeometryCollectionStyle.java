package com.atakmap.map.layer.feature.style.opengl;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import android.util.Pair;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.opengl.GLGeometry;
import com.atakmap.map.layer.feature.geometry.opengl.GLGeometryCollection;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLRenderBatch;

/** @deprecated use the batch feature renderering framework */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
final class GLGeometryCollectionStyle extends GLStyle {
    
    private final GLStyleSpi spi;

    GLGeometryCollectionStyle(Style style, GLStyleSpi spi) {
        super(style);
        
        this.spi = spi;
    }

    @Override
    public StyleRenderContext createRenderContext(GLMapView view, GLGeometry geometry) {
        return new Ctx(view, this.style, (GLGeometryCollection)geometry);
    }
    
    @Override
    public void releaseRenderContext(StyleRenderContext context) {
        if(context == null)
            return;
        Ctx ctx = (Ctx)context;
        for(Pair<GLStyle, StyleRenderContext> e : ctx.styles)
            if(e.first != null && e.second != null)
                e.first.releaseRenderContext(e.second);
    }

    @Override
    public void draw(GLMapView view, GLGeometry g, StyleRenderContext context) {
        final Ctx ctx = (Ctx)context;
        
        GLGeometryCollection geometry = (GLGeometryCollection)g;
        Iterator<GLGeometry> geomIter = geometry.iterator();
        GLGeometry child;
        for(Pair<GLStyle, StyleRenderContext> e : ctx.styles) {
            child = geomIter.next();
            if(e.first == null)
                continue;
            e.first.draw(view, child, e.second);
        }
    }

    @Override
    public void batch(GLMapView view, GLRenderBatch batch, GLGeometry g, StyleRenderContext context) {
        final Ctx ctx = (Ctx)context;
        
        GLGeometryCollection geometry = (GLGeometryCollection)g;
        Iterator<GLGeometry> geomIter = geometry.iterator();
        GLGeometry child;
        for(Pair<GLStyle, StyleRenderContext> e : ctx.styles) {
            child = geomIter.next();
            if(e.first == null)
                continue;
            e.first.batch(view, batch, child, e.second);
        }
    }

    @Override
    public boolean isBatchable(GLMapView view, GLGeometry g, StyleRenderContext context) {
        final Ctx ctx = (Ctx)context;
        
        GLGeometryCollection geometry = (GLGeometryCollection)g;
        Iterator<GLGeometry> geomIter = geometry.iterator();
        GLGeometry child;
        for(Pair<GLStyle, StyleRenderContext> e : ctx.styles) {
            child = geomIter.next();
            if(e.first == null)
                continue;
            if(!e.first.isBatchable(view, child, e.second))
                return false;
        }
        return true;
    }
    
    private class Ctx extends StyleRenderContext {
        private List<Pair<GLStyle, StyleRenderContext>> styles;
        
        public Ctx(GLMapView view, Style style, GLGeometryCollection collection) {
            this.styles = new LinkedList<Pair<GLStyle, StyleRenderContext>>();
            Iterator<GLGeometry> iter = collection.iterator();
            GLGeometry geom;
            GLStyle s;
            StyleRenderContext ctx;
            while(iter.hasNext()) {
                geom = iter.next();
                s = GLGeometryCollectionStyle.this.spi.create(Pair.<Style, Geometry>create(style, geom.getSubject()));
                ctx = (s != null) ? s.createRenderContext(view, geom) : null;
                this.styles.add(Pair.<GLStyle, StyleRenderContext>create(s, ctx));
            }                
        }
    }
}