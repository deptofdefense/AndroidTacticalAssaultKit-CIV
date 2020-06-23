
package com.atakmap.android.missionpackage.file;

import android.content.Context;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.IOException;

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
     * @param bImport   true to use Import Manager, false to simply extract contents from zip
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
        String manifestXml = GetManifestXml(zip);
        if (FileSystemUtils.isEmpty(manifestXml)) {
            Log.d(TAG,
                    "No manifest: PlainZipExtractor extracting "
                            + zip.getAbsolutePath());
            return new PlainZipExtractor();
        } else if (manifestXml.contains(MissionPackageExtractor.XML_MATCHER)
                && (manifestXml
                        .contains(MissionPackageExtractor.VERSION_MATCHER) ||
                        manifestXml.contains(
                                MissionPackageExtractor.VERSION_MATCHER_ALT))) {
            // TODO could regex to skip white space and do this in one comparison...
            Log.d(TAG,
                    "Current v2 manifest: MissionPackageExtractor extracting "
                            + zip.getAbsolutePath());
            return new MissionPackageExtractor();
        } else {
            // unknown/unsupported manifest, treat as "no manifest"
            Log.w(TAG,
                    "Unsupported manifest: PlainZipExtractor extracting "
                            + zip.getAbsolutePath());
            Log.w(TAG, manifestXml);
            return new PlainZipExtractor();
        }
    }

    /**
     * Check if the specified zip has a supported Mission Package Manifest
     * 
     * @param zip
     * @return
     */
    public static boolean HasManifest(File zip) {
        String manifestXml = GetManifestXml(zip);
        if (FileSystemUtils.isEmpty(manifestXml)) {
            // zip has no manifest
            return false;
        } else if (manifestXml.contains(MissionPackageExtractor.XML_MATCHER)
                && (manifestXml
                        .contains(MissionPackageExtractor.VERSION_MATCHER) ||
                        manifestXml.contains(
                                MissionPackageExtractor.VERSION_MATCHER_ALT))) {
            // TODO could regex to skip white space and do this in one comparison...
            Log.d(TAG,
                    "Current v2 manifest: MissionPackageExtractor matched "
                            + zip.getAbsolutePath());
            return true;
        } else {
            // unknown/unsupported manifest, treat as "no manifest"
            Log.w(TAG,
                    "Unsupported manifest, no match for "
                            + zip.getAbsolutePath());
            Log.w(TAG, manifestXml);
            return false;
        }
    }

    /**
     * Pull text context from Zip Entry: MANIFEST/manifest.xml
     *
     * @param zip
     * @return
     */
    public static String GetManifestXml(File zip) {
        return FileSystemUtils.GetZipFileString(zip,
                MissionPackageBuilder.MANIFEST_XML);
    }
}
