
package com.atakmap.android.http.rest.operation;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.cot.CotMapComponent;
import com.atakmap.android.http.rest.ServerContact;
import com.atakmap.android.http.rest.ServerVersion;
import com.atakmap.android.http.rest.request.GetClientListRequest;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpException;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.HttpStatus;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Generic operation to GET string content e.g. HTTP Get some JSON content
 * Returns content back to UI thread, if intensive processing on teh response is required,
 * then a new operation should be created to do the processing in the background/service
 */
public class GetClientListOperation extends HTTPOperation {
    private static final String TAG = "GetClientListOperation";

    public static final String PARAM_REQUEST = GetClientListOperation.class
            .getName() + ".PARAM_REQUEST";
    public static final String PARAM_RESPONSE = GetClientListOperation.class
            .getName() + ".PARAM_RESPONSE";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException,
            DataException {

        // Get request data
        GetClientListRequest queryRequest = null;

        try {
            queryRequest = GetClientListRequest.fromJSON(
                    new JSONObject(request.getString(
                            GetClientListOperation.PARAM_REQUEST)));
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

    private static Bundle queryRequest(GetClientListRequest queryRequest)
            throws ConnectionException {

        ArrayList<ServerContact> contactList = query(queryRequest);

        Bundle output = new Bundle();
        output.putParcelable(PARAM_REQUEST, queryRequest);
        output.putParcelableArrayList(PARAM_RESPONSE, contactList);
        output.putInt(NetworkOperation.PARAM_STATUSCODE, HttpStatus.SC_OK);
        return output;
    }

    public static ArrayList<ServerContact> query(
            GetClientListRequest queryRequest)
            throws ConnectionException {

        TakHttpClient client = null;
        try {
            //parse contact list
            NetConnectString server = NetConnectString.fromString(queryRequest
                    .getServerConnectString());
            if (server == null) {
                throw new IOException("Failed to parse server connect string: "
                        + queryRequest.getServerConnectString());
            }

            client = TakHttpClient.GetHttpClient(queryRequest.getBaseUrl(),
                    queryRequest
                            .getServerConnectString());

            //TODO this api/endpoint supports a couple parameters we are not currently using
            String queryUrl = client.getUrl("api/clientEndPoints");
            String responseBody = client.getGZip(queryUrl,
                    queryRequest.getMatcher());
            long syncTime = new CoordinatedTime().getMilliseconds();

            ArrayList<ServerContact> contactList = ServerContact
                    .fromResultJSON(server, syncTime, new JSONObject(
                            responseBody));

            //continue to capture api version in legacy method, to account for TAK Servers < 1.3.6
            //Note GetServerVersionOperation provides this api version and more
            ServerVersion ver = ServerVersion.fromJSON(queryRequest
                    .getServerConnectString(), new JSONObject(responseBody));
            //Log.d(TAG, queryRequest.getServerConnectString() + ", " + responseBody);
            CotMapComponent inst = CotMapComponent.getInstance();
            if (ver != null && ver.isValid() && inst != null) {
                inst.setServerVersion(ver);
            } else {
                Log.w(TAG,
                        "Unable to set server version for: "
                                + queryRequest.getBaseUrl());
            }

            Log.d(TAG, "Parsed contact list of size: " + contactList.size());
            return contactList;
        } catch (TakHttpException e) {
            Log.e(TAG, "Failed to get packages", e);
            throw new ConnectionException(e.getMessage(), e.getStatusCode());
        } catch (Exception e) {
            Log.e(TAG, "Failed to get packages", e);
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
