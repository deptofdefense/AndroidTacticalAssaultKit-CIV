
package com.atakmap.android.missionpackage.http;

import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.http.datamodel.FileTransfer;

/**
 * Handle Mission Package processing on download
 */
public interface MissionPackageDownloadHandler {

    /**
     * Mission Package has been downloaded and is ready to be processed
     *
     * @param fileTransfer File transfer metadata
     *                     Note: this is missing several fields - only includes
     *                     what we get back from Commo
     * @param manifest Mission Package
     * @return True if handled, false to allow default processing
     */
    boolean onMissionPackageDownload(FileTransfer fileTransfer,
            MissionPackageManifest manifest);
}
