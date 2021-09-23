
package com.atakmap.android.missionpackage.http.rest;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.atakmap.android.filesharing.android.service.AndroidFileInfo;
import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper;
import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper.TABLETYPE;
import com.atakmap.android.filesystem.MIMETypeMapper;
import com.atakmap.android.http.rest.DownloadProgressTracker;
import com.atakmap.android.http.rest.operation.HTTPOperation;
import com.atakmap.android.http.rest.operation.NetworkOperation;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.MissionPackageUtils;
import com.atakmap.android.missionpackage.file.MissionPackageConfiguration;
import com.atakmap.android.missionpackage.file.MissionPackageExtractorFactory;
import com.atakmap.android.missionpackage.file.MissionPackageFileIO;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.util.ServerListDialog;
import com.atakmap.app.R;
import com.atakmap.comms.SslNetCotPort;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpResponse;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * REST Operation to GET an ATAK Mission Package Delay operation if previously failed Files(s) are
 * written to file system (rename if already exists) CoT events in Mission Package are sent via
 * Intent to FileShareReceiver
 * 
 * 
 */
public final class GetFileTransferOperation extends HTTPOperation {
    private static final String TAG = "GetFileTransferOperation";

    /**
     * Hack! Since DataDroid does not re-parcel the request upon error, rather it uses the instance
     * cached by HTTPRequestManager, this operation is unable to modify the metadata associated with
     * the request upon error (failed download). Therefore we use a special status code to indicate
     * to the listener that some progress was made, but still hit an error. The request is
     * re-parceled upon success
     */
    public static final int PROGRESS_MADE_STATUS_CODE = 9999;

    public static final String PARAM_GETFILE = GetFileTransferOperation.class
            .getName()
            + ".PARAM_GETFILE";
    public static final String PARAM_MISSION_PACKAGE_MANIFEST = GetFileTransferOperation.class
            .getName()
            + ".PARAM_MISSION_PACKAGE_MANIFEST";

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException,
            DataException {

        // Get request data
        FileTransferRequest fileRequest = (FileTransferRequest) request
                .getParcelable(GetFileTransferOperation.PARAM_GETFILE);
        if (fileRequest == null) {
            throw new DataException(
                    "Unable to serialize file transfer request");
        }

        if (!fileRequest.isValid()) {
            throw new DataException(
                    "Unable to serialize invalid file transfer request");
        }

        // setup progress notifications
        String sender = MissionPackageReceiver.getSender(fileRequest
                .getFileTransfer());
        String tickerFilename = MissionPackageUtils.abbreviateFilename(
                fileRequest.getFileTransfer().getName(), 20);
        NotificationManager notifyManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);

