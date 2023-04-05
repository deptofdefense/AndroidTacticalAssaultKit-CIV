
package com.atakmap.android.missionpackage.file.task;

import android.content.Context;
import android.widget.Toast;

import com.atakmap.android.contact.Contact;
import com.atakmap.android.filesharing.android.service.AndroidFileInfo;
import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper;
import com.atakmap.android.filesharing.android.service.FileTransferLog;
import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper.TABLETYPE;
import com.atakmap.android.filesystem.MIMETypeMapper;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.MissionPackageUtils;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.app.R;
import com.atakmap.commoncommo.CommoException;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.missionpackage.MPSendListener;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.foxykeep.datadroid.exception.ConnectionException;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownServiceException;
import java.util.UUID;

/**
 * Async task to copy to specified location, and send via the Contact List
 * 
 */
public class CopyAndSendTask extends MissionPackageBaseTask {
    private static final String TAG = "CopyAndSendTask";

    private final String _deviceCallsign;
    private final Context _context;
    private File _destination;
    private AndroidFileInfo _fileInfo;
    private final Contact[] _netContacts;

    private static final int minProg = 20, maxProg = 95;

    public CopyAndSendTask(MissionPackageManifest contents,
            Contact[] netContacts, MissionPackageReceiver receiver,
            Callback callback) {
        super(contents, receiver, true, callback);
        MapView mapView = _receiver.getMapView();
        _deviceCallsign = mapView.getDeviceCallsign();
        _context = mapView.getContext();
        _netContacts = netContacts;
        _destination = null;
        _fileInfo = null;

    }

    @Override
    public String getProgressDialogMessage() {
        return getContext().getString(R.string.mission_package_deploying,
                _manifest.getName());
    }

