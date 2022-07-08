
package com.atakmap.comms.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;
import com.atakmap.net.AtakCertificateDatabase;
import com.atakmap.net.AtakCertificateDatabaseIFace;
import com.atakmap.net.CertificateManager;

import org.apache.http.conn.ssl.SSLSocketFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;

/**
 * TLS helper methods
 *
 */

public class TLSUtils {

    private final static String TAG = "TLSUtils";

    /**
     * Get TAK Server truststore for specified server host
     * Optionally, use default truststore
     *
     * @param server    if empty, then use default truststore
     * @param bUseDefault   if true, then use default if truststore not found for specified server
     * @return the trust store for the server based on the input.
     */
    public static TrustStore getTruststore(String server, boolean bUseDefault) {
        if (FileSystemUtils.isEmpty(server)) {
            return getDefaultTruststore(false);
        }

        byte[] data = AtakCertificateDatabase.getCertificateForServer(
                AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA,
                server);

        if (data != null && data.length > 0) {
            AtakAuthenticationCredentials caCertCredentials = AtakAuthenticationDatabase
                    .getCredentials(
                            AtakAuthenticationCredentials.TYPE_caPassword,
                            server);

            if (caCertCredentials != null
                    && !FileSystemUtils.isEmpty(caCertCredentials.password)) {
                Log.d(TAG, "Found truststore for: " + server);
                return new TrustStore(data, caCertCredentials.password);
            }
        }

        //use default creds
        if (bUseDefault) {
            return getDefaultTruststore(false);
        }

        Log.d(TAG, "No truststore found for: " + server);
        return null;

    }

    /**
     * Helper method to get Truststore and credentials
     *
     * @param truststoreType the truststore type
     * @param credentialType the credential type
     * @return the truststore for the specified trustore and credential types
     */
    public static TrustStore getTruststoreForType(String truststoreType,
            String credentialType) {
        byte[] trsuststore = AtakCertificateDatabase
                .getCertificate(truststoreType);

        AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                .getCredentials(credentialType);

        if (trsuststore != null && credentials != null
                && !FileSystemUtils.isEmpty(credentials.password)) {
            return new TrustStore(trsuststore, credentials.password);
        }

        Log.w(TAG, "No truststore found for type: " + truststoreType);
        return null;
    }

    /**
     * Get default TAK Server truststore
     * Optionally, get first available (if default not set)
     *
     * @return the default truststore
     */
    public static TrustStore getDefaultTruststore(boolean bFirstAvailable) {
        //look for default truststore
        AtakAuthenticationCredentials caCertCredentials = null;
        byte[] data = AtakCertificateDatabase
                .getCertificate(
                        AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA);
        if (data != null && data.length > 0) {
            caCertCredentials = AtakAuthenticationDatabase
                    .getCredentials(
                            AtakAuthenticationCredentials.TYPE_caPassword);

            if (caCertCredentials != null
                    && !FileSystemUtils.isEmpty(caCertCredentials.password)) {
                Log.d(TAG, "Found default truststore");
                return new TrustStore(data, caCertCredentials.password);
            }
        }

        if (bFirstAvailable) {
            CotPortListActivity.CotPort[] servers = KeyManagerFactory
                    .getInstance(MapView._mapView.getContext()).getServers();
            if (servers != null && servers.length > 0) {
                Log.d(TAG,
                        "No default truststore, looking for first available...");
                for (CotPortListActivity.CotPort cotPort : servers) {
                    NetConnectString ncs = NetConnectString.fromString(cotPort
                            .getConnectString());
                    String server = ncs.getHost();
                    if (!FileSystemUtils.isEmpty(server)) {
                        data = AtakCertificateDatabase
                                .getCertificateForServer(
                                        AtakCertificateDatabaseIFace.TYPE_TRUST_STORE_CA,
                                        server);

                        if (data != null && data.length > 0) {
                            caCertCredentials = AtakAuthenticationDatabase
                                    .getCredentials(
                                            AtakAuthenticationCredentials.TYPE_caPassword,
                                            server);

                            if (caCertCredentials != null
                                    && !FileSystemUtils
                                            .isEmpty(
                                                    caCertCredentials.password)) {
                                Log.d(TAG, "Found truststore for: " + server);
                                return new TrustStore(data,
                                        caCertCredentials.password);
                            }
                        }
                    }
                }
            }

        }

        Log.w(TAG, "No truststores found");
        return null;
    }

