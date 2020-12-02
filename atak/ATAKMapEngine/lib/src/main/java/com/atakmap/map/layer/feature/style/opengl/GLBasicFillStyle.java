package com.atakmap.map.layer.feature.style.opengl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import android.graphics.Color;
import android.util.Pair;

import com.atakmap.android.maps.graphics.GLBackground;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.opengl.GLGeometry;
import com.atakmap.map.layer.feature.geometry.opengl.GLPolygon;
import com.atakmap.map.layer.feature.style.BasicFillStyle;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch;
import com.atakmap.opengl.GLTriangulate;

/** @deprecated use the batch feature renderering framework */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public abstract class GLBasicFillStyle extends GLStyle {

    public final static GLStyleSpi SPI = new GLStyleSpi() {

        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public GLStyle create(Pair<Style, Geometry> object) {
            final Style s = object.first;
            final Geometry g = object.second;
            if(s == null || g == null)
                return null;
            if(!(s instanceof BasicFillStyle))
                return null;

            if(g instanceof com.atakmap.map.layer.feature.geometry.Polygon)
                return new Polygon((BasicFillStyle)s);
            else if(g instanceof GeometryCollection)
                return new GLGeometryCollectionStyle(s, this);
            return null;
        }
    };

    protected final float fillColorR;
    protected final float fillColorG;
    protected final float fillColorB;
    protected final float fillColorA;
    
    GLBasicFillStyle(BasicFillStyle style) {
        super(style);
        
        this.fillColorR = Color.red(style.getColor()) / 255f;
        this.fillColorG = Color.green(style.getColor()) / 255f;
        this.fillColorB = Color.blue(style.getColor()) / 255f;
        this.fillColorA = Color.alpha(style.getColor()) / 255f;
    }

    @Override
    public StyleRenderContext createRenderContext(GLMapView view, GLGeometry geom) {
        return null;
    }

    @Override
    public void releaseRenderContext(StyleRenderContext ctx) {}
    
    /**************************************************************************/

    private static class PolygonFillContext extends StyleRenderContext {
        final int fillMode;
        ShortBuffer indices;
        
        public PolygonFillContext(GLPolygon poly) {
            // XXX - holes
            ByteBuffer buf = com.atakmap.lang.Unsafe.allocateDirect((poly.getNumVertices(0)-2)*3*2);
            buf.order(ByteOrder.nativeOrder());
            this.indices = buf.asShortBuffer();
            this.fillMode = GLTriangulate.triangulate(poly.getPoints(0), poly.getNumVertices(0), this.indices);
            if(this.fillMode != GLTriangulate.INDEXED)
                this.indices = null;
        }
    }
    public static final class Polygon extends GLBasicFillStyle {

        private static GLBackground bkgrnd = null;
        
        public Polygon(BasicFillStyle style) {
            super(style);
        }

        @Override
        public StyleRenderContext createRenderContext(GLMapView view, GLGeometry geom) {
            return new PolygonFillContext((GLPolygon)geom);
        }
        
        @Override
        public void draw(GLMapView view, GLGeometry g, StyleRenderContext ctx) {

            if (!(ctx instanceof PolygonFillContext) || ctx == null) 
                return;

            final PolygonFillContext context = (PolygonFillContext)ctx;
            final GLPolygon geometry = (GLPolygon)g;

            switch(context.fillMode) {
                case GLTriangulate.TRIANGLE_FAN :
                case GLTriangulate.INDEXED :
                    this.drawImpl(view, geometry, context);
                    break;
                case GLTriangulate.STENCIL :
                    this.drawStencil(view, geometry, context);
                    break;
                default :
                    throw new IllegalStateException();
            }
        }
        
        private void drawStencil(GLMapView view, GLPolygon geometry, PolygonFillContext context) {
            final FloatBuffer vertices = geometry.getVertices(view, GLGeometry.VERTICES_PROJECTED, 0);
            
            // XXX - holes
            
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glLoadMatrixf(view.sceneModelForwardMatrix, 0);

            GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glVertexPointer(3, GLES20FixedPipeline.GL_FLOAT, 0, vertices);

            GLES20FixedPipeline.glClear(GLES20FixedPipeline.GL_STENCIL_BUFFER_BIT);
            GLES20FixedPipeline.glStencilMask(0xFFFFFFFF);
            GLES20FixedPipeline.glStencilFunc(GLES20FixedPipeline.GL_ALWAYS, 0x1, 0x1);
            GLES20FixedPipeline.glStencilOp(GLES20FixedPipeline.GL_KEEP,
                    GLES20FixedPipeline.GL_KEEP, GLES20FixedPipeline.GL_INVERT);
            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_STENCIL_TEST);

            GLES20FixedPipeline.glColorMask(false, false, false, false);
            GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_FAN, 0,
                    geometry.getNumVertices(0));
            GLES20FixedPipeline.glColorMask(true, true, true, true);

            GLES20FixedPipeline.glPopMatrix();

            GLES20FixedPipeline.glStencilMask(0xFFFFFFFF);
            GLES20FixedPipeline.glStencilFunc(GLES20FixedPipeline.GL_EQUAL, 0x1, 0x1);
            GLES20FixedPipeline.glStencilOp(GLES20FixedPipeline.GL_KEEP,
                    GLES20FixedPipeline.GL_KEEP, GLES20FixedPipeline.GL_KEEP);

            // draw background if fill == true
            if (bkgrnd == null)
                bkgrnd = new GLBackground(view._left, view._bottom, view._right, view._top);

            bkgrnd.draw(GLBackground.BKGRND_TYPE_SOLID,
                    this.fillColorR,
                    this.fillColorG,
                    this.fillColorB,
                    this.fillColorA,
                    false); // blend set to false

            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_STENCIL_TEST);

            GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        }
        
        private void drawImpl(GLMapView view, GLPolygon geometry, PolygonFillContext context) {
            // XXX - holes
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glLoadMatrixf(view.sceneModelForwardMatrix, 0);
            
            GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA, GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);
            
            GLES20FixedPipeline.glColor4f(this.fillColorR,
                                          this.fillColorG,
                                          this.fillColorB,
                                          this.fillColorA);
            final FloatBuffer vertices = geometry.getVertices(view, GLGeometry.VERTICES_PROJECTED, 0);
            
            GLES20FixedPipeline.glVertexPointer(3, GLES20FixedPipeline.GL_FLOAT, 0, vertices);
            
            switch(context.fillMode) {
                case GLTriangulate.TRIANGLE_FAN :
                    GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLE_FAN, 0, geometry.getNumVertices(0));
                    break;
                case GLTriangulate.INDEXED :
                    GLES20FixedPipeline.glDrawElements(GLES20FixedPipeline.GL_TRIANGLES, context.indices.remaining(), GLES20FixedPipeline.GL_UNSIGNED_SHORT, context.indices);
                    break;
                case GLTriangulate.STENCIL :
                default :
                    throw new IllegalStateException();
            }
            
            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            
            GLES20FixedPipeline.glPopMatrix();
        }

        @Override
        public void batch(GLMapView view, GLRenderBatch batch, GLGeometry g, StyleRenderContext ctx) {
            if (!(ctx instanceof PolygonFillContext) || ctx == null) 
                return;

            final PolygonFillContext context = (PolygonFillContext)ctx;
            final GLPolygon geometry = (GLPolygon)g;

            final FloatBuffer vertices = geometry.getVertices(view, GLGeometry.VERTICES_PIXEL, 0);
            
            switch(context.fillMode) {
                case GLTriangulate.TRIANGLE_FAN :
                    batch.addTriangleFan(vertices, this.fillColorR, this.fillColorG, this.fillColorB, this.fillColorA);
                    break;
                case GLTriangulate.INDEXED :
                    batch.addTriangles(vertices, context.indices, this.fillColorR, this.fillColorG, this.fillColorB, this.fillColorA);
                    break;
                case GLTriangulate.STENCIL :
                    batch.end();
                    try {
                        this.drawStencil(view, geometry, context);
                    } finally {
                        batch.begin();
                    }
                    break;
                default :
                    throw new IllegalStateException();
            }
        }

        @Override
        public boolean isBatchable(GLMapView view, GLGeometry geometry, StyleRenderContext ctx) {
            if (!(ctx instanceof PolygonFillContext) || ctx == null) 
                return false;

            final PolygonFillContext context = (PolygonFillContext)ctx;
            return (context.fillMode != GLTriangulate.STENCIL);
        }
    }
}
