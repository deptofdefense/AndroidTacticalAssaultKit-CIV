
package com.atakmap.android.missionpackage.http.rest;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.http.rest.operation.HTTPOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.util.ServerListDialog;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpException;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.HttpStatus;

/**
 * REST Operation to GET list of Mission Packages from a server
 * 
 * 
 */
public final class QueryMissionPackageOperation extends HTTPOperation {
    private static final String TAG = "QueryMissionPackageOperation";

    public static final String PARAM_QUERY = QueryMissionPackageOperation.class
            .getName()
            + ".PARAM_QUERY";
    public static final String PARAM_JSONLIST = QueryMissionPackageOperation.class
            .getName()
            + ".PARAM_JSONLIST";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException,
            DataException {

        // Get request data
        QueryMissionPackageRequest queryRequest = (QueryMissionPackageRequest) request
                .getParcelable(QueryMissionPackageOperation.PARAM_QUERY);
        if (queryRequest == null) {
            throw new DataException("Unable to serialize query request");
        }

        if (!queryRequest.isValid()) {
            throw new DataException(
                    "Unable to serialize invalid query request");
        }

        return queryPackages(queryRequest);
    }

    private static Bundle queryPackages(QueryMissionPackageRequest queryRequest)
            throws ConnectionException,
            DataException {

        TakHttpClient client = null;
        try {
            client = TakHttpClient.GetHttpClient(ServerListDialog
                    .getBaseUrl(queryRequest
                            .getServerConnectString()),
                    queryRequest.getServerConnectString());

            String queryUrl = client
                    .getUrl("/sync/search?keywords=missionpackage");
            if (queryRequest.hasTool()) {
                queryUrl += ("&tool=" + queryRequest.getTool());
            }
            String responseBody = client.get(queryUrl, "resultCount");

            Bundle output = new Bundle();
            output.putParcelable(PARAM_QUERY, queryRequest);
            output.putString(PARAM_JSONLIST, responseBody);
            output.putInt(NetworkOperation.PARAM_STATUSCODE, HttpStatus.SC_OK);
            return output;
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
