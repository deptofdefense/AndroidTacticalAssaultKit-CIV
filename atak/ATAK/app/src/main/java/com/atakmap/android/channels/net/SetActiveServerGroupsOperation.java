
package com.atakmap.android.channels.net;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.http.rest.operation.HTTPOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.maps.MapView;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpException;
import com.atakmap.comms.http.TakHttpResponse;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;

public class SetActiveServerGroupsOperation extends HTTPOperation {

    private static final String TAG = "ChannelsSetActiveServerGroupsOperation";

    public static final String PARAM_SET_ACTIVE_SERVER_GROUPS_REQUEST = SetActiveServerGroupsOperation.class
            .getName()
            + ".PARAM_SET_ACTIVE_SERVER_GROUPS_REQUEST";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException {

        TakHttpClient httpClient = null;

        try {
            request.setClassLoader(this.getClass().getClassLoader());

            // Get request data
            SetActiveServerGroupsRequest activeServerGroupsRequest = (SetActiveServerGroupsRequest) request
                    .getParcelable(
                            SetActiveServerGroupsOperation.PARAM_SET_ACTIVE_SERVER_GROUPS_REQUEST);
            if (activeServerGroupsRequest == null) {
                throw new DataException(
                        "Unable to serialize active groups request");
            }

            final String baseUrl = "https://"
                    + activeServerGroupsRequest.getServer();
            httpClient = new TakHttpClient(baseUrl);
            HttpPut httpPut = new HttpPut(
                    httpClient.getUrl("/api/groups/active?clientUid="
                            + MapView.getDeviceUid()));
            httpPut.addHeader("content-type", "application/json");
            httpPut.setEntity(new StringEntity(
                    activeServerGroupsRequest.getActiveGroups(),
                    FileSystemUtils.UTF8_CHARSET.toString()));

            TakHttpResponse response = httpClient.execute(httpPut);
            response.verifyOk();
            HttpEntity resEntity = response.getEntity();

            Bundle output = new Bundle();
            output.putParcelable(PARAM_SET_ACTIVE_SERVER_GROUPS_REQUEST,
                    activeServerGroupsRequest);
            return output;

        } catch (TakHttpException e) {
            Log.e(TAG, "SetActiveServerGroupsOperation failed!", e);
            ConnectionException ce = new ConnectionException(e.getMessage(),
                    e.getStatusCode());
            ce.initCause(e);
            throw ce;
        } catch (Exception e) {
            Log.e(TAG, "SetActiveServerGroupsOperation failed!", e);
            ConnectionException ce = new ConnectionException(e.getMessage(),
                    NetworkOperation.STATUSCODE_UNKNOWN);
            ce.initCause(e);
            throw ce;
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
