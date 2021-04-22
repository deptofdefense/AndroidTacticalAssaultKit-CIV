
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
public class GMLSpatialDb extends OgrSpatialDb {

    private final static FileFilter GML_FILTER = new FileFilter() {
        @Override
        public boolean accept(File arg0) {
            return (IOProviderFactory.isDirectory(arg0)
                    || arg0.getName().endsWith(".gml"));
        }

    };

    public final static FeatureDataSource ZIPPED_GML_DATA_SOURCE = new RecursiveFeatureDataSource(
            "gml-zipped", "ogr", GML_FILTER) {
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

    public static final String TAG = "GMLSpatialDb";

    public static final String GML_CONTENT_TYPE = "GML";
    public static final String GML_FILE_MIME_TYPE = "application/octet-stream";
    private static final String GROUP_NAME = "GML";
    private static final String ICON_PATH = "asset://icons/esri.png";
    public static final int GML_FILE_ICON_ID = R.drawable.ic_esri_file_notification_icon;
    public static final String GML_TYPE = "GML";

    public GMLSpatialDb(DataSourceFeatureDataStore spatialDb) {
        super(spatialDb, GROUP_NAME, ICON_PATH, GML_TYPE);
    }

    @Override
    public String getFileDirectoryName() {
        return FileSystemUtils.OVERLAYS_DIRECTORY;
    }

    @Override
    public int getIconId() {
        return GML_FILE_ICON_ID;
    }

    @Override
    public int processAccept(File file, int depth) {
        if (IOProviderFactory.isFile(file) && IOProviderFactory.canRead(file)) {
            String lc = file.getName().toLowerCase(LocaleUtil.getCurrent());
            if (lc.endsWith(".gml"))
                return PROCESS_ACCEPT;
            else if (lc.endsWith(".zip") && containsGmlFile(file))
                return PROCESS_ACCEPT;
        } else if (file.isDirectory()) {
            return PROCESS_RECURSE;
        }

        return PROCESS_REJECT;
    }

    /**
     * Determine if the zip file actually contains a .gml file before trying to
     * classify it as a shape file.   Not all zip files in the overlay directory
     * are shape files.
     */
    private boolean containsGmlFile(final File f) {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(f);
            Enumeration zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                String fileName = ((ZipEntry) zipEntries.nextElement())
                        .getName();
                String lc = fileName.toLowerCase(LocaleUtil.getCurrent());
                if (lc.endsWith(".gml"))
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
        return GML_FILE_MIME_TYPE;
    }

    @Override
    public String getContentType() {
        return GML_CONTENT_TYPE;
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
            return ZIPPED_GML_DATA_SOURCE.getName();
        } else
            return super.getProviderHint(file);
    }
}
