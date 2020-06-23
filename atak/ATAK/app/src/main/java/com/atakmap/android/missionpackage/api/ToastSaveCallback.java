
package com.atakmap.android.missionpackage.api;

import android.widget.Toast;
import com.atakmap.android.maps.MapView;

import com.atakmap.android.missionpackage.file.task.MissionPackageBaseTask;

public class ToastSaveCallback implements SaveAndSendCallback {

    @Override
    public void onMissionPackageTaskComplete(final MissionPackageBaseTask task,
            final boolean success) {
        final MapView mapView = task.getMapView();
        if (mapView != null) {
            mapView.post(new Runnable() {
                @Override
                public void run() {
                    if (success)
                        Toast.makeText(task.getContext(),
                                "Exported: "
                                        + task.getManifest().getLastSavedPath(),
                                Toast.LENGTH_SHORT).show();
                    else
                        Toast.makeText(task.getContext(),
                                "Failed to export: "
                                        + task.getManifest().getName(),
                                Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
