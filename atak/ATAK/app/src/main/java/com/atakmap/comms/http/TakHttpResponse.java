
package com.atakmap.comms.http;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;

/**
 * Encapsulates an HTTP response
 *
 * 
 */
public class TakHttpResponse {
    private static final String TAG = "TakHttpResponse";

    private final HttpRequestBase _request;
    private final HttpResponse _response;
    private final StatusLine _statusLine;
    private final int _statusCode;

    public TakHttpResponse(HttpRequestBase request, HttpResponse response) {
        this._request = request;
        this._response = response;

        _statusLine = _response.getStatusLine();
        _statusCode = _statusLine.getStatusCode();

        if (isOk() || isCreated()) {
            Log.d(TAG, "Op: " + _request.getRequestLine() + ", response: "
                    + _statusCode);
        } else {
            Log.w(TAG, "Op: " + _request.getRequestLine() + ", response: "
                    + _statusCode
                    + ", " + _statusLine.getReasonPhrase());
        }
    }

    //    public HttpResponse getResponse() {
    //        return _response;
    //    }

    public String getRequestUrl() {
        URI uri = _request.getURI();
        if (uri != null)
            return uri.toString();

        return null;
    }

    public StatusLine getStatusLine() {
        return _statusLine;
    }

    public int getStatusCode() {
        return _statusCode;
    }

    public void verifyOk() throws TakHttpException {
        verify(HttpStatus.SC_OK);
    }

    public boolean isOk() {
        return _statusCode == HttpStatus.SC_OK;
    }

    public boolean isCreated() {
        return _statusCode == HttpStatus.SC_CREATED;
    }

    public void verify(int status) throws TakHttpException {
        if (!isStatus(status)) {
            Log.w(TAG, "HTTP operation: " + _request.getRequestLine()
                    + " expected: " + status + ", " + _response.toString());
            String errorMessage = getReasonPhrase();
            if (FileSystemUtils.isEmpty(errorMessage))
                errorMessage = "HTTP operation failed: " + _statusCode;
            throw new TakHttpException(errorMessage, _statusCode);
        }
    }

    public boolean isStatus(int status) {
        return _statusCode == status;
    }

    public String getStringEntity() throws IOException {
        return getStringEntity(null);
    }

    public String getStringEntity(String verify) throws IOException {
        checkGZip();

        String ret = EntityUtils.toString(_response.getEntity(),
                FileSystemUtils.UTF8_CHARSET.name());
        if (!FileSystemUtils.isEmpty(verify)) {
            if (FileSystemUtils.isEmpty(ret)) {
                Log.w(TAG, "Failed to parse response body");
                throw new TakHttpException("Failed to parse response body",
                        _statusCode);
            }

            //validate expected contents
            if (!ret.contains(verify)) {
                Log.w(TAG, "Failed to parse expected response body: " + verify);
                throw new TakHttpException(
                        "Failed to parse expected response body: " + ret,
                        _statusCode);
            }
        }

        return ret;
    }

    public HttpEntity getEntity() {
        checkGZip();
        return _response.getEntity();
    }

    private void checkGZip() {
        //TODO refactor to use addRequestInterceptor & addResponseInterceptor?
        Header contentEncoding = getHeader("Content-Encoding");
        if (contentEncoding != null
                && HttpUtil.GZIP.equalsIgnoreCase(contentEncoding.getValue())) {
            Log.d(TAG, "Response deflating Gzip");
            _response.setEntity(new GzipInflatingEntity(_response.getEntity()));
        } else {
            //Log.d(TAG, "Response not compressed");
        }
    }

    public String getReasonPhrase() {
        return _statusLine.getReasonPhrase();
    }

    public Header getHeader(String name) {
        return _response.getFirstHeader(name);
    }

    public long getHeaderLong(String name) {
        long value = -1;
        Header h = getHeader(name);
        if (h == null || FileSystemUtils.isEmpty(h.getValue())) {
            //TODO throw instead?
            return value;
        }

        try {
            value = Long.parseLong(h.getValue());
        } catch (Exception e) {
            Log.w(TAG, "Unable to determine header: " + name, e);
            value = -1;
        }

        return value;
    }

    public String getHeaderString(String name) {
        Header h = getHeader(name);
        if (h == null || FileSystemUtils.isEmpty(h.getValue())) {
            //TODO throw instead?
            return null;
        }

        return h.getValue();
    }

    public long getContentLength() {
        return getHeaderLong("Content-Length");
    }

    public String getContentType() {
        return getHeaderString("Content-Type");
    }

    @Override
    public String toString() {
        return "" + _statusCode + ": " + _statusLine.getReasonPhrase();
    }

}
