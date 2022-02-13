
package com.atakmap.android.vehicle.model;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.tool.RubberModelEditTool;
import com.atakmap.app.R;

/**
 * For editing the position, heading, and altitude of a vehicle model
 * This is a copy of {@link RubberModelEditTool} with tweaked prompts
 */
public class VehicleModelEditTool extends RubberModelEditTool {

    private static final String TAG = "VehicleModelEditTool";
    public static final String TOOL_NAME = "com.atakmap.android.vehicle.model."
            + TAG;

    public VehicleModelEditTool(MapView mapView, MapGroup group) {
        super(mapView, group);
        _identifier = TOOL_NAME;
    }

    @Override
    public String getIdentifier() {
        return TOOL_NAME;
    }

    @Override
    protected void displayPrompt() {
        int msgId;
        if (_buttons[DRAG].isSelected())
            msgId = R.string.drag_vehicle_tooltip;
        else if (_buttons[HEADING].isSelected())
            msgId = R.string.rotate_vehicle_tooltip;
        else
            msgId = R.string.elevate_vehicle_tooltip;
        _cont.displayPrompt(_context.getString(msgId));
    }
}
