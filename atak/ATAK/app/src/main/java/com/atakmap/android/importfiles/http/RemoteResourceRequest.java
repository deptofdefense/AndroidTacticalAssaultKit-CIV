
package com.atakmap.android.importfiles.http;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.android.http.rest.request.GetFileRequest;
import com.atakmap.android.importfiles.resource.RemoteResource;

/**
 * Extends GetFileRequest to include the RemoteResource
 */
class RemoteResourceRequest extends GetFileRequest {

    private final RemoteResource _resource;
    private final boolean _showNotifications;

    RemoteResourceRequest(RemoteResource resource, String dir,
            int notificationId, boolean showNotifications) {
        this(resource, resource.getName(), dir, notificationId,
                showNotifications);
    }

    RemoteResourceRequest(RemoteResource resource, String fileName, String dir,
            int notificationId, boolean showNotifications) {
        super(resource.getUrl(), fileName, dir, notificationId);
        _resource = resource;
        _showNotifications = showNotifications;
    }

    public RemoteResource getResource() {
        return _resource;
    }

    public boolean showNotifications() {
        return _showNotifications;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            super.writeToParcel(dest, flags);
            dest.writeParcelable(_resource, flags);
            dest.writeByte((byte) (_showNotifications ? 1 : 0));
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
        _showNotifications = in.readByte() == 1;
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
