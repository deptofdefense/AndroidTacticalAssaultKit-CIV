
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
            setContentNoSync(new MissionPackageContent(content));
    }

    public boolean isValid() {
        return true;
    }

    public synchronized void clear() {
        _contents.clear();
    }

    public synchronized List<MissionPackageContent> getContents() {
        return new ArrayList<>(_contents);
    }

    /**
     * Get all content of the specified action (except those to be ignored)
     * 
     * @param bIsCoT true to get CoT, false to get other files
     * @return
     */
    public synchronized List<MissionPackageContent> getContents(
            boolean bIsCoT) {
        List<MissionPackageContent> contents = new ArrayList<>();
        for (MissionPackageContent c : _contents) {
            if (c != null && c.isCoT() == bIsCoT && !c.isIgnore()) {
                contents.add(c);
            }
        }

        return contents;
    }

    public synchronized boolean setContent(MissionPackageContent content) {
        return setContentNoSync(content);
    }

    private boolean setContentNoSync(MissionPackageContent content) {
        if (content == null || !content.isValid()) {
            Log.w(TAG, "Ignoring invalid content");
            return false;
        }

        if (_contents.remove(content))
            Log.d(TAG, "Replacing content: " + content);

        return _contents.add(content);
    }

    public synchronized void setContents(
            List<MissionPackageContent> parameters) {
        _contents = new ArrayList<>(parameters);
    }

    public synchronized boolean hasContent(boolean bIsCoT) {
        for (MissionPackageContent c : _contents) {
            if (c != null && c.isCoT() == bIsCoT && !c.isIgnore()) {
                return true;
            }
        }

        return false;
    }

    public synchronized boolean hasContent(String zipEntry) {
        for (MissionPackageContent c : _contents) {
            if (c != null && !FileSystemUtils.isEmpty(c.getManifestUid())
                    && c.getManifestUid().equals(zipEntry)) {
                return true;
            }
        }

        return false;
    }

    public synchronized boolean hasContent(MissionPackageContent content) {
        if (content == null)
            return false;

        return _contents.contains(content);
    }

    public synchronized boolean removeContent(MissionPackageContent content) {
        return content != null && _contents.remove(content);
    }

    @Override
    public synchronized int hashCode() {
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

    public synchronized boolean equals(MissionPackageContents rhsc) {
        return FileSystemUtils.isEquals(_contents, rhsc._contents);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public synchronized void writeToParcel(Parcel parcel, int flags) {
        if (isValid()) {
            parcel.writeInt(_contents.size());
            if (_contents.size() > 0) {
                for (MissionPackageContent content : _contents)
                    parcel.writeParcelable(content, flags);
            }
        } else
            Log.w(TAG, "cannot parcel invalid: " + this);
    }

    protected MissionPackageContents(Parcel in) {
        this();
        readFromParcel(in);
    }

    private void readFromParcel(Parcel in) {
        int count = in.readInt();
        for (int cur = 0; cur < count; cur++) {
            setContentNoSync(in
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
    public synchronized String toString() {
        return _contents == null ? "" : "" + _contents.size();
    }
}
