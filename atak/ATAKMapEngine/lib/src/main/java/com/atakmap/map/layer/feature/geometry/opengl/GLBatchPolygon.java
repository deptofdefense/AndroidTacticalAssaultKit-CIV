
package com.atakmap.map.layer.feature.geometry.opengl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import android.graphics.Color;
import android.opengl.GLES30;

import com.atakmap.lang.Unsafe;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.feature.style.BasicFillStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch2;
import com.atakmap.opengl.GLTriangulate;
import com.atakmap.opengl.GLWireFrame;
import com.atakmap.opengl.Tessellate;

public class GLBatchPolygon extends GLBatchLineString {

    private final static String TAG = "GLBatchPolygon";

    float fillColorR;
    float fillColorG;
    float fillColorB;
    float fillColorA;
    int fillColor;

    boolean drawStroke;

    DoubleBuffer polyTriangles;
    FloatBuffer polyVertices;

    public GLBatchPolygon(GLMapSurface surface) {
        this(surface.getGLMapView());
    }

    public GLBatchPolygon(MapRenderer surface) {
        super(surface, 2);

        this.fillColorR = 0.0f;
        this.fillColorG = 0.0f;
        this.fillColorB = 0.0f;
        this.fillColorA = 0.0f;
        this.fillColor = 0;
    }

    @Override
    public void setStyle(Style style) {
        super.setStyle(style);

        if(style instanceof CompositeStyle)
            style = CompositeStyle.find((CompositeStyle)style, BasicFillStyle.class);

        final boolean hasFill = (style instanceof BasicFillStyle);
        if (style instanceof BasicFillStyle) {
            BasicFillStyle basicFill = (BasicFillStyle)style;

            this.fillColor = basicFill.getColor();

            this.fillColorR = Color.red(this.fillColor) / 255f;
            this.fillColorG = Color.green(this.fillColor) / 255f;
            this.fillColorB = Color.blue(this.fillColor) / 255f;
            this.fillColorA = Color.alpha(this.fillColor) / 255f;
        }

        // validate the geometry to update polygon tessellation for fill
        this.validateGeometry();

        this.drawStroke = !hasFill;
        final RenderState[] rs = this.renderStates;
        if(!this.drawStroke && rs != null) {
            for(RenderState s : rs) {
                this.drawStroke |= (s.strokeColorA != 0f);
                if(this.drawStroke)
                    break;
            }
        }
    }

    @Override
    protected void setGeometryImpl(final ByteBuffer blob, final int type) {
        final int numRings = blob.getInt();
        if (numRings == 0) {
            this.numRenderPoints = 0;
        } else {
            super.setGeometryImpl(blob, type);
        }
    }

    @Override
    protected boolean validateGeometry() {
        final boolean updated = super.validateGeometry();

        // if the polygon has a fill and has at least 3 points then we need to
        // construct the polygon render geometry
        final boolean needsFill = this.fillColorA > 0.0f && this.numRenderPoints > 3;
        // polygon render geometry is valid if there was no update to the
        // linestring render points and we already have polygon render
        // triangles
        final boolean hasFill = !updated && (this.polyTriangles != null);

        if(needsFill != hasFill) {
            Unsafe.free(this.polyTriangles);
            this.polyTriangles = null;
            try { 
                if (needsFill) {
                    // XXX - we currently always pass in a threshold if tessellated
                    //       rather than also checking 'needsTessellate'. this is
                    //       because the vertex data in the source exterior ring
                    //       could be sufficient tessellated already, but the
                    //       triangles derived from tessellating the polygon may
                    //       exceed the threshold. should look at computing an MBB
                    //       and comparing diagonal length with threshold for a
                    //       short circuit
                    this.polyTriangles = Tessellate.polygon(this.renderPoints,
                                                        24,
                                                        3,
                                                        this.numRenderPoints-1,
                                                        this.tessellated ? GLBatchLineString.threshold : 0d,
                                                        true);
                }
            } catch (Exception e) { 
                // XXX - If release is called while the validateGeomtry is being 
                // called which seems to be the case with both ATAK-11161 and 
                // ATAK-11275, protect against a hard crash but note the exception.
                com.atakmap.coremap.log.Log.e(TAG, "XXX - freed / null renderPoint", e);
                return false;
            }
        }

        return updated;
    }

    @Override
    boolean projectVertices(GLMapView view, int vertices) {
        if(!super.projectVertices(view, vertices))
            return false;

        if(this.vertices == null) {
            Unsafe.free(this.polyVertices);
            this.polyVertices = null;
        }

        if(this.polyTriangles == null)
            return true;

        // XXX - ensure buffer capacity
        if(this.polyVertices == null || this.polyVertices.capacity() < this.polyTriangles.limit()) {
            if(this.polyVertices != null)
                Unsafe.free(this.polyVertices);
            this.polyVertices = Unsafe.allocateDirect(this.polyTriangles.limit(), FloatBuffer.class);
        }

        final double unwrap = view.idlHelper.getUnwrap(this.mbb);
        projectVerticesImpl(view, this.polyTriangles, this.polyTriangles.limit()/3, vertices, altitudeMode, unwrap, this.polyVertices);

        return true;
    }

    public void setGeometry(final Polygon polygon) {
        this.setGeometry(polygon, -1);
    }

    @Override
    protected void setGeometryImpl(Geometry geometry) {
        Polygon polygon = (Polygon)geometry;

        final int numRings = ((polygon.getExteriorRing() != null) ? 1 : 0) + polygon.getInteriorRings().size();
        if (numRings == 0) {
            this.numRenderPoints = 0;
        } else {
            super.setGeometryImpl(polygon.getExteriorRing());
        }
    }

    @Override
    public void draw(GLMapView view, int vertices) {
        this.projectVertices(view, vertices);
        final FloatBuffer v = this.vertices;
        if(v == null)
            return;

        if(this.fillColorA > 0f && this.polyTriangles != null) {
            ByteBuffer bb = Unsafe.allocateDirect(this.polyTriangles.limit()*4);
            bb.order(ByteOrder.nativeOrder());
            FloatBuffer toDraw = bb.asFloatBuffer();
            view.forward(polyTriangles, 3, toDraw, 3);


            GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                    GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

            GLES20FixedPipeline.glVertexPointer(3, GLES20FixedPipeline.GL_FLOAT, 0, toDraw);

            GLES20FixedPipeline.glColor4f(this.fillColorR,
                    this.fillColorG,
                    this.fillColorB,
                    this.fillColorA);

            GLES20FixedPipeline.glDrawArrays(GLES30.GL_TRIANGLES, 0,
                    toDraw.limit()/3);

            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);
            GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

            Unsafe.free(toDraw);
        }

        if (this.drawStroke)
            super.drawImpl(view, v, 3);

    }
    
    @Override
    protected void batchImpl(GLMapView view, GLRenderBatch2 batch, int renderPass, int vertices, int size, FloatBuffer v) {
        if(!MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SURFACE))
            return;

        if(this.fillColorA > 0 && this.polyTriangles != null) {
            batch.batch(-1,
                        GLES20FixedPipeline.GL_TRIANGLES,
                        size,
                        0, this.polyVertices,
                        0, null,
                        this.fillColorR,
                        this.fillColorG,
                        this.fillColorB,
                        this.fillColorA);
        }

        if(this.drawStroke)
            super.batchImpl(view, batch, renderPass, vertices, size, v);
    }

    @Override
    public void release() {
        super.release();

        Unsafe.free(this.polyVertices);
        this.polyVertices = null;
        Unsafe.free(this.polyTriangles);
        this.polyTriangles = null;
    }
}
