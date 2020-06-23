
package com.atakmap.android.gdal.layers;

import com.atakmap.android.layers.GenericLayerScanner;
import com.atakmap.android.layers.LayerScanner;
import com.atakmap.map.layer.raster.gdal.GdalLayerInfo;

import java.io.File;

public class GdalLayerScanner extends GenericLayerScanner {

    public final static LayerScanner.Spi SPI = new LayerScanner.Spi() {
        @Override
        public LayerScanner create() {
            return new GdalLayerScanner();
        }
    };

    /**************************************************************************/

    private final static String NATIVE_DATA_DIR = "native";

    /**************************************************************************/

    private GdalLayerScanner() {
        super("native");
    }

    @Override
    public void reset() {
    }

    @Override
    protected File[] getScanDirs() {
        return getDefaultScanDirs(NATIVE_DATA_DIR, true);
    }

    @Override
    protected String getProviderHint(final int depth, final File f) {
        return GdalLayerInfo.PROVIDER_NAME;
    }

    @Override
    protected int checkFile(final int depth, final File f) {
        if (f.getName().charAt(0) == '.') {
            return REJECT;
        } else {
            return ACCEPT;
        }
    }
}
