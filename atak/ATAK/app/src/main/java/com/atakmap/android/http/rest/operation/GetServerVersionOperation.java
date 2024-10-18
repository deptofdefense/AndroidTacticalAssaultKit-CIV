
package com.atakmap.android.http.rest.operation;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.http.rest.request.GetServerVersionRequest;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpException;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.HttpStatus;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Generic operation to GET server version
 */
public class GetServerVersionOperation extends HTTPOperation {
    private static final String TAG = "GetServerVersionOperation";

    public static final String PARAM_REQUEST = GetServerVersionOperation.class
            .getName() + ".PARAM_REQUEST";
    public static final String PARAM_RESPONSE = GetServerVersionOperation.class
            .getName() + ".PARAM_RESPONSE";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException,
            DataException {

        // Get request data
        GetServerVersionRequest queryRequest = null;

        try {
            queryRequest = GetServerVersionRequest.fromJSON(
                    new JSONObject(request.getString(
                            GetServerVersionOperation.PARAM_REQUEST)));
        } catch (JSONException e) {
            Log.e(TAG, "Failed to serialize JSON", e);
        }

        if (queryRequest == null) {
            throw new DataException("Unable to serialize query request");
        }

        if (!queryRequest.isValid()) {
            throw new DataException(
                    "Unable to serialize invalid query request");
        }

        return queryRequest(queryRequest);
    }

    /**
     * Give a server version request, perform the query and return the corresponding bundle containing
     * a PARAM_RESPONSE
     * @param queryRequest the query request to use
     * @return the bundle containing the PARAM_RESPONSE
     * @throws ConnectionException a connection exception if the query fails
     */
    public static Bundle queryRequest(
            GetServerVersionRequest queryRequest)
            throws ConnectionException {

        Bundle output = new Bundle();
        output.putParcelable(PARAM_REQUEST, queryRequest);
        output.putInt(NetworkOperation.PARAM_STATUSCODE, HttpStatus.SC_OK);

        TakHttpClient client = null;
        try {
            client = TakHttpClient.GetHttpClient(queryRequest.getBaseUrl());

            String path = queryRequest.isGetConfig() ? "api/version/config"
                    : "api/version";
            String queryUrl = client.getUrl(path);
            String responseBody = client.getGZip(queryUrl,
                    queryRequest.getMatcher());

            if (FileSystemUtils.isEmpty(responseBody)) {
                throw new ConnectionException("Server version response empty");
            }

            output.putString(PARAM_RESPONSE, responseBody.trim());
            return output;
        } catch (TakHttpException e) {
            Log.e(TAG, "Failed to get version", e);
            throw new ConnectionException(e.getMessage(), e.getStatusCode());
        } catch (Exception e) {
            Log.e(TAG, "Failed to get version", e);
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
