
package com.atakmap.android.missionpackage.http.rest;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.http.rest.operation.HTTPOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.missionpackage.file.task.CopyAndSendTask;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.TAKServer;
import com.atakmap.comms.TAKServerListener;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.HttpStatus;

import java.io.File;

/**
 * REST Operation to GET to see if a Mission Package has already been posted. If not then HTTP
 * Post the mission package
 * 
 * 
 */
public final class PostMissionPackageOperation extends HTTPOperation {
    private static final String TAG = "PostMissionPackageOperation";

    public static final String PARAM_POSTFILE = PostMissionPackageOperation.class
            .getName()
            + ".PARAM_POSTFILE";
    public static final String PARAM_POSTEDURL = PostMissionPackageOperation.class
            .getName()
            + ".PARAM_POSTEDURL";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException,
            DataException {

        // Get request data
        PostMissionPackageRequest postRequest = (PostMissionPackageRequest) request
                .getParcelable(PostMissionPackageOperation.PARAM_POSTFILE);
        if (postRequest == null) {
            throw new DataException("Unable to serialize post file request");
        }

        if (!postRequest.isValid()) {
            throw new DataException(
                    "Unable to serialize invalid post file request");
        }

        return postPackage(postRequest);
    }

    private static Bundle postPackage(
            PostMissionPackageRequest request)
            throws ConnectionException,
            DataException {

        TAKServer server = null;
        boolean disconnected = false;

        try {
            String serverConnString = request.getServerConnectString();
            String hash = request.getHash();
            String name = request.getName();
            String filepath = request.getFilepath();

            server = TAKServerListener.getInstance().findServer(
                    serverConnString);
            disconnected = server != null && !server.isEnabled();

            // Connect to the server if we aren't already
            if (disconnected) {
                server.setEnabled(true);
                CommsMapComponent.getInstance().getCotService()
                        .addStreaming(server);
                int waitAttempts = 0;
                while (!server.isConnected() && waitAttempts < 10) {
                    waitAttempts++;
                    Thread.sleep(1000);
                }
            }

            String postedUrl = CopyAndSendTask.postPackage(
                    serverConnString, hash, name, new File(filepath));

            if (FileSystemUtils.isEmpty(postedUrl)) {
                throw new ConnectionException("Failed to post mission package");
            }

            Bundle output = new Bundle();
            output.putParcelable(PARAM_POSTFILE, request);
            output.putString(PARAM_POSTEDURL, postedUrl);
            //if we made it this far we are a 200
            output.putInt(NetworkOperation.PARAM_STATUSCODE, HttpStatus.SC_OK);
            return output;
        } catch (ConnectionException e) {
            Log.e(TAG, "Failed to post package", e);
            //forward on the status code/message
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Failed to post package", e);
            throw new ConnectionException(e.getMessage(), -1);
        } finally {
            // Disconnect
            if (disconnected) {
                server.setEnabled(false);
                CommsMapComponent.getInstance().getCotService()
                        .addStreaming(server);
            }
        }
    }
}
