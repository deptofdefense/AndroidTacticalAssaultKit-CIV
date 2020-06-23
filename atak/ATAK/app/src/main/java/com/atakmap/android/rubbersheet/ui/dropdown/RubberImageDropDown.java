
package com.atakmap.android.rubbersheet.ui.dropdown;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.maps.AbstractSheet;
import com.atakmap.android.rubbersheet.maps.RubberImage;
import com.atakmap.android.rubbersheet.maps.RubberSheetMapGroup;
import com.atakmap.android.rubbersheet.tool.RubberSheetEditTool;

public class RubberImageDropDown extends AbstractSheetDropDown {

    public RubberImageDropDown(MapView mapView, RubberSheetMapGroup group) {
        super(mapView, group);
        _editTool = new RubberSheetEditTool(mapView, group);
    }

    @Override
    public boolean show(AbstractSheet item, boolean edit) {
        if (!(item instanceof RubberImage))
            return false;

        return super.show(item, edit);
    }
}
