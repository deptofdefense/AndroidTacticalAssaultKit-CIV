
package com.atakmap.comms.http;

import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.SslNetCotPort;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.CertificateManager;

import org.apache.http.auth.AuthenticationException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ssl.SSLSocketFactory;

import java.io.IOException;

/**
 * Creates an HttpClient with proper TLS certificates
 *
 * 
 */
public class TakHttpClient {
    private static final String TAG = "TakHttpClient";

    /**
     * Encapsulated client to execute HTTP operations
     */
    private final HttpClient _client;

    /**
     * Base URL for TAK (Server) related URLs. Manages building out the full request URL from
     * a base URL
     */
    private final String _baseUrl;

    /**
     * Setup HTTP client.
     * HTTPS base URLs use TLS to include client certificates. Uses internally stored certificates
     * HTTP URLs use Basic Auth
     * @param url the base url
     */
    public TakHttpClient(String url) {
        if (url.toLowerCase(LocaleUtil.getCurrent())
                .startsWith("https")) {
            //just use last TAK Server SSL socket
            //Log.d(TAG, "Streaming port was of type SSL, attempting to reuse socket factory");
            _baseUrl = url
                    + SslNetCotPort.getServerApiPath(SslNetCotPort.Type.SECURE);
            _client = HttpUtil.GetHttpClient(
                    CertificateManager.getSockFactory(true, _baseUrl));
        } else {
            //use Basic Auth credentials
            //Log.d(TAG, "Streaming port was of type TCP, using preemptive auth");
            _baseUrl = url
                    + SslNetCotPort
                            .getServerApiPath(SslNetCotPort.Type.UNSECURE);
            _client = HttpUtil.GetHttpClient(false);
        }
    }

    /**
     * Setup HTTP client.
     * HTTPS base URLs use TLS to include client certificates. Uses internally stored certificates
     * HTTP URLs use Basic Auth
     * @param url the base url
     * @param connectString the connectString which is in turn used for the certificate lookup to
     *                      ensure that https are made with the correct client cert for the port
     */
    public TakHttpClient(String url, String connectString) {
        if (url.toLowerCase(LocaleUtil.getCurrent())
                .startsWith("https")) {
            //just use last TAK Server SSL socket
            //Log.d(TAG, "Streaming port was of type SSL, attempting to reuse socket factory");
            _baseUrl = url
                    + SslNetCotPort.getServerApiPath(SslNetCotPort.Type.SECURE);
            _client = HttpUtil.GetHttpClient(
                    CertificateManager.getSockFactory(true,
                            NetConnectString.fromString(connectString)));
        } else {
            //use Basic Auth credentials
            //Log.d(TAG, "Streaming port was of type TCP, using preemptive auth");
            _baseUrl = url
                    + SslNetCotPort
                            .getServerApiPath(SslNetCotPort.Type.UNSECURE);
            _client = HttpUtil.GetHttpClient(false);
        }
    }

    /**
     * Setup HTTP client with a given sslSocketFactory. Note it is up to the caller to provide
     * a complete baseUrl with protocol, host, and port info
     * @param baseUrl the base url
     * @param sslSocketFactory the socket factory to be used
     */
    public TakHttpClient(String baseUrl, SSLSocketFactory sslSocketFactory) {
        _baseUrl = baseUrl;
        _client = HttpUtil.GetHttpClient(sslSocketFactory);
    }

    /**
     * Setup HTTP client with a given sslSocketFactory and custom timeout values.
     * Note it is up to the caller to provide a complete baseUrl with protocol, host, and port info
     * @param baseUrl the base url
     * @param sslSocketFactory the socket factory to be used
     * @param connectTimeout the connection timeout
     * @param soTimeout the socket timeout
     */
    public TakHttpClient(String baseUrl, SSLSocketFactory sslSocketFactory,
            int connectTimeout, int soTimeout) {
        _baseUrl = baseUrl;
        _client = HttpUtil.GetHttpClient(connectTimeout, soTimeout,
                sslSocketFactory);
    }

    /**
     * Creates an HttpClient and proper TAK Server base URL
     * Uses internally stored certificates
     *
     * @param url the base url
     * @return the corresponding client
     */
    public static TakHttpClient GetHttpClient(String url) {
        return new TakHttpClient(url);
    }

    /**
     * Creates an HttpClient and proper TAK Server base URL
     * Uses internally stored certificates
     *
     * @param url the base url
     * @return the corresponding client
     * @param connectString the connectString which is in turn used for the certificate lookup to
     *                      ensure that https are made with the correct client cert for the port
     */
    public static TakHttpClient GetHttpClient(String url,
            String connectString) {
        return new TakHttpClient(url, connectString);
    }

    /**
     * Creates an HttpClient using specified socket factory
     *
     * @param factory the socket factory to use
     * @param url the url to use when creating a client
     * @return the client corresponding to the url.
     */
    public static TakHttpClient GetHttpClient(SSLSocketFactory factory,
            String url) {
        return new TakHttpClient(url, factory);
    }

    /**
     * Get the TAK (Server) base URL
     * @return the String representation of the baseURL
     */
    public String getUrl() {
        return _baseUrl;
    }

