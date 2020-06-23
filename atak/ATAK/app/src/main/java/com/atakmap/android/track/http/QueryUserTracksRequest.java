
package com.atakmap.android.track.http;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.android.track.TrackHistoryDropDown;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.foxykeep.datadroid.requestmanager.Request;

/**
 * Parcelable for Query User Tracks request
 * 
 * 
 */
public class QueryUserTracksRequest implements Parcelable {

    private static final String TAG = "QueryUserTracksRequest";

    private final String mBaseUrl;
    private final int mNotificationId;
    private final String mCallsign;
    private final String mUid;
    private final long mStartTime;
    private final long mEndTime;

    public QueryUserTracksRequest(String baseUrl, int notificationId,
            String callsign, String uid, long startTime, long endTime) {
        mBaseUrl = baseUrl;
        mNotificationId = notificationId;
        mCallsign = callsign;
        mUid = uid;
        mStartTime = startTime;
        mEndTime = endTime;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(mBaseUrl)
                && !FileSystemUtils.isEmpty(mUid)
                && !FileSystemUtils.isEmpty(mCallsign)
                && mNotificationId >= 0
                && mStartTime >= 0
                && mEndTime >= 0;
    }

    public String getBaseUrl() {
        return mBaseUrl;
    }

    public int getNotificationId() {
        return mNotificationId;
    }

    public String getUid() {
        return mUid;
    }

    public String getCallsign() {
        return mCallsign;
    }

    public long getStartTime() {
        return mStartTime;
    }

    public long getEndTime() {
        return mEndTime;
    }

    @Override
    public String toString() {
        if (!isValid())
            return "";

        return mBaseUrl;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            dest.writeString(mBaseUrl);
            dest.writeInt(mNotificationId);
            dest.writeString(mUid);
            dest.writeString(mCallsign);
            dest.writeLong(mStartTime);
            dest.writeLong(mEndTime);
        }
    }

    public static final Creator<QueryUserTracksRequest> CREATOR = new Creator<QueryUserTracksRequest>() {
        @Override
        public QueryUserTracksRequest createFromParcel(Parcel in) {
            return new QueryUserTracksRequest(in);
        }

        @Override
        public QueryUserTracksRequest[] newArray(int size) {
            return new QueryUserTracksRequest[size];
        }
    };

    protected QueryUserTracksRequest(Parcel in) {
        mBaseUrl = in.readString();
        mNotificationId = in.readInt();
        mUid = in.readString();
        mCallsign = in.readString();
        mStartTime = in.readLong();
        mEndTime = in.readLong();
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
    public Request createQueryUserTracksRequest() {
        Request request = new Request(
                TrackHistoryDropDown.REQUEST_TYPE_GET_USER_TRACK);
        request.put(QueryUserTracksOperation.PARAM_QUERY, this);
        return request;
    }
}