        Notification.Builder builder;
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context);
        } else {
            builder = new Notification.Builder(context, "com.atakmap.app.def");
        }

        builder.setContentTitle(
                context.getString(R.string.mission_package_download))
                .setContentText(context.getString(
                        R.string.mission_package_sending,
                        sender, tickerFilename))
                .setSmallIcon(R.drawable.missionpackage_sent);

        TakHttpClient client = null;
        int statusCode = NetworkOperation.STATUSCODE_UNKNOWN;
        DownloadProgressTracker progressTracker = null;
        try {
            // delay this operation if it has previously failed
            fileRequest.getDelay().delay();

            // now start timer
            long startTime = System.currentTimeMillis();
            //            long transferTimeoutMS = fileRequest.getTransferTimeoutMS();
            //            long connectionTimeoutMS = fileRequest.getConnectionTimeoutMS();

            // Create temp file
            boolean bRestart = false;
            long existingLength = 0;
            File temp = new File(
                    MissionPackageFileIO.getMissionPackageIncomingDownloadPath(
                            FileSystemUtils.getRoot().getAbsolutePath()),
                    fileRequest.getFileTransfer().getUID());
            if (fileRequest.getRetryCount() > 1) {
                // this is a retry, lets see if we pick up where previous attempt left off
                if (IOProviderFactory.exists(temp)
                        && IOProviderFactory.length(temp) > 0
                        && IOProviderFactory.canWrite(temp)
                        && IOProviderFactory.length(temp) < fileRequest
                                .getFileTransfer()
                                .getSize()) {
                    existingLength = IOProviderFactory.length(temp);
                    bRestart = true;
                    Log.d(TAG, "Restarting download: "
                            + fileRequest.getFileTransfer().getName()
                            + " after byte: " + existingLength);
                } else {
                    FileSystemUtils.deleteFile(temp);
                    existingLength = 0;
                }
            }

            // ///////
            // Get file
            // ///////
            String connectString = fileRequest.getFileTransfer()
                    .getConnectString();

            String getUrl = ServerListDialog.getBaseUrl(connectString);
            boolean bSecure = getUrl.toLowerCase(LocaleUtil.getCurrent())
                    .startsWith(
                            "https");
            getUrl += SslNetCotPort
                    .getServerApiPath(bSecure ? SslNetCotPort.Type.SECURE
                            : SslNetCotPort.Type.UNSECURE);
            getUrl += "/sync/content?hash="
                    + fileRequest.getFileTransfer().getSHA256(false);

            if (bRestart) {
                Uri.Builder uribuilder = Uri.parse(getUrl).buildUpon();
                uribuilder.appendQueryParameter("offset",
                        String.valueOf(existingLength));
                getUrl = uribuilder.build().toString();
            }
            //try { 
            //   getUrl = URLEncoder.encode(getUrl, FileSystemUtils.UTF8_CHARSET);
            //} catch (UnsupportedEncodingException uee) { 
            //   Log.d(TAG, "unable to encode: " + getUrl);
            //}
            getUrl = getUrl.replaceAll(" ", "%20");

            //Note mission packages are Zipped, no need for GZip
            Log.d(TAG, "Sending File Transfer request to: " + getUrl);

            ///  XXXXX follow me for progress??
            client = TakHttpClient.GetHttpClient(getUrl, connectString);

            HttpGet httpget = new HttpGet(getUrl);
            TakHttpResponse response = client.execute(httpget, false);
            statusCode = response.getStatusCode();
            if (response.isStatus(HttpStatus.SC_UNAUTHORIZED)) {
                //add pre-auth and try again
                Log.d(TAG, "Unauthorized, adding http basic headers ");
                response = client.execute(httpget, true);
                statusCode = response.getStatusCode();
            }

            Log.d(TAG, "processing response");
            HttpEntity resEntity = response.getEntity();
            response.verifyOk();

            // open up for writing
            // stream in content, keep user notified on progress
            builder.setProgress(100, 1, false);
            if (notifyManager != null)
                notifyManager.notify(fileRequest.getNotificationId(),
                        builder.build());

            int len;
            byte[] buf = new byte[8192];
            progressTracker = new DownloadProgressTracker(fileRequest
                    .getFileTransfer().getSize());
            // if this is a restart, update initial content length
            progressTracker.setCurrentLength((bRestart ? existingLength : 0));

            try (OutputStream fos = IOProviderFactory.getOutputStream(temp,
                    bRestart);
                    InputStream in = resEntity.getContent()) {
                while ((len = in.read(buf)) > 0) {
                    fos.write(buf, 0, len);

                    // see if we should update progress notification based on progress or time since
                    // last update
                    long currentTime = System.currentTimeMillis();
                    if (progressTracker.contentReceived(len, currentTime)) {
                        String message = String
                                .format(LocaleUtil.getCurrent(),
                                        context.getString(
                                                R.string.mission_package_sending2),
                                        sender,
                                        tickerFilename,
                                        progressTracker
                                                .getCurrentProgress(),
                                        MathUtils.GetLengthString(fileRequest
                                                .getFileTransfer().getSize()),
                                        MathUtils
                                                .GetDownloadSpeedString(
                                                        progressTracker
                                                                .getAverageSpeed()),
                                        MathUtils
                                                .GetTimeRemainingString(
                                                        progressTracker
                                                                .getTimeRemaining()));
                        builder.setProgress(100,
                                progressTracker.getCurrentProgress(), false);
                        builder.setContentText(message);
                        if (notifyManager != null)
                            notifyManager
                                    .notify(fileRequest.getNotificationId(),
                                            builder.build());
                        Log.d(TAG, message);
                        // start a new block
                        progressTracker.notified(currentTime);
                    }
                } // end read loop
            }

            // Now verify we got download correctly
            if (!FileSystemUtils.isFile(temp)) {
                progressTracker.error();
                FileSystemUtils.deleteFile(temp);
                throw new ConnectionException("Failed to download data");
            }
            if (!fileRequest.getFileTransfer().verify(temp)) {
                progressTracker.error();
                FileSystemUtils.deleteFile(temp);
                throw new ConnectionException("Size or MD5 mismatch");
            }

            long downloadSize = IOProviderFactory.length(temp);
            Log.d(TAG, "File Transfer downloaded and verified");

            // update notification
            String message = context.getString(
                    R.string.mission_package_processing,
                    tickerFilename, sender);
            builder.setProgress(100, 99, false);
            builder.setContentText(message);
            if (notifyManager != null)
                notifyManager.notify(fileRequest.getNotificationId(),
                        builder.build());

            // pull import instructions from manifest
            MissionPackageManifest manifest = MissionPackageExtractorFactory
                    .GetManifest(temp);
            if (manifest == null || !manifest.isValid()) {
                throw new Exception("Unable to extract package manifest");
            }

            MissionPackageConfiguration.ImportInstructions inst = manifest
                    .getConfiguration().getImportInstructions();
            Log.d(TAG,
                    "Processing: " + getUrl + " with instructions: "
                            + inst.toString());
            switch (inst) {
                case ImportDelete: {
                    //extract, import, delete and return updated/localized manifest
                    manifest = MissionPackageExtractorFactory
                            .Extract(context, temp, FileSystemUtils.getRoot(),
                                    true);
                    Log.d(TAG,
                            "Auto imported/deleted Package: "
                                    + temp.getAbsolutePath());
                    FileSystemUtils.deleteFile(temp);
                }
                    break;
                case ImportNoDelete: {
                    //extract, import, update DB, and return updated/localized manifest
                    manifest = MissionPackageExtractorFactory
                            .Extract(context, temp, FileSystemUtils.getRoot(),
                                    true);
                    if (manifest == null)
                        throw new ConnectionException(
                                "Failed to extract "
                                        + context
                                                .getString(
                                                        R.string.mission_package_name));

                    // now we are unzipped and processed, add db entry
                    // create DB entry so Directory Watcher will ignore, then update after file write is
                    // check for name clash and rename file before creating .zip...
                    // complete. Rename .zip as necessary, but we do not update name in
                    // contents/manifest
                    // b/c then MD5 won't match the sender's. For consistency we do not update DB
                    // "user label" column
                    String path = MissionPackageFileIO
                            .getMissionPackagePath(FileSystemUtils.getRoot()
                                    .getAbsolutePath());
                    String filename = MissionPackageUtils.getUniqueFilename(
                            path, fileRequest.getFileTransfer().getName());
                    if (!filename.equals(fileRequest.getFileTransfer()
                            .getName()
                            + ".zip")) {
                        // TODO or would we rather overwrite if name/UID match?
                        Log.d(TAG,
                                "Name "
                                        + fileRequest.getFileTransfer()
                                                .getName()
                                        + " is already taken, renaming incoming Package file: "
                                        + filename);
                    }

                    File savedMissionPackage = new File(path, filename);
                    FileInfoPersistanceHelper db = FileInfoPersistanceHelper
                            .instance();
                    AndroidFileInfo fileInfo = db.getFileInfoFromFilename(
                            savedMissionPackage,
                            TABLETYPE.SAVED);
                    if (fileInfo == null) {
                        // dont create SHA256 until after we write out file
                        fileInfo = new AndroidFileInfo("",
                                savedMissionPackage,
                                MIMETypeMapper
                                        .GetContentType(savedMissionPackage),
                                manifest.toXml(true));
                        db.insertOrReplace(fileInfo, TABLETYPE.SAVED);
                    }

                    // move (or copy) file to permanent location
                    if (!FileSystemUtils.renameTo(temp, savedMissionPackage)) {
                        progressTracker.error();
                        throw new ConnectionException(
                                "Failed to save "
                                        + context
                                                .getString(
                                                        R.string.mission_package_name)
                                        + " Error 1");
                    }

                    // now update DB entry with label, sender, size, MD5, etc so user can view, resend
                    // etc via UI
                    if (!FileSystemUtils.isFile(savedMissionPackage)) {
                        progressTracker.error();
                        throw new ConnectionException(
                                "Failed to save "
                                        + context
                                                .getString(
                                                        R.string.mission_package_name)
                                        + " Error 2");
                    }

                    // TODO what if zip failed, but .zip existed from a previous compression task? may
                    // need to check retVal?
                    // TODO ensure at least one file was successfully zipped...
                    fileInfo.setUserName(fileRequest.getFileTransfer()
                            .getSenderCallsign());
                    fileInfo.setUserLabel(fileRequest.getFileTransfer()
                            .getName());
                    // TODO is this checked dynamically or cached when File is created?
                    fileInfo.setSizeInBytes((int) IOProviderFactory
                            .length(savedMissionPackage));

                    fileInfo.setUpdateTime(IOProviderFactory
                            .lastModified(savedMissionPackage));

                    // file size and hash was verified above, so lets use that rather than re-compute
                    String sha256 = fileRequest.getFileTransfer().getSHA256(
                            false);

                    if (FileSystemUtils.isEmpty(sha256)) {
                        Log.w(TAG, "Recomputing SHA256...");
                        fileInfo.computeSha256sum();
                    } else {
                        fileInfo.setSha256sum(sha256);
                    }

                    db.update(fileInfo, TABLETYPE.SAVED);
                    manifest.setPath(savedMissionPackage.getAbsolutePath());

                    Log.d(TAG,
                            "Received Package has been saved: "
                                    + savedMissionPackage.getAbsolutePath());
                }
                    break;
                case NoImportDelete: {
                    //extract, do not import, delete, and return updated/localized manifest
                    Log.d(TAG,
                            "Auto (no import) deleted Package: "
                                    + temp.getAbsolutePath());
                    manifest = MissionPackageExtractorFactory
                            .Extract(context, temp, FileSystemUtils.getRoot(),
                                    false);
                    FileSystemUtils.deleteFile(temp);
                }
                    break;
                case NoImportNoDelete: {
                    //extract, do not import, do not delete, and return updated/localized manifest
                    Log.d(TAG,
                            "Did not import or delete Package: "
                                    + temp.getAbsolutePath());
                    manifest = MissionPackageExtractorFactory
                            .Extract(context, temp, FileSystemUtils.getRoot(),
                                    false);
                }
                    break;
            }

            long stopTime = System.currentTimeMillis();
            if (bRestart)
                Log.d(TAG,
                        String.format(
                                LocaleUtil.getCurrent(),
                                "File Transfer restart processed %d of %d bytes in %f seconds",
                                (downloadSize - existingLength), downloadSize,
                                (stopTime - startTime) / 1000F));
            else
                Log.d(TAG, String.format(LocaleUtil.getCurrent(),
                        "File Transfer processed %d bytes in %f seconds",
                        downloadSize, (stopTime - startTime) / 1000F));

            Bundle output = new Bundle();
            output.putParcelable(PARAM_GETFILE, fileRequest);
            output.putInt(NetworkOperation.PARAM_STATUSCODE, statusCode);
            if (manifest != null)
                output.putParcelable(PARAM_MISSION_PACKAGE_MANIFEST, manifest);
            return output;
        } catch (Exception e) {
            boolean bProgress = progressTracker != null
                    && progressTracker.isProgressMade();
            Log.e(TAG, "Failed to download File Transfer, progress made="
                    + bProgress, e);

            if (bProgress)
                throw new ConnectionException(e.getMessage(),
                        PROGRESS_MADE_STATUS_CODE);
            else
                throw new ConnectionException(e.getMessage(), statusCode);
        } finally {
            try {
                if (client != null)
                    client.shutdown();
            } catch (Exception ignore) {
            }
        }
    }
}
