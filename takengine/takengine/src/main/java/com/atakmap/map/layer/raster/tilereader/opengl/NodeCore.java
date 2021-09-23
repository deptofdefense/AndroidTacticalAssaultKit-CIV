package com.atakmap.map.layer.raster.tilereader.opengl;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.Interop;
import com.atakmap.map.RenderContext;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.map.layer.raster.controls.TileCacheControl;
import com.atakmap.map.layer.raster.controls.TileClientControl;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory;
import com.atakmap.map.opengl.GLDiagnostics;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.math.PointI;
import com.atakmap.math.RectD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLTextureCache;
import com.atakmap.opengl.Shader;
import com.atakmap.util.*;

import java.util.*;

class NodeCore implements Disposable, TileCacheControl.OnTileUpdateListener {
    private final static double POLE_LATITUDE_LIMIT_EPISLON = 0.00001d;
    private final static Interop<Matrix> Matrix_interop = Interop.findInterop(Matrix.class);

    private final static Map<RenderContext, ReferenceCount<NodeContextResources>> resourcesReferences = new IdentityHashMap<>();

    final GLQuadTileNode2.Initializer init;
    Shader shader;
    final RenderState renderState = new RenderState();
    GLQuadTileNode2.Initializer.Result initResult;

    public final TileReader tileReader;
    /** Projects between the image coordinate space and WGS84. */
    public final DatasetProjection2 imprecise;
    /** Projects between the image coordinate space and WGS84. */
    public final DatasetProjection2 precise;

    public final int srid;
    public com.atakmap.map.layer.raster.tilereader.opengl.VertexResolver vertexResolver;
    public GLTextureCache textureCache;

    public final String type;

    public final String uri;

    public final boolean textureBorrowEnabled;

    public final boolean textureCopyEnabled;

    /** local GSD for dataset */
    public final double gsd;

    /* corner coordinates of dataset NOT tile */

    /** upper-left corner for dataset */
    public final GeoPoint upperLeft;
    /** upper-right corner for dataset */
    public final GeoPoint upperRight;
    /** lower-right corner for dataset */
    public final GeoPoint lowerRight;
    /** lower-left corner for dataset */
    public final GeoPoint lowerLeft;

    /** longitudinal unwrapping for datasets which cross the IDL */
    public final double unwrap;

    public final int[] frameBufferHandle;
    public final int[] depthBufferHandle;

    /** minification filter to be applied to texture */
    public int minFilter;
    /** magnification filter to be applied to texture */
    public int magFilter;

    // modulation color components
    public int color;
    public float colorR;
    public float colorG;
    public float colorB;
    public float colorA;

    public boolean debugDrawEnabled;
    public final GLQuadTileNode3.Options options;
    public final LinkedList<Releasable> releasables;
    public boolean loadingTextureEnabled;

    public boolean disposed;

    public RectD[] drawROI;
    public int drawPumpHemi;
    public int drawPumpLevel;

    public boolean progressiveLoading;

    public final long fadeTimerLimit;

    public boolean versionCheckEnabled = true;

    int tilesThisFrame;

    boolean adaptiveLodCulling = false;

    Matrix xproj;
    Matrix mvp;
    long mvpPtr;

    final NodeContextResources resources;
    private final ReferenceCount<NodeContextResources> resourcesRef;

    TileReadRequestPrioritizer requestPrioritizer;
    TileReader.AsynchronousIO asyncio;

    boolean suspended;
    int stateMask;

    RenderContext context;

    GLDiagnostics diagnostics = new GLDiagnostics();
    boolean diagnosticsEnabled = false;

    SurfaceRendererControl surfaceControl;

    TileCacheControl cacheControl;
    TileClientControl clientControl;

    private Set<PointI> updatedTilesWrite = new HashSet<>();
    Set<PointI> updatedTiles = new HashSet<>();

