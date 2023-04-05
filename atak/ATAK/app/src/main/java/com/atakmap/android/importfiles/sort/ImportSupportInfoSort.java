
package com.atakmap.android.importfiles.sort;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.Set;

/**
 * Sorts ATAK Support Files
 * 
 * 
 */
public class ImportSupportInfoSort extends ImportInternalSDResolver {

    private static final String TAG = "ImportSupportInfoSort";

    /**
     * Enumeration of support files
     *
     * 
     */
    private enum TYPE {
        SUPPORTINF("support.inf", FileSystemUtils.SUPPORT_DIRECTORY),
        SPLASH("atak_splash.png", FileSystemUtils.SUPPORT_DIRECTORY);

        final String _filename;
        final String _folder;

        TYPE(String filename, String folder) {
            _filename = filename;
            _folder = folder;
        }

        @Override
        public String toString() {
            return String.format("%s %s %s", super.toString(), _filename,
                    _folder);
        }
    }

    public ImportSupportInfoSort(boolean copyFile) {
        super("", "", false, copyFile, "Support Info File");
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file))
            return false;

        return getType(file) != null;
    }

    public static TYPE getType(File file) {
        try {
            for (TYPE t : TYPE.values()) {
                if (t._filename.equalsIgnoreCase(file.getName())) {
                    Log.d(TAG, "Match Support Info content: " + t);
                    return t;
                }
            }

            //Log.d(TAG, "Failed to match ATAK Support Info content");
            return null;
        } catch (Exception e) {
            Log.d(TAG, "Failed to match Support Info", e);
            return null;
        }
    }

    /**
     * Move to new location on same SD card Defer to TYPE for the relative path
     */
    @Override
    public File getDestinationPath(File file) {
        TYPE t = getType(file);
        if (t == null) {
            Log.e(TAG,
                    "Failed to match Support Info file: "
                            + file.getAbsolutePath());
            return null;
        }

        File folder = FileSystemUtils.isEmpty(t._folder)
                ? FileSystemUtils.getRoot()
                : FileSystemUtils.getItem(t._folder);
        return new File(folder, file.getName());
    }

    @Override
    protected void onFileSorted(File src, File dst, Set<SortFlags> flags) {
        super.onFileSorted(src, dst, flags);
    }
}
