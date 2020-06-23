
package com.atakmap.android.compassring;

import android.content.Context;
import android.content.Intent;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.AbstractMapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;

public class CompassRingMapComponent extends AbstractMapComponent implements
        MapEventDispatcher.MapEventDispatchListener {
    private MapView _mapView;
    private CompassRingMapReceiver _compassRingMapReceiver;
    private Marker _locationMarker;

    @Override
    public void onCreate(final Context context, final Intent intent,
            final MapView view) {
        _mapView = view;

        _compassRingMapReceiver = new CompassRingMapReceiver(view,
                _locationMarker);
        DocumentedIntentFilter showFilter = new DocumentedIntentFilter();
        showFilter.addAction("com.atakmap.android.maps.COMPASS");
        AtakBroadcast.getInstance().registerReceiver(_compassRingMapReceiver,
                showFilter);

        // Start listening for global ITEM_REFRESH MapEvent's. That is the
        // most sure fire way to find the location Marker
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.ITEM_REFRESH,
                this);
    }

    @Override
    protected void onDestroyImpl(final Context context, final MapView view) {
        _mapView.getMapEventDispatcher().removeMapEventListener(
                MapEvent.ITEM_REFRESH,
                this);
        AtakBroadcast.getInstance().unregisterReceiver(_compassRingMapReceiver);
        _compassRingMapReceiver = null;
    }

    @Override
    public void onMapEvent(final MapEvent event) {
        final String deviceUID = _mapView.getSelfMarker().getUID();
        final String itemUID = event.getItem().getUID();
        if (deviceUID != null && deviceUID.equals(itemUID)
                && event.getItem() instanceof Marker) {

            // this is the location Marker
            _locationMarker = (Marker) event.getItem();

            // add OnPointChangedListener here
            _locationMarker
                    .addOnPointChangedListener(_compassRingMapReceiver);
            _compassRingMapReceiver.onPointChanged(_locationMarker);

            // stop listening to refreshes (remove self)
            _mapView.getMapEventDispatcher().removeMapEventListener(
                    MapEvent.ITEM_REFRESH,
                    this);
        }
    }

}
