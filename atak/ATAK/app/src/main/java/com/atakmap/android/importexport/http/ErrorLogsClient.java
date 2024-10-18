
package com.atakmap.android.importexport.http;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.atakmap.android.http.rest.HTTPRequestManager;
import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.android.importexport.http.rest.PostErrorLogsOperation;
import com.atakmap.android.importexport.http.rest.PostErrorLogsRequest;
import com.atakmap.android.util.ServerListDialog;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.requestmanager.Request;
import com.foxykeep.datadroid.requestmanager.RequestManager;

import java.io.File;

/**
 *
 */
public class ErrorLogsClient implements RequestManager.RequestListener {

    protected static final String TAG = "ErrorLogsClient";

    /**
     * Core class members
     */
    private final Context _context;
    private int curNotificationId = 89000;

    public final static int REQUEST_TYPE_POST_ERROR_LOGS;

    static {
        REQUEST_TYPE_POST_ERROR_LOGS = NetworkOperationManager
                .register(
                        "com.atakmap.android.importexport.http.rest.PostErrorLogsOperation",
                        new PostErrorLogsOperation());
    }

    /**
     * ctor
     */
    public ErrorLogsClient(Context context) {
        _context = context;
    }

    public void dispose() {
    }

    public void sendLogsToServer(File logz, String server, boolean background,
            String uid,
            String callsign) {

        String versionName = _context.getString(R.string.app_name);
        String versionCode = "1";
        try {
            PackageInfo pInfo = _context.getPackageManager().getPackageInfo(
                    _context.getPackageName(), 0);
            versionName += " v" + pInfo.versionName;
            versionCode = Integer.toString(pInfo.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to determine version name", e);
        }

        post(
                logz,
                ServerListDialog.getBaseUrl(server),
                background,
                uid,
                callsign,
                versionName,
                versionCode);
    }

    public void post(File errorFile, String serverUrl, boolean background,
            String uid,
            String callsign,
            String versionName, String versionCode) {
        try {
            PostErrorLogsRequest request = new PostErrorLogsRequest(serverUrl,
                    background,
                    errorFile.getAbsolutePath(), uid, callsign, versionName,
                    versionCode, curNotificationId++);
            if (request == null || !request.isValid()) {
                Log.w(TAG, "Cannot post without valid request");
                return;
            }

            // notify user
            Log.d(TAG,
                    "Error log post request created for: "
                            + request);

            if (!background) {
                NotificationUtil
                        .getInstance()
                        .postNotification(
                                request.getNotificationId(),
                                R.drawable.sync_export,
                                NotificationUtil.BLUE,
                                _context.getString(
                                        R.string.importmgr_error_log_post_started),
                                String.format(
                                        _context.getString(
                                                R.string.importmgr_sending_to),
                                        request.getBaseUrl()),
                                String.format(
                                        _context.getString(
                                                R.string.importmgr_sending_to),
                                        request.getBaseUrl()));
            }

            // Kick off async HTTP request to post to server
            HTTPRequestManager.from(_context).execute(
                    request.createPostErrorLogsRequest(), this);
        } catch (Exception e) {
            Log.e(TAG, "Exception in post!", e);
        }
    }

    @Override
    public void onRequestFinished(Request request, Bundle resultData) {

        try {
            // HTTP response received successfully
            if (request
                    .getRequestType() == ErrorLogsClient.REQUEST_TYPE_POST_ERROR_LOGS) {

                if (resultData == null) {
                    Log.e(TAG,
                            "Error Logs Post Failed - Unable to obtain results");
                    NotificationUtil
                            .getInstance()
                            .postNotification(
                                    R.drawable.ic_network_error_notification_icon,
                                    NotificationUtil.RED,
                                    _context.getString(
                                            R.string.importmgr_error_logs_post_failed),
                                    _context.getString(
                                            R.string.unable_to_obtain_results),
                                    _context.getString(
                                            R.string.unable_to_obtain_results));
                    return;
                }

                // the initial request that was sent out
                PostErrorLogsRequest initialRequest = resultData
                        .getParcelable(PostErrorLogsOperation.PARAM_REQUEST);
                if (initialRequest == null || !initialRequest.isValid()) {
                    // TODO fatal error?
                    Log.e(TAG,
                            "Error Logs Post Failed - Unable to parse request");
                    NotificationUtil
                            .getInstance()
                            .postNotification(
                                    R.drawable.ic_network_error_notification_icon,
                                    NotificationUtil.RED,
                                    _context.getString(
                                            R.string.importmgr_error_logs_post_failed),
                                    _context.getString(
                                            R.string.unable_to_parse_request),
                                    _context.getString(
                                            R.string.unable_to_parse_request));
                    return;
                }

                if (!initialRequest.getBackground()) {
                    AlertDialog.Builder b = new AlertDialog.Builder(_context);
                    b.setTitle(R.string.post_error_logs);
                    b.setIcon(R.drawable.sync_export);
                    b.setMessage(_context.getString(
                            R.string.importmgr_error_log_successfully_sent_to,
                            initialRequest.getBaseUrl()));
                    b.setPositiveButton(R.string.ok, null);
                    b.show();
                }

                NotificationUtil.getInstance().clearNotification(
                        initialRequest.getNotificationId());
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception in OnRequestFinished!", e);
        }
    }

    @Override
    public void onRequestConnectionError(Request request,
            RequestManager.ConnectionError ce) {
        String detail = NetworkOperation.getErrorMessage(ce);
        Log.e(TAG, "Error Logs Operation Failed - Connection Error: " + detail);
        String error = _context
                .getString(R.string.importmgr_error_log_post_failed);
        //TODO use request notification ID to update the existing notification
        NotificationUtil.getInstance().postNotification(
                R.drawable.ic_network_error_notification_icon,
                NotificationUtil.RED,
                error,
                detail,
                detail);
    }

    @Override
    public void onRequestDataError(Request request) {
        Log.e(TAG, "Error Logs Operation Failed - Data Error");
        String error = _context
                .getString(R.string.importmgr_error_log_post_failed);
        NotificationUtil
                .getInstance()
                .postNotification(
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        error,
                        _context.getString(
                                R.string.importmgr_error_logs_connection_error),
                        _context.getString(
                                R.string.importmgr_error_logs_connection_error));
    }

    @Override
    public void onRequestCustomError(Request request, Bundle resultData) {
        Log.e(TAG, "Error Logs Operation Failed - Custom Error");
        String error = _context
                .getString(R.string.importmgr_error_log_post_failed);
        NotificationUtil
                .getInstance()
                .postNotification(
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        error,
                        _context.getString(
                                R.string.importmgr_error_logs_connection_error),
                        _context.getString(
                                R.string.importmgr_error_logs_connection_error));
    }
}
