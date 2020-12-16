
package com.atakmap.net;

import android.util.Base64;

import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import com.atakmap.coremap.log.Log;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.TrustManager;

public class AtakAuthenticationHandlerHTTP {
    public static interface OnAuthenticateCallback {

        /**
         * Obtain the basic authentication information.   Also include the status code from the 
         * previous attempt.
         * @param url the url that is requiring basic authentication.
         * @param previousStatus  -1 (no previous status was found), 
                    401 (username and password bad), and 403 (access forbidden)
         */
        public String[] getBasicAuth(URL url, int previousStatus);
    }

    private static String TAG = "AtakAuthenticationHandlerHTTP";
    private static OnAuthenticateCallback authCallback = null;
    private static final ReadWriteLock lock = new ReentrantReadWriteLock();
    private static final Map<String, Domain> domains = new HashMap<String, Domain>();


    public static int[] UNAUTHORIZED_AND_FORBIDDEN = new int[] {HttpsURLConnection.HTTP_UNAUTHORIZED, HttpsURLConnection.HTTP_FORBIDDEN};
    public static int[] UNAUTHORIZED_ONLY = new int[] { HttpsURLConnection.HTTP_UNAUTHORIZED };
    public static int[] FORBIDDEN_ONLY =  new int[] { HttpsURLConnection.HTTP_FORBIDDEN };


    public static void setCallback(OnAuthenticateCallback callback) {
        Lock wlock = lock.writeLock();
        wlock.lock();
        try {
            authCallback = callback;
        } finally {
            wlock.unlock();
        }
    }

    private static String encodeCredentials(String username, String password) {
        String uidpwd;
        String s = username + ":" + password;
        uidpwd = Base64.encodeToString(s.getBytes(FileSystemUtils.UTF8_CHARSET), Base64.NO_WRAP);
        return uidpwd;
    }

    public static Connection makeAuthenticatedConnection(HttpURLConnection conn,
            int loginAttempts) throws IOException {

        return makeAuthenticatedConnection(conn, loginAttempts, true, UNAUTHORIZED_AND_FORBIDDEN);
    }

    public static Connection makeAuthenticatedConnection(HttpURLConnection conn,
            int loginAttempts, boolean ignorePreviousFail) throws IOException {

        return makeAuthenticatedConnection(conn, loginAttempts, ignorePreviousFail, UNAUTHORIZED_AND_FORBIDDEN);
        
    }

    /**
     * Allows for a authenticated connection to be made if the access code returned matches one of
     * the provided access codes in the list.
     * @param conn the connection to use to try to make an authenticated connection with.
     * @param loginAttempts number of times before giving up.
     * @param ignorePreviousFail ignore any previous failure attempts
     * @param badAccessCodes the list of codes that is considered bad access (usually 401 and 403)
     *                       but can be client supplied
     * @return the working connection
     * @throws IOException if the connection failed to authenticate
     */
    public static Connection makeAuthenticatedConnection(HttpURLConnection conn,
            int loginAttempts, boolean ignorePreviousFail, int[] badAccessCodes) throws IOException {
        
        final String site = conn.getURL().getHost();

        Domain domain;
        synchronized(domains) {
            // NOTE: 'authenticatedDomains' isn't strictly protected as
            //       concurrent requests could do multiple connection attempts
            //       prior to the host being added. the desired effect is to
            //       avoid having to attempt unauthenticated connection every
            //       time for a domain that requires authentication, and this
            //       will be achieved following the possible concurrency during
            //       initial connection attempts.
            domain = domains.get(site);
            if(domain == null)
                domains.put(site, domain=new Domain(site));
        }

        Connection retval = new Connection();
        retval.conn = conn;
        if (retval.conn instanceof javax.net.ssl.HttpsURLConnection) {
            // XXX - should not accept untrusted certificates without prompt!
            try {
                setAcceptAllVerifier((javax.net.ssl.HttpsURLConnection) retval.conn);
            } catch (Exception ignored) {
            }
        }

        IOException raised = null;
        if (!domain.requiresAuthorization) {
            // try to create connection
            try {
                retval.conn.connect();
                retval.stream = retval.conn.getInputStream();
                return retval;
            } catch (IOException e) {
                retval.conn.disconnect();
                final int status = getResponseCode(retval.conn);
                // mark the domain as requiring authentication
                domain.requiresAuthorization = isBadAccess(status, badAccessCodes);
                raised = e;
                if(!isBadAccess(status, badAccessCodes))
                    throw e;
                retval.conn = duplicate(conn);
            }
        }

        if (domain.requiresAuthorization) {
            Lock rlock = lock.readLock();
            rlock.lock();
            try {
                if (authCallback != null) {
                    // all attempts to authenticate against a domain are
                    // synchronized on that domain
                    synchronized(domain) {
                        // if we are ignoring previous fails or if authentication
                        // against the domain has not yet failed, try to
                        // authenticate
                        if (ignorePreviousFail || !domain.authorizationFailed) {
                            final AtakAuthenticationCredentials credentials =
                                    AtakAuthenticationDatabase.getCredentials(
                                            AtakAuthenticationCredentials.TYPE_HTTP_BASIC_AUTH, site);

                            String cached = null;
                            if (credentials != null &&
                                    credentials.password != null &&
                                    credentials.password.length() != 0) {
                                cached = encodeCredentials(credentials.username, credentials.password);
                            }

                            int status = -1;
                            // try to open with the cached credentials
                            if(cached != null) {
                                try {
                                    retval.stream = tryConnect(retval.conn, cached);
                                    return retval;
                                } catch (IOException e) {
                                    retval.conn.disconnect();
                                    status = getResponseCode(retval.conn);
                                    if (isBadAccess(status, badAccessCodes)) {
                                        raised = e;
                                        retval.conn = duplicate(conn);
                                    } else {
                                        // if connection failed for a reason
                                        // other than status UNAUTHORIZED,
                                        // clear the authorization requirement
                                        // on the domain
                                        domain.requiresAuthorization &= isBadAccess(status, badAccessCodes);
                                        throw e;
                                    }
                                }
                            }

                            // do the login attempts
                            String[] uidpwd;
                            String uidpwdBase64 = null;
                            for (int i = 0; i < loginAttempts; i++) {
                                uidpwd = authCallback.getBasicAuth(conn.getURL(), status);
                                if(uidpwd != null) {
                                    uidpwdBase64 = encodeCredentials(uidpwd[0], uidpwd[1]);
                                    AtakAuthenticationDatabase.saveCredentials(
                                            AtakAuthenticationCredentials.TYPE_HTTP_BASIC_AUTH,
                                            site, uidpwd[0], uidpwd[1], false);
                                }
                                try {
                                    retval.stream = tryConnect(retval.conn, uidpwdBase64);
                                    return retval;
                                } catch (IOException e) {
                                    retval.conn.disconnect();
                                    status = getResponseCode(retval.conn);
                                    if (uidpwdBase64 != null 
                                            && isBadAccess(status, badAccessCodes)
                                            && i < (loginAttempts - 1)) {

                                        retval.conn = duplicate(conn);
                                        continue;
                                    }
                                    
                                    // authentication attempts failed
                                    domain.authorizationFailed = true;

                                    // if connection failed for a reason other
                                    // than status UNAUTHORIZED, clear the
                                    // authorization requirement on the domain
                                    domain.requiresAuthorization &= isBadAccess(status, badAccessCodes);
                                    throw e;
                                }
                            }
                        } else {
                            domain.authorizationFailed = true;
                        }
                    }
                }
            } finally {
                rlock.unlock();
            }
        }

        if (raised != null)
            throw raised;

        retval.conn = duplicate(conn);

        throw new IOException("Authorization failed");
    }


