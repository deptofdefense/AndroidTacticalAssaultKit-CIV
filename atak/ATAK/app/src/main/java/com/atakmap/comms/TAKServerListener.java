
package com.atakmap.comms;

import android.net.Uri;
import android.os.Bundle;

import com.atakmap.android.maps.MapView;
import com.atakmap.comms.app.CotPortListActivity;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple listener singleton for developer convenience
 *
 * For most development purposes we just need a list of TAK servers
 * So instead of having to create a new listener in each and every component
 * that wants the server list, just use this singleton
 * Also these methods return {@link TAKServer} objects instead of CotPort
 */
public class TAKServerListener {

    private static final String TAG = "TAKServerListener";

    private static TAKServerListener _instance;

    public static TAKServerListener getInstance() {
        return _instance;
    }

    private final CotStreamListener _serverListener;

    // Server connect string -> TAK server lookup cache
    private final Map<String, TAKServer> _serverMap = new HashMap<>();

    TAKServerListener(MapView view) {
        _serverListener = new CotStreamListener(view.getContext(), TAG, null) {
            @Override
            public CotPortListActivity.CotPort removeStream(Bundle b) {
                CotPortListActivity.CotPort server = super.removeStream(b);
                if (server == null)
                    return null;
                // Remove any reference to this server in the lookup map
                synchronized (_serverMap) {
                    List<String> toRemove = new ArrayList<>();
                    for (Map.Entry<String, TAKServer> e : _serverMap
                            .entrySet()) {
                        if (e.getValue() == server)
                            toRemove.add(e.getKey());
                    }
                    for (String key : toRemove)
                        _serverMap.remove(key);
                }
                return server;
            }
        };
        _instance = this;
    }

    /**
     * Dispose the listener - only to be called by {@link CommsMapComponent}
     */
    void dispose() {
        _serverListener.dispose();
        synchronized (_serverMap) {
            _serverMap.clear();
        }
    }

    /**
     * Get the full list of registered TAK servers
     *
     * @return Array of TAK servers
     */
    public TAKServer[] getServers() {
        return _serverListener.getServers();
    }

    public String getConnectedServerUrl() {
        return _serverListener.getConnectedServerUrl();
    }

    public synchronized boolean containsUrl(String url) {
        return _serverListener.containsUrl(url);
    }

    /**
     * Get a list of connected TAK servers
     *
     * @return Array of connected TAK servers
     */
    public TAKServer[] getConnectedServers() {
        List<TAKServer> connected = new ArrayList<>();
        TAKServer[] servers = getServers();
        if (servers != null) {
            for (TAKServer s : servers) {
                if (s.isConnected())
                    connected.add(s);
            }
        }
        return connected.toArray(new TAKServer[0]);
    }

    /**
     * Find a TAK server given a connect string or URL
     *
     * @param serverStr Server connect string or URL
     * @return TAK server or null if not found
     */
    public TAKServer findServer(String serverStr) {
        if (FileSystemUtils.isEmpty(serverStr))
            return null;

        synchronized (_serverMap) {
            TAKServer server = _serverMap.get(serverStr);
            if (server != null)
                return server;
        }

        // Parse string for server details (ignoring port)
        NetConnectString ncs;
        if (serverStr.contains("://")) {
            Uri uri = Uri.parse(serverStr);
            String proto = "tcp";
            String host = uri.getHost();
            int port = SslNetCotPort
                    .getServerApiPort(SslNetCotPort.Type.UNSECURE);
            if (uri.getScheme() != null && uri.getScheme().equals("https")) {
                proto = "ssl";
                port = SslNetCotPort
                        .getServerApiPort(SslNetCotPort.Type.SECURE);
            }
            ncs = new NetConnectString(proto, host, port);
        } else
            ncs = NetConnectString.fromString(serverStr);
        if (ncs == null)
            return null;
        String host = ncs.getHost();
        String proto = ncs.getProto();
        if (FileSystemUtils.isEmpty(host) || FileSystemUtils.isEmpty(proto))
            return null;

        final TAKServer[] servers = getServers();
        if (servers == null || servers.length < 1)
            return null;

        for (TAKServer server : servers) {
            NetConnectString pNCS = NetConnectString.fromString(
                    server.getConnectString());
            if (pNCS != null && !FileSystemUtils.isEmpty(pNCS.getHost())
                    && !FileSystemUtils.isEmpty(pNCS.getProto())
                    && proto.equals(pNCS.getProto())
                    && host.equals(pNCS.getHost())
                    && ncs.getPort() == pNCS.getPort()) {
                synchronized (_serverMap) {
                    _serverMap.put(serverStr, server);
                }
                return server;
            }
        }
        return null;
    }
}
