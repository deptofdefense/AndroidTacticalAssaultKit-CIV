
package com.atakmap.android.elev.graphics;

import android.graphics.Color;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.util.Pair;

import com.atakmap.android.elev.HeatMapOverlay;
import com.atakmap.android.elev.dt2.Dt2ElevationData;
import com.atakmap.android.elev.dt2.Dt2FileWatcher;
import com.atakmap.app.DeveloperOptions;
import com.atakmap.coremap.conversions.ConversionFactors;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.conversion.EGM96;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;

import com.atakmap.map.MapRenderer;
import com.atakmap.map.elevation.ElevationData;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.elevation.ElevationSource;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.opengl.GLLayer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DefaultDatasetProjection2;
import com.atakmap.map.layer.raster.tilereader.opengl.GLTileMesh;
import com.atakmap.map.opengl.GLAsynchronousMapRenderable;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.NoninvertibleTransformException;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLTexture;
import com.atakmap.util.zip.IoUtils;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

public class GLHeatMap extends GLAsynchronousMapRenderable<HeatMapParams>
        implements GLLayer, HeatMapOverlay.OnHeatMapColorChangedListener,
        HeatMapOverlay.OnHeatMapResolutionChangedListener {

    private final static boolean DEBUG_QUERY_TIME = (DeveloperOptions
            .getIntOption("heatmap.debug-query-time", 0) != 0);

    public final static GLLayerSpi2 SPI2 = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            // HeatMapOverlay : Layer
            return 1;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
            final MapRenderer surface = arg.first;
            final Layer layer = arg.second;
            if (layer instanceof HeatMapOverlay)
                return GLLayerFactory.adapt(new GLHeatMap(surface,
                        (HeatMapOverlay) layer));
            return null;
        }
    };

    public final static String TAG = "GLHeatMap";

    private final static int QUICK_FACTOR = 5;

    private final static Set<String> DTED_PATHS = new LinkedHashSet<>();
    static {
        DTED_PATHS.add(FileSystemUtils.getItem("DTED").getAbsolutePath()
                + File.separator);

        File[] roots = FileSystemUtils.getDeviceRoots();
        for (File root : roots) {
            final File dtedDir = FileSystemUtils.getItemOnSameRoot(root,
                    "DTED");
            if (dtedDir != null) {
                DTED_PATHS.add(dtedDir.getAbsolutePath() + File.separator);
            }
        }
    }

    private GLHeatMapTexture heatmap;

    protected final MapRenderer renderContext;
    protected final HeatMapOverlay subject;

    int fullResolutionX;
    int fullResolutionY;

    int quickResolutionX;
    int quickResolutionY;

    protected float saturation;
    protected float value;
    protected float alpha;

    private int terrainVersion;

    private final Collection<GLMapRenderable> renderable;

    private CancellationSignal querySignal;

    GLHeatMap(MapRenderer renderContext, HeatMapOverlay subject) {
        this.renderContext = renderContext;
        this.subject = subject;

        this.heatmap = null;

        this.subject.addOnHeatMapColorChangedListener(this);

        this.saturation = this.subject.getSaturation();
        this.value = this.subject.getValue();
        this.alpha = this.subject.getAlpha();

        this.subject.addOnHeatMapResolutionChangedListener(this);

        this.fullResolutionX = this.subject.getSampleResolutionX();
        this.fullResolutionY = this.subject.getSampleResolutionY();

        this.quickResolutionX = Math.max(this.fullResolutionX / QUICK_FACTOR,
                14);
        this.quickResolutionY = Math.max(this.fullResolutionY / QUICK_FACTOR,
                10);

        this.renderable = new LinkedList<>();

        this.terrainVersion = -1;
    }

    /**************************************************************************/
    // Heat Map On Heat Map Resolution Changed Listener

    @Override
    public void onHeatMapResolutionChanged(HeatMapOverlay overlay) {
        final int xResolution = overlay.getSampleResolutionX();
        final int yResolution = overlay.getSampleResolutionY();

        this.renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                GLHeatMap.this.fullResolutionX = xResolution;
                GLHeatMap.this.fullResolutionY = yResolution;

                GLHeatMap.this.quickResolutionX = Math.max(
                        GLHeatMap.this.fullResolutionX / QUICK_FACTOR, 14);
                GLHeatMap.this.quickResolutionY = Math.max(
                        GLHeatMap.this.fullResolutionY / QUICK_FACTOR, 10);

                GLHeatMap.this.invalidate();
            }
        });
    }

    /**************************************************************************/
    // Heat Map On Heat Map Resolution Changed Listener

    @Override
    public void onHeatMapColorChanged(HeatMapOverlay overlay) {
        final float s = overlay.getSaturation();
        final float v = overlay.getValue();
        final float a = overlay.getAlpha();

        this.renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                GLHeatMap.this.saturation = s;
                GLHeatMap.this.value = v;
                GLHeatMap.this.alpha = a;

                GLHeatMap.this.invalidate();
            }
        });
    }

    /**************************************************************************/
    // GL Layer

    @Override
    public Layer getSubject() {
        return this.subject;
    }

    /**************************************************************************/
    // GL Asynchronous Map Renderable

    @Override
    public void draw(GLMapView view) {
        final int viewTerrainVersion = view.getTerrainVersion();
        // if terrain has changed, mark invalid
        if (this.terrainVersion != viewTerrainVersion) {
            this.invalid = true;
            this.terrainVersion = viewTerrainVersion;
        }
        super.draw(view);
    }

    @Override
    protected Collection<GLMapRenderable> getRenderList() {
        return this.renderable;
    }

    @Override
    protected ViewState newViewStateInstance() {
        return new HeatMapState();
    }

    @Override
    protected void resetPendingData(HeatMapParams pendingData) {
    }

    @Override
    protected void releasePendingData(HeatMapParams pendingData) {
        pendingData.elevationData = null;
        pendingData.rgbaData = null;
        pendingData.hsvLut = null;
    }

    @Override
    protected HeatMapParams createPendingData() {
        return new HeatMapParams();
    }

    @Override
    protected boolean updateRenderableReleaseLists(HeatMapParams pendingData) {
        // our query results were invalid, there's nothing new to render
        if (!pendingData.valid)
            return false;

        // create the texture if we need to
        if (this.heatmap == null)
            this.heatmap = new GLHeatMapTexture();

        // make sure the texture is in the renderable list
        if (this.renderable.size() < 1)
            this.renderable.add(this.heatmap);

        // update the texture with the results from the query
        this.heatmap.update(pendingData);

        return !this.invalid && !pendingData.needsRefresh;
    }

    private void validateLUT(HeatMapState state, HeatMapParams result) {
        if (result.hsvLut == null ||
                result.lutAlpha != state.alpha ||
                result.lutSaturation != state.saturation ||
                result.lutValue != state.value) {

            if (result.hsvLut == null)
                result.hsvLut = new int[256];
            result.lutAlpha = state.alpha;
            result.lutSaturation = state.saturation;
            result.lutValue = state.value;
            float[] hsv = new float[3];
            final int alpha = (int) (255f * this.alpha);
            for (int i = 0; i < result.hsvLut.length; i++) {
                // We use android.graphics.Color to convert from HSV to RGB
                hsv[0] = (1.0f - (float) i / (float) (result.hsvLut.length - 1))
                        * 255f;
                hsv[1] = state.saturation;
                hsv[2] = state.value;

                result.hsvLut[i] = Color.HSVToColor(alpha, hsv);
            }
        }
    }

    protected void updateRGBA(HeatMapState state, HeatMapParams result) {
        this.validateLUT(state, result);

        int rgba;
        int i = 0;
        for (int j = 0; j < (result.xSampleResolution
                * result.ySampleResolution); j++) {
            if (Double.isNaN(result.elevationData[j]))
                rgba = 0x00000000;
            else
                rgba = result.hsvLut[(int) MathUtils.clamp(
                        (result.elevationData[j] - result.minElev)
                                / (result.maxElev - result.minElev)
                                * (float) (result.hsvLut.length - 1),
                        0,
                        result.hsvLut.length - 1)];
            result.rgbaData[i++] = (byte) Color.red(rgba);
            result.rgbaData[i++] = (byte) Color.green(rgba);
            result.rgbaData[i++] = (byte) Color.blue(rgba);
            result.rgbaData[i++] = (byte) Color.alpha(rgba);
        }
        result.valid = true;

        // update the shared data model for the sake of the ISO widget

        SharedDataModel.getInstance().minHeat = result.minElev;
        SharedDataModel.getInstance().maxHeat = result.maxElev;
    }

    @Override
    protected String getBackgroundThreadName() {
        return "HeatMap [" + this.subject.getName() + "] GL worker@"
                + Integer.toString(this.hashCode(), 16);
    }

    @Override
    public void release() {
        synchronized (this) {
            // Cancel active query (if any)
            if (querySignal != null) {
                Log.d(TAG, "Requesting cancel on current query");
                querySignal.cancel();
            }
        }
        super.release();
    }

    @Override
    protected void query(ViewState state, HeatMapParams result) {
        // set up some defaults
        result.maxElev = (float) (ConversionFactors.FEET_TO_METERS * 19000);
        result.minElev = (float) (ConversionFactors.FEET_TO_METERS * -900);

        result.numSamples = 0;

        // update the resolution based on whether or not the map has settled.
        // we'll use a lower resolution while the map is moving to hopefully
        // see more frequent updates

        final boolean quickOnly = (state.drawMapResolution > 30);
        if (state.settled) {
            if (result.drawVersion != state.drawVersion) {
                // the map is settled, but we haven't rendered for it yet.
                // obtain a quick view and mark ourselves as needing a refresh
                result.needsRefresh = true;
                result.quick = true;
            } else {
                result.needsRefresh = false;
                result.quick = quickOnly;
            }
        } else {
            result.needsRefresh = false;
            result.quick = true;
        }
        result.drawVersion = state.drawVersion;

        if (!result.quick) {
            result.xSampleResolution = this.fullResolutionX;
            result.ySampleResolution = this.fullResolutionY;
        } else {
            result.xSampleResolution = this.quickResolutionX;
            result.ySampleResolution = this.quickResolutionY;
        }

        // record the ROI that we are querying
        if (state.crossesIDL) {
            int hemi = state.drawLng < 0d ? GeoCalculations.HEMISPHERE_WEST
                    : GeoCalculations.HEMISPHERE_EAST;
            result.upperLeft
                    .set(GeoCalculations.wrapLongitude(state.upperLeft, hemi));
            result.upperRight
                    .set(GeoCalculations.wrapLongitude(state.upperRight, hemi));
            result.lowerRight
                    .set(GeoCalculations.wrapLongitude(state.lowerRight, hemi));
            result.lowerLeft
                    .set(GeoCalculations.wrapLongitude(state.lowerLeft, hemi));
        } else {
            result.upperLeft.set(state.upperLeft);
            result.upperRight.set(state.upperRight);
            result.lowerRight.set(state.lowerRight);
            result.lowerLeft.set(state.lowerLeft);
        }

        final double distance;
        GeoPoint gp1, gp2;

        gp1 = result.lowerLeft;
        gp2 = result.upperRight;

        distance = GeoCalculations.distanceTo(gp1, gp2);

        // obtain the elevation data
        if (result.elevationData == null
                || result.elevationData.length < (result.xSampleResolution
                        * result.ySampleResolution))
            result.elevationData = new float[(result.xSampleResolution
                    * result.ySampleResolution)];

        if (state.drawMapResolution > 1000)
            Arrays.fill(result.elevationData, Float.NaN);

        // Connect to active query
        synchronized (this) {
            querySignal = result.querySignal;
        }

        queryGridImpl(result, distance);

        // Disconnect from active query
        synchronized (this) {
            querySignal = null;
        }

        // generate the RGBA data from the elevation values
        if (result.rgbaData == null
                || result.rgbaData.length < (4 * result.xSampleResolution
                        * result.ySampleResolution))
            result.rgbaData = new byte[(4 * result.xSampleResolution
                    * result.ySampleResolution)];

        this.updateRGBA((HeatMapState) state, result);
    }

    /**
     * Maintains the corner coordinates for the heatmap texture. This class is
     * a double buffer. The {@link #get(int)} method provides read-only access
     * to the front buffer; the {@link #set(int, GeoPoint)} method provides
     * write-only access to the back buffer.  The method, {@link #swap} should
     * be invoked to swap the front and back buffers.
     *  
     * 
     */
    private static class CornerCoords {
        final static int UPPER_LEFT = 0;
        final static int UPPER_RIGHT = 1;
        final static int LOWER_RIGHT = 2;
        final static int LOWER_LEFT = 3;

        private final GeoPoint[] coords;
        private int idx;

        CornerCoords() {
            this.coords = new GeoPoint[8];
            for (int i = 0; i < coords.length; i++)
                coords[i] = GeoPoint.createMutable();
            this.idx = 0;
        }

        public GeoPoint get(int coord) {
            return this.coords[(idx * 4) + coord];
        }

        public void set(int coord, GeoPoint p) {
            this.coords[((idx ^ 1) * 4) + coord].set(p.getLatitude(),
                    p.getLongitude());
        }

        public void swap() {
            this.idx ^= 1;
        }
    }

    /** The heatmap texture */
    private static class GLHeatMapTexture implements GLMapRenderable {

        private GLTexture heatmapTexture;
        private GLTileMesh mesh;

        private final CornerCoords cornerCoords;

        private int heatmapWidth;
        private int heatmapHeight;
        private ByteBuffer image;
        private boolean dirty;

        GLHeatMapTexture() {
            this.dirty = false;
            this.image = null;
            this.cornerCoords = new CornerCoords();

            this.heatmapTexture = null;
            this.mesh = null;
        }

        void update(HeatMapParams params) {
            // make sure the data buffer is ready to store the new data
            if (this.image == null
                    || this.image.capacity() < (4 * params.xSampleResolution
                            * params.ySampleResolution)) {
                this.image = com.atakmap.lang.Unsafe.allocateDirect(4
                        * params.xSampleResolution * params.ySampleResolution);
                this.image.order(ByteOrder.nativeOrder());
            }
            // put the RGBA data into the buffer
            this.image.put(params.rgbaData, 0, 4 * params.xSampleResolution
                    * params.ySampleResolution);
            // reset the position/limit on the buffer
            this.image.clear();

            // update the width/height of the data
            this.heatmapWidth = params.xSampleResolution;
            this.heatmapHeight = params.ySampleResolution;

            // set the corner coordinates for the latest data update. remember
            // that when we 'set', we are assigning the values in the back
            // buffer -- these coordinates won't be visible in 'draw' until we
            // have invoked 'swap'
            this.cornerCoords.set(CornerCoords.UPPER_LEFT, params.upperLeft);
            this.cornerCoords.set(CornerCoords.UPPER_RIGHT, params.upperRight);
            this.cornerCoords.set(CornerCoords.LOWER_RIGHT, params.lowerRight);
            this.cornerCoords.set(CornerCoords.LOWER_LEFT, params.lowerLeft);

            // the data has been updated, mark us dirty
            this.dirty = true;
        }

        @Override
        public void draw(GLMapView view) {

            if (SharedDataModel.getInstance().isoDisplayMode
                    .equals(SharedDataModel.HIDE))
                return;

            // Move heatmap/viewshed to proper unwrap
            if (view.crossesIDL) {
                int hemi = view.currentPass.drawLng < 0
                        ? GeoCalculations.HEMISPHERE_WEST
                        : GeoCalculations.HEMISPHERE_EAST;
                double unwrap = 0;
                for (int i = 0; i < 4; i++) {
                    GeoPoint gp = cornerCoords.get(i);
                    int gHemi = GeoCalculations.getHemisphere(gp);
                    if (gHemi == GeoCalculations.HEMISPHERE_WEST
                            && hemi == GeoCalculations.HEMISPHERE_EAST
                            && gp.getLongitude() < view.currentPass.eastBound) {
                        unwrap = 360;
                        break;
                    } else if (gHemi == GeoCalculations.HEMISPHERE_EAST
                            && hemi == GeoCalculations.HEMISPHERE_WEST
                            && gp.getLongitude() > view.currentPass.westBound) {
                        unwrap = -360;
                        break;
                    }
                }
                if (unwrap != 0) {
                    for (int i = 0; i < 4; i++) {
                        GeoPoint gp = cornerCoords
                                .get(i);
                        gp.set(gp.getLatitude(), gp.getLongitude() + unwrap);
                    }
                    cornerCoords.swap();
                    this.dirty = true;
                }
            }

            // if we are marked dirty, the data has been updated and we need to
            // re-load the texture
            if (this.dirty) {
                // make sure our texture is valid
                if (this.heatmapTexture == null
                        || this.heatmapTexture.getTexWidth() < this.heatmapWidth
                        || this.heatmapTexture
                                .getTexHeight() < this.heatmapHeight) {
                    if (this.heatmapTexture != null)
                        this.heatmapTexture.release();

                    this.heatmapTexture = new GLTexture(heatmapWidth,
                            heatmapHeight, GLES20FixedPipeline.GL_RGBA,
                            GLES20FixedPipeline.GL_UNSIGNED_BYTE);
                }

                // load the data
                this.heatmapTexture.load(this.image, 0, 0, this.heatmapWidth,
                        this.heatmapHeight);

                // swap the coordinates to match the data that we just loaded 
                this.cornerCoords.swap();

                // update the mesh to reflect the new coordinates
                if (mesh == null) {

                    try {
                        mesh = new GLTileMesh(
                                0,
                                0,
                                heatmapWidth,
                                heatmapHeight,
                                0.5f / (float) heatmapTexture.getTexWidth(),
                                0.5f / (float) heatmapTexture.getTexHeight(),
                                ((float) heatmapWidth - 0.5f)
                                        / (float) heatmapTexture.getTexWidth(),
                                ((float) heatmapHeight - 0.5f)
                                        / (float) heatmapTexture.getTexHeight(),
                                new DefaultDatasetProjection2(
                                        4326,
                                        heatmapWidth,
                                        heatmapHeight,
                                        cornerCoords
                                                .get(CornerCoords.UPPER_LEFT),
                                        cornerCoords
                                                .get(CornerCoords.UPPER_RIGHT),
                                        cornerCoords
                                                .get(CornerCoords.LOWER_RIGHT),
                                        cornerCoords
                                                .get(CornerCoords.LOWER_LEFT)));
                    } catch (IllegalArgumentException iae) {
                        Log.d(TAG,
                                "XXX: ATAK-7286 Crash when changing Viewshed radius",
                                iae);
                        return;
                    }
                } else {
                    try {
                        mesh.resetMesh(
                                0,
                                0,
                                heatmapWidth,
                                heatmapHeight,
                                0.5f / (float) heatmapTexture.getTexWidth(),
                                0.5f / (float) heatmapTexture.getTexHeight(),
                                ((float) heatmapWidth - 0.5f)
                                        / (float) heatmapTexture.getTexWidth(),
                                ((float) heatmapHeight - 0.5f)
                                        / (float) heatmapTexture.getTexHeight(),
                                new DefaultDatasetProjection2(
                                        4326,
                                        heatmapWidth,
                                        heatmapHeight,
                                        cornerCoords
                                                .get(CornerCoords.UPPER_LEFT),
                                        cornerCoords
                                                .get(CornerCoords.UPPER_RIGHT),
                                        cornerCoords
                                                .get(CornerCoords.LOWER_RIGHT),
                                        cornerCoords
                                                .get(CornerCoords.LOWER_LEFT)));
                    } catch (IllegalArgumentException iae) {
                        Log.d(TAG,
                                "XXX: ATAK-7286 Crash when changing Viewshed radius",
                                iae);
                        return;
                    }
                }

                // clear the dirty flag
                this.dirty = false;
            } else if (this.heatmapTexture == null) {
                // we're not dirty and there's no texture -- no data has been
                // loaded yet so just return
                return;
            }

            // draw the texture
            mesh.drawMesh(view, heatmapTexture.getTexId(), 1f, 1f, 1f, 1f);
        }

        @Override
        public void release() {
            if (this.heatmapTexture != null) {
                this.heatmapTexture.release();
                this.heatmapTexture = null;
            }

            if (this.mesh != null) {
                this.mesh.release();
                this.mesh = null;
            }

            this.image = null;

            this.dirty = false;
        }

    }

    /**************************************************************************/

    private static final long _NUM_LNG_LINES_OFFSET = 47; /* offset into header where 4 char line count starts (4 char sample point follows) */
    private static final long _HEADER_OFFSET = 3428;
    private static final long _DATA_RECORD_PREFIX_SIZE = 8;
    private static final long _DATA_RECORD_SUFFIX_SIZE = 4;

    static void queryGridImpl(HeatMapParams result,
            double coverageArea) {
        try {
            GridCache gc = new GridCache(result, coverageArea);
            final long s = SystemClock.elapsedRealtime();
            gc.query();
            final long e = SystemClock.elapsedRealtime();
            if (DEBUG_QUERY_TIME)
                Log.d(TAG,
                        "elevation query for "
                                + (result.xSampleResolution
                                        * result.ySampleResolution)
                                + " in "
                                + (e - s)
                                + "ms (unresolved="
                                + ((result.xSampleResolution
                                        * result.ySampleResolution)
                                        - result.numSamples)
                                + ")");
        } catch (NoninvertibleTransformException e) {
            result.valid = false;
        } catch (java.nio.BufferUnderflowException bue) {
            GeoPoint gp1, gp2;

            gp1 = result.lowerLeft;
            gp2 = result.upperRight;

            Log.e(TAG, "underflow error reading tile, damaged for area ll="
                    + gp1 + " ur=" + gp2);
            Arrays.fill(result.elevationData, Float.NaN);
        }
    }

    private static int[] _selectDTEDResource(double coverageArea) {

        /////////////////////////////////////////////////////////////////////////////////////////
        // DTED policy rules
        //
        // select DTED resource based on coverage area.
        // dt2 detail is not relevant to large areas and consumes resources
        //

        final int[] queryExtensions = new int[4];
        if (coverageArea > 100000) {
            queryExtensions[0] = 0;
            queryExtensions[1] = 1;
            queryExtensions[2] = 2;
            queryExtensions[3] = 3;
        } else if (coverageArea > 30000) {
            queryExtensions[0] = 1;
            queryExtensions[1] = 2;
            queryExtensions[2] = 3;
            queryExtensions[3] = 0;
        } else if (coverageArea > 10000) {
            queryExtensions[0] = 2;
            queryExtensions[1] = 3;
            queryExtensions[2] = 1;
            queryExtensions[3] = 0;
        } else {
            queryExtensions[0] = 3;
            queryExtensions[1] = 2;
            queryExtensions[2] = 1;
            queryExtensions[3] = 0;
        }
        return queryExtensions;
    }

    /** MIL-PRF-89020A 3.11.3.1 */
    private static float getDtedHeight(short s) {
        // note that cast of Double.NaN to float results in Float.NaN
        return (float) Dt2ElevationData.interpretSample(s);
    }

    private static boolean isElevValid(float value) {
        return !Double.isNaN(value) && (value > -100)
                && (value < 10000);
    }

    /**************************************************************************/

    // local variable wrapper for the queryGridCacheChunks() and
    // queryGridCachePoints() methods to reduce the copy-paste
    // between them
    private static class GridCache implements
            CancellationSignal.OnCancelListener {

        private final HeatMapParams result;

        private final int[] queryLevels;
        private final int numCellsX;
        private final int numCellsY;
        private final Matrix geo2img;
        private final Matrix img2geo;
        private final StringBuilder p;
        private double cellMinLat;
        private double cellMaxLat;
        private double cellMinLng;
        private double cellMaxLng;
        private final PointD img;
        private final PointD geo;
        private int imgMinX;
        private int imgMinY;
        private int imgMaxX;
        private int imgMaxY;
        private double x0, x1, x2, x3;
        private double y0, y1, y2, y3;
        private FileChannel channel;
        private int imgIdx;
        private final Dted dted;
        private boolean canceled;

        GridCache(HeatMapParams result, double coverageArea)
                throws NoninvertibleTransformException {

            this.result = result;
            this.result.querySignal.setOnCancelListener(this);

            Arrays.fill(this.result.elevationData, Float.NaN);

            this.queryLevels = _selectDTEDResource(coverageArea);

            final double maxLat = this.result.getMaxLatitude();
            final double minLon = this.result.getMinLongitude();
            final double minLat = this.result.getMinLatitude();
            final double maxLon = this.result.getMaxLongitude();

            this.numCellsX = (int) (Math.floor(maxLon) - Math.floor(minLon))
                    + 1;
            this.numCellsY = (int) (Math.floor(maxLat) - Math.floor(minLat))
                    + 1;

            this.geo2img = Matrix.mapQuads(
                    this.result.upperLeft.getLongitude(),
                    this.result.upperLeft.getLatitude(),
                    this.result.upperRight.getLongitude(),
                    this.result.upperRight.getLatitude(),
                    this.result.lowerRight.getLongitude(),
                    this.result.lowerRight.getLatitude(),
                    this.result.lowerLeft.getLongitude(),
                    this.result.lowerLeft.getLatitude(),
                    0, 0,
                    this.result.xSampleResolution, 0,
                    this.result.xSampleResolution,
                    this.result.ySampleResolution,
                    0, this.result.ySampleResolution);

            if (geo2img == null)
                throw new NoninvertibleTransformException(
                        "null source geo2img");

            // May throw NoninvertibleTransformException
            this.img2geo = geo2img.createInverse();

            this.p = new StringBuilder();
            this.img = new PointD(0.0d, 0.0d);
            this.geo = new PointD(0.0d, 0.0d);
            this.dted = new Dted(512);
        }

        void queryChunks() {

            final float[] altGrid = this.result.elevationData;

            int chunkXMin;
            int chunkYMin;
            int chunkXMax;
            int chunkYMax;
            double chunkMinLat;
            double chunkMinLng;
            double chunkMaxLat;
            double chunkMaxLng;

            //long start = System.currentTimeMillis();

            BitSet[] coverages = Dt2FileWatcher.getInstance().getCoverages();

            for (int celly = 0; celly < numCellsY; celly++) {
                for (int cellx = 0; cellx < numCellsX; cellx++) {

                    // Check if query was canceled
                    if (canceled) {
                        Log.d(TAG, "Canceled busy query");
                        return;
                    }

                    int lat = (int) Math.floor(result.getMaxLatitude() - celly);
                    int lng = (int) Math
                            .floor(result.getMinLongitude() + cellx);

                    // Wrap IDL crossing
                    if (lng >= 180)
                        lng -= 360;
                    else if (lng < -180)
                        lng += 360;

                    int cvIdx = Dt2FileWatcher.getCoverageIndex(lat, lng);
                    if (cvIdx < 0 || cvIdx >= coverages[0].size())
                        continue;

                    outer: for (int level : queryLevels) {

                        if (!coverages[level].get(cvIdx))
                            continue;

                        for (String dtedPath : DTED_PATHS) {
                            // define the MBB for the DTED cell
                            cellMaxLat = Math.ceil(result.getMaxLatitude()
                                    - celly);
                            cellMinLng = Math.floor(result.getMinLongitude()
                                    + cellx);
                            cellMinLat = cellMaxLat - 1;
                            cellMaxLng = cellMinLng + 1;

                            // shrink to the region that intersects with the image
                            cellMaxLat = Math.min(cellMaxLat,
                                    result.getMaxLatitude());
                            cellMinLng = Math.max(cellMinLng,
                                    result.getMinLongitude());
                            cellMinLat = Math.max(cellMinLat,
                                    result.getMinLatitude());
                            cellMaxLng = Math.min(cellMaxLng,
                                    result.getMaxLongitude());

                            channel = null;
                            boolean missingElev = false;
                            try {
                                File file = new File(dtedPath, Dt2FileWatcher
                                        .getRelativePath(level, lat, lng));
                                channel = IOProviderFactory.getChannel(file,
                                        "r");

                                dted.readHeader(
                                        channel,
                                        Math.ceil(result.getMaxLatitude()
                                                - celly),
                                        Math.floor(result.getMinLongitude()
                                                + cellx));

                                chunkXMin = dted.getChunkX(
                                        Math.floor(cellMinLng),
                                        cellMinLng);
                                chunkYMin = dted.getChunkY(
                                        Math.ceil(cellMaxLat),
                                        cellMaxLat);
                                chunkXMax = dted.getChunkX(
                                        Math.floor(cellMinLng),
                                        cellMaxLng);
                                chunkYMax = dted.getChunkY(
                                        Math.ceil(cellMaxLat),
                                        cellMinLat);

                                for (int cy = chunkYMin; cy <= chunkYMax; cy++) {
                                    for (int cx = chunkXMin; cx <= chunkXMax; cx++) {
                                        dted.loadChunk(channel, cx, cy);

                                        chunkMaxLat = Math.ceil(cellMaxLat)
                                                - dted.chunkPixelYToLatitude(
                                                        cy, 0);
                                        chunkMinLng = Math.floor(cellMinLng)
                                                + dted.chunkPixelXToLongitude(
                                                        cx, 0);
                                        chunkMinLat = Math.ceil(cellMaxLat)
                                                - dted.chunkPixelYToLatitude(
                                                        cy,
                                                        dted.chunkRows);
                                        chunkMaxLng = Math.floor(cellMinLng)
                                                + dted.chunkPixelXToLongitude(
                                                        cx,
                                                        dted.chunkCols);

                                        chunkMaxLat = MathUtils.clamp(
                                                chunkMaxLat,
                                                cellMinLat, cellMaxLat);
                                        chunkMinLng = MathUtils.clamp(
                                                chunkMinLng,
                                                cellMinLng, cellMaxLng);
                                        chunkMinLat = MathUtils.clamp(
                                                chunkMinLat,
                                                cellMinLat, cellMaxLat);
                                        chunkMaxLng = MathUtils.clamp(
                                                chunkMaxLng,
                                                cellMinLng, cellMaxLng);

                                        geo.x = chunkMinLng;
                                        geo.y = chunkMaxLat;
                                        geo2img.transform(geo, img);
                                        x0 = img.x;
                                        y0 = img.y;

                                        geo.x = chunkMaxLng;
                                        geo.y = chunkMaxLat;
                                        geo2img.transform(geo, img);
                                        x1 = img.x;
                                        y1 = img.y;

                                        geo.x = chunkMaxLng;
                                        geo.y = chunkMinLat;
                                        geo2img.transform(geo, img);
                                        x2 = img.x;
                                        y2 = img.y;

                                        geo.x = chunkMinLng;
                                        geo.y = chunkMinLat;
                                        geo2img.transform(geo, img);
                                        x3 = img.x;
                                        y3 = img.y;

                                        imgMinX = MathUtils
                                                .clamp((int) MathUtils.min(x0,
                                                        x1,
                                                        x2, x3),
                                                        0,
                                                        result.xSampleResolution
                                                                - 1);
                                        imgMinY = MathUtils
                                                .clamp((int) MathUtils.min(y0,
                                                        y1,
                                                        y2, y3),
                                                        0,
                                                        result.ySampleResolution
                                                                - 1);
                                        imgMaxX = MathUtils.clamp(
                                                (int) Math.ceil(MathUtils.max(
                                                        x0,
                                                        x1, x2, x3)),
                                                0,
                                                result.xSampleResolution - 1);
                                        imgMaxY = MathUtils.clamp(
                                                (int) Math.ceil(MathUtils.max(
                                                        y0,
                                                        y1, y2, y3)),
                                                0,
                                                result.ySampleResolution - 1);

                                        for (int y = imgMinY; y <= imgMaxY; y++) {
                                            for (int x = imgMinX; x <= imgMaxX; x++) {
                                                img.x = x;
                                                img.y = y;
                                                img2geo.transform(img, geo);
                                                if (geo.y < chunkMinLat
                                                        || geo.y > chunkMaxLat
                                                        || geo.x < chunkMinLng
                                                        || geo.x > chunkMaxLng) {
                                                    continue;
                                                }

                                                imgIdx = (y
                                                        * result.xSampleResolution)
                                                        + x;
                                                if (isElevValid(
                                                        altGrid[imgIdx]))
                                                    continue;

                                                double chunkX = MathUtils
                                                        .clamp(dted
                                                                .chunkLongitudeToPixelX(
                                                                        cx,
                                                                        geo.x),
                                                                0,
                                                                dted.chunkCols
                                                                        - 1);
                                                double chunkY = MathUtils
                                                        .clamp(
                                                                dted.chunkLatitudeToPixelY(
                                                                        cy,
                                                                        geo.y),
                                                                0,
                                                                dted.chunkRows
                                                                        - 1);

                                                int chunkLx = (int) chunkX;
                                                int chunkRx = (int) Math
                                                        .ceil(chunkX);
                                                int chunkTy = (int) chunkY;
                                                int chunkBy = (int) Math
                                                        .ceil(chunkY);

                                                float chunkUL = getDtedHeight(
                                                        dted.chunk
                                                                .getShort(
                                                                        (chunkTy * 2
                                                                                * dted.chunkCols)
                                                                                + chunkLx
                                                                                        * 2));
                                                float chunkUR = getDtedHeight(
                                                        dted.chunk
                                                                .getShort(
                                                                        (chunkTy * 2
                                                                                * dted.chunkCols)
                                                                                + chunkRx
                                                                                        * 2));
                                                float chunkLR = getDtedHeight(
                                                        dted.chunk
                                                                .getShort(
                                                                        (chunkBy * 2
                                                                                * dted.chunkCols)
                                                                                + chunkRx
                                                                                        * 2));
                                                float chunkLL = getDtedHeight(
                                                        dted.chunk
                                                                .getShort(
                                                                        (chunkBy * 2
                                                                                * dted.chunkCols)
                                                                                + chunkLx
                                                                                        * 2));

                                                if (Float.isNaN(chunkUL)
                                                        || Float.isNaN(chunkUR)
                                                        || Float.isNaN(chunkLR)
                                                        || Float.isNaN(
                                                                chunkLL)) {
                                                    altGrid[imgIdx] = Float.NaN;
                                                } else {
                                                    final double wR = chunkX
                                                            - chunkLx;
                                                    final double wL = 1.0f - wR;
                                                    final double wB = chunkY
                                                            - chunkTy;
                                                    final double wT = 1.0f - wB;

                                                    altGrid[imgIdx] = 0.0f;

                                                    altGrid[imgIdx] += (float) ((wL
                                                            * wT) * chunkUL);
                                                    altGrid[imgIdx] += (float) ((wR
                                                            * wT) * chunkUR);
                                                    altGrid[imgIdx] += (float) ((wR
                                                            * wB) * chunkLR);
                                                    altGrid[imgIdx] += (float) ((wL
                                                            * wB) * chunkLL);

                                                    // XXX - should we divide to average
                                                    //       out nulls???
                                                }

                                                // identify min/max
                                                if (isElevValid(
                                                        altGrid[imgIdx])) {
                                                    if (result.numSamples == 0) {
                                                        result.minElev = altGrid[imgIdx];
                                                        result.maxElev = altGrid[imgIdx];
                                                    } else if (altGrid[imgIdx] < result.minElev) {
                                                        result.minElev = altGrid[imgIdx];
                                                    } else if (altGrid[imgIdx] > result.maxElev) {
                                                        result.maxElev = altGrid[imgIdx];
                                                    }
                                                    result.numSamples++;
                                                } else
                                                    missingElev = true;
                                            }
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                // an IO error occurred, skip this cell and try the next
                                // extension
                                continue;
                            } finally {
                                IoUtils.close(channel);
                            }
                            if (!missingElev)
                                break outer;
                        }
                    }
                }
            }

            //Log.d(TAG, "Took " + (System.currentTimeMillis() - start) + " ms to query chunks");
        }

        void queryPoints() {
            final float[] altGrid = this.result.elevationData;

            //long start = System.currentTimeMillis();

            BitSet[] coverages = Dt2FileWatcher.getInstance().getCoverages();

            for (int celly = 0; celly < numCellsY; celly++) {
                for (int cellx = 0; cellx < numCellsX; cellx++) {

                    // Check if query was canceled
                    if (canceled) {
                        Log.d(TAG, "Canceled busy query");
                        return;
                    }

                    int lat = (int) Math.floor(result.getMaxLatitude() - celly);
                    int lng = (int) Math
                            .floor(result.getMinLongitude() + cellx);

                    // Wrap IDL crossing
                    if (lng >= 180)
                        lng -= 360;
                    else if (lng < -180)
                        lng += 360;

                    int cvIdx = Dt2FileWatcher.getCoverageIndex(lat, lng);
                    if (cvIdx < 0 || cvIdx >= coverages[0].size())
                        continue;

                    outer: for (int level : queryLevels) {

                        if (!coverages[level].get(cvIdx))
                            continue;

                        for (String dtedPath : DTED_PATHS) {

                            // define the MBB for the DTED cell
                            cellMaxLat = Math.ceil(result.getMaxLatitude()
                                    - celly);
                            cellMinLng = Math.floor(result.getMinLongitude()
                                    + cellx);
                            cellMinLat = cellMaxLat - 1;
                            cellMaxLng = cellMinLng + 1;

                            // shrink to the region that intersects with the image
                            cellMaxLat = Math.min(cellMaxLat,
                                    result.getMaxLatitude());
                            cellMinLng = Math.max(cellMinLng,
                                    result.getMinLongitude());
                            cellMinLat = Math.max(cellMinLat,
                                    result.getMinLatitude());
                            cellMaxLng = Math.min(cellMaxLng,
                                    result.getMaxLongitude());

                            geo.x = cellMinLng;
                            geo.y = cellMaxLat;
                            geo2img.transform(geo, img);
                            x0 = img.x;
                            y0 = img.y;

                            geo.x = cellMaxLng;
                            geo.y = cellMaxLat;
                            geo2img.transform(geo, img);
                            x1 = img.x;
                            y1 = img.y;

                            geo.x = cellMaxLng;
                            geo.y = cellMinLat;
                            geo2img.transform(geo, img);
                            x2 = img.x;
                            y2 = img.y;

                            geo.x = cellMinLng;
                            geo.y = cellMinLat;
                            geo2img.transform(geo, img);
                            x3 = img.x;
                            y3 = img.y;

                            imgMinX = MathUtils.clamp(
                                    (int) MathUtils.min(x0, x1, x2, x3), 0,
                                    result.xSampleResolution - 1);
                            imgMinY = MathUtils.clamp(
                                    (int) MathUtils.min(y0, y1, y2, y3), 0,
                                    result.ySampleResolution - 1);
                            imgMaxX = MathUtils.clamp(
                                    (int) Math.ceil(MathUtils.max(x0, x1, x2,
                                            x3)),
                                    0, result.xSampleResolution - 1);
                            imgMaxY = MathUtils.clamp(
                                    (int) Math.ceil(MathUtils.max(y0, y1, y2,
                                            y3)),
                                    0, result.ySampleResolution - 1);

                            channel = null;
                            boolean missingElev = false;
                            try {
                                File file = new File(dtedPath, Dt2FileWatcher
                                        .getRelativePath(level, lat, lng));
                                channel = IOProviderFactory.getChannel(file,
                                        "r");

                                dted.readHeader(channel, cellMaxLat,
                                        cellMinLng);

                                for (int y = imgMinY; y <= imgMaxY; y++) {
                                    for (int x = imgMinX; x <= imgMaxX; x++) {
                                        img.x = x;
                                        img.y = y;
                                        img2geo.transform(img, geo);
                                        if (geo.y < cellMinLat
                                                || geo.y > cellMaxLat
                                                || geo.x < cellMinLng
                                                || geo.x > cellMaxLng) {
                                            continue;
                                        }

                                        imgIdx = (y * result.xSampleResolution)
                                                + x;

                                        if (!Float.isNaN(altGrid[imgIdx]))
                                            continue;

                                        altGrid[imgIdx] = dted.getHeight(
                                                channel,
                                                geo.y, geo.x);

                                        // identify min/max
                                        if (!Float.isNaN(altGrid[imgIdx])) {
                                            if (result.numSamples == 0) {
                                                result.minElev = altGrid[imgIdx];
                                                result.maxElev = altGrid[imgIdx];
                                            } else if (altGrid[imgIdx] < result.minElev) {
                                                result.minElev = altGrid[imgIdx];
                                            } else if (altGrid[imgIdx] > result.maxElev) {
                                                result.maxElev = altGrid[imgIdx];
                                            }
                                            result.numSamples++;
                                        } else
                                            missingElev = true;
                                    }
                                }
                            } catch (IOException e) {
                                // an IO error occurred, skip this cell and try the next
                                // extension
                                continue;
                            } finally {
                                IoUtils.close(channel);
                            }
                            if (!missingElev)
                                break outer;
                        }
                    }
                }
            }

            //Log.d(TAG, "Took " + (System.currentTimeMillis() - start) + " ms to query points");
        }

        void queryElevationManager() {
            final int numSamples = (result.xSampleResolution
                    * result.ySampleResolution);
            double[] els = new double[numSamples];

            {
                Collection<GeoPoint> pts = new LinkedList<>();
                for (int y = 0; y < result.ySampleResolution; y++) {
                    for (int x = 0; x < result.xSampleResolution; x++) {
                        img.x = x;
                        img.y = y;
                        img2geo.transform(img, geo);
                        pts.add(new GeoPoint(geo.y, geo.x));
                    }
                }

                ElevationData.Hints hints = new ElevationData.Hints();
                hints.preferSpeed = true;
                hints.interpolate = true;
                hints.resolution = DatasetDescriptor.computeGSD(
                        result.xSampleResolution,
                        result.ySampleResolution,
                        result.upperLeft,
                        result.upperRight,
                        result.lowerRight,
                        result.lowerLeft);
                ElevationManager.getElevation(pts.iterator(), els, null, hints);
            }

            result.numSamples = 0;
            for (int i = 0; i < numSamples; i++) {
                if (Double.isNaN(els[i]))
                    continue;
                img.x = (i % result.xSampleResolution);
                img.y = (i / result.xSampleResolution);
                img2geo.transform(img, geo);

                double altMSL = EGM96.getMSL(geo.y, geo.x,
                        els[i]);
                if (!GeoPoint.isAltitudeValid(altMSL)) {
                    result.elevationData[i] = Float.NaN;
                } else {
                    result.elevationData[i] = (float) altMSL;
                    if (result.numSamples == 0) {
                        result.minElev = result.elevationData[i];
                        result.maxElev = result.elevationData[i];
                    } else {
                        if (result.elevationData[i] < result.minElev)
                            result.minElev = result.elevationData[i];
                        else if (result.elevationData[i] > result.maxElev)
                            result.maxElev = result.elevationData[i];
                    }
                    result.numSamples++;
                }
            }
        }

        public void query() {
            ElevationSource.Cursor result;

            final double queryResolution = DatasetDescriptor.computeGSD(
                    this.result.xSampleResolution,
                    this.result.ySampleResolution,
                    this.result.upperLeft,
                    this.result.upperRight,
                    this.result.lowerRight,
                    this.result.lowerLeft);

            // build out the params for the AOI
            ElevationSource.QueryParameters params = new ElevationSource.QueryParameters();
            params.spatialFilter = DatasetDescriptor.createSimpleCoverage(
                    this.result.upperLeft,
                    this.result.upperRight,
                    this.result.lowerRight,
                    this.result.lowerLeft);

            // check to see if the elevation data is all DTED
            boolean isAllDted = DeveloperOptions.getIntOption(
                    "heatmap.force-elevation-mgr", 0) == 0;

            result = null;
            try {
                /*Log.d(TAG, "Querying sources " + this.result.upperLeft + ", "
                        + this.result.upperRight + ", "
                        + this.result.lowerRight + ", "
                        + this.result.lowerLeft);
                long start = System.currentTimeMillis();*/
                result = ElevationManager.queryElevationSources(params);
                while (result.moveToNext()) {
                    isAllDted &= result.getType()
                            .toLowerCase(LocaleUtil.getCurrent())
                            .startsWith("dted");
                }
                /*Log.d(TAG, "Finished sources query in "
                        + (System.currentTimeMillis() - start) + " ms");*/
            } finally {
                if (result != null)
                    result.close();
            }

            if (canceled) {
                Log.d(TAG, "Canceled busy query");
                return;
            }

            if (isAllDted) {
                // use the legacy implementation for DTED which outperforms the
                // more generic ElevationManager implementation currently

                // XXX - would be good to drive file selection by query results
                if (this.result.quick)
                    this.queryPoints();
                else
                    this.queryChunks();
            } else {
                // clamp so the value is greater than 0.
                final int maxSamples = Math.max(DeveloperOptions.getIntOption(
                        "heatmap.query-max-samples", 150), 1);

                final int ntx = (this.result.xSampleResolution - 1)
                        / maxSamples + 1;
                final int nty = (this.result.ySampleResolution - 1)
                        / maxSamples + 1;

                if (ntx == 1 && nty == 1) {
                    this.queryElevationManager();
                } else {
                    this.result.numSamples = 0;

                    ElevationData.Hints hints = new ElevationData.Hints();
                    hints.preferSpeed = true;
                    hints.interpolate = true;
                    hints.resolution = DatasetDescriptor.computeGSD(
                            this.result.xSampleResolution,
                            this.result.ySampleResolution,
                            this.result.upperLeft,
                            this.result.upperRight,
                            this.result.lowerRight,
                            this.result.lowerLeft);

                    double[] samples = new double[maxSamples * maxSamples];
                    ArrayList<GeoPoint> points = new ArrayList<>(
                            maxSamples * maxSamples);
                    for (int ty = 0; ty < nty; ty++) {
                        for (int tx = 0; tx < ntx; tx++) {
                            final int nrows = Math.min(maxSamples,
                                    this.result.ySampleResolution
                                            - (ty * maxSamples));
                            final int ncols = Math.min(maxSamples,
                                    this.result.xSampleResolution
                                            - (tx * maxSamples));
                            // build out the points
                            points.clear();
                            for (int y = 0; y < nrows; y++) {
                                for (int x = 0; x < ncols; x++) {
                                    img.x = x + (tx * maxSamples);
                                    img.y = y + (ty * maxSamples);
                                    img2geo.transform(img, geo);
                                    points.add(new GeoPoint(geo.y, geo.x));
                                }
                            }
                            ElevationManager.getElevation(points.iterator(),
                                    samples, null, hints);
                            for (int i = 0; i < (nrows * ncols); i++) {
                                if (Double.isNaN(samples[i]))
                                    continue;
                                final int resultX = (i % ncols)
                                        + (tx * maxSamples);
                                final int resultY = (i / ncols)
                                        + (ty * maxSamples);
                                final int resultIdx = (resultY
                                        * this.result.xSampleResolution)
                                        + resultX;
                                final GeoPoint geo = points.get(i);

                                double altMSL = EGM96.getMSL(
                                        geo.getLatitude(), geo.getLongitude(),
                                        samples[i]);
                                if (!GeoPoint.isAltitudeValid(altMSL)) {
                                    this.result.elevationData[resultIdx] = Float.NaN;
                                } else {
                                    this.result.elevationData[resultIdx] = (float) altMSL;
                                    if (this.result.numSamples == 0) {
                                        this.result.minElev = this.result.elevationData[resultIdx];
                                        this.result.maxElev = this.result.elevationData[resultIdx];
                                    } else {
                                        if (this.result.elevationData[resultIdx] < this.result.minElev)
                                            this.result.minElev = this.result.elevationData[resultIdx];
                                        else if (this.result.elevationData[resultIdx] > this.result.maxElev)
                                            this.result.maxElev = this.result.elevationData[resultIdx];
                                    }
                                    this.result.numSamples++;
                                }
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void onCancel() {
            this.canceled = true;
        }
    }

    public static class Dted {
        private final byte[] array;
        private final ByteBuffer buffer;
        private int extentX;
        private int extentY;
        private long dataRecSize;

        final int chunkSize;

        private final ByteBuffer chunk;
        private int numChunksX;
        private int numChunksY;
        private int chunkRows;
        private int chunkCols;
        private double cellLat;
        private double cellLng;

        public Dted(int chunkSize) {
            this.chunkSize = chunkSize;

            this.array = new byte[8];
            this.buffer = ByteBuffer.wrap(this.array);

            this.chunk = com.atakmap.lang.Unsafe.allocateDirect(this.chunkSize
                    * (this.chunkSize + 1) * 2);
            this.chunk.order(ByteOrder.BIG_ENDIAN);
        }

        public int getChunkX(double leftLng, double longitude) {
            final double lngRatio = longitude - leftLng;
            final double xd = lngRatio * (this.extentX - 1);

            return (int) xd / this.chunkSize;
        }

        public int getChunkY(double upperLat, double latitude) {
            final double latRatio = upperLat - latitude;

            final double yd = latRatio * (this.extentY - 1);
            return (int) yd / this.chunkSize;
        }

        public double chunkPixelXToLongitude(int chunkX, double px) {
            double fullX = chunkX * this.chunkSize + px;
            return fullX / (double) (this.extentX - 1);
        }

        public double chunkPixelYToLatitude(int chunkY, double py) {
            double fullY = chunkY * this.chunkSize + py;
            return fullY / (double) (this.extentY - 1);
        }

        public double chunkLongitudeToPixelX(int chunkX, double lng) {
            return ((this.extentX - 1) * (lng - cellLng))
                    - (chunkX * this.chunkSize);
        }

        public double chunkLatitudeToPixelY(int chunkY, double lat) {
            return ((this.extentY - 1) * (cellLat - lat))
                    - (chunkY * this.chunkSize);
        }

        public void readHeader(FileChannel channel, double cellLat,
                double cellLng) throws IOException {
            channel.position(_NUM_LNG_LINES_OFFSET);

            this.buffer.clear();
            int retval = channel.read(buffer);
            if (retval < 8)
                throw new EOFException();

            this.buffer.flip();

            if (this.buffer.remaining() < 8)
                throw new EOFException();

            try {
                this.extentX = Integer.parseInt(new String(this.array, 0, 4,
                        StandardCharsets.US_ASCII));
            } catch (NumberFormatException nfe) {
                throw new IOException(nfe);
            }

            try {
                this.extentY = Integer.parseInt(new String(this.array, 4, 4,
                        StandardCharsets.US_ASCII));
            } catch (NumberFormatException nfe) {
                throw new IOException(nfe);
            }

            this.dataRecSize = _DATA_RECORD_PREFIX_SIZE + (this.extentY * 2)
                    + _DATA_RECORD_SUFFIX_SIZE;

            this.numChunksX = (int) Math.ceil((double) this.extentX
                    / (double) this.chunkSize);
            this.numChunksY = (int) Math.ceil((double) this.extentY
                    / (double) this.chunkSize);

            this.cellLat = cellLat;
            this.cellLng = cellLng;
        }

        public float getHeight(FileChannel channel, double latitude,
                double longitude) throws IOException {
            final double latRatio = (latitude - Math.floor(latitude));
            final double lngRatio = (longitude - Math.floor(longitude));

            final double yd = latRatio * (this.extentY - 1);
            final double xd = lngRatio * (this.extentX - 1);

            long x = (long) xd;
            long y = (long) yd;

            final long byteOffset = _HEADER_OFFSET
                    + x * this.dataRecSize
                    + _DATA_RECORD_PREFIX_SIZE
                    + y * 2;

            this.buffer.order(ByteOrder.BIG_ENDIAN);

            channel.position(byteOffset);

            this.buffer.clear();
            this.buffer.limit(4);
            int retval = channel.read(this.buffer);
            if (retval < 4)
                throw new EOFException();

            this.buffer.flip();

            float chunkUL = getDtedHeight(this.buffer.getShort());
            float chunkLL = getDtedHeight(this.buffer.getShort());

            channel.position(channel.position() + this.dataRecSize - 4);

            this.buffer.clear();
            this.buffer.limit(4);

            retval = channel.read(this.buffer);
            if (retval < 4)
                throw new EOFException();

            this.buffer.flip();

            float chunkUR = getDtedHeight(this.buffer.getShort());
            float chunkLR = getDtedHeight(this.buffer.getShort());

            if (Float.isNaN(chunkUL)
                    || Float.isNaN(chunkUR)
                    || Float.isNaN(chunkLR)
                    || Float.isNaN(chunkLL)) {
                return Float.NaN;
            } else {
                final double wR = xd - x;
                final double wL = 1.0f - wR;
                final double wB = yd - y;
                final double wT = 1.0f - wB;

                float val = 0.0f;

                val += (float) ((wL * wT) * chunkUL);
                val += (float) ((wR * wT) * chunkUR);
                val += (float) ((wR * wB) * chunkLR);
                val += (float) ((wL * wB) * chunkLL);

                // XXX - should we divide to average
                //       out nulls???

                return val;
            }
        }

        public void loadChunk(FileChannel channel, int chunkX, int chunkY)
                throws IOException {
            //chunkY = this.numChunksY-1-chunkY;

            if (chunkX < 0 || chunkX >= this.numChunksX)
                throw new IllegalArgumentException();
            if (chunkY < 0 || chunkY >= this.numChunksY)
                throw new IllegalArgumentException();

            int dstX = chunkX * this.chunkSize;
            int dstY = chunkY * this.chunkSize;
            int dstW = Math.min(this.chunkSize, this.extentX - dstX);
            int dstH = Math.min(this.chunkSize, this.extentY - dstY);

            this.chunk.clear();
            this.chunk.limit(0);

            int srcX;
            int srcY;

            for (int x = 0; x < dstW; x++) {
                srcX = (this.extentY - 1) - (dstY + dstH - 1);
                srcY = (dstX + x);

                channel.position(_HEADER_OFFSET + (srcY * this.dataRecSize)
                        + _DATA_RECORD_PREFIX_SIZE + (srcX * 2));
                this.chunk.limit(this.chunk.capacity());
                this.chunk.position(this.chunk.capacity() - (dstH * 2));
                int retval = channel.read(this.chunk);
                if (retval < 0)
                    throw new EOFException();

                this.chunk.clear();
                for (int y = 0; y < dstH; y++) {
                    srcX = (dstH - 1) - y;
                    this.chunk.putShort(
                            ((y * dstW) + x) * 2,
                            this.chunk.getShort(this.chunk.capacity()
                                    - (dstH * 2) + (2 * srcX)));
                }
            }

            this.chunkCols = dstW;
            this.chunkRows = dstH;

            this.chunk.clear();
            this.chunk.limit(this.chunkCols * this.chunkRows * 2);
        }

        public int getChunkColumns() {
            return this.chunkCols;
        }

        public int getChunkRows() {
            return this.chunkRows;
        }

        public int getNumChunksX() {
            return this.numChunksX;
        }

        public int getNumChunksY() {
            return this.numChunksY;
        }

        public ByteBuffer getChunk() {
            return this.chunk;
        }
    }

    class HeatMapState extends ViewState {
        public float alpha;
        public float saturation;
        public float value;

        @Override
        public void set(GLMapView view) {
            super.set(view);

            this.alpha = GLHeatMap.this.alpha;
            this.saturation = GLHeatMap.this.saturation;
            this.value = GLHeatMap.this.value;
        }

        @Override
        public void copy(ViewState view) {
            super.copy(view);

            final HeatMapState state = (HeatMapState) view;
            this.alpha = state.alpha;
            this.saturation = state.saturation;
            this.value = state.value;
        }
    }
}
