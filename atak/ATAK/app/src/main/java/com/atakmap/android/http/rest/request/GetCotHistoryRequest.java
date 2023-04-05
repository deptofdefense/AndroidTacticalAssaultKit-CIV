
package com.atakmap.android.http.rest.request;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.operation.GetCotHistoryOperation;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.foxykeep.datadroid.requestmanager.Request;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Get CoT history (list of CoT events)
 */
public class GetCotHistoryRequest implements Parcelable {

    private static final String TAG = "GetCotHistoryRequest";
    public static final String COT_MATCHER = "<events";

    private final String mBaseUrl, mUID, mMatcher, mTag;
    private final long mStart, mEnd;
    private final int mNotificationId;
    private final boolean mParse;

    public GetCotHistoryRequest(String baseUrl, String uid, long start,
            long end, String matcher, String tag, int notificationId,
            boolean parse) {
        mBaseUrl = baseUrl;
        mUID = uid;
        mMatcher = matcher;
        mTag = tag;
        mNotificationId = notificationId;
        mStart = start;
        mEnd = end;
        mParse = parse;
    }

    public GetCotHistoryRequest(String baseUrl, String uid, long start,
            long end, String matcher, String tag, int notificationId) {
        this(baseUrl, uid, start, end, matcher, tag, notificationId, true);
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(mBaseUrl) &&
                !FileSystemUtils.isEmpty(mUID) &&
                !FileSystemUtils.isEmpty(mMatcher) &&
                mNotificationId >= 0;
    }

    public String getBaseUrl() {
        return mBaseUrl;
    }

    public String getUID() {
        return mUID;
    }

    public long getStartTime() {
        return mStart;
    }

    public long getEndTime() {
        return mEnd;
    }

    public String getMatcher() {
        return mMatcher;
    }

    public String getTag() {
        return mTag;
    }

    public int getNotificationId() {
        return mNotificationId;
    }

    public boolean getParseEvents() {
        return mParse;
    }

    @NonNull
    @Override
    public String toString() {
        if (!isValid())
            return "";

        return mUID;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            dest.writeString(mBaseUrl);
            dest.writeString(mUID);
            dest.writeLong(mStart);
            dest.writeLong(mEnd);
            dest.writeString(mMatcher);
            dest.writeString(mTag);
            dest.writeInt(mNotificationId);
            dest.writeByte((byte) (mParse ? 1 : 0));
        }
    }

    public static final Creator<GetCotHistoryRequest> CREATOR = new Creator<GetCotHistoryRequest>() {
        @Override
        public GetCotHistoryRequest createFromParcel(Parcel in) {
            return new GetCotHistoryRequest(in);
        }

        @Override
        public GetCotHistoryRequest[] newArray(int size) {
            return new GetCotHistoryRequest[size];
        }
    };

    protected GetCotHistoryRequest(Parcel in) {
        mBaseUrl = in.readString();
        mUID = in.readString();
        mStart = in.readLong();
        mEnd = in.readLong();
        mMatcher = in.readString();
        mTag = in.readString();
        mNotificationId = in.readInt();
        mParse = in.readByte() != 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public JSONObject toJSON() throws JSONException {
        if (!isValid())
            throw new JSONException("Invalid " + TAG);

        JSONObject json = new JSONObject();
        json.put("BaseUrl", mBaseUrl);
        json.put("UID", mUID);
        json.put("StartTime", mStart);
        json.put("EndTime", mEnd);
        json.put("Matcher", mMatcher);
        json.put("Tag", mTag);
        json.put("NotificationId", mNotificationId);
        json.put("ParseEvents", mParse);
        return json;
    }

    public static GetCotHistoryRequest fromJSON(JSONObject json)
            throws JSONException {
        return new GetCotHistoryRequest(
                json.getString("BaseUrl"),
                json.getString("UID"),
                json.getLong("StartTime"),
                json.getLong("EndTime"),
                json.getString("Matcher"),
                json.getString("Tag"),
                json.getInt("NotificationId"),
                json.has("ParseEvents") && json.getBoolean("ParseEvents"));
    }

    public Request createGetCotHistory() throws JSONException {
        Request request = new Request(
                NetworkOperationManager.REQUEST_TYPE_GET_COT_HISTORY);
        request.put(GetCotHistoryOperation.PARAM_REQUEST, this.toJSON()
                .toString());
        return request;
    }
}
