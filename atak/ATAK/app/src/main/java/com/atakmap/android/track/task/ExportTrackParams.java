
package com.atakmap.android.track.task;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

/**
 * Must set bCurrentTrack, or track_dbids, or timeMillis
 *
 * 
 */
public class ExportTrackParams implements Parcelable {
    private static final String TAG = "ExportTrackParams";

    private final int notificationId;

    /**
     * UID of node to export tracks for
     */
    private final String uid;

    /**
     * File path for export
     */
    private String filePath;

    /**
     * User given name/label
     */
    private final String name;

    /**
     * Export format
     */
    private final String format;

    /**
     * True to export current track, false to export using track_dbids
     */
    private final boolean bCurrentTrack;

    private final long millisToExport;

    /**
     * Track segments to export
     */
    private int[] track_dbids;

    /**
     * If format is KML/Z, then include timestamps, regardless of user pref
     */
    public final boolean bForceKmlTimestamps;

    /**
     * Optional, intent action to send upon completion of export
     * This object will be passed as intent extra
     */
    private final String callbackAction;

    public ExportTrackParams(int notificationId, String uid, String name,
            String filePath, String format,
            boolean bCurrentTrack, String callbackAction) {
        this(notificationId, uid, name, filePath, format, bCurrentTrack,
                -1,
                null, callbackAction, false);
    }

    public ExportTrackParams(int notificationId, String uid, String name,
            String filePath, String format,
            int[] track_dbids, String callbackAction) {
        this(notificationId, uid, name, filePath, format, false, -1,
                track_dbids, callbackAction, false);
    }

    public ExportTrackParams(int notificationId, String uid, String name,
            String filePath, String format,
            long millisToExport, String callbackAction) {
        this(notificationId, uid, name, filePath, format, false,
                millisToExport, null, callbackAction, false);
    }

    public ExportTrackParams(int notificationId, String uid, String name,
            String filePath, String format,
            boolean bCurrentTrack, long millisToExport,
            int[] track_dbids, String callbackAction,
            boolean bForceKmlTimestamps) {
        this.notificationId = notificationId;
        this.uid = uid;
        this.name = name;
        this.filePath = filePath;
        this.format = format;
        this.bCurrentTrack = bCurrentTrack;
        this.millisToExport = millisToExport;
        this.track_dbids = track_dbids;
        this.callbackAction = callbackAction;
        this.bForceKmlTimestamps = bForceKmlTimestamps;
    }

    public boolean isValid() {
        if (FileSystemUtils.isEmpty(uid)) {
            Log.w(TAG, "No Export Track export UID specified");
            return false;
        }

        if (FileSystemUtils.isEmpty(name)) {
            Log.w(TAG, "No Export Track export name specified");
            return false;
        }

        if (!bCurrentTrack
                && (millisToExport < 1)
                && (track_dbids == null || track_dbids.length < 1)) {
            Log.w(TAG, "No Export Track IDs specified");
            return false;
        }

        if (FileSystemUtils.isEmpty(filePath)) {
            Log.w(TAG, "No Export Track export file path specified");
            return false;
        }

        if (FileSystemUtils.isEmpty(format)) {
            Log.w(TAG, "No Export Track export format specified");
            return false;
        }

        return true;
    }

    public boolean hasTrackIds() {
        return getTrackIdCount() > 0;
    }

    public boolean hasMillis() {
        return millisToExport > 0;
    }

    public long getMillisToExport() {
        return millisToExport;
    }

    public int getTrackIdCount() {
        if (track_dbids == null)
            return 0;

        return track_dbids.length;
    }

    public boolean hasCallbackAction() {
        return !FileSystemUtils.isEmpty(this.callbackAction);
    }

    public int getNotificationId() {
        return notificationId;
    }

    public String getUid() {
        return uid;
    }

    public String getName() {
        return name;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String fp) {
        filePath = fp;
    }

    public String getFormat() {
        return format;
    }

    public boolean isCurrentTrack() {
        return bCurrentTrack;
    }

    public int[] getTrack_dbids() {
        return track_dbids;
    }

    public void setTrack_dbids(int[] ids) {
        track_dbids = ids;
    }

    public String getCallbackAction() {
        return callbackAction;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        if (isValid()) {
            parcel.writeInt(notificationId);
            parcel.writeString(uid);
            parcel.writeString(name);
            parcel.writeString(filePath);
            parcel.writeString(format);
            parcel.writeByte((byte) (bCurrentTrack ? 1 : 0));
            parcel.writeByte((byte) (bForceKmlTimestamps ? 1 : 0));
            parcel.writeLong(millisToExport);
            if (hasCallbackAction()) {
                parcel.writeByte((byte) 1);
                parcel.writeString(callbackAction);
            } else {
                parcel.writeByte((byte) 0);
            }

            int cnt = getTrackIdCount();
            parcel.writeInt(cnt);
            if (cnt > 0) {
                for (int trackId : track_dbids)
                    parcel.writeInt(trackId);
            }
        }
    }

    public static final Parcelable.Creator<ExportTrackParams> CREATOR = new Parcelable.Creator<ExportTrackParams>() {
        @Override
        public ExportTrackParams createFromParcel(Parcel in) {
            int notificationId = in.readInt();
            String uid = in.readString();
            String name = in.readString();
            String filePath = in.readString();
            String format = in.readString();
            boolean bCurrentTrack = (in.readByte() != 0);
            boolean bForceKmlTimestamps = (in.readByte() != 0);
            long millis = in.readLong();
            boolean hasCallbackAction = (in.readByte() != 0);
            String callbakcAction = null;
            if (hasCallbackAction) {
                callbakcAction = in.readString();
            }

            int size = in.readInt();
            int[] trackIds = null;
            if (size > 0) {
                trackIds = new int[size];
                for (int i = 0; i < size; i++) {
                    trackIds[i] = in.readInt();
                }
            }

            return new ExportTrackParams(notificationId, uid, name,
                    filePath, format,
                    bCurrentTrack, millis,
                    trackIds, callbakcAction, bForceKmlTimestamps);
        }

        @Override
        public ExportTrackParams[] newArray(int size) {
            return new ExportTrackParams[size];
        }
    };

    @Override
    public String toString() {
        return name;
    }
}
