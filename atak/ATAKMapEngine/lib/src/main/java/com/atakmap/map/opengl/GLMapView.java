
package com.atakmap.map.opengl;

import java.nio.Buffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.graphics.PointF;
import android.graphics.RectF;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;

import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.Unsafe;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.map.AtakMapController;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.EngineLibrary;
import com.atakmap.map.Globe;
import com.atakmap.map.Interop;
import com.atakmap.map.MapRenderer2;
import com.atakmap.map.RenderContext;
import com.atakmap.map.RenderSurface;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.layer.control.TerrainBlendControl;
import com.atakmap.map.layer.model.Mesh;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelBuilder;
import com.atakmap.map.layer.model.ModelHitTestControl;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.Models;
import com.atakmap.map.projection.Projection;
import com.atakmap.math.GeometryModel;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
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
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLTexture;
import com.atakmap.util.Collections2;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReadWriteLock;
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
public class GLMapView implements
        AtakMapView.OnMapMovedListener,
        AtakMapView.OnMapProjectionChangedListener,
        AtakMapView.OnLayersChangedListener,
        AtakMapView.OnElevationExaggerationFactorChangedListener,
        AtakMapView.OnContinuousScrollEnabledChangedListener,
        AtakMapController.OnFocusPointChangedListener,
        MapRenderer,
        MapRenderer2,
        Disposable {

    static {
        EngineLibrary.initialize();
    }

    final static NativePeerManager.Cleaner CLEANER = new NativePeerManager.Cleaner() {
        @Override
        protected void run(Pointer pointer, Object opaque) {
            destruct(pointer);
        }
    };

    final static Interop<MapSceneModel> MapSceneModel_interop = Interop.findInterop(MapSceneModel.class);
    final static Interop<Mesh> Mesh_interop = Interop.findInterop(Mesh.class);

    private final static int INVERSE_MODE_ABSOLUTE = 0;
    private final static int INVERSE_MODE_TERRAIN = 1;
    private final static int INVERSE_MODE_MODEL = 2;

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
    private final Cleaner cleaner;

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

    private RenderContext _context;
    private RenderSurface _surface;
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

    private Matrix verticalFlipTranslate;
    private int verticalFlipTranslateHeight;

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

    public final static double recommendedGridSampleDistance = 0.125d;

    private Map<Layer2, Collection<MapControl>> controls;
    private Collection<MapControl> mapViewControls;
    // XXX - cache the model hit test controls separately to optimize inverse impl
    private final Set<ModelHitTestControl> modelHitTestControls = Collections2.newIdentityHashSet();


    public double hardwareTransformResolutionThreshold = 0d;

    public TerrainRenderService terrain;
    public static final double elevationOffset = 0d;
    public double elevationScaleFactor;
    public boolean terrainBlendEnabled = false;
    public double terrainBlendFactor = 1.0;

    public boolean continuousScrollEnabled = true;

    private MapMoved mapMovedUpdater = new MapMoved();

    Pointer pointer;
    final ReadWriteLock rwlock = new ReadWriteLock();
    Object owner;

    Pointer ctxptr;

    private int syncVersion;
    private int syncPass;

    private final RenderSurface.OnSizeChangedListener sizeChangedHandler = new RenderSurface.OnSizeChangedListener() {
        @Override
        public void onSizeChanged(RenderSurface surface, int width, int height) {
            rwlock.acquireRead();
            try {
                if(pointer.raw == 0L)
                    return;
                setSize(pointer.raw, width, height);
            } finally {
                rwlock.releaseRead();
            }
        }
    };
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

        double hfactor = Double.NaN;

        int terrainTilesVersion = -1;
    }

    Offscreen offscreen;

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
        this(surface, surface.getMapView().getGlobe(), left, bottom, right, top);
    }
    public GLMapView(RenderContext context, Globe globe, int left, int bottom, int right, int top) {
        _context = context;
        _surface = _context.getRenderSurface();

        Interop<RenderContext> RenderContext_interop = Interop.findInterop(RenderContext.class);
        Interop<Globe> Globe_interop = Interop.findInterop(Globe.class);

        ctxptr = RenderContext_interop.wrap(_context);
        pointer = create(ctxptr.raw, Globe_interop.getPointer(globe), left, bottom, right, top);
        cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);

        intern(this);

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

        this.verticalFlipTranslate = null;
        this.verticalFlipTranslateHeight = -1;

        this.sceneModelForwardMatrix = new float[16];

        this.controls = new IdentityHashMap<Layer2, Collection<MapControl>>();

        this.terrain = new ElMgrTerrainRenderService(getTerrainRenderService(this.pointer.raw), this);

        this.offscreen = new Offscreen();

        this.controlsListeners = Collections.newSetFromMap(new IdentityHashMap<OnControlsChangedListener, Boolean>());

        this.registerControl(null, terrainBlendControl);

        this.sync();

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
            _context.queueEvent(new Runnable() {
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
            _context.queueEvent(new Runnable() {
                @Override
                public void run() {
                    GLMapView.this.terrainBlendFactor = clampedValue;
                }
            });
        }
    };

    protected void setTargeting(boolean v) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return;
            set_targeting(this.pointer.raw, v);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public void setBaseMap(final GLMapRenderable basemap) {
        setBaseMap(this.pointer.raw, LegacyAdapters.adapt(basemap));
    }

    /**
     * Clean up the GLMapView
     */
    public void dispose() {
        //if(this.cleaner != null)
        //    this.cleaner.clean();
    }

    public void release() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return;
            release(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
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

        // XXX - invoke native
        this.animator.startAnimating(lat, lng, scale, rotation, tilt, animateFactor);
    }

    public void startAnimatingFocus(float x, float y, double animateFactor) {
        // XXX - invoke native
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
        return (GLMapSurface) _context;
    }

    public RenderContext getRenderContext() {
        return _context;
    }

    @Override
    public boolean lookAt(GeoPoint from, GeoPoint at, boolean animate) {
        return false;
    }

    @Override
    public boolean lookAt(GeoPoint at, double resolution, double azimuth, double tilt, boolean animate) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return false;
            double alt = 0d;
            if(!Double.isNaN(at.getAltitude()))
                alt = at.getAltitude();
            if(at.getAltitudeReference() == GeoPoint.AltitudeReference.AGL)
                alt += ElevationManager.getElevation(at.getLatitude(), at.getLongitude(), null);
            return lookAt(this.pointer.raw, at.getLatitude(), at.getLongitude(), alt, resolution, azimuth, tilt, animate);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public boolean lookFrom(GeoPoint from, double azimuth, double elevation, boolean animate) {
        return false;
    }

    @Override
    public MapSceneModel getMapSceneModel(boolean instant, DisplayOrigin origin) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return null;
            return MapSceneModel_interop.create(getMapSceneModel(this.pointer.raw, instant, (origin == DisplayOrigin.Lowerleft)));
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public DisplayMode getDisplayMode() {
        final int srid;
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return null;
            srid = getDisplayMode(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }

        switch(srid) {
            case 4326 :
                return DisplayMode.Flat;
            case 4978 :
                return DisplayMode.Globe;
            default :
                return null;
        }
    }

    @Override
    public void setDisplayMode(DisplayMode mode) {
        if(mode == null)
            throw new IllegalArgumentException();
        int srid;
        switch(mode) {
            case Flat:
                srid = 4326;
                break;
            case Globe:
                srid = 4978;
                break;
            default :
                throw new IllegalArgumentException();
        }
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return;
            setDisplayMode(this.pointer.raw, srid);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public DisplayOrigin getDisplayOrigin() {
        return DisplayOrigin.Lowerleft;
    }

    @Override
    public void addOnCameraChangedListener(OnCameraChangedListener l) {
        // XXX -
    }

    @Override
    public void removeOnCameraChangedListener(OnCameraChangedListener l) {
        // XXX -
    }

    @Override
    public boolean forward(GeoPoint lla, PointD xyz, DisplayOrigin origin) {
        // XXX -
        return false;
    }

    @Override
    public InverseResult inverse(PointD xyz, GeoPoint lla, InverseMode mode, int hints, DisplayOrigin origin) {
        double x = xyz.x;
        double y = xyz.y;
        double z = xyz.z;
        // flip origin if necessary
        if(origin == DisplayOrigin.UpperLeft)
            y = _surface.getHeight()-y;

        switch (mode) {
            case Transform:
                if(inverse(this.pointer.raw, x, y, z, INVERSE_MODE_ABSOLUTE, lla))
                    return InverseResult.Transformed;
                else
                    return InverseResult.None;
            case RayCast:
                if(!MathUtils.hasBits(hints, HINT_RAYCAST_IGNORE_SURFACE_MESH)) {
                    List<ModelHitTestControl> controls;
                    synchronized (modelHitTestControls) {
                        controls = new ArrayList<>(modelHitTestControls);
                    }
                    // perform model hit test
                    final float px = (float) x;
                    final float py = (origin == DisplayOrigin.UpperLeft) ? (float) xyz.y : (float) (_surface.getHeight() - xyz.y);
                    for (ModelHitTestControl ctrl : controls) {
                        // NOTE: control expects UL xyz
                        if (ctrl.hitTest(px, py, lla))
                            return InverseResult.SurfaceMesh;
                    }
                }
                if(!MathUtils.hasBits(hints, HINT_RAYCAST_IGNORE_TERRAIN_MESH)) {
                    // perform terrain mesh hit test
                    if (inverse(this.pointer.raw, x, y, z, INVERSE_MODE_TERRAIN, lla))
                        return InverseResult.TerrainMesh;
                }
                // intersect with the underlying geometry model
                if (inverse(this.pointer.raw, x, y, z, INVERSE_MODE_MODEL, lla))
                    return InverseResult.GeometryModel;

                // no intersection
                return InverseResult.None;
            default:
                return InverseResult.None;
        }
    }

    @Override
    public void setElevationExaggerationFactor(double factor) {
        rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return;
            setElevationExaggerationFactor(this.pointer.raw, factor);
        } finally {
            rwlock.releaseRead();
        }
    }

    @Override
    public double getElevationExaggerationFactor() {
        rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return 1d;
            return getElevationExaggerationFactor(this.pointer.raw);
        } finally {
            rwlock.releaseRead();
        }
    }

    @Override
    public boolean isAnimating() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return false;
            return isAnimating(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void setFocusPointOffset(float x, float y) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return;
            setFocusPointOffset(this.pointer.raw, x, y);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public float getFocusPointOffsetX() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return 0f;
            return getFocusPointOffsetX(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public float getFocusPointOffsetY() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return 0f;
            return getFocusPointOffsetY(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public RenderSurface getRenderSurface() {
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
        return getTerrainMeshElevation(this.pointer.raw, latitude, longitude);
    }

    public int getTerrainVersion() {
        return offscreen.terrainTilesVersion;
    }

    /**
     *
     * @param latitude
     * @param longitude
     * @return
     *
     * @deprecated use {@link #getTerrainMeshElevation(double, double)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public double getElevation(double latitude, double longitude) {
        return getTerrainMeshElevation(latitude, longitude);
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
        forward(src, 2, dst, 2);
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
        if(srcSize != 2 && srcSize != 3)
            throw new IllegalArgumentException();
        if(dstSize != 2 && dstSize != 3)
            throw new IllegalArgumentException();
        if(src.remaining()/srcSize != dst.remaining()/dstSize)
            throw new IllegalArgumentException();

        if(!src.isDirect() || !dst.isDirect())
            throw new IllegalArgumentException();

        // XXX - add support for arrays
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return;
            forwardF(this.pointer.raw, Unsafe.getBufferPointer(src)+src.position()*4, srcSize, Unsafe.getBufferPointer(dst)+dst.position()*4, dstSize, src.remaining()/srcSize);
        } finally {
            this.rwlock.releaseRead();
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
        forward(src, 2, dst, 2);
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
        if(srcSize != 2 && srcSize != 3)
            throw new IllegalArgumentException();
        if(dstSize != 2 && dstSize != 3)
            throw new IllegalArgumentException();
        if(src.remaining()/srcSize != dst.remaining()/dstSize)
            throw new IllegalArgumentException();

        if(!src.isDirect() || !dst.isDirect())
            throw new IllegalArgumentException();

        // XXX - add support for arrays
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                return;
            forwardD(this.pointer.raw, Unsafe.getBufferPointer(src)+src.position()*8, srcSize, Unsafe.getBufferPointer(dst)+dst.position()*4, dstSize, src.remaining()/srcSize);
        } finally {
            this.rwlock.releaseRead();
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

    private void sync() {
        // sync with the map
        sync(this.pointer.raw, this);
        // update boolean fields explicitly
        this.targeting = get_targeting(this.pointer.raw);
        this.rigorousRegistrationResolutionEnabled = get_rigorousRegistrationResolutionEnabled(this.pointer.raw);
        this.crossesIDL = get_crossesIDL(this.pointer.raw);
        // XXX - managed by Java layer currently
        //this.terrainBlendEnabled = get_terrainBlendEnabled(this.pointer.raw);
        this.continuousScrollEnabled = get_continuousScrollEnabled(this.pointer.raw);
        this.settled = get_settled(this.pointer.raw);

        // update transforms
        GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_PROJECTION);
        GLES20FixedPipeline.glOrthof(this._left, this._right, this._bottom, this._top, (float) this.scene.camera.near, (float) this.scene.camera.far);
        GLES20FixedPipeline.glMatrixMode(GLES20FixedPipeline.GL_MODELVIEW);
        GLES20FixedPipeline.glLoadIdentity();

        // update "wrapped" east/west
        {
            // corners
            this.internal.pointF.x = _left;
            this.internal.pointF.y = _top;
            this.scene.inverse(this.internal.pointF, this.internal.geo, true);
            final double ullng = this.internal.geo.getLongitude();

            this.internal.pointF.x = _right;
            this.internal.pointF.y = _top;
            this.scene.inverse(this.internal.pointF, this.internal.geo, true);
            final double urlng = this.internal.geo.getLongitude();

            this.internal.pointF.x = _right;
            this.internal.pointF.y = _bottom;
            this.scene.inverse(this.internal.pointF, this.internal.geo, true);
            final double lrlng = this.internal.geo.getLongitude();

            this.internal.pointF.x = _left;
            this.internal.pointF.y = _bottom;
            this.scene.inverse(this.internal.pointF, this.internal.geo, true);
            final double lllng = this.internal.geo.getLongitude();

            eastBoundUnwrapped = MathUtils.max(ullng, urlng, lrlng, lllng);
            westBoundUnwrapped = MathUtils.min(ullng, urlng, lrlng, lllng);
        }

        // update IDL helper
        this.idlHelper.update(this);

        this.offscreen.terrainTilesVersion = getTerrainVersion(this.pointer.raw);
    }

    /**
     * Renders the current view of the map.
     */
    public void render() {
        scratch.pointD.x = 0;
        scratch.pointD.y = 0;
        scratch.pointD.z = 0;

        scratch.pointF.x = 0;
        scratch.pointF.y = 0;

        scratch.geo.set(0, 0, 0);

        internal.pointD.x = 0;
        internal.pointD.y = 0;
        internal.pointD.z = 0;

        internal.pointF.x = 0;
        internal.pointF.y = 0;

        internal.geo.set(0, 0, 0);

        render(this.pointer.raw);
        sync();
        if(this.drawTilt > 0 && this.syncPass == RENDER_PASS_SURFACE)
            this.syncPass = 0;
        this.syncPass = 0;

        if(this.drawTilt > 0 && this.terrainBlendEnabled)
            set_terrainBlendFactor(this.pointer.raw, (float)this.terrainBlendFactor);
        else
            set_terrainBlendFactor(this.pointer.raw, 1f);
    }

    public void start() {
        _surface.addOnSizeChangedListener(sizeChangedHandler);
        sizeChangedHandler.onSizeChanged(_surface, _surface.getWidth(), _surface.getHeight());
        setDisplayDpi(this.pointer.raw, _surface.getDpi());
        start(this.pointer.raw);
        sync(this.pointer.raw, this);
        this.syncPass = 0;
    }

    public void stop() {
        _surface.removeOnSizeChangedListener(sizeChangedHandler);
        stop(this.pointer.raw);
    }

    protected void drawRenderables() {
        // duplicating `render()` for backwards compatibility
        scratch.pointD.x = 0;
        scratch.pointD.y = 0;
        scratch.pointD.z = 0;

        scratch.pointF.x = 0;
        scratch.pointF.y = 0;

        scratch.geo.set(0, 0, 0);

        internal.pointD.x = 0;
        internal.pointD.y = 0;
        internal.pointD.z = 0;

        internal.pointF.x = 0;
        internal.pointF.y = 0;

        internal.geo.set(0, 0, 0);

        render(this.pointer.raw);
        sync();
        if(this.drawTilt > 0 && this.syncPass == RENDER_PASS_SURFACE)
            this.syncPass = 0;
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

    /** @deprecated refactor based on {@link #drawMapScale} or {@link #drawMapResolution} */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public double getLegacyScale() {
        return ((drawMapScale * Globe.getFullEquitorialExtentPixels(_surface.getDpi())) / 360d);
    }

    /**
     * Updates {@link #westBound}, {@link #eastBound}, {@link #southBound} and {@link #northBound}.
     * Invoked during {@link #render()}.
     */
    protected void updateBounds() {
        // corners
        this.internal.pointF.x = _left;
        this.internal.pointF.y = _top;
        this.scene.inverse(this.internal.pointF,   this.internal.geo, true);
        this.upperLeft.set(this.internal.geo);
        final double ullat = this.internal.geo.getLatitude();
        final double ullng = this.internal.geo.getLongitude();

        this.internal.pointF.x = _right;
        this.internal.pointF.y = _top;
        this.scene.inverse(this.internal.pointF,   this.internal.geo, true);
        this.upperRight.set(this.internal.geo);
        final double urlat = this.internal.geo.getLatitude();
        final double urlng = this.internal.geo.getLongitude();

        this.internal.pointF.x = _right;
        this.internal.pointF.y = _bottom;
        this.scene.inverse(this.internal.pointF,   this.internal.geo, true);
        this.lowerRight.set(this.internal.geo);
        final double lrlat = this.internal.geo.getLatitude();
        final double lrlng = this.internal.geo.getLongitude();

        this.internal.pointF.x = _left;
        this.internal.pointF.y = _bottom;
        this.scene.inverse(this.internal.pointF,   this.internal.geo, true);
        this.lowerLeft.set(this.internal.geo);
        final double lllat = this.internal.geo.getLatitude();
        final double lllng = this.internal.geo.getLongitude();

        northBound = MathUtils.max(ullat, urlat, lrlat, lllat);
        southBound = MathUtils.min(ullat, urlat, lrlat, lllat);;
        eastBound = eastBoundUnwrapped = MathUtils.max(ullng, urlng, lrlng, lllng);
        westBound = westBoundUnwrapped = MathUtils.min(ullng, urlng, lrlng, lllng);
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
        _context.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (GLMapView.this.drawSrid != srid) {
                    GLMapView.this.drawSrid = srid;
                    GLMapView.this.drawProjection = ProjectionFactory.getProjection(GLMapView.this.drawSrid);
                    GLMapView.this.drawVersion++;
                }
            }
        });
    }

    /**************************************************************************/
    // OnContinuousScrollEnabledChangedListener

    @Override
    public void onContinuousScrollEnabledChanged(AtakMapView mapView, final boolean enabled) {
        _context.queueEvent(new Runnable() {
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
        _context.queueEvent(new Runnable() {
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
            _context.queueEvent(new Runnable() {
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
        _context.queueEvent (new Runnable () {
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
        this.validateSceneModelImpl(_surface.getWidth(), _surface.getHeight());
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

            this.scene = new MapSceneModel(_surface.getDpi(),
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

            this.scene.forward.get(internal.matrixD);
            for(int i = 0; i < 16; i++)
                this.sceneModelForwardMatrix[i] = (float)internal.matrixD[i];

            // mark as valid
            this.sceneModelVersion = this.drawVersion;
        }
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
        @Deprecated
        @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
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
            GLMapView.this.drawMapResolution = Globe.getMapResolution(_surface.getDpi(), _drawMapScale);

            GLMapView.this.focusx = _focusx;
            GLMapView.this.focusy = _focusy;

            GLMapView.this.settled = _isSettled;

            GLMapView.this.drawVersion++;

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
                    _context.requestRefresh();
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
    public void registerControl(Layer2 layer, MapControl ctrl) {
        if (ctrl instanceof ModelHitTestControl) {
            synchronized (modelHitTestControls) {
                this.modelHitTestControls.add((ModelHitTestControl) ctrl);
            }
        }
        synchronized (this) {
            Collection<MapControl> ctrls = this.getMapViewControls();
            if (layer != null) {
                ctrls = this.controls.get(layer);
                if (ctrls == null)
                    this.controls.put(layer, ctrls = Collections2.<MapControl>newIdentityHashSet());
            }
            if (ctrls.add(ctrl)) {
                for (OnControlsChangedListener l : this.controlsListeners)
                    l.onControlRegistered(layer, ctrl);
            }
        }
    }

    @Override
    public void unregisterControl(Layer2 layer, MapControl ctrl) {
        if(ctrl instanceof ModelHitTestControl) {
            synchronized (modelHitTestControls) {
                this.modelHitTestControls.remove((ModelHitTestControl) ctrl);
            }
        }
        synchronized (this) {
            Collection<MapControl> ctrls = layer == null ? this.mapViewControls : this.controls.get(layer);
            if (ctrls != null) {
                if (ctrls.remove(ctrl)) {
                    for (OnControlsChangedListener l : this.controlsListeners)
                        l.onControlUnregistered(layer, ctrl);
                }
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

    /*************************************************************************/
    // MapRenderer : RenderContext

    @Override
    public final boolean isRenderThread() {
        return _context.isRenderThread();
    }

    @Override
    public final void queueEvent(Runnable r) {
        _context.queueEvent(r);
    }

    @Override
    public final void requestRefresh() {
        _context.requestRefresh();
    }

    @Override
    public final void setFrameRate(float rate) {
        _context.setFrameRate(rate);

    }

    @Override
    public final float getFrameRate() {
        return _context.getFrameRate();
    }

    @Override
    public final synchronized void setContinuousRenderEnabled(boolean enabled) {
        _context.setContinuousRenderEnabled(enabled);
    }

    @Override
    public final boolean isContinuousRenderEnabled() {
        return _context.isContinuousRenderEnabled();
    }

    //

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
            if (intersectWithTerrain2(this.pointer.raw, MapSceneModel_interop.getPointer(scene), x, y, result))
                return result;

        }
        return scene.inverse(new PointF(x, y), null);
    }

    /**
     * @deprecated subject to be changed/removed at any time
     * @return
     */
    @Deprecated
    @DeprecatedApi(since = "4.1")
    public static double estimateResolution(GLMapView model, double ullat, double ullng, double lrlat, double lrlng, GeoPoint closest) {
        return estimateResolutionFromViewAABB(model.pointer.raw, ullat, ullng, lrlat, lrlng, closest);
    }

    /**
     * @deprecated subject to be changed/removed at any time
     * @return
     */
    @Deprecated
    @DeprecatedApi(since = "4.1")
    public static double estimateResolution(MapSceneModel model, double ullat, double ullng, double lrlat, double lrlng, GeoPoint closest) {
        return estimateResolutionFromModelAABB(MapSceneModel_interop.getPointer(model), ullat, ullng, lrlat, lrlng, closest);
    }

    /**
     * @deprecated subject to be changed/removed at any time
     * @return
     */
    @Deprecated
    @DeprecatedApi(since = "4.1")
    public static double estimateResolution(GLMapView model, PointD center, double radius, GeoPoint closest) {
        return estimateResolutionFromViewSphere(model.pointer.raw, center.x, center.y, center.z, radius, closest);
    }

    /**
     * @deprecated subject to be changed/removed at any time
     * @return
     */
    @Deprecated
    @DeprecatedApi(since = "4.1")
    public static double estimateResolution(MapSceneModel model, PointD center, double radius, GeoPoint closest) {
        return estimateResolutionFromModelSphere(MapSceneModel_interop.getPointer(model), center.x, center.y, center.z, radius, closest);
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
        private boolean rigorousRegistrationResolutionEnabled;
        private long animationLastTick = -1L;
        private long animationDelta = -1L;
        private int sceneModelVersion;
        public MapSceneModel scene;
        private float[] sceneModelForwardMatrix;

        public State() {
            this.upperLeft = GeoPoint.createMutable();
            this.upperRight = GeoPoint.createMutable();
            this.lowerRight = GeoPoint.createMutable();
            this.lowerLeft = GeoPoint.createMutable();
            this.verticalFlipTranslate = Matrix.getIdentity();
            this.sceneModelForwardMatrix = new float[16];
            this.scene = null;
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
            this.rigorousRegistrationResolutionEnabled = view.rigorousRegistrationResolutionEnabled;
            this.animationLastTick = view.animationLastTick;
            this.animationDelta = view.animationDelta;
            this.sceneModelVersion = view.sceneModelVersion;
            this.scene = view.scene;
            System.arraycopy(view.sceneModelForwardMatrix, 0, this.sceneModelForwardMatrix, 0, 16);
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
            view.rigorousRegistrationResolutionEnabled = this.rigorousRegistrationResolutionEnabled;
            view.animationLastTick = this.animationLastTick;
            view.animationDelta = this.animationDelta;
            view.sceneModelVersion = this.sceneModelVersion;
            view.scene = this.scene;
            System.arraycopy(this.sceneModelForwardMatrix, 0, view.sceneModelForwardMatrix, 0, 16);
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
                _context.queueEvent(this);
            }
        }
    }

    /**
     * depcrecated public marking but still used by the ISP plugin
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

    /*************************************************************************/
    // Interop

    static long getPointer(GLMapView object) {
        return object.pointer.raw;
    }
    //static native Pointer wrap(GLLayerSpi2 object);
    static boolean hasPointer(GLMapView object) {
        return true;
    }
    //static native GLMapView create(Pointer pointer, Object ownerReference);
    //static native boolean hasObject(long pointer);
    //static native GLMapView getObject(long pointer);
    //static Pointer clone(long otherRawPointer);
    static native void destruct(Pointer pointer);

    // native implementation

    static native Pointer create(long ctxPtr, long mapviewPtr, int left, int bottom, int right, int top);
    static native void render(long ptr);
    static native void release(long ptr);
    static native void setBaseMap(long ptr, GLMapRenderable2 basemap);
    static native void sync(long ptr, GLMapView view);
    static native void start(long ptr);
    static native void stop(long ptr);
    static native void intern(GLMapView view);

    static native double getTerrainMeshElevation(long ptr, double latitude, double longitude);
    static native boolean intersectWithTerrain2(long viewptr, long sceneptr, float x, float y, GeoPoint result);
    static native void getTerrainTiles(long ptr, Collection<Pointer> tiles);
    static native int getTerrainVersion(long ptr);
    static native Pointer getTerrainRenderService(long ptr);

    static native void forwardD(long ptr, long srcBufPtr, int srcSize, long dstBufPtr, int dstSize, int count);
    static native void forwardF(long ptr, long srcBufPtr, int srcSize, long dstBufPtr, int dstSize, int count);

    static native double estimateResolutionFromViewAABB(long ptr, double ullat, double ullng, double lrlat, double lrlng, GeoPoint closest);
    static native double estimateResolutionFromModelAABB(long ptr, double ullat, double ullng, double lrlat, double lrlng, GeoPoint closest);
    static native double estimateResolutionFromViewSphere(long ptr, double centerX, double centerY, double centerZ, double radius, GeoPoint closest);
    static native double estimateResolutionFromModelSphere(long ptr, double centerX, double centerY, double centerZ, double radius, GeoPoint closest);

    static native void set_terrainBlendFactor(long pointer, float value);
    static native void set_targeting(long pointer, boolean value);

    // XXX - so bizarre, but getting a JNI error trying to set boolean fields, make accessors as temporary workaround

    static native boolean get_targeting(long pointer);
    static native boolean get_crossesIDL(long pointr);
    static native boolean get_settled(long pointer);
    static native boolean get_rigorousRegistrationResolutionEnabled(long pointer);
    static native boolean get_terrainBlendEnabled(long pointer);
    static native boolean get_continuousScrollEnabled(long pointer);

    // MapRenderer2 camera management
    static native int getDisplayMode(long pointer);
    static native void setDisplayMode(long pointer, int srid);
    static native boolean lookAt(long ptr, double lat, double lng, double alt, double resolution, double azimuth, double tilt, boolean animate);
    static native Pointer getMapSceneModel(long ptr, boolean instant, boolean llOrigin);
    static native boolean isAnimating(long ptr);
    static native void setFocusPointOffset(long ptr, float x, float y);
    static native float getFocusPointOffsetX(long ptr);
    static native float getFocusPointOffsetY(long ptr);
    static native void setDisplayDpi(long ptr, double dpi);
    static native void setSize(long ptr, int w, int h);

    static native boolean inverse(long ptr, double x, double y, double z, int mode, GeoPoint result);

    static native double getElevationExaggerationFactor(long ptr);
    static native void setElevationExaggerationFactor(long ptr, double factor);

}

