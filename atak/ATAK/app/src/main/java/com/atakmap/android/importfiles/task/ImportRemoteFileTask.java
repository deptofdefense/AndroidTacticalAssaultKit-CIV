
package com.atakmap.android.importfiles.task;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.importfiles.resource.RemoteResource;
import com.atakmap.android.importfiles.ui.ImportManagerDropdown;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.app.R;
import com.atakmap.coremap.log.Log;

/**
 * Background task to import a single file, minor extensions to ImportFileTask e.g. update the
 * RemoteResource configuration once downloaded, and different method of notifying user
 * 
 * 
 */
public class ImportRemoteFileTask extends ImportFileTask {

    private static final String TAG = "ImportRemoteFileTask";

    // flags pick up beyond where ImportFileTask flags left off
    public static final int FlagNotifyUserSuccess = 512;
    public static final int FlagUpdateResourceLocalPath = 1024;

    final RemoteResource _resource;
    final int _notificationId;

    public ImportRemoteFileTask(Context context, RemoteResource resource,
            int notificationId) {
        // Do not validate extension as we may not know file type
        // based on a URL input by the user, or remote machine serving
        // up the file naming convention
        // Also do not prompt user to overwrite when downloading remote file, we could but think
        // through it first...
        // Currently not implementing the callback mechanism provided by ImportFileTask
        super(context, null);
        _resource = resource;
        _notificationId = notificationId;
    }

    @Override
    protected void onPostExecute(ImportFileTask.Result result) {
        if (result == null) {
            Log.e(TAG, "Failed to import file");
            NotificationUtil
                    .getInstance()
                    .postNotification(
                            _notificationId,
                            R.drawable.ic_network_error_notification_icon,
                            NotificationUtil.RED,
                            _context.getString(
                                    R.string.importmgr_remote_import_failed),
                            _context.getString(R.string.failed_to_import),
                            _context.getString(R.string.failed_to_import));
            return;
        }

        if (result.isSuccess()) {
            Log.d(TAG, "Finished importing: "
                    + result.getFile().getAbsolutePath());
            if (checkFlag(FlagNotifyUserSuccess)) {
                NotificationUtil
                        .getInstance()
                        .postNotification(
                                _notificationId,
                                R.drawable.download_remote_file,
                                NotificationUtil.BLUE,
                                _context.getString(
                                        R.string.importmgr_remote_import_download_complete),
                                String.format(
                                        _context.getString(
                                                R.string.importmgr_file_downloaded_importing_now),
                                        result.getFile().getName()),
                                String.format(
                                        _context.getString(
                                                R.string.importmgr_file_downloaded_importing_now),
                                        result.getFile().getName()));
            }

            if (checkFlag(FlagUpdateResourceLocalPath)) {
                // send an intent so adapter can update details about the local cache of the remote
                // resource
                _resource.setLocalPath(result.getFile().getAbsolutePath());
                _resource.setMd5(result.getFileMD5());
                _resource.setType(result.getType());
                // File was updated now, even if we skipped it due to MD5 matching (no changes)
                _resource.setLastRefreshed(new CoordinatedTime()
                        .getMilliseconds());

                Intent updateIntent = new Intent();
                updateIntent.setAction(ImportManagerDropdown.UPDATE_RESOURCE);
                updateIntent.putExtra("resource", _resource);
                updateIntent.putExtra("updateLocalPath", true);
                AtakBroadcast.getInstance().sendBroadcast(updateIntent);
            }
        } else {
            String error = result.getError();
            if (error == null || error.length() < 1)
                error = _context.getString(
                        R.string.importmgr_failed_to_import_file_error_unknown);

            NotificationUtil
                    .getInstance()
                    .postNotification(
                            _notificationId,
                            R.drawable.ic_network_error_notification_icon,
                            NotificationUtil.RED,
                            _context.getString(
                                    R.string.importmgr_remote_import_cancelled),
                            error, error);
        }
    }
}
