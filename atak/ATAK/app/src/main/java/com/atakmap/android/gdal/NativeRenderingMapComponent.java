
package com.atakmap.android.gdal;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.gdal.layers.ApassMosaicScanner;
import com.atakmap.android.gdal.layers.GdalLayerScanner;
import com.atakmap.android.gdal.layers.GdalMosaicScanner;
import com.atakmap.android.gdal.layers.KmzLayerInfoSpi;
import com.atakmap.android.gdal.layers.graphics.GLGdalKmzMapLayer2;
import com.atakmap.android.layers.ScanLayersService;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.raster.DatasetDescriptorFactory2;
import com.atakmap.map.layer.raster.apass.ApassLayerInfoSpi;
import com.atakmap.map.layer.raster.gdal.GdalLayerInfo;
import com.atakmap.map.layer.raster.gdal.opengl.GLGdalMapLayer2;
import com.atakmap.map.layer.raster.gdal.opengl.GLGdalPfiMapLayer2;
import com.atakmap.map.layer.raster.gdal.opengl.GLGdalPriMapLayer2;
import com.atakmap.map.layer.raster.mosaic.opengl.GLMosaicMapLayer;
import com.atakmap.map.layer.raster.opengl.GLMapLayerFactory;
import com.atakmap.map.layer.raster.pfps.PfpsLayerInfoSpi;

/**
 * Responsible for initializing all native rendering with the system.
 * This includes all of the PFI, PRI, MrSID, PFPS, and GDAL supported
 * files.
 */
public class NativeRenderingMapComponent extends AbstractMapComponent {

    private static boolean initialized = false;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        synchronized (NativeRenderingMapComponent.class) {
            if (initialized)
                return;

            if (GdalLibrary.isInitialized()) {
                GLMapLayerFactory.registerSpi(GLGdalMapLayer2.SPI);
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
