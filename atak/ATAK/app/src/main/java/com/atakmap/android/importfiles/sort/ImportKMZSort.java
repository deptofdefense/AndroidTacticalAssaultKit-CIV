
package com.atakmap.android.importfiles.sort;

import android.content.Context;
import android.util.Pair;

import com.atakmap.android.layers.kmz.KMZPackageImporter;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.gdal.VSIFileFileSystemHandler;
import com.atakmap.spatial.file.KmlFileSpatialDb;

import org.gdal.ogr.DataSource;
import org.gdal.ogr.Driver;
import org.gdal.ogr.ogr;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.util.zip.ZipEntry;

import java.util.List;
import java.util.zip.ZipException;
import com.atakmap.util.zip.ZipFile;

/**
 * Imports KMZ files, per https://developers.google.com/kml/documentation/kmzarchives "For clarity,
 * this page refers to the main KML file within a KMZ archive as doc.kml. This main KML file can
 * have any name, as long as it ends in .kml, and as long as there is only one .kml file."
 * 
 * 
 */
public class ImportKMZSort extends ImportInPlaceResolver {

    private static final String TAG = "ImportKMZSort";

    /**
     * Tricky trade off between matching a KMZ (which contains KML files(s)) and a Mission Package
     * (which may contains KML files). If bStrict is true then we currently require a single
     * "doc.kml" in the top level folder and no other KML files. If it is false than any "*.kml"
     * will be a match during sorting. This particularly comes into play when importing files via
     * the Import Manager Remote Resource where the user label and URL may not allow us to imply the
     * file type. Note, KMZ is actually a ZIP file.
     */
    private final boolean _bStrict;

    public ImportKMZSort(Context context, boolean validateExt,
            boolean copyFile, boolean importInPlace, boolean bStrict) {
        super(".kmz", FileSystemUtils.OVERLAYS_DIRECTORY, validateExt,
                copyFile, importInPlace, context.getString(R.string.kmz_file),
                context.getDrawable(R.drawable.ic_kml));
        _bStrict = bStrict;
    }

    @Override
    public boolean match(File file) {
        if (!super.match(file))
            return false;

        // it is a .kmz, now lets see if it contains a .kml
        if (!hasKML(file, _bStrict)) {
            return false;
        }

        // Skip KMZ files with no vector elements
        List<String> contentTypes = KMZPackageImporter.getContentTypes(file);
        if (!contentTypes.contains(KmlFileSpatialDb.KML_CONTENT_TYPE)) {
            Log.d(TAG, "Skipping GRG KMZ: " + file.getAbsolutePath());
            return false;
        }

        // The KmlFileSpatialDb will try and open the file
        // using this same method, and can fail it the file
        // doesn't have the right structure. Check it now so that
        // files that can't be opened by the KmlFileSpatialDb
        // don't match as KMZ files.
        DataSource dataSource = null;
        try {
            String path = file.getAbsolutePath();
            if (!IOProviderFactory.isDefault())
                path = VSIFileFileSystemHandler.PREFIX + path;
            else if (file instanceof ZipVirtualFile)
                path = "/vsizip" + path;
            else if (!IOProviderFactory.isDefault())
                path = VSIFileFileSystemHandler.PREFIX + path;

            dataSource = ogr.Open(path, false);
            if (dataSource == null)
                return false;
            Driver driver = dataSource.GetDriver();
            if (driver == null)
                return false;
            String desc = driver.GetName();
            if (desc == null)
                return false;
            desc = desc.toLowerCase(LocaleUtil.US);
            return desc.contains("kml");
        } catch (Throwable t) {
            Log.e(TAG, "Error occurred testing KMZ file", t);
            return false;
        } finally {
            if (dataSource != null)
                dataSource.delete();
        }
    }

    /**
     * Search for a zip entry ending in .kml
     * 
     * @param file
     * @param bStrict If in strict mode (e.g. not validating the KMZ extension) then require a
     *            single doc.kml in top level zip folder (no other KML files included in the zip
     *            file)
     * @return
     */
    private static boolean hasKML(File file, boolean bStrict) {
        if (file == null) {
            Log.d(TAG, "KMZ file passed in was null.");
            return false;
        }

        if (!IOProviderFactory.exists(file)) {
            Log.d(TAG, "KMZ does not exist: " + file.getAbsolutePath());
            return false;
        }

        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(file);

            ArrayList<? extends ZipEntry> arZipEnt = Collections
                    .list(zipFile.entries());

            if (bStrict) {

                // require a single doc.kml in the top level zip folder (no other KML files)
                ZipEntry zeDoc = null;
                boolean bOtherKml = false;

                for (ZipEntry ze : arZipEnt) {
                    if (ze.getName().equalsIgnoreCase("doc.kml")) {
                        if (zeDoc != null) {
                            Log.d(TAG,
                                    "(strict) Found more than one doc.kml in "
                                            + file.getAbsolutePath());
                            return false;
                        }
                        zeDoc = ze;
                    } else if (ze.getName()
                            .toLowerCase(LocaleUtil.getCurrent())
                            .endsWith(".kml")) {
                        bOtherKml = true;
                    }
                }

                if (zeDoc == null) {
                    Log.d(TAG,
                            "(strict) KMZ does not contain doc.kml: "
                                    + file.getAbsolutePath());
                    return false;
                }

                if (bOtherKml) {
                    Log.d(TAG, "(strict) KMZ found multiple KML files in: "
                            + file.getAbsolutePath());
                    return false;
                }

                Log.d(TAG,
                        "(strict) Found a single doc.kml entry in: "
                                + file.getAbsolutePath());

                InputStream is = null;
                try {
                    return ImportKMLSort
                            .isKml(is = zipFile.getInputStream(zeDoc));
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            } else {

                // any old KML will do...
                for (ZipEntry ze : arZipEnt) {
                    if (ze.getName().toLowerCase(LocaleUtil.getCurrent())
                            .endsWith(".kml")) {
                        // Found KML file in KMZ, ensure it is well formed
                        Log.d(TAG, "Found KMZ entry: " + ze.getName());
                        InputStream zis = zipFile.getInputStream(ze);
                        boolean val = ImportKMLSort.isKml(zis);
                        zis.close();
                        return val;
                    }
                }
            }
        } catch (ZipException e) {
            Log.d(TAG,
                    "ZipException testing for KML compatibility: "
                            + file.getAbsolutePath(),
                    e);
            // XXX - ATAK-8959
            // Some zip files may raise exceptions via the Java ZIP API but can
            // still be successfully opened via GDAL. we'll return true from
            // here to promote better interoperability as an immediate sanity
            // check for GDAL loading is performed afterwards
            return true;
        } catch (Exception e) {
            Log.d(TAG,
                    "Failed to find KMZ content in: " + file.getAbsolutePath(),
                    e);
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    Log.e(TAG,
                            "Failed to close zip file: "
                                    + file.getAbsolutePath(),
                            e);
                }
            }
        }

        return false;
    }

    @Override
    public Pair<String, String> getContentMIME() {
        return new Pair<>(KmlFileSpatialDb.KML_CONTENT_TYPE,
                KmlFileSpatialDb.KMZ_FILE_MIME_TYPE);
    }
}
