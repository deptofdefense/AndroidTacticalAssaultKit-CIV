
package com.atakmap.android.missionpackage.event;

import android.content.Context;
import android.util.Pair;
import android.widget.Toast;

import com.atakmap.android.importfiles.sort.ImportResolver;
import com.atakmap.android.importfiles.sort.ImportResolver.SortFlags;
import com.atakmap.android.missionpackage.file.MissionPackageContent;
import com.atakmap.android.missionpackage.file.MissionPackageExtractor;
import com.atakmap.android.missionpackage.file.MissionPackageFileIO;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.MissionPackageManifestAdapter;
import com.atakmap.android.missionpackage.file.NameValuePair;
import com.atakmap.android.missionpackage.ui.MissionPackageListFileItem;
import com.atakmap.android.missionpackage.ui.MissionPackageListGroup;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Base handler for Mission Packages being processed
 * 
 * 
 */
public class MissionPackageEventHandler implements IMissionPackageEventHandler {

    private static final String TAG = "MissionPackageEventHandler";

    protected final Context _context;

    public MissionPackageEventHandler(Context context) {
        _context = context;
    }

    @Override
    public boolean add(MissionPackageListGroup group, File file) {

        MissionPackageContent content = MissionPackageManifestAdapter
                .FileToContent(file, null);
        if (content == null || !content.isValid()) {
            Log.w(TAG, "Failed to adapt file path to Mission Package Content");
            return false;
        }

        if (group.getManifest().hasFile(content)) {
            Log.i(TAG,
                    group + " already contains filename: "
                            + file.getName());
            Toast.makeText(_context, _context.getString(
                    R.string.mission_package_already_contains_file,
                    _context.getString(R.string.mission_package_name),
                    file.getName()),
                    Toast.LENGTH_LONG).show();
            return false;
        }

        // add file to package
        Log.d(TAG, "Adding file: " + content);
        return group.addFile(content, file);
    }

    @Override
    public boolean remove(MissionPackageListGroup group,
            MissionPackageListFileItem item) {
        Log.d(TAG, "Removing file: " + item.toString());
        return group.removeFile(item);
    }

    @Override
    public boolean extract(MissionPackageManifest manifest,
            MissionPackageContent content, ZipFile zipFile,
            File atakDataDir, byte[] buffer, List<ImportResolver> sorters)
            throws IOException {

        ZipEntry entry = zipFile.getEntry(content.getManifestUid());
        if (entry == null) {
            throw new IOException("Package does not contain manifest content: "
                    + content.getManifestUid());
        }

        // unzip Mission Package file
        Log.d(TAG, "Exracting file: " + content);
        String parent = FileSystemUtils
                .sanitizeWithSpacesAndSlashes(MissionPackageFileIO
                        .getMissionPackageFilesPath(atakDataDir
                                .getAbsolutePath())
                        + File.separatorChar
                        + manifest.getUID());
        File toUnzip = new File(parent, FileSystemUtils
                .sanitizeWithSpacesAndSlashes(content.getManifestUid()));
        MissionPackageExtractor.UnzipFile(zipFile.getInputStream(entry),
                toUnzip, false, buffer);

        // Content type metadata
        String contentType = content.getParameterValue(
                MissionPackageContent.PARAMETER_CONTENT_TYPE);

        // Whether this content is visible by default
        boolean hidden = FileSystemUtils.isEquals(content.getParameterValue(
                MissionPackageContent.PARAMETER_VISIBLE), "false");

        // Sort flags
        Set<SortFlags> flags = hidden
                ? Collections.singleton(SortFlags.HIDE_FILE)
                : Collections.emptySet();

        // attempt import using ImportManager
        String filePath = importFile(toUnzip, contentType, flags, sorters);
        if (!FileSystemUtils.isEmpty(filePath)) {
            Log.d(TAG, "Imported Supported File: " + filePath);
        } else {
            // if no importer, than file is already in correct location!
            filePath = toUnzip.getAbsolutePath();
            Log.d(TAG, "Extracted External File: " + filePath);
        }

        // build out "local" manifest using localpath
        content.setParameter(new NameValuePair(
                MissionPackageContent.PARAMETER_LOCALPATH, filePath));
        return true;
    }

    /**
     * This method based on ImportFileTask.sort()
     * 
     * @param file File to import
     * @param contentType File content type
     * @param flags Sort flags
     * @param sorters List of import resolvers to use
     * @return File path or null if import failed
     */
    private String importFile(final File file, final String contentType,
            final Set<SortFlags> flags, final List<ImportResolver> sorters) {
        if (!IOProviderFactory.exists(file)) {
            Log.w(TAG, "Import file not found: " + file.getAbsolutePath());
            return null;
        }

        // overwrite and move rather than copy
        if (sorters == null || sorters.size() < 1) {
            Log.w(TAG,
                    "Found no import sorters for: " + file.getAbsolutePath());
            return null;
        }

        Log.d(TAG, "Importing file: " + file.getAbsolutePath());
        for (ImportResolver sorter : sorters) {
            // see if this sorter can handle the current file
            if (sorter.match(file)) {

                // Make sure content type matches if one was specified
                if (contentType != null) {
                    Pair<String, String> p = sorter.getContentMIME();
                    if (p == null || !contentType.equals(p.first))
                        continue;
                }

                // check if we will be overwriting an existing file
                File destPath = sorter.getDestinationPath(file);
                if (destPath == null) {
                    Log.w(TAG, sorter
                            + ", Unable to determine destination path for: "
                            + file.getAbsolutePath());
                    continue;
                }

                // now attempt to sort (i.e. move the file to proper location)
                if (sorter.beginImport(file, flags)) {
                    Log.d(TAG,
                            sorter + ", Sorted: "
                                    + file.getAbsolutePath() + " to "
                                    + destPath.getAbsolutePath());
                    return destPath.getAbsolutePath();
                } else
                    Log.w(TAG, sorter
                            + ", Matched, but did not sort: "
                            + file.getAbsolutePath());
            } // end if sorter match was found
        }

        return null;
    }
}
