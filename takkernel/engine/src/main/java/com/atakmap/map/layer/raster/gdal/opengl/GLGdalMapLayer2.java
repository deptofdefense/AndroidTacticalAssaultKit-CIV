
package com.atakmap.map.layer.raster.gdal.opengl;

import java.util.HashSet;
import java.util.Set;

import android.util.Pair;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.LegacyAdapters;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.DefaultDatasetProjection2;
import com.atakmap.map.layer.raster.ImageDatasetDescriptor;
import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.map.layer.raster.PrecisionImagery;
import com.atakmap.map.layer.raster.PrecisionImageryFactory;
import com.atakmap.map.layer.raster.gdal.GdalDatasetProjection2;
import com.atakmap.map.layer.raster.gdal.GdalLayerInfo;
import com.atakmap.map.layer.raster.opengl.GLMapLayer3;
import com.atakmap.map.layer.raster.opengl.GLMapLayerSpi3;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory;
import com.atakmap.map.layer.raster.tilereader.opengl.GLQuadTileNode2;
import com.atakmap.map.layer.raster.tilereader.opengl.GLQuadTileNode4;
import com.atakmap.map.layer.raster.tilereader.opengl.GLTiledMapLayer2;
import com.atakmap.map.layer.raster.tilereader.opengl.PrefetchedInitializer;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.map.opengl.GLResolvableMapRenderable;
import com.atakmap.opengl.GLResolvable;
import com.atakmap.util.ConfigOptions;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;

/**
 * @deprecated  use {@link GLTiledMapLayer2}
 */
@Deprecated
@DeprecatedApi(since="4.1", forRemoval = true, removeAt = "4.4")
public class GLGdalMapLayer2 extends GLTiledMapLayer2 implements GLResolvableMapRenderable {

    private final static Set<String> SUPPORTED_TYPES = new HashSet<String>();
    static {
        SUPPORTED_TYPES.add("native");
    }

    public final static GLMapLayerSpi3 SPI = new GLMapLayerSpi3() {
        @Override
        public int getPriority() {
            return 2;
        }

        @Override
        public GLMapLayer3 create(Pair<MapRenderer, DatasetDescriptor> arg) {
            final MapRenderer surface = arg.first;
            final DatasetDescriptor info = arg.second;
            if (!SUPPORTED_TYPES.contains(info.getDatasetType()))
                return null;
            return new GLGdalMapLayer2(surface, info);
        }
    };

    private final static String TAG = "GLGdalMapLayer";

    /**************************************************************************/

    protected Dataset dataset;

    /**
     * The projection for the dataset.
     */
    protected DatasetProjection2 imprecise;
    protected DatasetProjection2 precise;

    private Thread initializer;
    protected boolean quadTreeInit;

    protected final boolean textureCacheEnabled;
    
    public GLGdalMapLayer2(MapRenderer surface, DatasetDescriptor info) {
        super(surface, info);

        this.initializer = null;
        this.quadTreeInit = false;
        
        this.textureCacheEnabled = (ConfigOptions.getOption("imagery.texture-cache", 1) != 0);
    }

    @Override
    protected synchronized void init() {
        if (this.initializer != null)
            return;

        this.initializer = new Thread("GLGdalMapLayerInit") {
            @Override
            public void run() {
                try {
                    GLGdalMapLayer2.this.initImpl();
                } catch(Throwable e) {
                    Log.e("GLGdalMapLayer", "Failed to initialize layer " + getName(), e);
                }
            }
        };
        this.initializer.setPriority(Thread.NORM_PRIORITY);
        this.initializer.start();
    }

    protected DatasetProjection2 createDatasetProjection() {
        DatasetProjection2 retval = GdalDatasetProjection2.getInstance(this.dataset);
        if(retval == null) {
            ImageDatasetDescriptor img = (ImageDatasetDescriptor)this.info;
            retval = new DefaultDatasetProjection2(img.getSpatialReferenceID(),
                                                   img.getWidth(),
                                                   img.getHeight(),
                                                   img.getUpperLeft(),
                                                   img.getUpperRight(),
                                                   img.getLowerRight(),
                                                   img.getLowerLeft());
        }
        return retval;
    }

