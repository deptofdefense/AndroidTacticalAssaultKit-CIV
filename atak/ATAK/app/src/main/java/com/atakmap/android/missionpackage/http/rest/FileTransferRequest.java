
package com.atakmap.android.missionpackage.http.rest;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.android.http.rest.request.RetryRequest;
import com.atakmap.android.missionpackage.http.MissionPackageDownloader;
import com.atakmap.android.missionpackage.http.datamodel.FileTransfer;
import com.foxykeep.datadroid.requestmanager.Request;

/**
 * Parcelable for File Transfer request
 * 
 * 
 */
public class FileTransferRequest extends RetryRequest {

    private static final String TAG = "FileTransferRequest";

    /**
     * The file being shared (as sent to me via CoT)
     */
    private final FileTransfer mFileTransfer;

    private static final long DEFAULT_CONNECTION_TIMEOUT_MS = 1000;
    private static final long DEFAULT_TRANSFER_TIMEOUT_MS = 10000;
    private long mConnectionTimeoutMS;
    private long mTransferTimeoutMS;

    /**
     * ctor
     *
     * @param fileTransfer
     * @param retryCount
     * @param notificationId
     */
    public FileTransferRequest(FileTransfer fileTransfer, int retryCount,
            int notificationId) {
        this(fileTransfer, retryCount, notificationId,
                DEFAULT_CONNECTION_TIMEOUT_MS, DEFAULT_TRANSFER_TIMEOUT_MS);
    }

    // TODO This is the new constructor that delivers the transfer timeout through the
    // TODO serialized (parcelable) interface
    public FileTransferRequest(FileTransfer fileTransfer, int retryCount,
            int notificationId,
            long connectionTimeoutMS, long transferTimeoutMS) {
        super(retryCount, notificationId);
        mFileTransfer = fileTransfer;
        mConnectionTimeoutMS = connectionTimeoutMS;
        mTransferTimeoutMS = transferTimeoutMS;
    }

    @Override
    public boolean isValid() {
        return mFileTransfer != null && mFileTransfer.isValid()
                && super.isValid();
    }

    public FileTransfer getFileTransfer() {
        return mFileTransfer;
    }

    public long getTransferTimeoutMS() {
        return mTransferTimeoutMS;
    }

    public void setTransferTimeoutMS(int timeoutMS) {
        mTransferTimeoutMS = timeoutMS;
    }

    // TODO -FAB
    // Placeholder
    public long getConnectionTimeoutMS() {
        return mConnectionTimeoutMS;
    }

    public void setConnectionTimeoutMS(int timeoutMS) {
        mConnectionTimeoutMS = timeoutMS;
    }

    @Override
    public String toString() {
        if (mFileTransfer == null)
            return "";

        return mFileTransfer.toString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            super.writeToParcel(dest, flags);
            dest.writeParcelable(mFileTransfer, flags);
            dest.writeLong(mConnectionTimeoutMS);
            dest.writeLong(mTransferTimeoutMS);
        }
    }

    public static final Parcelable.Creator<FileTransferRequest> CREATOR = new Parcelable.Creator<FileTransferRequest>() {
        @Override
        public FileTransferRequest createFromParcel(Parcel in) {
            return new FileTransferRequest(in);
        }

        @Override
        public FileTransferRequest[] newArray(int size) {
            return new FileTransferRequest[size];
        }
    };

    protected FileTransferRequest(Parcel in) {
        super(in);
        mFileTransfer = in.readParcelable(FileTransfer.class.getClassLoader());
        mConnectionTimeoutMS = in.readLong();
        mTransferTimeoutMS = in.readLong();
    }

    @Override
    public int describeContents() {
        // TODO Auto-generated method stub
        return 0;
    }

    /**
     * Create the request to download the shared file. Used by an asynch HTTP request Android
     * Service
     * 
     * @return The request.
     */
    public Request createFileTransferDownloadRequest() {
        Request request = new Request(
                MissionPackageDownloader.REQUEST_TYPE_FILETRANSFER_GET_FILE);
        request.put(GetFileTransferOperation.PARAM_GETFILE, this);
        return request;
    }
}
