
package com.atakmap.android.gdal.layers;

import com.atakmap.android.layers.GenericLayerScanner;
import com.atakmap.android.layers.LayerScanner;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.raster.apass.ApassLayerInfoSpi;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Implementation of a scanner responsible for ingesting APASS modsaic data.
 */
public class ApassMosaicScanner extends GenericLayerScanner {
    public final static LayerScanner.Spi SPI = new LayerScanner.Spi() {
        @Override
        public LayerScanner create() {
            return new ApassMosaicScanner();
        }
    };

    private final static Set<String> SCAN_BLACKLIST = new HashSet<>();
    static {
        SCAN_BLACKLIST.add(FileSystemUtils.OLD_ATAK_ROOT_DIRECTORY);
        SCAN_BLACKLIST.add(FileSystemUtils.ATAK_ROOT_DIRECTORY);
    }

    private final static int SCAN_DEPTH_LIMIT = 3;

    /**************************************************************************/

    private ApassMosaicScanner() {
        super("apass");
    }

    @Override
    public void reset() {
    }

    @Override
    protected File[] getScanDirs() {
        return FileSystemUtils.getDeviceRoots();
    }

    @Override
    protected void scanDirectory(int depth, File dir) {
        File apassDb = new File(dir, "images.pfiva.sqlite3");
        if (IOProviderFactory.exists(apassDb)) {
            if (!this.database.contains(apassDb))
                try {
                    this.database.add(apassDb, this.getName());
                } catch (IOException e) {
                    this.error("Error creating LayerInfo for "
                            + apassDb.getName());
                    Log.e(TAG, "error occurred creating layer info for: "
                            + apassDb.getName(), e);
                }
        }
        if (depth == SCAN_DEPTH_LIMIT)
            return;
        File[] children = IOProviderFactory.listFiles(dir);
        if (children == null)
            return;
        for (File aChildren : children) {
            if (IOProviderFactory.isDirectory(aChildren)
                    && !SCAN_BLACKLIST.contains(aChildren.getName()))
                this.scanDirectory(depth + 1, aChildren);
        }
    }

    @Override
    protected final int checkFile(int depth, File f) {
        throw new IllegalStateException();
    }

    @Override
    protected String getProviderHint(int depth, File f) {
        return ApassLayerInfoSpi.INSTANCE.getType();
    }
}
