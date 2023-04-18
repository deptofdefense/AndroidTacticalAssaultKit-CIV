
package gov.tak.api.engine.net;

import com.atakmap.filesystem.HashingUtils;

import java.io.File;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.List;

import gov.tak.api.util.Disposable;
import com.atakmap.coremap.log.Log;

/**
 * Data store construct for management of binary certificate data.
 */
public interface ICertificateStore extends Disposable {

    String TAG = "ICertificateStore";
    String TYPE_TRUST_STORE_CA = "TRUST_STORE_CA";
    String TYPE_CLIENT_CERTIFICATE = "CLIENT_CERTIFICATE";
    String TYPE_PRIVATE_KEY = "PRIVATE_KEY";
    String TYPE_UPDATE_SERVER_TRUST_STORE_CA = "UPDATE_SERVER_TRUST_STORE_CA";

    static boolean validateCertificate(ICertificateStore store, String type, String server, int port) {
        final byte[] certificate = store.getCertificate(type, server, port);
        if(certificate == null)
            return false; // no certificate

        final String hash = store.getCertificateHash(type, server, port);
        if (hash == null || hash.length() == 0) {
            Log.w(TAG, "found certificate without hash: " + type);
            return false;
        }

        String hashCheck = HashingUtils.sha256sum(certificate);
        if (!hash.equals(hashCheck)) {
            Log.w(TAG, "certificate hash validation failed!");
            return false;
        }
        return true;
    }

    /**
     * Gets the certificate based off of the given type and server and port.
     *
     * @param type   the certificate type
     * @param server the server. <code>null</code> represents a distinct value.
     * @param port   the port, -1 represents undefined port; ignored if <code>server</code> is <code>null</code>
     * @return <code>null</code>> if not exists, the certificate as a <code>byte[]</code> otherwise.
     */
    byte[] getCertificate(String type, String server, int port);

    /**
     * Saves the certificate with the given type and server and port
     *
     * @param type        the type of the certificate
     * @param server      the server name. <code>null</code>
     * @param port        the port, -1 represents undefined port; ignored if <code>server</code> is <code>null</code>
     * @param certificate the certificate as a <code>byte[]</code>
     */
    void saveCertificate(String type, String server, int port,
                                      byte[] certificate);

    /**
     * Deletes all certificates of the given type and server and port within the server_certificates table only.
     *
     * @param type   the type to be removed.
     * @param server the server to be removed.
     * @param port the port server to be removed, -1 is wildcard
     */
    boolean deleteCertificate(String type, String server, int port);

    /**
     * Gets a collection of distinct servers in the DB.
     *
     * @param type the type of certificate.
     * @return a collection of servers (names or IPs)
     */
    String[] getServers(String type);

    /**
     * Gets a collection of distinct server URIs in the DB.
     *
     * @param type the type of certificate.
     * @return a collection of servers (names or IPs), to include port if defined
     */
    URI[] getServerURIs(String type);

    /**
     * Gets the certificate of the given type and server.
     *
     * @param type   the certificate type.
     * @param server the server (name or IP). <code>null</code> represents a distinct value.
     * @param port   the port, -1 represents undefined port; ignored if <code>server</code> is <code>null</code>
     * @return The <code>byte[]</code> of the certificate of the given type, null if doesn't exist.
     */
    String getCertificateHash(String type, String server, int port);
}
