
package com.atakmap.android.channels.net;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.atakmap.android.channels.ChannelsReceiver;
import com.atakmap.android.http.rest.HTTPRequestManager;
import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.http.rest.ServerGroup;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.requestmanager.Request;
import com.foxykeep.datadroid.requestmanager.RequestManager;
import org.json.JSONObject;
import java.util.List;

public class ServerGroupsClient implements RequestManager.RequestListener {
    protected static final String TAG = "ChannelsServerGroupsClient";

    public interface ServerGroupsCallback {
        void onGetServerGroups(String server, List<ServerGroup> serverGroups);
    }

    private static ServerGroupsClient instance = null;

    public synchronized static ServerGroupsClient getInstance() {
        if (instance == null) {
            instance = new ServerGroupsClient();
        }
        return instance;
    }

    private ServerGroupsCallback serverGroupsCallback = null;
    private Context context;
    private Context appCtx;
    private MapView view;
    private boolean getAllGroupsQueryInProgress = false;

    public final static int REQUEST_TYPE_GET_ALL_SERVER_GROUPS;
    public final static int REQUEST_TYPE_SET_ACTIVE_SERVER_GROUPS;

    static {
        REQUEST_TYPE_GET_ALL_SERVER_GROUPS = NetworkOperationManager
                .register(
                        "GetAllServerGroupsOperation",
                        new GetAllServerGroupsOperation());

        REQUEST_TYPE_SET_ACTIVE_SERVER_GROUPS = NetworkOperationManager
                .register(
                        "SetActiveServerGroupsOperation",
                        new SetActiveServerGroupsOperation());
    }

    public void getAllGroups(final Context context, final String connectString,
            boolean sendLatestSA, ServerGroupsCallback serverGroupsCallback) {

        Log.d(TAG, "getting all groups for " + connectString);
        if (getAllGroupsQueryInProgress) {
            Log.d(TAG, "ignoring request for concurrent getAllGroups query");
            return;
        }

        this.context = context;
        this.view = MapView.getMapView();
        this.appCtx = view.getContext();
        this.serverGroupsCallback = serverGroupsCallback;

        // Kick off async HTTP request to post to server
        getAllGroupsQueryInProgress = true;
        GetAllServerGroupsRequest request = new GetAllServerGroupsRequest(
                connectString, sendLatestSA);
        HTTPRequestManager.from(context).execute(
                request.createGetAllServerGroupsRequest(), this);
    }

    public void setActiveGroups(final Context context,
            final String connectString, List<ServerGroup> serverGroups) {
        this.context = context;
        this.view = MapView.getMapView();
        this.appCtx = view.getContext();

        String serverGroupsJson = ServerGroup.toResultJSON(serverGroups)
                .toString();

        // Kick off async HTTP request to post to server
        SetActiveServerGroupsRequest request = new SetActiveServerGroupsRequest(
                connectString, serverGroupsJson);
        HTTPRequestManager.from(appCtx).execute(
                request.createSetActiveServerGroupsRequest(), this);
    }

    @Override
    public void onRequestFinished(Request request, Bundle resultData) {
        try {
            // HTTP response received successfully
            Log.d(TAG, "ServerGroupsClient request finished successfully: "
                    + request.getRequestType());
            getAllGroupsQueryInProgress = false;

            if (request
                    .getRequestType() == ServerGroupsClient.REQUEST_TYPE_GET_ALL_SERVER_GROUPS) {

                if (resultData == null) {
                    Log.e(TAG, "REQUEST_TYPE_GET_ALL_SERVER_GROUPS failed!");
                    return;
                }

                String groupJson = resultData.getString(
                        GetAllServerGroupsOperation.PARAM_GET_ALL_SERVER_GROUPS_RESPONSE);
                List<ServerGroup> serverGroups = ServerGroup
                        .fromResultJSON(new JSONObject(groupJson));

                GetAllServerGroupsRequest getAllServerGroupsRequest = (GetAllServerGroupsRequest) request
                        .getParcelable(
                                GetAllServerGroupsOperation.PARAM_GET_ALL_SERVER_GROUPS_REQUEST);

                if (serverGroupsCallback != null) {
                    serverGroupsCallback.onGetServerGroups(
                            getAllServerGroupsRequest.getServer(),
                            serverGroups);
                }

            } else if (request
                    .getRequestType() == ServerGroupsClient.REQUEST_TYPE_SET_ACTIVE_SERVER_GROUPS) {

                // Get request data
                SetActiveServerGroupsRequest setActiveServerGroupsRequest = (SetActiveServerGroupsRequest) request
                        .getParcelable(
                                SetActiveServerGroupsOperation.PARAM_SET_ACTIVE_SERVER_GROUPS_REQUEST);
                if (setActiveServerGroupsRequest == null) {
                    Log.e(TAG,
                            "onRequestFinished: unable to get SetActiveServerGroupsRequest!");
                    return;
                }

                Intent channelsUpdatedIntent = new Intent(
                        ChannelsReceiver.CHANNELS_UPDATED);
                channelsUpdatedIntent.putExtra("server",
                        setActiveServerGroupsRequest.getServer());
                AtakBroadcast.getInstance()
                        .sendBroadcast(channelsUpdatedIntent);
            }

        } catch (Exception e) {
            Log.e(TAG, "exception ing onRequestFinished!", e);
        }
    }

    @Override
    public void onRequestConnectionError(final Request request,
            RequestManager.ConnectionError ce) {
        String detail = NetworkOperation.getErrorMessage(ce);
        Log.e(TAG, "ServerGroupsClient Failed - Connection Error: "
                + detail);
        getAllGroupsQueryInProgress = false;
    }

    @Override
    public void onRequestDataError(final Request request) {
        Log.e(TAG, "ServerGroupsClient Failed - Data Error");
        getAllGroupsQueryInProgress = false;
    }

    @Override
    public void onRequestCustomError(Request request, Bundle resultData) {
        Log.e(TAG, "ServerGroupsClient Failed - Custom Error");
        getAllGroupsQueryInProgress = false;
    }
}
