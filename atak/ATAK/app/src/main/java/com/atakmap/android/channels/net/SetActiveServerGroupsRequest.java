
package com.atakmap.android.channels.net;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.foxykeep.datadroid.requestmanager.Request;

public class SetActiveServerGroupsRequest implements Parcelable {

    private static final String TAG = "ChannelsSetActiveServerGroupsRequest";

    private final String server;
    private final String connectString;
    private final String activeGroups;

    public SetActiveServerGroupsRequest(String connectString,
            String activeGroups) {
        NetConnectString ncs = NetConnectString.fromString(connectString);
        this.server = ncs.getHost();
        this.connectString = connectString;
        this.activeGroups = activeGroups;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(server)
                && !FileSystemUtils.isEmpty(activeGroups);
    }

    public String getServer() {
        return server;
    }

    public String getConnectString() {
        return connectString;
    }

    public String getActiveGroups() {
        return activeGroups;
    }

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
            dest.writeString(activeGroups);
        }
    }

    public static final Creator<SetActiveServerGroupsRequest> CREATOR = new Creator<SetActiveServerGroupsRequest>() {

        @Override
        public SetActiveServerGroupsRequest createFromParcel(Parcel in) {
            return new SetActiveServerGroupsRequest(in);
        }

        @Override
        public SetActiveServerGroupsRequest[] newArray(int size) {
            return new SetActiveServerGroupsRequest[size];
        }
    };

    protected SetActiveServerGroupsRequest(Parcel in) {
        server = in.readString();
        connectString = in.readString();
        activeGroups = in.readString();
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
    public Request createSetActiveServerGroupsRequest() {
        Request request = new Request(
                ServerGroupsClient.REQUEST_TYPE_SET_ACTIVE_SERVER_GROUPS);
        request.put(
                SetActiveServerGroupsOperation.PARAM_SET_ACTIVE_SERVER_GROUPS_REQUEST,
                this);
        return request;
    }
}
