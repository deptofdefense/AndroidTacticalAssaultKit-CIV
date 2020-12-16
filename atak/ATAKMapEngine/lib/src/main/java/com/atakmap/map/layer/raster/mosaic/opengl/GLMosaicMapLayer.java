
package com.atakmap.map.layer.raster.mosaic.opengl;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import org.gdal.gdal.Dataset;

import android.net.Uri;
import android.util.Pair;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.LegacyAdapters;
import com.atakmap.map.MapControl;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.control.ColorControl;
import com.atakmap.map.layer.control.Controls;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.DefaultDatasetProjection2;
import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.map.layer.raster.MosaicDatasetDescriptor;
import com.atakmap.map.layer.raster.PrecisionImagery;
import com.atakmap.map.layer.raster.PrecisionImageryFactory;
import com.atakmap.map.layer.raster.RasterDataAccess2;
import com.atakmap.map.layer.raster.controls.ImagerySelectionControl;
import com.atakmap.map.layer.raster.gdal.GdalDatasetProjection2;
import com.atakmap.map.layer.raster.gdal.GdalLayerInfo;
import com.atakmap.map.layer.raster.gdal.GdalTileReader;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabase2.Frame;
import com.atakmap.map.layer.raster.mosaic.MosaicDatabaseFactory2;
import com.atakmap.map.layer.raster.mosaic.MosaicFrameColorControl;
import com.atakmap.map.layer.raster.opengl.GLMapLayer3;
import com.atakmap.map.layer.raster.opengl.GLMapLayerSpi3;
import com.atakmap.map.layer.raster.service.RasterDataAccessControl;
import com.atakmap.map.layer.raster.tilereader.AndroidTileReader;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory;
import com.atakmap.map.layer.raster.tilereader.opengl.GLQuadTileNode2;
import com.atakmap.map.layer.raster.tilereader.opengl.GLQuadTileNode3;
import com.atakmap.map.opengl.GLAsynchronousMapRenderable;
import com.atakmap.map.opengl.GLMapRenderable;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.map.opengl.GLResolvableMapRenderable;
import com.atakmap.util.ConfigOptions;
import com.atakmap.util.Disposable;
import com.atakmap.util.Filter;
import com.atakmap.util.MultiCollection;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.math.Rectangle;