    NodeCore(RenderContext ctx,
             String type,
             GLQuadTileNode2.Initializer init,
             GLQuadTileNode2.Initializer.Result result,
             int srid,
             double gsdHint,
             GLQuadTileNode3.Options opts) {

        if (result.reader == null || result.imprecise == null)
            throw new NullPointerException();

        this.context = ctx;
        this.type = type;
        this.init = init;
        this.initResult = result;
        this.tileReader = this.initResult.reader;
        this.imprecise = this.initResult.imprecise;
        this.precise = this.initResult.precise;
        this.srid = srid;
        this.uri = this.tileReader.getUri();

        this.debugDrawEnabled = (ConfigOptions.getOption("imagery.debug-draw-enabled", 0) != 0);
        this.fadeTimerLimit = ConfigOptions.getOption("glquadtilenode2.fade-timer-limit", 0L);

        if(opts == null)
            opts = new GLQuadTileNode3.Options();
        this.options = opts;
        this.options.progressiveLoad &= this.tileReader.isMultiResolution();

        this.color = 0xFFFFFFFF;
        this.colorR = 1f;
        this.colorG = 1f;
        this.colorB = 1f;
        this.colorA = 1f;

        this.releasables = new LinkedList<Releasable>();

        this.loadingTextureEnabled = false;

        this.textureBorrowEnabled = (ConfigOptions.getOption("imagery.texture-borrow", 1) != 0);
        this.textureCopyEnabled = (ConfigOptions.getOption("imagery.texture-copy", 1) != 0);
        this.textureCache = opts.textureCache;

        this.upperLeft = GeoPoint.createMutable();
        this.imprecise.imageToGround(new PointD(0, 0), this.upperLeft);
        this.upperRight = GeoPoint.createMutable();
        this.imprecise.imageToGround(new PointD(this.tileReader.getWidth() - 1, 0), this.upperRight);
        this.lowerRight = GeoPoint.createMutable();
        this.imprecise.imageToGround(new PointD(this.tileReader.getWidth() - 1,
        this.tileReader.getHeight() - 1), this.lowerRight);
        this.lowerLeft = GeoPoint.createMutable();
        this.imprecise.imageToGround(new PointD(0, this.tileReader.getHeight() - 1), this.lowerLeft);

        // if dataset bounds cross poles (e.g. Google "Flat Projection"
        // tile server), clip the bounds inset a very small amount from the
        // poles
        final double minLat = MathUtils.min(this.upperLeft.getLatitude(), this.upperRight.getLatitude(), this.lowerRight.getLatitude());
        final double maxLat = MathUtils.max(this.upperLeft.getLatitude(), this.upperRight.getLatitude(), this.lowerRight.getLatitude());

        if(minLat < -90d || maxLat > 90d) {
            final double minLatLimit = -90d + POLE_LATITUDE_LIMIT_EPISLON;
            final double maxLatLimit = 90d - POLE_LATITUDE_LIMIT_EPISLON;
            this.upperLeft.set(MathUtils.clamp(this.upperLeft.getLatitude(), minLatLimit, maxLatLimit), this.upperLeft.getLongitude());
            this.upperRight.set(MathUtils.clamp(this.upperRight.getLatitude(), minLatLimit, maxLatLimit), this.upperRight.getLongitude());
            this.lowerRight.set(MathUtils.clamp(this.lowerRight.getLatitude(), minLatLimit, maxLatLimit), this.lowerRight.getLongitude());
            this.lowerLeft.set(MathUtils.clamp(this.lowerLeft.getLatitude(), minLatLimit, maxLatLimit), this.lowerLeft.getLongitude());
        }

        float minLng = (float) MathUtils.min(this.upperLeft.getLongitude(),
                this.upperRight.getLongitude(),
                this.lowerRight.getLongitude(),
                this.lowerLeft.getLongitude());
        float maxLng = (float) MathUtils.max(this.upperLeft.getLongitude(),
                this.upperRight.getLongitude(),
                this.lowerRight.getLongitude(),
                this.lowerLeft.getLongitude());

        if (minLng < -180 && maxLng > -180)
            this.unwrap = 360;
        else if (maxLng > 180 && minLng < 180)
            this.unwrap = -360;
        else
            this.unwrap = 0;

        if(!Double.isNaN(gsdHint)) {
            this.gsd = gsdHint;
        } else {
            this.gsd = DatasetDescriptor.computeGSD(this.tileReader.getWidth(),
                    this.tileReader.getHeight(),
                    this.upperLeft,
                    this.upperRight,
                    this.lowerRight,
                    this.lowerLeft);
        }

        this.frameBufferHandle = new int[1];
        this.depthBufferHandle = new int[1];

        this.minFilter = GLES20FixedPipeline.GL_LINEAR;
        this.magFilter = GLES20FixedPipeline.GL_LINEAR;

        if(this.imprecise != null)
            this.releasables.add(this.imprecise);
        if(this.precise != null)
            this.releasables.add(this.precise);

        this.drawROI = new RectD[] { new RectD(), new RectD() };
        this.drawPumpHemi = -1;

        this.progressiveLoading = false;

        this.disposed = false;

        this.xproj = Matrix.getIdentity();
        this.mvp = Matrix.getIdentity();
        this.mvpPtr = Matrix_interop.getPointer(this.mvp);

        if(this.tileReader != null) {
            this.asyncio = this.tileReader.getControl(TileReader.AsynchronousIO.class);
            if(this.asyncio != null) {
                // create the request prioritizer. If progressive loading is
                // being used, prioritize low resolution tiles, otherwise
                // prioritize high resolution tiles
                this.requestPrioritizer = new TileReadRequestPrioritizer(this.options.progressiveLoad);
                this.asyncio.setReadRequestPrioritizer(this.tileReader, this.requestPrioritizer);
            }

            this.cacheControl = this.tileReader.getControl(TileCacheControl.class);
            if(this.cacheControl != null)
                this.cacheControl.setOnTileUpdateListener(this);
            this.clientControl = this.tileReader.getControl(TileClientControl.class);
        }

        synchronized(resourcesReferences) {
            ReferenceCount<NodeContextResources> ref = resourcesReferences.get(ctx);
            if(ref == null) {
                ref = new ReferenceCount<NodeContextResources>(new NodeContextResources(), false) {
                    @Override
                    protected void onDereferenced() {
                        Unsafe.free(value.coordStreamBuffer);
                        super.onDereferenced();
                    }
                };
            }
            this.resourcesRef = ref;
            this.resources = this.resourcesRef.reference();
        }
    }

