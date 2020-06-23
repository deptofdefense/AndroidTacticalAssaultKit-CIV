
package com.atakmap.android.rubbersheet.ui;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.rubbersheet.data.export.ExportOBJTask;
import com.atakmap.android.rubbersheet.maps.RubberModel;

/**
 * List item representing a survey
 */
class RubberModelHierarchyListItem extends AbstractSheetHierarchyListItem {

    private static final String TAG = "RubberModelHierarchyListItem";

    private final RubberModel _item;

    RubberModelHierarchyListItem(MapView mapView, RubberModel item) {
        super(mapView, item);
        _item = item;
    }

    @Override
    protected void promptExport() {
        new ExportOBJTask(_mapView, _item, this).execute();
    }
}
