
package com.atakmap.android.missionpackage.http.rest;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.android.missionpackage.http.MissionPackageDownloader;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.foxykeep.datadroid.requestmanager.Request;

/**
 * Parcelable for Query Mission Packages request
 * 
 * 
 */
public class QueryMissionPackageRequest implements Parcelable {

    private static final String TAG = "QueryMissionPackageRequest";

    private final String mServerConnectString;
    private final String mTool;
    private final int mNotificationId;

    public QueryMissionPackageRequest(String serverConnectString,
            int notificationId) {
        this(serverConnectString, notificationId, null);
    }

    public QueryMissionPackageRequest(String serverConnectString,
            int notificationId, String tool) {
        mServerConnectString = serverConnectString;
        mNotificationId = notificationId;
        mTool = tool;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(mServerConnectString)
                && mNotificationId >= 0;
    }

    public String getServerConnectString() {
        return mServerConnectString;
    }

    public int getNotificationId() {
        return mNotificationId;
    }

    public String getTool() {
        return mTool;
    }

    public boolean hasTool() {
        return !FileSystemUtils.isEmpty(mTool);
    }

    @Override
    public String toString() {
        if (!isValid())
            return "";

        return mServerConnectString + ", " + mTool;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            dest.writeString(mServerConnectString);
            dest.writeInt(mNotificationId);
            dest.writeString(mTool);
        }
    }

    public static final Creator<QueryMissionPackageRequest> CREATOR = new Creator<QueryMissionPackageRequest>() {
        @Override
        public QueryMissionPackageRequest createFromParcel(Parcel in) {
            return new QueryMissionPackageRequest(in);
        }

        @Override
        public QueryMissionPackageRequest[] newArray(int size) {
            return new QueryMissionPackageRequest[size];
        }
    };

    protected QueryMissionPackageRequest(Parcel in) {
        mServerConnectString = in.readString();
        mNotificationId = in.readInt();
        mTool = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Create the request to download the shared file. Used by an asynch HTTP request Android
     * Service
     * 
     * @return The request.
     */
    public Request createQueryMissionPackageRequest() {
        Request request = new Request(
                MissionPackageDownloader.REQUEST_TYPE_QUERY_MISSIONPACKAGE);
        request.put(QueryMissionPackageOperation.PARAM_QUERY, this);
        return request;
    }
}
