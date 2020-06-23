
package com.atakmap.comms;

import android.os.Bundle;

import com.atakmap.app.preferences.json.JSONPreferenceControl;
import com.atakmap.app.preferences.json.JSONPreferenceSerializer;
import com.atakmap.coremap.log.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

/**
 * Serialize TAK server config to JSON
 */
public class TAKServerSerializer implements JSONPreferenceSerializer {

    private static final String TAG = "TAKServerSerializer";
    private static final String SERVERS_KEY = "takServers";

    private final List<TAKServer> _servers;

    public TAKServerSerializer(List<TAKServer> servers) {
        _servers = servers;
    }

    public TAKServerSerializer() {
        this(null);
    }

    @Override
    public boolean writeJSON(JSONObject root) {
        JSONArray arr = new JSONArray();
        try {
            List<TAKServer> servers = _servers != null ? _servers
                    : Arrays.asList(TAKServerListener.getInstance()
                            .getServers());
            for (TAKServer server : servers) {
                JSONObject o = new JSONObject();
                o.put(TAKServer.DESCRIPTION_KEY, server.getDescription());
                o.put(TAKServer.ENABLED_KEY, server.isEnabled());
                o.put(TAKServer.CONNECT_STRING_KEY, server.getConnectString());
                o.put(TAKServer.COMPRESSION_KEY, server.isCompressed());
                Bundle data = server.getData();
                if (data != null) {
                    writeData(o, data, "networkMeshKey");
                    writeData(o, data, "caLocation");
                    writeData(o, data, "certificateLocation");
                    writeData(o, data, "clientPassword");
                    writeData(o, data, "caPassword");
                    writeData(o, data, "enrollForCertificateWithTrust");
                }
                arr.put(o);
            }
            root.put(SERVERS_KEY, arr);
        } catch (Exception e) {
            Log.e(TAG, "Failed to write cloud servers JSON", e);
            return false;
        }

        return true;
    }

    private void writeData(JSONObject o, Bundle data, String key)
            throws JSONException {
        Object value = data.get(key);
        if (value != null)
            o.put(key, value);
    }

    @Override
    public boolean readJSON(JSONObject root) {
        if (!root.has(SERVERS_KEY))
            return false;
        boolean modified = false;
        try {
            JSONArray arr = root.getJSONArray(SERVERS_KEY);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Bundle data = new Bundle();
                readData(o, data, TAKServer.DESCRIPTION_KEY);
                readData(o, data, TAKServer.ENABLED_KEY);
                readData(o, data, TAKServer.CONNECT_STRING_KEY);
                readData(o, data, TAKServer.COMPRESSION_KEY);
                readData(o, data, "enrollForCertificateWithTrust");
                if (readAndRemove(o, data, "networkMeshKey")
                        || readAndRemove(o, data, "caLocation")
                        || readAndRemove(o, data, "certificateLocation")
                        || readAndRemove(o, data, "clientPassword")
                        || readAndRemove(o, data, "caPassword"))
                    modified = true;
                TAKServer server = new TAKServer(data);
                CommsMapComponent.getInstance().getCotService()
                        .addStreaming(server);
            }
            if (modified)
                root.put(JSONPreferenceControl.SAVE_MODIFIED, true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read cloud servers JSON", e);
            return false;
        }
        return true;
    }

    private boolean readData(JSONObject o, Bundle data, String key)
            throws JSONException {
        Object value = o.get(key);
        if (value instanceof String) {
            data.putString(key, (String) value);
            return true;
        } else if (value instanceof Boolean) {
            data.putBoolean(key, (Boolean) value);
            return true;
        }
        return false;
    }

    private boolean readAndRemove(JSONObject o, Bundle data, String key)
            throws JSONException {
        if (readData(o, data, key)) {
            o.remove(key);
            return true;
        }
        return false;
    }
}
