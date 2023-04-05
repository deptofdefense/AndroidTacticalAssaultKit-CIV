
package com.atakmap.android.http.rest.request;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.operation.GetServerVersionOperation;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.foxykeep.datadroid.requestmanager.Request;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Get server version, optionally get full configuration
 *
 */
public class GetServerVersionRequest implements Parcelable {

    private static final String TAG = "GetServerVersionRequest";
    public static final String SERVER_VERSION_MATCHER = "TAK Server";
    public static final String SERVER_CONFIG_MATCHER = "ServerConfig";

    private final String mBaseUrl;
    private final String mServerConnectString;
    private final String mMatcher;
    private final int mNotificationId;
    private final boolean bGetConfig;

    public GetServerVersionRequest(String baseUrl, String serverConnectString,
            boolean getConfig, int notificationId) {
        this(baseUrl, serverConnectString, getConfig,
                getConfig ? SERVER_CONFIG_MATCHER : SERVER_VERSION_MATCHER,
                notificationId);
    }

    public GetServerVersionRequest(String baseUrl, String serverConnectString,
            boolean getConfig, String matcher, int notificationId) {
        mBaseUrl = baseUrl;
        mServerConnectString = serverConnectString;
        mMatcher = matcher;
        bGetConfig = getConfig;
        mNotificationId = notificationId;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(mBaseUrl) &&
                !FileSystemUtils.isEmpty(mServerConnectString) &&
                !FileSystemUtils.isEmpty(mMatcher);
    }

    public String getBaseUrl() {
        return mBaseUrl;
    }

    public String getServerConnectString() {
        return mServerConnectString;
    }

    public String getMatcher() {
        return mMatcher;
    }

    public boolean isGetConfig() {
        return bGetConfig;
    }

    public int getNotificationId() {
        return mNotificationId;
    }

    @NonNull
    @Override
    public String toString() {
        if (!isValid())
            return "";

        return mServerConnectString;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            dest.writeString(mBaseUrl);
            dest.writeString(mServerConnectString);
            dest.writeString(mMatcher);
            dest.writeInt(mNotificationId);
            dest.writeByte(bGetConfig ? (byte) 1 : (byte) 0);
        }
    }

    public static final Creator<GetServerVersionRequest> CREATOR = new Creator<GetServerVersionRequest>() {
        @Override
        public GetServerVersionRequest createFromParcel(Parcel in) {
            return new GetServerVersionRequest(in);
        }

        @Override
        public GetServerVersionRequest[] newArray(int size) {
            return new GetServerVersionRequest[size];
        }
    };

    protected GetServerVersionRequest(Parcel in) {
        mBaseUrl = in.readString();
        mServerConnectString = in.readString();
        mMatcher = in.readString();
        mNotificationId = in.readInt();
        bGetConfig = in.readByte() == 1;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public JSONObject toJSON() throws JSONException {
        if (!isValid())
            throw new JSONException("Invalid GetServerVersionRequest");

        JSONObject json = new JSONObject();
        json.put("BaseUrl", mBaseUrl);
        json.put("ServerConnectString", mServerConnectString);
        json.put("GetConfig", bGetConfig);
        json.put("Matcher", mMatcher);
        json.put("NotificationId", mNotificationId);
        return json;
    }

    public static GetServerVersionRequest fromJSON(JSONObject json)
            throws JSONException {
        return new GetServerVersionRequest(
                json.getString("BaseUrl"),
                json.getString("ServerConnectString"),
                json.getBoolean("GetConfig"),
                json.getString("Matcher"),
                json.getInt("NotificationId"));
    }

    public Request createServerVersion() throws JSONException {
        Request request = new Request(
                NetworkOperationManager.REQUEST_TYPE_GET_SERVER_VERSION);
        request.put(GetServerVersionOperation.PARAM_REQUEST, this.toJSON()
                .toString());
        return request;
    }
}
