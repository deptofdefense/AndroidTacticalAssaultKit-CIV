
package com.atakmap.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.http.rest.HTTPRequestManager;
import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.app.CotPortListActivity;
import com.atakmap.coremap.log.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Support querying, downloading, importing TAK Server device profiles (Mission Packages)
 *
 * 
 */
public class DeviceProfileClient {

    protected static final String TAG = "DeviceProfileClient";

    public final static int REQUEST_TYPE_GET_PROFILE;

    static {
        REQUEST_TYPE_GET_PROFILE = NetworkOperationManager
                .register(
                        "com.atakmap.net.DeviceProfileOperation",
                        new DeviceProfileOperation());
    }

    /**
     * List of tool profiles received sent since ATAK startup
     */
    private final Set<String> toolProfileReceived;

    /**
     * Maintain ref to callbacks so they dont get garbage collected prior to processing
     */
    private final Map<String, DeviceProfileCallback> _pendingCallbacks;

    private SharedPreferences _prefs;

    private Context context;

    private static DeviceProfileClient instance = null;

    public static synchronized DeviceProfileClient getInstance() {
        if (instance == null) {
            instance = new DeviceProfileClient();
        }
        return instance;
    }

    public Context getContext() {
        return context;
    }

    private DeviceProfileClient() {
        toolProfileReceived = new HashSet<>();
        _pendingCallbacks = new HashMap<>();
    }

    public void dispose() {
        synchronized (this) {
            toolProfileReceived.clear();
            _pendingCallbacks.clear();
        }
    }

    /**
     * Check if a profile request has been received for the specified tool, since startup
     *
     * @param tool the tools name
     * @return true if the request has been received for the tool
     */
    public boolean hasProfileBeenReceived(String tool) {
        synchronized (this) {
            return toolProfileReceived.contains(tool);
        }
    }

    public void setProfileReceived(final String tool) {
        synchronized (this) {
            toolProfileReceived.add(tool);
        }
        Log.d(TAG, "setProfileReceived: " + tool);
    }

    public boolean callbackRecieved(DeviceProfileRequest request,
            boolean bSuccess) {
        if (request == null)
            return false;

        DeviceProfileCallback ret;
        synchronized (this) {
            //take care of this also, so we just have to sync once
            if (bSuccess && request.hasTool()) {
                setProfileReceived(request.getTool());
            }

            ret = _pendingCallbacks.remove(request.getId());
        }

        if (ret != null) {
            Log.d(TAG, "callbackRecieved: " + request);
            return true;
        } else {
            Log.d(TAG, "callbackRecieved not found: " + request);
            return false;
        }
    }

    /**
     * Get enrollment or connection device profile
     * Auto import the Mission Package
     * Invoke callback
     *
     * @param context the context to use
     * @param server the server to connect to
     * @param username the username
     * @param password the password
     * @param onEnrollment
     * @param onConnection
     * @param syncSecago    -1 to get all profile data. Positive value to get profile data published
     *                      within this amount of time
     * @param callback
     * @return true if request sent
     */
    public boolean getProfile(Context context, final String server,
            String username, String password, final boolean allowAllHostnames,
            final boolean onEnrollment, final boolean onConnection,
            long syncSecago,
            DeviceProfileCallback callback) {

        return getProfile(context, new DeviceProfileRequest(
                server, username, password, onEnrollment, onConnection,
                syncSecago, true, allowAllHostnames), callback);
    }

    /**
     * Request profile from server, if enabled in prefs. Note enrollment profile is always requested
     * regardless of ATAK pref
     *
     * @param context
     * @param deviceProfileRequest
     * @param callback
     * @return
     */
    private boolean getProfile(Context context,
            DeviceProfileRequest deviceProfileRequest,
            DeviceProfileCallback callback) {

        this.context = context;

        if (deviceProfileRequest == null) {
            Log.w(TAG, "getProfile request is null");
            return false;
        }

        if (!deviceProfileRequest.isValid()) {
            Log.w(TAG, "getProfile request invalid: "
                    + deviceProfileRequest);
            return false;
        }

        final SharedPreferences prefs = getPrefs(context);
        if (deviceProfileRequest.getOnConnect() &&
                !prefs.getBoolean("deviceProfileEnableOnConnect", false)) {
            Log.d(TAG, "getProfile disabled, skipping: "
                    + deviceProfileRequest);
            return false;
        }

        // Kick off async HTTP request to post to server
        Log.d(TAG, "getProfile request: " + deviceProfileRequest);
        if (callback != null) {
            synchronized (this) {
                _pendingCallbacks.put(deviceProfileRequest.getId(), callback);
            }
        }
        HTTPRequestManager.from(context).execute(
                deviceProfileRequest.createDeviceProfileRequest(), callback);
        return true;
    }

    /**
     * Get device profile for 'tool' with default settings
     * Auto import the Mission Package
     * Invoke callback
     *
     * @param context
     * @param server
     * @param tool
     * @param syncSecago    -1 to get all profile data. Positive value to get profile data published
     *                      within this amount of time
     * @param callback
     * @return true if request sent
     */
    public boolean getProfile(Context context, final String server,
            final String tool, long syncSecago,
            DeviceProfileCallback callback) {
        return getProfile(context, server, null, null, tool, null, syncSecago,
                true, null, callback);
    }

