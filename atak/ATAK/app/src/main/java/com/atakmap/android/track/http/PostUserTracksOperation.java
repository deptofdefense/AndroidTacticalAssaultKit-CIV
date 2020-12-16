
package com.atakmap.android.track.http;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

import com.atakmap.android.http.rest.operation.HTTPOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpException;
import com.atakmap.comms.http.TakHttpResponse;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.KmlFileSpatialDb;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;

import java.io.File;

/**
 * REST Operation to POST user tracks to a server
 *
 * 
 */
public final class PostUserTracksOperation extends HTTPOperation {
    private static final String TAG = "PostUserTracksOperation";

    public static final String PARAM_QUERY = PostUserTracksOperation.class
            .getName()
            + ".PARAM_QUERY";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException,
            DataException {

        // Get request data
        PostUserTracksRequest postRequest = (PostUserTracksRequest) request
                .getParcelable(PostUserTracksOperation.PARAM_QUERY);
        if (postRequest == null) {
            throw new DataException("Unable to serialize query request");
        }

        if (!postRequest.isValid()) {
            throw new DataException(
                    "Unable to serialize invalid query request");
        }

        return postUserTracks(postRequest);
    }

    private static Bundle postUserTracks(PostUserTracksRequest postRequest)
            throws ConnectionException {

        TakHttpClient client = null;
        int statusCode = NetworkOperation.STATUSCODE_UNKNOWN;
        try {
            client = TakHttpClient.GetHttpClient(postRequest.getBaseUrl());

            String postUrl = client.getUrl("/TracksKML");
            Uri.Builder postbuilder = Uri.parse(postUrl).buildUpon();
            postbuilder.appendQueryParameter("uid", postRequest.getUid());
            postbuilder.appendQueryParameter("callsign",
                    postRequest.getCallsign());
            postbuilder.appendQueryParameter("cotType",
                    postRequest.getCotType());
            postbuilder.appendQueryParameter("groupName",
                    postRequest.getGroupName());
            postbuilder.appendQueryParameter("groupRole",
                    postRequest.getGroupRole());
            postUrl = postbuilder.build().toString();

            File postfile = new File(postRequest.getFilePath());
            HttpPost httppost = new HttpPost(postUrl);

            FileEntity fileEntity = new FileEntity(postfile,
                    KmlFileSpatialDb.KML_FILE_MIME_TYPE);
            httppost.setEntity(fileEntity);

            long startTime = SystemClock.elapsedRealtime();
            TakHttpResponse postResponse = client.execute(httppost);
            statusCode = postResponse.getStatusCode();
            postResponse.verifyOk();
            String responseBody = postResponse.getStringEntity();

            Log.d(TAG, "Response from post: " + responseBody);
            long stopTime = SystemClock.elapsedRealtime();
            Log.d(TAG,
                    String.format(LocaleUtil.getCurrent(),
                            "User Track %s posted %d bytes in %f seconds",
                            postRequest.toString(),
                            IOProviderFactory.length(postfile),
                            (stopTime - startTime) / 1000F));

            Bundle output = new Bundle();
            output.putParcelable(PARAM_QUERY, postRequest);
            output.putInt(NetworkOperation.PARAM_STATUSCODE, statusCode);
            return output;
        } catch (TakHttpException e) {
            Log.e(TAG, "Failed to post user tracks", e);
            throw new ConnectionException(e.getMessage(), e.getStatusCode());
        } catch (Exception e) {
            Log.e(TAG, "Failed to post user tracks", e);
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
