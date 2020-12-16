package com.atakmap.map.formats.srtm;

import android.util.LruCache;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.map.elevation.ElevationChunk;
import com.atakmap.map.elevation.ElevationData;
import com.atakmap.map.elevation.ElevationSource;
import com.atakmap.map.elevation.ElevationSourceManager;
import com.atakmap.map.elevation.TiledElevationSource;
import com.atakmap.map.gdal.GdalElevationChunk;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.raster.tilematrix.TileMatrix;

import org.gdal.gdal.gdal;
import org.gdal.gdal.Dataset;

/**
 * SRTM service implementation based on the <I>Elevation Source</I> API.
 *
 * <P>SRTM directories are expected to be laid out in the following manner:
 * <pre>
 *     // SRTM 1/3
 *     &lt;root&gt;/N89W179.hgt
 *     &lt;root&gt;/N89W178.hgt
 *     ...
 *     &lt;root&gt;/N34W081.hgt
 *     &lt;root&gt;/N34W080.hgt
 *     &lt;root&gt;/N34W079.hgt
 *     ...
 *     &lt;root&gt;/S13E078.hgt
 *     &lt;root&gt;/S13E079.hgt
 *     &lt;root&gt;/S13E080.hgt
 *     ...
 *     &lt;root&gt;/S90E180.hgt
 *     // SRTM30
 *     &lt;root&gt;/W180N90.DEM
 *     &lt;root&gt;/W180N90.DMW
 *     &lt;root&gt;/W180N90.HDR
 *     &lt;root&gt;/W180N90.PRJ
 *     &lt;root&gt;/W140N90.DEM
 *     &lt;root&gt;/W140N90.DMW
 *     &lt;root&gt;/W140N90.HDR
 *     &lt;root&gt;/W140N90.PRJ
 *     ...
 *     &lt;root&gt;/W100N40.DEM
 *     &lt;root&gt;/W100N40.DMW
 *     &lt;root&gt;/W100N40.HDR
 *     &lt;root&gt;/W100N40.PRJ
 *     ...
 *     &lt;root&gt;/E140S10.DEM
 *     &lt;root&gt;/E140S10.DMW
 *     &lt;root&gt;/E140S10.HDR
 *     &lt;root&gt;/E140S10.PRJ
 * </pre>
 * <P>Notes:
 * <UL>
 *     <LI>SRTM 1/3 cells may be zipped; if zipped, must be the base file name plus ".zip", e.g. "N34W081.hgt.zip"</LI>
 *     <LI>Only one of an SRTM1 or SRTM3 cell may exist for a given latitude/longitude as the filenames are identical</LI>
 *     <LI>SRTM30 requires the .DEM, .HDR, .PRJ and .DMW files for each cell</LI>
 *     <LI>SRTM30 cells may not be zipped</LI>
 * </UL>
 */
public final class SrtmElevationSource {

    private final static TileMatrix.ZoomLevel[] SRTM_TILE_MATRIX = new TileMatrix.ZoomLevel[2];
    static {
        SRTM_TILE_MATRIX[0] = oneDegZoomLevel(90d, 0); // SRTM3
        SRTM_TILE_MATRIX[1] = oneDegZoomLevel(30d, 1); // SRTM1
    }

    private final static TileMatrix.ZoomLevel[] SRTM30_TILE_MATRIX = new TileMatrix.ZoomLevel[1];
    static {
        SRTM30_TILE_MATRIX[0] = new TileMatrix.ZoomLevel();
        SRTM30_TILE_MATRIX[0].level = 0;
        SRTM30_TILE_MATRIX[0].resolution = 900d;
        SRTM30_TILE_MATRIX[0].pixelSizeX = 1d; // 1deg
        SRTM30_TILE_MATRIX[0].pixelSizeY = 1d; // 1deg
        SRTM30_TILE_MATRIX[0].tileWidth = 40; // spans 40deg long
        SRTM30_TILE_MATRIX[0].tileHeight = 50; // spans 50deg lat
    }

