
package com.atakmap.android.update.http;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import com.atakmap.android.http.rest.DownloadProgressTracker;
import com.atakmap.android.http.rest.operation.HTTPOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.update.AppMgmtUtils;
import com.atakmap.android.update.ProductProviderManager;
import com.atakmap.android.update.RemoteProductProvider;
import com.atakmap.comms.http.HttpUtil;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpException;
import com.atakmap.comms.http.TakHttpResponse;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * REST Operation to GET a file much like <code>GetFileOperation</code>, but also 
 * display progress during download. Supports INFZ (compressed repo index cache), and legacy
 * INF (CSV file)
 * Also checks for HTTP 401 & 403 (unauthorized/forbidden)
 * Also ignores all SSL validation errors
 *
 * NOTE: Currently only used via AsyncTask, not via HTTPRequestService
 * 
 * 
 */
public final class GetRepoIndexOperation extends HTTPOperation {
    private static final String TAG = "GetRepoIndexOperation";

    public static final String PARAM_GETINFZFILE = "GetRepoIndexOperation.PARAM_GETINFZFILE";
    public static final String PARAM_GETCREDENTIALS = "GetRepoIndexOperation.PARAM_GETCREDENTIALS";

    private enum MODE {
        INF,
        INFZ
    }

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException,
            DataException {

        final RepoIndexRequest repoIndexRequest = (RepoIndexRequest) request
                .getParcelable(GetRepoIndexOperation.PARAM_GETINFZFILE);

        if (repoIndexRequest != null) {
            // Get request data
            return GetFile(
                    context,
                    TakHttpClient.GetHttpClient(repoIndexRequest.getUrl()),
                    repoIndexRequest,
                    0, 1, true, null);
        }
        return null;
    }

