
package com.atakmap.android.http.rest.request;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Simple HTTP REST operation for GET, PUT, DELETE
 */
public class SimpleHttpRequest implements Parcelable {

    private static final String TAG = "SimpleHttpRequest";

    public enum OpType {
        GET,
        PUT,
        DELETE
    }

    /**
     * Base URL of server
     */
    private final String mBaseUrl;

    /**
     * URL path, including query string params
     */
    private final String mPath;

    /**
     * An option tag used to identify this request
     */
    private final String mTag;

    /**
     * Type/Methods of HTTP operation
     */
    private final OpType mType;

    /**
     * String to match in response body
     */
    private final String mMatcher;

    /**
     * Expected HTTP Response code
     */
    private final int mMatcherCode;

    /**
     * Android notification ID
     */
    private final int mNotificationId;

    public SimpleHttpRequest(String baseUrl, String path, OpType type,
            String matcher, int matcherCode,
            int notificationId, String tag) {
        mBaseUrl = baseUrl;
        mPath = path;
        mType = type;
        mMatcher = matcher;
        mMatcherCode = matcherCode;
        mNotificationId = notificationId;
        mTag = tag;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(mBaseUrl) &&
                !FileSystemUtils.isEmpty(mPath) &&
                mType != null &&
                !FileSystemUtils.isEmpty(mMatcher) &&
                mMatcherCode >= 0 &&
                mNotificationId >= 0;
    }

    public String getBaseUrl() {
        return mBaseUrl;
    }

    public String getPath() {
        return mPath;
    }

    public OpType getType() {
        return mType;
    }

    public String getMatcher() {
        return mMatcher;
    }

    public int getMatcherCode() {
        return mMatcherCode;
    }

    public int getNotificationId() {
        return mNotificationId;
    }

    public String getTag() {
        return mTag;
    }

    @NonNull
    @Override
    public String toString() {
        if (!isValid())
            return "";

        return mType + " " + mPath;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            dest.writeString(mBaseUrl);
            dest.writeString(mPath);
            dest.writeString(mType.name());
            dest.writeString(mMatcher);
            dest.writeInt(mMatcherCode);
            dest.writeInt(mNotificationId);
            dest.writeString(mTag);
        }
    }

    public static final Creator<SimpleHttpRequest> CREATOR = new Creator<SimpleHttpRequest>() {
        @Override
        public SimpleHttpRequest createFromParcel(Parcel in) {
            return new SimpleHttpRequest(in);
        }

        @Override
        public SimpleHttpRequest[] newArray(int size) {
            return new SimpleHttpRequest[size];
        }
    };

    protected SimpleHttpRequest(Parcel in) {
        mBaseUrl = in.readString();
        mPath = in.readString();
        mType = OpType.valueOf(in.readString());
        mMatcher = in.readString();
        mMatcherCode = in.readInt();
        mNotificationId = in.readInt();
        mTag = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public JSONObject toJSON() throws JSONException {
        if (!isValid())
            throw new JSONException("Invalid PutRequest");

        JSONObject json = new JSONObject();
        json.put("BaseUrl", mBaseUrl);
        json.put("Path", mPath);
        json.put("Type", mType.name());
        json.put("Matcher", mMatcher);
        json.put("MatcherCode", mMatcherCode);
        json.put("NotificationId", mNotificationId);
        if (!FileSystemUtils.isEmpty("Tag"))
            json.put("Tag", mTag);
        return json;
    }

    public static SimpleHttpRequest fromJSON(JSONObject json)
            throws JSONException {
        String tag = null;
        if (json.has("Tag"))
            tag = json.getString("Tag");

        return new SimpleHttpRequest(
                json.getString("BaseUrl"),
                json.getString("Path"),
                OpType.valueOf(json.getString("Type")),
                json.getString("Matcher"),
                json.getInt("MatcherCode"),
                json.getInt("NotificationId"),
                tag);
    }
}