    @Override
    public void onTileUpdated(int level, int x, int y) {
        synchronized(updatedTilesWrite) {
            updatedTilesWrite.add(new PointI(x, y, level));
        }
        if(this.surfaceControl != null)
            this.surfaceControl.markDirty();
    }

    void refreshUpdateList() {
        synchronized(updatedTilesWrite) {
            updatedTiles.clear();
            updatedTiles.addAll(updatedTilesWrite);
            updatedTilesWrite.clear();
        }
    }

    @Override
    public synchronized void dispose() {
        if(this.disposed)
            return;
        this.init.dispose(this.initResult);
        synchronized (resourcesReferences) {
            this.resourcesRef.dereference();
            if(!this.resourcesRef.isReferenced())
                resourcesReferences.values().remove(this.resourcesRef);
        }
        if(this.cacheControl != null)
            this.cacheControl.setOnTileUpdateListener(null);

        this.disposed = true;
    }

    static NodeCore create(RenderContext ctx, ImageInfo info, TileReaderFactory.Options readerOpts, GLQuadTileNode3.Options opts, boolean throwOnReaderFailedInit, GLQuadTileNode2.Initializer init) {
        GLQuadTileNode2.Initializer.Result result = init.init(info, readerOpts);
        if(result.error != null && throwOnReaderFailedInit)
            throw new RuntimeException(result.error);

        return new NodeCore(ctx,
                            info.type,
                            init,
                            result,
                            info.srid,
                            info.maxGsd,
                            opts);
    }
}