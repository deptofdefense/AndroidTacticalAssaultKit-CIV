
package com.atakmap.android.channels.net;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.http.rest.operation.HTTPOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpException;
import com.atakmap.comms.http.TakHttpResponse;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

public class GetAllServerGroupsOperation extends HTTPOperation {

    private static final String TAG = "ChannelsGetAllServerGroupsOperation";

    public static final String PARAM_GET_ALL_SERVER_GROUPS_REQUEST = GetAllServerGroupsOperation.class
            .getName()
            + ".PARAM_GET_ALL_SERVER_GROUPS_REQUEST";

    public static final String PARAM_GET_ALL_SERVER_GROUPS_RESPONSE = GetAllServerGroupsOperation.class
            .getName()
            + ".PARAM_GET_ALL_SERVER_GROUPS_RESPONSE";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException {

        TakHttpClient httpClient = null;

        try {

            request.setClassLoader(this.getClass().getClassLoader());

            // Get request data
            GetAllServerGroupsRequest groupsRequestRequest = (GetAllServerGroupsRequest) request
                    .getParcelable(
                            GetAllServerGroupsOperation.PARAM_GET_ALL_SERVER_GROUPS_REQUEST);
            if (groupsRequestRequest == null) {
                throw new DataException("Unable to serialize groups request");
            }

            final String baseUrl = "https://"
                    + groupsRequestRequest.getServer();
            httpClient = new TakHttpClient(baseUrl,
                    groupsRequestRequest.getConnectString());

            String url = "/api/groups/all?useCache=true";
            if (groupsRequestRequest.getSendLatestSA()) {
                url += "&sendLatestSA=true";
            }

            HttpGet httpget = new HttpGet(httpClient.getUrl(url));
            TakHttpResponse response = httpClient.execute(httpget);
            response.verifyOk();
            HttpEntity resEntity = response.getEntity();

            String groups = EntityUtils.toString(resEntity);

            Bundle output = new Bundle();
            output.putParcelable(PARAM_GET_ALL_SERVER_GROUPS_REQUEST,
                    groupsRequestRequest);
            output.putString(PARAM_GET_ALL_SERVER_GROUPS_RESPONSE, groups);

            return output;

        } catch (TakHttpException e) {
            Log.e(TAG, "GetAllServerGroupsOperation failed!", e);
            throw new ConnectionException(e.getMessage(), e.getStatusCode());
        } catch (Exception e) {
            Log.e(TAG, "GetAllServerGroupsOperation failed!", e);
            throw new ConnectionException(e.getMessage(),
                    NetworkOperation.STATUSCODE_UNKNOWN);
        } finally {
            try {
                if (httpClient != null)
                    httpClient.shutdown();
            } catch (Exception e) {
                Log.d(TAG, "Failed to shutdown the client", e);
            }
        }
    }
}
