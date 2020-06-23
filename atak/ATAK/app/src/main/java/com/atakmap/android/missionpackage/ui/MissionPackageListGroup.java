
package com.atakmap.android.missionpackage.ui;

import com.atakmap.android.contact.Contact;
import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper;
import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper.TABLETYPE;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.missionpackage.MissionPackageMapComponent;
import com.atakmap.android.missionpackage.file.MissionPackageContent;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.MissionPackageManifestAdapter;
import com.atakmap.android.missionpackage.file.task.MissionPackageBaseTask.Callback;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.android.missionpackage.file.NameValuePair;

import java.io.File;
import java.util.List;

/**
 * Container for managing a Mission Package in the ATAK UI. Tracks user changes against baseline
 * (what is currently in the .zip on SD card). Also keeps a list of UI elements representing each
 * item in the Mission Package contents
 * 
 * 
 */
public class MissionPackageListGroup {
    private static final String TAG = "MissionPackageListGroup";

    /**
     * A copy of the original to compare for changes
     */
    private MissionPackageManifest _baseline;

    /**
     * Current (possibly user edited) state of Mission Package
     */
    private MissionPackageManifest _manifest;

    /**
     * UI element list, one for each item in _contents
     */
    private List<MissionPackageListItem> _items;

    /**
     * User name that last edited the Mission Package. May represent who sent the package to this
     * device, or local callsign if it was loaded from SD card or modified locally.
     */
    private String _userName;

    /**
     * ctor
     * 
     * @param manifest
     * @param items
     * @param userName
     */
    public MissionPackageListGroup(MissionPackageManifest manifest,
            List<MissionPackageListItem> items, String userName) {
        _manifest = manifest;
        _baseline = new MissionPackageManifest(manifest);
        _items = items;
        _userName = userName;
    }

    /**
     * Delegates to manifest to check for validity
     * @return
     */
    public boolean isValid() {
        return _manifest != null && _manifest.isValid();
    }

    public MissionPackageManifest getManifest() {
        return _manifest;
    }

    public List<MissionPackageListItem> getItems() {
        return _items;
    }

    public boolean isModified() {
        return !_manifest.equals(_baseline);
    }

    public String getUserName() {
        return _userName;
    }

    public void setManifest(MissionPackageManifest manifest) {
        this._manifest = manifest;
    }

    public void setItems(List<MissionPackageListItem> items) {
        this._items = items;
    }

    public void setUserName(String userName) {
        this._userName = userName;
    }

    public String toString() {
        return _manifest.toString() + ", " + isModified();
    }

    public boolean removeItem(MissionPackageListItem item) {
        boolean success = _manifest.removeFile(item.getContent()) &&
                _items.remove(item);

        if (success)
            Log.d(TAG, "Removed item: " + item + " from " + toString());
        else
            Log.w(TAG, "Failed to locate file: " + item + " in " + toString());
        return success;
    }

    public boolean addFile(File file, String uid) {
        MissionPackageContent content = MissionPackageManifestAdapter
                .FileToContent(file, uid);
        return addFile(content, file);
    }

    public boolean addFile(MissionPackageContent content, File file) {

        if (content == null || file == null) {
            Log.w(TAG,
                    "unable to add file content/file: " + content + "/" + file);
            return false;
        }

        // see if already in list
        MissionPackageListFileItem adapt = MissionPackageManifestAdapter
                .FileContentToUI(content,
                        file);
        if (adapt == null) {
            Log.w(TAG, "Unable to adapt file: " + file.getAbsolutePath());
            return false;
        }

        if (FileSystemUtils.isEquals(file.getAbsolutePath(),
                getManifest().getPath())) {
            Log.d(TAG, "Ignoring self-add for MP: " + file);
            return false;
        }

        if (_manifest.getFiles().contains(adapt.getContent())) {
            Log.d(TAG, "File already in package: " + adapt.toString());
            return true;
        }

        boolean success = _manifest.addContent(adapt.getContent());
        if (!adapt.getContent().isIgnore()) {
            // only display non-ignored files in MPT UI
            success &= _items.add(adapt);
        }
        Log.d(TAG, "Added file: " + file.getAbsolutePath() + " to "
                + toString());
        return success;
    }

    public boolean removeFile(MissionPackageListFileItem item) {
        return removeItem(item);
    }

    public void addMapItems(MapGroup mapGroup, String... uids) {

        boolean success = true, invalidate = false;
        for (String uid : uids) {
            MissionPackageListMapItem item = MissionPackageManifestAdapter
                    .UIDToUI(mapGroup, uid);
            if (item == null) {
                success = false;
                continue;
            }

            MissionPackageListMapItem remove = null;
            // Attempt to update map items already in the package
            for (MissionPackageListItem li : _items) {
                if (!(li instanceof MissionPackageListMapItem))
                    continue;
                MissionPackageListMapItem mli = (MissionPackageListMapItem) li;

                NameValuePair nvp = li.getContent().getParameter("uid");
                if (nvp != null
                        && FileSystemUtils.isEquals(uid, nvp.getValue())
                        && _manifest.removeMapItem(mli)
                        && _items.contains(li)) {
                    invalidate = true;
                    remove = mli;
                    break;
                }
            }

            if (remove != null)
                _items.remove(remove);

            if (!item.getContent().isIgnore()) {
                // only display non-ignored files in MPT UI
                success &= _items.add(item);
            }

            success &= _manifest.addContent(item.getContent());
        }
        if (invalidate)
            invalidate();
        if (success)
            Log.d(TAG, "Added: " + uids.length + " map items to " + toString());
        else
            Log.w(TAG, "Errors while adding: " + uids.length + " map items to "
                    + toString());
    }

    public boolean removeMapItem(MissionPackageListMapItem item) {
        return removeItem(item);
    }

    /**
     * Save if needed, send if requested Wrapper for MissionPackageFileIO save and send methods
     * 
     * @param component
     * @param bSend
     * @param callback
     * @param netContacts
     * @return
     */
    public void saveAndSend(MissionPackageMapComponent component,
            boolean bSend, Callback callback, Contact[] netContacts) {
        if (!bSend)
            component.getFileIO().save(_manifest, callback);
        else {
            if (isModified()) {
                component.getFileIO().saveAndSend(_manifest, callback, false,
                        netContacts);
            } else {
                component.getFileIO().send(_manifest, netContacts, callback);
            }
        }
    }

    /**
     * Reset baseline (for comparison) equal to current contents e.g. after saving current contents
     * out to disk
     */
    public void rebase() {
        Log.d(TAG, "Rebasing " + _manifest.toString());
        _baseline = new MissionPackageManifest(_manifest);
    }

    /**
     * Manually invalidate the MP so the user can choose to save it again
     * Used when contents are modified at a lower level than the manifest tells us
     */
    public void invalidate() {
        _baseline = new MissionPackageManifest(_manifest.getName(),
                _manifest.getUID(), _manifest.getPath());
    }

    public boolean isSaved() {
        return FileInfoPersistanceHelper.instance().getFileInfoFromFilename(
                new File(_manifest.getPath()), TABLETYPE.SAVED) != null;
    }

    MissionPackageManifest getBaseline() {
        return _baseline;
    }

    public void removeContents() {
        Log.d(TAG, "Removing group content: " + this.toString());
        for (MissionPackageListItem item : getItems()) {
            item.removeContent();
        }
    }
}
