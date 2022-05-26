
package com.atakmap.net;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.foxykeep.datadroid.requestmanager.Request;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Support profile request types:
 *  onEnrollment    occurs when certificate enrollment completes
 *  onConnection    occurs when connected to TAK Server
 *  "tool"          occurs on demand, or optionally when connected to TAK Server
 *  "tool/file"     request a file or files, within a tool profile. occurs on demand
 *
 *  Supports "secAgo" so a given server profile is only downloaded/processed once
 *  Supports "autoImportProfile" true to import profile (Mission Package) via import manager, and
 *      then delete the file. Or false to just rather return path to where mission package file is
 *      on local filesystem, see bundle.get(DeviceProfileOperation.PARAM_PROFILE_OUTPUT_FILE)
 */
public class DeviceProfileRequest implements Parcelable {

    private static final String TAG = "DeviceProfileRequest";

    /**
     * A unique ID for this request
     */
    private final String id;

    /**
     * Display notification
     */
    private boolean displayNotification;

    /**
     * Required
     */
    private final String server;

    /**
     * Optional based on request. i.e. used for enrollment profile requests
     */
    private final String username;

    /**
     * Optional based on request. i.e. used for enrollment profile requests
     */
    private final String password;

    /**
     * Request enrollmen profile
     */
    private final boolean onEnrollment;

    /**
     * Request connection profile
     */
    private final boolean onConnect;

    /**
     * Only receive profile within this time span
     */
    private final long syncSecago;

    /**
     * Auto import profile (mission package)
     */
    private final boolean autoImportProfile;

    /**
     * Request profile for the specified 'tool'
     */
    private final String tool;

    /**
     * Optional, if not autoImportProfile, then place the file here
     */
    private final String outputPath;

    /**
     * Optional request parameter to GET files modified Since this time
     */
    private final String ifModifiedSince;

    /**
     * Request these fileapths within the 'tool' profile
     */
    private final List<String> filepaths;

    /**
     * If Response Content-Type is zip, contents are unzipped by default. This allows clients
     * to request zip in tact (not un-compressed)
     */
    private boolean doNotUnzip;

    private final boolean allowAllHostnames;

    public DeviceProfileRequest(String server, String username, String password,
            boolean onEnrollment, boolean onConnect, long syncSecago,
            boolean autoImportProfile, boolean allowAllHostnames) {
        this.id = UUID.randomUUID().toString();
        this.server = server;
        this.username = username;
        this.password = password;
        this.onEnrollment = onEnrollment;
        this.onConnect = onConnect;
        this.syncSecago = syncSecago;
        this.tool = null;
        this.ifModifiedSince = null;
        this.filepaths = null;
        this.autoImportProfile = autoImportProfile;
        this.outputPath = null;
        this.doNotUnzip = false;
        this.displayNotification = false;
        this.allowAllHostnames = allowAllHostnames;
    }

    public DeviceProfileRequest(String server, String username, String password,
            String tool, long syncSecago, boolean autoImportProfile) {

        this(server, username, password, tool, (String) null, null,
                syncSecago, autoImportProfile, null, false);
    }

    public DeviceProfileRequest(String server, String username, String password,
            String tool, String filepath, String ifModifiedSince,
            long syncSecago, boolean autoImportProfile, String outputPath,
            boolean displayNotification) {
        this(server, username, password, tool,
                FileSystemUtils.isEmpty(filepath) ? null
                        : Collections.singletonList(filepath),
                ifModifiedSince, syncSecago, autoImportProfile, outputPath,
                displayNotification);
    }

    public DeviceProfileRequest(String server, String username, String password,
            String tool, List<String> filepaths, String ifModifiedSince,
            long syncSecago, boolean autoImportProfile, String outputPath,
            boolean displayNotification) {
        this.id = UUID.randomUUID().toString();
        this.server = server;
        this.username = username;
        this.password = password;
        this.onEnrollment = false;
        this.onConnect = false;
        this.syncSecago = syncSecago;
        this.tool = tool;
        this.ifModifiedSince = ifModifiedSince;
        this.filepaths = filepaths;
        this.autoImportProfile = autoImportProfile;
        this.outputPath = outputPath;
        this.doNotUnzip = false;
        this.displayNotification = displayNotification;
        this.allowAllHostnames = true;
    }

    public boolean isValid() {
        return ((onEnrollment ^ onConnect) || !FileSystemUtils.isEmpty(tool))
                && !FileSystemUtils.isEmpty(server)
                && !FileSystemUtils.isEmpty(id);
    }

    public String getId() {
        return id;
    }

