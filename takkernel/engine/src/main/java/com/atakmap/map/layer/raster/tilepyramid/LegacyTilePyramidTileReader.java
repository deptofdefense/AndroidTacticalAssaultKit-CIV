package com.atakmap.map.layer.raster.tilepyramid;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Pair;

import com.atakmap.android.maps.graphics.GLBitmapLoader;
import com.atakmap.android.maps.tilesets.TilesetInfo;
import com.atakmap.android.maps.tilesets.TilesetSupport;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory.Options;
import com.atakmap.map.layer.raster.tilereader.TileReaderSpi;
import com.atakmap.util.ReferenceCount;

public class LegacyTilePyramidTileReader extends AbstractTilePyramidTileReader {

    public final static TileReaderSpi SPI = new TileReaderSpi() {
        @Override
        public String getName() {
            return "tileset";
        }

        @Override
        public TileReader create(String uri, Options options) {
            if(!uri.startsWith("tsinfo://"))
                return null;
            ReferenceCount<Pair<GLBitmapLoader, WeakReference<TilesetInfo>>> entry = register.get(uri.substring(9));
            if(entry == null)
                return null;
            final TilesetInfo tsInfo = entry.value.second.get();
            if(tsInfo == null)
                return null;

            final TilesetSupport support = TilesetSupport.create(tsInfo, entry.value.first);
            if(support == null)
                return null;

            TileReader.AsynchronousIO io = TileReader.getMasterIOThread();
            if(options != null && options.asyncIO != null)
                io = options.asyncIO;
            
            return new LegacyTilePyramidTileReader(tsInfo, support, io);
        }
        
        @Override
        public boolean isSupported(String uri) {
            return uri.startsWith("tsinfo://");
        }
    };

    private final static String TAG = "LegacyTilePyramidTileReader";
    
    private final static Map<String, ReferenceCount<Pair<GLBitmapLoader, WeakReference<TilesetInfo>>>> register = new HashMap<String, ReferenceCount<Pair<GLBitmapLoader, WeakReference<TilesetInfo>>>>();

    // for layer converters that spit out stuff based on 32bit floats...
    private static final double EPSILON = 0.000000001;
    
    private TilesetSupport support;
    
    private final int gridOffsetX;
    private final int gridOffsetY;
    private final int gridWidth;
    private final int gridHeight;

    private Future<Bitmap> pending;
 
    private static int checkAndThrow(int level) { 
        if (level < 0) 
             throw new IllegalArgumentException("TilesetInfo level count is 0");
        return level;
    }

