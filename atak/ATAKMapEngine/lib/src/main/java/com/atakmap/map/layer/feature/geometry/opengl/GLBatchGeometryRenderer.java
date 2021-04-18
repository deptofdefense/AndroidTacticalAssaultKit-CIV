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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import android.graphics.Color;
import android.graphics.PointF;
import android.opengl.GLES30;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.DistanceCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.Interop;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.FeatureDataStore;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapSurface;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.math.Rectangle;
import com.atakmap.map.opengl.GLAntiAliasedLine;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLRenderBatch2;
import com.atakmap.opengl.GLTextureAtlas;
import com.atakmap.util.ConfigOptions;

public class GLBatchGeometryRenderer implements GLMapRenderable, GLMapRenderable2 {

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
        "flat out int f_pattern;\n" +
        "out vec2 v_offset;\n" +
        "flat out float f_halfStrokeWidth;\n" +
        "flat out int f_factor;\n" +
        "flat out vec2 f_origin;\n" +
        "flat out vec2 f_normal;\n" +
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
        "  gl_Position.x = gl_Position.x - adjY;\n" +
        "  gl_Position.y = gl_Position.y + adjX;\n" +
        "  v_color = a_color;\n" +
        "  v_offset = vec2(-normalDir*(dy/dist)*(a_halfStrokeWidth+c_smoothBuffer), normalDir*(dx/dist)*(a_halfStrokeWidth+c_smoothBuffer));\n" +
        "  f_pattern = a_pattern;\n" +
        "  f_factor = a_factor;\n" +
        "  f_halfStrokeWidth = a_halfStrokeWidth;\n" +
        // flip the normal used in the distance calculation here to avoid unnecessary per-fragment overhead
        "  f_normal = normalize(vec2(p1.xy-p0.xy)) * ((2.0*a_dir) - 1.0);\n" +
        // select an origin to measure `gl_FragCoord` distance from.
        "  f_origin = mix(p0.xy, p1.xy, a_dir);\n" +
        "  f_origin.x = -1.0*mod(f_origin.x, u_viewportSize.x);\n" +
        "  f_origin.y = -1.0*mod(f_origin.y, u_viewportSize.y);\n" +
        "}";

    private final static String LINE_FSH =
        "#version 300 es\n" +
        "precision mediump float;\n" +
        "in vec4 v_color;\n" +
        "flat in int f_pattern;\n" +
        "flat in int f_factor;\n" +
        "flat in vec2 f_origin;\n" +
        "flat in vec2 f_normal;\n" +
        "in vec2 v_offset;\n" +
        "flat in float f_halfStrokeWidth;\n" +
        "out vec4 v_FragColor;\n" +
        "void main(void) {\n" +
        // measure the distance of the frag coordinate to the origin
        "  float d = dot(f_normal, gl_FragCoord.xy-f_origin);\n" +
        "  int idist = int(d);\n" +
        "  float b0 = float((f_pattern>>((idist/f_factor)%16))&0x1);\n" +
        "  float b1 = float((f_pattern>>(((idist+1)/f_factor)%16))&0x1);\n" +
        "  float alpha = mix(b0, b1, fract(d));\n" +
        "  float antiAlias = smoothstep(-1.0, 0.25, f_halfStrokeWidth-length(v_offset));\n" +
        "  v_FragColor = vec4(v_color.rgb, v_color.a*antiAlias*alpha);\n" +
        "}";

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
    private final static int MAX_BUFFERED_3D_POINTS = (MAX_BUFFERED_2D_POINTS*2)/3;
    private final static int MAX_VERTS_PER_DRAW_ARRAYS = 5000;

    private final static int POINT_BATCHING_THRESHOLD = 500;

    private final static Map<MapRenderer, RenderBuffers> renderBuffers = new IdentityHashMap<MapRenderer, RenderBuffers>();

    /*************************************************************************/
    
