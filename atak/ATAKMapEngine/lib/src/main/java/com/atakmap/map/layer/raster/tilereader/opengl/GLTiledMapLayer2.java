
package com.atakmap.map.layer.raster.tilereader.opengl;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Pair;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.map.LegacyAdapters;
import com.atakmap.map.MapControl;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.control.ColorControl;
import com.atakmap.map.layer.control.Controls;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.DefaultDatasetProjection2;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.map.layer.raster.PrecisionImagery;
import com.atakmap.map.layer.raster.PrecisionImageryFactory;
import com.atakmap.map.layer.raster.RasterDataAccess2;
import com.atakmap.map.layer.raster.controls.TileClientControl;
import com.atakmap.map.layer.raster.opengl.GLMapLayer3;
import com.atakmap.map.layer.raster.opengl.GLMapLayerSpi3;
import com.atakmap.map.layer.raster.service.RasterDataAccessControl;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.map.opengl.GLResolvableMapRenderable;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLResolvable;
import com.atakmap.opengl.GLTexture;
import com.atakmap.util.ConfigOptions;
import com.atakmap.math.MathUtils;
import com.atakmap.math.PointD;
import com.atakmap.math.RectD;
import com.atakmap.math.Rectangle;
import com.atakmap.util.Disposable;

import org.gdal.gdal.Dataset;

public class GLTiledMapLayer2 implements GLMapLayer3, GLResolvableMapRenderable, GLMapRenderable2 {

    public final static GLMapLayerSpi3 SPI = new GLMapLayerSpi3() {
        @Override
        public int getPriority() {
            // DatasetDescriptor : ImageDatasetDescriptor
            return 1;
        }

        @Override
        public GLMapLayer3 create(Pair<MapRenderer, DatasetDescriptor> arg) {
            final MapRenderer surface = arg.first;
            final DatasetDescriptor info = arg.second;

            if(!(info instanceof ImageDatasetDescriptor))
                return null;

            if(!TileReaderFactory.isSupported(info.getUri(), info.getDatasetType()))
                return null;
            
            return new GLTiledMapLayer2(surface, info);
        }
    };

    /**************************************************************************/

    private final static String TAG = "GLTiledMapLayer";
    
    private static GLTexture loadingTexture;

    /**************************************************************************/

    protected final MapRenderer surface;
    protected final DatasetDescriptor info;
    /** @deprecated use {@link #renderable}*/
    @Deprecated
    @DeprecatedApi(since="4.1", forRemoval = true, removeAt = "4.4")
    protected GLQuadTileNode2 quadTree;
    protected GLMapRenderable2 renderable;
    protected TileReader tileReader;
    protected final TileReader.AsynchronousIO asyncio;
    
    private final Envelope minimumBoundingBox;
    private boolean initialized;

    protected final boolean textureCacheEnabled;
    protected final boolean ownsIO;

    protected Set<MapControl> controls;

    protected final ColorControl colorCtrl;
    protected final RasterDataAccessControl rasterAccessCtrl;
    protected final TileClientControl tileClientCtrl;

    protected GLTiledMapLayer2(MapRenderer surface, DatasetDescriptor info) {
        this.surface = surface;
        this.info = info;
        this.minimumBoundingBox = this.info.getCoverage(null).getEnvelope();

        this.initialized = false;
        this.tileReader = null;
        
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
        
        this.colorCtrl = new ColorControlImpl();
        this.rasterAccessCtrl = new RasterDataAccessControlImpl();
        this.tileClientCtrl = info.isRemote() ? new TileClientControlImpl() : null;

        this.controls = Collections.newSetFromMap(new IdentityHashMap<MapControl, Boolean>());
        this.controls.add(this.colorCtrl);
        this.controls.add(this.rasterAccessCtrl);
        if(this.tileClientCtrl != null)
            this.controls.add(this.tileClientCtrl);
    }

