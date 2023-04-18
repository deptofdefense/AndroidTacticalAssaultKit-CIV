
package com.atakmap.net;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.comms.NetConnectString;
import com.atakmap.coremap.log.Log;

import java.io.ByteArrayInputStream;
import java.net.Socket;
import java.io.IOException;
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
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.TrustManager;

import gov.tak.platform.commons.resources.AndroidAssetManager;
import org.apache.http.conn.ssl.SSLSocketFactory;

public class CertificateManager {

    public static final String TAG = "CertificateManager";

    private static CertificateManager _this;

    public static final CentralTrustManager SelfSignedAcceptingTrustManager = new CentralTrustManager();
    private static KeyManagerFactoryIFace keyManagerFactory = null;
    private static final Map<String, ExtendedSSLSocketFactory> socketFactories = new HashMap<>();

    private gov.tak.platform.engine.net.CertificateManager _impl;

    /**
     * Obtain the instance of the CertificateManager class.
     * @return the certificate manager
     */
    public synchronized static CertificateManager getInstance() {
        if (_this == null)
            _this = new CertificateManager();

        return _this;
    }

    private CertificateManager() {
        _impl = null;
    }

    /**
     * Initialize the current Certificate Manager with the context for the ATAK
     * application.   This will install 2 DoD root certificates.
     * @param ctx a valid application context
     */
    public void initialize(final Context ctx) {
        _impl = new gov.tak.platform.engine.net.CertificateManager(
                AtakCertificateDatabaseBase.getStore(),
                AtakAuthenticationDatabase.getStore(),
                new AndroidAssetManager(ctx));
        SelfSignedAcceptingTrustManager.impl = new gov.tak.platform.engine.net.CentralTrustManager(_impl);

        refresh();
    }

    /**
     * Add a cert to the current local trust store.
     * @param cert the X509 certificate
     */
    public synchronized void addCertificate(X509Certificate cert) {
        if(_impl != null)
            _impl.addCertificate(cert);
        refresh();
    }

    /**
     * Add a cert to the current local trust store.
     * @param cert the X509 certificate
     */
    public synchronized void removeCertificate(X509Certificate cert) {
        if(_impl != null)
            _impl.removeCertificate(cert);
        refresh();
    }

    /**
     * Rebuild the localTrustManager based on the currently supplied certificates.
     */
    public void refresh() {
        if(_impl != null)
            _impl.refresh();

        socketFactories.clear();
    }

    /** 
     * Given a X509 Trust Manager, provide the certificates to include the ones
     * internally contained within ATAK.
     */
    public X509Certificate[] getCertificates(final X509TrustManager x509Tm) {
        return _impl != null ?
                _impl.getCertificates(x509Tm) : new java.security.cert.X509Certificate[0];
    }

    /**
     * Returns a local trust manager.
     * @return the trust manager controlled by ATAK and populated with known trusted sources.
     */
    public X509TrustManager getLocalTrustManager() {
        return (_impl != null) ? _impl.getLocalTrustManager() : null;
    }

    /**
     * Returns a system trust manager.   This is is an unverified trust manager that is supplied
     * with the Android OS and could have not very trustworthy sources in it.  This method should
     * only be called if the client then validates things.
     * @return the system trust manager which is not validated.
     */
    public X509TrustManager getSystemTrustManager() {
        return (_impl != null) ? _impl.getSystemTrustManager() : null;
    }

    /**
     * Implementation of a self signed trust manager that will allow for either certificates signed
     * with a certificate installed on the device, one of the pre-supplied DoD certificates or will
     * just validate a self signed certificate.   Self signed certificates are still in use on private
     * tactical networks.
     */
    public final static class CentralTrustManager implements X509TrustManager {
        private gov.tak.platform.engine.net.CentralTrustManager impl;
        private final ExtendedSSLSocketFactory extendedSSLSocketFactory;

        public interface CertificatePrompt extends gov.tak.platform.engine.net.CentralTrustManager.CertificatePrompt {}

        /**
         * Whether to use the system TrustManager when validating a certificate that
         * may be self signed.
         * @param iss true to use the system TrustManager, false to only use the 
         * local TrustManager.
         * 
         */
        public void setIgnoreSystemCerts(final boolean iss) {
            this.impl.setIgnoreSystemCerts(iss);
        }

        private CentralTrustManager() {
            this(CertificateManager.getInstance(), null);
        }

        private CentralTrustManager(
                CertificateManager mgr,
                ExtendedSSLSocketFactory extendedSSLSocketFactory) {

            this.impl = new gov.tak.platform.engine.net.CentralTrustManager(mgr._impl);
            this.extendedSSLSocketFactory = extendedSSLSocketFactory;
        }

        /**
         * Install a prompt implementation if the System Trust Store Manager needs to 
         * be accessed.   This is a global change across the application and only one 
         * system prompt may be registered.
         */
        public void setSystemCertificatePrompt(final CertificatePrompt cp) {
            this.impl.setSystemCertificatePrompt(cp);
        }

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return this.impl.getAcceptedIssuers();
        }