    private LinkedList<GLBatchPolygon> surfacePolys = new LinkedList<>();
    private LinkedList<GLBatchPolygon> spritePolys = new LinkedList<>();
    private LinkedList<GLBatchLineString> spriteLines = new LinkedList<GLBatchLineString>();
    private LinkedList<GLBatchLineString> surfaceLines = new LinkedList<GLBatchLineString>();
    private LinkedList<GLBatchPoint> batchPoints2 = new LinkedList<GLBatchPoint>();
    private LinkedList<GLBatchPoint> labels = new LinkedList<GLBatchPoint>();
    private LinkedList<GLBatchPoint> loadingPoints = new LinkedList<GLBatchPoint>();

    private SortedSet<GLBatchGeometry> sortedPolys = new TreeSet<GLBatchGeometry>(FID_COMPARATOR);
    private SortedSet<GLBatchGeometry> sortedLines = new TreeSet<GLBatchGeometry>(FID_COMPARATOR);

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
    private int batchSrid = -1;
    private PointD batchCentroidProj = new PointD(0d, 0d, 0d);
    private GeoPoint batchCentroid = GeoPoint.createMutable();
    private int rebuildBatchBuffers = -1;
    private int batchTerrainVersion = -1;
    private Matrix localFrame = Matrix.getIdentity();
    private Collection<LinesBuffer> surfaceLineBuffers = new ArrayList<>();
    private Collection<LinesBuffer> spriteLineBuffers = new ArrayList<>();
    private LineShader lineShader = null;

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

    /**
     * @deprecated use {@link #hitTest3(Collection, GeoPoint, double, PointF, float, int)} instead
     * @param loc
     * @param thresholdMeters
     * @return
     */
    @Deprecated
    @DeprecatedApi(since="4.1", forRemoval = true, removeAt = "4.4")
    public long hitTest(Point loc, double thresholdMeters) {
        ArrayList<Long> fid = new ArrayList<Long>();
        this.hitTest2(fid, loc, thresholdMeters, 1);
        if(fid.isEmpty())
            return FeatureDataStore.FEATURE_ID_NONE;
        return fid.get(0);
    }

    /**
     * @deprecated use {@link #hitTest3(Collection, GeoPoint, double, PointF, float, int)} instead
     * @param fids
     * @param loc
     * @param metersRadius
     * @param limit
     */
    @Deprecated
    @DeprecatedApi(since="4.1", forRemoval = true, removeAt = "4.4")
    public void hitTest2(Collection<Long> fids, Point loc, double metersRadius, int limit) {
        if (glmv == null)
            return;

        GeoPoint geoPoint = new GeoPoint(loc.getY(), loc.getX());

        double mercatorscale = Math.cos(Math.toRadians(loc.getY()));
        if (mercatorscale < 0.0001)
            mercatorscale = 0.0001;
        float screenRadius = (float) (metersRadius / (glmv.drawMapResolution * mercatorscale));
        PointF screenPoint = glmv.forward(geoPoint);

        hitTest3(fids, geoPoint, metersRadius, screenPoint, screenRadius, limit);
    }

    public void hitTest3(Collection<Long> fids, GeoPoint geoPoint, double metersRadius,
            PointF screenPoint, float screenRadius, int limit) {

        // Invert screen point for bottom-left coordinate system
        if (glmv != null)
            screenPoint.y = (glmv.getTop() - glmv.getBottom()) - screenPoint.y;

        // Build hit envelope
        double locx = geoPoint.getLongitude();
        double locy = geoPoint.getLatitude();
        double rlat = Math.toRadians(locy);
        double metersDegLat = 111132.92 - 559.82 * Math.cos(2* rlat) + 1.175*Math.cos(4*rlat);
        double metersDegLng = 111412.84 * Math.cos(rlat) - 93.5 * Math.cos(3*rlat);

        double ra = metersRadius / metersDegLat;
        double ro = metersRadius / metersDegLng;

        // Setup query params
        HitTestQueryParams params = new HitTestQueryParams();
        params.loc = geoPoint;
        params.meterRadius = metersRadius;
        params.hitBox = new Rectangle(screenPoint.x - screenRadius,
                screenPoint.y - screenRadius,
                screenRadius * 2, screenRadius * 2);
        params.hitEnvelope = new Envelope(locx-ro, locy-ra, Double.NaN, locx+ro,
                locy+ra, Double.NaN);
        params.view = glmv;
        params.screenPoint = screenPoint;
        params.screenRadius = screenRadius;
        params.limit = limit;

        // Points
        fids.addAll(hitTestGeometry(this.labels, params));
        fids.addAll(hitTestGeometry(this.batchPoints2, params));

        // Lines and polygons
        fids.addAll(hitTestGeometry(this.spritePolys, params));
        fids.addAll(hitTestGeometry(this.spriteLines, params));
        fids.addAll(hitTestGeometry(this.surfacePolys, params));
        fids.addAll(hitTestGeometry(this.surfaceLines, params));
    }

