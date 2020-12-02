package com.atakmap.map.layer.feature.style.opengl;

import java.nio.FloatBuffer;

import android.graphics.Color;
import android.util.Pair;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.opengl.GLGeometry;
import com.atakmap.map.layer.feature.geometry.opengl.GLLineString;
import com.atakmap.map.layer.feature.geometry.opengl.GLPolygon;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch;

/** @deprecated use the batch feature renderering framework */
@Deprecated
@DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
public abstract class GLBasicStrokeStyle extends GLStyle {

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
            if(!(s instanceof BasicStrokeStyle))
                return null;
            if(g instanceof com.atakmap.map.layer.feature.geometry.LineString)
                return new LineString((BasicStrokeStyle)s);
            else if(g instanceof com.atakmap.map.layer.feature.geometry.Polygon)
                return new Polygon((BasicStrokeStyle)s);
            else if(g instanceof GeometryCollection)
                return new GLGeometryCollectionStyle(s, this);
            return null;
        }
    };

    protected final float strokeWidth;
    protected final float strokeColorR;
    protected final float strokeColorG;
    protected final float strokeColorB;
    protected final float strokeColorA;
    
    GLBasicStrokeStyle(BasicStrokeStyle style) {
        super(style);
        
        this.strokeWidth = style.getStrokeWidth();
        this.strokeColorR = Color.red(style.getColor()) / 255f;
        this.strokeColorG = Color.green(style.getColor()) / 255f;
        this.strokeColorB = Color.blue(style.getColor()) / 255f;
        this.strokeColorA = Color.alpha(style.getColor()) / 255f;
    }

    protected void drawImpl(GLMapView view, FloatBuffer vertices, int size, int count) {
        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA, GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);
        
        GLES20FixedPipeline.glColor4f(this.strokeColorR,
                                      this.strokeColorG,
                                      this.strokeColorB,
                                      this.strokeColorA);
        GLES20FixedPipeline.glLineWidth(this.strokeWidth);

        GLES20FixedPipeline.glVertexPointer(size, GLES20FixedPipeline.GL_FLOAT, 0, vertices);        
        GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINE_STRIP, 0, count);
        
        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
    }
    
    protected void batchImpl(GLMapView view, GLRenderBatch batch, FloatBuffer vertices) {
        batch.addLineStrip(vertices,
                           this.strokeWidth,
                           this.strokeColorR,
                           this.strokeColorG,
                           this.strokeColorB,
                           this.strokeColorA);
    }

    @Override
    public StyleRenderContext createRenderContext(GLMapView view, GLGeometry geom) {
        return null;
    }

    @Override
    public void releaseRenderContext(StyleRenderContext ctx) {}

    public static final class LineString extends GLBasicStrokeStyle {

        public LineString(BasicStrokeStyle style) {
            super(style);
        }

        @Override
        public void draw(GLMapView view, GLGeometry g, StyleRenderContext ctx) {
            final GLLineString geometry = (GLLineString)g;

            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glLoadMatrixf(view.sceneModelForwardMatrix, 0);
            this.drawImpl(view, geometry.getVertices(view, GLGeometry.VERTICES_PROJECTED), 3, geometry.getNumVertices());
            GLES20FixedPipeline.glPopMatrix();
        }

        @Override
        public void batch(GLMapView view, GLRenderBatch batch, GLGeometry geometry, StyleRenderContext ctx) {
            // XXX - really need to be able to specify software transform to
            //       batch
            this.batchImpl(view, batch, ((GLLineString)geometry).getVertices(view, GLGeometry.VERTICES_PIXEL));
        }

        @Override
        public boolean isBatchable(GLMapView view, GLGeometry geometry, StyleRenderContext ctx) {
            return true;
        }
    }
    
    public static final class Polygon extends GLBasicStrokeStyle {

        public Polygon(BasicStrokeStyle style) {
            super(style);
        }

        @Override
        public void draw(GLMapView view, GLGeometry g, StyleRenderContext ctx) {
            final GLPolygon geometry = (GLPolygon)g;

            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glLoadMatrixf(view.sceneModelForwardMatrix, 0);
            final int numRings = geometry.getNumInteriorRings()+1;
            for(int i = 0; i < numRings; i++)
                this.drawImpl(view, geometry.getVertices(view, GLGeometry.VERTICES_PROJECTED, i), 3, geometry.getNumVertices(i));
            GLES20FixedPipeline.glPopMatrix();
        }

        @Override
        public void batch(GLMapView view, GLRenderBatch batch, GLGeometry g, StyleRenderContext ctx) {
            final GLPolygon geometry = (GLPolygon)g;

            final int numRings = geometry.getNumInteriorRings()+1;
            final int numInt = geometry.getNumInteriorRings();
            try {
            for(int i = 0; i < numRings; i++)
                this.batchImpl(view, batch, geometry.getVertices(view, GLGeometry.VERTICES_PIXEL, i));
            } catch(ArrayIndexOutOfBoundsException e) {
                //System.out.println("numRings=" + numRings);
                //System.out.println("numInter=" + numInt);
                //System.out.println("sanity=" + geometry.getNumInteriorRings());
                throw e;
            }
        }

        @Override
        public boolean isBatchable(GLMapView view, GLGeometry geometry, StyleRenderContext ctx) {
            return true;
        }
    }
}
