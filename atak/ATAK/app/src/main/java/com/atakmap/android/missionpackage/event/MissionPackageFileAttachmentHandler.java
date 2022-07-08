
package com.atakmap.android.missionpackage.event;

import android.content.Context;
import android.widget.Toast;

import com.atakmap.android.importfiles.sort.ImportResolver;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.missionpackage.file.MissionPackageContent;
import com.atakmap.android.missionpackage.file.MissionPackageExtractor;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.MissionPackageManifestAdapter;
import com.atakmap.android.missionpackage.file.NameValuePair;
import com.atakmap.android.missionpackage.ui.MissionPackageListFileItem;
import com.atakmap.android.missionpackage.ui.MissionPackageListGroup;
import com.atakmap.android.util.AttachmentManager;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.util.zip.ZipEntry;
import com.atakmap.util.zip.ZipFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Handles special case processing for files which are attached to Map Items
 * 
 * 
 */
public class MissionPackageFileAttachmentHandler implements
        IMissionPackageEventHandler {

    private static final String TAG = "MissionPackageAddFileAttachment";

    private final MapGroup _rootGroup;
    private final Context _context;

    public MissionPackageFileAttachmentHandler(Context context,
            MapGroup rootGroup) {
        _context = context;
        _rootGroup = rootGroup;
    }

    @Override
    public boolean add(MissionPackageListGroup group, File file) {
        // check if file is a map item attachment
        if (!(file.getAbsolutePath().toLowerCase(LocaleUtil.getCurrent())
                .contains(File.separatorChar
                        + "attachments" + File.separatorChar)))
            return false;

        if (_rootGroup == null) {
            Log.w(TAG, "Cannot look up map item without Map Group");
            return false;
        }

        String uid = null;
        // see if its in atak/attachments/<uid>
        File parent = file.getParentFile();
        if (FileSystemUtils.isFile(parent)) {
            uid = parent.getName();
            MapItem item = _rootGroup.deepFindUID(uid);
            if (item != null) {
                Log.d(TAG, "Adding selected file which is attached to: " + uid);
            } else {
                // Not valid Map Item
                return false;
            }
        }

        MissionPackageContent content = MissionPackageManifestAdapter
                .FileToContent(file, uid);
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
        return group.addFile(content, file);
    }

    @Override
    public boolean remove(MissionPackageListGroup group,
            MissionPackageListFileItem file) {
        // no special handling for this, defer to MissionPackageEventHandler
        return false;
    }

    @Override
    public boolean extract(MissionPackageManifest manifest,
            MissionPackageContent content, ZipFile zipFile,
            File atakDataDir, byte[] buffer, List<ImportResolver> sorters)
            throws IOException {

        // check for UID and unzip there if this is a map item attachment
        if (content.hasParameter(MissionPackageContent.PARAMETER_UID)) {
            // Get map item UID
            String uid = content
                    .getParameter(MissionPackageContent.PARAMETER_UID)
                    .getValue();

            // Make sure map item exists on the map or is within manifest
            // If it's not then consider this a regular non-attachment file
            boolean isAttachment = false;
            List<MissionPackageContent> items = manifest.getMapItems();
            for (MissionPackageContent item : items) {
                if (item.hasParameter(MissionPackageContent.PARAMETER_UID)) {
                    String parentUid = content.getParameter(
                            MissionPackageContent.PARAMETER_UID).getValue();
                    if (FileSystemUtils.isEquals(parentUid, uid)) {
                        isAttachment = true;
                        break;
                    }
                }
            }
            if (!isAttachment && (_rootGroup == null
                    || _rootGroup.deepFindUID(uid) == null))
                return false;

            String parent = AttachmentManager.getFolderPath(uid);

            // get filename
            int index = content.getManifestUid().lastIndexOf(
                    File.separatorChar);
            if (index < 0) {
                throw new IOException(
                        "Unable to determine file path for Map Item attachment: "
                                + content.getManifestUid());
            }
            File toUnzip = new File(parent, content.getManifestUid().substring(
                    index + 1));

            ZipEntry entry = zipFile.getEntry(content.getManifestUid());
            if (entry == null) {
                throw new IOException(
                        "Package does not contain manifest content: "
                                + content.getManifestUid());
            }

            // unzip and build out "local" manifest using localpath
            MissionPackageExtractor.UnzipFile(zipFile.getInputStream(entry),
                    toUnzip, false, buffer);
            content.setParameter(new NameValuePair(
                    MissionPackageContent.PARAMETER_LOCALPATH,
                    toUnzip.getAbsolutePath()));
            Log.d(TAG, "Extracted Map Item Attachment: "
                    + toUnzip.getAbsolutePath());
            return true;
        }

        return false;
    }

}
