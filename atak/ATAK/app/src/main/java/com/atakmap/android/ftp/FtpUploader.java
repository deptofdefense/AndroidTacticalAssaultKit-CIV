
package com.atakmap.android.ftp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;

import com.atakmap.android.ftp.operation.FtpStoreFileOperation;
import com.atakmap.android.ftp.request.FtpStoreFileRequest;
import com.atakmap.android.http.rest.HTTPRequestManager;
import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.UserCredentials;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.util.NotificationIdRecycler;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.requestmanager.Request;
import com.foxykeep.datadroid.requestmanager.RequestManager;
import com.foxykeep.datadroid.requestmanager.RequestManager.RequestListener;

import java.io.File;

/**
 * FTP upload files. Leverages Android Service to offload async requests
 * 
 * 
 */
public class FtpUploader implements RequestListener {

    protected static final String TAG = "FtpUploader";

    private Context _context;
    private final SharedPreferences _prefs;

    final public static String PREF_lastFtpProto = "lastFtpProto";
    final public static String PREF_lastFtpServer = "lastFtpServer";
    final public static String PREF_lastFtpPort = "lastFtpPort";
    final public static String PREF_lastFtpServerPath = "lastFtpServerPath";
    final public static String PREF_lastFtpUsername = "lastFtpUsername";

    public static final NotificationIdRecycler _notificationId;

    private static final int FTP_UPLOAD_ATTEMPTS = 7;

    static {
        _notificationId = new NotificationIdRecycler(87440, 10);
    }

    public FtpUploader(final Context context, final SharedPreferences prefs) {
        _context = context;
        _prefs = prefs;
    }

    public void dispose() {
        _context = null;
    }

