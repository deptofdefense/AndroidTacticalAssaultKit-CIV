
package com.atakmap.android.missionpackage.file.task;

import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;

/**
 * Async task to delete a file
 * 
 * 
 */
public class DeleteFileTask extends MissionPackageBaseTask {
    private static final String TAG = "DeleteFileTask";

    public DeleteFileTask(MissionPackageManifest contents,
            MissionPackageReceiver receiver,
            Callback callback) {
        super(contents, receiver, true, callback);
    }

    @Override
    public String getProgressDialogMessage() {
        return getContext().getString(R.string.mission_package_cleaning_up);
    }

    @Override
    protected Boolean doInBackground(Void... arg0) {
        Thread.currentThread().setName("DeleteFileTask");

        // work to be performed by background thread
        Log.d(TAG, "Executing: " + this);

        File source = new File(_manifest.getPath());
        if (!FileSystemUtils.isFile(source)) {
            // nothing to delete
            Log.d(TAG, "Package does not exist");
            return true;
        }

        Log.d(TAG, "Deleting Package: " + source.getAbsolutePath());
        publishProgress(new ProgressDialogUpdate(50,
                getContext()
                        .getString(R.string.mission_package_deleting_file)));

        File moved = FileSystemUtils.moveToTemp(getContext(), source);
        FileSystemUtils.deleteFile(moved);

        publishProgress(new ProgressDialogUpdate(100,
                getContext()
                        .getString(R.string.mission_package_deleting_file)));
        return true;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        // work to be performed by UI thread after work is complete
        Log.d(TAG, "onPostExecute");

        // close the progress dialog
        if (_progressDialog != null) {
            _progressDialog.dismiss();
            _progressDialog = null;
        }

        if (_callback != null)
            _callback.onMissionPackageTaskComplete(this, result);
    }
} // end SendCompressionTask
