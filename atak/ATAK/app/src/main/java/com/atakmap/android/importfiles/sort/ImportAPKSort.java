
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.util.Pair;

import com.atakmap.android.update.AppMgmtUtils;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.Set;

/**
 * Sorts Android APK files, and initiates install
 * 
 * 
 */
public class ImportAPKSort extends ImportInternalSDResolver {

    private static final String TAG = "ImportAPKSort";
    private static final String MATCHER_FILE = "AndroidManifest.xml";

    private final Context _context;

    public ImportAPKSort(Context context, boolean validateExt) {
        super(".apk",
                FileSystemUtils.getItem(FileSystemUtils.TMP_DIRECTORY)
                        .getAbsolutePath(),
                validateExt, true,
                context.getString(R.string.android_apk_file),
                context.getDrawable(R.drawable.ic_android_display_settings));
        this._context = context;
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file))
            return false;

        // it is a .infz, now lets see if it has a product.inf
        boolean bMatch = isApk(file);
        Log.d(TAG, "APK " + (bMatch ? "found" : "not found"));
        return bMatch;
    }

    @Override
    protected void onFileSorted(File src, File dst, Set<SortFlags> flags) {
        super.onFileSorted(src, dst, flags);
        Log.d(TAG, "Sorted, now initiating repo sync");

        //kick off install
        //TODO add to FileSystemProductProvider?
        AppMgmtUtils.install(_context, dst);
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>("Android App",
                "application/vnd.android.package-archive");
    }

    /**
     * Check if the specified zip has an Android Manifest
     *
     * @param zip
     * @return
     */
    private static boolean isApk(File zip) {
        return FileSystemUtils.ZipHasFile(zip, MATCHER_FILE);
    }
}