    /**
     * Get the TAK (Server) URL for specified path
     *
     * @param path the path to be used for lookup
     * @return the path appropriately added to the url
     */
    public String getUrl(String path) {
        String url = _baseUrl;
        if (FileSystemUtils.isEmpty(path))
            return url;

        if (!url.endsWith("/") && !path.startsWith("/"))
            url += "/";

        url += path;
        return url;
    }

    /**
     * False for HTTPs, true for HTTP
     *
     * @return false if the url is https
     */
    public boolean useBasicAuth() {
        return !_baseUrl.toLowerCase(LocaleUtil.getCurrent())
                .startsWith("https");
    }

    /**
     * Add Basic Auth for HTTP, not HTTPS (which typically uses client certificates)
     *
     * @param request the request to add basic auth to.
     */
    public void addBasicAuthentication(HttpRequestBase request) {
        if (request == null) {
            return;
        }

        try {
            HttpUtil.AddBasicAuthentication(request);
        } catch (AuthenticationException e) {
            Log.e(TAG, "Failed to add authentication", e);
        }
    }

    /**
     * Add Basic Auth for the request using the credentials provided
     *
     * @param request the request to add basic auth to.
     * @param credentials the credentials to use to set up the basic auth.
     */
    public void addBasicAuthentication(HttpRequestBase request,
            AtakAuthenticationCredentials credentials) {
        if (request == null) {
            return;
        }

        try {
            HttpUtil.AddBasicAuthentication(request, credentials);
        } catch (AuthenticationException e) {
            Log.e(TAG, "Failed to add authentication", e);
        }
    }

    /**
     * Perform an HTTP GET on the URL
     *
     * @param url       URL to get
     * @return  response body as String if HTTP OK 200 is returned by server
     */
    public String get(String url) throws IOException {
        return get(url, null);
    }

    /**
     * Perform an HTTP GET on the URL
     *
     * @param url       URL to get
     * @param verify    Verify response contains this string
     * @return  response body as String if HTTP OK 200 is returned by server
     */
    public String get(String url, String verify) throws IOException {
        return get(url, verify, null);
    }

    public String get(String url, String verify, String accept)
            throws IOException {
        return get(url, verify, accept, null);
    }

    /**
     * Perform an HTTP GET on the URL, support GZip
     *
     * @param url the url to perform a http get on.
     * @param verify the string that is to be contained in the response
     * @return response body as String if HTTP OK 200 is returned by serve
     * @throws IOException when there is an issue with the gzip'd data
     */
    public String getGZip(String url, String verify) throws IOException {
        return getGZip(url, verify, null);
    }

    /**
     * Perform an HTTP GET on the URL, support GZip
     *
     * @param url the url to perform a http get on.
     * @param verify the string that is to be contained in the response
     * @param accept Set "Accept" header on request
     * @return response body as String if HTTP OK 200 is returned by serve
     * @throws IOException when there is an issue with the gzip'd data
     */
    public String getGZip(String url, String verify, String accept)
            throws IOException {
        return get(url, verify, accept, HttpUtil.GZIP);
    }

    /**
     * Perform an HTTP GET on the URL
     *
     * @param url       URL to get
     * @param verify    Verify response contains this string
     * @param accept    Set "Accept" header on request
     * @return  response body as String if HTTP OK 200 is returned by server
     */
    public String get(String url, String verify, String accept,
            String acceptEncoding) throws IOException {
        // GET file
        HttpGet httpget = new HttpGet(url);
        if (!FileSystemUtils.isEmpty(accept))
            httpget.addHeader("Accept", accept);
        if (!FileSystemUtils.isEmpty(acceptEncoding))
            httpget.addHeader("Accept-Encoding", acceptEncoding);

        TakHttpResponse response = execute(httpget);
        response.verifyOk();
        return response.getStringEntity(verify);
    }

    /**
     * Perform HTTP HEAD on the URL
     *
     * @param url url to perform a http head on.
     * @return true if content is not on server (does not return HTTP OK 200)
     */
    public boolean head(final String url) throws IOException {
        HttpHead httpHead = new HttpHead(url);
        Log.d(TAG,
                "Checking for content on server " + httpHead.getRequestLine());
        TakHttpResponse response = execute(httpHead);

        if (response.isOk()) {
            Log.d(TAG, "Content exists on server: " + url);
            return true;
        } else {
            Log.d(TAG, "Content not on server: " + url);
            return false;
        }
    }

    public TakHttpResponse execute(HttpRequestBase request) throws IOException {
        return execute(request, useBasicAuth());
    }

    public TakHttpResponse execute(HttpRequestBase request, boolean bAddAuth)
            throws IOException {
        if (bAddAuth) {
            addBasicAuthentication(request);
        }

        Log.d(TAG, "executing request " + request.getRequestLine());
        return new TakHttpResponse(request, _client.execute(request));
    }

    public TakHttpResponse execute(HttpRequestBase request,
            AtakAuthenticationCredentials credentials)
            throws IOException {
        addBasicAuthentication(request, credentials);

        Log.d(TAG, "executing request " + request.getRequestLine());
        return new TakHttpResponse(request, _client.execute(request));
    }

    public void shutdown() {
        if (_client != null)
            _client.getConnectionManager().shutdown();
    }
}
