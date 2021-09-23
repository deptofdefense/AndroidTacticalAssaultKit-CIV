
package com.atakmap.net;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.foxykeep.datadroid.requestmanager.Request;

public class CertificateConfigRequest implements Parcelable {

    private static final String TAG = "CertifiateConfigRequest";

    private final String server;
    private final String connectString;
    private final String cacheCreds;
    private final String description;
    private final String username;
    private final String password;
    private boolean hasTruststore;
    private boolean quickConnect;
    private Long expiration;

    private boolean allowAllHostnames;

    public CertificateConfigRequest(
            String connectString, String cacheCreds, String description,
            String username, String password) {

        NetConnectString ncs = NetConnectString.fromString(connectString);
        this.server = ncs.getHost();

        this.connectString = connectString;
        this.cacheCreds = cacheCreds;
        this.description = description;
        this.username = username;
        this.password = password;
        this.hasTruststore = false;
        this.allowAllHostnames = false;
    }

    public CertificateConfigRequest(
            String connectString, String cacheCreds, String description,
            String username, String password, Long expiration) {

        this(connectString, cacheCreds, description, username, password);
        this.expiration = expiration;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(server);
    }

    public String getServer() {
        return server;
    }

    public String getConnectString() {
        return connectString;
    }

    public String getCacheCreds() {
        return cacheCreds;
    }

    public String getDescription() {
        return description;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean hasTruststore() {
        return hasTruststore;
    }

    public void setHasTruststore(boolean hasTruststore) {
        this.hasTruststore = hasTruststore;
    }

    public Long getExpiration() {
        return expiration;
    }

    public void setExpiration(Long expiration) {
        this.expiration = expiration;
    }

    public boolean getAllowAllHostnames() {
        return allowAllHostnames;
    }

    public void setAllowAllHostnames(boolean allowAllHostnames) {
        this.allowAllHostnames = allowAllHostnames;
    }

    public boolean getQuickConnect() {
        return quickConnect;
    }

    public void setQuickConnect(boolean quickConnect) {
        this.quickConnect = quickConnect;
    }

    @Override
    public String toString() {
        if (!isValid())
            return "";

        return server;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            dest.writeString(server);
            dest.writeString(connectString);
            dest.writeString(cacheCreds);
            dest.writeString(description);
            dest.writeString(username);
            dest.writeString(password);
            dest.writeByte((byte) (hasTruststore ? 1 : 0));
            dest.writeByte((byte) (allowAllHostnames ? 1 : 0));
            dest.writeLong(expiration);
            dest.writeByte((byte) (quickConnect ? 1 : 0));
        }
    }

    public static final Creator<CertificateConfigRequest> CREATOR = new Creator<CertificateConfigRequest>() {

        @Override
        public CertificateConfigRequest createFromParcel(Parcel in) {
            return new CertificateConfigRequest(in);
        }

        @Override
        public CertificateConfigRequest[] newArray(int size) {
            return new CertificateConfigRequest[size];
        }
    };

    protected CertificateConfigRequest(Parcel in) {
        server = in.readString();
        connectString = in.readString();
        cacheCreds = in.readString();
        description = in.readString();
        username = in.readString();
        password = in.readString();
        hasTruststore = in.readByte() != 0;
        allowAllHostnames = in.readByte() != 0;
        expiration = in.readLong();
        quickConnect = in.readByte() != 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Create the request to enroll for a certificate. Used by an asynch HTTP request Android
     * Service
     *
     * @return The request.
     */
    public Request createCertificateConfigRequest() {
        Request request = new Request(
                CertificateEnrollmentClient.REQUEST_TYPE_CERTIFICATE_CONFIG);
        request.put(CertificateConfigOperation.PARAM_CONFIG_REQUEST, this);
        return request;
    }
}