    /**
     * Initializes {@link #renderable}.
     */
    protected void init() {
        try {
            TileReaderFactory.Options readerOpts = new TileReaderFactory.Options();
            readerOpts.preferredSpi = this.info.getDatasetType();
            if(this.info.getExtraData("offlineCache") != null)
                readerOpts.cacheUri = this.info.getExtraData("offlineCache");
            readerOpts.asyncIO = this.asyncio;
            
            this.tileReader = TileReaderFactory.create(this.info.getUri(), readerOpts);

            if(this.tileReader != null) {
                final ImageDatasetDescriptor img = (ImageDatasetDescriptor)this.info;
                
                final GeoPoint ll = img.getLowerLeft();
                final GeoPoint ur = img.getUpperRight();
                final GeoPoint ul = img.getUpperLeft();
                final GeoPoint lr = img.getLowerRight();
        
                DatasetProjection2 imprecise =
                        new DefaultDatasetProjection2(this.info.getSpatialReferenceID(),
                                                      this.tileReader.getWidth(),
                                                      this.tileReader.getHeight(),
                                                      ul, ur, lr, ll);
                
                DatasetProjection2 precise = null;
                if(img.isPrecisionImagery()) {
                    try {
                        PrecisionImagery p = PrecisionImageryFactory.create(this.info.getUri());
                        if(p == null)
                            throw new NullPointerException();
                        precise = p.getDatasetProjection();
                    } catch(Throwable t) {
                        Log.w(TAG, "Failed to parse precision imagery for " + this.info.getUri(), t);
                    }
                }
                                
                GLQuadTileNode3.Options opts = new GLQuadTileNode3.Options();
                opts.childTextureCopyResolvesParent = !this.info.isRemote();
                opts.levelTransitionAdjustment = 0.5d - ConfigOptions.getOption("imagery.relative-scale", 0d);
                opts.progressiveLoad = (ConfigOptions.getOption("imagery.progressive-load-enabled", 1) != 0);
                
                // XXX - avoid cast
                if(this.textureCacheEnabled)
                    opts.textureCache = GLRenderGlobals.get(this.surface).getTextureCache();

                GLQuadTileNode4 quadTree =
                        new GLQuadTileNode4(LegacyAdapters.getRenderContext(this.surface),
                                            new ImageInfo(img.getUri(),
                                                          img.getImageryType(),
                                                          img.isPrecisionImagery(),
                                                          img.getUpperLeft(),
                                                          img.getUpperRight(),
                                                          img.getLowerLeft(),
                                                          img.getLowerRight(),
                                                          img.getMaxResolution(null),
                                                          img.getWidth(),
                                                          img.getHeight(),
                                                          img.getSpatialReferenceID()),
                                            null,
                                            opts,
                                            new PrefetchedInitializer(this.tileReader,
                                                                      imprecise,
                                                                      precise,
                                                                      false));
                quadTree.setColor(this.colorCtrl.getColor());
                renderable = quadTree;
                //this.quadTree = quadTree;
                
                if(this.tileClientCtrl != null)
                    ((TileClientControlImpl)this.tileClientCtrl).apply();
            }
        } catch(Throwable t) {
            Log.e(TAG, "Failed to initialize renderer for " + this.info.getName(), t);
        }
    }

    @Override
    public void draw(GLMapView view) {
        draw(view, GLMapView.RENDER_PASS_SURFACE);
    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        if(!MathUtils.hasBits(renderPass, GLMapView.RENDER_PASS_SURFACE))
            return;

        if (!this.initialized) {
            this.init();
            this.initialized = true;
        }

        if(this.renderable != null) {
            if(this.tileClientCtrl != null)
                ((TileClientControlImpl)this.tileClientCtrl).apply();
            this.renderable.draw(view, renderPass);
        }
        else if (this.quadTree != null) {
            if(this.tileClientCtrl != null)
                ((TileClientControlImpl)this.tileClientCtrl).apply();
            this.quadTree.draw(view);
        }
    }

    /**
     * Cleans up any resources allocated as a result of {@link #init()}; always
     * invoked AFTER {@link #quadTree} is released.
     * 
     * <P>The default implementation returns immediately.
     */
    protected void releaseImpl() {}

