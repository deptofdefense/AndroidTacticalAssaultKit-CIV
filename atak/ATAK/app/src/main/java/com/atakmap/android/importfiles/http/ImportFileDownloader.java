
package com.atakmap.android.importfiles.http;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.filesystem.ResourceFile;
import com.atakmap.android.filesystem.ResourceFile.MIMEType;
import com.atakmap.android.http.rest.NetworkOperationManager;
import com.atakmap.android.http.rest.operation.GetFileOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.importfiles.resource.RemoteResource;
import com.atakmap.android.importfiles.task.ImportFileTask;
import com.atakmap.android.importfiles.task.ImportRemoteFileTask;
import com.atakmap.app.R;
import com.atakmap.comms.NetConnectString;
import com.atakmap.comms.TAKServer;
import com.atakmap.comms.TAKServerListener;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpResponse;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.file.SpatialDbContentSource;
import com.atakmap.util.zip.IoUtils;
import com.foxykeep.datadroid.requestmanager.Request;
import com.foxykeep.datadroid.requestmanager.RequestManager;

import com.atakmap.android.http.rest.request.GetFileRequest;

import com.atakmap.net.AtakAuthenticationHandlerHTTP;

import org.apache.http.client.methods.HttpGet;

import java.net.HttpURLConnection;
import java.net.URI;
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
public class ImportFileDownloader extends NetworkLinkDownloader {