        @Override
        public void checkClientTrusted(
                java.security.cert.X509Certificate[] certs,
                String authType) throws CertificateException {

            impl.checkClientTrusted(certs, authType);
        }

        @Override
        public void checkServerTrusted(
                java.security.cert.X509Certificate[] certs,
                String authType) throws CertificateException {

            if (certs != null && extendedSSLSocketFactory != null) {
                extendedSSLSocketFactory.setServerCerts(certs);
            }

            impl.checkServerTrusted(certs, authType);
        }

    }

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
                    CertificateManager.getInstance(), this);
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

        public ExtendedSSLSocketFactory(NetConnectString netConnectString,
                                        boolean allowAllHostnames)
                throws NoSuchAlgorithmException, KeyManagementException,
                KeyStoreException, UnrecoverableKeyException {
            super((KeyStore) null);

            CentralTrustManager centralTrustManager = new CentralTrustManager(
                    CertificateManager.getInstance(), this);
            centralTrustManager.setIgnoreSystemCerts(true);

            this.allowAllHostnames = allowAllHostnames;
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(
                    keyManagerFactory.getKeyManagers(netConnectString),
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

        ExtendedSSLSocketFactory socketFactory;
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

    public static ExtendedSSLSocketFactory getSockFactory(
            boolean useCache, NetConnectString netConnectString, boolean allowAllHostnames) {

        ExtendedSSLSocketFactory socketFactory = null;
        if (useCache) {
            socketFactory = socketFactories.get(netConnectString.toString());
            if (socketFactory != null) {
                return socketFactory;
            }
        }

        try {

            Log.i(TAG, "creating socketFactory for : " + netConnectString.toString());
            socketFactory = new ExtendedSSLSocketFactory(netConnectString,
                    allowAllHostnames);

            if (useCache) {
                socketFactories.put(netConnectString.toString(), socketFactory);
            }

            return socketFactory;

        } catch (Exception e) {
            Log.e(TAG, "Exception in getSocketFactory! " + e.getMessage());
        }

        return null;
    }

    public static ExtendedSSLSocketFactory getSockFactory(boolean useCache,
                                                          NetConnectString netConnectString) {
        return getSockFactory(useCache, netConnectString, true);
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

    @Deprecated
    @DeprecatedApi(since="4.6",forRemoval = true,removeAt = "4.9")
    public static void setCertificateDatabase(
            final AtakCertificateDatabaseIFace certdb) {
    }


    public static void setKeyManagerFactory(
            final KeyManagerFactoryIFace kmf) {
        keyManagerFactory = kmf;
    }

    public static Bundle certArrayToBundle(X509Certificate[] certs)
            throws CertificateEncodingException {
        int ndx = 0;
        Bundle certBundle = new Bundle();
        certBundle.putInt("certCount", certs.length);
        for (X509Certificate cert : certs) {
            certBundle.putByteArray(Integer.toString(ndx), cert.getEncoded());
            ndx++;
        }
        return certBundle;
    }

    public static List<X509Certificate> certBundleToArray(
            Bundle certBundle) throws CertificateException {
        final List<X509Certificate> results = new ArrayList<>();
        final int certCount = certBundle.getInt("certCount");
        final CertificateFactory certFactory = CertificateFactory
                .getInstance("X.509");
        for (int ndx = 0; ndx < certCount; ndx++) {
            final byte[] decoded = certBundle.getByteArray(Integer.toString(ndx));
            if (decoded != null) {
                final X509Certificate cert = (X509Certificate) certFactory
                        .generateCertificate(new ByteArrayInputStream(decoded));
                results.add(cert);
            }
        }
        return results;
    }

    /**
     * Creates an SSLContext.  This is similar to Java-provided 
     * SSLContext.getInstance(), but serves as a utility method
     * to consolidate such creations to ensure uniformity and easy
     * adjustment of low-level SSL parameters to meet security goals.
     * The returned SSLContext will already be initialized
     * (init() already invoked).
     * @param trusts array of TrustManagers to configure into the new context.
     *        Accepts and used as documented in SSLContext.init()
     * @throws NoSuchAlgorithmException if there is no underlying security
     *        provider registered with the JVM that can handle any of the
     *        TAK-preferred SSL protocols.
     * @throws KeyManagementException if initialization of a new context fails
     */
    public static SSLContext createSSLContext(TrustManager[] trusts) throws NoSuchAlgorithmException, KeyManagementException {
        /*
         * While we do specify TLSv1.2 here, it is worth clearly noting that
         * this does NOT restrict the returned context from supporting other
         * protocols. It particularly does not restrict the returned context
         * from using older protocols (TLS 1.x for example).
         * In the future, we may further configure/restrict the
         * accepted protocols of the returned SSLContext.
         * See comments in ATAK-15747 for more detail.
         */
        SSLContext ret = SSLContext.getInstance("TLSv1.2");
        ret.init(null, trusts, new java.security.SecureRandom());
        return ret;
    }
}