    /**
     * Get default TAK Server credentials (TYPE_COT_SERVICE)
     * Optionally, get first available (if default not set)
     *
     * @return the default credentials
     */
    public static AtakAuthenticationCredentials getDefaultCredentials(
            boolean bFirstAvailable) {
        //look for default creds
        AtakAuthenticationCredentials creds = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_COT_SERVICE);

        if (creds != null && !FileSystemUtils.isEmpty(creds.username)) {
            Log.d(TAG, "Found default credentials");
            return creds;
        }

        if (bFirstAvailable) {
            CotPortListActivity.CotPort[] servers = KeyManagerFactory
                    .getInstance(MapView._mapView.getContext()).getServers();
            if (servers != null && servers.length > 0) {
                Log.d(TAG,
                        "No default credentials, looking for first available...");
                for (CotPortListActivity.CotPort cotPort : servers) {
                    NetConnectString ncs = NetConnectString.fromString(cotPort
                            .getConnectString());
                    String server = ncs.getHost();
                    if (!FileSystemUtils.isEmpty(server)) {
                        creds = AtakAuthenticationDatabase
                                .getCredentials(
                                        AtakAuthenticationCredentials.TYPE_COT_SERVICE,
                                        server);

                        if (creds != null
                                && !FileSystemUtils.isEmpty(creds.username)) {
                            Log.d(TAG, "Found credentials for: " + server);
                            return creds;
                        }
                    }
                }
            }

        }

