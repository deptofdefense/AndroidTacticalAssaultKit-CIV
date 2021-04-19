
package com.atakmap.android.video;

import android.net.Uri;

import com.atakmap.android.video.manager.VideoManager;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class ConnectionEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String TAG = "ConnectionEntry";

    public static final String EXTRA_CONNECTION_ENTRY = "CONNECTION_ENTRY";

    public enum Source {
        LOCAL_STORAGE,
        EXTERNAL
    }

    public enum Protocol {
        RAW("raw"),
        UDP("udp"),
        RTSP("rtsp", 554),
        RTMP("rtmp", 1935),
        RTMPS("rtmps", 443),
        HTTP("http", 80),
        HTTPS("https", 443),
        FILE("file"),
        TCP("tcp"),
        RTP("rtp"),
        DIRECTORY("dir"),
        SRT("srt");

        private final String proto;
        private final int defaultPort;

        Protocol(final String proto) {
            this(proto, -1);
        }

        Protocol(final String proto, final int defaultPort) {
            this.proto = proto;
            this.defaultPort = defaultPort;
        }

        public String toString() {
            return proto;
        }

        public String toURL() {
            return proto + "://";
        }

        public int getDefaultPort() {
            return this.defaultPort;
        }

        /**
         * Turns a string representation into a Protocol.
         */
        public static Protocol fromString(final String proto) {
            //            Log.d(TAG, "looking up: " + proto);
            for (Protocol p : Protocol.values()) {
                if (p.proto.equalsIgnoreCase(proto)) {
                    return p;
                }
            }
            Log.d(TAG, "using raw for: " + proto);
            return Protocol.RAW;
        }

        /**
         * Given a URL return the protocol that is represented
         */
        public static Protocol getProtocol(final String url) {
            if (url == null)
                return null;

            for (Protocol p : Protocol.values()) {
                if (url.startsWith(p.toURL())) {
                    return p;
                }
            }
            return null;

        }

    }

    private String alias = "";
    private String uid;
    private String address = null;
    private String macAddress = null;
    private int port = -1;
    private int roverPort = -1;
    private boolean ignoreEmbeddedKLV = false;
    private String path = "";

    /**
     * Fortify has flagged this as Password Management: Hardcoded Password
     * This is a empty assignment just for the purposes of making the code simpler instead of
     * extra null pointer checks.    This is not hardcoded.
     */
    private String passphrase = "";

    private Protocol protocol = Protocol.UDP;
    private Source source = Source.LOCAL_STORAGE;

    private int networkTimeout = 5 * 1000;
    private int bufferTime = -1;
    private int rtspReliable = 0;

    // The local file for this entry
    private File localFile;

    // List of child entries (only applies to Protocol == DIRECTORY)
    private transient Set<String> childrenUIDs;

    // Parent entry (must be directory)
    private transient String parentUID;

    // Temporary video alias
    private transient boolean temporary;

    /**
     * Construct a connection entry that describes all of the fields that could potentially be
     * stored to describe a video source.
     * 
     * @param alias a human readable alias for the video stream.
     * @param address the IPv4 address for the video stream.
     * @param macAddress optional MAC address in the form XX:XX:XX:XX:XX:XX identifying the specific
     *            interface where the traffic should be from. This is only applied to MULTICAST UDP
     *            traffic and only applied if the MAC address provided resolves to an interface that
     *            is configured and is up. The MAC address can additionally be null, which will mean
     *            that the system default will be used instead of a particular interface for the
     *            multicast video.
     * @param port the port used for the specified video stream.
     * @param roverPort UDP sideband metadata traffic from a L3 Com Video Downlink Receiver, provide the
     *        additional rover port, -1 if there is no data.
     * @param path for rtsp and http, provide the path to use when connecting to the stream.
     * @param protocol the protocol for the video stream.
     * @param networkTimeout as specified in milliseconds (should be 5000).
     * @param bufferTime as specified in milliseconds (should be -1 unless required).
     * @param passphrase passphrase to access the source (empty string if not needed)
     * @param source the source of the saved data, either LOCAL_STORAGE or EXTERNAL (by default
     *            should be LOCAL_STORAGE).
     */
    public ConnectionEntry(final String alias,
            final String address,
            final String macAddress,
            final int port,
            final int roverPort,
            final String path,
            final Protocol protocol,
            final int networkTimeout,
            final int bufferTime,
            final int rtspReliable,
            final String passphrase,
            final Source source) {
        this();
        this.alias = alias;
        this.address = address;
        this.macAddress = macAddress;
        this.port = port;
        this.roverPort = roverPort;
        this.path = path;
        this.protocol = protocol;
        this.networkTimeout = networkTimeout;
        this.bufferTime = bufferTime;
        this.rtspReliable = rtspReliable;
        this.passphrase = passphrase;
        this.source = source;
    }

    /**
     * Construct a connection entry that describes all of the fields that could potentially be
     * stored to describe a video source.
     *
     * @param alias a human readable alias for the video stream.
     * @param address the IPv4 address for the video stream.
     * @param macAddress optional MAC address in the form XX:XX:XX:XX:XX:XX identifying the specific
     *            interface where the traffic should be from. This is only applied to MULTICAST UDP
     *            traffic and only applied if the MAC address provided resolves to an interface that
     *            is configured and is up. The MAC address can additionally be null, which will mean
     *            that the system default will be used instead of a particular interface for the
     *            multicast video.
     * @param port the port used for the specified video stream.
     * @param roverPort UDP sideband metadata traffic from a L3 Com Video Downlink Receiver, provide the
     *        additional rover port, -1 if there is no data.
     * @param path for rtsp and http, provide the path to use when connecting to the stream.
     * @param protocol the protocol for the video stream.
     * @param networkTimeout as specified in milliseconds (should be 5000).
     * @param bufferTime as specified in milliseconds (should be -1 unless required).
     * @param source the source of the saved data, either LOCAL_STORAGE or EXTERNAL (by default
     *            should be LOCAL_STORAGE).
     */
    public ConnectionEntry(final String alias,
            final String address,
            final String macAddress,
            final int port,
            final int roverPort,
            final String path,
            final Protocol protocol,
            final int networkTimeout,
            final int bufferTime,
            final int rtspReliable,
            final Source source) {
        this(alias, address, macAddress, port, roverPort, path, protocol,
                networkTimeout, bufferTime, rtspReliable, "", source);
    }

    public ConnectionEntry(File f) {
        this.alias = f.getName();
        this.path = f.getAbsolutePath();
        try {
            this.uid = UUID
                    .nameUUIDFromBytes(f.getAbsolutePath()
                            .getBytes(FileSystemUtils.UTF8_CHARSET))
                    .toString();
        } catch (Exception e) {
            this.uid = UUID.randomUUID().toString();
        }
        this.protocol = IOProviderFactory.isDirectory(f) ? Protocol.DIRECTORY
                : Protocol.FILE;
    }

    /**
     * Creates a connection entry based on a provided uri.
     * @param alias the alias that is presented to the user that describes the connection entry
     * @param uri the uri to turn into a connection entry.
     */
    public ConnectionEntry(String alias, String uri) {
        setAlias(alias);
        setBufferTime(-1);
        setNetworkTimeout(5000);

        int protoIndex = uri.indexOf("://");
        Protocol p = protoIndex != -1 ? Protocol.fromString(
                uri.substring(0, protoIndex)) : null;
        if (p == null) {
            Log.w(TAG, "Unsupported URL: " + uri);
            return;
        }
        String host, path = "";
        int port = p.getDefaultPort();

        // legacy 1234? 
        if (port < 0)
            port = 1234;

        protoIndex += 3;
        int portIndex = uri.indexOf(":", protoIndex);
        // if there is no port, start searching for a path after the protocol
        int pathIndex = uri.indexOf("/", protoIndex);

        // if there is a colon in the url and no port specified,
        // reset the portIndex and just grab the path starting after the address
        if ((pathIndex > 0 && portIndex > pathIndex) || portIndex < 0) {
            pathIndex = uri.indexOf("/", protoIndex);
            //If there is no trailing "/" do nothing
            if (pathIndex > -1)
                portIndex = -1;
        }

        if (pathIndex > -1)
            path = uri.substring(pathIndex);
        if (portIndex > -1) {
            host = uri.substring(protoIndex, portIndex);

            String portStr;
            if (pathIndex > portIndex)
                portStr = (uri.substring(portIndex + 1, pathIndex));
            else
                portStr = (uri.substring(portIndex + 1));

            try {
                port = Integer.parseInt(portStr);
            } catch (Exception ignore) {
                Log.e(TAG, "failed to parse portStr");
            }
        } else if (pathIndex > -1)
            host = uri.substring(protoIndex, pathIndex);
        else
            host = uri.substring(protoIndex);
        if (host.startsWith("@"))
            host = host.substring(1);
        setAddress(host);
        setPort(port);
        setPath(path);
        setProtocol(p);
    }

    /**
     * Produces a completely undefined ConnectionEntry, a minimum set of values would need to be set
     * before this can even be used. When capable use the full constructor for the connection entry.
     */
    public ConnectionEntry() {
        this.uid = UUID.randomUUID().toString();
    }

    /**
     * Construct a copy of the Connection Entry for using by the alternative video player and also
     * for use by the media processor so that the connection entry - if modified does not get messed
     * up.  This copy will have a different unique identifier.
     * @return a valid copy of the connection entry with a different unique identifier.
     */
    public ConnectionEntry copy() {
        ConnectionEntry retval = new ConnectionEntry();
        retval.copy(this);
        retval.setUID(retval.getUID() + "." + System.currentTimeMillis());
        return retval;
    }

    /**
     * Constructs an exact copy of the ConnectionEntry and even contains the same uid.
     * @param from the connection entry to copy the details from.
     */
    public void copy(final ConnectionEntry from) {
        alias = from.getAlias();
        uid = from.getUID();
        address = from.getAddress();
        macAddress = from.getMacAddress();
        port = from.getPort();
        roverPort = from.getRoverPort();
        ignoreEmbeddedKLV = from.getIgnoreEmbeddedKLV();
        path = from.getPath();
        protocol = from.getProtocol();
        source = from.getSource();
        networkTimeout = from.getNetworkTimeout();
        bufferTime = from.getBufferTime();
        rtspReliable = from.getRtspReliable();
        passphrase = from.getPassphrase();
        localFile = from.getLocalFile();
        synchronized (this) {
            childrenUIDs = from.childrenUIDs;
        }
        parentUID = from.parentUID;
    }

    /**
     * If the connection entry contains embedded klv, ignore it if the flag is true.
     * @return ignore embedded klv if the flag is true.
     */
    public boolean getIgnoreEmbeddedKLV() {
        return ignoreEmbeddedKLV;
    }

    /**
     * If the video stream contains metadata, set this flag to hint to the player to ignore any
     * klv processing.
     * @param state
     */
    public void setIgnoreEmbeddedKLV(final boolean state) {
        this.ignoreEmbeddedKLV = state;
    }

    /**
     * Get the port for any sideband metadata - this would be KLV encoded metadata not carried in
     * the same udp stream as the video.
     * @return the port that contains the klv
     */
    public int getRoverPort() {
        return roverPort;
    }

    /**
     * Set the port for any sideband metadata - this would be KLV encoded metadata not carried in
     * the same udp stream as the video.
     * @param port the port that contains the klv
     */
    public void setRoverPort(final int port) {
        roverPort = port;
    }

    /**
     * The alias assigned to this connection entry.
     * @return the alias otherwise the empty string.
     */
    public String getAlias() {
        if (alias != null)
            return alias;
        else
            return "";
    }

    /**
     * Sets the alias for the connection entry.
     * @param alias the alias to set, if null is passed in a call to getAlias will return the
     *              empty string.
     */
    public void setAlias(final String alias) {
        this.alias = alias;
    }

    /**
     * Get the UID for the alias. It is recommended but not required that this be a universally
     * unique identifer from the class UUID.
     * @return the UID for the alias.
     */
    public String getUID() {
        return uid;
    }

    /**
     * Set the UID for the alias. It is recommended but not required that this be a universally
     * unique identifer from the class UUID.
     * @param uid the unique identifier
     */
    public void setUID(final String uid) {
        this.uid = uid;
    }

    /**
     * Get the address associated with the alias.
     * @return the address for the Connection Entry.
     */
    public String getAddress() {
        return address;
    }

    /**
     * Sets the address associated with the alias.
     * @param address the address for the Connection Entry.
     */
    public void setAddress(final String address) {
        this.address = address;
    }

    /**
     * Returns the hinted MAC Address for the interface receiving the video. A valid return value is
     * in the format XX:XX:XX:XX:XX:XX and may describe an interface that exists or does not exist.
     * Invalid data can also be returned and it is assumed that the data should be ignored.
     * @return The hinted mac address.
     */
    public String getMacAddress() {
        return macAddress;
    }

    /**
     * Sets the mac address that is preferred for video udp.
     * @param macAddress for multicast udp video streams, an optional MAC address can be supplied to
     *            support video streams from a specific interface. For any other input, the video
     *            source is assumed to come from the default interface.
     */
    public void setMacAddress(final String macAddress) {
        this.macAddress = macAddress;
    }

    /**
     * The port for the video connection entry
     * @return the port for the entry
     */
    public int getPort() {
        return port;
    }

    /**
     * The port for the video connection entry
     * @param port the port for the entry
     */
    public void setPort(final int port) {
        this.port = port;
    }

    /**
     * The path for the video connection entry
     * @return the path for the entry
     */
    public String getPath() {
        if (path != null)
            return path;
        else
            return "";
    }

    /**
     * Set the path for the connection entry
     * @param path the path
     */
    public void setPath(final String path) {
        this.path = path;
    }

    /**
     * The protocol associated with the connection entry
     * @return the protocol described by the connection entry.
     */
    public Protocol getProtocol() {
        return protocol;
    }

    /**
     * Sets the protocol associated with the connection entry
     * @param protocol the protocol for the connection entry.
     */
    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    /**
     * Get the network timeout associated with the connection entry.
     * @return the network timeout in milliseconds
     */
    public int getNetworkTimeout() {
        return networkTimeout;
    }

    /**
     * Set the network timeout associated with the connection entry.
     * @param networkTimeout the network timeout in milliseconds
     */
    public void setNetworkTimeout(final int networkTimeout) {
        this.networkTimeout = networkTimeout;
    }

    /**
     * Get the time used to buffer an incoming stream.
     * @return the time used to buffer an incoming stream.
     */
    public int getBufferTime() {
        return bufferTime;
    }

    /**
     * Set the time used to buffer an incoming stream
     * @param bufferTime the time in milliseconds to buffer
     */
    public void setBufferTime(final int bufferTime) {
        this.bufferTime = bufferTime;
    }

    /**
     * Returns 1 if rtsp should be force negotiated as reliable (TCP).
     * @return 1 if the rtsp stream negotiation should be TCP
     */
    public int getRtspReliable() {
        return rtspReliable;
    }

    /**
     * Set 1 if rtsp should be force negotiated as reliable (TCP).
     * @param rtspReliable setting it to 1 will force RTSP to attempt to negotiate a TCP stream
     */
    public void setRtspReliable(final int rtspReliable) {
        this.rtspReliable = rtspReliable;
    }

    /**
     * Set to non-empty string if a passphrase is used to access the video
     * @param pass passphrase to access the video or empty string if not needed
     */
    public void setPassphrase(String pass) {
        this.passphrase = pass;
    }

    /**
     * Returns non-empty string if one is to be used to access the video source.
     * @return passphrase for the source if used, else empty string
     */
    public String getPassphrase() {
        return this.passphrase;
    }

    /**
     * Gets the source of the Connection Entry (Local or External) used to make sure that it is not
     * persisted incorrectly if it came from the removable card.
     * @return the source of the entry
     */
    public Source getSource() {
        return source;
    }

    /**
     * Sets the source of the connection entry.  This is used primarily when reading the connection
     * entry as it was persisted.
     * @param source the source.
     */
    public void setSource(final Source source) {
        this.source = source;
    }

    /**
     * Convenience method for checking if a connection entry is remote based
     * on its protocol
     *
     * @return True if remote connection entry
     */
    public boolean isRemote() {
        return this.protocol != Protocol.FILE
                && this.protocol != Protocol.DIRECTORY;
    }

    /**
     * Set list of children entries (only applies to DIRECTORY entries)
     *
     * @param children List of connection entries
     */
    public void setChildren(List<ConnectionEntry> children) {
        if (this.protocol != Protocol.DIRECTORY)
            return;
        if (children == null)
            children = new ArrayList<>();
        List<ConnectionEntry> oldChildren = getChildren();
        synchronized (this) {
            if (oldChildren != null) {
                for (ConnectionEntry e : oldChildren) {
                    if (e.parentUID.equals(this.uid))
                        e.parentUID = null;
                }
            }
            this.childrenUIDs = new HashSet<>();
            for (ConnectionEntry e : children) {
                if (e.isRemote())
                    continue; // Remote entries are always at top-level (ATAK-10652)
                this.childrenUIDs.add(e.getUID());
                e.parentUID = this.uid;
            }
        }
    }

    /**
     * Get children entries (only applies to DIRECTORY entries)
     * @return List of children entries or null if N/A
     */
    public List<ConnectionEntry> getChildren() {
        if (this.protocol != Protocol.DIRECTORY)
            return null;
        Set<String> uids;
        synchronized (this) {
            if (this.childrenUIDs == null)
                return new ArrayList<>();
            uids = new HashSet<>(this.childrenUIDs);
        }
        return VideoManager.getInstance().getEntries(uids);
    }

    private void removeChild(ConnectionEntry entry) {
        if (this.protocol != Protocol.DIRECTORY || entry == null)
            return;
        synchronized (this) {
            if (this.childrenUIDs != null)
                this.childrenUIDs.remove(entry.getUID());
        }
    }

    /**
     * Get the parent directory entry
     * @return Parent entry or null if none
     */
    public String getParentUID() {
        return this.parentUID;
    }

    /**
     * Called once this entry is removed from the manager and not to be used
     * from this point on
     */
    public void dispose() {
        // Remove this entry from the parent
        ConnectionEntry parent = VideoManager.getInstance()
                .getEntry(this.parentUID);
        if (parent != null)
            parent.removeChild(this);
    }

    /**
     * Set the associated local file for this entry
     * @param file Local file
     */
    public void setLocalFile(File file) {
        this.localFile = file;
    }

    public File getLocalFile() {
        return this.localFile;
    }

    /**
     * Set whether this video alias is temporary and should not be persisted
     * @param temp Temporary
     */
    public void setTemporary(boolean temp) {
        this.temporary = temp;
    }

    /**
     * Is the video alias considered temporary.
     * @return if the video alias is considered temporary
     */
    public boolean isTemporary() {
        return this.temporary;
    }

    /**
     * Provide the legacy call to getURL which will show all of the sensitive bits and pieces.
     */
    public static String getURL(ConnectionEntry ce) {
        return getURL(ce, false);
    }

    /**
     * Given a connection entry, produces a well formed URL.
     * @param forDisplay true if URL will be displayed or logged or used in some way other than
     *                   an actual connection to the media.  This results in sensitive parts of the
     *                   URL being obscured.
     */
    public static String getURL(ConnectionEntry ce, boolean forDisplay) {
        String url = ce.getAddress();

        // remove the null from the file entries
        if (url == null)
            url = "";

        Protocol p = ce.getProtocol();

        if (ce.getProtocol().equals(Protocol.RAW)) {
            url = ce.getAddress();
            return url.trim();
        } else if (p == Protocol.UDP || p == Protocol.RTSP
                || p == Protocol.FILE) {
            // original code did nothing
        } else {
            // construct a proper URL
            url = p.toURL() + ce.getAddress();
        }

        if (ce.getPort() != -1)
            url += ":" + ce.getPort();

        if (ce.getPath() != null && !ce.getPath().trim().equals("")) {
            if (!ce.getPath().startsWith("/")) {
                url += "/";
            }
            url += ce.getPath();
        }

        String sep = "?";
        if (url.contains("?")) {
            sep = "&";
        }

        if (ce.getNetworkTimeout() > 0) {
            if (p == Protocol.RTP) {
                // value of the timeout needs to be milliseconds
                url = url + sep + "timeout=" + (ce.getNetworkTimeout());
                sep = "&";
            } else if (p == Protocol.RTMP || p == Protocol.RTMPS) {
                // value of the timeout needs to be seconds
                url = url + sep + "timeout="
                        + ce.getNetworkTimeout() / 1000;
                sep = "&";
            } else if (p == Protocol.HTTP || p == Protocol.HTTPS) {
                // value of the timeout needs to be microseconds
                url = url + sep + "timeout=" + (ce.getNetworkTimeout() * 1000)
                        + "&seekable=0";
                sep = "&";
            } else if (p == Protocol.SRT) {
                // value of the timeout needs to be microseconds
                url = url + sep + "timeout=" + (ce.getNetworkTimeout() * 1000);
                sep = "&";
            }
        }

        if ((p == Protocol.RTSP) && (ce.getRtspReliable() == 1)) {
            url = url + sep + "tcp";
            Log.d(TAG, "rtsp reliable communications requested");
        }
        if (p == Protocol.SRT && (ce.getPassphrase().length() > 0)) {
            if (forDisplay)
                url = url + sep + "passphrase=XXHiddenXX";
            else
                url = url + sep + "passphrase=" + ce.getPassphrase();
        }
        return url.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ConnectionEntry that = (ConnectionEntry) o;
        return port == that.port &&
                roverPort == that.roverPort &&
                ignoreEmbeddedKLV == that.ignoreEmbeddedKLV &&
                networkTimeout == that.networkTimeout &&
                bufferTime == that.bufferTime &&
                rtspReliable == that.rtspReliable &&
                Objects.equals(alias, that.alias) &&
                Objects.equals(uid, that.uid) &&
                Objects.equals(address, that.address) &&
                Objects.equals(macAddress, that.macAddress) &&
                Objects.equals(path, that.path) &&
                protocol == that.protocol &&
                Objects.equals(passphrase, that.passphrase) &&
                source == that.source &&
                Objects.equals(localFile, that.localFile) &&
                Objects.equals(childrenUIDs, that.childrenUIDs) &&
                Objects.equals(parentUID, that.parentUID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alias, uid, address, macAddress, port,
                roverPort, ignoreEmbeddedKLV, path, protocol, passphrase,
                source,
                networkTimeout, bufferTime, rtspReliable, localFile,
                childrenUIDs, parentUID);
    }

    @Override
    public String toString() {
        return "ConnectionEntry [address=" + address +
                ", alias=" + alias + ", port=" + port +
                ", path=" + path + ", protocol=" + protocol +
                ", networkTimeout=" + networkTimeout +
                ", bufferTime=" + bufferTime +
                ", macAddress=" + macAddress + "]" +
                " possible url = " + getURL(this, true);
    }
}
