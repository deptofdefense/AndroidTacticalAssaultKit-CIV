
package com.atakmap.android.missionpackage.event;

import com.atakmap.android.importfiles.sort.ImportResolver;
import com.atakmap.android.missionpackage.file.MissionPackageContent;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.ui.MissionPackageListFileItem;
import com.atakmap.android.missionpackage.ui.MissionPackageListGroup;
import com.atakmap.util.zip.ZipFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Handler for Mission Package being processed
 * 
 * 
 */
public interface IMissionPackageEventHandler {

    /**
     * If file is supported, add to group/manifest. Does not actually update the zip
     * 
     * @param group
     * @param file
     * @return true if event is consumed
     */
    boolean add(MissionPackageListGroup group, File file);

    /**
     * If file is supported, remove from group/manifest. Does not actually update the zip
     * 
     * @param group
     * @param file
     * @return true if event is consumed
     */
    boolean remove(MissionPackageListGroup group,
            MissionPackageListFileItem file);

    /**
     * If content is supported, extract it from the zip
     * 
     * @param manifest
     * @param content
     * @param zipFile
     * @param atakDataDir
     * @param buffer
     * @param sorters
     * @return true if event is consumed
     */
    boolean extract(MissionPackageManifest manifest,
            MissionPackageContent content, ZipFile zipFile,
            File atakDataDir, byte[] buffer, List<ImportResolver> sorters)
            throws IOException;
}