    @Override
    public void release() {
        if(this.renderable != null) {
            this.renderable.release();
            if(this.renderable instanceof Disposable)
                ((Disposable)this.renderable).dispose();
            this.renderable = null;
        }
        else if (this.quadTree != null) {
            this.quadTree.release();
            this.quadTree.dispose();
            this.quadTree = null;
        }
        if(this.tileReader != null) {
            this.tileReader.dispose();
            this.tileReader = null;
        }

        if(this.ownsIO)
            this.asyncio.release();

        if(this.tileClientCtrl != null)
            ((TileClientControlImpl)this.tileClientCtrl).dirty = true;

        this.releaseImpl();
    }

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SURFACE;
    }

    @Override
    public State getState() {
        if(this.renderable instanceof GLResolvable)
            return ((GLResolvable)this.renderable).getState();
        else if (this.quadTree != null)
            return this.quadTree.getState();
        return State.RESOLVED;
    }

    @Override
    public void resume() {
    }

    @Override
    public void suspend() {
    }

    @Override
    public final String getLayerUri() {
        return this.info.getUri();
    }

    @Override
    public final DatasetDescriptor getInfo() {
        return this.info;
    }
    
    /**************************************************************************/
    // Controls
    
    @Override
    public <T extends MapControl> T getControl(Class<T> controlClazz) {
        // check our controls
        for(MapControl ctrl : this.controls)
            if(controlClazz.isAssignableFrom(ctrl.getClass()))
                return controlClazz.cast(ctrl);
        
        // check for controls on the tile reader
        final Controls ctrls = this.tileReader;
        if(ctrls == null)
            return null;
        return ctrls.getControl(controlClazz);
    }

    /**************************************************************************/
    
    private class ColorControlImpl implements ColorControl {
        private int color = -1;

        @Override
        public void setColor(final int color) {
            if(GLTiledMapLayer2.this.surface.isRenderThread()) {
                setColorImpl(color);
            } else {
                GLTiledMapLayer2.this.surface.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        setColorImpl(color);
                    }
                });
            }
        }

        private void setColorImpl(int color) {
            this.color = color;

            // TODO: Consolidate this with a setColor interface
            if(GLTiledMapLayer2.this.renderable instanceof GLQuadTileNode4)
                ((GLQuadTileNode4)GLTiledMapLayer2.this.renderable).setColor(color);
            else if(GLTiledMapLayer2.this.renderable instanceof GLQuadTileNode3)
                ((GLQuadTileNode3)GLTiledMapLayer2.this.renderable).setColor(color);
            else if(GLTiledMapLayer2.this.quadTree != null)
                GLTiledMapLayer2.this.quadTree.setColor(color);
        }
        
        @Override
        public int getColor() {
            return this.color;
        }
    }
    
    private class RasterDataAccessControlImpl implements RasterDataAccessControl {

        @Override
        public RasterDataAccess2 accessRasterData(GeoPoint point) {
            if(!Rectangle.contains(
                    GLTiledMapLayer2.this.minimumBoundingBox.minX,
                    GLTiledMapLayer2.this.minimumBoundingBox.minY,
                    GLTiledMapLayer2.this.minimumBoundingBox.maxX,
                    GLTiledMapLayer2.this.minimumBoundingBox.maxY,
                    point.getLongitude(),
                    point.getLatitude())) {

                return null;
            }

            // XXX - should check resolution???
            if(GLTiledMapLayer2.this.renderable instanceof RasterDataAccess2)
                return (RasterDataAccess2)GLTiledMapLayer2.this.renderable;
            return GLTiledMapLayer2.this.quadTree;
        }
    }

    private class TileClientControlImpl implements TileClientControl {
        private long refreshInterval;
        private boolean manualRefreshRequested;
        private boolean offlineOnlyMode;
        private boolean dirty;

        TileClientControlImpl() {
            this.refreshInterval = 0L;
            this.manualRefreshRequested = false;
            this.offlineOnlyMode = false;
            this.dirty = false;
        }

        @Override
        public void setOfflineOnlyMode(boolean offlineOnly) {
            synchronized(this) {
                if(offlineOnly != this.offlineOnlyMode) {
                    this.offlineOnlyMode = offlineOnly;
                    this.dirty = true;
                }
            }
        }

        @Override
        public boolean isOfflineOnlyMode() {
            synchronized(this) {
                return this.offlineOnlyMode;
            }
        }

        @Override
        public void refreshCache() {
            synchronized(this) {
                this.manualRefreshRequested = true;
                this.dirty = true;
            }
        }

        @Override
        public void setCacheAutoRefreshInterval(long milliseconds) {
            synchronized(this) {
                if(milliseconds != this.refreshInterval) {
                    this.refreshInterval = milliseconds;
                    this.dirty = true;
                }
            }
        }

        @Override
        public long getCacheAutoRefreshInterval() {
            synchronized(this) {
                return this.refreshInterval;
            }
        }
        
        /** should only be invoked on GL thread */
        void apply() {
            TileClientControl ctrl;
            long refreshInterval;
            boolean manualRefresh;
            boolean offlineOnly;
            synchronized(this) {
                if(!this.dirty)
                    return;
                TileReader reader = GLTiledMapLayer2.this.tileReader;
                if(reader == null)
                    return;
                ctrl = reader.getControl(TileClientControl.class);
                if(ctrl == null)
                    return;
                refreshInterval = this.refreshInterval;
                manualRefresh = this.manualRefreshRequested;
                offlineOnly = this.offlineOnlyMode;
                this.dirty = false;
            }
            
            ctrl.setOfflineOnlyMode(offlineOnly);
            ctrl.setCacheAutoRefreshInterval(refreshInterval);
            if(manualRefresh)
                ctrl.refreshCache();
        }
        
    }


    /**************************************************************************/

    static GLTexture getLoadingTexture() {
        if (loadingTexture == null) {
            final int w = 64;
            final int h = 64;
            final int sw = 16;
            final int sw2 = sw * 2;
            final int[] px = new int[] {
                    0xFF7F7F7F, 0xFFAAAAAA
            };

            loadingTexture = new GLTexture(w, h, Bitmap.Config.ARGB_8888);
            int[] argb = new int[64 * 64];
            int r;
            int c;
            for (int i = 0; i < argb.length; i++) {
                c = (i % w);
                r = (i / w);

                argb[i] = px[(((c % sw2) + (r % sw2)) / sw) % 2];
            }
            Bitmap bitmap = Bitmap.createBitmap(argb, 64, 64, Bitmap.Config.ARGB_8888);
            loadingTexture.setWrapS(GLES20FixedPipeline.GL_REPEAT);
            loadingTexture.setWrapT(GLES20FixedPipeline.GL_REPEAT);
            loadingTexture.load(bitmap);
            bitmap.recycle();
            bitmap = null;
        }

        return loadingTexture;
    }

    public static RectF getRasterROI(GLMapView view, Dataset dataset, DatasetProjection2 proj) {
        final int rasterWidth = dataset.GetRasterXSize();
        final int rasterHeight = dataset.GetRasterYSize();
        final GeoPoint ul = GeoPoint.createMutable();
        proj.imageToGround(new PointD(0, 0), ul);
        final GeoPoint ur = GeoPoint.createMutable();
        proj.imageToGround(new PointD(rasterWidth - 1, 0), ur);
        final GeoPoint lr = GeoPoint.createMutable();
        proj.imageToGround(new PointD(rasterWidth - 1, rasterHeight - 1), lr);
        final GeoPoint ll = GeoPoint.createMutable();
        proj.imageToGround(new PointD(0, rasterHeight - 1), ll);
        
        return getRasterROI(view,
                rasterWidth,
                rasterHeight,
                proj,
                ul, ur, lr, ll);
    }

    public static RectF getRasterROI(GLMapView view, int rasterWidth, int rasterHeight,
            DatasetProjection2 proj) {
        
        RectD retval = getRasterROI2(view, rasterWidth, rasterHeight, proj);
        if(retval == null)
            return null;
        return new RectF((float)retval.left,
                         (float)retval.top,
                         (float)retval.right,
                         (float)retval.bottom);
    }
    
    public static RectD getRasterROI2(GLMapView view, int rasterWidth, int rasterHeight,
            DatasetProjection2 proj) {
        GeoPoint ul = GeoPoint.createMutable();
        proj.imageToGround(new PointD(0, 0), ul);
        GeoPoint ur = GeoPoint.createMutable();
        proj.imageToGround(new PointD(rasterWidth - 1, 0), ur);
        GeoPoint lr = GeoPoint.createMutable();
        proj.imageToGround(new PointD(rasterWidth - 1, rasterHeight - 1), lr);
        GeoPoint ll = GeoPoint.createMutable();
        proj.imageToGround(new PointD(0, rasterHeight - 1), ll);
        
        return getRasterROI2(view,
                rasterWidth,
                rasterHeight,
                proj,
                ul, ur, lr, ll);
    }

    public static RectF getRasterROI(GLMapView view, long rasterWidth, long rasterHeight,
            DatasetProjection2 proj, GeoPoint ulG_R, GeoPoint urG_R, GeoPoint lrG_R, GeoPoint llG_R) {

        RectD retval = getRasterROI2(view,
                                     rasterWidth,
                                     rasterHeight,
                                     proj,
                                     ulG_R,
                                     urG_R,
                                     lrG_R,
                                     llG_R);
        if(retval == null)
            return null;
        return new RectF((float)retval.left,
                         (float)retval.top,
                         (float)retval.right,
                         (float)retval.bottom);
    }
    public static RectD getRasterROI2(GLMapView view, long rasterWidth, long rasterHeight,
            DatasetProjection2 proj, GeoPoint ulG_R, GeoPoint urG_R, GeoPoint lrG_R, GeoPoint llG_R) {

        RectD retval = new RectD();
        return getRasterROI2Impl(retval,
                                 view,
                                 view.northBound, view.westBound,
                                 view.southBound, view.eastBound,
                                 rasterWidth, rasterHeight,
                                 proj,
                                 ulG_R, urG_R, lrG_R, llG_R, 0d) ? retval : null;
    }
    
    public static int getRasterROI2(RectD[] rois, GLMapView view, long rasterWidth, long rasterHeight,
            DatasetProjection2 proj, GeoPoint ulG_R, GeoPoint urG_R, GeoPoint lrG_R, GeoPoint llG_R) {
        return getRasterROI2(rois, view, rasterWidth, rasterHeight, proj, ulG_R, urG_R, lrG_R, llG_R, 0d, 0d);
    }

    public static int getRasterROI2(RectD[] rois, GLMapView view, long rasterWidth, long rasterHeight,
            DatasetProjection2 proj, GeoPoint ulG_R, GeoPoint urG_R, GeoPoint lrG_R, GeoPoint llG_R, double unwrap, double padding) {
        int retval = 0;
        if(view.crossesIDL) {
            // west of IDL
            if(getRasterROI2Impl(rois[retval],
                    view,
                    view.northBound+padding, view.westBound-padding,
                    view.southBound-padding, 180d,
                    rasterWidth, rasterHeight,
                    proj,
                    ulG_R, urG_R, lrG_R, llG_R, unwrap)) {

                retval++;
            }
            // east of IDL
            if(getRasterROI2Impl(rois[retval],
                    view,
                    view.northBound+padding, -180d,
                    view.southBound-padding, view.eastBound+padding,
                    rasterWidth, rasterHeight,
                    proj,
                    ulG_R, urG_R, lrG_R, llG_R, unwrap)) {

                retval++;
            }
        } else {
            if(getRasterROI2Impl(rois[retval],
                    view,
                    view.northBound+padding, view.westBound-padding,
                    view.southBound-padding, view.eastBound+padding,
                    rasterWidth, rasterHeight,
                    proj,
                    ulG_R, urG_R, lrG_R, llG_R, unwrap)) {
                retval++;
            }
        }

        return retval;
    }

    private static boolean getRasterROI2Impl(RectD roi,
                                             GLMapView view,
                                             double viewNorth,
                                             double viewWest,
                                             double viewSouth,
                                             double viewEast,
                                             long rasterWidth,
                                             long rasterHeight,
                                             DatasetProjection2 proj,
                                             GeoPoint ulG_R,
                                             GeoPoint urG_R,
                                             GeoPoint lrG_R,
                                             GeoPoint llG_R,
                                             double unwrap) {

        double minLat = MathUtils.min(ulG_R.getLatitude(), urG_R.getLatitude(), lrG_R.getLatitude(), llG_R.getLatitude());
        double minLng = MathUtils.min(ulG_R.getLongitude(), urG_R.getLongitude(), lrG_R.getLongitude(), llG_R.getLongitude());
        double maxLat = MathUtils.max(ulG_R.getLatitude(), urG_R.getLatitude(), lrG_R.getLatitude(), llG_R.getLatitude());
        double maxLng = MathUtils.max(ulG_R.getLongitude(), urG_R.getLongitude(), lrG_R.getLongitude(), llG_R.getLongitude());

        double u2 = 0d;
        if (unwrap != 0d) {
            if (unwrap > 0d && (viewWest + viewEast) / 2 > 0d) {
                u2 = -360d;
                minLng -= u2;
                maxLng = 180d;
            } else if (unwrap < 0d && (viewWest + viewEast) / 2 < 0d) {
                u2 = 360d;
                maxLng -= u2;
                minLng = -180d;
            }
        }

        double roiMinLat = MathUtils.clamp(minLat, viewSouth, viewNorth);
        double roiMinLng = MathUtils.clamp(minLng, viewWest, viewEast);
        double roiMaxLat = MathUtils.clamp(maxLat, viewSouth, viewNorth);
        double roiMaxLng = MathUtils.clamp(maxLng, viewWest, viewEast);

        if (u2 != 0d) {
            roiMinLng += u2;
            roiMaxLng += u2;
        }

        if(Double.compare(roiMinLat,roiMaxLat) != 0 && Double.compare(roiMinLng,roiMaxLng) != 0) {
            view.scratch.geo.set(roiMaxLat, roiMinLng);
            PointD roiUL = new PointD(0d, 0d);
            proj.groundToImage(view.scratch.geo, roiUL);
            view.scratch.geo.set(roiMaxLat, roiMaxLng);
            PointD roiUR = new PointD(0d, 0d);
            proj.groundToImage(view.scratch.geo, roiUR);
            view.scratch.geo.set(roiMinLat, roiMaxLng);
            PointD roiLR = new PointD(0d, 0d);
            proj.groundToImage(view.scratch.geo, roiLR);
            view.scratch.geo.set(roiMinLat, roiMinLng);
            PointD roiLL = new PointD(0d, 0d);
            proj.groundToImage(view.scratch.geo, roiLL);

            // XXX - rounding issue ??? observe that blue marble needs one
            //       pixel of padding otherwise tiles disappear at higher zoom
            //       levels around zeams

            final int padding = 1;
            
            roi.left = MathUtils.clamp(MathUtils.min(roiUL.x, roiUR.x, roiLR.x, roiLL.x)-padding, 0, rasterWidth);
            roi.top = MathUtils.clamp(MathUtils.min(roiUL.y, roiUR.y, roiLR.y, roiLL.y)-padding, 0, rasterHeight);
            roi.right = MathUtils.clamp(MathUtils.max(roiUL.x, roiUR.x, roiLR.x, roiLL.x)+padding, 0, rasterWidth);
            roi.bottom = MathUtils.clamp(MathUtils.max(roiUL.y, roiUR.y, roiLR.y, roiLL.y)+padding, 0, rasterHeight);
            
            if(roi.left == roi.right || roi.top == roi.bottom)
                return false;
        }
        return true;
    }
}