    public String getServer() {
        return server;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean hasCredentials() {
        return !FileSystemUtils.isEmpty(username)
                && !FileSystemUtils.isEmpty(password);
    }

    public boolean getOnEnrollment() {
        return onEnrollment;
    }

    public boolean getOnConnect() {
        return onConnect;
    }

    public boolean getAutoImportProfile() {
        return autoImportProfile;
    }

    public boolean hasTool() {
        return !FileSystemUtils.isEmpty(tool);
    }

    public String getTool() {
        return tool;
    }

    /**
     * Check if request has at least one pah
     * @return returns true if the request has at least one path.
     */
    public boolean hasFilepath() {
        return hasTool() && !FileSystemUtils.isEmpty(filepaths);
    }

    /**
     * Check if request has more than one path
     * @return returns true if the request has more than 1 path.
     */
    public boolean hasFilepaths() {
        return hasFilepath() && filepaths.size() > 1;
    }

    public List<String> getFilepaths() {
        return filepaths;
    }

    public boolean hasIfModifiedSince() {
        return !FileSystemUtils.isEmpty(ifModifiedSince);
    }

    public String getIfModifiedSince() {
        return ifModifiedSince;
    }

    public boolean hasOutputPath() {
        return !FileSystemUtils.isEmpty(outputPath);
    }

    public String getOutputPath() {
        return outputPath;
    }

    public boolean hasSyncSecago() {
        return syncSecago >= 0;
    }

    public long getSyncSecago() {
        return syncSecago;
    }

    public boolean isDoNotUnzip() {
        return doNotUnzip;
    }

    public void setDoNotUnzip(boolean b) {
        this.doNotUnzip = b;
    }

    public void setDisplayNotification(boolean displayNotification) {
        this.displayNotification = displayNotification;
    }

    public boolean isDisplayNotification() {
        return displayNotification;
    }

    public boolean isAllowAllHostnames() {
        return allowAllHostnames;
    }

    /**
     * Get short description/label of the request type
     * @return
     */
    public String getType() {
        if (hasFilepath())
            return tool + "/" + filepaths.size();
        else if (hasTool())
            return tool;
        else if (onEnrollment)
            return "Enrollment";
        else if (onConnect)
            return "Connection";
        else
            return "";
    }

    @Override
    public String toString() {
        return server + "/" + onEnrollment + "/" + onConnect + "/"
                + autoImportProfile + "/" + tool + "/" +
                (hasFilepath() ? filepaths.size() : "na")
                + "/" + ifModifiedSince + "/" + id;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            dest.writeString(id);
            dest.writeString(server);
            dest.writeString(username);
            dest.writeString(password);
            dest.writeByte((byte) (onEnrollment ? 1 : 0));
            dest.writeByte((byte) (onConnect ? 1 : 0));
            dest.writeByte((byte) (autoImportProfile ? 1 : 0));
            dest.writeByte((byte) (doNotUnzip ? 1 : 0));
            dest.writeByte((byte) (displayNotification ? 1 : 0));
            dest.writeByte((byte) (allowAllHostnames ? 1 : 0));

            dest.writeLong(syncSecago);
            dest.writeString(tool);
            dest.writeString(ifModifiedSince);
            dest.writeString(outputPath);

            if (FileSystemUtils.isEmpty(filepaths)) {
                dest.writeInt(0);
            } else {
                dest.writeInt(filepaths.size());
                for (String filepath : filepaths)
                    dest.writeString(filepath);
            }
        }
    }

    public static final Creator<DeviceProfileRequest> CREATOR = new Creator<DeviceProfileRequest>() {

        @Override
        public DeviceProfileRequest createFromParcel(Parcel in) {
            return new DeviceProfileRequest(in);
        }

        @Override
        public DeviceProfileRequest[] newArray(int size) {
            return new DeviceProfileRequest[size];
        }
    };

    protected DeviceProfileRequest(Parcel in) {
        id = in.readString();
        server = in.readString();
        username = in.readString();
        password = in.readString();
        onEnrollment = in.readByte() != 0;
        onConnect = in.readByte() != 0;
        autoImportProfile = in.readByte() != 0;
        doNotUnzip = in.readByte() != 0;
        displayNotification = in.readByte() != 0;
        allowAllHostnames = in.readByte() != 0;

        syncSecago = in.readLong();
        tool = in.readString();
        ifModifiedSince = in.readString();
        outputPath = in.readString();

        filepaths = new ArrayList<>();
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            filepaths.add(in.readString());
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Create the request to enroll for a certificate. Used by an asynch HTTP request Android
     * Service
     *
     * @return The request.
     */
    public Request createDeviceProfileRequest() {
        Request request = new Request(
                DeviceProfileClient.REQUEST_TYPE_GET_PROFILE);
        request.put(DeviceProfileOperation.PARAM_PROFILE_REQUEST, this);
        return request;
    }
}
