
package com.atakmap.android.importfiles.sort;

import android.content.Context;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.map.layer.raster.ImageryFileType;

import java.io.File;
import java.util.Set;

/**
 * Sorts ATAK "Native" Imagery. Note legacy Tilesets are handled via <code>ImportTilesetSort</code>
 * 
 * @deprecated Dead code, replaced by {@link ImportLayersSort}
 */
@Deprecated
@DeprecatedApi(since = "4.2", forRemoval = true, removeAt = "4.5")
public class ImportImagerySort extends ImportInternalSDResolver {

    private static final String TAG = "ImportImagerySort";

    public ImportImagerySort(Context context, boolean validateExt,
            boolean copyFile) {
        // support multiple file extensions, and multiple destinations
        super(null, null, validateExt, copyFile,
                context.getString(R.string.native_imagery),
                context.getDrawable(R.drawable.ic_menu_maps));
    }

    /**
     * Support any file extension that matches per <code>ImageryFileType</code>
     * 
     * @param file
     * @return
     */
    @Override
    public boolean match(File file) {

        try {
            ImageryFileType.AbstractFileType fileType = ImageryFileType
                    .getFileType(file);
            if (fileType == null)
                return false;

            // KML/Z importers exist which do content inspection
            if (fileType.getID() == ImageryFileType.KML
                    || fileType.getID() == ImageryFileType.KMZ) {
                Log.d(TAG,
                        "Matched Imagery type "
                                + fileType.getDescription()
                                + ", but delegating to more specific importer for "
                                + file.getAbsolutePath());
                return false;
            }

            if (FileSystemUtils.isEmpty(fileType.getPath(file))) {
                Log.d(TAG,
                        "Matched Imagery type " + fileType.getDescription()
                                + ", but unable to determine path for "
                                + file.getAbsolutePath());
                return false;
            }

            Log.d(TAG, "Matched Imagery of type: " + fileType.getDescription());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Move to new location on same SD card Defer to AbstractFileType for the relative path
     * 
     * @param file
     * @return
     */
    @Override
    public File getDestinationPath(File file) {
        // Note this get called multiple times per file... if any speed concerns
        // arise on running through all the AbstractFileType matchers, then fix here
        ImageryFileType.AbstractFileType fileType = ImageryFileType
                .getFileType(file);
        if (fileType == null) {
            Log.d(TAG,
                    "Unable to determine Imagery type for "
                            + file.getAbsolutePath());
            return null;
        }

        if (FileSystemUtils.isEmpty(fileType.getPath(file))) {
            Log.d(TAG,
                    "Matched Imagery type " + fileType.getDescription()
                            + ", but unable to determine path for "
                            + file.getAbsolutePath());
            return null;
        }

        File folder = FileSystemUtils.getItem(fileType.getPath(file));
        return new File(folder, file.getName());
    }

    @Override
    protected void onFileSorted(File src, File dst, Set<SortFlags> flags) {
        super.onFileSorted(src, dst, flags);
    }
}
