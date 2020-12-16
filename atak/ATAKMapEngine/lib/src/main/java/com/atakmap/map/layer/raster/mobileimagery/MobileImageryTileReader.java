package com.atakmap.map.layer.raster.mobileimagery;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.net.Uri;

import com.atakmap.android.maps.tilesets.TilesetInfo;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.Databases;
import com.atakmap.lang.Objects;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.gdal.GdalGraphicUtils;
import com.atakmap.map.layer.raster.mobac.MobacTileReader;
import com.atakmap.map.layer.raster.osm.OSMDroidTileReader;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderSpi;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory.Options;
import com.atakmap.math.MathUtils;

final class MobileImageryTileReader extends TileReader {

    public final static TileReaderSpi SPI = new TileReaderSpi() {
        @Override
        public String getName() {
            return "mobile-imagery";
        }

        @Override
        public TileReader create(String u, Options options) {
            if(options == null) {
                options = new Options();
                options.asyncIO = TileReader.getMasterIOThread();
            }

            try {
                Uri uri = Uri.parse(u);
                if(uri == null)
                    return null;
                if(!Objects.equals(uri.getScheme(), "mobileimagery"))
                    return null;
                final String key = uri.getFragment();
                if(key != null) {
                    synchronized(MobileImageryTilesetSupport.descriptorCache) {
                        Collection<TilesetInfo> descs = MobileImageryTilesetSupport.descriptorCache.get(key);
                        if(descs == null || descs.isEmpty()) {
                            Log.e(TAG, "Failed to find descriptor for " + key + " [" + descs + "]");
                            return null;
                        }

                        Set<TileReader> readers = new HashSet<TileReader>();
                        int maxLevel = 0;
                        
                        // discover max level -- either max of maxes OR max on online descriptor
                        for(TilesetInfo tsInfo : descs) {
                            boolean online = tsInfo.getInfo().isRemote();

                            int minLvl = Integer.parseInt(DatasetDescriptor.getExtraData(tsInfo.getInfo(), "levelOffset",
                                    "0"));
                            int maxLvl = (minLvl + tsInfo.getLevelCount() - 1);
                            if(maxLvl > maxLevel || online)
                                maxLevel = maxLvl;
                            if(online)
                                break;
                        }

                        // XXX - 
                        boolean is2x1Grid = false;

                        // populate readers
                        for(TilesetInfo tsInfo : descs) {
                            TileReader reader = null;
                            final String type = tsInfo.getInfo().getExtraData("mobileimagery.type");
                            if(type == null)
                                throw new IllegalArgumentException();

                            is2x1Grid |= (tsInfo.getInfo().getSpatialReferenceID()==4326);

                            if(type.equals("mobac")) {
                                Options onlineOpts = new Options(options);
                                onlineOpts.cacheUri = tsInfo.getInfo().getExtraData("offlineCache");

                                reader = MobacTileReader.SPI.create(tsInfo.getInfo().getUri(), onlineOpts);
                            } else if(type.equals("osmdroid") || type.equals("osmdroid.atak")) {
                                reader = new OSMDroidTileReader(tsInfo.getInfo().getUri(),
                                        options.asyncIO,
                                        IOProviderFactory.createDatabase(new File(tsInfo.getInfo().getUri()), DatabaseInformation.OPTION_READONLY),
                                        0,
                                        maxLevel+1,
                                        0,
                                        0,
                                        is2x1Grid ? 1 : 0, // 2x1 grid for WGS84
                                        0,
                                        256,
                                        false);
                            } else {
                                throw new IllegalArgumentException();
                            }
                            
                            readers.add(reader);
                        }
                        
                        if(readers.isEmpty()) {
                            Log.e(TAG, "no readers");
                            return null;
                        }

                        return new MobileImageryTileReader(u,
                                                           options.asyncIO,
                                                           readers,
                                                           maxLevel,
                                                           is2x1Grid ? 2 : 1,
                                                           1,
                                                           256);
                    }
                }
            } catch(Exception ignored) {}

            return null;
        }
        
        @Override
        public boolean isSupported(String uri) {
            return uri.startsWith("mobileimagery");
        }
    };

