
package com.atakmap.android.toolbar.tools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.app.R;
import com.atakmap.comms.ReportingRate;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;

public class SpecifySelfLocationTool extends Tool {

    public static final String TOOL_IDENTIFIER = "com.atakmap.android.toolbar.tools.SPECIFY_SELF_LOCATION";
    String _linkBack = null;

    public SpecifySelfLocationTool(MapView mapView) {
        super(mapView, TOOL_IDENTIFIER);
    }

    @Override
    public void dispose() {
    }

    @Override
    protected boolean onToolBegin(Bundle extras) {

        _linkBack = extras.getString("linkBack", null);

        // push all the dispatch listeners
        _mapView.getMapEventDispatcher().pushListeners();

        // clear all the listeners listening for a click
        clearExtraListeners();

        _mapView.getMapTouchController().skipDeconfliction(true);
        _mapView.getMapEventDispatcher().addMapEventListener(
                MapEvent.MAP_CLICK,
                selfLocationPressListener);
        AtakBroadcast.getInstance().registerReceiver(locReceiver,
                new DocumentedIntentFilter(ReportingRate.REPORT_LOCATION,
                        "Track if the self marker is still movable"));

        String text = _mapView.getContext().getString(
                R.string.tap_current_location);
        TextContainer.getInstance().displayPrompt(text);

        return true;
    }

    @Override
    protected void onToolEnd() {
        _mapView.getMapTouchController().skipDeconfliction(false);
        _mapView.getMapEventDispatcher().popListeners();
        TextContainer.getInstance().closePrompt();
        AtakBroadcast.getInstance().unregisterReceiver(locReceiver);

        _linkBack = null;
    }

    private final MapEventDispatcher.MapEventDispatchListener selfLocationPressListener = new MapEventDispatcher.MapEventDispatchListener() {

        @Override
        public void onMapEvent(MapEvent event) {
            GeoPointMetaData gpm = findPoint(event);
            gpm.setGeoPointSource(GeoPointMetaData.USER);

            // Use new location
            _mapView.getMapData().putBoolean("fakeLocationAvailable", true);
            //XXY
            _mapView.getMapData().putParcelable("fakeLocation", gpm.get());
            _mapView.getMapData().putString("fakeLocationAltSrc",
                    gpm.getAltitudeSource());
            _mapView.getMapData().putString("fakeLocationSrc",
                    gpm.getGeopointSource());

            /**
             * All *LocationTime is used for to determine when the last GPS pump occurred.
             * should be based on SystemClock which is not prone to error by setting the
             * System Date/Time.
             */

            _mapView.getMapData().putLong("fakeLocationTime",
                    SystemClock.elapsedRealtime());

            _mapView.getMapData().putDouble("fakeLocationSpeed", Double.NaN);

            // tell LocationMapComponent to update overlays
            AtakBroadcast.getInstance().sendBroadcast(
                    new Intent(
                            "com.atakmap.android.map.SELF_LOCATION_SPECIFIED"));

            requestEndTool();

            if (_linkBack != null) {
                Intent intent = new Intent(_linkBack);
                AtakBroadcast.getInstance().sendBroadcast(intent);
            }
            _linkBack = null;

        }
    };

    private final BroadcastReceiver locReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Marker self = _mapView.getSelfMarker();
            if (self != null && !self.getMovable())
                requestEndTool();
        }
    };
}
