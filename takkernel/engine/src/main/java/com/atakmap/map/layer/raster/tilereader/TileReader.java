/*
 * TileReader.java
 *
 * Created on June 9, 2013, 10:07 AM
 */

package com.atakmap.map.layer.raster.tilereader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import android.os.SystemClock;

import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.control.Controls;

/**
 * Provides tile-based access to raster data. Synchronous and asynchronous data
 * access methods are provided.
 * 
 * <P>Data returned is intended for visualization purposes and the allowed pixel
 * formats represent this.
 * 
 * @author Developer
 */
public abstract class TileReader implements Controls {

    public static final String TAG = "TileReader";

    /**************************************************************************/

    public enum Format {
        MONOCHROME,
        MONOCHROME_ALPHA,
        RGB,
        RGBA,
        ARGB,
    };

    public enum Interleave {
        /** Band Sequential */
        BSQ,
        /** Pixel Interleave */
        BIP,
        /** Line Interleave */
        BIL,
    };
    
    public enum ReadResult {
        SUCCESS,
        ERROR,
        //AGAIN,
        CANCELED,
    };

    private static AsynchronousIO masterIOThread = null;

    protected final String uri;
    protected final AsynchronousIO asynchronousIO;
    protected int asyncRequestId;
    
    /**
     * The object used for synchronization of reads. Defaults to
     * <code>this</code>. Care should be taken as the default
     * {@link AsynchronousIO} will default to synchronize on this
     * <code>TileReader</code> instance.
     */
    protected Object readLock;
    
    /**
     * Flag indicating whether or not this instance is valid. Remains
     * <code>true</code> until {@link #dispose()} is invoked. Reads should not be
     * permitted if <code>false</code>.
     */
    protected boolean valid;

    // cache support
    private TileCache cache;
    private String cacheUri;
    
    private int minCacheLevel;

    private Set<Object> controls;

    protected TileReader(String uri, String cacheUri, int minCacheLevel, AsynchronousIO asyncIO) {
        this.uri = uri;

        if(asyncIO == null)
            asyncIO = new AsynchronousIO(this);

        this.asynchronousIO = asyncIO;
        this.asyncRequestId = 1;
        this.valid = true;

        this.readLock = this;
        
        this.cacheUri = cacheUri;
        if(this.cacheUri != null)
            this.minCacheLevel = minCacheLevel;
        else
            this.minCacheLevel = Integer.MAX_VALUE;
        
        this.controls = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());

