
package com.atakmap.android.importfiles.http;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.android.http.rest.request.GetFileRequest;
import com.atakmap.android.importfiles.resource.RemoteResource;

/**
 * Extends GetFileRequest to include the RemoteResource
 * 
 * 
 */
class RemoteResourceRequest extends GetFileRequest {

    private final RemoteResource _resource;

    RemoteResourceRequest(RemoteResource resource, String dir,
            int notificationId) {
        super(resource.getUrl(), resource.getName(), dir, notificationId);
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

    public static final Parcelable.Creator<RemoteResourceRequest> CREATOR = new Parcelable.Creator<RemoteResourceRequest>() {
        @Override
        public RemoteResourceRequest createFromParcel(Parcel in) {
            return new RemoteResourceRequest(in);
        }

        @Override
        public RemoteResourceRequest[] newArray(int size) {
            return new RemoteResourceRequest[size];
        }
    };

    private RemoteResourceRequest(Parcel in) {
        super(in);
        _resource = in.readParcelable(RemoteResource.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
