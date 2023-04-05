
package com.atakmap.android.missionpackage.file.task;

import android.widget.Toast;

import com.atakmap.android.filesharing.android.service.AndroidFileInfo;
import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper;
import com.atakmap.android.filesharing.android.service.FileInfoPersistanceHelper.TABLETYPE;
import com.atakmap.android.filesharing.android.service.FileTransferLog;
import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.file.MissionPackageConfiguration;
import com.atakmap.android.missionpackage.file.MissionPackageExtractorFactory;
import com.atakmap.android.missionpackage.file.MissionPackageFileIO;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.io.File;

/**
 * Async task to extract entire Mission Package
 * 
 * 
 */
public class ExtractMissionPackageTask extends MissionPackageBaseTask {
    private static final String TAG = "ExtractMissionPackageTask";

    private final File _missionPackageZipFile;

    public ExtractMissionPackageTask(File zipFile,
            MissionPackageReceiver receiver,
            Callback callback) {
        // Note no progress dialog for this one as we currently just run in background, do
        // not need user to wait until complete
        super(null, receiver, false, callback);
        _missionPackageZipFile = zipFile;
    }

    @Override
    public String getProgressDialogMessage() {
        return getContext().getString(R.string.mission_package_extracting);
    }

    @Override
    protected Boolean doInBackground(Void... arg0) {
        Thread.currentThread().setName("ExtractMissionPackageTask");

        // work to be performed by background thread
        Log.d(TAG, "Executing: " + this);

        if (!FileSystemUtils.isFile(_missionPackageZipFile)) {
            // nothing to delete
            Log.e(TAG, "Package does not exist");
            return false;
        }

        publishProgress(new ProgressDialogUpdate(10, null));

        // kick off task to unzip package

        try {
            _manifest = MissionPackageExtractorFactory.Extract(getContext(),
                    _missionPackageZipFile,
                    FileSystemUtils.getRoot(),
                    true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract: " + _missionPackageZipFile, e);
            return false;
        }

        if (_manifest == null || !_manifest.isValid()) {
            Log.e(TAG, "Failed to extract: " + _missionPackageZipFile);
            cancel("Failed to extract "
                    + (getMapView() == null ? " package"
                            : getContext().getString(
                                    R.string.mission_package_name)));
            return false;
        }

        publishProgress(new ProgressDialogUpdate(99, null));
        return true;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        // work to be performed by UI thread after work is complete
        Log.d(TAG, "onPostExecute");

        if (!isCancelled()) {
            if (_manifest == null
                    || !FileSystemUtils.isFile(_manifest.getPath())) {
                Log.e(TAG, "Failed to extract Package: "
                        + _missionPackageZipFile);
                Toast.makeText(getContext(), getContext().getString(
                        R.string.mission_package_failed_to_extract,
                        _missionPackageZipFile), Toast.LENGTH_LONG).show();
            } else {
                MissionPackageConfiguration.ImportInstructions inst = _manifest
                        .getConfiguration().getImportInstructions();
                Log.d(TAG,
                        "Processing: with instructions: "
                                + inst.toString());
                switch (inst) {
                    case ImportDelete: {
                        Log.d(TAG,
                                "Auto imported/deleted Package: "
                                        + _missionPackageZipFile
                                                .getAbsolutePath());

                        MissionPackageFileIO
                                .deletePackageFile(_missionPackageZipFile);
                    }
                        break;
                    case ImportNoDelete: {
                        // get proper username
                        File zipFile = new File(_manifest.getPath());
                        String userName = _receiver.getMapView()
                                .getDeviceCallsign();
                        AndroidFileInfo fileInfo = FileInfoPersistanceHelper
                                .instance()
                                .getFileInfoFromFilename(zipFile,
                                        TABLETYPE.SAVED);
                        if (fileInfo != null) {
                            // get username from DB
                            if (!FileSystemUtils.isEmpty(fileInfo.userName()))
                                userName = fileInfo.userName();

                            // update local manifest
                            fileInfo.setFileMetadata(_manifest.toXml(true));
                            FileInfoPersistanceHelper.instance()
                                    .insertOrReplace(
                                            fileInfo, TABLETYPE.SAVED);
                        } else {
                            Log.w(TAG, "Unable to update local manifest for: "
                                    + _manifest.getPath());
                        }

                        // now that contents have been extracted, add to UI list
                        _receiver.add(_manifest, userName);

                        // log that file was successfully imported
                        FileInfoPersistanceHelper
                                .instance()
                                .insertLog(
                                        new FileTransferLog(
                                                FileTransferLog.TYPE.IMPORT,
                                                _manifest.getName(),
                                                (getMapView() == null
                                                        ? " package"
                                                        : getContext()
                                                                .getString(
                                                                        R.string.mission_package_name))
                                                        + " imported from SD card: "
                                                        + _manifest.getPath(),
                                                IOProviderFactory
                                                        .length(zipFile)));
                    }
                }
            }
        }

        // close the progress dialog
        if (_progressDialog != null) {
            _progressDialog.dismiss();
            _progressDialog = null;
        }

        if (_callback != null && _manifest != null)
            _callback.onMissionPackageTaskComplete(this, result);
    }
} // end ExtractMissionPackageTask
