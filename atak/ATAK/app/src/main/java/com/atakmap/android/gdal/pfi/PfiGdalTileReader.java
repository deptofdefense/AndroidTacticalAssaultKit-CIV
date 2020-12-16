
package com.atakmap.android.gdal.pfi;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.gdal.GdalTileReader;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;

import java.util.SortedMap;
import java.util.TreeMap;

/** @deprecated use available generic <code>TileReader</code> implementations */
@Deprecated
@DeprecatedApi(since = "4.1.1", forRemoval = true, removeAt = "4.4")
public class PfiGdalTileReader extends GdalTileReader {

    public static final String TAG = "PfiGdalTileReader";

    private final SortedMap<Integer, GdalTileReader> overviews;

    public PfiGdalTileReader(Dataset dataset, String uri, int tileWidth,
            int tileHeight, String cacheUri,
            GdalTileReader.AsynchronousIO asynchronousIO,
            DatasetDescriptor pfiInfo) {
        super(dataset, uri, tileWidth, tileHeight, cacheUri, asynchronousIO);

        this.overviews = new TreeMap<>();

        String overviewUri;
        int overviewNum = 0;
        Dataset overviewDataset;
        do {
            overviewUri = pfiInfo.getExtraData("pfiOverview"
                    + overviewNum++);
            if (overviewUri == null)
                break;
            overviewDataset = gdal.Open(overviewUri);
            if (overviewDataset == null)
                continue;
            final double scaleX = ((double) this.dataset.GetRasterXSize()
                    / (double) overviewDataset
                            .GetRasterXSize());
            final double scaleY = ((double) this.dataset.GetRasterYSize()
                    / (double) overviewDataset
                            .GetRasterYSize());

            final double scale = Math.max(scaleX, scaleY);
            final int level = (int) (Math.log(scale) / Math.log(2.0d));

            if (level > 0
                    && !this.overviews.containsKey(level))
                this.overviews.put(level, new GdalTileReader(
                        dataset, this.uri, tileWidth,
                        tileHeight, null, this.asynchronousIO));
            else
                overviewDataset.delete();
        } while (true);
    }

    protected GdalTileReader selectReader(int level, int[] readerBaseLevel) {
        if (this.overviews.size() < 1) {
            readerBaseLevel[0] = 0;
            return this;
        }

        SortedMap<Integer, GdalTileReader> head = this.overviews
                .headMap(level + 1);
        if (head.size() < 1) {
            readerBaseLevel[0] = 0;
            return this;
        }

        readerBaseLevel[0] = head.firstKey();
        return head.get(head.firstKey());
    }

    protected double getReaderSourceX(int level, long baseSrcX) {
        return this.getReaderSourceX(this.selectReader(level, null), baseSrcX);
    }

    protected double getReaderSourceX(GdalTileReader reader, long baseSrcX) {
        double x = ((double) baseSrcX / (double) this.getWidth());
        x *= reader.getWidth();
        if (x <= 0)
            x = 0;
        else if (x > reader.getWidth())
            x = reader.getWidth();
        return x;
    }

    protected double getReaderSourceY(int level, long baseSrcY) {
        return this.getReaderSourceY(this.selectReader(level, null), baseSrcY);
    }

    protected double getReaderSourceY(GdalTileReader reader, long baseSrcY) {
        double y = ((double) baseSrcY / (double) this.getHeight());
        y *= reader.getHeight();
        if (y <= 0)
            y = 0;
        else if (y > reader.getHeight())
            y = reader.getHeight();
        return y;
    }

    @Override
    public long getWidth(int level) {
        int[] baseLevel = new int[1];
        final GdalTileReader reader = this.selectReader(level, baseLevel);
        if (reader == this)
            return super.getWidth(level - baseLevel[0]);
        else
            return reader.getWidth(level - baseLevel[0]);
    }

    @Override
    public long getHeight(int level) {
        int[] baseLevel = new int[1];
        final GdalTileReader reader = this.selectReader(level, baseLevel);
        if (reader == this)
            return super.getHeight(level - baseLevel[0]);
        else
            return reader.getHeight(level - baseLevel[0]);
    }

    @Override
    public ReadResult read(long srcX, long srcY, long srcW, long srcH,
            int dstW,
            int dstH, byte[] data) {
        final double scaleX = ((double) srcW / (double) dstW);
        final double scaleY = ((double) srcH / (double) dstH);

        final double scale = Math.max(scaleX, scaleY);
        final int level = (int) (Math.log(scale) / Math.log(2.0d));

        int[] baseLevel = new int[1];
        final GdalTileReader reader = this.selectReader(level, baseLevel);
        Log.d(TAG, "PfiGdalTileReader read baseLevel=" + baseLevel[0]
                + " (readerLevels="
                + this.overviews.keySet() + ")");
        if (reader == this)
            return super.read(srcX, srcY, srcW, srcH, dstW, dstH, data);

        final long readerMinSrcX = (long) this.getReaderSourceX(reader, srcX);
        final long readerMinSrcY = (long) this.getReaderSourceY(reader, srcY);
        final long readerMaxSrcX = (long) Math.ceil(this.getReaderSourceX(
                reader, srcX + srcW));
        final long readerMaxSrcY = (long) Math.ceil(this.getReaderSourceY(
                reader, srcY + srcH));

        return reader.read(readerMinSrcX, readerMinSrcY,
                (readerMaxSrcX - readerMinSrcX),
                (readerMaxSrcY - readerMinSrcY), dstW, dstH, data);
    }

    @Override
    protected void disposeImpl() {
        super.disposeImpl();

        for (GdalTileReader gdalTileReader : this.overviews.values())
            gdalTileReader.dispose();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            for (GdalTileReader gdalTileReader : this.overviews.values())
                gdalTileReader.getDataset().delete();
            this.overviews.clear();
        } finally {
            super.finalize();
        }
    }
}
