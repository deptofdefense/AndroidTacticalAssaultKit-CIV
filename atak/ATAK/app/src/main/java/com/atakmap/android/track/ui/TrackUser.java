
package com.atakmap.android.track.ui;

import com.atakmap.android.http.rest.ServerContact;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores details for a user which has tracks available e.g. in local DB or on TAK server
 *
 *
 */
public class TrackUser {

    private static final String TAG = "TrackUser";

    private final String callsign;
    private final String uid;
    private int numberTracks;

    public TrackUser(String callsign, String uid, int numberTracks) {
        this.callsign = callsign;
        this.uid = uid;
        this.numberTracks = numberTracks;
    }

    public String getCallsign() {
        return callsign;
    }

    public String getUid() {
        return uid;
    }

    public int getNumberTracks() {
        return numberTracks;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof TrackUser) {
            TrackUser c = (TrackUser) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(TrackUser c) {
        if (!FileSystemUtils.isEquals(uid, c.uid))
            return false;

        if (!FileSystemUtils.isEquals(callsign, c.callsign))
            return false;

        //do not compare track count for purposes of identifying a unique user

        return true;
    }

    @Override
    public int hashCode() {
        int hash = numberTracks;
        if (!FileSystemUtils.isEmpty(uid))
            hash += uid.hashCode();
        if (!FileSystemUtils.isEmpty(callsign))
            hash += callsign.hashCode();
        return 31 * hash;
    }

    @Override
    public String toString() {
        return callsign + " has " + numberTracks + " tracks";
    }

    public int increment() {
        return ++numberTracks;
    }

    public static List<TrackUser> convert(List<ServerContact> serverContacts) {
        List<TrackUser> users = new ArrayList<>();
        if (FileSystemUtils.isEmpty(serverContacts))
            return users;

        Log.d(TAG, "Converting server contact count: " + serverContacts.size());
        for (ServerContact sc : serverContacts) {
            if (sc == null || !sc.isValid()) {
                Log.w(TAG, "Skipping invalid server contact");
                continue;
            }

            TrackUser user = new TrackUser(sc.getCallsign(), sc.getUID(), 0);
            users.add(user);
        }

        return users;
    }

}