    /**
     * Perform the HTTP Get, and display progress during download
     *
     * @param httpclient
     * @param fileRequest
     * @return
     * @throws DataException, ConnectionException
     */
    public static Bundle GetFile(Context context, TakHttpClient httpclient,
            RepoIndexRequest fileRequest,
            int progressCurrent, int progressTotal, boolean bShutdownClient,
            ProductProviderManager.ProgressDialogListener l)
            throws DataException, ConnectionException {
        if (fileRequest == null) {
            throw new DataException("Unable to serialize import file request");
        }

        if (!fileRequest.isValid()) {
            throw new DataException(
                    "Unable to serialize invalid import file request");
        }

        //see if the URL already contains ..../product.inf
        MODE mode;
        String url = fileRequest.getUrl().trim();
        String compare = url.toLowerCase(Locale.US);
        if (compare.endsWith(MODE.INF.toString().toLowerCase(Locale.US))) {
            mode = MODE.INF;
        } else if (compare
                .endsWith(MODE.INFZ.toString().toLowerCase(Locale.US))) {
            mode = MODE.INFZ;
        } else {
            mode = MODE.INFZ;
            if (!url.endsWith("/")) {
                url += "/";
            }
            url += AppMgmtUtils.REPOZ_INDEX_FILENAME;
        }
        Log.d(TAG, "mode=" + mode);

        //update local filename
        if (mode == MODE.INF) {
            fileRequest.setFileName(AppMgmtUtils.REPO_INDEX_FILENAME);
        } else {
            fileRequest.setFileName(AppMgmtUtils.REPOZ_INDEX_FILENAME);
        }

        try {
            // now start timer
            long startTime = System.currentTimeMillis();

            // ///////
            // Get file
            // ///////
            Log.d(TAG, "sending GET File request to: " + url);

            // ///////
            // Send HTTP Head to get size first
            // ///////
            HttpHead httphead = new HttpHead(url);
            if (fileRequest.hasCredentials())
                HttpUtil.AddBasicAuthentication(httphead,
                        fileRequest.getCredentials());

            Log.d(TAG, "executing head " + httphead.getRequestLine());
            TakHttpResponse response = httpclient.execute(httphead, false);

            //we need to gather credentials
            if (response.isStatus(HttpStatus.SC_UNAUTHORIZED) ||
                    response.isStatus(HttpStatus.SC_FORBIDDEN)) {
                Log.d(TAG, "Not authorized: " + response.toString());
                Bundle output = new Bundle();
                output.putParcelable(PARAM_GETINFZFILE, fileRequest);
                output.putBoolean(PARAM_GETCREDENTIALS, true);
                output.putInt(NetworkOperation.PARAM_STATUSCODE,
                        response.getStatusCode());
                return output;
            }

            response.verifyOk();

            long contentLength = response.getContentLength();
            if (contentLength < 0) {
                Log.d(TAG,
                        "Content-Length not available: "
                                + httphead.getRequestLine());
            }

            File contentFile = new File(fileRequest.getDir(),
                    fileRequest.getFileName());
            if (contentFile.exists()) {
                //For now we just re-download since we don't currently have a way to get the HASH
                //of the server side file
                Log.d(TAG,
                        "Overwriting file: "
                                + contentFile.getAbsolutePath()
                                + " of size: "
                                + contentFile.length()
                                + " with new size: "
                                + contentLength);
            }

            //go head and download the file
            HttpGet httpget = new HttpGet(url);
            if (fileRequest.hasCredentials())
                HttpUtil.AddBasicAuthentication(httpget,
                        fileRequest.getCredentials());

            HttpEntity resEntity = response.getEntity();
            try {
                if (resEntity != null)
                    resEntity.consumeContent();
            } catch (IOException ioe) {
                Log.d(TAG, "error closing resEntity");
            }

            //we've already attached creds above
            response = httpclient.execute(httpget, false);
            resEntity = response.getEntity();

            Log.d(TAG, "processing response");

            // check response for HTTP 200
            //we need to gather credentials
            if (response.isStatus(HttpStatus.SC_UNAUTHORIZED) ||
                    response.isStatus(HttpStatus.SC_FORBIDDEN)) {
                Log.d(TAG, "Not authorized: " + response.toString());
                Bundle output = new Bundle();
                output.putParcelable(PARAM_GETINFZFILE, fileRequest);
                output.putBoolean(PARAM_GETCREDENTIALS, true);
                output.putInt(NetworkOperation.PARAM_STATUSCODE,
                        response.getStatusCode());
                return output;
            }

            response.verifyOk();

            // setup progress notifications
            NotificationManager notifyManager = null;
            if (l == null) {
                notifyManager = (NotificationManager) context
                        .getSystemService(Context.NOTIFICATION_SERVICE);
            }
            Notification.Builder builder;
            if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder = new Notification.Builder(context);
            } else {
                builder = new Notification.Builder(context,
                        "com.atakmap.app.def");
            }

            builder.setContentTitle(
                    "Downloading TAK Package Repo")
                    .setContentText("Downloading " + fileRequest.getFileName())
                    .setSmallIcon(
                            com.atakmap.android.util.ATAKConstants.getIconId());

            // now stream bytes over network into file
            DownloadProgressTracker progressTracker = new DownloadProgressTracker(
                    contentLength);
            int len;

            byte[] buf = new byte[8192];
            if (resEntity != null) {

                FileOutputStream fos = null;
                InputStream in = null;
                try {
                    fos = new FileOutputStream(contentFile);
                    in = resEntity.getContent();
                    while ((len = in.read(buf)) > 0) {
                        fos.write(buf, 0, len);

                        // see if we should update progress notification
                        // based on progress or time since last update

                        long currentTime = System.currentTimeMillis();
                        if (progressTracker.contentReceived(len, currentTime)) {
                            // do we know the content length?
                            if (contentLength > 0) {
                                // compute progress scaled to larger download set
                                int statusProgress = (100 * progressCurrent)
                                        / progressTotal
                                        + progressTracker.getCurrentProgress()
                                                / progressTotal;
                                String message = String
                                        .format(LocaleUtil.getCurrent(),
                                                "Downloading (%d%% of %s)  %s, %s remaining. %s ",
                                                progressTracker
                                                        .getCurrentProgress(),
                                                MathUtils
                                                        .GetLengthString(
                                                                contentLength),
                                                MathUtils
                                                        .GetDownloadSpeedString(
                                                                progressTracker
                                                                        .getAverageSpeed()),
                                                MathUtils
                                                        .GetTimeRemainingString(
                                                                progressTracker
                                                                        .getTimeRemaining()),
                                                (mode == MODE.INFZ
                                                        ? "Repo Cache"
                                                        : "Repo Index"));
                                builder.setProgress(100, statusProgress, false);
                                builder.setContentText(message);
                                if (!fileRequest.isSilent()) {
                                    if (l != null) {
                                        l.update(
                                                new ProductProviderManager.ProgressDialogUpdate(
                                                        statusProgress,
                                                        message));
                                    } else {
                                        if (notifyManager != null) {
                                            notifyManager
                                                    .notify(
                                                            fileRequest
                                                                    .getNotificationId(),
                                                            builder.build());
                                        }
                                    }
                                }
                                Log.d(TAG, message + ", overall progress="
                                        + statusProgress);
                                progressTracker.notified(currentTime);
                            } else {
                                // we cannot progress report if we don't know the total size...
                                String message = String
                                        .format(LocaleUtil.getCurrent(),
                                                "Downloading %d of %d, %s downloaded so far...",
                                                (progressCurrent + 1),
                                                progressTotal,
                                                MathUtils
                                                        .GetLengthString(
                                                                progressTracker
                                                                        .getCurrentLength()));
                                builder.setProgress(100, 100, true);
                                builder.setContentText(message);
                                if (!fileRequest.isSilent()) {
                                    if (l != null) {
                                        l.update(
                                                new ProductProviderManager.ProgressDialogUpdate(
                                                        0, message));
                                    } else {
                                        if (notifyManager != null) {
                                            notifyManager
                                                    .notify(
                                                            fileRequest
                                                                    .getNotificationId(),
                                                            builder.build());
                                        }
                                    }
                                }
                                Log.d(TAG, message + ", progress unknown...");
                                progressTracker.notified(currentTime);
                            }
                        }
                    }
                } finally {
                    try {
                        if (resEntity != null)
                            resEntity.consumeContent();
                    } catch (IOException ioe) {
                        Log.d(TAG, "error closing resEntity");
                    }
                    if (in != null)
                        in.close();
                    if (fos != null)
                        fos.close();
                }
                Log.d(TAG, "Content request complete");
            } else {
                // no-op still need to logout
                Log.w(TAG, "Response Entity is empty");
            }

            // Now verify we got download correctly
            if (!contentFile.exists()) {
                contentFile.delete();
                throw new ConnectionException("Failed to download data");
            }

            long downloadSize = contentFile.length();
            long stopTime = System.currentTimeMillis();

            Log.d(TAG, String.format(LocaleUtil.getCurrent(),
                    "File Request %s downloaded %d bytes in %f seconds",
                    fileRequest.toString(), downloadSize,
                    (stopTime - startTime) / 1000F));

            if (mode == MODE.INFZ) {
                Log.d(TAG, "Extracting compressed repo index cache");
                if (!fileRequest.isSilent()) {
                    if (l != null) {
                        l.update(
                                new ProductProviderManager.ProgressDialogUpdate(
                                        99, "Extracting repo cache"));
                    } else {
                        if (notifyManager != null) {
                            notifyManager.notify(
                                    fileRequest.getNotificationId(),
                                    builder.build());
                        }
                    }
                }

                //now extract downloaded file
                if (!RemoteProductProvider.extract(contentFile)) {
                    Log.w(TAG, "Cannot extract and rebuild repo: "
                            + contentFile.getAbsolutePath());
                    throw new ConnectionException(
                            "Failed to extract repo index");
                }
            }

            Bundle output = new Bundle();
            output.putParcelable(PARAM_GETINFZFILE, fileRequest);
            output.putInt(NetworkOperation.PARAM_STATUSCODE,
                    response.getStatusCode());
            return output;
        } catch (TakHttpException e) {
            Log.e(TAG, "Failed to download file: " + fileRequest.getUrl(), e);
            throw new ConnectionException(e.getMessage(), e.getStatusCode());
        } catch (Exception e) {
            Log.e(TAG, "Failed to download File: " + fileRequest.getUrl(), e);
            throw new ConnectionException(e.getMessage(),
                    NetworkOperation.STATUSCODE_UNKNOWN);
        } finally {
            try {
                if (bShutdownClient && httpclient != null)
                    httpclient.shutdown();
            } catch (Exception ignore) {
            }
        }
    }
}