    private static class HitTestQueryParams {
        GeoPoint loc;
        double meterRadius;
        Rectangle hitBox;
        Envelope hitEnvelope;
        GLMapView view;
        PointF screenPoint;
        float screenRadius;
        int limit, count;
    }

    public void setBatch(Collection<GLBatchGeometry> geoms) {
        sortedPolys.clear();
        sortedLines.clear();

        surfacePolys.clear();
        spritePolys.clear();
        spriteLines.clear();
        surfaceLines.clear();

        loadingPoints.clear();
        batchPoints2.clear();
        labels.clear();

        this.fillBatchLists(geoms);
        
        // 
        if(sortInfo.order == SortInfo.FID)
            Collections.sort(this.batchPoints2, FID_COMPARATOR);
        else if(sortInfo.order == SortInfo.DEPTH)
            Collections.sort(this.batchPoints2, new DepthComparator(sortInfo.centerLat, sortInfo.measureFromLat, sortInfo.measureFromLng));

        for(GLBatchGeometry geom : this.sortedLines) {
            final GLBatchLineString line = (GLBatchLineString)geom;
            if(line.altitudeMode == Feature.AltitudeMode.ClampToGround)
                surfaceLines.add(line);
            else
                spriteLines.add(line);
        }
        this.sortedLines.clear();
        
        for(GLBatchGeometry geom : this.sortedPolys) {
            final GLBatchPolygon poly = (GLBatchPolygon)geom;
            if (poly.altitudeMode == Feature.AltitudeMode.ClampToGround)
                surfacePolys.add(poly);
            else
                spritePolys.add(poly);
        }
        this.sortedPolys.clear();

        batchSrid = -1;
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
                    break;
                }
                case 2: {
                    if(((GLBatchPolygon)g).fillColorA > 0.0f) {
                        sortedPolys.add(g);
                        break;
                    } else if(!((GLBatchPolygon)g).drawStroke) {
                        break;
                    }
                    // if the polygon isn't filled, treat it just like a line
                }
                case 1: {
                    //if(((GLBatchLineString)g).strokeColorA > 0.0f)
                        sortedLines.add(g);
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

        // match batches as dirty
        if (batchSrid != view.drawSrid) {
            // reset relative to center
            batchCentroid.set(view.drawLat, view.drawLng);
            view.scene.mapProjection.forward(batchCentroid, batchCentroidProj);
            batchSrid = view.drawSrid;

            localFrame.setToTranslation(batchCentroidProj.x, batchCentroidProj.y, batchCentroidProj.z);

            // mark batches dirty
            rebuildBatchBuffers = 0xFFFFFFFF;
            batchTerrainVersion = view.getTerrainVersion();
        } else if (batchTerrainVersion != view.getTerrainVersion()) {
            // mark batches dirty
            rebuildBatchBuffers = 0xFFFFFFFF;
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
                for (LinesBuffer lb : surfaceLineBuffers)
                    GLES30.glDeleteBuffers(1, lb.vbo, 0);
                surfaceLineBuffers.clear();
                this.buildLineBuffers(surfaceLineBuffers, view, surfaceLines);
            }

            this.drawLineBuffers(view, surfaceLineBuffers);
        } else if(!surfaceLineBuffers.isEmpty()) {
            for (LinesBuffer lb : surfaceLineBuffers)
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

        // XXX - batch currently only supports 2D vertices

        final boolean hardwareTransforms = (view.drawMapResolution > view.hardwareTransformResolutionThreshold/4d);

        int vertType;
//        if(!hardwareTransforms) {
//            // XXX - force all polygons projected as pixels as stroking does
//            //       not work properly. since vertices are in projected
//            //       coordinate space units, width also needs to be
//            //       specified as such. attempts to compute some nominal
//            //       scale factor produces reasonable results at lower map
//            //       resolutions but cause width to converge to zero (32-bit
//            //       precision?) at higher resolutions
//            vertType = GLGeometry.VERTICES_PIXEL;
//        } else {
//            vertType = GLGeometry.VERTICES_PROJECTED;
//
//            GLES20FixedPipeline.glLoadMatrixf(view.sceneModelForwardMatrix, 0);
//        }
        vertType = GLGeometry.VERTICES_PIXEL;

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
        sortInfo.order = (view.drawTilt > 0d || view.drawSrid == 4978) ? SortInfo.DEPTH : SortInfo.FID;
        sortInfo.centerLat = view.drawLat;
        sortInfo.centerLng = view.drawLng;
        
        view.scratch.geo.set(view.drawLat, view.drawLng);
        GeoPoint bottomCenter = GeoCalculations.midPoint(view.lowerLeft, view.lowerRight);
        GeoPoint measureFrom = DistanceCalculations.computeDestinationPoint(view.scratch.geo, view.scratch.geo.bearingTo(bottomCenter), view.scratch.geo.distanceTo(bottomCenter)*1.5d);
        
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

        // polygons
        renderLines(view, spritePolys, GLMapView.RENDER_PASS_SPRITES);

        // lines
        if (!this.spriteLines.isEmpty()) {
            List<GLBatchLineString> extrudeLines = new ArrayList<>();
            List<GLBatchLineString> bufferLines = new ArrayList<>();
            for (GLBatchLineString line : spriteLines) {
                if (line.isExtruded() && view.drawTilt > 0d)
                    extrudeLines.add(line);
                else
                    bufferLines.add(line);
            }

            // 2D lines
            if (MathUtils.hasBits(rebuildBatchBuffers, GLMapView.RENDER_PASS_SPRITES)) {
                for (LinesBuffer lb : spriteLineBuffers)
                    GLES30.glDeleteBuffers(1, lb.vbo, 0);
                spriteLineBuffers.clear();
                this.buildLineBuffers(spriteLineBuffers, view, bufferLines);
            }
            this.drawLineBuffers(view, spriteLineBuffers);

            // Extruded lines
            renderLines(view, extrudeLines, GLMapView.RENDER_PASS_SPRITES);
        } else if(!spriteLineBuffers.isEmpty()) {
            for (LinesBuffer lb : spriteLineBuffers)
                GLES30.glDeleteBuffers(1, lb.vbo, 0);
            spriteLineBuffers.clear();
        }
    }
    