        Log.w(TAG, "No credentials found");
        return null;
    }

    /**
     * Get TAK Server credentials (TYPE_COT_SERVICE) for specified server host
     * Optionally, use default credentials
     *
     * @param server    if empty, then use default credentials
     * @param bUseDefault   if true, then use default if credentials not found for specified server
     * @return the authentication credentials.
     */
    public static AtakAuthenticationCredentials getCredentials(String server,
            boolean bUseDefault) {
        if (FileSystemUtils.isEmpty(server)) {
            return getDefaultCredentials(false);
        }

        //look for server creds
        AtakAuthenticationCredentials creds = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_COT_SERVICE,
                        server);

        if (creds != null && !FileSystemUtils.isEmpty(creds.username)) {
            Log.d(TAG, "Found credentials for: " + server);
            return creds;
        }

        //use default creds
        if (bUseDefault) {
            return getDefaultCredentials(false);
        }

        Log.d(TAG, "No credentials found for: " + server);
        return null;
    }

    /**
     * Get list of TAK Server connections which have User Authentication credentials
     * available. Optionally require 'useAuth' be actively enabled
     *
     * @return list of server connections
     */
    public static List<CotPortListActivity.CotPort> getServersWithCredentials(
            final boolean bRequireUseAuth) {
        final List<CotPortListActivity.CotPort> serversWithCred = new ArrayList<>();
        CotMapComponent inst = CotMapComponent.getInstance();
        if (inst == null)
            return serversWithCred;

        //check how many server connections have credentials
        final CotPortListActivity.CotPort[] servers = inst.getServers();
        if (servers == null || servers.length == 0) {
            Log.w(TAG, "No servers available");
            return serversWithCred;
        }

        for (CotPortListActivity.CotPort server : servers) {
            if (server == null)
                continue;

            if (!bRequireUseAuth || server.isUsingAuth()) {
                Log.d(TAG,
                        "Checking for server creds: "
                                + server.getConnectString());
                NetConnectString ncs = NetConnectString.fromString(server
                        .getConnectString());
                if (ncs == null) {
                    Log.w(TAG, "Unable to parse: " + server.getConnectString());
                    continue;
                }

                AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                        .getCredentials(
                                AtakAuthenticationCredentials.TYPE_COT_SERVICE,
                                ncs.getHost());
                if (credentials == null
                        || FileSystemUtils.isEmpty(credentials.username)
                        || FileSystemUtils.isEmpty(credentials.password)) {
                    Log.d(TAG, "No user password set: " + ncs.getHost());
                    continue;
                }

                Log.d(TAG, "Found host with credentials: " + ncs.getHost());
                serversWithCred.add(server);
            }
        }

        return serversWithCred;
    }

    /**
     * Pull truststore and credentials from internal storage
     * Create an SSL Context for the truststore
     *
     * @param truststoreType the truststore type
     * @param credentialType the credential type
     * @return
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     * @throws IOException
     * @throws CertificateException
     */
    public static SSLContext createSSLContext(final String truststoreType,
            final String credentialType) throws KeyStoreException,
            NoSuchAlgorithmException, KeyManagementException, IOException,
            CertificateException {
        if (FileSystemUtils.isEmpty(truststoreType)
                || FileSystemUtils.isEmpty(credentialType))
            return null;

        return createSSLContext(TLSUtils.getTruststoreForType(truststoreType,
                credentialType));
    }

    /**
     * Create an SSL context from the Truststore
     *
     * @param truststore
     * @return
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     * @throws IOException
     * @throws CertificateException
     */
    public static SSLContext createSSLContext(TrustStore truststore)
            throws KeyStoreException,
            NoSuchAlgorithmException, KeyManagementException, IOException,
            CertificateException {

        if (truststore == null)
            return null;

        return createSSLContext(truststore.getData(), truststore.getPass());
    }

    /**
     * Create an SSL context from the truststore and specified password
     * Use PKS12 and TLS
     *
     * @param truststore
     * @param password
     * @return
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     * @throws IOException
     * @throws CertificateException
     */
    public static SSLContext createSSLContext(final byte[] truststore,
            final String password) throws KeyStoreException,
            NoSuchAlgorithmException, KeyManagementException, IOException,
            CertificateException {

        SSLContext sslContext = null;
        if (FileSystemUtils.isEmpty(truststore) || password == null)
            return sslContext;

        InputStream trustedIn = null;
        try {
            trustedIn = new ByteArrayInputStream(truststore);
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            trustStore.load(trustedIn, password.toCharArray());

            javax.net.ssl.TrustManagerFactory trustManagerFactory = javax.net.ssl.TrustManagerFactory
                    .getInstance(javax.net.ssl.TrustManagerFactory
                            .getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            sslContext = CertificateManager.createSSLContext(
                    trustManagerFactory.getTrustManagers());
        } finally {
            if (trustedIn != null)
                trustedIn.close();
        }

        return sslContext;
    }

    /**
     * Pull truststore and credentials from internal storage
     * Create an SSL Socket Factory for the truststore
     *
     * @param truststoreType
     * @param credentialType
     * @return
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     * @throws IOException
     * @throws CertificateException
     * @throws UnrecoverableKeyException
     */
    public static SSLSocketFactory createSSLSocketFactory(
            final String truststoreType, final String credentialType)
            throws KeyStoreException,
            NoSuchAlgorithmException, KeyManagementException, IOException,
            CertificateException, UnrecoverableKeyException {
        if (FileSystemUtils.isEmpty(truststoreType)
                || FileSystemUtils.isEmpty(credentialType))
            return null;

        return createSSLSocketFactory(TLSUtils.getTruststoreForType(
                truststoreType, credentialType));
    }

    /**
     * Create an SSL context from the Truststore
     *
     * @param truststore
     * @return
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     * @throws IOException
     * @throws CertificateException
     * @throws UnrecoverableKeyException
     */
    public static SSLSocketFactory createSSLSocketFactory(
            final TrustStore truststore) throws KeyStoreException,
            NoSuchAlgorithmException, KeyManagementException, IOException,
            CertificateException, UnrecoverableKeyException {
        if (truststore == null)
            return null;

        return createSSLSocketFactory(truststore.getData(),
                truststore.getPass());
    }

    /**
     * Create an SSL context from the truststore and specified password
     *
     * @param truststore
     * @param password
     * @return
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     * @throws IOException
     * @throws CertificateException
     * @throws UnrecoverableKeyException
     */
    public static SSLSocketFactory createSSLSocketFactory(
            final byte[] truststore, final String password)
            throws KeyStoreException,
            NoSuchAlgorithmException, KeyManagementException, IOException,
            CertificateException, UnrecoverableKeyException {

        SSLContext sslContext = createSSLContext(truststore, password);
        if (sslContext == null)
            return null;

        return new CertificateManager.ExtendedSSLSocketFactory(sslContext);
    }

    /**
     * Validate client cert stored for specified connection
     *
     * @param hostname
     * @return
     */
    public static AtakCertificateDatabase.CertificateValidity validateCert(
            final String hostname) {
        if (FileSystemUtils.isEmpty(hostname)) {
            Log.w(TAG, "validateCert invalid hostname");
            return null;
        }

        Log.d(TAG, "Checking cert validity for: " + hostname);

        byte[] clientCert = AtakCertificateDatabase
                .getCertificateForServer(
                        AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                        hostname);
        AtakAuthenticationCredentials clientCertCredentials = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_clientPassword,
                        hostname);

        if (clientCert == null) {
            clientCert = AtakCertificateDatabase
                    .getCertificate(
                            AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE);
            clientCertCredentials = AtakAuthenticationDatabase
                    .getCredentials(
                            AtakAuthenticationCredentials.TYPE_clientPassword);
        }

        if (clientCert != null
                && clientCertCredentials != null
                &&
                !FileSystemUtils
                        .isEmpty(clientCertCredentials.password)) {

            return AtakCertificateDatabase.checkValidity(clientCert,
                    clientCertCredentials.password);
        } else {
            Log.w(TAG, "Cert not found for: " + hostname);
            return null;
        }
    }

    /**
     * Validate client cert stored for specified connection
     *
     * @param hostname
     * @param port
     * @return
     */
    public static AtakCertificateDatabase.CertificateValidity validateCert(
            final String hostname, final int port) {
        if (FileSystemUtils.isEmpty(hostname)) {
            Log.w(TAG, "validateCert invalid hostname");
            return null;
        }

        Log.d(TAG, "Checking cert validity for: " + hostname);

        byte[] clientCert = AtakCertificateDatabase
                .getCertificateForServerAndPort(
                        AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                        hostname, port);
        AtakAuthenticationCredentials clientCertCredentials = AtakAuthenticationDatabase
                .getCredentials(
                        AtakAuthenticationCredentials.TYPE_clientPassword,
                        hostname);

        if (clientCert == null) {
            clientCert = AtakCertificateDatabase
                    .getCertificate(
                            AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE);
            clientCertCredentials = AtakAuthenticationDatabase
                    .getCredentials(
                            AtakAuthenticationCredentials.TYPE_clientPassword);
        }

        if (clientCert != null
                && clientCertCredentials != null
                &&
                !FileSystemUtils
                        .isEmpty(clientCertCredentials.password)) {

            return AtakCertificateDatabase.checkValidity(clientCert,
                    clientCertCredentials.password);
        } else {
            Log.w(TAG, "Cert not found for: " + hostname);
            return null;
        }
    }

    public static void promptCertificateError(final Context context,
            final String host, final String message,
            final boolean interactive) {
        if (FileSystemUtils.isEmpty(host) || FileSystemUtils.isEmpty(message)) {
            Log.w(TAG, "Skipping invalid prompt");
            return;
        }

        MapView.getMapView().post(new Runnable() {
            @Override
            public void run() {
                if (!interactive) {
                    String ticker = host + " " + message;
                    NotificationUtil.getInstance().postNotification(
                            R.drawable.ic_network_error_notification_icon,
                            "TAK Certificate Issue", ticker, ticker,
                            new Intent("com.atakmap.app.NETWORK_SETTINGS"));
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(
                            context)
                                    .setTitle(host)
                                    .setIcon(
                                            R.drawable.ic_network_error_notification_icon)
                                    .setMessage(
                                            "TAK Server connectivity issue: "
                                                    + message
                                                    + ". Open network settings now?")
                                    .setPositiveButton(R.string.ok,
                                            new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(
                                                        DialogInterface dialog,
                                                        int i) {
                                                    dialog.dismiss();
                                                    context.startActivity(
                                                            new Intent(
                                                                    context,
                                                                    CotStreamListActivity.class));
                                                }
                                            })
                                    .setNegativeButton(R.string.cancel, null);
                    try {
                        builder.show();
                    } catch (Exception ignored) {
                        // catch a android.view.WindowManager$BadTokenException if it occurs
                    }
                }
            }
        });
    }
}
