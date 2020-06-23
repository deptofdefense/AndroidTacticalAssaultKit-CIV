
package com.atakmap.android.wfs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.data.DataMgmtReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.io.File;

public class WFSBroadcastReceiver extends BroadcastReceiver {

    private final MapView _mapView;
    private final File _wfsDir;

    WFSBroadcastReceiver(MapView mapView, File wfsDir) {
        _mapView = mapView;
        _wfsDir = wfsDir;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(DataMgmtReceiver.ZEROIZE_CONFIRMED_ACTION)) {
            FileSystemUtils.deleteDirectory(_wfsDir, false);
        }
    }
}
