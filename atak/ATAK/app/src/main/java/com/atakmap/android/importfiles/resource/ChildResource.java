
package com.atakmap.android.importfiles.resource;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.filesystem.FileSystemUtils;

import org.simpleframework.xml.Element;

/**
 * Information about a local child resource, e.g. a KML that was streamed from a child NetworkLink
 * for a top level RemoteResource
 * 
 * 
 */
public class ChildResource implements Parcelable {

    private static final String TAG = "ChildResource";

    @Element
    private String name;

    @Element
    private String localPath;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public boolean isValid() {
        return !(FileSystemUtils.isEmpty(name) || FileSystemUtils
                .isEmpty(localPath));

    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ChildResource) {
            ChildResource c = (ChildResource) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(ChildResource c) {
        if (!FileSystemUtils.isEquals(getName(), c.getName()))
            return false;

        if (!FileSystemUtils.isEquals(getLocalPath(), c.getLocalPath()))
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return 31 * ((getName() == null ? 0 : getName().hashCode())
                + (getLocalPath() == null ? 0
                        : getLocalPath().hashCode()));
    }

    @Override
    public String toString() {
        return String.format("%s %s", getName(), getLocalPath());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(name);
        parcel.writeString(localPath);
    }

    public static final Parcelable.Creator<ChildResource> CREATOR = new Parcelable.Creator<ChildResource>() {
        @Override
        public ChildResource createFromParcel(Parcel in) {
            ChildResource ret = new ChildResource();
            ret.setName(in.readString());
            ret.setLocalPath(in.readString());
            return ret;
        }

        @Override
        public ChildResource[] newArray(int size) {
            return new ChildResource[size];
        }
    };
}
