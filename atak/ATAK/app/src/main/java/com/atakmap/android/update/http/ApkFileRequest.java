
package com.atakmap.android.update.http;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.android.http.rest.BasicUserCredentials;
import com.atakmap.android.http.rest.request.GetFileRequest;
import com.atakmap.android.update.ApkDownloader;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.foxykeep.datadroid.requestmanager.Request;

/**
 * Extends GetFileRequest to include the Apk request
 * 
 * 
 */
public class ApkFileRequest extends GetFileRequest {

    private final boolean bInstall;
    private final boolean bSilent;
    private final String packageName;
    private final String hash;

    public ApkFileRequest(String packageName, String url, String filename,
            String dir,
            int notificationId, boolean install,
            boolean silent, String hash, BasicUserCredentials creds) {
        super(url, filename, dir, notificationId, creds);
        this.packageName = packageName;
        this.hash = hash;
        this.bSilent = silent;
        this.bInstall = install;
    }

    public String getPackageName() {
        return packageName;
    }

    public boolean isSilent() {
        return bSilent;
    }

    public boolean isInstall() {
        return bInstall;
    }

    public String getHash() {
        return hash;
    }

    public boolean hasHash() {
        return !FileSystemUtils.isEmpty(hash);
    }

    @Override
    public boolean isValid() {
        return !FileSystemUtils.isEmpty(packageName)
                && super.isValid();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            super.writeToParcel(dest, flags);
            dest.writeString(packageName);
            dest.writeString(hash);
            dest.writeByte(bInstall ? (byte) 1 : (byte) 0);
            dest.writeByte(bSilent ? (byte) 1 : (byte) 0);
        }
    }

    public static final Parcelable.Creator<ApkFileRequest> CREATOR = new Parcelable.Creator<ApkFileRequest>() {
        @Override
        public ApkFileRequest createFromParcel(Parcel in) {
            return new ApkFileRequest(in);
        }

        @Override
        public ApkFileRequest[] newArray(int size) {
            return new ApkFileRequest[size];
        }
    };

    protected ApkFileRequest(Parcel in) {
        super(in);
        packageName = in.readString();
        hash = in.readString();
        bInstall = in.readByte() != 0;
        bSilent = in.readByte() != 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public Request createGetFileRequest() {
        Request request = new Request(ApkDownloader.REQUEST_TYPE_GET_APK);
        request.put(GetApkOperation.PARAM_GETAPKFILE, this);
        return request;
    }
}
