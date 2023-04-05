
package com.atakmap.android.missionpackage.file.task;

import android.widget.Toast;

import com.atakmap.android.filesharing.android.service.AndroidFileInfo;
import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper;
import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper.TABLETYPE;
import com.atakmap.android.filesystem.MIMETypeMapper;
import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.file.MissionPackageBuilder;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;

/**
 * Async task to compress file(s) into a zip and report to progress dialog along the way. Optionally
 * execute a follow on task when complete, which can in turn have its own follow on task
 * 
 * 
 */
public class CompressionTask extends MissionPackageBaseTask {
    private static final String TAG = "CompressionTask";

    private final MissionPackageBaseTask _followUpTask;
    private final boolean _bDeleteUponError;
    private boolean _persist;

    public CompressionTask(MissionPackageManifest contents,
            MissionPackageReceiver receiver,
            boolean bShowProgress, MissionPackageBaseTask followUpTask,
            Callback callback,
            boolean bDeleteUponError) {
        super(contents, receiver, bShowProgress, callback);
        _followUpTask = followUpTask;
        _cancelReason = null;
        _bDeleteUponError = bDeleteUponError;
    }

    /**
     * Set whether the data package should be persisted to the local database
     * If true, the data package will show up within ATAK for this device
     * If false, the data package zip file will be created without showing up
     * in the local user's ATAK
     *
     * @param persist True to persist
     */
    public void setPersist(boolean persist) {
        _persist = persist;
    }

    /**
     * Get the number of chained tasks
     * @return Task count
     */
    public int getChainedTaskCount() {
        if (!(_followUpTask instanceof CompressionTask))
            return 1;

        CompressionTask ct = (CompressionTask) _followUpTask;
        return 1 + ct.getChainedTaskCount();
    }

    @Override
    public String getProgressDialogMessage() {
        return getContext().getString(R.string.mission_package_compressing,
                _manifest.getName());
    }

    @Override
    protected Boolean doInBackground(Void... arg0) {
        // work to be performed by background thread
        Thread.currentThread().setName("CompressionTask");

        Log.d(TAG, "Executing: " + this);

        String retVal = compressMissionPackage();

        // Note starting Android 4.0 onPostExecute not called if task is cancelled (e.g. user hit
        // back)
        // and onCancel may run before the async task/thread is able to cancel/quit compressing the
        // zip
        // so check here if we were cancelled, and delete the .zip if necessary
        // delete the file
        File file = new File(_manifest.getPath());
        if (_bDeleteUponError && isCancelled()
                && FileSystemUtils.isFile(file)) {
            Log.w(TAG, "Cancelled, deleting: " + _manifest.toString());
            FileSystemUtils.deleteFile(file);
        }

        // not 100% yet as postExecute may still take some time... so too might followTask
        publishProgress(new ProgressDialogUpdate(99, null));
        return retVal != null;
    }

    private String compressMissionPackage() {
        if (_persist)
            return CompressMissionPackage(_manifest, _receiver, this);
        else
            return buildPackage(_manifest, _receiver, this);
    }

    public static String CompressMissionPackage(
            MissionPackageManifest contents,
            MissionPackageReceiver receiver,
            MissionPackageBuilder.Progress progress) {

        String localManifestXml = contents.toXml(true);

        // create DB entry so Directory Watcher will ignore, then update after file write is
        // complete
        File file = new File(FileSystemUtils
                .sanitizeWithSpacesAndSlashes(contents.getPath()));
        FileInfoPersistanceHelper db = FileInfoPersistanceHelper.instance();
        AndroidFileInfo fileInfo = db.getFileInfoFromFilename(file,
                TABLETYPE.SAVED);
        if (fileInfo == null) {
            // dont create SHA256 until after we write out file
            fileInfo = new AndroidFileInfo("", file,
                    MIMETypeMapper.GetContentType(file),
                    localManifestXml);
            db.insertOrReplace(fileInfo, TABLETYPE.SAVED);
        }

        String retVal = buildPackage(contents, receiver, progress);

        // now that file was written out, set additional data
        if (FileSystemUtils.isFile(contents.getPath())) {
            fileInfo.setUserName(receiver.getMapView().getDeviceCallsign());
            fileInfo.setUserLabel(contents.getName());
            fileInfo.setSizeInBytes((int) IOProviderFactory.length(file));
            fileInfo.setUpdateTime(IOProviderFactory.lastModified(file));
            fileInfo.computeSha256sum();
            fileInfo.setFileMetadata(localManifestXml);
            db.update(fileInfo, TABLETYPE.SAVED);

            Log.d(TAG, "Package exported: " + contents.getPath());
        }

        return retVal;
    }

    private static String buildPackage(
            MissionPackageManifest contents,
            MissionPackageReceiver receiver,
            MissionPackageBuilder.Progress progress) {
        MissionPackageBuilder builder = new MissionPackageBuilder(progress,
                contents, receiver.getMapView().getRootGroup());
        return builder.build();
    }

    @Override
    protected void onPostExecute(Boolean result) {
        // work to be performed by UI thread after work is complete
        Log.d(TAG, "onPostExecute");

        if (!isCancelled()) {
            if (!FileSystemUtils.isFile(_manifest.getPath())) {
                Log.e(TAG, "Failed to create Package: " + _manifest.getPath());
                Toast.makeText(_receiver.getMapView().getContext(),
                        _receiver.getMapView().getContext().getString(
                                R.string.mission_package_failed_to_create,
                                _manifest.getPath()),
                        Toast.LENGTH_LONG)
                        .show();
            } else {
                if (_followUpTask != null) {
                    Log.d(TAG,
                            "Launching follow on task: "
                                    + _followUpTask);
                    // TODO account for follow on task when determining progress allocations...
                    _followUpTask.setProgressDialog(_progressDialog);
                    _followUpTask.execute();
                }
            }
        }

        // close the progress dialog unless there is a follow on task
        if (_followUpTask == null && _progressDialog != null) {
            // no follow on task.. we are all down
            _progressDialog.dismiss();
            _progressDialog = null;
        }

        if (_callback != null)
            _callback.onMissionPackageTaskComplete(this, result);
    }
} // end CompressionTask
