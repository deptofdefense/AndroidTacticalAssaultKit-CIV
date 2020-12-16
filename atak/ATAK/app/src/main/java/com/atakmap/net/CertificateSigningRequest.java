
package com.atakmap.net;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.foxykeep.datadroid.requestmanager.Request;

public class CertificateSigningRequest implements Parcelable {

    private static final String TAG = "CertifiateCSRRequest";

    private final String server;
    private final String username;
    private final String password;
    private final boolean hasTruststore;

    /**
     * @deprecated Certificate Enrollment only stores certificates with the connection
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    private boolean saveAsDefault;

    private boolean getProfile;
    private final String CSR;
    private final boolean allowAllHostnames;

    public CertificateSigningRequest(
            String server, String username, String password,
            boolean hasTruststore, boolean saveAsDefault,
            boolean allowAllHostnames, String CSR) {
        this.server = server;
        this.username = username;
        this.password = password;
        this.hasTruststore = hasTruststore;
        this.saveAsDefault = saveAsDefault;
        this.allowAllHostnames = allowAllHostnames;
        this.CSR = CSR;
    }

    public boolean isValid() {
        return !FileSystemUtils.isEmpty(server);
    }

    public String getServer() {
        return server;
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

    /*
        @deprecated Certificate Enrollment only stores certificates with the connection
    */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public boolean getSaveAsDefault() {
        return saveAsDefault;
    }

    public String getCSR() {
        return CSR;
    }

    public boolean getProfile() {
        return getProfile;
    }

    public void setGetProfile(boolean getProfile) {
        this.getProfile = getProfile;
    }

    public boolean getAllowAllHostnames() {
        return allowAllHostnames;
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
            dest.writeString(username);
            dest.writeString(password);
            dest.writeByte((byte) (hasTruststore ? 1 : 0));
            dest.writeByte((byte) (saveAsDefault ? 1 : 0));
            dest.writeByte((byte) (getProfile ? 1 : 0));
            dest.writeByte((byte) (allowAllHostnames ? 1 : 0));
            dest.writeString(CSR);
        }
    }

    public static final Creator<CertificateSigningRequest> CREATOR = new Creator<CertificateSigningRequest>() {

        @Override
        public CertificateSigningRequest createFromParcel(Parcel in) {
            return new CertificateSigningRequest(in);
        }

        @Override
        public CertificateSigningRequest[] newArray(int size) {
            return new CertificateSigningRequest[size];
        }
    };

    protected CertificateSigningRequest(Parcel in) {
        server = in.readString();
        username = in.readString();
        password = in.readString();
        hasTruststore = in.readByte() != 0;
        saveAsDefault = in.readByte() != 0;
        getProfile = in.readByte() != 0;
        allowAllHostnames = in.readByte() != 0;
        CSR = in.readString();
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
    public Request createCertificateSigningRequest() {
        Request request = new Request(
                CertificateEnrollmentClient.REQUEST_TYPE_CERTIFICATE_SIGNING);
        request.put(
                CertificateSigningOperation.PARAM_CERTIFICATE_SIGNING_REQUEST,
                this);
        return request;
    }
}
