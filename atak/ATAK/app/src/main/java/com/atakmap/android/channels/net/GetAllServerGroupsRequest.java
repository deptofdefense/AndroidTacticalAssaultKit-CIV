
package com.atakmap.android.channels.net;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.foxykeep.datadroid.requestmanager.Request;

public class GetAllServerGroupsRequest implements Parcelable {

    private static final String TAG = "ChannelsGetAllServerGroupsRequest";

    private final String server;
    private final String connectString;
    private final boolean sendLatestSA;

    public GetAllServerGroupsRequest(String connectString,
            boolean sendLatestSA) {
        NetConnectString ncs = NetConnectString.fromString(connectString);
        this.server = ncs.getHost();
        this.connectString = connectString;
        this.sendLatestSA = sendLatestSA;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(server);
    }

    public String getServer() {
        return server;
    }

    public String getConnectString() {
        return connectString;
    }

    public boolean getSendLatestSA() {
        return sendLatestSA;
    }

    @NonNull
    @Override
    public String toString() {
        if (!isValid())
            return "";

        return server;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            dest.writeString(server);
            dest.writeString(connectString);
            dest.writeInt(sendLatestSA ? 1 : 0);
        }
    }

    public static final Creator<GetAllServerGroupsRequest> CREATOR = new Creator<GetAllServerGroupsRequest>() {

        @Override
        public GetAllServerGroupsRequest createFromParcel(Parcel in) {
            return new GetAllServerGroupsRequest(in);
        }

        @Override
        public GetAllServerGroupsRequest[] newArray(int size) {
            return new GetAllServerGroupsRequest[size];
        }
    };

    protected GetAllServerGroupsRequest(Parcel in) {
        server = in.readString();
        connectString = in.readString();
        sendLatestSA = (in.readInt() == 1);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Create the request to enroll for a certificate. Used by an asynch HTTP request Android
     * Service
     *
     * @return The request.
     */
    public Request createGetAllServerGroupsRequest() {
        Request request = new Request(
                ServerGroupsClient.REQUEST_TYPE_GET_ALL_SERVER_GROUPS);
        request.put(
                GetAllServerGroupsOperation.PARAM_GET_ALL_SERVER_GROUPS_REQUEST,
                this);
        return request;
    }
}
