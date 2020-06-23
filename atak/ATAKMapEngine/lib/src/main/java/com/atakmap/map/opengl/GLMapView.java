package com.atakmap.map.opengl;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.opengl.GLES30;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;

import com.atakmap.lang.Unsafe;
import com.atakmap.map.EngineLibrary;
import com.atakmap.map.elevation.ElevationSourceManager;
import com.atakmap.map.layer.control.TerrainBlendControl;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.Models;
import com.atakmap.map.layer.model.VertexDataLayout;
import com.atakmap.map.layer.raster.mobileimagery.MobileImageryRasterLayer2;
import com.atakmap.map.projection.Ellipsoid;
import com.atakmap.map.projection.Projection;
import com.atakmap.math.GeometryModel;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.math.Ray;
import com.atakmap.math.Rectangle;
import com.atakmap.map.AtakMapController;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.MapControl;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.Layer2;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayer3;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.projection.ProjectionFactory;
import com.atakmap.math.Matrix;
import com.atakmap.math.Vector3D;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLTexture;
import com.atakmap.opengl.GLWireFrame;
import com.atakmap.util.ConfigOptions;
import com.atakmap.util.Visitor;

/**
 * OpenGL view implementation for {@link com.atakmap.map.AtakMapView}. Unless otherwise
 * noted, all methods should only be invoked on the GL context thread (see
 * {@link com.atakmap.map.opengl.GLMapSurface#isGLThread()} if you need to determine
 * whether or not you are on the GL context thread). The values of public members should be
 * considered read-only and only valid on the GL context thread.
 *
 * @author Developer
 */
