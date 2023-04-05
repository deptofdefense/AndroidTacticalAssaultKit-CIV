
package com.atakmap.android.http.rest.request;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.operation.GetCotEventOperation;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.foxykeep.datadroid.requestmanager.Request;

import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 */
public class GetCotEventRequest implements Parcelable {

    private static final String TAG = "GetCotEventRequest";
    public static final String COT_MATCHER = "<event";

    private final String mBaseUrl;
    private final String mUID;
    private final String mMatcher;
    private final String mTag;
    private final int mNotificationId;

    public GetCotEventRequest(String baseUrl, String uid, String matcher,
            String tag, int notificationId) {
        mBaseUrl = baseUrl;
        mUID = uid;
        mMatcher = matcher;
        mTag = tag;
        mNotificationId = notificationId;
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

    public String getMatcher() {
        return mMatcher;
    }

    public String getTag() {
        return mTag;
    }

    public int getNotificationId() {
        return mNotificationId;
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
            dest.writeString(mMatcher);
            dest.writeString(mTag);
            dest.writeInt(mNotificationId);
        }
    }

    public static final Creator<GetCotEventRequest> CREATOR = new Creator<GetCotEventRequest>() {
        @Override
        public GetCotEventRequest createFromParcel(Parcel in) {
            return new GetCotEventRequest(in);
        }

        @Override
        public GetCotEventRequest[] newArray(int size) {
            return new GetCotEventRequest[size];
        }
    };

    protected GetCotEventRequest(Parcel in) {
        mBaseUrl = in.readString();
        mUID = in.readString();
        mMatcher = in.readString();
        mTag = in.readString();
        mNotificationId = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public JSONObject toJSON() throws JSONException {
        if (!isValid())
            throw new JSONException("Invalid GetCotEventRequest");

        JSONObject json = new JSONObject();
        json.put("BaseUrl", mBaseUrl);
        json.put("UID", mUID);
        json.put("Matcher", mMatcher);
        json.put("Tag", mTag);
        json.put("NotificationId", mNotificationId);
        return json;
    }

    public static GetCotEventRequest fromJSON(JSONObject json)
            throws JSONException {
        return new GetCotEventRequest(
                json.getString("BaseUrl"),
                json.getString("UID"),
                json.getString("Matcher"),
                json.getString("Tag"),
                json.getInt("NotificationId"));
    }

    public Request createGetCotEvent() throws JSONException {
        Request request = new Request(
                NetworkOperationManager.REQUEST_TYPE_GET_COT_EVENT);
        request.put(GetCotEventOperation.PARAM_REQUEST, this.toJSON()
                .toString());
        return request;
    }
}