    private static boolean isBadAccess(int status, int[] badAccessCodes) {
        for (int accessCode : badAccessCodes)
            if (status == accessCode)
                return true;
        return false;
    }

    private static int getResponseCode(HttpURLConnection conn) {
        try {
            return conn.getResponseCode();
        } catch(IOException e) {
            return -1;
        }
    }

    private static HttpURLConnection duplicate(HttpURLConnection conn) throws IOException {
        HttpURLConnection retval = (HttpURLConnection) conn.getURL().openConnection();
        retval.setRequestProperty("User-Agent", "TAK");
        retval.setUseCaches(conn.getUseCaches());
        retval.setConnectTimeout(conn.getConnectTimeout());

        // XXX - other stuff

        if (retval instanceof javax.net.ssl.HttpsURLConnection)
            // XXX - should not accept untrusted certificate without prompt!!!
            try {
                setAcceptAllVerifier((javax.net.ssl.HttpsURLConnection) retval);
            } catch (Exception ignored) {
            }

        return retval;
    }

    private static InputStream tryConnect(HttpURLConnection conn, String uidpwdBase64)
            throws IOException {
        if (uidpwdBase64 != null)
            conn.setRequestProperty("Authorization", "Basic " + uidpwdBase64);

        conn.connect();
        final int status = conn.getResponseCode();
        try { 
            return conn.getInputStream();
        } catch (IOException ioe) { 
            Log.e(TAG, "error occured during connection: " + status);
            throw ioe;
        }
    }

    public static class Connection {
        public HttpURLConnection conn;
        public InputStream stream;

        private Connection() {
        }
    }

    private static class Domain {
        public final String host;
        public boolean authorizationFailed;
        public boolean requiresAuthorization;
        
        public Domain(String host) {
            this.host = host;
            this.authorizationFailed = false;
            this.requiresAuthorization = false;
        }
    }
    static javax.net.ssl.SSLSocketFactory sslSocketFactory;

    /**
     * Overrides the SSL TrustManager and HostnameVerifier to allow all certs and hostnames.
     * WARNING: This should only be used for testing, or in a "safe" (i.e. firewalled) environment.
     * 
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     */
    protected static void setAcceptAllVerifier(javax.net.ssl.HttpsURLConnection connection)
            throws java.security.NoSuchAlgorithmException, java.security.KeyManagementException {

        // Create the socket factory.
        // Reusing the same socket factory allows sockets to be
        // reused, supporting persistent connections.
        if (null == sslSocketFactory) {
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
            sc.init(null, new TrustManager[] { CertificateManager.SelfSignedAcceptingTrustManager}, new java.security.SecureRandom());
            sslSocketFactory = sc.getSocketFactory();
        }

        connection.setSSLSocketFactory(sslSocketFactory);

        // Since we may be using a cert with a different name, we need to ignore
        // the hostname as well.
        connection.setHostnameVerifier(CertificateManager.ALL_TRUSTING_HOSTNAME_VERIFIER);
    }

   
}
