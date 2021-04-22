
package com.atakmap.coremap.filesystem;

import android.content.Context;
import android.os.Build;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RemovableStorageHelper {

    private static Context context;
    private static final String TAG = "RemovableStorageHelper";

    public static void init(Context ctx) {
        context = ctx;
    }

    /**
     * Returns the actual removable storage directory as provided by utilizing the cached directory
     *
     * @return the value of "ANDROID_STORAGE" as an environment variable, otherwise if on Android 11
     * returns the appropriate removable storage directories.
     */
    public static String[] getRemovableStorageDirectory() {

        final String android_storage = System.getenv("ANDROID_STORAGE");

        final List<String> mount_points = new ArrayList<>();

        // Android 11 change - "ANDROID_STORAGE" is no longer readable
        // utilize a very narrow scoped change to allow for backporting to 4.2.1
        // already released
        if (Build.VERSION.SDK_INT > 29) {

            if (context == null) {
                Log.d(TAG, "RemovableStorageHelper not initialized, returning "
                        + android_storage);
                return new String[] {
                        android_storage
                };
            }
            final File[] folders = context.getExternalCacheDirs();
            for (File f : folders) {
                try {
                    final String path = f.getCanonicalPath();
                    if (path.startsWith(android_storage)
                            && !path.contains("emulated/0")) {
                        try {
                            String[] pathSplit = path.split("/");
                            String sdcardMntPt = pathSplit[1] + "/"
                                    + pathSplit[2] + "/";
                            mount_points.add(sdcardMntPt);
                        } catch (Exception e) {
                            Log.e(TAG, "error finding removable sdcard", e);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "error finding: " + f);
                }
            }

        } else {
            File f = new File(android_storage);
            if (IOProviderFactory.exists(f) && IOProviderFactory.isDirectory(f)
                    && IOProviderFactory.canRead(f)) {
                File[] subdirs = IOProviderFactory.listFiles(f);
                for (File subdir : subdirs) {
                    try {
                        String path = subdir.getCanonicalPath();
                        if (!path.contains("emulated/0"))
                            mount_points.add(path);
                    } catch (Exception e) {
                        Log.e(TAG, "error finding: " + subdir);
                    }
                }
            }

        }
        if (mount_points.isEmpty())
            mount_points.add(android_storage);

        return mount_points.toArray(new String[0]);
    }
}
