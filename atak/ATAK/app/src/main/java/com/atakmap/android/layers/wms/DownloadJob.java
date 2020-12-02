
package com.atakmap.android.layers.wms;

import android.os.Parcel;
import android.os.Parcelable;

import java.sql.Date;

public class DownloadJob implements Parcelable {

    public static final int CONNECTING = 1;
    public static final int DOWNLOADING = 2;
    public static final int COMPLETE = 3;
    public static final int CANCELLED = 4;
    public static final int ERROR = 5;

    private long id;
    private String name;
    private Date startTime;
    private Date completedTime;
    private Date lastStatusUpdate;
    private String source;
    private String destination;
    private int currentStatus;

    private DownloadJob() {
    }

    public DownloadJob(Parcel source) {
        String[] strData = new String[3];
        source.readStringArray(strData);
        this.name = strData[0];
        this.source = strData[1];
        this.destination = strData[2];

        this.currentStatus = source.readInt();

        long[] lngData = new long[4];
        source.readLongArray(lngData);
        this.id = lngData[0];
        this.startTime = new Date(lngData[1]);
        this.lastStatusUpdate = new Date(lngData[2]);
        this.completedTime = new Date(lngData[3]);

    }

    public long getId() {
        return id;
    }

    protected void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getCompletedTime() {
        return completedTime;
    }

    public void setCompletedTime(Date completedTime) {
        this.completedTime = completedTime;
    }

    public Date getLastStatusUpdate() {
        return lastStatusUpdate;
    }

    public void setLastStatusUpdate(Date lastStatusUpdate) {
        this.lastStatusUpdate = lastStatusUpdate;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public int getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(int currentStatus) {
        this.currentStatus = currentStatus;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringArray(new String[] {
                name, source, destination
        });
        dest.writeInt(currentStatus);
        dest.writeLongArray(new long[] {
                id, startTime.getTime(), lastStatusUpdate.getTime(),
                completedTime.getTime()
        });
    }

    public static final Parcelable.Creator<DownloadJob> CREATOR = new Parcelable.Creator<DownloadJob>() {

        @Override
        public DownloadJob createFromParcel(Parcel source) {
            return new DownloadJob(source);
        }

        @Override
        public DownloadJob[] newArray(int size) {
            return new DownloadJob[size];
        }

    };

}
