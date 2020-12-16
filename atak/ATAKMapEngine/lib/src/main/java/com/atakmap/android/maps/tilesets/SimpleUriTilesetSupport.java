
package com.atakmap.android.maps.tilesets;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.concurrent.FutureTask;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.map.layer.raster.osm.OSMTilesetSupport;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.android.maps.graphics.GLBitmapLoader;
import com.atakmap.database.Databases;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.projection.WebMercatorProjection;
import com.atakmap.coremap.locale.LocaleUtil;

public class SimpleUriTilesetSupport {
    private final static DecimalFormat NASAWW_INDEX_FORMAT = LocaleUtil.getDecimalFormat("0000");

    public final static TilesetResolver NASAWW_RESOLVER = new TilesetResolver() {
        @Override
        public String resolve(TilesetInfo tsInfo, int latIndex, int lngIndex, int level) {
            final DatasetDescriptor info = tsInfo.getInfo();

            String latStr = NASAWW_INDEX_FORMAT.format(latIndex);
            String lngStr = NASAWW_INDEX_FORMAT.format(lngIndex);
            String imageExt = tsInfo.getImageExt();

            StringBuilder sb = new StringBuilder(info.getUri());
            if (tsInfo.isArchive()) {
                sb.insert(0, "arc:");
                sb.append("!");
            }

            sb.append("/");
            sb.append(level);
            sb.append("/");
            sb.append(latStr);
            sb.append("/");
            sb.append(latStr);
            sb.append("_");
            sb.append(lngStr);
            sb.append(imageExt);

            return sb.toString();
        }
    };

    public final static TilesetResolver NASAWW_LARGE_RESOLVER = new TilesetResolver() {
        @Override
        public String resolve(TilesetInfo info, int latIndex, int lngIndex,
                int level) {

            int zeroLatIndex = latIndex, zeroLngIndex = lngIndex;
            int baseLevel = level;
            String archiveName = null;

            if (level != 0) {
                // resolve the lat and lon index to the first level to determine
                // what archive file to use

                while (baseLevel > 0) {
                    zeroLatIndex = zeroLatIndex / 2;
                    zeroLngIndex = zeroLngIndex / 2;
                    baseLevel--;
                }
            }

            // XXX -
            archiveName = "";// info._zeroDirectoryLookup.get(zeroLat);

            File parentDirectory = new File(info.getInfo().getUri()).getParentFile();
            File resolvedArchive = new File(parentDirectory, archiveName);
            StringBuilder sb = new StringBuilder(resolvedArchive.toString());
            if (info.isArchive()) {
                sb.insert(0, "arc:");
                sb.append("!");
            }

            sb.append("/");
            sb.append(level);
            sb.append("/");
            sb.append(NASAWW_INDEX_FORMAT.format(latIndex));
            sb.append("/");
            sb.append(NASAWW_INDEX_FORMAT.format(latIndex));
            sb.append("_");
            sb.append(NASAWW_INDEX_FORMAT.format(lngIndex));
            sb.append(info.getImageExt());

            return sb.toString();
        }
    };

    public final static TilesetResolver TMS_RESOLVER = new TilesetResolver() {
        @Override
        public String resolve(TilesetInfo tsInfo, int latIndex, int lngIndex, int level) {
            final DatasetDescriptor info = tsInfo.getInfo();
            String imageExt = tsInfo.getImageExt();

            int tmsLatIndex = (1 << level) - latIndex - 1;

            StringBuilder sb = new StringBuilder(info.getUri());
            if (tsInfo.isArchive()) {
                sb.insert(0, "arc:");
                sb.append("!");
            }

            sb.append("/");
            sb.append(level);
            sb.append("/");
            sb.append(lngIndex);
            sb.append("/");
            sb.append(tmsLatIndex);
            sb.append(imageExt);

            return sb.toString();
        }
    };

