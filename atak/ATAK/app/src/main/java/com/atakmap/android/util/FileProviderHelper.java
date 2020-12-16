
package com.atakmap.android.util;

import android.os.Build;
import androidx.core.content.FileProvider;
import com.atakmap.app.BuildConfig;
import android.content.Context;
import android.content.Intent;
import java.io.File;
import android.net.Uri;

/**
 * Required helper class to make use of API dependent changes with how files are 
 * passed by intent.   Prior to API level 24, Uri.fromFile(apk) was an acceptable 
 * way to for Intent::setDataAndType.   24 and higher now requires the use of the 
 * FileProvider.getUriForFile
 */

public class FileProviderHelper {

    /**
     * Required helper method that will given a file, correctly set the data
     * and type across the variety of versions ATAK supports.
     * @param context the context to use for the setDataAndType which is the 
     * application context.
     * @param intent intent to set the data and type on.
     * @param file the file to be passed by intent.
     * @param type the type of the file.
     */
    public static void setDataAndType(final Context context,
            final Intent intent, final File file, final String type) {
        intent.setDataAndType(fromFile(context, file), type);
        setReadAccess(intent);
    }

    /**
     * Required helper method that will given a file, correctly set the data
     * and type across the variety of versions ATAK supports.
     * @param context the context to use for the setDataAndType which is the 
     * application context.
     * @param file the file to be passed by intent.
     * @returns a Uri valid for the version of android being run on the device
     */
    public static Uri fromFile(final Context context, final File file) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return Uri.fromFile(file);
        } else {
            return FileProvider.getUriForFile(context,
                    BuildConfig.APPLICATION_ID + ".provider",
                    file);
        }
    }

    /** 
     * Required helper method that will set the appropriate access granting flags
     * on an intent that offers a file.
     */
    public static void setReadAccess(final Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.setFlags(
                    intent.getFlags() | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
    }
}
