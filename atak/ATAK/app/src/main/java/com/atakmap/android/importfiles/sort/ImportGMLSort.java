
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.util.Pair;

import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.GMLSpatialDb;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Set;

/**
 * Imports Shapefiles
 * 
 * 
 */
public class ImportGMLSort extends ImportInPlaceResolver {

    private static final String TAG = "ImportGMLSort";

    private final static String GMLMATCH = "<gml";

    public ImportGMLSort(Context context, boolean validateExt,
            boolean copyFile, boolean importInPlace) {
        super(".gml", FileSystemUtils.OVERLAYS_DIRECTORY, validateExt,
                copyFile, importInPlace, "GML",
                context.getDrawable(R.drawable.ic_shapefile));
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file)) {
            Log.d(TAG, "No match: " + file.getAbsolutePath());
            return false;
        }

        try (InputStream fis = IOProviderFactory.getInputStream(file)) {
            return isGML(fis);
        } catch (IOException e) {
            Log.e(TAG, "Error checking if GPX: " + file.getAbsolutePath(), e);
        }

        return false;
    }

    private static boolean isGML(InputStream stream) {
        try {
            // read first few hundred bytes and search for known GML strings
            char[] buffer = new char[2048];
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    stream));
            int numRead = reader.read(buffer);
            reader.close();

            if (numRead < 1) {
                Log.d(TAG, "Failed to read .gml stream");
                return false;
            }

            String content = String.valueOf(buffer, 0, numRead);
            boolean match = content.contains(GMLMATCH);
            if (!match) {
                Log.d(TAG, "Failed to match gml content");
            }

            return match;
        } catch (Exception e) {
            Log.d(TAG, "Failed to match .gml", e);
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

        // now send intent to start the import
        this.onFileSorted(file, new File(destParent, file.getName()), flags);

        return true;
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(GMLSpatialDb.GML_CONTENT_TYPE,
                GMLSpatialDb.GML_FILE_MIME_TYPE);
    }

}
