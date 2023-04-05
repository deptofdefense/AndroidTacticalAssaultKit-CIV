
package com.atakmap.android.compassring;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.atakmap.android.maps.CompassRing;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.PointMapItem.OnPointChangedListener;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.annotations.ModifierApi;

import java.util.UUID;

public class CompassRingMapReceiver extends BroadcastReceiver implements
        OnPointChangedListener {
    public static final String ACTION = "com.atakmap.android.maps.COMPASS";

    private final MapView _mapView;
    private boolean _isVisible = false;
    private CompassRing _compassRing = null;

    @ModifierApi(since = "4.5", target = "4.8", modifiers = {})
    public CompassRingMapReceiver(final MapView mapView,
            final Marker locationMarker) {

        _mapView = mapView;

        if (locationMarker != null) {
            String uid = UUID.randomUUID().toString();
            GeoPoint gp = new GeoPoint(locationMarker.getPoint());
            _compassRing = new CompassRing(gp, uid);
        }
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        String action = intent.getAction();

        if (action == null)
            return;

        if (action.equals("com.atakmap.android.maps.COMPASS")) {
            if (_compassRing != null) {
                Bundle extras = intent.getExtras();
                if (extras == null)
                    return;

                String targetUID = extras.getString("targetUID");
                MapItem item = _mapView.getRootGroup().findItem("uid",
                        targetUID);

                if (_isVisible) {

                    _mapView.getRootGroup().removeItem(_compassRing);
                    _isVisible = false;
                    if (item != null) {
                        item.setMetaBoolean("compass_on", false);
                    }
                } else {
                    // Show the compass ring
                    _mapView.getRootGroup().addItem(_compassRing);
                    _isVisible = true;
                    if (item != null) {
                        item.setMetaBoolean("compass_on", true);
                    }
                }
            }
        }
    }

    @Override
    public void onPointChanged(final PointMapItem item) {
        if (item instanceof Marker) {
            if (_compassRing == null) {
                final String uid = UUID.randomUUID().toString();
                _compassRing = new CompassRing(item.getPoint(), uid);
            } else {
                _compassRing.setPoint(item.getPoint());
            }
        }
    }

}