    @Override
    protected Boolean doInBackground(Void... arg0) {
        // work to be performed by background thread
        Thread.currentThread().setName("CopyAndSendTask");

        Log.d(TAG, "Executing: " + this);

        File source = new File(_manifest.getPath());
        if (!FileSystemUtils.isFile(source)) {
            cancel("Cannot create "
                    + (_context == null ? " package"
                            : _context.getString(
                                    R.string.mission_package_name))
                    + " with empty file");
            return false;
        }

        // copy to private directory in the "transfer" folder
        File parent = new File(_receiver.getComponent().getFileIO()
                .getMissionPackageTransferPath(), UUID.randomUUID().toString());
        if (!IOProviderFactory.exists(parent)) {
            if (!IOProviderFactory.mkdirs(parent)) {
                Log.d(TAG, "Failed to make dir at " + parent.getAbsolutePath());
            }
        }

        _destination = new File(parent, source.getName());
        Log.d(TAG, "Deploying Package: " + source.getAbsolutePath() +
                " to " + _destination.getAbsolutePath());

        // create DB entry so Directory Watcher will ignore, then update after file write is
        // complete
        FileInfoPersistanceHelper db = FileInfoPersistanceHelper.instance();
        AndroidFileInfo fileInfo = db.getFileInfoFromFilename(_destination,
                TABLETYPE.TRANSFER);
        boolean recomputeHash = false;
        if (fileInfo == null) {
            // dont create Md5 until after we write out file
            fileInfo = new AndroidFileInfo("", _destination,
                    MIMETypeMapper.GetContentType(_destination),
                    _manifest.toXml(true));
            // File hash may have changed during re-serialization
            // i.e. MANIFEST directory/file added where original is missing
            recomputeHash = true;
            if (db.insertOrReplace(fileInfo, TABLETYPE.TRANSFER)) {
                // re-pull to get ID
                fileInfo = db.getFileInfoFromFilename(_destination,
                        TABLETYPE.TRANSFER);

                if (fileInfo == null) {
                    cancel("Failed to get file info from destination file "
                            + _destination.getName());
                    return false;
                }
            } else {
                cancel("Failed to store "
                        + (_context == null ? " package"
                                : _context.getString(
                                        R.string.mission_package_name))
                        + " in database: "
                        + _manifest.getName());
                return false;
            }
        }

        // now copy to deploy directory
        try (InputStream is = IOProviderFactory.getInputStream(source);
                OutputStream os = IOProviderFactory
                        .getOutputStream(_destination)) {
            FileSystemUtils.copyStream(is, os);
        } catch (Exception e) {
            Log.w(TAG, "Failed to deploy (1) to: " + _destination, e);
            cancel("Failed to deploy "
                    + (_context == null ? " package"
                            : _context.getString(
                                    R.string.mission_package_name))
                    + " (CODE=1): " + _manifest.getName());
            return false;
        }

        // now that file was written out, set additional data
        if (!FileSystemUtils.isFile(_destination)) {
            Log.w(TAG, "Failed to deploy (2) to: " + _destination);
            cancel("Failed to deploy "
                    + (_context == null ? " package"
                            : _context.getString(
                                    R.string.mission_package_name))
                    + " (CODE=2): " + _manifest.getName());
            return false;
        }

        fileInfo.setUserName(_deviceCallsign);
        fileInfo.setUserLabel(_manifest.getName());
        fileInfo.setSizeInBytes((int) IOProviderFactory.length(_destination));
        fileInfo.setUpdateTime(IOProviderFactory.lastModified(_destination));

        // attempt to use existing file hash (for file we copied above), only recompute if necessary
        String sha256 = null;
        if (!recomputeHash) {
            AndroidFileInfo savedFileInfo = db.getFileInfoFromFilename(source,
                    TABLETYPE.SAVED);
            if (savedFileInfo != null)
                sha256 = savedFileInfo.sha256sum();
        }

        if (FileSystemUtils.isEmpty(sha256)) {
            Log.w(TAG, "Recomputing SHA256...");
            sha256 = fileInfo.computeSha256sum();
            if (FileSystemUtils.isEmpty(sha256)) {
                Log.w(TAG, "Failed to compute sha256 for package delivery to: "
                        + _destination);
                cancel("Failed to compute sha256 for "
                        + (_context == null ? " package"
                                : _context.getString(
                                        R.string.mission_package_name))
                        + " (CODE=3)  " + _manifest.getName());
                return false;
            }

        } else {
            fileInfo.setSha256sum(sha256);
        }

        db.update(fileInfo, TABLETYPE.TRANSFER);
        Log.d(TAG, "Package deployed: " + _destination);

        // ensure ID (required for remote HTTP request to the web server)
        if (fileInfo.id() < 0) {
            fileInfo = db.getFileInfoFromFilename(_destination,
                    TABLETYPE.TRANSFER);
            if (fileInfo == null || fileInfo.id() < 0) {
                cancel("Failed to retrieve "
                        + (_context == null ? " package"
                                : _context
                                        .getString(
                                                R.string.mission_package_name))
                        + " from database: "
                        + _manifest.getName());
                return false;
            }
        }

        _fileInfo = fileInfo;

        String[] uids = new String[0];
        if (!FileSystemUtils.isEmpty(_netContacts)) {
            uids = new String[_netContacts.length];
            int idx = 0;
            for (Contact c : _netContacts) {
                uids[idx++] = c.getUID();
            }
        }
        CommsMPSendListener sendListener = new CommsMPSendListener(this,
                sha256.hashCode());

        try {
            CommsMapComponent.getInstance().sendMissionPackage(uids,
                    fileInfo.file(), fileInfo.file().getName(),
                    _manifest.getName(), sendListener);
        } catch (Exception ex) {
            cancel("Failed to send "
                    + (_context == null ? " package"
                            : _context
                                    .getString(R.string.mission_package_name))
                    + " to target recipients "
                    + ex.getMessage() != null ? ("(" + ex.getMessage() + ")")
                            : "");
            return false;
        }

        while (!isCancelled() && !sendListener.waitForUploadComplete()) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }
        }

        boolean started = sendListener.hasUploadPhaseCompleted();
        if (started)
            publishProgress(
                    new ProgressDialogUpdate(maxProg, _context.getString(
                            R.string.mission_package_notifying_receivers)));

        return started;

    }

    @Override
    protected void onPostExecute(Boolean result) {
        // work to be performed by UI thread after work is complete
        Log.d(TAG, "onPostExecute");

        if (result && !isCancelled()) {
            if (!FileSystemUtils.isFile(_manifest.getPath())) {
                Log.e(TAG, "Failed to deploy Package: " + _manifest.getPath());
                Toast.makeText(getContext(), getContext().getString(
                        R.string.mission_package_failed_to_deploy,
                        getContext().getString(R.string.mission_package_name),
                        _manifest.getPath()), Toast.LENGTH_LONG).show();
            }
        }

        // close the progress dialog
        if (_progressDialog != null) {
            _progressDialog.dismiss();
            _progressDialog = null;
        }

        if (_callback != null)
            _callback.onMissionPackageTaskComplete(this, result);
    }

    public static String postPackage(String serverNetConnectString,
            String hash, String name,
            File postfile) throws ConnectionException {
        // hash's hashcode as notifierid per prior generation code
        CommsMPSendListener sendListener = new CommsMPSendListener(
                hash.hashCode(), name, IOProviderFactory.length(postfile));

        try {
            CommsMapComponent.getInstance().sendMissionPackage(
                    serverNetConnectString,
                    postfile, name, sendListener);
        } catch (CommoException ex) {
            throw new ConnectionException(ex.getMessage());
        } catch (UnknownServiceException ex) {
            throw new ConnectionException(
                    "Unknown TAK server connection specified");
        }

        while (!sendListener.waitForUploadComplete()) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }
        }

        String url = sendListener.getPostedUrl();
        if (url == null)
            // failed to upload
            throw new ConnectionException(sendListener.getUploadErrDetail());

        return url;
    }

    private static class CommsMPSendListener implements MPSendListener {

        private final int notifierId;
        private final Context context;
        private final String transferName;
        private final String tickerFilename;
        private final long fileSize;
        private String postedUrl;
        private String uploadErrorDetail;

        // non-null for sends to contacts controlled by an outer task
        // null for direct to server uploads
        private final CopyAndSendTask task;

        private String progressUid;
        private boolean uploadComplete;
        private boolean uploadResult;
        private boolean postedUploadNotification;

        // For sends to contacts
        public CommsMPSendListener(CopyAndSendTask task, int notifierId) {
            this.task = task;

            this.notifierId = notifierId;
            context = task.getContext();
            this.transferName = task._manifest.getName();
            tickerFilename = MissionPackageUtils.abbreviateFilename(
                    transferName, 20);
            fileSize = Math.max(task._fileInfo.sizeInBytes(), 1);
            postedUrl = null;

            progressUid = null;
            uploadComplete = false;
            uploadResult = false;
            postedUploadNotification = false;
        }

        // For direct uploads to tak server
        public CommsMPSendListener(int notifierId, String transferName,
                long fileSize) {
            this.task = null;

            this.notifierId = notifierId;
            context = MapView.getMapView().getContext();
            this.transferName = transferName;
            tickerFilename = MissionPackageUtils.abbreviateFilename(
                    transferName, 20);
            this.fileSize = Math.max(fileSize, 1);
            postedUrl = null;

            progressUid = null;
            uploadComplete = false;
            uploadResult = false;
            postedUploadNotification = false;
        }

        public synchronized String getPostedUrl() {
            if (!uploadComplete)
                return null;

            return postedUrl;
        }

        public synchronized String getUploadErrDetail() {
            return uploadErrorDetail;
        }

        private synchronized boolean waitForUploadComplete() {
            synchronized (this) {
                // Wait for upload to end to preserve
                // compatibility with legacy code
                if (!uploadComplete) {
                    try {
                        wait();
                    } catch (InterruptedException ignored) {
                    }
                }
                return uploadComplete;
            }
        }

        public synchronized boolean hasUploadPhaseCompleted() {
            return uploadComplete;
        }

        @Override
        public synchronized void mpSendRecipients(String[] contactUids) {
            // Existing UI and legacy code only supports progress
            // for one recipient and one upload, but new Comms
            // MP handling supports progress updates for each
            // recipient and potentially multiple uploads to support
            // the batch of recipients.
            // For now, key on first recipient for update tracking
            progressUid = contactUids[0];
        }

        @Override
        public void mpAckReceived(
                String contactUid,
                String ackDetail,
                long byteCount) {
            // This should not be invoked for direct-to-server 
            // uploads, but check for null contact anyway
            if (contactUid == null || !checkUid(contactUid))
                return;

            // Be sure anything waiting on upload is clear first
            mpUploadDone(true, null);

            String ackSenderName = MissionPackageReceiver
                    .getSenderCallsign(null, contactUid);

            String message = context.getString(
                    R.string.mission_package_cot_ack_received,
                    ackSenderName, tickerFilename,
                    (ackDetail != null ? " - "
                            + ackDetail : ""));
            NotificationUtil.getInstance().postNotification(
                    R.drawable.missionpackage_sent, NotificationUtil.GREEN,
                    context.getString(
                            R.string.file_share_transfer_complete),
                    message, message);

            // Note this logs individually each user successfully got a MP, we could alternatively
            // log via Web Server,
            // or during send process if we have ContactList notify/intent to instead tell us which
            // receivers the user selected
            FileInfoPersistanceHelper.instance().insertLog(
                    new FileTransferLog(FileTransferLog.TYPE.SEND,
                            transferName, context.getString(
                                    R.string.mission_package_name)
                                    + " Sent to " + ackSenderName,
                            byteCount));
        }

        @Override
        public void mpSendFailed(
                String contactUid,
                String nackDetail,
                long byteCount) {
            if (!checkUid(contactUid))
                return;

            boolean postNotification;
            synchronized (this) {
                postNotification = !(uploadComplete && !uploadResult);
            }

            // Be sure anything waiting on upload is clear first
            mpUploadDone(false, null);

            // Update notification with this final failure only if we
            // didn't give an upload failure already since that will
            // have more detail
            if (postNotification) {
                String ackSenderName = MissionPackageReceiver
                        .getSenderCallsign(null, contactUid);

                String message;
                if (nackDetail != null && ackSenderName != null) {
                    message = context.getString(
                            R.string.mission_package_cot_nack_received,
                            ackSenderName, tickerFilename,
                            nackDetail);
                } else {
                    message = context.getString(
                            R.string.failed_to_send_file);
                }
                NotificationUtil.getInstance().postNotification(
                        R.drawable.ic_network_error_notification_icon,
                        NotificationUtil.RED,
                        context.getString(R.string.file_share_transfer_failed),
                        message, message);
            }
        }

        @Override
        public void mpSendInProgress(String contactUid) {
            if (!checkUid(contactUid))
                return;

            mpUploadDone(true, null);
        }

        @Override
        public void mpUploadProgress(
                String contactUid,
                UploadStatus status,
                String detail,
                long byteCount) {
            if (!checkUid(contactUid))
                return;

            switch (status) {
                case PENDING:
                    if (task != null)
                        task.publishProgress(new ProgressDialogUpdate(minProg,
                                context.getString(
                                        R.string.mission_package_posting_package,
                                        "TAK Server")));
                    break;
                case IN_PROGRESS:
                    if (byteCount == 0) {
                        // checking
                        NotificationUtil.getInstance().postNotification(
                                notifierId,
                                R.drawable.sync_export, NotificationUtil.BLUE,
                                context.getString(
                                        R.string.checking_mission_package,
                                        transferName),
                                context.getString(
                                        R.string.checking_if_mission_package_on_server),
                                context.getString(
                                        R.string.checking_if_mission_package_on_server));
                    } else if (!postedUploadNotification) {
                        // uploading
                        postedUploadNotification = true;
                        NotificationUtil.getInstance().postNotification(
                                notifierId,
                                R.drawable.sync_export,
                                NotificationUtil.BLUE,
                                context.getString(
                                        R.string.posting_mission_package_to_server,
                                        transferName),
                                "", "");

                    }

                    if (task != null) {
                        int p = (int) ((maxProg - minProg) *
                                ((double) byteCount / fileSize));
                        task.publishProgress(new ProgressDialogUpdate(
                                minProg + p,
                                context.getString(
                                        R.string.mission_package_posting_package,
                                        "TAK Server")));
                    }
                    break;
                case COMPLETE:
                    mpUploadDone(true, detail);
                    NotificationUtil.getInstance().postNotification(
                            notifierId,
                            R.drawable.sync_export,
                            NotificationUtil.GREEN,
                            context.getString(
                                    R.string.posted_mission_package_to_server,
                                    transferName),
                            "", "");

                    break;
                case FAILED:
                    mpUploadDone(false, null);
                    NotificationUtil.getInstance().postNotification(
                            notifierId,
                            R.drawable.sync_error,
                            NotificationUtil.RED,
                            context.getString(R.string.failed_to_post_package,
                                    transferName),
                            detail,
                            detail);
                    uploadErrorDetail = detail;

                    break;
                case FILE_ALREADY_ON_SERVER:
                    mpUploadDone(true, detail);
                    NotificationUtil.getInstance().postNotification(
                            notifierId,
                            R.drawable.sync_export,
                            NotificationUtil.YELLOW,
                            context.getString(
                                    R.string.package_already_on_server,
                                    transferName),
                            "", "");
                    break;
            }
        }

        private void mpUploadDone(boolean success, String url) {
            synchronized (this) {
                if (uploadComplete)
                    return;

                uploadComplete = true;
                uploadResult = success;
                postedUrl = url;
                notifyAll();
            }
        }

        private synchronized boolean checkUid(String uid) {
            return (progressUid == null && uid == null) ||
                    (progressUid != null && progressUid.equals(uid));
        }

    }

} // end SendCompressionTask
