package com.atakmap.net;

import android.util.Pair;
import android.util.Patterns;

import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;
import com.atakmap.database.impl.DatabaseImpl;
import com.atakmap.filesystem.HashingUtils;
import com.atakmap.annotations.ModifierApi;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import gov.tak.api.engine.net.ICertificateStore;
import gov.tak.platform.engine.net.CertificateStore;

/**
 * Class responsible for the local calls similar to {@link AtakCertificateDatabaseAdapter}.
 *
 */
@ModifierApi(since = "4.3", target = "4.6", modifiers = {
        "final"
})
public final class AtakCertificateDatabaseAdapter implements AtakCertificateDatabaseIFace {

    /**
     * Tag for logging
     */
    private static final String TAG = "AtakCertificateDatabaseAdapter";
    /**
     * Holds the certificate database.
     */
    ICertificateStore certDb;

    final Object lock = new Object();

    /**
     * The constructor
     */
    public AtakCertificateDatabaseAdapter() {
        this.certDb = null;
    }

    /**
     * Closes the certificate database
     */
    public void dispose() {
        synchronized (lock) {
            if (certDb != null) {
                certDb.dispose();
            }
        }
    }

    /**
     * Deletes the given file.
     *
     * @param databaseFile the file to delete.
     */
    public void clear(File databaseFile) {
        synchronized (lock) {
            if(certDb != null)  certDb.dispose();
            FileSystemUtils.deleteFile(databaseFile);
            certDb = null;
        }
    }

    @Override
    public byte[] getCertificateForType(String type) {
        return getCertificateForType(type, true);
    }

    /**
     * Gets the certificate by the given type.
     *
     * @param type the certificate type.
     * @return Null if not exists, the certificate as a <code>byte[]</code> otherwise.
     */
    @Override
    public byte[] getCertificateForType(String type, boolean validateHash) {
        synchronized (lock) {
            byte[] certificate = getCertificate(type);
            if (certificate == null) {
                return null;
            }

            if(validateHash) {
                String hash = getCertificateHash(type);
                if (hash == null || hash.length() == 0) {
                    Log.w(TAG, "found certificate without hash: " + type);
                    return null;
                }
                String hashCheck = HashingUtils.sha256sum(certificate);
                if (!hash.equals(hashCheck)) {
                    Log.w(TAG, "certificate hash validation failed!");
                    return null;
                }
            }

            return certificate;
        }
    }

    /**
     * Retrieves the certificate for the specified type for the specified
     * server. The certificate is validated against the stored hash.
     *
     * @param type          The type
     * @param server        The server
     * @return
     */
    @Override
    public byte[] getCertificateForTypeAndServer(String type, String server) {
        return getCertificateForTypeAndServer(type, server, true);
    }

    /**
     * Gets the certificate based off of the given type and server.
     *
     * @param type   the certificate type
     * @param server the server
     * @return Null if not exists, the certificate as a <code>byte[]</code> otherwise.
     */
    @Override
    public byte[] getCertificateForTypeAndServer(String type, String server, boolean validateHash) {
        synchronized (lock) {

            byte[] certificate = getCertificateForServer(type, server);
            if (certificate == null) {
                return null;
            }

            if(validateHash) {
                String hash = getCertificateHashForServer(type, server);
                if (hash == null || hash.length() == 0) {
                    Log.w(TAG, "found certificate without hash: " + type);
                    return null;
                }
                String hashCheck = HashingUtils.sha256sum(certificate);
                if (!hash.equals(hashCheck)) {
                    Log.w(TAG, "certificate hash validation failed!");
                    return null;
                }
            }

            return certificate;
        }
    }

    /**
     * Gets the certificate based off of the given type and server and port.
     *
     * @param type   the certificate type
     * @param server the server
     * @param port   the port
     * @return Null if not exists, the certificate as a <code>byte[]</code> otherwise.
     */
    @Override
    public byte[] getCertificateForTypeAndServerAndPort(String type, String server, int port) {
        byte[] certificate = getCertificateForTypeAndServerAndPort(type, server, port, true);
        if (certificate == null) {
            certificate = getCertificateForTypeAndServer(type, server, true);
        }
        return certificate;
    }

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
    public byte[] getCertificateForTypeAndServerAndPort(String type, String server, int port, boolean validateHash) {
        synchronized (lock) {

            byte[] certificate = getCertificateForServerAndPort(type, server, port);
            if (certificate == null) {
                return null;
            }

            if(validateHash) {
                String hash = getCertificateHashForServerAndPort(type, server, port);
                if (hash == null || hash.length() == 0) {
                    Log.w(TAG, "found certificate without hash: " + type);
                    return null;
                }
                String hashCheck = HashingUtils.sha256sum(certificate);
                if (!hash.equals(hashCheck)) {
                    Log.w(TAG, "certificate hash validation failed!");
                    return null;
                }
            }

            return certificate;
        }
    }