    /**
     * Upload specified file asynchronously
     * @param file the file to be uploaded.
     * @param binaryMode switch to upload the file using binary mode.
     * @param notificationId the notificationId to use when displaying the notification
     * @param skipDialog the dialog display for before the upload starts that gets additonal details.
     * @param callbackAction the intent action to fire when the upload is complete.
     * @param callbackExtra additional details to be part of the finish intent.
     */
    public void upload(final File file, final boolean binaryMode,
            final int notificationId,
            final String passwd, final boolean skipDialog,
            final String callbackAction, final Parcelable callbackExtra) {

        LayoutInflater inflater = LayoutInflater.from(_context);
        View layout = inflater.inflate(R.layout.ftp_network_layout, null);

        final EditText host = layout.findViewById(R.id.ftp_add_host);
        final EditText portText = layout
                .findViewById(R.id.ftp_add_port);
        final EditText serverPath = layout
                .findViewById(R.id.ftp_server_path);
        final EditText username = layout
                .findViewById(R.id.ftp_username);

        // the xml has android:inputType="textPassword" set
        final EditText password = layout
                .findViewById(R.id.ftp_password);

        final RadioButton ftpRadio = layout
                .findViewById(R.id.ftp_proto);
        final RadioButton ftpsRadio = layout
                .findViewById(R.id.ftps_proto);

        //load cached prefs
        String lastFtpServer = _prefs.getString(PREF_lastFtpServer, null);
        if (!FileSystemUtils.isEmpty(lastFtpServer)) {
            host.setText(lastFtpServer);
        }
        String lastFtpPort = _prefs.getString(PREF_lastFtpPort, null);
        if (!FileSystemUtils.isEmpty(lastFtpPort)) {
            portText.setText(lastFtpPort);
        }
        String lastFtpServerPath = _prefs.getString(PREF_lastFtpServerPath,
                null);
        if (!FileSystemUtils.isEmpty(lastFtpServerPath)) {
            serverPath.setText(lastFtpServerPath);
        }
        String lastFtpUsername = _prefs.getString(PREF_lastFtpUsername, null);
        if (!FileSystemUtils.isEmpty(lastFtpUsername)) {
            username.setText(lastFtpUsername);
        }
        String lastFtpProto = _prefs.getString(PREF_lastFtpProto, null);
        if (!FileSystemUtils.isEmpty(lastFtpProto)) {
            if (FtpStoreFileRequest.FTP_PROTO.equals(lastFtpProto)) {
                ftpRadio.setChecked(true);
                ftpsRadio.setChecked(false);
            } else if (FtpStoreFileRequest.FTPS_PROTO.equals(lastFtpProto)) {
                ftpRadio.setChecked(false);
                ftpsRadio.setChecked(true);
            }
        }
        if (!FileSystemUtils.isEmpty(passwd)) {
            password.setText(passwd);
        }

        //see if we can skip the dialog and just send
        if (skipDialog && !FileSystemUtils.isEmpty(lastFtpServer)
                && !FileSystemUtils.isEmpty(lastFtpProto)
                && !FileSystemUtils.isEmpty(passwd)
                && !FileSystemUtils.isEmpty(lastFtpUsername)) {

            int port = 0;
            if (!FileSystemUtils.isEmpty(lastFtpPort)) {
                try {
                    port = Integer.parseInt(lastFtpPort);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse port: " + lastFtpPort, e);
                    port = 0;
                }
            } else {
                Log.w(TAG, "FTP Port not set");
            }

            upload(notificationId, file, lastFtpProto, lastFtpServer, port,
                    lastFtpServerPath,
                    lastFtpUsername, password.getText().toString(), binaryMode,
                    callbackAction, callbackExtra);
            return;
        }

        final AlertDialog.Builder b = new AlertDialog.Builder(_context);
        b.setTitle("Enter FTP Details");
        b.setView(layout);
        b.setPositiveButton("Send", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (FileSystemUtils.isEmpty(host.getText().toString())) {
                    Log.w(TAG, "FTP Host not set");
                    Toast.makeText(_context, "Must enter FTP host",
                            Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                    upload(file, binaryMode, notificationId, null, false,
                            callbackAction, callbackExtra);
                    return;
                }

                int port = 0;
                String portStr = portText.getText().toString();
                if (!FileSystemUtils.isEmpty(portStr)) {
                    try {
                        port = Integer.parseInt(portStr);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Failed to parse port: "
                                + portText.getText().toString(), e);
                        port = 0;
                    }
                } else {
                    Log.w(TAG, "FTP Port not set");
                }

                // determine protocol
                String proto = FtpStoreFileRequest.FTP_PROTO;
                if (ftpRadio.isChecked()) {
                    proto = FtpStoreFileRequest.FTP_PROTO;
                } else if (ftpsRadio.isChecked()) {
                    proto = FtpStoreFileRequest.FTPS_PROTO;
                }

                String hostString = host.getText().toString();
                String usernameString = username.getText().toString();
                String serverPathString = serverPath.getText().toString();

                upload(notificationId, file, proto, hostString, port,
                        serverPathString,
                        usernameString, password.getText().toString(),

                        binaryMode, callbackAction, callbackExtra);

                //store prefs
                if (!FileSystemUtils.isEmpty(proto)) {
                    _prefs.edit().putString(PREF_lastFtpProto, proto).apply();
                }

                if (!FileSystemUtils.isEmpty(hostString)) {
                    _prefs.edit().putString(PREF_lastFtpServer, hostString)
                            .apply();
                }

                if (!FileSystemUtils.isEmpty(portStr))
                    _prefs.edit().putString(PREF_lastFtpPort, portStr).apply();

                if (!FileSystemUtils.isEmpty(serverPathString)) {
                    _prefs.edit()
                            .putString(PREF_lastFtpServerPath, serverPathString)
                            .apply();
                }

                if (!FileSystemUtils.isEmpty(usernameString)) {
                    _prefs.edit()
                            .putString(PREF_lastFtpUsername, usernameString)
                            .apply();
                }

                dialog.dismiss();
            }
        });
        b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    /**
     * Upload specified file asynchronously
     * @param notificationId the notificationId to use when displaying the notification
     * @param file the file to be uploaded. 
     * @param proto the protocol to use {ftp or sftp}
     * @param host the host to use 
     * @param serverPath the server path to use
     * @param user the username to use
     * @param binaryMode switch to upload the file using binary mode.
     * @param callbackAction the intent action to fire when the upload is complete.
     * @param callbackExtra additional details to be part of the finish intent.
     */
    public void upload(int notificationId, File file, String proto,
            String host,
            int port, String serverPath, String user, String passwd,
            boolean binaryMode,
            String callbackAction, Parcelable callbackExtra) {

        if (!FileSystemUtils.isFile(file)) {
            Log.w(TAG, file.getAbsolutePath() + " does not exist");
            NotificationUtil.getInstance().postNotification(notificationId,
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    "Failed to FTP Upload",
                    file.getName() + " does not exist",
                    file.getName() + " does not exist");
            return;
        }

        UserCredentials creds = null;
        if (!FileSystemUtils.isEmpty(user) && !FileSystemUtils.isEmpty(passwd))
            creds = new UserCredentials(user, passwd);
        FtpStoreFileRequest request = new FtpStoreFileRequest(proto, host,
                port, serverPath,
                creds, file.getAbsolutePath(), false, binaryMode,
                (notificationId > 0 ? notificationId
                        : FtpUploader._notificationId.getNotificationId()),
                1,
                callbackAction, callbackExtra);
        upload(request);
    }

    protected void upload(FtpStoreFileRequest request) {

        // Kick off async FTP request to get config from update server
        HTTPRequestManager.from(_context).execute(
                request.createUploadFileRequest(), this);

        Log.d(TAG, "Uploading file to " + request);
        // notify user
        File file = new File(request.getFileToSend());
        NotificationUtil.getInstance().postNotification(
                request.getNotificationId(),
                com.atakmap.android.util.ATAKConstants.getIconId(),
                NotificationUtil.BLUE,
                "Uploading " + request.getProtocol(),
                "Uploading " + file.getName() + " to " + request.getHost()
                        + "...",
                "Uploading " + file.getName() + " to " + request.getHost()
                        + "...");
    }

    @Override
    public void onRequestFinished(Request request, Bundle resultData) {
        Log.d(TAG, "onRequestFinished");

        // FTP response received successfully
        if (request
                .getRequestType() == NetworkOperationManager.REQUEST_TYPE_FTP_UPLOAD) {
            if (resultData == null) {
                Log.e(TAG,
                        "Upload Download Failed - Unable to obtain results");
                NotificationUtil.getInstance().postNotification(
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        "FTP Upload Failed",
                        "FTP server not available",
                        "FTP server not available");
                return;
            }

            // the initial request that was sent out
            final FtpStoreFileRequest initialRequest = resultData
                    .getParcelable(FtpStoreFileOperation.PARAM_FILE);
            if (initialRequest == null || !initialRequest.isValid()) {
                Log.e(TAG, "Update Failed - Unable to parse request");
                NotificationUtil.getInstance().postNotification(
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        "FTP Upload Failed",
                        "Unable to parse request",
                        "Unable to parse request");
                return;
            }

            //check if we need to prompt user prior to overwriting remote file
            File file = new File(initialRequest.getFileToSend());
            if (resultData.getBoolean(
                    FtpStoreFileOperation.PARAM_FILEEXISTS, false)) {
                Log.d(TAG, "Prompting user to overwrite on FTP server: " +
                        initialRequest);
                AlertDialog.Builder b = new AlertDialog.Builder(_context);
                b.setTitle("Confirm FTP Overwrite");
                b.setMessage("Overwrite remote file: "
                        + initialRequest.getServerPath() + "/"
                        + file.getName()
                        + _context.getString(R.string.question_mark_symbol));
                b.setPositiveButton(R.string.yes,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                initialRequest.setOverwrite(true);
                                upload(initialRequest);
                                dialog.dismiss();
                            }
                        });
                b.setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                NotificationUtil
                                        .getInstance()
                                        .clearNotification(
                                                initialRequest
                                                        .getNotificationId());
                                Log.d(TAG,
                                        "Cancelled overwrite on FTP server: "
                                                +
                                                initialRequest);
                                dialog.dismiss();
                            }
                        });
                b.show();
                return;
            }

            String errString = resultData.getString(
                    FtpStoreFileOperation.PARAM_ERRSTRING);
            if (errString != null) {
                NotificationUtil.getInstance().postNotification(
                        initialRequest.getNotificationId(),
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        "FTP Upload Failed: " + file.getName(),
                        errString, errString);

                if (initialRequest.hasCallbackAction()) {
                    Log.d(TAG,
                            "Sending error callback: "
                                    + initialRequest.getCallbackAction());
                    Intent intent = new Intent(
                            initialRequest.getCallbackAction());
                    intent.putExtra("success", false);
                    if (initialRequest.hasCallbackExtra()) {
                        intent.putExtra("callbackExtra",
                                initialRequest.getCallbackExtra());
                    }
                    AtakBroadcast.getInstance().sendBroadcast(intent);
                }
                return;
            }

            Log.d(TAG, initialRequest.getProtocol() + " Upload complete "
                    + file.getAbsolutePath());
            NotificationUtil.getInstance().postNotification(
                    initialRequest.getNotificationId(),
                    com.atakmap.android.util.ATAKConstants.getIconId(),
                    NotificationUtil.GREEN,
                    initialRequest.getProtocol() + " Upload Complete",
                    "Uploaded " + file.getName(),
                    null, true);

            if (initialRequest.hasCallbackAction()) {
                Log.d(TAG,
                        "Sending success callback: "
                                + initialRequest.getCallbackAction());
                Intent intent = new Intent(
                        initialRequest.getCallbackAction());
                intent.putExtra("success", true);
                if (initialRequest.hasCallbackExtra()) {
                    intent.putExtra("callbackExtra",
                            initialRequest.getCallbackExtra());
                }
                AtakBroadcast.getInstance().sendBroadcast(intent);
            }
        }
    }

    @Override
    public void onRequestConnectionError(Request request,
            RequestManager.ConnectionError ce) {
        // Upon error, retry to download the file

        String detail = NetworkOperation.getErrorMessage(ce);
        Log.w(TAG, detail);

        // Get request data
        FtpStoreFileRequest fileRequest = (FtpStoreFileRequest) request
                .getParcelable(FtpStoreFileOperation.PARAM_FILE);
        if (fileRequest == null) {
            Log.e(TAG,
                    "FTP Upload Failed - Connection Data Error - Unable to serialize retry request");
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    "FTP Upload Failed",
                    "Unable to serialize retry request: " + detail,
                    "Unable to serialize retry request: " + detail);
            return;
        }

        if (!fileRequest.isValid()) {
            Log.e(TAG,
                    "FTP Upload Failed - Connection Data Error - Unable to serialize invalid retry request");
            NotificationUtil.getInstance().postNotification(
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    "FTP Upload Failed",
                    "Unable to serialize invalid retry request: " + detail,
                    "Unable to serialize invalid retry request: " + detail);
            return;
        }

        // see if already hit max retries
        File file = new File(fileRequest.getFileToSend());
        int currentAttempts = fileRequest.getRetryCount();
        if (currentAttempts >= FTP_UPLOAD_ATTEMPTS) {
            Log.e(TAG, "FTP Upload Failed - Connection Data Error - "
                    + currentAttempts
                    + " Retry attempts failed for file: "
                    + fileRequest);

            NotificationUtil.getInstance().postNotification(
                    fileRequest.getNotificationId(),
                    R.drawable.ic_network_error_notification_icon,
                    NotificationUtil.RED,
                    "FTP Upload Failed",
                    file.getName() + " failed after " + currentAttempts
                            + " attempts: " + detail,
                    file.getName() + " failed after " + currentAttempts
                            + " attempts: " + detail);

            if (fileRequest.hasCallbackAction()) {
                Log.d(TAG,
                        "Sending error callback: "
                                + fileRequest.getCallbackAction());
                Intent intent = new Intent(
                        fileRequest.getCallbackAction());
                intent.putExtra("success", false);
                if (fileRequest.hasCallbackExtra()) {
                    intent.putExtra("callbackExtra",
                            fileRequest.getCallbackExtra());
                }
                AtakBroadcast.getInstance().sendBroadcast(intent);
            }
            return;
        }

        // update notification
        NotificationUtil.getInstance().postNotification(
                fileRequest.getNotificationId(),
                R.drawable.ic_network_error_notification_icon,
                NotificationUtil.RED,
                "FTP Upload Failing",
                file.getName() + " has failed " + currentAttempts + " of "
                        + FTP_UPLOAD_ATTEMPTS + " attempts: " + detail,
                file.getName() + " has failed " + currentAttempts + " of "
                        + FTP_UPLOAD_ATTEMPTS + " attempts: " + detail);

        // attempt upload again
        fileRequest.setRetryCount(currentAttempts + 1);
        HTTPRequestManager.from(_context).execute(
                fileRequest.createUploadFileRequest(), this);
        Log.d(TAG, "FTP Upload attempt #" + (currentAttempts + 1)
                + " created for: "
                + fileRequest);
    }

    @Override
    public void onRequestDataError(Request request) {
        Log.e(TAG, "Download Failed - Request Data Error");
        NotificationUtil.getInstance().postNotification(
                R.drawable.ic_network_error_notification_icon,
                NotificationUtil.RED,
                "FTP Upload Failed",
                "Unable to upload file",
                "Unable to upload file");
    }

    @Override
    public void onRequestCustomError(Request request, Bundle resultData) {
        Log.e(TAG, "Download Failed - Request Custom Error");
        NotificationUtil.getInstance().postNotification(
                R.drawable.ic_network_error_notification_icon,
                NotificationUtil.RED,
                "FTP Upload Failed",
                "Unable to upload file",
                "Unable to upload file");
    }
}
