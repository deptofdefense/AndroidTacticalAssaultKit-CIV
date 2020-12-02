
package com.atakmap.comms.http;

import android.util.Base64;

import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.http.rest.BasicUserCredentials;
import com.atakmap.comms.app.TLSUtils;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.net.AtakAuthenticationCredentials;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Provides common HTTP util methods
 *
 * 
 */
public class HttpUtil {

    private static final String TAG = "HttpUtil";

    public static final String GZIP = "gzip";
    public static final String MIME_XML = ResourceFile.MIMEType.XML.MIME;
    public static final String MIME_JSON = "application/json";
    public static final String MIME_ZIP = "application/x-zip-compressed";

    public static final int DEFAULT_SO_TIMEOUT_MS = 15000;
    public static final int DEFAULT_CONN_TIMEOUT_MS = 10000;

    /** HTTP Time per RFC 1123 */
    public final static CoordinatedTime.SimpleDateFormatThread HTTP_TIME_FORMAT = new CoordinatedTime.SimpleDateFormatThread(
            "EEE, dd MMM yyyyy HH:mm:ss z",
            Locale.US,
            TimeZone.getTimeZone("GMT"));

    public static HttpClient GetHttpClient(SSLSocketFactory socketFactory) {
        return GetHttpClient(DEFAULT_CONN_TIMEOUT_MS, DEFAULT_SO_TIMEOUT_MS,
                socketFactory);
    }

    public static HttpClient GetHttpClient(boolean bPreemptiveBasicAuth) {
        return GetHttpClient(bPreemptiveBasicAuth,
                DEFAULT_CONN_TIMEOUT_MS, DEFAULT_SO_TIMEOUT_MS);
    }

    public static HttpClient GetHttpClient(long connTimeout,
            long soTimeout, SSLSocketFactory socketFactory) {
        HttpParams params = initParams(connTimeout, soTimeout);
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme("https", socketFactory, 443));
        SingleClientConnManager mgr = new SingleClientConnManager(params,
                registry);
        return new DefaultHttpClient(mgr, params);
    }

    public static HttpClient GetHttpClient(boolean bPreemptiveBasicAuth,
            long connTimeout,
            long soTimeout) {
        DefaultHttpClient httpclient = new DefaultHttpClient(initParams(
                connTimeout, soTimeout));

        if (bPreemptiveBasicAuth) {
            Log.d(TAG, "Setting Preemptive Auth");
            HttpRequestInterceptor preemptiveAuth = new HttpRequestInterceptor() {
                @Override
                public void process(final HttpRequest request,
                        final HttpContext context)
                        throws HttpException, IOException {
                    AuthState authState = (AuthState) context
                            .getAttribute(ClientContext.TARGET_AUTH_STATE);
                    CredentialsProvider credsProvider = (CredentialsProvider) context
                            .getAttribute(
                                    ClientContext.CREDS_PROVIDER);
                    HttpHost targetHost = (HttpHost) context
                            .getAttribute(ExecutionContext.HTTP_TARGET_HOST);

                    if (authState == null || credsProvider == null
                            || targetHost == null) {
                        if (authState == null)
                            Log.e(TAG, "Failed to get attribute "
                                    + ClientContext.TARGET_AUTH_STATE);
                        if (credsProvider == null)
                            Log.e(TAG, "Failed to get attribute "
                                    + ClientContext.CREDS_PROVIDER);
                        if (targetHost == null)
                            Log.e(TAG, "Failed to get attribute "
                                    + ExecutionContext.HTTP_TARGET_HOST);
                        return;
                    }

                    if (authState.getAuthScheme() == null) {
                        AuthScope authScope = new AuthScope(
                                targetHost.getHostName(),
                                targetHost.getPort());
                        Credentials creds = credsProvider
                                .getCredentials(authScope);
                        if (creds != null) {
                            authState.setAuthScheme(new BasicScheme());
                            authState.setCredentials(creds);
                        }
                    }
                }
            };
            httpclient.addRequestInterceptor(preemptiveAuth, 0);
        }
        return httpclient;
    }

    public static HttpParams initParams(long connTimeout, long soTimeout) {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, (int) connTimeout);
        HttpConnectionParams.setSoTimeout(params, (int) soTimeout);
        params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION,
                HttpVersion.HTTP_1_1);

        return params;
    }

    public static void AddBasicAuthentication(HttpRequestBase request)
            throws AuthenticationException {
        if (request == null)
            throw new AuthenticationException("Request invalid");

        AddBasicAuthentication(request, TLSUtils.getCredentials(
                request.getURI().getHost(), true));
    }

    public static void AddBasicAuthentication(HttpRequestBase request,
            AtakAuthenticationCredentials creds)
            throws AuthenticationException {
        if (creds == null)
            throw new AuthenticationException("Credentials invalid");

        AddBasicAuthentication(request, creds.username, creds.password);
    }

    public static void AddBasicAuthentication(HttpRequestBase request,
            String user,
            String password)
            throws AuthenticationException {
        if (FileSystemUtils.isEmpty(user) || password == null)
            throw new AuthenticationException("Credentials invalid");

        String basic = String.format("%s:%s", user, password);
        if (FileSystemUtils.isEmpty(basic) || basic.length() < 2)
            throw new AuthenticationException("Credential length insufficient");

        // set Basic Auth header
        request.setHeader(
                "Authorization",
                "Basic "
                        + Base64.encodeToString(basic
                                .getBytes(FileSystemUtils.UTF8_CHARSET),
                                Base64.NO_WRAP));

    }

    /**
     * Set Basic Auth headers, if provided. NOTE SSL and/or VPN should be used to protect these credentials in
     * transit
     *
     * @param request the request o set the basic authentication headers on
     * @param credentials the credentials to use
     */
    public static void AddBasicAuthentication(HttpRequestBase request,
            BasicUserCredentials credentials) {
        if (credentials == null || !credentials.isValid())
            return;

        // set Basic Auth header
        Log.d(TAG, "Adding Basic Auth to HTTP request");
        request.setHeader("Authorization", "Basic " + credentials.getBase64());
    }
}
