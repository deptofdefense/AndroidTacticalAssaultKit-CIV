
package com.atakmap.android.http.rest.operation;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.http.rest.request.GetFileRequest;
import com.atakmap.comms.app.TLSUtils;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpException;
import com.atakmap.comms.http.TakHttpResponse;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.filesystem.HashingUtils;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLSocketFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * REST Operation to GET a file
 * Full URL from the GetFileRequest is used, rather than building out based on
 * TAK Server REST API convention
 * 
 * 
 */
public final class GetFileOperation extends HTTPOperation {
    private static final String TAG = "GetFileOperation";

    public static final String PARAM_GETFILE = GetFileOperation.class.getName()
            + ".PARAM_GETFILE";
    public static final String PARAM_SHA256 = GetFileOperation.class.getName()
            + ".PARAM_SHA256";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException,
            DataException {

        final GetFileRequest getFileRequest = (GetFileRequest) request
                .getParcelable(
                        GetFileOperation.PARAM_GETFILE);

        // Get request data
        if (getFileRequest != null) {
            TakHttpClient client = null;
            if (getFileRequest.useTruststore()) {
                Log.d(TAG,
                        "Creating client for type: "
                                + getFileRequest.getTrustStoreType());
                try {
                    SSLSocketFactory factory = TLSUtils.createSSLSocketFactory(
                            getFileRequest.getTrustStoreType(),
                            getFileRequest.getCredentialType());

                    if (factory != null) {
                        client = TakHttpClient.GetHttpClient(factory,
                                getFileRequest.getUrl());
                    }
                } catch (KeyStoreException | UnrecoverableKeyException
                        | CertificateException | IOException
                        | KeyManagementException | NoSuchAlgorithmException e) {
                    Log.w(TAG, "Failed to create TLS client", e);
                }
            }

            if (client == null) {
                Log.d(TAG,
                        "Creating client for URL: " + getFileRequest.getUrl());
                client = TakHttpClient.GetHttpClient(getFileRequest.getUrl());
            }

            return GetFile(client,
                    getFileRequest,
                    true);
        }
        return null;
    }

    /**
     * Perform the HTTP Get. use provided URL, not built from httpclient
     * 
     * @param httpclient the http client to use
     * @param fileRequest the fire request
     * @return returns a bundle which contains the file request, the sha256, and the status code
     * @throws DataException, ConnectionException
     */
    static Bundle GetFile(TakHttpClient httpclient, GetFileRequest fileRequest,
            boolean bShutdownClient)
            throws DataException, ConnectionException {
        if (fileRequest == null) {
            throw new DataException("Unable to serialize import file request");
        }

        if (!fileRequest.isValid()) {
            throw new DataException(
                    "Unable to serialize invalid import file request");
        }

        try {
            // now start timer
            long startTime = System.currentTimeMillis();

            // ///////
            // Get file
            // ///////
            Log.d(TAG, "sending GET File request to: " + fileRequest.getUrl());
            HttpGet httpget = new HttpGet(fileRequest.getUrl());

            Log.d(TAG, "executing request " + httpget.getRequestLine());
            TakHttpResponse response = httpclient.execute(httpget);
            HttpEntity resEntity = response.getEntity();

            Log.d(TAG, "processing response");
            response.verifyOk();

            // Write file
            File temp = new File(fileRequest.getDir(),
                    fileRequest.getFileName());
            try (FileOutputStream fos = IOProviderFactory
                    .getOutputStream(temp)) {
                resEntity.writeTo(fos);
            }

            // Now verify we got download correctly
            if (!FileSystemUtils.isFile(temp)) {
                FileSystemUtils.deleteFile(temp);
                throw new ConnectionException("Failed to download data");
            }

            long downloadSize = IOProviderFactory.length(temp);
            long stopTime = System.currentTimeMillis();

            // Compute SHA256 checksum on service (rather than UI thread) for convenience
            String sha256 = HashingUtils.sha256sum(temp);
            Log.d(TAG,
                    String.format(
                            LocaleUtil.getCurrent(),
                            "File Request %s downloaded %d bytes in %f seconds. SHA256=%s",
                            fileRequest.toString(), downloadSize,
                            (stopTime - startTime) / 1000F, sha256));

            Bundle output = new Bundle();
            output.putParcelable(PARAM_GETFILE, fileRequest);
            output.putString(PARAM_SHA256, sha256);
            output.putInt(NetworkOperation.PARAM_STATUSCODE,
                    response.getStatusCode());
            return output;
        } catch (TakHttpException e) {
            Log.e(TAG, "Failed to download file: " + fileRequest.getUrl(), e);
            throw new ConnectionException(e.getMessage(), e.getStatusCode());
        } catch (Exception e) {
            Log.e(TAG, "Failed to download file: " + fileRequest.getUrl(), e);
            throw new ConnectionException(e.getMessage(),
                    NetworkOperation.STATUSCODE_UNKNOWN);
        } finally {
            try {
                if (bShutdownClient && httpclient != null)
                    httpclient.shutdown();
            } catch (Exception ignore) {
            }
        }
    }
}