    /**
     * Get device profile for 'tool'
     * Invoke callback
     *
     * @param context
     * @param server
     * @param username
     * @param password
     * @param tool
     * @param syncSecago
     * @param autoImportProfile
     * @param callback
     * @return true if request sent
     */
    public boolean getProfile(Context context, final String server,
            String username, String password,
            final String tool, String filename, long syncSecago,
            boolean autoImportProfile, String outputPath,
            DeviceProfileCallback callback) {

        return getProfile(context, new DeviceProfileRequest(
                server, username, password, tool, filename, null,
                syncSecago, autoImportProfile, outputPath, false), callback);
    }

    /**
     * Get file from specified tool profile
     * Invoke callback
     *
     * @param context
     * @param server
     * @param username
     * @param password
     * @param tool
     * @param filename
     * @param callback
     * @return
     */
    public boolean getProfile(Context context, final String server,
            String username, String password,
            final String tool, String filename, String outputPath,
            DeviceProfileCallback callback) {

        return getProfile(context, new DeviceProfileRequest(
                server, username, password, tool, filename, null,
                -1, false, outputPath, false), callback);
    }

    /**
     * Get files from specified tool profile, if modified since specified time
     * Invoke callback
     *
     * @param context
     * @param server
     * @param username
     * @param password
     * @param tool
     * @param filenames
     * @param ifModifiedSince   [optional]
     * @param callback
     * @return
     */
    public boolean getProfile(Context context, final String server,
            String username, String password,
            final String tool, List<String> filenames,
            String outputPath, String ifModifiedSince,
            boolean displayNotification, DeviceProfileCallback callback) {

        return getProfile(context, new DeviceProfileRequest(
                server, username, password, tool, filenames, ifModifiedSince,
                -1, false, outputPath, displayNotification), callback);
    }

    /**
     * Get file from specified tool profile, if modified since specified time
     * Invoke callback
     *
     * @param context
     * @param server
     * @param username
     * @param password
     * @param tool
     * @param filename
     * @param ifModifiedSince   [optional]
     * @param callback
     * @return
     */
    public boolean getProfile(Context context, final String server,
            String username, String password,
            final String tool, String filename,
            String outputPath, String ifModifiedSince,
            boolean displayNotification, DeviceProfileCallback callback) {

        return getProfile(context, new DeviceProfileRequest(
                server, username, password, tool, filename, ifModifiedSince,
                -1, false, outputPath, displayNotification), callback);
    }

    /**
     * Get device profile for 'tool'
     * Auto import the Mission Package
     * Also track time last sync'ed to avoid downloading duplicate profiles
     *
     * @param context
     * @param server
     * @param tool
     */
    public boolean getProfile(Context context, final String server,
            final String tool) {

        this.context = context;

        final SharedPreferences prefs = getPrefs(context);

        // get our last sync time for this connection
        final String syncSecPrefName = "toolProfileSyncTime_" + tool + "_"
                + server;
        final long lastSyncTime = prefs.getLong(syncSecPrefName, 0);
        final long currSyncTime = System.currentTimeMillis() / 1000;
        long syncSecago = currSyncTime - lastSyncTime;

        //download and import tool profile mission package
        Log.d(TAG, "Checking for " + tool + " profile on: " + server
                + " since: " + syncSecago);
        return getProfile(context, server, null, null,
                tool, null, syncSecago, true, null,
                new DeviceProfileCallback(context) {
                    @Override
                    public void onDeviceProfileRequestComplete(boolean status,
                            Bundle bundle) {
                        Log.d(TAG, "onDeviceProfileRequestComplete status: "
                                + status);
                        if (status) {
                            //Log.d(TAG, "onDeviceProfileRequestComplete update sync time: " + currSyncTime);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putLong(syncSecPrefName, currSyncTime);
                            editor.apply();
                        }
                    }
                });
    }

    private SharedPreferences getPrefs(Context context) {
        if (_prefs == null) {
            _prefs = PreferenceManager.getDefaultSharedPreferences(context);
        }

        return _prefs;
    }

    /**
     * Request specified tool profile from all connected servers
     *
     * @param context
     * @param tool
     * @return  true if at least one request sent
     */
    public boolean getProfile(Context context, final String tool) {

        this.context = context;

        CotMapComponent inst = CotMapComponent.getInstance();
        if (inst == null) {
            Log.w(TAG, "getProfile: No server list available");
            return false;
        }

        final CotPortListActivity.CotPort[] servers = inst.getServers();
        if (servers == null || servers.length == 0) {
            Log.w(TAG, "getProfile: No servers available");
            return false;
        }

        Log.d(TAG, "Requesting profile: " + tool + " from server count: "
                + servers.length);

        boolean bRet = false;
        for (CotPortListActivity.CotPort server : servers) {
            if (server == null)
                continue;

            if (!server.isConnected()) {
                Log.d(TAG,
                        "getProfile not connected: "
                                + server.getConnectString());
                continue;
            }

            NetConnectString ncs = NetConnectString
                    .fromString(server.getConnectString());
            if (ncs == null) {
                Log.d(TAG,
                        "getProfile server invalid: "
                                + server.getConnectString());
                continue;
            }

            getProfile(context, ncs.getHost(), tool);
            bRet = true;
        }

        return bRet;
    }
}
