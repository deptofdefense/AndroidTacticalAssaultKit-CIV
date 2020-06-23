
package com.atakmap.android.http.rest.operation;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.http.rest.request.SimpleHttpRequest;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpException;
import com.atakmap.comms.http.TakHttpResponse;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Generic operation to execute a "Rest style" HTTP operation with optional
 * query string params, but no body/content
 *
 * 
 */
public class SimpleHttpOperation extends HTTPOperation {
    private static final String TAG = "SimpleHttpOperation";

    public static final String PARAM_REQUEST = SimpleHttpOperation.class
            .getName() + ".PARAM_REQUEST";
    public static final String PARAM_RESPONSE = SimpleHttpOperation.class
            .getName() + ".PARAM_RESPONSE";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException,
            DataException {

        // Get request data
        SimpleHttpRequest putRequest = null;

        try {
            putRequest = SimpleHttpRequest.fromJSON(
                    new JSONObject(request.getString(
                            SimpleHttpOperation.PARAM_REQUEST)));
        } catch (JSONException e) {
            Log.e(TAG, "Failed to serialize JSON", e);
        }

        if (putRequest == null) {
            throw new DataException("Unable to serialize query request");
        }

        if (!putRequest.isValid()) {
            throw new DataException(
                    "Unable to serialize invalid query request");
        }

        return SimpleHttpOperation.query(putRequest);
    }

    private static Bundle query(SimpleHttpRequest putRequest)
            throws ConnectionException,
            DataException {

        TakHttpClient client = null;
        try {
            client = TakHttpClient.GetHttpClient(putRequest.getBaseUrl());

            String putUrl = client.getUrl(putRequest.getPath());
            putUrl = FileSystemUtils.sanitizeURL(putUrl);

            HttpRequestBase request = null;
            switch (putRequest.getType()) {
                case GET: {
                    request = new HttpGet(putUrl);
                }
                    break;
                case PUT: {
                    request = new HttpPut(putUrl);
                }
                    break;
                case DELETE: {
                    request = new HttpDelete(putUrl);
                }
                    break;
                default:
                    Log.w(TAG, "Invalid request type: " + putRequest.getType());
            }

            if (request == null) {
                throw new IOException("Failed to parse request body");
            }

            TakHttpResponse response = client.execute(request);
            response.verify(putRequest.getMatcherCode());
            String responseBody = response.getStringEntity(putRequest
                    .getMatcher());

            Bundle output = new Bundle();
            output.putString(PARAM_REQUEST, putRequest.toJSON().toString());
            output.putString(PARAM_RESPONSE, responseBody);
            output.putInt(NetworkOperation.PARAM_STATUSCODE,
                    response.getStatusCode());
            return output;
        } catch (TakHttpException e) {
            Log.e(TAG, "Failed to " + putRequest.getType(), e);
            throw new ConnectionException(e.getMessage(), e.getStatusCode());
        } catch (Exception e) {
            Log.e(TAG, "Failed to " + putRequest.getType(), e);
            throw new ConnectionException(e.getMessage(),
                    NetworkOperation.STATUSCODE_UNKNOWN);
        } finally {
            try {
                if (client != null)
                    client.shutdown();
            } catch (Exception ignore) {
            }
        }
    }
}
