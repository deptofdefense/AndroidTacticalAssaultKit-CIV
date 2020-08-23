
package com.atakmap.android.rubbersheet.ui.dropdown;

import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.maps.AbstractSheet;
import com.atakmap.android.rubbersheet.maps.RubberImage;
import com.atakmap.android.rubbersheet.tool.RubberSheetEditTool;

public class RubberImageDropDown extends AbstractSheetDropDown {

    public RubberImageDropDown(MapView mapView, MapGroup group) {
        super(mapView);
        _editTool = new RubberSheetEditTool(mapView, group);
    }

    @Override
    public boolean show(AbstractSheet item, boolean edit) {
        if (!(item instanceof RubberImage))
            return false;

        return super.show(item, edit);
    }
}
