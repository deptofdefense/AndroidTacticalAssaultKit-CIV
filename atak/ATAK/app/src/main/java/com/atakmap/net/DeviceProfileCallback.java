
package com.atakmap.net;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.requestmanager.Request;
import com.foxykeep.datadroid.requestmanager.RequestManager;

/**
 * Listener interface for device profile requests
 */
public abstract class DeviceProfileCallback
        implements RequestManager.RequestListener {

    private final static String TAG = "DeviceProfileCallback";
    private final Context context;

    public DeviceProfileCallback(Context context) {
        this.context = context;
    }

    /**
    * Callback for completed or error device profile requests
    *
    * @param status if the resultData should be considered available
    * @param resultData    Only available upon status = true
    */
    public abstract void onDeviceProfileRequestComplete(boolean status,
            Bundle resultData);

    @Override
    public void onRequestFinished(Request request, Bundle resultData) {

        try {
            // HTTP response received successfully
            Log.d(TAG, "DeviceProfileRequest finished successfully: "
                    + request.getRequestType());

            if (request
                    .getRequestType() == DeviceProfileClient.REQUEST_TYPE_GET_PROFILE) {

                // get the initial request that was sent out
                final DeviceProfileRequest initialRequest = resultData
                        .getParcelable(
                                DeviceProfileOperation.PARAM_PROFILE_REQUEST);

                //clean up internal tracking
                DeviceProfileClient.getInstance()
                        .callbackRecieved(initialRequest, true);

                //invoke child callback
                onDeviceProfileRequestComplete(true, resultData);

                //if enrollment, kick off reconnect of streaming connections
                if (initialRequest != null
                        && initialRequest.getOnEnrollment()) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            CommsMapComponent.getInstance().getCotService()
                                    .reconnectStreams();
                        }
                    }, "ReconnectStreamsThread").start();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception in OnRequestFinished!", e);
        }
    }

    @Override
    public void onRequestConnectionError(Request request,
            RequestManager.ConnectionError ce) {
        int status = ce.getStatusCode();

        String detail = NetworkOperation.getErrorMessage(ce);
        Log.w(TAG, "DeviceProfileRequest Failed - Connection Error: "
                + detail);

        NotificationUtil.getInstance().postNotification(
                NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                NotificationUtil.RED,
                context.getString(R.string.connection_error),
                context.getString(R.string.device_profile_failure),
                detail);

        try {
            Log.d(TAG,
                    "DeviceProfileRequest finished onRequestConnectionError: "
                            + request.getRequestType());

            if (request
                    .getRequestType() == DeviceProfileClient.REQUEST_TYPE_GET_PROFILE) {

                // get the initial request that was sent out
                final DeviceProfileRequest initialRequest = (DeviceProfileRequest) request
                        .getParcelable(
                                DeviceProfileOperation.PARAM_PROFILE_REQUEST);

                DeviceProfileClient.getInstance()
                        .callbackRecieved(initialRequest, false);

                // Older versions of TAK Server, prior to the profile API, will return 403/Forbidden
                onDeviceProfileRequestComplete(status == 403, null);
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception in onRequestConnectionError!", e);
        }
    }

    @Override
    public void onRequestDataError(Request request) {
        Log.w(TAG, "DeviceProfileRequest Failed - Data Error");

        NotificationUtil.getInstance().postNotification(
                NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                NotificationUtil.RED,
                context.getString(R.string.connection_error),
                context.getString(R.string.device_profile_failure),
                context.getString(R.string.device_profile_failure));

        try {
            Log.d(TAG, "DeviceProfileRequest finished onRequestDataError: "
                    + request.getRequestType());

            if (request
                    .getRequestType() == DeviceProfileClient.REQUEST_TYPE_GET_PROFILE) {

                // get the initial request that was sent out
                final DeviceProfileRequest initialRequest = (DeviceProfileRequest) request
                        .getParcelable(
                                DeviceProfileOperation.PARAM_PROFILE_REQUEST);

                DeviceProfileClient.getInstance()
                        .callbackRecieved(initialRequest, false);

                onDeviceProfileRequestComplete(false, null);
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception in onRequestDataError!", e);
        }
    }

    @Override
    public void onRequestCustomError(Request request, Bundle resultData) {
        Log.w(TAG, "DeviceProfileRequest Failed - Custom Error");

        NotificationUtil.getInstance().postNotification(
                NotificationUtil.GeneralIcon.NETWORK_ERROR.getID(),
                NotificationUtil.RED,
                context.getString(R.string.connection_error),
                context.getString(R.string.device_profile_failure),
                context.getString(R.string.device_profile_failure));

        try {
            Log.d(TAG, "DeviceProfileRequest finished onRequestCustomError: "
                    + request.getRequestType());

            if (request
                    .getRequestType() == DeviceProfileClient.REQUEST_TYPE_GET_PROFILE) {

                // get the initial request that was sent out
                final DeviceProfileRequest initialRequest = (DeviceProfileRequest) request
                        .getParcelable(
                                DeviceProfileOperation.PARAM_PROFILE_REQUEST);

                DeviceProfileClient.getInstance()
                        .callbackRecieved(initialRequest, false);

                onDeviceProfileRequestComplete(false, null);
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception in onRequestCustomError!", e);
        }
    }
}
