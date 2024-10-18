
package com.atakmap.android.http.rest;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.atakmap.android.http.rest.request.GetClientListRequest;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.NetConnectString;
import com.atakmap.spatial.kml.KMLUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.ArrayList;

/**
 * Represents a client/user that is or was connected to the specified server
 * TODO consider integrating with contact list and/or create some type of manager to keep this
 * info in sync with the server
 *
 *
 */
public class ServerContact implements Parcelable {

    private static final String TAG = "ServerContact";

    public enum LastStatus {
        Connected,
        Disconnected
    }

    private final NetConnectString server;

    private final String uid;
    private final String callsign;

    /**
     * Connected or Disconnected
     */
    private final LastStatus lastStatus;

    private final long lastEventTime;

    /**
     * Time this status was obtained from the server
     */
    private final long syncTime;

    /**
     * Last reported position update
     */
    private CotEvent lastReport;

    public ServerContact(NetConnectString server, String uid, String callsign,
            long lastEventTime, LastStatus lastStatus, long syncTime) {
        this.server = server;
        this.uid = uid;
        this.callsign = callsign;
        this.lastEventTime = lastEventTime;
        this.lastStatus = lastStatus;
        this.syncTime = syncTime;
    }

    public NetConnectString getServer() {
        return server;
    }

    public String getUID() {
        return uid;
    }

    public String getCallsign() {
        return callsign;
    }

    public long getLastEventTime() {
        return lastEventTime;
    }

    public LastStatus getLastStatus() {
        return lastStatus;
    }

    public long getSyncTime() {
        return syncTime;
    }

    public boolean hasLastReport() {
        return lastReport != null && lastReport.isValid();
    }

    public CotEvent getLastReport() {
        return lastReport;
    }

    public void setLastReport(CotEvent lastReport) {
        this.lastReport = lastReport;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(uid)
                && !FileSystemUtils.isEmpty(callsign)
                && lastEventTime > 0
                && syncTime > 0
                && server != null;
    }

    @Override
    public int hashCode() {
        int result = (uid == null) ? 0 : uid.hashCode();
        result = 31 * result + ((callsign == null) ? 0 : callsign.hashCode());
        result = 31 * result + ((server == null) ? 0 : server.hashCode());
        result = 31 * result
                + ((lastStatus == null) ? 0 : lastStatus.hashCode());
        result = 31 * result + (int) lastEventTime;
        result = 31 * result + (int) syncTime;
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ServerContact) {
            ServerContact c = (ServerContact) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(ServerContact rhsc) {
        // technically they could be invalid and equal, but we interested in valid ones
        if (!isValid() || !rhsc.isValid())
            return false;

        if (!FileSystemUtils.isEquals(uid, rhsc.uid))
            return false;

        if (!FileSystemUtils.isEquals(callsign, rhsc.callsign))
            return false;

        if (lastEventTime != rhsc.lastEventTime)
            return false;

        if (syncTime != rhsc.syncTime)
            return false;

        if (lastStatus != rhsc.lastStatus)
            return false;

        return server.equals(rhsc.server);
    }

    @NonNull
    @Override
    public String toString() {
        return uid + "/" + callsign;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

        if (isValid()) {
            dest.writeString(server.toString());
            dest.writeString(uid);
            dest.writeString(callsign);
            dest.writeLong(lastEventTime);
            dest.writeLong(syncTime);
            dest.writeString(lastStatus.toString());
            dest.writeByte(hasLastReport() ? (byte) 1 : (byte) 0);
            if (hasLastReport())
                dest.writeParcelable(lastReport, flags);
        }
    }

    public static final Creator<ServerContact> CREATOR = new Creator<ServerContact>() {
        @Override
        public ServerContact createFromParcel(Parcel in) {
            return new ServerContact(in);
        }

        @Override
        public ServerContact[] newArray(int size) {
            return new ServerContact[size];
        }
    };

    protected ServerContact(Parcel in) {
        server = NetConnectString.fromString(in.readString());
        uid = in.readString();
        callsign = in.readString();
        lastEventTime = in.readLong();
        syncTime = in.readLong();
        lastStatus = LastStatus.valueOf(in.readString());
        if (in.readByte() != 0) {
            lastReport = in.readParcelable(CotEvent.class.getClassLoader());
        } else {
            lastReport = null;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Create a server contact from a JSON object.
     * @param server the network connect string fro the server
     * @param syncTime the time of the sync in millis since epoch
     * @param obj the json object used to create the ServerContact
     * @return the server contact that corresponds with the json object
     * @throws JSONException if an error is encountered with the json object
     */
    public static ServerContact fromJSON(NetConnectString server,
            long syncTime, JSONObject obj) throws JSONException {

        String lastEventTimeString = obj.getString("lastEventTime");
        long lastEventTime;
        try {
            lastEventTime = KMLUtil.KMLDateTimeFormatterMillis.get()
                    .parse(lastEventTimeString).getTime();
        } catch (ParseException e) {
            Log.w(TAG, "Unable to parse lastEventTime: " + lastEventTimeString,
                    e);
            throw new JSONException("Unable to parse lastEventTime");
        }

        LastStatus lastStatus;
        try {
            lastStatus = LastStatus.valueOf(obj.getString("lastStatus"));
        } catch (IllegalArgumentException e) {
            Log.w(TAG,
                    "Unable to parse lastStatus: "
                            + obj.getString("lastStatus"),
                    e);
            throw new JSONException("Unable to parse lastStatus");
        }

        return new ServerContact(server,
                obj.getString("uid"),
                obj.getString("callsign"),
                lastEventTime,
                lastStatus,
                syncTime);
    }

    /**
     * Convert JSON to list of results
     * @param json the result json
     * @return an array of server contacts from the results
     * @throws JSONException an exception if the json is not valid
     */
    public static ArrayList<ServerContact> fromResultJSON(
            NetConnectString server, long syncTime, JSONObject json)
            throws JSONException {
        if (!json.has("type"))
            throw new JSONException("Missing type");
        String jtype = json.getString("type");
        if (!FileSystemUtils.isEquals(jtype,
                GetClientListRequest.CLIENT_LIST_MATCHER)) {
            throw new JSONException("Invalid type: " + jtype);
        }

        JSONArray array = json.getJSONArray("data");
        ArrayList<ServerContact> results = new ArrayList<>();
        if (array == null || array.length() < 1) {
            return results;
        }

        for (int i = 0; i < array.length(); i++) {
            ServerContact r = ServerContact.fromJSON(server, syncTime,
                    array.getJSONObject(i));
            if (r == null || !r.isValid())
                throw new JSONException("Invalid ServerContact");
            results.add(r);
        }

        return results;
    }
}
