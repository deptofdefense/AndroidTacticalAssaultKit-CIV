
package com.atakmap.android.gdal.layers;

import android.net.Uri;

import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.raster.gdal.GdalTileReader;
import com.atakmap.map.layer.raster.tilereader.AndroidTileReader;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory;
import com.atakmap.map.layer.raster.tilereader.TileReaderSpi2;

import org.gdal.gdal.Dataset;

public final class KMZTileReaderSpi implements TileReaderSpi2 {
    public final static TileReaderSpi2 INSTANCE = new KMZTileReaderSpi();

    private KMZTileReaderSpi() {
    }

    @Override
    public int getPriority() {
        return 3;
    }

    @Override
    public String getName() {
        return "kmz";
    }

    @Override
    public TileReader create(String uri,
            TileReaderFactory.Options options) {
        if (!uri.startsWith("zip://") || !uri.contains(".kmz"))
            return null;
        if (options == null || options.preferredTileWidth <= 0
                || options.preferredTileHeight <= 0) {
            final TileReaderFactory.Options opts = (options != null)
                    ? new TileReaderFactory.Options(options)
                    : new TileReaderFactory.Options();
            if (opts.preferredTileHeight <= 0)
                opts.preferredTileHeight = 256;
            if (opts.preferredTileWidth <= 0)
                opts.preferredTileWidth = 256;
            options = opts;
        }
        if (uri.toLowerCase(LocaleUtil.getCurrent())
                .endsWith("jpg")) {
            try {
                return new AndroidTileReader(
                        new ZipVirtualFile(Uri.parse(uri).getPath()),
                        256, 256,
                        options.cacheUri,
                        options.asyncIO);
            } catch (Throwable e) {
                Log.e("GLGdalKmzMapLayer",
                        "Failed to create Android KMZ tile reader.", e);
            }
        }
        final Dataset d = GdalLibrary.openDatasetFromPath(uri);
        if (d == null)
            return null;

        return new GdalTileReader(d,
                d.GetDescription(),
                options.preferredTileWidth,
                options.preferredTileHeight,
                options.cacheUri,
                options.asyncIO);
    }

    @Override
    public boolean isSupported(String uri) {
        if (!uri.startsWith("zip://") || !uri.contains(".kmz"))
            return false;
        Dataset d = null;
        try {
            d = GdalLibrary.openDatasetFromPath(uri);
            return d != null;
        } catch (Exception e) {
            return false;
        } finally {
            if (d != null)
                d.delete();
        }
    }
}