    private Set<TileReader> readers;
    private final int maxLevel;
    private final long width;
    private final long height;
    private final int tileSize;
    private Format format;
    private Interleave interleave;

    private MobileImageryTileReader(String uri,
                                    AsynchronousIO asyncIO,
                                    Set<TileReader> readers,
                                    int maxLevel,
                                    int gridZeroWidth,
                                    int gridZeroHeight,
                                    int tileSize) {

        super(uri, null, Integer.MAX_VALUE, asyncIO);
        
        this.readers = readers;
        this.maxLevel = maxLevel;
        this.tileSize = tileSize;
        this.width = ((long)gridZeroWidth<<(long)this.maxLevel)*(long)this.tileSize;
        this.height = ((long)gridZeroHeight<<(long)this.maxLevel)*(long)this.tileSize;
        
        this.format = null;
        this.interleave = null;
        for(TileReader reader : this.readers) {
            if(this.format == null)
                this.format = reader.getFormat();
            else if(this.format != reader.getFormat())
                throw new IllegalArgumentException();
            if(this.interleave == null)
                this.interleave = reader.getInterleave();
            else if(this.interleave != reader.getInterleave())
                throw new IllegalArgumentException();
            
            Collection<Object> controls = new HashSet<Object>();
            reader.getControls(controls);
            for(Object c : controls)
                this.registerControl(c);
        }
    }

    @Override
    public final boolean isMultiResolution() {
        return true;
    }

    @Override
    public final long getWidth() {
        return this.width;
    }

    @Override
    public final long getHeight() {
        return this.height;
    }

    @Override
    public final int getTileWidth() {
        return this.tileSize;
    }

    @Override
    public final int getTileHeight() {
        return this.tileSize;
    }

    @Override
    public final ReadResult read(int level, long tileColumn, long tileRow, byte[] data) {
        if (level < 0)
            throw new IllegalArgumentException();

        ArrayList<TileReader> candidates = new ArrayList<TileReader>();

        // filter readers that do not contain tile
        for(TileReader reader : this.readers) {
            // XXX - filtering
            candidates.add(reader);
        }
        
        // sort on tile version
        Collections.sort(candidates, new TileVersionComparator(level, (int)tileColumn, (int)tileRow));

        // iterate until successful read or canceled
        ReadResult retval = ReadResult.ERROR;
        for(TileReader reader : candidates) {
            retval = reader.read(level, tileColumn, tileRow, data);
            if(retval != ReadResult.ERROR)
                break;
        }
        return retval;
    }