    private LegacyTilePyramidTileReader(TilesetInfo info, TilesetSupport support, AsynchronousIO io) {
        super(info.getInfo().getUri(),
              null,
              io,
              info.getLevelCount(),
              info.getTilePixelWidth()*(getGridWidth(info)<<(checkAndThrow(info.getLevelCount()-1))),
              info.getTilePixelHeight()*(getGridHeight(info)<<(checkAndThrow(info.getLevelCount()-1))),
              info.getTilePixelWidth(),
              info.getTilePixelHeight());

        this.support = support;
        if(this.support == null)
            throw new NullPointerException("TilesetSupport cannot be null");

        this.support.init();
        this.support.start();
                
        final double zeroHeight = info.getZeroHeight();
        final double zeroWidth = info.getZeroWidth();

        final double gridOriginLat = info.getGridOriginLat();
        final double gridOriginLng = info.getGridOriginLng();

        Envelope mbb = info.getInfo().getCoverage(null).getEnvelope();

        double south = mbb.minY;
        double north = mbb.maxY;
        double west = mbb.minX;
        double east = mbb.maxX;

        // Shrink down on the 4 corners as much as we can
        GeoPoint sw = info.getInfo().getLowerLeft();
        GeoPoint nw = info.getInfo().getUpperLeft();
        GeoPoint ne = info.getInfo().getUpperRight();
        GeoPoint se = info.getInfo().getLowerRight();

        final double coverageSouth = Math.min(sw.getLatitude(), se.getLatitude());
        final double coverageNorth = Math.max(nw.getLatitude(), ne.getLatitude());
        final double coverageEast = Math.max(ne.getLongitude(), se.getLongitude());
        final double coverageWest = Math.min(nw.getLongitude(), sw.getLongitude());

        south = _alignMin(gridOriginLat, Math.max(coverageSouth, south), zeroHeight);
        west = _alignMin(gridOriginLng, Math.max(coverageWest, west), zeroWidth);
        north = _alignMax(gridOriginLat, Math.min(coverageNorth, north), zeroHeight);
        east = _alignMax(gridOriginLng, Math.min(coverageEast, east), zeroWidth);

        int _gridHeight = info.getGridHeight();
        if (_gridHeight < 0)
            _gridHeight = (int) (((north - south) + EPSILON) / zeroHeight);
        int _gridWidth = info.getGridWidth();
        if (_gridWidth < 0)
            _gridWidth = (int) (((east - west) + EPSILON) / zeroWidth);
        int _gridX = info.getGridOffsetX();
        if (_gridX < 0)
            _gridX = (int) ((west - gridOriginLng + EPSILON) / zeroWidth);
        int _gridY = info.getGridOffsetY();
        if (_gridY < 0)
            _gridY = (int) ((south - gridOriginLat + EPSILON) / zeroHeight);

        this.gridHeight = _gridHeight;
        this.gridWidth = _gridWidth;
        this.gridOffsetX = _gridX;
        this.gridOffsetY = _gridY;

        // commenting out this otherwise debug log information
    
        //System.out.println("tileset numlevels=" + this.numAvailableLevels);
        //System.out.println("tileset gridwidth[max]=" + this.getNumTilesX(0));
        //System.out.println("tileset gridheight[max]=" + this.getNumTilesY(0));
        //System.out.println("tileset gridwidth[min]=" + this.getNumTilesX(this.numAvailableLevels-1));
        //System.out.println("tileset gridheight[min]=" + this.getNumTilesY(this.numAvailableLevels-1));
        //System.out.println("tileset width=" + getWidth());
        //System.out.println("tileset height=" + getHeight());
        //System.out.println("tileset gridOffsetX=" + gridOffsetX);
        //System.out.println("tileset gridOffsetY=" + gridOffsetY);
        //System.out.println("tileset gridWidth=" + gridWidth);
        //System.out.println("tileset gridHeight=" + gridHeight);
        //System.out.println("tileset tileWidth=" + tileWidth);
        //System.out.println("tileset tileHeight=" + tileHeight);
     
    }
    
    private static int getGridWidth(TilesetInfo info) {
        int _gridWidth = info.getGridWidth();
        if(_gridWidth < 0) {
            final double zeroWidth = info.getZeroWidth();
    
            final double gridOriginLng = info.getGridOriginLng();
    
            Envelope mbb = info.getInfo().getCoverage(null).getEnvelope();
    
            double west = mbb.minX;
            double east = mbb.maxX;
    
            // Shrink down on the 4 corners as much as we can
            GeoPoint sw = new GeoPoint(mbb.minY, mbb.minX);
            GeoPoint nw = new GeoPoint(mbb.maxY, mbb.minX);
            GeoPoint ne = new GeoPoint(mbb.maxY, mbb.maxX);
            GeoPoint se = new GeoPoint(mbb.minY, mbb.maxX);
    
            final double coverageEast = Math.max(ne.getLongitude(), se.getLongitude());
            final double coverageWest = Math.min(nw.getLongitude(), sw.getLongitude());
    
            west = _alignMin(gridOriginLng, Math.max(coverageWest, west), zeroWidth);
            east = _alignMax(gridOriginLng, Math.min(coverageEast, east), zeroWidth);

            _gridWidth = (int) (((east - west) + EPSILON) / zeroWidth);
        }
        return _gridWidth;
    }

    private static int getGridHeight(TilesetInfo info) {
        int _gridHeight = info.getGridHeight();
        if(_gridHeight < 0) {
            final double zeroHeight = info.getZeroHeight();
    
            final double gridOriginLat = info.getGridOriginLat();
    
            Envelope mbb = info.getInfo().getCoverage(null).getEnvelope();
    
            double south = mbb.minY;
            double north = mbb.maxY;
    
            // Shrink down on the 4 corners as much as we can
            GeoPoint sw = new GeoPoint(mbb.minY, mbb.minX);
            GeoPoint nw = new GeoPoint(mbb.maxY, mbb.minX);
            GeoPoint ne = new GeoPoint(mbb.maxY, mbb.maxX);
            GeoPoint se = new GeoPoint(mbb.minY, mbb.maxX);
    
            final double coverageSouth = Math.min(sw.getLatitude(), se.getLatitude());
            final double coverageNorth = Math.max(nw.getLatitude(), ne.getLatitude());
    
            south = _alignMin(gridOriginLat, Math.max(coverageSouth, south), zeroHeight);
            north = _alignMax(gridOriginLat, Math.min(coverageNorth, north), zeroHeight);

            _gridHeight = (int) (((north - south) + EPSILON) / zeroHeight);

        }
        return _gridHeight;
    }

