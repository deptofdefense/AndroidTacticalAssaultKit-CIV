
package com.atakmap.spatial.file;

import android.content.Context;

import com.atakmap.android.features.FeatureDataStoreDeepMapItemQuery;
import com.atakmap.android.overlay.MapOverlay;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;

import java.io.File;
import java.io.FileFilter;

public class MvtSpatialDb extends SpatialDbContentSource {

    private final static FileFilter MVT_FILTER = new FileFilter() {
        @Override
        public boolean accept(File arg0) {
            return (IOProviderFactory.isDirectory(arg0)
                    || arg0.getName().endsWith(".mbtiles"));
        }

    };

    public static final String TAG = "MvtSpatialDb";

    public static final String MVT_CONTENT_TYPE = "MVT";
    public static final String MVT_FILE_MIME_TYPE = "application/octet-stream";
    static final String GROUP_NAME = "Mapbox Vector Tiles";
    static final String ICON_PATH = ATAKUtilities
            .getResourceUri(R.drawable.ic_mvt);
    public static final int MVT_FILE_ICON_ID = R.drawable.ic_mvt;
    public static final String MVT_TYPE = "MVT";

    public MvtSpatialDb(DataSourceFeatureDataStore spatialDb) {
        super(spatialDb, GROUP_NAME, MVT_TYPE);
    }

    @Override
    public String getFileDirectoryName() {
        return FileSystemUtils.OVERLAYS_DIRECTORY;
    }

    @Override
    public int getIconId() {
        return MVT_FILE_ICON_ID;
    }

    @Override
    public int processAccept(File file, int depth) {
        if (IOProviderFactory.isFile(file) && IOProviderFactory.canRead(file)) {
            String lc = file.getName().toLowerCase(LocaleUtil.getCurrent());
            if (lc.endsWith(".mbtiles"))
                return PROCESS_ACCEPT;
        } else if (file.isDirectory()) {
            return PROCESS_RECURSE;
        }

        return PROCESS_REJECT;
    }

    @Override
    public String getFileMimeType() {
        return MVT_FILE_MIME_TYPE;
    }

    @Override
    public String getIconPath() {
        return ICON_PATH;
    }

    @Override
    public String getContentType() {
        return MVT_CONTENT_TYPE;
    }

    @Override
    protected String getProviderHint(File file) {
        return "MVT";
    }

    @Override
    public MapOverlay createOverlay(Context context,
            FeatureDataStoreDeepMapItemQuery query) {
        return new MvtMapOverlay(context, database, query);
    }
}
