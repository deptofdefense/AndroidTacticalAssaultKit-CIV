
package com.atakmap.android.http.rest.operation;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import com.atakmap.android.http.rest.request.GetCotEventRequest;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpException;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.HttpStatus;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Generic operation to GET last CoT Event for a given UID
 */
public class GetCotEventOperation extends HTTPOperation {
    private static final String TAG = "GetCotEventOperation";

    public static final String PARAM_REQUEST = GetCotEventOperation.class
            .getName() + ".PARAM_REQUEST";
    public static final String PARAM_RESPONSE = GetCotEventOperation.class
            .getName() + ".PARAM_RESPONSE";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException,
            DataException {

        // Get request data
        GetCotEventRequest queryRequest = null;

        try {
            queryRequest = GetCotEventRequest.fromJSON(
                    new JSONObject(request.getString(
                            GetCotEventOperation.PARAM_REQUEST)));
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

        return query(queryRequest);
    }

    private static Bundle query(GetCotEventRequest queryRequest)
            throws ConnectionException,
            DataException {

        TakHttpClient client = null;
        try {
            client = TakHttpClient.GetHttpClient(queryRequest.getBaseUrl());

            String queryUrl = client.getUrl("api/cot/xml/"
                    + queryRequest.getUID());
            String responseBody = client.get(queryUrl,
                    queryRequest.getMatcher());

            if (responseBody != null && responseBody.contains("&#x")) {
                // Response XML contains encoded characters, decode them here
                // TODO: Why is this occurring in the first place?
                StringBuilder decRes = new StringBuilder();
                int start = 0, i;
                while ((i = responseBody.indexOf("&#x", start)) > -1) {
                    decRes.append(responseBody, start, i);
                    int end = responseBody.indexOf(";", i);
                    if (end == -1) {
                        start = i + 3;
                        continue;
                    }
                    String encChar = responseBody.substring(i + 3, end);
                    String decChar = Uri.decode("%" + encChar);
                    decRes.append(decChar);
                    start = end + 1;
                }
                decRes.append(responseBody.substring(start));
                responseBody = decRes.toString();
            }

            //parse contact list
            CotEvent event = CotEvent.parse(responseBody);
            if (event == null || !event.isValid()) {
                Log.e(TAG, "Failed to parse event: " + responseBody,
                        new IOException());
                return null;
                //throw new IOException("Failed to parse event: " + responseBody);
            }

            if (responseBody != null)
                Log.d(TAG,
                        "Parsed CoT event of size: " + responseBody.length());
            else
                Log.d(TAG, "response body is empty");

            Bundle output = new Bundle();
            output.putParcelable(PARAM_REQUEST, queryRequest);
            output.putParcelable(PARAM_RESPONSE, event);
            output.putInt(NetworkOperation.PARAM_STATUSCODE, HttpStatus.SC_OK);
            return output;
        } catch (TakHttpException e) {
            Log.e(TAG, "Failed to get cot", e);
            throw new ConnectionException(e.getMessage(), e.getStatusCode());
        } catch (Exception e) {
            Log.e(TAG, "Failed to get cot", e);
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
