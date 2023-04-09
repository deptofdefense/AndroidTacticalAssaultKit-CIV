
package com.atakmap.android.missionpackage.file;

import android.content.Context;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;

/**
 * Creates and invokes appropriate Extractor
 * 
 * 
 */
public class MissionPackageExtractorFactory {

    private static final String TAG = "MissionPackageExtractorFactory";


    /**
     * Extract manifeset from the specified Zip File
     *
     * @param zip
     * @return
     */
    public static MissionPackageManifest GetManifest(File zip) {
        IMissionPackageExtractor extractor = GetExtractor(zip);
        return extractor.getManifest(zip);
    }

    /**
     * Extract contents of the specified Zip File
     *
     * @param context
     * @param zip
     * @param atakRoot
     * @param bImport  true to use Import Manager, false to simply extract contents from zip
     * @return
     */
    public static MissionPackageManifest Extract(Context context, File zip,
                                                 File atakRoot, boolean bImport) {
        try {
            IMissionPackageExtractor extractor = GetExtractor(zip);
            return extractor.extract(context, zip, atakRoot, bImport);
        } catch (IOException e) {
            Log.e(TAG, "Failed to Extract: " + zip.getAbsolutePath());
            return null;
        }
    }

    /**
     * Get Extractor for the specified Zip
     *
     * @param zip
     * @return
     */
    public static IMissionPackageExtractor GetExtractor(File zip) {
        if (HasManifest(zip))
            return new MissionPackageExtractor();
        else
            return new PlainZipExtractor();
    }

    /**
     * Check if the specified zip has a supported Mission Package Manifest
     *
     * @param zip
     * @return
     */
    public static boolean HasManifest(File zip) {
        return getManifest(zip) != null;
    }

    /**
     * Pull text context from Zip Entry: MANIFEST/manifest.xml
     * Please note - this is likely broken, please @see getManifest()
     * @param zip the datapackage
     * @return the manifest contents as a String
     * @deprecated
     */
    @Deprecated
    @DeprecatedApi(since = "4.6", forRemoval = true, removeAt = "5.0")
    public static String GetManifestXml(File zip) {
        MissionPackageManifest manifest = getManifest(zip);
        if (manifest == null)
            return null;

        return manifest.toXml(true);
    }

    /**
     * More robust implementation of GetManifest that looks to make sure that the
     * base directory is set.
     * @param zip the data package
     * @return the manifest for the datapackage or null
     */
    public static MissionPackageManifest getManifest(File zip) {
        if (!FileSystemUtils.isFile(zip)) {
            Log.e(TAG, "Zip does not exist: " + zip.getAbsolutePath());
            return null;
        }
        String manifestXml = null;
        String manifestXmlLocation = MissionPackageBuilder.MANIFEST_XML;
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(zip);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements() && manifestXml == null) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(manifestXmlLocation)) {
                    Log.d(TAG, "found mission package manifest: " + entry);
                    manifestXmlLocation = entry.getName();

                    char[] charBuffer = new char[FileSystemUtils.CHARBUFFERSIZE];
                    manifestXml = FileSystemUtils.copyStreamToString(
                            zipFile.getInputStream(entry), true,
                            FileSystemUtils.UTF8_CHARSET, charBuffer);
                }
            }
        } catch (Exception e) {
        } finally {
            if (zipFile != null)
                try {
                    zipFile.close();
                } catch (Exception e) {}
        }
        if (FileSystemUtils.isEmpty(manifestXml)) {
            Log.e(TAG, "Manifest does not exist: " + zip.getAbsolutePath());
            return null;
        }

        try {
            MissionPackageManifest manifest = MissionPackageManifest.fromXml(
                    manifestXml,
                    zip.getAbsolutePath());
            manifest.setManifestLocation(manifestXmlLocation);

            if (manifest == null || !manifest.isValid()) {
                Log.e(TAG, "Failed to de-serialize manifest");
                return null;
            }

            Log.d(TAG, "valid manifest found in: " + zip);
            return manifest;

        } catch (Exception e) {
            Log.e(TAG, "Failed to get manifest for: " + zip.getAbsolutePath(),
                    e);
        }

        return null;

    }

}
