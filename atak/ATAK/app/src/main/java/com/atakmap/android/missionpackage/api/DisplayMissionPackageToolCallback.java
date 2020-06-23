
package com.atakmap.android.missionpackage.api;

import android.content.Intent;
import android.widget.Toast;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.missionpackage.file.task.CompressionTask;
import com.atakmap.android.missionpackage.file.task.MissionPackageBaseTask;
import com.atakmap.app.R;

/**
 * Simple callback to display the Mission Package Tool
 * 
 * 
 */
public class DisplayMissionPackageToolCallback implements SaveAndSendCallback {

    @Override
    public void onMissionPackageTaskComplete(MissionPackageBaseTask task,
            boolean success) {
        if (task instanceof CompressionTask) {
            if (!success) {
                Toast.makeText(task.getContext(),
                        R.string.failed_to_create_mission_package,
                        Toast.LENGTH_LONG)
                        .show();
            } else {
                // launch MPT once package has been created
                Intent mpIntent = new Intent();
                mpIntent.setAction(
                        "com.atakmap.android.missionpackage.MISSIONPACKAGE");
                AtakBroadcast.getInstance()
                        .sendBroadcast(mpIntent);
            }
        }
    }
}
