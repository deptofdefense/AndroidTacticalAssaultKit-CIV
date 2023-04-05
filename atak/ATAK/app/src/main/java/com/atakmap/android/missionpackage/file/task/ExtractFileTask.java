
package com.atakmap.android.missionpackage.file.task;

import com.atakmap.android.missionpackage.MissionPackageReceiver;
import com.atakmap.android.missionpackage.file.MissionPackageContent;
import com.atakmap.android.missionpackage.file.MissionPackageExtractor;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.NameValuePair;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.io.File;

/**
 * Async task to extract a file
 * 
 * 
 */
public class ExtractFileTask extends MissionPackageBaseTask {
    private static final String TAG = "ExtractFileTask";

    private final MissionPackageContent _content;
    private File _outFile;

    public ExtractFileTask(MissionPackageManifest contents,
            MissionPackageReceiver receiver,
            MissionPackageContent content, Callback callback) {
        super(contents, receiver, true, callback);
        _content = content;
    }

    public File getExtractedFile() {
        return _outFile;
    }

    @Override
    public String getProgressDialogMessage() {
        return getContext().getString(R.string.mission_package_extracting);
    }

    @Override
    protected Boolean doInBackground(Void... arg0) {
        Thread.currentThread().setName("ExtractFileTask");

        // work to be performed by background thread
        Log.d(TAG, "Executing: " + this);

        final File source = new File(_manifest.getPath());
        if (!FileSystemUtils.isFile(source)) {
            cancel("Cannot extract missing "
                    + getContext().getString(R.string.mission_package_name));
            return false;
        }

        Log.d(TAG,
                "Extracting " + _content + " from Package: "
                        + source.getAbsolutePath());
        publishProgress(new ProgressDialogUpdate(10, null));

        if (!MissionPackageExtractor.ExtractFile(source, _content)) {
            // TODO print more user friendly error message (just name of file)
            cancel("Failed to extract: " + _content.getManifestUid());
        }

        NameValuePair nvp = _content
                .getParameter(MissionPackageContent.PARAMETER_LOCALPATH);
        if (nvp != null && nvp.getValue() != null)
            _outFile = new File(nvp.getValue());

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
