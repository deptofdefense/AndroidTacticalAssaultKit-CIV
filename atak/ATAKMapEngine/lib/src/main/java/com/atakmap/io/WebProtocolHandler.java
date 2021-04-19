package com.atakmap.io;

import android.util.Base64;
import com.atakmap.util.zip.IoUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Web protocol handler for UriFactory
 */
public class WebProtocolHandler implements ProtocolHandler {

    /**
     * Basic Auth Credentials
     */
    public static class AuthCreds {
        public String username;
        public String password;
    }

    /**
     * Callbacks for resolving application specific information
     */
    public interface Callbacks {

        /**
         * Get the user User-Agent for a URI. Return null to use
         * default "TAK"
         *
         * @param uri the provided uri
         * @return the corresponding user agent
         */
        String getUserAgent(String uri);

        /**
         * Determine if basic authentication is needed for a uri, and
         * if this is from a previous failure to authenticate (401)
         *
         * @param uri the uri
         * @param failedCount the number of previously 401 failures (starting at 0 for first attempt)
         * @return true if basic authentication is needed
         */
        boolean shouldAuthenticateURI(String uri, int failedCount);

        /**
         * Get the future for basic authentication credentials (from user prompt or cache provided
         * by application code).
         *
         * @param uri the uri used for retrieving the authentication credentials
         * @return the authentication credentials
         */
        Future<AuthCreds> getAuthenticationCreds(String uri, boolean previousFailure);

        /**
         * Called when authentication succeeds
         *
         * @param uri the uri
         * @param authCreds the authentication credentials used
         * @param previousFailure true if this is called after previous failures
         */
        void authCredsSuccess(String uri, AuthCreds authCreds, boolean previousFailure);

        X509TrustManager getTrustManager();
    }

    private static String USER_AGENT = "TAK";
    private static Callbacks callbacks;

    private static class DefaultCallbacks implements Callbacks {

        @Override
        public String getUserAgent(String uri) {
            return USER_AGENT;
        }

        @Override
        public boolean shouldAuthenticateURI(String uri, int failedCount) {
            return false;
        }

        @Override
        public Future<AuthCreds> getAuthenticationCreds(String uri, boolean previousFailure) {
            return null;
        }

        @Override
        public void authCredsSuccess(String uri, AuthCreds authCreds, boolean previousFailure) {

        }

        @Override
        public X509TrustManager getTrustManager() {
            // default
            return null;
        }
    }

    /**
     * Default handler
     */
    public WebProtocolHandler() {
        WebProtocolHandler.callbacks = new DefaultCallbacks();
    }

    public WebProtocolHandler(Callbacks callbacks) {
        WebProtocolHandler.callbacks = callbacks;
    }

    @Override
    public UriFactory.OpenResult handleURI(String uri) {

        try {
            URI uriObj = new URI(uri);
            if (uriObj.getScheme() != null &&
               (uriObj.getScheme().compareToIgnoreCase("http") == 0 ||
                uriObj.getScheme().compareToIgnoreCase("https") == 0)) {

                return doURLRequest(uri);
            }
        } catch (Exception ex) {
            // ignore
        }

        return null;
    }

    @Override
    public long getContentLength(String uri) {
        UriFactory.OpenResult result = handleURI(uri);
        if (result != null) {
            IoUtils.close(result.inputStream);
            return result.contentLength;
        }
        return 0;
    }

    @Override
    public Collection<String> getSupportedSchemes() {
        return Arrays.asList("http", "https");
    }

    private AuthCreds getBasicAuthCreds(String uri, int failedCount) throws ExecutionException, InterruptedException {
        if (callbacks.shouldAuthenticateURI(uri, failedCount)) {
            Future<AuthCreds> futureCreds = callbacks.getAuthenticationCreds(uri, failedCount > 0);
            if (futureCreds == null) {
                throw new IllegalStateException();
            }

            AuthCreds creds = futureCreds.get();
            if (creds == null || creds.username == null || creds.password == null) {
                throw new IllegalStateException();
            }

            return creds;
        }
        return null;
    }

    private UriFactory.OpenResult doURLRequest(String uri) {
        try {
            boolean connecting = true;
            AuthCreds authCreds;
            int authFailedCount = 0;
            URL url = new URL(uri);
            do {
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                if (con instanceof HttpsURLConnection) {
                    HttpsURLConnection scon = (HttpsURLConnection)con;
                    X509TrustManager trustManager = callbacks.getTrustManager();
                    if (trustManager != null) {
                        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("SSL");
                        sslContext.init(null, new TrustManager[]{trustManager}, new java.security.SecureRandom());
                        scon.setSSLSocketFactory(sslContext.getSocketFactory());
                    } else {
                        scon.setSSLSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault());
                    }
                }
                con.setInstanceFollowRedirects(false);
                con.setRequestMethod("GET");

                String userAgent = callbacks.getUserAgent(uri);
                if (userAgent == null)
                    userAgent = USER_AGENT;
                con.setRequestProperty("User-Agent", userAgent);

                authCreds = getBasicAuthCreds(uri, authFailedCount);
                if (authCreds != null) {
                    String encodedUserPass = Base64.encodeToString((authCreds.username + ":" + authCreds.password).getBytes(StandardCharsets.UTF_8),
                            Base64.DEFAULT);
                    con.setRequestProperty("Authorization", "Basic " + encodedUserPass);
                }

                int responseCode = con.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) { // success
                    callbacks.authCredsSuccess(uri, authCreds, authFailedCount > 0);
                    UriFactory.OpenResult result = new UriFactory.OpenResult();
                    result.contentLength = con.getContentLength();
                    result.inputStream = con.getInputStream();
                    return result;
                } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    ++authFailedCount;
                } else {
                    connecting = false;
                }
            } while (connecting);
        } catch (Exception ex) {
            // Because there will be many individual, frequent requests that make up large
            // data-sets, do not log in favor of client handling down-stream.
        }
        return null;
    }
}