    private final static Map<File, Collection<ElevationSource>> mounts = new HashMap<>();

    private SrtmElevationSource() {}

    /**
     * Mounts the specified SRTM directory, making available through the
     * <code>ElevationSourceManager</code> subsystem. This method returns
     * immediately if the specified directory is currently mounted.
     *
     * @param directory The SRTM directory
     */
    public static void mountDirectory(File directory) {
        synchronized(mounts) {
            if(mounts.containsKey(directory))
                return;

            final Collection<ElevationSource> srtm = new ArrayList<>(2);
            // SRTM 1/3
            srtm.add(new TileSource.SRTM(directory));
            // SRTM30
            srtm.add(new TileSource.SRTM30(directory));
            mounts.put(directory, srtm);

            for(ElevationSource source : srtm)
                ElevationSourceManager.attach(source);
        }
    }

    /**
     * Unmounts the specified SRTM directory, removing terrain data made
     * available through the <code>ElevationSourceManager</code> subsystem
     * via a previous call to {@link #mountDirectory(File)}. This method
     * returns immediately if the specified directory is not currently
     * mounted.
     *
     * @param directory The SRTM directory
     */
    public static void unmountDirectory(File directory) {
        synchronized(mounts) {
            final Collection<ElevationSource> srtm = mounts.remove(directory);
            if(srtm == null)
                return;

            for(ElevationSource source : srtm)
                ElevationSourceManager.detach(source);
        }
    }

    static TileMatrix.ZoomLevel oneDegZoomLevel(double gsd, int level) {
        final TileMatrix.ZoomLevel retval = new TileMatrix.ZoomLevel();
        retval.level = level;
        retval.resolution = gsd;
        retval.pixelSizeX = 1d;
        retval.pixelSizeY = 1d;
        retval.tileWidth = 1;
        retval.tileHeight = 1;
        return retval;
    }

    static abstract class TileSource extends TiledElevationSource {
        final LruCache<String, ElevationChunk> cache;
        final File dir;
        final boolean srtm30;
        final boolean isZipSupported;

        TileSource(File dir, Envelope bounds, TileMatrix.ZoomLevel[] matrix, boolean srtm30, boolean isZipSupported) {
            super("SRTM", 4326, bounds, -180d, 90d, TileScheme.Custom, matrix, true);
            this.cache = new LruCache<String, ElevationChunk>(32) {
                @Override
                protected void entryRemoved(boolean evicted, String key, ElevationChunk oldValue, ElevationChunk newValue) {
                    oldValue.dispose();
                }
            };
            this.dir = dir;
            this.srtm30 = srtm30;
            this.isZipSupported = isZipSupported;
        }

        @Override
        public Cursor query(QueryParameters params) {
            return new SrtmCursorImpl(params);
        }

        @Override
        public ElevationChunk getTerrainTile(int zoom, int x, int y) {
            final String key = getFileName(zoom, x, y);
            // requested tile is out of bounds, return null
            if(key == null)
                return null;
            synchronized(this.cache) {
                ElevationChunk cached = this.cache.get(key);
                // if there's an entry in the cache, create a new reference to
                // the shared data
                if(cached != null)
                    return ElevationChunk.Factory.makeShared(cached);
            }

            ElevationChunk chunk;
            do {
                // check for the regular file
                chunk = createSrtm(new File(dir, key), true);
                if(chunk != null)
                    break;

                // lower-case
                chunk = createSrtm(new File(dir, key.toLowerCase()), srtm30);
                if(chunk != null)
                    break;

                // if zip is not supported, skip checks
                if(!this.isZipSupported)
                    break;

                // check for the zipped file
                chunk = createSrtm(new File(dir, key + ".zip"), srtm30);
                if (chunk != null)
                    break;

                // lower-case
                chunk = createSrtm(new File(dir, key.toLowerCase() + ".zip"), srtm30);
                if (chunk != null)
                    break;
            } while(false);

            if(chunk == null)
                return null;

            // cache the chunk, if we had a race and two threads have managed
            // to create, the update will cause a dereference on the shared
            // entry created the 'other' thread
            synchronized(this.cache) {
                chunk = ElevationChunk.Factory.makeShared(chunk);

                // it's important to put a new shared reference in the cache
                this.cache.put(key, ElevationChunk.Factory.makeShared(chunk));
            }

            return chunk;
        }

