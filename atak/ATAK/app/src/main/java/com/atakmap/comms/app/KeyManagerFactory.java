
package com.atakmap.comms.app;

import android.content.Context;
import android.util.Pair;

import com.atakmap.comms.CotStreamListener;
import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.log.Log;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;
import com.atakmap.net.AtakCertificateDatabase;
import com.atakmap.net.AtakCertificateDatabaseIFace;
import com.atakmap.net.KeyManagerFactoryIFace;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.HashMap;

import javax.net.ssl.KeyManager;

public class KeyManagerFactory implements KeyManagerFactoryIFace {

    private static KeyManagerFactory instance = null;

    public static KeyManagerFactory getInstance(Context context) {
        if (instance == null) {
            instance = new KeyManagerFactory(context);
        }
        return instance;
    }

    public static final String TAG = "KeyManagerFactory";
    private CotStreamListener serverListener;
    private final HashMap<String, KeyManager[]> keyManagerCache = new HashMap<>();

    public KeyManagerFactory(Context context) {
        serverListener = new CotStreamListener(context, TAG, null);
    }

    public void dispose() {
        if (serverListener != null) {
            serverListener.dispose();
            serverListener = null;
        }
    }

    CotPortListActivity.CotPort[] getServers() {
        if (serverListener == null)
            return null;

        return serverListener.getServers();
    }

    @Override
    public KeyManager[] getKeyManagers(String server) {

        try {
            KeyManager[] keyManagers = keyManagerCache.get(server);
            if (keyManagers != null) {
                return keyManagers;
            }

            String[] parts = server.split(":");
            if (parts == null || parts.length < 2) {
                Log.e(TAG, "getKeyManagers called with invalid server: "
                        + server);
                return null;
            }

            server = parts[1].substring(2);

            //
            // look for connection specific certs first
            //
            byte[] clientCert = AtakCertificateDatabase
                    .getCertificateForServer(
                            AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                            server);
            AtakAuthenticationCredentials clientCertCredentials = AtakAuthenticationDatabase
                    .getCredentials(
                            AtakAuthenticationCredentials.TYPE_clientPassword,
                            server);

            if (clientCert == null) {
                Pair<byte[], String> results = AtakCertificateDatabase
                        .getCertificateForIPaddress(
                                AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                                server);

                if (results != null && results.first != null
                        && results.second != null) {
                    clientCert = results.first;
                    server = results.second;

                    clientCertCredentials = AtakAuthenticationDatabase
                            .getCredentials(
                                    AtakAuthenticationCredentials.TYPE_clientPassword,
                                    server);
                }
            }

            //
            // try getting the default certs
            //
            if (clientCert == null) {
                clientCert = AtakCertificateDatabase
                        .getCertificate(
                                AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE);
                clientCertCredentials = AtakAuthenticationDatabase
                        .getCredentials(
                                AtakAuthenticationCredentials.TYPE_clientPassword);
            }

            if (clientCert == null) {
                throw new RuntimeException(
                        "Please re-import your client certificate");
            }

            if (clientCertCredentials == null
                    || clientCertCredentials.password == null
                    || clientCertCredentials.password.length() == 0) {
                throw new RuntimeException(
                        "Please re-enter your client certificate password");
            }

            //
            // load the cert into a key stores
            //
            InputStream clientIn;
            KeyStore client = KeyStore.getInstance("PKCS12");
            clientIn = new ByteArrayInputStream(clientCert);
            client.load(clientIn, clientCertCredentials.password.toCharArray());
            clientIn.close();

            //
            // get key managers for the keystore
            //
            javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory
                    .getInstance(
                            javax.net.ssl.KeyManagerFactory
                                    .getDefaultAlgorithm());
            kmf.init(client, clientCertCredentials.password.toCharArray());
            keyManagers = kmf.getKeyManagers();

            return keyManagers;

        } catch (Exception e) {
            Log.e(TAG, "Exception in getKeyManagers! " + e.getMessage());
            return null;
        }
    }

    @Override
    public KeyManager[] getKeyManagers(NetConnectString netConnectString) {

        try {
            KeyManager[] keyManagers = keyManagerCache
                    .get(netConnectString.toString());
            if (keyManagers != null) {
                return keyManagers;
            }

            //
            // look for connection specific certs first
            //
            byte[] clientCert = AtakCertificateDatabase
                    .getCertificateForServerAndPort(
                            AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                            netConnectString.getHost(),
                            netConnectString.getPort());

            // TODO need to pull by server and port
            AtakAuthenticationCredentials clientCertCredentials = AtakAuthenticationDatabase
                    .getCredentials(
                            AtakAuthenticationCredentials.TYPE_clientPassword,
                            netConnectString.getHost());

            if (clientCert == null) {
                Pair<byte[], String> results = AtakCertificateDatabase
                        .getCertificateForIPaddress(
                                AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE,
                                netConnectString.getHost(),
                                netConnectString.getPort());

                if (results != null && results.first != null
                        && results.second != null) {
                    clientCert = results.first;
                    String server = results.second;

                    clientCertCredentials = AtakAuthenticationDatabase
                            .getCredentials(
                                    AtakAuthenticationCredentials.TYPE_clientPassword,
                                    server);
                }
            }

            //
            // try getting the default certs
            //
            if (clientCert == null) {
                clientCert = AtakCertificateDatabase
                        .getCertificate(
                                AtakCertificateDatabaseIFace.TYPE_CLIENT_CERTIFICATE);
                clientCertCredentials = AtakAuthenticationDatabase
                        .getCredentials(
                                AtakAuthenticationCredentials.TYPE_clientPassword);
            }

            if (clientCert == null) {
                throw new RuntimeException(
                        "Please re-import your client certificate");
            }

            if (clientCertCredentials == null
                    || clientCertCredentials.password == null
                    || clientCertCredentials.password.length() == 0) {
                throw new RuntimeException(
                        "Please re-enter your client certificate password");
            }

            //
            // load the cert into a key stores
            //
            InputStream clientIn;
            KeyStore client = KeyStore.getInstance("PKCS12");
            clientIn = new ByteArrayInputStream(clientCert);
            client.load(clientIn, clientCertCredentials.password.toCharArray());
            clientIn.close();

            //
            // get key managers for the keystore
            //
            javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory
                    .getInstance(
                            javax.net.ssl.KeyManagerFactory
                                    .getDefaultAlgorithm());
            kmf.init(client, clientCertCredentials.password.toCharArray());
            keyManagers = kmf.getKeyManagers();

            return keyManagers;

        } catch (Exception e) {
            Log.e(TAG, "Exception in getKeyManagers! " + e.getMessage());
            return null;
        }
    }

}
