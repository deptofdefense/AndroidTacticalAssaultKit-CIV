
package com.atakmap.android.cot;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.atakmap.android.http.rest.HTTPRequestManager;
import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.ServerContact;
import com.atakmap.android.http.rest.ServerVersion;
import com.atakmap.android.http.rest.operation.GetClientListOperation;
import com.atakmap.android.http.rest.operation.GetServerVersionOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.http.rest.request.GetClientListRequest;
import com.atakmap.android.http.rest.request.GetServerVersionRequest;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.android.util.ServerListDialog;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.TAKServer;
import com.atakmap.comms.TAKServerListener;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.CotStreamListener;
import com.atakmap.comms.app.CotPortListActivity;
import com.atakmap.net.DeviceProfileCallback;
import com.atakmap.net.DeviceProfileClient;
import com.foxykeep.datadroid.requestmanager.Request;
import com.foxykeep.datadroid.requestmanager.RequestManager;

import org.apache.http.HttpStatus;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CotMapServerListener extends CotStreamListener implements
        RequestManager.RequestListener {

    private static final String TAG = "CotMapServerListener";
    //TODO tighter integration with existing ATAK ContactList?

    /**
     * Store client list cache, for all connected servers
     * This is all users currently or previously connected, at the time the request was sent to the server
     * The server request is currently sent once per server, upon first successful connection
     * Subsequently connected users are available via Contacts.getInstance()
     */
    private final Map<String, List<ServerContact>> serverContactMap = new HashMap<>();
    private final Map<String, ServerVersion> serverVersionMap = new HashMap<>();

    /**
     * List of tool profiles to request upon connection to TAK Server
     */
    private final Set<String> toolProfileRequests = new HashSet<>();

    private final CotMapComponent _component;

    final SharedPreferences _prefs;

    private boolean deferredSetCotRemote = false;

    /**
     * ctor
     *
     * @param context
     */
    public CotMapServerListener(Context context, CotMapComponent component) {
        super(context, TAG, component);
        _component = component;
        _prefs = PreferenceManager.getDefaultSharedPreferences(_context);
        if (deferredSetCotRemote)
            _component.setCotRemote(getRemote());
    }

    @Override
    public void dispose() {
        DeviceProfileClient.getInstance().dispose();
        super.dispose();
    }

    @Override
    protected void serviceConnected() {
        Log.d(TAG, "onCotServiceConnected");

        if (_component == null) {
            // service connected prior to finishing construction
            deferredSetCotRemote = true;
        } else {
            _component.setCotRemote(getRemote());
        }
    }

    @Override
    protected void connected(CotPortListActivity.CotPort port,
            boolean connected) {
        super.connected(port, connected);

        String connectString = port.getConnectString();

        // not sure how the _component can be null at this point, but received a crash where it was.
        if (_component == null)
            return;

        _component.connected(port, connected);

        if (connected) {

            try {
                //get server version upon each connect
                getServerVersion(connectString, -1, false, this);
            } catch (JSONException e) {
                Log.w(TAG,
                        "Failed to get server version from: " + connectString,
                        e);
            }

            //get contact list, if we don't already have it
            boolean bHaveContactList = false;
            synchronized (serverContactMap) {
                if (serverContactMap.containsKey(connectString)) {
                    //Log.d(TAG, "Already have contacts for: " + connectString);
                    bHaveContactList = true;
                }
            }

            NetConnectString ncs = NetConnectString.fromString(connectString);
            if (ncs == null) {
                Log.w(TAG, "connected invalid: " + connectString);
                return;
            }

            if (!bHaveContactList) {
                //TODO server could go down for update, and api version change, currently client list
                //populates this value, and is only queried (successfully) once until ATAK is restarted

                //we don't yet have the contact list for this connection
                try {
                    getClientList(connectString, -1, this);
                } catch (JSONException e) {
                    Log.w(TAG,
                            "Failed to get client list from: " + connectString,
                            e);
                }

                try {
                    //TODO track if the profile was successfully received, rather than just requested
                    //see how we are tracking "tool" profiles
                    getProfileUpdates(ncs.getHost());
                } catch (Exception e) {
                    Log.w(TAG, "Failed to get profile from: " + connectString,

                            e);
                }
            }

            getToolProfileUpdates(ncs.getHost());
        }
    }

    private void getToolProfileUpdates(String host) {
        // do we want to pull down profile updates?
        if (!_prefs.getBoolean("deviceProfileEnableOnConnect", false)) {
            return;
        }

        //request tool profiles as necessary
        DeviceProfileClient client = DeviceProfileClient.getInstance();
        synchronized (toolProfileRequests) {
            for (String tool : toolProfileRequests) {
                //Note in case of multiple servers, its possible the profile will be requested
                //from more than one. But once one has been received/processed no more requests sent
                if (!client.hasProfileBeenReceived(tool)) {
                    client.getProfile(_context, host, tool);
                } else {
                    Log.d(TAG, "Already received profile for: " + tool);
                }
            }
        }
    }

    /**
     * Asynchronously get server version, and return to specified callback when ready
     *
     * @param serverConnectString
     * @param notificationId    use -1 for "silent mode"
     * @param callback
     * @throws JSONException
     */
    public synchronized void getServerVersion(final String serverConnectString,
            final int notificationId,
            final boolean bLegacy,
            final RequestManager.RequestListener callback)
            throws JSONException {

        String serverUrl = ServerListDialog.getBaseUrl(serverConnectString);
        GetServerVersionRequest request = new GetServerVersionRequest(serverUrl,
                serverConnectString, !bLegacy, notificationId);
        if (request == null || !request.isValid()) {
            Log.w(TAG, "Cannot get server version without valid request");
            return;
        }

        // notify user
        Log.d(TAG, "Server version request created for: " + request);

        if (notificationId >= 0) {
            NotificationUtil.getInstance().postNotification(
                    request.getNotificationId(),
                    NotificationUtil.GeneralIcon.ATAK.getID(),
                    NotificationUtil.BLUE,
                    _context.getString(R.string.notification_text1),
                    _context.getString(R.string.notification_text2a)
                            + request.getBaseUrl(),
                    _context.getString(R.string.notification_text2a)
                            + request.getBaseUrl());
        }

        // Kick off async HTTP request to post to server
        HTTPRequestManager.from(_context).execute(
                request.createServerVersion(), callback);
    }

    /**
     * Asynchronously get client list from specified server, and return to specified callback when ready
     *
     * @param serverConnectString
     * @param notificationId    use -1 for "silent mode"
     * @param callback
     * @throws JSONException
     */
    public void getClientList(String serverConnectString, int notificationId,
            RequestManager.RequestListener callback) throws JSONException {

        String serverUrl = ServerListDialog.getBaseUrl(serverConnectString);
        GetClientListRequest request = new GetClientListRequest(serverUrl,
                serverConnectString,
                GetClientListRequest.CLIENT_LIST_MATCHER, notificationId);
        if (request == null || !request.isValid()) {
            Log.w(TAG, "Cannot get client list without valid request");
            return;
        }

        // notify user
        Log.d(TAG, "Client list request created for: " + request);

        if (notificationId >= 0) {
            NotificationUtil.getInstance().postNotification(
                    request.getNotificationId(),
                    NotificationUtil.GeneralIcon.SYNC_ORIGINAL.getID(),
                    NotificationUtil.BLUE,
                    _context.getString(R.string.notification_text1),
                    _context.getString(R.string.notification_text2)
                            + request.getBaseUrl(),
                    _context.getString(R.string.notification_text2)
                            + request.getBaseUrl());
        }

        // Kick off async HTTP request to post to server
        HTTPRequestManager.from(_context).execute(
                request.createGetClientList(), callback);
    }

    /**
     * Get list of server contacts, for servers that this device has connected to, at least
     * once since ATAK startup. Does not reach out to server, rather checks cached contacts
     *
     * @param connectString Pass null to get contacts for all servers
     * @return the list of server contacts
     */
    public List<ServerContact> getServerContacts(String connectString) {
        List<ServerContact> contacts = new ArrayList<>();

        synchronized (serverContactMap) {
            if (!FileSystemUtils.isEmpty(connectString)) {
                contacts = serverContactMap.get(connectString);
            } else {
                for (Map.Entry<String, List<ServerContact>> cur : serverContactMap
                        .entrySet()) {
                    contacts.addAll(cur.getValue());
                }
            }
        }

        return contacts;
    }

    /**
     * Helper util to find a contact in a list, by UID
     * If multiple callsigns for a given UID, return most recent
     *
     * @param contacts the list of contacts
     * @param uid the uid to search on
     * @return returns the most recent contact that matches the UID.
     */
    public static ServerContact getContact(List<ServerContact> contacts,
            String uid) {
        if (FileSystemUtils.isEmpty(contacts) || FileSystemUtils.isEmpty(uid))
            return null;

        long recent = -1;
        ServerContact ret = null;
        for (ServerContact c : contacts) {
            if (uid.equals(c.getUID())) {
                if (c.getLastEventTime() >= recent) {
                    recent = c.getLastEventTime();
                    //Log.d(TAG, "Mapped " + uid + ", to " + c.toString());
                    ret = c;
                }
            }
        }

        return ret;
    }

    /**
     * Helper to find callsign of user in a list, by UID
     *
     * @param contacts the list of contacts
     * @param uid the uid for the contact to look for
     * @return the callsign of the matching contact
     */
    public static String getCallsign(List<ServerContact> contacts, String uid) {
        ServerContact c = getContact(contacts, uid);
        if (c == null || !c.isValid())
            return uid;

        return c.getCallsign();
    }

    public static String getServerCallsignFromList(
            List<ServerContact> contacts, String uid) {
        return CotMapServerListener.getCallsign(contacts, uid);
    }

    public ServerContact getContact(String serverConnectString, String uid) {
        return getContact(getServerContacts(serverConnectString), uid);
    }

    /**
     * Get the Server Version for the specific hostname e.g. gov.atakserver.com
     * Note, NetConnectString format is also accepted
     *
     * @param hostname the server hostname
     * @return the version of the server.
     */
    public synchronized ServerVersion getServerVersion(String hostname) {
        if (serverVersionMap == null || serverVersionMap.isEmpty())
            return null;

        //first check if user provided net connect string
        ServerVersion ver = serverVersionMap.get(hostname);
        if (ver != null) {
            return ver;
        }

        //now check for hostname match
        for (String key : serverVersionMap.keySet()) {
            NetConnectString s = NetConnectString.fromString(key);
            if (s != null && FileSystemUtils.isEquals(s.getHost(), hostname)) {
                return serverVersionMap.get(key);
            }
        }

        Log.w(TAG, "Unable to find server version for: " + hostname);
        return ver;
    }

    /**
     * Set server version and return previous value, or null if none set
     *
     * @param ver the server version to set the server to.
     * @return the previous value.
     */
    public synchronized ServerVersion setServerVersion(ServerVersion ver) {
        if (ver == null || !ver.isValid()) {
            Log.w(TAG, "setServerVersion invalid");
            return null;
        }

        Log.d(TAG, "setServerVersion: " + ver);

        //see if any missing details, favor new details
        ServerVersion existing = serverVersionMap.get(ver.getNetConnect());
        if (existing != null) {
            if (!ver.hasVersion() && existing.hasVersion())
                ver.setVersion(existing.getVersion());
            if (!ver.hasApiVersion() && existing.hasApiVersion())
                ver.setApiVersion(existing.getApiVersion());
        }

        Bundle b = new Bundle();
        b.putString("connect_string", ver.getNetConnect());
        if (ver.hasVersion())
            b.putString("version", ver.getVersion());
        if (ver.hasApiVersion())
            b.putInt("api", ver.getApiVersion());

        com.atakmap.android.metrics.MetricsApi.record("server", b);

        return serverVersionMap.put(ver.getNetConnect(), ver);

    }

    public String getCallsign(String serverConnectString, String uid) {
        return getCallsign(getServerContacts(serverConnectString), uid);
    }

    @Override
    public void onRequestFinished(Request request, Bundle resultData) {
        if (request
                .getRequestType() == NetworkOperationManager.REQUEST_TYPE_GET_CLIENT_LIST) {
            if (resultData == null) {
                Log.e(TAG,
                        "Server Contact List Failed - Unable to obtain results");
                NotificationUtil.getInstance().postNotification(
                        NotificationUtil.GeneralIcon.SYNC_ERROR.getID(),
                        NotificationUtil.RED,
                        _context.getString(R.string.notification_text3),
                        _context.getString(R.string.video_text5),
                        _context.getString(R.string.video_text5));
                return;
            }

            // the initial request that was sent out
            GetClientListRequest initialRequest = null;
            try {
                initialRequest = GetClientListRequest.fromJSON(
                        new JSONObject(request.getString(
                                GetClientListOperation.PARAM_REQUEST)));
            } catch (JSONException e) {
                Log.e(TAG, "Failed to serialize JSON", e);
            }

            if (initialRequest == null || !initialRequest.isValid()) {
                Log.e(TAG,
                        "Server Contact List Failed - Unable to parse request");
                NotificationUtil.getInstance().postNotification(
                        NotificationUtil.GeneralIcon.SYNC_ERROR.getID(),
                        NotificationUtil.RED,
                        _context.getString(R.string.notification_text3),
                        _context.getString(R.string.video_text9),
                        _context.getString(R.string.video_text9));
                return;

            }

            ArrayList<ServerContact> serverContacts = resultData
                    .getParcelableArrayList(
                            GetClientListOperation.PARAM_RESPONSE);
            if (FileSystemUtils.isEmpty(serverContacts)) {
                if (initialRequest.getNotificationId() > 0) {
                    Log.w(TAG,
                            "Server Contact List Failed - No contacts");
                    NotificationUtil.getInstance().postNotification(
                            NotificationUtil.GeneralIcon.SYNC_ERROR.getID(),
                            NotificationUtil.RED,
                            _context.getString(R.string.notification_text4),
                            _context.getString(R.string.notification_text5)
                                    + initialRequest.getBaseUrl(),
                            _context.getString(R.string.notification_text5)
                                    + initialRequest.getBaseUrl());
                    return;
                }
            } else {
                Log.d(TAG, "Got contact list of size " + serverContacts.size()
                        + " from: " + initialRequest.getServerConnectString());
                synchronized (serverContactMap) {
                    serverContactMap.put(
                            initialRequest.getServerConnectString(),
                            serverContacts);
                }
            }

            if (initialRequest.getNotificationId() > 0) {
                NotificationUtil.getInstance().postNotification(
                        initialRequest.getNotificationId(),
                        NotificationUtil.GeneralIcon.SYNC_SUCCESS.getID(),
                        NotificationUtil.GREEN,
                        _context.getString(R.string.notification_text6),
                        _context.getString(R.string.notification_text7)
                                + initialRequest.getBaseUrl(),
                        _context.getString(R.string.notification_text7)
                                + initialRequest.getBaseUrl());
            }
        } else if (request
                .getRequestType() == NetworkOperationManager.REQUEST_TYPE_GET_SERVER_VERSION) {
            if (resultData == null) {
                Log.e(TAG,
                        "Server Version Failed - Unable to obtain results");
                NotificationUtil.getInstance().postNotification(
                        NotificationUtil.GeneralIcon.SYNC_ERROR.getID(),
                        NotificationUtil.RED,
                        _context.getString(R.string.notification_text3a),
                        _context.getString(R.string.video_text5),
                        _context.getString(R.string.video_text5));
                return;
            }

            // the initial request that was sent out
            GetServerVersionRequest initialRequest = null;
            try {
                initialRequest = GetServerVersionRequest.fromJSON(
                        new JSONObject(request.getString(
                                GetServerVersionOperation.PARAM_REQUEST)));
            } catch (JSONException e) {
                Log.e(TAG, "Failed to serialize JSON", e);
            }

            if (initialRequest == null || !initialRequest.isValid()) {
                Log.e(TAG,
                        "Server Version Failed - Unable to parse request");
                NotificationUtil.getInstance().postNotification(
                        NotificationUtil.GeneralIcon.SYNC_ERROR.getID(),
                        NotificationUtil.RED,
                        _context.getString(R.string.notification_text3a),
                        _context.getString(R.string.video_text9),
                        _context.getString(R.string.video_text9));
                return;

            }

            String connectString = initialRequest.getServerConnectString();
            String response = resultData
                    .getString(GetServerVersionOperation.PARAM_RESPONSE);
            if (FileSystemUtils.isEmpty(response)) {
                Log.w(TAG, "Server Version Failed - 2");
                if (initialRequest.getNotificationId() > 0) {
                    NotificationUtil.getInstance().postNotification(
                            NotificationUtil.GeneralIcon.SYNC_ERROR.getID(),
                            NotificationUtil.RED,
                            _context.getString(R.string.notification_text4a),
                            _context.getString(R.string.notification_text4a)
                                    + initialRequest.getBaseUrl(),
                            _context.getString(R.string.notification_text4a)
                                    + initialRequest.getBaseUrl());
                    return;
                }
            } else {
                Log.d(TAG, "Got server version of size " + response.length()
                        + " from: " + connectString);

                ServerVersion version = null;

                //parse response
                if (!initialRequest.isGetConfig()) {
                    Log.d(TAG, "Parsed response version: " + response);
                    version = new ServerVersion(connectString, response);
                } else {
                    Log.d(TAG, "Parsed response config: " + response);

                    try {
                        version = ServerVersion.fromJSON(connectString,
                                new JSONObject(response));
                    } catch (JSONException e) {
                        Log.w(TAG, "Failed to parse server version response: "
                                + response, e);
                    }
                }

                if (version != null && version.isValid()
                        && version.hasVersion()) {
                    //update version on the persistent stream (local copy)
                    CotPortListActivity.CotPort input = findExistingStream(
                            connectString);
                    if (input != null) {
                        Log.d(TAG, _name + ":Set version: "
                                + version.getVersion());
                        input.setServerVersion(version);
                    }

                    //update version on the cot service stream (actual copy)
                    CommsMapComponent cinst = CommsMapComponent.getInstance();
                    if (cinst != null) {
                        cinst.setServerVersion(connectString, version);
                    }

                    // Update for TAKServerListener (used by various components)
                    TAKServer server = TAKServerListener.getInstance()
                            .findServer(connectString);
                    if (server != null)
                        server.setServerVersion(version);

                    //update version cache, for other components to query
                    CotMapComponent inst = CotMapComponent.getInstance();
                    if (inst != null) {
                        inst.setServerVersion(version);
                    } else {
                        Log.w(TAG,
                                "Unable to set server version for: "
                                        + initialRequest.getBaseUrl());
                    }
                } else {
                    Log.w(TAG, "Failed to parse version: " + response);
                }
            }

            if (initialRequest.getNotificationId() > 0) {
                NotificationUtil.getInstance().postNotification(
                        initialRequest.getNotificationId(),
                        NotificationUtil.GeneralIcon.SYNC_SUCCESS.getID(),
                        NotificationUtil.GREEN,
                        _context.getString(R.string.notification_text6a),
                        _context.getString(R.string.notification_text6a)
                                + initialRequest.getBaseUrl(),
                        _context.getString(R.string.notification_text6a)
                                + initialRequest.getBaseUrl());
            }
        }
    }

    @Override
    public void onRequestConnectionError(Request request,
            RequestManager.ConnectionError ce) {

        onError(NetworkOperation.getErrorMessage(ce));

        if (request
                .getRequestType() == NetworkOperationManager.REQUEST_TYPE_GET_SERVER_VERSION) {
            // the initial request that was sent out
            GetServerVersionRequest initialRequest = null;
            try {
                initialRequest = GetServerVersionRequest.fromJSON(
                        new JSONObject(request.getString(
                                GetServerVersionOperation.PARAM_REQUEST)));
            } catch (JSONException e) {
                Log.e(TAG, "Failed to serialize JSON", e);
            }

            if (initialRequest != null && initialRequest.isValid()
                    && initialRequest.isGetConfig()
                    && ce.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                //TAK Server <= 1.3.11 does not support the server config endpoint, so fall back
                // on legacy endpoint
                Log.d(TAG, "Get server version, fallback on legacy endpoint");
                try {
                    getServerVersion(initialRequest.getServerConnectString(),
                            initialRequest.getNotificationId(), true, this);
                } catch (JSONException e) {
                    Log.w(TAG, "Failed to send legacy request", e);
                }
            }
        }
    }

    @Override
    public void onRequestDataError(Request request) {
        onError("Request Data Error");
    }

    @Override
    public void onRequestCustomError(Request request, Bundle bundle) {
        onError("Request Custom Error");
    }

    private void onError(String message) {
        Log.e(TAG, "HTTP Operation Failed: " + message);
        //        NotificationUtil.getInstance().postNotification(
        //                NotificationUtil.GeneralIcon.SYNC_ERROR.getID(),
        //                "Server Sync Failure",
        //                message, message);
    }

    /**
     * Request the specified tool profile be downloaded from TAK Server
     * Note, could extend this to provide a DeviceProfileCallback, this currently uses a default impl
     * @param tool the tool profile name
     * @return true if the request is added
     */
    public boolean addToolProfileRequest(String tool) {
        Log.d(TAG, "addToolProfileRequest: " + tool);

        synchronized (toolProfileRequests) {
            return toolProfileRequests.add(tool);
        }
    }

    /**
     * Remove the request that a specific tool profile be downloaded from the TAK Sever
     * @param tool the tool profile name
     * @return true if the request is removed.
     */
    public boolean removeToolProfileRequest(String tool) {
        Log.d(TAG, "removeToolProfileRequest: " + tool);

        synchronized (toolProfileRequests) {
            return toolProfileRequests.remove(tool);
        }
    }

    private void getProfileUpdates(final String server) {
        // do we want to pull down profile updates?
        if (!_prefs.getBoolean("deviceProfileEnableOnConnect", false)) {
            return;
        }

        // get our last sync time for this connection
        final long lastSyncTime = _prefs.getLong(
                "deviceProfileOnConnectSyncTime" + server, 0);
        final long currSyncTime = System.currentTimeMillis() / 1000;

        // pull down any connection profiles updated since our last sync time. the first time
        // through, lastSyncTime will be 0 so the client will get all profiles
        DeviceProfileClient.getInstance().getProfile(_context,
                server, null, null, true, false, true,
                currSyncTime - lastSyncTime,
                new DeviceProfileCallback(_context) {
                    @Override
                    public void onDeviceProfileRequestComplete(boolean status,
                            Bundle resultData) {
                        Log.d(TAG, "onDeviceProfileRequestComplete: " + status);

                        if (status) {
                            // reset last sync time
                            SharedPreferences.Editor editor = _prefs.edit();
                            editor.putLong(
                                    "deviceProfileOnConnectSyncTime" + server,
                                    currSyncTime);
                            editor.apply();
                        }
                    }
                });
    }
}
