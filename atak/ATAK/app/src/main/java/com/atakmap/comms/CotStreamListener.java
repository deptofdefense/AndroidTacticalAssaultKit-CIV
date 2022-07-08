
package com.atakmap.comms;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.util.ServerListDialog;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.comms.app.CotPortListActivity;
import com.atakmap.comms.app.CotPortListActivity.CotPort;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to connect to CotService and keep track of
 * CoT Streams (e.g. TAK Server connections)
 */
public class CotStreamListener implements
        CotServiceRemote.OutputsChangedListener,
        CotServiceRemote.ConnectionListener {

    private static final String TAG = "CotStreamListener";

    protected Context _context;
    protected final String _name;
    private final CotServiceRemote _remote;

    /**
     * List of streams and their current state (e.g. enabled, connected)
     * Note, currently when a connection is disabled we remove the stream from the list
     */
    protected final List<CotPort> _streams;

    /**
     * ctor
     * @param context the context to use for the StreamListener
     * @param name The name of the stream listener
     * @param eventListener [optional]
     */
    public CotStreamListener(Context context, String name,
            CotServiceRemote.CotEventListener eventListener) {
        this(context, name, eventListener, true);
    }

    /**
     * ctor
     * @param context the context to use for the StreamListener
     * @param name The name of the stream listener
     * @param eventListener [optional]
     * @param bConnect True to connect immediately, false to manually do so later via init()
     */
    public CotStreamListener(Context context, String name,
            CotServiceRemote.CotEventListener eventListener, boolean bConnect) {
        _context = context;
        _name = name;
        _streams = new ArrayList<>();

        // connect to the service
        _remote = new CotServiceRemote();
        //get connection state callbacks
        _remote.setOutputsChangedListener(this);
        if (eventListener != null)
            _remote.setCotEventListener(eventListener);
        if (bConnect)
            init();
    }

    /**
     * Connect to the CoT Service.
     * Note callbacks may begin immediatly, so call after initialization is complete.
     * For example a child class implemented by a plugin may want the constructor to
     * complete before any callbacks are fired
     */
    protected void init() {
        _remote.connect(this);
    }

    /**
     * Invoked when connection to device's CoT service occurs
     */
    protected void serviceConnected() {
    }

    /**
     * Invoked when disconnection from device's CoT service occurs
     */
    protected void serviceDisconnected() {
    }

    /**
     * Invoked when connection to a TAK Server is established or disconnected
     * @param port the cot port that is connected or disconnected as specified by the connected parameter
     * @param connected true or false depending on the status
     */
    protected void connected(CotPortListActivity.CotPort port,
            boolean connected) {
    }

    /**
     * Invoked when connection to a TAK Server is enabled or disabled by user
     * @param port the connection to enable or disable.
     */
    protected void enabled(CotPortListActivity.CotPort port, boolean enabled) {
    }

    public CotServiceRemote getRemote() {
        return _remote;
    }

    public void dispose() {
        if (_remote != null) {
            _remote.disconnect();
        }
    }

    public void addStreams(Bundle[] bundles) {
        if (bundles != null && bundles.length > 0) {
            for (Bundle port : bundles) {
                if (port != null) {
                    CotStreamUpdateWraper wrapper = updateStream(port);

                    //notify child/impl classes of what changed
                    if (wrapper != null && wrapper.port != null) {
                        if (wrapper.enabledChanged()) {
                            enabled(wrapper.port, wrapper.enabled());
                        }

                        if (wrapper.connectedChanged()) {
                            connected(wrapper.port, wrapper.connected());
                        }
                    }
                }
            }
        }
    }

    protected synchronized CotPortListActivity.CotPort findExistingStream(
            String connectString) {
        if (FileSystemUtils.isEmpty(connectString))
            return null;

        for (CotPortListActivity.CotPort i : _streams) {
            //Log.d(TAG, _name + ":" + connectString + " compared to " + i.getConnectString());
            if (connectString.equalsIgnoreCase(i.getConnectString())) {
                //Log.d(TAG, _name + ": found a match: " + i);
                return i;
            }
        }
        return null;
    }

    protected static class CotStreamUpdateWraper {

        CotPortListActivity.CotPort port = null;
        Boolean isEnabled = null;
        Boolean isConnected = null;

        void setEnabled(boolean b) {
            isEnabled = b;
        }

        void setConnected(boolean b) {
            isConnected = b;
        }

        boolean enabledChanged() {
            return isEnabled != null;
        }

        boolean connectedChanged() {
            return isConnected != null;
        }

        boolean enabled() {
            return isEnabled;
        }

        boolean connected() {
            return isConnected;
        }
    }

    /**
     * Add or Update stream and return whether stream enabled or connection state changed
     * @param bundle the bundle that describes a stream
     * @return returns the wrapper
     */
    private synchronized CotStreamUpdateWraper updateStream(Bundle bundle) {
        if (bundle == null || bundle.size() < 1) {
            Log.w(TAG, _name + ":Cannot updateStream without bundle");
            return null;
        }

        String connectString = bundle
                .getString(CotPortListActivity.CotPort.CONNECT_STRING_KEY);
        if (FileSystemUtils.isEmpty(connectString)) {
            Log.w(TAG, _name + ":Cannot updateStream without connectString");
            return null;
        }

        //        Log.v(TAG,
        //                _name + ":Received update message for stream "
        //                        + connectString
        //                        + bundle.getString(CotPortListActivity.CotPort.DESCRIPTION_KEY)
        //                        + ": enabled="
        //                        + bundle.getBoolean(
        //                                CotPortListActivity.CotPort.ENABLED_KEY, true)
        //                        + ": connected="
        //                        + bundle.getBoolean(
        //                                CotPortListActivity.CotPort.CONNECTED_KEY, true));

        CotPortListActivity.CotPort port = findExistingStream(connectString);
        boolean bAdd = false;
        if (port == null) {
            Log.i(TAG, _name + ":updateStream adding NEW: " + connectString);
            port = new CotPortListActivity.CotPort(bundle);
            _streams.add(port);
            bAdd = true;
        } else {
            Log.i(TAG, _name + ":updateStream updating: " + connectString);
        }

        CotStreamUpdateWraper wrapper = null;
        if (port != null) {
            wrapper = new CotStreamUpdateWraper();
            wrapper.port = port;

            boolean enabled = bundle.getBoolean(
                    CotPortListActivity.CotPort.ENABLED_KEY, true);
            if (bAdd || enabled != port.isEnabled()) {
                port.setEnabled(enabled);
                wrapper.setEnabled(enabled);
                Log.i(TAG,
                        _name + ":updateStream set enabled: "
                                + port.isEnabled());
            }

            boolean connected = bundle.getBoolean(
                    CotPortListActivity.CotPort.CONNECTED_KEY, true);
            if (bAdd || connected != port.isConnected()) {
                port.setConnected(connected);
                wrapper.setConnected(connected);
                Log.i(TAG,
                        _name + ":updateStream set connected: "
                                + port.isConnected());
            }
        }

        return wrapper;
    }

    public synchronized CotPortListActivity.CotPort removeStream(
            Bundle bundle) {
        if (bundle == null || bundle.size() < 1) {
            Log.w(TAG, _name + ":Cannot removeStream without bundle");
            return null;
        }

        String connectString = bundle
                .getString(CotPortListActivity.CotPort.CONNECT_STRING_KEY);
        if (FileSystemUtils.isEmpty(connectString)) {
            Log.w(TAG, _name + ":Cannot removeStream without connectString");
            return null;
        }

        CotPortListActivity.CotPort input = findExistingStream(connectString);
        if (input != null) {
            Log.d(TAG, _name + ":Removing stream: " + connectString);
            _streams.remove(input);
            return input;
        } else {
            Log.w(TAG, _name + ":Couldn't find stream " + connectString
                    + " to remove");
            return null;
        }
    }

    /**
     * Check if we have at least one connection to a TAK server
     * @return true if there is at least one connection
     */
    public synchronized boolean isConnected() {
        for (CotPortListActivity.CotPort i : _streams) {
            if (i.isConnected()) {
                //Log.d(TAG, _name + ":" + i.toString() + " is connected");
                return true;
            }
        }

        return false;
    }

    /**
     * Check if currently connected to the specified server
     *
     * @param serverConnectString a NettConnectString describing a server
     * @return true if the connection string is connected.
     */
    public synchronized boolean isConnected(String serverConnectString) {
        for (CotPortListActivity.CotPort i : _streams) {
            if (FileSystemUtils.isEquals(i.getConnectString(),
                    serverConnectString) && i.isConnected()) {
                Log.d(TAG, _name + ":" + i + " is connected");
                return true;
            }
        }

        return false;
    }

    /**
     * Check if we have at least one connection to a TAK server enabled for connection attempt
     * @return returns true if the Stream is enabled.
     */
    public synchronized boolean isEnabled() {
        for (CotPortListActivity.CotPort i : _streams) {
            if (i.isEnabled()) {
                Log.d(TAG, _name + ":" + i + " is enabled");
                return true;
            }
        }

        return false;
    }

    /**
     * Obtains the description of the first encountered and connected/enabled server.
     */
    public synchronized String getFirstActiveDescription() {
        for (CotPortListActivity.CotPort i : _streams) {
            if (i.isEnabled() && i.isConnected()) {
                String s = i.getDescription();
                if (s.length() == 0)
                    s = "[no description supplied]";
                return s;
            }
        }
        return "[no connection]";
    }

    /**
     * Get URL of (the first) connected TAK Server
     * If none, then take first server in list
     * If none, return null
     * @return the first connected TAK Server.
     */
    public synchronized CotPortListActivity.CotPort getPrimaryConnection() {
        if (getStreamCount() < 1)
            return null;

        for (CotPortListActivity.CotPort i : _streams) {
            if (i.isConnected()) {
                Log.d(TAG, _name + ":" + i + " - is connected");
                return i;
            }
        }

        return _streams.get(0);
    }

    /**
     * Get base URL of Primary connection
     *  e.g. https://myserver.com
     *
     * @return the String describing the url of the primary connection otherwise null.
     */
    public String getConnectedServerUrl() {
        CotPortListActivity.CotPort port = getPrimaryConnection();
        if (port == null) {
            Log.d(TAG, _name + ":No streams available");
            return null;
        }

        NetConnectString ncs = NetConnectString.fromString(port
                .getConnectString());
        if (ncs == null) {
            Log.d(TAG, _name + ":Invalid stream info");
            return null;
        }

        if (CotServiceRemote.Proto.ssl.toString().equals(ncs.getProto()))
            return "https://" + ncs.getHost();
        else
            return "http://" + ncs.getHost();
    }

    public String getConnectedServerString() {
        CotPortListActivity.CotPort port = getPrimaryConnection();
        if (port == null) {
            Log.d(TAG, _name + ":No streams available");
            return null;
        }

        return port.getConnectString();
    }

    /**
     * Get list of servers
     * //TODO force these references to be immutable, or make copies...
     *
     * @return the list of servers
     */
    public synchronized CotPortListActivity.CotPort[] getServers() {
        if (getStreamCount() < 1)
            return null;

        return _streams
                .toArray(new CotPort[0]);
    }

    public synchronized boolean containsUrl(final String url) {
        if (getStreamCount() < 1)
            return false;

        for (CotPortListActivity.CotPort i : _streams) {
            if (FileSystemUtils.isEquals(url, ServerListDialog.getBaseUrl(i))) {
                //Log.d(TAG, _name + ":" + i.toString() + " - found");
                return true;
            }
        }

        return false;
    }

    public synchronized int getStreamCount() {
        return _streams.size();
    }

    @Override
    public void onCotServiceConnected(Bundle fullServiceState) {
        //Log.d(TAG, _name + ":onCotServiceConnected");
        serviceConnected();

        addStreams((fullServiceState == null ? null
                : (Bundle[]) fullServiceState.getParcelableArray("streams")));
    }

    @Override
    public void onCotServiceDisconnected() {
        //Log.d(TAG, _name + ":onCotServiceDisconnected");
        serviceDisconnected();
    }

    /**
     * Note, currently called by CotService when removed or disconnected
     *
     * CotServiceRemote.OutputsChangedListener.onCotOutputRemoved
     * @param descBundle the bundle that describes the stream.
     */
    @Override
    public void onCotOutputRemoved(final Bundle descBundle) {
        //Log.d(TAG, _name + ":onCotOutputRemoved");
        removeStream(descBundle);
    }

    /**
     * Note, currently called by CotService when added or connected
     *
     * CotServiceRemote.OutputsChangedListener.onCotOutputAdded
     * @param descBundle the bundle containing a description of the output that was updated.
     */
    @Override
    public void onCotOutputUpdated(Bundle descBundle) {
        //Log.d(TAG, _name + ":onCotOutputUpdated");
        if (!descBundle.getBoolean(CotPort.ISSTREAM_KEY, false))
            // We only track streams
            return;

        CotStreamUpdateWraper wrapper = updateStream(descBundle);

        //notify child/impl classes of what changed
        if (wrapper != null && wrapper.port != null) {
            if (wrapper.enabledChanged()) {
                enabled(wrapper.port, wrapper.enabled());
            }

            if (wrapper.connectedChanged()) {
                connected(wrapper.port, wrapper.connected());
            }
        }
    }
}
