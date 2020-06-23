
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.util.Pair;

import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.DrwFileDatabase;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Table;

import java.io.File;
import java.io.IOException;

/**
 * Imports DRW files (local points from Falcon View) MS-Access based
 * 
 * 
 */
public class ImportDRWSort extends ImportInPlaceResolver {

    private static final String TAG = "ImportDRWSort";

    public ImportDRWSort(Context context, boolean validateExt,
            boolean copyFile, boolean importInPlace) {
        super(".drw", FileSystemUtils.OVERLAYS_DIRECTORY, validateExt,
                copyFile, importInPlace, context.getString(R.string.drw_file));
    }

    @Override
    public boolean match(final File file) {
        if (!super.match(file))
            return false;

        // it is a .drw, now lets see if it has DRW shapes
        return HasDrawing(file);
    }

    /**
     * Search for a MS Access table "Points"
     * 
     * @param file
     * @return
     */
    private static boolean HasDrawing(final File file) {

        if (file == null || !file.exists()) {
            Log.e(TAG,
                    "DRW does not exist: "
                            + (file == null ? "null" : file.getAbsolutePath()));
            return false;
        }

        Database msaccessDb;
        try {
            DatabaseBuilder db = new DatabaseBuilder();
            db.setFile(file);
            db.setReadOnly(true);
            msaccessDb = db.open();
        } catch (Exception e) {
            Log.d(TAG, "Error reading DRW file from disk: " + file, e);
            return false;
        }

        try {
            Table msaccessTable = msaccessDb.getTable("Main");
            return (msaccessTable != null && msaccessTable.getRowCount() > 0);
        } catch (IOException e) {
            Log.d(TAG, "Error parsing/reading MS Access table for DRW file: "
                    + file, e);
            return false;
        }
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(DrwFileDatabase.DRW_CONTENT_TYPE,
                DrwFileDatabase.DRW_FILE_MIME_TYPE);
    }
}
