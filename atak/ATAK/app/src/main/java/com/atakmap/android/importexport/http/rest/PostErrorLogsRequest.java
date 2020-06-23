
package com.atakmap.android.importexport.http.rest;

import android.os.Parcel;
import android.os.Parcelable;
import com.atakmap.android.importexport.http.ErrorLogsClient;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.foxykeep.datadroid.requestmanager.Request;

/**
 *
 */
public class PostErrorLogsRequest implements Parcelable {

    private static final String TAG = "PostErrorLogsRequest";

    private final String mBaseUrl;
    private final boolean mBackground;
    private final String mExportFile;
    private final String mUid;
    private final String mCallsign;
    private final String mVersionName;
    private final String mVersionCode;
    private final int mNotificationId;

    public PostErrorLogsRequest(String baseUrl, boolean background,
            String exportFile, String uid,
            String callsign,
            String versionName, String versionCode,
            int notificationId) {
        mBaseUrl = baseUrl;
        mBackground = background;
        mExportFile = exportFile;
        mUid = uid;
        mCallsign = callsign;
        mVersionName = versionName;
        mVersionCode = versionCode;
        mNotificationId = notificationId;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(mBaseUrl) && mNotificationId >= 0;
    }

    public String getBaseUrl() {
        return mBaseUrl;
    }

    public boolean getBackground() {
        return mBackground;
    }

    public String getExportFile() {
        return mExportFile;
    }

    public String getUid() {
        return mUid;
    }

    public String getCallsign() {
        return mCallsign;
    }

    public String getVersionName() {
        return mVersionName;
    }

    public String getVersionCode() {
        return mVersionCode;
    }

    public int getNotificationId() {
        return mNotificationId;
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
            dest.writeByte((byte) (mBackground ? 1 : 0));
            dest.writeString(mExportFile);
            dest.writeString(mUid);
            dest.writeString(mCallsign);
            dest.writeString(mVersionName);
            dest.writeString(mVersionCode);
            dest.writeInt(mNotificationId);
        }
    }

    public static final Creator<PostErrorLogsRequest> CREATOR = new Creator<PostErrorLogsRequest>() {

        @Override
        public PostErrorLogsRequest createFromParcel(Parcel in) {
            return new PostErrorLogsRequest(in);
        }

        @Override
        public PostErrorLogsRequest[] newArray(int size) {
            return new PostErrorLogsRequest[size];
        }
    };

    protected PostErrorLogsRequest(Parcel in) {
        mBaseUrl = in.readString();
        mBackground = in.readByte() != 0;
        mExportFile = in.readString();
        mUid = in.readString();
        mCallsign = in.readString();
        mVersionName = in.readString();
        mVersionCode = in.readString();
        mNotificationId = in.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Create the request to post the error logs. Used by an asynch HTTP request Android
     * Service
     *
     * @return The request.
     */
    public Request createPostErrorLogsRequest() {
        Request request = new Request(
                ErrorLogsClient.REQUEST_TYPE_POST_ERROR_LOGS);
        request.put(PostErrorLogsOperation.PARAM_REQUEST, this);
        return request;
    }
}