    @Override
    public void start() {
        this.support.start();
    }
    
    @Override
    public void stop() {
        this.support.stop();
    }
    
    /**************************************************************************/
    // Tile Reader
    
    @Override
    public void disposeImpl() {
        super.disposeImpl();
        if(this.support != null) {
            this.support.release();
            this.support = null;
        }
    }

    @Override
    protected synchronized void cancel() {
        if(this.pending != null)
            this.pending.cancel(true);
    }

    @Override
    protected Bitmap getTileImpl(int level, long tileColumn, long tileRow, ReadResult[] code) {
        return getTileImpl(level, tileColumn, tileRow, null, code);
    }

    @Override
    protected Bitmap getTileImpl(int level, long tileColumn, long tileRow, BitmapFactory.Options opts, ReadResult[] code) {
        if (level < 0)
            throw new IllegalArgumentException();

        // need to invert level and tile row for lower-left origin of legacy
        // tileset infrastructure
        final int tsLevel = (this.numAvailableLevels-level-1);
        tileRow = (this.gridHeight<<tsLevel)-tileRow-1;

        final int latIndex = (int)tileRow + (this.gridOffsetY<<tsLevel);
        final int lngIndex = (int)tileColumn + (this.gridOffsetX<<tsLevel);

        if(opts.inPreferredConfig == null)
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Future<Bitmap> bitmap = this.support.getTile(latIndex, lngIndex, tsLevel, opts);
        if(bitmap == null) {
            if(code != null) code[0] = ReadResult.ERROR;
            return null;
        }
        
        try {
            while(!bitmap.isDone())
                try {
                    this.wait(20);
                } catch(InterruptedException ignored) {}

            final Bitmap retval = bitmap.get();
            
            if(code != null) {
                if(bitmap.isCancelled())    code[0] = ReadResult.CANCELED;
                else if(retval == null)     code[0] = ReadResult.ERROR;
                else                        code[0] = ReadResult.SUCCESS;
            }
                
            return retval;
        } catch(CancellationException e) {
            if(code != null) code[0] = ReadResult.CANCELED;
            return null;
        } catch(ExecutionException e) {
            if(code != null) code[0] = ReadResult.ERROR;
            Log.w(TAG, "Tile [" + level + "," + tileColumn + "," + tileRow + "] failed to load", e);
            return null;
        } catch(InterruptedException e) {
            if(code != null) code[0] = ReadResult.CANCELED;
            return null;
        }
    }
    
    /**************************************************************************/
    
    private static double _alignMin(double o, double v, double a) {
        double n = (v - o) / a;
        return (Math.floor(n) * a) + o;
    }

    private static double _alignMax(double o, double v, double a) {
        double n = (v - o) / a;
        return (Math.ceil(n) * a) + o;
    }
    
    /**************************************************************************/
    
    private static String generateKey(GLBitmapLoader loader, TilesetInfo info) {
        // XXX - hash codes? static counter?
        return UUID.randomUUID().toString();
    }
    public synchronized static String registerTilesetInfo(GLBitmapLoader loader, TilesetInfo info) {
        final String retval = generateKey(loader, info);
        register.put(retval, new ReferenceCount<Pair<GLBitmapLoader, WeakReference<TilesetInfo>>>(Pair.create(loader, new WeakReference<TilesetInfo>(info))));
        return "tsinfo://" + retval;
    }
    
    public synchronized static void unregisterLayer(TilesetInfo info) {
        ReferenceCount<Pair<GLBitmapLoader, WeakReference<TilesetInfo>>> ref = null;
        
        Iterator<ReferenceCount<Pair<GLBitmapLoader, WeakReference<TilesetInfo>>>> iter;
        
        iter = register.values().iterator();
        TilesetInfo registerInfo;
        while(iter.hasNext()) {
            ref = iter.next();
            
            registerInfo = ref.value.second.get();
            
            // if the reference was reclaimed, remove
            if(registerInfo == null)
                iter.remove();
            if(registerInfo == info) {
                ref.dereference();
                if(!ref.isReferenced())
                    iter.remove();
                break;
            }
            
        }
    }
}
