
package com.atakmap.android.http.rest.request;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.operation.GetFileOperation;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.requestmanager.Request;
import com.atakmap.android.http.rest.BasicUserCredentials;

import java.io.File;

/**
 * Parcelable for a simple File request
 * 
 * 
 */
public class GetFileRequest implements Parcelable {

    private static final String TAG = "GetFileRequest";

    /**
     * Name of file on disk
     */
    private String _destFilename;
    private final String _url;
    private final String _destDir;
    private BasicUserCredentials credentials;

    /**
     * Optional type of truststore to use
     */
    private String _trustStoreType;

    /**
     * Optional type of truststore credentials
     */
    private String _credentialType;

    private final int _notificationId;

    public GetFileRequest(String url, String filename, String dir,
            int notificationId) {
        this(url, filename, dir, notificationId, null);
    }

    /**
     *
     * @param url URL to download
     * @param filename Name of file to create locally
     * @param dir Where to store file locally
     * @param notificationId the notification id to use
     * @param creds basic user credentials
     */
    public GetFileRequest(String url, String filename, String dir,
            int notificationId, BasicUserCredentials creds) {
        _url = encode(url);
        _destFilename = filename;
        _destDir = dir;
        _notificationId = notificationId;
        this.credentials = creds;

    }

    public GetFileRequest(String url, String filename, String dir,
            int notificationId, String trustStoreType, String credentialType) {
        this(url, filename, dir, notificationId);
        _trustStoreType = trustStoreType;
        _credentialType = credentialType;
    }

    private String encode(String encode) {
        if (encode == null)
            return encode;
        encode = encode.replace("'", "%27");
        encode = encode.replace(" ", "%20");
        encode = encode.replace("[", "%5B");
        encode = encode.replace("]", "%5D");
        return encode;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(_url)
                && !FileSystemUtils.isEmpty(_destDir)
                && !FileSystemUtils.isEmpty(_destFilename);
    }

    public boolean hasCredentials() {
        return credentials != null && credentials.isValid();
    }

    public BasicUserCredentials getCredentials() {
        return credentials;
    }

    public void setCredentials(BasicUserCredentials creds) {
        this.credentials = creds;
    }

    /**
     * Check if truststore and credential types are set
     *
     * @return true if the trustore and credential types are set
     */
    public boolean useTruststore() {
        return !FileSystemUtils.isEmpty(_trustStoreType)
                && !FileSystemUtils.isEmpty(_credentialType);
    }

    public String getTrustStoreType() {
        return _trustStoreType;
    }

    public String getCredentialType() {
        return _credentialType;
    }

    public String getUrl() {
        return _url;
    }

    public String getDir() {
        return _destDir;
    }

    public String getFileName() {
        return _destFilename;
    }

    public void setFileName(String fileName) {
        Log.d(TAG, "setFileName: " + fileName);
        this._destFilename = fileName;
    }

    public File getFile() {
        return new File(this.getDir(), this.getFileName());
    }

    public int getNotificationId() {
        return _notificationId;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("%s, %s/%s", _url, _destDir, _destFilename);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            dest.writeString(_url);
            dest.writeString(_destDir);
            dest.writeString(_destFilename);
            dest.writeInt(_notificationId);
            dest.writeString(_trustStoreType);
            dest.writeString(_credentialType);
            if (credentials != null && credentials.isValid()) {
                dest.writeByte((byte) 1);
                dest.writeParcelable(credentials, flags);
            } else {
                dest.writeByte((byte) 0);
            }

        }
    }

    public static final Parcelable.Creator<GetFileRequest> CREATOR = new Parcelable.Creator<GetFileRequest>() {
        @Override
        public GetFileRequest createFromParcel(Parcel in) {
            return new GetFileRequest(in);
        }

        @Override
        public GetFileRequest[] newArray(int size) {
            return new GetFileRequest[size];
        }
    };

    protected GetFileRequest(Parcel in) {
        _url = in.readString();
        _destDir = in.readString();
        _destFilename = in.readString();
        _notificationId = in.readInt();
        _trustStoreType = in.readString();
        _credentialType = in.readString();
        if (in.readByte() > 0) {
            credentials = in.readParcelable(BasicUserCredentials.class
                    .getClassLoader());
        }

    }

    @Override
    public int describeContents() {
        // TODO Auto-generated method stub
        return 0;
    }

    /**
     * Create the request to download the file. Used by an asynch HTTP request Android Service
     * 
     * @return The request.
     */
    public Request createGetFileRequest() {
        Request request = new Request(
                NetworkOperationManager.REQUEST_TYPE_GET_FILE);
        request.put(GetFileOperation.PARAM_GETFILE, this);
        return request;
    }
}