    @Override
    public final ReadResult read(long srcX, long srcY, long srcW, long srcH, int dstW,
            int dstH, byte[] data) {
        
        
        final int level = MathUtils.clamp((int)Math.ceil(Math.log(Math.max((double)srcW / (double)dstW, (double)srcH / (double)dstH)) / Math.log(2d)), 0, this.maxLevel);

        final double subsampleX = (double)dstW / ((double)srcW/(double)(1<<level));
        final double subsampleY = (double)dstH / ((double)srcH/(double)(1<<level));

        final long stx = MathUtils.clamp(this.getTileColumn(level, srcX), 0, this.getNumTilesX(level)-1);
        final long sty = MathUtils.clamp(this.getTileRow(level, srcY), 0, this.getNumTilesY(level)-1);
        final long ftx = MathUtils.clamp(this.getTileColumn(level, srcX+srcW-1), 0, this.getNumTilesX(level)-1);
        final long fty = MathUtils.clamp(this.getTileRow(level, srcY+srcH-1), 0, this.getNumTilesY(level)-1);

        // if we are reading exactly one tile, skip the compositing
        if( Double.compare(subsampleX, 1d) == 0 && Double.compare(subsampleY,1d) == 0 &&
           stx == ftx && sty == fty &&
           dstW == this.tileSize && dstH == this.tileSize) {

            return this.read(level, stx, sty, data);
        }

        ReadResult result = ReadResult.SUCCESS;

        Bitmap retval = null;
        int[] tileArgb;
        try {
            retval = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888);
                        
            Canvas g = new Canvas(retval);
            Bitmap tile;
            RectF dst = new RectF();
            byte[] tileData = null;
            int tileWidth;
            int tileHeight;
            int tileDataLength;
            ReadResult tileResult;
outer:      for(long ty = sty; ty <= fty; ty++) {
                for(long tx = stx; tx <= ftx; tx++) {
                    tile = null;
                    try {
                        tileWidth = this.getTileWidth(level, tx);
                        tileHeight = this.getTileHeight(level, ty);
                        tileDataLength = this.getPixelSize()*tileWidth*tileHeight;
                        if(tileData == null || (tileData.length < tileDataLength))
                            tileData = new byte[tileDataLength];
                        tileResult = this.read(level, tx, ty, tileData);
                        if(tileResult == ReadResult.CANCELED) {
                            result = tileResult;
                            break outer;
                        } else if(tileResult != ReadResult.SUCCESS) {
                            continue;
                        }

                        tile = GdalGraphicUtils.createBitmap(tileData,
                                                             tileWidth,
                                                             tileHeight,
                                                             this.interleave,
                                                             this.format);

                        dst.top = (int)((double)((this.getTileSourceY(level, ty)-srcY)>>level) * subsampleY);
                        dst.left = (int)((double)((this.getTileSourceX(level, tx)-srcX)>>level) * subsampleX);
                        dst.bottom = (int)Math.ceil((double)((this.getTileSourceY(level, ty+1)-srcY)>>level) * subsampleY);
                        dst.right = (int)Math.ceil((double)((this.getTileSourceX(level, tx+1)-srcX)>>level) * subsampleX);

                        g.drawBitmap(tile,
                                     null,
                                     dst,
                                     null);
                    } finally {
                        if(tile != null)
                            tile.recycle();
                    }
                }
            }

            tileArgb = new int[dstW*dstH];
            retval.getPixels(tileArgb, 0, dstW, 0, 0, dstW, dstH);
        } finally {
            if(retval != null)
                retval.recycle();
        }

        if(result == ReadResult.SUCCESS) {
            int argb;
            int idx = 0;
            for(int i = 0; i < tileArgb.length; i++) {
                argb = tileArgb[i];
                data[idx++] = (byte)((argb>>24)&0xFF);
                data[idx++] = (byte)((argb>>16)&0xFF);
                data[idx++] = (byte)((argb>>8)&0xFF);
                data[idx++] = (byte)(argb&0xFF);
            }
        }

        return result;
    }

    @Override
    public final Format getFormat() {
        return this.format;
    }

    @Override
    public final Interleave getInterleave() {
        return this.interleave;
    }

    @Override
    public long getTileVersion(int level, long tileCol, long tileRow) {
        long retval = 0L;
        for(TileReader reader : this.readers)
            retval += reader.getTileVersion(level, tileCol, tileRow);
        return retval;
    }
    
    @Override
    protected void cancel() {
        super.cancel();
        for(TileReader reader : this.readers)
            this.cancelChild(reader);
    }

    @Override
    protected void disposeImpl() {
        super.disposeImpl();
        
        // XXX - not great but too much complexity to unwind right now. we kick
        //       into a background thread to avoid asyncio lock reacquisition

        Thread t = new Thread() {
            @Override
            public void run() {
                for(TileReader reader : readers)
                    reader.dispose();
            }
        };
        t.setPriority(Thread.MIN_PRIORITY);
        t.setName("MobileImageryTileReader-child-disposer");
        t.start();
    }



    private static class TileVersionComparator implements Comparator<TileReader> {
        private final int level;
        private final int tileColumn;
        private final int tileRow;
        
        TileVersionComparator(int level, int tileColumn, int tileRow) {
            this.level = level;
            this.tileColumn = tileColumn;
            this.tileRow = tileRow;
        }

        @Override
        public int compare(TileReader lhs, TileReader rhs) {
            final long version0 = lhs.getTileVersion(level, tileColumn, tileRow);
            final long version1 = rhs.getTileVersion(level, tileColumn, tileRow);
            if(version0 > version1)
                return -1;
            else if(version0 < version1)
                return 1;
            else
                return lhs.hashCode()-rhs.hashCode();
        }
    }
}