    private void initImpl() {
        // check out of sync -- this Thread can only be set as the initializer
        // once
        if(this.initializer != Thread.currentThread())
            return;

        final Dataset d = gdal.Open(GdalLayerInfo.getGdalFriendlyUri(info));
        if(d == null) {
            Log.e("GLGdalMapLayer", "Failed to open dataset");
            return;
        }

        synchronized (this) {
            if (this.initializer != Thread.currentThread()) {
                // release was invoked asynchronously; we are no longer the
                // initializer so delete the dataset
                d.delete();
            } else {
                this.dataset = d;
                this.imprecise = this.createDatasetProjection();
                
                if(((ImageDatasetDescriptor)this.info).isPrecisionImagery()) {
                    try {
                        PrecisionImagery p = PrecisionImageryFactory.create(this.info.getUri());
                        if(p == null)
                            throw new NullPointerException();
                        this.precise = p.getDatasetProjection();
                    } catch(Throwable t) {
                        Log.w(TAG, "Failed to parse precision imagery for " + this.info.getUri(), t);
                    }
                }
                
                this.tileReader = this.createTileReader();

                this.surface.queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        if(GLGdalMapLayer2.this.dataset != null && GLGdalMapLayer2.this.imprecise != null) {
                            GLQuadTileNode2.Options opts = new GLQuadTileNode2.Options();
                            // XXX - avoid cast
                            if(GLGdalMapLayer2.this.textureCacheEnabled)
                                opts.textureCache = GLRenderGlobals.get(GLGdalMapLayer2.this.surface).getTextureCache();

                            if(GLGdalMapLayer2.this.getClass() != GLGdalMapLayer2.class) {
                                // XXX - legacy path for any subclasses
                                GLGdalMapLayer2.this.quadTree = GLGdalMapLayer2.this.createRoot(opts);
                                if (GLGdalMapLayer2.this.quadTree == null)
                                      return;
                            
                                GLGdalMapLayer2.this.quadTree.setColor(GLGdalMapLayer2.this.colorCtrl.getColor());
                            } else {
                                if (tileReader == null)
                                     return;

                                ImageDatasetDescriptor img = (ImageDatasetDescriptor)GLGdalMapLayer2.this.info;
                                GLGdalMapLayer2.this.renderable = new GLQuadTileNode4(
                                    LegacyAdapters.getRenderContext(GLGdalMapLayer2.this.surface),
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
                                     new PrefetchedInitializer(GLGdalMapLayer2.this.tileReader,
                                                               GLGdalMapLayer2.this.imprecise,
                                                               GLGdalMapLayer2.this.precise,
                                                               false));
                                ((GLQuadTileNode4)GLGdalMapLayer2.this.renderable).setColor(GLGdalMapLayer2.this.colorCtrl.getColor());
                            }
                            // XXX - tileclientcontrol -- not strictly necessary
                            //       as this should always be local content, but
                            //       worth noting
                        }
                        
                        GLGdalMapLayer2.this.quadTreeInit = true;
                    }
                });

                this.initializer = null;
            }
        }
    }

    @Override
    protected void releaseImpl() {
        super.releaseImpl();

        // flag that we are no longer initializing
        this.initializer = null;
    }

    @Override
    public final synchronized void release() {
        super.release();
        this.quadTreeInit = false;
    }

    protected GLQuadTileNode2 createRoot(GLQuadTileNode2.Options opts) {
        ImageDatasetDescriptor img = (ImageDatasetDescriptor)this.info;

        if (tileReader == null) 
             return null;

        return new GLQuadTileNode2(new ImageInfo(img.getUri(),
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
                                                              this.imprecise,
                                                              this.precise,
                                                              false));
    }

    protected TileReader createTileReader() {
        TileReaderFactory.Options opts = new TileReaderFactory.Options();
        opts.asyncIO = this.asyncio;
        opts.preferredTileWidth = 512;
        opts.preferredTileHeight = 512;
        opts.cacheUri = info.getExtraData("tilecache");
        opts.preferredSpi = "gdal";
        return TileReaderFactory.create(GdalLayerInfo.getGdalFriendlyUri(info), opts);
    }

    @Override
    public State getState() {
        final GLResolvable root;
        if(this.quadTree != null)
            root = this.quadTree;
        else if(this.renderable instanceof GLResolvable)
            root = (GLResolvable)this.renderable;
        else
            return this.quadTreeInit ? State.UNRESOLVABLE : State.UNRESOLVED;
        return root.getState();
    }

    @Override
    public void suspend() {
        final GLResolvable root;
        if(this.quadTree != null)
            root = this.quadTree;
        else if(this.renderable instanceof GLResolvable)
            root = (GLResolvable)this.renderable;
        else
            return;
        root.suspend();
    }

    @Override
    public void resume() {
        final GLResolvable root;
        if(this.quadTree != null)
            root = this.quadTree;
        else if(this.renderable instanceof GLResolvable)
            root = (GLResolvable)this.renderable;
        else
            return;
        root.resume();
    }
}