    public final static TilesetResolver OSMDROID_ZIP_RESOLVER = new TilesetResolver() {
        @Override
        public String resolve(TilesetInfo info, int latIndex, int lngIndex, int level) {
            final int osmLevel = level
                    + Integer.parseInt(DatasetDescriptor.getExtraData(info.getInfo(), "levelOffset", "0"));
            final int osmLatIndex = (1 << osmLevel) - latIndex - 1;

            String imageExt = info.getImageExt();

            StringBuilder sb = new StringBuilder(info.getInfo().getUri());
            if (info.isArchive()) {
                sb.insert(0, "arc:");
                sb.append("!");
            }
            sb.append("/");

            sb.append(DatasetDescriptor.getExtraData(info.getInfo(), "subpath", ""));

            sb.append(osmLevel);
            sb.append("/");
            sb.append(lngIndex);
            sb.append("/");
            sb.append(osmLatIndex);
            sb.append(imageExt);

            return sb.toString();
        }
    };

    public final static TilesetResolver OSMDROID_SQLITE_RESOLVER = new TilesetResolver() {
        @Override
        public String resolve(TilesetInfo info, int latIndex, int lngIndex, int level) {
            final int osmLevel = level
                    + Integer.parseInt(DatasetDescriptor.getExtraData(info.getInfo(), "levelOffset", "0"));
            final int osmLatIndex = (1 << osmLevel) - latIndex - 1;

            try {
                return "sqlite://"
                        + info.getInfo().getUri()
                        + "?query="
                        + URLEncoder.encode(
                                "SELECT tile FROM tiles WHERE key = "
                                        + OSMUtils.getOSMDroidSQLiteIndex(osmLevel, lngIndex,
                                                osmLatIndex), FileSystemUtils.UTF8_CHARSET.name());
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
    };

    public final static TilesetResolver NETT_WARRIOR_SQLITE_RESOLVER = new TilesetResolver() {
        @Override
        public String resolve(TilesetInfo info, int latIndex, int lngIndex, int level) {
            final int osmLevel = level
                    + Integer.parseInt(DatasetDescriptor.getExtraData(info.getInfo(), "levelOffset", "0"));

            try {
                return "sqlite://"
                        + info.getInfo().getUri()
                        + "?query="
                        + URLEncoder.encode(
                                "SELECT tile_data FROM tiles WHERE zoom_level = "
                                        + String.valueOf(osmLevel) + " AND tile_column = "
                                        + String.valueOf(lngIndex) + " AND tile_row = "
                                        + String.valueOf(latIndex), FileSystemUtils.UTF8_CHARSET.name());
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
    };

    /**************************************************************************/

    private SimpleUriTilesetSupport() {
    }

    private static FutureTask<Bitmap> getTileImpl(GLBitmapLoader bitmapLoader,
            TilesetInfo tsInfo, TilesetResolver resolver, int latIndex, int lngIndex, int level,
            BitmapFactory.Options opts) {
        return bitmapLoader.loadBitmap(resolver.resolve(tsInfo, latIndex, lngIndex, level), opts);
    }

    private static void initImpl(GLBitmapLoader loader, TilesetInfo tsInfo) {
        final String uri = tsInfo.getInfo().getUri();
        if (TilesetInfo.isZipArchive(uri))
            GLBitmapLoader.mountArchive(uri);
        else if (IOProviderFactory.isDatabase(new File(uri)))
            GLBitmapLoader.mountDatabase(uri);
    }

    private static void releaseImpl(GLBitmapLoader loader, TilesetInfo tsInfo) {
        final String uri = tsInfo.getInfo().getUri();
        if (TilesetInfo.isZipArchive(uri))
            GLBitmapLoader.unmountArchive(uri);
        else if (IOProviderFactory.isDatabase(new File(uri)))
            GLBitmapLoader.unmountDatabase(uri);
    }

    /**************************************************************************/

    public final static class Spi implements TilesetSupport.Spi {
        public final static TilesetSupport.Spi INSTANCE = new Spi();

        @Override
        public String getName() {
            return "simple";
        }

        @Override
        public final TilesetSupport create(TilesetInfo tsInfo, GLBitmapLoader bitmapLoader) {
            final String _pathStructure = DatasetDescriptor.getExtraData(tsInfo.getInfo(),
                    "_pathStructure", "NASAWW");
            if (_pathStructure.equals("NASAWW")) {
                return new EquirectangularTilesetFilter(tsInfo, bitmapLoader, NASAWW_RESOLVER);
            } else if (_pathStructure.equals("TMS")) {
                return new EquirectangularTilesetFilter(tsInfo, bitmapLoader, TMS_RESOLVER);
            } else if (_pathStructure.equals("NASAWW_LARGE")) {
                return new EquirectangularTilesetFilter(tsInfo, bitmapLoader, NASAWW_LARGE_RESOLVER);
            } else if (_pathStructure.equals("OSM_DROID")) {
                return new OSMDroidFilter(tsInfo, bitmapLoader, OSMDROID_ZIP_RESOLVER);
            } else if (_pathStructure.equals("OSM_DROID_SQLITE")
                    && tsInfo.getInfo().getSpatialReferenceID() == WebMercatorProjection.INSTANCE
                            .getSpatialReferenceID()) {
                return new OSMDroidFilter(tsInfo, bitmapLoader, OSMDROID_SQLITE_RESOLVER);
            } else if (_pathStructure.equals("OSM_DROID_SQLITE")) {
                return new EquirectangularTilesetFilter(tsInfo, bitmapLoader,
                        OSMDROID_SQLITE_RESOLVER);
            } else if (_pathStructure.equals("NETT_WARRIOR_SQLITE")) {
                return new OSMDroidFilter(tsInfo, bitmapLoader, NETT_WARRIOR_SQLITE_RESOLVER);
            } else {
                return null;
            }
        }
    }

    /**************************************************************************/

    private static class EquirectangularTilesetFilter extends EquirectangularTilesetSupport {

        private final TilesetInfo tsInfo;
        private final TilesetResolver resolver;

        public EquirectangularTilesetFilter(TilesetInfo tsInfo, GLBitmapLoader loader,
                TilesetResolver filter) {
            super(tsInfo, loader);

            this.tsInfo = tsInfo;
            this.resolver = filter;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void init() {
            SimpleUriTilesetSupport.initImpl(this.bitmapLoader, this.tsInfo);
        }

        @Override
        public void release() {
            SimpleUriTilesetSupport.releaseImpl(this.bitmapLoader, this.tsInfo);
        }

        @Override
        public FutureTask<Bitmap> getTile(int latIndex, int lngIndex,
                int level, BitmapFactory.Options opts) {
            return SimpleUriTilesetSupport.getTileImpl(this.bitmapLoader, this.tsInfo,
                    this.resolver, latIndex, lngIndex, level, opts);
        }
    }

    private static class OSMDroidFilter extends OSMTilesetSupport {
        private final TilesetInfo tsInfo;
        private final TilesetResolver resolver;

        public OSMDroidFilter(TilesetInfo tsInfo, GLBitmapLoader loader, TilesetResolver filter) {
            super(tsInfo, loader);

            this.tsInfo = tsInfo;
            this.resolver = filter;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void init() {
            SimpleUriTilesetSupport.initImpl(this.bitmapLoader, this.tsInfo);
        }

        @Override
        public void release() {
            SimpleUriTilesetSupport.releaseImpl(this.bitmapLoader, this.tsInfo);
        }

        @Override
        public FutureTask<Bitmap> getTile(int latIndex, int lngIndex,
                int level, BitmapFactory.Options opts) {
            return SimpleUriTilesetSupport.getTileImpl(this.bitmapLoader, this.tsInfo,
                    this.resolver, latIndex, lngIndex, level, opts);
        }
    }
}
