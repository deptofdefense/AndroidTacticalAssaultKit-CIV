
package com.atakmap.android.missionpackage.ui;

import com.atakmap.android.data.URIContentHandler;
import com.atakmap.android.data.URIContentManager;
import com.atakmap.android.hashtags.HashtagContent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.file.MissionPackageContent;
import com.atakmap.android.missionpackage.file.NameValuePair;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.maps.assets.Icon;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * UI convenience wrapper around File MissionPackageContent
 * 
 * 
 */
public class MissionPackageListFileItem extends MissionPackageListItem {

    private static final String TAG = "MissionPackageListFileItem";

    public MissionPackageListFileItem(Icon icon, String type, String name,
            MissionPackageContent content) {
        super(icon, type, name, content);
    }

    @Override
    public boolean isFile() {
        return true;
    }

    public String getPath() {
        if (_content == null)
            return null;

        NameValuePair p = _content
                .getParameter(MissionPackageContent.PARAMETER_LOCALPATH);
        if (p == null || !p.isValid())
            return null;

        return p.getValue();
    }

    /**
     * Get UID of marker this file is attached to, or null if not attached
     *
     * @return the marker uid
     */
    public String getMarkerUid() {
        if (_content == null)
            return null;

        NameValuePair p = _content
                .getParameter(MissionPackageContent.PARAMETER_UID);
        if (p == null || !p.isValid())
            return null;

        return p.getValue();
    }

    @Override
    public boolean exists(MapView mapView) {
        return FileSystemUtils.isFile(getPath());
    }

    @Override
    public void removeContent() {
        File f = new File(getPath());

        // Remove content from map via content handler
        List<URIContentHandler> handlers = URIContentManager.getInstance()
                .getHandlers(f);
        for (URIContentHandler h : handlers)
            h.deleteContent();

        // Delete file
        FileSystemUtils.delete(f);
    }

    @Override
    public String toString() {
        return getPath();
    }

    @Override
    public long getsizeInBytes() {
        String path = getPath();
        if (!FileSystemUtils.isFile(path))
            return 0;

        return IOProviderFactory.length(new File(path));
    }

    @Override
    public void addHashtags(Collection<String> tags) {
        if (tags.isEmpty())
            return;

        String path = getPath();
        if (FileSystemUtils.isEmpty(path))
            return;

        URIContentHandler handler = URIContentManager.getInstance()
                .getHandler(new File(path));
        if (handler instanceof HashtagContent) {
            HashtagContent content = (HashtagContent) handler;
            Collection<String> itemTags = content.getHashtags();
            itemTags.addAll(tags);
            content.setHashtags(itemTags);
        }
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object rhs) {
        if (!(rhs instanceof MissionPackageListFileItem))
            return false;

        MissionPackageListFileItem rhsc = (MissionPackageListFileItem) rhs;

        if (!FileSystemUtils.isEquals(getPath(), rhsc.getPath()))
            return false;

        return super.equals(rhs);
    }
}
