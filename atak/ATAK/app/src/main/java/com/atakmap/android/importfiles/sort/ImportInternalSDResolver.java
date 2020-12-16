
package com.atakmap.android.importfiles.sort;

import android.graphics.drawable.Drawable;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * Import Resolver that sorts (copies or moves) file prior to import
 * 
 * 
 */
public abstract class ImportInternalSDResolver extends ImportResolver {

    private static final String TAG = "ImportInternalSDResolver";

    private final String displayName;
    private final Drawable icon;

    public ImportInternalSDResolver(String ext, String folderName,
            boolean validateExt, boolean copyFile,
            String displayName, Drawable icon) {
        super(ext, folderName, validateExt, copyFile);
        this.displayName = displayName;
        this.icon = icon;
    }

    public ImportInternalSDResolver(String ext, String folderName,
            boolean validateExt,
            boolean copyFile,
            String displayName) {
        this(ext, folderName, validateExt, copyFile, displayName, null);
    }

    /**
     * Move to new location in default ATAK data directory
     * 
     * @param file
     * @return
     */
    @Override
    public File getDestinationPath(File file) {
        File folder = FileSystemUtils.getItem(_folderName);

        // set if file extension, if applicable
        String fileName = file.getName();
        if (!FileSystemUtils.isEmpty(getExt())
                && !fileName.endsWith(getExt())) {
            Log.d(TAG, "Added extension to destination path: " + fileName);
            fileName += getExt();
        }

        return new File(folder, fileName);
    }

    /**
     * Copies or moves file based on configuration
     */
    @Override
    public boolean beginImport(File file) {
        return beginImport(file, Collections.<SortFlags> emptySet());
    }

    @Override
    public boolean beginImport(File file, Set<SortFlags> flags) {
        File dest = getDestinationPath(file);
        if (dest == null) {
            Log.w(TAG,
                    "Failed to find SD card root for: "
                            + file.getAbsolutePath());
            return false;
        }

        File destParent = dest.getParentFile();
        if (destParent == null) {
            Log.w(TAG,
                    "Destination has no parent file: "
                            + dest.getAbsolutePath());
            return false;
        }

        if (!IOProviderFactory.exists(destParent)) {
            if (!IOProviderFactory.mkdirs(destParent)) {
                Log.w(TAG,
                        "Failed to create directory: "
                                + destParent.getAbsolutePath());
                return false;
            } else {
                Log.d(TAG,
                        "Created directory: " + destParent.getAbsolutePath());
            }
        }

        // Note we attempt to place new file on same SD card, so copying should be minimal
        if (_bCopyFile)
            try {
                FileSystemUtils.copyFile(file, dest);
                this.onFileSorted(file, dest, flags);
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Failed to copy file: " + dest.getAbsolutePath(), e);
                return false;
            }
        else {
            final boolean retval = FileSystemUtils.renameTo(file, dest);
            if (retval)
                this.onFileSorted(file, dest, flags);
            return retval;
        }
    }

    @Override
    public String getDisplayableName() {
        return this.displayName;
    }

    @Override
    public Drawable getIcon() {
        return this.icon != null ? this.icon : super.getIcon();
    }
}
