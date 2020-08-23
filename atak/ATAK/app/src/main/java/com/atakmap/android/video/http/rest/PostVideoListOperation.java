
package com.atakmap.android.video.http.rest;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.http.rest.operation.HTTPOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.comms.http.HttpUtil;
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

/**
 * REST Operation to POST a list of video connections to a server
 *
 * 
 */
public final class PostVideoListOperation extends HTTPOperation {
    private static final String TAG = "PostVideoListOperation";

    public static final String PARAM_REQUEST = PostVideoListOperation.class
            .getName()
            + ".PARAM_REQUEST";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException,
            DataException {

        // Get request data
        PostVideoListRequest postRequest = (PostVideoListRequest) request
                .getParcelable(PostVideoListOperation.PARAM_REQUEST);
        if (postRequest == null) {
            throw new DataException("Unable to serialize post request");
        }

        if (!postRequest.isValid()) {
            throw new DataException(
                    "Invalid post request");
        }

        return postVideoList(postRequest);
    }

    private static Bundle postVideoList(
            PostVideoListRequest postRequest)
            throws ConnectionException {

        TakHttpClient client = null;
        try {
            client = TakHttpClient.GetHttpClient(postRequest.getBaseUrl());
            HttpPost httppost = new HttpPost(client.getUrl("/vcm"));

            StringEntity se = new StringEntity(postRequest.getVideoXml());
            se.setContentType(HttpUtil.MIME_XML);
            se.setContentEncoding(FileSystemUtils.UTF8_CHARSET.name());
            httppost.setEntity(se);

            // post file
            TakHttpResponse response = client.execute(httppost);
            response.verifyOk();

            Bundle output = new Bundle();
            output.putParcelable(PARAM_REQUEST, postRequest);
            output.putInt(NetworkOperation.PARAM_STATUSCODE,
                    response.getStatusCode());
            return output;
        } catch (TakHttpException e) {
            Log.e(TAG, "Failed to post video list", e);
            throw new ConnectionException(e.getMessage(), e.getStatusCode());
        } catch (Exception e) {
            Log.e(TAG, "Failed to post video list", e);
            throw new ConnectionException(e.getMessage(),
                    NetworkOperation.STATUSCODE_UNKNOWN);
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
