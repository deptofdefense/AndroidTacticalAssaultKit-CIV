
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.util.Pair;

import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.ShapefileSpatialDb;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Imports Shapefiles
 * 
 * 
 */
public class ImportSHPSort extends ImportInPlaceResolver {

    private static final String TAG = "ImportSHPSort";

    private static final int MAGIC_NUMBER = 9994;

    public ImportSHPSort(Context context, boolean validateExt,
            boolean copyFile, boolean importInPlace) {
        super(".shp", FileSystemUtils.OVERLAYS_DIRECTORY, validateExt,
                copyFile, importInPlace, context.getString(R.string.shapefile),
                context.getDrawable(R.drawable.ic_shapefile));
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file)) {
            Log.d(TAG, "No match: " + file.getAbsolutePath());
            return false;
        }

        // it is a .shp, now lets see if it contains reasonable data
        try (InputStream is = IOProviderFactory.getInputStream(file)) {
            boolean b = isShp(is);
            Log.d(TAG, (b ? "Matched Shapefile: " + file.getAbsolutePath()
                    : "Not a Shapefile: "
                            + file.getAbsolutePath()));
            return b;
        } catch (IOException e) {
            Log.e(TAG, "Error checking if SHP: " + file.getAbsolutePath(), e);
        }

        return false;
    }

    static boolean isShp(InputStream in) {
        try {
            // first field (4 bytes) should be a known value
            int ch1 = in.read();
            int ch2 = in.read();
            int ch3 = in.read();
            int ch4 = in.read();
            if ((ch1 | ch2 | ch3 | ch4) < 0) {
                Log.d(TAG, "Failed to read 4 byte value");
                return false;
            }

            int value = ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4));
            if (value == MAGIC_NUMBER) {
                return true;
            } else {
                Log.d(TAG, "Magic number mismatch: " + value);
                return false;
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to match .shp", e);
            return false;
        }
    }

    /**
     * Override parent to copy all related shapefiles Namely, all files in the source directory
     * which have the same file name (minus extension)
     * 
     * @param file the file to attempt to import
     * @return true if the file is imported by this sorter.
     */
    @Override
    public boolean beginImport(File file) {
        return beginImport(file, Collections.<SortFlags> emptySet());
    }

    @Override
    public boolean beginImport(File file, Set<SortFlags> flags) {
        if (_bImportInPlace) {
            onFileSorted(file, file, flags);
            return true;
        }

        //otherwise copy all related files
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

        // copy/move all the physical files for this shapefile
        File sourceParent = file.getParentFile();
        if (sourceParent == null) {
            Log.w(TAG, "Source has no parent file: " + file.getAbsolutePath());
            return false;
        }

        List<File> files = GetShapefileMemberFiles(file);
        if (files == null || files.size() < 1) {
            Log.w(TAG,
                    "Unable to locate any relevant files for file: "
                            + file.getAbsolutePath());
            return false;
        }

        // now loop all files that matched the dataset name filter
        for (File curFile : files) {
            Log.d(TAG, "Moving file: " + curFile.getAbsolutePath());
            File curDest = new File(destParent, curFile.getName());

            // Note we attempt to place new file on same SD card, so copying should be minimal
            if (_bCopyFile)
                try {
                    FileSystemUtils.copyFile(curFile, curDest);
                } catch (IOException e) {
                    Log.e(TAG,
                            "Failed to copy file: " + curDest.getAbsolutePath(),
                            e);
                    return false;
                }
            else {
                if (!FileSystemUtils.renameTo(curFile, curDest)) {
                    return false;
                }
            }
        }

        // now send intent to start the import
        this.onFileSorted(file, new File(destParent, file.getName()), flags);

        return true;
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(ShapefileSpatialDb.SHP_CONTENT_TYPE,
                ShapefileSpatialDb.SHP_FILE_MIME_TYPE);
    }

    /**
     * Get logically related files in the same directory as the specified Shapefile i.e. those that
     * share the same base filename (minus extension)
     * 
     * @param file the file to get the member files from
     * @return the list of member files.
     */
    public static List<File> GetShapefileMemberFiles(File file) {
        List<File> files = new ArrayList<>();

        File parent = file.getParentFile();
        if (!FileSystemUtils.isFile(parent)) {
            Log.w(TAG,
                    "Unable to determine parent for file: "
                            + file.getAbsolutePath());
            return null;
        }

        final String dataSetName = FileSystemUtils.stripExtension(file
                .getName());
        if (FileSystemUtils.isEmpty(dataSetName)) {
            Log.w(TAG,
                    "Unable to determine dataset name for file: "
                            + file.getAbsolutePath());
            return null;
        }

        FilenameFilter shpFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                if (FileSystemUtils.isEmpty(filename))
                    return false;

                String curDataSetName = FileSystemUtils
                        .stripExtension(filename);
                return dataSetName.equalsIgnoreCase(curDataSetName);
            }
        };

        File[] listFiles = IOProviderFactory.listFiles(parent, shpFilter);
        if (listFiles != null && listFiles.length > 0) {
            files.addAll(new ArrayList<>(Arrays.asList(listFiles)));
        }

        // also look for <dataset>.shp.xml
        File xmlFile = new File(parent, file.getName() + ".xml");
        if (FileSystemUtils.isFile(xmlFile)) {
            files.add(xmlFile);
        }

        return files;
    }
}