    int forceGLRB = -1;
    int forcePointsDraw = -1;

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SPRITES|GLMapView.RENDER_PASS_SURFACE;
    }

    private static void bls3_vertex(ByteBuffer vbuf, GLBatchLineString.RenderState state, PointD v1, PointD v2, int n, int dir) {
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
        vbuf.put((byte)Math.min(state.strokeWidth/2.0f, 255.0f));
        vbuf.put((byte)dir);
        vbuf.put((byte)MathUtils.clamp(state.factor, 1, 255));
        vbuf.putInt(state.pattern);
    }

    void buildLineBuffers(Collection<LinesBuffer> linesBuf, GLMapView view, Collection<GLBatchLineString> lines) {
        // pointer to head of buffer; remains unmodified
        ByteBuffer buf = this.buffers.buffer.duplicate();
        buf.clear();

        // streaming vertex buffer
        ByteBuffer vbuf = this.buffers.buffer.duplicate();
        vbuf.order(ByteOrder.nativeOrder());
        vbuf.clear();

        GLBatchLineString.RenderState[] defaultrs = { GLBatchLineString.DEFAULT_RS };
        for (GLBatchLineString line : lines) {
            if (line.numPoints < 2)
                continue;

            // project the line vertices, applying the batch centroid
            line.projectedVerticesSrid = -1;
            line.centroidProj.x = batchCentroidProj.x;
            line.centroidProj.y = batchCentroidProj.y;
            line.centroidProj.z = batchCentroidProj.z;
            line.projectVertices(view, GLGeometry.VERTICES_PROJECTED);
            line.projectedVerticesSrid = -1;

            PointD p0 = new PointD(0d, 0d, 0d);
            PointD p1 = new PointD(0d, 0d, 0d);

            GLBatchLineString.RenderState[] rs = line.renderStates;
            if(rs == null)
                rs = defaultrs;
            for (int i = 0; i < rs.length; i++) {
                for (int j = 0; j < (line.numRenderPoints-1); j++) {
                    if (vbuf.remaining() < (6 * LINES_VERTEX_SIZE)) {
                        LinesBuffer b = new LinesBuffer();
                        GLES30.glGenBuffers(1, b.vbo, 0);
                        if (b.vbo[0] == GLES30.GL_NONE) {
                            Log.e(TAG, "Failed to allocate VBO, lines will not be drawn");
                        } else {
                            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, b.vbo[0]);
                            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vbuf.position(), buf, GLES30.GL_STATIC_DRAW);
                            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
                            b.count = vbuf.position() / LINES_VERTEX_SIZE;
                            linesBuf.add(b);
                        }
                        vbuf.clear();
                    }

                    p0.x = line.vertices.get(j * 3);
                    p0.y = line.vertices.get(j * 3+1);
                    p0.z = line.vertices.get(j * 3+2);
                    p1.x = line.vertices.get((j+1) * 3);
                    p1.y = line.vertices.get((j+1) * 3+1);
                    p1.z = line.vertices.get((j+1) * 3+2);

                    bls3_vertex(vbuf, rs[i], p0, p1, 0xFF, 0xFF);
                    bls3_vertex(vbuf, rs[i], p1, p0, 0xFF, 0x00);
                    bls3_vertex(vbuf, rs[i], p0, p1, 0x00, 0xFF);

                    bls3_vertex(vbuf, rs[i], p0, p1, 0xFF, 0xFF);
                    bls3_vertex(vbuf, rs[i], p1, p0, 0xFF, 0x00);
                    bls3_vertex(vbuf, rs[i], p1, p0, 0x00, 0x00);
                }
            }
        }

        // flush the remaining record
        if (vbuf.position() > 0) {
            LinesBuffer b = new LinesBuffer();
            GLES30.glGenBuffers(1, b.vbo, 0);
            if (b.vbo[0] == GLES30.GL_NONE) {
                Log.e(TAG, "Failed to allocate VBO, lines will not be drawn");
            } else {
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, b.vbo[0]);
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vbuf.position(), buf, GLES30.GL_STATIC_DRAW);
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
                b.count = vbuf.position() / LINES_VERTEX_SIZE;
                linesBuf.add(b);
            }
            vbuf.clear();
        }
    }

    void drawLineBuffers(GLMapView view, Collection<LinesBuffer> buf) {
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
            view.scratch.matrix.translate(batchCentroidProj.x, batchCentroidProj.y, batchCentroidProj.z);
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

        for (LinesBuffer it : buf) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, it.vbo[0]);
            GLES30.glVertexAttribPointer(lineShader.a_vertexCoord0, 3, GLES30.GL_FLOAT, false, LINES_VERTEX_SIZE, 0);
            GLES30.glVertexAttribPointer(lineShader.a_vertexCoord1, 3, GLES30.GL_FLOAT, false, LINES_VERTEX_SIZE, 12);
            GLES30.glVertexAttribPointer(lineShader.a_color, 4, GLES30.GL_UNSIGNED_BYTE, true, LINES_VERTEX_SIZE, 24);
            GLES30.glVertexAttribPointer(lineShader.a_normal, 1, GLES30.GL_UNSIGNED_BYTE, true, LINES_VERTEX_SIZE, 28);
            GLES30.glVertexAttribPointer(lineShader.a_halfStrokeWidth, 1, GLES30.GL_UNSIGNED_BYTE, false, LINES_VERTEX_SIZE, 29);
            GLES30.glVertexAttribPointer(lineShader.a_dir, 1, GLES30.GL_UNSIGNED_BYTE, true, LINES_VERTEX_SIZE, 30);
            // pattern
            GLES30.glVertexAttribPointer(lineShader.a_factor, 1, GLES30.GL_UNSIGNED_BYTE, false, LINES_VERTEX_SIZE, 31);
            GLES30.glVertexAttribPointer(lineShader.a_pattern, 1, GLES30.GL_UNSIGNED_INT, false, LINES_VERTEX_SIZE, 32);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, it.count);
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

        for (LinesBuffer it : surfaceLineBuffers)
            GLES30.glDeleteBuffers(1, it.vbo, 0);
        surfaceLineBuffers.clear();
        for (LinesBuffer it : spriteLineBuffers)
            GLES30.glDeleteBuffers(1, it.vbo, 0);
        spriteLineBuffers.clear();
    }
    
    /**************************************************************************/

    /**
     * Perform a hit test using a list of batch geometry
     * @param list List of batch geometry (points or line)
     * @param params Hit test query params
     * @return List of hit FIDs
     */
    private static List<Long> hitTestGeometry(
            LinkedList<? extends GLBatchGeometry> list,
            HitTestQueryParams params) {
        List<Long> fids = new ArrayList<>();

        // Hit query limit
        if (params.count >= params.limit)
            return fids;

        Iterator<? extends GLBatchGeometry> iter = list.descendingIterator();
        while (iter.hasNext()) {
            GLBatchGeometry item = iter.next();

            boolean hit = false;

            // Screen based hit test for points
            // Each point contains the last computed screen location
            if (item instanceof GLBatchPoint) {
                GLBatchPoint point = (GLBatchPoint) item;
                hit = params.hitBox.contains(point.screenX, point.screenY);
            }

            // Geodetic hit tests for lines
            // This is more memory-efficient than storing the screen point for every line vertex
            // Also, since lines (currently) aren't rendered floating above terrain this will
            // work just fine, for now.
            else if (item instanceof GLBatchLineString) {
                GLBatchLineString line = (GLBatchLineString) item;
                hit = testOrthoHit(line, params);
            }

            if (hit) {
                fids.add(item.featureId);
                if (++params.count == params.limit)
                    return fids;
            }
        }

        return fids;
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
    
    /**************************************************************************/
    // XXX - CONSOLIDATE INTO ONE LOCATION POST 3.3 !!!
    /**************************************************************************/

    // XXX - next 3 modified from EditablePolyline, review for optimization
    
    private static boolean mbrIntersects(Envelope mbb, Point point, double radiusMeters) {
        final double x = point.getX();
        final double y = point.getY();

        if(Rectangle.contains(mbb.minX, mbb.minY, mbb.maxX, mbb.maxY, x, y))
            return true;
        
        // XXX - check distance from minimum bounding box is with the radius
        final double fromX;
        if(x < mbb.minX) {
            fromX = mbb.minX;
        } else if(x > mbb.maxX){
            fromX = mbb.maxX;
        } else {
            fromX = x;
        }
        
        final double fromY;
        if(y < mbb.minY) {
            fromY = mbb.minY;
        } else if(y > mbb.maxY){
            fromY = mbb.maxY;
        } else {
            fromY = y;
        }

        return (DistanceCalculations.calculateRange(new GeoPoint(fromY, fromX), new GeoPoint(y, x)) < radiusMeters);
    }
    
    private static boolean testOrthoHit(GLBatchLineString line,
                                        HitTestQueryParams params) {
        if (line.renderPoints == null)
            return false;

        long linestringPtr = Unsafe.getBufferPointer(line.renderPoints);
        double x0;
        double y0;
        double x1;
        double y1;
        double z0, z1;

        double lx = params.loc.getLongitude();
        Envelope t2 = new Envelope(params.hitEnvelope);
        Envelope mbr = line.mbb;

        // Account for unwrapped longitudes
        if (mbr.maxX > 180 && mbr.minX > t2.maxX) {
            t2.minX += 360;
            t2.maxX += 360;
            lx += 360;
        } else if (mbr.minX < -180 && mbr.maxX < t2.minX) {
            t2.minX -= 360;
            t2.minX -= 360;
            lx -= 360;
        }

        boolean onGround = params.view.drawTilt == 0
                || line.altitudeMode == Feature.AltitudeMode.ClampToGround;

        // if it is not tilted, then a simple intersect should suffice for the first pass.
        if (onGround && !Rectangle.intersects(mbr.minX, mbr.minY, mbr.maxX, mbr.maxY,
                                  t2.minX, t2.minY, t2.maxX, t2.maxY)) {

            //Log.d(TAG, "hit not contained in any geobounds");
            return false;
        }

        // Distance calculation is squared
        double radiusSq = Math.pow(params.screenRadius, 2);

        int bytesPerPoint = 24;
        final double ly = params.loc.getLatitude();
        for (int i = 0; i < line.numRenderPoints-1; ++i) {
            x0 = Unsafe.getDouble(linestringPtr);
            y0 = Unsafe.getDouble(linestringPtr + 8);
            x1 = Unsafe.getDouble(linestringPtr + bytesPerPoint);
            y1 = Unsafe.getDouble(linestringPtr + bytesPerPoint + 8);

            if(onGround && isectTest(x0, y0, x1, y1, lx, ly, params.meterRadius, t2)) {
                return true;
            }

            // perform Vector3D intersection
            if (params.view.drawTilt > 0) {

                z0 = Unsafe.getDouble(linestringPtr + 16);
                z1 = Unsafe.getDouble(linestringPtr + bytesPerPoint + 16);

                if (line.altitudeMode == Feature.AltitudeMode.Relative) {
                    z0 += params.view.getTerrainMeshElevation(y0, x0);
                    z1 += params.view.getTerrainMeshElevation(y1, x1);
                }

                com.atakmap.coremap.maps.coords.Vector3D touch =
                        new com.atakmap.coremap.maps.coords.Vector3D(
                                params.screenPoint.x, params.screenPoint.y, 0);
                PointF spt1 = params.view.forward(new GeoPoint(y0, x0, z0));
                PointF spt2 = params.view.forward(new GeoPoint(y1, x1, z1));

                com.atakmap.coremap.maps.coords.Vector3D nearest = com.atakmap.coremap.maps.coords.Vector3D.nearestPointOnSegment(touch,
                        new com.atakmap.coremap.maps.coords.Vector3D(spt1.x, spt1.y, 0),
                        new com.atakmap.coremap.maps.coords.Vector3D(spt2.x, spt2.y, 0));

                double dist = nearest.distanceSq(touch);
                if (dist <= radiusSq) {
                    return true;
                }
            }

            linestringPtr += bytesPerPoint;
        }
        //Log.d(TAG, "hit not contained in any sub geobounds");
        return false;
    }

    private static boolean isectTest(double x1, double y1, double x2, double y2, double x3,double y3, double radius, Envelope test) { // x3,y3 is the point
        if(!Rectangle.intersects(Math.min(x1, x2), Math.min(y1, y2),
                                 Math.max(x1, x2), Math.max(y1, y2),
                                 test.minX, test.minY,
                                 test.maxX, test.maxY)) {
            return false;
        }

        double px = x2-x1;
        double py = y2-y1;
    
        double something = px*px + py*py;
    
        double u =  ((x3 - x1) * px + (y3 - y1) * py) / something;
    
        if(u > 1)
            u = 1;
        else if(u < 0)
            u = 0;
    
        double x = x1 + u * px;
        double y = y1 + u * py;

        return DistanceCalculations.calculateRange(new GeoPoint(y, x), new GeoPoint(y3, x3))<radius;
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

    final static class LinesBuffer {
        int[] vbo = {GLES30.GL_NONE};
        int count = 0;
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
