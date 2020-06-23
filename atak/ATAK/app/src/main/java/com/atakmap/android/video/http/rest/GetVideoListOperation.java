
package com.atakmap.android.video.http.rest;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.http.rest.operation.HTTPOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpException;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.HttpStatus;

/**
 * REST Operation to GET list of video connections from a server
 *
 * 
 */
public final class GetVideoListOperation extends HTTPOperation {
    private static final String TAG = "GetVideoListOperation";

    public static final String PARAM_QUERY = GetVideoListOperation.class
            .getName()
            + ".PARAM_QUERY";
    public static final String PARAM_XMLLIST = GetVideoListOperation.class
            .getName()
            + ".PARAM_XMLLIST";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException,
            DataException {

        // Get request data
        GetVideoListRequest getRequest = (GetVideoListRequest) request
                .getParcelable(GetVideoListOperation.PARAM_QUERY);
        if (getRequest == null) {
            throw new DataException("Unable to serialize get request");
        }

        if (!getRequest.isValid()) {
            throw new DataException(
                    "Invalid get request");
        }

        return getVideoList(getRequest);
    }

    private static Bundle getVideoList(
            GetVideoListRequest getRequest)
            throws ConnectionException {

        TakHttpClient client = null;
        try {
            client = TakHttpClient.GetHttpClient(getRequest.getBaseUrl());
            String responseBody = client.get(client.getUrl("/vcm"),
                    "videoConnections");

            Bundle output = new Bundle();
            output.putParcelable(PARAM_QUERY, getRequest);
            output.putString(PARAM_XMLLIST, responseBody);
            output.putInt(NetworkOperation.PARAM_STATUSCODE, HttpStatus.SC_OK);
            return output;
        } catch (TakHttpException e) {
            Log.e(TAG, "Failed to get video list", e);
            throw new ConnectionException(e.getMessage(), e.getStatusCode());
        } catch (Exception e) {
            Log.e(TAG, "Failed to get video list", e);
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
