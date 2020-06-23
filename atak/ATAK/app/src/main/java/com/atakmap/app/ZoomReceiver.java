
package com.atakmap.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;

/**
 * Broadcast receiver that can handle zoom events via intent.
 */
public class ZoomReceiver extends BroadcastReceiver {

    private final MapView _mapView;

    public ZoomReceiver(MapView mapView) {
        _mapView = mapView;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        String action = intent.getAction();
        if (action == null)
            return;

        switch (action) {
            case "com.atakmap.android.map.ZOOM":
                GeoPoint touchPoint = GeoPoint.parseGeoPoint(intent
                        .getStringExtra("focusPoint"));
                double zoom = intent.getDoubleExtra("zoomLevel", 1d);
                if (intent.getBooleanExtra("maintainZoomLevel", false))
                    zoom = _mapView.getMapScale();
                _mapView.getMapController().panZoomTo(touchPoint,
                        zoom,
                        true);
                break;
            case "com.atakmap.android.map.ZOOM_IN":
                _mapView.getMapController()
                        .zoomTo(_mapView.getMapScale() * 2, true);
                break;
            case "com.atakmap.android.map.ZOOM_OUT":
                _mapView.getMapController()
                        .zoomTo(_mapView.getMapScale() / 2, true);
                break;
        }
    }

}
