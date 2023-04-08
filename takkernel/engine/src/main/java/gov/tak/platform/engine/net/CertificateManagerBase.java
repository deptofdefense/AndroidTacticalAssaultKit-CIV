
package gov.tak.platform.engine.net;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.net.AtakAuthenticationCredentials;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.Provider;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.X509TrustManager;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import gov.tak.api.commons.resources.IAssetManager;
import gov.tak.api.engine.net.ICertificateStore;
import gov.tak.api.engine.net.ICredentialsStore;

abstract class CertificateManagerBase {

    public static final String TAG = "CertificateManagerBase";
    private final List<X509Certificate> certificates = new ArrayList<>();
    private X509TrustManager localTrustManager = null;
    private X509TrustManager systemTrustManager = null;
    private ICertificateStore certificateStore = null;
    private ICredentialsStore credentialsStore = null;

    CertificateManagerBase(ICertificateStore certificateStore, ICredentialsStore credentialsStore, IAssetManager ctx) {
        try {
            this.certificateStore = certificateStore;
            this.credentialsStore = credentialsStore;

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
        addCertNoRefresh(ctx, "certs/isrgrootx1.crt");

        refresh();
    }

    private void addCertNoRefresh(final IAssetManager ctx, final String name) {

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

    /**
     * Gets a CA Certificate.
     *
     * @return a list of {@link X509Certificate}
     */
    public static List<X509Certificate> getCACerts(ICertificateStore certdb, ICredentialsStore authdb) {
        List<X509Certificate> retval = new ArrayList<X509Certificate>();
        try {

            // get the default ca cert

            byte[] caCertP12 = null;
            if(ICertificateStore.validateCertificate(certdb, ICertificateStore.TYPE_TRUST_STORE_CA, null, -1))
                caCertP12 = certdb.getCertificate(ICertificateStore.TYPE_TRUST_STORE_CA, null, -1);
            ICredentialsStore.Credentials caCertCredentials =
                    authdb.getCredentials(
                            AtakAuthenticationCredentials.TYPE_caPassword);
            if (caCertP12 != null && caCertCredentials != null
                    && !FileSystemUtils.isEmpty(caCertCredentials.password)) {
                List<X509Certificate> caCerts = CertificateManager.loadCertificate(caCertP12, caCertCredentials.password);
                if (caCerts != null) {
                    retval.addAll(caCerts);
                }
            }

            // see if we have an update server ca cert
            caCertP12 = null;
            if(ICertificateStore.validateCertificate(certdb, ICertificateStore.TYPE_UPDATE_SERVER_TRUST_STORE_CA, null, -1))
                caCertP12 = certdb.getCertificate(ICertificateStore.TYPE_UPDATE_SERVER_TRUST_STORE_CA, null, -1);
            caCertCredentials =
                    authdb.getCredentials(
                            AtakAuthenticationCredentials.TYPE_updateServerCaPassword);
            if (caCertP12 != null && caCertCredentials != null
                    && !FileSystemUtils.isEmpty(caCertCredentials.password)) {
                List<X509Certificate> caCerts = CertificateManager.loadCertificate(caCertP12, caCertCredentials.password);
                if (caCerts != null) {
                    retval.addAll(caCerts);
                }
            }

            // get legacy certs
            String[] servers = certdb.getServers(
                    ICertificateStore.TYPE_TRUST_STORE_CA);
            if (servers != null) {
                for (String server : servers) {
                    caCertP12 = null;
                    if(ICertificateStore.validateCertificate(certdb, ICertificateStore.TYPE_TRUST_STORE_CA, server, -1))
                        caCertP12 = certdb.getCertificate(ICertificateStore.TYPE_TRUST_STORE_CA, server, -1);
                    caCertCredentials = authdb.getCredentials(
                            AtakAuthenticationCredentials.TYPE_caPassword, server);
                    if (caCertP12 != null && caCertCredentials != null &&
                            !FileSystemUtils.isEmpty(caCertCredentials.password)) {
                        List<X509Certificate> caCerts = CertificateManager.loadCertificate(
                                caCertP12, caCertCredentials.password);
                        if (caCerts != null) {
                            retval.addAll(caCerts);
                        }
                    }
                }
            }

            URI[] netConnectStrings = certdb.getServerURIs(
                    ICertificateStore.TYPE_TRUST_STORE_CA);
            if (netConnectStrings != null) {
                for (URI netConnectString : netConnectStrings) {
                    caCertP12 = null;
                    if(ICertificateStore.validateCertificate(certdb,
                            ICertificateStore.TYPE_TRUST_STORE_CA,
                            netConnectString.getHost(), netConnectString.getPort()))
                        caCertP12 = certdb.getCertificate(ICertificateStore.TYPE_TRUST_STORE_CA,
                                netConnectString.getHost(), netConnectString.getPort());

                    caCertCredentials = authdb.getCredentials(
                            AtakAuthenticationCredentials.TYPE_caPassword, netConnectString.getHost());
                    if (caCertP12 != null && caCertCredentials != null &&
                            !FileSystemUtils.isEmpty(caCertCredentials.password)) {
                        List<X509Certificate> caCerts = CertificateManager.loadCertificate(
                                caCertP12, caCertCredentials.password);
                        if (caCerts != null) {
                            retval.addAll(caCerts);
                        }
                    }
                }
            }

            Log.d(TAG, "getCACerts found " + retval.size() + " certs");

            return retval;
        } catch (Exception e) {
            Log.e(TAG, "exception in getCACerts!", e);
            return null;
        }
    }

    private List<X509Certificate> getAcceptedIssuers() {
        List<X509Certificate> trustedIssuers = new LinkedList<>(certificates);
        List<X509Certificate> certificateDatabaseCerts = getCACerts(certificateStore, credentialsStore);
        if (certificateDatabaseCerts != null) {
            trustedIssuers.addAll(certificateDatabaseCerts);
        }
        return trustedIssuers;
    }

    public ICertificateStore getCertificateStore() {
        return certificateStore;
    }

    public ICredentialsStore getCredentialsStore() {
        return credentialsStore;
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
    private static X509Certificate getCertFromFile(IAssetManager assetManager, String path)
            throws Exception {

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
     * Helper method that extracts a list of x509 certificates from the encrypted container
     *
     * @param p12 encrypted certificate container
     * @param password certificate container password
     * @param provider  The provider implementation for the keystore, or <code>null</code> to use default
     * @param error If non-<code>null</code> captures any <code>Throwable</code> raised that prevented loading
     */
    public static List<X509Certificate> loadCertificate(byte[] p12, String password, Provider provider, Throwable[] error) {
        try {
            List<X509Certificate> results = new LinkedList<X509Certificate>();

            ByteArrayInputStream caCertStream = new ByteArrayInputStream(p12);
            KeyStore ks = (provider != null) ?
                    KeyStore.getInstance("PKCS12", provider) : KeyStore.getInstance("PKCS12");
            ks.load(caCertStream, password.toCharArray());
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                X509Certificate cert = (X509Certificate) ks.getCertificate(aliases.nextElement());
                results.add(cert);
            }

            Log.d(TAG, "loadCertificate found " + results.size() + " certs");
            return results;
        }
        catch (Exception e) {
            if(error != null)
                error[0] = e;
        }
        return null;
    }
}
