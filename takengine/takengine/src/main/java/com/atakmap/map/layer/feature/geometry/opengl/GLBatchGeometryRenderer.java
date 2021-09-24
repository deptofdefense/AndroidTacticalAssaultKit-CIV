package com.atakmap.map.layer.feature.geometry.opengl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.graphics.Color;
import android.graphics.PointF;
import android.opengl.GLES30;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.Interop;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.hittest.ClassResultFilter;
import com.atakmap.map.hittest.HitTestControl;
import com.atakmap.map.hittest.HitTestResult;
import com.atakmap.map.layer.feature.Feature.AltitudeMode;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.map.opengl.GLAntiAliasedLine;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch2;
import com.atakmap.opengl.GLTextureAtlas;
import com.atakmap.opengl.Shader;
import com.atakmap.util.Collections2;
import com.atakmap.util.ConfigOptions;

public class GLBatchGeometryRenderer implements GLMapRenderable,
        GLMapRenderable2, HitTestControl {

    final static Interop<MapSceneModel> MapSceneModel_interop = Interop.findInterop(MapSceneModel.class);

    /*
     {
         float x0; // 12
         float y0;
         float z0;
         float x1; // 12
         float y1;
         float z1;
         uint8_t r; // 4
         uint8_t g;
         uint8_t b;
         uint8_t a;
         uint8_t normalDir; // 1
         uint8_t halfWidthPixels; // 1
         uint8_t dir; // 1
         uint8_t patternLen; // 1
         uint32_t pattern; // 4
     }
     */
    private final static int LINES_VERTEX_SIZE = (12+12+4+1+1+4+1+1); // 36


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
        "in float a_dir;\n" +
        "in int a_pattern;\n" +
        "in int a_factor;\n" +
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
        "  v_mix = a_dir;\n" +
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
        "  f_pattern = 0xFFFF;\n" +
        "  f_factor = a_factor;\n" +
        "  f_dist = dist;\n" +
        "  f_halfStrokeWidth = a_halfStrokeWidth;\n" +
        "}";

    private final static String LINE_FSH =
        "#version 300 es\n" +
        "precision mediump float;\n" +
        "uniform mediump vec2 u_viewportSize;\n" +
        "in vec4 v_color;\n" +
        "in float v_mix;\n" +
        "flat in int f_pattern;\n" +
        "flat in int f_factor;\n" +
        "flat in float f_dist;\n" +
        "in vec2 v_normal;\n" +
        "flat in float f_halfStrokeWidth;\n" +
        "out vec4 v_FragColor;\n" +
        "void main(void) {\n" +
        "  float d = (f_dist*v_mix);\n" +
        "  int idist = int(d);\n" +
        "  float b0 = float((f_pattern>>((idist/f_factor)%16))&0x1);\n" +
        "  float b1 = float((f_pattern>>(((idist+1)/f_factor)%16))&0x1);\n" +
        "  float alpha = mix(b0, b1, fract(d));\n" +
        "  float antiAlias = smoothstep(-1.0, 0.25, f_halfStrokeWidth-length(v_normal));\n" +
        "  v_FragColor = vec4(v_color.rgb, v_color.a*antiAlias*alpha);\n" +
        "}";


    private final static int TRIANGLES_VERTEX_SIZE = (12+4); // 16

    private static final String TAG = "GLBatchGeometryRenderer";


    private static Comparator<GLBatchPoint> POINT_BATCH_COMPARATOR = new Comparator<GLBatchPoint>() {
        @Override
        public int compare(GLBatchPoint lhs, GLBatchPoint rhs) {
            long retval = lhs.textureId-rhs.textureId;
            if(retval != 0)
                return (int)retval;
            retval = lhs.color-rhs.color;
            if(retval != 0)
                return (int)retval;
            retval = (lhs.featureId-rhs.featureId);
            if(retval != 0)
                return (retval>0) ? 1 : -1;
            retval = (lhs.subid-rhs.subid);
            return (int)retval;
        }
    };
    
    private static Comparator<GLBatchGeometry> FID_COMPARATOR = new Comparator<GLBatchGeometry>() {
        @Override
        public int compare(GLBatchGeometry lhs, GLBatchGeometry rhs) {
            long retval = (lhs.featureId-rhs.featureId);
            if(retval != 0L)
                return (retval>0L) ? 1 : -1;
            retval = (lhs.subid-rhs.subid);
            return (int)retval;
        }
    };
    
    private final static int PRE_FORWARD_LINES_POINT_RATIO_THRESHOLD = 3;

    private final static int MAX_BUFFERED_2D_POINTS = 20000;
    private final static int MAX_VERTS_PER_DRAW_ARRAYS = 5000;

    private final static int POINT_BATCHING_THRESHOLD = 500;

    private final static Map<MapRenderer, RenderBuffers> renderBuffers = new IdentityHashMap<MapRenderer, RenderBuffers>();

    /*************************************************************************/

    private final ArrayList<GLBatchPolygon> surfacePolys = new ArrayList<>();
    private final ArrayList<GLBatchLineString> spritePolys = new ArrayList<>();
    private final ArrayList<GLBatchLineString> spriteLines = new ArrayList<>();
    private final ArrayList<GLBatchLineString> surfaceLines = new ArrayList<>();
    private final ArrayList<GLBatchLineString> shapes = new ArrayList<>();
    private final ArrayList<GLBatchPoint> batchPoints2 = new ArrayList<>();
    private final ArrayList<GLBatchPoint> labels = new ArrayList<>();
    private final ArrayList<GLBatchPoint> loadingPoints = new ArrayList<>();
    private final ArrayList<GLBatchPoint> points = new ArrayList<>();
    private final Set<GLBatchPoint> lastPoints = Collections2.newIdentityHashSet();

    private SortInfo sortInfo = new SortInfo();

    private final MapRenderer renderCtx;
    private RenderBuffers buffers;
    private FloatBuffer pointsBuffer;
    private long pointsBufferPtr;
    private FloatBuffer pointsVertsTexCoordsBuffer;
    private IntBuffer textureAtlasIndicesBuffer;

    private BatchPipelineState state;

    private GLRenderBatch2 batch;

    private TextureProgram textureProgram2d;
    private TextureProgram textureProgram3d;

    private VectorProgram vectorProgram2d;
    private VectorProgram vectorProgram3d;

    private GLMapView glmv;
    GLAntiAliasedLine _lineRenderer;
    private int batchSpritesSrid = -1;
    private int batchSurfaceSrid = -1;
    private PointD batchSpritesCentroidProj = new PointD(0d, 0d, 0d);
    private PointD batchSurfaceCentroidProj = new PointD(0d, 0d, 0d);
    private GeoPoint batchSpritesCentroid = GeoPoint.createMutable();
    private GeoPoint batchSurfaceCentroid = GeoPoint.createMutable();
    private int rebuildBatchBuffers = -1;
    private int batchTerrainVersion = -1;
    private boolean refreshBatchList;
    private Matrix spritesLocalFrame = Matrix.getIdentity();
    private Matrix surfaceLocalFrame = Matrix.getIdentity();
    private Collection<PrimitiveBuffer> surfaceLineBuffers = new ArrayList<>();
    private Collection<PrimitiveBuffer> spriteLineBuffers0 = new ArrayList<>();
    private Collection<PrimitiveBuffer> spriteLineBuffers1 = new ArrayList<>();
    private Collection<PrimitiveBuffer> spriteTriangleBuffers = new ArrayList<>();
    private LineShader lineShader = null;
    private Shader triangleShader = null;

    public GLBatchGeometryRenderer() {
        this(null);
    }

    public GLBatchGeometryRenderer(MapRenderer renderCtx) {
        this.renderCtx = renderCtx;
        this.glmv = renderCtx instanceof GLMapView ? (GLMapView) renderCtx : null;

        this.state = new BatchPipelineState();
        this.batch = null;

        this.buffers = null;
        _lineRenderer = new GLAntiAliasedLine();
    }

    @Override
    public void hitTest(MapRenderer3 renderer, HitTestQueryParameters params, Collection<HitTestResult> results) {

        // Only feature ID results supported
        if (!params.acceptsResult(Long.class))
            return;

        // Points
        hitTestGeometry(this.labels, renderer, params, results);
        hitTestGeometry(this.batchPoints2, renderer, params, results);

        // Lines and polygons
        hitTestGeometry(this.spritePolys, renderer, params, results);
        hitTestGeometry(this.spriteLines, renderer, params, results);
        hitTestGeometry(this.surfacePolys, renderer, params, results);
        hitTestGeometry(this.surfaceLines, renderer, params, results);
    }

    /**
     * @deprecated Use {@link #hitTest(MapRenderer3, HitTestQueryParameters, Collection)}
     */
    @Deprecated
    @DeprecatedApi(since="4.4", forRemoval = true, removeAt = "4.7")
    public void hitTest3(Collection<Long> fids, GeoPoint geoPoint, double metersRadius,
                         PointF screenPoint, float screenRadius, int limit) {

        if (glmv == null)
            return;

        // Setup query params
        HitTestQueryParameters params = new HitTestQueryParameters(
                glmv.getSurface(), screenPoint.x, screenPoint.y, screenRadius,
                MapRenderer2.DisplayOrigin.UpperLeft);
        params.initGeoPoint(glmv);
        params.limit = limit;
        params.resultFilter = new ClassResultFilter(Long.class);

        List<HitTestResult> results = new ArrayList<>();
        hitTest(glmv, params, results);

        for (HitTestResult result : results) {
            if (result.subject instanceof Long)
                fids.add((Long) result.subject);
        }
    }

    public void setBatch(Collection<GLBatchGeometry> geoms) {
        surfacePolys.clear();
        spritePolys.clear();
        spriteLines.clear();
        surfaceLines.clear();
        shapes.clear();

        loadingPoints.clear();
        batchPoints2.clear();
        labels.clear();
        points.clear();

        this.fillBatchLists(geoms);

        //
        if(sortInfo.order == SortInfo.FID)
            Collections.sort(this.batchPoints2, FID_COMPARATOR);
        else if(sortInfo.order == SortInfo.DEPTH)
            Collections.sort(this.batchPoints2, new DepthComparator(sortInfo.centerLat, sortInfo.measureFromLat, sortInfo.measureFromLng));

        Collections.sort(surfaceLines, FID_COMPARATOR);
        Collections.sort(spriteLines, FID_COMPARATOR);
        Collections.sort(surfacePolys, FID_COMPARATOR);
        Collections.sort(spritePolys, FID_COMPARATOR);

        batchSpritesSrid = -1;
        batchSurfaceSrid = -1;
    }

    private void fillBatchLists(Collection<GLBatchGeometry> geoms) {
        for(GLBatchGeometry g : geoms) {
            switch(g.zOrder) {
                case 0: {
                    GLBatchPoint point = (GLBatchPoint)g;
                    if(point.textureKey != 0L) {
                        batchPoints2.add(point);
                    } else if(point.iconUri != null) {
                        loadingPoints.add(point);
                    } else if(point.name != null){
                        labels.add(point);
                    }
                    points.add(point);
                    break;
                }
                case 2: {
                    if(((GLBatchPolygon)g).drawFill) {
                        final GLBatchPolygon poly = (GLBatchPolygon)g;
                        if(poly.getAltitudeMode() == AltitudeMode.ClampToGround)
                            surfacePolys.add(poly);
                        else
                            spritePolys.add(poly);
                        shapes.add(poly);
                        break;
                    } else if(!((GLBatchPolygon)g).drawStroke) {
                        break;
                    }
                    // if the polygon isn't filled, treat it just like a line
                }
                case 1: {
                    final GLBatchLineString line = (GLBatchLineString)g;
                    if(line.getAltitudeMode() == AltitudeMode.ClampToGround)
                        surfaceLines.add(line);
                    else if(line.hasFill() && line.isExtruded())
                        spritePolys.add(line);
                    else
                        spriteLines.add(line);
                    shapes.add(line);
                    break;
                }
                case 10 :
                case 11 :
                case 12 :
                case 13 :
                    this.fillBatchLists(((GLBatchGeometryCollection)g).points);
                    this.fillBatchLists(((GLBatchGeometryCollection)g).lines);
                    this.fillBatchLists(((GLBatchGeometryCollection)g).polys);
                    this.fillBatchLists(((GLBatchGeometryCollection)g).collections);
                    break;
                default :
                    throw new IllegalArgumentException();
            }
        }
    }

    @Override
    public void draw(GLMapView view) {
        this.draw(view, GLMapView.RENDER_PASS_SPRITES|GLMapView.RENDER_PASS_SURFACE);
    }

    @Override
    public  void draw(GLMapView view, int renderPass) {
        if(this.buffers == null) {
            synchronized(renderBuffers) {
                buffers = renderBuffers.get(renderCtx);
                if(buffers == null) {
                    buffers = new RenderBuffers(MAX_BUFFERED_2D_POINTS);
                    if(renderCtx != null)
                        renderBuffers.put(renderCtx, buffers);
                }
                buffers.references++;
            }

            this.pointsBuffer = buffers.pointsBuffer;
            this.pointsBufferPtr = buffers.pointsBufferPtr;
            this.pointsVertsTexCoordsBuffer = buffers.pointsVertsTexCoordsBuffer;
            this.textureAtlasIndicesBuffer = buffers.textureAtlasIndicesBuffer;
        }

        final boolean surface = MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SURFACE);
        final boolean sprites = MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SPRITES);

        // match batches as dirty
        if (surface && batchSurfaceSrid != view.drawSrid) {
            // reset relative to center
            batchSurfaceCentroid.set(view.currentScene.drawLat, view.currentScene.drawLng);
            view.scene.mapProjection.forward(batchSurfaceCentroid, batchSurfaceCentroidProj);
            batchSurfaceSrid = view.drawSrid;

            surfaceLocalFrame.setToTranslation(batchSurfaceCentroidProj.x, batchSurfaceCentroidProj.y, batchSurfaceCentroidProj.z);

            // mark batches dirty
            rebuildBatchBuffers |= GLMapView.RENDER_PASS_SURFACE;
            batchTerrainVersion = view.getTerrainVersion();
        }
        if (sprites && batchSpritesSrid != view.drawSrid) {
            // reset relative to center
            batchSpritesCentroid.set(view.currentScene.drawLat, view.currentScene.drawLng);
            view.scene.mapProjection.forward(batchSpritesCentroid, batchSpritesCentroidProj);
            batchSpritesSrid = view.drawSrid;

            spritesLocalFrame.setToTranslation(batchSpritesCentroidProj.x, batchSpritesCentroidProj.y, batchSpritesCentroidProj.z);

            // mark batches dirty
            rebuildBatchBuffers |= GLMapView.RENDER_PASS_SPRITES;
            batchTerrainVersion = view.getTerrainVersion();
        }
        if (batchTerrainVersion != view.getTerrainVersion()) {
            // mark batches dirty
            rebuildBatchBuffers |= GLMapView.RENDER_PASS_SPRITES;
            batchTerrainVersion = view.getTerrainVersion();
        }

        // reset the state to the defaults
        this.state.color = 0xFFFFFFFF;
        this.state.lineWidth = 1.0f;
        this.state.texId = 0;

        int[] i = new int[1];
        GLES20FixedPipeline.glGetIntegerv(GLES20FixedPipeline.GL_ACTIVE_TEXTURE, i, 0);
        this.state.textureUnit = i[0];

        if(MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SURFACE))
            this.renderSurface(view);
        if(MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SPRITES))
            this.renderSprites(view);

        rebuildBatchBuffers &= ~renderPass;
    }

    private void renderSurface(GLMapView view) {
        // polygons
        renderLines(view, surfacePolys, GLMapView.RENDER_PASS_SURFACE);

        // lines
        if (!this.surfaceLines.isEmpty()) {
            if (MathUtils.hasBits(rebuildBatchBuffers, GLMapView.RENDER_PASS_SURFACE)) {
                for (PrimitiveBuffer lb : surfaceLineBuffers)
                    GLES30.glDeleteBuffers(1, lb.vbo, 0);
                surfaceLineBuffers.clear();
                this.buildLineBuffers(surfaceLineBuffers, view, surfaceLines, batchSurfaceCentroidProj, true);
            }

            this.drawLineBuffers(view, surfaceLineBuffers, batchSurfaceCentroidProj);
        } else if(!surfaceLineBuffers.isEmpty()) {
            for (PrimitiveBuffer lb : surfaceLineBuffers)
                GLES30.glDeleteBuffers(1, lb.vbo, 0);
            surfaceLineBuffers.clear();
        }
    }

    private void renderLines(GLMapView view, Collection<? extends GLBatchLineString> lines, int renderPass) {
        if (lines.isEmpty())
            return;

        if(this.batch == null)
            this.batch = new GLRenderBatch2();

        GLES20FixedPipeline.glPushMatrix();

        final int vertType = GLGeometry.VERTICES_PIXEL;

        int hints = GLRenderBatch2.HINT_UNTEXTURED;
        if(!(vertType == GLGeometry.VERTICES_PROJECTED && view.scene.mapProjection.is3D()))
            hints |= GLRenderBatch2.HINT_TWO_DIMENSION;

        this.batch.begin(hints);
        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
        this.batch.setMatrix(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);
        this.batch.setMatrix(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);

        for(GLBatchLineString g : lines)
            g.batch(view, this.batch, renderPass, vertType);

        this.batch.end();
        GLES20FixedPipeline.glPopMatrix();
    }


    private void renderSprites(GLMapView view) {

        for (GLBatchLineString shape : shapes) {

            // While we're in sprite rendering mode, check if any of the shapes
            // need to be redrawn in the proper render pass
            if (shape.updateNadirClamp(view))
                refreshBatchList = true;

            // Update screen vertices for hit-testing
            shape.updateScreenVertices(view);
        }

        sortInfo.order = (view.drawTilt > 0d || view.drawSrid == 4978) ? SortInfo.DEPTH : SortInfo.FID;
        sortInfo.centerLat = view.drawLat;
        sortInfo.centerLng = view.drawLng;

        view.scratch.geo.set(view.drawLat, view.drawLng);
        GeoPoint bottomCenter = GeoCalculations.midPoint(view.lowerLeft, view.lowerRight);
        GeoPoint measureFrom = GeoCalculations.pointAtDistance(view.scratch.geo, view.scratch.geo.bearingTo(bottomCenter), view.scratch.geo.distanceTo(bottomCenter) * 1.5d);

        sortInfo.measureFromLat = measureFrom.getLatitude();
        sortInfo.measureFromLng = measureFrom.getLongitude();
        sortInfo.measureFromHae = measureFrom.getAltitude();

        // points

        // if the relative scaling has changed we need to reset the default text
        // and clear the texture atlas
        if(GLBatchPoint.iconAtlasDensity != GLRenderGlobals.getRelativeScaling()) {
            GLBatchPoint.ICON_ATLAS.release();
            GLBatchPoint.ICON_ATLAS = new GLTextureAtlas(1024, (int)Math.ceil(32*GLRenderGlobals.getRelativeScaling()));
            GLBatchPoint.iconLoaders.clear();
            GLBatchPoint.iconAtlasDensity = GLRenderGlobals.getRelativeScaling();
        }

        // check all points with loading icons and move those whose icon has
        // loaded into the batchable list
        final Iterator<GLBatchPoint> iter = this.loadingPoints.iterator();
        while(iter.hasNext()) {
            GLBatchPoint point = iter.next();
            GLBatchPoint.getOrFetchIcon(view.getRenderContext(), point);
            if(point.textureKey != 0L) {
                this.batchPoints2.add(point);
                iter.remove();
                rebuildBatchBuffers |= GLMapView.RENDER_PASS_SPRITES;
            }
        }

        // render all labels
        if(!this.labels.isEmpty()) {
            if(this.batch == null)
                this.batch = new GLRenderBatch2();

            int hints = 0;
            // XXX - when do we want to do 2D for labels???
            /*
            if(!(vertType == GLGeometry.VERTICES_PROJECTED && view.scene.mapProjection.is3D()))
                hints |= GLRenderBatch2.HINT_TWO_DIMENSION;
            */
            this.batch.begin(hints);
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
            this.batch.setMatrix(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);
            this.batch.setMatrix(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);

            for(GLBatchGeometry g : this.labels)
                g.batch(view, this.batch, GLMapView.RENDER_PASS_SPRITES);
            this.batch.end();
        }

        // render points with icons
        final int numBatchPoints = this.batchPoints2.size();
        if(forceGLRB == -1)
            forceGLRB = ConfigOptions.getOption("glbatchgeometryrenderer.force-points-render-batch", 0);
        if(forcePointsDraw == -1)
            forcePointsDraw = ConfigOptions.getOption("glbatchgeometryrenderer.force-points-draw", 0);
        if((forceGLRB == 0 && forcePointsDraw == 0) && (numBatchPoints > POINT_BATCHING_THRESHOLD ||
                (numBatchPoints > 1 &&
                        (!GLMapSurface.SETTING_displayLabels)))) {

            // batch if there are many points on the screen or if we have more
            // than one point and labels are not going to be drawn
            this.batchDrawPoints(view);
        } else if(forcePointsDraw != 0) {
            for(GLBatchGeometry point : this.batchPoints2)
                point.draw(view);
        } else if(numBatchPoints > 0) {
            if(this.batch == null)
                this.batch = new GLRenderBatch2();

            int hints = 0;
            // XXX - when do we want to do 2D for points???
            /*
            if(!(vertType == GLGeometry.VERTICES_PROJECTED && view.scene.mapProjection.is3D()))
                hints |= GLRenderBatch2.HINT_TWO_DIMENSION;
            */
            this.batch.begin(hints);
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
            this.batch.setMatrix(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
            GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);
            this.batch.setMatrix(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);

            for(GLBatchGeometry point : this.batchPoints2)
                point.batch(view, this.batch, GLMapView.RENDER_PASS_SPRITES);
            this.batch.end();
        }

        // lines
        if (!this.spriteLines.isEmpty()) {
            // 3D lines
            if (MathUtils.hasBits(rebuildBatchBuffers, GLMapView.RENDER_PASS_SPRITES)) {
                for (PrimitiveBuffer lb : spriteLineBuffers0)
                    GLES30.glDeleteBuffers(1, lb.vbo, 0);
                spriteLineBuffers0.clear();
                this.buildLineBuffers(spriteLineBuffers0, view, spriteLines, batchSpritesCentroidProj, true);
            }

            this.drawLineBuffers(view, spriteLineBuffers0, batchSpritesCentroidProj);
        } else if(!spriteLineBuffers0.isEmpty()) {
            for (PrimitiveBuffer lb : spriteLineBuffers0)
                GLES30.glDeleteBuffers(1, lb.vbo, 0);
            spriteLineBuffers0.clear();
        }

        // polygons
        if (!this.spritePolys.isEmpty()) {
            // 2D lines
            if (MathUtils.hasBits(rebuildBatchBuffers, GLMapView.RENDER_PASS_SPRITES)) {
                for (PrimitiveBuffer lb : spriteTriangleBuffers)
                    GLES30.glDeleteBuffers(1, lb.vbo, 0);
                spriteTriangleBuffers.clear();
                this.buildTriangleBuffers(spriteTriangleBuffers, view, spritePolys, batchSpritesCentroidProj, true);
                for (PrimitiveBuffer lb : spriteLineBuffers1)
                    GLES30.glDeleteBuffers(1, lb.vbo, 0);
                spriteLineBuffers1.clear();
                this.buildLineBuffers(spriteLineBuffers1, view, spritePolys, batchSpritesCentroidProj, false);
            }

            // draw the polygons, applying polygon offset to support outlines
            GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL);
            GLES30.glPolygonOffset(1.0f, 1.0f);
            this.drawTrianglesBuffers(view, spriteTriangleBuffers, batchSpritesCentroidProj);
            GLES30.glPolygonOffset(0.0f, 0.0f);
            GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL);

            // draw outlines
            this.drawLineBuffers(view, spriteLineBuffers1, batchSpritesCentroidProj);
        } else if(!spriteTriangleBuffers.isEmpty()) {
            for (PrimitiveBuffer lb : spriteTriangleBuffers)
                GLES30.glDeleteBuffers(1, lb.vbo, 0);
            spriteTriangleBuffers.clear();
                for (PrimitiveBuffer lb : spriteLineBuffers1)
                    GLES30.glDeleteBuffers(1, lb.vbo, 0);
                spriteLineBuffers1.clear();
        }
    }

    int forceGLRB = -1;
    int forcePointsDraw = -1;

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SPRITES|GLMapView.RENDER_PASS_SURFACE;
    }

    /**
     * Check if this renderer is requesting a contents update via
     * {@link #setBatch(Collection)}
     * Once checked, the value is reset to false
     * @return True if update requested
     */
    public boolean checkBatchListRefresh() {
        try {
            return refreshBatchList;
        } finally {
            refreshBatchList = false;
        }
    }

    /**
     * Release labels that are no longer being rendered
     * @param releaseAll True to release all labels regardless of whether
     *                   they're still in the batch list or not
     */
    public void invalidateLabels(boolean releaseAll) {

        // Exclude points that are still being rendered
        if (!releaseAll)
            lastPoints.removeAll(points);

        // Release labels on points no longer being rendered
        for(GLBatchPoint point : lastPoints)
            point.releaseLabel();

        // Update points list
        lastPoints.clear();
        if (!releaseAll)
            lastPoints.addAll(points);
    }

    private static void bls3_vertex(ByteBuffer vbuf, GLBatchLineString.RenderState state, float relativeScale, PointD v1, PointD v2, int n, int dir) {
        vbuf.putFloat((float)v1.x);
        vbuf.putFloat((float)v1.y);
        vbuf.putFloat((float)v1.z);
        vbuf.putFloat((float)v2.x);
        vbuf.putFloat((float)v2.y);
        vbuf.putFloat((float)v2.z);
        vbuf.put((byte)((state.strokeColor>>16)&0xFF));
        vbuf.put((byte)((state.strokeColor>>8)&0xFF));
        vbuf.put((byte)(state.strokeColor&0xFF));
        vbuf.put((byte)((state.strokeColor>>24)&0xFF));
        vbuf.put((byte)n);
        vbuf.put((byte)Math.min(state.strokeWidth/relativeScale/2.0f, 255.0f));
        vbuf.put((byte)dir);
        vbuf.put((byte)MathUtils.clamp(state.factor, 1, 255));
        vbuf.putInt(state.pattern&0xFFFF);
    }

    void buildLineBuffers(Collection<PrimitiveBuffer> linesBuf, GLMapView view, Collection<? extends GLBatchLineString> lines, PointD centroidProj, boolean requiresProject) {
        final int transferSize = 512*1024; // 512kb

        // streaming vertex buffer
        ByteBuffer vbuf;

        // NOTE: the primitives are actually triangles as we are emulating line
        // drawing with quads to apply antialiasing and pattern effects

        PrimitiveBuffer b = new PrimitiveBuffer(GLES30.GL_TRIANGLES);
        GLES30.glGenBuffers(1, b.vbo, 0);
        if (b.vbo[0] == GLES30.GL_NONE) {
            // out of memory
            return;
        } else {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, b.vbo[0]);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, transferSize, null, GLES30.GL_STATIC_DRAW);
            vbuf = (ByteBuffer)GLES30.glMapBufferRange(GLES30.GL_ARRAY_BUFFER, 0, transferSize, GLES30.GL_MAP_WRITE_BIT|GLES30.GL_MAP_INVALIDATE_BUFFER_BIT);
        }
        if(vbuf == null)
            return;
        vbuf.mark();
        vbuf.order(ByteOrder.nativeOrder());

        PointD p0 = new PointD(0d, 0d, 0d);
        PointD p1 = new PointD(0d, 0d, 0d);

        final float relativeScale = view.currentPass.relativeScaleHint / GLRenderGlobals.getRelativeScaling();

        GLBatchLineString.RenderState[] defaultrs = { GLBatchLineString.DEFAULT_RS };
        for (GLBatchLineString line : lines) {
            if (line.numPoints < 2)
                continue;

            // project the line vertices, applying the batch centroid
            if(requiresProject) {
                line.projectedVerticesSrid = -1;
                line.centroidProj.x = centroidProj.x;
                line.centroidProj.y = centroidProj.y;
                line.centroidProj.z = centroidProj.z;
                line.projectVertices(view, GLGeometry.VERTICES_BATCH);
                line.projectedVerticesSrid = -1;
            }

            GLBatchLineString.RenderState[] rs = line.renderStates;
            if(rs == null)
                rs = defaultrs;
            for (int i = 0; i < rs.length; i++) {
                // skip if invisible
                if(rs[i].outlineColorA == 0f && rs[i].strokeColorA == 0f)
                    continue;

                final int numSegs;
                final int step;
                if(line.renderPointsDrawMode == GLES30.GL_LINES) {
                    numSegs = line.numRenderPoints/2;
                    step = 2;
                } else {
                    numSegs = line.numRenderPoints-1;
                    step = 1;
                }
                for (int j = 0; j < numSegs; j++) {
                    if (vbuf.remaining() < (6 * LINES_VERTEX_SIZE)) {
                        b.count = vbuf.position() / LINES_VERTEX_SIZE;
                        GLES30.glUnmapBuffer(GLES30.GL_ARRAY_BUFFER);
                        vbuf = null;
                        linesBuf.add(b);

                        b = new PrimitiveBuffer(GLES30.GL_TRIANGLES);
                        GLES30.glGenBuffers(1, b.vbo, 0);
                        if (b.vbo[0] == GLES30.GL_NONE) {
                            // out of memory
                            return;
                        } else {
                            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, b.vbo[0]);
                            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, transferSize, null, GLES30.GL_STATIC_DRAW);
                            vbuf = (ByteBuffer)GLES30.glMapBufferRange(GLES30.GL_ARRAY_BUFFER, 0, transferSize, GLES30.GL_MAP_WRITE_BIT|GLES30.GL_MAP_INVALIDATE_BUFFER_BIT);
                        }
                        if(vbuf == null)
                            return;
                        vbuf.mark();
                        vbuf.order(ByteOrder.nativeOrder());
                    }

                    final int aidx = (j*step);
                    final int bidx = aidx+1;
                    p0.x = line.vertices.get(aidx * 3);
                    p0.y = line.vertices.get(aidx * 3+1);
                    p0.z = line.vertices.get(aidx * 3+2);
                    p1.x = line.vertices.get(bidx * 3);
                    p1.y = line.vertices.get(bidx * 3+1);
                    p1.z = line.vertices.get(bidx * 3+2);

                    bls3_vertex(vbuf, rs[i], relativeScale, p0, p1, 0xFF, 0xFF);
                    bls3_vertex(vbuf, rs[i], relativeScale, p1, p0, 0xFF, 0x00);
                    bls3_vertex(vbuf, rs[i], relativeScale, p0, p1, 0x00, 0xFF);

                    bls3_vertex(vbuf, rs[i], relativeScale, p0, p1, 0xFF, 0xFF);
                    bls3_vertex(vbuf, rs[i], relativeScale, p1, p0, 0xFF, 0x00);
                    bls3_vertex(vbuf, rs[i], relativeScale, p1, p0, 0x00, 0x00);
                }
            }
        }

        // flush the remaining record
        if (vbuf.position() > 0) {
            b.count = vbuf.position() / LINES_VERTEX_SIZE;
            GLES30.glUnmapBuffer(GLES30.GL_ARRAY_BUFFER);
            vbuf = null;
            linesBuf.add(b);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
        } else {
            GLES30.glUnmapBuffer(GLES30.GL_ARRAY_BUFFER);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
            GLES30.glDeleteBuffers(1, b.vbo, 0);
        }

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
            lineShader.a_dir = GLES30.glGetAttribLocation(lineShader.handle, "a_dir");
            lineShader.a_pattern = GLES30.glGetAttribLocation(lineShader.handle, "a_pattern");
            lineShader.a_factor = GLES30.glGetAttribLocation(lineShader.handle, "a_factor");
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
        GLES30.glEnableVertexAttribArray(lineShader.a_dir);
        GLES30.glEnableVertexAttribArray(lineShader.a_pattern);
        GLES30.glEnableVertexAttribArray(lineShader.a_factor);

        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

        for (PrimitiveBuffer it : buf) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, it.vbo[0]);
            GLES30.glVertexAttribPointer(lineShader.a_vertexCoord0, 3, GLES30.GL_FLOAT, false, LINES_VERTEX_SIZE, 0);
            GLES30.glVertexAttribPointer(lineShader.a_vertexCoord1, 3, GLES30.GL_FLOAT, false, LINES_VERTEX_SIZE, 12);
            GLES30.glVertexAttribPointer(lineShader.a_color, 4, GLES30.GL_UNSIGNED_BYTE, true, LINES_VERTEX_SIZE, 24);
            GLES30.glVertexAttribPointer(lineShader.a_normal, 1, GLES30.GL_UNSIGNED_BYTE, true, LINES_VERTEX_SIZE, 28);
            GLES30.glVertexAttribPointer(lineShader.a_halfStrokeWidth, 1, GLES30.GL_UNSIGNED_BYTE, false, LINES_VERTEX_SIZE, 29);
            GLES30.glVertexAttribPointer(lineShader.a_dir, 1, GLES30.GL_UNSIGNED_BYTE, true, LINES_VERTEX_SIZE, 30);
            // pattern
            GLES30.glVertexAttribPointer(lineShader.a_factor, 1, GLES30.GL_UNSIGNED_BYTE, false, LINES_VERTEX_SIZE, 31);
            GLES30.glVertexAttribPointer(lineShader.a_pattern, 1, GLES30.GL_INT, false, LINES_VERTEX_SIZE, 32);
            GLES30.glDrawArrays(it.mode, 0, it.count);
        }
        GLES30.glDisable(GLES30.GL_BLEND);

        GLES30.glDisableVertexAttribArray(lineShader.a_vertexCoord0);
        GLES30.glDisableVertexAttribArray(lineShader.a_vertexCoord1);
        GLES30.glDisableVertexAttribArray(lineShader.a_color);
        GLES30.glDisableVertexAttribArray(lineShader.a_normal);
        GLES30.glDisableVertexAttribArray(lineShader.a_halfStrokeWidth);
        GLES30.glDisableVertexAttribArray(lineShader.a_dir);
        GLES30.glDisableVertexAttribArray(lineShader.a_pattern);
        GLES30.glDisableVertexAttribArray(lineShader.a_factor);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
        GLES30.glUseProgram(GLES30.GL_NONE);
    }

    void buildTriangleBuffers(Collection<PrimitiveBuffer> polysBuf, GLMapView view, Collection<? extends GLBatchLineString> polys, PointD centroidProj, boolean requiresProject) {
        final int transferSize = 512*1024; // 512kb

        // streaming vertex buffer
        ByteBuffer vbuf;

        // NOTE: the primitives are actually triangles as we are emulating line
        // drawing with quads to apply antialiasing and pattern effects

        PrimitiveBuffer b = new PrimitiveBuffer(GLES30.GL_TRIANGLES);
        GLES30.glGenBuffers(1, b.vbo, 0);
        if (b.vbo[0] == GLES30.GL_NONE) {
            // out of memory
            return;
        } else {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, b.vbo[0]);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, transferSize, null, GLES30.GL_STATIC_DRAW);
            vbuf = (ByteBuffer)GLES30.glMapBufferRange(GLES30.GL_ARRAY_BUFFER, 0, transferSize, GLES30.GL_MAP_WRITE_BIT|GLES30.GL_MAP_INVALIDATE_BUFFER_BIT);
        }
        if(vbuf == null)
            return;
        vbuf.mark();
        vbuf.order(ByteOrder.nativeOrder());

        if(false)
        Log.i(TAG, "buildTrianglesBuffers " + polys + " polys");

        GLBatchLineString.RenderState[] defaultrs = { GLBatchLineString.DEFAULT_RS };
        for (GLBatchLineString poly : polys) {
            if (poly.numPoints < 2)
                continue;

            if(!poly.hasFill())
                continue;

            // project the line vertices, applying the batch centroid
            if(requiresProject) {
                poly.projectedVerticesSrid = -1;
                poly.centroidProj.x = centroidProj.x;
                poly.centroidProj.y = centroidProj.y;
                poly.centroidProj.z = centroidProj.z;
                poly.projectVertices(view, GLGeometry.VERTICES_BATCH);
                poly.projectedVerticesSrid = -1;
            }

            if(poly.polyVertices == null)
                continue;

            GLBatchLineString.RenderState[] rs = poly.renderStates;
            if(rs == null)
                rs = defaultrs;
            for (int i = 0; i < rs.length; i++) {
                if(false)
                Log.i(TAG, "Push RS " +
                        "stroke={" + Integer.toString(rs[i].strokeColor, 16) + "}" +
                        "outline={" + Integer.toString(rs[i].outlineColor, 16) + "}" +
                        "fill={" + Integer.toString(rs[i].fillColor, 16) + "}");
                // skip if invisible
                if(rs[i].fillColorA == 0f)
                    continue;


                for (int j = 0; j < poly.polyVertices.limit()/3; j++) {
                    if (vbuf.remaining() < (3 * TRIANGLES_VERTEX_SIZE)) {
                        b.count = vbuf.position() / TRIANGLES_VERTEX_SIZE;
                        GLES30.glUnmapBuffer(GLES30.GL_ARRAY_BUFFER);
                        vbuf = null;
                        polysBuf.add(b);

                        b = new PrimitiveBuffer(GLES30.GL_TRIANGLES);
                        GLES30.glGenBuffers(1, b.vbo, 0);
                        if (b.vbo[0] == GLES30.GL_NONE) {
                            // out of memory
                            return;
                        } else {
                            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, b.vbo[0]);
                            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, transferSize, null, GLES30.GL_STATIC_DRAW);
                            vbuf = (ByteBuffer)GLES30.glMapBufferRange(GLES30.GL_ARRAY_BUFFER, 0, transferSize, GLES30.GL_MAP_WRITE_BIT|GLES30.GL_MAP_INVALIDATE_BUFFER_BIT);
                        }
                        if(vbuf == null)
                            return;
                        vbuf.mark();
                        vbuf.order(ByteOrder.nativeOrder());
                    }

                    vbuf.putFloat(poly.polyVertices.get(j*3));
                    vbuf.putFloat(poly.polyVertices.get(j*3+1));
                    vbuf.putFloat(poly.polyVertices.get(j*3+2));
                    vbuf.put((byte)((rs[i].fillColor>>16)&0xFF));
                    vbuf.put((byte)((rs[i].fillColor>>8)&0xFF));
                    vbuf.put((byte)(rs[i].fillColor&0xFF));
                    vbuf.put((byte)((rs[i].fillColor>>24)&0xFF));
                }
            }
        }

        // flush the remaining record
        if (vbuf.position() > 0) {
            b.count = vbuf.position() / TRIANGLES_VERTEX_SIZE;
            GLES30.glUnmapBuffer(GLES30.GL_ARRAY_BUFFER);
            vbuf = null;
            polysBuf.add(b);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
        } else {
            GLES30.glUnmapBuffer(GLES30.GL_ARRAY_BUFFER);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
            GLES30.glDeleteBuffers(1, b.vbo, 0);
        }
    }

    void drawTrianglesBuffers(GLMapView view, Collection<PrimitiveBuffer> buf, PointD centroidProj) {
        if (triangleShader == null)
            triangleShader = Shader.get(view.getRenderContext());

        GLES30.glUseProgram(triangleShader.handle);

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
            GLES30.glUniformMatrix4fv(triangleShader.uMVP, 1, false, view.scratch.matrixF, 0);
        }

        GLES30.glUniform4f(triangleShader.uColor, 1f, 1f, 1f, 1f);
        GLES30.glUniform1ui(triangleShader.uTexture, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, GLRenderGlobals.get(view).getWhitePixel().getTexId());

        GLES30.glEnableVertexAttribArray(triangleShader.aVertexCoords);
        GLES30.glEnableVertexAttribArray(triangleShader.aColors);

        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

        for (PrimitiveBuffer it : buf) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, it.vbo[0]);
            GLES30.glVertexAttribPointer(triangleShader.aVertexCoords, 3, GLES30.GL_FLOAT, false, TRIANGLES_VERTEX_SIZE, 0);
            GLES30.glVertexAttribPointer(triangleShader.aColors, 4, GLES30.GL_UNSIGNED_BYTE, true, TRIANGLES_VERTEX_SIZE, 12);
            GLES30.glDrawArrays(it.mode, 0, it.count);
        }
        GLES30.glDisable(GLES30.GL_BLEND);

        GLES30.glDisableVertexAttribArray(triangleShader.aVertexCoords);
        GLES30.glDisableVertexAttribArray(triangleShader.aColors);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
        GLES30.glUseProgram(GLES30.GL_NONE);
    }

    private void batchDrawPoints(GLMapView view) {
        this.state.color = 0xFFFFFFFF;
        this.state.texId = 0;

        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glLoadIdentity();

        GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_BLEND);
        GLES20FixedPipeline.glBlendFunc(GLES20FixedPipeline.GL_SRC_ALPHA,
                GLES20FixedPipeline.GL_ONE_MINUS_SRC_ALPHA);

        final TextureProgram textureProgram;

        if(view.drawTilt > 0d || view.drawSrid == 4978) {
            if(this.textureProgram3d == null)
                this.textureProgram3d = new TextureProgram(3);
            else
                GLES20FixedPipeline.glUseProgram(this.textureProgram3d.programHandle);
            textureProgram = this.textureProgram3d;
        } else {
            if(this.textureProgram2d == null)
                this.textureProgram2d = new TextureProgram(2);
            else
                GLES20FixedPipeline.glUseProgram(this.textureProgram2d.programHandle);
            textureProgram = this.textureProgram2d;
        }


        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, view.scratch.matrixF, 0);
        GLES20FixedPipeline.glUniformMatrix4fv(textureProgram.uProjectionHandle, 1, false,
                view.scratch.matrixF, 0);

        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_MODELVIEW, view.scratch.matrixF, 0);
        GLES20FixedPipeline.glUniformMatrix4fv(textureProgram.uModelViewHandle, 1, false, view.scratch.matrixF,
                0);

        // work with texture0
        GLES20FixedPipeline.glActiveTexture(this.state.textureUnit);
        GLES20FixedPipeline.glUniform1i(textureProgram.uTextureHandle, this.state.textureUnit
                - GLES20FixedPipeline.GL_TEXTURE0);

        // sync the current color with the shader
        GLES20FixedPipeline.glUniform4f(textureProgram.uColorHandle,
                                        Color.red(this.state.color) / 255f,
                                        Color.green(this.state.color) / 255f,
                                        Color.blue(this.state.color) / 255f,
                                        Color.alpha(this.state.color) / 255f);

        this.pointsBuffer.clear();
        this.textureAtlasIndicesBuffer.clear();

        int pointsBufferPos = 0;

        GLBatchPoint point;
        for(GLBatchGeometry geom : this.batchPoints2) {
            point = (GLBatchPoint)geom;

            if(point.iconUri == null)
                continue;

            if(point.textureKey == 0L) {
                GLBatchPoint.getOrFetchIcon(view.getRenderContext(), point);
                continue;
            }

            if(this.state.texId != point.textureId) {
                this.pointsBuffer.position(pointsBufferPos/4);
                this.renderPointsBuffers(view, textureProgram);
                pointsBufferPos = 0;
                this.pointsBuffer.clear();
                this.textureAtlasIndicesBuffer.clear();

                this.state.texId = point.textureId;
                GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D,
                        this.state.texId);
            }
            if(this.state.texId == 0)
                continue;

            if(point.color != this.state.color) {
                this.pointsBuffer.position(pointsBufferPos/4);
                this.renderPointsBuffers(view, textureProgram);
                pointsBufferPos = 0;
                this.pointsBuffer.clear();
                this.textureAtlasIndicesBuffer.clear();

                GLES20FixedPipeline.glUniform4f(textureProgram.uColorHandle,
                        Color.red(point.color) / 255f,
                        Color.green(point.color) / 255f,
                        Color.blue(point.color) / 255f,
                        Color.alpha(point.color) / 255f);

                this.state.color = point.color;
            }

            if(textureProgram.vertSize == 2) {
                Unsafe.setFloats(this.pointsBufferPtr+pointsBufferPos,
                                 (float)view.idlHelper.wrapLongitude(point.longitude),
                                 (float)point.latitude,
                                 point.iconScale);
                pointsBufferPos += 12;
            } else {
                double alt = 0d;
                if(view.drawTilt > 0d) {
                    final double el = point.computeAltitude(view);
                    // note: always NaN if source alt is NaN
                    double adjustedAlt = (el + view.elevationOffset) * view.elevationScaleFactor;

                    // move up half icon height
                    adjustedAlt += view.drawMapResolution * (point.iconHeight / 2d);

                    // move up ~5 pixels from surface
                    adjustedAlt += view.drawMapResolution * 10d * GLRenderGlobals.getRelativeScaling();

                    alt = adjustedAlt;
                }

                Unsafe.setFloats(this.pointsBufferPtr+pointsBufferPos,
                                 (float)view.idlHelper.wrapLongitude(point.longitude),
                                 (float)point.latitude,
                                 (float)alt,
                                 point.iconScale);
                pointsBufferPos += 16;
            }
            this.textureAtlasIndicesBuffer.put(point.textureIndex);

            if(((pointsBufferPos/4)+(textureProgram.vertSize+1)) >= this.pointsBuffer.limit()) {
                this.pointsBuffer.position(pointsBufferPos/4);
                this.renderPointsBuffers(view, textureProgram);
                this.textureAtlasIndicesBuffer.clear();
                this.pointsBuffer.clear();
                pointsBufferPos = 0;
            }
        }

        if(pointsBufferPos > 0) {
            this.pointsBuffer.position(pointsBufferPos/4);
            this.renderPointsBuffers(view, textureProgram);
            this.textureAtlasIndicesBuffer.clear();
            this.pointsBuffer.clear();
            pointsBufferPos = 0;
        }

        GLES20FixedPipeline.glPopMatrix();

        GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_BLEND);

        if(this.state.texId != 0)
            GLES20FixedPipeline.glBindTexture(GLES20FixedPipeline.GL_TEXTURE_2D, 0);

        // sync the current color with the pipeline
        GLES20FixedPipeline.glColor4f(Color.red(this.state.color) / 255f,
                                      Color.green(this.state.color) / 255f,
                                      Color.blue(this.state.color) / 255f,
                                      Color.alpha(this.state.color) / 255f);
    }

    private void renderPointsBuffers(GLMapView view, TextureProgram textureProgram) {
        this.textureAtlasIndicesBuffer.flip();

        if(this.textureAtlasIndicesBuffer.remaining() < 1)
            return;

        this.pointsBuffer.flip();

        this.pointsVertsTexCoordsBuffer.clear();

        fillVertexArrays(MapSceneModel_interop.getPointer(view.currentPass.scene),
                         textureProgram.vertSize,
                         this.pointsBuffer,
                         this.textureAtlasIndicesBuffer,
                         GLBatchPoint.ICON_ATLAS.getImageWidth(0), // fixed size
                         GLBatchPoint.ICON_ATLAS.getTextureSize(),
                         this.pointsVertsTexCoordsBuffer,
                         this.textureAtlasIndicesBuffer.remaining());

        GLES20FixedPipeline.glVertexAttribPointer(textureProgram.aVertexCoordsHandle, textureProgram.vertSize,
                GLES20FixedPipeline.GL_FLOAT,
                false, (4*textureProgram.vertSize)+8, this.pointsVertsTexCoordsBuffer.position(0));
        GLES20FixedPipeline.glEnableVertexAttribArray(textureProgram.aVertexCoordsHandle);

        GLES20FixedPipeline.glVertexAttribPointer(textureProgram.aTextureCoordsHandle, 2,
                GLES20FixedPipeline.GL_FLOAT,
                false, (4*textureProgram.vertSize)+8, this.pointsVertsTexCoordsBuffer.position(textureProgram.vertSize));
        GLES20FixedPipeline.glEnableVertexAttribArray(textureProgram.aTextureCoordsHandle);

        int remaining = this.textureAtlasIndicesBuffer.remaining();
        final int iconsPerPass = MAX_VERTS_PER_DRAW_ARRAYS / 6;
        int off = 0;
        do {
            // XXX - note that we could use triangle strips here, but we would
            //       need a degenerate triangle for every icon except the last
            //       one, meaning that all icons except the last would require
            //       6 vertices
            GLES30.glDrawArrays(GLES20FixedPipeline.GL_TRIANGLES,
                                off*6,
                                Math.min(remaining, iconsPerPass)*6);

            remaining -= iconsPerPass;
            off += iconsPerPass;
        } while(remaining > 0);

        GLES20FixedPipeline.glDisableVertexAttribArray(textureProgram.aVertexCoordsHandle);
        GLES20FixedPipeline.glDisableVertexAttribArray(textureProgram.aTextureCoordsHandle);

        this.pointsBuffer.position(this.pointsBuffer.limit());
        this.textureAtlasIndicesBuffer.position(this.textureAtlasIndicesBuffer.limit());
    }

    @Override
    public void release() {
        this.surfaceLines.clear();
        this.spriteLines.clear();
        this.surfacePolys.clear();
        this.spritePolys.clear();
        this.shapes.clear();

        this.batchPoints2.clear();
        this.loadingPoints.clear();
        this.labels.clear();
        
        if(batch != null) {
            this.batch.release();
            this.batch.dispose();
            this.batch = null;
        }

        if(this.buffers != null) {
            this.buffers.references--;
            if(this.buffers.references < 1) {
                synchronized(renderBuffers) {
                    renderBuffers.remove(this.renderCtx);
                }
            }

            this.pointsBuffer = null;
            this.pointsVertsTexCoordsBuffer = null;
            this.textureAtlasIndicesBuffer = null;
            this.buffers = null;
        }

        for (PrimitiveBuffer it : surfaceLineBuffers)
            GLES30.glDeleteBuffers(1, it.vbo, 0);
        surfaceLineBuffers.clear();
        for (PrimitiveBuffer it : spriteLineBuffers0)
            GLES30.glDeleteBuffers(1, it.vbo, 0);
        spriteLineBuffers0.clear();
        for (PrimitiveBuffer it : spriteLineBuffers1)
            GLES30.glDeleteBuffers(1, it.vbo, 0);
        spriteLineBuffers1.clear();
        for (PrimitiveBuffer it : spriteTriangleBuffers)
            GLES30.glDeleteBuffers(1, it.vbo, 0);
        spriteTriangleBuffers.clear();
    }
    
    /**************************************************************************/

    /**
     * Perform a hit test using a list of batch geometry
     * @param list List of batch geometry (points or line)
     * @param renderer Map renderer
     * @param params Hit test query params
     * @param results Total list of hit results
     */
    private static void hitTestGeometry(
            List<? extends GLBatchGeometry> list,
            MapRenderer3 renderer,
            HitTestQueryParameters params,
            Collection<HitTestResult> results) {

        for (int i = list.size()-1; i >= 0; i--) {
            if (params.hitLimit(results))
                break;

            GLBatchGeometry item = list.get(i);

            HitTestResult result = item.hitTest(renderer, params);
            if (result != null)
                results.add(result);
        }
    }

    /**************************************************************************/
    
    private static native void fillVertexArrays(long scenePtr,
                                                int vertSize,
                                                FloatBuffer translations,
                                                IntBuffer textureKeys,
                                                int iconSize,
                                                int textureSize,
                                                FloatBuffer vertsTexCoords,
                                                int count);

    /**************************************************************************/
    
    private static class BatchPipelineState {
        public int color;
        public float lineWidth;
        public int texId;
        public int textureUnit;
    }
    
    private static class VectorProgram {
        final int programHandle;
        final int uProjectionHandle;
        final int uModelViewHandle;
        final int aVertexCoordsHandle;
        final int uColorHandle;
        
        final int vertSize;
        
        VectorProgram(int vertSize) {
            this.vertSize = vertSize;

            String vertShaderSrc;
            switch(this.vertSize) {
                case 2 :
                    vertShaderSrc = GLES20FixedPipeline.VECTOR_2D_VERT_SHADER_SRC;
                    break;
                case 3 :
                    vertShaderSrc = GLES20FixedPipeline.VECTOR_3D_VERT_SHADER_SRC;
                    break;
                default :
                    throw new IllegalArgumentException();
            }
            final int vertShader = GLES20FixedPipeline.loadShader(
                    GLES20FixedPipeline.GL_VERTEX_SHADER,
                    vertShaderSrc);
            final int fragShader = GLES20FixedPipeline.loadShader(
                    GLES20FixedPipeline.GL_FRAGMENT_SHADER,
                    GLES20FixedPipeline.GENERIC_VECTOR_FRAG_SHADER_SRC);
            
            this.programHandle = GLES20FixedPipeline.createProgram(vertShader, fragShader);
            GLES20FixedPipeline.glUseProgram(this.programHandle);

            this.uProjectionHandle = GLES20FixedPipeline.glGetUniformLocation(
                    this.programHandle, "uProjection");
            
            this.uModelViewHandle = GLES20FixedPipeline.glGetUniformLocation(
                    this.programHandle, "uModelView");
            
            this.uColorHandle = GLES20FixedPipeline.glGetUniformLocation(this.programHandle,
                    "uColor");
            
            this.aVertexCoordsHandle = GLES20FixedPipeline.glGetAttribLocation(
                    this.programHandle, "aVertexCoords");
        }
    }
    
    private static class TextureProgram {
        final int programHandle;
        final int uProjectionHandle;
        final int uModelViewHandle;
        final int uTextureHandle;
        final int aTextureCoordsHandle;
        final int aVertexCoordsHandle;
        final int uColorHandle;
        
        final int vertSize;
        
        TextureProgram(int vertSize) {
            this.vertSize = vertSize;

            String vertShaderSrc;
            switch(this.vertSize) {
                case 2 :
                    vertShaderSrc = GLES20FixedPipeline.TEXTURE_2D_VERT_SHADER_SRC;
                    break;
                case 3 :
                    vertShaderSrc = GLES20FixedPipeline.TEXTURE_3D_VERT_SHADER_SRC;
                    break;
                default :
                    throw new IllegalArgumentException();
            }
            final int vertShader = GLES20FixedPipeline.loadShader(
                    GLES20FixedPipeline.GL_VERTEX_SHADER,
                    vertShaderSrc);
            final int fragShader = GLES20FixedPipeline.loadShader(
                    GLES20FixedPipeline.GL_FRAGMENT_SHADER,
                    GLES20FixedPipeline.MODULATED_TEXTURE_FRAG_SHADER_SRC);
            
            this.programHandle = GLES20FixedPipeline.createProgram(vertShader, fragShader);
            GLES20FixedPipeline.glUseProgram(this.programHandle);

            this.uProjectionHandle = GLES20FixedPipeline.glGetUniformLocation(
                    this.programHandle, "uProjection");
            
            this.uModelViewHandle = GLES20FixedPipeline.glGetUniformLocation(
                    this.programHandle, "uModelView");
            
            this.uTextureHandle = GLES20FixedPipeline.glGetUniformLocation(this.programHandle,
                    "uTexture");
            
            this.uColorHandle = GLES20FixedPipeline.glGetUniformLocation(this.programHandle,
                    "uColor");
            
            this.aVertexCoordsHandle = GLES20FixedPipeline.glGetAttribLocation(
                    this.programHandle, "aVertexCoords");
            this.aTextureCoordsHandle = GLES20FixedPipeline.glGetAttribLocation(
                    this.programHandle, "aTextureCoords");

        }
    }

    protected final static class RenderBuffers {
        public int references;
        public final ByteBuffer buffer;
        public final FloatBuffer pointsBuffer;
        public final long pointsBufferPtr;
        public final FloatBuffer pointsVertsTexCoordsBuffer;
        public final IntBuffer textureAtlasIndicesBuffer;
        
        public RenderBuffers(int maxBuffered2dPoints) {
            this.buffer = Unsafe.allocateDirect(maxBuffered2dPoints * 2 * 4, ByteBuffer.class);
            this.pointsBuffer = this.buffer.asFloatBuffer();
            this.pointsBufferPtr = Unsafe.getBufferPointer(pointsBuffer);

            this.pointsVertsTexCoordsBuffer = Unsafe
                    .allocateDirect(pointsBuffer.capacity() * 2 * 4 * 6 * 2).order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            this.textureAtlasIndicesBuffer = Unsafe
                    .allocateDirect(pointsBuffer.capacity() * 4).order(ByteOrder.nativeOrder())
                    .asIntBuffer();            
        }
    }
    
    final static class SortInfo {
        final static int DEPTH = 0;
        final static int FID = 1;
        
        int order = FID;
        double centerLat = 0d;
        double centerLng = 0d;
        double measureFromLat = 0d;
        double measureFromLng = 0d;
        double measureFromHae = 0d;
    }
    
    static class DepthComparator implements Comparator<GLBatchPoint> {

        double measureFromLat;
        double measureFromLng;
        double measureFromHae;
        double metersPerDegreeLatSq;
        double metersPerDegreeLngSq;
        
        DepthComparator(double centerLat, double measureFromLat, double measureFromLng) {
            // XXX - this is a bit crude, but we don't have the camera
            //       information here...
            
            this.measureFromLat = measureFromLat;
            this.measureFromLng = measureFromLng;
            
            // approximate meters per degree given center
            final double rlat = Math.toRadians(centerLat);
            final double metersLat = 111132.92 - 559.82 * Math.cos(2* rlat) + 1.175*Math.cos(4*rlat);
            final double metersLng = 111412.84 * Math.cos(rlat) - 93.5 * Math.cos(3*rlat);
            
            metersPerDegreeLatSq = metersLat*metersLat;
            metersPerDegreeLngSq = metersLng*metersLng;
        }
        
        @Override
        public int compare(GLBatchPoint a, GLBatchPoint b) {
            // check if the same object
            if(a.featureId == b.featureId)
                return 0;
            
            final double adlat = a.latitude - measureFromLat;
            final double adlng = a.longitude - measureFromLng;
            
            final double bdlat = b.latitude - measureFromLat;
            final double bdlng = b.longitude - measureFromLng;
            

            // compute distance-squared for comparison
            final double aDistSq = ((adlat*adlat)*metersPerDegreeLatSq) + ((adlng*adlng)*metersPerDegreeLngSq);
            final double bDistSq = ((bdlat*bdlat)*metersPerDegreeLatSq) + ((bdlng*bdlng)*metersPerDegreeLngSq);

            if(aDistSq > bDistSq)
                return -1;
            else if(aDistSq < bDistSq)
                return 1;
            else
                return FID_COMPARATOR.compare(a, b);
        }
    }

    final static class PrimitiveBuffer {
        int[] vbo = {GLES30.GL_NONE};
        int count = 0;
        final int mode;

        PrimitiveBuffer(int mode) {
            this.mode = mode;
        }
    }

    final static class LineShader {
        public int handle;
        int u_mvp;
        int u_viewportSize;
        int a_vertexCoord0;
        int a_vertexCoord1;
        int a_texCoord;
        int a_color;
        int a_normal;
        int a_halfStrokeWidth;
        int a_dir;
        int a_pattern;
        int a_factor;
    }

}
