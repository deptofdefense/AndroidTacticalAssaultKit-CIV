
package com.atakmap.android.missionpackage.file.task;

import android.widget.Toast;

import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.UUID;

/**
 * Async task to copy to specified location, and invoke callback
 * 
 * 
 */
public class CopyTask extends MissionPackageBaseTask {
    private static final String TAG = "CopyTask";

    private File _destination;

    public CopyTask(MissionPackageManifest contents,
            MissionPackageReceiver receiver, Callback callback) {
        super(contents, receiver, true, callback);
        _destination = null;
    }

    public File getDestination() {
        return _destination;
    }

    @Override
    public String getProgressDialogMessage() {
        return getContext().getString(R.string.mission_package_deploying,
                _manifest.getName());
    }

    @Override
    protected Boolean doInBackground(Void... arg0) {
        // work to be performed by background thread
        Thread.currentThread().setName(TAG);

        Log.d(TAG, "Executing: " + this);

        File source = new File(_manifest.getPath());
        if (!FileSystemUtils.isFile(source)) {
            cancel("Cannot create "
                    + (getMapView() == null ? " package"
                            : getContext().getString(
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

        // now copy to deploy directory

        try (FileInputStream fis = IOProviderFactory.getInputStream(source);
                FileOutputStream fos = IOProviderFactory
                        .getOutputStream(_destination)) {
            FileSystemUtils.copyStream(fis, fos);
        } catch (Exception e) {
            Log.w(TAG, "Failed to deploy (1) to: " + _destination, e);
            cancel("Failed to deploy "
                    + (getMapView() == null ? " package"
                            : getContext().getString(
                                    R.string.mission_package_name))
                    + " (CODE=1): " + _manifest.getName());
            return false;
        }

        // now that file was written out, set additional data
        if (!FileSystemUtils.isFile(_destination)) {
            Log.w(TAG, "Failed to deploy (2) to: " + _destination);
            cancel("Failed to deploy "
                    + (getMapView() == null ? " package"
                            : getContext().getString(
                                    R.string.mission_package_name))
                    + " (CODE=2): " + _manifest.getName());
            return false;
        }

        Log.d(TAG, "Package deployed: " + _destination);
        publishProgress(new ProgressDialogUpdate(95, null));
        return true;
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
} // end SendCompressionTask
