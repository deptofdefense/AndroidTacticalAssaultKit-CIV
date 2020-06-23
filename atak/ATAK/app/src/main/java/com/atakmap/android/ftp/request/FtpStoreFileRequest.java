
package com.atakmap.android.ftp.request;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.android.ftp.operation.FtpStoreFileOperation;
import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.UserCredentials;
import com.atakmap.android.http.rest.request.RetryRequest;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.foxykeep.datadroid.requestmanager.Request;

import com.atakmap.coremap.locale.LocaleUtil;

/**
 * Parcelable for uploading a file via FTP
 * 
 * 
 */
public class FtpStoreFileRequest extends RetryRequest {

    private static final String TAG = "FtpStoreFileRequest";

    private final String mFileToSend;
    private final String mHost;
    private final int mPort;
    private String mServerPath;
    private UserCredentials mCredentials;
    private final String mProtocol;

    /**
     * True for Active mode, False for Passive
     */
    private final boolean mActiveMode;

    /**
     * True for Binary transfer, False for ASCII
     */
    private final boolean mBinaryMode;

    /**
     * True to overwrite remote file, False to prompt user
     */
    private boolean mOverwrite;

    public static final String FTP_PROTO = "FTP";
    public static final String FTPS_PROTO = "FTPS";

    private String mCallbackAction;
    private Parcelable mCallbackExtra;

    /**
     *
     * @param proto
     * @param host
     * @param port
     * @param serverPath
     * @param credentials
     * @param fileToSend
     * @param activeMode
     * @param binaryMode
     * @param notificationId
     * @param retryCount
     * @param callbackAction
     * @param callbackExtra
     */
    public FtpStoreFileRequest(String proto, String host, int port,
            String serverPath, UserCredentials credentials,
            String fileToSend, boolean activeMode, boolean binaryMode,
            int notificationId, int retryCount,
            String callbackAction, Parcelable callbackExtra) {
        super(retryCount, notificationId);
        mProtocol = proto;
        mHost = host;
        mPort = port;
        mServerPath = serverPath;
        mCredentials = credentials;
        mFileToSend = fileToSend;
        mActiveMode = activeMode;
        mBinaryMode = binaryMode;
        mCallbackAction = callbackAction;
        mCallbackExtra = callbackExtra;
    }

    @Override
    public boolean isValid() {
        //credentials are optional
        return !FileSystemUtils.isEmpty(mProtocol) &&
                !FileSystemUtils.isEmpty(mHost) &&
                !FileSystemUtils.isEmpty(mServerPath) &&
                !FileSystemUtils.isEmpty(mFileToSend) &&
                super.isValid();
    }

    public String getProtocol() {
        return mProtocol;
    }

    public String getHost() {
        return mHost;
    }

    public int getPort() {
        return mPort;
    }

    public String getServerPath() {
        return mServerPath;
    }

    public void setServerPath(String path) {
        mServerPath = path;
    }

    public String getFileToSend() {
        return mFileToSend;
    }

    public UserCredentials getCredentials() {
        return mCredentials;
    }

    public boolean isActiveMode() {
        return mActiveMode;
    }

    public boolean isBinaryMode() {
        return mBinaryMode;
    }

    public boolean isOverwrite() {
        return mOverwrite;
    }

    public void setOverwrite(boolean b) {
        mOverwrite = b;
    }

    public boolean hasCallbackAction() {
        return !FileSystemUtils.isEmpty(mCallbackAction);
    }

    public String getCallbackAction() {
        return mCallbackAction;
    }

    public Parcelable getCallbackExtra() {
        return mCallbackExtra;
    }

    public boolean hasCallbackExtra() {
        return mCallbackExtra != null;
    }

    public boolean hasCredentials() {
        return mCredentials != null && mCredentials.isValid();
    }

    @Override
    public String toString() {
        return String.format(LocaleUtil.getCurrent(), "%s:%d/%s; %s", mHost,
                mPort,
                mServerPath, mFileToSend);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            super.writeToParcel(dest, flags);
            dest.writeString(mProtocol);
            dest.writeString(mHost);
            dest.writeInt(mPort);
            dest.writeString(mServerPath);
            dest.writeString(mFileToSend);
            dest.writeByte(mActiveMode ? (byte) 1 : (byte) 0);
            dest.writeByte(mBinaryMode ? (byte) 1 : (byte) 0);
            dest.writeByte(mOverwrite ? (byte) 1 : (byte) 0);

            if (hasCredentials()) {
                dest.writeByte((byte) 1);
                dest.writeParcelable(mCredentials, flags);
            } else {
                dest.writeByte((byte) 0);
            }

            if (hasCallbackAction()) {
                dest.writeByte((byte) 1);
                dest.writeString(mCallbackAction);
            } else {
                dest.writeByte((byte) 0);
            }

            if (hasCallbackExtra()) {
                dest.writeByte((byte) 1);
                dest.writeParcelable(mCallbackExtra, flags);
            } else {
                dest.writeByte((byte) 0);
            }
        }
    }

    public static final Parcelable.Creator<FtpStoreFileRequest> CREATOR = new Parcelable.Creator<FtpStoreFileRequest>() {
        @Override
        public FtpStoreFileRequest createFromParcel(Parcel in) {
            return new FtpStoreFileRequest(in);
        }

        @Override
        public FtpStoreFileRequest[] newArray(int size) {
            return new FtpStoreFileRequest[size];
        }
    };

    private FtpStoreFileRequest(Parcel in) {
        super(in);
        mProtocol = in.readString();
        mHost = in.readString();
        mPort = in.readInt();
        mServerPath = in.readString();
        mFileToSend = in.readString();
        mActiveMode = in.readByte() != 0;
        mBinaryMode = in.readByte() != 0;
        mOverwrite = in.readByte() != 0;

        if (in.readByte() != 0) {
            mCredentials = in.readParcelable(UserCredentials.class
                    .getClassLoader());
        }

        if (in.readByte() != 0) {
            mCallbackAction = in.readString();
        }

        if (in.readByte() != 0) {
            mCallbackExtra = in.readParcelable(FtpStoreFileRequest.class
                    .getClassLoader());
        }
    }

    public Request createUploadFileRequest() {
        Request request = new Request(
                NetworkOperationManager.REQUEST_TYPE_FTP_UPLOAD);
        request.put(FtpStoreFileOperation.PARAM_FILE, this);
        return request;
    }
}
