
package com.atakmap.android.missionpackage.file;

import com.atakmap.android.data.FileContentHandler;
import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.hierarchy.action.Visibility;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.missionpackage.ui.MissionPackageListFileItem;
import com.atakmap.android.missionpackage.ui.MissionPackageListGroup;
import com.atakmap.android.missionpackage.ui.MissionPackageListItem;
import com.atakmap.android.missionpackage.ui.MissionPackageListMapItem;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.filesystem.HashingUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Convert between data (file or UID), manifest content, and UI element
 * 
 * 
 */
public class MissionPackageManifestAdapter {

    private static final String TAG = "MissionPackageManifestAdapter";

    public static MissionPackageContent UIDToContent(String uid) {
        if (FileSystemUtils.isEmpty(uid)) {
            Log.w(TAG, "Ignoring empty UID");
            return null;
        }

        // zip entry of uid/uid.cot
        MissionPackageContent content = new MissionPackageContent(
                uid + File.separatorChar + uid + ".cot");
        content.setParameter(new NameValuePair(
                MissionPackageConfiguration.PARAMETER_UID, uid));
        content.setParameter(new NameValuePair(
                MissionPackageContent.PARAMETER_LOCALISCOT,
                Boolean.TRUE.toString()));
        return content;
    }

    public static MissionPackageListMapItem UIDToUI(MapGroup mapGroup,
            String uid) {
        return UIDContentToUI(mapGroup, UIDToContent(uid));
    }

    public static MissionPackageListMapItem UIDContentToUI(MapGroup mapGroup,
            MissionPackageContent content) {
        if (content == null || !content.isValid()) {
            Log.w(TAG, "Unable to add empty UID");
            return null;
        }

        NameValuePair uid = content
                .getParameter(MissionPackageConfiguration.PARAMETER_UID);
        if (uid == null || !uid.isValid()) {
            Log.w(TAG, "Ignoring invalid CoT content");
            return null;
        }

        MapItem item = mapGroup.deepFindUID(uid.getValue());
        if (item == null) {
            Log.w(TAG, "Unable to find item: " + uid);
            return new MissionPackageListMapItem(null, "unknown",
                    uid.getValue(), content);
        } else {
            String iconUri = ATAKUtilities.getIconUri(item);
            int iconColor = ATAKUtilities.getIconColor(item);
            String title = ATAKUtilities.getDisplayName(item);
            content.setParameter(new NameValuePair(
                    MissionPackageContent.PARAMETER_NAME, title));
            MissionPackageListMapItem ret = new MissionPackageListMapItem(
                    null, item.getType(), title, content);
            ret.seticon(iconUri, iconColor);
            return ret;
        }
    }

    /**
     * Convert the file path to a Mission Package Content
     * 
     * @param file
     * @param uid
     * @return
     */
    public static MissionPackageContent FileToContent(File file, String uid) {
        if (!FileSystemUtils.isFile(file)) {
            Log.w(TAG, "Unable to convert empty file to Content");
            return null;
        }

        // zip entry of <MD5 hash of filepath (not file contents)>/<filename> for consistency e.g.
        // so we don't re-add same file multiple times
        String fileNameHash = HashingUtils.md5sum(file.getAbsolutePath());
        if (FileSystemUtils.isEmpty(fileNameHash)) {
            Log.w(TAG, "Unable to create file name hash");
            return null;
        }

        MissionPackageContent content = new MissionPackageContent(
                fileNameHash + File.separatorChar + file.getName());
        content.setParameter(new NameValuePair(
                MissionPackageContent.PARAMETER_LOCALPATH, file
                        .getAbsolutePath()));
        if (!FileSystemUtils.isEmpty(uid)) {
            content.setParameter(new NameValuePair(
                    MissionPackageConfiguration.PARAMETER_UID, uid));
        }

        // Pull metadata from file handler if there is one
        URIContentHandler h = URIContentManager.getInstance().getHandler(file);
        if (h != null) {
            // Title for this file
            content.setParameter(MissionPackageContent.PARAMETER_NAME,
                    h.getTitle());

            // Importer content type
            String cType = h instanceof FileContentHandler
                    ? ((FileContentHandler) h).getContentType()
                    : null;
            if (!FileSystemUtils.isEmpty(cType))
                content.setParameter(
                        MissionPackageContent.PARAMETER_CONTENT_TYPE, cType);

            // Visibility
            if (h.isActionSupported(Visibility.class))
                content.setParameter(MissionPackageContent.PARAMETER_VISIBLE,
                        String.valueOf(((Visibility) h).isVisible()));
        }

        return content;
    }