public class GLMapView implements AtakMapView.OnMapMovedListener,
        AtakMapView.OnMapProjectionChangedListener,
        AtakMapView.OnLayersChangedListener,
        AtakMapView.OnElevationExaggerationFactorChangedListener,
        AtakMapView.OnContinuousScrollEnabledChangedListener,
        AtakMapController.OnFocusPointChangedListener,
        MapRenderer {

    static {
        EngineLibrary.initialize();
    }

    private final static String OFFSCREEN_VERT_SHADER_SRC =
            "uniform mat4 uProjection;\n" +
                    "uniform mat4 uModelView;\n" +
                    "uniform mat4 uModelViewOffscreen;\n" +
                    "uniform float uTexWidth;\n" +
                    "uniform float uTexHeight;\n" +
                    "attribute vec3 aVertexCoords;\n" +
                    "varying vec2 vTexPos;\n" +
                    "void main() {\n" +
                    "  vec4 offscreenPos = uModelViewOffscreen * vec4(aVertexCoords.xyz, 1.0);\n" +
                    "  offscreenPos.x = offscreenPos.x / offscreenPos.w;\n" +
                    "  offscreenPos.y = offscreenPos.y / offscreenPos.w;\n" +
                    "  offscreenPos.z = offscreenPos.z / offscreenPos.w;\n" +
                    "  vec4 texPos = vec4(offscreenPos.x / uTexWidth, offscreenPos.y / uTexHeight, 0.0, 1.0);\n" +
                    "  vTexPos = texPos.xy;\n" +
                    "  gl_Position = uProjection * uModelView * vec4(aVertexCoords.xyz, 1.0);\n" +
                    "}";

    private final static String OFFSCREEN_FRAG_SHADER_SRC =
            "precision mediump float;\n" +
                    "uniform sampler2D uTexture;\n" +
                    "varying vec2 vTexPos;\n" +
                    "void main(void) {\n" +
                    "  gl_FragColor = texture2D(uTexture, vTexPos);\n" +
                    "}";

    public final static int RENDER_PASS_SURFACE = 0x01;
    public final static int RENDER_PASS_SPRITES = 0x02;
    public final static int RENDER_PASS_SCENES = 0x04;
    public final static int RENDER_PASS_UI = 0x08;

    // retain an instance of the scale component for the vertical flip so we
    // don't have to instantiate on every draw
    private final static Matrix XFORM_VERTICAL_FLIP_SCALE = Matrix
            .getScaleInstance(1.0d, -1.0d);

    public final static int MATCH_SURFACE = -1;
    private static final double _EPSILON = 0.0001d;

    private static final double _EPSILON_F = 0.01d;

    private final static boolean depthEnabled = true;

    /** The scale that the map is being drawn at. */
    public double drawMapScale = 2.5352504279048383E-9d;
    /** The resolution in meters-per-pixel that the map is being drawn at. */
    public double drawMapResolution = 0.0d;
    /** The latitude of the center point of the rendering */
    public double drawLat = 0d;
    /** The longitude of the center point of the rendering */
    public double drawLng = 0d;
    /** The rotation, in radians, of the map about the center point */
    public double drawRotation = 0d;
    /** The tilt, in radians, of the map about the center point */
    public double drawTilt = 0d;
    /** The current animation factor for transitions */
    public double animationFactor = 0.3d;
    /**
     * The current version of the draw parameters. Must be incremented each time the parameters
     * change.
     */
    public int drawVersion = 0;

    /** Flag indicating whether or not this view is used for targeting */
    public boolean targeting = false;

    public double westBound = -180d;
    public double southBound = -90d;
    public double northBound = 90d;
    public double eastBound = 180d;
    public double eastBoundUnwrapped = 180d;
    public double westBoundUnwrapped = -180d;
    public boolean crossesIDL = false;
    public final GLAntiMeridianHelper idlHelper = new GLAntiMeridianHelper();

    public int _left;
    public int _right;
    public int _top;
    public int _bottom;

    private GLMapSurface _surface;
    public int drawSrid = -1;
    private Projection drawProjection;

    public float focusx, focusy;

    public GeoPoint upperLeft;
    public GeoPoint upperRight;
    public GeoPoint lowerRight;
    public GeoPoint lowerLeft;

    public boolean settled;

    public int renderPump;

    protected Animator animator;

    /** access is only thread-safe on the layers changed callback thread */
    private Map<Layer, GLLayer2> layerRenderers;
    /** access is only thread-safe on the GL thread */
    private List<GLLayer2> renderables;

    private GLMapRenderable basemap;
    private GLMapRenderable defaultBasemap;

    private Matrix verticalFlipTranslate;
    private int verticalFlipTranslateHeight;

    private long coordTransformPtr;

    /**
     * A shared set of mutable objects that may be used by renderables in their <code>draw</code>
     * method to avoid having to instantiate new objects for common rendering tasks like coordinate
     * transformation. References to these objects should not be held.
     */
    public final ScratchPad scratch;

    private final ScratchPad internal;

    public boolean rigorousRegistrationResolutionEnabled = true;

    public long animationLastTick = -1L;
    public long animationDelta = -1L;

    private int sceneModelVersion;
    public MapSceneModel scene;
    private MapSceneModel oscene;
    public float[] sceneModelForwardMatrix;
    private DoubleBuffer bulkForwardMatrix;
    private DoubleBuffer bulkInverseMatrix;

    // XXX - OsrUtils current expects 3x3 matrix for 2D transforms !!!
    private DoubleBuffer bulkForwardMatrix3x3;
    private DoubleBuffer bulkInverseMatrix3x3;

    public final static double recommendedGridSampleDistance = 0.125d;

    private Map<Layer2, Collection<MapControl>> controls;
    private Collection<MapControl> mapViewControls;

    public double hardwareTransformResolutionThreshold = 0d;

    //public GLTerrain terrain;
    public TerrainRenderService terrain;
    public static final double elevationOffset = 0d;
    public double elevationScaleFactor;
    public boolean terrainBlendEnabled = false;
    public double terrainBlendFactor = 1.0;

    public boolean continuousScrollEnabled = true;

    private boolean drawTerrain = true;
    private boolean drawTerrainMesh = true;
    private boolean refreshTerrain = true;
    private boolean lockTiles = false;
    // XXX - when not forced, lines do significant vertical jitter when zoomed in and tilted ???
    private boolean forceTerrainTileRefresh = true;

    private boolean continuousRenderEnabled = true;

    private MapMoved mapMovedUpdater = new MapMoved();

    // XXX - move offscreen into single struct for easier management

    // offscreen rendering


    static class Offscreen {
        static class Program {
            int handle;

            int uProjection;
            int uModelView;
            int uModelViewOffscreen;
            int uTexWidth;
            int uTexHeight;
            int aVertexCoords;

            int uTexture;

            Program() {
                handle = 0;
            }
        }

        /** offscreen texture */
        GLTexture texture;
        /** offscreen scene */
        MapSceneModel scene;

        /** offscreen FBO handles, index 0 is FBO, index 1 is depth buffer */
        int[] fbo;

        //GLOffscreenVertex[] vertices;

        double hfactor = Double.NaN;

        Collection<TerrainTile> terrainTiles = new LinkedList<TerrainTile>();
        int terrainTilesVersion = -1;

        final Program program = new Program();
    }

    static class TerrainTile {
        int numIndices;
        ModelInfo info;
        Model model;
        int skirtIndexOffset;
        /**
         * AABB in WGS84; x=latitude, y=longitude, z=hae
         */
        Envelope aabb;

        Object opaque;

        TerrainTile() {
            this.info = new ModelInfo();
            this.info.localFrame = Matrix.getIdentity();
            this.info.srid = -1;
            this.model = null;
            this.aabb = null;
        }
    }

    Offscreen offscreen;
    private final boolean enableMultiPassRendering;

    private boolean debugDrawBounds;

    private Set<OnControlsChangedListener> controlsListeners;

    /**
     * Create a GLMapView
     *
     * @param surface the GLMapSurface to render to
     * @param left the left pixel bound in GL view space
     * @param bottom the bottom pixel bound in GL view space
     * @param right the right pixel bound in GL view space
     * @param top the top pixel bound in GL view space
     */
    public GLMapView(GLMapSurface surface, int left, int bottom, int right, int top) {
        _surface = surface;


        this.upperLeft = GeoPoint.createMutable();
        this.upperRight = GeoPoint.createMutable();
        this.lowerRight = GeoPoint.createMutable();
        this.lowerLeft = GeoPoint.createMutable();

        this.settled = true;

        this.renderPump = 0;

        this.animator = new Animator();

        this.scratch = new ScratchPad();
        this.internal = new ScratchPad();

        this.layerRenderers = new IdentityHashMap<Layer, GLLayer2>();
        this.renderables = new LinkedList<GLLayer2>();

        this.defaultBasemap = new GLBaseMap();
        this.basemap = this.defaultBasemap;

        this.verticalFlipTranslate = null;
        this.verticalFlipTranslateHeight = -1;

        this.coordTransformPtr = 0L;

        this.sceneModelForwardMatrix = new float[16];

        _left = left;
        _right = right;
        _top = top;
        _bottom = bottom;
        focusx = (float) (_right + _left) / 2;
        focusy = (float) (_top + _bottom) / 2;

        this.scene = new MapSceneModel(_surface.getMapView());
        this.sceneModelVersion = this.drawVersion-1;

        this.drawSrid = this.scene.mapProjection.getSpatialReferenceID();
        this.drawProjection = this.scene.mapProjection;
        this.drawLat = _surface.getMapView().getLatitude();
        this.drawLng = _surface.getMapView().getLongitude();
        this.drawRotation = _surface.getMapView().getMapRotation();
        this.drawTilt = _surface.getMapView().getMapTilt();
        this.drawMapScale = _surface.getMapView().getMapScale();
        this.drawMapResolution = _surface.getMapView().getMapResolution();
        this.elevationScaleFactor = _surface.getMapView().getElevationExaggerationFactor();
        this.focusx = _surface.getMapView().getMapController().getFocusX();
        this.focusy = _surface.getMapView().getMapController().getFocusY();
        this.continuousScrollEnabled = _surface.getMapView().isContinuousScrollEnabled();

        ByteBuffer buf;

        buf = com.atakmap.lang.Unsafe.allocateDirect(16*8);
        buf.order(ByteOrder.nativeOrder());
        this.bulkForwardMatrix = buf.asDoubleBuffer();

        buf = com.atakmap.lang.Unsafe.allocateDirect(16*8);
        buf.order(ByteOrder.nativeOrder());
        this.bulkInverseMatrix = buf.asDoubleBuffer();

        // XXX -
        buf = com.atakmap.lang.Unsafe.allocateDirect(9*8);
        buf.order(ByteOrder.nativeOrder());
        this.bulkForwardMatrix3x3 = buf.asDoubleBuffer();

        buf = com.atakmap.lang.Unsafe.allocateDirect(9*8);
        buf.order(ByteOrder.nativeOrder());
        this.bulkInverseMatrix3x3 = buf.asDoubleBuffer();

        this.coordTransformPtr = OsrUtils.createProjection(this.drawSrid);

        this.controls = new IdentityHashMap<Layer2, Collection<MapControl>>();

        this.terrain = new ElMgrTerrainRenderService(this);
        ElevationSourceManager.addOnSourcesChangedListener((ElMgrTerrainRenderService)this.terrain);
        //this.terrain = new LegacyElMgrTerrainRenderService();

        this.offscreen = new Offscreen();

        this.enableMultiPassRendering = (ConfigOptions.getOption("glmapview.enable-multi-pass-rendering", 1) != 0);
        this.debugDrawBounds = (ConfigOptions.getOption("glmapview.debug-draw-bounds", 0) != 0);
        this.drawTerrainMesh = (ConfigOptions.getOption("glmapview.draw-terrain-mesh", 0) != 0);

        this.controlsListeners = Collections.newSetFromMap(new IdentityHashMap<OnControlsChangedListener, Boolean>());

        this.registerControl(null, terrainBlendControl);

        this.startAnimating(this.drawLat, this.drawLng, this.drawMapScale, this.drawRotation, this.drawTilt, 1d);
    }

    private TerrainBlendControl terrainBlendControl = new TerrainBlendControl() {

        private double localBlendFactor = 1.0;
        private boolean localBlendEnabled = false;

        @Override
        public double getBlendFactor() {
            synchronized (this) {
                return localBlendFactor;
            }
        }

        @Override
        public boolean getEnabled() {
            synchronized (this) {
                return localBlendEnabled;
            }
        }

        @Override
        public void setEnabled(final boolean enabled) {
            synchronized (this) {
                localBlendEnabled = enabled;
            }
            _surface.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLMapView.this.terrainBlendEnabled = enabled;
                }
            });
        }

        @Override
        public void setBlendFactor(double blendFactor) {
            final double clampedValue = MathUtils.clamp(blendFactor, 0.0, 1.0);
            synchronized (this) {
                localBlendFactor = clampedValue;
            }
            _surface.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLMapView.this.terrainBlendFactor = clampedValue;
                }
            });
        }
    };

    public void setBaseMap(final GLMapRenderable basemap) {
        _surface.queueEvent(new Runnable() {
            @Override
            public void run() {
                if(GLMapView.this.basemap != null)
                    GLMapView.this.basemap.release();
                GLMapView.this.basemap = basemap;
            }
        });
    }

    /**
     * Clean up the GLMapView
     */
    public void dispose() {
        if(this.renderables != null){
            this.renderables.clear();
            this.renderables = null;
        }

        if (this.coordTransformPtr != 0L) {
            OsrUtils.destroyProjection(this.coordTransformPtr);
            this.coordTransformPtr = 0L;
        }

        ElevationSourceManager.removeOnSourcesChangedListener((ElMgrTerrainRenderService)this.terrain);
        this.terrain.dispose();
    }

    public void release() {
        if(this.renderables != null)
            for(GLLayer2 layer : this.renderables) {
                // XXX - stop here ???
                layer.release();
            }
        if(this.basemap != null)
            this.basemap.release();
    }

    /**
     * Start animating to a target Latitude, Longitude, and zoom level
     *
     * @param lat target latitude
     * @param lng target longitude
     * @param scale target map scale
     * @param animateFactor (0d, 1d] smoothness factor of snapping animation effect (0.3d is a good
     *            default)
     */
    public void startAnimating(double lat, double lng, double scale, double rotation,
                               double tilt, double animateFactor) {
        this.animator.startAnimating(lat, lng, scale, rotation, tilt, animateFactor);
    }

    public void startAnimatingFocus(float x, float y, double animateFactor) {
        this.animator.startAnimatingFocus(x, y, animateFactor);
    }

    public void getMapRenderables(Collection<GLMapRenderable> retval) {
        if(!GLMapSurface.isGLThread())
            throw new IllegalStateException();
        if(this.renderables != null)
            retval.addAll(this.renderables);
    }

    public void setOnAnimationSettledCallback(OnAnimationSettledCallback c) {
        this.animator._animCallback = c;
    }

    /**
     * Get the GLMapSurface being rendered to
     * <P>
     * <B>This method may be invoked from any thread.</B>
     *
     * @return
     */
    public GLMapSurface getSurface() {
        return _surface;
    }

    /**
     * Retrieves the mesh elevation, including offset and scaling, at the
     * specified latitude and longitude.
     *
     * <P>This method may <B>ONLY</B> be safely called from the GL thread.
     *
     * @param latitude
     * @param longitude
     *
     * @return  The elevation value at the given latitude/longitude
     */
    public double getTerrainMeshElevation(double latitude, double longitude) {
        return this.getTerrainMeshElevationImpl(latitude, longitude, true);
    }

    private double getTerrainMeshElevationImpl(double latitude, double longitude, boolean lookupOffMesh) {
        double elevation = Double.NaN;
        // XXX - implement IDL cross handling
        if(!this.crossesIDL) {
            synchronized(this.offscreen) {
                final double aboveSurface = 30000;

                // shoot a nadir ray into the terrain tiles and obtain the
                // height at the intersection
                for(TerrainTile tile : this.offscreen.terrainTiles) {
                    // AABB/bounds check
                    if(!Rectangle.contains(tile.aabb.minX,
                                           tile.aabb.minY,
                                           tile.aabb.maxX,
                                           tile.aabb.maxY,
                                           longitude, latitude)) {
                        continue;
                    }

                    Projection proj = MobileImageryRasterLayer2.getProjection(tile.info.srid);
                    if(proj == null)
                        continue;

                    GeoPoint scratchG = GeoPoint.createMutable();
                    PointD scratchP = new PointD(0d, 0d, 0d);

                    // obtain the ellipsoid surface point
                    scratchG.set(latitude, longitude);
                    scratchG.set(GeoPoint.UNKNOWN);
                    proj.forward(scratchG, scratchP);
                    final double surfaceX = scratchP.x;
                    final double surfaceY = scratchP.y;
                    final double surfaceZ = scratchP.z;

                    // obtain the point at altitude
                    scratchG.set(aboveSurface);
                    proj.forward(scratchG, scratchP);

                    // construct the geometry model and compute the intersection
                    GeometryModel model = Models.createGeometryModel(tile.model, tile.info.localFrame);
                    PointD isect = model.intersect(new Ray(scratchP, new Vector3D(surfaceX-scratchP.x, surfaceY-scratchP.y, surfaceZ-scratchP.z)));
                    if(isect != null) {
                        scratchG.set(GeoPoint.UNKNOWN);
                        proj.inverse(isect, scratchG);
                        final double alt = scratchG.getAltitude();
                        if(!GeoPoint.isAltitudeValid(alt))
                            continue;
                        final double el = EGM96.getHAE(scratchG);
                        if(Double.isNaN(elevation) || el > elevation)
                            elevation = el;
                    }
                }
            }
        }
        // if lookup failed and lookup off mesh is allowed, query the terrain
        // service
        if(Double.isNaN(elevation) && lookupOffMesh) {
            elevation = this.terrain.getElevation(new GeoPoint(latitude, longitude));
        }
        // no elevation was found, use the mean
        if(Double.isNaN(elevation))
            elevation = 0d;
        return elevation;
    }

    /**
     *
     * @param latitude
     * @param longitude
     * @return
     *
     * @deprecated
     */
    public double getElevation(double latitude, double longitude) {
        if(this.offscreen == null)
            return GeoPoint.UNKNOWN;
        synchronized(this.offscreen) {
            final double retval = this.getTerrainMeshElevationImpl(latitude, longitude, false);
            if(Double.isNaN(retval))
                return GeoPoint.UNKNOWN;
            return retval;
        }
    }

    /**
     * Computes the OpenGL coordinate space coordinate that corresponds with the specified geodetic
     * coordinate.
     *
     * @param p A geodetic coordinate
     * @return The OpenGL coordinate that corresponds to <code>p</code>
     */
    public PointF forward(GeoPoint p) {
        return this.forward(p, new PointF());
    }

    /**
     * Computes the OpenGL coordinate space coordinate that corresponds with the specified geodetic
     * coordinate.
     *
     * @param p A geodetic coordinate
     * @param retval A optionally pre-allocated {@link android.graphics.PointF} instance that will
     *            have its value set to the transformed coordinate. If <code>null</code> a new
     *            <code>PointF</code> will be allocated and returned.
     * @return The OpenGL coordinate that corresponds to <code>p</code>
     */
    public PointF forward(GeoPoint p, PointF retval) {
        if (retval == null)
            retval = new PointF();
        this.forwardImpl(p, retval);
        return retval;
    }

    /**
     * Bulk geodetic to OpenGL coordinate transformation.
     *
     * @param p An array of geodetic coordinate
     * @param retval A optionally pre-allocated {@link android.graphics.PointF} array that will have
     *            its elements set to the transformed coordinates. If <code>null</code> a new
     *            <code>PointF</code> array will be allocated and returned. If non-<code>null</code>
     *            , elements will only be instantiated and assigned if <code>null</code>.
     * @return The OpenGL coordinates that correspond to <code>p</code>
     * @see {@link #forward(GeoPoint, PointF)}
     */
    public PointF[] forward(GeoPoint[] p, PointF[] retval) {
        if (retval == null)
            retval = new PointF[p.length];

        for (int i = 0; i < p.length; i++) {
            if (retval[i] == null)
                retval[i] = new PointF();
            this.forwardImpl(p[i], retval[i]);
        }
        return retval;
    }

    public PointD forward(GeoPoint p, PointD retval) {
        if (retval == null)
            retval = new PointD(0d, 0d, 0d);
        this.scene.forward(p, retval);
        return retval;
    }

    private void forwardImpl(GeoPoint p, PointF retval) {
        this.scene.forward(p, retval);
    }

    /**
     * Bulk geodetic to Open GL coordinate transformation method. This method is potentially MUCH
     * more efficient when transforming more than one coordinate pair than any of the other forward
     * methods. The <code>src</code> and <code>dst</code> objects may point to the same object.
     *
     * @param src The source coordinates, ordered as X, Y pairs (longitude, latitude).
     * @param dst The destination buffer for the open GL coordinates.
     * @throws IllegalArgumentException if <code>src</code> or <code>dst</code> are not direct, are
     *             not ordered by native endianness or do not return the same value for
     *             <code>remaining()</code>.
     */
    public void forward(FloatBuffer src, FloatBuffer dst) {
        // XXX - OsrUtils does not currently interpret input/output points as 3D
        if (!this.scene.mapProjection.is3D() && this.coordTransformPtr != 0L) {
            OsrUtils.forward(this.coordTransformPtr, this.bulkForwardMatrix3x3, src, dst);
        } else {
            final int count = src.remaining() / 2;
            int srcIdx = src.position();
            int dstIdx = dst.position();
            double lat;
            double lon;
            for (int i = 0; i < count; i++) {
                lon = src.get(srcIdx++);
                lat = src.get(srcIdx++);
                this.internal.geo.set(lat, lon, 0d);
                this.forwardImpl(this.internal.geo, this.internal.pointF);
                dst.put(dstIdx++, this.internal.pointF.x);
                dst.put(dstIdx++, this.internal.pointF.y);
            }
        }
    }

    /**
     * Bulk geodetic to Open GL coordinate transformation method. This method is potentially MUCH
     * more efficient when transforming more than one coordinate pair than any of the other forward
     * methods. The <code>src</code> and <code>dst</code> objects may point to the same object.
     *
     * @param src The source coordinates, ordered as X, Y pairs (longitude, latitude).
     * @param dst The destination buffer for the open GL coordinates.
     * @throws IllegalArgumentException if <code>src</code> or <code>dst</code> are not direct, are
     *             not ordered by native endianness or do not return the same value for
     *             <code>remaining()</code>.
     */
    public void forward(FloatBuffer src, int srcSize, FloatBuffer dst, int dstSize) {
        if(srcSize == 2 && dstSize == 2) {
            this.forward(src, dst);
            return;
        }

        final long srcPtr = Unsafe.getBufferPointer(src);
        final long dstPtr = Unsafe.getBufferPointer(dst);

        if(srcSize == 2 && dstSize == 3) {
            this.internal.geo.set(GeoPoint.UNKNOWN);

            final int count = src.remaining() / srcSize;
            int srcIdx = src.position();
            int dstIdx = dst.position();
            double lat;
            double lon;
            for (int i = 0; i < count; i++) {
                lon = Unsafe.getFloat(srcPtr + (srcIdx*4));
                lat = Unsafe.getFloat(srcPtr + ((srcIdx+1)*4));
                srcIdx += 2;
                this.internal.geo.set(lat, lon);
                this.scene.mapProjection.forward(this.internal.geo, this.internal.pointD);
                this.scene.forward.transform(this.internal.pointD, this.internal.pointD);
                Unsafe.setFloats(dstPtr + (dstIdx*4),
                        (float)this.internal.pointD.x,
                        (float)this.internal.pointD.y,
                        (float)this.internal.pointD.z);

                dstIdx += 3;
            }
        } else if(srcSize == 3 && dstSize == 2) {
            final int count = src.remaining() / srcSize;
            int srcIdx = src.position();
            int dstIdx = dst.position();
            double lat;
            double lon;
            double alt;
            for (int i = 0; i < count; i++) {
                lon = Unsafe.getFloat(srcPtr + (srcIdx*4));
                lat = Unsafe.getFloat(srcPtr + ((srcIdx+1)*4));
                alt = Unsafe.getFloat(srcPtr + ((srcIdx+2)*4));
                srcIdx += 3;
                this.internal.geo.set(lat, lon);
                this.internal.geo.set(alt);
                this.scene.mapProjection.forward(this.internal.geo, this.internal.pointD);
                this.scene.forward.transform(this.internal.pointD, this.internal.pointD);
                Unsafe.setFloats(dstPtr + (dstIdx*4),
                        (float)this.internal.pointD.x,
                        (float)this.internal.pointD.y);
                dstIdx += 2;
            }
        } else if(srcSize == 3 && dstSize == 3) {
            final int count = src.remaining() / srcSize;
            int srcIdx = src.position();
            int dstIdx = dst.position();
            double lat;
            double lon;
            double alt;
            for (int i = 0; i < count; i++) {
                lon = Unsafe.getFloat(srcPtr + (srcIdx*4));
                lat = Unsafe.getFloat(srcPtr + ((srcIdx+1)*4));
                alt = Unsafe.getFloat(srcPtr + ((srcIdx+2)*4));
                srcIdx += 3;
                this.internal.geo.set(lat, lon);
                this.internal.geo.set(alt);
                this.scene.mapProjection.forward(this.internal.geo, this.internal.pointD);
                this.scene.forward.transform(this.internal.pointD, this.internal.pointD);
                Unsafe.setFloats(dstPtr + (dstIdx*4),
                        (float)this.internal.pointD.x,
                        (float)this.internal.pointD.y,
                        (float)this.internal.pointD.z);

                dstIdx += 3;
            }
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Bulk geodetic to Open GL coordinate transformation method. This method is potentially MUCH
     * more efficient when transforming more than one coordinate pair than any of the other forward
     * methods. The <code>src</code> and <code>dst</code> objects may point to the same object.
     *
     * @param src The source coordinates, ordered as X, Y pairs (longitude, latitude).
     * @param dst The destination buffer for the open GL coordinates.
     * @throws IllegalArgumentException if <code>src</code> or <code>dst</code> are not direct, are
     *             not ordered by native endianness or do not return the same value for
     *             <code>remaining()</code>.
     */
    public void forward(DoubleBuffer src, FloatBuffer dst) {
        // XXX - OsrUtils does not currently interpret input/output points as 3D
        if (!this.scene.mapProjection.is3D() && this.coordTransformPtr != 0L) {
            OsrUtils.forward(this.coordTransformPtr, this.bulkForwardMatrix3x3, src, dst);
        } else {
            final int count = src.remaining() / 2;
            int srcIdx = src.position();
            int dstIdx = dst.position();
            double lat;
            double lon;
            for (int i = 0; i < count; i++) {
                lon = src.get(srcIdx++);
                lat = src.get(srcIdx++);
                this.internal.geo.set(lat, lon, 0d);
                this.forwardImpl(this.internal.geo, this.internal.pointF);
                dst.put(dstIdx++, this.internal.pointF.x);
                dst.put(dstIdx++, this.internal.pointF.y);
            }
        }
    }

    /**
     * Bulk geodetic to Open GL coordinate transformation method. This method is potentially MUCH
     * more efficient when transforming more than one coordinate pair than any of the other forward
     * methods. The <code>src</code> and <code>dst</code> objects may point to the same object.
     *
     * @param src The source coordinates, ordered as X, Y pairs (longitude, latitude).
     * @param dst The destination buffer for the open GL coordinates.
     * @throws IllegalArgumentException if <code>src</code> or <code>dst</code> are not direct, are
     *             not ordered by native endianness or do not return the same value for
     *             <code>remaining()</code>.
     */
    public void forward(DoubleBuffer src, int srcSize, FloatBuffer dst, int dstSize) {
        if(srcSize == 2 && dstSize == 2) {
            this.forward(src, dst);
            return;
        }

        final long srcPtr = Unsafe.getBufferPointer(src);
        final long dstPtr = Unsafe.getBufferPointer(dst);

        if(srcSize == 2 && dstSize == 3) {
            this.internal.geo.set(GeoPoint.UNKNOWN);

            final int count = src.remaining() / srcSize;
            int srcIdx = src.position();
            int dstIdx = dst.position();
            double lat;
            double lon;
            for (int i = 0; i < count; i++) {
                lon = Unsafe.getDouble(srcPtr + (srcIdx*8));
                lat = Unsafe.getDouble(srcPtr + ((srcIdx+1)*8));
                srcIdx += 2;
                this.internal.geo.set(lat, lon, 0d);
                this.scene.mapProjection.forward(this.internal.geo, this.internal.pointD);
                this.scene.forward.transform(this.internal.pointD, this.internal.pointD);
                Unsafe.setFloats(dstPtr + (dstIdx*4),
                        (float)this.internal.pointD.x,
                        (float)this.internal.pointD.y,
                        (float)this.internal.pointD.z);

                dstIdx += 3;
            }
        } else if(srcSize == 3 && dstSize == 2) {
            final int count = src.remaining() / srcSize;
            int srcIdx = src.position();
            int dstIdx = dst.position();
            double lat;
            double lon;
            double alt;
            for (int i = 0; i < count; i++) {
                lon = Unsafe.getDouble(srcPtr + (srcIdx*8));
                lat = Unsafe.getDouble(srcPtr + ((srcIdx+1)*8));
                alt = Unsafe.getDouble(srcPtr + ((srcIdx+2)*8));
                srcIdx += 3;
                this.internal.geo.set(lat, lon, alt);
                this.scene.mapProjection.forward(this.internal.geo, this.internal.pointD);
                this.scene.forward.transform(this.internal.pointD, this.internal.pointD);
                Unsafe.setFloats(dstPtr + (dstIdx*4),
                        (float)this.internal.pointD.x,
                        (float)this.internal.pointD.y);
                dstIdx += 2;
            }
        } else if(srcSize == 3 && dstSize == 3) {
            final int count = src.remaining() / srcSize;
            int srcIdx = src.position();
            int dstIdx = dst.position();
            double lat;
            double lon;
            double alt;
            for (int i = 0; i < count; i++) {
                lon = Unsafe.getDouble(srcPtr + (srcIdx*8));
                lat = Unsafe.getDouble(srcPtr + ((srcIdx+1)*8));
                alt = Unsafe.getDouble(srcPtr + ((srcIdx+2)*8));
                srcIdx += 3;
                this.internal.geo.set(lat, lon, alt);
                this.scene.mapProjection.forward(this.internal.geo, this.internal.pointD);
                this.scene.forward.transform(this.internal.pointD, this.internal.pointD);
                Unsafe.setFloats(dstPtr + (dstIdx*4),
                        (float)this.internal.pointD.x,
                        (float)this.internal.pointD.y,
                        (float)this.internal.pointD.z);

                dstIdx += 3;
            }
        } else {
            throw new IllegalArgumentException();
        }
    }

    public void inverse(FloatBuffer src, FloatBuffer dst) {
        // XXX -

        final int count = src.remaining() / 2;
        int srcIdx = src.position();
        int dstIdx = dst.position();
        for (int i = 0; i < count; i++) {
            this.internal.pointF.x = src.get(srcIdx++);
            this.internal.pointF.y = src.get(srcIdx++);
            this.inverseImpl(this.internal.pointF, this.internal.geo);
            dst.put(dstIdx++, (float) this.internal.geo.getLongitude());
            dst.put(dstIdx++, (float) this.internal.geo.getLatitude());
        }
    }

    public void inverse(FloatBuffer src, DoubleBuffer dst) {
        // XXX -

        final int count = src.remaining() / 2;
        int srcIdx = src.position();
        int dstIdx = dst.position();
        for (int i = 0; i < count; i++) {
            this.internal.pointF.x = src.get(srcIdx++);
            this.internal.pointF.y = src.get(srcIdx++);
            this.inverseImpl(this.internal.pointF, this.internal.geo);
            dst.put(dstIdx++, this.internal.geo.getLongitude());
            dst.put(dstIdx++, this.internal.geo.getLatitude());
        }
    }

    /**
     * Computes the geodetic coordinate corresponding to the specified pixel in OpenGL coordinate
     * space.
     *
     * @param p A pixel in the OpenGL coordinate space
     * @return The geodetic coordinate that corresponds to <code>p</code>
     */
    public GeoPoint inverse(PointF p) {
        return this.inverse(p, GeoPoint.createMutable());
    }

    /**
     * Computes the geodetic coordinate corresponding to the specified pixel in OpenGL coordinate
     * space.
     *
     * @param p A pixel in the OpenGL coordinate space
     * @param retval An optionally pre-allocated mutable
     *            {@link com.atakmap.coremap.maps.coords.GeoPoint} that will be set to the
     *            resulting geodetic coordinate. If <code>null</code> a newly allocated
     *            <code>GeoPoint<code> instance will be returned (whether
     *                  or not it is a <code>MutableGeoPoint</code> is undefined).
     * @return The geodetic coordinate that corresponds to <code>p</code>
     */
    public GeoPoint inverse(PointF p, GeoPoint retval) {
        if (retval == null)
            retval = GeoPoint.createMutable();
        this.inverseImpl(p, retval);
        return retval;
    }

    /**
     * Bulk OpenGL to geodetic coordinate transformation.
     *
     * @param p An array ofs pixel in the OpenGL coordinate space
     * @param retval An optionally pre-allocated {@link com.atakmap.coremap.maps.coords.GeoPoint}
     *            array that will be set to the resulting geodetic coordinates. If <code>null</code>
     *            a newly allocated <code>GeoPoint<code> array will be returned. If
     *                  non-<code>null</code>, all elements that are instances of
     *            <code>MutableGeoPoint</code> will have their values set, otherwise the element
     *            will be assigned a newly allocated <code>GeoPoint</code> instance.
     * @return The geodetic coordinates that correspond to <code>p</code>
     */
    public GeoPoint[] inverse(PointF[] p, GeoPoint[] retval) {
        if (retval == null)
            retval = new GeoPoint[p.length];

        for (int i = 0; i < p.length; i++) {
            if (!(retval[i].isMutable()))
                retval[i] = GeoPoint.createMutable();
            this.inverseImpl(p[i],  retval[i]);
        }
        return retval;
    }

    private void inverseImpl(PointF p, GeoPoint retval) {
        this.scene.inverse(p, retval);
    }

    private void computeOffscreenBounds(int drawSurfaceWidth, int drawSurfaceHeight) {
        double offscreenNorth;
        double offscreenWest;
        double offscreenSouth;
        double offscreenEast;
        GeoPoint offscreenUpperLeft;
        GeoPoint offscreenUpperRight;
        GeoPoint offscreenLowerRight;
        GeoPoint offscreenLowerLeft;
        boolean offscreenCrossesIdl;

        State stack = new State();
        stack.save(this);
        try {
            final double tiltSkew = Math.sin(Math.toRadians(this.drawTilt));

            // compute an adjustment to be applied to the scale based on the
            // current tilt
            double scaleAdj = 1d + (tiltSkew*2.5d);

            // we're going to stretch the capture texture vertically to try
            // to closely match the AOI with the perspective skew. If we
            // did not adjust the texture dimensions, the AOI defined by
            // simply zooming out would request way more data than we are
            // actually interested in
            final int offscreenTextureWidth = drawSurfaceWidth;
            final int offscreenTextureHeight = (int)Math.min(Math.ceil((double)drawSurfaceHeight * scaleAdj), this.offscreen.texture.getTexHeight());

            // update _left, _right, _top, _bottom
            _left = 0;
            _bottom = 0;
            _right = offscreenTextureWidth;
            _top = offscreenTextureHeight;

            // update focus
            this.focusx = ((float)this.focusx/(float)_surface.getMapView().getWidth()) * (float)offscreenTextureWidth;
            this.focusy = ((float)this.focusy/(float)_surface.getMapView().getHeight()) * (float)offscreenTextureHeight;

            // regenerate the scene model based on current parameters
            this.sceneModelVersion = ~this.drawVersion;
            this.validateSceneModelImpl(offscreenTextureWidth, offscreenTextureHeight);
            this.updateBounds();

            offscreenNorth = this.northBound;
            offscreenWest = this.westBound;
            offscreenSouth = this.southBound;
            offscreenEast = this.eastBound;

            offscreenUpperLeft = new GeoPoint(this.upperLeft);
            offscreenUpperRight = new GeoPoint(this.upperRight);
            offscreenLowerRight = new GeoPoint(this.lowerRight);
            offscreenLowerLeft = new GeoPoint(this.lowerLeft);
        } finally {
            stack.restore(this);
        }

        this.northBound = offscreenNorth;
        this.westBound = offscreenWest;
        this.southBound = offscreenSouth;
        this.eastBound = offscreenEast;

        (this.upperLeft).set(offscreenUpperLeft);
        (this.upperRight).set(offscreenUpperRight);
        (this.lowerRight).set(offscreenLowerRight);
        (this.lowerLeft).set(offscreenLowerLeft);

        this.idlHelper.update(this);
    }

    /**
     * Renders the current view of the map.
     */
    public void render() {
        if (_right == MATCH_SURFACE)
            _right = _surface.getWidth();
        if (_top == MATCH_SURFACE)
            _top = _surface.getHeight();

        // for occluded MapView do not render
        if (_right == _left || _top == _bottom)
             return; 


        animator.animate();

        // mark terrain fetch if the version has changed
        this.refreshTerrain |= (this.offscreen != null && this.terrain.getTerrainVersion() != this.offscreen.terrainTilesVersion);
        this.refreshTerrain |= forceTerrainTileRefresh;

        this.animator.updateView();

        // validate scene model
        this.validateSceneModel();
        this.oscene = this.scene;

        this.updateBounds();

        // compute the ortho far plane

        // obtain the camera position as LLA, then compute the distance to
        // horizon
        scene.mapProjection.inverse(scene.camera.location, internal.geo);
        // use a minimum height of 2m (~person standing)
        double heightMsl = Math.max(internal.geo.isAltitudeValid() ? EGM96.getMSL(internal.geo) : internal.geo.getAltitude(), 2d);
        // https://en.wikipedia.org/wiki/Horizon#Distance_to_the_horizon
        final double horizonDistance = Math.sqrt((2d*Ellipsoid.WGS84.semiMajorAxis*heightMsl) + (heightMsl*heightMsl));

        // the far distance in meters will be the minimm of the distance  to
        // the horizon and the center of the earth -- if the distance to the
        // horizon is less than the eye altitude, simply use the eye altitude
        final double farMeters = Math.max(horizonDistance, heightMsl);

        // compute camera location/target in meters
        final double camLocMetersX = scene.camera.location.x*scene.displayModel.projectionXToNominalMeters;
        final double camLocMetersY = scene.camera.location.y*scene.displayModel.projectionYToNominalMeters;
        final double camLocMetersZ = scene.camera.location.z*scene.displayModel.projectionZToNominalMeters;
        final double camTgtMetersX = scene.camera.target.x*scene.displayModel.projectionXToNominalMeters;
        final double camTgtMetersY = scene.camera.target.y*scene.displayModel.projectionYToNominalMeters;
        final double camTgtMetersZ = scene.camera.target.z*scene.displayModel.projectionZToNominalMeters;

        // distance from camera to target
        final double dist = MathUtils.distance(camLocMetersX, camLocMetersY, camLocMetersZ, camTgtMetersX, camTgtMetersY, camTgtMetersZ);

        // direction of camera pointing
        final double dirMeterX = (camTgtMetersX-camLocMetersX)/dist;
        final double dirMeterY = (camTgtMetersY-camLocMetersY)/dist;
        final double dirMeterZ = (camTgtMetersZ-camLocMetersZ)/dist;

        // compute the projected location, scaled to meters at the computed far
        // distance
        internal.pointD.x = camLocMetersX+(farMeters*dirMeterX);
        internal.pointD.y = camLocMetersY+(farMeters*dirMeterY);
        internal.pointD.z = camLocMetersZ+(farMeters*dirMeterZ);

        // unscale from meters to original projection units
        internal.pointD.x /= scene.displayModel.projectionXToNominalMeters;
        internal.pointD.y /= scene.displayModel.projectionYToNominalMeters;
        internal.pointD.z /= scene.displayModel.projectionZToNominalMeters;

        // obtain the far location in screen space to get the 'z'
        scene.forward.transform(internal.pointD, internal.pointD);

        // clamp the far plane to -1f
        final float far = Math.max(-1f, -(float)internal.pointD.z);
        GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_PROJECTION);
        GLES20FixedPipeline.glOrthof(_left, _right, _bottom, _top, 0.01f, far);

        // XXX - implementation needs to be transitioned to C++ SDK
        scene.camera.near = 0.01f;
        scene.camera.far = far;

        GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_MODELVIEW);
        GLES20FixedPipeline.glLoadIdentity();

        if(depthEnabled) {
            GLES20FixedPipeline.glDepthMask(true);
            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_DEPTH_TEST);

            GLES20FixedPipeline.glDepthRangef(0f, 1f);
            GLES20FixedPipeline.glClearDepthf(1f);
            GLES20FixedPipeline.glDepthFunc(GLES20FixedPipeline.GL_LEQUAL);
        }

        State stack = new State();
        stack.save(this);
        try {
            this.drawRenderables();
        } finally {
            stack.restore(this);
        }

        this.renderPump++;
    }

    private void constructOffscreenScene(int drawSurfaceWidth, int drawSurfaceHeight) {
        this.scene = createOffscreenSceneModel(this, drawSurfaceWidth, drawSurfaceHeight);
        this.refreshSceneMatrices();
        // update render parameters
        this.drawTilt = this.scene.camera.elevation+90d;
        this.drawRotation = this.scene.camera.azimuth;
        this.focusx = this.scene.focusx;
        this.focusy = this.scene.focusy;

        // update _left, _right, _top, _bottom
        _left = 0;
        _bottom = 0;
        _right = this.scene.width;
        _top = this.scene.height;

        this.offscreen.scene = this.scene;
    }

    private static MapSceneModel createOffscreenSceneModel(GLMapView view, final int drawSurfaceWidth, final int drawSurfaceHeight) {
        // adjust the size of the offscreen texture target based on the
        // tilt skew
        final double tiltSkew = Math.sin(Math.toRadians(view.drawTilt));

        // compute an adjustment to be applied to the scale based on the
        // current tilt
        final double scaleAdj = 1d + (tiltSkew*2.55d);

        // reset the tilt to 0 for offscreen render
        final double drawTilt = 0d;

        // adjust the scale/resolution to capture more than the nadir
        // AOI
        final double drawMapResolution = view.drawMapResolution * scaleAdj;

        // we're going to stretch the capture texture vertically to try
        // to closely match the AOI with the perspective skew. If we
        // did not adjust the texture dimensions, the AOI defined by
        // simply zooming out would request way more data than we are
        // actually interested in
        final int offscreenTextureWidth = (int)Math.ceil((double)drawSurfaceWidth / (scaleAdj*0.75d));
        final int offscreenTextureHeight = (int)Math.min(Math.ceil((double)drawSurfaceHeight * scaleAdj), view.offscreen.texture.getTexHeight());

        // update focus
        final float focusx = ((float)view.focusx/(float)view._surface.getMapView().getWidth()) * (float)offscreenTextureWidth;
        final float focusy = ((float)view.focusy/(float)view._surface.getMapView().getHeight()) * (float)offscreenTextureHeight;

        // generate the scene model based on current parameters
        final int vflipHeight = offscreenTextureHeight;

        view.internal.geo.set(view.drawLat, view.drawLng, 0d);

        if(view.drawProjection == null || view.drawProjection.getSpatialReferenceID() != view.drawSrid)
            view.drawProjection = ProjectionFactory.getProjection(view.drawSrid);
        final MapSceneModel retval = new MapSceneModel(AtakMapView.DENSITY*240f,
                offscreenTextureWidth, offscreenTextureHeight,
                view.drawProjection,
                view.internal.geo,
                focusx, focusy,
                view.drawRotation, drawTilt,
                drawMapResolution,
                view.continuousScrollEnabled);

        // account for flipping of y-axis for OpenGL coordinate space
        retval.inverse.translate(0d, vflipHeight, 0d);
        retval.inverse.concatenate(XFORM_VERTICAL_FLIP_SCALE);

        retval.forward.preConcatenate(XFORM_VERTICAL_FLIP_SCALE);
        view.internal.matrix.setToTranslation(0d, vflipHeight, 0d);
        retval.forward.preConcatenate(view.internal.matrix);

        return retval;
    }

    int depthToggle = 0x1;

    protected void drawRenderables() {
        scratch.pointD.x = 0;
        scratch.pointD.y = 0;
        scratch.pointD.z = 0;

        scratch.pointF.x = 0;
        scratch.pointF.y = 0;

        internal.pointD.x = 0;
        internal.pointD.y = 0;
        internal.pointD.z = 0;

        internal.pointF.x = 0;
        internal.pointF.y = 0;

        // if tilt, set FBO to texture
        boolean offscreenSurfaceRendering = false;

        int pass = RENDER_PASS_SCENES|RENDER_PASS_SPRITES|RENDER_PASS_SURFACE;

        final int drawSurfaceWidth = _surface.getWidth();
        final int drawSurfaceHeight = _surface.getHeight();
        final boolean emptySurface = ((drawSurfaceWidth*drawSurfaceHeight) == 0);

        LinkedList<TerrainTile> terrainTiles = new LinkedList<TerrainTile>();
        int terrainTilesVersion = -1;
        if(this.refreshTerrain && !lockTiles)
            terrainTilesVersion = this.terrain.lock(this, terrainTiles);


        State stack = null;
        if(!emptySurface && this.enableMultiPassRendering && (this.drawTilt > 0d || false)) {
            if(this.offscreen.fbo == null) {
                offscreenSurfaceRendering = this.initOffscreenRendering(drawSurfaceWidth, drawSurfaceHeight);
            } else if(this.offscreen.fbo[0] != 0){
                GLES20FixedPipeline.glBindFramebuffer(GLES20FixedPipeline.GL_FRAMEBUFFER,
                        this.offscreen.fbo[0]);
                offscreenSurfaceRendering = true;
            }
            if(offscreenSurfaceRendering) {
                GLES20FixedPipeline.glClear(GLES20FixedPipeline.GL_COLOR_BUFFER_BIT |
                        GLES20FixedPipeline.GL_DEPTH_BUFFER_BIT |
                        GLES20FixedPipeline.GL_STENCIL_BUFFER_BIT);

                stack = new State();
                stack.save(this);

                if(this.intersectWithTerrain(terrainTiles, this.scene, focusx, _top-focusy, this.internal.geo)) {
                    this.drawLat = this.internal.geo.getLatitude();
                    this.drawLng = this.internal.geo.getLongitude();
                }

                // compute the offscreen bounds based on the adjusted center
                this.computeOffscreenBounds(drawSurfaceWidth, drawSurfaceHeight);

                // construct the offscreen scene
                this.constructOffscreenScene(drawSurfaceWidth, drawSurfaceHeight);

                // reset the bounds based on the offscreen scene
                stack.northBound = this.northBound;
                stack.westBound = this.westBound;
                stack.eastBound = this.eastBound;
                stack.southBound = this.southBound;
                stack.upperLeft.set(this.upperLeft);
                stack.upperRight.set(this.upperRight);
                stack.lowerRight.set(this.lowerRight);
                stack.lowerLeft.set(this.lowerLeft);

                // mark render pass: surface
                pass = RENDER_PASS_SURFACE;

                // update the viewport and transformation matrices
                GLES20FixedPipeline.glViewport(_left, _bottom, _right, _top);
                GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_PROJECTION); // select projection
                // matrix
                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glLoadIdentity(); // reset projection matrix
                GLES20FixedPipeline.glOrthof(_left, _right, _bottom, _top, -1f, 1f);

                GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_MODELVIEW);
                GLES20FixedPipeline.glPushMatrix();
                GLES20FixedPipeline.glLoadIdentity();

                if(depthEnabled) {
                    GLES20FixedPipeline.glDepthMask(false);
                    GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_DEPTH_TEST);
                }
            }
        }
        if(depthEnabled && !offscreenSurfaceRendering) {
            if(MathUtils.hasBits(depthToggle, 0x1))
                GLES20FixedPipeline.glDepthFunc(GLES20FixedPipeline.GL_ALWAYS);
            if(MathUtils.hasBits(depthToggle, 0x2))
                GLES20FixedPipeline.glDepthMask(false);
            if(MathUtils.hasBits(depthToggle, 0x4))
                GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_DEPTH_TEST);
        }

        // render the basemap and layers. this will always include the surface
        // pass and may also include the sprites pass
        if(this.basemap != null)
            this.basemap.draw(this);

        this.renderableDrawPump(pass);

        // if tilt, reset the FBO to the display and render the captured scene
        if(offscreenSurfaceRendering) {
            if (this.debugDrawBounds) {
                final double ullat = this.upperLeft.getLatitude();
                final double ullng = this.upperLeft.getLongitude();
                final double urlat = this.upperRight.getLatitude();
                final double urlng = this.upperRight.getLongitude();
                final double lrlat = this.lowerRight.getLatitude();
                final double lrlng = this.lowerRight.getLongitude();
                final double lllat = this.lowerLeft.getLatitude();
                final double lllng = this.lowerLeft.getLongitude();

                // draw bounding box on offscreen
                ByteBuffer bb = Unsafe.allocateDirect(2 * 4 * 8);
                bb.order(ByteOrder.nativeOrder());

                bb.putFloat((float) ullng);
                bb.putFloat((float) ullat);
                bb.putFloat((float) urlng);
                bb.putFloat((float) urlat);
                bb.putFloat((float) lrlng);
                bb.putFloat((float) lrlat);
                bb.putFloat((float) lllng);
                bb.putFloat((float) lllat);
                bb.putFloat((float) ullng);
                bb.putFloat((float) ullat);
                bb.putFloat((float) lrlng);
                bb.putFloat((float) lrlat);
                bb.putFloat((float) lllng);
                bb.putFloat((float) lllat);
                bb.putFloat((float) urlng);
                bb.putFloat((float) urlat);

                bb.flip();

                this.forward(bb.asFloatBuffer(), bb.asFloatBuffer());

                GLES20FixedPipeline.glColor4f(1f, 0f, 0f, 1f);
                GLES20FixedPipeline.glLineWidth(8f);
                GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
                GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0, bb);
                GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINE_STRIP, 0, bb.limit() / 8);
                GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

                bb.putFloat((float) lrlng);
                bb.putFloat((float) lrlat);
                bb.putFloat((float) drawLng);
                bb.putFloat((float) drawLat);
                bb.putFloat((float) lllng);
                bb.putFloat((float) lllat);


                bb.flip();

                this.forward(bb.asFloatBuffer(), bb.asFloatBuffer());

                GLES20FixedPipeline.glColor4f(0f, 0f, 1f, 1f);
                GLES20FixedPipeline.glLineWidth(8f);
                GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
                GLES20FixedPipeline.glVertexPointer(2, GLES20FixedPipeline.GL_FLOAT, 0, bb);
                GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINE_STRIP, 0, bb.limit() / 8);
                GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

                Unsafe.free(bb);
            }

            if (depthEnabled) {
                GLES20FixedPipeline.glDepthMask(true);
                GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_DEPTH_TEST);
                GLES20FixedPipeline.glDepthFunc(GLES20FixedPipeline.GL_LEQUAL);
            }



            // restore state
            stack.restore(this);

            synchronized (this.offscreen) {
                if(this.refreshTerrain && !lockTiles) {
                    this.terrain.unlock(this.offscreen.terrainTiles);
                    this.offscreen.terrainTiles.clear();
                    this.offscreen.terrainTiles.addAll(terrainTiles);
                    this.offscreen.terrainTilesVersion = terrainTilesVersion;
                    this.refreshTerrain = false;
                }
            }

            GLES20FixedPipeline.glViewport(0, 0, drawSurfaceWidth, drawSurfaceHeight);
            GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_PROJECTION); // select projection                                                                                 // matrix
            GLES20FixedPipeline.glPopMatrix();

            GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_MODELVIEW);
            GLES20FixedPipeline.glPopMatrix();

            // reset FBO to display
            GLES20FixedPipeline.glBindFramebuffer(GLES20FixedPipeline.GL_FRAMEBUFFER,
                    0);
            GLES20FixedPipeline.glClear(GLES20FixedPipeline.GL_COLOR_BUFFER_BIT |
                    GLES20FixedPipeline.GL_DEPTH_BUFFER_BIT |
                    GLES20FixedPipeline.GL_STENCIL_BUFFER_BIT);
