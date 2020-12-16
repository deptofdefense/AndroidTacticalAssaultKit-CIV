
package com.atakmap.spatial.file;

import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;

import java.io.File;
import com.atakmap.coremap.locale.LocaleUtil;

/**
 * Support loading GPX files as ATAK map items
 * 
 * 
 */
public class GpxFileSpatialDb extends OgrSpatialDb {

    public static final String GPX_FILE_MIME_TYPE = "application/gpx+xml";
    public static final int GPX_FILE_ICON_ID = R.drawable.ic_gpx_file_notification_icon;
    public static final String GPX_CONTENT_TYPE = "GPX";

    public GpxFileSpatialDb(DataSourceFeatureDataStore spatialDb) {
        super(spatialDb, "GPX", "asset://icons/gpx.png", "GPX");
    }

    @Override
    public int processAccept(File file, int depth) {
        if (IOProviderFactory.isFile(file) && IOProviderFactory.canRead(file)) {
            String lc = file.getName().toLowerCase(LocaleUtil.getCurrent());
            if (lc.endsWith(".gpx"))
                return PROCESS_ACCEPT;
        } else if (IOProviderFactory.isDirectory(file)) {
            return PROCESS_RECURSE;
        }

        return PROCESS_REJECT;
    }

    @Override
    public String getFileDirectoryName() {
        return FileSystemUtils.OVERLAYS_DIRECTORY;
    }

    @Override
    public int getIconId() {
        return GPX_FILE_ICON_ID;
    }

    @Override
    public String getFileMimeType() {
        return GPX_FILE_MIME_TYPE;
    }

    @Override
    public String getContentType() {
        return GPX_CONTENT_TYPE;
    }
}
