
package com.atakmap.map.layer.raster.tilereader.opengl;

import java.nio.ByteBuffer;
import java.util.Comparator;


import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.Interop;
import com.atakmap.map.RenderContext;
import com.atakmap.map.layer.raster.DatasetProjection2;
import com.atakmap.map.layer.raster.ImageInfo;
import com.atakmap.map.layer.raster.RasterDataAccess2;
import com.atakmap.map.layer.raster.gdal.GdalTileReader;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory;
import com.atakmap.map.opengl.GLMapRenderable2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLResolvableMapRenderable;
import com.atakmap.map.projection.PointL;
import com.atakmap.math.Matrix;
import com.atakmap.opengl.GLTextureCache;
import com.atakmap.util.Disposable;
import com.atakmap.math.PointD;
import com.atakmap.util.ResourcePool;

public class GLQuadTileNode3 implements
                                    GLMapRenderable2,
                                    GLResolvableMapRenderable,
                                    TileReader.AsynchronousReadRequestListener,
                                    RasterDataAccess2,
                                    Disposable {

    private final static Interop<Matrix> Matrix_interop = Interop.findInterop(Matrix.class);

    final static int STATE_RESOLVED = 0x01;
    final static int STATE_RESOLVING = 0x02;
    final static int STATE_UNRESOLVED = 0x04;
    final static int STATE_UNRESOLVABLE = 0x08;
    final static int STATE_SUSPENDED = 0x10;

    final static int[] POI_ITERATION_BIAS =
        {
            1, 3, 0, 2, // 0
            1, 0, 2, 3, // 1
            0, 1, 2, 3, // 2
            0, 2, 1, 3, // 3
            2, 0, 3, 1, // 4
            2, 3, 0, 1, // 5
            3, 2, 1, 0, // 6
            3, 1, 2, 0, // 7
        };

    static boolean offscreenFboFailed = false;

    private static final String TAG = "GLQuadTileNode3";

    final static double POLE_LATITUDE_LIMIT_EPISLON = 0.00001d;

    final static Comparator<PointL> POINT_COMPARATOR = new Comparator<PointL>() {
        @Override
        public int compare(PointL p0, PointL p1) {
            long retval = p0.y - p1.y;
            if (retval == 0L)
                retval = p0.x - p1.x;
            if(retval > 0L)
                return 1;
            else if(retval < 0L)
                return -1;
            else
                return 0;
        }
    };

    static boolean mipmapEnabled = false;

    /*************************************************************************/

    final static boolean DEBUG = false;

    static {
        GdalTileReader.setPaletteRgbaFormat(GdalTileReader.Format.RGBA);
    }

    final static int TEXTURE_CACHE_HINT_RESOLVED = 0x00000001;

    final static int MAX_GRID_SIZE = 32;

    // 8MB reserved transfer buffer
    final static ResourcePool<ByteBuffer> transferBuffers = new ResourcePool<>(32);

    private GLQuadTileNode4 impl;

    /**************************************************************************/

    /**
     * Creates a new root node.
     *
     * @param info          The image information
     * @param readerOpts    The {@link TileReaderFactory.Options} to be used
     *                      when creating the {@link TileReader} associated with
     *                      this node. May be <code>null</code>
     * @param opts          The render configuration {@link GLQuadTileNode2.Options}. May be
     *                      <code>null</code>.
     * @param init          The initializer for this node.
     */
    public GLQuadTileNode3(RenderContext ctx, ImageInfo info, TileReaderFactory.Options readerOpts, Options opts, GLQuadTileNode2.Initializer init) {
        this.impl = new GLQuadTileNode4(ctx, info, readerOpts, opts, init);
    }

    public void setColor(int color) {
        impl.setColor(color);
    }

    public DatasetProjection2 getDatasetProjection() {
        return impl.getDatasetProjection();
    }

    public TileReader getReader() {
        return impl.getReader();
    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        impl.draw(view, renderPass);
    }

    @Override
    public int getRenderPass() {
        return impl.getRenderPass();
    }


    @Override
    public void release() {
        impl.release();
    }

    @Override
    public void draw(GLMapView view) {
        impl.draw(view);
    }

    /**************************************************************************/

    // XXX - implementation for getState

    @Override
    public State getState() {
        return impl.getState();
    }

    @Override
    public void suspend() {
        impl.suspend();
    }

    @Override
    public void resume() {
        impl.resume();
    }

    /**************************************************************************/
    // RasterDataAccess2

    @Override
    public String getType() {
        return impl.getType();
    }

    @Override
    public String getUri() {
        return impl.getUri();
    }

    @Override
    public boolean imageToGround(PointD image, GeoPoint ground, boolean[] precise) {
        return impl.imageToGround(image, ground, precise);
    }

    @Override
    public boolean groundToImage(GeoPoint ground, PointD image, boolean[] precise) {
        return impl.groundToImage(ground, image, precise);
    }

    @Override
    public int getSpatialReferenceId() {
        return this.impl.getSpatialReferenceId();
    }

    @Override
    public boolean hasPreciseCoordinates() {
        return this.impl.hasPreciseCoordinates();
    }

    @Override
    public int getWidth() {
        return this.impl.getWidth();
    }

    @Override
    public int getHeight() {
        return this.impl.getHeight();
    }

    /**************************************************************************/
    // Asynchronous Read Request Listener

    @Override
    public void requestCreated(TileReader.ReadRequest request) {
        impl.requestCreated(request);
    }

    @Override
    public void requestStarted(int id) {
        impl.requestStarted(id);
    }

    @Override
    public void requestUpdate(final int id, byte[] data, final int dstX, final int dstY,
                              final int dstW, final int dstH) {

        impl.requestUpdate(id, data, dstX, dstY, dstW, dstH);
    }

    @Override
    public void requestCompleted(final int id) {
        impl.requestCompleted(id);
    }

    @Override
    public void requestCanceled(final int id) {
        impl.requestCanceled(id);
    }

    @Override
    public void requestError(final int id, Throwable error) {
        impl.requestError(id, error);
    }

    /**************************************************************************/
    // Disposable

    @Override
    public void dispose() {
        impl.dispose();
    }

    /**************************************************************************/
    // Object

    public String toString() {
        return impl.toString();
    }

    public String toString(boolean l) {
        return impl.toString(l);
    }
    /**************************************************************************/

    public static class Options {
        public boolean textureCopyEnabled;
        public boolean childTextureCopyResolvesParent;
        public GLTextureCache textureCache;
        public boolean progressiveLoad;
        public double levelTransitionAdjustment;
        public boolean textureBorrowEnabled;
        public boolean adaptiveTileLod;

        public Options() {
            this.textureCopyEnabled = true;
            this.childTextureCopyResolvesParent = true;
            this.textureCache = null;
            this.progressiveLoad = true;
            this.levelTransitionAdjustment = 0d;
            this.textureBorrowEnabled = true;
            this.adaptiveTileLod = false;
        }
    }
}
