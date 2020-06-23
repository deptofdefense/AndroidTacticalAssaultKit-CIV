
package com.atakmap.android.missionpackage.file;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.ArrayList;
import java.util.List;

/**
 * List of contents for the Mission Package
 * 
 * 
 */
@Root
public class MissionPackageContents implements Parcelable {

    private static final String TAG = "MissionPackageContents";

    @ElementList(entry = "Content", inline = true, required = false)
    private List<MissionPackageContent> _contents;

    public MissionPackageContents() {
        _contents = new ArrayList<>();
    }

    public MissionPackageContents(MissionPackageContents copy) {
        _contents = new ArrayList<>();
        for (MissionPackageContent content : copy.getContents())
            setContent(new MissionPackageContent(content));
    }

    public boolean isValid() {
        return true;
    }

    public void clear() {
        _contents.clear();
    }

    public List<MissionPackageContent> getContents() {
        return _contents;
    }

    /**
     * Get all content of the specified action (except those to be ignored)
     * 
     * @param bIsCoT true to get CoT, false to get other files
     * @return
     */
    public List<MissionPackageContent> getContents(boolean bIsCoT) {
        List<MissionPackageContent> contents = new ArrayList<>();
        for (MissionPackageContent c : _contents) {
            if (c != null && c.isCoT() == bIsCoT && !c.isIgnore()) {
                contents.add(c);
            }
        }

        return contents;
    }

    public boolean setContent(MissionPackageContent content) {
        if (content == null || !content.isValid()) {
            Log.w(TAG, "Ignoring invalid content");
            return false;
        }

        if (_contents.contains(content)) {
            Log.d(TAG, "Replacing content: " + content.toString());
            _contents.remove(content);
        }

        return _contents.add(content);
    }

    public void setContents(List<MissionPackageContent> parameters) {
        _contents = parameters;
    }

    public boolean hasContent(boolean bIsCoT) {
        for (MissionPackageContent c : _contents) {
            if (c != null && c.isCoT() == bIsCoT && !c.isIgnore()) {
                return true;
            }
        }

        return false;
    }

    public boolean hasContent(String zipEntry) {
        for (MissionPackageContent c : _contents) {
            if (c != null && !FileSystemUtils.isEmpty(c.getManifestUid())
                    && c.getManifestUid().equals(zipEntry)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasContent(MissionPackageContent content) {
        if (content == null)
            return false;

        return _contents.contains(content);
    }

    public boolean removeContent(MissionPackageContent content) {
        if (content == null)
            return false;

        if (_contents.contains(content))
            return _contents.remove(content);

        return false;
    }

    @Override
    public int hashCode() {
        return (_contents == null) ? 0 : _contents.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MissionPackageContents) {
            MissionPackageContents c = (MissionPackageContents) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(MissionPackageContents rhsc) {
        if (!FileSystemUtils.isEquals(_contents, rhsc._contents))
            return false;

        return true;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        if (isValid()) {
            parcel.writeInt(_contents.size());
            if (_contents.size() > 0) {
                for (MissionPackageContent content : _contents)
                    parcel.writeParcelable(content, flags);
            }
        } else
            Log.w(TAG, "cannot parcel invalid: " + toString());
    }

    protected MissionPackageContents(Parcel in) {
        this();
        readFromParcel(in);
    }

    private void readFromParcel(Parcel in) {
        int count = in.readInt();
        for (int cur = 0; cur < count; cur++) {
            setContent((MissionPackageContent) in
                    .readParcelable(MissionPackageContent.class
                            .getClassLoader()));
        }
    }

    public static final Parcelable.Creator<MissionPackageContents> CREATOR = new Parcelable.Creator<MissionPackageContents>() {
        @Override
        public MissionPackageContents createFromParcel(Parcel in) {
            return new MissionPackageContents(in);
        }

        @Override
        public MissionPackageContents[] newArray(int size) {
            return new MissionPackageContents[size];
        }
    };

    @Override
    public String toString() {
        return _contents == null ? "" : "" + _contents.size();
    }
}
