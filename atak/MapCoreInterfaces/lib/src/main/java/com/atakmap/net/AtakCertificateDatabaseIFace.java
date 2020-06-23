
package com.atakmap.net;

import android.content.Context;
import java.security.cert.X509Certificate;
import java.util.List;

public interface AtakCertificateDatabaseIFace {

    String TYPE_TRUST_STORE_CA = "TRUST_STORE_CA";
    String TYPE_CLIENT_CERTIFICATE = "CLIENT_CERTIFICATE";
    String TYPE_PRIVATE_KEY = "PRIVATE_KEY";
    String TYPE_UPDATE_SERVER_TRUST_STORE_CA = "UPDATE_SERVER_TRUST_STORE_CA";

    byte[] getCertificateForType(String type);

    void saveCertificateForType(String type, byte[] certificate);

    byte[] getCertificateForTypeAndServer(String type, String server);

    void saveCertificateForTypeAndServer(String type, String server,
            byte[] certificate);

    void deleteCertificateForType(String type);

    void deleteCertificateForTypeAndServer(String type, String server);

    List<X509Certificate> getCACerts();

    byte[] importCertificateFromPreferences(
            Context context, String prefLocation,
            String type, boolean delete);

    AtakAuthenticationCredentials importCertificatePasswordFromPreferences(
            Context context, String prefLocation,
            String prefDefault,
            String type, boolean delete);

    boolean checkValidity(byte[] keystore, String password);
}
