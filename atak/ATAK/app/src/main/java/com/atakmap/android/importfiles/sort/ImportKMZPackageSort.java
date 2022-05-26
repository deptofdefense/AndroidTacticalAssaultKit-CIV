
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.util.Pair;

import com.atakmap.android.layers.kmz.KMZPackageImporter;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.spatial.file.KmlFileSpatialDb;

import java.io.File;

/**
 * Sorter class that handles KMZ files with multiple types of data inside
 */
public class ImportKMZPackageSort extends ImportInPlaceResolver {

    public ImportKMZPackageSort(Context context, boolean validateExt,
            boolean copyFile, boolean importInPlace) {
        super(".kmz", FileSystemUtils.OVERLAYS_DIRECTORY, validateExt, copyFile,
                importInPlace, context.getString(R.string.kmz_package),
                context.getDrawable(R.drawable.ic_kmz_package));

    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(KMZPackageImporter.CONTENT_TYPE,
                KmlFileSpatialDb.KMZ_FILE_MIME_TYPE);
    }

    @Override
    public boolean match(File file) {
        return KMZPackageImporter.getContentTypes(file).size() > 1;
    }
}