    private String getByNameHelper(String server) {
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final String[] results = new String[1];
            results[0] = null;
            Thread t = new Thread(TAG + "-GetHostAddress") {
                @Override
                public void run() {
                    try {
                        InetAddress inetAddress = InetAddress.getByName(server);
                        if (inetAddress == null) {
                            Log.w(TAG, "getByName failed for " + server);
                        } else {
                            results[0] = inetAddress.getHostAddress();
                        }
                    } catch (UnknownHostException uhe) {
                        Log.w(TAG, "UnknownHostException for " + server);
                    }

                    latch.countDown();
                }
            };
            t.start();
            latch.await();

            return results[0];
        } catch (InterruptedException e) {
            Log.e(TAG, "getByNameHelper interrupted!", e);
            return null;
        }
    }

    /**
     * Gets the certificate based on the given IP Address
     *
     * @param type the type
     * @param IP   the IP address
     * @return the Pair of certificate and server name.
     */
    public Pair<byte[], String> getCertificateForIPaddress(final String type, final String IP) {
        synchronized (lock) {
            try {

                // bail if we dont have a proper IP address
                if (IP == null || !Patterns.IP_ADDRESS.matcher(IP).matches()) {
                    return null;
                }

                String[] servers = getServers(type);
                if (servers == null) {
                    return null;
                }

                for (final String server : servers) {

                    // ignore any certs stored via IP address
                    if (Patterns.IP_ADDRESS.matcher(server).matches()) {
                        continue;
                    }

                    String ipForCert = getByNameHelper(server);

                    if (ipForCert == null) {
                        Log.w(TAG, "getByName failed for " + server);
                    } else if (ipForCert.compareTo(IP) == 0) {
                        byte[] cert = getCertificateForServer(type, server);
                        Pair<byte[], String> pair = new Pair<byte[], String>(cert, server);
                        return pair;
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "exception in getCertificateForIPaddress: " + e.getMessage(), e);
            }

            return null;
        }
    }

    /**
     * Gets the certificate based on the given IP Address and port
     *
     * @param type the type
     * @param IP   the IP address
     * @param port the port
     * @return the Pair of certificate and server name.
     */
    public Pair<byte[], String> getCertificateForIPaddressAndPort(
            final String type, final String IP, final int port) {
        synchronized (lock) {
            try {

                // bail if we dont have a proper IP address
                if (IP == null || !Patterns.IP_ADDRESS.matcher(IP).matches()) {
                    return null;
                }

                NetConnectString[] netConnectStrings = getServerConnectStrings(type);
                if (netConnectStrings == null) {
                    return null;
                }

                for (final NetConnectString netConnectString : netConnectStrings) {

                    String host = netConnectString.getHost();

                    // ignore any certs stored via IP address
                    if (Patterns.IP_ADDRESS.matcher(host).matches()) {
                        continue;
                    }

                    String ipForCert = getByNameHelper(host);

                    if (ipForCert == null) {
                        Log.w(TAG, "getByName failed for " + host);
                    } else if (ipForCert.compareTo(IP) == 0) {
                        byte[] cert = getCertificateForServerAndPort(
                                type, netConnectString.getHost(), netConnectString.getPort());
                        Pair<byte[], String> pair = new Pair<byte[], String>(cert, host);
                        return pair;
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "exception in getCertificateForIPaddressAndPort: " + e.getMessage(), e);
            }

            return null;
        }
    }

    /**
     * Saves the certificate with the given type.
     *
     * @param type        the type of the certificate
     * @param certificate the certificate as a <code>byte[]</code>
     */
    @Override
    public void saveCertificateForType(String type, byte[] certificate) {

        synchronized (lock) {
            String hash = HashingUtils.sha256sum(certificate);
            saveCertificate(type, certificate, hash);
        }
    }

    /**
     * Saves the certificate with the given type and server.
     *
     * @param type        the type of the certificate
     * @param server      the server name.
     * @param certificate the certificate as a <code>byte[]</code>
     */
    @Override
    public void saveCertificateForTypeAndServer(String type, String server, byte[] certificate) {

        synchronized (lock) {
            String hash = HashingUtils.sha256sum(certificate);
            saveCertificateForServer(type, server, certificate, hash);
        }
    }

    /**
     * Saves the certificate with the given type and server and port
     *
     * @param type        the type of the certificate
     * @param server      the server name.
     * @param port        the port.
     * @param certificate the certificate as a <code>byte[]</code>
     */
    @Override
    public void saveCertificateForTypeAndServerAndPort(String type, String server, int port, byte[] certificate) {

        synchronized (lock) {
            String hash = HashingUtils.sha256sum(certificate);
            saveCertificateForServerAndPort(type, server, port, certificate, hash);
        }
    }

    /**
     * Deletes all certificates of the given type within the certificates table only.
     *
     * @param type the type to be removed.
     */
    @Override
    public boolean deleteCertificateForType(String type) {
        synchronized (lock) {
            return deleteCertificateImpl(type);
        }
    }

    /**
     * Deletes all certificates of the given type and server within the server_certificates table only.
     *
     * @param type   the type to be removed.
     * @param server the server to be removed.
     */
    @Override
    public boolean deleteCertificateForTypeAndServer(String type, String server) {
        synchronized (lock) {
            return deleteCertificateForServer(type, server);
        }
    }

    /**
     * Deletes all certificates of the given type and server and port within the server_certificates table only.
     *
     * @param type   the type to be removed.
     * @param server the server to be removed.
     * @param port the port server to be removed.
     */
    @Override
    public boolean deleteCertificateForTypeAndServerAndPort(String type, String server, int port) {
        synchronized (lock) {
            return deleteCertificateForServerAndPort(type, server, port);
        }
    }

    /**
     * Gets a CA Certificate.
     *
     * @return a list of {@link X509Certificate}
     */
    public List<X509Certificate> getCACerts() {
        return gov.tak.platform.engine.net.CertificateManager.getCACerts(
                certDb,
                AtakAuthenticationDatabase.getStore());
    }

    /**
     * Checks the validity of the given keystore and password.
     *
     * @param keystore the keystore
     * @param password the password
     * @return true if valid, false otherwise.
     */
    @Override
    public boolean checkValidity(byte[] keystore, String password) {
        AtakCertificateDatabase.CertificateValidity validity = AtakCertificateDatabase.checkValidity(keystore, password);
        return validity != null && validity.isValid();
    }

    /**
     * Gets the certificate of the given type.
     *
     * @param type the certificate type.
     * @return The <code>byte[]</code> of the certificate of the given type, null if doesn't exist.
     */
    private byte[] getCertificate(String type) {
        if(this.certDb == null)
            return null;
        return this.certDb.getCertificate(type, null, -1);
    }

    /**
     * Gets the certificate of the given type.
     *
     * @param type the certificate type.
     * @return The <code>byte[]</code> of the certificate of the given type, null if doesn't exist.
     */
    private String getCertificateHash(String type) {
        if(this.certDb == null)
            return null;
        return this.certDb.getCertificateHash(type, null, -1);
    }

    /**
     * Saves the certificate to the DB.
     *
     * @param type the type of certificate
     * @param cert the certificate represented as a <code>byte[]</code>
     * @param hash the hash of the certificate.
     * @return true if successful, false otherwise.
     */
    private boolean saveCertificate(String type, byte[] cert, String hash) {
        if(this.certDb == null)
            return false;

        this.certDb.saveCertificate(type, null, -1, cert);
        return true;
    }

    /**
     * Deletes a certificate of the given type.
     *
     * @param type the type of certificate to delete.
     * @return True if successful, false otherwise.
     */
    private boolean deleteCertificateImpl(String type) {
        if (this.certDb == null)
            return false;

        return this.certDb.deleteCertificate(type, null, -1);
    }

    /**
     * Gets a collection of servers in the DB.
     *
     * @param type the type of certificate.
     * @return a collection of server names
     */
    @Override
    public String[] getServers(String type) {
        if(this.certDb == null)
            return null;

        return this.certDb.getServers(type);
    }

    /**
     * Gets a collection of connectionStrings in the DB.
     *
     * @param type the type of certificate.
     * @return a collection of server names
     */
    @Override
    public NetConnectString[] getServerConnectStrings(String type) {
        if (this.certDb == null)
            return null;

        URI[] urls = this.certDb.getServerURIs(type);
        if (urls == null)
            return null;
        List<NetConnectString> returnList = new ArrayList<>(urls.length);
        for (URI url : urls) {
            returnList.add(NetConnectString.fromString(
                    url.getHost() + ":" +
                    url.getPort() + ":ssl"));
        }
        return returnList.toArray(new NetConnectString[0]);
    }

    /**
     * Gets the certificate of the given type and server.
     *
     * @param type   the certificate type.
     * @param server the server.
     * @return The <code>byte[]</code> of the certificate of the given type, null if doesn't exist.
     */
    private byte[] getCertificateForServer(String type, String server) {
        if(this.certDb == null)
            return null;

        return this.certDb.getCertificate(type, server, -1);
    }

    /**
     * Gets the certificate of the given type and server and port
     *
     * @param type   the certificate type.
     * @param server the server.
     * @param port the server.
     * @return The <code>byte[]</code> of the certificate of the given type, null if doesn't exist.
     */
    private byte[] getCertificateForServerAndPort(String type, String server, int port) {
        if(this.certDb == null)
            return null;

        return this.certDb.getCertificate(type, server, port);
    }


    /**
     * Gets the certificate of the given type and server.
     *
     * @param type   the certificate type.
     * @param server the server.
     * @return The <code>byte[]</code> of the certificate of the given type, null if doesn't exist.
     */
    private String getCertificateHashForServer(String type, String server) {
        if(this.certDb == null)
            return null;
        return this.certDb.getCertificateHash(type, server, -1);
    }

    /**
     * Gets the certificate of the given type and server.
     *
     * @param type   the certificate type.
     * @param server the server.
     * @return The <code>byte[]</code> of the certificate of the given type, null if doesn't exist.
     */
    private String getCertificateHashForServerAndPort(String type, String server, int port) {
        if(this.certDb == null)
            return null;
        return this.certDb.getCertificateHash(type, server, port);
    }

    /**
     * Saves the server certificate to the DB.
     *
     * @param type   the type of certificate
     * @param cert   the certificate represented as a <code>byte[]</code>
     * @param hash   the hash of the certificate.
     * @param server the server
     * @return true if successful, false otherwise.
     */
    private boolean saveCertificateForServer(String type, String server, byte[] cert,
                                               String hash) {
        if (this.certDb == null)
            return false;
        this.certDb.saveCertificate(type, server, -1, cert);
        return true;
    }

    private boolean saveCertificateForServerAndPort(String type, String server, int port, byte[] cert,
                                             String hash) {
        if (this.certDb == null)
            return false;
        this.certDb.saveCertificate(type, server, port, cert);
        return true;
    }

    /**
     * Deletes a certificate for server of the given type.
     *
     * @param type   the type of certificate to delete.
     * @param server the server for the certificate
     * @return True if successful, false otherwise.
     */
    private boolean deleteCertificateForServer(String type, String server) {
        if(this.certDb == null)
            return false;
        return this.certDb.deleteCertificate(type, server, -1);
    }

    /**
     * Deletes a certificate for server of the given type.
     *
     * @param type   the type of certificate to delete.
     * @param server the server for the certificate
     * @return True if successful, false otherwise.
     */
    private boolean deleteCertificateForServerAndPort(String type, String server, int port) {
        if(this.certDb == null)
            return false;
        return this.certDb.deleteCertificate(type, server, port);
    }

    public boolean openOrCreateDatabase(String path) {
        return openOrCreateDatabase(path, null) == 0;
    }
    int openOrCreateDatabase(String absolutePath, String pwd) {
        synchronized(lock) {
            if(this.certDb != null) {
                this.certDb.dispose();
                this.certDb = null;
            }
            try {
                this.certDb = new CertificateStore(absolutePath, pwd);
                return (this.certDb != null) ? 0 : -1;
            } catch(Throwable t) {
                return -1;
            }
        }
    }
}
