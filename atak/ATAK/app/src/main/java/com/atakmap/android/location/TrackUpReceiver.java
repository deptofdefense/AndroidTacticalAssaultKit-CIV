
package com.atakmap.android.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.maps.MapMode;
import com.atakmap.android.maps.MapView;

class TrackUpReceiver extends BroadcastReceiver {

    public MapMode _method = MapMode.NORTH_UP;
    private final MapView _mapView;

    public TrackUpReceiver(MapView mapView) {
        _mapView = mapView;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        MapMode action = MapMode.findFromIntent(intent.getAction());

        if (action == MapMode.NORTH_UP) {
            _method = MapMode.NORTH_UP;
            _mapView.getMapController().rotateTo(0, true);
        } else if (action == MapMode.TRACK_UP) {
            _method = MapMode.TRACK_UP;
        } else if (action == MapMode.MAGNETIC_UP) {
            _method = MapMode.MAGNETIC_UP;
        } else if (action == MapMode.USER_DEFINED_UP) {
            _method = MapMode.USER_DEFINED_UP;
        }
    }

    public MapMode getOrientationMethod() {
        return _method;
    }

}
