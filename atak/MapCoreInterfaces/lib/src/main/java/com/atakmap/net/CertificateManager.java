
package com.atakmap.net;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Bundle;

import com.atakmap.coremap.log.Log;

import java.io.ByteArrayInputStream;
import java.net.Socket;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;

public class CertificateManager {

    public static final String TAG = "CertificateManager";
    private final static List<X509Certificate> certificates = new ArrayList<>();
    private X509TrustManager localTrustManager = null;
    private static CertificateManager _this;
    private X509TrustManager systemTrustManager = null;

    public static final CentralTrustManager SelfSignedAcceptingTrustManager = new CentralTrustManager();
    private static AtakCertificateDatabaseIFace certificateDatabase = null;
    private static KeyManagerFactoryIFace keyManagerFactory = null;
    private static HashMap<String, ExtendedSSLSocketFactory> socketFactories = new HashMap<>();

    /**
     * Obtain an instance of the CertificateManager class.
     * @return
     */
    public synchronized static CertificateManager getInstance() {
        if (_this == null)
            _this = new CertificateManager();

        return _this;
    }

    private CertificateManager() {
    }

    /**
     * Initialize the current Certificate Manager with the context for the ATAK
     * application.   This will install 2 DoD root certificates.
     * @param ctx a valid application context
     */
    public void initialize(final Context ctx) {

        try {
            final TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());

            // Using null here initialises the TMF with the default trust store.   
            // In this case, this is setting the systemTrustManager to only the 
            // trust supplied by the underlying system.   The only time this 
            // trust manager is used is used is if a developer actively makes a
            // call to getSystemTrustManager()
            tmf.init((KeyStore) null);

            // Get hold of the default trust manager
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    systemTrustManager = (X509TrustManager) tm;
                    Log.d(TAG, "found the system X509TrustManager: " + tm);
                    break;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "unable to initialize X509TrustManager", e);
        }

        // DoD root CA 
        addCertNoRefresh(ctx, "certs/DODSWCA-61.crt");
        addCertNoRefresh(ctx, "certs/DODSWCA-54.crt");
        addCertNoRefresh(ctx, "certs/DODIDSWCA-38.crt");
        addCertNoRefresh(ctx, "certs/DoDRootCA3.crt");
        addCertNoRefresh(ctx, "certs/DoDRootCA5.crt");

        // Verisign / DigiCert  
        addCertNoRefresh(ctx, "certs/DigiCertHighAssuranceEVRootCA.crt");
        addCertNoRefresh(ctx, "certs/DigiCertGlobalRootCA.crt");

        // LetsEncrypt
        addCertNoRefresh(ctx, "certs/DSTRootCAX3.crt");

        refresh();
    }

    private void addCertNoRefresh(final Context ctx, final String name) {

        try {
            final X509Certificate cert = getCertFromFile(ctx, name);

            if (cert != null)
                certificates.add(cert);
        } catch (Exception e) {
            Log.d(TAG, "error initializing: " + name);
        }
    }

    /**
     * Add a cert to the current local trust store.
     * @param cert the X509 certificate
     */
    public synchronized void addCertificate(X509Certificate cert) {
        if (cert == null) {
            return;
        }

        Log.d(TAG, "added: " + cert);
        certificates.add(cert);
        refresh();
    }

    /**
     * Add a cert to the current local trust store.
     * @param cert the X509 certificate
     */
    public synchronized void removeCertificate(X509Certificate cert) {
        if (cert == null) {
            return;
        }
        Log.d(TAG, "removed: " + cert);

        certificates.remove(cert);
        refresh();
    }

    private List<X509Certificate> getAcceptedIssuers() {
        List<X509Certificate> trustedIssuers = new LinkedList<>(certificates);
        List<X509Certificate> certificateDatabaseCerts = certificateDatabase
                .getCACerts();
        if (certificateDatabaseCerts != null) {
            trustedIssuers.addAll(certificateDatabaseCerts);
        }
        return trustedIssuers;
    }

    /**
     * Rebuild the localTrustManager based on the currently supplied certificates.
     */
    public void refresh() {

        try {
            final KeyStore localTrustStore = KeyStore.getInstance("BKS");
            localTrustStore.load(null, null);

            for (final X509Certificate cert : getAcceptedIssuers()) {
                final String alias = (cert.getSubjectX500Principal())
                        .hashCode() + "";
                localTrustStore.setCertificateEntry(alias, cert);
            }

            String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(tmfAlgorithm);
            tmf.init(localTrustStore);
            TrustManager[] trustManagers = tmf.getTrustManagers();
            localTrustManager = (X509TrustManager) trustManagers[0];

            socketFactories.clear();

            Log.d(TAG, "obtained localized trust manager");
        } catch (Exception e) {
            Log.d(TAG, "error obtaining localized trust manager", e);
        }
    }

    /** 
     * Given a X509 Trust Manager, provide the certificates to include the ones
     * internally contained within ATAK.
     */
    public X509Certificate[] getCertificates(final X509TrustManager x509Tm) {

        java.security.cert.X509Certificate[] localCerts;

        if (x509Tm == null)
            localCerts = new X509Certificate[0];
        else
            localCerts = x509Tm.getAcceptedIssuers();

        // in case x509Tm.getAcceptedIssuers() returns null
        if (localCerts == null)
            localCerts = new X509Certificate[0];

        List<X509Certificate> acceptedIssuers = getAcceptedIssuers();

        java.security.cert.X509Certificate[] certs = new java.security.cert.X509Certificate[localCerts.length
                + acceptedIssuers.size()];

        System.arraycopy(localCerts, 0, certs, 0, localCerts.length);

        for (int i = 0; i < acceptedIssuers.size(); ++i) {
            Log.d(TAG, "added: " + acceptedIssuers.get(i));
            certs[i + localCerts.length] = acceptedIssuers.get(i);
        }

        return certs;
    }

    /**
     */
    private static X509Certificate getCertFromFile(Context context, String path)
            throws Exception {
        AssetManager assetManager = context.getResources().getAssets();
        InputStream inputStream;
        InputStream caInput = null;
        X509Certificate cert = null;

        try {
            inputStream = assetManager.open(path);
        } catch (IOException e) {
            Log.d(TAG, "error occured loading cert", e);
            return null;
        }
        try {
            if (inputStream != null) {
                caInput = new BufferedInputStream(inputStream);
                CertificateFactory cf = CertificateFactory.getInstance("X509");
                cert = (X509Certificate) cf.generateCertificate(caInput);
                //Log.d(TAG, "completed: " + path + " " + cert.getSerialNumber());
            }
        } finally {

            if (caInput != null) {
                try {
                    caInput.close();
                } catch (IOException ignored) {
                }
            }
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }

        }
        return cert;
    }

    /**
     * Returns a local trust manager.
     * @return the trust manager controlled by ATAK and populated with known trusted sources.
     */
    public X509TrustManager getLocalTrustManager() {
        return localTrustManager;
    }

    /**
     * Returns a system trust manager.   This is is an unverified trust manager that is supplied
     * with the Android OS and could have not very trustworthy sources in it.  This method should
     * only be called if the client then validates things.
     * @return the system trust manager which is not validated.
     */
    public X509TrustManager getSystemTrustManager() {
        return systemTrustManager;
    }

    /**
     * Implementation of a self signed trust manager that will allow for either certificates signed
     * with a certificate installed on the device, one of the pre-supplied DoD certificates or will
     * just validate a self signed certificate.   Self signed certificates are still in use on private
     * tactical networks.
     */
    public final static class CentralTrustManager implements X509TrustManager {
        private boolean iss = false;

        // store an instance of the socket factory that's using a CentralTrustManager in order
        // to pass back the server certificates
        private ExtendedSSLSocketFactory extendedSSLSocketFactory = null;

        private final static CertificatePrompt PERMISSIVE_CERT_PROMPT = new CertificatePrompt() {
            @Override
            public boolean promptToUseSystemTrustManager(
                    java.security.cert.X509Certificate[] certs) {
                Log.d(TAG,
                        "using permissive prompt for system trust manager access");
                return true;
            }

            @Override
            public boolean promptToAccept(
                    java.security.cert.X509Certificate[] certs) {
                Log.d(TAG,
                        "self signed server cert detected, without anchor, blocked by default");
                return false;
            }

        };

        private CertificatePrompt cp = PERMISSIVE_CERT_PROMPT;

        public interface CertificatePrompt {
            /**
             * Callback that can be used to prompt the user to make use of
             * the System TrustStore.
             * @param certs are the certs passed in so the user can decide if to accept them or not.
             */
            boolean promptToUseSystemTrustManager(
                    java.security.cert.X509Certificate[] certs);

            /**
             * Callback that can be used to prompt the user to make use of self signed certs of length 1
             * generated for example, by this little gem: 
             * <p>
             *     openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout mysitename.key -out mysitename.crt
             * </p>
             * @param certs are the certs passed in so the user can decide if to accept them or not.
             */
            boolean promptToAccept(java.security.cert.X509Certificate[] certs);
        }

        /**
         * Whether to use the system TrustManager when validating a certificate that
         * may be self signed.
         * @param iss true to use the system TrustManager, false to only use the 
         * local TrustManager.
         * 
         */
        public void setIgnoreSystemCerts(final boolean iss) {
            this.iss = iss;
        }

        private CentralTrustManager() {
        }

        private CentralTrustManager(
                ExtendedSSLSocketFactory extendedSSLSocketFactory) {
            this.extendedSSLSocketFactory = extendedSSLSocketFactory;
        }

        /**
         * Install a prompt implementation if the System Trust Store Manager needs to 
         * be accessed.   This is a global change across the application and only one 
         * system prompt may be registered.
         */
        public void setSystemCertificatePrompt(final CertificatePrompt cp) {
            if (cp != null)
                this.cp = cp;
            else
                this.cp = PERMISSIVE_CERT_PROMPT;
        }

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {

            X509TrustManager x509Tm = null;

            if (!iss) {
                Log.d(TAG, "using system trust manager, getAcceptedIssuers");
                x509Tm = CertificateManager.getInstance()
                        .getSystemTrustManager();
            }

            return CertificateManager.getInstance()
                    .getCertificates(x509Tm);
        }

        @Override
        public void checkClientTrusted(
                java.security.cert.X509Certificate[] certs,
                String authType) throws CertificateException {

            X509TrustManager ltm = CertificateManager.getInstance()
                    .getLocalTrustManager();
            if (iss) {
                ltm.checkClientTrusted(certs, authType);
            } else {
                try {
                    ltm.checkClientTrusted(certs, authType);
                } catch (CertificateException ce) {
                    X509TrustManager x509Tm = CertificateManager.getInstance()
                            .getSystemTrustManager();
                    if (x509Tm != null
                            && cp.promptToUseSystemTrustManager(certs)) {
                        Log.d(TAG,
                                "using system trust manager, checkClientTrusted");
                        x509Tm.checkClientTrusted(certs, authType);
                    } else {
                        Log.d(TAG, "System TrustManager access denied");
                        throw new CertificateException(
                                "System TrustManager denied");
                    }
                }
            }

        }

        @Override
        public void checkServerTrusted(
                java.security.cert.X509Certificate[] certs,
                String authType) throws CertificateException {

            if (certs == null) {
                Log.e(TAG, "checkServerTrusted called with null certs array!");
                throw new IllegalArgumentException();
            }

            if (extendedSSLSocketFactory != null) {
                extendedSSLSocketFactory.setServerCerts(certs);
            }

            for (X509Certificate cert : certs) {
                cert.checkValidity();
            }

            X509TrustManager ltm = CertificateManager.getInstance()
                    .getLocalTrustManager();
            if (iss) {
                try {
                    ltm.checkServerTrusted(certs, authType);
                } catch (CertificateException ce) {
                    if (cp.promptToAccept(certs)) {
                        // user has decided to accept the risk
                    } else {
                        throw ce;
                    }
                }
            } else {
                try {
                    ltm.checkServerTrusted(certs, authType);
                } catch (CertificateException ce) {

                    X509TrustManager x509Tm = CertificateManager
                            .getInstance().getSystemTrustManager();
                    if (x509Tm != null
                            && cp.promptToUseSystemTrustManager(certs)) {
                        Log.d(TAG,
                                "using system trust manager, checkServerTrusted");
                        try {
                            x509Tm.checkServerTrusted(certs, authType);
                        } catch (CertificateException ce2) {
                            if (cp.promptToAccept(certs)) {
                                // user has decided to accept the risk
                            } else {
                                throw ce2;
                            }
                        }
                    } else {
                        Log.d(TAG, "System TrustManager access denied");
                        throw new CertificateException(
                                "System TrustManager denied");
                    }
                }
            }
        }

    };

    public static final class ExtendedSSLSocketFactory
            extends SSLSocketFactory {
        final SSLContext sslContext;
        boolean allowAllHostnames = true;
        X509Certificate[] serverCerts;

        public ExtendedSSLSocketFactory(String server,
                boolean allowAllHostnames)
                throws NoSuchAlgorithmException, KeyManagementException,
                KeyStoreException, UnrecoverableKeyException {
            super((KeyStore) null);

            CentralTrustManager centralTrustManager = new CentralTrustManager(
                    this);
            centralTrustManager.setIgnoreSystemCerts(true);

            this.allowAllHostnames = allowAllHostnames;
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(
                    keyManagerFactory.getKeyManagers(server),
                    new TrustManager[] {
                            centralTrustManager
                    },
                    null);
        }

        public ExtendedSSLSocketFactory(SSLContext c)
                throws UnrecoverableKeyException, NoSuchAlgorithmException,
                KeyStoreException, KeyManagementException {
            super((KeyStore) null);
            sslContext = c;
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port,
                boolean autoClose) throws IOException {

            SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory()
                    .createSocket(socket, host, port, autoClose);

            if (!allowAllHostnames) {
                SSLSocketFactory.STRICT_HOSTNAME_VERIFIER.verify(
                        host, sslSocket);
            }

            return sslSocket;
        }

        @Override
        public Socket createSocket() throws IOException {
            return sslContext.getSocketFactory().createSocket();
        }

        public X509Certificate[] getServerCerts() {
            return serverCerts;
        }

        public void setServerCerts(X509Certificate[] serverCerts) {
            this.serverCerts = serverCerts;
        }
    }

    public static ExtendedSSLSocketFactory getSockFactory(
            boolean useCache, String server, boolean allowAllHostnames) {

        ExtendedSSLSocketFactory socketFactory = null;
        if (useCache) {
            socketFactory = socketFactories.get(server);
            if (socketFactory != null) {
                return socketFactory;
            }
        }

        try {

            Log.i(TAG, "creating socketFactory for : " + server);
            socketFactory = new ExtendedSSLSocketFactory(server,
                    allowAllHostnames);

            if (useCache) {
                socketFactories.put(server, socketFactory);
            }

            return socketFactory;

        } catch (Exception e) {
            Log.e(TAG, "Exception in getSocketFactory! " + e.getMessage());
        }

        return null;
    }

    public static ExtendedSSLSocketFactory getSockFactory(boolean useCache,
            String server) {
        return getSockFactory(useCache, server, true);
    }

    /**
     * evict all entries in the socketFactories cache for the given host. Entries in the
     * cache are keyed based on a full URL (ie https://host:port/Marti). This function is
     * passed the host and will evict all corresponding cache entries.
     * @param host All entries corresponding to this host will be removed
     */
    public static void invalidate(String host) {
        Log.i(TAG, "removing cached socketFactories for : " + host);

        Iterator<HashMap.Entry<String, ExtendedSSLSocketFactory>> itr = socketFactories
                .entrySet().iterator();
        while (itr.hasNext()) {
            HashMap.Entry<String, ExtendedSSLSocketFactory> next = itr.next();
            String nextServer = next.getKey();
            if (nextServer.compareTo(host) == 0 || nextServer.contains(host)) {
                itr.remove();
                Log.i(TAG, "removed socketFactory for : " + nextServer);
            }
        }
    }

    /**
     * Provides a permissive host name verified.  This is used on tactical networks where 
     * host name verification might not be possible.
     */
    public static final javax.net.ssl.HostnameVerifier ALL_TRUSTING_HOSTNAME_VERIFIER = new javax.net.ssl.HostnameVerifier() {
        @Override
        public boolean verify(String hostname,
                javax.net.ssl.SSLSession session) {
            return true;
        }
    };

    public static void setCertificateDatabase(
            final AtakCertificateDatabaseIFace certdb) {
        certificateDatabase = certdb;
    }

    public static void setKeyManagerFactory(
            final KeyManagerFactoryIFace kmf) {
        keyManagerFactory = kmf;
    }

    public static Bundle certArrayToBundle(X509Certificate[] certs)
            throws CertificateEncodingException {
        Integer ndx = 0;
        Bundle certBundle = new Bundle();
        certBundle.putInt("certCount", certs.length);
        for (X509Certificate cert : certs) {
            certBundle.putByteArray(ndx.toString(), cert.getEncoded());
            ndx++;
        }
        return certBundle;
    }

    public static List<X509Certificate> certBundleToArray(
            Bundle certBundle) throws CertificateException {
        final List<X509Certificate> results = new ArrayList<>();
        final Integer certCount = certBundle.getInt("certCount");
        final CertificateFactory certFactory = CertificateFactory
                .getInstance("X.509");
        for (Integer ndx = 0; ndx < certCount; ndx++) {
            final byte[] decoded = certBundle.getByteArray(ndx.toString());
            if (decoded != null) {
                final X509Certificate cert = (X509Certificate) certFactory
                        .generateCertificate(new ByteArrayInputStream(decoded));
                results.add(cert);
            }
        }
        return results;
    }
}
