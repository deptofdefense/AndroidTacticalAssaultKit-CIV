
package com.atakmap.android.http.rest.request;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.operation.GetClientListOperation;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.foxykeep.datadroid.requestmanager.Request;

import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 */
public class GetClientListRequest implements Parcelable {

    private static final String TAG = "GetClientListRequest";
    public static final String CLIENT_LIST_MATCHER = "com.bbn.marti.remote.ClientEndpoint";

    private final String mBaseUrl;
    private final String mServerConnectString;
    private final String mMatcher;
    private final int mNotificationId;

    public GetClientListRequest(String baseUrl, String serverConnectString,
            String matcher, int notificationId) {
        mBaseUrl = baseUrl;
        mServerConnectString = serverConnectString;
        mMatcher = matcher;
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
        }
    }

    public static final Creator<GetClientListRequest> CREATOR = new Creator<GetClientListRequest>() {
        @Override
        public GetClientListRequest createFromParcel(Parcel in) {
            return new GetClientListRequest(in);
        }

        @Override
        public GetClientListRequest[] newArray(int size) {
            return new GetClientListRequest[size];
        }
    };

    protected GetClientListRequest(Parcel in) {
        mBaseUrl = in.readString();
        mServerConnectString = in.readString();
        mMatcher = in.readString();
        mNotificationId = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public JSONObject toJSON() throws JSONException {
        if (!isValid())
            throw new JSONException("Invalid GetClientListRequest");

        JSONObject json = new JSONObject();
        json.put("BaseUrl", mBaseUrl);
        json.put("ServerConnectString", mServerConnectString);
        json.put("Matcher", mMatcher);
        json.put("NotificationId", mNotificationId);
        return json;
    }

    public static GetClientListRequest fromJSON(JSONObject json)
            throws JSONException {
        return new GetClientListRequest(
                json.getString("BaseUrl"),
                json.getString("ServerConnectString"),
                json.getString("Matcher"),
                json.getInt("NotificationId"));
    }

    public Request createGetClientList() throws JSONException {
        Request request = new Request(
                NetworkOperationManager.REQUEST_TYPE_GET_CLIENT_LIST);
        request.put(GetClientListOperation.PARAM_REQUEST, this.toJSON()
                .toString());
        return request;
    }
}
