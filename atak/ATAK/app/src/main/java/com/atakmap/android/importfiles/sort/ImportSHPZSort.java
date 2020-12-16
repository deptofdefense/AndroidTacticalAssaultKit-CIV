
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.util.Pair;

import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.ShapefileSpatialDb;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;

import java.io.File;
import java.io.InputStream;
import java.util.Enumeration;

/**
 * Imports archived Shapefiles
 * 
 * 
 */
public class ImportSHPZSort extends ImportInPlaceResolver {

    private static final String TAG = "ImportSHPZSort";

    private final Context _context;

    public ImportSHPZSort(Context context, boolean validateExt,
            boolean copyFile, boolean importInPlace) {
        super(".zip", FileSystemUtils.OVERLAYS_DIRECTORY, validateExt,
                copyFile, importInPlace,
                context.getString(R.string.zipped_shapefile),
                context.getDrawable(R.drawable.ic_shapefile));
        _context = context;
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file))
            return false;

        // it is a .zip, now lets see if it contains a .shp
        return HasSHP(file);
    }

    /**
     * Search for a zip entry ending in .shp .shx .dbf
     * 
     * @param file
     * @return
     */
    public static boolean HasSHP(File file) {
        if (file == null) {
            Log.d(TAG, "ZIP file points to null.");
            return false;
        }

        if (!IOProviderFactory.exists(file)) {
            Log.d(TAG, "ZIP does not exist: " + file.getAbsolutePath());
            return false;
        }

        ZipFile zip = null;
        try {
            zip = new ZipFile(file);

            boolean bSHP = false, bSHX = false, bDBF = false;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                if (ze.getName().toLowerCase(LocaleUtil.getCurrent())
                        .endsWith(".shp")) {
                    final InputStream zis = zip.getInputStream(ze);
                    try {
                        if (ImportSHPSort.isShp(zis)) {
                            bSHP = true;
                        } else {
                            Log.w(TAG,
                                    "Found invalid archived SHP file: "
                                            + ze.getName());
                        }
                    } finally {
                        zis.close();
                    }
                } else if (ze.getName().toLowerCase(LocaleUtil.getCurrent())
                        .endsWith(".shx")) {
                    bSHX = true;
                } else if (ze.getName().toLowerCase(LocaleUtil.getCurrent())
                        .endsWith(".dbf")) {
                    bDBF = true;
                }

                if (bSHP && bSHX && bDBF) {
                    // found what we needed, quit looping
                    break;
                }
            }

            if (!bSHP || !bSHX || !bDBF) {
                Log.w(TAG,
                        "Invalid archived Shapefile: "
                                + file.getAbsolutePath());
                return false;
            }

            Log.d(TAG, "Matched archived Shapfile: " + file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.d(TAG,
                    "Failed to find SHP content in: " + file.getAbsolutePath(),
                    e);
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (Exception e) {
                    Log.e(TAG,
                            "Failed closing archived Shapefile: "
                                    + file.getAbsolutePath(),
                            e);
                }
            }
        }

        return false;
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(ShapefileSpatialDb.SHP_CONTENT_TYPE,
                ShapefileSpatialDb.SHP_FILE_MIME_TYPE);
    }
}
