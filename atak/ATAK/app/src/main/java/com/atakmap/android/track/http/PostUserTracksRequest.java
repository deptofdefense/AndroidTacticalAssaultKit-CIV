
package com.atakmap.android.track.http;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.android.track.TrackHistoryDropDown;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.foxykeep.datadroid.requestmanager.Request;

/**
 * Parcelable for POST User Tracks request
 * 
 * 
 */
public class PostUserTracksRequest implements Parcelable {

    private static final String TAG = "PostUserTracksRequest";

    private final String mBaseUrl;
    private final int mNotificationId;
    private final String mCallsign;
    private final String mUid;
    private final String mCotType;
    private final String mGroupName;
    private final String mGroupRole;
    private String mCallbackAction;
    private Parcelable mCallbackExtra;

    /**
     * Local file path to upload
     */
    private final String mFilePath;

    public PostUserTracksRequest(String baseUrl, int notificationId,
            String callsign, String uid, String cotType, String groupName,
            String groupRole, String filePath, String callbackAction,
            Parcelable callbackExtra) {
        mBaseUrl = baseUrl;
        mNotificationId = notificationId;
        mCallsign = callsign;
        mUid = uid;
        mCotType = cotType;
        mGroupName = groupName;
        mGroupRole = groupRole;
        mFilePath = filePath;
        mCallbackAction = callbackAction;
        mCallbackExtra = callbackExtra;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(mBaseUrl)
                && !FileSystemUtils.isEmpty(mUid)
                && !FileSystemUtils.isEmpty(mCallsign)
                && mNotificationId >= 0
                && !FileSystemUtils.isEmpty(mCotType)
                && !FileSystemUtils.isEmpty(mGroupName)
                && !FileSystemUtils.isEmpty(mGroupRole)
                && FileSystemUtils.isFile(mFilePath);
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

    public String getCotType() {
        return mCotType;
    }

    public String getGroupName() {
        return mGroupName;
    }

    public String getGroupRole() {
        return mGroupRole;
    }

    public String getFilePath() {
        return mFilePath;
    }

    public boolean hasCallbackAction() {
        return !FileSystemUtils.isEmpty(mCallbackAction);
    }

    public String getCallbackAction() {
        return mCallbackAction;
    }

    public Parcelable getCallbackExtra() {
        return mCallbackExtra;
    }

    public boolean hasCallbackExtra() {
        return mCallbackExtra != null;
    }

    @Override
    public String toString() {
        String ret = "";
        if (!isValid())
            ret = "[invalid] ";

        return ret
                + String.format("%s, %s, %s, %s, %s, %s, %s, %s", mBaseUrl,
                        mCallsign, mUid, mCotType, mGroupName, mGroupRole,
                        mFilePath, mCallbackAction);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            dest.writeString(mBaseUrl);
            dest.writeInt(mNotificationId);
            dest.writeString(mUid);
            dest.writeString(mCallsign);
            dest.writeString(mCotType);
            dest.writeString(mGroupName);
            dest.writeString(mGroupRole);
            dest.writeString(mFilePath);

            if (hasCallbackAction()) {
                dest.writeByte((byte) 1);
                dest.writeString(mCallbackAction);
            } else {
                dest.writeByte((byte) 0);
            }

            if (hasCallbackExtra()) {
                dest.writeByte((byte) 1);
                dest.writeParcelable(mCallbackExtra, flags);
            } else {
                dest.writeByte((byte) 0);
            }
        }
    }

    public static final Creator<PostUserTracksRequest> CREATOR = new Creator<PostUserTracksRequest>() {
        @Override
        public PostUserTracksRequest createFromParcel(Parcel in) {
            return new PostUserTracksRequest(in);
        }

        @Override
        public PostUserTracksRequest[] newArray(int size) {
            return new PostUserTracksRequest[size];
        }
    };

    protected PostUserTracksRequest(Parcel in) {
        mBaseUrl = in.readString();
        mNotificationId = in.readInt();
        mUid = in.readString();
        mCallsign = in.readString();
        mCotType = in.readString();
        mGroupName = in.readString();
        mGroupRole = in.readString();
        mFilePath = in.readString();

        if (in.readByte() != 0) {
            mCallbackAction = in.readString();
        }

        if (in.readByte() != 0) {
            mCallbackExtra = in.readParcelable(PostUserTracksRequest.class
                    .getClassLoader());
        }
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
    public Request createPostUserTracksRequest() {
        Request request = new Request(
                TrackHistoryDropDown.REQUEST_TYPE_POST_USER_TRACK);
        request.put(PostUserTracksOperation.PARAM_QUERY, this);
        return request;
    }
}
