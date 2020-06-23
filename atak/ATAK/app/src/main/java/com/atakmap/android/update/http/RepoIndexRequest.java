
package com.atakmap.android.update.http;

import android.os.Parcel;

import com.atakmap.android.http.rest.BasicUserCredentials;
import com.atakmap.android.http.rest.request.GetFileRequest;
import com.atakmap.android.update.AppMgmtUtils;

/**
 * Extends GetFileRequest to process INFZ product.inf compressed archive including product icons
 * 
 * 
 */
public class RepoIndexRequest extends GetFileRequest {

    private final boolean bSilent;

    public RepoIndexRequest(String url, String dir,
            int notificationId, boolean silent, BasicUserCredentials creds) {
        super(url, AppMgmtUtils.REPO_INDEX_FILENAME, dir, notificationId,
                creds);
        bSilent = silent;
    }

    public boolean isSilent() {
        return bSilent;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            super.writeToParcel(dest, flags);
            dest.writeByte(bSilent ? (byte) 1 : (byte) 0);
        }
    }

    public static final Creator<RepoIndexRequest> CREATOR = new Creator<RepoIndexRequest>() {
        @Override
        public RepoIndexRequest createFromParcel(Parcel in) {
            return new RepoIndexRequest(in);
        }

        @Override
        public RepoIndexRequest[] newArray(int size) {
            return new RepoIndexRequest[size];
        }
    };

    protected RepoIndexRequest(Parcel in) {
        super(in);
        bSilent = in.readByte() != 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    //    @Override
    //    public Request createGetFileRequest() {
    //        Request request = new Request(ApkDownloader.REQUEST_TYPE_GET_REMOTE_INDEX);
    //        request.put(GetRepoIndexOperation.PARAM_GETINFZFILE, this);
    //        return request;
    //    }
}
