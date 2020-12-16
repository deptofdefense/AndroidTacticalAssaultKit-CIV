
package com.atakmap.android.wfs;

import com.atakmap.android.data.ClearContentRegistry;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.io.File;

public class WFSBroadcastReceiver
        implements ClearContentRegistry.ClearContentListener {

    private final MapView _mapView;
    private final File _wfsDir;

    WFSBroadcastReceiver(MapView mapView, File wfsDir) {
        _mapView = mapView;
        _wfsDir = wfsDir;
    }

    @Override
    public void onClearContent(boolean clearmaps) {
        FileSystemUtils.deleteDirectory(_wfsDir, false);
    }

}