    protected static final String TAG = "ImportFileDownloader";

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
        super(context, 83000);
        _flags = flags;
    }

    /**
     * Download specified file asynchronously
     * 
     * @param resource the resource to be downloaded
     */
    @Override
    public void download(final RemoteResource resource,
            boolean showNotifications) {
        // Note the type specific import sorters will ensure a file has the proper extension
        int notificationId = getNotificationId();
        RemoteResourceRequest request;
        String path = resource.getLocalPath();
        if (!FileSystemUtils.isEmpty(path)) {
            File f = new File(path);
            request = new RemoteResourceRequest(resource, f.getName(),
                    f.getParent(), notificationId, showNotifications);
        } else
            request = new RemoteResourceRequest(
                    resource, FileSystemUtils
                            .getItem(FileSystemUtils.TMP_DIRECTORY)
                            .getAbsolutePath(),
                    notificationId, showNotifications);

        // notify user
        Log.d(TAG,
                "Import File download request created for: "
                        + request);

        if (ImportFileTask.checkFlag(_flags,
                ImportRemoteFileTask.FlagNotifyUserSuccess)) {
            postNotification(request, R.drawable.download_remote_file,
                    getString(R.string.importmgr_remote_file_download_started),
                    getString(R.string.importmgr_downloading_url,
                            request.getUrl()));
        }

        // Kick off async HTTP request to get file
        ndl(request);
    }

    public void ndl(final RemoteResourceRequest r) {
        Thread t = new Thread() {
            public void run() {
                Log.d(TAG, "start download... ");
                final String urlStr = r.getUrl();
                _downloading.add(urlStr);

                try {
                    InputStream input;
                    GetFileRequest request = r;
                    String fileName = request.getFileName();

                    URI uri = new URI(urlStr);
                    String host = uri.getHost();
                    String contentType = null;

                    boolean hostIsTakServer = false;

                    TAKServer[] servers = TAKServerListener.getInstance()
                            .getConnectedServers();
                    if (servers != null) {
                        for (TAKServer server : servers) {
                            NetConnectString netConnectString = NetConnectString
                                    .fromString(
                                            server.getConnectString());
                            if (netConnectString.getHost()
                                    .equalsIgnoreCase(host)) {
                                hostIsTakServer = true;
                                break;
                            }
                        }
                    }

                    if (hostIsTakServer &&
                            urlStr.toLowerCase(LocaleUtil.getCurrent())
                                    .startsWith("https")
                            &&
                            uri.getPort() == 8443) {

                        String baseUrl = "https://" + uri.getHost() + ":"
                                + uri.getPort();
                        TakHttpClient client = TakHttpClient
                                .GetHttpClient(baseUrl);
                        HttpGet httpget = new HttpGet(urlStr);
                        TakHttpResponse response = client.execute(httpget);

                        input = response.getEntity().getContent();
                        contentType = response.getContentType();

                    } else {
                        URL url = new URL(urlStr);
                        URLConnection conn = url.openConnection();
                        conn.setRequestProperty("User-Agent", "TAK");
                        conn.setUseCaches(true);
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(30000);

                        // support authenticated connections
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

                        contentType = conn.getHeaderField("Content-Type");

                    }

                    // Add extension based on response content type
                    if (!FileSystemUtils.isEmpty(contentType)) {
                        MIMEType mt = ResourceFile
                                .getMIMETypeForMIME(contentType);
                        if (mt != null && !fileName.endsWith("." + mt.EXT))
                            fileName += "." + mt.EXT;
                    }

                    File fout = new File(request.getDir(), fileName);

                    try (FileOutputStream fos = IOProviderFactory
                            .getOutputStream(fout)) {
                        FileSystemUtils.copy(input, fos);
                        Log.d(TAG, "success: " + fileName);
                    } catch (Exception e) {
                        Log.d(TAG, "failure: " + fileName);
                        onRequestConnectionError(new Request(
                                NetworkOperationManager.REQUEST_TYPE_GET_FILE),
                                new RequestManager.ConnectionError(900,
                                        "unable to write download"));
                    } finally {
                        IoUtils.close(input);
                    }
                    Bundle b = new Bundle();
                    b.putParcelable(GetFileOperation.PARAM_GETFILE, request);
                    b.putString("path", fout.getAbsolutePath());
                    onRequestFinished(new Request(
                            NetworkOperationManager.REQUEST_TYPE_GET_FILE), b);

                } catch (Exception e) {
                    Log.e(TAG, "error encountered", e);
                    onRequestConnectionError(new Request(
                            NetworkOperationManager.REQUEST_TYPE_GET_FILE),
                            new RequestManager.ConnectionError(-1,
                                    "unable to download network source"));

                }
                Log.d(TAG, "end download... ");
                _downloading.remove(urlStr);
            }
        };
        t.start();
    }

    @Override
    public void onRequestFinished(Request request, Bundle resultData) {

        // HTTP response received successfully
        if (request
                .getRequestType() == NetworkOperationManager.REQUEST_TYPE_GET_FILE) {
            if (resultData == null) {
                Log.e(TAG,
                        "File Transfer Download Failed - Unable to obtain results");
                postNotification(SpatialDbContentSource.getNotificationId(),
                        R.drawable.ic_network_error_notification_icon,
                        R.string.importmgr_remote_file_download_failed,
                        R.string.importmgr_unable_to_obtain_results);
                return;
            }

            // the initial request that was sent out
            RemoteResourceRequest initialRequest = resultData
                    .getParcelable(GetFileOperation.PARAM_GETFILE);
            if (initialRequest == null || !initialRequest.isValid()) {
                Log.e(TAG,
                        "File Transfer Download Failed - Unable to parse request");
                postNotification(SpatialDbContentSource.getNotificationId(),
                        R.drawable.ic_network_error_notification_icon,
                        R.string.importmgr_remote_file_download_failed,
                        R.string.importmgr_unable_to_parse_request);
                return;
            }

            File downloadedFile = new File(resultData.getString("path"));
            if (!FileSystemUtils.isFile(downloadedFile)) {
                Log.e(TAG,
                        "Remote File Download Failed - Failed to create local file");
                postNotification(initialRequest,
                        R.drawable.ic_network_error_notification_icon,
                        getString(
                                R.string.importmgr_remote_file_download_failed),
                        getString(
                                R.string.importmgr_failed_to_create_local_file));
                return;
            }

            Log.d(TAG, "Preparing to sort: " + initialRequest);

            // async task to sort file to proper location
            ImportRemoteFileTask task = new ImportRemoteFileTask(_context,
                    initialRequest.getResource(),
                    initialRequest.getNotificationId());
            task.addFlag(ImportFileTask.FlagSkipDeleteOnMD5Match | _flags);
            if (!initialRequest.showNotifications()) {
                task.removeFlag(
                        ImportFileTask.FlagShowNotificationsDuringImport);
                task.removeFlag(ImportRemoteFileTask.FlagNotifyUserSuccess);
            }
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
        postNotification(SpatialDbContentSource.getNotificationId(),
                R.drawable.ic_network_error_notification_icon,
                getString(R.string.importmgr_remote_file_download_failed),
                getString(R.string.importmgr_check_your_url, detail));
    }

    @Override
    public void onRequestDataError(Request request) {
        Log.e(TAG, "File Transfer Download Failed - Request Data Error");
        postNotification(SpatialDbContentSource.getNotificationId(),
                R.drawable.ic_network_error_notification_icon,
                R.string.importmgr_remote_file_download_failed,
                R.string.importmgr_request_data_error);
    }

    @Override
    public void onRequestCustomError(Request request, Bundle resultData) {
        Log.e(TAG, "File Transfer Download Failed - Request Custom Error");
        postNotification(SpatialDbContentSource.getNotificationId(),
                R.drawable.ic_network_error_notification_icon,
                R.string.importmgr_remote_file_download_failed,
                R.string.importmgr_request_custom_error);
    }
}
