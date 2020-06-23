
package com.atakmap.android.attachment;

import android.widget.Toast;

import com.atakmap.android.missionpackage.api.MissionPackageApi;
import com.atakmap.android.missionpackage.api.SaveAndSendCallback;
import com.atakmap.android.missionpackage.file.task.CompressionTask;
import com.atakmap.android.missionpackage.file.task.CopyAndSendTask;
import com.atakmap.android.missionpackage.file.task.MissionPackageBaseTask;
import com.atakmap.coremap.log.Log;

/**
 * Mission Package Tool callback to delete the local .zip
 *
 * 
 */
public class DeleteAfterSendCallback implements SaveAndSendCallback {
    private final static String TAG = "DeleteAfterSendCallback";

    @Override
    public void onMissionPackageTaskComplete(MissionPackageBaseTask task,
            boolean success) {

        if (task instanceof CompressionTask) {
            if (!success) {
                Log.e(TAG, "Failed to create Mission Package: "
                        + task.getManifest().getName());
                Toast.makeText(task.getContext(), "Failed to send file",
                        Toast.LENGTH_LONG)
                        .show();
            }
        } else if (task instanceof CopyAndSendTask) {
            if (!success) {
                Log.e(TAG, "Failed to send Mission Package: "
                        + task.getManifest().getName());
                Toast.makeText(task.getContext(), "Failed to send file...",
                        Toast.LENGTH_LONG)
                        .show();
                return;
            }

            // whether successfully deployed to be sent or not, delete the .zip
            Log.v(TAG, "Cleaning up Mission Package temp file: "
                    + task.getManifest().toString());
            // clean up the temp folder
            MissionPackageApi.Delete(task.getContext(), task.getManifest());
        }
    }
}
