
package com.atakmap.android.layers;

import android.util.Pair;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureCursor;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.FeatureLayer;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchGeometry;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchGeometryRenderer;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchPoint;
import com.atakmap.map.layer.feature.opengl.GLFeatureLayer;
import com.atakmap.map.layer.feature.service.FeatureHitTestControl;
import com.atakmap.map.layer.opengl.GLAsynchronousLayer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import gov.tak.platform.commons.opengl.GLES30;
import gov.tak.platform.graphics.Color;

class GLLayerOutlinesLayer extends
        GLAsynchronousLayer<GLLayerOutlinesLayer.Content>
        implements
        FeatureDataStore.OnDataStoreContentChangedListener,
        Layer.OnLayerVisibleChangedListener,
        FeatureHitTestControl {

    final static GLLayerSpi2 SPI2 = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            // OutlinesFeatureDatStore : FeatureLayer : Layer
            return 3;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
            final MapRenderer surface = arg.first;
            final Layer layer = arg.second;
            if (!(layer instanceof FeatureLayer))
                return null;
            final FeatureDataStore dataStore = ((FeatureLayer) layer)
                    .getDataStore();
            if (!(dataStore instanceof OutlinesFeatureDataStore))
                return null;
            return new GLLayerOutlinesLayer(surface, (FeatureLayer) layer);
        }
    };

    private final static Comparator<Feature> FID_COMPARATOR = new Comparator<Feature>() {
        @Override
        public int compare(Feature lhs, Feature rhs) {
            final long fid0 = lhs.getId();
            final long fid1 = rhs.getId();

            return Long.compare(fid0, fid1);
        }
    };

    final static int LINES_VERTEX_SIZE = (12+12+4+2+2); // 32
    final static int LINE_PRIMITIVE_SIZE = 6*LINES_VERTEX_SIZE;

    private final static String LINE_VSH =
            "#version 300 es\n" +
            "precision highp float;\n" +
            "const float c_smoothBuffer = 2.0;\n" +
            "uniform mat4 u_mvp;\n" +
            "uniform mediump vec2 u_viewportSize;\n" +
            "in vec3 a_vertexCoord0;\n" +
            "in vec3 a_vertexCoord1;\n" +
            "in vec2 a_texCoord;\n" +
            "in vec4 a_color;\n" +
            "in float a_normal;\n" +
            "in float a_halfStrokeWidth;\n" +
            "out vec4 v_color;\n" +
            "flat out float f_dist;\n" +
            "out float v_mix;\n" +
            "flat out int f_pattern;\n" +
            "out vec2 v_normal;\n" +
            "flat out float f_halfStrokeWidth;\n" +
            "flat out int f_factor;\n" +
            "void main(void) {\n" +
            "  gl_Position = u_mvp * vec4(a_vertexCoord0.xyz, 1.0);\n" +
            "  vec4 next_gl_Position = u_mvp * vec4(a_vertexCoord1.xyz, 1.0);\n" +
            "  vec4 p0 = (gl_Position / gl_Position.w)*vec4(u_viewportSize, 1.0, 1.0);\n" +
            "  vec4 p1 = (next_gl_Position / next_gl_Position.w)*vec4(u_viewportSize, 1.0, 1.0);\n" +
            "  float dist = distance(p0.xy, p1.xy);\n" +
            "  float dx = p1.x - p0.x;\n" +
            "  float dy = p1.y - p0.y;\n" +
            "  float normalDir = (2.0*a_normal) - 1.0;\n" +
            "  float adjX = normalDir*(dx/dist)*((a_halfStrokeWidth+c_smoothBuffer)/u_viewportSize.y);\n" +
            "  float adjY = normalDir*(dy/dist)*((a_halfStrokeWidth+c_smoothBuffer)/u_viewportSize.x);\n" +
            "  gl_Position.x = gl_Position.x - adjY*gl_Position.w;\n" +
            "  gl_Position.y = gl_Position.y + adjX*gl_Position.w;\n" +
            "  v_color = a_color;\n" +
            "  v_normal = vec2(-normalDir*(dy/dist)*(a_halfStrokeWidth+c_smoothBuffer), normalDir*(dx/dist)*(a_halfStrokeWidth+c_smoothBuffer));\n" +
            "  f_halfStrokeWidth = a_halfStrokeWidth;\n" +
            "}";

    private final static String LINE_FSH =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "in vec4 v_color;\n" +
            "in vec2 v_normal;\n" +
            "flat in float f_halfStrokeWidth;\n" +
            "out vec4 v_FragColor;\n" +
            "void main(void) {\n" +
            "  float antiAlias = smoothstep(-1.0, 0.25, f_halfStrokeWidth-length(v_normal));\n" +
            "  v_FragColor = vec4(v_color.rgb, v_color.a*antiAlias);\n" +
            "}";

    private final FeatureLayer subject;
    private final FeatureDataStore dataStore;

    private boolean visible = false;

    private Map<Long, RendererEntry> features;

    private Collection<GLMapRenderable> renderList;

    private GLBatchGeometryRenderer renderer;

    private Collection<PrimitiveBuffer> lineBuffers = new LinkedList<>();
    private LineShader lineShader;

    private GLLayerOutlinesLayer(MapRenderer surface, FeatureLayer subject) {
        super(surface, subject);

        this.subject = subject;
        this.dataStore = this.subject.getDataStore();

        this.features = new HashMap<>();

    }

    /**************************************************************************/
    // GL Layer


    public synchronized void draw(GLMapView view) {
        if(preparedState != null)
            drawLineBuffers(view, lineBuffers, new PointD(preparedState.drawLng, preparedState.drawLat, 0d));
        super.draw(view);
    }

    void drawLineBuffers(GLMapView view, Collection<PrimitiveBuffer> buf, PointD centroidProj) {
        if (this.lineShader == null) {
            this.lineShader = new LineShader();
            final int vertShader = GLES20FixedPipeline.loadShader(GLES30.GL_VERTEX_SHADER, LINE_VSH);
            final int fragShader = GLES20FixedPipeline.loadShader(GLES30.GL_FRAGMENT_SHADER, LINE_FSH);

            lineShader.handle = GLES20FixedPipeline.createProgram(vertShader, fragShader);
            GLES30.glDeleteShader(vertShader);
            GLES30.glDeleteShader(fragShader);

            GLES30.glUseProgram(lineShader.handle);
            lineShader.u_mvp = GLES30.glGetUniformLocation(lineShader.handle, "u_mvp");
            lineShader.u_viewportSize = GLES30.glGetUniformLocation(lineShader.handle, "u_viewportSize");
            lineShader.a_vertexCoord0 = GLES30.glGetAttribLocation(lineShader.handle, "a_vertexCoord0");
            lineShader.a_vertexCoord1 = GLES30.glGetAttribLocation(lineShader.handle, "a_vertexCoord1");
            lineShader.a_color = GLES30.glGetAttribLocation(lineShader.handle, "a_color");
            lineShader.a_normal = GLES30.glGetAttribLocation(lineShader.handle, "a_normal");
            lineShader.a_halfStrokeWidth = GLES30.glGetAttribLocation(lineShader.handle, "a_halfStrokeWidth");
        }

        GLES30.glUseProgram(lineShader.handle);

        // MVP
        {
            // projection
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
            for(int i = 0; i < 16; i++)
                view.scratch.matrix.set(i%4, i/4, view.scratch.matrixF[i]);
            // model-view
            view.scratch.matrix.concatenate(view.scene.forward);
            view.scratch.matrix.translate(centroidProj.x, centroidProj.y, centroidProj.z);
            for (int i = 0; i < 16; i++) {
                double v;
                v = view.scratch.matrix.get(i % 4, i / 4);
                view.scratch.matrixF[i] = (float)v;
            }
            GLES30.glUniformMatrix4fv(lineShader.u_mvp, 1, false, view.scratch.matrixF, 0);
        }
        // viewport size
        {
            int[] viewport = new int[4];
            GLES30.glGetIntegerv(GLES30.GL_VIEWPORT, viewport, 0);
            GLES30.glUniform2f(lineShader.u_viewportSize, (float)viewport[2] / 2.0f, (float)viewport[3] / 2.0f);
        }

        GLES30.glEnableVertexAttribArray(lineShader.a_vertexCoord0);
        GLES30.glEnableVertexAttribArray(lineShader.a_vertexCoord1);
        GLES30.glEnableVertexAttribArray(lineShader.a_color);
        GLES30.glEnableVertexAttribArray(lineShader.a_normal);
        GLES30.glEnableVertexAttribArray(lineShader.a_halfStrokeWidth);

        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

        for (PrimitiveBuffer it : buf) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, it.handle);
            GLES30.glVertexAttribPointer(lineShader.a_vertexCoord0, 3, GLES30.GL_FLOAT, false, LINES_VERTEX_SIZE, 0);
            GLES30.glVertexAttribPointer(lineShader.a_vertexCoord1, 3, GLES30.GL_FLOAT, false, LINES_VERTEX_SIZE, 12);
            GLES30.glVertexAttribPointer(lineShader.a_color, 4, GLES30.GL_UNSIGNED_BYTE, true, LINES_VERTEX_SIZE, 24);
            GLES30.glVertexAttribPointer(lineShader.a_normal, 1, GLES30.GL_UNSIGNED_SHORT, true, LINES_VERTEX_SIZE, 28);
            GLES30.glVertexAttribPointer(lineShader.a_halfStrokeWidth, 1, GLES30.GL_UNSIGNED_SHORT, false, LINES_VERTEX_SIZE, 30);

            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, it.count);
        }
        GLES30.glDisable(GLES30.GL_BLEND);

        GLES30.glDisableVertexAttribArray(lineShader.a_vertexCoord0);
        GLES30.glDisableVertexAttribArray(lineShader.a_vertexCoord1);
        GLES30.glDisableVertexAttribArray(lineShader.a_color);
        GLES30.glDisableVertexAttribArray(lineShader.a_normal);
        GLES30.glDisableVertexAttribArray(lineShader.a_halfStrokeWidth);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
        GLES30.glUseProgram(GLES30.GL_NONE);
    }
    
    @Override
    public void start() {
        super.start();

        this.subject.addOnLayerVisibleChangedListener(this);
        this.visible = this.subject.isVisible();

        this.renderContext.registerControl(this.subject, this);

        this.dataStore.addOnDataStoreContentChangedListener(this);
    }

    @Override
    public synchronized void stop() {
        super.stop();

        this.subject.removeOnLayerVisibleChangedListener(this);
        this.dataStore.removeOnDataStoreContentChangedListener(this);

        this.renderContext.unregisterControl(this.subject, this);
    }

    /**************************************************************************/
    // GL Asynchronous Map Renderable

    @Override
    protected String getBackgroundThreadName() {
        return "Outlines [" + this.subject.getName() + "] GL worker@"
                + Integer.toString(this.hashCode(), 16);
    }

    @Override
    protected void initImpl(GLMapView view) {
        super.initImpl(view);

        this.renderer = new GLBatchGeometryRenderer(view);
        this.renderList = Collections
                .<GLMapRenderable> singleton(this.renderer);
    }

    @Override
    protected void releaseImpl() {
        if (this.renderer != null) {
            this.renderer.release();
            this.renderer = null;
        }
        for (RendererEntry feature : this.features.values())
            for (GLBatchGeometry g : feature.renderer)
                g.release();
        this.features.clear();

        if(!lineBuffers.isEmpty()) {
            final int[] deleteVbos = new int[lineBuffers.size()];
            int idx = 0;
            for(PrimitiveBuffer pb : lineBuffers)
                deleteVbos[idx++] = pb.handle;
            GLES30.glDeleteBuffers(deleteVbos.length, deleteVbos, 0);
            lineBuffers.clear();
        }

        if(lineShader != null) {
            GLES30.glDeleteShader(lineShader.handle);
            lineShader = null;
        }
        this.renderList = null;

        super.releaseImpl();
    }

    @Override
    protected Collection<? extends GLMapRenderable> getRenderList() {
        if (!this.visible || this.renderList == null)
            return Collections.emptySet();
        return this.renderList;
    }

    @Override
    protected void resetPendingData(Content pendingData) {
        releasePendingData(pendingData);
    }

    @Override
    protected void releasePendingData(Content pendingData) {
        pendingData.labels.clear();

        // delete VBOs
        if(!pendingData.lines.isEmpty()) {
            final int[] deleteVbos = new int[pendingData.lines.size()];
            int idx = 0;
            for(PrimitiveBuffer pb : pendingData.lines)
                deleteVbos[idx++] = pb.handle;
            pendingData.lines.clear();
            renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLES30.glDeleteBuffers(deleteVbos.length, deleteVbos, 0);
                }
            });
        }
    }

    @Override
    protected Content createPendingData() {
        return new Content(renderContext);
    }

    @Override
    protected boolean updateRenderableReleaseLists(
            Content pendingData) {

        // delete VBOs
        if(!lineBuffers.isEmpty()) {
            final int[] deleteVbos = new int[lineBuffers.size()];
            int idx = 0;
            for(PrimitiveBuffer pb : lineBuffers)
                deleteVbos[idx++] = pb.handle;
            renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLES30.glDeleteBuffers(deleteVbos.length, deleteVbos, 0);
                }
            });
        }
        lineBuffers.clear();
        lineBuffers.addAll(pendingData.lines);
        pendingData.lines.clear();

        // XXX - release old labels

        LinkedList<GLBatchGeometry> geoms = new LinkedList<>();
        for (Collection<GLBatchGeometry> e : pendingData.labels.values())
            geoms.addAll(e);
        pendingData.labels.clear();

        // set the render content on the batch. both this method and 'draw' are
        // invoked while holding 'this'
        this.renderer.setBatch(geoms);

        return true;
    }

    @Override
    protected void query(ViewState state, Content retval) {
        if (state.crossesIDL) {
            double east = Math.min(state.westBound, state.eastBound) + 360;
            double west = Math.max(state.westBound, state.eastBound);
            this.queryImpl(state.northBound, west,
                    state.southBound, east,
                    state.drawMapResolution,
                    state.drawLat, state.drawLng,
                    retval);
        } else {
            this.queryImpl(state.northBound,
                    state.westBound,
                    state.southBound,
                    state.eastBound,
                    state.drawMapResolution,
                    state.drawLat, state.drawLng,
                    retval);
        }
    }

    private void queryImpl(double northBound,
            double westBound,
            double southBound,
            double eastBound,
            double drawMapResolution,
            double drawLat, double drawLng,
            Content retval) {

        FeatureCursor result = null;
        try {
            FeatureDataStore.FeatureQueryParameters params = new FeatureDataStore.FeatureQueryParameters();
            params.spatialFilter = new FeatureDataStore.FeatureQueryParameters.RegionSpatialFilter(
                    new GeoPoint(northBound, westBound),
                    new GeoPoint(southBound, eastBound));
            params.maxResolution = drawMapResolution;
            params.visibleOnly = true;

            if (this.checkQueryThreadAbort())
                return;

            //long s = android.os.SystemClock.elapsedRealtime();
            result = this.dataStore.queryFeatures(params);
            while (result.moveToNext()) {
                if (this.checkQueryThreadAbort())
                    break;
                final Feature f = result.get();
                retval.processFeature(f.getId(), f.getGeometry(), f.getStyle(), drawLat, drawLng);
            }

            if(retval.sink != null) {
                retval.unmapBuffer(retval.sink);
                retval.lines.add(retval.sink);
                retval.sink = null;
            }

            //long e = android.os.SystemClock.elapsedRealtime();
            //Log.d(TAG, retval.size() + " results in " + (e-s) + "ms");
        } finally {
            if (result != null)
                result.close();
        }
    }

    /**************************************************************************/
    // Feature Data Store On Data Store Content Changed Listener

    @Override
    public void onDataStoreContentChanged(FeatureDataStore dataStore) {
        if (GLMapSurface.isGLThread()) {
            invalidate();
        } else {
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLLayerOutlinesLayer.this.invalidateNoSync();
                }
            });
        }
    }

    @Override
    public void onLayerVisibleChanged(Layer layer) {
        final boolean visible = layer.isVisible();
        if (GLMapSurface.isGLThread())
            this.visible = visible;
        else
            this.renderContext.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLLayerOutlinesLayer.this.visible = visible;
                }
            });
    }

    /**************************************************************************/
    // HitTestService

    @Override
    public synchronized void hitTest(Collection<Long> fids,
            float screenX, float screenY,
            GeoPoint point, double resolution,
            float radius, int limit) {

        Feature f;
        for (Map.Entry<Long, RendererEntry> entry : this.features.entrySet()) {
            f = this.dataStore.getFeature(entry.getKey());
            if (f != null) {
                if (GLFeatureLayer.hitTest(f.getGeometry(), point, radius
                        * resolution)) {
                    fids.add(f.getId());
                    if (fids.size() == limit)
                        break;
                }
            }
        }
    }

    /**************************************************************************/

    static BasicStrokeStyle getStrokeStyle(Style s) {
        if(s instanceof BasicStrokeStyle) {
            return (BasicStrokeStyle)s;
        } else if(s instanceof CompositeStyle) {
            for(int i = 0; i < ((CompositeStyle) s).getNumStyles(); i++) {
                BasicStrokeStyle bs = getStrokeStyle(((CompositeStyle) s).getStyle(i));
                if(bs != null)
                    return bs;
            }
        }
        return null;
    }

    private static void bls3_vertex(ByteBuffer vbuf, byte stroker, byte strokeg, byte strokeb, byte strokea, float halfWidth, PointD v1, PointD v2, int n) {
        vbuf.putFloat((float)v1.x); // 0
        vbuf.putFloat((float)v1.y);
        vbuf.putFloat((float)v1.z);
        vbuf.putFloat((float)v2.x); // 12
        vbuf.putFloat((float)v2.y);
        vbuf.putFloat((float)v2.z);
        vbuf.put(stroker); // 24
        vbuf.put(strokeg);
        vbuf.put(strokeb);
        vbuf.put(strokea);
        vbuf.putShort((short)n); // 28
        vbuf.putShort((short)halfWidth); // 30
    }

    /**************************************************************************/

    private static class RendererEntry {
        public long version;
        public final ArrayList<GLBatchGeometry> renderer;

        public RendererEntry() {
            this.version = -1L;
            this.renderer = new ArrayList<>(1);
        }
    }

    static class PrimitiveBuffer {
        ByteBuffer clientArray = null;
        int handle = GLES30.GL_NONE;
        int count = 0;
    }

    static class Content {
        Map<Long, Collection<GLBatchGeometry>> labels;
        Collection<PrimitiveBuffer> lines;
        PrimitiveBuffer sink;
        final MapRenderer surface;

        Content(MapRenderer ctx) {
            surface = ctx;
            labels = new HashMap<>();
            lines = new LinkedList<>();
            sink = null;
        }

        void processFeature(long fid, Geometry geom, Style style, double rtcLat, double rtcLng) {
            if(geom instanceof Point) {
                Collection<GLBatchGeometry> renderer = labels.get(fid);
                if(renderer == null)
                    labels.put(fid, renderer=new ArrayList<>(1));
                GLBatchPoint r = new GLBatchPoint(surface);
                r.init((fid << 20L) | (renderer.size() & 0xFFFFF), null);
                r.setGeometry((Point) geom);
                r.setStyle(style);
                renderer.add(r);
            } else if(geom instanceof LineString) {
                processFeature((LineString)geom, style, rtcLat, rtcLng);
            } else if(geom instanceof Polygon) {
                Polygon poly = (Polygon)geom;
                processFeature(poly.getExteriorRing(), style, rtcLat, rtcLng);
                for(LineString ring : poly.getInteriorRings())
                    processFeature(ring, style, rtcLat, rtcLng);
            } else if(geom instanceof GeometryCollection) {
                for(Geometry child : ((GeometryCollection)geom).getGeometries())
                    processFeature(fid, child, style, rtcLat, rtcLng);
            }
        }

        void processFeature(LineString geom, Style style, double rtcLat, double rtcLng) {
            if(sink == null)
                sink = mapBuffer(512*1024);

            byte stroke_r = (byte)0x00;
            byte stroke_g = (byte)0xFF;
            byte stroke_b = (byte)0x00;
            byte stroke_a = (byte)0xFF;

            float strokeWidth = 2f;
            final BasicStrokeStyle stroke = getStrokeStyle(style);
            if(stroke != null) {
                final int strokeColor = stroke.getColor();
                stroke_r = (byte) Color.red(strokeColor);
                stroke_g = (byte) Color.green(strokeColor);
                stroke_b = (byte) Color.blue(strokeColor);
                stroke_a = (byte) Color.alpha(strokeColor);
                strokeWidth = stroke.getStrokeWidth();
            }

            final float halfWidth = Math.min(strokeWidth*GLRenderGlobals.getRelativeScaling()/2.0f, 255.0f);
            final int numPoints = geom.getNumPoints();
            final int numSegments = numPoints-1;
            PointD p0 = new PointD(0d, 0d, 0d);
            PointD p1 = new PointD(0d, 0d, 0d);
            for(int i = 0; i < numSegments; i++) {
                if(sink.clientArray.remaining() < LINE_PRIMITIVE_SIZE) {
                    // unmap the buffer
                    unmapBuffer(sink);
                    // push it back
                    lines.add(sink);

                    sink = mapBuffer(512*1024);
                }

                p0.x = geom.getX(i)-rtcLng;
                p0.y = geom.getY(i)-rtcLat;
                p1.x = geom.getX(i+1)-rtcLng;
                p1.y = geom.getY(i+1)-rtcLat;

                // emit vertices
                bls3_vertex(sink.clientArray, stroke_r, stroke_g, stroke_b, stroke_a, halfWidth, p0, p1, 0xFFFF);
                bls3_vertex(sink.clientArray, stroke_r, stroke_g, stroke_b, stroke_a, halfWidth, p1, p0, 0xFFFF);
                bls3_vertex(sink.clientArray, stroke_r, stroke_g, stroke_b, stroke_a, halfWidth, p0, p1, 0x00);

                bls3_vertex(sink.clientArray, stroke_r, stroke_g, stroke_b, stroke_a, halfWidth, p0, p1, 0xFFFF);
                bls3_vertex(sink.clientArray, stroke_r, stroke_g, stroke_b, stroke_a, halfWidth, p1, p0, 0xFFFF);
                bls3_vertex(sink.clientArray, stroke_r, stroke_g, stroke_b, stroke_a, halfWidth, p1, p0, 0x00);
            }
        }

        PrimitiveBuffer cb_mapBuffer(final int capacity) {
            PrimitiveBuffer pb = new PrimitiveBuffer();
            int[] handle = new int[1];
            GLES30.glGenBuffers(1, handle, 0);
            pb.handle = handle[0];
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, handle[0]);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, capacity, null, GLES30.GL_STATIC_DRAW);
            pb.clientArray = (ByteBuffer)GLES30.glMapBufferRange(GLES30.GL_ARRAY_BUFFER, 0, capacity, android.opengl.GLES30.GL_MAP_WRITE_BIT);
            pb.clientArray.order(ByteOrder.nativeOrder());
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);

            return pb;
        }

        PrimitiveBuffer mapBuffer(final int capacity) {
            final PrimitiveBuffer[] pb = new PrimitiveBuffer[1];
            if(surface.isRenderThread()) {
                pb[0] = cb_mapBuffer(capacity);
            } else {
                surface.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        PrimitiveBuffer buf = cb_mapBuffer(capacity);
                        synchronized(pb) {
                            pb[0] = buf;
                            pb.notify();
                        }
                    }
                });

                while(true) {
                    synchronized(pb) {
                        if(pb[0] == null) {
                            try {
                                pb.wait();
                                continue;
                            } catch (InterruptedException ignored) {
                                break;
                            }
                        }
                        break;
                    }
                }
            }
            return pb[0];
        }

        void cb_unmapBuffer(int handle) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, handle);
            GLES30.glUnmapBuffer(GLES30.GL_ARRAY_BUFFER);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
        }
        void unmapBuffer(PrimitiveBuffer buffer) {
            final int[] handle = new int[] {buffer.handle};
            buffer.count = buffer.clientArray.position()/LINES_VERTEX_SIZE;
            buffer.clientArray = null;
            if(surface.isRenderThread()) {
                cb_unmapBuffer(handle[0]);
            } else {
                surface.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        cb_unmapBuffer(handle[0]);
                        synchronized(handle) {
                            handle[0] = GLES30.GL_NONE;
                            handle.notify();
                        }
                    }
                });

                while(true) {
                    synchronized(handle) {
                        if(handle[0] != GLES30.GL_NONE) {
                            try {
                                handle.wait();
                                continue;
                            } catch (InterruptedException ignored) {
                                break;
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    final static class LineShader {
        public int handle;
        int u_mvp;
        int u_viewportSize;
        int a_vertexCoord0;
        int a_vertexCoord1;
        int a_color;
        int a_normal;
        int a_halfStrokeWidth;
    }

} // GLLayersOutlineLayer
