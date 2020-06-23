
package com.atakmap.android.filesharing.android.service;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;

public class AndroidFileInfo extends FileInfo
        implements Parcelable {

    public AndroidFileInfo() {
        super();
    }

    public AndroidFileInfo(File file, String fileMetadata) {
        super(file, fileMetadata);
    }

    public AndroidFileInfo(File file, String userName, String userLabel,
            String contentType,
            String fileMetadata) {
        super(file, userName, userLabel, contentType, fileMetadata);
    }

    public AndroidFileInfo(String sha256, File file,
            String contentType,
            String fileMetadata) {
        super(sha256, file, contentType, fileMetadata);
    }

    public AndroidFileInfo(FileInfo fi) {
        setId(fi.id());
        setFileName(fi.fileName());
        setContentType(fi.contentType());
        setSizeInBytes(fi.sizeInBytes());
        setUpdateTime(fi.updateTime());
        setUserName(fi.userName());
        setUserLabel(fi.userLabel());
        setSha256sum(fi.sha256sum());
        setDestinationPath(fi.destinationPath());
        setDownloadUrl(fi.downloadUrl());
        setFileMetadata(fi.fileMetadata());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(id());
        parcel.writeString(fileName());
        parcel.writeString(contentType());
        parcel.writeInt(sizeInBytes());
        parcel.writeString(destinationPath());
        parcel.writeLong(updateTime());
        parcel.writeString(userName());
        parcel.writeString(userLabel());
        parcel.writeString(downloadUrl());
        parcel.writeString(sha256sum());
        parcel.writeString(fileMetadata());
    }

    public static final Parcelable.Creator<AndroidFileInfo> CREATOR = new Parcelable.Creator<AndroidFileInfo>() {
        @Override
        public AndroidFileInfo createFromParcel(Parcel in) {
            AndroidFileInfo ret = new AndroidFileInfo();
            ret.setId(in.readInt());
            ret.setFileName(in.readString());
            ret.setContentType(in.readString());
            ret.setSizeInBytes(in.readInt());
            ret.setDestinationPath(in.readString());
            ret.setUpdateTime(in.readLong());
            ret.setUserName(in.readString());
            ret.setUserLabel(in.readString());
            ret.setDownloadUrl(in.readString());
            ret.setSha256sum(in.readString());
            ret.setFileMetadata(in.readString());
            return ret;
        }

        @Override
        public AndroidFileInfo[] newArray(int size) {
            return new AndroidFileInfo[size];
        }
    };
}
