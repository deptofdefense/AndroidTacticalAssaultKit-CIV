
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.util.Pair;

import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.FalconViewSpatialDb;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Table;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Imports LPT files (local points from Falcon View) MS-Access based
 * 
 * 
 */
public class ImportLPTSort extends ImportInPlaceResolver {

    private static final String TAG = "ImportLPTSort";

    public ImportLPTSort(Context context, boolean validateExt,
            boolean copyFile, boolean importInPlace) {
        super(".lpt", FileSystemUtils.OVERLAYS_DIRECTORY, validateExt,
                copyFile, importInPlace, context.getString(R.string.lpt_file),
                context.getDrawable(R.drawable.ic_falconview_lpt));
    }

    @Override
    public boolean match(final File file) {
        if (!super.match(file))
            return false;

        // it is a .lpt, now lets see if it has LPT points
        return HasPoints(file);
    }

    /**
     * Search for a MS Access table "Points"
     * 
     * @param file
     * @return
     */
    private static boolean HasPoints(final File file) {

        if (file == null) {
            Log.e(TAG, "LPT file was null.");
            return false;
        }

        if (!IOProviderFactory.exists(file)) {
            Log.e(TAG, "LPT does not exist: " + file.getAbsolutePath());
            return false;
        }

        try (FileChannel chan = IOProviderFactory.getChannel(file, "r")) {
            Database msaccessDb = null;
            try {
                DatabaseBuilder db = new DatabaseBuilder();
                db.setChannel(chan);
                db.setReadOnly(true);
                msaccessDb = db.open();
            } catch (Exception e) {
                Log.d(TAG, "Error reading LPT file from disk: " + file, e);
                return false;
            }

            try {
                Table msaccessTable = msaccessDb.getTable("Points");
                return (msaccessTable != null
                        && msaccessTable.getRowCount() > 0);
            } catch (IOException e) {
                Log.d(TAG,
                        "Error parsing/reading MS Access table for LPT file: "
                                + file,
                        e);
                return false;
            }
        } catch (IOException e) {
            Log.d(TAG, "Error obtaining LPT file channel: "
                    + file, e);
            return false;
        }
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(FalconViewSpatialDb.LPT,
                FalconViewSpatialDb.MIME_TYPE);
    }
}
