
package com.atakmap.android.warning;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapOverlayManager;
import com.atakmap.android.maps.MapView;

/**
 * The functionality to scan the map and provide for warnings during 
 * danger close scenarios.    This component is responsible for 
 * maintaining the dange close calculation thread and management of 
 * the related widgets.
 */
public class WarningMapComponent extends AbstractMapComponent {

    private DangerCloseCalculator dcc;
    private DangerCloseMapOverlay _overlay;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {
        dcc = new DangerCloseCalculator(context);
        //setup Danger Close overlay
        _overlay = new DangerCloseMapOverlay(view, dcc);
        final MapOverlayManager overlayManager = view.getMapOverlayManager();
        overlayManager.addAlertsOverlay(_overlay);
        dcc.start();
    }

    @Override
    public void onDestroyImpl(Context context, MapView view) {
        dcc.cancel();
    }

}
