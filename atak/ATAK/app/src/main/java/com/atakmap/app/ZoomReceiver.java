
package com.atakmap.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.CameraController;
import com.atakmap.map.MapRenderer3;

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
                CameraController.Interactive.zoomBy(
                        _mapView.getRenderer3(), 2d,
                        MapRenderer3.CameraCollision.Abort, true);
                break;
            case "com.atakmap.android.map.ZOOM_OUT":
                CameraController.Interactive.zoomBy(
                        _mapView.getRenderer3(), 0.5d,
                        MapRenderer3.CameraCollision.AdjustFocus, true);
                break;
        }
    }

}
