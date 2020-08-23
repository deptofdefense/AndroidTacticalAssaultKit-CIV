
package com.atakmap.android.missionpackage.ui;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.file.MissionPackageContent;
import com.atakmap.android.missionpackage.file.NameValuePair;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.maps.assets.Icon;

import java.util.Collection;

/**
 * UI convenience wrapper around MissionPackageContent
 * 
 * 
 */
public abstract class MissionPackageListItem {
    private Icon _icon;
    private String _type;
    private String _name;
    protected final MissionPackageContent _content;

    public MissionPackageListItem(Icon icon, String type, String name,
            MissionPackageContent content) {
        this._icon = icon;
        this._type = type;
        this._name = name;
        this._content = content;

        // Use the defined content name if available
        NameValuePair nvp = content.getParameter(
                MissionPackageContent.PARAMETER_NAME);
        String cName = nvp != null ? nvp.getValue() : null;
        if (!FileSystemUtils.isEmpty(cName))
            _name = cName;
    }

    public abstract boolean isFile();

    public abstract long getsizeInBytes();

    /**
     * Check if "this" item exists in context of mapView
     * 
     * @param mapView
     * @return
     */
    public abstract boolean exists(MapView mapView);

    /**
     * Remove content from ATAK
     */
    public abstract void removeContent();

    public Icon geticon() {
        return _icon;
    }

    public void seticon(Icon _icon) {
        this._icon = _icon;
    }

    public void seticon(String iconUri, int iconColor) {
        seticon(new Icon.Builder()
                .setImageUri(Icon.STATE_DEFAULT, iconUri)
                .setColor(Icon.STATE_DEFAULT, iconColor)
                .build());
    }

    public String gettype() {
        return _type;
    }

    public void settype(String _type) {
        this._type = _type;
    }

    public String getname() {
        return _name;
    }

    public void setname(String _name) {
        this._name = _name;
    }

    public MissionPackageContent getContent() {
        return this._content;
    }

    public void addHashtags(Collection<String> tags) {
    }

    @Override
    public int hashCode() {
        int result = (_icon == null) ? 0 : _icon.hashCode();
        result = 31 * result + ((_type == null) ? 0 : _type.hashCode());
        result = 31 * result + ((_name == null) ? 0 : _name.hashCode());
        result = 31 * result + ((_content == null) ? 0 : _content.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object rhs) {
        if (!(rhs instanceof MissionPackageListItem))
            return false;

        MissionPackageListItem rhsc = (MissionPackageListItem) rhs;

        if (getsizeInBytes() != rhsc.getsizeInBytes())
            return false;
        if (!FileSystemUtils.isEquals(_name, rhsc.getname()))
            return false;
        if (!FileSystemUtils.isEquals(_type, rhsc.gettype()))
            return false;
        // TODO compare _icon?
        return _content.equals(rhsc.getContent());
    }

}
