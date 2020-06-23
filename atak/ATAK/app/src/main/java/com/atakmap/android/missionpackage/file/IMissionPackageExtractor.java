
package com.atakmap.android.missionpackage.file;

import android.content.Context;

import java.io.File;
import java.io.IOException;

/**
 * Interface for all Mission Package extractors
 * 
 * 
 */
public interface IMissionPackageExtractor {

    /**
     * Extract contents from the specified Zip File
     * 
     * @param context the context for the application
     * @param zipFile the zip file to unzip
     * @param atakRoot where to unzip data and files
     * @param bImport, true to import, false to simply unzip
     * @return the manifest object
     * @throws IOException
     */
    MissionPackageManifest extract(Context context,
            File zipFile, File atakRoot, boolean bImport)
            throws IOException;

    /**
     * Extract manifest from the specified Zip File
     *
     * @param zipFile
     * @return
     */
    MissionPackageManifest getManifest(File zipFile);
}
