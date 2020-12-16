
package com.atakmap.net;

import android.content.Context;
import java.security.cert.X509Certificate;
import java.util.List;

public interface AtakCertificateDatabaseIFace {

    String TYPE_TRUST_STORE_CA = "TRUST_STORE_CA";
    String TYPE_CLIENT_CERTIFICATE = "CLIENT_CERTIFICATE";
    String TYPE_PRIVATE_KEY = "PRIVATE_KEY";
    String TYPE_UPDATE_SERVER_TRUST_STORE_CA = "UPDATE_SERVER_TRUST_STORE_CA";

    /**
     * Retrieves the certificate for the specified type. The certificate is
     * validated against the stored hash.
     *
     * @param type          The type
     * @return
     */
    byte[] getCertificateForType(String type);

    /**
     * Retrieves the certificate for the specified type.
     *
     * @param type          The type
     * @param validateHash  If <code>true</code>, the certificate is validated
     *                      against the stored hash. If the validation check
     *                      fails, <code>null</code> is returned even if the
     *                      certificate is in the database
     * @return
     */
    byte[] getCertificateForType(String type, boolean validateHash);

    void saveCertificateForType(String type, byte[] certificate);

    /**
     * Retrieves the certificate for the specified type for the specified
     * server. The certificate is validated against the stored hash.
     *
     * @param type          The type
     * @param server        The server
     * @return
     */
    byte[] getCertificateForTypeAndServer(String type, String server);

    /**
     * Retrieves the certificate for the specified type for the specified
     * server.
     *
     * @param type          The type
     * @param server        The server
     * @param validateHash  If <code>true</code>, the certificate is validated
     *                      against the stored hash. If the validation check
     *                      fails, <code>null</code> is returned even if the
     *                      certificate is in the database
     * @return
     */
    byte[] getCertificateForTypeAndServer(String type, String server,
            boolean validateHash);

    void saveCertificateForTypeAndServer(String type, String server,
            byte[] certificate);

    boolean deleteCertificateForType(String type);

    boolean deleteCertificateForTypeAndServer(String type, String server);

    List<X509Certificate> getCACerts();

    boolean checkValidity(byte[] keystore, String password);

    String[] getServers(String type);

    void dispose();
}
