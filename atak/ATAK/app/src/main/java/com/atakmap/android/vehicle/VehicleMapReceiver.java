
package com.atakmap.android.vehicle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.vehicle.model.VehicleModelEditTool;

/**
 * Receiver for various vehicle-related intents
 */
public class VehicleMapReceiver extends BroadcastReceiver {

    public static final String TAG = "VehicleMapReceiver";
    public static final String ROTATE = "com.atakmap.android.maps.ROTATE";
    public static final String EDIT = "com.atakmap.android.vehicle.model.EDIT";
    public static final String TOGGLE_LABEL = "com.atakmap.android.maps.TOGGLE_LABEL";

    private final MapView _mapView;

    public VehicleMapReceiver(MapView mapView) {
        _mapView = mapView;
        new VehicleRotationTool(_mapView, null);
        new VehicleModelEditTool(_mapView, VehicleMapComponent
                .getVehicleGroup(_mapView));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action == null)
            return;

        String uid = intent.getStringExtra("uid");
        if (uid == null)
            return;

        MapItem mi = _mapView.getRootGroup().deepFindUID(uid);
        if (!(mi instanceof VehicleMapItem)) {
            mi = ATAKUtilities.findAssocShape(mi);
            if (!(mi instanceof VehicleMapItem))
                return;
        }

        Bundle extras = intent.getExtras();
        if (extras == null)
            extras = new Bundle();

        // Start vehicle rotation tool
        switch (action) {
            case ROTATE:
                ToolManagerBroadcastReceiver.getInstance().startTool(
                        VehicleRotationTool.TOOL_NAME, extras);
                break;

            // Edit vehicle model
            case EDIT:
                ToolManagerBroadcastReceiver.getInstance().startTool(
                        VehicleModelEditTool.TOOL_NAME, extras);
                break;

            // Toggle vehicle marker label
            case TOGGLE_LABEL:
                if (mi instanceof VehicleShape) {
                    VehicleShape veh = (VehicleShape) mi;
                    veh.setShowLabel(!veh.hasMetaValue("showLabel"));
                    veh.save();
                } else {
                    mi.toggleMetaData("showLabel",
                            !mi.hasMetaValue("showLabel"));
                    mi.persist(_mapView.getMapEventDispatcher(), null,
                            getClass());
                }
                break;
        }
    }
}
