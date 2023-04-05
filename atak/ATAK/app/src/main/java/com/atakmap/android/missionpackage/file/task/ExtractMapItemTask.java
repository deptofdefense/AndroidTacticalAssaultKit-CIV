
package com.atakmap.android.missionpackage.file.task;

import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.file.MissionPackageContent;
import com.atakmap.android.missionpackage.file.MissionPackageExtractor;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;

/**
 * Async task to extract a map and related images
 * 
 * 
 */
public class ExtractMapItemTask extends MissionPackageBaseTask {
    private static final String TAG = "ExtractMapItemTask";

    private final MissionPackageContent _content;

    public ExtractMapItemTask(MissionPackageManifest contents,
            MissionPackageReceiver receiver,
            MissionPackageContent content, Callback callback) {
        super(contents, receiver, true, callback);
        _content = content;
    }

    @Override
    public String getProgressDialogMessage() {
        return getContext().getString(R.string.mission_package_extracting);
    }

    @Override
    protected Boolean doInBackground(Void... arg0) {
        Thread.currentThread().setName("ExtractMapItemTask");

        // work to be performed by background thread
        Log.d(TAG, "Executing: " + this);

        File source = new File(_manifest.getPath());
        if (!FileSystemUtils.isFile(source)) {
            cancel("Cannot extract missing " +
                    getContext().getString(R.string.mission_package_name));
            return false;
        }

        Log.d(TAG,
                "Extracting " + _content.toString() + " from Package: "
                        + source.getAbsolutePath());
        publishProgress(new ProgressDialogUpdate(10, null));

        if (FileSystemUtils.isEmpty(MissionPackageExtractor.ExtractCoT(
                getContext(), source, _content, true))) {
            Log.e(TAG, "Failed to extract Map Item" + _content);
            cancel("Failed to extract Map Item");
        }

        publishProgress(new ProgressDialogUpdate(99, null));
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
