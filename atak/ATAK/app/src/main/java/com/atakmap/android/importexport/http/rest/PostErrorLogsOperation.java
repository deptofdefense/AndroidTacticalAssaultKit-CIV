
package com.atakmap.android.importexport.http.rest;

import android.content.Context;
import android.os.Bundle;
import android.util.Base64;

import com.atakmap.android.http.rest.operation.HTTPOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.app.R;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpException;
import com.atakmap.comms.http.TakHttpResponse;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

import java.io.File;
import java.net.URLEncoder;

/**
 *
 */
public final class PostErrorLogsOperation extends HTTPOperation {
    private static final String TAG = "PostErrorLogsOperation";

    public static final String PARAM_REQUEST = PostErrorLogsOperation.class
            .getName()
            + ".PARAM_REQUEST";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException,
            DataException {

        // Get request data
        PostErrorLogsRequest postRequest = (PostErrorLogsRequest) request
                .getParcelable(PostErrorLogsOperation.PARAM_REQUEST);
        if (postRequest == null) {
            throw new DataException("Unable to serialize post request");
        }

        if (!postRequest.isValid()) {
            throw new DataException(
                    "Invalid post request");
        }

        return postErrorLog(context, postRequest);
    }

    private static Bundle postErrorLog(Context context,
            PostErrorLogsRequest postRequest)
            throws ConnectionException,
            DataException {

        TakHttpClient client = null;
        int statusCode = NetworkOperation.STATUSCODE_UNKNOWN;
        try {
            client = TakHttpClient.GetHttpClient(postRequest.getBaseUrl());

            //TODO update progress/notification?
            String postUrl = client.getUrl("/ErrorLog?platform=")
                    + URLEncoder.encode(context.getString(R.string.app_name),
                            FileSystemUtils.UTF8_CHARSET.name())
                    + "&uid="
                    + URLEncoder.encode(postRequest.getUid(),
                            FileSystemUtils.UTF8_CHARSET.name())
                    + "&callsign="
                    + URLEncoder.encode(postRequest.getCallsign(),
                            FileSystemUtils.UTF8_CHARSET.name())
                    + "&majorVersion="
                    + URLEncoder.encode(postRequest.getVersionName(),
                            FileSystemUtils.UTF8_CHARSET.name())
                    + "&minorVersion="
                    + URLEncoder.encode(postRequest.getVersionCode(),
                            FileSystemUtils.UTF8_CHARSET.name());

            File exportFile = new File(postRequest.getExportFile());
            byte[] fileContents = FileSystemUtils.read(exportFile);
            String encodedContents = Base64.encodeToString(fileContents,
                    Base64.DEFAULT);
            StringEntity se = new StringEntity(encodedContents);
            se.setContentType("text/plain");
            se.setContentEncoding(FileSystemUtils.UTF8_CHARSET.name());

            HttpPost httppost = new HttpPost(postUrl);
            httppost.setEntity(se);

            // post file
            TakHttpResponse response = client.execute(httppost);
            statusCode = response.getStatusCode();
            response.verifyOk();

            Bundle output = new Bundle();
            output.putParcelable(PARAM_REQUEST, postRequest);
            output.putInt(NetworkOperation.PARAM_STATUSCODE, statusCode);
            return output;
        } catch (TakHttpException e) {
            Log.e(TAG, "Failed to post error logs", e);
            throw new ConnectionException(e.getMessage(), e.getStatusCode());
        } catch (Exception e) {
            Log.e(TAG, "Failed to post error logs", e);
            throw new ConnectionException(e.getMessage(), statusCode);
        } finally {
            try {
                if (client != null)
                    client.shutdown();
            } catch (Exception e) {
                Log.d(TAG, "Failed to shutdown the client", e);
            }
        }
    }
}
