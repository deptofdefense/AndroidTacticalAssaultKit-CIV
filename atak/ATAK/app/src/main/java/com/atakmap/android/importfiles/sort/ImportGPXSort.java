
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.util.Pair;

import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.GpxFileSpatialDb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Imports GPX Files
 * 
 * 
 */
public class ImportGPXSort extends ImportInPlaceResolver {

    private static final String TAG = "ImportGPXSort";

    private final static String GPXMATCH = "<gpx";

    public ImportGPXSort(Context context, boolean validateExt,
            boolean copyFile, boolean importInPlace) {
        super(".gpx", FileSystemUtils.OVERLAYS_DIRECTORY, validateExt,
                copyFile, importInPlace, context.getString(R.string.gpx_file),
                context.getDrawable(R.drawable.ic_gpx));
    }

    protected ImportGPXSort(boolean validateExt,
            boolean copyFile, boolean importInPlace, String name) {
        super(".gpx", FileSystemUtils.OVERLAYS_DIRECTORY, validateExt,
                copyFile, importInPlace, name);
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file))
            return false;

        // it is a .gpx, now lets see if it contains reasonable xml
        try (InputStream fis = IOProviderFactory.getInputStream(file)) {
            return isGpx(fis);
        } catch (IOException e) {
            Log.e(TAG, "Error checking if GPX: " + file.getAbsolutePath(), e);
        }

        return false;
    }

    private static boolean isGpx(InputStream stream) {
        try {
            // read first few hundred bytes and search for known GPX strings
            char[] buffer = new char[1024];
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    stream));
            int numRead = reader.read(buffer);
            reader.close();

            if (numRead < 1) {
                Log.d(TAG, "Failed to read .gpx stream");
                return false;
            }

            String content = String.valueOf(buffer, 0, numRead);
            boolean match = content.contains(GPXMATCH);
            if (!match) {
                Log.d(TAG, "Failed to match gpx content");
            }

            return match;
        } catch (Exception e) {
            Log.d(TAG, "Failed to match .gpx", e);
            return false;
        }
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(GpxFileSpatialDb.GPX_CONTENT_TYPE,
                GpxFileSpatialDb.GPX_FILE_MIME_TYPE);
    }
}
