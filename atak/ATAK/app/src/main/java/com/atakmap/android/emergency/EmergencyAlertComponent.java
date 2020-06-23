
package com.atakmap.android.emergency;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapOverlayManager;
import com.atakmap.android.maps.MapView;

import com.atakmap.coremap.log.Log;

public class EmergencyAlertComponent extends AbstractMapComponent {

    private EmergencyAlertReceiver _receiver;

    private EmergencyAlertMapOverlay _overlay;

    @Override
    public void onCreate(Context context, Intent intent, MapView view) {

        _receiver = new EmergencyAlertReceiver(view);
        DocumentedIntentFilter filter = new DocumentedIntentFilter();
        filter.addAction(EmergencyAlertReceiver.ALERT_EVENT);
        filter.addAction(EmergencyAlertReceiver.CANCEL_EVENT);
        filter.addAction(EmergencyAlertReceiver.REMOVE_ALERT);
        AtakBroadcast.getInstance().registerReceiver(_receiver, filter);

        _overlay = new EmergencyAlertMapOverlay(view, _receiver);
        MapOverlayManager overlayManager = view.getMapOverlayManager();
        overlayManager.addAlertsOverlay(_overlay);
    }

    @Override
    protected void onDestroyImpl(Context context, MapView view) {
        if (_receiver != null) {
            Log.d(TAG, "unregistered the EmergencyAlertReceiver");
            AtakBroadcast.getInstance().unregisterReceiver(_receiver);
            _receiver.dispose();
            _receiver = null;
        }

        if (_overlay != null) {
            MapOverlayManager overlayManager = view.getMapOverlayManager();
            overlayManager.removeOverlay(_overlay);
        }
    }

}