        @Override
        public void addOnContentChangedListener(OnContentChangedListener l) {} // no-op

        @Override
        public void removeOnContentChangedListener(OnContentChangedListener l) {} // no-op

        abstract String getFileName(int tileZoom, int tileX, int tileY);

        private class SrtmCursorImpl extends CursorImpl {

            SrtmCursorImpl(QueryParameters params) {
                super(params);
            }

            @Override
            public boolean moveToNext() {
                // Make sure the STRM directory exists before continuing
                return IOProviderFactory.exists(dir) && super.moveToNext();
            }
        }

        final static class SRTM extends TileSource {
            SRTM(File directory) {
                super(directory, new Envelope(-180d, -90d, 0d, 180d, 90d, 0d), SRTM_TILE_MATRIX, false, true);
            }

            String getFileName(int zoom, int x, int y) {
                if(zoom < 0 || zoom > 1)
                    return null;
                if(x < 0 || x > 359)
                    return null;
                if(y < 0 || y > 179)
                    return null;

                x -= 180;
                y = 89 - y;

                StringBuilder p = new StringBuilder();

                if (y >= 0) {
                    p.append("N");
                } else {
                    p.append("S");
                    y = -y;
                }

                if (y < 10)
                    p.append("0");

                p.append(y);

                if (x >= 0) {
                    p.append("E");
                } else {
                    p.append("W");
                    x = -x;
                }
                if (x < 10)
                    p.append("00");
                else if (x < 100)
                    p.append("0");
                p.append(x);

                p.append(".hgt");

                return p.toString();
            }
        }

        final static class SRTM30 extends TileSource {
            SRTM30(File directory) {
                super(directory, new Envelope(-180d, -60d, 0d, 180d, 90d, 0d), SRTM30_TILE_MATRIX, true, false);
            }

            String getFileName(int zoom, int x, int y) {
                if(zoom != 0)
                    return null;
                if(x < 0 || x > 8)
                    return null;
                if(y < 0 || y > 2)
                    return null;

                x *= 40;
                x -= 180;

                y *= 50;
                y = 90 - y;

                StringBuilder p = new StringBuilder();

                if (x >= 0) {
                    p.append("E");
                } else {
                    p.append("W");
                    x = -x;
                }
                if (x < 10)
                    p.append("00");
                else if (x < 100)
                    p.append("0");
                p.append(x);

                if (y >= 0) {
                    p.append("N");
                } else {
                    p.append("S");
                    y = -y;
                }

                if (y < 10)
                    p.append("0");

                p.append(y);

                p.append(".DEM");

                return p.toString();
            }
        }
    }

    private static ElevationChunk createSrtm(File f, boolean srtm30) {
        if(!IOProviderFactory.exists(f))
            return null;
        Dataset dataset = GdalLibrary.openDatasetFromFile(f);
        if(dataset == null)
            return null;
        // XXX -
        double resolution = Double.NaN;
        String type = "SRTM";
        if(srtm30) {
            type = "SRTM30";
            resolution = 900d;
        } else if (dataset.GetRasterXSize() == 3601) { // SRTM1
            type = "SRTM1";
            resolution = 30d;
        } else if (dataset.GetRasterXSize() == 1201) { // SRTM3
            type = "SRTM3";
            resolution = 90d;
        }

        return GdalElevationChunk.create(dataset, true, type, resolution, ElevationData.MODEL_TERRAIN);
    }
}
