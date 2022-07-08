
package com.atakmap.android.ftp.operation;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import com.atakmap.android.ftp.request.FtpStoreFileRequest;
import com.atakmap.android.http.rest.DownloadProgressTracker;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.math.MathUtils;
import com.atakmap.commoncommo.CommoException;
import com.atakmap.comms.CommsFileTransferListener;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import com.atakmap.coremap.locale.LocaleUtil;

/**
 * FTP Operation to upload a file
 * 
 * 
 */
public final class FtpStoreFileOperation extends NetworkOperation {
    private static final String TAG = "FtpStoreFileOperation";

    public static final String PARAM_FILE = FtpStoreFileOperation.class
            .getName() + ".FILE";
    public static final String PARAM_FILEEXISTS = FtpStoreFileOperation.class
            .getName() + ".FILEEXISTS";
    public static final String PARAM_ERRSTRING = FtpStoreFileOperation.class
            .getName() + ".ERRSTRING";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException {

        FtpStoreFileRequest fileUploadRequest = null;
        try {
            // Get upload data
            fileUploadRequest = (FtpStoreFileRequest) request
                    .getParcelable(FtpStoreFileOperation.PARAM_FILE);
            if (fileUploadRequest == null || !fileUploadRequest.isValid()) {
                throw new DataException("Unable to serialize file data");
            }

            File file = new File(fileUploadRequest.getFileToSend());
            if (!IOProviderFactory.exists(file)) {
                throw new DataException(file.getName() + " does not exist");
            }

            // delay this operation if it has previously failed
            fileUploadRequest.getDelay().delay();

            long startTime = System.currentTimeMillis();

            // setup progress notifications
            NotificationManager notifyManager = (NotificationManager) context
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            Notification.Builder builder;

            if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder = new Notification.Builder(context);
            } else {
                builder = new Notification.Builder(context,
                        "com.atakmap.app.def");
            }

            builder.setContentTitle("Uploading FTP")
                    .setContentText(
                            "Uploading " + file.getName() + " to "
                                    + fileUploadRequest.getHost() + "...")
                    .setSmallIcon(
                            com.atakmap.android.util.ATAKConstants.getIconId());

            String protoScheme;
            byte[] caCert = null;
            String caCertPassword = null;
            if (FtpStoreFileRequest.FTPS_PROTO.equals(fileUploadRequest
                    .getProtocol())) {
                Log.d(TAG,
                        "Connecting FTPS to: " + fileUploadRequest +
                                ", retry count= "
                                + fileUploadRequest.getRetryCount());
                //TODO, don't trust all certs...
                // make caCert point to some real pkcs#12 truststore and
                // caCertPassword to its password
                protoScheme = "ftps";
            } else {
                Log.d(TAG, "Connecting FTP to: " + fileUploadRequest
                        +
                        ", retry count= " + fileUploadRequest.getRetryCount());
                protoScheme = "ftp";
            }

            CommsFileTransferListener listener = new NotificationStreamListener(
                    fileUploadRequest.getNotificationId(),
                    IOProviderFactory.length(file),
                    notifyManager, builder);
            int port = fileUploadRequest.getPort();
            if (port <= 0)
                port = -1;

            String user = null;
            String pass = null;
            if (fileUploadRequest.hasCredentials()) {
                user = fileUploadRequest.getCredentials().getUser();
                pass = fileUploadRequest.getCredentials().getPasswd();
            }

            String path = "/";
            if (!FileSystemUtils.isEmpty(fileUploadRequest.getServerPath())) {
                if (fileUploadRequest.getServerPath().contains("\\")) {
                    Log.i(TAG, "Fixing up forward slashes");
                    fileUploadRequest.setServerPath(fileUploadRequest
                            .getServerPath().replace('\\', '/'));
                }

                path += fileUploadRequest.getServerPath() + "/";
            }

            path += file.getName();

            String host = fileUploadRequest.getHost();

            URI uri = new URI(protoScheme, null, host, port, path, null, null);

            // caCert is always null
            CommsMapComponent.getInstance().syncFileTransfer(listener, true,
                    uri, caCert, caCertPassword, user, pass, file);

            long stopTime = System.currentTimeMillis();
            Log.d(TAG, String.format(LocaleUtil.getCurrent(),
                    "File size %f KB sent in %f seconds",
                    IOProviderFactory.length(file) / 1024F,
                    (stopTime - startTime) / 1000F));

            Bundle bundle = new Bundle();
            bundle.putParcelable(PARAM_FILE, fileUploadRequest);
            bundle.putInt(NetworkOperation.PARAM_STATUSCODE, 200);
            return bundle;

        } catch (CommoException e) {
            Bundle bundle = new Bundle();
            bundle.putParcelable(PARAM_FILE, fileUploadRequest);
            bundle.putString(PARAM_ERRSTRING,
                    "Failed to setup transfer (" + e.getMessage() + ")");
            bundle.putInt(NetworkOperation.PARAM_STATUSCODE, 500);
            return bundle;

        } catch (IOException e) {
            Bundle bundle = new Bundle();
            bundle.putParcelable(PARAM_FILE, fileUploadRequest);
            bundle.putString(PARAM_ERRSTRING, e.getMessage());
            bundle.putInt(NetworkOperation.PARAM_STATUSCODE, 500);
            return bundle;

        } catch (Exception e) {
            Log.e(TAG, "Failed to send file", e);
            throw new ConnectionException(e.getMessage(),
                    NetworkOperation.STATUSCODE_UNKNOWN);
        }
    }

    private static class NotificationStreamListener implements
            CommsFileTransferListener {

        private final int notificationId;
        private final long totalLength;
        private long lastTransferCount;
        private final DownloadProgressTracker progressTracker;
        private final NotificationManager notifyManager;
        private final Builder builder;

        NotificationStreamListener(int notificationId, long contentLength,
                NotificationManager notifyManager, Builder builder) {
            this.notificationId = notificationId;
            this.totalLength = contentLength;
            this.progressTracker = new DownloadProgressTracker(totalLength);
            this.notifyManager = notifyManager;
            this.builder = builder;
            lastTransferCount = 0;
        }

        @Override
        public void bytesTransferred(long totalBytesTransferred,
                long streamSize) {
            if (totalBytesTransferred == 0)
                // Still unknown/not started
                return;

            long bytesTransferredEx = totalBytesTransferred - lastTransferCount;
            lastTransferCount = totalBytesTransferred;
            if (bytesTransferredEx > Integer.MAX_VALUE)
                bytesTransferredEx = Integer.MAX_VALUE;
            int bytesTransferred = (int) bytesTransferredEx;

            // see if we should update progress notification based on progress or time since
            // last update
            long currentTime = System.currentTimeMillis();
            if (progressTracker.contentReceived(bytesTransferred,
                    currentTime)) {
                // compute progress scaled to larger download set
                String message = String.format(LocaleUtil.getCurrent(),
                        "Uploading (%d%% of %s) %s, %s remaining",
                        progressTracker.getCurrentProgress(),
                        MathUtils.GetLengthString(totalLength),
                        MathUtils.GetDownloadSpeedString(progressTracker
                                .getAverageSpeed()),
                        MathUtils.GetTimeRemainingString(progressTracker
                                .getTimeRemaining()));
                builder.setProgress(100, progressTracker.getCurrentProgress(),
                        false);
                builder.setContentText(message);
                notifyManager.notify(notificationId,
                        builder.build());
                Log.d(TAG, message);
                progressTracker.notified(currentTime);
            }
        }
    }
}
