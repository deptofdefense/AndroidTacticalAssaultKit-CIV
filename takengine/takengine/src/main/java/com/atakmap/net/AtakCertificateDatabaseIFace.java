
package com.atakmap.net;

import com.atakmap.comms.NetConnectString;

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

    /**
     * Saves the certificate with the given type.
     *
     * @param type        the type of the certificate
     * @param certificate the certificate as a <code>byte[]</code>
     */
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
     * Gets the certificate based off of the given type and server and port and optionally
     * validate the hash of the certificate retrieved from the database
     *
     * @param type   the certificate type
     * @param server the server
     * @param port   the port
     * @param validateHash   true to validate the certificates hash
     * @return Null if not exists, the certificate as a <code>byte[]</code> otherwise.
     */
    byte[] getCertificateForTypeAndServerAndPort(String type, String server, int port);

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

    /**
     * Saves the certificate with the given type and server.
     *
     * @param type        the type of the certificate
     * @param server      the server name.
     * @param certificate the certificate as a <code>byte[]</code>
     */
    void saveCertificateForTypeAndServer(String type, String server,
            byte[] certificate);

    /**
     * Saves the certificate with the given type and server and port
     *
     * @param type        the type of the certificate
     * @param server      the server name.
     * @param port        the port.
     * @param certificate the certificate as a <code>byte[]</code>
     */
    public void saveCertificateForTypeAndServerAndPort(String type, String server, int port,
                                                       byte[] certificate);
    /**
     * Deletes all certificates of the given type within the certificates table only.
     *
     * @param type the type to be removed.
     */
    boolean deleteCertificateForType(String type);

    /**
     * Deletes all certificates of the given type and server within the server_certificates table only.
     *
     * @param type   the type to be removed.
     * @param server the server to be removed.
     */
    boolean deleteCertificateForTypeAndServer(String type, String server);

    /**
     * Deletes all certificates of the given type and server and port within the server_certificates table only.
     *
     * @param type   the type to be removed.
     * @param server the server to be removed.
     * @param port the port server to be removed.
     */
    boolean deleteCertificateForTypeAndServerAndPort(String type, String server, int port);

    /**
     * Gets a CA Certificate.
     *
     * @return a list of {@link X509Certificate}
     */
    List<X509Certificate> getCACerts();

    /**
     * Checks the validity of the given keystore and password.
     *
     * @param keystore the keystore
     * @param password the password
     * @return true if valid, false otherwise.
     */
    boolean checkValidity(byte[] keystore, String password);

    /**
     * Gets a collection of servers in the DB.
     *
     * @param type the type of certificate.
     * @return a collection of server names
     */
    String[] getServers(String type);

    /**
     * Gets a collection of connectionStrings in the DB.
     *
     * @param type the type of certificate.
     * @return a collection of server names
     */
    NetConnectString[] getServerConnectStrings(String type);

    /**
     * Closes the certificate database
     */
    void dispose();
}
