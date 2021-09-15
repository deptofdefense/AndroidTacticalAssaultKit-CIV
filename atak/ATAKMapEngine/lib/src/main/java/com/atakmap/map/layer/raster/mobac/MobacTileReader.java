package com.atakmap.map.layer.raster.mobac;

import java.io.File;
import java.io.IOException;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.raster.controls.TileClientControl;
import com.atakmap.map.layer.raster.controls.TileCacheControl;
import com.atakmap.map.layer.raster.tilematrix.TileClient;
import com.atakmap.map.layer.raster.tilematrix.TileClientSpi;
import com.atakmap.map.layer.raster.tilepyramid.AbstractTilePyramidTileReader;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderSpi;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory.Options;
import com.atakmap.util.IntervalMonitor;

public class MobacTileReader extends AbstractTilePyramidTileReader {

    public final static TileReaderSpi SPI = new TileReaderSpi() {
        @Override
        public String getName() {
            return "mobac";
        }

        @Override
        public TileReader create(String uri, Options options) {
            File file = new File(uri);
            if(!IOProviderFactory.exists(file))
                return null;
            
            try {
                MobacMapSource source = MobacMapSourceFactory.create(file);
                if(source == null)
                    return null;
                
                String cacheUri = null;
                AsynchronousIO io = TileReader.getMasterIOThread();
                if(options != null) {
                    cacheUri = options.cacheUri;
                    if(options.asyncIO != null)
                        io = options.asyncIO;
                }
    
                return new MobacTileReader(uri, cacheUri, io, source);
            } catch(Exception e) {
                return null;
            }
        }
        
        @Override
        public boolean isSupported(String uri) {
            File file = new File(uri);
            if(!IOProviderFactory.exists(file))
                return false;

            try {
                final MobacMapSource source = MobacMapSourceFactory.create(file);
                return (source != null);
            } catch(IOException e) {
                return false;
            }
        }
    };

    private final static Class<?>[] transferControls = new Class[]
    {
        TileClientControl.class,
    };

    private final int maxZoom;
    private MobacMapSource source;
    private TileClient client;
    private final TileClientControl control;
    private final TileCacheControl cacheControl;
    private IntervalMonitor refreshMonitor = new IntervalMonitor();
    
    private long version;

    // package private on deprecate complete
    /**
     * @deprecated use {@link com.atakmap.map.layer.raster.tilereader.TileReaderFactory#create(String, Options)}
     */
    @Deprecated
    @DeprecatedApi(since = "4.3", forRemoval = true, removeAt = "4.6")
    public MobacTileReader(String uri, String cacheUri, AsynchronousIO asyncIO, MobacMapSource mapSource) throws IOException {
        super(uri,
              null,
              asyncIO,
              mapSource.getMaxZoom()-mapSource.getMinZoom()+1,
              (long)(mapSource.getTileSize()*getGridWidth(mapSource))<<(long)mapSource.getMaxZoom(),
              (long)(mapSource.getTileSize()*getGridHeight(mapSource))<<(long)mapSource.getMaxZoom(),
              mapSource.getTileSize(),
              mapSource.getTileSize());

        this.source = mapSource;
        TileClientSpi.Options opts = new TileClientSpi.Options();
        opts.proxy = true;
        this.client = MobacTileClient2.SPI.create(uri, cacheUri, opts);
        this.control = this.client.getControl(TileClientControl.class);
        this.maxZoom = mapSource.getMaxZoom();

        cacheControl = client.getControl(TileCacheControl.class);
        if(cacheControl != null) {
            this.registerControl(new TilesControlImpl(cacheControl));
        }


        for(Class<?> c : transferControls) {
            Object o = client.getControl(c);
            if (o != null)
                this.registerControl(o);
        }
        
        this.version = 0L;
    }
    
    /**************************************************************************/
    // Tile Reader
    
    @Override
    public void disposeImpl() {
        super.disposeImpl();

        this.client.dispose();
        this.client = null;
        this.readLock.notify();
    }

    @Override
    protected void cancel() {}

    @Override
    public long getTileVersion(int level, long tileColumn, long tileRow) {
        return this.version;
    }

    @Override
    protected Bitmap getTileImpl(int level, long tileColumn, long tileRow, ReadResult[] code) {
        return getTileImpl(level, tileColumn, tileRow, null, code);
    }

    @Override
    protected Bitmap getTileImpl(int level, long tileColumn, long tileRow, BitmapFactory.Options opts, ReadResult[] code) {
        if (level < 0)
            throw new IllegalArgumentException();

        // get the tile data
        final byte[] data = this.client.getTileData(this.maxZoom-level, (int)tileColumn, (int)tileRow, null);
        if(data == null) {
            code[0] = ReadResult.ERROR;
            return null;
        }
        // decode the bitmap using client specified options
        final Bitmap retval = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        code[0] = (retval!=null) ? ReadResult.SUCCESS : ReadResult.ERROR;
        return retval;

    }

    @Override
    public void start() {
        // before kicking off a bunch of tile readers, reset the connectivity
        // check in the map source to re-enable downloading if network
        // connectivity was restored
        this.client.checkConnectivity();

        // check the refresh interval
        if(this.control != null && this.refreshMonitor.check(this.control.getCacheAutoRefreshInterval(), SystemClock.uptimeMillis())) {
            // mark all tiles as expired
            if(this.cacheControl != null)
                cacheControl.expireTiles(System.currentTimeMillis());
            this.version++;
        }
    }

    /*************************************************************************/

    private static int getGridWidth(MobacMapSource mapSource) {
        if(mapSource.getSRID() == 4326)
            return 2;
        else
            return 1;
    }

    private static int getGridHeight(MobacMapSource mapSource) {
        return 1;
    }
    
    /*************************************************************************/

    private class TilesControlImpl implements TileCacheControl, TileCacheControl.OnTileUpdateListener {

        final TileCacheControl impl;
        TileCacheControl.OnTileUpdateListener listener;

        TilesControlImpl(TileCacheControl impl) {
            this.impl = impl;
            this.impl.setOnTileUpdateListener(this);
        }

        @Override
        public void onTileUpdated(int level, int x, int y) {
            final OnTileUpdateListener l = listener;
            if(l != null)
                l.onTileUpdated(maxZoom-level, x, y);

        }

        @Override
        public void prioritize(GeoPoint p) {
            impl.prioritize(p);
        }

        @Override
        public void abort(int level, int x, int y) {
            impl.abort(maxZoom-level, x, y);
        }

        @Override
        public boolean isQueued(int level, int x, int y) {
            return impl.isQueued(maxZoom-level, x, y);
        }

        @Override
        public void setOnTileUpdateListener(OnTileUpdateListener l) {
            listener = l;
        }

        @Override
        public void expireTiles(long expiry) {
            impl.expireTiles(expiry);
        }
    }
}
