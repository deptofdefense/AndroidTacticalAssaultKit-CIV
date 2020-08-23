
package com.atakmap.android.video;

import android.content.Intent;

import com.atakmap.android.video.manager.VideoManager;
import com.atakmap.coremap.log.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class VideoIntent {

    private static final String TAG = "VideoIntent";

    /**
    * Given a DISPLAY intent, with all of the legacy parameters, create the appropriate video
    * ConnectionEntry.
    * @param intent the intent containing the legacy parameters
    * @return the ConnectionEntry extracted from the intent.
    */
    static ConnectionEntry parse(final Intent intent) {

        ConnectionEntry recvEntry = null;
        if (intent.hasExtra("videoUID")
                && !intent.getStringExtra("videoUID").equals("")) {
            String videoUID = intent.getStringExtra("videoUID");
            List<ConnectionEntry> entries1 = VideoManager.getInstance()
                    .getRemoteEntries();
            Map<String, ConnectionEntry> ret = new HashMap<>();
            for (ConnectionEntry entry : entries1)
                ret.put(entry.getUID(), entry);
            if (ret.containsKey(videoUID))
                recvEntry = ret.get(videoUID);
            else {
                //check for same alias if the uid isnt in the list
                if (intent.getStringExtra("videoUrl") != null) {
                    String url = intent.getStringExtra("videoUrl");
                    List<ConnectionEntry> conns = VideoManager.getInstance()
                            .getEntries();

                    for (ConnectionEntry ce : conns) {
                        if (ConnectionEntry.getURL(ce, false).equals(url)) {
                            recvEntry = ce;
                            break;
                        }
                    }
                }
            }

        } else if (intent.getStringExtra("videoUrl") != null) {
            // the user clicked on the button in the radial menu (from the map)
            String videoUrl = intent.getStringExtra("videoUrl");
            String aliasName = "new video";
            String uid = null;
            if (intent.hasExtra("uid")
                    && intent.getStringExtra("uid") != null
                    && !intent.getStringExtra("uid").contentEquals(""))
                uid = intent.getStringExtra("uid");
            if (intent.hasExtra("callsign")
                    && intent.getStringExtra("callsign") != null
                    && !intent.getStringExtra("callsign").contentEquals("")) {
                aliasName = intent.getStringExtra("callsign");
            } else if (uid != null)
                aliasName = uid;

            recvEntry = StreamManagementUtils.createConnectionEntryFromUrl(
                    aliasName, videoUrl);
            if (recvEntry != null) {
                if (uid != null)
                    recvEntry.setUID(uid);
                String buffer = intent.getStringExtra("buffer");
                if (buffer != null && !buffer.trim().equals(""))
                    recvEntry.setBufferTime(Integer.parseInt(buffer));
                String timeout = intent.getStringExtra("timeout");
                if (timeout != null && !timeout.trim().equals(""))
                    recvEntry.setNetworkTimeout(Integer.parseInt(timeout));
                // if the alias isnt in the list add it
                VideoManager.getInstance().addEntry(recvEntry);
                Log.d(TAG, "received url: " + recvEntry);
            }
        } else {
            // the callee has passed in a ConnectionEntry to use.
            recvEntry = (ConnectionEntry) intent
                    .getSerializableExtra(
                            ConnectionEntry.EXTRA_CONNECTION_ENTRY);
            Log.d(TAG, "received connection entry: " + recvEntry);
        }

        return recvEntry;

    }
}
