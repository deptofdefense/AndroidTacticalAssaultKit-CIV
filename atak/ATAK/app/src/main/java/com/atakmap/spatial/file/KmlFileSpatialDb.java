
package com.atakmap.spatial.file;

import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;

import java.io.File;
import java.util.HashSet;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Set;

public class KmlFileSpatialDb extends OgrSpatialDb {
    public static final String KML_FILE_MIME_TYPE = "application/vnd.google-earth.kml+xml";
    public static final String KMZ_FILE_MIME_TYPE = "application/vnd.google-earth.kmz";
    public static final String KML_CONTENT_TYPE = "KML";
    public static final String KML_TYPE = "kml";
    public static final String KMZ_TYPE = "kmz";

    private final static Set<String> KML_MIME_TYPES = new HashSet<>();
    static {
        KML_MIME_TYPES.add(KML_FILE_MIME_TYPE);
        KML_MIME_TYPES.add(KMZ_FILE_MIME_TYPE);
    }

    public static final String KML_DIRECTORY_NAME = FileSystemUtils.OVERLAYS_DIRECTORY;

    private static final String GROUP_NAME = "KML";
    private static final String ICON_PATH = "asset://icons/kml.png";
    public static final int KML_FILE_ICON_ID = R.drawable.ic_kml_file_notification_icon;

    public KmlFileSpatialDb(DataSourceFeatureDataStore spatialDb) {
        super(spatialDb, GROUP_NAME, ICON_PATH, "kml");
    }

    @Override
    public int getIconId() {
        return KML_FILE_ICON_ID;
    }

    @Override
    public String getFileDirectoryName() {
        return KML_DIRECTORY_NAME;
    }

    @Override
    public int processAccept(File file, int depth) {
        if (IOProviderFactory.isFile(file) && IOProviderFactory.canRead(file)) {
            String lc = file.getName().toLowerCase(LocaleUtil.getCurrent());
            if (lc.endsWith(".kml") || lc.endsWith(".kmz"))
                return PROCESS_ACCEPT;
        } else if (IOProviderFactory.isDirectory(file)) {
            return PROCESS_RECURSE;
        }

        return PROCESS_REJECT;
    }

    @Override
    public String getFileMimeType() {
        return KML_FILE_MIME_TYPE;
    }

    @Override
    public String getContentType() {
        return KML_CONTENT_TYPE;
    }

    @Override
    public Set<String> getSupportedMIMETypes() {
        return KML_MIME_TYPES;
    }
}
