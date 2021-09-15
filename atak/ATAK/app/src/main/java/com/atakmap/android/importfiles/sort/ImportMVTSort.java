
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.util.Pair;

import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.map.layer.feature.FeatureDataSource;
import com.atakmap.map.layer.feature.FeatureDataSourceContentFactory;
import com.atakmap.spatial.file.MvtSpatialDb;

import java.io.File;

public class ImportMVTSort extends ImportInPlaceResolver {

    private static final String TAG = "ImportMVTSort";

    public ImportMVTSort(Context context, boolean validateExt,
            boolean copyFile, boolean importInPlace) {
        super(".mbtiles", FileSystemUtils.OVERLAYS_DIRECTORY, validateExt,
                copyFile, importInPlace, context.getString(R.string.mvt_file),
                context.getDrawable(R.drawable.ic_mvt));
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file))
            return false;

        FeatureDataSource.Content ds = null;
        try {
            ds = FeatureDataSourceContentFactory.parse(file,
                    MvtSpatialDb.MVT_TYPE);
            return (ds != null);
        } catch (Throwable t) {
            return false;
        } finally {
            if (ds != null)
                ds.close();
        }
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(MvtSpatialDb.MVT_CONTENT_TYPE,
                MvtSpatialDb.MVT_FILE_MIME_TYPE);
    }
}
