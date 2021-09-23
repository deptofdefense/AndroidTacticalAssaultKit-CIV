
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.util.Pair;

import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.KmlFileSpatialDb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * Imports KML Files
 * 
 * 
 */
public class ImportKMLSort extends ImportInPlaceResolver {

    private static final String TAG = "ImportKMLSort";

    private final static String KMLMATCH = "<kml";
    private final static String KMLMATCH_REGEX_WITHNS = "(?s).*<[^>]+:kml.*";

    private final Context _context;

    public ImportKMLSort(Context context, boolean validateExt,
            boolean copyFile, boolean importInPlace) {
        super(".kml", FileSystemUtils.OVERLAYS_DIRECTORY, validateExt,
                copyFile, importInPlace, context.getString(R.string.kml_file),
                context.getDrawable(R.drawable.ic_kml));

        _context = context;
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file))
            return false;

        // it is a .kml, now lets see if it contains reasonable xml
        try (FileInputStream fis = IOProviderFactory.getInputStream(file)) {
            return isKml(fis);
        } catch (IOException e) {
            Log.e(TAG, "Error checking if KML: " + file.getAbsolutePath(), e);
        }

        return false;
    }

    static boolean isKml(InputStream stream) {
        try {
            // read first few hundred bytes and search for known KML strings
            char[] buffer = new char[1024];
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    stream));
            int numRead = reader.read(buffer);
            reader.close();

            if (numRead < 1) {
                Log.d(TAG, "Failed to read .kml stream");
                return false;
            }

            String content = String.valueOf(buffer, 0, numRead);
            boolean match = content.contains(KMLMATCH)
                    || content.matches(KMLMATCH_REGEX_WITHNS);
            if (!match) {
                Log.d(TAG, "Failed to match kml content");
            }

            return match;
        } catch (Exception e) {
            Log.d(TAG, "Failed to match .kml", e);
            return false;
        }
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(KmlFileSpatialDb.KML_CONTENT_TYPE,
                KmlFileSpatialDb.KML_FILE_MIME_TYPE);
    }
}
