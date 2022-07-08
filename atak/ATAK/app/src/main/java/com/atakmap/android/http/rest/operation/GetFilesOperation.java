
package com.atakmap.android.http.rest.operation;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.http.rest.request.GetFileRequest;
import com.atakmap.android.http.rest.request.GetFilesRequest;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.HttpStatus;

import java.util.List;

/**
 * REST Operation to GET one or more files
 */
public final class GetFilesOperation extends HTTPOperation {
    private static final String TAG = "GetFilesOperation";

    public static final String PARAM_GETFILES = GetFilesOperation.class
            .getName()
            + ".PARAM_GETFILES";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException,
            DataException {

        // Get request data
        GetFilesRequest fileRequests = (GetFilesRequest) request
                .getParcelable(GetFilesOperation.PARAM_GETFILES);
        if (fileRequests == null) {
            throw new DataException("Unable to serialize import file requests");
        }

        if (!fileRequests.isValid()) {
            throw new DataException(
                    "Unable to serialize invalid import file requests");
        }

        List<GetFileRequest> requests = fileRequests.getRequests();
        Log.d(TAG, "executing request count " + requests.size());
        // TODO OK to reuse same client? possibly hitting different servers...
        // TODO do we need check to be sure it is still open/available each time?
        for (GetFileRequest curRequest : requests) {
            // TODO catch individual error and attempt the rest?
            // Note MD5 is in output bundle, but currently not using it
            TakHttpClient httpclient = TakHttpClient.GetHttpClient(curRequest
                    .getUrl());
            GetFileOperation.GetFile(httpclient, curRequest, true);
        }

        Bundle output = new Bundle();
        output.putParcelable(PARAM_GETFILES, fileRequests);
        //if all succeeded, then all were 200
        output.putInt(NetworkOperation.PARAM_STATUSCODE, HttpStatus.SC_OK);
        return output;
    }
}
