package com.atakmap.map.layer.feature.style.opengl;

import java.util.LinkedList;
import java.util.List;

import android.util.Pair;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.opengl.GLGeometry;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLRenderBatch;

/** @deprecated use the batch feature renderering framework */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public class GLCompositeStyle extends GLStyle {

    public final static GLStyleSpi SPI = new GLStyleSpi() {

        @Override
        public int getPriority() {
            return 1;
        }

        @Override
        public GLStyle create(Pair<Style, Geometry> object) {
            final Style s = object.first;
            final Geometry g = object.second;
            if(s == null || g == null)
                return null;
            if(!(s instanceof CompositeStyle))
                return null;

            return new GLCompositeStyle((CompositeStyle)s);
        }
    };

    public GLCompositeStyle(CompositeStyle style) {
        super(style);
    }

    @Override
    public void draw(GLMapView view, GLGeometry geometry, StyleRenderContext ctx) {
        if (!(ctx instanceof Ctx) || ctx == null) 
             return;

        Ctx context = (Ctx)ctx;
        for(Pair<GLStyle, StyleRenderContext> e : context.styles)
            e.first.draw(view, geometry, e.second);
    }

    @Override
    public void batch(GLMapView view, GLRenderBatch batch, GLGeometry geometry,
            StyleRenderContext ctx) {

        if (!(ctx instanceof Ctx) || ctx == null) 
             return;
        
        Ctx context = (Ctx)ctx;
        for(Pair<GLStyle, StyleRenderContext> e : context.styles)
            e.first.batch(view, batch, geometry, e.second);        
    }

    @Override
    public boolean isBatchable(GLMapView view, GLGeometry geometry, StyleRenderContext ctx) {
        if (!(ctx instanceof Ctx) || ctx == null) 
             return false;

        Ctx context = (Ctx)ctx;
        for(Pair<GLStyle, StyleRenderContext> e : context.styles)
            if(!e.first.isBatchable(view, geometry, e.second))
                return false;
        return true;
    }

    @Override
    public StyleRenderContext createRenderContext(GLMapView view, GLGeometry geometry) {
        return new Ctx(view, (CompositeStyle)this.style, geometry);
    }

    @Override
    public void releaseRenderContext(StyleRenderContext ctx) {

        if (!(ctx instanceof Ctx) || ctx == null) 
             return;

        Ctx context = (Ctx)ctx;
        for(Pair<GLStyle, StyleRenderContext> e : context.styles)
            if(e.first != null)
                e.first.releaseRenderContext(e.second);
    }

    private class Ctx extends StyleRenderContext {
        private List<Pair<GLStyle, StyleRenderContext>> styles;
        
        public Ctx(GLMapView view, CompositeStyle style, GLGeometry geom) {
            this.styles = new LinkedList<Pair<GLStyle, StyleRenderContext>>();
            GLStyle s;
            for(int i = 0; i < style.getNumStyles(); i++) {
                s = GLStyleFactory.create(Pair.<Style, Geometry>create(style.getStyle(i), geom.getSubject()));
                if(s == null)
                    continue;
                this.styles.add(Pair.<GLStyle, StyleRenderContext>create(s, s.createRenderContext(view, geom)));
            }
        }
    }
}
