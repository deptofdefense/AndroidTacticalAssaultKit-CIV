
package com.atakmap.android.importfiles.http;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.android.http.rest.request.GetFileRequest;
import com.atakmap.android.http.rest.request.GetFilesRequest;
import com.atakmap.android.importfiles.resource.RemoteResource;

import java.util.List;

/**
 * Extends GetFilesRequest to include the RemoteResource
 * 
 * 
 */
class RemoteResourcesRequest extends GetFilesRequest {

    private final RemoteResource _resource;

    RemoteResourcesRequest(RemoteResource resource, String uid,
            List<GetFileRequest> requests, int notificationId) {
        super(uid, requests, notificationId);
        _resource = resource;
    }

    public RemoteResource getResource() {
        return _resource;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            super.writeToParcel(dest, flags);
            dest.writeParcelable(_resource, flags);
        }
    }

    public static final Parcelable.Creator<RemoteResourcesRequest> CREATOR = new Parcelable.Creator<RemoteResourcesRequest>() {
        @Override
        public RemoteResourcesRequest createFromParcel(Parcel in) {
            return new RemoteResourcesRequest(in);
        }

        @Override
        public RemoteResourcesRequest[] newArray(int size) {
            return new RemoteResourcesRequest[size];
        }
    };

    private RemoteResourcesRequest(Parcel in) {
        super(in);
        _resource = in.readParcelable(RemoteResource.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
