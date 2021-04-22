
package com.atakmap.android.toolbars;

import android.widget.ImageButton;

import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;

public class RangeAndBearingCompat {
    public static MapGroup getGroup() {
        return RangeAndBearingMapComponent.getGroup();
    }

    public static void updateDropdownUnits(DropDownReceiver ddr) {
        ((RangeAndBearingDropDown) ddr).updateUnits();
    }

    public static DynamicRangeAndBearingTool getDynamicRangeAndBearingInstance(
            MapView mapView, ImageButton button) {
        return new DynamicRangeAndBearingTool(mapView, button);
    }

    public static BullseyeTool createBullseyeToolInstance(MapView mapView,
            ImageButton button) {
        return new BullseyeTool(mapView, button);
    }

    public static RangeCircleButtonTool createRangeCircleButtonToolInstance(
            MapView mapView, ImageButton button) {
        return new RangeCircleButtonTool(mapView, button);
    }
}
