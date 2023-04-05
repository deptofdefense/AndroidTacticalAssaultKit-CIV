
package com.atakmap.comms;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class NetworkContact implements Parcelable {
    public static final String TAG = "NetworkContact";

    public static final Parcelable.Creator<NetworkContact> CREATOR = new Parcelable.Creator<NetworkContact>() {
        @Override
        public NetworkContact createFromParcel(Parcel source) {
            return new NetworkContact(source);
        }

        @Override
        public NetworkContact[] newArray(int size) {
            return new NetworkContact[size];
        }
    };

    private String uid;
    private String callsign;
    private String team, role;
    private NetConnectString connectionEndpoint;
    private int _version = 2; //0 - GeoChat, 1 - 2.2 and before, 2 - 2.3

    private long lastRefresh = 0L;
    private UpdateStatus status = UpdateStatus.DEAD;

    private final List<EventListener> listeners = new ArrayList<>();

    public NetworkContact(String uid, String callsign, String team,
            String role, NetConnectString endpoint) {
        this.uid = uid;
        this.callsign = callsign;
        this.team = team;
        this.role = role;
        this.connectionEndpoint = endpoint;
        if (endpoint != null) {
            Log.d(TAG, "new networkContact: " + this);
            this.connectionEndpoint.setCallsign(callsign); //double check
        }
        this.refresh(new CoordinatedTime().getMilliseconds());
    }

    public NetworkContact(String vmfParticipantID, String callsign) {
        this.uid = vmfParticipantID;
        this.callsign = callsign;
        this.connectionEndpoint = new NetConnectString("vmf", "", 0);
        this.refresh(new CoordinatedTime().getMilliseconds());
    }

    private NetworkContact(Parcel source) {
        uid = source.readString();
        callsign = source.readString();
        team = source.readString();
        role = source.readString();
        this.connectionEndpoint = NetConnectString.fromString(source
                .readString());
        if (this.connectionEndpoint != null) {
            Log.d(TAG, "new networkContact: " + this);
            this.connectionEndpoint.setCallsign(callsign); //double check
        }
    }

    public String toString() {
        return connectionEndpoint.toString();
    }

    public String getUID() {
        return uid;
    }

    public String getCallsign() {
        return callsign;
    }

    public String getTeam() {
        return team;
    }

    public String getRole() {
        return role;
    }

    public int getVersion() {
        return _version;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setVersion(int v) {
        _version = v;
    }

    /**
     * XXX - not sure why the uid is being used over group name, but when a group contact changes we
     * need to set this.
     */
    public void setUID(String uid) {
        this.uid = uid;
    }

    /**
     * In case some scheme ever arises where the callsign can change w/o changing the actual
     * contact.
     * 
     * @param callsign the callsign
     */
    public void setCallsign(final String callsign) {
        Log.d(TAG, "Set callsign for uid " + uid + " to " + callsign);
        this.callsign = callsign;
        if (connectionEndpoint != null) {
            connectionEndpoint.setCallsign(callsign);
        }
    }

    public NetConnectString getConnectString() {
        return connectionEndpoint;
    }

    public void setConnectString(final NetConnectString connectString) {
        Log.d(TAG,
                "Set connect string for uid " + uid + " to " + connectString);
        connectionEndpoint = connectString;
        connectionEndpoint.setCallsign(callsign);
    }

    public long getLastRefresh() {
        return lastRefresh;
    }

    public UpdateStatus getStatus() {
        return status;
    }

    public boolean isVMFContact() {
        return connectionEndpoint != null
                && connectionEndpoint.toString().contains("vmf");

    }

    public void refresh(long time) {
        long timeSince = time - lastRefresh;
        lastRefresh = time;

        // switch from non-current status to current status
        if (status != UpdateStatus.CURRENT) {
            for (EventListener l : listeners)
                l.onAlive(this);
        }

        status = UpdateStatus.CURRENT;

        if (task != null) {
            task.cancel();
        }

        for (EventListener l : listeners)
            l.onRefresh(this, timeSince);
    }

    /**
     * Equivalent to calling <code>stale(false, 0)</code>
     */
    public void stale() {
        this.stale(false, 0);
    }

    /**
     * Optionally starts a timer that automatically calls <code>die()</code> if no updates come from
     * this contact. The timer will fire after <code>delay</code> milliseconds.
     * 
     * @param startTimer if true, then then once the delay elapses, the timer calls die()
     * @param delay if <code>startTimer</code> is false has no relevance
     */
    public void stale(boolean startTimer, long delay) {
        if (status == UpdateStatus.CURRENT) {
            status = UpdateStatus.STALE;
            for (EventListener l : listeners) {
                l.onStale(this);
            }

            // check to see if a task is already scheduled
            if (task != null) {
                task.cancel();
            }

            if (startTimer) {
                t.schedule(task = new TimerTask() {
                    @Override
                    public void run() {
                        die();
                    }
                }, delay); // start timer delay ms
            }
        }
    }

    public void die() {
        if (status != UpdateStatus.DEAD) {
            status = UpdateStatus.DEAD;
            for (EventListener l : listeners)
                l.onDie(this);
        }
    }

    public void addListener(EventListener l) {
        if (l != null)
            listeners.add(l);
    }

    public void removeListener(EventListener l) {
        listeners.remove(l);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(uid);
        dest.writeString(callsign);
        dest.writeString(team);
        dest.writeString(role);
        dest.writeString(
                (this.connectionEndpoint == null) ? ""
                        : this.connectionEndpoint.toString());
    }

    @Override
    public int hashCode() {
        int result = uid.hashCode();
        result = 31 * result + ((callsign == null) ? 0 : callsign.hashCode());
        result = 31 * result + ((team == null) ? 0 : team.hashCode());
        result = 31 * result + ((role == null) ? 0 : role.hashCode());
        result = 31
                * result
                + ((connectionEndpoint == null) ? 0
                        : connectionEndpoint
                                .hashCode());
        result = 31 * result + _version;
        result = 31 * result + (int) (lastRefresh ^ (lastRefresh >>> 32));
        result = 31 * result + ((status == null) ? 0 : status.hashCode());
        result = 31 * result + listeners.hashCode();
        result = 31 * result + ((task == null) ? 0 : task.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof NetworkContact) {
            NetworkContact c = (NetworkContact) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(NetworkContact c) {
        if (uid != null)
            return uid.equals(c.uid);
        return false;
    }

    static private final Timer t = new Timer("stale-contact-timer");
    private TimerTask task = null;

    public interface EventListener {
        void onAlive(NetworkContact contact);

        void onDie(NetworkContact contact);

        void onStale(NetworkContact contact);

        void onRefresh(NetworkContact contact, long timeSinceLastRefresh);
    }

    public enum UpdateStatus {
        CURRENT,
        STALE,
        DEAD
    }
}