        this.registerControl(this.asynchronousIO);
    }

    /**
     * Returns the URI for the data.
     * 
     * @return  The URI for the data.
     */
    public String getUri() {
        return this.uri;
    }

    /**
     * Returns the width of the image at its native resolution.
     * 
     * @return The width of the image at its native resolution.
     */
    public abstract long getWidth();

    /**
     * Returns the width of the image at the specified level.
     * 
     * @param level The level
     * @return The width of the image at the specified level.
     */
    public long getWidth(int level) {
        return Math.max(this.getWidth() >> (long)level, 1L);
    }

    /**
     * Returns the height of the image at its native resolution.
     * 
     * @return The height of the image at its native resolution.
     */
    public abstract long getHeight();

    /**
     * Returns the height of the image at the specified level.
     * 
     * @return The height of the image at the specified level.
     */
    public long getHeight(int level) {
        return Math.max(this.getHeight() >> (long)level, 1L);
    }

    /**
     * Returns the nominal width of a tile.
     * 
     * @return The nominal width of a tile.
     */
    public abstract int getTileWidth();

    /**
     * Returns the width of the tile in the specified column at the specified
     * level.
     * 
     * @return  The width of the tile in the specified column at the specified
     *          level.
     */
    public final int getTileWidth(int level, long tileColumn) {
        final long maxX = this.getWidth(level);
        final int tileWidth = this.getTileWidth();
        long retval = tileWidth;
        if ((retval * (tileColumn + 1)) > maxX)
            retval = maxX - ((long)tileWidth * tileColumn);
        return (int)retval;
    }

    /**
     * Returns the nominal height of a tile.
     * 
     * @return The nominal height of a tile.
     */
    public abstract int getTileHeight();

    /**
     * Returns the height of the tile in the specified row at the specified
     * level.
     * 
     * @return  The height of the tile in the specified row at the specified
     *          level.
     */
    public final int getTileHeight(int level, long tileRow) {
        final long maxY = this.getHeight(level);
        final int tileHeight = this.getTileHeight();
        long retval = tileHeight;
        if ((retval * (tileRow + 1)) > maxY)
            retval = maxY - ((long)tileHeight * tileRow);
        return (int)retval;
    }

    /**
     * Reads the specified tile at the specified level and stores the data in
     * the specified array. The returned data will have dimensions consistent
     * with {@link #getTileWidth(int, long)} and {@link #getTileHeight(int, long)}
     * for the specified level, tile column and tile row.
     * 
     * @param level         The resolution level
     * @param tileColumn    The tile column
     * @param tileRow       The tile row
     * @param data          Output buffer for the data of the specified tile
     * 
     * @return  <code>true</code> if the read completed successfuly,
     *          <code>false</code> if the operation was canceled asynchronously
     */
    public ReadResult read(int level, long tileColumn, long tileRow, byte[] data) {
        if (level < 0)
            throw new IllegalArgumentException();

        return this.read(this.getTileSourceX(level, tileColumn),
                         this.getTileSourceY(level, tileRow),
                         this.getTileSourceWidth(level, tileColumn),
                         this.getTileSourceHeight(level, tileRow),
                         this.getTileWidth(level, tileColumn),
                         this.getTileHeight(level, tileRow),
                         data);
    }

    /**
     * Reads an arbitrary region of the image at an arbitrary scale. The source
     * data will be scaled to meet <code>dstW</code> and <code>dstH</code>. 
     * 
     * @param srcX  The source (unscaled) x-coordinate of the region
     * @param srcY  The source (unscaled) y-coordinate of the region
     * @param srcW  The source (unscaled) width of the region
     * @param srcH  The source (unscaled) height of the region
     * @param dstW  The output width
     * @param dstH  The output size
     * @param buf   Output buffer for the data of the specified tile
     * 
     * @return  <code>true</code> if the read completed successfuly,
     *          <code>false</code> if the operation was canceled asynchronously
     *          
     * @see #read(int, long, long, byte[])
     */
    public abstract ReadResult read(long srcX, long srcY, long srcW, long srcH, int dstW,
            int dstH, byte[] buf);

    /**
     * Performs an asynchronous read of a single tile from the dataset at the
     * specified resolution level.
     * 
     * @param level         The resolution level
     * @param tileColumn    The tile column at the specified resolution level
     * @param tileRow       The tile column at the specified resolution level
     * @param callback      The callback listener that the data will be
     *                      delivered to; may not be <code>null</code>.
     */
    public void asyncRead(int level, long tileColumn, long tileRow,
            AsynchronousReadRequestListener callback) {
        if (level < 0)
            throw new IllegalArgumentException();

        synchronized (this.asynchronousIO.syncOn) {
            this.asyncRead(new ReadRequest(this.asyncRequestId++, level, tileColumn, tileRow,
                    callback));
        }
    }

    /**
     * Performs an asynchronous read of an arbitrary region of the image at an
     * arbitrary scale. The source data will be scaled to meet <code>dstW</code>
     * and <code>dstH</code>. 
     * 
     * @param srcX      The source (unscaled) x-coordinate of the region
     * @param srcY      The source (unscaled) y-coordinate of the region
     * @param srcW      The source (unscaled) width of the region
     * @param srcH      The source (unscaled) height of the region
     * @param dstW      The output width
     * @param dstH      The output size
     * @param callback  The callback listener that the data will be delivered
     *                  to; may not be <code>null</code>.
     *          
     * @see #asyncRead(int, long, long, AsynchronousReadRequestListener)
     */
    public void asyncRead(long srcX, long srcY, long srcW, long srcH, int dstW, int dstH,
            AsynchronousReadRequestListener callback) {
        synchronized (this.asynchronousIO.syncOn) {
            this.asyncRead(new ReadRequest(this.asyncRequestId++, srcX, srcY, srcW, srcH, dstW,
                    dstH, callback));
        }
    }

    private void asyncRead(ReadRequest retval) {
        retval.callback.requestCreated(retval);
        this.asynchronousIO.runLater(this, new ReadRequestTask(this, retval));
    }

    /**
     * Implements asynchronous cancel. This method is invoked by the actively
     * servicing {@link ReadRequest} and should never be invoked externally.
     * 
     * <P>The default implementation returns immediately.
     */
    protected void cancel() {}
    
    /**
     * Fills the specified asynchronous {@link ReadRequest}.
     * 
     * <P>This method will return immediately if the <code>TileReader</code> has
     * been previously disposed or if the source or destination regions for the
     * request are empty. 
     * 
     * @param request   The request to be filled.
     */
    protected final ReadResult fill(ReadRequest request) {
        synchronized (this.readLock) {
            if (!this.valid)
                request.canceled = true;

            // if the request was asynchronously canceled or if the ROI is empty
            // ignore
            if (request.canceled ||
                request.srcW == 0 ||
                request.srcH == 0 ||
                request.dstW == 0 ||
                request.dstH == 0) {

                return request.canceled ? ReadResult.CANCELED : ReadResult.SUCCESS;
            }

            return this.fillImpl(request);
        }
    }

    /**
     * Fills the asynchronous read request. This method should issue the
     * {@link AsynchronousReadRequestListener#requestUpdate(int, byte[], int, int, int, int) AsynchronousReadRequest.requestUpdate}
     * and
     * {@link AsynchronousReadRequestListener#requestError(int, Throwable) AsynchronousReadRequest.requestError}
     * callbacks as appropriate before this method returns. The
     * {@link AsynchronousReadRequestListener#requestStarted(int) AsynchronousReadRequest.requestStarted},
     * {@link AsynchronousReadRequestListener#requestCanceled(int) AsynchronousReadRequest.requestCanceled},
     * and
     * {@link AsynchronousReadRequestListener#requestCompleted(int) AsynchronousReadRequest.requestCompleted}
     * will be issued as appropriate externally.
     * 
     * <P>This method should always be invoked after being externally
     * synchronized on <code>this.readLock</code>.
     * 
     * @param request
     */
    private ReadResult fillImpl(ReadRequest request) {
        if (request.level >= 0 && request.level < this.getMinCacheLevel())
            return this.fillDirect(request);
        else
            return this.fillCache(request);
    }
    
    private ReadResult fillDirect(ReadRequest request) {
        final int size = this.getTransferSize(request.dstW, request.dstH);
        final byte[] readBuffer = this.asynchronousIO.getReadBuffer(size);

        final ReadResult retval = this.read(request.srcX,
                                            request.srcY,
                                            request.srcW,
                                            request.srcH,
                                            request.dstW,
                                            request.dstH, readBuffer);

        if (retval == ReadResult.SUCCESS) {
            this.dispatchUpdate(request,
                                readBuffer,
                                0, 0,
                                request.dstW, request.dstH);
        }

        return retval;
    }
    
    private ReadResult fillCache(ReadRequest request) {
        if(this.cache == null && this.cacheUri != null) {
            try {
                this.cache = new TileCache(this.cacheUri, this);
            } catch(Throwable t) {
                Log.e("CachingTileSource", "Failed to open tile cache " + this.cacheUri, t);
                this.minCacheLevel = Integer.MAX_VALUE;
                this.cache = null;
            } finally {
                this.cacheUri = null;
            }
            
            // failed to create the cache, fallback on default implementation
            if(this.cache == null) {
                return this.fillDirect(request);
            }
        }
        
        try {
            this.cache.getTile(null,
                               request.tileRow,
                               request.tileColumn,
                               request.level,
                               this.asReadCallback(request));

            return request.canceled ? ReadResult.CANCELED : ReadResult.SUCCESS;
        } catch (Throwable t) {
            // if the cache read failed, fallback on the default implementation
            Log.e(TAG, "error: ", t);

            this.cache = null;
            this.minCacheLevel = Integer.MAX_VALUE;

            return this.fillDirect(request);
        }
    }

    /**
     * Returns the number of tile columns at native resolution.
     * 
     * @return  The number of tile columns at native resolution.
     */
    public final long getNumTilesX() {
        return this.getNumTilesX(0);
    }

    /**
     * Returns the number of tile columns at the specified resolution level.
     * 
     * @param level The resolution level
     * 
     * @return  The number of tile columns at the specified resolution level.
     */
    public final long getNumTilesX(int level) {
        return (int) Math.ceil((double) this.getWidth(level) / (double) this.getTileWidth());
    }

    /**
     * Returns the number of tile rows at native resolution.
     * 
     * @return  The number of tile rows at native resolution.
     */
    public final long getNumTilesY() {
        return this.getNumTilesY(0);
    }

    /**
     * Returns the number of tile rows at the specified resolution level.
     * 
     * @param level The resolution level
     * 
     * @return  The number of tile rows at the specified resolution level.
     */
    public final long getNumTilesY(int level) {
        return (int) Math.ceil((double) this.getHeight(level) / (double) this.getTileHeight());
    }

    /**
     * Returns the x-coordinate, at native resolution, of the specified tile.
     *  
     * @param level         The resolution level
     * @param tileColumn    The tile column.
     * 
     * @return  The x-coordinate, at native resolution, of the specified tile.
     */
    public final long getTileSourceX(int level, long tileColumn) {
        return tileColumn * ((long)this.getTileWidth() << (long)level);
    }

    /**
     * Returns the y-coordinate, at native resolution, of the specified tile.
     *  
     * @param level     The resolution level
     * @param tileRow   The tile row.
     * 
     * @return  The y-coordinate, at native resolution, of the specified tile.
     */
    public final long getTileSourceY(int level, long tileRow) {
        return tileRow * ((long)this.getTileHeight() << (long)level);
    }

    /**
     * Returns the width, at native resolution, of the specified tile.
     *  
     * @param level         The resolution level
     * @param tileColumn    The tile column.
     * 
     * @return  The width, at native resolution, of the specified tile.
     */
    public final long getTileSourceWidth(int level, long tileColumn) {
        long retval = ((long)this.getTileWidth() << (long)level);
        final long maxX = this.getWidth(0);
        if ((retval * (tileColumn + 1)) > maxX)
            retval = (maxX - (retval * tileColumn));
        return retval;
    }

    /**
     * Returns the height, at native resolution, of the specified tile.
     *  
     * @param level     The resolution level
     * @param tileRow   The tile row.
     * 
     * @return  The height, at native resolution, of the specified tile.
     */
    public final long getTileSourceHeight(int level, long tileRow) {
        long retval = ((long)this.getTileHeight() << (long)level);
        final long maxY = this.getHeight(0);
        if ((retval * (tileRow + 1)) > maxY)
            retval = (maxY - (retval * tileRow));
        return retval;
    }

    public final long getTileColumn(int level, long srcX) {
        return (long) ((double) srcX / (double) ((long)this.getTileWidth() << (long)level));
    }

    public final long getTileRow(int level, long srcY) {
        return (long) ((double) srcY / (double) ((long)this.getTileHeight() << (long)level));
    }
    
    /**
     * Returns the pixel format of the data as it will be returned from the
     * <code>read</code> and <code>asyncRead</code> methods.
     * 
     * @return  The pixel format of the data
     */
    public abstract Format getFormat();

    /**
     * Returns the interleave mode of the data as it will be returned from the
     * <code>read</code> and <code>asyncRead</code> methods.
     * 
     * @return  The interleave mode of the data
     */
    public abstract Interleave getInterleave();
    
    /**
     * Disposes the <code>TileReader</code> and releases any allocated
     * resources. Any pending asynchronous read requests will be aborted. The
     * user should always cancel any in-progress asynchronous read request
     * before invoking this method to avoid having to wait for the request to
     * complete before this method can return.
     */
    public final void dispose() {
        this.dispose(null);
    }

    /**
     * Disposes the <code>TileReader</code> and releases any allocated
     * resources. Any pending asynchronous read requests will be aborted. The
     * user should always cancel any in-progress asynchronous read request
     * before invoking this method to avoid having to wait for the request to
     * complete before this method can return.
     * 
     * @param releaseHook   If non-<code>null</code>, a hook that will be
     *                      executed on the {@link AsynchronousIO} thread after
     *                      all pending read requests have been aborted.
     */
    public final void dispose(final Runnable releaseHook) {
        Thread t = new Thread(TAG + "-Dispose") {
            @Override
            public void run() {
                try {
                    disposeImpl(releaseHook);
                } catch(Throwable t) {
                    Log.w(TAG, "Error occurred during TileReader disposal", t);
                }
            }
        };
        t.setPriority(Thread.NORM_PRIORITY);
        t.start();
    }

    private void disposeImpl(Runnable releaseHook) {
        synchronized (this.asynchronousIO.syncOn) {
            // abort all of our pending requests
            this.asynchronousIO.abortRequests(this);
            // perform any user requested clean-up
            if (releaseHook != null)
                this.asynchronousIO.runLater(this, releaseHook);
            // if we are the exclusive owner of the async I/O thread, release it
            if (this.asynchronousIO.syncOn == this)
                this.asynchronousIO.runLater(this, new Runnable() {
                    @Override
                    public void run() {
                        TileReader.this.asynchronousIO.release();
                    }
                });
            

            // XXX - move onto async IO thread ??? we could get stuck here for
            //       a while if there is a request being serviced that hasn't
            //       been canceled
            synchronized(TileReader.this.readLock) {
                TileReader.this.disposeImpl();
            }
        }
    }
    
    /**
     * Implementation specific disposal. Implementations should always invoke
     * <code>super.disposeImpl()</code>.
     * 
     * <P>The default implementation marks {@link #valid} as <code>false</code>.
     * 
     * <P>This method will always be externally synchronized on
     * <code>this.readLock</code>.
     */
    protected void disposeImpl() {
        // mark as invalid to prevent final possible asynchronous read after
        // this method returns
        this.valid = false;
        
        if(this.cache != null) {
            this.cache.close();
            this.cache = null;
        }
    }
    
    /** @hide */
    protected void cancelChild(TileReader child) {
        child.cancel();
    }
    
    /**
     * Returns the maximum number of resolution levels supported by this tile
     * source.
     * 
     * @return  The maximum number of resolution levels supported by this tile
     *          source.
     */
    public int getMaxNumResolutionLevels() {
        return getNumResolutionLevels(this.getWidth(),
                                      this.getHeight(),
                                      this.getTileWidth(),
                                      this.getTileHeight());
    }
    
    /**
     * Returns a flag indicating whether or not the source is multi-resolution.
     * If <code>true</code> the source can decode the image at resolutions other
     * than the native resolution without subsampling. If <code>false</code> all
     * tiles at a resolution other than the native resolution must be
     * subsampled.
     * 
     * <P>Use of a  
     * {@link com.atakmap.map.layer.raster.tilereader.TileCache TileCache}
     * does not make a <code>TileReader</code> multi-resolution.
     * 
     * @return  <code>true</code> if the source contains tiles at multiple
     *          resolutions, <code>false</code> otherwise.
     */
    public boolean isMultiResolution() {
        return false;
    }
    
    /**
     * Returns a flag indicating whether or not the specified request has been
     * canceled.
     * 
     * @param request   An asynchronous read request
     * 
     * @return  <code>true</code> if the request has been canceled,
     *          <code>false</code> otherwise.
     */
    protected boolean isCanceled(ReadRequest request) {
        return request.canceled;
    }
    
    /**
     * Dispatches an update for the specified asynchronous read request.
     * 
     * @param request   The request
     * @param data      The update data; contains <code>dstW*dstH</code> pixels
     * @param dstX      The x-coordinate for the update data in the destination
     *                  pixel buffer
     * @param dstY      The y-coordinate for the update data in the destination
     *                  pixel buffer
     * @param dstW      The width of the update data
     * @param dstH      The height of the udpdate data
     */
    protected void dispatchUpdate(ReadRequest request, byte[] data, int dstX, int dstY, int dstW, int dstH) {
        request.callback.requestUpdate(request.id, data, dstX, dstY, dstW, dstH);
    }

    /**
     * Dispatches an error for the specified asynchronous read request.
     * 
     * @param request   The request
     * @param error     The error that occurred.
     */
    protected void dispatchError(ReadRequest request, Throwable error) {
        request.callback.requestError(request.id, error);
    }

    /**
     * Returns the transfer size, in bytes, for a buffer of <code>width</code>
     * by <code>height</code> pixels.
     * 
     * <P>Defaults to <code>this.getPixelSize()*width*height</code>.
     * 
     * @param width     The width of the output buffer
     * @param height    The height of the output buffer
     * 
     * @return  The number of bytes required for an output buffer capable of
     *          holding <code>width</code> by <code>height</code> pixels.
     */
    protected int getTransferSize(int width, int height) {
        return this.getPixelSize()*width*height;
    }
    
    /**
     * Returns the size of a pixel, in bytes.
     * 
     * @return  The size of a pixel, in bytes.
     */
    public int getPixelSize() {
        return getPixelSize(this.getFormat());
    }
    
    // cache support

    public TileCacheData.Allocator getTileCacheDataAllocator() {
        return null;
    }

    public TileCacheData.Compositor getTileCacheDataCompositor() {
        return null;
    }

    public TileCacheData.Serializer getTileCacheDataSerializer() {
        return null;
    }
    
    /**
     * Returns the minimum RSET level where the tile cache will be employed. Any
     * requested level less than the returned value will be read and subsampled
     * directly from the dataset every time.
     * 
     * @return
     */
    public final int getMinCacheLevel() {
        return this.minCacheLevel;
    }
    
    protected TileCache.ReadCallback asReadCallback(final ReadRequest request) {
        return new TileCache.ReadCallback() {
            @Override
            public boolean canceled() {
                return TileReader.this.isCanceled(request);
            }

            @Override
            public void update(int dstX, int dstY, int dstW, int dstH, byte[] data, int off,
                               int len) {

                TileReader.this.dispatchUpdate(request,
                                               data,
                                               dstX, dstY,
                                               dstW, dstH);
            }
        };
    }
    
    /**
     * Instructs the <code>TileReader</code> that more than one tile may be
     * consecutively requested.
     * 
     * <P>This merely serves as a hint to the reader; tiles may be requested
     * regardless of whether <code>start()</code> had previously been invoked.
     */
    public void start() {}
    
    /**
     * Instructs the <code>TileReader</code> that the bulk requests indicated
     * by <code>start()</code> will cease.
     * 
     * <P>This merely serves as a hint to the reader; tiles may continue to be
     * requested even after <code>stop()</code> has been invoked.
     */
    public void stop() {}
    
    /**
     * Returns the version for the specified tile. This value may change over
     * the duration of the runtime, indicating that tiles previously read with
     * a different version are not the most up-to-date and should be reloaded.
     * Version numbers should be monotonically increasing.
     * 
     * @param level         The level
     * @param tileColumn    The tile column
     * @param tileRow       The tile row
     * 
     * @return  The current tiles version. The default implementation always
     *          returns <code>0</code>
     */
    public long getTileVersion(int level, long tileColumn, long tileRow) {
        return 0L;
    }

    protected final void registerControl(Object control) {
        synchronized(this.controls) {
            this.controls.add(control);
        }
    }
    
    protected final void unregisterControl(Object control) {
        synchronized(this.controls) {
            this.controls.remove(control);
        }
    }
    
    @Override
    public final <C> C getControl(Class<C> controlClazz) {
        synchronized(this.controls) {
            for(Object c : this.controls) {
                if(controlClazz.isAssignableFrom(c.getClass()))
                    return controlClazz.cast(c);
            }
            return null;
        }
    }
    
    @Override
    public final void getControls(Collection<Object> ctrls) {
        synchronized(this.controls) {
            ctrls.addAll(this.controls);
        }
    }

    protected final void asyncRun(Runnable r) {
        this.asynchronousIO.runLater(this, r);
    }

    /**************************************************************************/

    /**
     * Returns the number of resolution levels possible given the specified
     * image and tile dimensions.
     * 
     * @param width         The image width
     * @param height        The image height
     * @param tileWidth     The tile width
     * @param tileHeight    The tile height
     * 
     * @return  The number of resolution levels possible given the specified
     *          image and tile dimensions.
     */
    public static int getNumResolutionLevels(long width, long height, long tileWidth, long tileHeight) {
        if(tileWidth <= 0 || tileHeight <= 0)
            throw new IllegalArgumentException();

        long numTilesX = (long) Math.ceil((double) width / (double) tileWidth);
        long numTilesY = (long) Math.ceil((double) height / (double) tileHeight);

        int retval = 1;
        while (numTilesX > 1 && numTilesY > 1) {
            width = Math.max(width >> 1L, 1L);
            height = Math.max(height >> 1L, 1L);
            numTilesX = (long) Math.ceil((double) width / (double) tileWidth);
            numTilesY = (long) Math.ceil((double) height / (double) tileHeight);
            retval++;
        }
        return retval;
    }
    
    /**
     * Returns the pixel size of the specified format, in bytes.
     * 
     * @param format    The pixel format
     * 
     * @return  The pixel size of the specified format, in bytes.
     */
    public static int getPixelSize(Format format) {
        switch (format) {
            case MONOCHROME:
                return 1;
            case MONOCHROME_ALPHA:
                return 2;
            case RGB:
                return 3;
            case ARGB:
            case RGBA:
                return 4;
            default:
                throw new IllegalStateException();
        }
    }

    public static synchronized AsynchronousIO getMasterIOThread() {
        if (masterIOThread == null)
            masterIOThread = new TileReader.AsynchronousIO();
        return masterIOThread;
    }

    /**************************************************************************/
    

    /**
     * Runnable that supports cancellation.
     * 
     * @author Developer
     */
    private static interface Cancelable extends Runnable {
        /**
         * Attempts to cancel the {@link Runnable} and cause the {@link #run()}
         * method to return earlier than it would during normal execution.
         */
        public void cancel();
    } // Cancelable

    /**
     * An asynchronous read request. Defines the region or tile to be read and
     * provides a mechanism to cancel the request asynchronously.
     * 
     * @author Developer
     */
    public final class ReadRequest {
        /**
         * The ID for the request. IDs are monotonically increasing per
         * <code>TileReader</code>.
         */
        public final int id;

        private boolean canceled;
        private boolean servicing = false;

        /**
         * The source (unscaled) x-coordinate of the region to be read.
         */
        public final long srcX;
        /**
         * The source (unscaled) y-coordinate of the region to be read.
         */
        public final long srcY;
        /**
         * The source (unscaled) width of the region to be read.
         */
        public final long srcW;
        /**
         * The source (unscaled) height of the region to be read.
         */
        public final long srcH;
        /**
         * The output width of the region.
         */
        public final int dstW;
        /**
         * The output height of the region.
         */
        public final int dstH;

        /**
         * The tile row to be read. Will be <code>-1</code> if the read request
         * was made over an arbitrary region.
         */
        public final long tileRow;
        /**
         * The tile column to be read. Will be <code>-1</code> if the read
         * request was made over an arbitrary region.
         */
        public final long tileColumn;
        /**
         * The tile level to be read. Will be <code>-1</code> if the read
         * request is over an arbitrary region.
         */
        public final int level;

        private final AsynchronousReadRequestListener callback;

        private ReadRequest(int id, int level, long tileColumn, long tileRow,
                AsynchronousReadRequestListener callback) {
            this(id,
                 TileReader.this.getTileSourceX(level, tileColumn),
                 TileReader.this.getTileSourceY(level, tileRow),
                 TileReader.this.getTileSourceWidth(level, tileColumn),
                 TileReader.this.getTileSourceHeight(level, tileRow),
                 TileReader.this.getTileWidth(level, tileColumn),
                 TileReader.this.getTileHeight(level, tileRow),
                 level,
                 tileColumn,
                 tileRow,
                 callback);
        }

        private ReadRequest(int id, long srcX, long srcY, long srcW, long srcH, int dstW, int dstH,
                AsynchronousReadRequestListener callback) {
            this(id, srcX, srcY, srcW, srcH, dstW, dstH, -1, -1, -1, callback);
        }

        private ReadRequest(int id, long srcX, long srcY, long srcW, long srcH, int dstW, int dstH,
                int level, long tileColumn, long tileRow, AsynchronousReadRequestListener callback) {
            this.id = id;
            this.srcX = srcX;
            this.srcY = srcY;
            this.srcW = srcW;
            this.srcH = srcH;
            this.dstW = dstW;
            this.dstH = dstH;
            this.level = level;
            this.tileColumn = tileColumn;
            this.tileRow = tileRow;
            this.callback = callback;

            this.canceled = false;
            this.servicing = false;
        }

        /**
         * Cancels the read request. If the request is currently be serviced, an
         * attempt is made to abort the in-progress operation.
         * 
         * <P>This method may be invoked from any thread.
         */
        public void cancel() {
            this.canceled = true;
            if (this.servicing)
                TileReader.this.cancel();
        }

        public String toString() {
            return "ReadRequest {id=" + id + ",srcX=" + srcX + ",srcY=" + srcY + ",srcW=" + srcW
                    + ",srcH=" + srcH + ",dstW=" + dstW + ",dstH=" + dstH + ",canceled=" + canceled
                    + "}";
        }
    }

    final static class ReadRequestTask implements Cancelable {
        final TileReader reader;
        final ReadRequest request;

        ReadRequestTask(TileReader reader, ReadRequest request) {
            this.reader = reader;
            this.request = request;
        }
        @Override
        public void cancel() {
            request.cancel();
            request.callback.requestCanceled(request.id);
        }

        @Override
        public void run() {
            ReadResult result = null;
            Throwable err = null;
            try {
                request.servicing = true;
                request.callback.requestStarted(request.id);
                // long s = SystemClock.elapsedRealtime();
                result = reader.fill(request);
                // long e = SystemClock.elapsedRealtime();
                // if(request.tileColumn != -1)
                // Log.d(TAG, "read request (" + level + "," + tileColumn + "," +
                // tileRow + ") in " + (e-s) + "ms");
            } catch (Throwable t) {
                result = ReadResult.ERROR;
                err = t;
            } finally {
                if(result == null)
                    throw new IllegalStateException();

                switch(result) {
                    case SUCCESS :
                        request.callback.requestCompleted(request.id);
                        break;
                    case ERROR :
                        request.callback.requestError(request.id, err);
                        break;
                    case CANCELED :
                        request.callback.requestCanceled(request.id);
                        break;
                    default :
                        throw new IllegalStateException();
                }
                request.servicing = false;
            }
        }
    }

    /**
     * Callback interface for asynchronous reads.
     * 
     * @author Developer
     */
    public static interface AsynchronousReadRequestListener {
        /**
         * This method is invoked when the read request is created.
         * 
         * @param request   The request that was created
         */
        public void requestCreated(ReadRequest request);

        /**
         * This method is invoked immediately before a read request begins being
         * serviced.
         * 
         * @param id    The ID of the read request about to be serviced.
         */
        public void requestStarted(int id);

        /**
         * This method is invoked while the read request is being serviced to
         * deliver the data that is being read. Coordinates specified are always
         * relative to the output buffer. This method may be invoked multiple
         * times while the request is being serviced.
         * 
         * @param id    The ID of the read request currently being serviced
         * @param data  The data for the update
         * @param dstX  The x-coordinate in the full output buffer of the update
         *              data
         * @param dstY  The y-coordinate in the full output buffer of the update
         *              data
         * @param dstW  The width in the full output buffer of the update data
         * @param dstH  The height in the full output buffer of the update data
         */
        public void requestUpdate(int id, byte[] data, int dstX, int dstY, int dstW, int dstH);

        /**
         * This method is invoked when the request is successfully completed.
         * This method will only be invoked for a read request that the
         * {@link #requestStarted(int)} method had been previously invoked for.
         *
         * @param id    The ID of the read request that completed
         */
        public void requestCompleted(int id);

        /**
         * This method is invoked if the read request was canceled while being
         * serviced. This method will only be invoked for a read request that
         * the {@link #requestStarted(int)} method had been previously invoked
         * for.
         * 
         * @param id    The ID of the read request that was canceled
         */
        public void requestCanceled(int id);

        /**
         * This method is invoked if an error occurs while servicing the read
         * request. This method will only be invoked for a read request that
         * the {@link #requestStarted(int)} method had been previously invoked
         * for.
         * 
         * @param id    The ID of the read request that was canceled
         * @param error The error that occurred.
         */
        public void requestError(int id, Throwable error);        
    } // AsynchronousReadRequestListener

    /**
     * The asynchronous I/O thread for use by one or more
     * <code>TileReader</code> instances. The same instance can be utilized by
     * multiple <code>TileReader</code> objects to ensure data reading and
     * delivery in series rather than in parallel. Forcing request servicing
     * into series rather than in parallel can be advantageous in memory
     * constrained environments when trying to render virtual mosaics from
     * multiple files/tile readers onto a single canvas.
     * 
     * @author Developer
     */
    public final static class AsynchronousIO {
        private Map<TileReader, RequestQueue> tasks;
        private Task executing;
        private final Object syncOn;
        private boolean dead;
        private boolean started;
        private final long maxIdle;

        private byte[] readBuffer;

        public AsynchronousIO() {
            this(null, 0L);
        }
        
        public AsynchronousIO(long maxIdle) {
            this(null, maxIdle);
        }

        public AsynchronousIO(Object syncOn) {
            this(syncOn, 0L);
        }

        public AsynchronousIO(Object syncOn, long maxIdle) {
            if (syncOn == null)
                syncOn = this;
            this.syncOn = syncOn;
            this.tasks = new HashMap<>();
            this.dead = true;
            this.started = false;
            this.maxIdle = maxIdle;

            this.readBuffer = null;
        }

        private byte[] getReadBuffer(int size) {
            if (this.readBuffer == null || this.readBuffer.length < size)
                this.readBuffer = new byte[size];
            return this.readBuffer;
        }

        /**
         * Aborts all unserviced tasks and kills the thread. If a task is
         * currently being serviced, it will complete before the thread exits.
         * The thread may be restarted by queueing a new task.
         */
        public void release() {
            synchronized (this.syncOn) {
                this.abortRequests(null);
                this.dead = true;
                this.syncOn.notify();
            }
        }

        /**
         * Aborts all unserviced tasks made by the specified reader. If
         * <code>null</code> tasks for all readers are aborted.
         * 
         * @param reader
         */
        public void abortRequests(TileReader reader) {
            synchronized (this.syncOn) {
                if(this.executing != null && executing.reader == reader) {
                    if (this.executing.action instanceof Cancelable)
                        ((Cancelable) this.executing.action).cancel();
                }

                if(reader != null) {
                    final RequestQueue queue = this.tasks.remove(reader);
                    while (queue != null) {
                        Task t = queue.get();
                        if (t == null)
                            break;
                        if (t.action instanceof Cancelable)
                            ((Cancelable) t.action).cancel();
                    }
                } else {
                    for(Map.Entry<TileReader, RequestQueue> entry : tasks.entrySet()) {
                        while (true) {
                            Task t = entry.getValue().get();
                            if (t == null)
                                break;
                            if (t.action instanceof Cancelable)
                                ((Cancelable) t.action).cancel();
                        }
                    }
                    tasks.clear();
                }
            }
        }

        public void setReadRequestPrioritizer(TileReader reader, Comparator<ReadRequest> prioritizer) {
            synchronized(this.syncOn) {
                RequestQueue queue = this.tasks.get(reader);
                if(queue == null)
                    this.tasks.put(reader, queue=new RequestQueue());
                queue.requestPrioritizer = prioritizer;
            }
        }

        private void runLater(TileReader reader, Runnable r) {
            synchronized (this.syncOn) {
                RequestQueue queue = this.tasks.get(reader);
                if(queue == null)
                    this.tasks.put(reader, queue=new RequestQueue());
                queue.enqueue(new Task(reader, r));
                this.syncOn.notify();

                this.dead = false;
                if (!this.started) {
                    Thread t = new Thread() {
                        @Override
                        public void run() {
                            AsynchronousIO.this.runImpl();
                        }
                    };
                    t.setPriority(Thread.MIN_PRIORITY);
                    t.setName("tilereader-async-io-thread@" + Integer.toString(this.hashCode(), 16));

                    this.started = true;
                    t.start();
                }
            }
        }

        private void runImpl() {
            Task task;
            while (true) {
                synchronized (this.syncOn) {
                    this.executing = null;
                    if (this.dead) {
                        this.started = false;
                        break;
                    }

                    // iterate all request queues and select the oldest task.
                    // oldest task is used here to prevent starvation
                    RequestQueue rq = null;
                    int candidateId = Integer.MAX_VALUE;
                    for(RequestQueue queue : tasks.values()) {
                        final Task t = queue.peek();
                        if(t == null)
                            continue;
                        if(t.id < candidateId) {
                            rq = queue;
                            candidateId = t.id;
                        }
                    }

                    if (rq == null) {
                        final long startIdle = SystemClock.elapsedRealtime();
                        this.readBuffer = null;
                        try {
                            this.syncOn.wait(this.maxIdle);
                        } catch (InterruptedException ignored) {
                        }
                        final long stopIdle = SystemClock.elapsedRealtime();
                        // check if the thread has idle'd out
                        if(this.maxIdle > 0L && (stopIdle-startIdle) >= this.maxIdle)
                            this.dead = true;
                        // wake up and re-run the sync block
                        continue;
                    }

                    task = rq.get();
                    this.executing = task;
                }

                try {
                    task.action.run();
                } catch (RuntimeException e) {
                    Log.e(TAG, "error: ", e);
                }
            }
        }

        final static class Task {
            final static AtomicInteger idGenerator = new AtomicInteger(0);
            public final TileReader reader;
            public final Runnable action;
            public final int id;

            public Task(TileReader reader, Runnable action) {
                this.reader = reader;
                this.action = action;
                this.id = idGenerator.getAndIncrement();
            }
        }
    } // AsynchronousIO


    final static class RequestQueue implements Comparator<AsynchronousIO.Task> {
        // sort order for tasks is LO => HI priority
        ArrayList<AsynchronousIO.Task> tasks = new ArrayList<>(64);
        Comparator<ReadRequest> requestPrioritizer;

        AsynchronousIO.Task peek() {
            if(tasks.isEmpty())
                return null;
            return tasks.get(tasks.size()-1);
        }

        AsynchronousIO.Task get() {
            if(tasks.isEmpty())
                return null;
            return tasks.remove(tasks.size()-1);
        }

        void enqueue(AsynchronousIO.Task task) {
            tasks.add(task);
            Collections.sort(tasks, this);
            while(!tasks.isEmpty()) {
                final int idx = tasks.size()-1;
                final AsynchronousIO.Task t = tasks.get(idx);
                if(t.action instanceof ReadRequestTask && ((ReadRequestTask)t.action).request.canceled)
                    tasks.remove(idx);
                else
                    break;
            }
        }

                @Override
        public int compare(AsynchronousIO.Task a, AsynchronousIO.Task b) {
            if(a == null && b == null)
                return 0;
            else if(a == null)
                return -1;
            else if(b == null)
                return 1;
            final boolean aIsRead = (a.action instanceof ReadRequestTask);
            final boolean bIsRead = (b.action instanceof ReadRequestTask);
            if(!aIsRead && !bIsRead)
                return b.id-a.id; // FIFO on non read requests
            else if(!aIsRead)
                return 1;
            else if(!bIsRead)
                return -1;

            final ReadRequest aRequest = ((ReadRequestTask)a.action).request;
            final ReadRequest bRequest = ((ReadRequestTask)b.action).request;
            if(aRequest.canceled && bRequest.canceled)
                return b.id-a.id;
            else if(aRequest.canceled)
                return 1;
            else if(bRequest.canceled)
                return -1;
            if(this.requestPrioritizer != null) {
                final int c = this.requestPrioritizer.compare(aRequest, bRequest);
                if(c != 0)
                    return c;
            }

            // prioritize high resolution tiles over low resolution tiles
            if(aRequest.level < bRequest.level)
                return 1;
            else if(aRequest.level > bRequest.level)
                return -1;
            else
                return a.id-b.id; // LIFO on read requests
        }
    }
} // TileReader