/*
            GLES20FixedPipeline.glViewport(0, 0, drawSurfaceWidth, drawSurfaceHeight);
            GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_PROJECTION); // select projection                                                                                 // matrix
            GLES20FixedPipeline.glPushMatrix();
            loadMatrix(scene.camera.projection);

            GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_MODELVIEW);
            GLES20FixedPipeline.glPushMatrix();
            loadMatrix(scene.camera.modelView);
*/

            this.renderableDrawPump(RENDER_PASS_SCENES);
            pass |= GLMapView.RENDER_PASS_SCENES;

            // draw the terrain tiles
            drawTerrainTiles(drawSurfaceWidth, drawSurfaceHeight);
            if(drawTerrainMesh)
                drawTerrainMeshes();
/*
            GLES20FixedPipeline.glViewport(0, 0, drawSurfaceWidth, drawSurfaceHeight);
            GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_PROJECTION); // select projection                                                                                 // matrix
            GLES20FixedPipeline.glPopMatrix();

            GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_MODELVIEW);
            GLES20FixedPipeline.glPopMatrix();
            */
        }

        // execute any remaining passes
        pass ^= (RENDER_PASS_SCENES|RENDER_PASS_SURFACE|RENDER_PASS_SPRITES);
        if(pass != 0)
            this.renderableDrawPump(pass);

        this.renderableDrawPump(GLMapView.RENDER_PASS_UI);
    }

    private void loadMatrix(Matrix m) {
        m.get(internal.matrixD, Matrix.MatrixOrder.COLUMN_MAJOR);
        for(int i = 0; i < 16; i++)
            internal.matrixF[i] = (float)internal.matrixD[i];
        GLES20FixedPipeline.glLoadMatrixf(internal.matrixF, 0);

    }

    private void renderableDrawPump(int pass) {
        for (GLLayer2 l : this.renderables) {
            GLLayer3 r = (GLLayer3)l;
            if((r.getRenderPass()&pass) != 0)
                r.draw(this, pass);
        }
    }

    private void drawTerrainTiles(int drawSurfaceWidth, int drawSurfaceHeight) {
        GLES30.glUseProgram(this.offscreen.program.handle);
        int[] activeTexture = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_ACTIVE_TEXTURE, activeTexture, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, this.drawTerrain ? this.offscreen.texture.getTexId() : 0);

        GLES30.glUniform1i(this.offscreen.program.uTexture, activeTexture[0] - GLES30.GL_TEXTURE0);
        GLES30.glUniform1f(this.offscreen.program.uTexWidth, this.offscreen.texture.getTexWidth());
        GLES30.glUniform1f(this.offscreen.program.uTexHeight, this.offscreen.texture.getTexHeight());

        GLES20FixedPipeline.glGetFloatv(GLES20FixedPipeline.GL_PROJECTION, this.internal.matrixF, 0);
        GLES30.glUniformMatrix4fv(this.offscreen.program.uProjection, 1, false, this.internal.matrixF, 0);

        GLES30.glEnableVertexAttribArray(this.offscreen.program.aVertexCoords);

        if (this.terrainBlendEnabled) {
            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_CONSTANT_ALPHA, GLES30.GL_ONE_MINUS_CONSTANT_ALPHA);
            GLES30.glBlendEquation(GLES30.GL_FUNC_ADD);
            GLES30.glBlendColor(0.0f, 0.0f, 0.0f, (float)this.terrainBlendFactor);
        }

        // draw terrain tiles
        for (TerrainTile tile : offscreen.terrainTiles)
            drawTerrainTile(tile);

        if(this.crossesIDL) {
            State stack = new State();
            stack.save(this);
            // reconstruct the scene model in the secondary hemisphere
            if(idlHelper.getPrimaryHemisphere() == GLAntiMeridianHelper.HEMISPHERE_WEST)
                this.drawLng += 360d;
            else if(idlHelper.getPrimaryHemisphere() == GLAntiMeridianHelper.HEMISPHERE_EAST)
                this.drawLng -= 360d;
            else
                throw new IllegalStateException();
            this.sceneModelVersion = ~this.sceneModelVersion;
            this.validateSceneModel();

            final MapSceneModel ooscene = this.offscreen.scene;
            try {
                this.offscreen.scene = createOffscreenSceneModel(this, drawSurfaceWidth, drawSurfaceHeight);
                for (TerrainTile tile : offscreen.terrainTiles) {
                    drawTerrainTile(tile);
                }
            } finally {
                this.offscreen.scene = ooscene;
            }

            stack.restore(this);
        }

        if (this.terrainBlendEnabled) {
            GLES30.glDisable(GLES30.GL_BLEND);
        }

        GLES30.glDisableVertexAttribArray(this.offscreen.program.aVertexCoords);

        GLES30.glUseProgram(0);
    }

    private void drawTerrainMeshes() {
        GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);

        GLES20FixedPipeline.glColor4f(0.6f, 0.6f, 0.6f, 0.6f);

        int idx = 0;

        // draw terrain tiles
        for(TerrainTile tile : offscreen.terrainTiles) {
            GLES20FixedPipeline.glColor4f(idx%2, (idx%2)+1, 0f, 1f);
            idx++;
            drawTerrainMesh(tile);
        }

        if(this.crossesIDL) {
            State stack = new State();
            stack.save(this);
            // reconstruct the scene model in the secondary hemisphere
            if(idlHelper.getPrimaryHemisphere() == GLAntiMeridianHelper.HEMISPHERE_WEST)
                this.drawLng += 360d;
            else if(idlHelper.getPrimaryHemisphere() == GLAntiMeridianHelper.HEMISPHERE_EAST)
                this.drawLng -= 360d;
            else
                throw new IllegalStateException();
            this.sceneModelVersion = ~this.sceneModelVersion;
            this.validateSceneModel();

            for(TerrainTile tile : offscreen.terrainTiles) {
                GLES20FixedPipeline.glColor4f(idx%2, (idx%2)+1, 0f, 1f);
                idx++;
                drawTerrainMesh(tile);
            }

            stack.restore(this);
        }
        GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
    }

    private void drawTerrainTile(TerrainTile tile) {
        final int drawMode;
        switch(tile.model.getDrawMode()) {
            case Triangles:
                drawMode = GLES20FixedPipeline.GL_TRIANGLES;
                break;
            case TriangleStrip:
                drawMode = GLES20FixedPipeline.GL_TRIANGLE_STRIP;
                break;
            default :
                Log.w("GLMapView", "Undefined terrain model draw mode");
                return;
        }

        // set the local frame
        this.internal.matrix.set(this.scene.forward);
        this.internal.matrix.scale(1d, 1d, this.elevationScaleFactor);
        this.internal.matrix.concatenate(tile.info.localFrame);

        this.internal.matrix.get(this.internal.matrixD, Matrix.MatrixOrder.COLUMN_MAJOR);
        for(int i = 0; i < 16; i++)
            this.internal.matrixF[i] = (float)this.internal.matrixD[i];

        GLES30.glUniformMatrix4fv(this.offscreen.program.uModelView, 1, false, this.internal.matrixF, 0);

        // set the local frame for the offscreen texture
        this.internal.matrix.set(this.offscreen.scene.forward);
        this.internal.matrix.concatenate(tile.info.localFrame);

        float[] f2 = new float[16];
        this.internal.matrix.get(this.internal.matrixD, Matrix.MatrixOrder.COLUMN_MAJOR);
        for(int i = 0; i < 16; i++)
            f2[i] = (float)this.internal.matrixD[i];

        GLES30.glUniformMatrix4fv(this.offscreen.program.uModelViewOffscreen, 1, false, f2, 0);

        if(depthEnabled) {
            GLES20FixedPipeline.glDepthFunc(GLES20FixedPipeline.GL_LEQUAL);
        }
/*
            GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glVertexPointer(3, GLES20FixedPipeline.GL_FLOAT, 0, this.offscreen.vertexCoords);
            GLES20FixedPipeline.glColor4f(1f,  1f, 1f, 1f);
            GLES20FixedPipeline.glDrawElements(GLES20FixedPipeline.GL_LINE_STRIP, this.offscreen.indicesCount, GLES20FixedPipeline.GL_UNSIGNED_SHORT, this.offscreen.indices);
            GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
*/

        final boolean hasWinding = (tile.model.getFaceWindingOrder() != null && tile.model.getFaceWindingOrder() != Model.WindingOrder.Undefined);
        if(hasWinding) {
            GLES20FixedPipeline.glEnable(GLES20FixedPipeline.GL_CULL_FACE);
            switch(tile.model.getFaceWindingOrder()) {
                case Clockwise:
                    GLES20FixedPipeline.glFrontFace(GLES20FixedPipeline.GL_CW);
                    break;
                case CounterClockwise:
                    GLES20FixedPipeline.glFrontFace(GLES20FixedPipeline.GL_CCW);
                    break;
                default :
                    throw new IllegalStateException();
            }
            GLES20FixedPipeline.glCullFace(GLES20FixedPipeline.GL_BACK);
        }

        // render offscreen texture
        VertexDataLayout layout = tile.model.getVertexDataLayout();

        // XXX - VBO
        // XXX - assumes ByteBuffer
        ByteBuffer vertexCoords = (ByteBuffer)tile.model.getVertices(Model.VERTEX_ATTR_POSITION);
        vertexCoords = vertexCoords.duplicate();
        vertexCoords.position(layout.position.offset);

        GLES30.glVertexAttribPointer(offscreen.program.aVertexCoords, 3, GLES30.GL_FLOAT, false, layout.position.stride, vertexCoords);

        if(tile.model.isIndexed()) {
            int numIndices = this.terrainBlendEnabled ? tile.skirtIndexOffset : tile.numIndices;
            GLES30.glDrawElements(drawMode, numIndices, GLES20FixedPipeline.GL_UNSIGNED_SHORT, tile.model.getIndices());
        } else
            GLES30.glDrawArrays(drawMode, 0, tile.model.getNumVertices());

        if(hasWinding)
            GLES20FixedPipeline.glDisable(GLES20FixedPipeline.GL_CULL_FACE);
    }

    private void drawTerrainMesh(TerrainTile tile) {
        final int drawMode;
        switch(tile.model.getDrawMode()) {
            case Triangles:
                drawMode = GLES20FixedPipeline.GL_TRIANGLES;
                break;
            case TriangleStrip:
                drawMode = GLES20FixedPipeline.GL_TRIANGLE_STRIP;
                break;
            default :
                Log.w("GLMapView", "Undefined terrain model draw mode");
                return;
        }
/*
        float[] hsv = new float[3];
        hsv[0] = (1.0f - (float) OSMUtils.mapnikTileLevel(tile.info.minDisplayResolution) / (float) (18))
                * 255f;
        hsv[1] = 0.5f;
        hsv[2] = 0.5f;

        int rgb = Color.HSVToColor(255, hsv);

        GLES20FixedPipeline.glColor4f(Color.red(rgb)/255f, Color.green(rgb)/255f, Color.blue(rgb)/255f, 1f);
*/
        GLES20FixedPipeline.glPushMatrix();

        // set the local frame
        this.internal.matrix.set(this.scene.forward);
        this.internal.matrix.scale(1d, 1d, this.elevationScaleFactor);
        this.internal.matrix.concatenate(tile.info.localFrame);

        for(int i = 0; i < 16; i++)
            this.internal.matrixF[i] = (float)this.internal.matrix.get(i%4, i/4);

        GLES20FixedPipeline.glLoadMatrixf(this.internal.matrixF, 0);

        // render offscreen texture
        VertexDataLayout layout = tile.model.getVertexDataLayout();

        // XXX - VBO
        // XXX - assumes ByteBuffer
        ByteBuffer vertexCoords = (ByteBuffer)tile.model.getVertices(Model.VERTEX_ATTR_POSITION);
        vertexCoords = vertexCoords.duplicate();
        vertexCoords.position(layout.position.offset);

        GLES20FixedPipeline.glVertexPointer(3, GLES20FixedPipeline.GL_FLOAT, layout.position.stride, vertexCoords);

        Buffer indices = null;
        try {
            if (tile.model.isIndexed()) {
                Buffer bindices = tile.model.getIndices();
                if(bindices instanceof ByteBuffer) {
                    ((ByteBuffer)bindices).order(ByteOrder.nativeOrder());
                    bindices = ((ByteBuffer) bindices).asShortBuffer();
                    bindices.limit(Models.getNumIndices(tile.model));
                }
                indices = GLWireFrame.deriveIndices((ShortBuffer) bindices, drawMode, Models.getNumIndices(tile.model), GLES20FixedPipeline.GL_UNSIGNED_SHORT);
            } else {
                indices = GLWireFrame.deriveIndices(drawMode, tile.model.getNumVertices(), GLES20FixedPipeline.GL_UNSIGNED_SHORT);
            }

            GLES20FixedPipeline.glDrawElements(GLES20FixedPipeline.GL_LINES, indices.limit(), GLES20FixedPipeline.GL_UNSIGNED_SHORT, indices);
        } finally {
            if(indices != null)
                Unsafe.free(indices);
        }

        GLES20FixedPipeline.glPopMatrix();
    }

    private boolean initOffscreenRendering(int drawSurfaceWidth, int drawSurfaceHeight) {
        if(Double.isNaN(offscreen.hfactor))
            offscreen.hfactor = ConfigOptions.getOption("glmapview.offscreen.hfactor", 3.5d);

        float wfactor = 1.25f;
        float hfactor = (float)offscreen.hfactor;

        int[] ivalue = new int[1];
        GLES20FixedPipeline.glGetIntegerv(GLES20FixedPipeline.GL_MAX_TEXTURE_SIZE, ivalue, 0);

        final int offscreenTextureWidth = Math.min((int)((float)drawSurfaceWidth*wfactor), ivalue[0]);
        final int offscreenTextureHeight = Math.min((int)((float)drawSurfaceHeight*hfactor), ivalue[0]);

        final int textureSize = Math.max(offscreenTextureWidth, offscreenTextureHeight);

        // Using 565 without any alpha to avoid alpha drawing overlays from becoming darker
        // when the map is tilted. Alternatively glBlendFuncSeparate could be used for all
        // glBlendFunc calls where srcAlpha is set to 0 and dstAlpha is 1
        this.offscreen.texture = new GLTexture(textureSize, textureSize, Bitmap.Config.RGB_565);
        this.offscreen.texture.init();

        this.offscreen.fbo = new int[2];

        boolean fboCreated = false;
        do {
            if (this.offscreen.fbo[0] == 0)
                GLES20FixedPipeline.glGenFramebuffers(1, this.offscreen.fbo, 0);

            if (this.offscreen.fbo[1] == 0)
                GLES20FixedPipeline.glGenRenderbuffers(1, this.offscreen.fbo, 1);

            GLES20FixedPipeline.glBindRenderbuffer(GLES20FixedPipeline.GL_RENDERBUFFER,
                    this.offscreen.fbo[1]);
            GLES20FixedPipeline.glRenderbufferStorage(GLES20FixedPipeline.GL_RENDERBUFFER,
                    GLES20FixedPipeline.GL_DEPTH_COMPONENT16, this.offscreen.texture.getTexWidth(),
                    this.offscreen.texture.getTexHeight());
            GLES20FixedPipeline.glBindRenderbuffer(GLES20FixedPipeline.GL_RENDERBUFFER, 0);

            GLES20FixedPipeline.glBindFramebuffer(GLES20FixedPipeline.GL_FRAMEBUFFER,
                    this.offscreen.fbo[0]);

            // clear any pending errors
            while(GLES20FixedPipeline.glGetError() != GLES20FixedPipeline.GL_NO_ERROR)
                ;
            GLES20FixedPipeline.glFramebufferTexture2D(GLES20FixedPipeline.GL_FRAMEBUFFER,
                    GLES20FixedPipeline.GL_COLOR_ATTACHMENT0,
                    GLES20FixedPipeline.GL_TEXTURE_2D, this.offscreen.texture.getTexId(), 0);

            // XXX - observing hard crash following bind of "complete"
            //       FBO on SM-T230NU. reported error is 1280 (invalid
            //       enum) on glFramebufferTexture2D. I have tried using
            //       the color-renderable formats required by GLES 2.0
            //       (RGBA4, RGB5_A1, RGB565) but all seem to produce
            //       the same outcome.
            if(GLES20FixedPipeline.glGetError() != GLES20FixedPipeline.GL_NO_ERROR)
                break;

            GLES20FixedPipeline.glFramebufferRenderbuffer(GLES20FixedPipeline.GL_FRAMEBUFFER,
                    GLES20FixedPipeline.GL_DEPTH_ATTACHMENT, GLES20FixedPipeline.GL_RENDERBUFFER,
                    this.offscreen.fbo[1]);
            final int fboStatus = GLES20FixedPipeline.glCheckFramebufferStatus(GLES20FixedPipeline.GL_FRAMEBUFFER);
            fboCreated = (fboStatus == GLES20FixedPipeline.GL_FRAMEBUFFER_COMPLETE);
        } while(false);

        int vertShader = GLES20FixedPipeline.GL_NONE;
        int fragShader = GLES20FixedPipeline.GL_NONE;
        try {
            vertShader = GLES20FixedPipeline.loadShader(GLES20FixedPipeline.GL_VERTEX_SHADER, OFFSCREEN_VERT_SHADER_SRC);
            fragShader = GLES20FixedPipeline.loadShader(GLES20FixedPipeline.GL_FRAGMENT_SHADER, OFFSCREEN_FRAG_SHADER_SRC);

            offscreen.program.handle = GLES20FixedPipeline.createProgram(vertShader, fragShader);
            offscreen.program.aVertexCoords = GLES20FixedPipeline.glGetAttribLocation(offscreen.program.handle, "aVertexCoords");
            offscreen.program.uProjection = GLES20FixedPipeline.glGetUniformLocation(offscreen.program.handle, "uProjection");
            offscreen.program.uModelView = GLES20FixedPipeline.glGetUniformLocation(offscreen.program.handle, "uModelView");
            offscreen.program.uModelViewOffscreen = GLES20FixedPipeline.glGetUniformLocation(offscreen.program.handle, "uModelViewOffscreen");
            offscreen.program.uTexWidth = GLES20FixedPipeline.glGetUniformLocation(offscreen.program.handle, "uTexWidth");
            offscreen.program.uTexHeight = GLES20FixedPipeline.glGetUniformLocation(offscreen.program.handle, "uTexHeight");
            offscreen.program.uTexture = GLES20FixedPipeline.glGetUniformLocation(offscreen.program.handle, "uTexture");
        } finally {
            if(vertShader != GLES20FixedPipeline.GL_NONE)
                GLES20FixedPipeline.glDeleteShader(vertShader);
            if(fragShader != GLES20FixedPipeline.GL_NONE)
                GLES20FixedPipeline.glDeleteShader(fragShader);
        }
        return fboCreated;
    }

    public int getLeft() {
        return _left;
    }

    public int getRight() {
        if (_right == MATCH_SURFACE) {
            _right = _surface.getWidth() - 1;
        }
        return _right;
    }

    public int getTop() {
        if (_top == MATCH_SURFACE) {
            _top = _surface.getHeight() - 1;
        }
        return _top;
    }

    public int getBottom() {
        return _bottom;
    }

    /** @deprecated */
    public double getLegacyScale() {
        final AtakMapView mapView = _surface.getMapView();
        return ((drawMapScale * mapView.fullEquitorialExtentPixels) / 360d);
    }

    /**
     * Updates {@link #westBound}, {@link #eastBound}, {@link #southBound} and {@link #northBound}.
     * Invoked during {@link #render()}.
     */
    protected void updateBounds() {
        // corners
        this.internal.pointF.x = _left;
        this.internal.pointF.y = _top;
        this.scene.inverse(this.internal.pointF,   this.upperLeft, true);

        this.internal.pointF.x = _right;
        this.internal.pointF.y = _top;
        this.scene.inverse(this.internal.pointF,   this.upperRight, true);

        this.internal.pointF.x = _right;
        this.internal.pointF.y = _bottom;
        this.scene.inverse(this.internal.pointF,   this.lowerRight, true);

        this.internal.pointF.x = _left;
        this.internal.pointF.y = _bottom;
        this.scene.inverse(this.internal.pointF,   this.lowerLeft, true);

        northBound = MathUtils.max(this.upperLeft.getLatitude(), this.upperRight.getLatitude(), this.lowerRight.getLatitude(), this.lowerLeft.getLatitude());
        southBound = MathUtils.min(this.upperLeft.getLatitude(), this.upperRight.getLatitude(), this.lowerRight.getLatitude(), this.lowerLeft.getLatitude());
        eastBound = eastBoundUnwrapped = MathUtils.max(this.upperLeft.getLongitude(), this.upperRight.getLongitude(), this.lowerRight.getLongitude(), this.lowerLeft.getLongitude());
        westBound = westBoundUnwrapped = MathUtils.min(this.upperLeft.getLongitude(), this.upperRight.getLongitude(), this.lowerRight.getLongitude(), this.lowerLeft.getLongitude());
        crossesIDL = continuousScrollEnabled && (eastBound > 180d && westBound < 180d
                || westBound < -180d && eastBound > -180d);

        if(this.scene.mapProjection.is3D()) {
            this.UpdateLatLonAABBoxEllipsoid();

            wrapCorner(this.upperLeft);
            wrapCorner(this.upperRight);
            wrapCorner(this.lowerRight);
            wrapCorner(this.lowerLeft);

            westBound = GeoCalculations.wrapLongitude(westBound);
            eastBound = GeoCalculations.wrapLongitude(eastBound);
        } else if(continuousScrollEnabled) {
            wrapCorner(this.upperLeft);
            wrapCorner(this.upperRight);
            wrapCorner(this.lowerRight);
            wrapCorner(this.lowerLeft);

            westBound = GeoCalculations.wrapLongitude(westBound);
            eastBound = GeoCalculations.wrapLongitude(eastBound);
        }

        this.idlHelper.update(this);
    }

    private static void wrapCorner(GeoPoint p) {
        GeoPoint g = p;
        if(g.getLongitude() > 180d)
            g.set(g.getLatitude(), g.getLongitude()-360d);
        else if(g.getLongitude() < -180d)
            g.set(g.getLatitude(), g.getLongitude()+360d);
    }
    private static double distanceSquared(PointD a, PointD b) {
        final double dx = a.x-b.x;
        final double dy = a.y-b.y;
        final double dz = a.z-b.z;
        return dx*dx + dy*dy + dz+dz;
    }
    private void UpdateLatLonAABBoxEllipsoid()
    {
        int w=(_right-_left), hw=w>>1, h=(_top-_bottom), hh=h>>1;
        double north=-90, south=90, east=-180, west=180;

        GeoPoint[] points = new GeoPoint[8];
        int idx = 0;
        //GetEllipsoidPosition(0,0,true),
        this.internal.pointF.x = _left;
        this.internal.pointF.y = _bottom+h;
        points[idx] = this.scene.inverse(this.internal.pointF, null, false);
        if(points[idx] != null)
            idx++;
        //GetEllipsoidPosition((float)hw,0,true),
        this.internal.pointF.x = _left+hw;
        this.internal.pointF.y = _bottom+h;
        points[idx] = this.scene.inverse(this.internal.pointF, null, false);
        if(points[idx] != null)
            idx++;
        //GetEllipsoidPosition( (float)w,0,true),
        this.internal.pointF.x = _left+w;
        this.internal.pointF.y = _bottom+h;
        points[idx] = this.scene.inverse(this.internal.pointF, null, false);
        if(points[idx] != null)
            idx++;
        //GetEllipsoidPosition(0,(float)hh,true),
        this.internal.pointF.x = _left+w;
        this.internal.pointF.y = _bottom+hh;
        points[idx] = this.scene.inverse(this.internal.pointF, null, false);
        if(points[idx] != null)
            idx++;
        //GetEllipsoidPosition((float)w,(float)hh,true),
        this.internal.pointF.x = _left+w;
        this.internal.pointF.y = _bottom;
        points[idx] = this.scene.inverse(this.internal.pointF, null, false);
        if(points[idx] != null)
            idx++;
        //GetEllipsoidPosition( 0,(float)h,true),
        this.internal.pointF.x = _left+hw;
        this.internal.pointF.y = _bottom;
        points[idx] = this.scene.inverse(this.internal.pointF, null, false);
        if(points[idx] != null)
            idx++;
        //GetEllipsoidPosition((float)hw,(float)h,true),
        this.internal.pointF.x = _left;
        this.internal.pointF.y = _bottom;
        points[idx] = this.scene.inverse(this.internal.pointF, null, false);
        if(points[idx] != null)
            idx++;
        //GetEllipsoidPosition( (float)w,(float)h,true)
        this.internal.pointF.x = _left;
        this.internal.pointF.y = _bottom+hh;
        points[idx] = this.scene.inverse(this.internal.pointF, null, false);
        if(points[idx] != null)
            idx++;


        //Mapping::Coords::LatLon ^center=Mapping::Coords::LatLon::FromECEF(_posTarget);
        this.internal.geo.set(this.drawLat, this.drawLng);

        boolean horizonInView = (idx < points.length);
        boolean poleInView = (idx == 0 && drawLat != 0d);
        if(idx > 0) {
            north = points[0].getLatitude();
            south = points[0].getLatitude();
            double furthestDsq = distanceSquared(scene.camera.location, scene.mapProjection.forward(points[0], internal.pointD));
            for(int i = 1; i < idx; i++) {
                final double lat = points[i].getLatitude();
                if(lat > north)
                    north = lat;
                else if(lat < south)
                    south = lat;

                double dsq = distanceSquared(scene.camera.location, scene.mapProjection.forward(points[i], internal.pointD));
                if(dsq > furthestDsq)
                    furthestDsq = dsq;
            }

            poleInView = (this.drawLat < south || this.drawLat > north) ||
                    distanceSquared(scene.camera.location, scene.mapProjection.forward(internal.geo.set(90d, 0d), internal.pointD)) < furthestDsq ||
                    distanceSquared(scene.camera.location, scene.mapProjection.forward(internal.geo.set(-90d, 0d), internal.pointD)) < furthestDsq;
        }
        // if only two intersection points are detected, the full globe is in
        // view
        if(idx == 2 && !poleInView) {
            north = 90d;
            south = -90d;
        }

        horizonInView |= poleInView;

        if(poleInView) {
            // complete globe is in view, wrap 180 degrees
            if(idx == 0) {
                north = Math.min(this.drawLat+90d, 90d);
                south = Math.max(this.drawLat-90d, -90d);
            }

            if (this.drawLat > 0d) {
                north = 90d;
                if(idx < 8 && this.drawTilt == 0d)
                    south = this.drawLat-90d;
            } else if (this.drawLat < 0d) {
                south = -90d;
                if(idx < 8 && this.drawTilt == 0d)
                    north = this.drawLat+90d;
            } else {
                // XXX -
            }

            // if pole is in view, we need to wrap 360 degrees
            east = GeoCalculations.wrapLongitude(this.drawLng + 180d);
            west = GeoCalculations.wrapLongitude(this.drawLng - 180d);
            this.crossesIDL = this.drawLng != 0d;

            this.upperLeft.set(north, GeoCalculations.wrapLongitude(west));
            this.upperRight.set(north, GeoCalculations.wrapLongitude(east));
            this.lowerRight.set(south, GeoCalculations.wrapLongitude(east));
            this.lowerLeft.set(south, GeoCalculations.wrapLongitude(west));
        } else if (horizonInView) {
            east = GeoCalculations.wrapLongitude(this.drawLng + 90d);
            west = GeoCalculations.wrapLongitude(this.drawLng - 90d);
            this.crossesIDL = Math.abs(this.drawLng) > 90d;

            this.upperLeft.set(north, GeoCalculations.wrapLongitude(west));
            this.upperRight.set(north, GeoCalculations.wrapLongitude(east));
            this.lowerRight.set(south, GeoCalculations.wrapLongitude(east));
            this.lowerLeft.set(south, GeoCalculations.wrapLongitude(west));
        } else {
            LineString ring = new LineString(2);
            for(int i = 0; i <= idx; i++)
                ring.addPoint(points[i%idx].getLongitude(), points[i%idx].getLatitude());

            // derived from http://stackoverflow.com/questions/1165647/how-to-determine-if-a-list-of-polygon-points-are-in-clockwise-order
            // compute winding order to determine IDL crossing
            double sum = 0.0;
            final int count = ring.getNumPoints();
            for (int i = 0; i < (count - 1); i++) {
                double dx = ring.getX(i + 1) - ring.getX(i);
                double dy = ring.getY(i + 1) + ring.getY(i);
                sum += dx * dy;
            }
            Envelope mbb = ring.getEnvelope();
            north = mbb.maxY;
            south = mbb.minY;

            if (sum >= 0) {
                crossesIDL = false;
/*
                // XXX - check if any non-consecutive segments intersect eachother
                for(int i = 0; i < count; i++) {
                    double Ax0 = ring.getX(i);
                    double Ay0 = ring.getY(i);
                    double Ax1 = ring.getX((i+1)%count);
                    double Ay1 = ring.getY((i+1)%count);

                    for(int j = 0; j < count; j++) {
                        if(j == i)
                            continue;
                        if(((j+1)%count) == i)
                            continue;
                        if(j == ((i+1)%count))
                            continue;
                        if(((j+1)%count) == ((i+1)%count))
                            continue;

                        double Bx0 = ring.getX(i);
                        double By0 = ring.getY(i);
                        double Bx1 = ring.getX(i%count);
                        double By1 = ring.getY(i%count);

                        crossesIDL |= MathUtils.intersects(Ax0, Ay0, Ax1, Ay1, Bx0, By0, Bx1, By1);
                        if(crossesIDL)
                            break;
                    }
                    if(crossesIDL)
                        break;
                }
                */
            } else {
                // winding order indicates crossing
                crossesIDL = true;
            }

            if(!crossesIDL) {
                west = mbb.minX;
                east = mbb.maxX;
            } else {
                // crossing IDL
                west = 180;
                east = -180;
                double lng;
                for(int i = 0; i < idx; i++) {
                    lng = points[i].getLongitude();
                    if(lng > 0 && lng < west)
                        west = lng;
                    else if(lng < 0 && lng > east)
                        east = lng;
                }
            }
        }

        this.northBound = north;
        this.westBound = west;
        this.southBound = south;
        this.eastBound = east;

        //System.out.println("GLMAPVIEW BOUNDS idl=" + crossesIDL + " north=" + northBound + " west=" + westBound + " south=" + southBound + " east=" + eastBound);
    }

    /**************************************************************************/
    // On Map Moved Listener

    @Override
    public void onMapMoved (final AtakMapView view,
                            final boolean animate) {

        this.mapMovedUpdater.postUpdate(view, animate);
    }

    /**************************************************************************/
    // On Map Projection Changed Listener

    @Override
    public void onMapProjectionChanged(AtakMapView view) {
        final int srid = view.getProjection().getSpatialReferenceID();
        _surface.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (GLMapView.this.drawSrid != srid) {
                    GLMapView.this.drawSrid = srid;
                    GLMapView.this.drawProjection = ProjectionFactory.getProjection(GLMapView.this.drawSrid);
                    GLMapView.this.drawVersion++;
                    GLMapView.this.refreshTerrain = true;

                    if (GLMapView.this.coordTransformPtr != 0L) {
                        OsrUtils.destroyProjection(GLMapView.this.coordTransformPtr);
                        GLMapView.this.coordTransformPtr = 0L;
                    }

                    GLMapView.this.coordTransformPtr = OsrUtils
                            .createProjection(GLMapView.this.drawSrid);
                }
            }
        });
    }

    /**************************************************************************/
    // OnContinuousScrollEnabledChangedListener

    @Override
    public void onContinuousScrollEnabledChanged(AtakMapView mapView, final boolean enabled) {
        _surface.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (GLMapView.this.continuousScrollEnabled != enabled) {
                    GLMapView.this.continuousScrollEnabled = enabled;
                    GLMapView.this.drawVersion++;
                }
            }
        });
    }

    /**************************************************************************/
    // OnElevationExaggerationFactorChangedListener

    @Override
    public void onTerrainExaggerationFactorChanged(AtakMapView mapView, final double factor) {
        _surface.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (GLMapView.this.elevationScaleFactor != factor) {
                    GLMapView.this.elevationScaleFactor = factor;
                    GLMapView.this.drawVersion++;
                }
            }
        });
    }

    /**************************************************************************/
    // On Layers Changed Listener

    @Override
    public void onLayerAdded(AtakMapView mapView, Layer layer) {
        if(this.layerRenderers.containsKey(layer)) {
            Log.w("GLMapView", "GLMapView already contains renderer for " + layer.getName());
        } else {
            final GLLayer3 renderer = GLLayerFactory.create4(this, layer);
            if(renderer != null) {
                this.layerRenderers.put(layer, renderer);
                // start the renderer
                renderer.start();
            }
        }

        this.refreshLayers2(mapView.getLayers());
    }

    @Override
    public void onLayerRemoved(AtakMapView mapView, Layer layer) {
        final GLLayer2 renderer = this.layerRenderers.remove(layer);
        // if the layer had a renderer, stop it to discontinue access
        if(renderer != null) {
            renderer.stop();
            this.refreshLayers2(mapView.getLayers());
        }
    }

    @Override
    public void onLayerPositionChanged(AtakMapView mapView, Layer layer, int oldPosition,
                                       int newPosition) {

        this.refreshLayers2(mapView.getLayers());
    }

    /**
     * <P>This method should only be invoked from the layers changed callback
     * thread.
     *
     * @param layers    The map layers, in display order
     */
    protected void refreshLayers2(final List<Layer> layers) {
        if(GLMapSurface.isGLThread()) {
            this.refreshLayersImpl2(layers, this.layerRenderers);
        } else {
            final Map<Layer, GLLayer2> renderers = new IdentityHashMap<Layer, GLLayer2>(this.layerRenderers);
            _surface.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLMapView.this.refreshLayersImpl2(layers, renderers);
                }
            });
        }
    }

    /**
     * <P><B>MUST BE INVOKED ON GL THREAD!</B>
     *
     * @param layers        The map layers, in display order
     * @params renderers    The
     */
    protected void refreshLayersImpl2(List<Layer> layers, Map<Layer, GLLayer2> renderers) {
        // if renderables is null, then completely exit from this method
        if (this.renderables == null)
            return;

        this.renderables.clear();

        GLLayer2 glLayer;
        for(Layer layer : layers) {
            glLayer = renderers.get(layer);
            if(glLayer != null)
                this.renderables.add(glLayer);
        }
    }

    /**************************************************************************/
    // On FocusPoint Changed Listener

    @Override
    public void onFocusPointChanged (final float x,
                                     final float y) {
        _surface.queueEvent (new Runnable () {
            @Override
            public void run () {
                animator.startAnimatingFocus (x, y, 0.3);
            }
        });
    }

    /**************************************************************************/

    private static boolean _isTiny(double v) {
        return Math.abs(v) <= _EPSILON;
    }

    private static boolean _isTinyF(float v) {
        return Math.abs(v) <= _EPSILON_F;
    }

    private static boolean _hasSettled(double dlat, double dlng, double dscale,
                                       double drot, double dtilt, float dfocusX, float dfocusY) {
        return _isTiny(dlat) && _isTiny(dlng) && _isTiny(dscale)
                && _isTiny(drot) && _isTiny(dtilt) && _isTinyF(dfocusX) && _isTinyF(dfocusY);
    }

    /**************************************************************************/

    protected final void validateSceneModel() {
        AtakMapView mapView = _surface.getMapView();
        this.validateSceneModelImpl(mapView.getWidth(), mapView.getHeight());
    }

    protected final void validateSceneModelImpl(int width, int height) {
        if (this.sceneModelVersion != this.drawVersion) {
            final int vflipHeight = height;
            if (this.verticalFlipTranslate == null
                    || vflipHeight != this.verticalFlipTranslateHeight) {
                this.verticalFlipTranslate = Matrix
                        .getTranslateInstance(0, vflipHeight);
                this.verticalFlipTranslateHeight = vflipHeight;
            }

            this.internal.geo.set(this.drawLat, this.drawLng, 0d);

            if(this.drawProjection == null || this.drawProjection.getSpatialReferenceID() != this.drawSrid)
                this.drawProjection = ProjectionFactory.getProjection(this.drawSrid);

            this.scene = new MapSceneModel(AtakMapView.DENSITY*240f,
                    width, height,
                    this.drawProjection,
                    this.internal.geo,
                    this.focusx, this.focusy,
                    this.drawRotation, this.drawTilt,
                    this.drawMapResolution,
                    this.continuousScrollEnabled);

            // account for flipping of y-axis for OpenGL coordinate space
            this.scene.inverse.concatenate(GLMapView.this.verticalFlipTranslate);
            this.scene.inverse.concatenate(XFORM_VERTICAL_FLIP_SCALE);

            this.scene.forward.preConcatenate(XFORM_VERTICAL_FLIP_SCALE);
            this.scene.forward.preConcatenate(GLMapView.this.verticalFlipTranslate);

            this.refreshSceneMatrices();

            // mark as valid
            this.sceneModelVersion = this.drawVersion;
        }
    }

    private void refreshSceneMatrices() {
        // fill the inverse matrix for bulk transforms
        this.scene.inverse.get(this.internal.matrixD, Matrix.MatrixOrder.ROW_MAJOR);
        this.bulkInverseMatrix.clear();
        this.bulkInverseMatrix.put(this.internal.matrixD);
        this.bulkInverseMatrix.flip();

        // fill the forward matrix for bulk transforms
        this.scene.forward.get(this.internal.matrixD, Matrix.MatrixOrder.ROW_MAJOR);
        this.bulkForwardMatrix.clear();
        this.bulkForwardMatrix.put(this.internal.matrixD);
        this.bulkForwardMatrix.flip();

        // XXX -
        this.bulkForwardMatrix3x3.put(0, this.bulkForwardMatrix.get(0));
        this.bulkForwardMatrix3x3.put(1, this.bulkForwardMatrix.get(1));
        this.bulkForwardMatrix3x3.put(2, this.bulkForwardMatrix.get(3));
        this.bulkForwardMatrix3x3.put(3, this.bulkForwardMatrix.get(4));
        this.bulkForwardMatrix3x3.put(4, this.bulkForwardMatrix.get(5));
        this.bulkForwardMatrix3x3.put(5, this.bulkForwardMatrix.get(7));
        this.bulkForwardMatrix3x3.put(6, this.bulkForwardMatrix.get(12));
        this.bulkForwardMatrix3x3.put(7, this.bulkForwardMatrix.get(13));
        this.bulkForwardMatrix3x3.put(8, this.bulkForwardMatrix.get(15));

        this.bulkInverseMatrix3x3.put(0, this.bulkInverseMatrix.get(0));
        this.bulkInverseMatrix3x3.put(1, this.bulkInverseMatrix.get(1));
        this.bulkInverseMatrix3x3.put(2, this.bulkInverseMatrix.get(3));
        this.bulkInverseMatrix3x3.put(3, this.bulkInverseMatrix.get(4));
        this.bulkInverseMatrix3x3.put(4, this.bulkInverseMatrix.get(5));
        this.bulkInverseMatrix3x3.put(5, this.bulkInverseMatrix.get(7));
        this.bulkInverseMatrix3x3.put(6, this.bulkInverseMatrix.get(12));
        this.bulkInverseMatrix3x3.put(7, this.bulkInverseMatrix.get(13));
        this.bulkInverseMatrix3x3.put(8, this.bulkInverseMatrix.get(15));

        // fill the forward matrix for the Model-View
        this.scene.forward.get(this.internal.matrixD, Matrix.MatrixOrder.COLUMN_MAJOR);
        for(int i = 0; i < 16; i++)
            this.sceneModelForwardMatrix[i] = (float)this.internal.matrixD[i];
    }

    protected class Animator {
        private double _drawMapScale = 2.5352504279048383E-9d;
        private double _drawLat = 0d;
        private double _drawLng = 0d;
        private double _drawRotation = 0d;
        private double _drawTilt = 0d;
        private double _animationFactor = 0.3d;

        private double _targetMapScale = 2.5352504279048383E-9d;
        private double _targetLat = 0d;
        private double _targetLng = 0d;
        private double _targetRotation = 0d;
        private double _targetTilt = 0d;

        private float _targetFocusx = 0f;
        private float _targetFocusy = 0f;

        private float _focusx, _focusy;

        private boolean _isSettled;

        private boolean viewFieldsValid = false;

        private OnAnimationSettledCallback _animCallback;

        /** @deprecated use {@link #startAnimating(double, double, double, double, double, double)} */
        public final void startAnimating(double lat, double lng, double scale,
                                         double rotation, double animateFactor) {
            this.startAnimating(lat, lng, scale, rotation, _targetTilt, animateFactor);
        }

        /**
         * Start animating to a target Latitude, Longitude, and zoom level
         *
         * @param lat target latitude
         * @param lng target longitude
         * @param scale target map scale
         * @param animateFactor (0d, 1d] smoothness factor of snapping animation effect (0.3d is a
         *            good default)
         */
        public void startAnimating(double lat, double lng, double scale,
                                   double rotation, double tilt, double animateFactor) {
            _targetLat = lat;
            _targetLng = lng;
            _targetMapScale = scale;
            _targetRotation = rotation;
            _targetTilt = tilt;
            _animationFactor = animateFactor;
            _isSettled = false;
        }

        public void startAnimatingFocus(float x, float y, double animateFactor) {
            _targetFocusx = x;
            _targetFocusy = y;
            _animationFactor = animateFactor;
            _isSettled = false;
        }

        /**
         * Synchronizes the various view state fields of the enclosing
         * {@link GLMapView} with the current animation values. The enclosing
         * view's {@link GLMapView#drawVersion} will be bumped as a result of
         * the invocation of this method if any of the fields have changed.
         */
        public void updateView() {
            if (this.viewFieldsValid)
                return;

            GLMapView.this.animationFactor = _animationFactor;
            GLMapView.this.drawLat = _drawLat;
            GLMapView.this.drawLng = _drawLng;
            GLMapView.this.drawRotation = _drawRotation;
            GLMapView.this.drawTilt = _drawTilt;
            GLMapView.this.drawMapScale = _drawMapScale;
            GLMapView.this.drawMapResolution = GLMapView.this._surface.getMapView().getMapResolution(_drawMapScale);

            GLMapView.this.focusx = _focusx;
            GLMapView.this.focusy = _focusy;

            GLMapView.this.settled = _isSettled;

            GLMapView.this.drawVersion++;

            GLMapView.this.refreshTerrain = true;

            this.viewFieldsValid = true;
        }

        public void animate() {
            if (_isSettled)
                return;

            double scaleDelta = (_targetMapScale - _drawMapScale);
            double latDelta = (_targetLat - _drawLat);
            double lngDelta = (_targetLng - _drawLng);
            float focusxDelta = (_targetFocusx - _focusx);
            float focusyDelta = (_targetFocusy - _focusy);

            // Go the other way if continuous scrolling is enabled
            if (GLMapView.this.continuousScrollEnabled
                    && Math.abs(lngDelta) > 180d) {
                if (lngDelta < 0d)
                    lngDelta += 360d;
                else
                    lngDelta -= 360d;
            }

            _drawMapScale += scaleDelta * _animationFactor;
            _drawLat += latDelta * _animationFactor;
            _drawLng += lngDelta * _animationFactor;
            _focusx += focusxDelta * _animationFactor;
            _focusy += focusyDelta * _animationFactor;

            double rotDelta = (_targetRotation - _drawRotation);

            // Go the other way
            if (Math.abs(rotDelta) > 180d) {
                if (rotDelta < 0d) {
                    _drawRotation -= 360d;
                } else {
                    _drawRotation += 360d;
                }
                rotDelta = (_targetRotation - _drawRotation);
            }

            _drawRotation += rotDelta * _animationFactor;

            double tiltDelta = (_targetTilt - _drawTilt);

            _drawTilt += tiltDelta * _animationFactor;

            if (!_isSettled) {
                _isSettled = _hasSettled(latDelta, lngDelta, scaleDelta, rotDelta,
                        tiltDelta,
                        focusxDelta, focusyDelta);
                if (_isSettled) {
                    _drawMapScale = _targetMapScale;
                    _drawLat = _targetLat;
                    _drawLng = _targetLng;
                    _drawRotation = _targetRotation;
                    _drawTilt = _targetTilt;
                    _focusx = _targetFocusx;
                    _focusy = _targetFocusy;
                    if (_animCallback != null) {
                        _animCallback.onAnimationSettled();
                    }
                } else if(!isContinuousRenderEnabled()){
                    // if not continuous rendering, continue to request render
                    // until settled
                    _surface.requestRender();
                }
            }

            this.viewFieldsValid = false;
        }
    }

    public static interface OnAnimationSettledCallback {
        public void onAnimationSettled();
    }

    public final static class ScratchPad {
        public final PointF pointF = new PointF();
        public final PointD pointD = new PointD(0, 0);
        public final RectF rectF = new RectF();
        public final GeoPoint geo = GeoPoint.createMutable();
        public final double[] matrixD = new double[16];
        public final float[] matrixF = new float[16];
        public final Matrix matrix = Matrix.getIdentity();

        private ScratchPad() {}
    }

    private Collection<MapControl> getMapViewControls() {
        if (this.mapViewControls == null) {
            this.mapViewControls = Collections.newSetFromMap(new IdentityHashMap<MapControl, Boolean>());
        }
        return this.mapViewControls;
    }

    @Override
    public synchronized void registerControl(Layer2 layer, MapControl ctrl) {
        Collection<MapControl> ctrls = this.getMapViewControls();
        if (layer != null) {
            ctrls = this.controls.get(layer);
            if (ctrls == null)
                this.controls.put(layer, ctrls = Collections.newSetFromMap(new IdentityHashMap<MapControl, Boolean>()));
        }
        if(ctrls.add(ctrl)) {
            for(OnControlsChangedListener l : this.controlsListeners)
                l.onControlRegistered(layer, ctrl);
        }
    }

    @Override
    public synchronized void unregisterControl(Layer2 layer, MapControl ctrl) {
        Collection<MapControl> ctrls = layer == null ? this.mapViewControls : this.controls.get(layer);
        if(ctrls != null) {
            if(ctrls.remove(ctrl)) {
                for(OnControlsChangedListener l : this.controlsListeners)
                    l.onControlUnregistered(layer, ctrl);
            }
        }
    }

    @Override
    public synchronized <T extends MapControl> boolean visitControl(Layer2 layer, Visitor<T> visitor, Class<T> ctrlClazz) {
        Collection<MapControl> ctrls = layer == null ? this.mapViewControls : this.controls.get(layer);
        if(ctrls == null)
            return false;

        for(MapControl ctrl : ctrls) {
            if(ctrlClazz.isAssignableFrom(ctrl.getClass())) {
                visitor.visit(ctrlClazz.cast(ctrl));
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized boolean visitControls(Layer2 layer, Visitor<Iterator<MapControl>> visitor) {
        Collection<MapControl> ctrls = layer == null ? this.mapViewControls : this.controls.get(layer);
        if(ctrls == null)
            return false;
        visitor.visit(ctrls.iterator());
        return true;
    }

    @Override
    public synchronized void visitControls(Visitor<Iterator<Map.Entry<Layer2, Collection<MapControl>>>> visitor) {
        visitor.visit(this.controls.entrySet().iterator());
    }

    @Override
    public boolean isRenderThread() {
        return GLMapSurface.isGLThread();
    }

    @Override
    public void queueEvent(Runnable r) {
        _surface.queueEvent(r);
    }

    @Override
    public void requestRefresh() {
        if(!this.isContinuousRenderEnabled())
            _surface.requestRender();
    }

    @Override
    public void setFrameRate(float rate) {
        _surface.getRenderer().setFrameRate(rate);

    }

    @Override
    public float getFrameRate() {
        return (float)_surface.getRenderer().getFramerate();
    }

    @Override
    public synchronized void setContinuousRenderEnabled(boolean enabled) {
        this.continuousRenderEnabled = enabled;
        _surface.setRenderMode(this.continuousRenderEnabled ? GLMapSurface.RENDERMODE_CONTINUOUSLY : GLMapSurface.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public boolean isContinuousRenderEnabled() {
        return this.continuousRenderEnabled;
    }

    @Override
    public synchronized void addOnControlsChangedListener(OnControlsChangedListener l) {
        this.controlsListeners.add(l);
    }

    @Override
    public synchronized void removeOnControlsChangedListener(OnControlsChangedListener l) {
        this.controlsListeners.remove(l);
    }

    public GeoPoint intersectWithTerrain2(MapSceneModel scene, float x, float y) {
        GeoPoint result = GeoPoint.createMutable();
        synchronized(this.offscreen) {
            if (intersectWithTerrain(this.offscreen.terrainTiles, scene, x, y, result))
                return result;

        }
        return scene.inverse(new PointF(x, y), null);
    }

    /**
     * Must be called when holding lock on <code>this</code> or on GL thread
     * @param tiles
     * @param scene
     * @param x
     * @param y
     * @param result
     * @return
     */

    private static boolean intersectWithTerrain(Collection<TerrainTile> tiles, MapSceneModel scene, float x, float y, GeoPoint result) {
        if(tiles.isEmpty())
            return false;

        final double camdirx = (scene.camera.location.x-scene.camera.target.x)*scene.displayModel.projectionXToNominalMeters;
        final double camdiry = (scene.camera.location.y-scene.camera.target.y)*scene.displayModel.projectionYToNominalMeters;
        final double camdirz = (scene.camera.location.z-scene.camera.target.z)*scene.displayModel.projectionZToNominalMeters;

        final double mag = Math.max(Math.sqrt(camdirx*camdirx + camdiry*camdiry + camdirz*camdirz), 2000);

        //PointD loc = new PointD(scene.camera.target.x+(camdirx*mag*2d), scene.camera.target.y+(camdiry*mag*2d), scene.camera.target.z+(camdirz*mag*2d));
        PointD loc = new PointD(scene.camera.target.x, scene.camera.target.y, scene.camera.target.z);
        loc.x *= scene.displayModel.projectionXToNominalMeters;
        loc.x += (camdirx*mag*2d);
        loc.y *= scene.displayModel.projectionYToNominalMeters;
        loc.y += (camdiry*mag*2d);
        loc.z *= scene.displayModel.projectionZToNominalMeters;
        loc.z += (camdirz*mag*2d);

        PointD candidate = null;
        double candDist2 = Double.NaN;
        for(TerrainTile tile : tiles) {
            if(hitTest(scene, tile, x, y, result)) {
                // set as candidate if no result or closest to camera
                final PointD hit = scene.mapProjection.forward(result, null);
                final double dx = ((hit.x*scene.displayModel.projectionXToNominalMeters)-loc.x);
                final double dy = ((hit.y*scene.displayModel.projectionYToNominalMeters)-loc.y);
                final double dz = ((hit.z*scene.displayModel.projectionZToNominalMeters)-loc.z);
                double dist2 = (dx*dx)+(dy*dy)+(dz*dz);
                if(candidate == null || dist2 < candDist2) {
                    candDist2 = dist2;
                    candidate = hit;
                }
            }
        }

        if(candidate == null)
            return false;
        scene.mapProjection.inverse(candidate, result);
        // XXX - legacy did not return altitude
        result.set(GeoPoint.UNKNOWN);
        return true;
    }

    /**
     * Must be called when holding lock on <code>this</code> or on GL thread
     * @param scene
     * @param tile
     * @param screenX
     * @param screenY
     * @param result
     * @return
     */
    private static boolean hitTest(MapSceneModel scene, TerrainTile tile, float screenX, float screenY,
                           GeoPoint result) {
        Model m;
        int srid;
        Matrix localFrame = null;
        double renderElOffset;
        m = tile.model;
        srid = tile.info.srid;
        if (tile.info.localFrame != null) { 
            if (localFrame == null) {
                localFrame = tile.info.localFrame;
            } else {
                localFrame.concatenate(tile.info.localFrame);
            }
        } else {
            return false;
        }

        if (scene.mapProjection.getSpatialReferenceID() != srid)
            return false;

        GeometryModel gm = Models.createGeometryModel(m, localFrame);
        if (gm == null)
            return false;

        if (scene.inverse(new PointF(screenX, screenY), result, gm) == null)
            return false;

        return true;
    }

    static double camLocAdj = 2.5d;

    private static PointD adjustCamLocation(MapSceneModel view) {
        final double camlocx = view.camera.location.x*view.displayModel.projectionXToNominalMeters;
        final double camlocy = view.camera.location.y*view.displayModel.projectionYToNominalMeters;
        final double camlocz = view.camera.location.z*view.displayModel.projectionZToNominalMeters;
        final double camtgtx = view.camera.target.x*view.displayModel.projectionXToNominalMeters;
        final double camtgty = view.camera.target.y*view.displayModel.projectionYToNominalMeters;
        final double camtgtz = view.camera.target.z*view.displayModel.projectionZToNominalMeters;

        final double len = MathUtils.distance(camlocx, camlocy, camlocz, camtgtx, camtgty, camtgtz);

        final double dirx = (camlocx - camtgtx)/len;
        final double diry = (camlocy - camtgty)/len;
        final double dirz = (camlocz - camtgtz)/len;
        return new PointD((camtgtx + (dirx*len*camLocAdj)) / view.displayModel.projectionXToNominalMeters,
                          (camtgty + (diry*len*camLocAdj)) / view.displayModel.projectionYToNominalMeters,
                          (camtgtz + (dirz*len*camLocAdj)) / view.displayModel.projectionZToNominalMeters);
    }

    /**
     * @deprecated subject to be changed/removed at any time
     * @return
     */
    public static double estimateResolution(GLMapView model, double ullat, double ullng, double lrlat, double lrlng, GeoPoint closest) {
        return estimateResolution(model.oscene, ullat, ullng, lrlat, lrlng, closest);
    }

    /**
     * @deprecated subject to be changed/removed at any time
     * @return
     */
    public static double estimateResolution(MapSceneModel model, double ullat, double ullng, double lrlat, double lrlng, GeoPoint closest) {
        final double gsd;
        if(model.camera.elevation > -90d) {
            // get eye pos as LLA
            final PointD eyeProj;
            if (!model.camera.perspective) {
                eyeProj = adjustCamLocation(model);
            } else {
                eyeProj = model.camera.location;
            }
            GeoPoint eye = model.mapProjection.inverse(eyeProj, null);
            eye = new GeoPoint(eye.getLatitude(), GeoCalculations.wrapLongitude(eye.getLongitude()), eye.getAltitude());

            // XXX - find closest LLA on tile
            final double closestLat = MathUtils.clamp(eye.getLatitude(), lrlat, ullat);
            final double eyelng = eye.getLongitude();
            double lrlng_dist = Math.abs(lrlng-eyelng);
            if(lrlng_dist > 180d)
                lrlng_dist = 360d - lrlng_dist;
            double ullng_dist = Math.abs(ullng-eyelng);
            if(ullng_dist > 180d)
                ullng_dist = 360d - ullng_dist;
            final double closestLng;
            if(eyelng >= ullng && eyelng <= lrlng) {
                closestLng = eyelng;
            } else if(eyelng > lrlng || (lrlng_dist < ullng_dist)) {
                closestLng = lrlng;
            } else if(eyelng < ullng || (ullng_dist < lrlng_dist)) {
                closestLng = ullng;
            } else {
                throw new IllegalStateException();
            }

            if(closest == null)
                closest = GeoPoint.createMutable();
            closest.set(closestLat, GeoCalculations.wrapLongitude(closestLng), 0d);

            final boolean isSame = (eye.getLatitude() == closestLat && eye.getLongitude() == closestLng);
            if(isSame)
                return model.gsd;

            final double closestslant = GeoCalculations.slantDistanceTo(eye, closest);

            final double camlocx = model.camera.location.x*model.displayModel.projectionXToNominalMeters;
            final double camlocy = model.camera.location.y*model.displayModel.projectionYToNominalMeters;
            final double camlocz = model.camera.location.z*model.displayModel.projectionZToNominalMeters;
            final double camtgtx = model.camera.target.x*model.displayModel.projectionXToNominalMeters;
            final double camtgty = model.camera.target.y*model.displayModel.projectionYToNominalMeters;
            final double camtgtz = model.camera.target.z*model.displayModel.projectionZToNominalMeters;

            final double camtgtslant = MathUtils.distance(camlocx, camlocy, camlocz, camtgtx, camtgty, camtgtz);

            return (closestslant/camtgtslant)*model.gsd;
        } else {
            return model.gsd;
        }
    }

    /**
     * @deprecated subject to be changed/removed at any time
     * @return
     */
    public static double estimateResolution(GLMapView model, PointD center, double radius, GeoPoint closest) {
        return estimateResolution(model.oscene, center, radius, closest);
    }

    /**
     * @deprecated subject to be changed/removed at any time
     * @return
     */
    public static double estimateResolution(MapSceneModel model, PointD center, double radius, GeoPoint closest) {
        final double gsd;
        if(model.camera.elevation > -90d) {
            // get eye pos as LLA
            final PointD eyeProj;
            if (!model.camera.perspective) {
                eyeProj = adjustCamLocation(model);
            } else {
                eyeProj = model.camera.location;
            }
            GeoPoint eye = model.mapProjection.inverse(eyeProj, null);
            eye = new GeoPoint(eye.getLatitude(), GeoCalculations.wrapLongitude(eye.getLongitude()), eye.getAltitude());

            if(closest == null)
                closest = GeoPoint.createMutable();
            closest.set(center.y, center.x, center.z);

            // XXX - find closest LLA on tile
            if(Math.abs(center.x-eye.getLongitude()) > 180d) {
                // XXX - wrapping
            }

            final double closestslant = GeoCalculations.slantDistanceTo(eye, closest);
            if(closestslant <= radius)
                return model.gsd;

            final double camlocx = model.camera.location.x*model.displayModel.projectionXToNominalMeters;
            final double camlocy = model.camera.location.y*model.displayModel.projectionYToNominalMeters;
            final double camlocz = model.camera.location.z*model.displayModel.projectionZToNominalMeters;
            final double camtgtx = model.camera.target.x*model.displayModel.projectionXToNominalMeters;
            final double camtgty = model.camera.target.y*model.displayModel.projectionYToNominalMeters;
            final double camtgtz = model.camera.target.z*model.displayModel.projectionZToNominalMeters;

            final double camtgtslant = MathUtils.distance(camlocx, camlocy, camlocz, camtgtx, camtgty, camtgtz);

            return ((closestslant-radius)/camtgtslant)*model.gsd;
        } else {
            return model.gsd;
        }
    }

    private final static class State {
        private double drawMapScale = 2.5352504279048383E-9d;
        private double drawMapResolution = 0.0d;
        private double drawLat = 0d;
        private double drawLng = 0d;
        private double drawRotation = 0d;
        private double drawTilt = 0d;
        private double animationFactor = 0.3d;
        private int drawVersion = 0;
        private boolean targeting = false;
        private double westBound = -180d;
        private double southBound = -90d;
        private double northBound = 90d;
        private double eastBound = 180d;
        private int _left;
        private int _right;
        private int _top;
        private int _bottom;
        private int drawSrid = -1;
        private Projection drawProjection;
        private float focusx, focusy;
        private GeoPoint upperLeft;
        private GeoPoint upperRight;
        private GeoPoint lowerRight;
        private GeoPoint lowerLeft;
        private boolean settled;
        private int renderPump;
        private Matrix verticalFlipTranslate;
        private int verticalFlipTranslateHeight;
        private long coordTransformPtr;
        private boolean rigorousRegistrationResolutionEnabled;
        private long animationLastTick = -1L;
        private long animationDelta = -1L;
        private int sceneModelVersion;
        public MapSceneModel scene;
        private float[] sceneModelForwardMatrix;
        private double[] bulkForwardMatrix;
        private double[] bulkInverseMatrix;
        private double[] bulkForwardMatrix3x3;
        private double[] bulkInverseMatrix3x3;

        public State() {
            this.upperLeft = GeoPoint.createMutable();
            this.upperRight = GeoPoint.createMutable();
            this.lowerRight = GeoPoint.createMutable();
            this.lowerLeft = GeoPoint.createMutable();
            this.verticalFlipTranslate = Matrix.getIdentity();
            this.sceneModelForwardMatrix = new float[16];
            this.scene = null;
            this.bulkForwardMatrix = new double[16];
            this.bulkInverseMatrix = new double[16];
            this.bulkForwardMatrix3x3 = new double[9];
            this.bulkInverseMatrix3x3 = new double[9];
        }

        /**
         * Saves the state of the specified GLMapView.
         *
         * @param view
         */
        public void save(GLMapView view) {
            this.drawMapScale = view.drawMapScale;
            this.drawMapResolution = view.drawMapResolution;
            this.drawLat = view.drawLat;
            this.drawLng = view.drawLng;
            this.drawRotation = view.drawRotation;
            this.drawTilt = view.drawTilt;
            this.animationFactor = view.animationFactor;
            this.drawVersion = view.drawVersion;
            this.targeting = view.targeting;
            this.westBound = view.westBound;
            this.southBound = view.southBound;
            this.northBound = view.northBound;
            this.eastBound = view.eastBound;
            this._left = view._left;
            this._right = view._right;
            this._top = view._top;
            this._bottom = view._bottom;
            this.drawSrid = view.drawSrid;
            this.drawProjection = view.drawProjection;
            this.focusx = view.focusx;
            this.focusy = view.focusy;
            this.upperLeft.set(view.upperLeft);
            this.upperRight.set(view.upperRight);
            this.lowerRight.set(view.lowerRight);
            this.lowerLeft.set(view.lowerLeft);
            this.settled = view.settled;
            this.renderPump = view.renderPump;
            this.verticalFlipTranslate.set(view.verticalFlipTranslate);
            this.verticalFlipTranslateHeight = view.verticalFlipTranslateHeight;
            this.coordTransformPtr = view.coordTransformPtr;
            this.rigorousRegistrationResolutionEnabled = view.rigorousRegistrationResolutionEnabled;
            this.animationLastTick = view.animationLastTick;
            this.animationDelta = view.animationDelta;
            this.sceneModelVersion = view.sceneModelVersion;
            this.scene = view.scene;
            System.arraycopy(view.sceneModelForwardMatrix, 0, this.sceneModelForwardMatrix, 0, 16);

            view.bulkForwardMatrix.get(this.bulkForwardMatrix);
            view.bulkForwardMatrix.clear();

            view.bulkInverseMatrix.get(this.bulkInverseMatrix);
            view.bulkInverseMatrix.clear();

            view.bulkForwardMatrix3x3.get(this.bulkForwardMatrix3x3);
            view.bulkForwardMatrix3x3.clear();

            view.bulkInverseMatrix3x3.get(this.bulkInverseMatrix3x3);
            view.bulkInverseMatrix3x3.clear();
        }

        /**
         * Restores the state of the specified GLMapView.
         * @param view
         */
        public void restore(GLMapView view) {
            view.drawMapScale = this.drawMapScale;
            view.drawMapResolution = this.drawMapResolution;
            view.drawLat = this.drawLat;
            view.drawLng = this.drawLng;
            view.drawRotation = this.drawRotation;
            view.drawTilt = this.drawTilt;
            view.animationFactor = this.animationFactor;
            view.drawVersion = this.drawVersion;
            view.targeting = this.targeting;
            view.westBound = this.westBound;
            view.southBound = this.southBound;
            view.northBound = this.northBound;
            view.eastBound = this.eastBound;
            view._left = this._left;
            view._right = this._right;
            view._top = this._top;
            view._bottom = this._bottom;
            view.drawSrid = this.drawSrid;
            view.drawProjection = this.drawProjection;
            view.focusx = this.focusx;
            view.focusy = this.focusy;
            (view.upperLeft).set(this.upperLeft);
            (view.upperRight).set(this.upperRight);
            (view.lowerRight).set(this.lowerRight);
            (view.lowerLeft).set(this.lowerLeft);
            view.settled = this.settled;
            view.renderPump = this.renderPump;
            view.verticalFlipTranslate.set(this.verticalFlipTranslate);
            view.verticalFlipTranslateHeight = this.verticalFlipTranslateHeight;
            view.coordTransformPtr = this.coordTransformPtr;
            view.rigorousRegistrationResolutionEnabled = this.rigorousRegistrationResolutionEnabled;
            view.animationLastTick = this.animationLastTick;
            view.animationDelta = this.animationDelta;
            view.sceneModelVersion = this.sceneModelVersion;
            view.scene = this.scene;
            System.arraycopy(this.sceneModelForwardMatrix, 0, view.sceneModelForwardMatrix, 0, 16);

            view.bulkForwardMatrix.put(this.bulkForwardMatrix);
            view.bulkForwardMatrix.clear();

            view.bulkInverseMatrix.put(this.bulkInverseMatrix);
            view.bulkInverseMatrix.clear();

            view.bulkForwardMatrix3x3.put(this.bulkForwardMatrix3x3);
            view.bulkForwardMatrix3x3.clear();

            view.bulkInverseMatrix3x3.put(this.bulkInverseMatrix3x3);
            view.bulkInverseMatrix3x3.clear();
        }
    }

    class MapMoved implements Runnable {
        double targetLatitude;
        double targetLongitude;
        double targetScale;
        double targetRotation;
        double targetTilt;
        double targetAnimateFactor;
        boolean enqueued = false;

        @Override
        public void run() {
            final double latitude;
            final double longitude;
            final double scale;
            final double rotation;
            final double tilt;
            final double animateFactor;
            synchronized(this) {
                this.enqueued = false;
                latitude = targetLatitude;
                longitude = targetLongitude;
                scale = targetScale;
                rotation = targetRotation;
                tilt = targetTilt;
                animateFactor = targetAnimateFactor;
            }
            GLMapView.this.startAnimating(latitude,
                                          longitude,
                                          scale,
                                          rotation,
                                          tilt,
                                           animateFactor);
        }
        public synchronized void postUpdate(AtakMapView view, boolean animate) {
            targetLatitude = view.getLatitude ();
            targetLongitude = view.getLongitude ();
            targetScale = view.getMapScale ();
            targetRotation = view.getMapRotation ();
            targetTilt = view.getMapTilt ();
            targetAnimateFactor = animate ? 0.3 : 1.0;

            if(!this.enqueued) {
                this.enqueued = true;
                _surface.queueEvent(this);
            }
        }
    }

    /**
     * Stub of the old 'gdalext' library class.
     * @deprecated only made public to enable the compilation of the ISP plugin.
     */
    public static class OsrUtils {
        static {
            System.loadLibrary("proj");
            System.loadLibrary("gdal");
        }
        private OsrUtils() {}
        
        public static native long createProjection(int srid);
        public static native void destroyProjection(long nativePtr);
        
        private static void checkBuffers(Buffer src, Buffer dst) {
            if(!src.isDirect() || !dst.isDirect())
                throw new IllegalArgumentException("source and destination buffers must be direct");
            if(src.remaining() > dst.remaining())
                throw new IllegalArgumentException("destination does not have sufficient remaining");
        }

        public static int forward(int srid, FloatBuffer src, FloatBuffer dst) {
            checkBuffers(src, dst);
            
            return forwardImplF(srid, src, src.position(), dst, dst.position(), src.remaining() / 2);
        }
        
        public static int forward(int srid, DoubleBuffer src, FloatBuffer dst) {
            checkBuffers(src, dst);
            
            return forwardImplD(srid, src, src.position(), dst, dst.position(), src.remaining() / 2);
        }
        
        public static int forward(int srid, double[] postTransform, FloatBuffer src, FloatBuffer dst) {
            checkBuffers(src, dst);
            
            return forwardImplF(srid, postTransform, src, src.position(), dst, dst.position(), src.remaining() / 2);
        }
        
        public static int forward(int srid, double[] postTransform, DoubleBuffer src, FloatBuffer dst) {
            checkBuffers(src, dst);
            
            return forwardImplD(srid, postTransform, src, src.position(), dst, dst.position(), src.remaining() / 2);
        }
        
        public static int forward(int srid, DoubleBuffer postTransform, FloatBuffer src, FloatBuffer dst) {
            checkBuffers(src, dst);
            
            return forwardImplF(srid, postTransform, src, src.position(), dst, dst.position(), src.remaining() / 2);
        }
        
        public static int forward(int srid, DoubleBuffer postTransform, DoubleBuffer src, FloatBuffer dst) {
            checkBuffers(src, dst);
            
            return forwardImplD(srid, postTransform, src, src.position(), dst, dst.position(), src.remaining() / 2);
        }
        
        public static int forward(long transformPtr, FloatBuffer src, FloatBuffer dst) {
            checkBuffers(src, dst);
            
            return forwardImplF(transformPtr, src, src.position(), dst, dst.position(), src.remaining());
        }
        
        public static int forward(long transformPtr, DoubleBuffer src, FloatBuffer dst) {
            checkBuffers(src, dst);
            
            return forwardImplD(transformPtr, src, src.position(), dst, dst.position(), src.remaining());
        }
        
        public static int forward(long transformPtr, double[] postTransform, FloatBuffer src, FloatBuffer dst) {
            checkBuffers(src, dst);
            
            return forwardImplF(transformPtr, postTransform, src, src.position(), dst, dst.position(), src.remaining() / 2);
        }
        
        public static int forward(long transformPtr, double[] postTransform, DoubleBuffer src, FloatBuffer dst) {
            checkBuffers(src, dst);
            
            return forwardImplD(transformPtr, postTransform, src, src.position(), dst, dst.position(), src.remaining() / 2);
        }
        
        public static int forward(long transformPtr, DoubleBuffer postTransform, FloatBuffer src, FloatBuffer dst) {
            checkBuffers(src, dst);
            
            return forwardImplF(transformPtr, postTransform, src, src.position(), dst, dst.position(), src.remaining() / 2);
        }
        
        public static int forward(long transformPtr, DoubleBuffer postTransform, DoubleBuffer src, FloatBuffer dst) {
            checkBuffers(src, dst);
            
            return forwardImplD(transformPtr, postTransform, src, src.position(), dst, dst.position(), src.remaining() / 2);
        }
        
        /*************************************************************************/
        
        private static native int forwardImplF(int srid, FloatBuffer src, int srcOff, FloatBuffer dst, int dstOff, int count);
        
        private static native int forwardImplD(int srid, DoubleBuffer src, int srcOff, FloatBuffer dst, int dstOff, int count);
        
        private static native int forwardImplF(int srid, double[] postTransform, FloatBuffer src, int srcOff, FloatBuffer dst, int dstOff, int count);
        
        private static native int forwardImplD(int srid, double[] postTransform, DoubleBuffer src, int srcOff, FloatBuffer dst, int dstOff, int count);
        
        private static native int forwardImplF(int srid, DoubleBuffer postTransform, FloatBuffer src, int srcOff, FloatBuffer dst, int dstOff, int count);
        
        private static native int forwardImplD(int srid, DoubleBuffer postTransform, DoubleBuffer src, int srcOff, FloatBuffer dst, int dstOff, int count);
        
        private static native int forwardImplF(long transformPtr, FloatBuffer src, int srcOff, FloatBuffer dst, int dstOff, int count);
        
        private static native int forwardImplD(long transformPtr, DoubleBuffer src, int srcOff, FloatBuffer dst, int dstOff, int count);
        
        private static native int forwardImplF(long transformPtr, double[] postTransform, FloatBuffer src, int srcOff, FloatBuffer dst, int dstOff, int count);
        
        private static native int forwardImplD(long transformPtr, double[] postTransform, DoubleBuffer src, int srcOff, FloatBuffer dst, int dstOff, int count);
        
        private static native int forwardImplF(long transformPtr, DoubleBuffer postTransform, FloatBuffer src, int srcOff, FloatBuffer dst, int dstOff, int count);
        
        private static native int forwardImplD(long transformPtr, DoubleBuffer postTransform, DoubleBuffer src, int srcOff, FloatBuffer dst, int dstOff, int count);
        
    }

}