    public static MissionPackageListFileItem FileToUI(File file, String uid) {
        if (!FileSystemUtils.isFile(file)) {
            Log.w(TAG,
                    "File does not exist: "
                            + (file == null ? "null" : file.getAbsolutePath()));
            return null;
        }

        MissionPackageContent content = FileToContent(file, uid);
        if (content == null || !content.isValid()) {
            Log.w(TAG, "Unable to add empty file");
            return null;
        }

        return FileContentToUI(content, file);
    }

    public static MissionPackageListFileItem FileContentToUI(
            MissionPackageContent content,
            File file) {
        Icon icon = null;
        ResourceFile.MIMEType t = ResourceFile.getMIMETypeForFile(file
                .getName());
        if (t != null) {
            icon = new Icon(t.ICON_URI);
        } else {
            icon = new Icon("asset://icons/generic_doc.png");
        }

        return new MissionPackageListFileItem(
                icon,
                FileSystemUtils.getExtension(file, true, true),
                file.getName(),
                content);
    }

    /**
     * Get list of file paths in the Mission Package
     * 
     * @return
     */
    public static List<MissionPackageListFileItem> GetFiles(
            MissionPackageManifest manifest) {
        List<MissionPackageListFileItem> files = new ArrayList<>();

        for (MissionPackageContent content : manifest.getFiles()) {
            if (content == null) {
                Log.w(TAG, "Unable to adapt null content file.");
                continue;

            }
            if (!content.isValid()) {
                Log.w(TAG,
                        "Unable to adapt invalid content file: "
                                + content.getManifestUid());
                continue;
            }

            NameValuePair p = content
                    .getParameter(MissionPackageContent.PARAMETER_LOCALPATH);
            if (p == null || !p.isValid()) {
                Log.w(TAG, "Unable to adapt invalid content file path: "
                        + content.getManifestUid());
                continue;
            }

            files.add(MissionPackageManifestAdapter.FileContentToUI(content,
                    new File(FileSystemUtils
                            .sanitizeWithSpacesAndSlashes(p.getValue()))));
        }

        return files;
    }

    /**
     * Get list of UIDs for Map Items in the Mission Package
     * 
     * @return
     */
    public static List<MissionPackageListMapItem> GetMapItems(
            MapGroup mapGroup,
            MissionPackageManifest manifest) {
        List<MissionPackageListMapItem> items = new ArrayList<>();

        for (MissionPackageContent content : manifest.getMapItems()) {
            if (content == null || !content.isValid()) {
                Log.w(TAG,
                        "Unable to adapt invalid content UID: "
                                + (content == null ? "null"
                                        : content
                                                .getManifestUid()));
                continue;
            }

            MissionPackageListMapItem item = MissionPackageManifestAdapter
                    .UIDContentToUI(mapGroup,
                            content);
            if (item == null) {
                Log.w(TAG, "Unable to adapt invalid content UID item: "
                        + content.getManifestUid());
                continue;
            }

            items.add(item);
        }

        return items;
    }

    public static MissionPackageListGroup adapt(
            MissionPackageManifest manifest, String userName,
            MapGroup mapGroup) {
        if (manifest == null || !manifest.isValid()) {
            Log.w(TAG, "Unable to adapt invalid manifest");
            return null;
        }

        List<MissionPackageListItem> items = new ArrayList<>();
        if (manifest.hasMapItems()) {
            for (MissionPackageListMapItem item : MissionPackageManifestAdapter
                    .GetMapItems(
                            mapGroup, manifest)) {
                if (item == null || item.getContent() == null)
                    continue;

                if (item.getContent().isIgnore()) {
                    Log.d(TAG, "Skipping MapItem ignore content: "
                            + item.getContent().toString());
                    continue;
                }

                items.add(item);
            }
        }

        if (manifest.hasFiles()) {
            for (MissionPackageListFileItem item : MissionPackageManifestAdapter
                    .GetFiles(manifest)) {
                if (item == null || item.getContent() == null)
                    continue;

                if (item.getContent().isIgnore()) {
                    Log.d(TAG, "Skipping File ignore content: "
                            + item.getContent().toString());
                    continue;
                }

                items.add(item);
            }
        }

        return new MissionPackageListGroup(manifest, items, userName);
    }
}
