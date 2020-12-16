
package com.atakmap.android.gdal.layers;

import com.atakmap.android.layers.GenericLayerScanner;
import com.atakmap.android.layers.LayerScanner;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.map.layer.raster.mosaic.MosaicUtils;
import com.atakmap.map.layer.raster.pfps.PfpsLayerInfoSpi;

import java.io.File;

public class GdalMosaicScanner extends GenericLayerScanner {
    public final static LayerScanner.Spi SPI = new LayerScanner.Spi() {
        @Override
        public LayerScanner create() {
            return new GdalMosaicScanner();
        }
    };

    /**************************************************************************/

    private final static String PFPS_DATA_DIR = "pfps";

    /**************************************************************************/

    private GdalMosaicScanner() {
        super("pfps");
    }

    @Override
    public void reset() {
    }

    @Override
    protected String getProviderHint(int depth, File f) {
        return PfpsLayerInfoSpi.INSTANCE.getType();
    }

    @Override
    protected int checkFile(int depth, File f) {
        if (IOProviderFactory.isFile(f)) {
            return REJECT;
        } else if (IOProviderFactory.isDirectory(f)
                && !MosaicUtils.isMosaicDir(f)) {
            return DELAY;
        } else {
            return ACCEPT;
        }
    }

    @Override
    protected File[] getScanDirs() {
        return getDefaultScanDirs(PFPS_DATA_DIR, true);
    }
}
