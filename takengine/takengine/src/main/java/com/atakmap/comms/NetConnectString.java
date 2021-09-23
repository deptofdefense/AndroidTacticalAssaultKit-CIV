
package com.atakmap.comms;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.HashMap;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;

/**
 * Representation of the individual parts of a Network Connection string that is passed from machine
 * to machine as a colon separated string.
 */
public class NetConnectString {

    private static final String TAG = "NetConnectString";
    private final String _proto;
    private final String _host;
    private final int _port;
    private String _callsign;

    /**
     * Construct a NetConnectString object
     * @param proto protocol is one of udp, tcp
     * @param host the address of the host
     * @param port the port to be used
     */
    public NetConnectString(final String proto, final String host,
            final int port) {
        _proto = proto;
        _host = host;
        _port = port;
    }

    /**
     * Obtains the protocol for the NetConnectString, either udp or tcp.
     * @return the protocol.
     */
    public String getProto() {
        return _proto;
    }

    /**
     * Obtains the host for the NetConnectString, can be numeric or by name.
     * @return the host
     */
    public String getHost() {
        return _host;
    }

    /**
     * Obtains the port for the NetConnectString
     * @return the port
     */
    public int getPort() {
        return _port;
    }

    public String getCallsign() {
        return _callsign;
    }

    public void setCallsign(final String callsign) {
        _callsign = callsign;
    }

    @Override
    public String toString() {
        if (_callsign == null) {
            return _host + ":" + _port + ":" + _proto;
        } else {
            return _host + ":" + _port + ":" + _proto + ":" + _callsign;
        }
    }

    // cache map for the production of NetConnectStrings
    private static final Map<String, NetConnectString> ncCache = new HashMap<>();

    /**
     * Given a connection string in the form [proto:]address:port[:callsign] produce a
     * NetworkConnectString.
     */
    public static NetConnectString fromString(final String connectString) {
        // does not need to waste cycles on synchronization. It really does not
        // matter if two copies are produced and inserted into the map.
        //Log.d("NetConnectString", "checking for string: " + connectString);

        // based on the error condition for parse, where a bad parse returns 
        // null, just return null
        if (connectString == null)
            return null;

        NetConnectString ncs = ncCache.get(connectString);
        if (ncs == null) {
            //Log.d("NetConnectString", "didn't find string: " + connectString + "; adding...");
            ncs = parseConnectString(connectString);
            ncCache.put(connectString, ncs);
        }

        return ncs;
    }

    private static NetConnectString parseConnectString(
            final String connectString) {

        if (FileSystemUtils.isEmpty(connectString)) {
            Log.w(TAG, "Failed to parse net connect string");
            return null;
        }

        String proto = null;
        String host;
        int port;

        NetConnectString result = null;

        try {
            String[] parts = connectString.split(":");
            if (parts.length >= 2)
                proto = parts[2].toLowerCase(LocaleUtil.getCurrent());
            if (parts[0] == null || parts[0].equals("")) {
                port = Integer.parseInt(parts[1]);
                host = "0.0.0.0";
            } else if (parts[1].contains("//")) {
                proto = parts[0];
                host = parts[1].replace("/", "");
                port = Integer.parseInt(parts[2]);
            } else {
                port = Integer.parseInt(parts[1]);
                host = parts[0];
            }
            result = new NetConnectString(proto, host, port);

            if (parts.length > 3) {
                result.setCallsign(parts[3]);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse net connect string: "
                    + (connectString == null ? "" : connectString), e);
            // failed... ignore will return null
        }

        return result;
    }

    @Override
    public boolean equals(Object other) {
        boolean r = false;
        if (other instanceof NetConnectString) {
            NetConnectString c = (NetConnectString) other;
            r = matches(c._proto, c._host, c._port);
        }
        return r;
    }

    public boolean matches(String proto, String host, int port) {
        return _proto.equals(proto) &&
                _host.equals(host) &&
                port == _port;
    }

    @Override
    public int hashCode() {
        int result = 31;
        if (_proto != null) {
            result = _proto.hashCode();
        }
        if (_host != null) {
            result = 31 * result + _host.hashCode();
        }
        result = 31 * result + _port;
        return result;
    }
}
