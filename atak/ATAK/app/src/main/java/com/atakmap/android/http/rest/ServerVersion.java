
package com.atakmap.android.http.rest;

import androidx.annotation.NonNull;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.http.rest.request.GetServerVersionRequest;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Tracks information about server connections
 *
 *             {
 *                 "version": "2",
 *                     "type": "ServerConfig",
 *                     "data": {
 *                 "version": "1.3.12.156-DEV",
 *                         "api": "2",
 *                         "hostname": "localhost"
 *                  }
 *             }
 *
 * 
 */
public class ServerVersion {

    private static final String TAG = "ServerVersion";
    private static final int INVALID = -1;
    public static final int MPT_TOOL_PARAM_MIN_VERSION = 2;

    private final String netConnect;
    private int apiVersion;
    private String version;

    public ServerVersion(String netConnect, String version) {
        this(netConnect, version, INVALID);
    }

    public ServerVersion(String netConnect, int apiVersion) {
        this(netConnect, null, apiVersion);
    }

    public ServerVersion(String netConnect, String version, int apiVersion) {
        this.netConnect = netConnect;
        this.version = version;
        this.apiVersion = apiVersion;
    }

    public int getApiVersion() {
        return apiVersion;
    }

    public String getNetConnect() {
        return netConnect;
    }

    public String getVersion() {
        return version;
    }

    public boolean hasVersion() {
        return !FileSystemUtils.isEmpty(version);
    }

    public void setVersion(String version) {
        this.version = version;
        Log.d(TAG, "setVersion: " + version);
    }

    public void setApiVersion(int apiVersion) {
        this.apiVersion = apiVersion;
        Log.d(TAG, "setApiVersion: " + apiVersion);
    }

    public boolean hasApiVersion() {
        return apiVersion != INVALID;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(netConnect);
    }

    @NonNull
    @Override
    public String toString() {
        if (!isValid())
            return "" + apiVersion;

        return netConnect + ", " + version + ", " + apiVersion;
    }

    public static ServerVersion fromJSON(String netConnect, JSONObject obj)
            throws JSONException {

        if (obj == null || !obj.has("version")) {
            Log.w(TAG, "invalid json");
            return new ServerVersion(netConnect, INVALID);
        }

        //Note version is on most server REST json endpoints
        ServerVersion version = new ServerVersion(netConnect,
                obj.getInt("version"));

        //Note ServerConfig json is returned on one specific server REST endpoint
        //Note obj.type == obj.data.api
        if (!obj.has("type") ||
                !FileSystemUtils.isEquals(obj.getString("type"),
                        GetServerVersionRequest.SERVER_CONFIG_MATCHER)) {
            Log.w(TAG, "invalid json type");
            return version;
        }

        if (!obj.has("data")) {
            Log.w(TAG, "invalid json data");
            return version;
        }

        JSONObject data = obj.getJSONObject("data");
        if (!data.has("version")) {
            Log.w(TAG, "invalid json version");
            return version;
        }

        version.setVersion(data.getString("version"));
        return version;
    }

    /**
     * Prior to TAK Server 1.3.6 API ver=2 the 'tool' param was not supported
     *
     * @param hostname hostname (not Server Connect String or base URL)
     * @return true if the tool parameter is needed for the hostname
     */
    public static boolean includeToolParam(String hostname) {
        ServerVersion ver = CotMapComponent.getInstance().getServerVersion(
                hostname);
        boolean bIncludeTool = false;
        if (ver != null && ver.isValid()) {
            bIncludeTool = ver
                    .getApiVersion() >= ServerVersion.MPT_TOOL_PARAM_MIN_VERSION;
            Log.d(TAG,
                    "query bIncludeTool=" + bIncludeTool + ", "
                            + ver);
        } else {
            Log.d(TAG, "Server version not set for: " + hostname);
            bIncludeTool = false;
        }

        return bIncludeTool;
    }
}
