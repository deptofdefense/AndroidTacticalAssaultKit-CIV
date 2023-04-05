
package com.atakmap.android.http.rest.request;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.operation.GetFilesOperation;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.foxykeep.datadroid.requestmanager.Request;

import java.util.ArrayList;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;

/**
 * Parcelable for multiple simple File request
 * 
 * 
 */
public class GetFilesRequest implements Parcelable {

    private static final String TAG = "GetFilesRequest";

    private final String _uid;
    private final int _notificationId;
    private final List<GetFileRequest> _requests;

    /**
     * 
     * @param uid the unique identifier for the request
     * @param requests the list of individual requests that make up this larger bundled request
     */
    public GetFilesRequest(String uid, List<GetFileRequest> requests,
            int notificationId) {
        _uid = uid;
        _requests = requests;
        _notificationId = notificationId;
    }

    /**
     * Return the UID associated with the request.
     * @return the uid
     */
    public String getUID() {
        return _uid;
    }

    /**
     * The number of sub file requests associated with this request if the reuest is valid
     * @return 0 if the request is not valid otherwise the number of individual file requests.
     */
    public int getCount() {
        if (!isValid())
            return 0;

        return _requests.size();
    }

    /**
     * Get the notification id associated with the request
     * @return the integer notification id
     */
    public int getNotificationId() {
        return _notificationId;
    }

    public boolean isValid() {
        if (FileSystemUtils.isEmpty(_uid))
            return false;

        if (_requests == null || _requests.size() < 1)
            return false;

        for (GetFileRequest request : _requests) {
            if (request == null || !request.isValid())
                return false;
        }

        return true;
    }

    /**
     * The list of requests that make up this request.
     * @return the list that was used when the request was created.
     */
    public List<GetFileRequest> getRequests() {
        return _requests;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(LocaleUtil.getCurrent(), "%s Request count: %d",
                _uid,
                (_requests == null ? 0
                        : _requests.size()));
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            dest.writeString(_uid);
            dest.writeInt(_notificationId);
            dest.writeInt(_requests.size());
            for (GetFileRequest request : _requests)
                dest.writeParcelable(request, flags);
        }
    }

    public static final Parcelable.Creator<GetFilesRequest> CREATOR = new Parcelable.Creator<GetFilesRequest>() {
        @Override
        public GetFilesRequest createFromParcel(Parcel in) {
            return new GetFilesRequest(in);
        }

        @Override
        public GetFilesRequest[] newArray(int size) {
            return new GetFilesRequest[size];
        }
    };

    protected GetFilesRequest(Parcel in) {
        _uid = in.readString();
        _notificationId = in.readInt();
        int size = in.readInt();
        _requests = new ArrayList<>();
        for (int i = 0; i < size; i++)
            _requests
                    .add(in
                            .readParcelable(GetFileRequest.class
                                    .getClassLoader()));
    }

    @Override
    public int describeContents() {
        // TODO Auto-generated method stub
        return 0;
    }

    /**
     * Create the request to download the files. Used by an asynch HTTP request Android Service
     * 
     * @return The request.
     */
    public Request createGetFileRequests() {
        Request request = new Request(
                NetworkOperationManager.REQUEST_TYPE_GET_FILES);
        request.put(GetFilesOperation.PARAM_GETFILES, this);
        return request;
    }
}
