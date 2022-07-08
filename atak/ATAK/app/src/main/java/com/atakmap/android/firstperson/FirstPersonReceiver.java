
package com.atakmap.android.firstperson;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.user.MapClickTool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.annotations.ModifierApi;

/**
 * Broadcast receiver that can handle First Person events via intent.
 */
public class FirstPersonReceiver extends BroadcastReceiver {

    private final MapView _mapView;

    public static final String FIRSTPERSON = "com.atakmap.android.map.FIRSTPERSON";
    public static final String MAP_CLICKED = "com.atakmap.android.map.FIRSTPERSON_MAP_CLICKED";

    private static FirstPersonTool _firstPersonTool;

    @ModifierApi(since = "4.5", target = "4.8", modifiers = {})
    public FirstPersonReceiver(MapView mapView) {
        _mapView = mapView;
        _firstPersonTool = new FirstPersonTool(_mapView);
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String action = intent.getAction();
        if (action == null)
            return;

        Bundle bundle = new Bundle();
        switch (action) {
            case FIRSTPERSON:
                bundle.putString("prompt",
                        "Tap location for First Person View");
                bundle.putParcelable("callback", new Intent(MAP_CLICKED));
                ToolManagerBroadcastReceiver.getInstance().startTool(
                        MapClickTool.TOOL_NAME, bundle);
                break;
            case MAP_CLICKED:
                if (intent.hasExtra("point")) {
                    GeoPoint from = GeoPoint
                            .parseGeoPoint(intent.getStringExtra("point"));
                    if (from == null)
                        return;
                    double fromAlt = from.getAltitude();
                    if (Double.isNaN(fromAlt))
                        fromAlt = 0d;
                    double terrain = ElevationManager.getElevation(from, null);
                    if (!Double.isNaN(terrain) && fromAlt < terrain)
                        fromAlt = terrain;
                    if (fromAlt < 0d)
                        fromAlt = 0d;
                    double mapItemHeight = intent.getDoubleExtra("itemHeight",
                            Double.NaN);
                    if (!Double.isNaN(mapItemHeight))
                        fromAlt += mapItemHeight;
                    fromAlt += 2d;
                    from = new GeoPoint(from.getLatitude(), from.getLongitude(),
                            fromAlt);
                    bundle.putString("fromPoint",
                            from.toStringRepresentation());
                    ToolManagerBroadcastReceiver.getInstance().startTool(
                            FirstPersonTool.TOOL_NAME, bundle);
                }
        }
    }
}
