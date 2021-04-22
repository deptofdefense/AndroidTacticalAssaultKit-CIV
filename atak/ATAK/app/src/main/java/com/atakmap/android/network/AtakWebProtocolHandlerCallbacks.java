
package com.atakmap.android.network;

import com.atakmap.coremap.log.Log;
import com.atakmap.io.WebProtocolHandler;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;
import com.atakmap.net.CertificateManager;

import java.io.IOException;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class AtakWebProtocolHandlerCallbacks
        implements WebProtocolHandler.Callbacks {

    private final AtakAuthenticatedConnectionCallback authCallback;
    private final static String TAG = "AtakWebProtocolHandlerCallbacks";

    public AtakWebProtocolHandlerCallbacks(
            AtakAuthenticatedConnectionCallback authCallback) {
        this.authCallback = authCallback;
    }

    @Override
    public String getUserAgent(String uri) {
        // use default
        return null;
    }

    @Override
    public boolean shouldAuthenticateURI(String uri, int failedCount) {

        if (failedCount == 0) {
            // use cached creds as a hint we should auth on first attempt
            WebProtocolHandler.AuthCreds authCreds = getCachedAuthCreds(uri);
            if (authCreds != null)
                return true;
        }

        // Should auth if we've failed before
        return failedCount > 0;
    }

    @Override
    public Future<WebProtocolHandler.AuthCreds> getAuthenticationCreds(
            final String uri, final boolean previouslyFailed) {
        try {
            FutureTask<WebProtocolHandler.AuthCreds> result = new FutureTask<>(
                    new Callable<WebProtocolHandler.AuthCreds>() {
                        @Override
                        public WebProtocolHandler.AuthCreds call()
                                throws Exception {
                            WebProtocolHandler.AuthCreds authCreds = getCachedAuthCreds(
                                    uri);
                            if (previouslyFailed || authCreds == null) {
                                String[] creds = authCallback
                                        .getBasicAuth(new URL(uri), -1);
                                authCreds = new WebProtocolHandler.AuthCreds();
                                authCreds.username = creds[0];
                                authCreds.password = creds[1];
                                return authCreds;
                            }
                            return authCreds;
                        }
                    });
            result.run();
            return result;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void authCredsSuccess(String uri,
            WebProtocolHandler.AuthCreds authCreds, boolean previousFailure) {
        // previous failure means our cached creds were wrong or non-existent
        if (previousFailure) {
            try {
                URL url = new URL(uri);
                AtakAuthenticationDatabase.saveCredentials(
                        AtakAuthenticationCredentials.TYPE_HTTP_BASIC_AUTH,
                        url.getHost(),
                        authCreds.username,
                        authCreds.password,
                        false);
            } catch (Throwable t) {
                Log.d(TAG, "Failed login attempt for host " + uri);
            }
        }
    }

    @Override
    public X509TrustManager getTrustManager() {
        ArrayList<X509TrustManager> x509TrustManagers = new ArrayList<>();

        try {
            // The default TrustManager with default keystore
            String defaultAlg = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory factory = TrustManagerFactory
                    .getInstance(defaultAlg);
            factory.init((KeyStore) null);
            for (TrustManager tm : factory.getTrustManagers()) {
                if (tm instanceof X509TrustManager)
                    x509TrustManagers.add((X509TrustManager) tm);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get default trust manager " + e);
        }

        try {
            // Android System and User CAs
            List<X509TrustManager> androidManagers = getFullAndroidTrustManagers();
            if (androidManagers != null)
                x509TrustManagers.addAll(androidManagers);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get Android CA trust manager " + e);
        }

        // ATAK's prefs managed trust store
        X509TrustManager atakLocal = CertificateManager.getInstance()
                .getLocalTrustManager();
        if (atakLocal != null)
            x509TrustManagers.add(atakLocal);

        // Combine all together
        return new AggregateTrustManagerImpl(x509TrustManagers);
    }

    private static WebProtocolHandler.AuthCreds getCachedAuthCreds(String uri) {
        try {
            final String site = new URL(uri).getHost();
            final AtakAuthenticationCredentials credentials = AtakAuthenticationDatabase
                    .getCredentials(
                            AtakAuthenticationCredentials.TYPE_HTTP_BASIC_AUTH,
                            site);
            if (credentials == null || !credentials.isValid())
                return null;
            WebProtocolHandler.AuthCreds result = new WebProtocolHandler.AuthCreds();
            result.username = credentials.username;
            result.password = credentials.password;
            return result;
        } catch (Throwable t) {
            return null;
        }
    }

    //
    //
    //

    private static final Object androidTrustManagersLock = new Object();
    private static List<X509TrustManager> androidTrustManagers;

    private static List<X509TrustManager> getFullAndroidTrustManagers()
            throws KeyStoreException, CertificateException,
            NoSuchAlgorithmException, IOException {
        synchronized (androidTrustManagersLock) {
            if (androidTrustManagers == null) {
                // "system" AND "user" anchors included
                KeyStore keyStore = KeyStore.getInstance("AndroidCAStore");
                keyStore.load(null, null);

                final TrustManagerFactory factory = TrustManagerFactory
                        .getInstance(TrustManagerFactory.getDefaultAlgorithm());
                factory.init(keyStore);

                ArrayList<X509TrustManager> x509TrustManagers = new ArrayList<>();
                for (TrustManager tm : factory.getTrustManagers()) {
                    if (tm instanceof X509TrustManager)
                        x509TrustManagers.add((X509TrustManager) tm);
                }
                androidTrustManagers = x509TrustManagers;
            }
            return androidTrustManagers;
        }
    }

    private static class AggregateTrustManagerImpl implements X509TrustManager {

        protected ArrayList<X509TrustManager> trustManagers;

        protected AggregateTrustManagerImpl(
                Collection<X509TrustManager> trustManagers) {
            this.trustManagers = new ArrayList<>(trustManagers);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates,
                String authType) throws CertificateException {
            for (X509TrustManager tm : trustManagers) {
                try {
                    tm.checkClientTrusted(x509Certificates, authType);
                    return;
                } catch (CertificateException e) {
                    // ignore and check next
                }
            }
            throw new CertificateException();
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates,
                String authType) throws CertificateException {
            for (X509TrustManager tm : trustManagers) {
                try {
                    tm.checkServerTrusted(x509Certificates, authType);
                    return;
                } catch (CertificateException e) {
                    // ignore
                }
            }
            throw new CertificateException();
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            final ArrayList<X509Certificate> list = new ArrayList<>();
            for (X509TrustManager tm : trustManagers)
                list.addAll(Arrays.asList(tm.getAcceptedIssuers()));
            return list.toArray(new X509Certificate[0]);
        }
    }
}
