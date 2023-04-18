
package com.atakmap.android.gdal;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.gdal.layers.ApassMosaicScanner;
import com.atakmap.android.gdal.layers.GdalLayerScanner;
import com.atakmap.android.gdal.layers.GdalMosaicScanner;
import com.atakmap.android.gdal.layers.KMZTileReaderSpi;
import com.atakmap.android.gdal.layers.KmzLayerInfoSpi;
import com.atakmap.android.gdal.layers.graphics.GLGdalKmzMapLayer2;
import com.atakmap.android.layers.ScanLayersService;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.raster.DatasetDescriptorFactory2;
import com.atakmap.map.layer.raster.apass.ApassLayerInfoSpi;
import com.atakmap.map.layer.raster.drg.DRGTileReader;
import com.atakmap.map.layer.raster.gdal.GdalLayerInfo;
import com.atakmap.map.layer.raster.gdal.GdalTileReader;
import com.atakmap.map.layer.raster.gdal.opengl.GLGdalPfiMapLayer2;
import com.atakmap.map.layer.raster.gdal.opengl.GLGdalPriMapLayer2;
import com.atakmap.map.layer.raster.mosaic.opengl.GLMosaicMapLayer;
import com.atakmap.map.layer.raster.opengl.GLMapLayerFactory;
import com.atakmap.map.layer.raster.pfps.PfpsLayerInfoSpi;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.map.layer.raster.tilereader.TileReaderFactory;
import com.atakmap.map.layer.raster.tilereader.TileReaderSpi2;

import org.gdal.gdal.Dataset;

/**
 * Responsible for initializing all native rendering with the system.
 * This includes all of the PFI, PRI, MrSID, PFPS, and GDAL supported
 * files.
 */
public class NativeRenderingMapComponent extends AbstractMapComponent {
    private final static TileReaderSpi2 GDAL_TILEREADER_SPI = new TileReaderSpi2() {
        @Override
        public String getName() {
            return "native";
        }

        @Override
        public TileReader create(String uri, TileReaderFactory.Options opts) {
            // XXX - short circuit is here as moving it further up would
            //       involve unwinding the use of GLGdalMapLayer2 which needs
            //       to be fully traced for potential issues. This achieves
            //       the same net result as an immediate changeset without
            //       introducing any additional risk.

            // if the file is a DRG, return the DRG reader to do the masking
            if (DRGTileReader.SPI.isSupported(uri)) {
                final TileReader drg = DRGTileReader.SPI.create(uri, opts);
                if (drg != null)
                    return drg;
            }

            Throwable error = null;
            try {
                Dataset dataset = GdalLibrary.openDatasetFromPath(uri);
                if (dataset != null) {
                    int tileWidth = 0;
                    int tileHeight = 0;
                    String cacheUri = null;
                    TileReader.AsynchronousIO asyncIO = null;
                    if (opts != null) {
                        tileWidth = opts.preferredTileWidth;
                        tileHeight = opts.preferredTileHeight;
                        cacheUri = opts.cacheUri;
                        asyncIO = opts.asyncIO;
                    }

                    if (tileWidth <= 0)
                        tileWidth = dataset.GetRasterBand(1).GetBlockXSize();
                    if (tileHeight <= 0)
                        tileHeight = dataset.GetRasterBand(1).GetBlockYSize();

                    return new GdalTileReader(dataset, uri, tileWidth,
                            tileHeight, cacheUri, asyncIO);
                }
            } catch (Throwable t) {
                error = t;
            }

            Log.e("GdalTileReader", "Failed to open dataset " + uri, error);
            return null;
        }

        @Override
        public boolean isSupported(String uri) {
            Dataset ds = null;
            try {
                ds = GdalLibrary.openDatasetFromPath(uri);
                return (ds != null);
            } finally {
                if (ds != null)
                    ds.delete();
            }
        }

        @Override
        public int getPriority() {
            return 1;
        }
    };

    private static boolean initialized = false;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        synchronized (NativeRenderingMapComponent.class) {
            if (initialized)
                return;

            if (GdalLibrary.isInitialized()) {
                TileReaderFactory.registerSpi(GDAL_TILEREADER_SPI);
                TileReaderFactory.registerSpi(KMZTileReaderSpi.INSTANCE);

                GLMapLayerFactory.registerSpi(GLGdalPriMapLayer2.SPI);
                GLMapLayerFactory.registerSpi(GLGdalPfiMapLayer2.SPI);
                GLMapLayerFactory.registerSpi(GLMosaicMapLayer.SPI);
                GLMapLayerFactory.registerSpi(GLGdalKmzMapLayer2.SPI);

                DatasetDescriptorFactory2
                        .register(GdalLayerInfo.INSTANCE);
                DatasetDescriptorFactory2
                        .register(ApassLayerInfoSpi.INSTANCE);
                DatasetDescriptorFactory2
                        .register(PfpsLayerInfoSpi.INSTANCE);
                DatasetDescriptorFactory2
                        .register(KmzLayerInfoSpi.INSTANCE);

                // install layer scanners
                ScanLayersService.getInstance()
                        .registerScannerSpi(ApassMosaicScanner.SPI);

                // For legacy support of the old directory structure specifically on the
                // external sd card.

                ScanLayersService.getInstance()
                        .registerScannerSpi(GdalLayerScanner.SPI);
                ScanLayersService.getInstance()
                        .registerScannerSpi(GdalMosaicScanner.SPI);

            }
            initialized = true;
        }
    }

    @Override
    public void onDestroyImpl(Context context, MapView view) {
    }

}
