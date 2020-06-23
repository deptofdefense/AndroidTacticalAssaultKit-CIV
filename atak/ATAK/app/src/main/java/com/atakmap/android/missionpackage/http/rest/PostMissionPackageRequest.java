
package com.atakmap.android.missionpackage.http.rest;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.android.missionpackage.http.MissionPackageDownloader;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.foxykeep.datadroid.requestmanager.Request;

/**
 * Parcelable for POST File request
 * 
 * 
 */
public class PostMissionPackageRequest implements Parcelable {

    private static final String TAG = "PostPackageRequest";

    private final String mServerConnectString;
    private final String mHash;
    private final String mName;
    private final String mFilepath;
    private String mCreatorUid;
    private int mNotificationId;

    public PostMissionPackageRequest(String serverConnectString, String hash,
            String name,
            String creatorUid, String filepath) {
        mServerConnectString = serverConnectString;
        mHash = hash;
        mName = name;
        mFilepath = filepath;
        mCreatorUid = creatorUid;
        mNotificationId = -1;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(mServerConnectString)
                && !FileSystemUtils.isEmpty(mHash)
                && !FileSystemUtils.isEmpty(mName)
                && !FileSystemUtils.isEmpty(mCreatorUid)
                && !FileSystemUtils.isEmpty(mFilepath);
    }

    public String getServerConnectString() {
        return mServerConnectString;
    }

    public String getHash() {
        return mHash;
    }

    public String getName() {
        return mName;
    }

    public String getFilepath() {
        return mFilepath;
    }

    public String getCreatorUid() {
        return mCreatorUid;
    }

    public int getNotificationId() {
        return mNotificationId;
    }

    public void setNotificationId(int notificationId) {
        mNotificationId = notificationId;
    }

    @Override
    public String toString() {
        if (!isValid())
            return "";

        return mServerConnectString + ", " + mHash + ", " + mName + ", "
                + mFilepath;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            dest.writeString(mServerConnectString);
            dest.writeString(mHash);
            dest.writeString(mName);
            dest.writeString(mFilepath);
            dest.writeString(mCreatorUid);
            dest.writeInt(mNotificationId);
        }
    }

    public static final Creator<PostMissionPackageRequest> CREATOR = new Creator<PostMissionPackageRequest>() {
        @Override
        public PostMissionPackageRequest createFromParcel(Parcel in) {
            return new PostMissionPackageRequest(in);
        }

        @Override
        public PostMissionPackageRequest[] newArray(int size) {
            return new PostMissionPackageRequest[size];
        }
    };

    protected PostMissionPackageRequest(Parcel in) {
        mServerConnectString = in.readString();
        mHash = in.readString();
        mName = in.readString();
        mFilepath = in.readString();
        mCreatorUid = in.readString();
        mNotificationId = in.readInt();
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
    public Request createPostMissionPackageRequest() {
        Request request = new Request(
                MissionPackageDownloader.REQUEST_TYPE_POST_MISSIONPACKAGE);
        request.put(PostMissionPackageOperation.PARAM_POSTFILE, this);
        return request;
    }
}