public class GLMosaicMapLayer extends
    GLAsynchronousMapRenderable<MosaicPendingData> implements GLMapLayer3 {

    public static final String TAG = "GLMosaicMapLayer";

    public final static GLMapLayerSpi3 SPI = new GLMapLayerSpi3() {
        @Override
        public GLMapLayer3 create(Pair<MapRenderer, DatasetDescriptor> arg) {
            final MapRenderer surface = arg.first;
            final DatasetDescriptor info = arg.second;
            if (!info.getDatasetType().equals("native-mosaic"))
                return null;
            if(!(info instanceof MosaicDatasetDescriptor))
                return null;

            final MosaicDatasetDescriptor mosaic = (MosaicDatasetDescriptor)info;
            File dbFile = mosaic.getMosaicDatabaseFile();
            if (dbFile == null || !IOProviderFactory.exists(dbFile) || IOProviderFactory.length(dbFile) < 1) {
                Log.e(TAG, "Mosaic database does not exist for dataset " + info.getUri());
                return null;
            }
            if(!MosaicDatabaseFactory2.canCreate(mosaic.getMosaicDatabaseProvider())) {
                Log.e(TAG, "No database provider for dataset " + info.getUri());
                return null;
            }
            return new GLMosaicMapLayer(surface, mosaic);
        }

        @Override
        public int getPriority() {
            // MosaicDatasetDescriptor : DatasetDescriptor
            return 1;
        }
    };

    private final static Comparator<MosaicDatabase2.Frame> FRAME_STORAGE_COMPARATOR = new Comparator<MosaicDatabase2.Frame>() {
        @Override
        public int compare(MosaicDatabase2.Frame f0, MosaicDatabase2.Frame f1) {
            // if same path, always same
            final int path = f0.path.compareTo(f1.path);
            if(path == 0)
                return 0;

            // with request, sort based on GSD, then type, then path
            if (f0.maxGsd < f1.maxGsd)
                return 1;
            else if (f0.maxGsd > f1.maxGsd)
                return -1;
            int retval = f0.type.compareTo(f1.type);
            if (retval == 0)
                retval = path;
            return retval;
        }
    };

    private final static Set<String> USE_FRAME_COORDS_AS_PROJ = new HashSet<String>();
    static {
        USE_FRAME_COORDS_AS_PROJ.add("PRI");
        USE_FRAME_COORDS_AS_PROJ.add("PFI");
        USE_FRAME_COORDS_AS_PROJ.add("kmz");
    }

    /**************************************************************************/

    /** the currently visible frames */
    protected NavigableMap<MosaicDatabase2.Frame, GLResolvableMapRenderable> visibleFrames;

    /**
     * previously visible frames that are still in the ROI and should be released once all
     * {@link #visibleFrames} are resolved.
     */
    private Map<MosaicDatabase2.Frame, GLResolvableMapRenderable> zombieFrames;
    /**
     * A view of the subset of {@link #visibleFrames} that were in the zombie list during the
     * previous call to {@link #draw}, but have been moved into the visible list. Each member should
     * have its <code>resume</code> method invoked on the GL thread before the next call
     * <code>super.draw</code>.
     */
    private Collection<GLResolvableMapRenderable> resurrectedFrames;;

    private Collection<GLResolvableMapRenderable> renderList;

    private DatasetDescriptor info;
    private String baseUri;
    private boolean relativePaths;

    private MapRenderer surface;

    protected final TileReader.AsynchronousIO asyncio;
    protected final boolean ownsIO;

    protected final boolean textureCacheEnabled;

    protected Set<MapControl> controls;
    protected final RasterDataAccessControl rasterAccessControl;
    private final ColorControlImpl colorControlImpl;
    protected final ColorControl colorControl;
    protected final MosaicFrameColorControl frameColorControl;
    private final ImagerySelectionControl selectControl;

    private ImagerySelectionControl.Mode resolutionSelectMode;
    private Set<String> imageryTypeFilter;

    public GLMosaicMapLayer(MapRenderer surface, MosaicDatasetDescriptor info) {
        this.surface = surface;
        this.info = info;

        this.relativePaths = DatasetDescriptor.getExtraData(info, "relativePaths", "true").equals("true");
        this.visibleFrames = new TreeMap<>(FRAME_STORAGE_COMPARATOR);
        this.zombieFrames = new TreeMap<>(FRAME_STORAGE_COMPARATOR);

        this.renderList = new MultiCollection<GLResolvableMapRenderable>(Arrays.<Collection<? extends GLResolvableMapRenderable>>asList(this.zombieFrames.values(), this.visibleFrames.values()));

        this.resurrectedFrames = new LinkedList<>();

        this.baseUri = null;

        if(info.getLocalData("asyncio") != null) {
            this.asyncio = (TileReader.AsynchronousIO)info.getLocalData("asyncio");
            this.ownsIO = false;
        } else if(ConfigOptions.getOption("imagery.single-async-io", 0) != 0) {
            this.asyncio = TileReader.getMasterIOThread();
            this.ownsIO = false;
        } else {
            this.asyncio = new TileReader.AsynchronousIO();
            this.ownsIO = true;
        }

        this.textureCacheEnabled = (ConfigOptions.getOption("imagery.texture-cache", 1) != 0);

        this.controls = Collections.newSetFromMap(new IdentityHashMap<MapControl, Boolean>());

        this.rasterAccessControl = new RasterDataAccessControlImpl();

        this.colorControlImpl = new ColorControlImpl();
        this.colorControl = this.colorControlImpl;
        this.frameColorControl = this.colorControlImpl;
        this.selectControl = new SelectControlImpl();

        this.imageryTypeFilter = null;
        this.resolutionSelectMode = ImagerySelectionControl.Mode.MinimumResolution;

        this.controls.add(this.rasterAccessControl);
        this.controls.add(this.colorControl);
        this.controls.add(this.frameColorControl);
        this.controls.add(this.selectControl);
    }

    /**************************************************************************/

    @Override
    public synchronized void draw(GLMapView view) {
        // clean up the zombie list
        if(!this.zombieFrames.isEmpty()) {
            boolean allResolved = true;
            for (Map.Entry<MosaicDatabase2.Frame, GLResolvableMapRenderable> entry : visibleFrames.entrySet()) {
                // check if resolved
                allResolved &= (entry.getValue().getState() == GLResolvableMapRenderable.State.RESOLVED);
            }


            Iterator<Map.Entry<MosaicDatabase2.Frame, GLResolvableMapRenderable>> zombieIter = this.zombieFrames.entrySet().iterator();
            while (zombieIter.hasNext()) {
                final Map.Entry<MosaicDatabase2.Frame, GLResolvableMapRenderable> entry = zombieIter.next();
                final MosaicDatabase2.Frame frame = entry.getKey();
                final GLResolvableMapRenderable resolvable = entry.getValue();
                if (allResolved ||
                        !intersects(this.preparedState, frame)) {

                    // all visible frames are resolved, the zombie frame has no
                    // data or has fallen out of the ROI; release it
                    zombieIter.remove();
                    resolvable.release();
                } else if (resolvable.getState() == GLResolvableMapRenderable.State.RESOLVING) {
                    resolvable.suspend();
                }
            }
        }
        // resurrected frames have already been moved from the zombie or
        // retention lists to the visible list; we just need to tell them to
        // resume
        for(GLResolvableMapRenderable resurrected : this.resurrectedFrames)
            resurrected.resume();
        this.resurrectedFrames.clear();

        // apply any color mmodulation settings
        ((ColorControlImpl)this.colorControl).apply();

        super.draw(view);

        //System.out.println("$$ GLGdalMosaicMapLayer [" + this.info.getName() + "] draw " + this.renderable.size());
    }

    @Override
    protected Collection<GLResolvableMapRenderable> getRenderList() {
        return this.renderList;
    }

    @Override
    protected void resetPendingData(MosaicPendingData pendingData) {
        pendingData.frames.clear();
        pendingData.spatialCalc.clear();

        pendingData.loaded.clear();
        for(MosaicDatabase2.Frame frame : this.visibleFrames.keySet())
            pendingData.loaded.add(this.resolvePath(frame.path));
    }

    @Override
    protected void releasePendingData(MosaicPendingData pendingData) {
        pendingData.frames.clear();
        pendingData.spatialCalc.dispose();
        pendingData.loaded.clear();

        // NOTE: the renderables contained in the map have been instantiated but
        //       not yet initialized. This is an important distinction as we
        //       must invoke release to free any non-GL resources owned by the
        //       renderable but we may do so OFF of the GL thread
        for(GLMapRenderable r : pendingData.renderablePreload.values())
            r.release();
        pendingData.renderablePreload.clear();

        if(pendingData.database != null) {
            pendingData.database.close();
            pendingData.database = null;
        }
    }

    @Override
    protected MosaicPendingData createPendingData() {
        final MosaicDatasetDescriptor mosaic = (MosaicDatasetDescriptor)this.info;
        final MosaicPendingData retval = new MosaicPendingData();

        try {
            retval.database = MosaicDatabaseFactory2.create(mosaic.getMosaicDatabaseProvider());
            if(retval.database != null)
                retval.database.open(mosaic.getMosaicDatabaseFile());
        } catch(Throwable t) {
            Log.e(TAG, "Failed to open mosaic " + this.info.getUri());
        }

        return retval;
    }

    @Override
    protected boolean updateRenderableReleaseLists(MosaicPendingData pendingData) {
        //long s = android.os.SystemClock.elapsedRealtime();

        // establish list of renderables that need to be released, initialized
        // with all current renderables
        Map<MosaicDatabase2.Frame, GLResolvableMapRenderable> toRelease = new HashMap<>(this.visibleFrames);

        // move any previously visible nodes into our nowVisible map
        for (MosaicDatabase2.Frame frame : pendingData.frames) {
            // visible, do not release
            toRelease.remove(frame);

            GLResolvableMapRenderable renderable = this.visibleFrames.get(frame);
            if (renderable != null)
                continue; // no-op

            if(this.zombieFrames.containsKey(frame)) {
                // transfer from the zombie list back to the visible list and resurrect
                renderable = this.zombieFrames.remove(frame);
                this.resurrectedFrames.add(renderable);
            } else if(pendingData.renderablePreload.containsKey(frame)) {
                renderable = pendingData.renderablePreload.remove(frame);

                // apply color modulation to the newly created renderable
                ((ColorControlImpl)this.colorControl).apply(frame, renderable);
            } else {
                // the frame is not in any of the lists and intersects the view,
                // create a node for it
                renderable = this.createRootNode(frame);

                // apply color modulation to the newly created renderable
                ((ColorControlImpl)this.colorControl).apply(frame, renderable);
            }

                // add it to the list of frames that are now visible
            if(renderable != null)
                this.visibleFrames.put(frame, renderable);
        }
        pendingData.frames.clear();

        // any resurrected frames are marked, clear and rebuild the zombie list
        toRelease.putAll(this.zombieFrames);
        this.zombieFrames.clear();

        Iterator<Map.Entry<MosaicDatabase2.Frame, GLResolvableMapRenderable>> releaseIter = toRelease
                .entrySet().iterator();
        while (releaseIter.hasNext()) {
            final Map.Entry<MosaicDatabase2.Frame, GLResolvableMapRenderable> entry = releaseIter.next();
            final MosaicDatabase2.Frame frame = entry.getKey();
            final GLResolvableMapRenderable mapRenderable = entry.getValue();

            // if the frame marked for release intersects the current ROI,
            // transfer it to the zombie list
            if (intersects(this.targetState, frame)) {
                // transfer from previously visible to zombie
                this.visibleFrames.remove(frame);
                this.zombieFrames.put(frame, mapRenderable);

                // remove from release
                releaseIter.remove();
            }
        }

        // retain a reference to the renderables that need to be released
        final Collection<GLResolvableMapRenderable> releaseList = toRelease.values();
        for(MosaicDatabase2.Frame frame : toRelease.keySet())
            visibleFrames.remove(frame);


        this.surface.queueEvent(new Runnable() {
            @Override
            public void run() {
                for(GLResolvableMapRenderable renderable : releaseList) {
                    renderable.release();
                    if(renderable instanceof Disposable)
                        ((Disposable)renderable).dispose();
                }
                releaseList.clear();
            }
        });

        // NOTE: the renderables contained in the map have been instantiated but
        //       not yet initialized. This is an important distinction as we
        //       must invoke release to free any non-GL resources owned by the
        //       renderable but we may do so OFF of the GL thread
        for(GLMapRenderable renderable : pendingData.renderablePreload.values())
            renderable.release();
        pendingData.renderablePreload.clear();
        
        //long e = android.os.SystemClock.elapsedRealtime();
        //System.out.println("GLGdalMosaicMapLayer@" + Integer.toString(this.hashCode(), 16) + " updateRenderableList in " + (e-s) + "ms");
        return true;
    }

    @Override
    protected String getBackgroundThreadName() {
        return "Mosaic [" + this.info.getName() + "] GL worker@" + Integer.toString(this.hashCode(), 16);
    }
    
    @Override
    protected void query(ViewState state, MosaicPendingData retval) {
        if (retval.database == null)
            return;

        final ViewState localQuery = (ViewState) state;
        if(localQuery.crossesIDL) {
            int processed = 0;

            ViewState hemi = this.newViewStateInstance();

            // west of IDL
            hemi.copy(localQuery);
            hemi.eastBound = 180d;
            hemi.upperLeft.set(hemi.northBound, hemi.westBound);
            hemi.upperRight.set(hemi.northBound, hemi.eastBound);
            hemi.lowerRight.set(hemi.southBound, hemi.eastBound);
            hemi.lowerLeft.set(hemi.southBound, hemi.westBound);
            this.queryImpl(hemi, retval);
            
            // east of IDL
            hemi.copy(localQuery);
            hemi.westBound = -180d;
            hemi.upperLeft.set(hemi.northBound, hemi.westBound);
            hemi.upperRight.set(hemi.northBound, hemi.eastBound);
            hemi.lowerRight.set(hemi.southBound, hemi.eastBound);
            hemi.lowerLeft.set(hemi.southBound, hemi.westBound);
            this.queryImpl(hemi, retval);
        } else {
            queryImpl(localQuery, retval);
        }
    }

    private List<MosaicDatabase2.QueryParameters> constructQueryParams(ViewState localQuery) {
        MosaicDatabase2.QueryParameters base = new MosaicDatabase2.QueryParameters();
        base.spatialFilter = DatasetDescriptor.createSimpleCoverage(localQuery.upperLeft,
                localQuery.upperRight,
                localQuery.lowerRight,
                localQuery.lowerLeft);

        base.types = this.imageryTypeFilter;

        List<MosaicDatabase2.QueryParameters> retval = new LinkedList<MosaicDatabase2.QueryParameters>();

        final ImagerySelectionControl.Mode resSelectMode = this.resolutionSelectMode;
        switch(resSelectMode) {
            case IgnoreResolution:
                retval.add(base);
                break;
            case MaximumResolution:
                base.maxGsd = localQuery.drawMapResolution / 2d;
                base.maxGsdCompare = MosaicDatabase2.QueryParameters.GsdCompare.MaximumGsd;
                retval.add(base);
                break;
            case MinimumResolution:
                MosaicDatabase2.QueryParameters asc = new MosaicDatabase2.QueryParameters(base);
                asc.maxGsd = localQuery.drawMapResolution / 2d;
                asc.maxGsdCompare = MosaicDatabase2.QueryParameters.GsdCompare.MinimumGsd;
                asc.order = MosaicDatabase2.QueryParameters.Order.MaxGsdDesc;
                retval.add(asc);
                MosaicDatabase2.QueryParameters desc = new MosaicDatabase2.QueryParameters(base);
                desc.minGsd = localQuery.drawMapResolution / 2d;
                desc.minGsdCompare = MosaicDatabase2.QueryParameters.GsdCompare.MaximumGsd;
                desc.maxGsd = localQuery.drawMapResolution / 16d;
                asc.maxGsdCompare = MosaicDatabase2.QueryParameters.GsdCompare.MinimumGsd;
                desc.order = MosaicDatabase2.QueryParameters.Order.MaxGsdAsc;
                retval.add(desc);
                break;
        }

        return retval;
    }
    
    private void queryImpl(ViewState localQuery, MosaicPendingData retval) {
        if(this.checkQueryThreadAbort())
            return;

        //Log.i(TAG, "QUERY ON " + localQuery.upperLeft + " (" + localQuery._left + "," + localQuery._top + ") " + localQuery.lowerRight + " (" + localQuery._right + "," + localQuery._bottom + ") @ " + localQuery.drawMapResolution);

        // compute the amount of buffering to be added around the bounds of the
        // frame -- target 2 pixels
        final double deltaPxX = (double) (localQuery._right - localQuery._left);
        final double deltaPxY = (double) (localQuery._top - localQuery._bottom);
        final double distancePx = Math.sqrt(deltaPxX * deltaPxX + deltaPxY * deltaPxY);
        final double deltaLat = localQuery.northBound - localQuery.southBound;
        final double deltaLng = localQuery.eastBound - localQuery.westBound;
        final double distanceDeg = Math.sqrt(deltaLat * deltaLat + deltaLng * deltaLng);

        final double bufferArg = (distanceDeg / distancePx) * 2;

        MosaicDatabase2.Cursor result = null;

        //int processed = 0;
        // wrap everything in a transaction
        retval.spatialCalc.beginBatch();
        try {
            long viewHandle = retval.spatialCalc.createPolygon(localQuery.upperLeft,
                    localQuery.upperRight,
                    localQuery.lowerLeft,
                    localQuery.lowerRight);
            long coverageHandle = 0L;
            long frameHandle = 0L;

            List<MosaicDatabase2.QueryParameters> params = this.constructQueryParams(localQuery);
outer:      for(MosaicDatabase2.QueryParameters p : params) {
                try {
                    // query the database over the spatial ROI
                    result = retval.database.query(p);

                    String selectedType = null;

                    while (result.moveToNext()) {
                        if (this.checkQueryThreadAbort())
                            break outer;

                        //processed++;
                        frameHandle = retval.spatialCalc.createPolygon(result.getUpperLeft(),
                                result.getUpperRight(),
                                result.getLowerRight(),
                                result.getLowerLeft());

                        if (coverageHandle == 0L) {
                            coverageHandle = frameHandle;
                        } else {
                            // if the current coverage contains the frame, skip it
                            try {
                                if (retval.spatialCalc.contains(coverageHandle, frameHandle))
                                    continue;
                            } catch (Throwable t) {
                                Log.e(TAG, "error: ", t);
                            }
                            // create the union of the current coverage plus the frame.
                            // specify a small buffer on the frame to try to prevent
                            // numerically insignificant seams between adjacent frames
                            // that would result in effectively occluded frames ending
                            // up in the render list
                            try {
                                retval.spatialCalc.buffer(frameHandle, bufferArg, frameHandle);
                                retval.spatialCalc.union(coverageHandle, frameHandle, coverageHandle);
                            } catch (Throwable t) {
                                Log.e(TAG, "error: ", t);
                            }
                        }

                        // XXX - we're using the ID to indicate the pump in which it was fetched to sort zombie frames to the back
                        MosaicDatabase2.Frame frame = new MosaicDatabase2.Frame(
                                localQuery.drawVersion,
                                result.getPath(),
                                result.getType(),
                                result.isPrecisionImagery(),
                                result.getMinLat(),
                                result.getMinLon(),
                                result.getMaxLat(),
                                result.getMaxLon(),
                                result.getUpperLeft(),
                                result.getUpperRight(),
                                result.getLowerRight(),
                                result.getLowerLeft(),
                                result.getMinGSD(),
                                result.getMaxGSD(),
                                result.getWidth(),
                                result.getHeight(),
                                result.getSrid()
                        );
                        frame = result.asFrame();

                        retval.frames.add(frame);
                        if (selectedType == null)
                            selectedType = result.getType();
                        // if the current coverage contains the ROI, break
                        try {
                            if (retval.spatialCalc.contains(coverageHandle, viewHandle))
                                break outer;
                        } catch (Throwable t) {
                            Log.e(TAG, "error: ", t);
                        }
                    }

                    this.info.setLocalData("selectedType", selectedType);
                } finally {
                    if (result != null)
                        result.close();
                }
            }
        } catch(Throwable t) {
            Log.e(TAG, "Data query unexpectedly failed, results may be incomplete.", t);
        } finally {
            // dump everything -- we don't need it to persist
            retval.spatialCalc.endBatch(false);
        }
    }

    @Override
    protected void initImpl(GLMapView view) {
        this.baseUri = GdalLayerInfo.getGdalFriendlyUri(this.info);
        if (this.baseUri.length() > 0 && this.baseUri.charAt(this.baseUri.length() - 1) == File.separatorChar)
            this.baseUri = this.baseUri.substring(0, this.baseUri.length() - 1);
        if(this.baseUri.startsWith("file:///"))
            this.baseUri = this.baseUri.substring(7);
        else if(this.baseUri.startsWith("file://"))
            this.baseUri = this.baseUri.substring(6);
    }
    
    @Override
    protected void releaseImpl() {
        super.releaseImpl();
        if(this.ownsIO)
            this.asyncio.release();
    }

    protected GLResolvableMapRenderable createRootNode(MosaicDatabase2.Frame frame) {
        String tileCacheDatabase = null;
        /*
        if (GLMosaicMapLayer.this.info.getExtraData("tilecacheDir") != null) {
            String tilecachePath = GLMosaicMapLayer.this.info.getExtraData("tilecacheDir");
            tileCacheDatabase = tilecachePath + File.pathSeparator + String.valueOf(frame.id);
        }
         */
        
        TileReaderFactory.Options opts = new TileReaderFactory.Options();
        opts.asyncIO = this.asyncio;
        opts.cacheUri = tileCacheDatabase;
        opts.preferredTileWidth = 256;
        opts.preferredTileHeight = 256;

        final GLQuadTileNode3.Options glopts = new GLQuadTileNode3.Options();
        glopts.levelTransitionAdjustment = -1d * ConfigOptions.getOption("imagery.zoom-level-adjust", 0d);
//        glopts.levelTransitionAdjustment = 0.5d;
        // XXX - avoid cast
        if(GLMosaicMapLayer.this.textureCacheEnabled)
            glopts.textureCache = GLRenderGlobals.get(GLMosaicMapLayer.this.surface).getTextureCache();
        
        try {
            return new GLQuadTileNode3(LegacyAdapters.getRenderContext(this.surface), frame, opts, glopts, this.nodeInitializer);
        } catch(Throwable t) {
            return null;
        }
    }
    
    private GLQuadTileNode2.Initializer nodeInitializer = new GLQuadTileNode2.Initializer() {
        @Override
        public Result init(ImageInfo info, TileReaderFactory.Options opts) {
            MosaicDatabase2.Frame frame = (MosaicDatabase2.Frame)info;
            
            // XXX -
            if(frame.type.equals("kmz") && frame.path.endsWith(".jpg")) {
                try {
                    String kmzPath;
                    if(GLMosaicMapLayer.this.relativePaths)
                        kmzPath = Uri.parse(GLMosaicMapLayer.this.baseUri + "/" + frame.path).getPath();
                    else
                        kmzPath = Uri.parse(frame.path).getPath();
                    Result retval =  new Result();
                    retval.reader = new AndroidTileReader(
                                            new ZipVirtualFile(kmzPath),
                                            opts.preferredTileWidth,
                                            opts.preferredTileHeight,
                                            opts.cacheUri,
                                            opts.asyncIO);
                    retval.imprecise = getDatasetProjection(frame, null);
                    return retval;
                } catch(Throwable e) {
                    // fall-through and process using GDAL
                }
            }
    
            // try to create a TileReader for the frame. we will attempt using the
            // GDAL provider first since the more likely case is native imagery,
            // then try a general open
            opts.preferredSpi = "gdal";
            
            Result retval = new Result();
            
            retval.reader = null;
            do {
                retval.reader = TileReaderFactory.create(frame.path, opts);
                if(retval.reader != null || opts.preferredSpi == null)
                    break;
                opts.preferredSpi = null; 
            } while(true);
    
            Dataset dataset = null;
            if(retval.reader == null) {
                dataset = GdalLibrary.openDatasetFromPath(GLMosaicMapLayer.this.resolvePath(frame.path));
                if (dataset == null) {
                    retval.error = new RuntimeException("Failed to create tile reader for " + frame.path);
                    return retval;
                }
                retval.reader = new GdalTileReader(dataset,
                                                dataset.GetDescription(),
                                                opts.preferredTileWidth,
                                                opts.preferredTileHeight,
                                                opts.cacheUri,
                                                opts.asyncIO);
            }

            retval.imprecise = getDatasetProjection(frame, dataset);
            if (retval.imprecise == null) {
                retval.reader.dispose();
                retval.reader = null;
                retval.error = new RuntimeException("Failed to create dataset projection for " + frame.path);
                return retval;
            }
    
            if(frame.precisionImagery) {
                try {
                    PrecisionImagery p = PrecisionImageryFactory.create(GLMosaicMapLayer.this.resolvePath(frame.path));
                    if(p == null)
                        throw new NullPointerException();
                    retval.precise = p.getDatasetProjection();
                } catch(Throwable t) {
                    Log.w(TAG, "Failed to parse precision imagery for " + frame.path, t);
                }
            }
    
            return retval;
        }
        
        @Override
        public void dispose(Result result) {
            if(result.reader != null)
                result.reader.dispose();
            if(result.imprecise != null)
                result.imprecise.release();
            if(result.precise != null)
                result.precise.release();
        }
    };

    @Override
    public String getLayerUri() {
        return this.info.getUri();
    }

    @Override
    public DatasetDescriptor getInfo() {
        return this.info;
    }
    
    @Override
    public <T extends MapControl> T getControl(Class<T> clazz) {
        for(MapControl ctrl : this.controls) {
            if(clazz.isAssignableFrom(ctrl.getClass()))
                return clazz.cast(ctrl);
        }
        return null;
    }

    /**
     * Resolves the 'path' entry in the database into a GDAL friendly URI.
     * 
     * @param path
     * @return
     */
    private String resolvePath(String path) {
        if (this.relativePaths) {
            return this.baseUri + File.separator + path;
        } else {
            if(path.startsWith("file:///"))
                path = path.substring(7);
            else if(path.startsWith("file://"))
                path = path.substring(6);
            else if(path.startsWith("zip://"))
                path = path.replace("zip://", "/vsizip/");
            return path;
        }
    }

    /**************************************************************************/

    public String toString() {
        return "GLMosaicMapLayer@" + this.hashCode() + " " + this.info.getName();
    }

    /**************************************************************************/

    private static boolean intersects(ViewState state, MosaicDatabase2.Frame frame) {
        return (state.southBound <= frame.maxLat &&
                state.northBound >= frame.minLat &&
                state.westBound <= frame.maxLon && state.eastBound >= frame.minLon);
    }

    private static DatasetProjection2 getDatasetProjection(MosaicDatabase2.Frame frame, Dataset dataset) {
        if (USE_FRAME_COORDS_AS_PROJ.contains(frame.type) || dataset == null) {
            return new DefaultDatasetProjection2(frame.srid,
                                                 frame.width, frame.height,
                                                 frame.upperLeft,
                                                 frame.upperRight,
                                                 frame.lowerRight,
                                                 frame.lowerLeft);
        } else {
            try {
                return GdalDatasetProjection2.getInstance(dataset);
            } catch (UnsupportedOperationException e) {
                Log.e(TAG, "Failed to create projection for " + frame.path, e);
                try {
                    Matrix img2proj = Matrix.mapQuads(0, 0,
                            frame.width - 1, 0,
                            frame.width - 1, frame.height - 1,
                            0, frame.height - 1,
                            frame.upperLeft.getLongitude(), frame.upperLeft.getLatitude(),
                            frame.upperRight.getLongitude(), frame.upperRight.getLatitude(),
                            frame.lowerRight.getLongitude(), frame.lowerRight.getLatitude(),
                            frame.lowerLeft.getLongitude(), frame.lowerLeft.getLatitude());

                    return GdalLayerInfo.createDatasetProjection2(img2proj);
                } catch (Throwable t) {
                    return null;
                }
            }
        }
    }

    /**************************************************************************/
    
    private class RasterDataAccessControlImpl implements RasterDataAccessControl {
        @Override
        public RasterDataAccess2 accessRasterData(GeoPoint p) {
            synchronized(GLMosaicMapLayer.this) {
                final double latitude = p.getLatitude();
                final double longitude = p.getLongitude();

                final NavigableMap<MosaicDatabase2.Frame, GLResolvableMapRenderable> reverse = GLMosaicMapLayer.this.visibleFrames.descendingMap();
                MosaicDatabase2.Frame frame;
                GLResolvableMapRenderable r;
                RasterDataAccess2 data;
                PointD img = new PointD(0d, 0d);
                for(Map.Entry<MosaicDatabase2.Frame, GLResolvableMapRenderable> entry : reverse.entrySet()) {
                    frame = entry.getKey();
                    if(frame.minLat > latitude || frame.maxLat < latitude)
                        continue;
                    if(frame.minLon > longitude || frame.maxLon < longitude)
                        continue;
                    r = entry.getValue();
                    data = null;
                    if(r instanceof RasterDataAccess2) {
                        data = (RasterDataAccess2)r;
                    } else if(r instanceof Controls) {
                        RasterDataAccessControl ctrl = ((Controls)r).getControl(RasterDataAccessControl.class);
                        if(ctrl != null)
                            data = ctrl.accessRasterData(p);
                    }
                    if (data == null)
                        continue;
                    if(!data.groundToImage(p, img, null))
                        continue;
                    if(Rectangle.contains(0d,
                                          0d,
                                          data.getWidth(),
                                          data.getHeight(),
                                          img.x,
                                          img.y)) {

                        return data;
                    }
                }
                return null;
            }
        }
    }
    
    private static int modulate(int color1, int color2) {
        final float a1 = ((color1>>24)&0xFF)/255f;
        final float r1 = ((color1>>16)&0xFF)/255f;
        final float g1 = ((color1>>8)&0xFF)/255f;
        final float b1 = (color1&0xFF)/255f;
        
        final float a2 = ((color2>>24)&0xFF)/255f;
        final float r2 = ((color2>>16)&0xFF)/255f;
        final float g2 = ((color2>>8)&0xFF)/255f;
        final float b2 = (color2&0xFF)/255f;
        
        return ((int)(MathUtils.clamp(a1*a2, 0f, 1f)*255) << 24) |
               ((int)(MathUtils.clamp(r1*r2, 0f, 1f)*255) << 16) |
               ((int)(MathUtils.clamp(g1*g2, 0f, 1f)*255) << 8) |
               (int)(MathUtils.clamp(b1*b2, 0f, 1f)*255);
    }

    private class ColorControlImpl implements ColorControl, MosaicFrameColorControl {
        private int color;
        private boolean dirty;
        
        private Map<Filter<MosaicDatabase2.Frame>, Integer> filters;
        
        public ColorControlImpl() {
            this.color = -1;
            this.dirty = false;
            
            this.filters = new IdentityHashMap<Filter<MosaicDatabase2.Frame>, Integer>();
        }

        @Override
        public void setColor(int color) {
            synchronized(GLMosaicMapLayer.this) {
                if(color != this.color) {
                    this.color = color;
                    this.dirty = true;
                }
            }
        }

        @Override
        public int getColor() {
            synchronized(GLMosaicMapLayer.this) {
                return this.color;
            }
        }
        
        /** must hold lock on <code>GLMosaicMapLayer.this</code> */
        void apply() {
            if(!this.dirty)
                return;
            for(Map.Entry<MosaicDatabase2.Frame, GLResolvableMapRenderable> entry : GLMosaicMapLayer.this.visibleFrames.entrySet())
                apply(entry.getKey(), entry.getValue());
            this.dirty = false;
        }

        /** must hold lock on <code>GLMosaicMapLayer.this</code> */
        void apply(MosaicDatabase2.Frame frame, GLMapRenderable r) {
            final int layerColor = this.color;
            final int frameColor = this.getColorImpl(frame);
            
            final int color = modulate(layerColor, frameColor);

            if(r instanceof GLQuadTileNode3) {
                ((GLQuadTileNode3)r).setColor(color);
            } else if(r instanceof Controls){
                ColorControl ctrl = ((Controls)r).getControl(ColorControl.class);
                if(ctrl != null)
                    ctrl.setColor(color);
            }
        }

        @Override
        public void addFilter(Filter<MosaicDatabase2.Frame> filter, int color) {
            synchronized(GLMosaicMapLayer.this) {
                this.filters.put(filter, Integer.valueOf(color));
                this.dirty = true;
            }
        }

        @Override
        public void removeFilter(Filter<MosaicDatabase2.Frame> filter) {
            synchronized(GLMosaicMapLayer.this) {
                this.dirty |= (this.filters.remove(filter) != null);
            }
        }

        @Override
        public int getColor(Frame frame) {
            synchronized(GLMosaicMapLayer.this) {
                return this.getColorImpl(frame);
            }
        }
        
        private int getColorImpl(Frame frame) {
            for(Map.Entry<Filter<MosaicDatabase2.Frame>, Integer> entry : this.filters.entrySet()) {
                if(entry.getKey().accept(frame))
                    return entry.getValue().intValue();
            }
            return -1;
        }
    }

    final class SelectControlImpl implements ImagerySelectionControl {

        @Override
        public void setResolutionSelectMode(Mode mode) {
            if(mode == null)
                throw new NullPointerException();
            GLMosaicMapLayer.this.resolutionSelectMode = mode;
            GLMosaicMapLayer.this.invalidateNoSync();
        }

        @Override
        public Mode getResolutionSelectMode() {
            return GLMosaicMapLayer.this.resolutionSelectMode;
        }

        @Override
        public void setFilter(Set<String> filter) {
            if(filter != null)
                filter = new HashSet<String>(filter);
            GLMosaicMapLayer.this.imageryTypeFilter = filter;
            GLMosaicMapLayer.this.invalidateNoSync();
        }
    }

    final static class FrameRecord {
        MosaicDatabase2.Frame frame;
        int requestPump;
        GLResolvableMapRenderable renderable;
    }
}
