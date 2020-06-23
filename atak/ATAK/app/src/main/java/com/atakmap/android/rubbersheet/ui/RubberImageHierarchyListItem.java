
package com.atakmap.android.rubbersheet.ui;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.data.export.ExportGRGTask;
import com.atakmap.android.rubbersheet.maps.RubberImage;

/**
 * List item representing a survey
 */
class RubberImageHierarchyListItem extends AbstractSheetHierarchyListItem {

    private static final String TAG = "RubberImageHierarchyListItem";

    private final RubberImage _item;

    RubberImageHierarchyListItem(MapView mapView, RubberImage item) {
        super(mapView, item);
        _item = item;
    }

    @Override
    protected void promptExport() {
        new ExportGRGTask(_mapView, _item, this).execute();
    }
}
