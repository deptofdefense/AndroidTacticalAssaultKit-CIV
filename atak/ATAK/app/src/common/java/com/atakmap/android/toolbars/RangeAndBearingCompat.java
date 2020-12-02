
package com.atakmap.android.toolbars;

import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapGroup;

public class RangeAndBearingCompat {
    public static MapGroup getGroup() {
        return RangeAndBearingMapComponent.getGroup();
    }

    public static void updateDropdownUnits(DropDownReceiver ddr) {
        ((RangeAndBearingDropDown) ddr).updateUnits();
    }
}
