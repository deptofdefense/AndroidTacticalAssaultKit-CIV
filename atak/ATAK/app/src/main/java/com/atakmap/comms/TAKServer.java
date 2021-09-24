
package com.atakmap.comms;

import android.os.Bundle;

import com.atakmap.android.http.rest.ServerVersion;
import com.atakmap.annotations.FortifyFinding;
import com.atakmap.comms.app.CotPortListActivity;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

/**
 * Metadata for a TAK server connection
 * Moved out of {@link CotPortListActivity.CotPort}
 */
public class TAKServer {

    private static final String TAG = "TAKServer";

    public static final String CONNECT_STRING_KEY = "connectString";
    public static final String DESCRIPTION_KEY = "description";
    public static final String COMPRESSION_KEY = "compress";
    public static final String ENABLED_KEY = "enabled";
    public static final String CONNECTED_KEY = "connected";
    public static final String ERROR_KEY = "error";
    public static final String SERVER_VERSION_KEY = "serverVersion";
    public static final String SERVER_API_KEY = "serverAPI";
    public static final String USEAUTH_KEY = "useAuth";
    public static final String USERNAME_KEY = "username";
    public static final String ENROLL_FOR_CERT_KEY = "enrollForCertificateWithTrust";
    public static final String EXPIRATION_KEY = "expiration";

    @FortifyFinding(finding = "Password Management: Hardcoded Password", rational = "This is only a key and not a password")
    public static final String PASSWORD_KEY = "password";
    public static final String CACHECREDS_KEY = "cacheCreds";
    public static final String ISSTREAM_KEY = "isStream";
    public static final String ISCHAT_KEY = "isChat";

    private final Bundle data;

    public TAKServer(Bundle bundle) throws IllegalArgumentException {
        String connectString = bundle.getString(CONNECT_STRING_KEY);
        if (connectString == null || connectString.trim().length() == 0) {
            throw new IllegalArgumentException(
                    "Cannot construct CotPort with empty/null connnectString");
        }
        this.data = new Bundle(bundle);
    }

    public TAKServer(TAKServer other) {
        this(other.getData());
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof TAKServer)
            return getConnectString().equalsIgnoreCase(((TAKServer) other)
                    .getConnectString());
        return false;
    }

    public String getConnectString() {
        return this.data.getString(CONNECT_STRING_KEY);
    }

    public String getURL(boolean includePort) {
        try {
            NetConnectString ncs = NetConnectString.fromString(
                    getConnectString());
            String proto = ncs.getProto();
            if (proto.equalsIgnoreCase("ssl"))
                proto = "https";
            else
                proto = "http";
            String url = proto + "://" + ncs.getHost();
            if (includePort)
                url += ":" + ncs.getPort();
            return url;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get server URL: " + this, e);
        }
        return null;
    }

    public void setServerVersion(ServerVersion version) {
        setServerVersion(version.getVersion());
        this.data.putInt(SERVER_API_KEY, version.getApiVersion());
    }

    public void setServerVersion(String version) {
        this.data.putString(SERVER_VERSION_KEY, version);
    }

    public String getServerVersion() {
        return this.data.getString(SERVER_VERSION_KEY);
    }

    public int getServerAPI() {
        return this.data.getInt(SERVER_API_KEY, -1);
    }

    public void setErrorString(String error) {
        appendString(this.data, ERROR_KEY, error);
    }

    public static void appendString(Bundle data, String key, String value) {
        String existing = data.getString(key);
        if (FileSystemUtils.isEmpty(existing))
            data.putString(key, value);
        else if (!existing.contains(value)) {
            data.putString(key, existing + ", " + value);
        }
    }

    public String getErrorString() {
        return this.data.getString(ERROR_KEY);
    }

    public boolean isCompressed() {
        return this.data.getBoolean(COMPRESSION_KEY, false);
    }

    public boolean isUsingAuth() {
        return this.data.getBoolean(USEAUTH_KEY, false);
    }

    public boolean enrollForCert() {
        return this.data.getBoolean(ENROLL_FOR_CERT_KEY, false);
    }

    public boolean isStream() {
        return this.data.getBoolean(ISSTREAM_KEY, false);
    }

    public boolean isChat() {
        return this.data.getBoolean(ISCHAT_KEY, false);
    }

    public String getCacheCredentials() {
        String cacheCreds = this.data.getString(CACHECREDS_KEY);
        if (cacheCreds == null) {
            cacheCreds = "";
        }
        return cacheCreds;
    }

    public String getDescription() {
        String description = this.data.getString(DESCRIPTION_KEY);
        if (description == null) {
            description = "";
        }
        return description;
    }

    public String getUsername() {
        String s = this.data.getString(USERNAME_KEY);
        if (s == null) {
            s = "";
        }
        return s;
    }

    public String getPassword() {
        String s = this.data.getString(PASSWORD_KEY);
        if (s == null) {
            s = "";
        }
        return s;
    }

    @Override
    public int hashCode() {
        return this.getConnectString().hashCode();
    }

    public boolean isEnabled() {
        return this.data.getBoolean(ENABLED_KEY, true);
    }

    public void setEnabled(boolean newValue) {
        this.data.putBoolean(ENABLED_KEY, newValue);
    }

    public boolean isConnected() {
        return this.data.getBoolean(CONNECTED_KEY, false);
    }

    public void setConnected(boolean newValue) {
        this.data.putBoolean(CONNECTED_KEY, newValue);
        if (newValue)
            this.data.remove(ERROR_KEY);
    }

    @Override
    public String toString() {
        if (data != null) {
            return "connect_string=" + data.getString(CONNECT_STRING_KEY)
                    + " " +
                    "description=" + data.getString(DESCRIPTION_KEY) + " " +
                    "compression=" + data.getBoolean(COMPRESSION_KEY) + " "
                    +
                    "enabled=" + data.getBoolean(ENABLED_KEY) + " " +
                    "connected=" + data.getBoolean(CONNECTED_KEY);
        }
        return super.toString();
    }

    public Bundle getData() {
        return this.data;
    }
}
