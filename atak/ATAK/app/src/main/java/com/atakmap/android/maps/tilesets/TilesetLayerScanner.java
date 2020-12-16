
package com.atakmap.android.maps.tilesets;

import com.atakmap.android.gdal.layers.KmzLayerInfoSpi;
import com.atakmap.android.layers.GenericLayerScanner;
import com.atakmap.android.layers.LayerScanner;
import com.atakmap.android.layers.ScanLayersService;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.map.gpkg.GeoPackage;
import com.atakmap.map.layer.raster.gpkg.GeoPackageLayerInfoSpi;

import java.io.File;
import java.io.FilenameFilter;

public class TilesetLayerScanner extends GenericLayerScanner {

    public static final String TAG = "TilesetLayerScanner";

    public final static LayerScanner.Spi SPI = new LayerScanner.Spi() {
        @Override
        public LayerScanner create() {
            return new TilesetLayerScanner();
        }
    };

    private final static FilenameFilter _contentsXmlFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String filename) {
            return filename.equals("contents.xml")
                    || filename.equals(ADJACENT_MARKER_FILE_NAME);
        }
    };

    private final static FilenameFilter _dirFilter = new FilenameFilter() {
        @Override
        public boolean accept(File dir, String filename) {
            return IOProviderFactory.isDirectory(
                    new File(FileSystemUtils.sanitizeWithSpacesAndSlashes(
                            dir + File.separator + filename)));
        }

    };

    private final static String ADJACENT_MARKER_FILE_NAME = ".adjacent";

    public TilesetLayerScanner() {
        super("Tileset");
    }

    @Override
    public void reset() {
        _removeContentsFiles();
    }

    // XXX - next 2 legacy; remove
    private static void _deleteContentsXmlAndRecurse(File dir) {
        File[] subDirs = IOProviderFactory.listFiles(dir, _dirFilter);
        File[] contentXmlFiles = IOProviderFactory.listFiles(dir,
                _contentsXmlFilter);
        if (contentXmlFiles != null) {
            for (File cf : contentXmlFiles) {
                FileSystemUtils.deleteFile(cf);
            }
        }
        if (subDirs != null) {
            for (File sd : subDirs) {
                _deleteContentsXmlAndRecurse(sd);
            }
        }
    }

    private void _removeContentsFiles() {
        for (String dir : ScanLayersService.getRootDirs()) {
            File f = new File(dir + File.separator + "layers");
            if (IOProviderFactory.exists(f)
                    && IOProviderFactory.isDirectory(f)) {
                _deleteContentsXmlAndRecurse(f);
            }
        }
    }

    /**************************************************************************/
    // Generic Layer Scanner

    // Current scanning rules:
    // Files and directories in the root (depth 0) are always inspected as new;
    // - however, a contents.xml file is maintained for consistency.
    // All directories are traversed and if a contents.xml file exists then it is loaded;
    // - otherwise the directory is scanned as new.

    @Override
    protected File[] getScanDirs() {
        return getDefaultScanDirs("layers", true);
    }

    @Override
    protected int checkFile(int depth, File f) {
        int retval;
        if ((depth == 0 && IOProviderFactory.isDirectory(f)) ||
                GeoPackage.isGeoPackage(f) ||
                IOProviderFactory.isDatabase(f) ||
                TilesetInfo.isZipArchive(f.getAbsolutePath()) ||
                f.getName().endsWith(".kmz")) {

            if (IOProviderFactory.isDirectory(f))
                retval = checkDir(f);
            else
                retval = ACCEPT;
        } else {
            retval = REJECT;
        }
        return retval;
    }

    private int checkDir(File dir) {
        File[] children = IOProviderFactory.listFiles(dir);
        if (children != null) {
            for (File aChildren : children) {
                // XXX - spi only supports single recursion, should we allow full
                //       recursion???
                //if(children[i].isDirectory() && checkDir(children[i]))
                //    return true;
                if (checkFile(1, aChildren) == ACCEPT) {
                    if (GeoPackage.isGeoPackage(aChildren)
                            || aChildren.getName().endsWith(".kmz"))
                        return DELAY;
                    else
                        return ACCEPT;
                }
            }
        }
        return REJECT;
    }

    @Override
    protected String getProviderHint(int depth, File f) {
        if (f.getName().endsWith(".kmz"))
            return KmzLayerInfoSpi.INSTANCE.getType();
        else if (GeoPackage.isGeoPackage(f))
            return GeoPackageLayerInfoSpi.INSTANCE.getType();
        else
            return TilesetLayerInfoSpi.INSTANCE.getType();
    }
}
