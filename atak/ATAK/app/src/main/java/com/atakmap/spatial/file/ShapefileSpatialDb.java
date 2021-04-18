
package com.atakmap.spatial.file;

import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.FeatureDataSource;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Enumeration;

/**
 * Support ingesting ESRI Shapefiles
 * 
 * 
 */
public class ShapefileSpatialDb extends OgrSpatialDb {

    private final static FileFilter SHP_FILTER = new FileFilter() {
        @Override
        public boolean accept(File arg0) {
            return (IOProviderFactory.isDirectory(arg0)
                    || arg0.getName().endsWith(".shp"));
        }

    };

    public final static FeatureDataSource ZIPPED_SHP_DATA_SOURCE = new RecursiveFeatureDataSource(
            "shp-zipped", "ogr", SHP_FILTER) {
        @Override
        public Content parse(File file) throws IOException {
            if (!(file instanceof ZipVirtualFile)) {
                do {
                    // check if it is a zip file
                    if (FileSystemUtils.isZipPath(file)) {
                        try {
                            // to to create, if we fail, we'll drop through to return null
                            file = new ZipVirtualFile(file);
                            break;
                        } catch (Throwable ignored) {
                        }
                        return null;
                    }
                } while (false);
            }
            return super.parse(file);
        }
    };

    public static final String TAG = "ShapefileSpatialDb";

    public static final String SHP_CONTENT_TYPE = "Shapefile";
    public static final String SHP_FILE_MIME_TYPE = "application/octet-stream"; // TODO?
                                                                                // application/x-esri-shape
    private static final String GROUP_NAME = "Shapefile";
    private static final String ICON_PATH = "asset://icons/esri.png";
    public static final int SHP_FILE_ICON_ID = R.drawable.ic_esri_file_notification_icon;
    public static final String SHP_TYPE = "shp";

    public ShapefileSpatialDb(DataSourceFeatureDataStore spatialDb) {
        super(spatialDb, GROUP_NAME, ICON_PATH, "shp");
    }

    @Override
    public String getFileDirectoryName() {
        return FileSystemUtils.OVERLAYS_DIRECTORY;
    }

    @Override
    public int getIconId() {
        return SHP_FILE_ICON_ID;
    }

    @Override
    public int processAccept(File file, int depth) {
        if (IOProviderFactory.isFile(file) && IOProviderFactory.canRead(file)) {
            String lc = file.getName().toLowerCase(LocaleUtil.getCurrent());
            if (lc.endsWith(".shp"))
                return PROCESS_ACCEPT;
            else if (lc.endsWith(".zip") && containsShpFile(file))
                return PROCESS_ACCEPT;
        } else if (file.isDirectory()) {
            return PROCESS_RECURSE;
        }

        return PROCESS_REJECT;
    }

    /**
     * Determine if the zip file actually contains a .shp file before trying to 
     * classify it as a shape file.   Not all zip files in the overlay directory
     * are shape files.
     */
    private boolean containsShpFile(final File f) {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(f);
            Enumeration zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                String fileName = ((ZipEntry) zipEntries.nextElement())
                        .getName();
                String lc = fileName.toLowerCase(LocaleUtil.getCurrent());
                if (lc.endsWith(".shp"))
                    return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "error processing: " + f);
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException ignore) {
                    // ignore
                }
            }
        }
        return false;
    }

    @Override
    public String getFileMimeType() {
        return SHP_FILE_MIME_TYPE;
    }

    @Override
    public String getContentType() {
        return SHP_CONTENT_TYPE;
    }

    @Override
    public boolean processFile(File file) {
        if (FileSystemUtils.checkExtension(file, "zip"))
            try {
                file = new ZipVirtualFile(file);
            } catch (IllegalArgumentException | IllegalStateException ignored) {
            }
        return super.processFile(file);
    }

    @Override
    protected String getProviderHint(File file) {
        if (file instanceof ZipVirtualFile) {
            return ZIPPED_SHP_DATA_SOURCE.getName();
        } else
            return super.getProviderHint(file);
    }
}
