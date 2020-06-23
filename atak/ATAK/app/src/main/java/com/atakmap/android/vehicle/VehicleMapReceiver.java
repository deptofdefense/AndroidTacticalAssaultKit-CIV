
package com.atakmap.android.vehicle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.android.vehicle.overhead.OverheadRotationTool;

public class VehicleMapReceiver extends BroadcastReceiver {
    public static final String TAG = "VehicleMapReceiver";
    public static final String ROTATE = "com.atakmap.android.maps.ROTATE";
    public static final String TOGGLE_LABEL = "com.atakmap.android.maps.TOGGLE_LABEL";

    private VehicleRotationTool _vehTool;
    private OverheadRotationTool _overheadTool;
    private final MapView _mapView;
    private final MapGroup _overheadGroup;

    public VehicleMapReceiver(MapView mapView) {
        _mapView = mapView;
        _overheadGroup = VehicleMapComponent.getOverheadGroup(_mapView);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action == null)
            return;

        String uid = intent.getStringExtra("uid");
        if (uid == null)
            return;

        if (action.equals(ROTATE)) {
            if (_vehTool == null)
                _vehTool = new VehicleRotationTool(_mapView, null);
            if (_overheadTool == null)
                _overheadTool = new OverheadRotationTool(_mapView, null);

            String tool = VehicleRotationTool.TOOL_NAME;
            if (_overheadGroup.deepFindUID(uid) != null)
                tool = OverheadRotationTool.TOOL_NAME;
            Intent myIntent = new Intent();
            myIntent.setAction(ToolManagerBroadcastReceiver.BEGIN_TOOL);
            myIntent.putExtra("tool", tool);
            Bundle extras = intent.getExtras();
            if (extras == null)
                extras = new Bundle();
            myIntent.putExtras(extras);
            AtakBroadcast.getInstance().sendBroadcast(myIntent);
        } else if (action.equals(TOGGLE_LABEL)) {
            MapItem mi = _mapView.getRootGroup().deepFindUID(uid);
            if (mi == null)
                return;
            if (mi.getType().equals("shape_marker"))
                mi = ATAKUtilities.findAssocShape(mi);
            if (mi instanceof VehicleShape) {
                VehicleShape veh = (VehicleShape) mi;
                veh.setShowLabel(!veh.hasMetaValue("showLabel"));
                veh.save();
            }
        }
    }
}
