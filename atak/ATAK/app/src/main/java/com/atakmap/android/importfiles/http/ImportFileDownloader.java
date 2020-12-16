
package com.atakmap.android.importfiles.http;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.operation.GetFileOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.importfiles.resource.RemoteResource;
import com.atakmap.android.importfiles.task.ImportFileTask;
import com.atakmap.android.importfiles.task.ImportRemoteFileTask;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.SpatialDbContentSource;
import com.foxykeep.datadroid.requestmanager.Request;
import com.foxykeep.datadroid.requestmanager.RequestManager;
import com.foxykeep.datadroid.requestmanager.RequestManager.RequestListener;

import com.atakmap.android.http.rest.request.GetFileRequest;

import com.atakmap.net.AtakAuthenticationHandlerHTTP;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.io.InputStream;
import java.io.FileOutputStream;

import java.io.File;

/**
 * HTTP download support for file import. Leverages Android Service to offload async HTTP requests
 * 
 * 
 */
public class ImportFileDownloader implements RequestListener {

    protected static final String TAG = "ImportFileDownloader";

    /**
     * Core class members
     */
    private final Context _context;

    private int curNotificationId = 83000;

    /**
     * See <code>ImportFileTask</code>
     */
    private final int _flags;

    /**
     * ctor
     * 
     * @param context the context to be used during the download process
     * @param flags
     */
    public ImportFileDownloader(final Context context, final int flags) {
        _context = context;
        _flags = flags;
    }

    public synchronized int getNotificationId() {
        return curNotificationId++;
    }

    /**
     * Download specified file asynchronously
     * 
     * @param resource the resource to be downloaded
     */
    public void download(final RemoteResource resource) {

        download(resource, getNotificationId());
    }

    public void download(final RemoteResource resource,
            final int notificationId) {

        // Note the type specific import sorters will ensure a file has the proper extension
        RemoteResourceRequest request = new RemoteResourceRequest(
                resource, FileSystemUtils
                        .getItem(FileSystemUtils.TMP_DIRECTORY)
                        .getAbsolutePath(),
                notificationId);

        // notify user
        Log.d(TAG,
                "Import File download request created for: "
                        + request.toString());

        if (ImportFileTask.checkFlag(_flags,
                ImportRemoteFileTask.FlagNotifyUserSuccess)) {
            NotificationUtil
                    .getInstance()
                    .postNotification(
                            request.getNotificationId(),
                            R.drawable.download_remote_file,
                            NotificationUtil.BLUE,
                            _context.getString(
                                    R.string.importmgr_remote_file_download_started),
                            String.format(
                                    _context.getString(
                                            R.string.importmgr_downloading_url),
                                    request.getUrl()),
                            String.format(
                                    _context.getString(
                                            R.string.importmgr_downloading_url),
                                    request.getUrl()));
        }

        // Kick off async HTTP request to get file
        ndl(request);
    }

    public void ndl(final RemoteResourceRequest r) {
        Thread t = new Thread() {
            public void run() {
                Log.d(TAG, "start download... ");
                try {
                    GetFileRequest request = r;

                    URL url = new URL(request.getUrl());
                    URLConnection conn = url.openConnection();
                    conn.setRequestProperty("User-Agent", "TAK");
                    conn.setUseCaches(true);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(30000);

                    // support authenticated connections
                    InputStream input;
                    if (conn instanceof HttpURLConnection) {
                        AtakAuthenticationHandlerHTTP.Connection connection;
                        connection = AtakAuthenticationHandlerHTTP
                                .makeAuthenticatedConnection(
                                        (HttpURLConnection) conn, 3);
                        conn = connection.conn;
                        input = connection.stream;
                    } else {
                        conn.connect();
                        input = conn.getInputStream();
                    }

                    File fout = new File(request.getDir(),
                            request.getFileName());
                    FileOutputStream fos = IOProviderFactory
                            .getOutputStream(fout);

                    try {
                        FileSystemUtils.copy(input, fos);
                        Log.d(TAG, "success: " + request.getFileName());
                    } catch (Exception e) {
                        Log.d(TAG, "failure: " + request.getFileName());
                        onRequestConnectionError(new Request(
                                NetworkOperationManager.REQUEST_TYPE_GET_FILE),
                                new RequestManager.ConnectionError(900,
                                        "unable to write download"));
                    }

                    Bundle b = new Bundle();
                    b.putParcelable(GetFileOperation.PARAM_GETFILE, request);
                    onRequestFinished(new Request(
                            NetworkOperationManager.REQUEST_TYPE_GET_FILE), b);

                } catch (Exception e) {
                    e.printStackTrace();
                    Log.d(TAG, "error", e);
                    onRequestConnectionError(new Request(
                            NetworkOperationManager.REQUEST_TYPE_GET_FILE),
                            new RequestManager.ConnectionError(-1,
                                    "unable to download network source"));

                }
                Log.d(TAG, "end download... ");
            }
        };
        t.start();

        //HTTPRequestManager.from(_context).execute(
        //        request.createGetFileRequests(), this);

    }

