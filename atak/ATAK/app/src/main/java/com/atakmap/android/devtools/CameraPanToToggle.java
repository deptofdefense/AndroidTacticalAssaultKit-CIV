
package com.atakmap.android.devtools;

import com.atakmap.android.maps.MapTouchController;
import com.atakmap.android.maps.MapView;

final class CameraPanToToggle extends DevToolToggle {
    final MapTouchController _touchController;

    public CameraPanToToggle(MapView mapView) {
        super("Camera Pan To Enabled",
                "MapTouchController.CameraPanTo.Enabled");
        _touchController = mapView.getMapTouchController();
    }

    @Override
    protected void setEnabled(boolean visible) {
        _touchController.setCameraPanToEnabled(visible);
    }

    @Override
    protected boolean isEnabled() {
        return _touchController.isCameraPanToEnabled();
    }
}