    @Override
    public void onRequestFinished(Request request, Bundle resultData) {

        // HTTP response received successfully
        if (request
                .getRequestType() == NetworkOperationManager.REQUEST_TYPE_GET_FILE) {
            if (resultData == null) {
                Log.e(TAG,
                        "File Transfer Download Failed - Unable to obtain results");
                NotificationUtil
                        .getInstance()
                        .postNotification(
                                SpatialDbContentSource.getNotificationId(),
                                R.drawable.ic_network_error_notification_icon,
                                NotificationUtil.RED,
                                _context.getString(
                                        R.string.importmgr_remote_file_download_failed),
                                _context.getString(
                                        R.string.importmgr_unable_to_obtain_results),
                                _context.getString(
                                        R.string.importmgr_unable_to_obtain_results));
                return;
            }

            // the initial request that was sent out
            RemoteResourceRequest initialRequest = resultData
                    .getParcelable(GetFileOperation.PARAM_GETFILE);
            if (initialRequest == null || !initialRequest.isValid()) {
                Log.e(TAG,
                        "File Transfer Download Failed - Unable to parse request");
                NotificationUtil
                        .getInstance()
                        .postNotification(
                                SpatialDbContentSource.getNotificationId(),
                                R.drawable.ic_network_error_notification_icon,
                                NotificationUtil.RED,
                                _context.getString(
                                        R.string.importmgr_remote_file_download_failed),
                                _context.getString(
                                        R.string.importmgr_unable_to_parse_request),
                                _context.getString(
                                        R.string.importmgr_unable_to_parse_request));
                return;
            }

            File downloadedFile = new File(initialRequest.getDir(),
                    initialRequest.getFileName());
            if (!FileSystemUtils.isFile(downloadedFile)) {
                Log.e(TAG,
                        "Remote File Download Failed - Failed to create local file");
                NotificationUtil
                        .getInstance()
                        .postNotification(
                                SpatialDbContentSource.getNotificationId(),
                                R.drawable.ic_network_error_notification_icon,
                                NotificationUtil.RED,
                                _context.getString(
                                        R.string.importmgr_remote_file_download_failed),
                                _context.getString(
                                        R.string.importmgr_failed_to_create_local_file),
                                _context.getString(
                                        R.string.importmgr_failed_to_create_local_file));
                return;
            }

            Log.d(TAG, "Preparing to sort: " + initialRequest.toString());

            // async task to sort file to proper location
            ImportRemoteFileTask task = new ImportRemoteFileTask(_context,
                    initialRequest.getResource(),
                    initialRequest.getNotificationId());
            task.addFlag(ImportFileTask.FlagSkipDeleteOnMD5Match
                    | ImportFileTask.FlagKMZStrictMode | _flags);
            task.execute(downloadedFile.getAbsolutePath());
        } else {
            Log.w(TAG,
                    "Unhandled request response: " + request.getRequestType());
        }
    }

    @Override
    public void onRequestConnectionError(Request request,
            RequestManager.ConnectionError ce) {
        String detail = NetworkOperation.getErrorMessage(ce);
        Log.e(TAG, detail);

        Log.e(TAG, "File Transfer Download Failed - Request Connection Error");
        NotificationUtil
                .getInstance()
                .postNotification(
                        SpatialDbContentSource.getNotificationId(),
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        _context.getString(
                                R.string.importmgr_remote_file_download_failed),
                        String.format(
                                _context.getString(
                                        R.string.importmgr_check_your_url),
                                detail),
                        String.format(
                                _context.getString(
                                        R.string.importmgr_check_your_url),
                                detail));
    }

    @Override
    public void onRequestDataError(Request request) {
        Log.e(TAG, "File Transfer Download Failed - Request Data Error");
        NotificationUtil
                .getInstance()
                .postNotification(
                        SpatialDbContentSource.getNotificationId(),
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        _context.getString(
                                R.string.importmgr_remote_file_download_failed),
                        _context.getString(
                                R.string.importmgr_request_data_error),
                        _context.getString(
                                R.string.importmgr_request_data_error));
    }

    @Override
    public void onRequestCustomError(Request request, Bundle resultData) {
        Log.e(TAG, "File Transfer Download Failed - Request Custom Error");
        NotificationUtil
                .getInstance()
                .postNotification(
                        SpatialDbContentSource.getNotificationId(),
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        _context.getString(
                                R.string.importmgr_remote_file_download_failed),
                        _context.getString(
                                R.string.importmgr_request_custom_error),
                        _context.getString(
                                R.string.importmgr_request_custom_error));
    }

    public void shutdown() {
        // TODO what to do? Shutdown RequestService? cancel any ongoing downloads?
    }
}
